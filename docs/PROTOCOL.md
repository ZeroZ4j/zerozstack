# Detailed Protocol Specification

ZeroZ Stack relies on a pure binary WebSocket protocol to enable high-performance, bidirectional communication between the WebAssembly client and the Jakarta EE backend. The protocol eliminates JSON overhead entirely, providing dense serialization and native object mapping.

## General Frame Structure

Every WebSocket frame in ZeroZ Stack is binary and begins with a 4-byte ID:

```
[ 4 bytes: Correlation ID or Handle ID (Int) ]
[ 1 byte : Frame Type / Opcode (Optional for RMI Requests) ]
[ Variable : Payload ]
```

* **Correlation ID**: Used in Request/Response pairs to match server replies with suspended client coroutines.
* **Handle ID**: Used in LiveSync frames to identify the synced object graph.

## The handshake

Everything above happens on a connection that has already been accepted. Two checks run first,
before any frame is exchanged.

**Origin — which page opened it.** The `Origin` header must match the host the request was sent to,
or one of the origins listed in `zeroz.origins`. A handshake carrying no `Origin` at all is allowed:
browsers always send one, so its absence means the caller is not a browser and carries no cookies of
its own.

**Host — which name it was addressed to.** With `zeroz.hosts` set, the `Host` header must be one of
the names listed there. Unset, this check does not run.

A refused handshake is closed with WebSocket close code 1008, and the reason names which of the two
checks refused it and nothing else about the deployment. The full explanation, with the configured
values, is in the server log.

**Client identity.** Every connection carries a browser id the *server* minted: 256 random bits,
HMAC-signed, delivered in an `HttpOnly`, `SameSite=Strict` cookie that page script cannot read. It is
not a session id — it survives reconnects and reloads — and it is not a user: it identifies a browser
profile, and anyone at that machine is the same client. It backs `Scope.CLIENT` and
`RmiRequestContext.getClientId()`, which is what lets an application with no login keep one browser's
state to itself.

**Credentials**, if any, are presented here too — as handshake parameters or headers, read by the
application's `AuthenticationProvider`. A provider that declines does not fail the upgrade: the
connection proceeds anonymously and is refused at every `@Secured` call, because a rejected upgrade
gives the client no way to report why.

## What does not travel on this wire

**File contents.** A message here is assembled whole in memory and is limited to 4 MB by default
(`zeroz.ws.maxBinaryMessageBytes`), so files go over a separate HTTP address instead — see
[Accepting file uploads](guides/file-uploads.md). The only part of an upload that touches this wire
is a small call asking for a one-time pass.

## RPC Protocol (RMI)

Remote Method Invocations allow the client to call server-side CDI beans directly.

### RMI Request (Client -> Server)
Interestingly, standard RMI requests do not include a dedicated opcode byte. Instead, they rely on the length prefix of the interface name. Since the interface name string length is less than 16MB, the 5th byte (MSB of the length integer) is always `0x00`. The server interprets `0x00` as an RMI call because it falls outside the `0x10-0x1F` LiveSync opcode range.

**Structure:**
* `[4 bytes]` Message ID
* `[String]` Interface FQCN (Length + UTF-8 bytes)
* `[String]` Method Name
* `[4 bytes]` Argument Count
* `[N Elements]` Arguments (Type Tag + Value)

**In flight at once.** One connection may have 32 frames being decoded and executed at the same time
(`zeroz.ws.maxConcurrentFramesPerSession`). A frame that arrives while the connection is at its limit
waits — nothing is dropped and no call fails, so a burst is served a few at a time rather than
refused. Waiting slows down that one connection's read loop, which is the point; other connections
are unaffected. The limit exists because decoding is where a small message becomes a large object
graph, so a message-size limit is only a real ceiling if the number of messages being decoded at once
is bounded too.

### Server Responses (Server -> Client)

Server responses include an explicit opcode byte at index 4.

* **0x01 — RMI_RESPONSE (Success)**
  * Payload: `[Type Tag + Value]` (The return value of the method)
