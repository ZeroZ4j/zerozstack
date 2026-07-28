# ZeroZ Stack: Core Concepts You Need to Know

> ℹ️ Terminology is now defined canonically in the **[Glossary](reference/glossary.md)**, and the
> question of *which* construct to use for a given feature is answered in
> **[Choosing how state moves](decide/index.md)**. Note that concept 7 below understates LiveSync: the
> server must call `syncEngine.notifyChanged(obj)` explicitly, and inbound updates do **not** notify
> the view.

When developing an application with ZeroZ Stack, you are stepping into a unified, zero-impedance Java stack. There are no REST APIs, no JSON, no ORMs, and no JavaScript. To navigate this architecture, here are the 10 core concepts you need to understand:

## 1. `@DataModel`
Every domain model or entity that needs to be transmitted between the client and server must be annotated with `@DataModel`. This triggers the `zerozstack-apt` annotation processor to generate an ultra-fast binary serializer at compile-time.

## 2. `@RmiService`
Used to annotate your service interfaces. Any interface annotated with `@RmiService` will have a client-side Wasm stub generated automatically. You simply invoke these interface methods on the client, and they execute on the server over the persistent WebSocket.

## 3. `Zeroz4jClient`
The bootstrap mechanism for your Wasm client application. By calling `Zeroz4jClient.connect(url, callback)`, you establish the binary WebSocket connection to the backend and initialize the RMI subsystem.

## 4. `Zeroz4jServer`
The bootstrap mechanism for your Helidon-based backend. Calling `Zeroz4jServer.start(port, name)` spins up the HTTP/WebSocket server, initializes the CDI environment (Weld), and binds the `zerozstack-server-core` RMI dispatcher to incoming connections.

## 5. Security: `@Secured` and `@RolesAllowed`
ZeroZ Stack supports method-level security on the server. By annotating an `@RmiService` implementation method with `@Secured`, you ensure only authenticated users can call it. Adding `@RolesAllowed("admin")` restricts invocation to specific roles.

## 6. Authentication: `RmiSecurityContext`
The client logs in by calling an authentication endpoint or sending an `AUTH` frame. Once the server validates the user, it replies with an `AUTH` frame containing the user's roles. On the client side, `RmiSecurityContext.onAuthenticated()` fires, allowing your app to load authenticated UI views.

## 7. `LiveSync` (Implicit Synchronization)
While RMI is great for fetching data on demand, `LiveSync` is used for reactive, real-time UI updates. By annotating an entity with `@LiveSync`, the framework tracks it using session-scoped reference handles. When the server-side state changes, the `SyncEngine` automatically pushes updates to the client. The client's `ObjectMapper` intercepts these updates and modifies the object in memory inline, completely eliminating the need for explicit polling or subscriptions.

## 8. EclipseStore (Native Persistence)
ZeroZ Stack bypasses JPA and SQL completely. By using the `zerozstack-store-eclipsestore` module, your Java memory graph *is* your database. You modify your objects in memory, call `storage.store(object)`, and the delta is serialized directly to disk as a binary graph.

## 9. DOM-less UI (`zerozstack-ui-components`)
You do not write HTML/DOM manipulation code. The `zerozstack-ui-components` module provides a Vaadin-like programmatic component model (e.g., `new Button("Click Me")`). The components are styled using Tailwind CSS and DaisyUI utility classes, completely in Java.

## 10. `GrowableBuffer` (Binary Protocol)
Under the hood, all communication uses `GrowableBuffer` to serialize method arguments and return types into a highly dense binary WebSocket frame. You rarely interact with this directly (as the APT processor handles it), but it is the secret to ZeroZ Stack's incredible performance and low overhead, replacing verbose JSON parsing.

## 11. Reactive Signals (`ValueSignal`, `Computed`, `Effect`)
In the frontend UI layer, ZeroZ Stack provides a set of reactive primitives in the `com.zeroz4j.signals` package. `ValueSignal` holds a mutable state, `Computed` derives state from other signals, and `Effect` automatically re-runs when its tracked dependencies change. This provides a clean, dependency-tracking reactive state management system entirely within the Wasm client heap.
