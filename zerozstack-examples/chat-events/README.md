# chat-events

A real-time chat demonstrating **typed server events** (`EventTopic` / `EventPublisher` / `ServerEvents`) — deliberately without client-side Signals, so the events feature stands on its own.

## What it demonstrates

* **Typed topics, declared once** — `ChatEvents` in the shared module binds each wire name to its payload type. Server and client both compile against it; no duplicated topic strings, and a payload mismatch is a compile error.
* **Plain event handlers** — the view reacts to `MESSAGE_POSTED` and `HISTORY_CLEARED` by updating its components directly. No reactive framework required to consume events.
* **Snapshot-race handling** — the view subscribes *before* fetching history, then merges the snapshot with any events that arrived in flight (deduplicated via `ChatMessage` value equality).
* **Lifecycle hygiene** — every subscription returns a `Disposable`, released in `ChatView.dispose()`.
* **One POJO end-to-end** — the same `ChatMessage` class is persisted by EclipseStore, returned over RMI, broadcast as an event payload, and rendered by the Wasm UI. No DTOs, no mapping.

Note the trade-off this style accepts: view state lives in a plain list, and every mutation must remember to call `render()`. That is fine for a single render path like this chat — once state is rendered in several places or derived (counts, filters), reach for Signals: see the `todo-signals` example and the "Combining with Signals" section of `docs/SERVER_EVENTS.md`.

## How it differs from the other examples

| | chat-livesync | chat-events | todo-signals |
|---|---|---|---|
| Feature | `@LiveSync` implicit state sync | Typed server events | Client-side reactive Signals |
| Server → client | State object mutated in place | Discrete typed events | Initial data only (RMI) |
| Client state | Mirrored transparently | Plain fields + manual render | Signals + computed + effects |

## Running

Build the reactor once from the repository root (`mvn install -DskipTests`), then
double-click `run.bat` in this folder (or run it from a terminal) — it launches the
server with plain `java`, no Maven involved. Alternatively, by hand:

```bash
mvn install -DskipTests
cd zerozstack-examples/chat-events/chat-events-server
mvn exec:java -Dexec.mainClass="com.zeroz4j.example.server.ExampleServer"
```

Open `http://localhost:8080` in two browser windows and sign in as a different demo
user in each — `demo` / `demo` and `admin` / `admin` — then chat between them. The
credentials are validated server-side during the WebSocket handshake (dev-mode
login; see `DevAuth`), and the sender name on each message is the authenticated
principal, never client-supplied. Only `admin` may clear the history
(`@RolesAllowed("admin")`).
