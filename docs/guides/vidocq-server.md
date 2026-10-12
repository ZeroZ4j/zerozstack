# Running the server on Vidocq instead of Helidon

This page is for a developer who wants to try a ZeroZ Stack application on the Vidocq runtime, and ends with that application running from the command line.

!!! warning "Experimental"
    `zerozstack-server-vidocq` runs on Vidocq 0.3.0 (Vidocq's version, not ZeroZ Stack's) <!-- version-check: Vidocq's own version, not ours -->
    and needs **Java 25**. Two examples are known to run on it, `chat-events` and `chat-livesync`.
    Nothing else has been tried, and it is not part of a default build.

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
else as static content, with the shell as the fallback for client routes.

The entry point mirrors Helidon's `Zeroz4jServer`, under a name of its own so both can be on one
classpath:

```java
import com.zeroz4j.server.vidocq.Zeroz4jVidocqServer;

public final class Main {
    public static void main(String[] args) {
        Zeroz4jVidocqServer.start(8080, "My application").join();
    }
}
```

`start(int port, String appName)`, `port()`, `join()` and `close()` behave as they do under Helidon.
Port `0` picks a free port.

## Select it

The module is in the build only under the `vidocq` profile, because it compiles for Java 25 and the
rest of the repository compiles for Java 21. A build without the profile is unchanged.

```bash
# JDK 25 on the path
mvn clean install -Pvidocq -DskipTests
```

In your own server module, depend on the binding and leave Helidon out:

```xml
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-server-vidocq</artifactId>
</dependency>
```

`zerozstack-bom` manages the version. The binding does not need `zerozstack-server-jaxrs`.

## Run the two examples

`VidocqLauncher` is a main class that needs no code from the application: `--port <n>` sets the port
(else `-Dzeroz.port`, else 8080) and `--dev-login` turns on the built-in development accounts.
Each example has a launcher module that collects its jars without Helidon.

```bash
mvn clean package -Pvidocq -DskipTests \
    -pl zerozstack-examples/chat-events/chat-events-vidocq -am
cd zerozstack-examples/chat-events/chat-events-vidocq
java -cp "target/libs/*" com.zeroz4j.server.vidocq.VidocqLauncher --port 8195 --dev-login
```

`chat-livesync` is the same with `chat-livesync-vidocq` and a port of your choice. Open
`http://localhost:<port>/` and sign in as `demo` / `demo`. The examples' own `ExampleServer` classes
are not used here, and how the examples run on Helidon is unchanged.

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

- Experimental: it has been run against `chat-events`, `chat-livesync` and the module's own tests.
- Java 25 only. The profile fails on an earlier JDK.
- Application-defined JAX-RS resources (for example the `pwa-install` example's manifest resource)
  are not served; only the three addresses above are.
- `zerozstack-server-test` starts a Weld container, so tests for an application on this binding
  drive a real socket instead, as this module's own tests do.
- JaCoCo 0.8.12 cannot read Java 25 class files, so the module is built with coverage off.
