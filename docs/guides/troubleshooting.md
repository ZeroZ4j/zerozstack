# Troubleshooting

ZeroZ Stack's characteristic failure is *nothing happening*. Several conditions are caught and logged
rather than thrown, so there is no exception to search for and no stack trace to follow. This page is
organized by symptom.

Before anything else: **read your build warnings**, and **read the server log**. Two of the most
common causes report themselves there and nowhere else.

## Nothing propagates

### `notifyChanged` throws "it has never been serialized to a client"

The object has no `ObjectMapper` handle. An object gets one by being serialized to a client.

**Fix:** return it from an RMI method at least once before relying on sync.

```java
LiveChatState state = chatService.getState();   // this is what registers the handle
```

### A client's edit to a `@ClientWritable` object is ignored

The server rejected it. The writer now receives a `0x15 REJECT` frame naming the reason, and every
rejection cause is logged server-side, so check both.

Causes, in order:

1. the class has both `@LiveSync` and `@ClientWritable`;
2. the session holds one of the roles named in `@ClientWritable("...")`;
3. the proposed value passes the model's validation annotations;
4. the canonical instance exists on the server, i.e. it was synced out at least once.

If the message says **"The change also alters a …"**, the edit reached a second object further
inside. Every object a change touches is checked separately, so a model nested inside a
`@ClientWritable` model needs its own `@ClientWritable` and its own roles. Either mark the inner
model, or stop sending it to the client. See
[LiveSync](../LIVESYNC.md#every-object-the-change-touches-is-checked-not-just-the-outer-one).

### `events.publish(...)` reaches no client

The payload is not wire-serializable. `publish` now throws `IllegalArgumentException` naming the
offending type, so this should no longer be silent — if you see it, check for a caught exception in
your own code.

**Fix:** annotate the payload class `@DataModel`, or use a supported built-in type. Primitives,
`String`, `UUID`, enums, `BigDecimal`, `BigInteger`, the `java.time` local types, `Duration`,
`Optional`, `List`, `Set`, `Map` and primitive arrays are all supported; object arrays (`String[]`),
zone-aware date-times and EclipseStore's `Lazy` are not. See
[Limitations](../reference/limitations.md#serialization).

### A shared signal never reaches clients

Same cause as above: `set()` now throws `IllegalArgumentException` for an unserializable value. Note
that a rejected *client* write to a `sharedWritable` signal is still logged nowhere — the writer simply
gets snapped back.

### A shared signal has the wrong initial value, or ignores its roles

Two declarations collided. The default wire name is the payload's class name, so there is one default
shared signal per type. A conflicting redeclaration now throws `IllegalStateException` naming the
signal; the fix is explicit names.

```java
// Both return the SAME signal.
Signals.shared(new Temperature(20));
Signals.shared(new Temperature(5));

// Fix: name them explicitly.
Signals.shared("temp.indoor",  new Temperature(20));
Signals.shared("temp.outdoor", new Temperature(5));
```

## The data changed but the UI did not

### A LiveSync'd object updates but nothing re-renders

You read the getter outside an `Effect`. A live object notifies the reactive system, but only code that
read it *while being tracked* is subscribed.

```java
// WRONG — read once at construction; nothing is tracking, so nothing re-runs.
label.setText(profile.getMission());

// RIGHT
Effect.create(() -> label.setText(profile.getMission()));
```

Also check the object actually arrived by sync (see `notifyChanged` above), and that the field was
changed through its setter rather than by mutating a collection in place. See
[LiveSync objects are reactive](../decide/events-vs-signals-vs-livesync.md#livesync-objects-are-reactive).

### An `Effect` does not re-run after a change

The value was mutated in place. `ValueSignal.set` compares with `equals` and does nothing when the new
value matches the old — and a mutated list equals itself.

```java
// WRONG
tasks.get().add(task);
tasks.set(tasks.get());

// RIGHT
tasks.update(current -> {
    List<Task> next = new ArrayList<>(current);
    next.add(task);
    return next;
});
```

### A field's edits do not reach the signal

`bindValue` requires a writable `ValueSignal` and now throws when given anything else, so this should
surface at the binding call rather than as missing updates. Use `bindValueReadOnly` for a deliberate
one-way binding.

### A LiveSync collection edit is invisible

Setters are the tracking boundary, so `obj.getTags().add("x")` reports nothing. Reassign through the
setter, or call `LiveMutationTracker.touch(obj)` afterwards. Tracked collections do not exist.

### Events that happened during startup are missing

A snapshot race: you fetched initial state and then subscribed, losing anything that arrived in
between. Subscribe first, then fetch, then reconcile by value equality.

## Build problems

### `mvn clean test` fails to resolve modules

The annotation processor and shared API must be in your local repository before the modules that
consume them can compile. Install first, then test.

```bash
mvn clean install -DskipTests
mvn test
```

### Generated classes are missing — `MyService_Stub`, `MyModel_Rules`, `MyModel_Live`

Check that the module has `zerozstack-apt` on its annotation-processor path, that the model is annotated
`@DataModel`, and that the build actually ran the processor. Note that a `@ClientWritable` field with
no setter, and `@ClientWritable` without `@LiveSync`, are now **compile errors** rather than warnings —
both used to produce a silently half-working model.

### `NoSuchMethodError` or a missing serializer at runtime

Stale generated sources. Rebuild from clean:

```bash
mvn clean install -DskipTests
```

### `IllegalArgumentException: Unknown @DataModel class`

The class was not registered. Registrars are discovered through `META-INF/services`, generated per
module — so this usually means the module containing the model was not rebuilt, or the model is
missing `@DataModel`.

### `IllegalStateException: No server binding is on the classpath`

The server module has neither `zerozstack-server-helidon` nor `zerozstack-server-vidocq`, so
`Zeroz4jServer.start` has nothing to start. Add one (a generated project: build with `-Phelidon` or
`-Pvidocq`). A WAR never calls `start`; it takes `zerozstack-server-jakarta`. See
[Starting the server](starting-the-server.md#when-nothing-is-on-the-classpath).

### `IllegalStateException: More than one server binding is on the classpath: helidon, vidocq`

Both binding jars are in `target/libs`, usually left over from a build with the other container.
Run `mvn clean install` with one profile, or pick one with `-Dzeroz.server.binding=helidon` (or
`vidocq`). See [Starting the server](starting-the-server.md#when-more-than-one-is-on-the-classpath).

### Log lines written through `System.Logger` never appear

The log shows `SLF4J(W): No SLF4J providers were found` at start, lines from `java.util.logging`
appear, and lines from Helidon or a library that logs through `System.Logger` are missing.

`zerozstack-server-helidon` depends on `helidon-logging-slf4j`, which brings
`slf4j-jdk-platform-logging`. That library hands every `System.Logger` line to SLF4J. With no SLF4J
provider on the classpath, SLF4J drops them.

A project generated from the archetype since 0.11.0 has `org.slf4j:slf4j-simple` (2.0.12, as in the
example servers) in its server module, and does not show this. It also does no harm under the
`vidocq` profile, where nothing hands it `System.Logger` lines. A project generated earlier, or one
that removed it, needs the dependency back:

```xml
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-simple</artifactId>
  <version>2.0.12</version>
</dependency>
```

Stay on a stable 2.0.x release. SLF4J 2.1 is alpha.

## Connection problems

### The page loads but the app never starts

The WebSocket did not connect. The endpoint is `/wasm-rmi`. Check that the client's URL matches the
server's host and port, and that it uses `wss://` when the page is served over HTTPS.

### A blank page and no errors

Since 0.11.0 a page whose application does not start shows a sentence and a Reload button in
`#app-root` instead of nothing: "Could not start the application." when the start code threw,
"Could not connect to the server." when the connection is not usable after 15 seconds, and
"Sign-in was refused." when the server refused the sign-in. The error is also in the server log, see
[Browser errors in the server log](#browser-errors-in-the-server-log). It shows only when `#app-root`
holds no element, so an application that drew something is never covered.

If you still get a blank page, the compiled client did not load or threw before it could say so. Open the browser console — a TeaVM failure
shows up there. Check that `classes.js` is being served (the client build writes it to the client
module's `target/js`) and that the page's script tag points at it.

### Browser errors in the server log

Since 0.11.0 an uncaught error in the browser, and a promise rejected with no handler, are written to
the server log once the connection is up:

```
WARNING: Browser error [k3m9xa] user=anna page=/orders: Cannot read properties of undefined | at ...
```

The reference in brackets is also shown on the page when the start failure message is up. At most 20
reports leave one connection, 120 a minute leave the server, and an identical error twice running is
sent once. The text is cut to length and line breaks become spaces, so one report is one log line. The
page path is logged without its query. Errors before the connection opens are in the browser console
only.

| Setting | Effect | Default |
|---|---|---|
| `zeroz.clienterrors.enabled` | `false` takes no reports, and the browser stops sending after the first answer | on |
| `zeroz.clienterrors.maxPerConnection` | Reports taken from one connection | **20** |

In the client, `Zeroz4jClient.reportErrors(false)` sends nothing and
`Zeroz4jClient.showStartFailure(false)` removes the start message. Call either before `connect`. The
logger is `com.zeroz4j.client`.

### The connection drops every 60 seconds when idle, and works again the moment you click

A proxy in front of the application is closing a tunnel that carried nothing. nginx defaults to 60
seconds (`proxy_read_timeout`), Cloudflare cuts at 100 and is not yours to configure. The giveaway is
the regularity: sockets open, authenticate, and die at exactly the same age, each reconnect re-sending
everything.

Since 0.6.1 the client prevents this by itself — after 25 seconds of silence it sends a keepalive and
the server answers. If you still see it, check that both ends are on 0.6.1 or later, and that nothing
called `Keepalive.configure(0)`. Set a shorter interval than the proxy's timeout with
`Keepalive.configure(seconds)`; raising the proxy's own timeout is still worth doing where you
control it.

### An RMI call never returns

The default request timeout is 30 seconds, and a call still unanswered then fails with
`RequestTimeoutException`. Its message is "The server did not answer in time. Try again.", which a
screen can show as it is; the browser console names the call and the deadline it missed
(`[zeroz4j] RMI call ... timed out`). The deadline is checked by a timer of its own, so it fires even when
nothing else is happening on the connection. If calls time out, check the server log for an exception
inside the service method — an error becomes an error frame, but a hung method produces nothing.

Work that can pass 30 seconds does not belong in a plain call. Make it a
[slow call](slow-calls.md): the method returns at once, the screen follows the work, and the result
survives a reconnect and a page reload. Raising `WasmRmiClient.setRequestTimeout` moves the limit for
every call in the browser, so treat it as a stopgap.

A hang is **not** the connection: since 0.5.0 a call made while the socket is down, or in flight
when it drops, fails immediately with `DisconnectedException` instead of hanging. A network that
dies without closing the socket is caught too: a call that has waited five seconds with nothing
arriving makes the client ping, and a ping unanswered for ten seconds closes the socket, which fails
the call with `DisconnectedException`. Raise that bound with `Keepalive.configureLiveness(seconds)`
on a network known to pause for longer.

### The page loads but the WebSocket handshake answers 404 — on Linux only

The classpath was built from a `libs/*` wildcard, which expands in directory order: alphabetical
on Windows, arbitrary on Linux. One of the arbitrary orders loads Helidon's CDI extensions in a
sequence where the WebSocket routing registers after the server was already built — HTTP routes
fine, static content serves, and every upgrade to `/wasm-rmi` gets 404. Same jars, same code,
works on Windows. Launch with a sorted explicit classpath instead:

```bash
java -cp "target/classes:$(ls target/libs/*.jar | sort | tr '\n' ':')" com.mycompany.server.ServerApp
```

The `Dockerfile` generated by the archetype already sorts. If a jpackage image built on Linux
shows the symptom, the jar order in its `app/<name>.cfg` is the same suspect.

### The page says "Connection lost — reconnecting…"

That is the built-in banner doing its job: the WebSocket dropped and the client is retrying with
backoff. When the server is reachable again the banner disappears, shared signals snap to their
current values, and live objects are re-synced automatically. While the page is hidden the client
does not retry at all (0.9.1+); it connects at once when the page is shown again. An application
that draws its own indicator turns the banner off with `Zeroz4jClient.showConnectionBanner(false)`.

### The page says "We could not reconnect to the server. Reload the page to try again."

Since 0.9.1 the client stops after 10 failed attempts in a row, about 75 seconds of retrying. The
server was down or unreachable for all of that time. Reloading the page is the fix, and the bar's
Reload button does it. `WasmRmiClientChannel.hasGivenUp()` is true in this state, and
`WasmRmiClientChannel.reconnect()` starts again from code.

### A browser keeps reconnecting, and you want to know why

Since 0.9.1 the server log has one INFO line per closed connection, and the next connection from the
same browser reports how the one before it ended:

```
Connection closed: session 7f3a, user alice, code 1006 CLOSED_ABNORMALLY, no reason given, open 830 ms
Client connected: alice roles=[user] session=8b21; previous connection closed with code 1006 after 830 ms, page hidden, reconnect attempt 3
```

The second line comes from the `zerozClose*` query parameters the client puts on its next handshake,
so it appears even when the first connection never reached the server's close handler. Code 1001
means the browser left the page, 1006 means the connection ended with no close frame (a proxy
timeout, a network change, a phone freezing a background tab), 1008 and 1013 mean the server closed
it. `page hidden` on a short-lived connection points at the browser, not the server. A transport
error on a connection is one WARN line, `Connection error: session …`, with a stack trace only when
it is something other than the other end going away.

### Data is stale after the server restarted

Re-sync restores live objects from the server's in-memory handle registry, which a restart empties.
Shared signals recover; live objects fetched before the restart cannot, and the server log says how
many (`Re-sync for session …: N handle(s) unknown`). The application must re-fetch them the way it
first obtained them — a `StateListener` on `CONNECTED` is the place.

### Per-session pushes stop after a reconnect

A reconnect is a **new session with a new id**. Anything the application keyed by session id — a
`Scope.SESSION` push target, a "sessions viewing this dashboard" registry — points at the dead
session. Re-register from a `StateListener` on `CONNECTED`, and observe the CDI event
`SessionClosedEvent` server-side to drop the stale entry.

## Sign-in and access

### `SecurityException: Rejected RMI call to unregistered service`

The server only dispatches to interfaces it discovered on a CDI bean at startup. Confirm the
implementation is annotated `@ApplicationScoped` and that it implements an interface annotated
`@RmiService`.

### A method returns an error for an authenticated user

`@RolesAllowed` implies `@Secured`. Method-level roles override interface-level roles — check both.
Roles come from the WebSocket handshake, so a user whose roles changed must reconnect.

### A login screen appears and you have no credentials

Four examples enable development authentication — `chat-events`, `chat-livesync`, `job-monitor` and
`components-showcase`. Credentials are `demo` / `demo` (role `user`) and `admin` / `admin` (roles
`user`, `admin`); use `admin` for operations annotated `@RolesAllowed("admin")`. Replace it before any
real deployment by registering an `AuthenticationProvider` — see
[Authentication and authorization](security-auth.md).

### `@RolesAllowed` is ignored

You put it on the implementation. The dispatcher scans the `@RmiService` **interface** and its declared
methods only, so an annotation on the bean class is never read and the method stays open. Move it
to the interface — and check you imported `com.zeroz4j.api.RolesAllowed`, not the Jakarta annotation of
the same name.

### The examples ask for a password and nothing you type works

Since 0.7.0 an example server does not switch on the built-in `demo` and `admin` logins by starting.
Add `--dev-login` to the command line, or run the example's `run.bat`, which already passes it. A
server running that way prints a warning at startup.

### The error says "The server could not complete this request. Reference: …"

That is every unexpected exception. Search the server log for the same code and you have the real
message and the stack trace. If this is a refusal your own code raised on purpose, throw
`com.zeroz4j.server.ClientVisibleException` instead and its message reaches the caller word for word.

### `LiveMutex.lock()` fails at once instead of waiting

The server only grants a lock on an object it actually sent to that browser. Fetch the object from
your service and lock the copy you get back. A record of what was sent holds 10,000 objects per
browser and is dropped after 24 hours idle, so a very old object may need re-fetching. If the
message mentions signing in, this deployment has `zeroz.livemutex.requireAuthentication=true`.

### An object comes back empty after a reconnect

Same rule: the server answers a re-read only for objects it sent to that browser. This happens with
a client that carries no cookie — a test harness, a non-browser client — because it is remembered
only for the life of one connection. Fetch the objects again the way you first obtained them; that
always works.

### An upload is refused

Read the sentence the component shows; it is what the server sent.

| Sentence | What to do |
|---|---|
| "That file is too big. The largest we can take is 25 MB." | Choose a smaller file, or raise `zeroz.upload.maxBytes`. |
| "That took too long to start. Please choose the file again." | The permission expired after 60 seconds. Pick the file again. |
| "That file did not finish sending. Please try again." | The connection dropped part-way, or the upload was canceled. |
| "We could not accept that file. Reload the page and try again." | The live connection is gone. A reload gets a new one. |

If nothing happens at all and the browser console shows a 404 for `zeroz4j-upload`, the deployment
is missing `zerozstack-server-jakarta` (in a WAR) or `zerozstack-server-jaxrs` (standalone), or a
`web.xml` has taken that servlet name over. See [Accepting file uploads](file-uploads.md).

## When you are stuck

Check the [anti-patterns](../decide/antipatterns.md) list — it is the same material organized by cause
rather than symptom — and open an issue if the behavior still does not match the documentation. A doc
that misled you is a bug worth reporting.
