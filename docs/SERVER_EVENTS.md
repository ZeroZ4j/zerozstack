# Server Events: Typed Push Topics

ZeroZ Stack lets the server broadcast **typed events** to connected Wasm clients over the existing binary WebSocket — no REST callbacks, no JSON, no hand-maintained topic strings on either side.

## Terminology

ZeroZ Stack uses four terms with distinct meanings — keeping them apart keeps the mental model clean:

| Term | Meaning |
|---|---|
| **Event** | A discrete, fire-and-forget *occurrence* broadcast from server to client: `EventTopic`, `EventPublisher`, `ServerEvents`. There is no "current value" and no replay. |
| **Signal** | Reactive *state*: `ValueSignal`, `Computed`, `Effect` — local to either tier or shared across both via `Signals.shared` (see [SIGNALS.md](SIGNALS.md)). A separate, independent feature — **events do not require signals**. |
| **Push** | The transport direction: the 0x02 PUSH frame that carries events over the WebSocket. |
| **Message** | Reserved for application domains (e.g. a `ChatMessage` in a chat app). Never a framework concept — ZeroZ Stack is not a message broker. |

## Declaring topics

Declare each topic **once**, in your shared API module. The declaration binds the wire name to the payload type; server and client both compile against it, so a payload mismatch is a compile error:

```java
public final class ChatEvents {
    public static final EventTopic<ChatMessage> MESSAGE_POSTED =
            EventTopic.of(ChatMessage.class, "chat.messagePosted");

    public static final EventTopic<Void> HISTORY_CLEARED =
            EventTopic.of(Void.class, "chat.historyCleared");
}
```

Topic names are explicit strings, deliberately *not* derived from class names: renaming or moving a payload class never silently changes the wire protocol, and the names survive Wasm class-name minification.

## Publishing (server)

Inject `EventPublisher` into your `@RmiService` implementation — not the transport engine — and publish:

```java
@ApplicationScoped
public class ChatServiceImpl implements ChatService {
    @Inject private EventPublisher events;

    @Override
    public void sendMessage(String text) {
        ChatMessage msg = ...;
        // persist ...
        events.publish(ChatEvents.MESSAGE_POSTED, msg);
    }

    @Override
    public void clearHistory() {
        // persist ...
        events.publish(ChatEvents.HISTORY_CLEARED);
    }
}
```

## Subscribing (client)

`ServerEvents.on` registers a typed handler — an ordinary callback. Update your components directly in it:

```java
Disposable sub = ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg -> {
    messages.add(msg);
    render();
});
```

Every subscription returns a `Disposable` — dispose it when the owning view is permanently removed. Handlers run on the platform UI scheduler when one is configured.

The `chat-events` example (`zerozstack-examples/chat-events`) demonstrates this pattern end-to-end.

## Avoiding the snapshot race

If a view loads initial state via RMI *and* listens for events, subscribe **before** fetching, then merge the snapshot with anything that arrived while the fetch was in flight (deduplicate via value equality on the payload):

```java
ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg -> ...);   // 1. subscribe first
List<ChatMessage> history = chatService.getHistory();     // 2. then fetch
// 3. merge history with already-received events
```

## Combining with Signals (optional)

Signals are **not required** to consume events. The rule of thumb:

* **One render path** (a handler updates one component): plain handlers, as above.
* **Derived or multiply-rendered state** (counts, filters, the same data shown in several places): hold the state in a `ValueSignal` and let the handler *reduce* the event into it with an immutable update — rendering then follows automatically via `Effect`, and the two can never drift apart:

```java
ValueSignal<List<ChatMessage>> messages = new ValueSignal<>(new ArrayList<>());

Disposable sub = ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg ->
        messages.update(list -> {
            List<ChatMessage> next = new ArrayList<>(list);
            next.add(msg);
            return next;
        }));
```

If what you are broadcasting genuinely *is* state (a status, a live counter), you usually don't want events at all — declare a [shared signal](SIGNALS.md#shared-signals-one-declaration-both-tiers) instead: the server `set()`s it, every client mirror updates automatically, and late joiners receive the retained value. `ServerEvents.latest(topic, initialValue)` remains as a bridge for deriving last-seen state from a genuine event stream.

See [SIGNALS.md](SIGNALS.md) and the `todo-signals` example for the reactive model itself.

## Delivery semantics

Stated plainly so there are no surprises:

* **Broadcast** to all currently connected sessions (no per-topic subscription filtering yet).
* **At most once** — a disconnected client misses events; there is no queueing, acknowledgement, or redelivery.
* **No replay** — late subscribers do not receive past events.
* Payloads must be wire-serializable: `@DataModel` classes or types supported by `BinarySerializer`.

If you need durable delivery or replay, model it in your application (as the snapshot-then-merge pattern above does) — ZeroZ Stack deliberately does not include broker semantics.
