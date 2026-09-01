# Quickstart

Build ZeroZ Stack and run a working application in about five minutes. The build and run commands on this
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
git clone https://github.com/ZeroZ4j/zerozstack.git
cd zerozstack
mvn clean install -DskipTests
```

Expect a couple of minutes with a warm Maven cache, and considerably longer on the first run while
dependencies download.
The build compiles the framework, runs the annotation processor, compiles all twelve example
clients with TeaVM, and installs everything into your local repository.

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

Every example has a port of its own, so you can leave several running side by side.
`todo-signals` is on **8084**; the full list is in [Examples](../examples/index.md). To put one
somewhere else, add `--port 9000` to the command, or pass the number to `run.bat`:
`run.bat 9000`.

!!! note "`mvn exec:java` never works, and most examples have no runnable jar"
    There is no `exec-maven-plugin` anywhere, so `mvn exec:java` fails in every module. Eight of the
    twelve examples also have no runnable jar — `todo-signals`, `chat-events`, `chat-livesync`,
    `job-monitor`, `form-signup`, `inventory-crud`, `components-showcase` and
    `payments-datamodels` — because no shade or assembly plugin is configured for them. For those
    eight, the classpath command above is the way, and it is what `run.bat` does. The four added in
    0.6.0 — `routing-tour`, `oidc-login`, `scoped-signals` and `pwa-install` — do build one, so
    `java -jar …-server.jar` works for those.

**What you should see.** Weld and Helidon start up, and the last lines report the server listening.
The console stays open; stop it with `Ctrl+C`.

## 3. Open it

Go to **<http://localhost:8084>**.

`todo-signals` connects anonymously, so you land straight in the application. Add a task, toggle it,
filter the list. Everything you see is Java compiled for the browser — the visible list and the
remaining count are `Computed` values, and the rendering is driven by `Effect`.

### Examples that do require signing in

Four examples show a `Login` component: **`chat-events`**, **`chat-livesync`**, **`job-monitor`**
and **`components-showcase`**. `todo-signals`, `form-signup`, `inventory-crud` and
`payments-datamodels` connect anonymously.

**`routing-tour`** and **`scoped-signals`** also need an identity, but take the credentials from the
URL — `?user=admin&password=admin` — so two windows can be open as different users at once.
**`oidc-login`** signs in against a real Keycloak instead; see its README.

The accounts those six use are the framework's built-in development ones, and **starting a server
does not switch them on.** Ask for them with `--dev-login`:

```bash
java -jar scoped-signals-server/target/scoped-signals-server-0.8.0-SNAPSHOT.jar --dev-login
```

The `run.bat` scripts pass the flag already. A server running this way prints a warning saying so.

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
| `chat-livesync` | LiveSync both ways — a synced object driving an `Effect`, plus a topic box anybody can type into |
| `components-showcase` | The component library gallery |
| `routing-tour` | `@Route` views, nested layouts, typed parameters, loaders |
| `scoped-signals` | One signal value per tenant, user or browser |
| `pwa-install` | Installing the application, and a manifest built per request |
| `oidc-login` | Signing in against a real Keycloak with OpenID Connect |
| `payments-datamodels` | A record, a sealed family of types, and a shared base class, all crossing the wire |

## Next steps

- **[Choosing how state moves](../decide/index.md)** — read this before you build anything. Picking
  the wrong propagation mechanism is the most common source of trouble in ZeroZ Stack applications.
- [Troubleshooting](../guides/troubleshooting.md) — if something does not work, and especially if
  nothing at all happens.
- [Declaring the types that cross the wire](../guides/data-models.md) — the first thing you write in
  a new application: a class, a record, or a sealed family.
- [Testing an application](../guides/testing.md) — start a server inside a test, in one process.
- [Limitations](../reference/limitations.md) — what this version does not do.

## Starting your own project

Use the Maven archetype. It scaffolds the three-module layout with TeaVM, the annotation processor and
Helidon already wired up.

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.zeroz4j \
  -DarchetypeArtifactId=zerozstack-archetype \
  -DarchetypeVersion=0.8.0 \
  -DgroupId=com.example \
  -DartifactId=myapp \
  -Dversion=1.0.0-SNAPSHOT
```

The framework version is pinned automatically to the version of the archetype you generated from.
Override it with `-Dzeroz4jVersion=...` if you need a different one.

The generated project also carries its own **`AGENTS.md`**, naming that same version and holding the
rules an AI coding assistant needs before it can write anything here. If you work with one, point it
at that file first. The full rule list for exactly your version travels inside the jar you already
depend on, at `META-INF/zeroz4j/AGENTS.md` in `zerozstack-shared-api`; the generated page says how to
read it. Prefer both to anything found online, because the published documentation and the Context7
index follow this repository's main line and describe features a released version may not have.

Then build and run it exactly like an example:

```bash
cd myapp
mvn install
cd myapp-server
java -cp "target/classes;target/libs/*" com.example.myapp.server.ServerApp
```

**A generated project has three build commands, not one, and the difference is most of the time a
build takes.** `mvn compile` and `mvn test-compile` run `javac` and stop — they do **not** compile
the user interface for the browser, which is what makes them the right command for an ordinary
"did that compile" check. `mvn install`, above, builds the browser bundle in a readable,
unoptimized form and gives you a runnable application. `mvn install -Pproduction` optimizes and
minifies it, and is the shape you ship. On a freshly generated project with a warm Maven cache those
are roughly 5, 16 and 17 seconds.

Two things follow. The browser compiler accepts a smaller language than `javac` does, so client code
can pass the quick check and still fail to compile — run a full build before you believe client work
is finished, and give an automated pipeline `mvn verify` rather than `mvn test`, which stops before
the bundle is built. And build and run `-Pproduction` before you ship: minification renames things,
and that has broken this framework before. The generated `README.md` and `AGENTS.md` both say so.

You should get a styled card at <http://localhost:8080> reading "It works". It is a small page on
purpose, but everything in it is the real thing: the card and its text are components from
`zerozstack-ui-components`, the words ask for a size by name from `TextStyle`, and the page is put on
screen with `Component.replaceContents`, which is the one correct way to swap what is inside an
element. Read the comments in the generated `ClientApp.java` — they say why each of those is what it
is.

The stylesheet comes from the two lines the generated `index.html` already carries, explained in
[Where the styles come from](../UI_COMPONENTS.md#where-the-styles-come-from). **If the page comes up
as bare black-on-white text, those two lines are not arriving** — that is the one failure this page
can have that still looks like it started correctly.

For idiomatic use of signals, RMI and the rest of the component library, read the examples above.
