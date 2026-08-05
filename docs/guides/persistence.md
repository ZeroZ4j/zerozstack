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

## Limits

- **Transactions are available** through `ZeroZDbNode` and `DbCommand` (see above); `storeAll`
  remains the simple grouping mechanism for the raw storage manager.
- **No conflict detection.** Two writers changing the same object: the later write wins, silently.
- **Saving is manual.** Forget it and the change lives only in memory until the process ends.
- **No query language.** Linear scans unless you build your own index.

## See also

- [Limitations](../reference/limitations.md)
- [Choosing how state moves](../decide/index.md)
