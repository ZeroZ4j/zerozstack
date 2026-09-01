# Testing an application

This page is for someone who has built an application on ZeroZ Stack and now wants tests that run in
seconds, in one process, and fail when something is wrong.

By the end you will be able to start a server inside a test, open connections to it, drive it, and
start a second server beside the first without the two getting in each other's way.

## When to use this

Use this harness for anything that needs a real server: an RMI service, a server event, a shared
signal, a LiveSync push, a refusal, a limit. It is not a unit-test replacement — a class with no
server in it needs no server. It is also not a browser: it tells you what bytes the server sent, not
what a page looked like.

Everything here needs one extra dependency, with **test scope**:

```xml
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-server-test</artifactId>
    <scope>test</scope>
</dependency>
```

It starts a bean container, so keep it out of your production classpath.

**Three examples use it, and they are the fastest way in.** `payments-datamodels` drives real call
frames through a server to prove its models survive the wire; `chat-livesync` checks that a message
one person sends is pushed to every browser and that clearing the history needs the admin role;
`form-signup` checks that a browser ignoring the validation rules is stopped at the server. All
three are under `zerozstack-examples/`.

## Start a server

```java
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderNoticeTest {

    @Test
    void approvingAnOrderTellsTheBrowser() {
        try (TestServer server = TestServer.builder()
                     .named("orders")
                     .beans(OrderServiceImpl.class)
                     .start();
             TestConnection browser = server.connect("alice", "admin")) {

            server.bean(OrderService.class).approve(17L);

            assertEquals(1, browser.pushCount(), "the browser is told the order was approved");
        }
    }
}
```

Three things are happening.

`TestServer.builder().beans(...)` lists **your** classes: the service implementations and anything
they inject. Nothing is discovered by scanning, so a bean you did not name cannot appear by
accident. The framework's own beans are always there.

`server.connect("alice", "admin")` opens a connection the way a browser does — a sign-in with those
roles, a browser identifier, an opening frame telling the client who it is. It gives back a
`TestConnection`, which is a real connection as far as the server is concerned; the bytes just land
in a list instead of on a network.

`browser.pushCount()` waits for the server's outbound writer to catch up and then counts the events
that arrived. Writing leaves the calling thread — every connection has a queue and one writer — so
reading a list straight after a call would race it. Every read method on `TestConnection` waits
first.

Close both when the test ends. A try-with-resources is the easiest way; without it the bean
container stays up.

## What a connection can tell you

| You want | Call |
|---|---|
| how many events arrived | `browser.pushCount()` |
| how many frames of one kind | `browser.countOf(TestConnection.OBJECT_UPDATE)` |
| the raw bytes of one frame | `browser.frame(0)` |
| what kind a frame is | `browser.opcode(0)` |
| everything, oldest first | `browser.frames()` |
| a clean slate before the next step | `browser.clearFrames()` |
| whether the server closed it, and why | `browser.isOpen()`, `browser.closedBecause()` |
| the browser identifier it carries | `browser.browserId()` |

To send something up, `browser.send(bytes)` puts a frame in as though the browser had sent it.

## Calling the way a browser calls

`server.bean(OrderService.class).approve(17L)` calls the service directly. That is the right thing
most of the time, and it is not the same as a browser calling it. Two differences matter.

**An object gets its handle by going over the wire.** A `@LiveSync` object returned from a direct
bean call is handed to your test and registered with nobody, so `notifyChanged` on it afterwards
throws — correctly, because no browser holds it. A test about syncing has to fetch the object
through the connection first, the way the view does.

**Nothing that depends on the caller's identity is exercised.** Roles, `@Secured`, argument
validation and the disclosure record all live on the dispatch path, and a direct call goes round it.

So a test about any of those builds the frame itself. Its shape is in
[the protocol reference](../PROTOCOL.md): a message number, the
interface name, the method name, how many arguments there are, then the arguments, written with
`BinarySerializer`. Two things then catch people out, and both are handled the same way in all
three examples:

- **The answer arrives a moment later.** A frame a browser sends is put on that connection's queue
  and handled on another thread, so `send` returns before the call has run. Read the connection in a
  short loop until the answer is there.
- **The answer is not necessarily the last frame.** A call that changes a synced object is followed
  by the object update it caused, on the same connection. Match on the number you put on the front
  of the request, and on the opcode being `0x01` or `0x0F`, exactly as a browser does.

`PaymentsWireRoundTripTest` in `zerozstack-examples/payments-datamodels` is about sixty lines of
this, comments included, and is the one to copy.

## Settings belong to the server, not to the process

Settings have always been system properties: `-Dzeroz.ws.maxBinaryMessageBytes=8388608` and the
like. That is still true, and nothing about an existing deployment changes.

A system property belongs to the whole Java process, though, which makes it useless in a test — set
it and you have set it for everything else running at the same time. So a server can be given
settings of its own:

```java
import com.zeroz4j.server.ServerSettings;

TestServer small = TestServer.builder()
        .ignoringSystemProperties()
        .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 1024)
        .start();
```

