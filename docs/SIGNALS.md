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

## Scoped signals: one value per tenant, user or browser

A shared signal is one value the whole server agrees on. That is wrong for anything belonging to
somebody. `Signals.scoped(name, initialValue, scope)` declares the same thing narrowed: one retained
value per target, and a client only ever sees its own.

```java
// shared module
public final class BasketSignals {
    public static final ScopedSignal<Basket> BASKET =
            Signals.scoped("shop.basket", Basket.empty(), Scope.CLIENT);
}
```

```java
// server — name the target, so who receives it is never accidental:
BasketSignals.BASKET.forTarget(RmiRequestContext.getClientId()).set(updated);

// client — indistinguishable from any other signal:
Effect.create(() -> badge.setText(BasketSignals.BASKET.mine().get().itemCount() + " items"));
```

The client calls `mine()` with no argument and never learns its own target. That is the point: a
browser that could name a target could name somebody else's. The server resolves it from the
handshake, and the wire frame carries the family's name, not the target's — so no client can tell
that other targets exist, let alone what they hold.

### Choosing a scope

Picking one decides who receives the value. It is not a preference.

| Scope | Keyed by | Needs a login? | Survives reconnect? |
|---|---|---|---|
| `Scope.SESSION` | the WebSocket session id | no | **no** — session ids change on every drop |
| `Scope.CLIENT` | the browser's client id | no | yes, and page reloads too |
| `Scope.USER` | the authenticated user name | yes | yes |
| `Scope.TENANT` | the authenticated tenant | yes | yes |

`Scope.CLIENT` is the one for an application with no login: the client id is issued by the server and
stored in an `HttpOnly` cookie, so it outlives both reconnects and reloads. See
[client identity](guides/security-auth.md#client-identity-without-a-login).

**`CLIENT` and `SESSION` are not a boundary between people.** They identify a browser, not a person:
two people sharing a machine share the id. Anything one user must not see needs `USER` or `TENANT`,
which require real authentication.

### Rules

* **One name, one meaning.** A wire name is either shared or scoped, never both — declaring it twice
  throws rather than quietly giving it two meanings.
* **`Scope.GLOBAL` is refused**, because a global signal is what `Signals.shared` already is.
* **A session with no target receives nothing.** An anonymous session has no user and no tenant, so
  it never matches a `USER` or `TENANT` signal — no initial value is guessed and nobody else's is
  sent.
* **Each tier gets one method.** On the server `forTarget(...)` works and `mine()` throws; on a client
  it is the other way round. With no transport installed — a plain unit test — both work locally.
* **Client writes** use `Signals.scopedWritable(...)` and land on the writer's own target. Roles and
  validation are checked exactly as for a writable shared signal.

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
