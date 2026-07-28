# AGENTS.md — working on or with ZeroZ4j

Instructions for AI coding agents. Humans should start at [README.md](README.md) and
[docs/](docs/).

ZeroZ4j is an experimental pure-Java full-stack framework at version **0.4.0**. The Java UI is
compiled by TeaVM to run in the browser, client and server talk over a binary WebSocket RPC protocol,
and the server persists a live object graph with EclipseStore. You write no JavaScript, JSON, REST
routes or SQL.

**Compilation target:** the build produces **JavaScript** via TeaVM's JavaScript backend
(`<targetType>JAVASCRIPT</targetType>`); no module sets `WEBASSEMBLY`. This is a deliberate interim
choice — TeaVM's WasmGC backend does not yet provide functionality ZeroZ4j needs — and WasmGC remains
the intended destination. Do not tell users their code compiles to Wasm today, and do not "fix" the
JavaScript target.

The client module is `zerozstack-client`. It was renamed from `zerozstack-client-wasm` so the name does not
encode a compilation backend; anything published earlier, and older documentation, uses the old
artifactId.

## Build and test

Requires **JDK 21** and Maven 3.9+.

```bash
mvn clean install -DskipTests   # from the repository root
mvn test                        # then tests
```

Use **`clean`**. Each example server copies its dependencies into `target/libs`, and that directory is
never pruned — without `clean`, jars from previous versions accumulate there and Weld reports
duplicate beans with "this may result in incorrect behavior" at startup.

Install before you test. The annotation processor and shared API must be in your local repository
before the modules that consume them can compile, so `mvn clean test` on its own is not a reliable
entry point.

A full clean build of the reactor, including every example, takes about a minute on a warm Maven
cache.

## Module map

| Module | Contains |
|---|---|
| `zerozstack-shared-api` | Annotations, signals, `EventTopic`, validation, serialization primitives. Compiled into **both** tiers. |
| `zerozstack-apt` | Annotation processor. Generates `_Serializer`, `_Rules`, `_Live`, `_Stub` and the SPI registrar. |
| `zerozstack-client` | TeaVM client runtime: `Zeroz4jClient`, `WasmRmiClient`, `ServerEvents`. Renamed from `zerozstack-client-wasm`. |
| `zerozstack-ui-components` | Java component library styled with Tailwind/DaisyUI. Components wrap a TeaVM `HTMLElement`, reachable via `getElement()`; there is no server-side DOM state. |
| `zerozstack-bom` | Dependency BOM — the intended way for applications to import versions. |
| `zerozstack-server-core` | CDI engine, RMI dispatcher, `SyncEngine`, `EventPublisher`, dev auth. |
| `zerozstack-server-helidon` | Helidon HTTP/WebSocket bindings. |
| `zerozstack-store-eclipsestore` | Object-graph persistence adapter, multi-tenant storage. |
| `zerozstack-archetype` | Maven archetype scaffolding a three-module application. |
| `zerozstack-examples` | Seven runnable reference applications. |

An application has three modules: **shared** (`@DataModel` classes, `@RmiService` interfaces),
**client** (the UI, compiled for the browser by TeaVM), **server** (`@ApplicationScoped`
implementations).

## Non-negotiable rules

1. **Every type crossing the wire is `@DataModel`** with a public no-arg constructor plus getters and
   setters. Without it, serialization throws at runtime.
2. **Do not wrap client event handlers in your own thread.** `Component.addDomEventListener` already
   wraps every DOM listener via `Component.threaded(...)`, so a handler body runs on a suspendable
   TeaVM green thread — which is why a suspending RMI call works directly inside it. An extra thread is
   redundant, and updates made from one may not repaint until the next UI event. For delayed work use
   `Window.setTimeout`.
3. **Client-side validation is feedback, never a boundary.** The server re-validates independently.
4. **Client writes are denied by default.** Opt in with `Signals.sharedWritable` or
   `@ClientWritable`.
5. **Dispose what you create.** `Effect.create` and `ServerEvents.on` return a `Disposable`;
   `Computed` has `dispose()`.
6. **Never mutate a signal's value in place.** `set()` skips notification when the new value `equals`
   the old one, so mutate-and-set-back changes nothing. Use `update()` and return a new instance.
7. **Bootstrap the client with `Zeroz4jClient.connect(url, onReady)`.** Do not call
   `BinaryPackableRegistrar` or `WasmRmiClient.initialize` yourself — registrars are discovered via
   `META-INF/services`.
8. **Get an RMI stub with `new MyService_Stub()`.** There is no `WasmRmiClient.create(Class)`.

## Choosing a state-propagation mechanism

This is the decision developers most often get wrong. Ask in order:

```
1. Does anything outside this browser tab need to know?
   No  -> local ValueSignal / Computed / Effect. Stop. (Most UI state lands here.)

2. Does the client need an answer, or is it invoking a named operation
   ("approve", "checkout", "log in")?
   Yes -> RMI call on an @RmiService. Stop.

3. Is the data private to one user or session?
   Yes -> scope the push, then continue to 4 to pick the mechanism.
          Events: publishToUser / publishToSession.
          LiveSync: Scope.SESSION or Scope.USER.
          Shared signals CANNOT be scoped, so per-user state is never one.
          The unscoped forms reach every session with no principal check.

4. Would keeping only the latest value lose information?
   Yes -> EventTopic (a discrete occurrence: a chat message, a log line).
   No  -> continue.

5. One value replaced wholesale, or an identified object edited field by field?
   One value        -> Signals.shared / sharedWritable
   Identified object -> @LiveSync (+ @ClientWritable for client edits)
```

