# Quickstart

Build ZeroZ4j and run a working application in about five minutes. The build and run commands on this
page have been executed against the current `main`.

## Prerequisites

| Requirement | Version | Check |
|---|---|---|
| JDK | **21** or later | `java -version` |
| Maven | 3.9 or later | `mvn -version` |
| Browser | current Chrome, Edge or Firefox | — |
| Disk | ~1 GB for the Maven cache and build output | — |

JDK 21 is not optional: the server uses virtual threads, and the build targets release 21.

## 1. Clone and build

```bash
git clone https://github.com/fschoning/zeroz4j.git
cd zeroz4j
mvn clean install -DskipTests
```

Expect a couple of minutes with a warm Maven cache, and considerably longer on the first run while
dependencies download.
The build compiles the framework, runs the annotation processor, compiles all seven example clients
with TeaVM, and installs everything into your local repository.

!!! warning "Always include `clean`"
    Each example server copies its dependencies into `target/libs`, and that directory is never
    pruned. Without `clean`, jars from earlier versions accumulate and the server starts with
    duplicate beans on the classpath, logging `WELD-ENV-002008 … this may result in incorrect
    behavior`. If you see that warning, run `mvn clean install -DskipTests` again.

## 2. Run an example

Start with `todo-signals` — it is the smallest, and it demonstrates the reactive model in isolation.

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

    Use `:` instead of `;` as the classpath separator on Linux and macOS.

Run one example at a time — they all bind port 8080.

!!! note
    There is no executable jar. `java -jar …-server.jar` and `mvn exec:java` do not work: the build
    configures neither a shade/assembly plugin nor `exec-maven-plugin`. The classpath invocation above
    is the supported way, and it is what `run.bat` does.

**What you should see.** Weld and Helidon start up, and the last lines report the server listening.
The console stays open; stop it with `Ctrl+C`.

## 3. Open it

Go to **<http://localhost:8080>**.

`todo-signals` connects anonymously, so you land straight in the application. Add a task, toggle it,
filter the list. Everything you see is Java compiled for the browser — the visible list and the
remaining count are `Computed` values, and the rendering is driven by `Effect`.

### Examples that do require signing in

Four of the seven examples enable development authentication by setting
`zeroz.security.mode=dev` in their `ExampleServer.main`, and show a `Login` component:
**`chat-events`**, **`chat-livesync`**, **`job-monitor`** and **`components-showcase`**. The other
three — `todo-signals`, `form-signup` and `inventory-crud` — do not, and connect anonymously.

When an example does ask you to sign in:

| Username | Password | Roles |
|---|---|---|
| `demo` | `demo` | `user` |
| `admin` | `admin` | `user`, `admin` |

These are hardcoded in `DevAuth` (`zerozstack-server-core`); the client passes them as WebSocket
handshake parameters and `DevAuth` validates them. Use `admin` for operations annotated
`@RolesAllowed("admin")`, such as `chat-events`' "clear history". Replace this provider before
deploying anything.

## 4. Try the others

Each example isolates one part of the framework. Read the decision guide alongside them, because the
differences between the three propagation examples are the point.

| Example | Demonstrates |
|---|---|
| `todo-signals` | Local signals, `Computed`, `Effect` |
| `form-signup` | Validation annotations enforced on both tiers |
| `inventory-crud` | Master-detail CRUD with derived KPIs |
| `chat-events` | Server events — deliberately without signals |
| `job-monitor` | A shared signal driven from a server-side virtual thread |
| `chat-livesync` | LiveSync — a synced object driving an `Effect` directly |
| `components-showcase` | The component library gallery |

## Next steps

- **[Choosing how state moves](../decide/index.md)** — read this before you build anything. Picking
  the wrong propagation mechanism is the most common source of trouble in ZeroZ4j applications.
- [Troubleshooting](../guides/troubleshooting.md) — if something does not work, and especially if
  nothing at all happens.
- [Limitations](../reference/limitations.md) — what this version does not do.

## Starting your own project

Use the Maven archetype. It scaffolds the three-module layout with TeaVM, the annotation processor and
Helidon already wired up.

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.zeroz4j \
  -DarchetypeArtifactId=zerozstack-archetype \
  -DarchetypeVersion=0.4.0 \
  -DgroupId=com.example \
  -DartifactId=myapp \
  -Dversion=1.0.0-SNAPSHOT
```

The framework version is pinned automatically to the version of the archetype you generated from.
Override it with `-Dzeroz4jVersion=...` if you need a different one.

Then build and run it exactly like an example:

```bash
cd myapp
mvn clean install
cd myapp-server
java -cp "target/classes;target/libs/*" com.example.myapp.server.ServerApp
```

You should get a page at <http://localhost:8080> reading "Zeroz4j App is running!". The generated
client is a deliberately minimal hello-world that writes to the DOM directly; to see idiomatic use of
`zerozstack-ui-components`, signals and RMI, read the examples above.
