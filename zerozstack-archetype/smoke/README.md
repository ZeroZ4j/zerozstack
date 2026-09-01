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
| The first page is styled, not bare text | `index.html` brings in the stylesheet the components need |

## Running it

The generated project is deliberately minimal — it has no `@RmiService` and no shared signal — so the
fixtures in `fixtures/` add the smallest thing that exercises each path.

```bash
mvn -o install -DskipTests
```

**Build into a private repository, not the shared one.** This procedure installs a throwaway
`com.smoke` application, and nothing should be able to resolve that afterwards. Give every command
below a repository of its own with the shared one behind it as a read-only fallback, and the
throwaway artifacts land somewhere you can delete in one go:

```bash
REPO="-Dmaven.repo.local=$PWD/.m2smoke -Dmaven.repo.local.tail=$HOME/.m2/repository"
```

Pin the plugin coordinates. The bare `archetype:generate` prefix resolves against the current
project, so outside one Maven 3.9 fails with "requires a project to execute but there is no POM".
Use the version the repository is on - `0.8.0-SNAPSHOT` while 0.8.0 is unreleased.

```bash
mvn -B $REPO org.apache.maven.plugins:maven-archetype-plugin:3.3.1:generate -DarchetypeGroupId=com.zeroz4j -DarchetypeArtifactId=zerozstack-archetype -DarchetypeVersion=0.8.0-SNAPSHOT -DgroupId=com.smoke -DartifactId=smokeapp -Dversion=1.0.0-SNAPSHOT -Dpackage=com.smoke -DinteractiveMode=false
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
cd smokeapp && mvn -B $REPO install && cd smokeapp-server && java -cp "target/classes:target/libs/*" com.smoke.server.ServerApp
```

**Look at the page as well as the checks.** The generated project loads Tailwind and daisyUI in its
`index.html`, and its first screen is built out of real components, so a fresh project is styled the
moment it starts. If it comes up as bare black-on-white text, that stylesheet is not arriving and
the checks below will not tell you - they only look at the words.

`ServerApp` in the fixtures binds **8100** rather than the archetype's 8080, which is commonly taken.
Then, with `playwright` installed:

```bash
node smoke-test.mjs http://localhost:8100
```

It exits non-zero if any check fails, and writes a screenshot to `shots/smokeapp.png`. Node resolves
`playwright` from the script's own directory upwards, so either install it here or copy the script
next to an installation.

## Drop-recovery test

The second script proves the connection recovery added in 0.5.0 end to end: it starts the server
itself, kills it mid-session, asserts the built-in banner appears, restarts it, and asserts the
banner clears and shared-signal updates flow again — across a **full server restart**, the harshest
case. Stop any already-running smoke server first; this script owns the server lifecycle:

```bash
node drop-recovery-test.mjs /path/to/smokeapp/smokeapp-server
```

## Packaged-app variant

The smoke test can also run against the jpackage output instead of the classpath launch, which
proves CDI discovery survives packaging. Build with `mvn verify -Ppackage`, start
`smokeapp-server/target/dist/smokeapp/smokeapp.exe` (or `bin/smokeapp` on Linux) instead of the
`java -cp` line, and run `smoke-test.mjs` as above. Same five checks, same expected 5/5.

## Also worth eyeballing

- `smokeapp/AGENTS.md` should exist, and its second line under "The version this project is built
  on" should name a real version — `0.8.0-SNAPSHOT`, not the literal `${zeroz4jVersion}`. It should
  also still have its six `##` headings. Velocity, which filters the file, reads `##` as the start
  of a comment and drops the rest of the line without a word, so the headings are escaped in the
  archetype's copy and an accidental un-escaping shows up here as missing headings and nothing else.
- `smokeapp-shared/target/classes` should contain `Message_Serializer.class`,
  `EchoService_Stub.class` and one `com/zeroz4j/generated/BinaryPackableRegistrar_*.class`. Empty
  means the processor did not run.
- The server log should contain **no** `WELD-000119` (classes skipped over a missing servlet API) and
  **no** `WELD-001409` (duplicate jars in `target/libs`).
