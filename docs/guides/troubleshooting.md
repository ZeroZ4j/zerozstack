# Troubleshooting

ZeroZ Stack's characteristic failure is *nothing happening*. Several conditions are caught and logged
rather than thrown, so there is no exception to search for and no stack trace to follow. This page is
organised by symptom.

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

### An RMI call never returns

The default request timeout is 30 seconds. If a call hangs, check the server log for an exception
inside the service method — an error becomes an error frame, but a hung method produces nothing.

## Security and access

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
methods only, so an annotation on the bean class is never read and the method stays unprotected. Move it
to the interface — and check you imported `com.zeroz4j.api.RolesAllowed`, not the Jakarta annotation of
the same name.

## When you are stuck

Check the [anti-patterns](../decide/antipatterns.md) list — it is the same material organised by cause
rather than symptom — and open an issue if the behaviour still does not match the documentation. A doc
that misled you is a bug worth reporting.
