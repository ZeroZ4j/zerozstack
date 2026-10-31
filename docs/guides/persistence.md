# Saving data

How to persist your object graph, and the one rule that prevents the most common data-loss bug.

## When to use this

Read the rule below before you write your first service method. It is easy to get wrong and the
failure only shows up after a crash.

## The basics

Your objects *are* the database. There is no SQL, no mapping and no schema. You change an object in
memory and then tell the storage manager to write it.

```java
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    @Inject
    private EmbeddedStorageManager storage;

    private DataRoot root() {
        return (DataRoot) storage.root();
    }
}
```

Saving is explicit. The framework never writes for you.

```java
product.setQuantity(12);
storage.store(product);
```

## The rule: one call, one save

**Each call to `store()` is a separate save. If you need two things saved together, use one call.**

```java
// WRONG - two saves. A crash between them saves the product and loses the id counter,
// so the next save hands out an id that is already in use.
root.getProducts().add(product);
root.setNextId(nextId + 1);
storage.store(root.getProducts());
storage.store(root);

// RIGHT - one save. Either both are written or neither is.
root.getProducts().add(product);
root.setNextId(nextId + 1);
storage.storeAll(root.getProducts(), root);
```

`storeAll` takes any number of objects and writes them in a single commit. Use it whenever a change
touches more than one object.

### Transactions

