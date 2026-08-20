# Limitations

Every known gap in ZeroZ Stack 0.6.2, in one place. This page exists because surprises are what make
people abandon a framework, and because a coding agent that reads it will not generate code against
features that do not exist.

ZeroZ Stack is an experimental proof-of-concept. Read this list as the honest boundary of the
demonstration, not as a roadmap commitment.

## Declared but not implemented

These exist in the source as annotations or constants and do nothing. Do not build on them.

| Item | Status |
|---|---|
| Protocol opcodes `0x11 SNAPSHOT`, `0x12 UNSUBSCRIBE`, `0x13 MUTATE`, `0x14 ACK`, `0x15 REJECT`, `0x16 SIGNAL_SUB`, `0x18 PUSH` | Declared and unreferenced. Reserved for future protocol work. |
| Versioned mutations, acknowledgement and conflict rejection | Reserved in the protocol. The implemented sync path has no version field, no ACK and no conflict detection. |
| Coalesced LiveSync mutations and UI-scheduler dispatch of inbound frames | Both are conditional on a `PlatformScheduler`, and `WasmRmiClient.setPlatformScheduler` is never called anywhere in the framework. So every setter sends its own mutation frame, and all inbound frames are applied inline on the WebSocket callback. |

## Connection and reconnection

Since 0.5.0 a dropped WebSocket recovers by itself: the channel reconnects with backoff, a built-in
banner shows the outage, shared signals re-subscribe, live objects are re-synced from the server,
edits and writes made while offline are sent on reconnect, and RMI calls fail immediately with
`DisconnectedException` instead of hanging. Since 0.6.1 an idle connection also sends a keepalive
every 25 seconds, so a proxy in front of the application does not close it for silence.

What automatic recovery deliberately does **not** cover:

- **RMI calls are never replayed.** A call that failed to a drop is the application's to retry — the
  framework cannot know whether repeating it is safe. Catch `DisconnectedException`, or disable
  controls while `WasmRmiClient.connectionState()` is not `CONNECTED`.
- **A server restart empties the handle registry.** Re-sync can only restore objects the server
  still knows. After a restart, live objects held by clients stay as they were and the application
  must re-fetch them the way it first obtained them; the server logs how many handles it could not
  restore. Shared signals recover fully either way.
- **Session ids change on reconnect.** Anything keyed by session id — `Scope.SESSION` pushes,
  application registries of "sessions viewing X" — points at a dead session after a drop. The
  application must re-register from a `StateListener` on `CONNECTED`; observe the server-side CDI
  event `SessionClosedEvent` to clean up the stale entry.
- **A lost `LiveMutex` stays lost.** The server releases a session's locks when the socket closes.
  Reconnecting does not re-acquire; the holder is told through `setLostListener`. Taking the
  lock again after a reconnect works, because the right to lock an object is remembered per browser,
  not per connection.
- **Events broadcast during an outage are gone.** Events are fire-and-forget news with no replay;
  this is unchanged and by design. State belongs in signals or LiveSync, which do recover.
- **Offline writes are last-write-wins.** A shared-signal write queued offline flushes as one write
  with the final value; intermediate values are not replayed. Concurrent edits from other clients
  during the outage are settled by the server's usual last-write-wins rules, not merged.

## LiveSync

- **Change notification is per object, not per field.** Any inbound sync touching an instance re-runs
  every effect that read any of its getters. Fine-grained per-field tracking is not implemented.
- **Whole-object, last-write-wins.** No field-level merging. Two concurrent unlocked editors race and
  the later write wins; serialize them with `LiveMutex` where that matters.
- **You can lock only an object the server sent you.** A `LiveMutex` request naming an object this
  browser was never sent is refused straight away, with a message saying so. Being sent an object is
  what earns the right to lock it; knowing its handle is not. No sign-in is required, because
  applications with no login use locking too; a deployment that has logins can additionally require
  one with `zeroz.livemutex.requireAuthentication=true`.
- **A lock is waited for at most 30 seconds.** Then the call fails with a message naming the wait,
  and nothing is changed. `zeroz.livemutex.waitSeconds` moves it. Callers are served in arrival
  order.
- **The lock table only holds locks in use.** An object has an entry while somebody holds its lock or
  is waiting for it, and the entry goes the moment the last of them leaves. So the table is bounded
  by how many locks are actually in use right now, not by how many object names have ever been
  presented. There is no separate ceiling and no expiry: a lock held for ever by a live session stays
  held, which is what a lock is for.
- **No tracked collections.** Setters are the tracking boundary; in-place collection edits are
  invisible. Reassign through the setter or call `LiveMutationTracker.touch(obj)`.
