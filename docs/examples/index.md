# Examples

Twelve runnable applications under `zerozstack-examples/`. Each isolates one part of the framework, and CI
builds all of them on every push — so the code in them is always current, which is not true of every
page in this documentation.

## Running any of them

Build once from the repository root, then start an example. **Every example has a port of its own**
(0.8.0+), so you can leave several running at the same time. None of them uses 8080: on a working
machine that number is usually taken by something else already.

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

The seven original examples share the main class `com.zeroz4j.example.server.ExampleServer`. The four
added in 0.6.0 have their own, and so does `payments-datamodels`, which is in a package of its own:

| Example | Main class | Port |
|---|---|---|
| `routing-tour` | `com.zeroz4j.example.routing.server.RoutingTourServer` | 8091 |
| `oidc-login` | `com.zeroz4j.example.oidclogin.server.OidcLoginServer` | 8081 |
| `scoped-signals` | `com.zeroz4j.example.scopedsignals.server.ScopedSignalsServer` | 8082 |
| `pwa-install` | `com.zeroz4j.example.pwa.server.PwaInstallServer` | 8083 |
| `todo-signals` | `com.zeroz4j.example.server.ExampleServer` | 8084 |
| `chat-events` | `com.zeroz4j.example.server.ExampleServer` | 8085 |
| `chat-livesync` | `com.zeroz4j.example.server.ExampleServer` | 8086 |
| `job-monitor` | `com.zeroz4j.example.server.ExampleServer` | 8087 |
| `form-signup` | `com.zeroz4j.example.server.ExampleServer` | 8088 |
| `inventory-crud` | `com.zeroz4j.example.server.ExampleServer` | 8089 |
| `components-showcase` | `com.zeroz4j.example.server.ExampleServer` | 8090 |
| `payments-datamodels` | `com.zeroz4j.example.payments.server.ExampleServer` | 8092 |

### Moving one somewhere else

If a number is already taken on your machine, say so on the command line. Every example understands
the same two ways of being told:

```bash
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer --port 9000
java -Dzeroz.port=9000 -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer
```

The `run.bat` scripts take the port as their first argument, and print the address they are using:

```bat
run.bat 9000
```

The four added in 0.6.0 also build a runnable jar, so
`java -jar <example>-server/target/<example>-server-0.9.0.jar` works for those without a
classpath. The rest do not; use the classpath command, which is what `run.bat` does.

Six examples need a sign-in, and the only accounts they have are the framework's development ones.
**Starting a server does not switch those on** — pass `--dev-login` on the command line, which the
`run.bat` scripts already do. Then sign in as `demo` / `demo`, or `admin` / `admin` for
admin-restricted operations. `chat-events`, `chat-livesync`, `job-monitor` and `components-showcase`
present a `Login` component; `routing-tour` and `scoped-signals` take credentials from the URL
(`?user=admin&password=admin`), which is how you open two of them side by side as different users.
`todo-signals`, `form-signup`, `inventory-crud`, `pwa-install` and `payments-datamodels` connect
anonymously.

`oidc-login` is the exception: it needs a running Keycloak. Its
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
| **`chat-livesync`** | LiveSync | An object kept alive on both tiers, updated in place, in both directions. The view reads its getter inside an `Effect`, so an inbound sync re-renders automatically — no polling, no subscription. The topic box goes the other way: it is `@ClientWritable`, so typing in it changes the server's object and every other window follows, with no service call. |
| **`components-showcase`** | UI components | The gallery for the component library. The place to look for how a given component is constructed and styled. |
| **`routing-tour`** | Routing | Every routing feature in one application: nested layouts, path and query parameters, a literal beating a parameter, `@RequiresRole` guards, and not-found/forbidden fallbacks. Each route loads its data before anything renders. |
| **`scoped-signals`** | Scoped signals | The three reaches side by side — global, per-browser, per-user. Open it twice in one browser as two different users and watch the basket stay shared while the personal notice does not. |
| **`oidc-login`** | OIDC authentication | A real Keycloak login with PKCE, then three RMI calls showing what that identity is worth server-side. Its README carries the realm setup and the two Keycloak defaults that otherwise cost an afternoon. |
| **`pwa-install`** | PWA | Installability, a per-request manifest built with `PwaManifest`, an install button bound to `Pwa.installable()`, and web push. Stop the server and reload to see the one thing installing does *not* buy you. |
| **`payments-datamodels`** | The shapes a wire type can take | A till: a basket, four kinds of payment, and a ledger. `Money` and `LineItem` are records; the way somebody paid is a sealed interface with a record per kind; a payment and a refund share a base class. All three travel nested inside each other and inside collections, in both directions, and both the server log and the screen say what actually arrived. |

The three propagation examples — `chat-events`, `job-monitor` and `chat-livesync` — are best read
together, alongside [Events, signals or LiveSync?](../decide/events-vs-signals-vs-livesync.md). The
differences between them are the point.

## Reading them as reference

The examples are the most reliable specification of current usage in the repository. When a document
and an example disagree, trust the example — it compiles.

Two caveats:

- **`chat-livesync` is the only example that writes back.** Its topic box is `@ClientWritable`, and
  it is also where a refused edit is shown to the person, through
  `LiveMutationRefusals.onRefused(...)`. Type more than eighty characters into that box to watch a
  refusal happen — the model caps it there, the server puts the box back, and the reason appears
  above it.
- **`components-showcase` uses `Binder`; everything else uses `bindValue`.** These are two different
  tools, not rivals — see [Forms and binding](../guides/ui-forms-and-validation.md).
- **`components-showcase` broadcasts to topics nothing subscribes to.** It uses the low-level
  `WasmRmiServerEngine.broadcastPush(String, Object)` rather than a typed `EventTopic`, and no client
  registers a listener. Do not copy that pattern; use `EventTopic` and `ServerEvents.on` as
  `chat-events` does.