* **0x0F — RMI_ERROR (Error)**
  * Payload: `[String]` (Error message)
  * Two kinds of message travel word for word: one the application deliberately wrote for the caller
    (`com.zeroz4j.server.ClientVisibleException`), and the framework's own refusals — authentication
    required, access denied, unknown service, unknown method, failed argument validation.
  * Every other failure is answered with `The server could not complete this request. Reference: <code>`.
    The real message and stack trace are written to the server log under the same code, so a user
    reading the code off their screen is enough to find the log line.
* **0x02 — PUSH (Server-initiated)**
  * Payload: `[String]` (Topic Name) + `[Type Tag + Value]` (Payload)
* **0x03 — AUTH (Authentication Result)**
  * Sent by the **server** on every connection open, including anonymous and rejected ones. The
    client never sends this frame; credentials are presented on the WebSocket handshake instead.
  * Payload: `[1 byte]` (Protocol Version, currently `2`) + `[1 byte]` (Authenticated: `1` or `0`) +
    `[String]` (Username) + `[4 bytes]` (Role Count) + `[N Strings]` (Roles)
  * **The authenticated flag is the server's decision and nothing else stands in for it.** A refused
    connection still carries a name (`"anonymous"`), and a genuinely signed-in user may hold no roles
    at all — so neither field can be used to infer the outcome. Before version 2 the flag did not
    exist and clients assumed success, which meant a rejected credential reported as authenticated.
  * The frame is sent even when authentication fails, because silence cannot be told apart from a
    slow network. A client reading a frame with no version byte treats the connection as
    unauthenticated rather than guessing.
* **0x18 — PUSH (One-shot server message)**
  * Payload: `[String]` (Topic Name) + `[Type Tag + Value]` (Payload)
  * Scoped publishes are filtered **server-side**: a frame is written only to sessions matching the
    target. Topic filtering is the client's job, session filtering is not.
* **0x19 — PONG (Keepalive answer)**
  * No payload. The server's answer to a keepalive ping.
  * It answers rather than swallowing because a proxy times each **direction** separately — nginx
    uses `proxy_read_timeout` one way and `proxy_send_timeout` the other — so a ping the server
    absorbed would keep only one of the two timers alive.

## Keepalive

An idle WebSocket is closed by whichever proxy in the path has the shortest timeout: nginx defaults
to 60 seconds, Cloudflare cuts at 100. Browsers do not expose WebSocket ping frames to page script,
so this cannot be solved above the transport — which is why it lives here rather than in an
application's service interface.

After **25 seconds of silence** the client sends a five-byte RMI-shaped frame naming the reserved
service `zeroz4j.keepalive`, method `ping`, with correlation id `0` — fire and forget, because
nothing waits for the answer. The server recognizes the name before service dispatch and replies with
one `0x19 PONG`: no service lookup, nothing checked beyond the connection already being open, no
request context.

Any real traffic postpones the next ping, so a connection in use sends none at all. `Keepalive.configure(seconds)`
changes the interval; zero turns it off.

**One connection is answered at most once per second** (`zeroz.ws.keepaliveMinIntervalMillis`).
Pings that arrive faster than that are ignored and cost nothing. A working client is nowhere near
the limit — it waits 25 seconds between pings. The answer is written on the connection's own read
thread and never becomes a task, so a connection is still answered while it is busy.

## LiveSync Protocol (0x10 – 0x1F)

LiveSync handles real-time object graph synchronization and reactive signals.

* **0x10 — SUBSCRIBE** (Client -> Server)
  * Payload: `[String]` Class Name
* **0x11 — SNAPSHOT** (Server -> Client)
  * Payload: `[8 bytes]` Version + `[Type Tag + Value]` Serialized Object
* **0x12 — UNSUBSCRIBE** (Client -> Server)
  * Payload: None (Handle ID is sufficient)
* **0x13 — MUTATE** (Client -> Server)
  * Propose a new state.
  * Payload: `[8 bytes]` Base Version + `[Type Tag + Value]` Serialized Object
* **0x14 — ACK** (Server -> Client)
  * Mutation accepted.
  * Payload: `[8 bytes]` New Version
