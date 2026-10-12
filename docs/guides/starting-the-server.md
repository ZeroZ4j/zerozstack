# How do I write the server's main class and start the server?

This page is for a developer who needs an entry point for a ZeroZ Stack server — a `main` method that
starts it — and ends with that server running on a port you chose.

## When to use this

- You are writing the `main` class of a standalone server, or reading one and want to know what it does.
- You want to know how the server's port, the development logins and the container (Helidon or
  Vidocq) get chosen.
- You are deploying a WAR: you write no main class at all. Read [A WAR calls nothing](#a-war-calls-nothing).

## The main class

A server's main class is one call. Put it in the server module, next to your `@ApplicationScoped`
service implementations:

```java
package com.example.server;

import com.zeroz4j.server.Zeroz4jServer;

public final class Main {
    public static void main(String[] args) {
        Zeroz4jServer.start(8080, "My application").join();
    }
}
```

`Zeroz4jServer` is in `zerozstack-server-core`, which a server module already depends on. The
archetype's generated `ServerApp` is the same call, after loading a `logging.properties`.

Run it from the server module with the classpath command. There is no `mvn exec:java` anywhere in
this project:

```bash
java -cp "target/classes;target/libs/*" com.example.server.Main
```

Use `:` instead of `;` on Linux and macOS. Open <http://localhost:8080>.

Do not call `BinaryRegistry.init()`, start Weld, or name a Helidon or Vidocq class. The binding does
all of that.

## What `start` does and returns

`Zeroz4jServer.start(int port, String appName)` finds the server binding on the classpath, starts it,
and returns once the server is accepting connections. `appName` is a display name for the log.

It returns a `Zeroz4jServer`, which has three methods:

| Method | What it does |
|---|---|
| `port()` | The TCP port the server is listening on. |
| `join()` | Blocks the calling thread until it is interrupted, so `main` does not return. |
| `close()` | Stops the server. `Zeroz4jServer` is `AutoCloseable`, so a test can use try-with-resources. |

On Vidocq, port `0` picks a free port; read it back with `port()`.

## How the port is chosen

`start` takes the port as an argument and nothing else decides it. The examples read it from the
command line so that twelve of them can run side by side. Copy their order if you want the same:
`--port`, then the `zeroz.port` system property, then a constant.

```java
package com.example.server;

import com.zeroz4j.server.DevAuth;
import com.zeroz4j.server.Zeroz4jServer;

public final class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        if (hasFlag(args, "--dev-login")) {
            System.setProperty("zeroz.security.mode", "dev");
            System.out.println(DevAuth.WARNING_BANNER);
        }
        Zeroz4jServer.start(port(args), "My application").join();
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static int port(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--port".equals(args[i])) {
                return Integer.parseInt(args[i + 1].trim());
            }
        }
        String configured = System.getProperty("zeroz.port", "").trim();
        return configured.isEmpty() ? DEFAULT_PORT : Integer.parseInt(configured);
    }
}
```

```bash
java -cp "target/classes;target/libs/*" com.example.server.Main --port 9000
java -Dzeroz.port=9000 -cp "target/classes;target/libs/*" com.example.server.Main
```

`--port` and `zeroz.port` are a convention of the examples and of `VidocqLauncher`, not something the
framework reads for you. The main class above is where they are read.

## Development logins: `--dev-login`

`--dev-login` turns on two built-in accounts, `demo` / `demo` and `admin` / `admin`, so you can sign in
without an identity provider. In the main class above it sets the system property
`zeroz.security.mode` to `dev` and prints `DevAuth.WARNING_BANNER`. `-Dzeroz.security.mode=dev` on the
command line does the same without any code.

**Nothing turns this on for you, and a main class you copy must not.** The accounts have hardcoded
passwords. A main class that enabled them by itself would carry that into every deployment that
copied it. A server with them on prints the warning at startup and again on the first sign-in.

The accounts, their roles and what replaces them are in
[Authentication and authorization](security-auth.md#development-authentication).

## How the container is chosen

`start` names no container. It uses the server binding on the classpath. A binding is the module that
starts one runtime and registers itself in `META-INF/services` so that `ServiceLoader` can find it
(the lookup runs before any bean container exists):

| Binding | Artifact | Runtime | Status |
|---|---|---|---|
| `helidon` | `zerozstack-server-helidon` | Helidon with Weld | The default. |
| `vidocq` | `zerozstack-server-vidocq` | Vidocq, Java 25 | Supported second binding. See [Running on Vidocq](vidocq-server.md). |

You choose by what you put on the classpath:

- **A hand-written project:** depend on `zerozstack-server-helidon` or on `zerozstack-server-vidocq`,
  not both. `zerozstack-bom` manages the versions.
- **A project generated from the archetype:** the server module has a profile for each. Switch with
  `mvn clean install -Phelidon` (Java 21 or later) or `mvn clean install -Pvidocq` (Java 25). Use
  `clean`, so `target/libs` holds one container's jars. The run command does not change.
- **At generation time:** the archetype asks `container`, `helidon` if you do not answer, or
  `-Dcontainer=vidocq`. The answer sets the default profile and nothing else.
- **With both on the classpath:** `-Dzeroz.server.binding=helidon` or `-Dzeroz.server.binding=vidocq`
  picks one.

`start` logs `[zeroz4j] Starting on the helidon server binding` (or `vidocq`) when it picks one.

### When nothing is on the classpath

`start` throws an `IllegalStateException`:

> No server binding is on the classpath. Add com.zeroz4j:zerozstack-server-helidon, or com.zeroz4j:zerozstack-server-vidocq (Java 25). A WAR does not call Zeroz4jServer.start: its servlet container starts it, through zerozstack-server-jakarta.

### When more than one is on the classpath

It throws, naming the bindings it found:

> More than one server binding is on the classpath: helidon, vidocq. Remove all but one, or pick one with -Dzeroz.server.binding=<name>.

The usual cause is a `target/libs` folder left over from a build with the other container. Run
`mvn clean install` again. If you name a binding that is not there, the message is
`-Dzeroz.server.binding=<name> names a server binding that is not on the classpath. Found: …`.

## A WAR calls nothing

A WAR has no main class. The servlet container starts it. Take `zerozstack-server-jakarta` instead of a
binding, and do not add `zerozstack-server-jaxrs`. A main class that calls `start` in a WAR fails with
the sentence for no binding above. See [Shape 4 in Packaging and running](packaging.md#shape-4-a-war-on-a-jakarta-ee-server).

## Limits

- `start` takes a port and a name and nothing more. There is no builder for TLS, thread pools or
  other server settings. The settings the framework reads are system properties, listed in
  [Packaging and running](packaging.md#every-setting-the-framework-reads).
- The `--port` and `--dev-login` flags exist only where your main class reads them.
- `Zeroz4jServer.start` is for a standalone server. A test starts a server with
  `zerozstack-server-test`, not with this call: see [Testing an application](testing.md).

## Where to go next

- [Packaging and running](packaging.md) — ship the server as a folder, an image or a WAR, and every
  `zeroz.*` setting.
- [Authentication and authorization](security-auth.md) — replace the development logins.
- [Running on Vidocq](vidocq-server.md) — what is different on the Vidocq binding.
- [Examples](../examples/index.md) — the main classes of twelve working servers.
