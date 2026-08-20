# LiveSync: Two-Way Object Synchronization

LiveSync keeps the same object alive on both tiers. It has two directions, each opt-in:

* **Down (server → clients)**: annotate the class `@LiveSync`; after mutating it on the server, call `syncEngine.notifyChanged(obj)` — every client's instance updates **in place**.
* **Up (client → server)**: additionally annotate `@ClientWritable`; setter calls on the client's instance propagate to the server **automatically** — no service method, no explicit save:

```java
@DataModel @LiveSync @ClientWritable("editor")
public class TeamProfile {
    @NotBlank private String mission;
    @Min(1) private int headcount;
    ...
}
```

```java
// client — this is the entire write path:
profile.setMission("Ship it");
```

## How the up direction works

Deserialization on the client instantiates an APT-generated `<Model>_Live` subclass whose setters report changes. A burst of setter calls coalesces into one whole-object mutation frame. The **server stays authoritative** — every mutation passes three gates before it touches the canonical instance:

1. the class is `@ClientWritable` (without it, mutations are ignored — deny by default);
2. the session holds a declared write role, if any (`@ClientWritable("editor")`);
3. the proposed state passes the model's [validation annotations](VALIDATION.md), checked against a throwaway copy so a rejected mutation never touches server state.

Accepted mutations are applied in place, announced to `LiveMutationListener` beans, and re-broadcast to all sessions. Rejected mutations answer the writer with a corrective sync that reverts its optimistic local change.

### Every object the change touches is checked, not just the outer one

A mutation frame carries a whole object graph, and applying it writes each part into the server object that part names.
So the three gates above are applied to **every** server object the frame reaches.

If a `@ClientWritable` model contains another model, that inner model needs `@ClientWritable` of its own before a client can edit it as part of the outer one:

```java
// The client may edit a Team, including the Member inside it.
@DataModel @LiveSync @ClientWritable
public class Team {
    private String name;
    private Member member;      // Member is @ClientWritable too, so this part is editable
    private AuditTrail audit;   // AuditTrail is not, so a change touching it is refused
    ...
}
```

A change that reaches an object without that mark is refused **in full**: nothing is applied, nothing is broadcast, and the writer is snapped back and told which type it was not allowed to write.
The same happens when the inner model names a role the connection does not hold.

This is a security rule rather than a convenience.
Objects travel with their name attached, so a client knows the names of everything it has ever been sent — including things nested inside a broadcast it merely received.
Checking only the outer object let a client change something it may write, with something it may not write smuggled inside, and the server then published the smuggled version to everyone.

If a refusal appears where an edit used to work, the fix is to decide which of the two you meant: add `@ClientWritable` to the inner model, or stop sending it up by leaving it out of the client's copy.

## Persistence and business logic

The framework does not know your storage root — implement `LiveMutationListener` to persist and audit:

```java
@ApplicationScoped
public class ProfilePersistence implements LiveMutationListener {
    @Inject ZeroZDbNode db;

    @Override
    public void onMutated(Object model, Principal principal) {
        // A write-block, so the change commits atomically and is on disk when this returns.
        db.localDb().write(ctx -> ctx.store(model));
    }
}
```

For writes that are *operations* rather than edits — "checkout", "approve", "close ticket" — keep using RMI service methods: an operation deserves a name, its own security annotations, and a validation point. The doctrine: **state edits sync, operations call.**

## Locking an object while you edit it

Two people editing the same object race, and the later write wins.
`LiveMutex` stops that: the second person waits until the first one is done.

```java
import com.zeroz4j.api.LiveMutex;

// client - take the lock, edit, always hand it back
LiveMutex mutex = LiveMutex.get(profile);
mutex.setLostListener(() -> form.setEnabled(false));   // the connection dropped; the lock is gone
mutex.lock();
try {
    profile.setMission("Ship it");
} finally {
    mutex.unlock();
}
```

### You can lock only an object the server sent you

Every object travels with a name - a **handle** - and locking asks for an object by name.
A name is not a permission.
The server keeps a record of the objects it has actually sent to each browser, and a lock request for anything else is refused at once, with a sentence saying so.

This matters because the lock service is open to every connection, signed in or not, and it has to stay that way: applications with no login use it too.
Without the rule, anyone could take the lock on an object they had never been shown and make everybody who really needed it wait the full timeout for nothing.

Three things follow:

* **Fetch the object, then lock it.** Anything your service returned, synced or pushed to this browser is lockable. Something you only heard the name of is not.
* **A reconnect changes nothing.** The record is kept per browser, and the browser id survives a drop and a page reload.
* **A very old object may need re-fetching first.** The record is dropped after 24 hours of inactivity, and holds at most 10,000 objects per browser. If a lock is refused for an object you really did hold, fetch it again from your service and lock the copy you get back.

A deployment that does have logins can go further and allow locking only on signed-in connections: set `zeroz.livemutex.requireAuthentication=true`.
It is off by default, because switching it on stops every anonymous application from locking anything.

### Waiting, and giving up

A caller waits **30 seconds** for a lock somebody else holds, then the call fails with a message naming that wait.
Nothing is changed when it fails.
Set `zeroz.livemutex.waitSeconds` to allow longer or shorter.

Callers are served in the order they arrived, so a queue of editors does not starve the one who has been waiting longest.
The server keeps a lock only while somebody holds it or is waiting for it, so finished edits leave nothing behind.

## Rules and limits (stated plainly)

* **Setters are the tracking boundary.** Mutations must go through setters. In-place collection edits (`obj.getTags().add(...)`) are invisible — reassign via the setter or call `LiveMutationTracker.touch(obj)` afterward. Tracked collections are planned.
* **Whole-object, last-write-wins.** Mutations replace the object's state; two unlocked concurrent editors race and the later write wins. Serialize editors with `LiveMutex` (see the collab-editor example pattern) where that matters. Field-level merging and version-conflict rejection (`MUTATE`/`ACK`/`REJECT` versions) are reserved in the protocol but not yet implemented.
* **Re-rendering is automatic.** A `@LiveSync` object is a reactive dependency: read one of its getters inside an `Effect` or `Computed` and an inbound sync re-runs it. Notification is per object, not per field.
* Only objects the server has previously synced to the client can be mutated (the canonical instance must exist in the server's object mapper).
* **Every object a change reaches is checked, not just the outermost one.** A model nested inside a `@ClientWritable` model needs its own `@ClientWritable` before a client can edit it, and one refusal refuses the whole change.
* **Being sent an object is what earns the right to re-read it.** The server remembers what it sent to which browser, and answers a re-read only from that record.
* **Being sent an object is also what earns the right to lock it.** Same record, same rule. A lock request for an object this browser was never sent is refused immediately.

## Reconnection

A dropped WebSocket needs nothing from the application. When the connection restores itself (which
it does automatically, with backoff and a built-in banner):

* Edits made to `@ClientWritable` objects **while offline** are retained and sent first, as the
  usual whole-object mutations. Last-write-wins settles them against anything that changed
  server-side during the outage.
* Every live object this client holds is then **re-synced**: the server re-sends its current state
  and it is applied in place, so effects re-run and the screen is correct again — including changes
  broadcast while the socket was down, which would otherwise be silently missing.

Two things do not come back by themselves. A held `LiveMutex` is released by the server the moment
the session closes; the holder learns of it through `mutex.setLostListener(...)` and should stop
accepting edits. And if the **server itself restarted**, its handle registry is empty, so re-sync
cannot restore the objects — the application re-fetches them the way it first obtained them (the
server log names how many handles were unknown).

### What re-sync will and will not send back

Every object travels with a name — a **handle** — and the client asks for objects back by naming them.
A name is not a permission.
The server keeps a record of the objects it has actually sent to each browser, and re-sync answers only for objects in that record.

Three things follow, and only the third is likely to surprise anyone:

* **A reconnect works as before.** The record is kept per browser, not per connection, and the browser id survives a drop and a page reload. A new connection from the same browser restores everything that browser holds.
* **A name learned some other way is worth nothing.** An object nested in a broadcast event or a shared signal goes out with its name attached, so its name is known to everyone who received the outer payload. None of them can fetch it.
* **A client with no cookie re-fetches instead.** A non-browser client that carries no browser id is remembered only for the life of one connection, so after a reconnect its objects come back the way it first obtained them. The server log says so once at startup.

The record is bounded: at most 10,000 objects per browser, and a browser's whole record is dropped after 24 hours of inactivity (`zeroz.disclosure.maxHandlesPerClient` and `zeroz.disclosure.idleHours`).
A dropped record behaves exactly like a server restart — the client is told nothing was found and re-fetches.

## Choosing a propagation feature

| | Shape | Client writes |
|---|---|---|
| **Shared signal** | one value, latest-wins, retained | `sharedWritable` opt-in |
| **Server event** | discrete occurrence, no replay | n/a (events come from the server) |
| **LiveSync** | object graph, in-place | `@ClientWritable` opt-in |
