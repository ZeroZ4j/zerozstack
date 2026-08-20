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

!!! warning "On Linux, sort the classpath"
    A `libs/*` wildcard expands in **directory order** — alphabetical on Windows, arbitrary on
    Linux. One of the arbitrary orders loads Helidon's CDI extensions in a sequence where the
    WebSocket routing registers after the server was already built. The symptom: HTTP works, the
    page loads, and **every WebSocket handshake answers 404 — on Linux only**. Found by running
    the same jars on both systems; any deterministic order fixes it. On Linux, launch with a
    sorted explicit classpath:

    ```bash
    java -cp "target/classes:$(ls target/libs/*.jar | sort | tr '\n' ':')" com.mycompany.server.ServerApp
    ```

    The generated `Dockerfile` already does this, and on Windows the wildcard happens to be safe
    because the filesystem returns sorted entries.

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

## Settings a real deployment needs

Whichever shape you ship, set these before anyone outside your machine can reach the application.
They all default to the behaviour that is convenient during development, which is not the behaviour
you want in front of the internet.

| Property | Set it to | Why |
|---|---|---|
| `zeroz.hosts` | Every name the application is reached by, e.g. `app.example.com` | Refuses a handshake addressed to a name you do not serve. Without it, an attacker who points their own domain at your server has a page that can talk to your application from a visitor's browser. |
| `zeroz.clientId.secret` | A long random string, the same on every node | The key that signs the browser id. Generated at startup when unset, so a restart logs everyone's browser out and other nodes reject each other's ids. |
| `zeroz.origins` | Leave unset, unless the page is served from a different host than the socket | Unset means same-origin only, which is what you want. |
| `zeroz.security.mode` | **Leave unset** | Setting it to `dev` switches on two accounts whose passwords are printed in this documentation. |
| `zeroz.ws.maxBinaryMessageBytes` | e.g. `8388608` | See below. |

```bash
java -Dzeroz.hosts=app.example.com \
     -Dzeroz.clientId.secret=$MY_SECRET \
     -Dzeroz.ws.maxBinaryMessageBytes=8388608 \
     -jar myapp-server.jar
```

**Serve it over HTTPS.** The identity cookie is marked `Secure`, and TLS is what stops a browser
accepting a name that has been repointed at your server. Behind a proxy that terminates TLS, set
`zeroz.clientId.secureCookie=true` so the cookie keeps that mark.

The development accounts are described in
[Authentication and authorization](security-auth.md#development-authentication). The examples take a
`--dev-login` flag to switch them on; nothing switches them on by itself, and a server that has them
on says so at startup.

## Shape 4: a WAR on a Jakarta EE server

Everything above assumes ZeroZ Stack brings its own server. It does not have to. To deploy into an
application server — WildFly, Payara, Open Liberty, TomEE — take `zerozstack-server-jakarta` instead
of `zerozstack-server-helidon`:

```xml
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-server-core</artifactId>
</dependency>
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-server-jakarta</artifactId>
</dependency>
```

That module carries the three things every WAR otherwise had to work out for itself:

| Class | Does | Needs configuration? |
|---|---|---|
| `Zeroz4jServletBootstrap` | Calls `BinaryRegistry.init()` at startup, so the generated serializers load | No — `@WebListener` |
| `Zeroz4jWebSocketConfig` | Publishes the `/wasm-rmi` endpoint, which container scanning does not find inside a dependency jar | No |
| `ManagedThreadFactoryProvider` | Runs RMI calls on container threads (see below) | No — registered via `META-INF/services` |
| `Zeroz4jShellServlet` | Serves the client bundle and the shell for client-route deep links | **Yes** — map it yourself |

The servlet is deliberately unmapped: where it sits is the deployment's decision, and a WAR with its
own servlets must be able to take this module without something claiming `/`. Map it in `web.xml`:

```xml
<servlet>
    <servlet-name>zeroz-shell</servlet-name>
    <servlet-class>com.zeroz4j.server.jakarta.Zeroz4jShellServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>zeroz-shell</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

**Do not add `zerozstack-server-jaxrs` to a WAR** unless you want its catch-all. That module carries a
JAX-RS application at `/` answering every unmatched path — right for a standalone server, and a
collision inside a WAR that has its own servlets. It is a separate module precisely so you can leave
it out; `zerozstack-server-core` contains no JAX-RS type at all.

### Container threads

By default the framework dispatches RMI calls onto virtual threads it creates itself. Inside an
application server that is a problem: the container attaches thread-locals — the naming context behind
`java:comp/env/…`, the transaction context, the security context — before calling application code,
and a thread the container did not create has none of them. A service doing a JNDI lookup, or holding
an `@Resource` resolved lazily on the calling thread, then fails a long way from the cause.

It cannot be repaired from inside such a thread by handing the work to a `ManagedExecutorService`:
there is no context on that thread to capture. It has to be right at thread *creation*, which is why
the SPI is a `ThreadFactory`:

```java
public final class MyThreadFactoryProvider implements SessionThreadFactoryProvider {
    @Override
    public ThreadFactory threadFactory() {
        return InitialContext.doLookup("java:comp/DefaultManagedThreadFactory");
    }
}
```

`zerozstack-server-jakarta` already registers exactly this, so the dependency is normally all you
need. Point it elsewhere with `-Dzeroz.threads.jndiName=…` if your container publishes its factory
under another name. If the lookup fails the framework logs a warning and falls back to virtual
threads — degraded but running, rather than a deployment that will not start.

!!! warning "Container threads are platform threads"
    A Jakarta EE 10 `ManagedThreadFactory` cannot produce virtual threads. Inside a container that is
    the right trade — context matters more than cheap threads — but it *is* a trade, and worth
    knowing before a load test surprises you.

The framework's side of this contract is narrow: **calls are dispatched on threads the supplied
factory produced.** Whether a particular container's factory really carries naming, transaction and
security context is that container's contract, and worth asserting in your own integration test.

### WebSocket limits

Two properties, both unset by default so the container's own configuration wins:

| Property | Effect |
|---|---|
| `zeroz.ws.maxBinaryMessageBytes` | Largest binary message the endpoint accepts |
| `zeroz.ws.idleTimeoutMinutes` | How long a silent connection is held before closing |

**Set the first one.** The engine's `@OnMessage` takes a whole message — there is no partial-message
handling — so a response larger than the container's binary buffer does not raise an error, it closes
the socket. Container defaults are small, and one page of records can exceed them. The symptom is a
connection that drops under load with nothing in the log to explain it.

```
-Dzeroz.ws.maxBinaryMessageBytes=8388608 -Dzeroz.ws.idleTimeoutMinutes=30
```

The idle timeout matters for a different reason: without one, an abandoned browser tab holds a
session and its server-side resources indefinitely. The client's automatic reconnect means closing an
idle connection is invisible to a user who comes back.
