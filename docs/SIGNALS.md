# Signals: Client-Side Reactive State

ZeroZ Stack has **one signal abstraction** with three scopes: local to the client, local to the server, or shared across both. State lives in **signals**, derived values are **computed**, and rendering (or any side effect) happens in **effects** that re-run automatically when anything they read changes. No manual listener wiring, no "remember to update the label too" bugs.

The signal core (`com.zeroz4j.signals`) lives in the shared API module and knows nothing about the network. Server events can *feed* signals (see [SERVER_EVENTS.md](SERVER_EVENTS.md)), but neither feature requires the other.

## The primitives

| Type | Role |
|---|---|
| `ValueSignal<T>` | Mutable source state: `get()`, `set(value)`, `update(fn)` |
| `Computed<T>` | Lazily evaluated derived state; recomputes when dependencies change |
| `Effect` | Side-effect runner (usually rendering); re-runs when dependencies change |
| `ObservableSignal<T>` | The interface custom signal implementations must implement to participate in tracking |
| `Disposable` | Returned by `Effect.create`; releases the subscription |

## Automatic dependency tracking

Reading a signal with `.get()` inside a `Computed` or `Effect` registers it as a dependency — there is no subscribe call:

```java
ValueSignal<List<Task>> tasks = new ValueSignal<>(new ArrayList<>());
ValueSignal<String> filter = new ValueSignal<>("all");

Computed<List<Task>> visible = new Computed<>(() ->
        tasks.get().stream().filter(t -> matches(t, filter.get())).toList());

Computed<Integer> remaining = new Computed<>(() ->
        (int) tasks.get().stream().filter(t -> !t.isDone()).count());

Disposable render = Effect.create(() -> renderList(visible.get()));
Disposable badge  = Effect.create(() -> label.setText(remaining.get() + " open"));
```

Changing `tasks` or `filter` now updates exactly the parts of the UI that depend on them.

## The immutability contract

`ValueSignal.set()` skips notification when the new value `equals` the old one. **Never mutate a value in place and set it back** — the signal cannot see the change:

```java
// WRONG — same list reference, equality check swallows it, nothing re-renders:
tasks.get().add(task);
tasks.set(tasks.get());

// RIGHT — immutable update produces a new list:
tasks.update(current -> {
    List<Task> next = new ArrayList<>(current);
    next.add(task);
    return next;
});
```

## Shared signals: one declaration, both tiers

A signal created with `Signals.shared(name, initialValue)` is the same `ValueSignal` type, bound to a wire identity. Declare it **once** as a constant in your shared API module — the constant *is* the signal; there is no topic object, no subscribe call, no publish call:

```java
// shared module
public final class JobSignals {
    public static final ValueSignal<JobStatus> STATUS =
            Signals.shared(JobStatus.idle());
}
```

```java
// server — the whole propagation story:
JobSignals.STATUS.set(next);

// client — indistinguishable from a local signal:
Effect.create(() -> progressBar.setValue(JobSignals.STATUS.get().getPercent()));
```

Because the shared module compiles into both tiers, each tier holds its own instance of the constant bound to the same name; the runtime gives it its role. The server instance broadcasts on `set()` and **retains the latest value**; a client mirror receives the retained value the moment it subscribes — late joiners are always current, with no snapshot fetch and no merge logic. In a plain unit test with no transport installed, a shared signal behaves exactly like a local one.

Semantics and current limits, stated plainly:

* **Server-authoritative by default** — a client-side `set()` on a `Signals.shared(...)` signal throws `IllegalStateException`. Opt specific signals into client writes with `Signals.sharedWritable(initialValue)` or `Signals.sharedWritable("name", initialValue, "role"...)`: the client applies the write optimistically and sends it up; the server — still authoritative — accepts it (role check plus the value's [validation annotations](VALIDATION.md)) and broadcasts to everyone, or rejects it and answers with a corrective update that snaps the writer back to server truth. Last accepted write wins; there is no per-field merging — writes replace the whole value.
* **Latest-wins state, not events** — consecutive equal values are deduplicated, and there is no history or replay of intermediate values. For discrete occurrences use [server events](SERVER_EVENTS.md).
* **Serializable payloads** — the value type must be wire-serializable (`@DataModel` or a `BinarySerializer`-supported type). Treat shared values as immutable: `set()` a new instance, never mutate the current one.
* **Naming** — the wire name defaults to the payload's class name (the same runtime identity the binary serializer already puts on the wire), giving one default signal per type. Need several signals of the same type, or a stable name across payload-class renames? Use `Signals.shared("explicit.name", initialValue)`.
* **Reconnection is automatic.** When a dropped WebSocket restores itself, every shared signal re-subscribes and snaps its mirror to the current retained value — updates broadcast during the outage are not missed, they arrive as the fresh value. A write made to a `sharedWritable` signal *while offline* is applied optimistically on screen, queued, and sent on reconnect (last value only); the server then accepts and broadcasts it, or corrects the writer, exactly as it would have online.

## Component binding

Fields support two-way binding to a signal:

```java
ValueSignal<Boolean> darkTheme = new ValueSignal<>(true);
themeToggle.bindValue(darkTheme);   // toggle ⇄ signal stay in sync both ways
```

## Lifecycle

`Effect.create` returns a `Disposable`; `Computed` has a `dispose()` method. A view that creates effects or computeds must release them when it is permanently removed, or its upstream signals keep it alive:

```java
private final List<Disposable> disposables = new ArrayList<>();
...
disposables.add(Effect.create(() -> ...));
...
public void dispose() {
    disposables.forEach(Disposable::dispose);
    disposables.clear();
    myComputed.dispose();
}
```

## Threading

Signals are not synchronized — treat them as **UI-thread-only**. Server push handlers are dispatched onto the platform UI scheduler, so reducing events into signals from a `ServerEvents.on` handler is safe.

## Custom signals

Anything implementing `ObservableSignal<T>` (get + add/removeListener) participates in `Effect`/`Computed` tracking. The framework's own `ServerEvents.LatestSignal` — a signal holding the most recent payload of an event topic — is built exactly this way.

## When to use signals

* **Use them** when state is rendered in more than one place, or when values derive from other values (counts, filters, validation) — the cases where manual `render()` bookkeeping drifts.
* **Skip them** when a view has a single render path and no derived state — a plain field plus a render method is simpler (see the `chat-events` example for that style).

The `todo-signals` example (`zerozstack-examples/todo-signals`) demonstrates the full model in isolation.
