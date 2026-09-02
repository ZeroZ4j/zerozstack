# AGENTS.md — working on or with ZeroZ Stack

Instructions for AI coding agents. Humans should start at [README.md](README.md) and
[docs/](docs/).

ZeroZ Stack is an experimental pure-Java full-stack framework at version **0.9.0**. The
Java UI is compiled by TeaVM to run in the browser, client and server talk over a binary WebSocket
RPC protocol, and the server persists a live object graph with EclipseStore. You write no
JavaScript, JSON, REST routes or SQL.

**Compilation target:** the build produces **JavaScript** via TeaVM's JavaScript backend
(`<targetType>JAVASCRIPT</targetType>`); no module sets `WEBASSEMBLY`. This is a deliberate interim
choice — TeaVM's WasmGC backend does not yet provide functionality ZeroZ Stack needs — and WasmGC remains
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

Prefer **`clean`**. Each example server copies its dependencies into `target/libs`; since 0.4.1
that directory is emptied before it is refilled, so a plain `install` no longer leaves jars from
previous versions behind (which used to kill startup with "WELD-001409: Ambiguous dependencies").
`clean` remains the honest choice before a release, and the purge tolerates a jar held open by a
running server rather than failing the build.

Install before you test. The annotation processor and shared API must be in your local repository
before the modules that consume them can compile, so `mvn clean test` on its own is not a reliable
entry point.

A full clean build of the reactor, including every example, takes about a minute on a warm Maven
cache. Compiling the twelve example clients for the browser is roughly **45 seconds** of that — the
single largest item, and it is paid on every build of the reactor because the compiler has no
usable incremental mode. There is no flag here that skips it; if you are only changing server-side
code, build the modules you touched with `-pl <module> -am` rather than the whole reactor.

### An application's build has two shapes; this repository's has one

A project generated from the archetype builds its browser bundle at **`prepare-package`**, so
`mvn compile` and `mvn test-compile` are `javac` and nothing else, and it builds that bundle
**unoptimized and unminified by default**. `-Pproduction` is what turns on whole-program
optimization and minification. That is the split an assistant working in a generated application
has to understand: the quick check does not compile the user interface at all, a full
`mvn install` does, and only `-Pproduction` produces the shape a user receives.

This repository's own examples do not have that split. They compile at `process-classes` with
`minifying=false` hardcoded, and that is deliberate: the browser proof page and the release gate
read names out of the generated JavaScript. It does mean **no example in this repository can ever
reproduce a minification bug**, which is exactly how the connection bar below shipped broken twice.

## Module map

| Module | Contains |
|---|---|
| `zerozstack-shared-api` | Annotations, signals, `EventTopic`, validation, serialization primitives. Compiled into **both** tiers. |
| `zerozstack-apt` | Annotation processor. Generates `_Serializer`, `_Rules`, `_Live`, `_Stub` and the SPI registrar. |
| `zerozstack-client` | TeaVM client runtime: `Zeroz4jClient`, `WasmRmiClient`, `ServerEvents`. Renamed from `zerozstack-client-wasm`. |
| `zerozstack-ui-components` | Java component library styled with Tailwind/DaisyUI. Components wrap a TeaVM `HTMLElement`, reachable via `getElement()`; there is no server-side DOM state. |
| `zerozstack-bom` | Dependency BOM — the intended way for applications to import versions. |
| `zerozstack-server-core` | CDI engine, RMI dispatcher, `SyncEngine`, `EventPublisher`, dev auth. **Carries no JAX-RS or servlet type**, which is what makes it safe inside somebody else's WAR. |
| `zerozstack-server-test` | Test harness. `TestServer` starts an isolated server in-process, `TestConnection` stands in for a browser. Application takes it with **test scope**; it starts a bean container and must never reach a production classpath. See docs/guides/testing.md. |
| `zerozstack-server-jaxrs` | The JAX-RS catch-all at `/` serving the client bundle and the shell. A standalone server wants it; a WAR with its own servlets must be able to leave it out, which is why it is separate. |
| `zerozstack-server-jakarta` | Servlet-container binding: WebSocket registration, serializer bootstrap, the container `ManagedThreadFactory`, and an optional shell servlet. Take this instead of `-helidon` for a WAR. |
| `zerozstack-server-helidon` | Helidon HTTP/WebSocket bindings. Depends on `-jaxrs`. |
| `zerozstack-auth-oidc` | Optional OIDC authentication provider — token verification at the handshake, Keycloak claim mapping. |
| `zerozstack-store-eclipsestore` | Persistence on ZeroZ DB: per-tenant stores, transactions, and the embedded/server mode switch. |
| `zerozstack-archetype` | Maven archetype scaffolding a three-module application. |
| `zerozstack-examples` | Twelve runnable reference applications. |

An application has three modules: **shared** (`@DataModel` classes, `@RmiService` interfaces),
**client** (the UI, compiled for the browser by TeaVM), **server** (`@ApplicationScoped`
implementations).