- **`notifyChanged` requires a prior send.** It throws `IllegalStateException` unless the object
  already has an `ObjectMapper` handle, which it gets by being serialized to a client.
- **Rejections carry a reason.** The writer receives a corrective sync followed by a `0x15 REJECT`
  frame naming the model and the reason, and every rejection cause is logged server-side.
- **Mutations do not coalesce** in the current build — one frame per setter call. See the table above.
- **Handles are never evicted.** The `ObjectMapper` is application-scoped with no per-session
  partitioning and no eviction, so handles accumulate for the process lifetime.
- Only `@ClientWritable` classes accept client writes, and only objects the server has already synced
  can be mutated.

## Server events

- **Broadcast only.** No per-topic subscription filtering on the server; a session receives every
  frame published within its scope and filters by topic client-side.
- **Scoping is opt-in.** `publish(topic, payload)` reaches every connected session with no principal
  check. Use `publishToUser` or `publishToSession` for anything belonging to somebody.
- **Tenant scope requires a provider that reports a tenant.** `Scope.TENANT` filters on the tenant an
  `AuthenticationProvider` attached to the session; a session with no tenant never matches.
- **At most once.** A disconnected client misses events. No queueing, acknowledgement or redelivery.
- **No replay.** Late subscribers receive nothing.
- **Serialization failures throw to the caller.** The payload is checked once before the broadcast, so
  `publish` fails loudly instead of appearing to succeed while reaching nobody.

## Shared signals

