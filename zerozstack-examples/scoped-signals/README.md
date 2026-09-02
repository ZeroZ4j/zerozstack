# Scoped signals

Three signals side by side, one per kind of reach: everyone, one browser, one person. Reading them is
identical in all three cases — an `Effect` over a signal. Only the declaration differs.

## Run it

```bash
mvn -pl zerozstack-examples/scoped-signals/scoped-signals-server -am install
java -jar zerozstack-examples/scoped-signals/scoped-signals-server/target/scoped-signals-server-0.9.0.jar --dev-login
```

`--dev-login` switches on the framework's built-in development accounts, which the per-user signal
needs an identity from. Without the flag nothing can sign in, and the server says so when it starts.

Open <http://localhost:8082/?user=demo&password=demo>.

## The three declarations

```java
// everyone
public static final ValueSignal<Integer> VISITORS = Signals.shared("shop.visitors", 0);

// one browser — no login needed
public static final ScopedSignal<Basket> BASKET =
        Signals.scoped("shop.basket", Basket.empty(), Scope.CLIENT);

// one person, across their devices
public static final ScopedSignal<String> NOTICE =
        Signals.scoped("shop.notice", "", Scope.USER);
```

The server names the target from the connection's own identity, never from a method argument:

```java
ShopSignals.BASKET.forTarget(RmiRequestContext.getClientId()).update(b -> b.plus(item));
```

The client just reads its own:

```java
Effect.create(() -> label.setText("Basket: " + ShopSignals.BASKET.mine().get().getItems()));
```

## The demonstration

Open the page **twice in the same browser** with different users:

- tab 1: <http://localhost:8082/?user=demo&password=demo>
- tab 2: <http://localhost:8082/?user=admin&password=admin>

Then:

| Do this | What happens | Why |
|---|---|---|
| Add an item in either tab | Both tabs' baskets update | `Scope.CLIENT` keys on the browser, and both tabs share one client id |
| Notify yourself in tab 1 | Only tab 1's notice changes | `Scope.USER` keys on the person, and the tabs are signed in as different users |
| Count a visitor anywhere | Both tabs' counters move | `Signals.shared` is one value for everyone |

Both tabs print their identity at the top: the same `client=…` and different `session=…`.

To see per-browser isolation, open the page in a **different** browser or a private window — it gets
its own client id and therefore its own basket.

## The client id

Issued by the server, signed, and stored in an `HttpOnly` cookie. Confirm the last part yourself:

```js
document.cookie          // empty — page script cannot read it
```

That is deliberate. A cross-site scripting bug can read `localStorage`; it cannot read this cookie.
See [client identity](../../docs/guides/security-auth.md#client-identity-without-a-login).

**It identifies a browser, not a person.** Two people sharing a machine share a basket. That is fine
for a basket and wrong for anything that must be private to one human — which is what `Scope.USER`
and `Scope.TENANT` are for, and why they need real authentication.

## Note on green threads

Every RMI call here runs inside `new Thread(...).start()`. Click handlers and framework callbacks run
on stacks that began in native JavaScript, where TeaVM cannot suspend a coroutine — and an RMI call
is a suspension. See the client-environment notes in
[limitations](../../docs/reference/limitations.md).
