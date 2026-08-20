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

Everything above happens on a connection that has already been accepted. Two checks run first, before
any frame is exchanged.

**Origin.** The `Origin` header must match the host the request was sent to, or one of the origins in
`zeroz.origins`. This is not optional politeness: a browser attaches cookies to any connection to your
origin, including one opened by a page the user is merely visiting, so an unchecked `Origin` would
hand that page the visitor's identity. A refused handshake is closed with WebSocket close code 1008, with a reason naming which check
refused it and nothing else about the deployment.
A handshake carrying no `Origin` at all is allowed — browsers always send one, so its absence means a
non-browser client with no ambient cookies to abuse.

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
    The real message and stack trace are written to the server log under the same code. An
    unplanned failure's message names classes, fields and container internals, and an anonymous
    caller can trigger those failures on purpose to learn how the system is put together.
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
nothing waits for the answer. The server recognises the name before service dispatch and replies with
one `0x19 PONG`: no service lookup, no security check beyond the connection already being open, no
request context.

Any real traffic postpones the next ping, so a connection in use sends none at all. `Keepalive.configure(seconds)`
changes the interval; zero turns it off.

Because a ping is answered before any check, it is also the cheapest frame to send in a loop. One
connection is answered at most once per second (`zeroz.ws.keepaliveMinIntervalMillis`); pings that
arrive faster are ignored and cost nothing. A working client is nowhere near that limit — it waits 25
seconds. The answer is written on the connection's own read thread and never becomes a task, so a
connection is still answered while it is busy.

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

Note the tag space (`0x00`–`0x21`) is independent of the frame opcode space; a tag byte never appears
where an opcode is expected.

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
   `List` of every object handle this client holds. The server answers with one `0x10` frame per
   handle it still knows, carrying that object's current state, applied in place on the client
   exactly like any LiveSync update. Re-serializing also re-registers the objects' lazy-field
   handles for the new session. Handles the server does not know — it restarted since they were
   fetched — produce no frame and one server-side log line naming the count.

A handle presented to `zeroz4j.resync` is answered only when the server's own record says that
browser was sent the object. Presenting a handle used to be proof enough, on the theory that a handle
can only be learned by being sent the object — which is not so, because an object nested inside a
broadcast event or a shared signal goes out with its handle attached, teaching every recipient the
handles of things it was never given.

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
