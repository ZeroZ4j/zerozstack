# ZeroZ Stack Architecture Guide

> ⚠️ **Contains pre-0.3.0 API references.** The architectural narrative holds, but do not copy code
> from this page. Specifically: `WasmRmiClient.create(Class)` **does not exist** (obtain a stub with
> `new MyService_Stub()`); the frame-type names differ from the code (`SyncFrameTypes` uses
> `RPC_RESPONSE`, `RPC_PUSH`, `RPC_ERROR`); the downstream LiveSync frame is `0x10`, not a
> server-sent `SUBSCRIBE`; and `zeroz4j-server-backend` is not a module — the modules are
> `zerozstack-server-core` and `zerozstack-server-helidon`. For the wire format use
> [PROTOCOL.md](PROTOCOL.md); for current limits see [Limitations](reference/limitations.md).

This document provides a deep dive into the architecture of the **ZeroZ Stack** framework. It is intended for developers, AI agents (like context7), and architects seeking to understand the internal mechanics of a pure Java, zero-impedance stack.

## The Problem: Impedance Mismatch

Modern web development often feels like integrating a series of translation layers:
1. **Database to Backend**: Object-Relational Mapping (ORM) translates SQL rows into Java Objects (JPA/Hibernate).
2. **Backend to Frontend**: JSON serialization translates Java Objects into text, which is parsed back into JavaScript Objects.
3. **Frontend to UI**: JavaScript manipulates DOM trees.

These translation layers introduce performance overhead, break static typing guarantees, complicate refactoring, and require duplicate domain models.

## The ZeroZ Stack Solution

ZeroZ Stack completely eliminates these translation layers. 

- **End-to-End Java**: You write Java for the UI, the backend, and the database.
- **EclipseStore**: The database *is* your JVM memory heap. Objects are stored natively as a graph, eliminating ORM entirely.
- **Binary RPC over WebSockets**: The client (compiled to Wasm) and the server communicate via a dense binary protocol over persistent WebSockets. No JSON serialization, no REST controllers.

## Compilation Pipeline (AOT)

ZeroZ Stack relies heavily on Ahead-of-Time (AOT) compilation to guarantee performance in the browser. 

1. **Annotation Processing (`zerozstack-apt`)**: During Maven compilation, the annotation processor scans for `@DataModel` and `@RmiService`. It generates `_Serializer` classes for every model and `_Stub` classes for every service. This avoids runtime reflection, which is slow and often problematic when compiling to WebAssembly.
2. **TeaVM WasmGC**: The `zerozstack-client` module bridges Java to the browser. TeaVM transpiles the client-side Java bytecode (including the generated stubs and serializers) directly into WasmGC. 
3. **Coroutines for Non-Blocking I/O**: Because Wasm runs on the browser's single UI thread, you cannot block it (e.g., waiting for an HTTP response). TeaVM provides `@Async` continuation coroutines. When a client invokes an RMI stub, ZeroZ Stack suspends the coroutine, sends the binary frame, and cooperatively yields to the browser. When the WebSocket receives the server response, it resumes the coroutine perfectly. To make this ergonomic for developers, the `zerozstack-ui-components` library automatically dispatches all standard UI events (like button clicks) inside a new virtual thread. This guarantees that developers can make blocking backend calls directly inside UI event listeners without ever freezing the browser or writing boilerplate async code.

## Network Protocol Specification

The ZeroZ Stack RPC protocol operates entirely over binary WebSockets.

### Client-to-Server Invocation Frame

When an RMI stub is called, it packs the request into a contiguous `ByteBuffer`:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                          Message ID                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Interface Name (Length + UTF-8 string bytes)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Method Name (Length + UTF-8 string bytes)                     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Argument Count                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Marshaled Arguments (Each starting with type tag byte)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Server-to-Client Response Frame

The backend is modularized into `zerozstack-server-core` (the agnostic CDI engine and RMI dispatcher)
and a specific HTTP/WebSocket binding — `zerozstack-server-helidon` for a standalone server, or
`zerozstack-server-jakarta` to deploy as a WAR into WildFly, Payara, Open Liberty or TomEE. The core
carries no JAX-RS or servlet type at all, which is what makes "agnostic" true rather than
aspirational: it can sit inside somebody else's WAR without claiming a URL. The backend unpacks the
frame, routes it to the CDI bean implementing the interface, invokes the method on a thread from the
configured factory — virtual by default, container-managed when a `SessionThreadFactoryProvider`
supplies one — and writes the response:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               Correlation ID / Target Listener ID             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Frame Type   |  Payload (Value with type tag or String err)  |
| (0x01/02/03/0F|                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Frame types are standardized in `SyncFrameTypes`:
- `0x01 (SUCCESS)`: Synchronous return.
- `0x02 (PUSH)`: Server-Initiated Asynchronous Push (e.g. chat messages, live updates).
- `0x03 (AUTH)`: Authentication details and Protocol Version handshake.
- `0x0F (ERROR)`: Application Exception.

## LiveSync Design Rationale

While traditional RMI allows the client to fetch a static snapshot of an object (`getData()`), **LiveSync** is designed to automatically push changes made by background threads on the server to the TeaVM UI.

LiveSync is now entirely implicit. By annotating a `@DataModel` with `@LiveSync`, the framework tracks its lifecycle. When an object is sent to the client (e.g. via an RMI call), it gets assigned a session-scoped reference handle by the `ObjectMapper`. 

Whenever the backend modifies this synced object, it calls `SyncEngine.notifyChanged(obj)`. The server broadcasts the updated state (a `SUBSCRIBE` frame containing the serialized object) to connected clients. On the client side, the `ObjectMapper` intercepts the frame, looks up the existing instance by its reference ID, and updates its fields inline without any explicit `watch()` or `subscribe()` calls in your UI code.

### When to use RMI vs LiveSync

**Use RMI (`MyService.getData()`)** when you need a one-time data fetch for information that doesn't change frequently or where you don't need real-time UI updates:
```java
// Synchronous fetch over WebSocket
UserData data = WasmRmiClient.create(UserService.class).getUser(123);
uiLabel.setText(data.getName());
```

**Use LiveSync (`@LiveSync`)** when you want a reactive UI that automatically updates whenever the data changes on the backend:
```java
// Just fetch it once. Because DashboardStats is annotated with @LiveSync,
// the client will automatically receive inline updates whenever the server modifies it.
DashboardStats stats = WasmRmiClient.create(DashboardService.class).getStats();

// Bind the UI to the object's properties or a UI update loop
// (assuming UI components are bound to detect these underlying changes)
uiLabel.setText("Active Users: " + stats.getActiveCount());
```

## UI Components

`zerozstack-ui-components` is a Vaadin-inspired declarative UI library. However, unlike Vaadin, there is **no server-side DOM state**. 

UI components are instantiated, configured, and bound to listeners entirely in the client-side Wasm heap. The components use `DaisyUI` and `Tailwind CSS` utility classes for pristine, modern styling without custom CSS overhead.

## Scalability & Virtual Threads

Handling thousands of persistent WebSockets requires efficient concurrency. The `zeroz4j-server-backend` leverages Project Loom Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`). Incoming WebSocket frames are immediately handed off to a Virtual Thread, preventing the application server's I/O threads from blocking during long-running database queries or complex processing.
