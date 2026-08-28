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
They all default to the behavior that is convenient during development, which is not the behavior
you want in front of the internet.

| Property | Set it to | What it does |
|---|---|---|
| `zeroz.hosts` | Every name the application is reached by, e.g. `app.example.com` | The server accepts a connection only when it was addressed to one of these names. Unset, it accepts any name at all. |
| `zeroz.clientId.secret` | A long random string, the same on every node | The key that signs the browser id. Unset, a new one is made at every startup, so a restart gives everybody a new browser id and other nodes do not recognize this node's ids. |
| `zeroz.origins` | Leave unset, unless the page is served from a different host than the socket | Unset means the page must come from the same address as the socket, which is what you want. |
| `zeroz.security.mode` | **Leave unset** | Setting it to `dev` switches on two built-in accounts whose passwords are printed in this documentation. |
| `zeroz.ws.maxBinaryMessageBytes` | e.g. `8388608` | The largest message the server accepts. 4 MB unless you say otherwise; see below. |

```bash
java -Dzeroz.hosts=app.example.com \
     -Dzeroz.clientId.secret=$MY_SECRET \
     -Dzeroz.ws.maxBinaryMessageBytes=8388608 \
     -jar myapp-server.jar
```

**Serve it over HTTPS.** The identity cookie is marked `Secure`, which a browser only keeps on an
`https://` page. Behind a proxy that terminates TLS — where the application itself only ever sees
plain HTTP — set `zeroz.clientId.secureCookie=true` so the cookie keeps that mark anyway.

