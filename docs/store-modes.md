# Store modes: embedded or server-hosted

ZeroZ4J persists application data with [ZeroZ DB](https://github.com/ZeroZ4j/zerozstack-db), which
adds transactions, indexes, constraints and an optional network server on top of EclipseStore.

**Where the data lives is a deployment decision, not an application one.** Services are written
once and run unchanged whether this process owns its data or talks to a database server.

## Choosing a mode

```properties
zeroz4j.store.mode = EMBEDDED        # default
zeroz4j.store.path = ./data
zeroz4j.store.durability = SYNC      # or OS_BUFFERED
```

| mode | who owns the data | when to use it |
|---|---|---|
| `EMBEDDED` | this process, exclusively; no socket | single instance, development, per-tenant data nothing else touches. One copy of the graph in memory. |
| `AUTO_SERVER` | this process if the store is free, otherwise whoever already owns it | several instances sharing stores over a common filesystem, with automatic takeover if the owner dies |
| `CLIENT` | a separate `zeroz4j-db` server | stateless application instances that can be restarted and scaled without moving data |

`CLIENT` needs an address:

```properties
zeroz4j.store.mode = CLIENT
zeroz4j.store.server.host = db.internal
zeroz4j.store.server.port = 5150
zeroz4j.store.server.secret = ${DB_SECRET}
zeroz4j.store.schemaId = myapp-v3
```

## Writing services that survive a mode change

Inject `ZeroZDbNode` and express work as commands and queries. They execute wherever the data
is — in this process when embedded, on the server when not — so the same code runs in every mode.

```java
@ApplicationScoped
public class ProductService {

    @Inject
    ZeroZDbNode db;

    public long add(String name) {
        return db.execute(new AddProduct(name));
    }

    public int count() {
        return db.query(new CountProducts());
    }
}
```

```java
public class AddProduct implements DbCommand<Long> {
    public String name;

    public AddProduct() { }                       // needed for deserialisation
    public AddProduct(String name) { this.name = name; }

    @Override
    public Long execute(WriteContext ctx, Object root) {
        Catalog catalog = (Catalog) root;
        ctx.edit(catalog);
        ctx.edit(catalog.products);
        long id = catalog.nextId++;               // id allocation and insert
        catalog.products.put("P-" + id, name);    // land in ONE commit
        return id;
    }
}
```

Everything the command changes is enlisted and flushed atomically: a crash cannot persist the new
product without the counter that named it, which is the class of bug the old
"store the list, then store the root" shape allowed.

**Use plain classes for commands and queries, not records.** EclipseStore's serializer reaches
fields directly and the JVM refuses that for records unless started with
`--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`. The failure appears at the first remote
call, not at compile time.

### Fast reads

```java
try (var local = db.<Catalog>localReads()) {
    int count = local.read(c -> c.products.size());   // heap access, no round trip
}
```

The live graph when this process owns the store; a continuously refreshed replica when it does
not, trailing the server by about one round trip. Use `db.query(...)` when a read must be exactly
current.

### Direct engine access

`db.localDb()` returns the engine itself — lambda write-blocks, index registration,
`CrossStoreWrite` — and is **only present when this process owns the data**. Using it pins the
application to `EMBEDDED` or `AUTO_SERVER`, so keep it out of code you might later run as a
client.

Injecting `EmbeddedStorageManager` still works and is unchanged for existing applications, with
the same caveat: it exists only where the data is local, and in `CLIENT` mode it fails with an
explanation rather than silently doing something else.

## Running the server

```bash
java -cp zeroz4j-db.jar:my-app.jar org.zeroz4j.db.server.ZeroZDbServerMain server.properties
```

```properties
port = 5150
bindAddress = 0.0.0.0
secret = ${DB_SECRET}
schemaId = myapp-v3
durability = SYNC
store.acme.dir  = /data/acme
store.acme.root = com.example.Catalog
console.port = 8090
console.password = ${CONSOLE_PASSWORD}
```

The server loads your application classes: it executes your commands and maintains your indexes,
so it is deployed with the same jar and versioned with it. Binding to anything but loopback
requires a secret — the server refuses to start otherwise.

## What each mode costs

- `EMBEDDED` — nothing beyond EclipseStore. One writer per store; stores are independent.
- `AUTO_SERVER` — a socket, and clients hold their own replica of what they read.
- `CLIENT` — one round trip per command or query (~370 µs locally), or ~130 ns per read through
  `localReads()`, at the price of a replica in memory and staleness of about one refresh. Plus a
  server to run and monitor.

Schema changes are checked the same way in every mode: see the ZeroZ DB guide on
`SchemaCompatibility` for gating a build on rollback compatibility, and set
`zeroz4j.store.schemaId` from your **shared model** version rather than the application version,
so an application release that does not change the model cannot lock itself out of the server.
