# todo-signals

A task board demonstrating **zeroz4j's reactive Signals** (`ValueSignal`, `Computed`, `Effect`) in isolation — all interaction after the initial load is client-side, so nothing distracts from the reactive model.

## What it demonstrates

* **Source state in signals** — the task list and the active filter are `ValueSignal`s, changed only through immutable updates (`tasks.update(...)` returning a new list), so equality-based change detection sees every change.
* **Derived state with `Computed`** — the visible (filtered) task list and the remaining-count recompute lazily whenever their dependencies change. Note that nothing "wires" them to the sources: dependencies are tracked automatically when the computation reads a signal.
* **Rendering with `Effect`** — the list, the summary badge, and the filter-button highlighting are each an `Effect`. They are the only code paths that write to the UI, so the view can never drift from the state — contrast with the manual `render()` bookkeeping in `chat-events`.
* **Two-way component binding** — the theme toggle uses `bindValue(signal)` to stay in sync with a `ValueSignal<Boolean>` in both directions.
* **Lifecycle hygiene** — every `Effect` returns a `Disposable`, and `Computed` instances are disposed too; `TodoView.dispose()` releases everything.

The server's only role is serving the Wasm client and providing the seed tasks over RMI. For server-to-client push, see the `chat-events` example; for implicit state mirroring, see `chat-livesync`.

## Running

Build the reactor once from the repository root (`mvn install -DskipTests`), then
double-click `run.bat` in this folder (or run it from a terminal) — it launches the
server with plain `java`, no Maven involved. Alternatively, by hand:

```bash
mvn install -DskipTests
cd zerozstack-examples/todo-signals/todo-signals-server
mvn exec:java -Dexec.mainClass="com.zeroz4j.example.server.ExampleServer"
```

Open `http://localhost:8084`, add and complete tasks, and switch filters — every visible change flows from a signal.