* **0x15 — REJECT** (Server -> Client)
  * Mutation rejected (e.g., version conflict).
  * Payload: `[8 bytes]` Current Version + `[Type Tag + Value]` Current Object + `[String]` Reason
* **0x16 — SIGNAL_SUB** (Client -> Server)
  * Reserved. The current subscribe mechanism rides an RMI-shaped frame to the internal
    service `zeroz4j.signals` (method `subscribe`, one String argument: the signal name);
    the server intercepts it before service dispatch and answers with the retained value.
* **0x17 — SIGNAL_UPD** (Server -> Client)
  * A signal's value. Broadcast to all sessions on every server-side change, and sent
    directly to a session in response to a subscribe.
  * Payload: `[String]` Signal Name + `[Type Tag + Value]` Serialized Value
  * **A scoped signal uses the same frame and carries the family's base name only** — never the
    target. The server resolves the target from the subscribing session's own identity and sends
    that target's value; the frame is written only to sessions matching it. A client therefore
    cannot tell a scoped signal from a shared one, cannot name a target, and never learns that other
    targets exist.

## Binary Type Tags (Serialization)

ZeroZ Stack serializes data dynamically using `BinarySerializer`. Each serialized argument or payload value is prefixed with a 1-byte type tag:

| Tag | Type | Encoding |
|-----|------|----------|
| `0x00` | Null | No payload |
| `0x01` | Integer | 4 bytes |
| `0x02` | Long | 8 bytes |
| `0x03` | Double | 8 bytes |
| `0x04` | Float | 4 bytes |
| `0x05` | Boolean | 1 byte (0 or 1) |
| `0x06` | String | 4-byte length + UTF-8 bytes |
| `0x07` | Object | `[String]` ClassName + Field bytes from the generated `<Model>_Serializer` |
| `0x08` | Short | 2 bytes |
| `0x09` | Byte | 1 byte |
| `0x0A` | Character | 2 bytes |
| `0x0B` | List | 4-byte Size + `N` Elements (Tag + Value) |
| `0x0C` | Map | 4-byte Size + `N` Key/Value pairs (Tag + Value) |
| `0x0D` | Byte Array | 4-byte Length + `N` bytes |
| `0x0E` | Reference | `[String]` object id of an instance already written in this graph (cycles, shared references) |
| `0x0F` | UUID | Canonical 36-char string form (not two longs — TeaVM does not emulate `UUID.getMostSignificantBits()`) |
| `0x10` | Instant | 8-byte epoch second + 4-byte nano |
| `0x11` | Enum | `[String]` declaring-class FQCN + `[String]` `name()` |
| `0x12` | Set | 4-byte Size + `N` Elements (Tag + Value). Rebuilt as a `LinkedHashSet`, so encounter order survives |
| `0x13` | BigDecimal | `[String]` exact `toString()` form — scale and precision preserved |
| `0x14` | BigInteger | `[String]` exact `toString()` form |
| `0x15` | LocalDate | 8-byte epoch-day long |
| `0x16` | LocalTime | 8-byte nano-of-day long |
| `0x17` | LocalDateTime | 8-byte epoch-day long + 8-byte nano-of-day long |
| `0x18` | Duration | 8-byte seconds long + 4-byte nano int |
| `0x19` | Optional | The contained value, or `0x00` NULL when empty |
| `0x1A` | int[] | 4-byte length + `N` × 4 bytes |
| `0x1B` | long[] | 4-byte length + `N` × 8 bytes |
| `0x1C` | double[] | 4-byte length + `N` × 8 bytes |
| `0x1D` | float[] | 4-byte length + `N` × 4 bytes |
| `0x1E` | short[] | 4-byte length + `N` × 2 bytes |
| `0x1F` | char[] | 4-byte length + `N` × 2 bytes |
| `0x20` | boolean[] | 4-byte length + `N` × 1 byte |
| `0x21` | Lazy | `[String]` session-scoped handle. **Never the contents** — the client resolves it with an RMI round trip on first `get()` |
| `0x22` | Record | `[String]` object id + `[String]` ClassName + components from the generated `<Record>_Serializer`, in canonical order |
| `0x23` | Sealed | `[String]` the sealed base's FQCN, then the value itself as `0x07`, `0x22`, `0x0E` or `0x00` |

