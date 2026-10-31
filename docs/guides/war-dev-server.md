# Running a WAR application in development

A WAR is deployed to a servlet container, and that container scans every jar on the classpath. With a
few hundred jars the scan is most of the start time. `zerozstack-dev-tomcat` runs the same application
on embedded Tomcat and Weld without scanning, so it starts in seconds from the IDE or from a test.

Use it when the application has servlets of its own (a sign-in form, a download), or maps the shell
servlet itself, so that the Helidon and Vidocq bindings cannot run it. An application with no servlets
runs on `zerozstack-server-helidon` already, see [Starting the server](starting-the-server.md).

## Take it with test or provided scope

```xml
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-dev-tomcat</artifactId>
    <scope>provided</scope>
</dependency>
```

The version comes from `zerozstack-bom`. Use `provided` when the dev `main` lives in `src/main`, so it
runs from the IDE and stays out of the WAR. Use `test` when the dev `main` lives in `src/test`. Never
`compile`: Tomcat and Weld have no place in a deployed WAR, and the WAR already takes
`zerozstack-server-jakarta`.

## Start it

```java
public static void main(String[] args) {
    try (Zeroz4jDevServer server = Zeroz4jDevServer.builder()
            .port(8080)
            .contextPath("/app")
            .beans(OrderServiceImpl.class, OrderRepository.class)
            .servlet(SignInServlet.class)
            .start()) {
        server.await();
    }
}
```

| Call | Meaning |
|---|---|
| `port(int)` | Port to listen on, 8080 by default. 0 picks a free one, read with `port()`. |
| `contextPath(String)` | Where the WAR is deployed. Use the production value: the session cookie path, the service worker scope and the shell's `<base href>` follow it. |
| `beans(Class...)` | The `@RmiService` implementations and everything they inject. |
| `servlet(Class)` | A servlet, mapped at the patterns its own `@WebServlet` declares and created through CDI, so `@Inject` works. |
| `shell(boolean)` | `false` leaves `/` to the application. The shell servlet answers there by default. |

`baseUrl()` and `webSocketUrl()` give the addresses. Settings are the usual `zeroz.*` system
properties, for example `-Dzeroz.security.mode=dev`.

## Name every bean

Nothing is discovered. A class that is injected and not named in `beans(...)` fails at start with the
CDI message that names it. The framework's own beans are included. Producers that read from
WildFly (JNDI resources, caches, queues) are the reason to list beans instead of scanning, so leave
those out and supply a plain bean in their place.

## What it does not do

- It is not a container. Nothing reads `web.xml`, `@WebFilter` or `@WebListener` from your classes:
  add what you need in code, or leave it out in development.
- Container-managed threads are not present, so RMI calls run on virtual threads, as in a standalone
  server.
- Do not use it to host a deployment.

See [Packaging](packaging.md#shape-4-a-war-on-a-jakarta-ee-server) for the WAR itself.