The development accounts are described in
[Authentication and authorization](security-auth.md#development-authentication). The examples take a
`--dev-login` flag to switch them on; nothing switches them on by itself, and a server that has them
on says so at startup.

## Every setting the framework reads

All of them are JVM system properties: `-Dname=value` on the command line. Every one has a working
default, and a fresh application needs none of them set.

### The connection

| Property | What it does | Unset |
|---|---|---|
| `zeroz.origins` | Which pages may open a connection. A comma-separated list, or `*` for no check | the page must come from the same address as the socket |
| `zeroz.hosts` | Which host names this deployment answers for. A comma-separated list; an entry with no port accepts that name on any port | any name is accepted |
| `zeroz.ws.maxBinaryMessageBytes` | Largest message the server accepts | **4 MB (4,194,304 bytes)** |
| `zeroz.ws.idleTimeoutMinutes` | How long a silent connection is held before closing | the container's own timeout |
| `zeroz.ws.maxConcurrentFramesPerSession` | Messages from one connection being handled at the same time | **32** |
| `zeroz.ws.maxPendingFramesPerSession` | Messages that may be waiting to go out on one connection | **256** |
| `zeroz.ws.maxPendingBytesPerSession` | Bytes that may be waiting to go out on one connection | **8 MB (8,388,608 bytes)** |
| `zeroz.ws.keepaliveMinIntervalMillis` | Shortest gap between two keepalive answers to one connection | **1000** |

### Who the caller is

| Property | What it does | Unset |
|---|---|---|
| `zeroz.clientId.secret` | The key that signs the browser id. Set the same value on every node | a new key at every startup |
| `zeroz.clientId.ttlDays` | How long a browser id stays valid | **365** |
| `zeroz.clientId.secureCookie` | Forces the `Secure` mark on the identity cookie on or off | follows the request: on for `https`, off for `http` |
| `zeroz.security.mode` | `dev` switches on the built-in `demo` and `admin` logins | off |

### Editing locks

| Property | What it does | Unset |
|---|---|---|
| `zeroz.livemutex.waitSeconds` | How long a caller waits for a lock somebody else holds | **30** |
| `zeroz.livemutex.requireAuthentication` | `true` allows locking only on signed-in connections | off |

### The record of what was sent to each browser

| Property | What it does | Unset |
|---|---|---|
| `zeroz.disclosure.maxHandlesPerClient` | Objects remembered as sent to one browser | **10,000** |
| `zeroz.disclosure.idleHours` | How long that record survives with no activity | **24** |

### File uploads

| Property | What it does | Unset |
|---|---|---|
| `zeroz.upload.maxBytes` | Largest file the server accepts | **25 MB (26,214,400 bytes)** |
| `zeroz.upload.passSeconds` | How long an upload permission stays usable | **60** |
| `zeroz.upload.tempDir` | Where a file is written while it arrives | `zeroz4j-uploads` inside the system temporary directory |

### Inside an application server

| Property | What it does | Unset |
|---|---|---|
| `zeroz.threads.jndiName` | The JNDI name of the thread factory RMI calls run on | `java:comp/DefaultManagedThreadFactory` |

### Logging in with OpenID Connect

Read only when `zerozstack-auth-oidc` is on the classpath. Full explanation in
[Logging in with OpenID Connect](oidc-auth.md).

| Property | What it does | Unset |
|---|---|---|
| `zeroz.oidc.issuer` | The realm URL that appears in your tokens' `iss` claim | **required** — startup fails without it |
| `zeroz.oidc.jwksUri` | Where the signing keys are published | Keycloak's location under the issuer |
| `zeroz.oidc.clientId` | This application's client id | none |
| `zeroz.oidc.audience` | The `aud` value a token must carry | the `azp` claim is compared with `clientId` instead |
| `zeroz.oidc.principalClaim` | Which claim becomes the user name | `preferred_username` |
| `zeroz.oidc.rolesClaim` | A flat claim holding the roles | Keycloak's own role structure is read |
| `zeroz.oidc.tenantClaim` | Which claim carries the tenant | none |
| `zeroz.oidc.tenantFromRealm` | `true` takes the tenant from the realm in the issuer URL | off |
| `zeroz.oidc.clockSkewSeconds` | How much clock difference an expiry check tolerates | **60** |

Storage has its own settings under a different prefix, `zeroz4j.store.*` — see
[Store modes](../store-modes.md).

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

That module carries the things every WAR otherwise had to work out for itself:

| Class | Does | Needs configuration? |
|---|---|---|
| `Zeroz4jServletBootstrap` | Calls `BinaryRegistry.init()` at startup, so the generated serializers load | No — `@WebListener` |
| `Zeroz4jWebSocketConfig` | Publishes the `/wasm-rmi` endpoint, which container scanning does not find inside a dependency jar | No |
| `ManagedThreadFactoryProvider` | Runs RMI calls on container threads (see below) | No — registered via `META-INF/services` |
| `Zeroz4jShellServlet` | Serves the client bundle and the shell for client-route deep links | **Yes** — map it yourself |
| `FileUploadServlet` | Receives file uploads at `/zeroz4j-upload` | No — `@WebServlet` |

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
application server that is a problem: the container attaches thread-locals — the naming context
behind `java:comp/env/…`, the transaction context, and the caller's identity — before calling
application code, and a thread the container did not create has none of them. A service doing a JNDI lookup, or holding
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
factory produced.** Whether a particular container's factory really carries naming, transactions and
the caller's identity is that container's contract, and worth asserting in your own integration test.

## How big a message can be, and what happens when one is too big

**The server accepts messages up to 4 MB** (`zeroz.ws.maxBinaryMessageBytes`), the same default
gRPC uses.

The framework sets that number itself rather than taking the container's. It uses the Jakarta
WebSocket API, which Helidon 4.0.8 implements by embedding Tyrus 2.1.5, and Tyrus starts each
connection's message limit at `Integer.MAX_VALUE` — about 2 GB. A message is assembled whole before
any of your code sees it, so that number is what the server would be prepared to hold in memory for
one connection.

An explicit setting always wins, up or down, so a deployment that already tuned this keeps its
number:

```
-Dzeroz.ws.maxBinaryMessageBytes=8388608 -Dzeroz.ws.idleTimeoutMinutes=30
```

The server logs the limit in force once at startup, naming the property, so a message that is
refused later can be explained without guessing.

### When a browser stops reading

`zeroz.ws.maxPendingFramesPerSession` and `zeroz.ws.maxPendingBytesPerSession` are about the other
direction: messages the server is trying to send.

The server sends a message by handing it to the operating system, which puts it on the network as
fast as the browser accepts it.
A browser that has stopped accepting — a laptop that went to sleep, a phone that lost signal, a tab
that is wedged — makes that hand-off stop part-way.
The server keeps the messages for that one connection in a queue until it starts moving again.

The queue has a size, and the two settings above are it.
Reaching either one closes that connection with WebSocket code `1013`, "try again later", and writes
a line to the log saying which limit was hit and what the setting is called.
An empty queue always accepts the next message however large it is, so a single big response is
never refused; the limits are on what piles up behind it.

Closing is deliberate.
A browser that is that far behind has already missed messages it will never see, so its copy of your
data is wrong whichever choice is made, and the client reconnects and asks for a fresh copy on its
own.
Holding the queue open instead would mean one connection deciding how much memory the server uses.

**Nothing else on the server waits for a connection in this state.**
Each connection has its own queue and its own thread that empties it, so a browser that has stopped
reading holds up its own messages and nothing else — not another browser's, and not a broadcast on
its way to everyone.

Raise the limits if you send large messages in bursts and you would rather a struggling connection
recovered than dropped.
Lower them if you have many connections and want a stalled one dealt with sooner.

```
-Dzeroz.ws.maxPendingFramesPerSession=512 -Dzeroz.ws.maxPendingBytesPerSession=16777216
```

**What a client sees when a message is too big: the connection closes.** There is no error response
and no exception you can catch. The engine's `@OnMessage` takes a whole message — there is no
partial-message handling — so an over-sized message never reaches framework code at all. The client
reconnects automatically, so the symptom is a socket that drops every time one particular call is
made.

If a response is genuinely over the limit, either raise the limit or return less: page the results,
or return identifiers and fetch details on demand.

!!! warning "This connection is not for file uploads"
    Do not send file contents over the RMI socket. It is sized for the messages an application
    exchanges, not for documents, images or video, and a big enough file simply closes the
    connection. Use [file upload](file-uploads.md), which posts to its own HTTP address and streams
    straight to disk.

### How many messages one connection may be running at once

One connection may have **32** messages being decoded and executed at the same time
(`zeroz.ws.maxConcurrentFramesPerSession`). A message that arrives while the connection is at its
limit waits its turn — nothing is dropped and no call fails, so a burst is served a few at a time.
The waiting happens on that one connection's read loop, so other connections carry on unaffected.

Decoding is where a small message turns into a large object graph, so the size limit above is a real
ceiling on memory only when the number of messages being decoded at once is bounded too.

### The idle timeout

`zeroz.ws.idleTimeoutMinutes` is unset by default, so an abandoned browser tab holds a session and
its server-side resources for as long as the container allows. The client reconnects by itself, so
closing an idle connection is invisible to a user who comes back.
