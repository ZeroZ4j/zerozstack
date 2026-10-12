# Running the server on Vidocq instead of Helidon

This page is for a developer who wants to try a ZeroZ Stack application on the Vidocq runtime, and ends with that application running from the command line.

!!! warning "Experimental"
    `zerozstack-server-vidocq` runs on Vidocq 0.3.0 (Vidocq's version, not ZeroZ Stack's) <!-- version-check: Vidocq's own version, not ours -->
    and needs **Java 25**. Eleven examples are known to run on it: `chat-events`, `chat-livesync`, `pwa-install`, `todo-signals`,
    `job-monitor`, `form-signup`, `inventory-crud`, `components-showcase`, `routing-tour`, `scoped-signals`
    and `payments-datamodels`. `oidc-login` has not been tried. It is not part of a default build.

## When to use this

- You want to see the server run without Helidon, Weld or Tyrus on the classpath.
- You are on Java 25.
- Do not use it for a production deployment. Take `zerozstack-server-helidon`, or
  `zerozstack-server-jakarta` for a WAR.

## What it is

Vidocq is a set of Java libraries. This module uses two of them:

- **Chappe**, its HTTP and WebSocket server, takes the requests.
- **Vauban**, its CDI container, finds your beans and the framework's engine.

`zerozstack-server-core` runs unchanged. The module's job is the gap between Chappe's WebSocket and the
Jakarta WebSocket `Session` the engine is written against, plus the three addresses a standalone
server answers: `/wasm-rmi` (the live connection), `/zeroz4j-upload` (file upload) and everything
else as static content, with the shell as the fallback for client routes. It also serves the
application's own JAX-RS resources, described in the next section.

## One start call, whichever container

Application code does not name the container. It calls the same class under Helidon and Vidocq:

```java
import com.zeroz4j.server.Zeroz4jServer;

public final class Main {
    public static void main(String[] args) {
        Zeroz4jServer.start(8080, "My application").join();
    }
}
```

`Zeroz4jServer` is in `zerozstack-server-core`. It starts the server binding it finds on the
classpath. A binding is the module that starts one runtime: `zerozstack-server-helidon` registers
`helidon`, and `zerozstack-server-vidocq` registers `vidocq`.

| On the classpath | What `start` does |
|---|---|
| One binding | Uses it, and logs which. |
| None | Fails, naming the two artifacts. A WAR does not call `start`. |
| Both | Fails, naming them, unless `-Dzeroz.server.binding=helidon` or `=vidocq` picks one. |

`start(int port, String appName)`, `port()`, `join()` and `close()` behave the same on both. Port `0`
picks a free port on Vidocq. `Zeroz4jVidocqServer` is still there and does the work, but an
application has no reason to call it.

## Build the binding

The module is in the build only under the `vidocq` profile, because it compiles for Java 25 and the
rest of the repository compiles for Java 21. A build without the profile is unchanged.

```bash
# JDK 25 on the path
mvn clean install -Pvidocq -DskipTests
```

## Switch an application

**A project generated from the archetype** switches with a profile and no source edit:

```bash
mvn clean install -Pvidocq     # Vidocq, JDK 25
mvn clean install -Phelidon    # Helidon, JDK 21 or later
```

Use `clean` when switching, so `target/libs` holds one container's jars. The run command does not
change. The archetype asks `container` when it generates a project (`helidon` if you do not answer,
or `-Dcontainer=vidocq`); the answer only sets which of the two is the default. `mvn verify
-Ppackage` works for both. The generated `Dockerfile` takes its Java version from that default, so
pass `--build-arg JAVA_VERSION=25` when you built with `-Pvidocq` in a Helidon project.

**In a project you wrote by hand**, depend on the binding and leave Helidon out:

```xml
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-server-vidocq</artifactId>
</dependency>
```

`zerozstack-bom` manages the version. The binding does not need `zerozstack-server-jaxrs`.

## Your own JAX-RS resources

A class annotated `@Path` in your server module is served, with CDI injection, written the same as
under Helidon:

```java
@Path("/client-log")
public class ClientLogResource {
    @Inject LogSink sink;                      // any bean works

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response log(@QueryParam("level") String level, String message) {
        sink.add(level, message);
        return Response.noContent().build();
    }
}
```

- **Found at runtime.** The binding looks through the beans for classes with `@Path` or `@Provider`.
  Nothing is generated at build time, so the application's build needs no annotation processor and no
  plugin. The class must sit in a jar or folder with `META-INF/beans.xml` (the same as for any bean
  here). `Cassini` is Vidocq's JAX-RS 4.0 implementation; the binding depends on it, so an application
  adds nothing.
