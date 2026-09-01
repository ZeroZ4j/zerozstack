# payments-datamodels — a payments desk

A small till. Put things in a basket, say how the customer paid, take the payment, give money back.
Everything on the screen has crossed the wire in one direction or the other, and the three shapes a
type can take on that wire are all in it because the domain wanted them, not because the example
needed something to show.

Runs on **port 8092**. No sign-in.

```bash
mvn clean install -DskipTests          # once, from the repository root
cd zerozstack-examples/payments-datamodels
run.bat                                # or run.bat 9000 for a different port
```

Then open <http://localhost:8092>. On anything that is not Windows:

```bash
cd zerozstack-examples/payments-datamodels/payments-datamodels-server
java -cp "target/classes:target/libs/*" com.zeroz4j.example.payments.server.ExampleServer --port 8092
```

## The three shapes, and why each one belongs here

### A record, for a value that never changes

`Money` is an amount and a currency. `LineItem` is a description, a count and a price. Neither
changes once it exists — correcting a basket line means taking it off and putting a different one
on, which is what the screen does. A record is one line instead of ten, and the compiler writes the
`equals` that makes two identical amounts actually equal, which matters because these end up inside
other values and inside a map.

`DailySummary` is a record too: it is worked out fresh every time it is asked for.

### A sealed interface, for a value that is one of a known set

`PaymentMethod` is a card payment, or cash, or a bank transfer, or a gift card. Each carries
different details: a card has a scheme and four digits, cash has the note handed over and therefore
change, a transfer has a reference, a gift card has its number and what is left on it. There is no
field they share.

The version this replaces is a class with a `kind` string and every field any kind might need, most
of them empty. Nothing checks that a row saying `"CARD"` has its digits filled in, and the day
somebody adds a fifth kind every place that switches on that string keeps compiling and starts being
wrong.

Here the set is written down and the compiler holds it. The screen asks
`method instanceof CardPayment card` and gets the real thing.

### A base class, for what two types genuinely share

`Payment` and `Refund` both need an identifier, the moment it happened, the amount, and whatever the
person at the till wrote on it. Those four fields live once, on `LedgerEntry`, instead of twice.

`LedgerEntry` is *also* sealed — a ledger entry is one of exactly two things and there will never be
a third — which is a separate decision from being a base class and is worth keeping separate in your
head. Being sealed is what lets `List<LedgerEntry>` come back from the server with each row arriving
as the kind it really is.

**Before 0.8.0 this refactor quietly broke an application.** The generated code looked only at the
fields a class declared itself, so everything on the base class stopped being sent — no compile
error, no wire error, just missing data. If you avoided a base class because of that, you no longer
have to.

## Where it presses on the wire

The interesting part is not that these types compile. It is that they survive the journey, nested
inside each other and inside collections, in both directions.

| Call | What travels up | What comes back |
|---|---|---|
| `quote(lines)` | records inside a list | a record |
| `take(payment)` | a class extending an abstract base, carrying four inherited fields, a list of records, and a sealed value that may itself hold a record | the same object, finished |
| `refund(id, note)` | plain text | one of the two kinds, with the base class's fields on it |
| `ledger()` | nothing | a list whose declared element type is an abstract sealed base, each row the real kind |
| `summary()` | nothing | a record holding a map whose values are records |

**Two places say what actually arrived.** The server logs every call field by field, naming the real
kind of every sealed value it received, so the console shows what came off the wire. And the screen
prints a short list under the payment button — "came back as `Payment`, a class that extends
`LedgerEntry`"; "from the base class: id, time, note"; "paid by: gift card". Both are written so
that a failure would be visible rather than theoretical: the screen asks each value what it is,
rather than reading a flag it put there itself.

## Nothing here is saved to disk, on purpose

Every other example that stores anything puts it in ZeroZ DB. This one keeps the ledger in a list
for as long as the server is running, and the reason is worth knowing before you copy the pattern:
**a record is a wire shape, not a storage shape.** The persistence layer reaches into an object's
fields directly, and the Java runtime refuses that for a record unless the whole application is
started with an extra command-line flag. A record also cannot be a persistence root.

So records travel, and classes are what you save. Mixing that up produces a failure at the first
write that looks nothing like its cause.

## The test is the other half of the example

`PaymentsWireRoundTripTest` does not call the service. It starts a `TestServer` — a whole server in
this process, in about a tenth of a second — builds the bytes a browser would send, puts them on a
connection, and decodes the answer with the same serializer the browser uses.

That distinction is the whole value of it. Calling `new PaymentsServiceImpl().take(payment)` would
prove the arithmetic and nothing else: no serializer would run, so a record that could not be
written and a base class whose fields were dropped would both pass. The code being checked here is
the code the annotation processor generated *for these models*, which is written fresh for every
project — so every project wants a test of this shape.

See [Testing an application](../../docs/guides/testing.md).

## What to read next

- [Declaring the types that cross the wire](../../docs/guides/data-models.md) — the rules behind
  every choice on this page.
- [The wire protocol](../../docs/PROTOCOL.md) — the bytes themselves.
- [Limitations](../../docs/reference/limitations.md#serialization) — which field types are allowed.