Two rules of thumb: **state edits sync, operations call**, and **events are news, signals are
facts** — news is missed if you were not listening, facts are true when you arrive.

Full guide: [docs/decide/](docs/decide/).

## When nothing happens

| Symptom | Cause |
|---|---|
Most of these now throw. The ones that remain are the genuinely silent cases.

| Symptom | Cause |
|---|---|
| A LiveSync'd object updates but the UI does not | The getter was read outside an `Effect`, so nothing subscribed. Read it inside `Effect.create(...)`. |
| An `Effect` does not re-run | The value was mutated in place and `equals` deduplication swallowed it. Use `update()` with a new instance. |
| A LiveSync collection edit does nothing | Setters are the tracking boundary; `obj.getTags().add(x)` is invisible. Reassign, or call `LiveMutationTracker.touch(obj)`. |
| A rejected `sharedWritable` write reverts with no explanation | Shared-signal write rejections are still logged nowhere. LiveSync mutations do report a reason. |

These used to be silent and now fail loudly — expect an exception, not a mystery:
`notifyChanged` on an unsynced object, an unserializable event or signal payload, a conflicting
shared-signal declaration, `bindValue` with a non-writable signal, and — at compile time —
`@ClientWritable` without `@LiveSync` or on a field with no setter.

## Running the examples

All seven examples live under `zerozstack-examples/`. After `mvn clean install -DskipTests` from the root,
each has a `run.bat` (Windows). They serve on `http://localhost:8080`; run one at a time.

There is no executable jar and no `exec-maven-plugin` — `java -jar` and `mvn exec:java` both fail
regardless of what older docs say. The working invocation is the one `run.bat` uses:

```bash
cd zerozstack-examples/todo-signals/todo-signals-server
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer   # ';' on Windows, ':' on POSIX
```

Every example uses the same main class, `com.zeroz4j.example.server.ExampleServer`.

**Four of the seven require signing in:** `chat-events`, `chat-livesync`, `job-monitor` and
`components-showcase` set `zeroz.security.mode=dev` in their `ExampleServer.main` and show a client-side
`Login` component. `todo-signals`, `form-signup` and `inventory-crud` connect anonymously.

Dev credentials are `demo` / `demo` (role `user`) and `admin` / `admin` (roles `user`, `admin`). The
client passes them as WebSocket handshake parameters and `DevAuth` validates them. (`DevLoginServlet` is
a separate servlet-container path at `/dev-login`, not what the examples use.)

**To replace it:** implement `com.zeroz4j.server.AuthenticationProvider` and register it in
`META-INF/services/com.zeroz4j.server.AuthenticationProvider`. Discovery is via `ServiceLoader`, not
CDI, because the handshake runs before the endpoint exists. Return an `AuthenticatedPrincipal` with a
name, roles and optionally a tenant; return null to leave the connection anonymous. Registering a
provider disables the `DevAuth` fallback entirely.

| Example | Demonstrates |
|---|---|
| `todo-signals` | Local signals, `Computed`, `Effect` in isolation |
| `chat-events` | `EventTopic` / `EventPublisher` / `ServerEvents`, deliberately without signals |
| `chat-livesync` | `@LiveSync` down-direction driving an `Effect` directly |
| `job-monitor` | `Signals.shared` driven from a server-side virtual thread |
| `form-signup` | Validation annotations, generated `_Rules`, `Computed` form validity |
| `inventory-crud` | Master-detail CRUD, local signals, `Computed` KPIs |
| `components-showcase` | The component library gallery |

## Not implemented — do not generate code against these

- `@Route` exists as an annotation but **there is no router**. It has no usages and no registry.
- Protocol opcodes `0x11 SNAPSHOT`, `0x12 UNSUBSCRIBE`, `0x13 MUTATE`, `0x14 ACK`, `0x15 REJECT`,
  `0x16 SIGNAL_SUB` and `0x18 PUSH` are declared but unreferenced. There is no version field, no
  acknowledgement and no conflict rejection in the implemented sync path.
- LiveSync has no field-level merging and no version-conflict detection. Whole-object,
  last-write-wins.
- Tracked collections do not exist.
- Events have no per-topic server-side subscription filtering.
- No serialization support for object arrays (`String[]`, `MyModel[]` — use a `List`), or for
  `ZonedDateTime`, `OffsetDateTime`, `ZoneId`, `Period`, `java.util.Date`. Primitives, `String`,
  `UUID`, enums, `BigDecimal`, `BigInteger`, `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`,
  `Duration`, `Optional`, `List`, `Set`, `Map`, all primitive arrays and EclipseStore `Lazy<T>`
  **are** supported.
- A `Lazy<T>` field travels as a session-scoped handle, never its contents. The client resolves it
  with a suspending RMI round trip on first `get()`. Lazy references originate on the server; a client
  cannot create one and send it up.
- Declare collection fields as `List`, `Set`, `Map`, not as concrete types: they are rebuilt as
  `ArrayList`, `LinkedHashSet` and `LinkedHashMap`, so a field typed `TreeSet` fails with a
  `ClassCastException` on deserialization.

## Conventions

- Apache 2.0 licence header on every new `.java` file; copy an existing one.
- Javadoc on public API, including the wire opcode where a method sends a frame.
- Documentation lives in `/docs` as plain Markdown. See
  [docs/contribute/docs-style-guide.md](docs/contribute/docs-style-guide.md).
