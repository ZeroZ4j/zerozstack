# Events, signals or LiveSync?

Three mechanisms push state from the ZeroZ Stack server to connected clients. They look
interchangeable and are not. This page compares them on the properties that actually decide the
choice, states each one's limits plainly, and works through concrete features.

If you just want the answer, use the [decision procedure](index.md).

## When to use each

- **Server event** — a discrete occurrence that every client should hear about *as it happens*, and
  where keeping only the latest one would lose information.
- **Shared signal** — one current value that every client should agree on, including clients that
  connect later.
- **LiveSync** — an identified object that lives on both tiers, whose fields are edited
  individually, or any push that must be scoped to a single session or user.

## Side by side

| | Server event | Shared signal | LiveSync |
|---|---|---|---|
| Declared as | `EventTopic.of(Class, "name")` constant | `Signals.shared(...)` constant | `@DataModel @LiveSync` on a class |
| Annotations required | none | none | two, or three with `@ClientWritable` |
| Granularity | the whole payload, per occurrence | the whole value, deduplicated by `equals` | the whole object graph, applied in place |
| A late-joining client sees | nothing | the retained latest value | nothing until something pushes |
| Replay of past values | no | no — latest only | no |
| **Can be scoped to a session or user** | **no** | **no** | **yes** |
| Client writes | not applicable | opt in with `sharedWritable` | opt in with `@ClientWritable` |
| **Tells the view something changed** | the handler *is* the notification | yes, via `Effect` and `Computed` | yes, via `Effect` and `Computed` |
| Wire opcode (server → client) | `0x02` | `0x17` | `0x10` |

Two rows in that table decide more cases than all the others, and both are easy to miss.

## Scoping a push

`EventPublisher.publish(topic, payload)` and a server-side `set()` on a shared signal both reach
**every connected session**, with no principal check.

Events can be scoped explicitly:

```java
events.publishToUser(AccountEvents.BALANCE_CHANGED, balance, principalName);
events.publishToSession(UiEvents.TOAST, "Saved", RmiRequestContext.getSessionId());
```

LiveSync scopes the same way, with `Scope.SESSION` or `Scope.USER`.

Signals scope too, but through a different declaration. `Signals.shared` is one value the whole
server agrees on — the registry behind it is static, so there is exactly **one value per signal name
for the whole JVM**, across every user and every tenant. For state that belongs to somebody, declare
it with `Signals.scoped(...)` instead, which holds one retained value per target and sends each client
only its own.

So this is not a performance trade-off. It is a security boundary:

```java
// WRONG — every connected session receives this user's private balance.
public static final ValueSignal<Money> ACCOUNT_BALANCE = Signals.shared(Money.zero());

// RIGHT — one balance per user, and a client is never told another's.
public static final ScopedSignal<Money> ACCOUNT_BALANCE =
        Signals.scoped("account.balance", Money.zero(), Scope.USER);
```

