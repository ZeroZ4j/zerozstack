# Limitations

Every known gap in ZeroZ4j 0.4.0, in one place. This page exists because surprises are what make
people abandon a framework, and because a coding agent that reads it will not generate code against
features that do not exist.

ZeroZ4j is an experimental proof-of-concept. Read this list as the honest boundary of the
demonstration, not as a roadmap commitment.

## Declared but not implemented

These exist in the source as annotations or constants and do nothing. Do not build on them.

| Item | Status |
|---|---|
| `@Route` | The annotation exists. **There is no router** — no registry, no resolution, no usages. There is no framework-provided navigation story; multi-view applications wire views by hand. |
| Protocol opcodes `0x11 SNAPSHOT`, `0x12 UNSUBSCRIBE`, `0x13 MUTATE`, `0x14 ACK`, `0x15 REJECT`, `0x16 SIGNAL_SUB`, `0x18 PUSH` | Declared and unreferenced. Reserved for future protocol work. |
| Versioned mutations, acknowledgement and conflict rejection | Reserved in the protocol. The implemented sync path has no version field, no ACK and no conflict detection. |
| `@RequiresRole` | A `@Target(TYPE)` guard intended for **client-side views**, checked by the router against `RmiSecurityContext.hasAnyRole`. Unimplemented for the same reason `@Route` is — there is no router. It is not a substitute for `@RolesAllowed`, which is a server-side RMI check; gate views by hand with `RmiSecurityContext.hasAnyRole(...)` and always enforce on the server. |
| Coalesced LiveSync mutations and UI-scheduler dispatch of inbound frames | Both are conditional on a `PlatformScheduler`, and `WasmRmiClient.setPlatformScheduler` is never called anywhere in the framework. So every setter sends its own mutation frame, and all inbound frames are applied inline on the WebSocket callback. |

## LiveSync

- **Change notification is per object, not per field.** Any inbound sync touching an instance re-runs
  every effect that read any of its getters. Fine-grained per-field tracking is not implemented.
- **Whole-object, last-write-wins.** No field-level merging. Two concurrent unlocked editors race and
  the later write wins; serialize them with `LiveMutex` where that matters.
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

- **JVM-global.** The registry is static: one value per signal name for the whole server, across every
  user and tenant. There is no per-session or per-tenant shared signal, and unlike events there is no
  scoped `set()` — a shared signal is by definition one value everyone agrees on. For per-user state
  use a scoped event or LiveSync with `Scope.USER`.
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
JavaScript and WasmGC, and **ZeroZ4j uses the JavaScript backend today**. Every example client module
sets `<targetType>JAVASCRIPT</targetType>`, and no module in the repository sets `WEBASSEMBLY`.

**This is a deliberate interim choice, not a stale setting.** TeaVM's WasmGC backend does not yet
provide functionality ZeroZ4j depends on. WasmGC remains the intended destination — hence the
`zerozstack-client` module name — and the project will move to it once TeaVM's support is complete.

No application code changes with the backend: you write the same Java either way. The distinction
matters only when describing what the build emits, so don't state that client code compiles to
WebAssembly today.

## Client environment

- Only TeaVM-supported JDK APIs are available in client modules.
- Client code runs on a cooperative single-threaded scheduler. Creating a `java.lang.Thread` in client
  code is unsupported.

## Multi-tenancy

`README` describes multi-tenancy as available out of the box. Be precise about where it exists:
tenant isolation is provided at the **storage layer** by `TenantResolver` and the EclipseStore
`TenantStorageProvider`. It does **not** extend to the propagation mechanisms — server events and
shared signals cross tenant boundaries, and the `ObjectMapper` handle namespace is shared across
tenants.

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
