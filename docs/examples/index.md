# Examples

Ten runnable applications under `zerozstack-examples/`. Each isolates one part of the framework, and CI
builds all of them on every push — so the code in them is always current, which is not true of every
page in this documentation.

## Running any of them

Build once from the repository root, then start one example. Most bind port 8080, so run one at a
time unless the table below says otherwise.

```bash
mvn clean install -DskipTests
```

=== "Windows"

    ```bat
    cd zerozstack-examples\todo-signals
    run.bat
    ```

=== "Any OS"

    ```bash
    cd zerozstack-examples/todo-signals/todo-signals-server
    java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer
    ```

    Use `:` as the classpath separator on Linux and macOS.

The seven original examples share the main class `com.zeroz4j.example.server.ExampleServer` and bind
port 8080. The four added in 0.6.1 have their own, and three of them use a different port so they can
run alongside another:

| Example | Main class | Port |
|---|---|---|
| `routing-tour` | `com.zeroz4j.example.routing.server.RoutingTourServer` | 8080 |
| `oidc-login` | `com.zeroz4j.example.oidclogin.server.OidcLoginServer` | 8081 |
| `scoped-signals` | `com.zeroz4j.example.scopedsignals.server.ScopedSignalsServer` | 8082 |
| `pwa-install` | `com.zeroz4j.example.pwa.server.PwaInstallServer` | 8083 |

Each of those also builds a runnable jar, so `java -jar <example>-server/target/<example>-server-0.6.1.jar`
works without a classpath.

Six examples need a sign-in, and the only accounts they have are the framework's development ones.
**Starting a server does not switch those on** — pass `--dev-login` on the command line, which the
`run.bat` scripts already do. Then sign in as `demo` / `demo`, or `admin` / `admin` for
admin-restricted operations. `chat-events`, `chat-livesync`, `job-monitor` and `components-showcase`
present a `Login` component; `routing-tour` and `scoped-signals` take credentials from the URL
(`?user=admin&password=admin`), which is how you open two of them side by side as different users.
`todo-signals`, `form-signup`, `inventory-crud` and `pwa-install` connect anonymously.

`oidc-login` is the exception: it needs a running Keycloak and binds port 8081. Its
[README](https://github.com/ZeroZ4j/zerozstack/tree/main/zerozstack-examples/oidc-login) has the
setup commands.

See [Quickstart](../start/quickstart.md) for prerequisites and why `clean` matters.

## What each one demonstrates

| Example | Mechanism | Why that one |
|---|---|---|
| **`todo-signals`** | Local signals | The reactive model in isolation, with no network involved: `ValueSignal` sources, `Computed` for the visible list and the remaining count, `Effect` for rendering. Start here. |
| **`form-signup`** | Validation + local signals | Constraints declared once on the model and enforced on both tiers, with a `Computed` driving form validity. |
| **`inventory-crud`** | Local signals + RMI | Master-detail CRUD. Derived KPIs as `Computed` values; save and delete as named RMI operations. |
| **`chat-events`** | Server events | Discrete occurrences. Written **deliberately without signals**, to show that a plain handler and one render path is sometimes the simpler choice. Also shows the subscribe-then-fetch ordering that avoids the snapshot race. |
| **`job-monitor`** | Shared signal | One current value that every client must agree on, driven from a server-side virtual thread. A client opening the page mid-job immediately sees the retained progress — the thing events cannot do. |
| **`chat-livesync`** | LiveSync | An object kept alive on both tiers, updated in place. The view reads its getter inside an `Effect`, so an inbound sync re-renders automatically — no polling, no subscription. |
| **`components-showcase`** | UI components | The gallery for the component library. The place to look for how a given component is constructed and styled. |
| **`routing-tour`** | Routing | Every routing feature in one application: nested layouts, path and query parameters, a literal beating a parameter, `@RequiresRole` guards, and not-found/forbidden fallbacks. Each route loads its data before anything renders. |
| **`scoped-signals`** | Scoped signals | The three reaches side by side — global, per-browser, per-user. Open it twice in one browser as two different users and watch the basket stay shared while the personal notice does not. |
| **`oidc-login`** | OIDC authentication | A real Keycloak login with PKCE, then three RMI calls showing what that identity is worth server-side. Its README carries the realm setup and the two Keycloak defaults that otherwise cost an afternoon. |
| **`pwa-install`** | PWA | Installability, a per-request manifest built with `PwaManifest`, an install button bound to `Pwa.installable()`, and web push. Stop the server and reload to see the one thing installing does *not* buy you. |

The three propagation examples — `chat-events`, `job-monitor` and `chat-livesync` — are best read
together, alongside [Events, signals or LiveSync?](../decide/events-vs-signals-vs-livesync.md). The
differences between them are the point.

## Reading them as reference

The examples are the most reliable specification of current usage in the repository. When a document
and an example disagree, trust the example — it compiles.

Two caveats:

- **No example uses `@ClientWritable`.** The LiveSync up-direction is exercised only in
  `ServerLiveMutationTest` in `zerozstack-server-core`. Read that test before building on it.
- **`components-showcase` uses `Binder`; everything else uses `bindValue`.** These are two different
  tools, not rivals — see [Forms and binding](../guides/ui-forms-and-validation.md).
- **`components-showcase` broadcasts to topics nothing subscribes to.** It uses the low-level
  `WasmRmiServerEngine.broadcastPush(String, Object)` rather than a typed `EventTopic`, and no client
  registers a listener. Do not copy that pattern; use `EventTopic` and `ServerEvents.on` as
  `chat-events` does.
