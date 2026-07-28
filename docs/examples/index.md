# Examples

Seven runnable applications under `zerozstack-examples/`. Each isolates one part of the framework, and CI
builds all of them on every push — so the code in them is always current, which is not true of every
page in this documentation.

## Running any of them

Build once from the repository root, then start one example. They all bind port 8080, so run one at a
time.

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

Every example uses the same main class, `com.zeroz4j.example.server.ExampleServer`. Then open
<http://localhost:8080>.

Four examples require signing in — `chat-events`, `chat-livesync`, `job-monitor` and
`components-showcase`, which set `zeroz.security.mode=dev` and present a `Login` component. Use
`demo` / `demo`, or `admin` / `admin` for the admin-restricted operations. The other three
(`todo-signals`, `form-signup`, `inventory-crud`) connect anonymously and open straight into the app.

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
