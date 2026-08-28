# Declaring the types that cross the wire

This page is for someone writing the shared module of a ZeroZ Stack application, deciding what shape
to give the types that travel between the browser and the server. By the end you will know when to
write a class, when to write a record, and how to say "this value is one of a known set".

## When to use this

Read this before you write your first `@DataModel`, and again when a model starts to feel awkward —
when several models are copying the same three fields, or when you find yourself adding a `type`
field and casting on the other side. Both of those have a proper answer now.

For the bytes themselves, and the reasons behind every rule below, see the
[wire protocol reference](../PROTOCOL.md). For which field *types* are allowed, see
[Limitations](../reference/limitations.md#serialization).

## Everything that travels is annotated

Any type sent as an RMI argument, returned from an RMI method, carried by an event or held in a
signal is annotated `@DataModel`, in the shared module. The annotation processor generates the code
that writes it and reads it back. A type that is not annotated is refused when you compile, not at
run time.

```java
import com.zeroz4j.api.DataModel;

@DataModel
public record Money(long amount, String currency) { }
```

## Three shapes, and how to choose

| You have | Write | Because |
|---|---|---|
| A value that never changes once it is made — an amount, a coordinate, a search filter | a **record** | one line instead of ten, and equality comes free |
| Something a browser edits in place, or that the server pushes changes into | a **class** | `@LiveSync` and `@ClientWritable` need setters |
| A value that is one of a known set — a chat message that is text, or a ping, or a file | a **sealed interface** with a record or a final class per member | the compiler checks the set, and so does the receiver |

The rest of this page takes them in that order.

### A record

A record needs nothing but its components:

```java
@DataModel
public record Money(long amount, String currency) { }
```

A record can carry everything a class can carry: text, numbers, dates, lists, sets, maps, other
records and ordinary classes. Validation annotations work exactly the same way. Records work in the
browser too — the browser compiler turns a record into an ordinary class, including the `equals` and
`hashCode` the Java compiler writes for you, so a record is safe as a map key or a set element on
both sides.

**Two things a record cannot do.**

- It cannot be marked `@LiveSync` or `@ClientWritable`. Both of those are about editing an object
  after it exists, and a record never changes. Use a class.
- It cannot be part of a loop. If `A` holds a `B` and that `B` holds the same `A`, one of the two has
  to be a class. Sending such a loop is refused with a message naming the record, rather than failing
  somewhere far away. The receiver has to read every component of a record before it can build it, so
  there is nothing yet for the loop to point back at; a class is built empty and filled in
  afterwards, which is why a class can close the loop.

There is also a persistence rule, unrelated to the wire: a record cannot be a persistence root. Keep
the objects you save as classes.

### A class

Write a class when a browser edits the object in place, or when the server pushes changes into it.
A class needs a public no-argument constructor, and a getter and a setter for every field that
travels:

```java
import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.LiveSync;

@DataModel
@LiveSync
@ClientWritable("sales")        // and only a session holding the "sales" role may edit it
public class Order {
    private String id;
    private String status;

    public Order() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

`@LiveSync` and `@ClientWritable` go on the class, not on a field. Setters are the boundary the
framework watches: an in-place edit such as `order.getTags().add(x)` is invisible, because no setter
was called. See [LiveSync](../LIVESYNC.md).

### A sealed family

When a value is one of a known set, say so. The old way was a `kind` field and a cast on the other
side, which the compiler cannot check and which goes quietly wrong the day somebody adds a fourth
kind.

```java
@DataModel
public sealed interface Message permits Text, Ping, Attachment { }

@DataModel public record Text(String author, String body) implements Message { }
@DataModel public record Ping(long sentAt) implements Message { }
@DataModel public final class Attachment implements Message { /* ... */ }
```

A field, a list element, a call argument or a return value may now be declared as `Message`. What
arrives on the other side is a `Text`, a `Ping` or an `Attachment` — the real type, with no cast and
no kind field. A sealed abstract class works the same way, and any field the base declares travels
with each member.

It is safer than the version it replaces. The list of permitted types is fixed when the code is
compiled, so the receiving side knows it; a message naming anything else is turned away before that
type is created at all, and the refusal says which type was named and which ones were allowed.

Five rules are checked when you compile, and each one has a reason:

- every permitted type is itself `@DataModel` — otherwise the receiver could not build it;
- every permitted type is `final`, which a record already is — a `non-sealed` member could be
  extended from outside the set;
- a permitted type is not itself sealed — the receiver checks against one list, not a tree of lists;
- a sealed class used as the base is `abstract` — a base that is also a value of its own would be
  neither in nor out of its set;
- a plain, unsealed interface is refused — there is no list, so any class at all could be named.

## Sharing fields with a base class

A model may extend another model, and the base class's fields travel with it. This is the ordinary
Java refactor of moving what several models share up one level, and it now works.

```java
@DataModel
public abstract class Document {
    private String id;
    private Instant createdAt;
    // getters and setters
}

@DataModel
public class Invoice extends Document { /* its own fields */ }
```

An abstract model is never a value in its own right: it has no code generated to build one, because
nothing can. It exists to hand its fields down. Declaring a field as an abstract model type still
works.

!!! warning "Before 0.8.0 this lost data in silence"
    The generated code looked only at the fields a class declared itself, so everything on the base
    stopped arriving — no compile error, no wire error, just missing data, and nothing pointing at
    inheritance as the cause. If you avoided a base class because of this, you no longer have to.

Two shapes are refused when you compile, both because they used to lose data quietly:

- **Extending a class that is not a `@DataModel` and that has fields of its own.** There is no way to
  know those fields belong on the wire. Annotate the base, or move the fields down. A base class with
  no fields is fine as it is.
- **Declaring a field with a name a base class already uses.** Both would be written and both read
  back through the same accessor, so one would overwrite the other.

## Limits

- **A record cannot be `@LiveSync`, `@ClientWritable`, or part of a reference loop.** Use a class.
- **A record cannot be a persistence root.**
- **Two objects that are the same object do not always arrive as the same object.** The same object
  in two fields of one model arrives once; the same object as two separate items of a top-level list
  arrives twice. Do not use `==` to ask whether two things that came off the wire are the same one —
  compare by identifier, or with `equals`.
- **A sealed value costs one extra class name per value** on the wire, on top of the one an object
  already carries.
- **Field types are restricted.** Object arrays, `ZonedDateTime`, `OffsetDateTime`, `ZoneId`,
  `Period` and `java.util.Date` are refused when you compile, and the message names the replacement.
  Declare collection fields as `List`, `Set` or `Map`, never as `TreeSet`, `LinkedList` or `TreeMap`.
  The full list is in [Limitations](../reference/limitations.md#serialization).
- **Server-side validation of RMI arguments recurses into `List` elements but not into `Map` values
  or nested object fields.** See [Validation](../VALIDATION.md).
