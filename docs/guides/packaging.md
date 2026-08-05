# Packaging and running

How to turn a ZeroZ Stack application into something you can ship. Three supported shapes, one
hard rule.

## The rule: never shade a ZeroZ Stack server

`maven-shade-plugin` merges every jar into one. That breaks Weld, and it breaks it far from the
cause: Weld treats **each jar as a separate bean archive** with its own `beans.xml` and its own
discovery mode. Merging collapses that structure — the many `beans.xml` files collide and one
survives, its settings now applying to classes from every library, and `META-INF/services`
registrations overwrite each other unless every transformer is configured exactly right. The
result is beans that vanish, beans that appear twice ("WELD-001409: Ambiguous dependencies"), or
an application that deploys and quietly does nothing.

This is not fixable with more shade configuration. Use one of the shapes below; in all three,
every jar stays intact.

## Shape 1: the development layout (what the archetype builds)

`mvn package` produces the server jar plus every dependency in `target/libs/`. Launch with a
classpath:

```bash
cd myapp-server
java -cp "target/classes;target/libs/*" com.mycompany.server.ServerApp   # Windows
java -cp "target/classes:target/libs/*" com.mycompany.server.ServerApp   # Linux / macOS
```

The compiled client is embedded in the server's resources (`META-INF/resources`), so the server
is the whole application. This layout is the base for both shapes below. `target/libs` is emptied
before it is refilled on every build, so a version bump cannot leave two framework jars on the
classpath.

## Shape 2: a double-clickable executable (`jpackage`)

For handing the application to someone as a file. Projects generated from the archetype (0.5.1+)
carry a `package` profile:

```bash
mvn verify -Ppackage
```

This produces `myapp-server/target/dist/myapp/`:

```
myapp/
├── myapp.exe        ← the launcher (bin/myapp on Linux, myapp.app on macOS)
├── app/             ← your jar and every dependency jar, unmodified
└── runtime/         ← a bundled Java runtime
```

Ship the folder; the target machine needs no Java installation. The build uses the JDK's own
`jpackage` tool, so the build machine needs nothing beyond the JDK either. The image is built
**for the OS you build on** — a Windows build makes a Windows app; build on each OS you ship to.

Two details worth knowing:

- The EclipseStore data directory is resolved relative to the **working directory at launch**,
  same as when running from Maven. Set `zeroz4j.store.directory` to an absolute path for an app
  you distribute, or it will write `data/` wherever it was double-clicked from.
- To make an installer (`.msi`, `.deb`, `.dmg`) instead of a folder, change `--type app-image` in
  the profile to `--type msi` (Windows needs the free WiX toolset installed for this), `deb` or
  `dmg`. The folder form is the default because it needs no extra tooling anywhere.

## Shape 3: a container image (servers and cloud)

Projects generated from the archetype carry a `Dockerfile` at the root:

```bash
mvn package
docker build -t myapp .
docker run -p 8080:8080 myapp
```

The dependency jars are copied as their **own image layer**, below the app jar. Rebuilding after
a code change re-pushes only your application's few kilobytes; the framework layer is cached and
shared. No shading anywhere — the container launches the same plain classpath as shape 1.

## What about a single executable jar or a native binary?

- **Spring Boot-style nested jars** solve the merge problem with a custom launcher classloader —
  which is exactly the kind of environment Weld's archive scanner mis-handles. Not supported.
- **GraalVM native-image** is attractive and not yet realistic here: EclipseStore leans on JDK
  internals that native-image restricts. If that changes, this page will change.
- **jlink custom runtimes** require fully modularized dependencies, which Weld and friends are
  not. `jpackage` delivers the same "no Java install needed" result without that fight.

## Which shape when

| You want | Use |
|---|---|
| To develop and run locally | Shape 1, or just `run.sh` / `run.bat` |
| To give the app to a person or run it as a plain OS process | Shape 2 (`mvn verify -Ppackage`) |
| To deploy to a server, Kubernetes, or any cloud | Shape 3 (`docker build`) |
| One merged jar | Nothing — see the rule at the top |
