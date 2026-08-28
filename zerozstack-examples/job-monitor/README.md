# job-monitor

A real-time deployment pipeline monitor demonstrating **shared reactive signals** (`Signals.shared(...)`) — zero REST, zero polling, zero DTOs, and zero client status-fetching RMI calls.

## What it demonstrates

* **Shared Signal State (`JobSignals.STATUS`)** — declared once in the shared module (`Signals.shared(JobStatus.idle())`). The server updates status by setting a new `JobStatus` instance, and all connected Wasm clients update automatically.
* **Retained Value for Late Joiners** — a second browser window opened mid-job receives the retained current status immediately on connection, with no snapshot fetch and no merge logic needed.
* **Pure Client-Side Reactivity (`Effect`)** — UI components (`Progress` bar, `Steps` pipeline, `StatusDot`, `Badge`, and "Start job" `Button`) are bound to `JobSignals.STATUS` via `Effect.create(...)`.
* **Zero Client-Side Polling** — the only client-to-server call is `startJob()`.
* **Virtual Threads on Server** — `JobRunner` (`JobServiceImpl`) launches a Java 21 virtual thread (`Thread.ofVirtual()`) advancing through "Queued" → "Building" → "Testing" → "Deploying" → "Done" over ~20s, with a ~25% chance of failing partway through.

## How to run it

Build the reactor once from the repository root:

```bash
mvn install
```

Then double-click `run.bat` in this directory or run from terminal:

```bash
cd zerozstack-examples/job-monitor/job-monitor-server
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer --dev-login
```

`--dev-login` switches on the built-in development accounts; without it nothing can sign in. Open
`http://localhost:8087` in two browser windows (log in as `demo/demo` or `admin/admin`). Click "Start job" in one window and watch both windows track progress in real time.
