# Anti-patterns

Named failure modes, so they can be pointed at in a code review. Each is something ZeroZ Stack
applications actually do. Most now fail loudly rather than silently, but the mistake is still worth
avoiding — see [Troubleshooting](../guides/troubleshooting.md) for the symptom-first version.

## Event-as-state

Broadcasting state through a `EventTopic` instead of a shared signal.

```java
// WRONG — a client that connects after this publish sees no progress at all.
events.publish(JobEvents.PROGRESS_CHANGED, new JobStatus(42));
```

Events have no retained value and no replay. It works while you test with a browser that was already
open, and fails for every user who arrives later or reloads.

**Fix:** declare a shared signal. The server `set()`s it, and late joiners receive the retained value
on subscribe.

```java
JobSignals.STATUS.set(new JobStatus(42));
```

## Signal-as-log

Pushing a growing collection through a shared signal instead of publishing events.

```java
// WRONG — resends the entire history on every message, and `equals` deduplication
// makes the behaviour hard to predict as the list grows.
CHAT_HISTORY.update(list -> append(list, msg));
```

**Fix:** publish an event per occurrence and let each client reduce it into its own local signal. One
message on the wire per message in the chat.

## In-place mutation

Mutating a value and setting it back. `ValueSignal.set` compares the new value with the old using
`equals` and does nothing when they match — and a mutated list equals itself.

```java
// WRONG — same list reference; the equality check swallows it, nothing re-renders.
tasks.get().add(task);
tasks.set(tasks.get());

// RIGHT — a new instance.
tasks.update(current -> {
    List<Task> next = new ArrayList<>(current);
    next.add(task);
    return next;
});
```

The LiveSync variant is the same mistake through a different door: setters are the tracking boundary,
so `profile.getTags().add("x")` reports nothing. Reassign through the setter, or call
`LiveMutationTracker.touch(profile)`.

## Operation-as-edit

Modelling an operation as a `@ClientWritable` field flip.

```java
// WRONG — "approve" has no name, no security annotation, no validation point,
// no audit trail, and no way to tell the caller it was refused.
invoice.setApproved(true);
```

An operation deserves a method. **State edits sync, operations call.**

```java
@RmiService
public interface InvoiceService {
    @RolesAllowed("approver")         // on the INTERFACE method — the implementation is not scanned
    void approve(String invoiceId);
}
```

## Unbounded broadcast

Putting per-user, per-tenant or otherwise sensitive data into a server event or a shared signal.

Both reach **every connected session**, with no principal check and no tenant filter. Shared signals
are worse: their registry is static, so there is one value per name for the entire JVM, across all
tenants.

```java
// WRONG — every session receives every user's balance.
public static final ValueSignal<Money> BALANCE = Signals.shared(Money.zero());
```

**Fix:** scope it. All three mechanisms take a scope:

```java
events.publishToUser(AccountEvents.BALANCE_CHANGED, balance, principalName);
syncEngine.notifyChanged(balance, Scope.USER, principalName);

// and a signal, declared once in the shared module
public static final ScopedSignal<Money> BALANCE =
        Signals.scoped("account.balance", Money.zero(), Scope.USER);
```

Note that `Signals.shared` is the unscoped one — it is a single value the whole server agrees on, and
per-user state must never be declared with it. `Signals.scoped` is the fix, not a workaround for it.

This is a security bug, not an efficiency one. Treat it as such in review.

## Polling a live object

Copying a `@LiveSync` object into a signal on a timer, because you expect it not to notify anyone.

```java
// WRONG — a live object is already a reactive dependency.
Window.setInterval(() -> messages.set(new ArrayList<>(state.getMessages())), 500);

// RIGHT — read the getter inside an Effect; an inbound sync re-runs it.
Effect.create(() -> messages.set(new ArrayList<>(state.getMessages())));
```

Reading a live object's getter inside an `Effect` or `Computed` subscribes to it. See
[LiveSync objects are reactive](events-vs-signals-vs-livesync.md#livesync-objects-are-reactive).

## Sync-before-send

Calling `syncEngine.notifyChanged(obj)` on an object the server has never sent to a client. It has no
`ObjectMapper` handle, so the call throws `IllegalStateException`.

**Fix:** return the object from an RMI method at least once — that is what registers the handle — then
`notifyChanged` works for the lifetime of the connection.

## Leaked effect

Creating an `Effect` in a view that is removed without disposing it. The upstream signal holds a
reference to the effect, which holds your view, which holds its components.

```java
private final List<Disposable> disposables = new ArrayList<>();

void build() {
    disposables.add(Effect.create(() -> label.setText(count.get() + " open")));
}

public void dispose() {
    disposables.forEach(Disposable::dispose);
    disposables.clear();
    myComputed.dispose();          // Computed has its own dispose()
}
```

The same applies to `ServerEvents.on`, which also returns a `Disposable`.

## Snapshot race

Fetching initial state and *then* subscribing. Anything that happens during the round trip is lost.

```java
// WRONG
List<ChatMessage> history = chatService.getHistory();
ServerEvents.on(ChatEvents.MESSAGE_POSTED, this::accept);

// RIGHT — subscribe, fetch, then reconcile by value equality
ServerEvents.on(ChatEvents.MESSAGE_POSTED, this::accept);
merge(chatService.getHistory());
```

## Polling over RMI

Calling an RMI method on a timer to discover whether something changed. The connection is already
persistent and bidirectional; the server can simply tell you.

**Fix:** a shared signal for current values, an event for occurrences.

There is no legitimate reason to poll: RMI answers, events announce, and shared signals and live
objects both notify the reactive system.

## Wrapping handlers in your own thread

```java
// WRONG — redundant. The listener body is already running on a suspendable thread.
sendButton.addClickListener(e ->
        new Thread(() -> chatService.sendMessage(text)).start());
```

`Component.addDomEventListener` already wraps every DOM listener via `Component.threaded(...)`, so your
handler body runs on a TeaVM green thread — which is exactly why a suspending RMI call works directly
inside it. Adding another thread buys nothing, and updates made from an extra thread may not repaint
until the next UI event.

```java
// RIGHT — just call it.
sendButton.addClickListener(e -> chatService.sendMessage(text));
```

For delayed work use `Window.setTimeout` rather than a thread plus a sleep.

## Trusting client validation

Validation annotations generate client-side field feedback *and* server-side enforcement, from the
same declaration. The client half is a courtesy to the user; it runs in code the user controls.

Never treat a client-side check as a boundary. The server re-validates RMI arguments, LiveSync
mutations and shared-signal writes independently — keep it that way, and put `@RolesAllowed` on
anything that matters.

## Unconsumed push

Broadcasting to a topic nothing listens for. It costs a serialization pass and a frame per session,
and it reads as working code.

If you publish a topic, subscribe to it somewhere — or delete the publish.
