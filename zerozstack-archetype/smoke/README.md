# Archetype smoke test

Generates a project from `zerozstack-archetype`, builds it, starts it, and drives it with a headless
browser.

Run it before every release. The three blockers fixed in 0.4.1 — the annotation processor skipped on
JDK 23+, TeaVM's `main()` never invoked, and RMI services never discovered — all produced a project
that **compiled cleanly, started cleanly and served pages while not working**. Nothing short of an
end-to-end run catches that: each failure surfaces far from its cause, and none of them produces an
error at the point of the mistake.

## What it asserts

| Check | Proves |
|---|---|
| A shared `@DataModel` signal arrives in the browser | The annotation processor ran — a `Message_Serializer` exists |
| The view renders instead of the `Loading…` placeholder, and a WebSocket opens | `main()` was invoked |
| An RMI call returns its reply | The `@ApplicationScoped` implementation was discovered through the bean manager |
| No JavaScript errors | Nothing failed quietly |

## Running it

The generated project is deliberately minimal — it has no `@RmiService` and no shared signal — so the
fixtures in `fixtures/` add the smallest thing that exercises each path.

```bash
mvn -o install -DskipTests
```

Pin the plugin coordinates. The bare `archetype:generate` prefix resolves against the current
project, so outside one Maven 3.9 fails with "requires a project to execute but there is no POM".

```bash
mvn -B org.apache.maven.plugins:maven-archetype-plugin:3.3.1:generate -DarchetypeGroupId=com.zeroz4j -DarchetypeArtifactId=zerozstack-archetype -DarchetypeVersion=0.5.0 -DgroupId=com.smoke -DartifactId=smokeapp -Dversion=1.0.0-SNAPSHOT -Dpackage=com.smoke -DinteractiveMode=false
```

Copy the fixtures over the generated sources, keeping each one's subdirectory — the fixture packages
are `com.smoke.service` and `com.smoke.signals`, not `com.smoke`:

| Fixture | Destination |
|---|---|
| `fixtures/shared/service/EchoService.java` | `smokeapp-shared/src/main/java/com/smoke/service/` |
| `fixtures/shared/signals/SmokeSignals.java` | `smokeapp-shared/src/main/java/com/smoke/signals/` |
| `fixtures/server/*.java` | `smokeapp-server/src/main/java/com/smoke/server/` |
| `fixtures/client/ClientApp.java` | over the generated `smokeapp-client/src/main/java/com/smoke/client/ClientApp.java` |

Then build and start it:

```bash
cd smokeapp && mvn -B install && cd smokeapp-server && java -cp "target/classes:target/libs/*" com.smoke.server.ServerApp
```

`ServerApp` in the fixtures binds **8100** rather than the archetype's 8080, which is commonly taken.
Then, with `playwright` installed:

```bash
node smoke-test.mjs http://localhost:8100
```

It exits non-zero if any check fails, and writes a screenshot to `shots/smokeapp.png`. Node resolves
`playwright` from the script's own directory upwards, so either install it here or copy the script
next to an installation.

## Drop-recovery test

The second script proves the 0.5.0 connection-recovery story end to end: it starts the server
itself, kills it mid-session, asserts the built-in banner appears, restarts it, and asserts the
banner clears and shared-signal updates flow again — across a **full server restart**, the harshest
case. Stop any already-running smoke server first; this script owns the server lifecycle:

```bash
node drop-recovery-test.mjs /path/to/smokeapp/smokeapp-server
```

## Also worth eyeballing

- `smokeapp-shared/target/classes` should contain `Message_Serializer.class`,
  `EchoService_Stub.class` and one `com/zeroz4j/generated/BinaryPackableRegistrar_*.class`. Empty
  means the processor did not run.
- The server log should contain **no** `WELD-000119` (classes skipped over a missing servlet API) and
  **no** `WELD-001409` (duplicate jars in `target/libs`).
