# Changelog

All notable changes to ZeroZ4j are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) — with the pre-1.0 caveat that breaking
changes may land in a minor version while the design settles.

ZeroZ4j is an experimental proof-of-concept. Read each release's **Breaking** section before
upgrading.

## [0.4.0] — Unreleased

The last release was `v0.2.0`. **Version 0.3.0 was never tagged**, so its changes — UUID, `Instant`
and enum wire support, and per-module serializer registrars — ship as part of this release. If you are
upgrading from `v0.2.0`, everything below applies.

This release makes the framework fail loudly. Most of the changes below exist because a mistake used
to produce *nothing happening*: no exception, no log line, no clue.

### Breaking

- **Renamed to ZeroZ Stack.** Every module is now `zerozstack-*` instead of `zeroz4j-*`
  (`zerozstack-server-core`, `zerozstack-client`, `zerozstack-shared-api`,
  `zerozstack-ui-components`, `zerozstack-store-eclipsestore`, `zerozstack-apt`, `zerozstack-bom`,
  `zerozstack-archetype`). The groupId stays `com.zeroz4j`, and Java packages are unchanged.
  `zeroz4j` is now the family name only — the umbrella over this framework and
  [ZeroZ DB](https://github.com/ZeroZ4j/zerozdb) — so no product carries it and neither reads as
  the other's module. The repository moved to `github.com/ZeroZ4j/zerozstack`.

### Added

- **Persistence runs on ZeroZ DB**, bringing transactions, indexes, constraints and an optional
  network server. Inject `ZeroZDbNode` and send `DbCommand`/`DbQuery`: everything a command
  enlists commits atomically, and a command that throws persists nothing and restores the objects
  it touched in memory.
- **`zeroz4j.store.mode` chooses where data lives** — `EMBEDDED` (default, unchanged behaviour),
  `AUTO_SERVER` (own the store if free, otherwise join whoever has it, take over if they die), or
  `CLIENT` (connect to a ZeroZ DB server, so instances hold no data and can be restarted or scaled
  freely). The same service code runs in all three, so this is a deployment decision rather than an
  application one. See [docs/store-modes.md](docs/store-modes.md).
- **`node.localReads()`** for heap-speed reads: the live graph when this process owns the store, a
  continuously refreshed replica when it does not.

- **`zerozstack-client-wasm` is renamed to `zerozstack-client`.** A module should not be named after its
  compilation backend; the new name stays correct after the eventual move to WasmGC. Update the
  artifactId in your client module. The Java package `com.zeroz4j.client` is unchanged.
- **`SyncEngine.SyncScope` is replaced by `com.zeroz4j.api.Scope`**, now shared by LiveSync and
  events. Replace `SyncEngine.SyncScope.USER` with `Scope.USER`.
- **`SyncEngine.notifyChanged` throws** when the object has never been serialized to a client, instead
  of returning silently. Return the object from an `@RmiService` method at least once first — that is
  what registers its handle.
- **An unserializable event or shared-signal payload throws to the caller.** It was previously caught
  per session and logged, so `publish()` and `set()` appeared to succeed while reaching nobody.
- **A conflicting shared-signal declaration throws.** Two declarations colliding on one wire name used
  to be resolved by silently keeping the first. Re-running an identical declaration is still
  idempotent. The default wire name is the payload's class name, so give signals explicit names when
  you need more than one per type.
- **`bindValue` requires a writable signal** and throws otherwise, instead of silently degrading to a
  one-way binding. Use the new `bindValueReadOnly(signal)` when one-way is what you want.
- **`bindText` and `bindValue` return a `Disposable`** instead of `void`. They previously discarded it,
  so the binding could never be released.
- **`HasValue.addValueChangeListener` throws by default** rather than doing nothing, and
  `removeValueChangeListener` is added. A no-op made `Binder.setBean` appear to work while never
  writing to the bean. Fields extending `AbstractField` are unaffected.
- **`Binder.readBean` releases the bean held by `setBean`.** Previously `setBean(a); readBean(b);` left
  edits silently writing into `a`.
- **Three annotation-processor conditions are now compile errors**, not warnings:
  `@ClientWritable` without `@LiveSync`; `@ClientWritable` on a field with no setter; and a
  `@DataModel` field whose type cannot be serialized.
- **A `_Live` subclass is generated for every `@LiveSync` model**, not only `@ClientWritable` ones.

### Added

- **LiveSync objects are reactive.** Reading a `@LiveSync` object's getter inside an `Effect` or
  `Computed` subscribes to it, and an inbound sync re-runs whatever read it. Models stay plain POJOs.
  Notification is per object; updates arriving in one frame are batched so effects run against a fully
  applied graph. This removes the polling workaround previously required.
- **EclipseStore `Lazy<T>` fields on the wire.** A `@DataModel` may declare `Lazy<T>`; the reference
  travels as a session-scoped handle and never as its contents, and the client resolves it with a
  suspending RMI round trip on first `get()`. Handles are bound to the session they were disclosed to
  and released when it closes. No EclipseStore implementation class reaches the browser bundle.
- **An authentication SPI.** Implement `AuthenticationProvider`, register it through
  `META-INF/services`, and the development fallback is gone. It reports a name, roles and a **tenant**,
  receives query parameters, headers and any container principal, and can decline or refuse. Two
  registered providers is a startup error rather than an arbitrary choice.
- **`Scope.TENANT`.** Tenant-scoped events and LiveSync pushes, filtered on the tenant the provider
  attached to the session. A session with no tenant never matches, so tenant data cannot reach an
  unauthenticated connection by default.
- **`RmiRequestContext.getTenantId()`**, alongside the existing principal, roles and session id.
- **Scoped event publishing.** `publishToUser(topic, payload, principalName)` and
  `publishToSession(topic, payload, sessionId)`, plus `publish(topic, payload, scope, target)`.
  Previously every event reached every connected session with no principal check.
- **Rejected LiveSync mutations report a reason.** The writer receives a `0x15 REJECT` frame naming
  the model and the cause, alongside the corrective sync.
- **Serializer support for 16 more types** (tags `0x12`–`0x21`): `Set`, `BigDecimal`, `BigInteger`,
  `LocalDate`, `LocalTime`, `LocalDateTime`, `Duration`, `Optional`, all primitive arrays, and
  EclipseStore `Lazy`. `BigDecimal` travels as its exact `toString()` form, so scale and precision
  survive — safe for monetary amounts.
- **`Binder` gains `refreshFields()`, `hasChanges()` and `withRule(...)`**, the last letting
  constraints declared once on a `@DataModel` be reused in a `Binder` instead of restated.
- **`zerozstack-bom` manages the TeaVM artifacts**, so a client module cannot drift from the TeaVM the
  framework was built against.
- **A structured documentation pack** organised on Diátaxis, published as a MkDocs site, with a
  `decide/` section for choosing between the five state-propagation mechanisms.
- **Agent-facing configuration**: `context7.json`, `AGENTS.md` and `llms.txt`.

### Fixed

- **The test suite was red on `main`.** `RmiAnnotationProcessorTest` still expected
  `BinaryPackableRegistrar` after it was renamed per module.
- **The Maven archetype produced a project that could neither build nor run.** Four defects: a
  `1.0.0-SNAPSHOT` version pin against published artifacts, a missing `teavm-classlib`, a `<resources>`
  block that dropped `logging.properties` and served no web assets, and no `target/libs`. The version
  default is now filtered from the reactor so it cannot drift again.
- **A failed role check on a LiveSync mutation logged nothing at all**, making an absent log entry
  indistinguishable from an accepted write.
- **`Binder.removeBinding` left its listener attached**, so an unbound field kept writing to the bean.
- **The EclipseStore version is a single property.** A client/server mismatch previously surfaced as an
  obscure `cannot access UsageMarkable` compile error.

### Documentation

- The README described client code as compiled to WebAssembly. Every client module targets TeaVM's
  **JavaScript** backend — a deliberate interim choice while WasmGC support matures — and this is now
  stated as such.
- `@Secured` and `@RolesAllowed` are read **only from the `@RmiService` interface**. Two samples placed
  them on the implementation, where they are silently ignored.
- Only four of the seven examples require signing in; the docs claimed all of them did.
- `docs/AGENT_PROMPTS.md` instructed agents to spawn `new Thread(...)` while forbidding it elsewhere in
  the same file.
- Documents predating this release carry banners naming their specific known errors.

### Known gaps

Stated plainly, and listed in full in [Limitations](docs/reference/limitations.md):

- **Shared signals cannot be scoped.** A shared signal is one value the whole server agrees on, so
  per-user state belongs in a scoped event or in LiveSync.
- **Identity is fixed for the life of a connection.** Roles are read once at handshake, so a user whose
  roles change must reconnect. There is no handshake origin check and no session expiry.
- **A rejected `sharedWritable` write is still logged nowhere** and sends no reason, unlike a rejected
  LiveSync mutation.
- **`ObjectMapper` handles are never evicted**, so they accumulate for the process lifetime. Lazy
  handles do evict on session close.
- **`@Route` has no router**, so there is no framework-provided navigation story.
- **No example exercises `@ClientWritable`**; the LiveSync up-direction is covered only by tests.

## [0.3.0] — Never released

Developed on `main` but never tagged. Included in 0.4.0.

### Added

- Wire support for `UUID`, `Instant` and enums.
- Per-module serializer registrars (`BinaryPackableRegistrar_<suffix>`), so two modules with
  `@DataModel` types no longer collide on one classpath.

## [0.2.0] — 2026-07-23

Shared signals, server events, validation and the LiveSync up-direction; the `job-monitor`,
`inventory-crud`, `chat-events`, `chat-livesync`, `form-signup` and `todo-signals` examples.

## [0.1.0]

Initial public proof-of-concept: binary RMI over WebSocket, `@DataModel` serialization, EclipseStore
persistence, and the TeaVM UI component library.

[0.4.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.2.0...main
[0.2.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ZeroZ4j/zerozstack/releases/tag/v0.1.0