Note the tag space (`0x00`–`0x23`) is independent of the frame opcode space; a tag byte never appears
where an opcode is expected.

## Object identity, and how far it reaches

Every model value is written with an object id. The second time the same instance is met, only that
id goes out, as `0x0E`. That is what lets a graph contain loops and shared parts and arrive with its
shape intact rather than as a tree of copies.

**The rule is: identity holds within one top-level value, and not between two.**

The set of "already written" instances is emptied whenever a value is written that was not reached
from inside another one. So:

```java
Order order = new Order();
order.setBilling(address);
order.setShipping(address);          // the same Address instance
send(order);                          // arrives as ONE address, in both fields

send(List.of(address, address));      // arrives as TWO addresses
```

In the first case the two fields are reached from inside `order`, so the second is a reference. In
the second case each element of the list *is* a top-level value, so each is written in full.

Two consequences worth knowing before relying on either:

- **Do not use `==` across a call boundary to decide whether two things are the same.** Compare by
  id, or by `equals`. This is the one that costs an afternoon.
- A model reached through a `List`, `Set`, `Map` or a field declared as a model type all behave
  the same way. There is no longer a path that writes a model in place without recording it.

### Two kinds of name (0.8.0+)

An id on the wire is one of two things, and which one it is decides how long it means anything.

| | What it is | How long it means something |
|---|---|---|
| **A handle** | a random 36-character identifier, kept in the server's and the browser's handle registry | until the object it names is no longer used |
| **A message name** | a short id beginning with `~`, counted from zero inside each top-level value | only inside the message it arrived in |

A handle is given to a **`@LiveSync` model, and to every object reachable inside one**. Nothing else
gets one. That is not an optimization: those are exactly the objects a later message needs to be able
to name again — a client edit coming back up, a re-sync after a reconnect, a lock request — and the
whole graph inside a live object is included because a client edit arrives as one whole graph and is
applied part by part into the objects those parts name.

Everything else — the return value of a call, the payload of an event, the value of a shared signal —
is a **value**. It is written with a message name, it is never entered in a registry, and the
receiving side builds a fresh instance for it. Shared references and loops inside one top-level value
still arrive intact, because that is all the message name was ever needed for.

Two consequences worth knowing:

- **Sending the same ordinary object twice sends two objects.** It was already true across two
  top-level values; it is now true across two messages as well. Compare by id or `equals`, never by
  `==`.
- **An ordinary object a client sends back up is a copy.** Before 0.8.0, passing a previously
  received object back as a call argument silently reached into the server's own instance and
  overwrote it. The server now builds a fresh instance from what the client sent, and its own data is
  left alone. A change a client should be able to make to server state goes through a service method
  or through `@LiveSync`, both of which check it first.

### Neither side keeps an object alive (0.8.0+)

The handle registry holds what it names **weakly**, on both tiers. An entry disappears once the
application itself has let go of the object; no timer, no eviction policy, no ceiling. Before 0.8.0
both registries held every object for ever, so a screen that redraws itself every few seconds built
an unbounded pile on the server and in the browser — the failure this rule exists to prevent.

What follows from it:

- **The server answers a re-sync only for objects it still holds.** If the application there has
  dropped its last reference to a live object, its handle is gone and the answer is the same as
  after a server restart: no frame, one counted log line, and the client re-fetches. Keep live
  objects in your store or a field, which is where they already live in any real application.
- **A browser asks for exactly what its screen still holds.** Anything scrolled away, replaced or
  navigated off is not asked for.

## Nested models are written by reference, not in place

Before 0.8.0, a field whose declared type was a model class was written straight into the buffer
with nothing recorded about it. It saved a few bytes and cost three things:

