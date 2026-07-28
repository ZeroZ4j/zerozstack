# chat-livesync

A real-time chat demonstrating **`@LiveSync` implicit state synchronization**: the server
mutates a shared `LiveChatState` object and calls `syncEngine.notifyChanged(...)` — every
client's instance updates in place, no events, no explicit subscriptions.

See `chat-events` for the explicit-events counterpart and `docs/LIVESYNC.md` for the
full feature guide, including the two-way `@ClientWritable` direction.

## Running

Build the reactor once from the repository root (`mvn install -DskipTests`), then
double-click `run.bat` in this folder — it launches the server with plain `java`.

Open `http://localhost:8080` in two browser windows and sign in as a different demo
user in each — `demo` / `demo` and `admin` / `admin` — then chat between them. The
credentials are validated server-side during the WebSocket handshake (dev-mode login).
