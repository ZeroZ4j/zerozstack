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

```bash
mvn -B archetype:generate -DarchetypeGroupId=com.zeroz4j -DarchetypeArtifactId=zerozstack-archetype -DarchetypeVersion=0.4.1 -DgroupId=com.smoke -DartifactId=smokeapp -Dversion=1.0.0-SNAPSHOT -Dpackage=com.smoke
```

Copy the fixtures over the generated sources — `fixtures/shared/*` into
`smokeapp-shared/src/main/java/com/smoke/`, `fixtures/server/*` into
`smokeapp-server/src/main/java/com/smoke/server/`, and `fixtures/client/ClientApp.java` over the
generated one — then build and start it:

```bash
cd smokeapp && mvn -B install && cd smokeapp-server && java -cp "target/classes:target/libs/*" com.smoke.server.ServerApp
```

`ServerApp` in the fixtures binds **8100** rather than the archetype's 8080, which is commonly taken.
Then, with `playwright` installed:

```bash
node smoke-test.mjs http://localhost:8100
```

It exits non-zero if any check fails.

## Also worth eyeballing

- `smokeapp-shared/target/classes` should contain `Message_Serializer.class`,
  `EchoService_Stub.class` and one `com/zeroz4j/generated/BinaryPackableRegistrar_*.class`. Empty
  means the processor did not run.
- The server log should contain **no** `WELD-000119` (classes skipped over a missing servlet API) and
  **no** `WELD-001409` (duplicate jars in `target/libs`).
