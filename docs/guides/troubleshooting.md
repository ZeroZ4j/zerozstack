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

## Connection problems

### The page loads but the app never starts

The WebSocket did not connect. The endpoint is `/wasm-rmi`. Check that the client's URL matches the
server's host and port, and that it uses `wss://` when the page is served over HTTPS.

### A blank page and no errors

The compiled client did not load or threw during startup. Open the browser console — a TeaVM failure
shows up there. Check that `classes.js` is being served (the client build writes it to the client
module's `target/js`) and that the page's script tag points at it.

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

The default request timeout is 30 seconds. If a call hangs, check the server log for an exception
inside the service method — an error becomes an error frame, but a hung method produces nothing.

A hang is **not** the connection: since 0.5.0 a call made while the socket is down, or in flight
when it drops, fails immediately with `DisconnectedException` instead of hanging.

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
backoff, indefinitely. When the server is reachable again the banner disappears, shared signals
snap to their current values, and live objects are re-synced automatically. Nothing to do unless
it never disappears — then the server is down or unreachable, and the browser console shows the
retry attempts. An application that draws its own indicator turns the banner off with
`Zeroz4jClient.showConnectionBanner(false)`.

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
