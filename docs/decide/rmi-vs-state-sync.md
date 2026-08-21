# RMI or state sync?

Once you can sync state automatically, it is tempting to sync everything and delete your service
layer. Resist it. The rule is:

> **State edits sync, operations call.**

## What belongs in an RMI method

Anything you would describe with a verb: *approve*, *check out*, *cancel*, *delete*, *log in*,
*submit*, *retry*. These share four properties that a field edit cannot express:

1. **A name.** `approve(invoiceId)` says what happened. `invoice.setApproved(true)` says a field
   changed and leaves intent to the reader.
2. **A place to decide who may do it.** `@Secured` and `@RolesAllowed` attach to methods. A field
   flip is governed only by the coarse `@ClientWritable("role")` on the whole class.

    Put these annotations on the **`@RmiService` interface**, not on the implementation. The dispatcher
    scans the interface and its declared methods only — an annotation on the bean class is never read,
    so a method you believed was protected is not.
3. **A validation point.** A method can reject the *request* on business grounds — wrong state, wrong
   time, insufficient funds — not just on the shape of a value.
4. **An answer.** The caller learns whether it worked. A rejected LiveSync mutation only snaps the
   client's optimistic change back; the reason is logged on the server and never sent.

```java
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.RolesAllowed;

@RmiService
public interface InvoiceService {
    @RolesAllowed("approver")              // on the INTERFACE method — the impl is not scanned
    void approve(String invoiceId);

    Invoice get(String invoiceId);
}
```

Note the import: these are `com.zeroz4j.api.RolesAllowed` and `com.zeroz4j.api.Secured`, not the
Jakarta annotations of the same name.

## What belongs in sync

Edits to a value, where the field *is* the meaning: a document's body, a profile's mission
statement, a quantity, a title. Nobody needs `setMission` to have its own audit entry, its own
failure mode, or its own name.

```java
// client — the whole write path for an editable field
profile.setMission("Ship it");
```

## Use both

The two are not rivals; the common shape uses each for what it is good at. **Call to act, sync to
observe:**

```java
jobService.startJob();                    // RMI — a named operation, with a result
// ... progress arrives on a shared signal, to every interested client
```

`job-monitor` is exactly this. So is any CRUD screen: `save()` and `delete()` are RMI methods, while
the list everyone is looking at updates through sync.

## The pull that goes the other way

There is also a first-call obligation in the other direction: `syncEngine.notifyChanged(obj)` does
nothing until the object has an `ObjectMapper` handle, and it gets one by being serialized to a
client. So a LiveSync feature almost always *starts* with an RMI getter:

```java
liveState = chatService.getState();   // RMI — establishes the handle
// from here, syncEngine.notifyChanged(state) on the server reaches this client
```

## Summary

| | RMI method | State sync |
|---|---|---|
| Expresses | an operation | an edit |
| Has a name | yes | no |
| Per-method `@Secured` / `@RolesAllowed` | yes | no — per class |
| Can reject with a reason | yes | rejects silently |
| Returns a result | yes | no |
| Initiated by | the client | either tier |
| Reaches other clients | no | yes |

See also: [the full decision procedure](index.md), and
[Operation-as-edit](antipatterns.md#operation-as-edit).