- **`Signals.shared` is JVM-global.** The registry is static: one value per signal name for the whole
  server, across every user and tenant. That is the definition, not a gap — for one value per tenant,
  user, browser or session use `Signals.scoped(name, initialValue, scope)` instead
  (see [SIGNALS.md](../SIGNALS.md#scoped-signals-one-value-per-tenant-user-or-browser)).
- **Scoped signals hold every target's value for the process lifetime.** Targets are created on first
  use and never evicted, so a `Scope.CLIENT` signal in a long-running server accumulates one entry per
  browser that ever connected. Nothing pages them out or persists them across a restart.
- **A scoped signal's targets are not enumerable from a client**, and `knownTargets()` on the server
  reports only targets that have been touched — a target that has never been written is absent even
  though subscribing to it works and yields the initial value.
- **One default signal per payload type.** The default wire name is the payload's class name, so two
  unnamed declarations of the same type collide. A conflicting redeclaration now throws
  `IllegalStateException` rather than silently keeping the first; give signals explicit names.
- **Latest-wins only.** No history, no replay, and `equals`-equal consecutive values are dropped, so a
  rapidly changing signal can skip intermediate states.
- **Whole-value replacement.** No per-field merging on client writes.
- **Empty `writeRoles` means anyone may write**, anonymous sessions included.
- **Serialization failures throw to the caller** — the value is checked once before the broadcast.
- **A rejected client write is logged nowhere.** The writer is snapped back with no server-side record.
  (Shared-signal writes still lack the reason frame that LiveSync mutations now get.)
- Validation on a client write checks the top-level value only — it does not recurse into fields or
  collection elements.

## Signals in general

- **Treat the signal graph as single-threaded.** `ValueSignal` synchronizes its own reads, writes and
  listener notification, but `Computed` has no synchronization at all, so the graph as a whole is not
  thread-safe even though one type in it is.
- **`KeyedList` discards its `Disposable`.** Its effect cannot be released and lives as long as the
  upstream signal. `bindText` and `bindValue` now return theirs.
- **`bindValue` requires a writable signal.** Passing a `Computed` throws rather than silently
  degrading to one-way; use `bindValueReadOnly` when a one-way binding is what you want.

## Binder

- **No converters.** `Binder` binds a field's value type directly to the bean property type; there is no
  `withConverter`, so a `String` field cannot be bound to an `int` property. Use a field whose type
  matches, or convert in the getter/setter pair you pass to `bind`.
- **One validation message per field** is surfaced at a time — the first violation wins.
- **No `setReadOnly`** on the binder or its bindings, and no validation-status handler hook.
- A custom `HasValue` implementation must implement `addValueChangeListener` and
  `removeValueChangeListener` or `Binder` throws `UnsupportedOperationException` when binding it. This
  is deliberate; fields extending `AbstractField` already satisfy it.
- A `Computed` still returns its last value after `dispose()`.

## Persistence

- **Transactions and rollback are available** since 0.4.0, because the store runs on ZeroZ DB.
  Send a `DbCommand` through the injected `ZeroZDbNode`: everything it enlists commits atomically,
  and a command that throws persists nothing and restores the objects it touched. The raw
  `EmbeddedStorageManager` remains available where the data is local, where `storeAll(...)` groups
  a write but cannot undo one.
- **Each `store()` call is its own commit.** Two calls where one was meant is the most common
  data-loss bug: a crash between them persists the first and loses the second. Use `storeAll`.
- **No conflict detection at the storage layer.** Two writers changing the same object: the later write
  wins, silently.
- **Saving is manual.** The framework never writes for you, including after a `@ClientWritable` edit
  from a browser — implement `LiveMutationListener` or the edit is lost on restart.
- **No query language.** Reads are plain Java over the in-memory graph, so lookups are linear scans
  unless the application keeps its own index.
- **Uncommitted state is visible.** A change is in the object graph as soon as you make it, whether or
  not it has been saved, so another request can read it.

## Serialization

**Supported:**

- Primitives and their wrappers: `int`, `long`, `double`, `float`, `boolean`, `short`, `byte`, `char`
- `String`, `UUID`, enums
- `BigDecimal` and `BigInteger` — carried as their exact `toString()` form, so scale and precision
  survive; safe for monetary amounts
- `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Duration`
- `Optional` — empty and present both round-trip
- Collections: `List`, `Set`, `Map`
- Arrays: `byte[]`, `int[]`, `long[]`, `double[]`, `float[]`, `short[]`, `char[]`, `boolean[]`
- `@DataModel` classes, including cycles and shared references

- EclipseStore `Lazy<T>` fields — see below

**Not supported:** object arrays (`String[]`, `MyModel[]` — use a `List`), `ZonedDateTime`,
`OffsetDateTime`, `ZoneId`, `Period`, `java.util.Date`, `java.sql.*`.

Since 0.4.0 the annotation processor **rejects an unsupported `@DataModel` field type at compile
time**, naming the replacement, so the mistake no longer waits until runtime. The check is a
blocklist of types known to break, not an allowlist: a field typed `Object`, an interface or an
abstract class still compiles, because serialization dispatches on the runtime type.

### Lazy references

A `@DataModel` may declare EclipseStore `Lazy<T>` fields. The reference travels as a **session-scoped
handle and never as its contents**, so a deferred subgraph stays deferred across the network:

```java
@DataModel
public class Order {
    private String id;
    private Lazy<List<OrderLine>> lines;   // handle on the wire
}
```

```java
// Client — suspends on the round trip, then caches. Reading again is free.
for (OrderLine line : order.getLines().get()) { ... }
```

The server holds a real `Lazy.Default` backed by storage; the client holds a `ClientLazy` backed by an
RMI call. Both satisfy the same interface, so your model is unchanged. TeaVM links only the `Lazy`
interface and eliminates every EclipseStore implementation behind it — no storage class reaches the
browser bundle.

Limits and rules:

- **Handles are bound to the session they were sent to.** Another session presenting the same handle
  is refused, because a handle is a capability to read a subgraph.
- **Handles are released when the session closes**, unlike `ObjectMapper` entries.
- **Lazy references originate on the server.** A client cannot create one and send it up; assign the
  resolved value instead.
- **The client and server must agree on the EclipseStore version** — the `Lazy` interface changed
  shape between major versions, and a mismatch shows up as an obscure `cannot access UsageMarkable`
  compile error. Both sides take the version from the `eclipsestore.version` property.
- **`isStored()` is always true on the client**, and `lastTouched()` returns 0; usage marks drive
  server-side cache eviction and are inert in the browser.
- Resolving a lazy field is a round trip. It is not automatically batched with anything else, so
  resolving many in a loop makes many calls.

**Declare collection fields as the interface type** — `Set`, `List`, `Map` — not as `TreeSet`,
`LinkedList` or `TreeMap`. Collections are rebuilt on the receiving side as `LinkedHashSet`,
`ArrayList` and `LinkedHashMap`, so a field declared as a concrete type outside that hierarchy fails
with a `ClassCastException` on deserialization. A `TreeSet` is written in its sorted order and arrives
ordered but without its `Comparator`, so later insertions are not re-sorted.

`UUID` is carried as its canonical string form rather than two longs, because TeaVM does not emulate
`UUID.getMostSignificantBits()`.

## Validation

- Server-side RMI argument validation recurses into `List` elements but **not** into `Map` values or
  nested object fields.
- Client-side validation is user feedback, never a security boundary. The server re-validates
  independently.

## Compile-time warnings that are not errors

The annotation processor warns rather than fails for two footguns. Read your build output.

- A `@ClientWritable` field **with no setter** is skipped — its mutations are not tracked.
- `@ClientWritable` **without `@LiveSync`** means mutations travel up but no state comes back down.

## Compilation target

The client is written entirely in Java and compiled ahead-of-time by TeaVM. TeaVM has two backends,
JavaScript and WasmGC, and **ZeroZ Stack uses the JavaScript backend today**. Every example client module
sets `<targetType>JAVASCRIPT</targetType>`, and no module in the repository sets `WEBASSEMBLY`.

**This is a deliberate interim choice, not a stale setting.** TeaVM's WasmGC backend does not yet
provide functionality ZeroZ Stack depends on. WasmGC remains the intended destination — hence the
`zerozstack-client` module name — and the project will move to it once TeaVM's support is complete.

No application code changes with the backend: you write the same Java either way. The distinction
matters only when describing what the build emits, so don't state that client code compiles to
WebAssembly today.

## Client environment

- Only TeaVM-supported JDK APIs are available in client modules.
- **Client code runs on a cooperative single-threaded scheduler.** `java.lang.Thread` exists and is
  what TeaVM calls a green thread — starting one re-enters TeaVM's scheduler and is the documented
  way to reach a context where a call may suspend. It buys **no parallelism**: nothing runs at the
  same time as anything else, and code that assumes real concurrency is wrong here.
- **A suspending call cannot start on a stack that began in native JavaScript.** An RMI call inside a
  DOM event handler, a `setTimeout` callback, or a WebSocket frame handler fails with "suspension
  point reached from non-threading context". The router hits this on every navigation and handles it
  by running each navigation on a green thread; application code fetching from such a callback must
  do the same.

## Routing

- **Loaders run in sequence, not in parallel.** A layout's loader and its child's cannot overlap,
  because of the single-threaded scheduler above. The guarantee routing gives is ordering — data
  before render, shared data fetched once in a layout — not concurrency.
- **The whole chain is rebuilt on every navigation.** A layout is not kept mounted while its children
  swap, so moving between two children of the same layout re-runs that layout's loader and rebuilds
  its components.
- **One child per layout.** Sibling outlets are not modelled.
- **No wildcard or optional segments.** Patterns are literal segments and `:params` with a fixed
  count; `/files/*path` is not supported.
- **No lazy loading, transitions or scroll restoration.** Everything is in one bundle and the
  container's contents are replaced outright.
- **Route guards are client-side only.** `@RequiresRole` decides what to show; the server re-checks
  every call, and that is what protects data.

## PWA

- **Installing does not make an application work offline, and is not intended to.** Every view loads
  its data over the WebSocket, signals get their retained values from the server on subscribe, and
  LiveSync objects live server-side. There is no client-side store, so with no connection there is
  nothing to render. Opened offline, an application shows `/zeroz4j-offline.html` and stops there.
  This is a property of the architecture. Do not read the presence of a service worker as a promise
  of offline operation.
- **The service worker caches the shell only** — the client bundle and the offline page. No data,
  and no application assets beyond what a page happens to request.
- **Its caching strategy is fixed.** Navigations are network-first, same-origin assets are
  cache-first, `/wasm-rmi` and cross-origin requests are never intercepted. An application needing
  different behaviour registers its own worker with `Pwa.install(path)` and takes on the
  cache-invalidation problem the shipped one solves.
- **No background sync and no queued writes.** An action taken with no connection is lost, not
  replayed later.
- **Push delivery is not implemented.** The framework collects a subscription; posting to it needs a
  signed VAPID JWT and RFC 8291 payload encryption, which is a library's job. Subscription lifecycle
  — deleting one after a 404 or 410 from the push service — is the application's.
- **No icon generation.** Applications supply their own PNGs at the sizes browsers want, including a
  maskable one.
- **Installation and push need a secure origin.** `http://localhost` counts; any other host needs
  HTTPS, and browsers offer neither without it.

## Deployment and transport

- **Messages are whole, never partial.** `@OnMessage` takes a complete `ByteBuffer`; there is no
  partial-message handling and no chunking. A message larger than the limit does not raise an error
  and never reaches framework code — it closes the socket. There is nothing to catch and nothing sent
  back. The client reconnects by itself, so the symptom is a connection that drops whenever one
  particular call is made.
- **Messages are capped at 4 MB by default.** `zeroz.ws.maxBinaryMessageBytes` sets the largest
  binary message the endpoint accepts; unset, the framework applies 4,194,304 bytes, the same default
  gRPC uses. An explicit setting wins in either direction. The limit in force is logged once at
  startup, naming the property. Raise it if a real response needs more, or return less — page the
  results, or return identifiers and fetch details on demand.
- **The RMI connection is not an upload channel.** It carries the messages an application exchanges,
  not documents, images or video. A file over the limit closes the connection. File upload is a
  separate feature.
- **The idle timeout is still the container's.** `zeroz.ws.idleTimeoutMinutes` is unset by default,
  so without setting it an abandoned browser tab holds a session and its server-side resources for as
  long as the container allows.
- **Wire lengths are checked before anything is allocated.** Every length and element count in the
  binary format is a number the sender chose. Each one is now compared against the bytes actually
  present, at the width of the element it describes, before an array or a collection is created, and
  a negative one is refused with a message rather than escaping as a `NegativeArraySizeException`.
  Nesting is capped at 256 levels. A malformed or hostile message therefore fails fast instead of
  reserving memory or overflowing the stack. Applications see this only as a clearer exception on a
  corrupt stream.
- **Container-managed threads are platform threads.** A Jakarta EE 10 `ManagedThreadFactory` cannot
  produce virtual threads, so a WAR deployment supplying one through
  `SessionThreadFactoryProvider` trades cheap threads for the container's naming, transaction and
  security context. Without such a provider, RMI calls run on framework-created virtual threads that
  carry none of that, and a `java:comp/env/…` lookup inside a service fails.
- **The framework does not verify what a container's factory carries.** Its contract is only that
  calls are dispatched on threads the supplied factory produced; whether those threads have the
  container's context is the container's contract, and worth an integration test in the application.
- **`zerozstack-server-jaxrs` is a catch-all at `/`.** Do not add it to a WAR that has its own
  servlets. `zerozstack-server-core` carries no JAX-RS or servlet type at all, which is what makes it
  safe inside somebody else's deployment.
- **`Zeroz4jShellServlet` is not auto-mapped.** Deliberately: mapping it at `/` from inside the
  framework would reintroduce the collision the module split exists to prevent. The deployment
  declares the mapping.
- **Mapped at `/`, the shell servlet replaces the container's default servlet.** Nothing else serves
  static files after that, so it serves them: the classpath under `/META-INF/resources/` first, then
  the WAR's own web content through the `ServletContext`. `WEB-INF` and `META-INF` are never served
  from the archive root. A file present in both places is served from the classpath.
- **A context path is handled, but only for what the framework owns.** The shell is served with a
  `<base href>` for the deployment's context path, and the router, `Pwa.install()` and
  `Zeroz4jClient.defaultWebSocketUrl()` all read the application's root from it. Anything an
  application writes with a leading slash — an `href`, a `fetch`, a redirect, a cookie `Path`, a
  hand-built WebSocket URL — still escapes the context path, and does so silently until deployed.
  Build those with `AppBase.location(...)` / `AppBase.url(...)`.
- **The `<base href>` is skipped when the shell already declares one**, and when it has no `<head>`.
  Both are deliberate, and both mean an application that does either owns the problem itself.

## Multi-tenancy

`README` describes multi-tenancy as available out of the box. Be precise about where it exists:

- **Storage** — isolated by `TenantResolver` and the EclipseStore `TenantStorageProvider`.
- **Server events and LiveSync** — isolated when published with `Scope.TENANT`, which requires an
  `AuthenticationProvider` that reports a tenant. `publish(topic, payload)` with no scope still
  reaches every connected session.
- **Signals** — `Signals.scoped(name, initialValue, Scope.TENANT)` holds one value per tenant.
  `Signals.shared(...)` is a single global value by definition and crosses every boundary.
- **Not isolated:** the `ObjectMapper` handle namespace is shared across tenants, and scoped signals
  keep every target's value in memory for the process lifetime with no eviction.

Nothing here is automatic: a tenant-scoped push is a scope you pass, and choosing `GLOBAL` — or
leaving the scope off — is what leaks.

## Examples

- **No example uses `@ClientWritable`.** The LiveSync up-direction is exercised only by
  `ServerLiveMutationTest` in `zerozstack-server-core`.
- `components-showcase` publishes to push topics that nothing subscribes to, using the low-level
  `broadcastPush(String, Object)` rather than a typed `EventTopic`. Do not copy that pattern.

## Documentation

Several documents predating 0.4.0 contain stale API claims and are being rewritten. Where a document
and the source disagree, the source is correct — please open an issue. `docs/GETTING_STARTED.md`,
`docs/CODE_WALKTHROUGH.md`, `docs/ARCHITECTURE.md` and `docs/CONCEPTS.md` carry warning banners naming
their specific known errors.
