# Limitations

Every known gap in ZeroZ Stack 0.8.0, in one place. This page exists because surprises are what make
people abandon a framework, and because a coding agent that reads it will not generate code against
features that do not exist.

ZeroZ Stack is an experimental proof-of-concept. Read this list as the honest boundary of the
demonstration, not as a roadmap commitment.

## Declared but not implemented

These exist in the source as annotations or constants and do nothing. Do not build on them.

| Item | Status |
|---|---|
| Protocol opcodes `0x11 SNAPSHOT`, `0x12 UNSUBSCRIBE`, `0x13 MUTATE`, `0x14 ACK`, `0x16 SIGNAL_SUB`, `0x18 PUSH` | Declared and unreferenced. Reserved for future protocol work. (`0x15 REJECT` left this list in 0.4.0 and is now sent for every refused live mutation.) |
| Versioned mutations, acknowledgment and conflict rejection | Reserved in the protocol. The implemented sync path has no version field, no ACK and no conflict detection. A refusal is reported, but nothing counts versions. |
| UI-scheduler dispatch of inbound frames | Conditional on a `PlatformScheduler`, and `WasmRmiClient.setPlatformScheduler` is never called anywhere in the framework, so all inbound frames are applied inline on the WebSocket callback. (Outgoing mutations no longer depend on it: since 0.8.0 they are coalesced by a real timer - see [LiveSync](../LIVESYNC.md).) |

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
- **A handle lasts exactly as long as the object.** Since 0.8.0 the handle registry holds what it
  names weakly, on both tiers, so an entry disappears once the application has let go of the object.
  There is no ceiling and no expiry, and none is needed. The consequence to know: a live object the
  server no longer holds cannot be re-synced, and answers a re-sync the same way a restarted server
  does — nothing comes back, the count is logged, the client re-fetches. Keep live objects in your
  store or a field. Before 0.8.0 nothing was ever removed and handles accumulated for the process
  lifetime, which filled a real application's heap.
- **Only a `@LiveSync` model and the objects inside one get a handle.** Everything else on the wire
  is a value with a name good for its own message only. So an ordinary value returned from a service
  method cannot be synced, mutated, locked or re-read by handle — and a client that sends one back up
  as a call argument now hands over a copy, where before 0.8.0 it reached into the server's own
  instance and overwrote it.
- **A re-sync request carries at most 10,000 handles.** A client holding more throws its list away,
  logs one line, and lets the application re-fetch. The number matches the server's own per-browser
  record, which holds no more than that either.
- Only `@ClientWritable` classes accept client writes, and only objects the server has already synced
  can be mutated.
- **A refusal is a sentence, not an exception.** The server sends back both the corrected state and
  the reason; the client applies the state and hands the reason to any
  `LiveMutationRefusals.onRefused(...)` listener. With no listener it goes to the browser console.
  Nothing is thrown, because the edit was already over by the time the answer arrived.
- **An edit waits before it travels, and leaving the page loses what is still waiting (0.8.0+).**
  A change is sent after the changes stop for 150 ms, or after 1000 ms whatever happens - whichever
  comes first (`LiveMutations.configure`). So a burst of typing costs a handful of messages instead
  of one per character. What it also means: somebody who closes the tab or follows a link mid-burst
  loses up to a second of typing. There is deliberately no flush when the page is left, because a
  browser will not reliably put bytes on a WebSocket while it is taking the page apart, and a rescue
  that works half the time is worse than none. Lower the ceiling for a screen where a second
  matters.
- **One connection's messages are handled in the order they were sent (0.8.0+).** Anything a client
  sends after an edit - a service call, a lock, a signal write - goes on the wire behind that edit,
  and the server handles it after that edit. A service method whose correctness depends on an edit
  made a moment earlier is safe without a `LiveMutex`. Two exceptions, both deliberate: the keepalive
  ping is answered straight away rather than queued, and a lock request that is waiting for somebody
  else lets the messages behind it past, because it has changed nothing yet. Before 0.8.0 the server
  handled up to 32 messages from one connection at once and this was a real hazard.
- **The server's own broadcast will fight a text field, if you let it.** An accepted edit is sent
  back to everybody including its author, carrying the value the server had a moment ago. An
  `Effect` that copies that into the field somebody is typing in deletes what they typed since.
  Follow the incoming value everywhere except the field that has the keyboard; see the pattern in
  [LiveSync](../LIVESYNC.md).
- **An edit that cannot be sent is announced, not retried.** If a change cannot be put on the wire,
  the client asks the server to re-send that object — which puts the screen back to the truth — and
  reports it to `LiveMutationRefusals`. It is not queued and it is not tried again. (An edit made
  while the connection is *down* is a different thing, and that one is kept and sent on reconnect.)

## Server events

- **Broadcast only.** No per-topic subscription filtering on the server; a session receives every
  frame published within its scope and filters by topic client-side.
- **Scoping is opt-in.** `publish(topic, payload)` reaches every connected session with no principal
  check. Use `publishToUser` or `publishToSession` for anything belonging to somebody.
