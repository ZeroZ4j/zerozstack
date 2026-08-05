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

### Server Responses (Server -> Client)

Server responses include an explicit opcode byte at index 4.

* **0x01 — RMI_RESPONSE (Success)**
  * Payload: `[Type Tag + Value]` (The return value of the method)
* **0x0F — RMI_ERROR (Error)**
  * Payload: `[String]` (Error message)
* **0x02 — PUSH (Server-initiated)**
  * Payload: `[String]` (Topic Name) + `[Type Tag + Value]` (Payload)
* **0x03 — AUTH (Authentication Handshake)**
  * Sent automatically on connection open.
  * Payload: `[String]` (Username) + `[4 bytes]` (Role Count) + `[N Strings]` (Roles)

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
  * Shared signal value. Broadcast to all sessions on every server-side change, and sent
    directly to a session in response to a subscribe.
  * Payload: `[String]` Signal Name + `[Type Tag + Value]` Serialized Value

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

Presenting a handle to `zeroz4j.resync` is treated as proof of prior disclosure, the same trust
model as LiveSync mutation: handles are unguessable random UUIDs a client can only have learned by
being sent the object.

In-flight RMI requests are **not** replayed. Their suspended callers fail with
`DisconnectedException` the moment the drop is detected, as do calls attempted while disconnected.

Collections are rebuilt on the receiving side as `ArrayList`, `LinkedHashSet` and `LinkedHashMap`.
Declare `@DataModel` fields as `List`, `Set` and `Map` rather than concrete implementation types.