- two models referring to each other recursed until the stack ran out;
- the same instance in two fields arrived as two objects;
- a model nested inside a `@LiveSync` one was rebuilt with a plain constructor, so it never became a
  tracked instance and edits to it were invisible.

A model reached through a collection never had any of those problems, because collections always
took the tagged path. Now every model-typed field takes it too. The cost is an id and a class name
per nested model — bounded by how many model-typed fields a model has, not by how much data there
is, since collections were already paying it.

## Inheritance

A model may extend another model, and **the base class's fields travel with it**. The base has no
serializer of its own; what it declares is written as part of each concrete model below it, base
fields first, then the subclass's own.

An abstract model is never a value in its own right. It gets no serializer and no entry in the
registry — it exists to hand its fields down. A field declared as an abstract model type still works:
the tagged path dispatches on the runtime type.

Two shapes are refused at compile time, both because they used to lose data silently:

| Refused | Why |
|---|---|
| A model extending a class that is **not** a `@DataModel` and that declares instance fields | there is no way to know those fields belong on the wire, so they were dropped without a word. Annotate the base, or move the fields down. A base class with no fields needs no annotation. |
| A model declaring a field name a base class already declares | both would be written, and both read back through the same accessor, so one would overwrite the other |

## Records

A `record` annotated `@DataModel` travels as `0x22`. The bytes look like an ordinary object; what
differs is **when the value exists**.

An ordinary model is created empty, registered under its id, and only then are its fields read into
it. That order is what lets a graph point back at something still being read — the instance already
exists, so a `0x0E` reference to it resolves.

A record's components are final and are set only by its canonical constructor, so the reader has to
do the opposite: read every component, then construct, then register under the id.

The consequence is a real limit, and both sides state it rather than let it fail quietly:

- **A record cannot take part in a reference cycle.** A cycle needs the record to exist while its own
  components are still being read. The **writer** refuses when a value inside a record points back at
  that record, naming the record and saying to use a class for the type that closes the loop. The
  **reader** refuses a hand-built payload that does the same, rather than resolving the reference to
  null. Use a class wherever the loop closes: a class is created empty and filled afterwards, so it
  can point back at itself.
- **The same record appearing twice is fine.** The second appearance is an ordinary `0x0E` reference,
  and by the time the reader meets it the record has been built.

A record still cannot be a persistence root: EclipseStore reaches fields directly and the JVM refuses
that for records without `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`. That is a
persistence rule, not a wire rule.

Records are safe in the browser. TeaVM 0.15.0 lowers a record to an ordinary class, including the
`equals`, `hashCode` and `toString` the compiler generates through `invokedynamic`, so a record used
as a map key or a set element behaves in the browser as it does on the server.

### The class a developer used to write, and the record they write now

```java
// Before — ten lines of ceremony, all of it required by how the reader worked
@DataModel
public class Money {
    private long amount;
    private String currency;
    public Money() { }
    public Money(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

// Now
@DataModel
public record Money(long amount, String currency) { }
```

`@LiveSync` and `@ClientWritable` are refused on a record at compile time. Both are about editing an
object in place through its setters and reporting the change; a record has no setters and never
changes.

## Sealed types

A value whose declared type is a sealed `@DataModel` interface or sealed abstract class travels as
`0x23`: the base's own class name, then the value.

```java
@DataModel
public sealed interface Message permits Text, Ping, Attachment { }

@DataModel public record Text(String author, String body) implements Message { }
@DataModel public record Ping(long sentAt) implements Message { }
@DataModel public final class Attachment implements Message { /* ... */ }
```

A field, a list element, a call argument or a return value may now be a `Message`. What arrives is a
`Text`, a `Ping` or an `Attachment` — the concrete type, with no cast and no kind field.

**Why the base name is on the wire.** `sealed` means the compiler knows the complete list of classes
the value may be. The annotation processor writes that list into the generated registrar, and the
reader looks up the base the payload names and refuses any class that base does not permit —
**before anything is created**. A payload naming a type outside the set fails with a message saying
which type it named and which ones are permitted. A field declared as the sealed base additionally
refuses a value of some *other* sealed family, so the check holds even where a cast would not.