- **Tenant scope requires a provider that reports a tenant.** `Scope.TENANT` filters on the tenant an
  `AuthenticationProvider` attached to the session; a session with no tenant never matches.
- **At most once.** A disconnected client misses events. No queueing, acknowledgment or redelivery.
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
- **`KeyedList` has to be disposed by you.** Since 0.8.0 it implements `Disposable`, so its effect
  can be released — but nothing releases it for you. Keep the object `new KeyedList<>(...)` gives
  back and call `dispose()` on it when the screen leaves, normally from `onDetach`. Before 0.8.0 it
  handed out nothing to stop it with, so every one ever built kept watching its signal for ever.
  `bindText` and `bindValue` return their own `Disposable` the same way.
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
- `@DataModel` **records** (0.8.0+) — but a record cannot take part in a reference cycle, cannot be
  `@LiveSync` or `@ClientWritable`, and cannot be a persistence root
- `@DataModel` **sealed interfaces and sealed abstract classes** (0.8.0+) — a field, list element,
  argument or return value declared as the sealed base arrives as the real member type. Every
  permitted type must itself be `@DataModel` and `final`, and must not itself be sealed
- **Fields inherited from a base `@DataModel`** (0.8.0+). Before that they were dropped silently.
  Extending a non-`@DataModel` class that has fields of its own, and shadowing a base field name,
  are both refused when you compile

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
- Client-side validation is user feedback. It decides nothing; the server re-validates
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

## User interface

New in 0.8.0: every control in `zerozstack-ui-components` can be reached with Tab, pressed with
Enter, and says what it is, and `KeyboardAndNamingContractTest` fails the build when a new one
cannot. That is a floor, not a guarantee of a usable screen.

- **The build check cannot tell you whether a name is a good name.** It sees that a control has one.
  "Button" and "Delete this invoice permanently" both pass.
- **It says nothing about color contrast, focus rings, motion, or whether the tab order makes
  sense.** A control can satisfy every rule the check knows and still be unreadable on the theme it
  ships with, invisible when focused, or reached tenth when it should be reached first. Those need
  eyes, and this project has no automated answer to any of them.
- **It reads source code, not a running page.** It works out which element each listener was put on
  and what tag that element is. A keydown handler that answers Enter by doing nothing satisfies it.
- **The real browser harness is not part of the build.** `tools/ui-proof` is where key presses are
  actually sent and the page is actually asked what it says — the only place a question like "is
  this error message part of the text a person can read" gets a true answer. It compiles the library
  to JavaScript, takes about a minute, needs a Chrome on the machine, and is run by hand. Nothing
  runs it for you, so a change that breaks it can be merged without anybody noticing until somebody
  runs it.
- **No accessibility rule is enforced in your application.** Every check described here reads this
  repository's own source. A screen you write in your own project is checked by nothing.
- **`replaceContents` is not enforced outside this repository either.** The check that fails a build
  for emptying a container by hand reads the files in this checkout. In your application, writing
  `getElement().setInnerHTML("")` still silently leaves the old screen's timers and effects running.
- **A dialog beats every layer, and only a dialog can cover a dialog.** `Layer` orders everything
  drawn on the page, but an open modal `Dialog` sits in a place of the browser's own above the whole
  page that no stacking number reaches. Inside that place things stack in the order they arrived, so
  a dialog opened after the framework's "connection lost" bar is drawn over it.
- **On a browser older than Chrome 114, Safari 17 or Firefox 125 the "connection lost" bar has
  nowhere to go**, and behaves as it did before 0.8.0: visible everywhere except under a dialog.
- **A tooltip is drawn on the side it was told to sit on.** One placed against the right-hand edge of
  the window is still partly off the window. It no longer takes the whole page sideways, which is
  what it did before 0.8.0.

## Routing

- **Loaders run in sequence, not in parallel.** A layout's loader and its child's cannot overlap,
  because of the single-threaded scheduler above. The guarantee routing gives is ordering — data
  before render, shared data fetched once in a layout — not concurrency.
- **The whole chain is rebuilt on every navigation.** A layout is not kept mounted while its children
  swap, so moving between two children of the same layout re-runs that layout's loader and rebuilds
  its components.
- **One child per layout.** Sibling outlets are not modeled.
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
  different behavior registers its own worker with `Pwa.install(path)` and takes on the
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

## File uploads

- **One handler per application.** If more than one `FileUploadHandler` is deployed, the framework
  uses one of them and logs a warning naming them all.
- **No resume and no chunking.** A file arrives in one request. An upload that was canceled, or
  whose connection dropped, starts again from the beginning next time.
- **No per-user, per-tenant or per-day quota, and no limit on how many files may be uploaded at
  once.** `zeroz.upload.maxBytes` is 25 MB and applies to one file. Anything else — who may upload,
  how often, how much in total — the application decides for itself from `getPrincipal()`,
  `getRoles()` and `getTenantId()`.
- **No virus scanning and no content checking.** The framework never looks inside the file.
  `getContentType()` is the browser's guess from the file extension, and `getFileName()` is whatever
  the browser reported; both are text, and neither is used for anything by the framework.