## Non-negotiable rules

1. **Every type crossing the wire is `@DataModel`.** Three shapes qualify, and nothing else:
   a **class** with a public no-arg constructor plus getters and setters; a **record** (0.8.0+),
   which needs none of that ceremony; and a **sealed interface or sealed abstract class** (0.8.0+),
   which declares "one of these types" and carries no fields of its own unless it is an abstract
   class. Without the annotation, serialization throws at runtime. A record may not be `@LiveSync`
   or `@ClientWritable` — those edit an object in place and a record never changes — and a record
   may not be part of a reference cycle; the send is refused with an explanation, so use a class
   where the loop closes. See [docs/PROTOCOL.md](docs/PROTOCOL.md).
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
9. **Never empty an element by hand.** `getElement().setInnerHTML("")` takes what was inside off
   the page without telling it, so its `onDetach` never runs and its timers, effects and
   subscriptions keep running against a screen nobody is looking at. Swap contents with
   `container.replaceContents(next)`, or `Component.replaceContents(element, next)` for a plain
   element such as an application's root `<div>`; empty one with `removeAll()`. Both run `onDetach`
   on everything leaving, nested parts included. `DetachContractTest` reads every Java file in the
   checkout on every build and fails it otherwise. Only components put in with `add(...)` get a
   lifecycle - appending an element straight to `getElement()` does not.
10. **Never put a click listener on a `Div`.** A control has to be reachable with Tab and pressed
   with Enter, and has to have words that say what it does. Use a `Button` - `btn-ghost` and
   `btn-link` make one look like anything. `KeyboardAndNamingContractTest` reads every component
   on every build and fails it otherwise, and every control also has to appear on the browser
   proof page in `tools/ui-proof`. Full rule:
   [docs/guides/ui-keyboard-and-naming.md](docs/guides/ui-keyboard-and-naming.md).
11. **Name every input with `withLabel(...)`, not with the constructor argument** (0.8.0+).
   `new TextField("Email address")` sets the *placeholder* — example text inside the empty box that
   disappears the moment somebody types and is announced by nothing. The caption is
   `new TextField().withLabel("Email address")`. `setHelperText`, `setRequiredIndicatorVisible` and
   `setErrorMessage` carry the other three things a field says, and a `Binder` sets the last of them
   for you. Every input in the library has all four.
12. **Ask for a text size by name; never write out your own** (0.8.0+). Five in
   `com.zeroz4j.ui.theme.TextStyle` - `PAGE_TITLE`, `SECTION_TITLE`, `BODY`, `SECONDARY`, `CAPTION` -
   used as `TextStyle.SECONDARY.paragraph("...")`, `TextStyle.CAPTION.span("...")` or
   `TextStyle.CAPTION.applyTo(existingComponent)`. How loud is a *separate* question,
   `com.zeroz4j.ui.theme.Emphasis` - `FULL`, `QUIET`, `FAINT` - passed as a second argument only
   where the text disagrees with its size, such as an error line that is small but must be read.
   Never write `text-sm text-base-content/60` or any hand-picked fade: that names a color, and it
   goes wrong on a tinted notice or a dark page. Text drawn *inside* a chart has its own four names,
   `com.zeroz4j.ui.chart.PlotText` - `FIGURE`, `LABEL`, `CAPTION`, `MESSAGE` - because class names do
   not reach a drawing; a test fails the build if a chart draws text without naming a role.

## Persistence and transactions