Since 0.4.0 the store runs on [ZeroZ DB](https://github.com/zeroz4j/zerozdb), so grouping writes is
no longer all you get. Inject `ZeroZDbNode` and send a `DbCommand`: everything it enlists lands in
one atomic commit, and if it throws, nothing is persisted and the objects are restored in memory.

```java
public class AddProduct implements DbCommand<Long> {
    public String name;

    public AddProduct() { }
    public AddProduct(String name) { this.name = name; }

    @Override
    public Long execute(WriteContext ctx, Object root) {
        Catalog catalog = (Catalog) root;
        ctx.edit(catalog);
        ctx.edit(catalog.products);
        long id = catalog.nextId++;             // id allocation and insert
        catalog.products.put("P-" + id, name);  // in ONE commit
        return id;
    }
}
```

The same command runs wherever the data is — in this process when embedded, on the server when not
— so the choice between the two is configuration, not code. See
[Store modes](../store-modes.md).

### If your application is multi-tenant

`@Inject ZeroZDbNode` resolves the tenant **once, when the injecting bean is created** — the
producer is `@Dependent`, which it has to be, because `ZeroZDbNode` is `final` and a normal CDI
scope would need to proxy it.

For a single-tenant application, which is the default, that is exactly right and there is nothing
to think about. If you publish a `TenantResolver`, it is not: an `@ApplicationScoped` service is
created once, so whichever tenant triggered its creation would own every write after it. Inject the
provider and resolve per operation instead:

```java
@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    @Inject TenantStorageProvider storage;
    @Inject TenantResolver tenants;

    public long add(String name) {
        return storage.getNode(tenants.resolveTenant()).execute(new AddProduct(name));
    }
}
```

The framework logs a warning when it sees an application-scoped bean injecting the node directly
while a `TenantResolver` is present. It cannot be an error — the pattern is correct for everyone
else.

## What to save after changing a collection

Changing the contents of a collection does not change the object that holds it. Save the collection.

```java
root.getProducts().add(product);
storage.store(root.getProducts());     // the list changed, so save the list
```

Save the owner as well only if you also changed one of its own fields, and then save both in one call
as above.

## Reading

Reading is plain Java over objects already in memory. There is no query language.

```java
return root().getProducts().stream()
        .filter(p -> p.getCategory().equals(category))
        .toList();
```

That means reads are fast and that large collections are scanned linearly. Keep your own index on the
root if you need lookup by key.

## Deferring part of the graph

A field typed `Lazy<T>` is not loaded until something calls `get()` on it, and it is not sent to the
browser until a client asks for it.

```java
@DataModel
public class Order {
    private String id;
    private Lazy<List<OrderLine>> lines;
}
```

See [Limitations](../reference/limitations.md#lazy-references) for the rules.

## Multiple tenants

Each tenant gets its own store on disk. Implement `TenantResolver` to say which tenant a request
belongs to, and `DataRootProvider` to create an empty root for a new one. The injected storage manager
is then already the right tenant's.

## Saving after a client edit

A `@ClientWritable` edit arriving from a browser is applied to the server's object and broadcast to
other clients, but **not saved**. Persisting it is your job:

```java
@ApplicationScoped
public class ProductPersistence implements LiveMutationListener {

    @Inject EmbeddedStorageManager storage;

    @Override
    public void onMutated(Object model, Principal principal) {
        storage.store(model);
    }
}
```

Without a listener like this, client edits are lost on restart.

## Observed behavior

These came from applications running on this release. Each is marked with how it was checked:
**confirmed** means it was read in the source or reproduced, and **reported** means an application
saw it and it was not checked here.

- **Deleted data stays on disk (confirmed).** EclipseStore rewrites a storage file to remove dead
  data only when less than 75% of it is live data (the default `defaultMinimumUseRatio` of
  EclipseStore 4.1.0, which this release uses). It also rewrites files that are too small or too
  large, which does not depend on dead data. That work runs in the store's own threads while the store is open, in small steps. A
  store that is closed does none of it, and ZeroZ DB has no compaction method. Removing an object from
  the graph therefore does not remove its bytes from the file, which matters when a person's data has
  to be erased for good (GDPR). ZeroZ Stack has no way to force the rewrite today.
- **A version field in the root cannot tell you the store is old (confirmed).** The root object your
  build supplies is the instance the stored root is loaded into. A field the old store never had keeps
  the value your constructor gave it. Reproduced: a store written by a root class with one field,
  opened by a build whose root also has `int version = 2`, reads `version` as 2. To catch an
  incompatible change before it ships, use `SchemaCompatibility` in the build.
- **Every stored object costs memory while the store is open, even behind `Lazy` (confirmed from
  the source, not measured).** EclipseStore keeps one `StorageEntity` entry per stored object. Its
  fields add up to about 64 bytes with compressed object references, before the object itself is
  counted. `Lazy` keeps the object out of the graph; it does not remove the entry. Plan the heap for
  the number of objects, not only their size.
- **`ZeroZDbNode` cannot declare a field rename (confirmed).** Its builder has no setting for a
  `SchemaEvolution`, and by default a field whose name changed arrives unset. `ZeroZDb.open(root,
  directory, durability, evolution)` takes one, so a rename needs the engine directly, which pins the
  code to `EMBEDDED` or `AUTO_SERVER`.
- **Enums are stored by position (confirmed), and `SchemaCompatibility` in this release does not
  check enum constants (confirmed against the released jar).** Reordering, removing or renaming a
  constant changes what existing data means, and nothing in the build reports it. Only append
  constants. A later ZeroZ DB source already checks them; this release does not include that.
- **`DbQuery` can return live objects (confirmed).** When this process owns the store, `query` runs
  the query on the real graph and returns exactly what `execute` returned, with no copy. Only a
  process that does not own the store gets a serialized copy. Code that changes a returned object
  changes the graph outside any transaction and without saving it. Return copies or plain values
  from every query, as the rule about queries says, whichever mode you run in today.
- **Per-tenant stores are never closed before shutdown (confirmed).** `TenantStorageProvider` opens a
  store the first time a tenant is used and closes all of them when the application stops. Nothing
  closes an idle tenant's store, so memory grows with the number of tenants that have been used since
  the start. This is the same per-object cost as above, summed over tenants.

## Limits

- **Transactions are available** through `ZeroZDbNode` and `DbCommand` (see above); `storeAll`
  remains the simple grouping mechanism for the raw storage manager.
- **No conflict detection.** Two writers changing the same object: the later write wins, silently.
- **Saving is manual.** Forget it and the change lives only in memory until the process ends.
- **No query language.** Linear scans unless you build your own index.

## See also

- [Limitations](../reference/limitations.md)
- [Choosing how state moves](../decide/index.md)