See [Signals](../SIGNALS.md#scoped-signals-one-value-per-tenant-user-or-browser) for the scopes and
which of them work without a login.

When the data belongs to one user or session, use LiveSync and say so:

```java
import com.zeroz4j.server.SyncEngine;

@Inject SyncEngine syncEngine;

// Only the session that owns this object receives the update.
syncEngine.notifyChanged(balance, Scope.SESSION, sessionId);

// Or every session belonging to one authenticated user.
syncEngine.notifyChanged(balance, Scope.USER, principalName);
```

`Scope` has three values: `GLOBAL` (the default, used by the single-argument
`notifyChanged(obj)`), `SESSION`, and `USER`.

## LiveSync objects are reactive

When a sync frame arrives, the client resolves the object by its handle and writes the new values
into the fields of the instance you already hold — no re-fetching, no re-binding, no stale copies.

**And it tells the reactive system.** A `@LiveSync` object is a signal dependency in its own right:
reading one of its getters inside an `Effect` or `Computed` subscribes to it, exactly as reading a
`ValueSignal` does. An inbound sync then re-runs whatever read it.

```java
// The model stays a plain POJO — no interface, no base class, no Signal fields.
@DataModel @LiveSync
public class TeamProfile {
    private String mission;
    // getters and setters
}
```

```java
// The view. This is the whole thing.
Effect.create(() -> missionLabel.setText(profile.getMission()));
```

```java
// The server.
profile.setMission("Ship it");
syncEngine.notifyChanged(profile);
```

The annotation processor generates a `TeamProfile_Live` subclass whose getters report reads; the
client instantiates that subclass during deserialization. You never see it or reference it.

Granularity is **per object**, not per field: any sync touching the instance re-runs every effect that
read any of its getters. Updates arriving in one frame are batched, so an effect reading two objects
from the same sync re-runs against the fully applied graph rather than a half-updated one.

Two things still to know:

* **In-place collection edits are invisible**, as ever. `profile.getTags().add("x")` neither
  propagates nor re-renders. Reassign through the setter.
* **Dispose your effects.** `Effect.create` returns a `Disposable`; a view that creates effects over a
  live object must release them when it is removed, or the object keeps the view alive.

## Server events

Declare the topic once in the shared module, so both tiers compile against the same payload type:

```java
import com.zeroz4j.api.EventTopic;

public final class ChatEvents {
    public static final EventTopic<ChatMessage> MESSAGE_POSTED =
            EventTopic.of(ChatMessage.class, "chat.messagePosted");

    public static final EventTopic<Void> HISTORY_CLEARED =
            EventTopic.of(Void.class, "chat.historyCleared");
}
```

Topic names are explicit strings rather than derived from class names, so renaming or moving a payload
class never silently changes the wire protocol, and the names survive whatever the TeaVM build does to
class names.

Publish from the server by injecting `EventPublisher` — not the transport engine:

```java
import com.zeroz4j.server.EventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatServiceImpl implements ChatService {
    @Inject private EventPublisher events;

    @Override
    public void sendMessage(String text) {
        ChatMessage msg = persist(text);
        events.publish(ChatEvents.MESSAGE_POSTED, msg);
    }

    @Override
    public void clearHistory() {
        events.publish(ChatEvents.HISTORY_CLEARED);   // EventTopic<Void> needs no payload
    }
}
```

Subscribe on the client, and keep the `Disposable`:

```java
import com.zeroz4j.api.Disposable;
import com.zeroz4j.client.ServerEvents;

Disposable sub = ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg -> {
    messages.add(msg);
    render();
});
// ... later, when the view is permanently removed:
sub.dispose();
```

**Limits.** Delivery is at-most-once: a disconnected client misses events, and there is no queueing,
acknowledgement or redelivery. Late subscribers receive nothing. Payloads must be `@DataModel` or a
built-in serializable type, and a payload that is not serializable **fails silently** — the
exception is caught per session and logged on the server.

If a view loads initial state by RMI *and* subscribes to events, subscribe **first**, then fetch,
then merge — otherwise events that arrive during the fetch are lost:

```java
ServerEvents.on(ChatEvents.MESSAGE_POSTED, this::accept);   // 1. subscribe
List<ChatMessage> history = chatService.getHistory();       // 2. fetch
merge(history);                                            // 3. reconcile by value equality
```

## Shared signals

A shared signal is an ordinary `ValueSignal` bound to a wire name. Declare it once, as a constant in
the shared module — the constant *is* the signal. There is no topic object, no subscribe call and no
publish call:

```java
import com.zeroz4j.signals.Signals;
import com.zeroz4j.signals.ValueSignal;

public final class JobSignals {
    public static final ValueSignal<JobStatus> STATUS = Signals.shared(JobStatus.idle());
}
```

```java
// server — the entire propagation story
JobSignals.STATUS.set(new JobStatus(percent, step));
```

```java
// client — indistinguishable from a local signal
Effect.create(() -> progressBar.setValue(JobSignals.STATUS.get().getPercent()));
```

The shared module compiles into both tiers, so each tier holds its own instance of the constant bound
to the same name. The server instance broadcasts on `set()` and retains the latest value; a client
receives that retained value the moment it subscribes. Late joiners are always current, with no
snapshot fetch and no merge logic. In a unit test with no transport installed, a shared signal
behaves exactly like a local one.

Clients cannot write unless you allow it. A client-side `set()` on a `Signals.shared(...)` signal
throws `IllegalStateException`. Opt in with `Signals.sharedWritable`:

```java
// any authenticated or anonymous session may write
Signals.sharedWritable(initialValue);

// only sessions holding one of these roles may write
Signals.sharedWritable("doc.title", "Untitled", "editor", "admin");
```

The client applies the write optimistically and sends it up. The server stays authoritative: it
checks the roles and the value's validation annotations, then either broadcasts to everyone or
rejects and answers the writer with a corrective update that snaps the local mirror back to server
truth.

**Limits.** Latest-wins whole-value replacement — no per-field merge, no history, and consecutive
`equals`-equal values are dropped, so a rapidly changing signal can skip states the UI never sees.
An empty `writeRoles` list means *any* session may write, including anonymous ones. Serialization
failures are silent. And the default wire name is the payload's class name, which means:

```java
// Both calls return the SAME signal. The second initial value is silently ignored,
// as are its writability and roles.
ValueSignal<Temperature> indoor  = Signals.shared(new Temperature(20));
ValueSignal<Temperature> outdoor = Signals.shared(new Temperature(5));
```

Give every signal an explicit name unless you genuinely want one per type:

```java
ValueSignal<Temperature> indoor  = Signals.shared("temp.indoor",  new Temperature(20));
ValueSignal<Temperature> outdoor = Signals.shared("temp.outdoor", new Temperature(5));
```

## LiveSync

Annotate the class, then tell the engine after you mutate it:

```java
import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.DataModel;
import com.zeroz4j.api.LiveSync;
import com.zeroz4j.api.validation.NotBlank;

@DataModel @LiveSync @ClientWritable("editor")
public class TeamProfile {
    @NotBlank private String mission;
    private int headcount;
    // public no-arg constructor, getters and setters
}
```

```java
// server
profile.setMission("Ship it");
syncEngine.notifyChanged(profile);
```

With `@ClientWritable`, setter calls on the client propagate up automatically — no service method,
no explicit save:

```java
// client — this is the entire write path
profile.setMission("Ship it");
```

The client's instance is an APT-generated `TeamProfile_Live` subclass whose setters report changes.

!!! note "One frame per setter, today"
    The mutation path is designed to coalesce a burst of setter calls into a single frame, but the
    coalescing only engages when a platform scheduler is installed — and nothing in the framework
    currently installs one. In the present build each setter call flushes immediately, so a five-field
    edit sends five whole-object mutation frames. Batch your own edits if that matters.

The server stays authoritative and every
mutation passes three gates: the class must be `@ClientWritable`, the session must hold a declared
write role, and the proposed state must pass the model's validation annotations — checked against a
throwaway copy, so a rejected mutation never touches server state. Rejected mutations answer the
writer with a corrective sync.

Persist by implementing `LiveMutationListener`; the framework does not know your storage root:

```java
import com.zeroz4j.server.LiveMutationListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import java.security.Principal;

@ApplicationScoped
public class ProfilePersistence implements LiveMutationListener {
    @Inject EmbeddedStorageManager storage;

    @Override
    public void onMutated(Object model, Principal principal) {
        storage.store(model);
    }
}
```

**Limits.**

- **`notifyChanged` silently does nothing** if the object has no `ObjectMapper` handle — that is, if
  the server has never serialized it to a client. Return it from an RMI method at least once first.
  This is the single most common LiveSync surprise.
- **Setters are the tracking boundary.** `profile.getTags().add("x")` is invisible. Reassign through
  the setter, or call `LiveMutationTracker.touch(profile)` afterwards. Tracked collections do not
  exist.
- **Whole-object, last-write-wins.** Two unlocked concurrent editors race and the later write wins.
  Serialize them with `LiveMutex` where it matters. Field-level merging and version-conflict
  rejection are reserved in the protocol but not implemented.
- **Rejection reasons never reach the client.** They are logged on the server; the writer only sees
  its optimistic change snap back.
- A `@ClientWritable` field with no setter, and `@ClientWritable` without `@LiveSync`, both produce
  compiler **warnings, not errors**. Read your build output.

## Worked examples

The wrong answers below are all plausible — they are the ones people actually reach for.

| Feature | Use | Tempting wrong choice, and why it fails |
|---|---|---|
| Chat message stream | event | Shared signal — only the last message survives, history is lost |
| "Someone is typing…" | shared signal, `sharedWritable` | Event — the indicator never clears if the stop event is missed |
| Job progress bar | shared signal | Event — a client opening the page mid-job sees nothing until the next tick |
| "Job finished" toast | event | Shared signal — a late joiner replays a toast for a job that ended an hour ago |
| Collaborative document body | LiveSync + `@ClientWritable` | Shared signal — every keystroke replaces the whole document, and concurrent edits to different fields clobber each other |
| "Add to cart" | RMI | LiveSync — an operation with rules and a result disguised as a field edit |
| Form contents while typing | local signal | Anything shared — you are broadcasting keystrokes |
| Filter, sort, selected tab | local signal | Shared signal — one user's filter changes everyone's view |
| Inventory quantity, staff-editable | LiveSync + `@ClientWritable("staff")` | RMI per keystroke — chatty, and other viewers never see it |
| Online-user list | shared signal | Join and leave events — one missed event and the list is wrong forever |
| Audit log tail | event | LiveSync — you want appends, not a mutable past |
| Feature flag | shared signal | RMI at startup — never updates without a reload |
| A user's own notifications | LiveSync, `Scope.USER` | Event or shared signal — **broadcasts to every session, including other tenants** |
| A badge counting items in a list | `Computed` over whatever holds the list | Recomputing in each render path — the copies drift apart |

## Combining mechanisms

They compose, and the combinations are often right:

- **Event reduced into a signal.** When an event's data is rendered in more than one place, or
  derived values hang off it, let the handler reduce it into a `ValueSignal` and render from
  `Effect`. The two can then never drift apart.

    ```java
    ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg ->
            messages.update(list -> {
                List<ChatMessage> next = new ArrayList<>(list);
                next.add(msg);
                return next;
            }));
    ```

- **RMI to act, sync to observe.** Call an RMI method to *do* the thing; let a shared signal or
  LiveSync carry the resulting state change to everyone. `job-monitor` is exactly this: `startJob()`
  over RMI, progress over a shared signal.
- **A live object read inside an `Effect`**, which is simply how LiveSync is consumed — see above.

What you should *not* do is use an event to carry state, or a shared signal to carry a stream. See
[anti-patterns](antipatterns.md).