Persistence runs on **[ZeroZ DB](https://github.com/ZeroZ4j/zerozdb)** (`com.zeroz4j:zerozdb`),
the sibling project in the ZeroZ4J family. Inject `ZeroZDbNode`, not `EmbeddedStorageManager`.

Where the data lives is a **deployment** choice, set by `zeroz4j.store.mode`:

| mode | who owns the data |
|---|---|
| `EMBEDDED` (default) | this process, no socket, one copy of the graph |
| `AUTO_SERVER` | this process if the store is free, otherwise whoever already owns it |
| `CLIENT` | a separate `zerozdb` server, configured with `zeroz4j.store.server.host` and `.port` |

### Writing

Every write is a transaction: everything it enlists commits atomically, and if it throws, nothing
is persisted and the objects it touched are restored in memory.

For code that must run in **every** mode, send a command — it executes wherever the data is:

```java
@Inject ZeroZDbNode db;

long id = db.execute(new AddProduct("Laptop stand"));
int total = db.query(new CountProducts());
```

```java
public class AddProduct implements DbCommand<Long> {
    public String name;

    public AddProduct() { }                    // needed for deserialisation
    public AddProduct(String name) { this.name = name; }

    @Override
    public Long execute(WriteContext ctx, Object root) {
        Catalog catalog = (Catalog) root;
        ctx.edit(catalog);                     // the counter is about to change
        ctx.edit(catalog.getProducts());       // and so is the list
        long id = catalog.getNextId();
        catalog.setNextId(id + 1);
        catalog.getProducts().add(new Product(id, name));
        return id;                             // both land in ONE commit
    }
}
```

For code that only ever runs embedded — which is most example and single-instance code — a
write-block is shorter and equivalent:

```java
db.localDb().write(ctx -> {
    ctx.edit(root.getTasks());
    root.getTasks().add(task);
});
```

`localDb()` is the in-process engine and is **null in CLIENT mode**, so using it pins that code to
`EMBEDDED` or `AUTO_SERVER`. That is a fine trade for an example and the wrong one for a library.

### The node is `@Dependent`, and why that matters

The producer for `ZeroZDbNode` is `@Dependent` and cannot be anything else: `ZeroZDbNode` is
`final`, a normal scope would make CDI proxy it, and a proxy is a generated subclass. Through
0.4.1 the producer was `@RequestScoped` and `@Inject ZeroZDbNode` therefore failed at deployment
with WELD-001410 in every application. Do not "fix" the scope back.

The consequence to know: a `@Dependent` producer runs when the injecting bean is created, so an
`@ApplicationScoped` service resolves its tenant **once**. Correct for a single-tenant application.
Wrong for one with a `TenantResolver` — there, inject `TenantStorageProvider` and call
`getNode(tenantId)` per operation.

### The rules that break code most often

**Enlisting does not cascade.** `ctx.store(obj)` covers that object only. Changing a field on the
root *and* a collection hanging off it means enlisting both. Getting this wrong loses the change
silently — it is in memory and never reaches disk.

**Enlist before mutating** with `ctx.edit(obj)`. The rollback snapshot is taken at enlistment, so
one taken afterwards already contains the change.

**`DbCommand` and `DbQuery` must be plain classes, never records.** EclipseStore's serializer
reaches fields directly and the JVM refuses that for records without
`--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`. It fails at the first remote call rather
than at compile time, so it looks like a networking fault. Public fields, public no-arg constructor.

**A query returns values, not live graph nodes.** Whatever it returns is serialized to the caller,
so returning a deeply-connected entity ships its reachable graph. Copy or reduce first.

**Never start a write inside a read block.** A read lock cannot be upgraded; the engine throws
rather than deadlocking.

### Reading fast on a client

```java
try (var local = db.<Catalog>localReads()) {
    int count = local.read(c -> c.getProducts().size());   // heap access, no round trip
}
```

The live graph when this process owns the store, a continuously refreshed replica when it does not
— trailing the owner by about one round trip. Use `db.query(...)` when a read must be exactly
current — deciding whether a caller may do something, especially.

Full detail: [docs/store-modes.md](docs/store-modes.md), [docs/guides/persistence.md](docs/guides/persistence.md),
and the [ZeroZ DB guide](https://github.com/ZeroZ4j/zerozdb/blob/main/docs/Guide.md).

## Choosing a state-propagation mechanism

This is the decision developers most often get wrong. Ask in order:

```
1. Does anything outside this browser tab need to know?
   No  -> local ValueSignal / Computed / Effect. Stop. (Most UI state lands here.)

2. Does the client need an answer, or is it invoking a named operation
   ("approve", "checkout", "log in")?
   Yes -> RMI call on an @RmiService. Stop.

3. Does the data belong to somebody rather than to everybody?
   Yes -> scope it, then continue to 4 to pick the mechanism.
          Events:  publishToUser / publishToSession / publishToClient.
          LiveSync: notifyChanged(obj, Scope.X, target).
          Signals:  Signals.scoped(name, initial, Scope.X)  -- NOT Signals.shared,
                    which is one value for the whole JVM by definition.
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

### Picking a scope (0.6.0+)

| Scope | Keyed by | Needs a login? | Survives reconnect? |
|---|---|---|---|
| `SESSION` | WebSocket session id | no | **no** — the id changes on every drop |
| `CLIENT` | server-issued browser id | no | yes, and page reloads too |
| `USER` | authenticated user name | yes | yes |
| `TENANT` | authenticated tenant | yes | yes |

`Scope.CLIENT` is the default for an application with no login. **`CLIENT` and `SESSION` identify a
browser, not a person** — never use them to keep one user's data from another; that needs `USER` or
`TENANT`.

On the server name the target explicitly, and take it from the connection, never from an argument:

```java
BasketSignals.BASKET.forTarget(RmiRequestContext.getClientId()).set(updated);   // server
Effect.create(() -> label.setText(BasketSignals.BASKET.mine().get().size()));   // client
```

`forTarget` throws on a client and `mine()` throws on the server — deliberately, so a browser cannot
name someone else's target. See [docs/SIGNALS.md](docs/SIGNALS.md).

## Routing (0.6.0+)

`@Route` on a class implementing `RouteView<T>`; the annotation processor generates the route table
at compile time, so there is no reflection and a malformed route is a compile error.

```java
@Route(value = "/tasks/:id", layout = AppShell.class)
public class TaskDetailView implements RouteView<Task> {
    public Task load(RouteParams p) { return tasks.byId(p.getLong("id")); }   // runs first
    public Component render(Task task, RouteParams p) { ... }                 // then this
}
```

- **`load` completes before `render` is called.** Never fetch from inside a mounted component.
- Real URLs via the history API. Deep links work because `StaticContentResource` serves the shell for
  any path with no file behind it.
- `Router.start("app-root")` once; `Router.navigate(path)`, or an `<a data-route href="...">`.
- Guard with `@RequiresRole`; it decides what to *show*, the server still decides what is allowed.

See [docs/ROUTING.md](docs/ROUTING.md).

## Suspending calls and green threads

An RMI call suspends a TeaVM coroutine, and **a coroutine cannot suspend on a stack that began in
native JavaScript**. Click handlers, `setTimeout` callbacks and framework callbacks such as
`RmiSecurityContext.onAuthenticated` are all such stacks, and calling a service directly from one
fails with *"suspension point reached from non-threading context"*.

```java
button.addClickListener(e -> new Thread(() -> service.save(item)).start());
```

That is a green thread re-entering TeaVM's scheduler, not parallelism. The router does this
internally for every navigation.

## When nothing happens

Most of these now throw. The ones that remain are the genuinely silent cases.

| Symptom | Cause |
|---|---|
| A LiveSync'd object updates but the UI does not | The getter was read outside an `Effect`, so nothing subscribed. Read it inside `Effect.create(...)`. |
| An `Effect` does not re-run | The value was mutated in place and `equals` deduplication swallowed it. Use `update()` with a new instance. |
| A LiveSync collection edit does nothing | Setters are the tracking boundary; `obj.getTags().add(x)` is invisible. Reassign, or call `LiveMutationTracker.touch(obj)`. |
| A rejected `sharedWritable` write reverts with no explanation | Shared-signal write rejections are still logged nowhere. LiveSync mutations do report a reason. |

These used to be silent and now fail loudly — expect an exception, not a mystery:
`notifyChanged` on an unsynced object, an unserializable event or signal payload, a conflicting
shared-signal declaration, `bindValue` with a non-writable signal, an RMI call while the connection
is down (`DisconnectedException`, immediately — never a 30-second hang), and — at compile time —
`@ClientWritable` without `@LiveSync` or on a field with no setter.

## Packaging — never shade

Never add `maven-shade-plugin` to a ZeroZ Stack server. Weld treats each jar as its own bean
archive with its own `beans.xml`; a merged jar collapses that and CDI discovery breaks far from
the cause (beans vanish, or WELD-001409 duplicates). The supported shapes, all keeping jars
intact: the default jar + `target/libs` classpath layout; `mvn verify -Ppackage` in a generated
project, which runs the JDK's `jpackage` and produces a self-contained folder with launcher
executable and bundled runtime at `<app>-server/target/dist/<app>/`; and the generated
`Dockerfile`, which layers `libs/` separately from the app jar. GraalVM native-image is not
supported (EclipseStore uses JDK internals it restricts). See docs/guides/packaging.md.

On Linux, never launch with a bare `libs/*` classpath wildcard: it expands in arbitrary directory
order, and one of the orders registers Helidon's WebSocket routing after the server is built —
HTTP works, every WebSocket handshake 404s, Windows is unaffected (its filesystem sorts). Build
the classpath sorted: `java -cp "target/classes:$(ls target/libs/*.jar | sort | tr '\n' ':')" …`.
The generated Dockerfile already does this.

## Connection drops (0.5.0+)

Do not generate reconnect plumbing — the framework recovers by itself: automatic reconnect with
backoff, a built-in "Connection lost" banner (`Zeroz4jClient.showConnectionBanner(false)` to opt
out), shared signals re-subscribed, live objects re-synced in place, offline signal writes and
`@ClientWritable` edits queued and flushed. Connection state is a signal:
`WasmRmiClient.connectionState()` — read it in an `Effect` to disable controls while not
`CONNECTED`. What remains application work: retrying an RMI call that failed with
`DisconnectedException` (never replayed automatically), re-registering anything keyed by session id
(ids change on reconnect; observe `SessionClosedEvent` server-side to clean up), reacting to a lost
`LiveMutex` (`setLostListener`), and re-fetching live objects after a full **server restart**, which
empties the handle registry that re-sync restores from.

**Only a `@LiveSync` model and the objects inside one carry a handle (0.8.0+),** and the registry
holds them weakly on both tiers. Everything else on the wire is a value with a name good for its own
message: it cannot be synced, locked or re-read, and a client sending one back as a call argument
hands over a copy rather than reaching into the server's instance. Keep live objects in your store or
a field — an object the server has dropped answers a re-sync the way a restarted server does. A
re-sync request carries at most 10,000 handles; a client over that throws its list away and re-fetches
rather than sending a message the connection would refuse. A test that registers models by hand calls
`BinaryRegistry.registerHandleBearing(fqcn)` for the ones standing in for `@LiveSync` models.

## Running the examples

All twelve examples live under `zerozstack-examples/`. After `mvn clean install -DskipTests` from the
root, the seven original ones and `payments-datamodels` have a `run.bat` (Windows). **Every example
binds a port of its own (0.8.0+), so several can run at once** — the table below has the numbers.

For those seven there is no executable jar and no `exec-maven-plugin` — `java -jar` and
`mvn exec:java` both fail regardless of what older docs say. The working invocation is the one
`run.bat` uses:

```bash
cd zerozstack-examples/todo-signals/todo-signals-server
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer   # ';' on Windows, ':' on POSIX
```

They share the main class `com.zeroz4j.example.server.ExampleServer`. **The four added in 0.6.0 do
not**: each has a main class of its own, and three of them also build a runnable jar.
`payments-datamodels` has one of its own too, in its own package:
`com.zeroz4j.example.payments.server.ExampleServer`.

| Example | Port | Example | Port |
|---|---|---|---|
| `routing-tour` | 8091 | `job-monitor` | 8087 |
| `oidc-login` | 8081 (needs Keycloak) | `form-signup` | 8088 |
| `scoped-signals` | 8082 | `inventory-crud` | 8089 |
| `pwa-install` | 8083 | `components-showcase` | 8090 |
| `todo-signals` | 8084 | | |
| `chat-events` | 8085 | | |
| `chat-livesync` | 8086 | `payments-datamodels` | 8092 |

`routing-tour`, `oidc-login` and `scoped-signals` also build runnable jars:
`java -jar routing-tour-server/target/routing-tour-server-0.9.0.jar`.

**To move one:** `--port 9000` on the command line, or `-Dzeroz.port=9000`, or — for the seven with
a `run.bat` — `run.bat 9000`. Each server reads them in that order and falls back to its own
`DEFAULT_PORT` constant.

**Four of the seven originals require signing in:** `chat-events`, `chat-livesync`, `job-monitor` and
`components-showcase` show a client-side `Login` component. `todo-signals`, `form-signup`,
`inventory-crud` and `payments-datamodels` connect anonymously. `routing-tour` and `scoped-signals` take credentials from the
URL — `?user=admin&password=admin` — which is how you open two windows as different users.

**No example enables the development logins by itself.** Pass `--dev-login` to the server main class
(the `run.bat` scripts do), or set `-Dzeroz.security.mode=dev` yourself; a server with it on prints
`DevAuth.WARNING_BANNER` at startup and again on the first sign-in. Do not reintroduce a default:
these classes are what applications get copied from.

Dev credentials are `demo` / `demo` (role `user`) and `admin` / `admin` (roles `user`, `admin`). The
client passes them as WebSocket handshake parameters and `DevAuth` validates them. There is no HTTP
login page and no HTTP-level gate: **authentication happens at the handshake, and an unauthenticated
visitor loads the page normally** and is refused at every `@Secured` call.

**To replace it:** implement `com.zeroz4j.server.AuthenticationProvider` and register it in
`META-INF/services/com.zeroz4j.server.AuthenticationProvider`. Discovery is via `ServiceLoader`, not
CDI, because the handshake runs before the endpoint exists. Return an `AuthenticatedPrincipal` with a
name, roles and optionally a tenant; return null to leave the connection anonymous. Registering a
provider disables the `DevAuth` fallback entirely.

**For OpenID Connect, do not write a provider** — depend on `zerozstack-auth-oidc` and register
`com.zeroz4j.server.oidc.OidcAuthenticationProvider`, configured with `zeroz.oidc.issuer` and
`zeroz.oidc.clientId`. In the browser, `OidcClient.start(config, onReady)` runs the whole
authorization-code + PKCE flow. Leave `zeroz.oidc.audience` unset unless the realm has an audience
mapper: a stock Keycloak token carries `aud: "account"` and names the client in `azp`.
See [docs/guides/oidc-auth.md](docs/guides/oidc-auth.md).

**A rejected sign-in is not authenticated.** `RmiSecurityContext.isAuthenticated()` is false and
`onAuthenticated(...)` does not fire, so gate a login screen on that callback and report failure from
`onAuthenticationFailed(...)`. Do not check for a specific role as a proxy for "did the login work" —
that workaround existed because of a bug fixed in 0.6.0.

**Mount the UI from `onResolved(...)`, never `onAuthenticated(...)`.** `onResolved` fires once the
server has answered, authenticated or anonymous — that is the "connection is usable" signal.
`onAuthenticated` is about *identity* and never fires for an anonymous connection, so an app with no
login that mounts from it renders a blank page. Build the view on a green thread if it makes an RMI
call: `onResolved(() -> new Thread(this::mountUi).start())`.

**Deploying as a WAR:** take `zerozstack-server-jakarta` instead of `zerozstack-server-helidon`. It
registers the WebSocket endpoint, calls `BinaryRegistry.init()` at startup, and supplies the
container's `ManagedThreadFactory` so RMI calls carry the container's context for naming,
transactions and the caller's identity — without which a `java:comp/env/…` lookup inside a service
fails. Map `Zeroz4jShellServlet` yourself;
it is deliberately unmapped so it cannot claim `/` in a WAR that has its own servlets. **Do not add
`zerozstack-server-jaxrs` to a WAR**: it is a catch-all at `/`. `zerozstack-server-core` contains no
JAX-RS type, which is what makes it safe inside somebody else's WAR.

**`zeroz.ws.maxBinaryMessageBytes` defaults to 4 MB** (0.7.0+), matching gRPC. Set it only to move
away from that. `@OnMessage` takes a whole message, so anything larger closes the socket rather than
raising an error the application can catch. Do not leave it to the container: on Helidon the Jakarta
WebSocket layer is Tyrus, whose message-assembly limit is `Integer.MAX_VALUE` — about 2 GB. This
socket is not the route for file uploads. `zeroz.ws.idleTimeoutMinutes` stops an abandoned tab
holding a session forever; it stays unset by default, leaving the container's own value.

**One connection's messages are handled in order (0.8.0+).** The server handles a connection's
messages one at a time, in the order the client wrote them, so anything a browser sends after a live
edit - a service call, a lock, a signal write - is handled after that edit. Do not generate a
`LiveMutex` to order one person's own messages; a lock is for two people editing the same thing.
Ordering is per connection: a slow call for one person never delays anybody else. The keepalive is
answered outside the queue, and a lock request that is waiting for somebody else lets the messages
behind it past, because it has changed nothing yet.

**Per-connection ceilings (0.7.0+):** 32 messages from one connection may be waiting to be handled
(`zeroz.ws.maxQueuedFramesPerSession`, called `zeroz.ws.maxConcurrentFramesPerSession` before 0.8.0
and still read under that name); 256 messages or 8 MB may be waiting to go out
(`zeroz.ws.maxPendingFramesPerSession`, `zeroz.ws.maxPendingBytesPerSession`), past which that one
connection is closed with WebSocket code `1013`. An empty outgoing queue always accepts the next
message however large, so a single big response is never refused.

**File upload (0.7.0+):** put `FileUpload` on a screen and write one `@ApplicationScoped` class
implementing `FileUploadHandler`. Files go over their own HTTP address, not the RMI socket — never
put file bytes in an RMI argument. 25 MB per file by default (`zeroz.upload.maxBytes`). The
temporary file is deleted the moment the handler returns, so move it inside the method. Treat
`getFileName()` and `getContentType()` as text the browser typed: generate the stored name yourself.
See [docs/guides/file-uploads.md](docs/guides/file-uploads.md).

**A client may only read back, and only lock, an object the server sent that browser (0.7.0+).** The
server keeps a record per browser — 10,000 objects, dropped after 24 hours idle
(`zeroz.disclosure.maxHandlesPerClient`, `zeroz.disclosure.idleHours`). Ask it yourself with
`Disclosures.wasDisclosedTo(session, handleId)`.

**A live change is checked against every object it reaches (0.7.0+),** not just the outermost one. A
model nested inside a `@ClientWritable` model needs its own `@ClientWritable`, and one refusal
refuses the whole change.

**A burst of live edits is one message, not one per setter call (0.8.0+).** A `@ClientWritable`
change waits for the editing to stop for 150 ms, or for 1 second whatever happens, whichever comes
first, and everything changed in that burst travels together - measured at 38 messages before and 4
after for a 38-character sentence typed into the `chat-livesync` topic box. Change it with
`LiveMutations.configure(pauseMillis, ceilingMillis)`; `(0, 0)` restores a message per setter call.
Anything the client sends afterwards - a service call, a lock, a signal write - goes on the wire
behind the waiting edit, so do not generate flush-before-save plumbing. Two things this makes true:
leaving the page loses what was still waiting (there is deliberately no unload flush - browsers do
not reliably put bytes on a WebSocket while tearing a page down), and an `Effect` that copies the
server's broadcast back into the field somebody is typing in now deletes what they typed since, so
follow the incoming value everywhere except the focused field.

**An edit that does not reach the server is reported, never dropped (0.8.0+).** Whether the server
refused it or the browser could not send it, the screen is put back to the server's state and the
reason goes to `LiveMutationRefusals.onRefused(model, reason)`; with no listener it is written to
the console as a sentence. Nothing is thrown — the setter call finished long before the answer
arrived. Do not generate retry or rollback plumbing for this. The generated registrar records both
the model name and its `<Model>_Live` subclass name, and the writer puts the model name on the wire;
that pairing is what makes the up direction work at all, so do not simplify `registerLive` back to
one name.

**Unexpected server exceptions reach the client as `The server could not complete this request.
Reference: <code>` (0.7.0+),** with the real message in the log under that code. To send a sentence
the caller should read, throw `com.zeroz4j.server.ClientVisibleException`.

**A refusal can be said in the caller's language.** Declare `.properties` files in the shared module
(`i18n/app.properties`, `i18n/app_de.properties`) and mark them with an empty class annotated
`@MessageCatalog(baseName = "i18n/app", fallback = "en")`; the processor writes `AppText_Text` with
one camel-cased method per key, each taking one argument per `{0}` blank and returning a `Message`
rather than a `String`. Throw `new ClientVisibleException(AppText_Text.invoiceApproved(id))` and the
caller reads it in their language while the log keeps English; the `String` constructor stays correct
for a one-language application. Blanks are `{0}`, `{1}` and nothing else - never `MessageFormat`,
which costs 43% more browser download than every language of text put together. The connection's
language is resolved once at the handshake (`lang` parameter, `zeroz-lang` cookie,
`Accept-Language`, `zeroz.i18n.defaultLocale`, then `en`), narrowed to the languages files exist for,
and read with `RmiRequestContext.getLocale()` - never from a method argument. Nothing checks the
other languages, so add one test calling `CatalogParity.assertConsistent(folder, baseName)`.
**English is byte-identical for a project that adds no language.** The browser half - a catalog on
the wire, a language picker, live switching - does not exist yet; see
[docs/guides/language.md](docs/guides/language.md).

**Client identity without a login:** every connection carries a server-issued, HMAC-signed browser id
in an `HttpOnly` cookie, readable as `RmiRequestContext.getClientId()` and used by `Scope.CLIENT`. It
identifies a browser, not a person. Handshakes are also origin-checked; set `zeroz.origins` when the
page is served from a different host than the socket, and set `zeroz.hosts` to the host names the
deployment answers for. `zeroz.hosts` is unset by default; set, a handshake addressed to any other
name is refused, and the close reason names which of the two checks refused it.

**Making an app installable:** `Pwa.install()` in `main` before `connect`, plus a manifest and
`<link rel="manifest">`. **Never tell a user this makes the application work offline** — every view
loads its data over the socket and there is no client-side store, so opened offline it shows
`/zeroz4j-offline.html` and nothing else. Do not write a service worker: the framework ships one from
`zerozstack-server-core`, version-stamped so a deployment evicts the old shell. Gate an install button
on the `Pwa.installable()` signal, not a one-shot boolean — the browser's offer arrives after the UI
is built. See [docs/PWA.md](docs/PWA.md).

| Example | Demonstrates |
|---|---|
| `todo-signals` | Local signals, `Computed`, `Effect` in isolation |
| `chat-events` | `EventTopic` / `EventPublisher` / `ServerEvents`, deliberately without signals |
| `chat-livesync` | `@LiveSync` both ways: the message list comes down into an `Effect`, the topic box is `@ClientWritable` and goes up, and `LiveMutationRefusals` tells the person when the server would not have their edit |
| `job-monitor` | `Signals.shared` driven from a server-side virtual thread |
| `form-signup` | Validation annotations, generated `_Rules`, `Computed` form validity |
| `inventory-crud` | Master-detail CRUD, local signals, `Computed` KPIs |
| `components-showcase` | The component library gallery |
| `routing-tour` | `@Route`, nested `RouteLayout`, path and query parameters, `@RequiresRole` guards, colocated loaders |
| `scoped-signals` | `Signals.scoped` with `Scope.CLIENT` and `Scope.USER` beside a global `Signals.shared` |
| `oidc-login` | `OidcClient` PKCE login against Keycloak, and `@Secured`/`@RolesAllowed` enforced from its claims |
| `pwa-install` | `Pwa.install()`, `Pwa.installable()`, `PwaManifest` per request, push subscription, and the offline page |
| `payments-datamodels` | The three shapes a wire type can take - `record`, sealed family, and a model extending another model - nested and in collections, both directions, with a `TestServer` test driving real frames |

## Not implemented — do not generate code against these

- Route loaders **do not run in parallel**. Client code is a single cooperative scheduler, so a
  layout's loader and its child's are sequential. The guarantee routing gives is ordering — data
  before render — not concurrency.
- The route chain is **rebuilt on every navigation**; a layout is not kept mounted while its children
  swap, so its loader re-runs.
- Routing has no wildcard or optional segments, no lazy loading, and one child per layout.
- Protocol opcodes `0x11 SNAPSHOT`, `0x12 UNSUBSCRIBE`, `0x13 MUTATE`, `0x14 ACK`, `0x16 SIGNAL_SUB`
  and `0x18 PUSH` are declared but unreferenced. There is no version field, no acknowledgment and no
  conflict rejection in the implemented sync path. `0x15 REJECT` **is** implemented: it carries the
  reason a live mutation was refused.
- LiveSync has no field-level merging and no version-conflict detection. Whole-object,
  last-write-wins.
- Tracked collections do not exist.
- Events have no per-topic server-side subscription filtering.
- No serialization support for object arrays (`String[]`, `MyModel[]` — use a `List`), or for
  `ZonedDateTime`, `OffsetDateTime`, `ZoneId`, `Period`, `java.util.Date`. Primitives, `String`,
  `UUID`, enums, `BigDecimal`, `BigInteger`, `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`,
  `Duration`, `Optional`, `List`, `Set`, `Map`, all primitive arrays, EclipseStore `Lazy<T>`,
  **records** and **sealed hierarchies** (both 0.8.0+) **are** supported.
- **A record cannot be part of a reference cycle**, and it cannot be a persistence root. Sending a
  looped record is refused when it is sent, naming the record; use a class for the type that closes
  the loop. The persistence rule is separate and unchanged: EclipseStore reaches fields directly and
  the JVM refuses that for records.
- **A sealed hierarchy must be one level deep**, every class it permits must be `final` and
  `@DataModel`, and a sealed *class* base must be `abstract`. All four are compile errors, each
  because the receiving side could not otherwise tell an allowed type from any other.
- **A model may extend another model and the base's fields travel too** (0.8.0+; before that they
  were silently dropped). An abstract model gets no serializer and no registry entry — it exists to
  hand its fields down. Two shapes are now compile errors, both formerly silent data loss: extending
  a class that is not a `@DataModel` and declares instance fields, and redeclaring a field name a
  base class already uses.
- **Object identity holds within one top-level value, not between two.** The same instance in two
  fields of one model arrives once; the same instance as two elements of a top-level `List` arrives
  twice. Never use `==` across a call boundary to decide whether two things are the same — compare by
  id or `equals`.
- A `Lazy<T>` field travels as a session-scoped handle, never its contents. The client resolves it
  with a suspending RMI round trip on first `get()`. Lazy references originate on the server; a client
  cannot create one and send it up.
- Declare collection fields as `List`, `Set`, `Map`, not as concrete types: they are rebuilt as
  `ArrayList`, `LinkedHashSet` and `LinkedHashMap`, so a field typed `TreeSet` fails with a
  `ClassCastException` on deserialization.

## What an application's own assistant reads

This page is for working **on** the framework. Two other documents are for working **in** an
application built on it, and neither is written by hand:

- **`META-INF/zeroz4j/AGENTS.md` inside `zerozstack-shared-api`.** Generated during this build from
  the `rules` array in `context7.json`, stamped with this build's version. Every application
  resolves that artifact, so an assistant gets rules for the version the project actually depends
  on rather than for whatever a documentation index is currently serving. Change `context7.json`
  and the jar follows; there is nothing else to edit.
- **`AGENTS.md` in a generated project.** The archetype writes it, with `${zeroz4jVersion}`
  substituted. Source:
  `zerozstack-archetype/src/main/resources/archetype-resources/AGENTS.md`. Its `##` headings are
  wrapped in Velocity literal blocks because Velocity reads `##` as a comment and silently drops the
  line; the file says so at the top.

**Every version stated in prose is checked.** `VersionStatementTest` compares it against
`<revision>` and fails the build, naming the file and line. A sentence about the past keeps its own
number as long as it says so — `(0.6.0+)`, `since 0.5.0`, `before 0.8.0`, `added in 0.6.0`. A
version with no such marker is read as a claim about the current release.

## Conventions

- **Never name anything with a single letter inside a `@JSBody` script.** TeaVM inlines the script
  as text and renames only the method's parameters, and a minified build renames them to single
  letters: `b` for the first parameter, `c` for the second, and so on. A one-letter name inside the
  script becomes the same name as a parameter, and one of them silently becomes the other. That is
  how the connection bar came to read `[object HTMLDivElement]` for two releases, and how a file
  upload's progress figure never moved. Use `idx`, `ignored`, `bar`, `node`.
  `JsBodyNamingContractTest` reads every Java file in the checkout on every build and fails it
  otherwise.

  **Nothing in this repository builds minified**, and a generated application only does so under
  `-Pproduction`, so this class of bug is invisible in every ordinary build on both sides. The
  contract test is the whole defense. Do not weaken it, and do not assume a working example proves
  a `@JSBody` script is safe.
- Apache 2.0 license header on every new `.java` file; copy an existing one.
- Javadoc on public API, including the wire opcode where a method sends a frame.
- Documentation lives in `/docs` as plain Markdown. See
  [docs/contribute/docs-style-guide.md](docs/contribute/docs-style-guide.md).
