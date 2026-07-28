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

## Rules and limits (stated plainly)

* **Setters are the tracking boundary.** Mutations must go through setters. In-place collection edits (`obj.getTags().add(...)`) are invisible — reassign via the setter or call `LiveMutationTracker.touch(obj)` afterward. Tracked collections are planned.
* **Whole-object, last-write-wins.** Mutations replace the object's state; two unlocked concurrent editors race and the later write wins. Serialize editors with `LiveMutex` (see the collab-editor example pattern) where that matters. Field-level merging and version-conflict rejection (`MUTATE`/`ACK`/`REJECT` versions) are reserved in the protocol but not yet implemented.
* **Re-rendering is automatic.** A `@LiveSync` object is a reactive dependency: read one of its getters inside an `Effect` or `Computed` and an inbound sync re-runs it. Notification is per object, not per field.
* Only objects the server has previously synced to the client can be mutated (the canonical instance must exist in the server's object mapper).

## Choosing a propagation feature

| | Shape | Client writes |
|---|---|---|
| **Shared signal** | one value, latest-wins, retained | `sharedWritable` opt-in |
| **Server event** | discrete occurrence, no replay | n/a (events come from the server) |
| **LiveSync** | object graph, in-place | `@ClientWritable` opt-in |
