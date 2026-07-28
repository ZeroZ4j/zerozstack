# Choosing how state moves

ZeroZ Stack gives you five ways to move state around. Picking the wrong one is the most common source of
trouble in ZeroZ Stack applications, and the symptoms are rarely obvious — a feature works on your
machine and fails for the second user, or works until someone reloads the page.

This page is the decision procedure. Work through it in order; each question has one answer.

## The five mechanisms

| Mechanism | Direction | Carries |
|---|---|---|
| Local signal — `ValueSignal`, `Computed`, `Effect` | none, client only | a value |
| RMI call — `@RmiService` | client → server → reply | a request and its result |
| Server event — `EventTopic` | server → all clients | a discrete occurrence |
| Shared signal — `Signals.shared` | server ⇄ clients | one current value |
| LiveSync — `@LiveSync` | server ⇄ clients | an object's fields, updated in place |

## The decision procedure

**1. Does anything outside this browser tab need to know?**

*No* → **local signal**. Stop here. Most UI state belongs here: form contents while typing, which
tab is selected, filter and sort choices, whether a dialog is open. Sending these anywhere is at
best waste and at worst a bug that makes one user's filter change everybody's view.

*Yes* → question 2.

**2. Does the client need an answer, or is it invoking a named operation?**

Operations are the things you would name with a verb and audit: *approve*, *check out*, *delete*,
*log in*, *submit*. They have rules, they can fail, and the caller wants to know what happened.

*Yes* → **RMI call**. Stop here. See [RMI or state sync?](rmi-vs-state-sync.md).

*No — the server has news to volunteer, or the client is editing shared state* → question 3.

**3. Is the data private to one user or session?**

*Yes* → scope the push explicitly, and continue to question 4 to pick the mechanism. A **server
event** takes `publishToUser` / `publishToSession`; **LiveSync** takes `Scope.SESSION` or
`Scope.USER`. A **shared signal cannot be scoped at all** — it is one value the whole server agrees
on — so per-user state is never a shared signal.

The unscoped forms, `publish(topic, payload)` and a shared-signal `set()`, reach **every connected
session** with no principal check. Using one for somebody's data is a leak, not an inefficiency.

*No, everyone sees the same thing* → question 4.

**4. Would keeping only the latest value lose information?**

*Yes — each occurrence matters on its own* → **server event**. A chat message, a log line, "job 47
failed", "a user joined". You would not be satisfied with only the most recent one.

*No — only the current value matters* → question 5.

**5. One value replaced wholesale, or an identified object edited field by field?**

*One value* → **shared signal**. A job's progress, a price, a feature flag, a presence list. The
server `set()`s it and every client mirror follows; a client that connects late immediately receives
the retained value.

*An identified object that several views hold a reference to, edited through individual setters* →
**LiveSync**. A live object is itself a reactive dependency: read a getter inside an `Effect` and an
inbound sync re-runs it. See
[LiveSync objects are reactive](events-vs-signals-vs-livesync.md#livesync-objects-are-reactive).

## Two rules of thumb

**State edits sync, operations call.** If it is an edit to a value, let it sync. If it is something
happening, give it a name and call it.

**Events are news, signals are facts.** News is missed if you were not listening. Facts are true
when you arrive.

## Next

- [Events, signals or LiveSync?](events-vs-signals-vs-livesync.md) — the full comparison, with
  worked examples and the honest limits of each
- [RMI or state sync?](rmi-vs-state-sync.md)
- [Local or shared signal?](local-vs-shared-signal.md)
- [Anti-patterns](antipatterns.md) — named failure modes and their fixes
