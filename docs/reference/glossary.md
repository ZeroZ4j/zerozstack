# Glossary

ZeroZ Stack overloads several words that mean other things elsewhere. These are the definitions used
throughout the documentation; they are not interchangeable.

## The four propagation words

Keeping these apart keeps the mental model clean.

**Event**
: A discrete, fire-and-forget *occurrence* broadcast from server to clients — `EventTopic`,
`EventPublisher`, `ServerEvents`. An event has no current value and no replay. "Events are news."

**Signal**
: Reactive *state* — `ValueSignal`, `Computed`, `Effect`. Local to one tier, or shared across both
via `Signals.shared`. Independent of events: events do not require signals and signals do not require
events. "Signals are facts."

**Push**
: A transport direction, not a feature: any server-to-client frame. Events, shared-signal updates and
LiveSync updates are all pushes, on different opcodes.

**Message**
: Reserved for application domains — a `ChatMessage` in a chat app. Never a framework concept.
ZeroZ Stack is not a message broker.

## Framework terms

**`@DataModel`**
: Marks a class as wire-serializable. The annotation processor generates a binary serializer for it at
compile time. Requires a public no-arg constructor, and for every serialized field either a public
field or standard accessors — public fields are enough on their own, which is what the archetype's
own `Message` example uses.

**RMI**
: Remote method invocation over the binary WebSocket — *not* `java.rmi`. A `@RmiService` interface in
the shared module, implemented as a CDI bean on the server, called through a generated `_Stub` on the
client.

**Stub**
: The generated client-side implementation of an `@RmiService` interface, named `MyService_Stub`. It
marshals arguments into a frame and suspends until the response arrives.

**LiveSync**
: Keeping one identified object alive on both tiers, with inbound updates applied **in place** to the
instance you already hold. Not a CRDT: there is no field-level merge and no conflict resolution.
Whole-object, last-write-wins.

**Handle**
: The identifier the `ObjectMapper` assigns to an object the first time it is serialized. LiveSync
resolves inbound updates to existing instances by handle, which is why an object must be sent once
before `notifyChanged` can reach a client.

**`ObjectMapper`**
: ZeroZ Stack's handle registry — *not* Jackson's `ObjectMapper`. Maps handles to object instances so that
identity, cycles and in-place updates survive serialization.

**Mutation**
: A client-originated change to a `@ClientWritable` LiveSync object, sent as one coalesced
whole-object frame and re-checked by the server before it is applied.

**Retained value**
: The current value a shared signal holds on the server and sends to each client as it subscribes.
This is what makes late joiners correct without a snapshot fetch, and what events deliberately lack.

**Corrective sync**
: The single-session update the server sends to a writer whose mutation or shared-signal write was
rejected, snapping its optimistic local change back to server truth. The reason is never sent to the
client, and is not always logged on the server either — a failed role check leaves no record at all.

**Re-sync**
: The automatic recovery after a reconnect: the client re-subscribes its shared signals and sends
the server the list of object handles it holds (`zeroz4j.resync`); the server answers with each
object's current state, applied in place. Restores what a drop made stale — it does not replay RMI
calls, re-acquire lost locks, or survive a server restart (which empties the handle registry).

**`DisconnectedException`**
: What an RMI call throws when the connection is down — immediately, whether the call was made
while offline or was in flight when the socket dropped. Never queued or replayed: retrying is the
application's decision, because only it knows whether repeating the call is safe.

**Connection state**
: `CONNECTING`, `CONNECTED`, `RECONNECTING` or `CLOSED`, exposed as a signal by
`WasmRmiClient.connectionState()`. The built-in "Connection lost — reconnecting…" banner renders
from it; applications with their own indicator disable the banner via
`Zeroz4jClient.showConnectionBanner(false)`.

**Scope**
: How wide a LiveSync push or a scoped event reaches — `Scope.GLOBAL`, `SESSION`, `USER` or
`TENANT`. Shared signals have no scoping; a shared signal is one value every session agrees on.
`SESSION` targets a session id, which changes when that client reconnects.

**Effect**
: A side-effect runner, usually rendering, that re-runs when any signal it read changes. Created with
`Effect.create`, which returns a `Disposable`.

**Computed**
: Lazily evaluated derived state. Recomputes on read when a dependency has changed. Never crosses the
wire.

**Dependency tracking**
: Reading a signal with `get()` inside an `Effect` or `Computed` registers it as a dependency. There
is no subscribe call.

**Frame**
: One binary WebSocket message. Every frame opens with a 32-bit correlation id — zero for
server-initiated frames — followed by an opcode byte and a payload, except client-to-server RMI
requests, which omit the opcode. Object handles are never in the header; they travel inside the
serialized payload.

**APT**
: The `zerozstack-apt` annotation processor. Generates `_Serializer`, `_Rules`, `_Live`, `_Stub` and the
`META-INF/services` registrar at compile time, so the client needs no runtime reflection.

**`_Live` subclass**
: The generated subclass of a `@ClientWritable` model whose setters report changes to the mutation
tracker. The client instantiates these instead of the base class.

**TeaVM**
: The ahead-of-time compiler that turns the Java client into browser-executable code. It has both a
JavaScript and a WasmGC backend; ZeroZ Stack currently uses the **JavaScript** backend in every client
module and in the archetype.

**WasmGC**
: The WebAssembly garbage-collection proposal, TeaVM's other backend and **ZeroZ Stack's intended
destination**. Not yet in use, because TeaVM's WasmGC support is missing functionality the framework
needs. The client module is named `zerozstack-client` rather than after either backend, so the name stays
correct when the target changes. See [Limitations](limitations.md#compilation-target).

**Cooperative scheduler**
: The client's single-threaded execution model. RMI calls suspend and resume on it rather than
blocking, which is why client code must never create a `java.lang.Thread`.

**Virtual thread**
: A JDK 21 lightweight thread. The server hands each session's inbound frames to one, so thousands of
open WebSockets do not exhaust the platform thread pool. Server-side only — the client's suspension
mechanism is unrelated, despite the similar feel.
