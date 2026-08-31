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

Accepted mutations are applied in place, announced to `LiveMutationListener` beans, and re-broadcast to all sessions. Rejected mutations answer the writer with a corrective sync that reverts its optimistic local change, and with the reason.

### When an edit does not land (0.8.0+)

The change is put on the screen before it is sent, so a person can be looking at a value the server
never received. Two things can go wrong, and both arrive in the same place:

* **The server refused it** — not `@ClientWritable`, a missing role, or a value that fails the
  model's validation. The server sends the current state back first, so the screen is corrected, and
  the reason after it.
* **The browser could not send it** — the change could not be put on the wire at all. The client
  asks the server to re-send that object, which corrects the screen, and reports the failure.

Either way the application is told:

```java
LiveMutationRefusals.onRefused((model, reason) -> toast.show("Not saved: " + reason));
```

Nothing is thrown: by the time the answer arrives, the setter call is long finished. **With no
listener registered, every refusal is still written to the browser console as a sentence saying the
change was not saved.** It is never silent — before 0.8.0 a failure to send *was* silent, which is
how the whole up direction stayed broken for a version with nobody noticing.

An edit made while the connection is **down** is a different thing and is not a refusal: it is kept
and sent when the connection comes back. See [Reconnection](#reconnection).

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

The rule is: **a client may edit exactly the models you marked, wherever they appear.**
Position in the graph does not grant permission, so an unmarked model stays unmarked even when it sits inside a marked one.

If a refusal appears where an edit used to work, decide which of the two you meant: add `@ClientWritable` to the inner model, or stop sending it up by leaving it out of the client's copy.

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

For writes that are *operations* rather than edits — "checkout", "approve", "close ticket" — keep using RMI service methods: an operation deserves a name, its own `@Secured` and `@RolesAllowed` marks, and a validation point. The doctrine: **state edits sync, operations call.**

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

The lock service is open to every connection, signed in or not, and it has to stay that way: applications with no login use it too.
So this record is what decides who may lock what.

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

## Waiting, sending, and what it costs

An edit made on the client waits a moment before it travels, so that a burst of typing becomes one
message instead of dozens. Two numbers decide when it goes.

| What it is | Default | What it does |
|---|---|---|
| the pause | 150 ms | how long the changes have to stop before they are sent |
| the ceiling | 1000 ms | the longest anything waits, even while the changes keep coming |

`LiveMutations.configure(pauseMillis, ceilingMillis)`, called before `Zeroz4jClient.connect`,
changes them. A pause of `0` turns the waiting off and sends every setter call at once, which is
what the framework did before 0.8.0.

**The ceiling is the important one.** Without it, somebody typing steadily never pauses, so nothing
is ever sent, and a dropped connection or a closed tab takes the whole paragraph. With it, a person
who types without stopping still has their work sent about once a second, and never has more than
about a second of typing that the server has not heard about.

### Nothing you do next can arrive first

A person types into a field and immediately presses a button that calls a service. The typing is
still waiting; the button's call would reach the server first, the server would decide on the value
the person has already replaced, and the screen would look as though the typing was ignored.

That cannot happen: **every outgoing call sends the waiting edits first.** RMI service calls,
`LiveMutex` locks and shared-signal writes all go through it. You write nothing.

One caveat, and it is the server's, not the client's: the server may handle several messages from
one connection at the same time (up to `zeroz.ws.maxConcurrentFramesPerSession`, 32 by default), so
sending in the right order is not the same as being *handled* in that order. In practice a live
edit is applied to the server's own object as the very first thing its message does, and a service
call takes longer to reach the point where it reads that object - but if a service method's
correctness genuinely depends on an edit that was made a fraction of a second earlier, take a
`LiveMutex` around the pair rather than relying on the timing.

### Leaving the page loses what was still waiting

Somebody who closes the tab or follows a link mid-burst loses whatever had not been sent - at most
the ceiling's worth, about a second of typing. **There is deliberately no rescue for this.** A
handler on the browser's page-leaving events was built and measured: it runs, but whether the
browser gets the bytes out of the WebSocket before it takes the page apart is the browser's
decision, and on the same machine on the same day it went both ways for both a closed tab and a
followed link. The one mechanism browsers do guarantee at unload speaks HTTP and cannot write to a
WebSocket. Something that works half the time is worse than nothing here, because an application
would come to rely on it. Lower the ceiling for a screen where even a second matters.

### Do not write the server's value back into a box somebody is typing in

This is the one thing an application has to get right itself, and it became easy to get wrong when
edits started waiting. The server broadcasts every accepted edit back, including to the person who
made it - and what comes back is what the server had a moment ago, not what is in the box now. An
`Effect` that copies the incoming value into the field will delete whatever was typed since.

So follow the incoming value everywhere *except* the field that has the keyboard in it:

```java
boolean[] beingTypedIn = {false};
field.addDomEventListener("focus", e -> beingTypedIn[0] = true);
field.addDomEventListener("blur", e -> beingTypedIn[0] = false);

Effect.create(() -> {
    String current = profile.getMission();
    label.setText(current);                                   // always
    if (!beingTypedIn[0] && !current.equals(field.getValue())) {
        field.setValue(current);                              // only when nobody is typing
    }
});
```

Before 0.8.0 this mistake was nearly invisible, because the value came back after every single
character and therefore almost always matched. Now it comes back up to a second late, and the
mistake eats words. The `chat-livesync` example shows the pattern above.

## Rules and limits (stated plainly)

* **A burst of edits is one message, not one per keystroke (0.8.0+).** A change is not sent the
  instant a setter returns. It waits for a short pause - **150 ms** by default - and everything
  changed during that burst goes in one message. Typing a short sentence into a field bound straight
  to a live object used to send one whole-object message per character; it now sends a handful.
  Measured on the `chat-livesync` example, in a real browser, counting on the server: **38
  characters typed at ordinary speed sent 38 messages before this change and 4 after it.**
  See [Waiting, sending, and what it costs](#waiting-sending-and-what-it-costs).
* **Setters are the tracking boundary.** Mutations must go through setters. In-place collection edits (`obj.getTags().add(...)`) are invisible — reassign via the setter or call `LiveMutationTracker.touch(obj)` afterward. Tracked collections are planned.
* **Whole-object, last-write-wins.** Mutations replace the object's state; two unlocked concurrent editors race and the later write wins. Serialize editors with `LiveMutex` (see the collab-editor example pattern) where that matters. Field-level merging and version-conflict rejection (`MUTATE`/`ACK`/`REJECT` versions) are reserved in the protocol but not yet implemented.
* **Re-rendering is automatic.** A `@LiveSync` object is a reactive dependency: read one of its getters inside an `Effect` or `Computed` and an inbound sync re-runs it. Notification is per object, not per field.
* Only objects the server has previously synced to the client can be mutated (the canonical instance must exist in the server's object mapper).
* **Every object a change reaches is checked, not just the outermost one.** A model nested inside a `@ClientWritable` model needs its own `@ClientWritable` before a client can edit it, and one refusal refuses the whole change.
* **Being sent an object is what earns the right to read it back.** The server remembers what it sent to which browser, and answers a re-read only from that record.
* **Being sent an object is also what earns the right to lock it.** Same record, same rule. A lock request for an object this browser was never sent is refused immediately.
* **Only a `@LiveSync` model and the objects inside one have a name.** Everything else on the wire is a value: it cannot be synced, mutated, locked or re-read, because there is nothing to name it by. This is what stops a screen that redraws itself from filling memory with objects nobody will ever ask for again.
* **A name lasts as long as the object does, and no longer.** Neither tier keeps an object alive just because it once put it on the wire.
* **Every model a client change reaches must be `@ClientWritable`, whether or not it came with a name.** A model the client invented on the spot is checked exactly like one it named, so an unmarked model cannot be smuggled into a marked one. A `record` is exempt: it has no setters and never changes, so it travels as a value.

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

### The server keeps a name only while it still holds the object (0.8.0+)

The handle registry no longer keeps objects alive. A live object's name lasts exactly as long as the
application's own reference to it, on both sides.

* **On the server**, an object your code has dropped is collected, and its name goes with it. A
  re-sync naming it gets the same answer as after a restart: nothing comes back, the count is logged,
  and the client re-fetches. This is not a new thing to do — keep live objects in your store, a root
  object or a field, which is where they already are in any real application. An object built for one
  call and never kept was never re-syncable in any meaningful sense.
* **In the browser**, an object nothing on the screen refers to any more is collected too, and it is
  not asked for after a reconnect. Before 0.8.0 the browser asked for everything it had ever been
  sent, which is what made a long-lived tab unable to reconnect at all.

Nothing an application can see changes while it holds its objects normally. What changes is the
answer to "what happens to the ones it dropped": they used to stay for ever, and now they go.

### A tab that has collected too much heals itself (0.8.0+)

A re-sync request carries at most 10,000 handles. Past that the browser throws the list away instead
of sending it and writes one line to the console saying that it did and why. Everything on the screen
is re-fetched by the application the way it first obtained it, and the connection works again.

This exists because the alternative is a tab that can never connect. An over-sized request is refused
by the server, which closes the connection; the client reconnects and sends the identical request;
the list never gets shorter. If that line ever appears, a screen is holding references to objects it
stopped showing long ago — the log line is the place to start looking.

### What re-sync will and will not send back

Every object travels with a name — a **handle** — and the client asks for objects back by naming them.
A name is not a permission.
The server keeps a record of the objects it has actually sent to each browser, and re-sync answers only for objects in that record.

Three things follow, and only the third is likely to surprise anyone:

* **A reconnect works as before.** The record is kept per browser, not per connection, and the browser id survives a drop and a page reload. A new connection from the same browser restores everything that browser holds.
* **A name learned some other way is worth nothing.** An object nested in a broadcast event or a shared signal goes out with its own name attached, so everybody who received the outer payload also learned the names of the parts inside it. Knowing a name is not the same as having been sent that object, and only the second one counts.
* **A client with no cookie re-fetches instead.** A non-browser client that carries no browser id is remembered only for the life of one connection, so after a reconnect its objects come back the way it first obtained them. The server log says so once at startup.

The record is bounded: at most 10,000 objects per browser, and a browser's whole record is dropped after 24 hours of inactivity (`zeroz.disclosure.maxHandlesPerClient` and `zeroz.disclosure.idleHours`).
A dropped record behaves exactly like a server restart — the client is told nothing was found and re-fetches.

Since 0.8.0 far less goes into that record, because far less carries a name at all: only a live object
and the objects inside it. An ordinary value returned from a service method is not named, cannot be
asked for again, and does not use up a slot.

## Choosing a propagation feature

| | Shape | Client writes |
|---|---|---|
| **Shared signal** | one value, latest-wins, retained | `sharedWritable` opt-in |
| **Server event** | discrete occurrence, no replay | n/a (events come from the server) |
| **LiveSync** | object graph, in-place | `@ClientWritable` opt-in |