The rules the annotation processor enforces, each because the receiver could not act safely
otherwise:

| Rule | Why |
|---|---|
| Every permitted class is itself `@DataModel` | otherwise a payload naming it could not be built |
| Every permitted class is `final` (a record already is) | a `non-sealed` member can be extended by classes outside the set, and the receiver could not tell them apart |
| A permitted class is not itself sealed | the reader looks up one permitted set; a family of families has no single set to check against |
| A sealed class base is `abstract` | a base that is also a value of its own would be neither in nor out of its set |
| A plain, unsealed interface is refused | there is no list, so any class at all could be named |

A sealed abstract class may declare fields the whole family shares. The base has no serializer of its
own — what it declares is written as part of each member's bytes.

The cost is one extra class-name string per value, on top of the one an object already carries.

Resolving a lazy handle rides an RMI-shaped frame to the internal service `zeroz4j.lazy` (method
`resolve`, one String argument: the handle), alongside `zeroz4j.signals` and `zeroz4j.livesync`. The
server intercepts it before service dispatch, checks that the handle was disclosed to that session,
and answers with an ordinary `0x01` response carrying the loaded value.

## Reconnection and re-sync

A dropped socket reconnects automatically with exponential backoff (500 ms doubling to a 15 s cap,
retried indefinitely). Reconnecting produces a **new session**: the handshake and the `0x03` AUTH
frame run again, and the previous session's server-side registrations are gone. The client restores
itself in this order, all as fire-and-forget frames:

1. Shared-signal writes queued while offline — internal service `zeroz4j.signals`, method `set`,
   one frame per signal carrying the last value written.
2. Live-object edits retained while offline — `zeroz4j.livesync`, method `mutate`, one whole-object
   frame per edited object.
3. A re-subscribe for every shared signal the client declared — `zeroz4j.signals`, method
   `subscribe`; each is answered with a `0x17` update carrying the current retained value.
4. One re-sync request — internal service **`zeroz4j.resync`**, method `sync`, one argument: the
   `List` of every object handle this client still holds. The server answers with one `0x10` frame
   per handle it still knows, carrying that object's current state, applied in place on the client
   exactly like any LiveSync update. Re-serializing also re-registers the objects' lazy-field
   handles for the new session. Handles the server does not know — it restarted since they were
   fetched, or the application there has let go of the object — produce no frame and one server-side
   log line naming the count.

   **The list is capped at 10,000 handles.** A client that somehow holds more throws its list away
   rather than sending it, logs one line saying so, and lets the application re-fetch. The cap is an
   escape hatch, not a budget: a request larger than the 4 MB a connection accepts is refused, the
   connection is closed, and the client reconnects and sends the identical request — for ever. A tab
   in that state could never connect again. The server's own record of what it sent a browser holds
   at most 10,000 objects anyway, so a longer list could not have been answered.

A handle presented to `zeroz4j.resync` is answered only when the server's own record says that
browser was sent the object. **A handle names an object; it does not stand for permission to have
it.** Handles travel inside payloads — an object nested in a broadcast event or a shared signal goes
out with its own handle attached — so every recipient of the outer payload knows the handles of the
parts inside, and the record is what separates knowing a handle from having been sent the object.

The record is kept per **browser id**, not per session, so a reconnect — which is always a new session
— still restores what that browser holds. A connection carrying no browser id is remembered for the
life of that one connection and re-fetches after a reconnect instead. A handle the caller was never
sent and a handle the server no longer knows are treated identically: no frame, no error, one counted
log line.

The same record answers the question "may this client take a lock on that object", through
`Disclosures.wasDisclosedTo(session, handleId)`.

In-flight RMI requests are **not** replayed. Their suspended callers fail with
`DisconnectedException` the moment the drop is detected, as do calls attempted while disconnected.

Collections are rebuilt on the receiving side as `ArrayList`, `LinkedHashSet` and `LinkedHashMap`.
Declare `@DataModel` fields as `List`, `Set` and `Map` rather than concrete implementation types.