- **Order.** `/wasm-rmi` and `/zeroz4j-upload` are always the framework's. Next, a request goes to a
  resource if one has a method whose path matches (any HTTP verb; a wrong verb gets 405). Anything
  else is static content, and a path nothing claims gets the shell page. A resource that answers 404
  itself keeps its own 404. An application with no `@Path` class behaves exactly as before.
- **`Application`.** An `Application` subclass among the beans is used: its `@ApplicationPath` becomes
  a prefix (resources answer under it and nowhere else), and its `getClasses()` are added. With
  several, the first by class name is used and a warning is logged. Not yet covered by a test.
- **Resource scope.** A `@Path` class with no scope annotation is request scoped, as JAX-RS says.
- **Dependency clash.** A server that depends on `jakarta.jakartaee-api` (JAX-RS 3.1 classes) should
  exclude it when switching to Vidocq, as the `pwa-install-vidocq` module does; the Vidocq jars bring
  the individual Jakarta APIs they need.
- **Not verified.** Only plain resources were tried: the shapes in this module's tests
  (`@GET`/`@POST`, `@PathParam`, `@QueryParam`, a `String` body, `Response`, `@Inject`,
  `@Context HttpHeaders` and `UriInfo`). Filters, exception mappers, JSON bodies, `@Context`
  `SecurityContext` and streaming (Server-Sent Events) have not been tried here. The claim check uses
  Cassini's router class from an internal package, which is safe only at the pinned version.

## Run the examples

`VidocqLauncher` is a main class that needs no code from the application: `--port <n>` sets the port
(else `-Dzeroz.port`, else 8080) and `--dev-login` turns on the built-in development accounts.
Each example has a launcher module that collects its jars without Helidon.

```bash
mvn clean package -Pvidocq -DskipTests \
    -pl zerozstack-examples/chat-events/chat-events-vidocq -am
cd zerozstack-examples/chat-events/chat-events-vidocq
java -cp "target/libs/*" com.zeroz4j.server.vidocq.VidocqLauncher --port 8195 --dev-login
```

Every other example in the list above is the same with its own `<example>-vidocq` module (except
`todo-signals`, which has none) and a port of your choice. Start `routing-tour` and `scoped-signals`
with their own main classes (`RoutingTourServer`, `ScopedSignalsServer`) so their set-up runs;
the rest work with either. `pwa-install` shows a JAX-RS resource (its manifest) on Vidocq; `pwa-install` shows a JAX-RS resource (its manifest) on Vidocq. Open
`http://localhost:<port>/` and sign in as `demo` / `demo`. The examples' own `ExampleServer` classes
are not needed here, and how the examples run on Helidon is unchanged. `VidocqLauncher` calls
`Zeroz4jServer.start` too, and asks for the `vidocq` binding by name unless
`zeroz.server.binding` is already set.

## Settings

The settings are the same as under Helidon, read from the same system properties.

| Property | Effect here |
|---|---|
| `zeroz.ws.maxBinaryMessageBytes` | Default 4 MB. A larger message closes that connection with code 1009, as under Helidon. |
| `zeroz.ws.idleTimeoutMinutes` | Unset by default. A connection silent for longer is closed with code 1001. |
| `zeroz.upload.maxBytes` | As for every binding; the upload address applies it. |
| `zeroz.origins`, `zeroz.hosts`, `zeroz.security.mode` | As for every binding. |

## Where it differs from Helidon

**The browser-id cookie is set on the page only.** Chappe 0.3.0 writes a fixed `101` response and <!-- version-check: Vidocq's own version, not ours -->
cannot add a header to it. Under Helidon the handshake can set the cookie if the page did not.
Here the cookie is set when the HTML page is served, so a client that opens the WebSocket without
loading the page first has an id for that connection that its browser never stores.

**The adapter enforces the two limits, not the server.** Chappe has no per-connection message-size or
idle-timeout setting. The adapter reads the limits the engine sets on each connection and applies
them. Chappe reads a whole message (up to 64 MB) before the adapter can measure it, so the 4 MB
ceiling stops the work, not the memory for that one message.

## Limits

- Experimental: it has been run against the examples listed at the top and the module's own tests.
  For the seven added last, one scripted exchange each over a real socket worked: shared-signal
  pushes (`job-monitor`), per-user and per-browser signals kept apart (`scoped-signals`), server-side
  validation (`form-signup`), create/read/update/delete (`inventory-crud`), role refusal and a file
  upload (`components-showcase`), records and a sealed family (`payments-datamodels`), deep links and
  the admin/standard split (`routing-tour`). No browser has driven them on Vidocq.
- `routing-tour` has no server-side role check on its calls; its `/admin` guard is in the browser only.
- Java 25 only. The profile fails on an earlier JDK.
- `zerozstack-server-test` starts a Weld container, so tests for an application on this binding
  drive a real socket instead, as this module's own tests do.
- JaCoCo 0.8.12 cannot read Java 25 class files, so the module is built with coverage off.