`set(...)` gives this server one setting. Anything it is not given still comes from the system
property, exactly as before.

`ignoringSystemProperties()` goes further: this server reads none of the process's settings at all.
Use it whenever a test asserts on a limit. The result then depends only on what the test set, and
does not change because the build was run with a different `-D` flag.

The names live in `com.zeroz4j.server.ServerSettings` — one constant per setting, with what it
means and what it defaults to.

## Two servers at once

Two servers in one test are two servers. Each has its own connections, its own record of what it has
sent to whom, its own object locks, and its own settings.

```java
try (TestServer alpha = TestServer.builder().named("alpha").start();
     TestServer beta  = TestServer.builder().named("beta").start();
     TestConnection onAlpha = alpha.connect();
     TestConnection onBeta  = beta.connect()) {

    alpha.events().publish(NOTICE, "for alpha only");

    assertEquals(1, onAlpha.pushCount());
    assertEquals(0, onBeta.pushCount(), "beta's browser is not alpha's to write to");
}
```

Naming them is worth doing. When something goes wrong the message says which server it was.

## What is isolated, and what is not

**Isolated — each server has its own:**

- the connections that are open on it, and everything published, broadcast or synced to them;
- the record of which objects it has sent to which browser, which is what decides whether a client
  may read an object back or lock it;
- the names it has issued for deferred data (`Lazy` fields);
- object locks (`LiveMutex`);
- the keepalive budget per connection;
- every setting listed in `ServerSettings`;
- your own beans — `alpha.bean(OrderService.class)` and `beta.bean(OrderService.class)` are two
  different objects.

**Still shared by the whole Java process — one copy however many servers you start:**

- **The value of a shared signal.** `Signals.shared("banner", …)` is a `static final` field of one of
  your classes, so there is one value per process by definition; that is what "shared" has always
  meant here. Delivery *is* per server: each one pushes the value to its own connections. If two
  servers in one test both watch the same shared signal, they see the same value. Call
  `Signals.resetForTesting()` between tests, and use `Signals.scoped(...)` where you need per-user
  or per-tenant values.
- **The key the browser identifier is signed with.** One key per process, generated at startup
  unless `zeroz.clientId.secret` is set. A browser identifier issued by one server therefore
  verifies on another. It has no effect on which server a connection belongs to.
- **Upload passes.** The short-lived token that lets a browser post a file is kept per process, and
  a file arrives over plain HTTP where there is no connection to say which server it belongs to. The
  size limit and the pass lifetime are per server; the pass table is not.
- **Which authentication provider is in use, and which thread factory.** Both are found on the
  classpath with `ServiceLoader`, so they are a property of the build rather than of a server. Two
  servers in one process get the same one.
- **Registered wire types.** `BinaryRegistry` is one table per process, as it must be: it maps a
  class name to how that class travels, and the answer cannot differ between two servers running the
  same code.

**What to do about the ones that remain.** For a shared signal, reset the signals library between
tests. For uploads, test the handler directly rather than through the HTTP address. For the rest,
there is nothing to do: they are the same for every server because the classpath is.

## It complains rather than passing quietly

The reason this page exists is a real report. An application ran three browser tests in one process;
two of them passed while asserting nothing at all, because they were watching a connection that
belonged to a different server. Somebody really was writing to it. Nothing said otherwise.

So the framework now refuses, out loud, in the cases that used to be silent:

- **Handing one server's connection to another server throws**, naming both servers.
- **A connection that was never opened on a running server throws** the moment the framework is
  asked to write to it — instead of the write going nowhere.
- **Driving a server after it is closed throws.** A test that keeps working a closed server proves
  nothing about it, so it fails instead of passing.
- **A WebSocket endpoint that reached no server throws** with a sentence saying so, rather than the
  `NullPointerException` it used to produce on the first connection.

If you see one of these, the test was measuring the wrong thing. That is the point.

## What this harness is not

It runs your server's Java, in this process, against connections that record what they were sent. It
is the fast test, not the whole story.

- **No browser and no client code.** The client is compiled to JavaScript by TeaVM; nothing here
  runs it. A test proves the server sent the right bytes, not that the screen showed the right
  thing.
- **No HTTP.** Only the live connection. The pages, the client bundle and the file-upload address
  are served by whichever binding you deploy — Helidon or a servlet container — and are not started
  here.
- **No real network.** Nothing is timed out, dropped or reconnected unless you make it happen.
- **A frame is bytes.** Reading what is inside one means decoding it with `BinarySerializer`, the
  same way the client does. Most tests do not need to: counting frames and asserting on your own
  beans covers more than it sounds like it does.
- **Persistence is yours to arrange.** The harness starts no store. Add whatever your beans need to
  the `beans(...)` list — for a store that is usually a four-line class with a `@Produces` method
  handing back a node opened in a `@TempDir`, which is what `chat-livesync` and `form-signup` do.
  Nothing is discovered by scanning, so a bean the test did not name does not exist.