- **The temporary file is deleted the moment the handler returns**, whether it returned a result or
  threw. Move or copy it inside the method; keeping the `Path` and reading it later finds nothing.
- **The handler runs on the upload request's thread**, so a slow handler holds that request open.
  Hand long work to a background thread and return quickly.
- **An upload needs a live connection.** The page asks its existing WebSocket for a one-time pass,
  valid for 60 seconds (`zeroz.upload.passSeconds`), usable once, and only from the browser it was
  issued to. There is no way to upload without a connection open — no API key, no signed URL.
- **A file that did not arrive whole never reaches the handler.** A canceled upload, or one whose
  connection dropped, is answered "That file did not finish sending. Please try again." and the
  part-received file is deleted.

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
- **A connection that stops reading is closed, not waited for.** The server can only send as fast as
  the browser accepts, so messages for a browser that has stopped accepting are held in a queue for
  that one connection. The queue holds 256 messages or 8 MB, whichever comes first
  (`zeroz.ws.maxPendingFramesPerSession`, `zeroz.ws.maxPendingBytesPerSession`); an empty queue
  always accepts the next message however large it is, so a single big response is never refused.
  Past that the
  connection is closed with WebSocket code `1013`, and the log names the limit that was hit. Such a
  browser has already missed messages it will never see, so it has to reconnect and fetch a fresh
  copy either way; holding more would let one browser use up the server's memory. Nothing else waits
  for it: each connection has its own queue and its own thread, so a stalled browser delays only its
  own messages, never another browser's and never a broadcast.
- **Wire lengths are checked before anything is allocated.** Every length and element count in the
  binary format is a number the sender chose. Each one is compared against the bytes actually
  present, at the width of the element it describes, before an array or a collection is created, and
  a negative one is refused with a readable message rather than escaping as a
  `NegativeArraySizeException`. Collections grow as items arrive rather than being sized from a
  claimed count, and nesting is capped at 256 levels. A damaged message therefore fails at once with
  an explanation instead of reserving memory or running out of stack. Applications see this only as
  a clearer exception on a corrupt stream.
- **Container-managed threads are platform threads.** A Jakarta EE 10 `ManagedThreadFactory` cannot
  produce virtual threads, so a WAR deployment supplying one through
  `SessionThreadFactoryProvider` trades cheap threads for the container's context — naming,
  transactions and the caller's identity. Without such a provider, RMI calls run on
  framework-created virtual threads that carry none of that, and a `java:comp/env/…` lookup inside a
  service fails.
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
- **Closing a connection does not wait for the message it interrupted.** Everything the connection
  had queued is thrown away and the message being handled is interrupted, but the close returns
  straight away rather than waiting for that thread to stop. So a service method can still be
  running for a moment after the connection it belongs to is gone, and a shutdown that closes
  connections can reach the end of the container's own teardown while one is still finishing. The
  framework no longer lets that thread die noisily, and a call that fails is still answered on its
  own; what an application must not assume is that nothing of a connection's is running once it has
  been told the connection closed. Waiting instead is not an option: a message may legitimately be
  waiting up to thirty seconds for a lock, and the close runs on the thread the container reads
  every connection with.

## Multi-tenancy

`README` describes multi-tenancy as available out of the box. Be precise about where it exists:

- **Storage** — isolated by `TenantResolver` and the EclipseStore `TenantStorageProvider`.
- **Server events and LiveSync** — isolated when published with `Scope.TENANT`, which requires an
  `AuthenticationProvider` that reports a tenant. `publish(topic, payload)` with no scope still
  reaches every connected session.
- **Signals** — `Signals.scoped(name, initialValue, Scope.TENANT)` holds one value per tenant.
  `Signals.shared(...)` is a single global value by definition and crosses every boundary.
- **Not isolated:** the `ObjectMapper` handle namespace is shared across tenants, and scoped signals
  keep every target's value in memory for the process lifetime with no eviction. The handle namespace
  being shared is not a leak — a handle is a random 36-character identifier and being sent an object
  is what earns the right to ask for it back — but it is one namespace, not one per tenant.

Nothing here is automatic: a tenant-scoped push is a scope you pass. `GLOBAL` — which is also what
you get by leaving the scope off — sends to every connected session, whichever tenant it belongs to.

## Examples

- **One example uses `@ClientWritable`** — the topic box in `chat-livesync`, which is the only place
  the up direction of LiveSync is shown end to end. It was added in 0.8.0, along with the fix for the
  up direction being broken.
- `components-showcase` publishes to push topics that nothing subscribes to, using the low-level
  `broadcastPush(String, Object)` rather than a typed `EventTopic`. Do not copy that pattern.

## Documentation

Several documents predating 0.4.0 contain stale API claims and are being rewritten. Where a document
and the source disagree, the source is correct — please open an issue. `docs/GETTING_STARTED.md`,
`docs/CODE_WALKTHROUGH.md`, `docs/ARCHITECTURE.md` and `docs/CONCEPTS.md` carry warning banners naming
their specific known errors.
