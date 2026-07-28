# Local or shared signal?

`ValueSignal` and `Signals.shared` produce the same type and behave identically at the call site.
The only difference is whether the value crosses the wire. That makes the choice easy to get wrong by
accident, and easy to get right on purpose.

## Default to local

Most UI state has no business leaving the browser tab:

- form field contents while the user types
- which tab, accordion panel or dialog is open
- filter, sort and search terms
- pagination position
- the light/dark theme toggle
- transient validation messages

```java
ValueSignal<String> filter = new ValueSignal<>("all");
ValueSignal<Boolean> darkTheme = new ValueSignal<>(true);
```

Sharing these is not merely wasteful. Because shared signals broadcast to every session, a shared
filter means **one user's filter changes everyone's view**.

## Share only when other clients must agree

A shared signal is right when the value is genuinely one fact about the system that every client
should see the same way, and a client arriving late must see the current state:

- a background job's progress
- a feature flag or runtime configuration value
- a price, a rate, a queue depth
- who is currently online

```java
public final class JobSignals {
    public static final ValueSignal<JobStatus> STATUS = Signals.shared(JobStatus.idle());
}
```

## Three things to know before sharing

**It is JVM-global.** The registry behind `Signals.shared` is static — one value per name for the
whole server, across every user and every tenant. There is no per-session shared signal. If the value
is not the same for everybody, do not use one; see
[Unbounded broadcast](antipatterns.md#unbounded-broadcast).

**The default name is the payload's class name.** So there is exactly one default shared signal per
type, and a second declaration silently returns the first — ignoring its initial value, its
writability and its roles.

```java
// Both are the SAME signal.
Signals.shared(new Temperature(20));
Signals.shared(new Temperature(5));

// Name them.
Signals.shared("temp.indoor",  new Temperature(20));
Signals.shared("temp.outdoor", new Temperature(5));
```

**The payload must be serializable.** A `@DataModel` class or a supported built-in type. A
serialization failure is caught and logged rather than thrown, so the signal simply never propagates.

## Client writes

Shared signals are server-authoritative by default: a client-side `set()` throws
`IllegalStateException`. Opt in per signal:

```java
Signals.sharedWritable("doc.title", "Untitled", "editor");
```

An empty role list means *any* session may write, anonymous included. Be explicit.

## Derived state is always local

`Computed` never crosses the wire, and does not need to. Derive locally from whatever holds the
source — local signal or shared:

```java
Computed<Integer> remaining = new Computed<>(() ->
        (int) tasks.get().stream().filter(t -> !t.isDone()).count());

Computed<String> label = new Computed<>(() ->
        JobSignals.STATUS.get().getPercent() + "%");
```

Bind two-way only to a `ValueSignal`. Passing a `Computed` to `bindValue` silently degrades to a
read-only binding.

## Summary

| | Local signal | Shared signal |
|---|---|---|
| Created with | `new ValueSignal<>(v)` | `Signals.shared(...)` |
| Crosses the wire | no | yes |
| Scope | one browser tab | the whole JVM, every session |
| Late joiner | not applicable | receives the retained value |
| Payload constraints | none | must be wire-serializable |
| Client may write | always | only with `sharedWritable` |
| Right for | most UI state | facts every client must agree on |
