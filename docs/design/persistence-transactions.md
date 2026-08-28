# Transactions and concurrency control

Status: **PROPOSED DESIGN — none of this is implemented.**
Date: 2026-07-26.

ZeroZ Stack persists a live object graph with EclipseStore and has no transactions, no rollback, no
store-level locking and no conflict detection. Today that is a documented limitation. This document
is the design for closing it, written now rather than later because two of the decisions are
effectively irreversible once applications exist: whether persistent types carry framework state, and
whether the storage manager applications inject is the raw one.

Everything under "What exists today" is verified against the code and cited. Everything under
"Design" is a proposal.

---

## 1. What exists today

### The persistence layer is 200 lines of glue

`zerozstack-store-eclipsestore` is five files. It uses `org.eclipse.store:storage-embedded:4.1.0`.

**Stores are per tenant, not one store.** `TenantStorageProvider.java:58` holds
`ConcurrentHashMap<String, EmbeddedStorageManager>`, populated at `:88-100`:

```java
return storageManagers.computeIfAbsent(tenantId, tid -> {
    Object root = null;
    if (!dataRootProvider.isUnsatisfied()) { root = dataRootProvider.get().createDefaultRoot(tid); }
    if (root == null) { root = new Object(); } // Fallback
    EmbeddedStorageManager manager = EmbeddedStorage.start(root, Paths.get(basePath, tid));
```

The root shape is application-defined and typed `Object` in the SPI (`DataRootProvider.java:38`).
`EclipseStoreProducer.java:54-65` produces a `@RequestScoped EmbeddedStorageManager` for the tenant
resolved by the optional `TenantResolver`.

### Applications call `store()` directly

`ProductServiceImpl.java:33-38` injects the raw manager. There is no `Storer`, no batching, no unit
of work — zero hits for `createStorer`, `storeAll` or `commit` anywhere in the repo.

The consequence is already a bug in the example, `ProductServiceImpl.java:57-58`:

```java
storage.store(root.getProducts()); storage.store(root);
```

Two independent commits. A crash between them persists the new product and loses the `nextId` bump.
Same shape at `:95-96`. A unit of work fixes this class of bug by construction.

Note the framework itself never persists: an accepted LiveSync mutation is applied in place,
announced and re-broadcast with no store (`WasmRmiServerEngine.java:488-499`), delegating to the
application via `LiveMutationListener` (`LiveMutationListener.java:25`, *"e.g. `storage.store(...)`"*).
**No example implements it.**

### Concurrency: virtual threads, no persistence-tier control

`WasmRmiServerEngine.java:201` creates `Executors.newVirtualThreadPerTaskExecutor()` per session and
submits every inbound frame to it (`:668-684`), activating a CDI `RequestContextController` by hand
because *"virtual threads have none, and @RequestScoped beans (e.g. the per-tenant
EmbeddedStorageManager producer) must resolve inside service calls"* (`:670-676`).

There is **no `ReentrantReadWriteLock`, `ReentrantLock` or `StampedLock` anywhere in the project.**
`synchronized` appears only on `ObjectMapper`'s handle maps and for WebSocket write serialization.

The one real lock is advisory and application-level: `LiveMutexManager.java:37-96` keeps a fair
1-permit `Semaphore` per object handle with a 30s `tryAcquire`, owner recorded as the session, and
`releaseAll(ownerId)` on disconnect. Opt-in via `LiveMutex.get(obj).lock()`. It is per-object,
session-scoped and cooperative — never held around a `store()`.

Writes also happen off-request: `JobServiceImpl.java:48` spawns `Thread.ofVirtual().start(...)`
mutating live state for ~15s outside any request context.

### The gaps are already documented, honestly

`reference/limitations.md:19`:

> Versioned mutations, acknowledgment and conflict rejection | Reserved in the protocol. The
> implemented sync path has no version field, no ACK and no conflict detection.

and `:26-27`:

> Whole-object, last-write-wins. No field-level merging. Two concurrent unlocked editors race and the
> later write wins; serialize them with `LiveMutex` where that matters.

The wire format reserves the space — `SyncFrameTypes.java:73-74` ("handle ID + version") — but
`0x11 SNAPSHOT` is dead code (`limitations.md:17`). No `@Version` exists.

---

## 2. The constraint that shapes everything

**Persistent types are also wire types, and they are TeaVM-compiled into the browser bundle.**

`Product`, `DataRoot` and friends live in the *shared* module, compiled into both tiers. The client
build is *verified* free of EclipseStore implementation classes — `zerozstack-client/pom.xml:41-42`:

> eliminates every EclipseStore implementation behind it — verified: no LazyReferenceManager,
> ObjectSwizzling, Lazy$Default or storage class appears in the emitted bundle.

So anything you put on a persistent type must (a) compile under TeaVM, (b) drag in nothing from
storage, and (c) pass the APT's `@DataModel` field-type blocklist and `_Serializer` generation
(`limitations.md:114-117`).

**This rules out the obvious design.** No base class carrying transaction metadata. No `@Version`
field. No marker interface implemented by persistent types. Framework state must live in **side
tables keyed by reference identity**, and `ObjectMapper.java:38-40` already sets that precedent with
a `Collections.synchronizedMap(new IdentityHashMap<>())`.

Three more constraints follow from the code:

- **The subclass seam is taken.** APT generates `<Model>_Live extends Model` overriding setters to
  call `LiveMutationTracker.fieldChanged(this)` (`RmiAnnotationProcessor.java:640`). A second
  generated subclass for dirty tracking collides. And it is client-only anyway —
  `BinaryRegistry.java:113-121`: *"The Wasm client runtime enables this at bootstrap; the server never
  does."* **Server-side persistent instances have zero dirty-tracking hook.**
- **There is no identity contract.** Domain keys are ad-hoc `long id` fields with an app-managed
  `DataRoot.nextId` and linear scans (`ProductServiceImpl.java:52-68`). No `getId()`, no key→instance
  index, no repository. `ObjectMapper`'s UUID handles are *wire* identity, application-scoped and
  never evicted (`limitations.md:33-35`) — unrelated to persistence.
- **A session carries no tenant identity** (`limitations.md:203-206`): tenancy exists only at the
  storage layer, resolved through a `@RequestScoped` producer.

---

## 3. The defects, ordered by what actually bites

This ordering differs from the equivalent design in a server-side framework, and the difference is the point.

**D1 — No unit of work.** Multi-object writes are non-atomic (`ProductServiceImpl.java:57-58`). Bites
every application that writes more than one object, which is all of them.

**D2 — No conflict detection on the wire.** Two clients editing the same object: whole-object
last-write-wins, silently. This is ZeroZ Stack's *characteristic* concurrency problem, because LiveSync
is a headline feature and the races are client-to-server, not server-internal.

**D3 — No store-level lock.** Concurrent virtual-thread writers mutating a shared graph with no
mutual exclusion. Real, but currently masked by D1: without transactions there is no window of
uncommitted state to observe, because every `store()` is its own commit.

**D4 — No rollback.** Nothing to undo a partially applied multi-object mutation. Note the mutation
path *avoids* needing undo rather than implementing it — `WasmRmiServerEngine.java:450-490` decodes
the payload twice, first with a throwaway `ObjectMapper` for authorization and validation so *"the
canonical object is untouched if the mutation is rejected"* (`:386-392`), then rewinds and applies in
place. That is validate-before-apply. It works for a single whole-object write and does nothing for a
multi-object failure mid-sequence.

**D2 is the one to fix first.** A store-level version check does not solve it — the check has to be
threaded through the binary protocol, which is precisely the reserved-but-unimplemented work at
`limitations.md:19`. Shipping a server-side transaction layer without touching the wire would leave
the documented problem exactly as documented.

---

## 4. Design

### 4.1 Explicit enlistment, no base class

```java
tx.willModify(product);
```

Forced by §2: there is no server-side dirty-tracking hook and there cannot be a base class. Before
images come from reflective flat-field snapshots taken at enlistment, held in an
`IdentityHashMap` inside the transaction.

This is the opposite trade from a framework with a persistent base class, and it is the right one
here: it costs application authors one line per modified object and costs the object model nothing.

Enforce it — a `store()` inside a transaction on an unenlisted object should **throw**, not silently
work. Silent divergence between "what I changed" and "what will be written" is the failure mode this
whole layer exists to prevent.

### 4.2 Scoped per storage manager

Constructed alongside `TenantStorageProvider`'s map, never a singleton. Two tenants must not
serialize against each other. A transaction captures which tenant's store it belongs to; cross-tenant
transactions are meaningless in ZeroZ Stack and should be rejected outright rather than half-supported.

### 4.3 A programmatic API first, interception second

```java
storeTx.run(tx -> { tx.willModify(root); root.getProducts().add(p); root.bumpNextId(); });
```

A CDI interceptor around `dispatchFrame` is *insufficient on its own*: writes happen from background
virtual threads (`JobServiceImpl.java:48`) and from the `LiveMutationListener` callback
(`WasmRmiServerEngine.java:493`), neither in a request scope. The programmatic form is the primitive;
an interceptor, if added, is sugar over it.

The natural interception point already exists and already does this shape of bracketing —
`WasmRmiServerEngine.java:674-683` activates and deactivates a CDI request context around
`dispatchFrame`. A transaction can bracket the same call.

### 4.4 Deferred stores, flushed once

No `store()` during the transaction body; one ordered flush at commit, under a single write-lock
acquisition. Fixes D1.

Worth knowing what *not* to copy: XDEV's `spring-data-eclipse-store` queues actions but takes the
lock and calls `storer.commit()` once **per action**, so its commit is not atomic. Do better than the
reference implementation.

### 4.5 A per-store read/write lock, held across the transaction

Fixes D3. Write lock acquired at transaction start, released at completion — spanning mutate *and*
commit-or-rollback. Reentrant.

`org.eclipse.serializer.concurrency.LockedExecutor` ships in serializer 4.1.0, which is already on
the classpath, and Eclipse Data Grid's own `ClusterLockScope` wraps it. But its interface is
callback-only (`read(Action)`, `write(Producer<R>)`) and never exposes the underlying lock, so it
cannot express a span. Since `run(...)` is the API anyway (§4.3), `LockedExecutor` is a genuinely good
fit here — better than it is for a framework saddled with a `begin`/`commit` API. Use it.

`StripeLockedExecutor` in the same package is the escape hatch if per-object granularity is ever
needed. `LiveMutexManager` already occupies that niche cooperatively; do not build a second one.

### 4.6 Version and conflict detection, on the wire

Fixes D2, and this is the part that needs protocol work rather than storage work.

- **Version state in a side table** keyed by reference identity, per §2. A synthetic counter, since
  persistent types cannot carry a version field. Bumped on commit.
- **Wire it through.** `SyncFrameTypes.java:73-74` already reserves handle ID + version. A LiveSync
  mutation frame carries the version the client last saw; the server compares before applying, and
  rejects with the existing corrective-sync path (`WasmRmiServerEngine.java:520-522`) on mismatch.
- **Reuse validate-before-apply.** The two-pass decode at `:450-490` is already the right structure —
  the version check belongs in pass one, alongside authorization and validation, where the canonical
  object is still untouched.
- This also retires the dead `0x11 SNAPSHOT` opcode (`limitations.md:17`) or gives it a purpose.

The client half needs a decision this document cannot make: on rejection, does the UI silently
re-render canonical state (losing the user's edit), or surface a conflict? The optimistic-UI machinery
already re-sends canonical state to the writer only, so silent-revert is the zero-work default and is
probably wrong for anything a user typed.

### 4.7 The layer must replace the producer, or it is decoration

Applications inject the raw `EmbeddedStorageManager` (`ProductServiceImpl.java:33`) and every document
teaches `storage.store(...)` directly (`CONCEPTS.md:33`, `CODE_WALKTHROUGH.md:80`,
`AGENT_PROMPTS.md:100`). A transaction layer alongside that is trivially bypassable, and a
bypassable correctness layer is worse than none — it creates the belief that writes are transactional
when some are not.

Making it authoritative means wrapping `EclipseStoreProducer` so what applications inject is the
transactional manager. That is a **breaking change across all seven examples, the archetype, and the
documentation and agent-prompt corpus.** Cheap now at 0.4.0 with no external users; expensive later.
This is the single most time-sensitive decision in this document.

---

### 4.8 Participating in a Helidon transaction

The design above is self-contained: `storeTx.run(...)` owns its own commit. That is fine while
EclipseStore is the only durable resource. The moment an application writes to EclipseStore **and**
anything else in one logical operation — a database, a JMS queue — the two commits are independent and
a failure between them loses data. This section is the design for joining a Helidon-managed
transaction so that does not happen.

#### What we have to work with

`zerozstack-server-helidon/.../Zeroz4jServer.java:20` imports `io.helidon.microprofile.server.Server`,
and `zerozstack-server-helidon/pom.xml:26-39` pulls
`io.helidon.microprofile.bundles:helidon-microprofile`. So this is **Helidon MP 4.0.8** (BOM at root
`pom.xml:151-157`), CDI implementation Weld.

**There is no transaction manager today.** Grep across the whole repo for `jakarta.transaction`,
`Transactional`, `TransactionManager`, `UserTransaction`, `narayana`: **zero matches**, and
`jakarta.transaction-api` is not on the resolved classpath at all. This is greenfield, not a
modification.

#### Do not use `@Tx.Required`

Helidon does have a declarative transaction API — `io.helidon.transaction.Tx.Required` and friends,
in `io.helidon.transaction:helidon-transaction`, from **4.3.0** (not 4.2). Ignore it. `TxInterceptor`
implements `io.helidon.service.registry.Interception.Interceptor`, **not**
`jakarta.interceptor` — so `@Tx.Required` on a Weld bean does nothing at all, silently. It is
Helidon-SE/Service-Registry machinery.

Helidon's transaction SPI is also not an enlistment point, which is worth recording so nobody spends
a day on it. `io.helidon.transaction.spi` has exactly two interfaces: `TxSupport`, which *replaces*
the whole transaction implementation (`jta` vs `resource-local`), and `TxLifeCycle`, which looks like
a participation hook and is not — in `JtaTxSupport` the notification fires in a `finally` **after**
`tx.commit()` has already returned or thrown, so it cannot vote, cannot abort, and cannot do
transactional work. It also reports `commit` even when the commit failed. Not a durability signal.

**Helidon adds no enlistment extension point of its own.** That is good news: the design is plain
JTA, spec-anchored and portable.

#### The route that works today

`io.helidon.integrations.cdi:helidon-integrations-cdi-jta` + `-jta-weld`, both **already managed by
the Helidon BOM at 4.0.8** — add them with no `<version>`. One gotcha: the BOM does *not* manage
`jakarta.transaction:jakarta.transaction-api`, so pin it yourself (2.0.1 for Jakarta EE 10) or Maven
fails with *"'dependencies.dependency.version' … is missing"*.

That brings in **Narayana 7.0.0.Final**, and gives a real
`TransactionManager` / `UserTransaction` / `TransactionSynchronizationRegistry`. Narayana's own jar
carries `com.arjuna.ats.jta.cdi.TransactionExtension` and the `TransactionalInterceptor*` classes and
registers itself as a CDI extension, so `@Transactional` and `@TransactionScoped` **self-activate**
once it is on the classpath. Helidon's `NarayanaExtension` additionally publishes a
`jakarta.transaction.Transaction` bean in `TransactionScoped` scope, so `@Inject Transaction` works
and enlistment is directly reachable.

#### Three ways to join, and which to pick

| Option | Mechanism | Atomicity | Recovery burden |
|---|---|---|---|
| A. `Synchronization` | `registerInterposedSynchronization`, flush in `beforeCompletion` | **none** — ordering only | zero |
| B. **`XAResource` + LRCO** | + Narayana's last-resource marker | real, if we are the only non-XA resource | zero |
| C. Full `XAResource` | durable prepared-xid journal + `recover()` | full 2PC | high |

**Choose B.** Helidon MP *is* Narayana, so the Last Resource Commit Optimization is free.

Option A is what Hibernate does — its entire `JtaPlatform` SPI has no `XAResource` method anywhere; it
registers a `Synchronization`, flushes in `beforeCompletion`, and gets atomicity from the container's
XA `DataSource`. That crutch does not exist here. Under A the losing interleaving needs no crash at
all: EclipseStore commits in `beforeCompletion`, then the database's `prepare()` fails on a
constraint, the database rolls back, and EclipseStore holds data the database does not.

Option C is the only design with no window, and it requires EclipseStore to durably retain a prepared
`Xid` across process death and report it from `recover()`. See the open question below.

#### Option B, concretely

- Implement `XAResource` and additionally the marker interface
  `com.arjuna.ats.jta.resources.LastResourceCommitOptimisation` (British spelling; the configurable
  property is `com.arjuna.ats.jta.lastResourceOptimisationInterfaceClassName`).
- Narayana's `LastResourceRecord.topLevelPrepare()` **actually calls our `commit()`**, sorted last
  within the prepare phase; `topLevelCommit()` is then a no-op. So the real sequence is: prepare every
  XA resource → commit us → the coordinator logs its decision → phase 2 on the XA resources. By the
  time we commit, application and constraint failures are already eliminated.
- **Only one LRCO resource is permitted per transaction, and `Transaction.enlistResource` returns
  `false` rather than throwing when a second is attempted. Check the boolean.**
- Register any ordering-sensitive work with `registerInterposedSynchronization`, not a plain
  `Synchronization` — interposed callbacks run after everything registered directly with the
  transaction but before 2PC starts, which is where a resource-manager-level participant belongs.
- `afterCompletion(int)` runs in an undefined context, permits no transactional work, and **may arrive
  on a different thread** than the one that registered the synchronization. Hibernate ships
  `SynchronizationCallbackCoordinatorTrackingImpl` precisely for this. Do not touch thread-bound state
  there.
- If `beforeCompletion()` throws, Narayana defers the throwable and calls `preventCommit()`, so the
  transaction does roll back. A rollback-only transaction skips `beforeCompletion` entirely, because
  `CoordinatorEnvironmentBean.beforeCompletionWhenRollbackOnly` defaults to `false`. Both behaviors
  are Narayana defaults, not spec guarantees — depend on them knowingly.
- Use **`@TransactionScoped`**, not `@RequestScoped`, for the per-transaction accumulator: it follows
  the transaction rather than the thread, survives suspend/resume, and dies exactly when the
  transaction ends. `@RequestScoped` would stay alive across transaction boundaries inside one request.

#### The residual window, and why it is not closable today

With LRCO the window shrinks to: our commit returns durably, then the coordinator dies before its
commit record reaches disk. On restart the prepared XA branches roll back, and we have committed.
Narayana's own documentation is blunt that this is silent — *"there is no record in the system of the
fact that a heuristic outcome has occurred."* No exception, nothing to alert on.

Narayana closes this for JDBC with the **Commit Markable Resource**, which writes the prepare record
into a table inside the non-XA resource's own atomic commit. CMR is JDBC-only. The equivalent here
would be for EclipseStore to record the `Xid` **within its own atomic commit** and expose a real
`recover()` — which would make it genuinely 2PC-capable, option C. **Whether EclipseStore's storage
model permits writing such a record atomically alongside the object-graph commit is unresolved and is
the pivotal question for this section.**

#### Constraints this puts on ZeroZ Stack

- **Keep the whole transactional unit on one virtual thread.** JTA context is thread-bound by
  definition and Helidon's `io.helidon.common.context.Contexts` propagates Helidon's context but
  **not** JTA's. Handing work to another thread loses the transaction unless you explicitly
  `suspend()`/`resume()`. Do not design against MicroProfile Context Propagation — `ThreadContext.TRANSACTION`
  propagation is optional in the spec, and whether Helidon 4 MP implements it at all is unverified.
- **`JobServiceImpl.java:48`'s `Thread.ofVirtual().start(...)` and the `LiveMutationListener` callback
  (`WasmRmiServerEngine.java:493`) have no transaction context** and would each need their own
  transaction. This is the same constraint that makes §4.3's programmatic API mandatory, arriving from
  a second direction.
- Virtual threads are otherwise fine: the Narayana `synchronized`-pinning problem was fixed by
  migrating to `ReentrantLock` (jbosstm/narayana#2077) and shipped in **7.0.0.Final**, which is what
  4.0.8 pins. Recovery and background pools were explicitly out of scope of that fix.
- `@Transactional` is interception-based, so self-invocation and non-CDI objects are not intercepted.
  The engine's `CDI.current().select(...)` style is compatible.
- Narayana is configured by **system properties**, not MicroProfile Config
  (`ObjectStoreEnvironmentBean.objectStoreDir`, `com.arjuna.ats.arjuna.coordinator.defaultTimeout`).
- Naming collision to head off in the code and docs: EclipseStore's own `transactions_*.sft` storage
  log has nothing to do with JTA transactions.

#### This is novel work

Verified: **no EclipseStore/MicroStream JTA integration exists.** The CDI extension
(`eclipse-store-integrations-cdi4`) exposes `@Storage`, an injectable `StorageManager`, foundation
customizers and initializers — no JTA, no `XAResource`, no `@Transactional` participation. The Spring
Boot integration likewise has no `PlatformTransactionManager`. No issue, PR or document proposing one
was found. The closest working precedent for the *pattern* is **Apache Geode**, which uses Narayana's
`LastResourceCommitOptimisation` marker exactly as proposed here; Infinispan offers the same spectrum
(`useSynchronization=true` vs full XA with recovery) and documents the identical tradeoff.

Treat this as optional and demand-driven: build it when an application actually needs to write
EclipseStore and a second resource atomically. Until then §4.4's single-resource commit is correct and
simpler, and nothing here changes its design — B is additive.

## 5. Not adopted

**Working copies** (copy on read, mutate the copy, merge back at commit — the XDEV model). Rejected
for a reason specific to ZeroZ Stack: the deep copy would have to happen on the *server*, and the
serializer-based copy trick that makes it cheap requires `--add-opens` on `java.base` plus
`setTypeEvaluatorPersistable(a -> true)` — a broad reflection grant for a framework whose thesis is
minimal machinery. It also destroys object identity across reads, which `ObjectMapper`'s
identity-keyed handle registry depends on.

**A `@Version` field on persistent types.** §2. Un-shareable with the client tier.

**Eclipse Data Grid** for multi-VM. Source is open (EPL-2.0, `github.com/eclipse-datagrid/datagrid`,
incubating) but nothing is published to Maven Central (verified: **404** on
`repo1.maven.org/maven2/org/eclipse/datagrid/`, 0 artifacts in search, no releases, no tags, all
`1.0.0-SNAPSHOT`). It is single-writer full-replica over Kafka, eventually consistent cluster-wide,
with **one store per cluster** — the writer role is a field on a singleton `StorageNodeManager`, so
per-tenant stores cannot each have their own writer node. Mandatory Kubernetes + Kafka. Not viable
for a per-tenant-store framework today; revisit if the multi-cluster console ships.

---

## 6. Sequence

| # | Item | Fixes | Breaking? |
|---|---|---|---|
| 1 | Wrap `EclipseStoreProducer`; applications inject a transactional manager | enables all | **yes** — examples, archetype, docs |
| 2 | `storeTx.run(...)` + explicit enlistment + deferred flush | D1 | no |
| 3 | Per-store lock via `LockedExecutor` | D3 | no |
| 4 | Before-image rollback | D4 | no |
| 5 | Version side table + wire version field + conflict rejection | D2 | protocol change |
| — | JTA/Helidon participation via `XAResource` + LRCO (§4.8) | multi-resource atomicity | additive, opt-in |

1 first because it is the breaking one and gets cheaper the earlier it lands. 5 last because it needs
a client-side product decision (§4.6), even though D2 is the most user-visible defect.

---

## 7. Open questions

1. **Does the framework take over persistence, or stay out of it?** Today
   `LiveMutationListener` delegates persistence to the application and no example implements it. A
   transaction layer implies the framework knows when to write. Deciding this settles §4.7.
2. **Conflict UX on rejection** — silent corrective re-render, or a surfaced conflict? (§4.6)
3. **Does `LiveMutex` survive?** With versioned optimistic rejection, a cooperative pessimistic
   mutex becomes a second mechanism for the same problem. Keeping both is defensible — pessimistic
   for long human edits, optimistic for everything — but it should be a decision, and
   `decide/index.md` would need a sixth row.
4. **Enlistment ergonomics.** `tx.willModify(x)` is honest but easy to forget, and forgetting it
   should throw (§4.1). Is a throwing default acceptable for a framework that markets simplicity?
5. **Can EclipseStore write a prepared-`Xid` record atomically inside its own commit?** (§4.8) If yes,
   a genuinely 2PC-capable resource is possible and the LRCO window closes. If no, LRCO's silent
   heuristic window is the floor for multi-resource atomicity. Unresolved from public sources;
   needs a spike against the storage engine.
6. **Does ZeroZ Stack want a JTA dependency at all?** §4.8 is additive and opt-in, but adopting it puts
   Narayana in the runtime. An alternative is to declare multi-resource atomicity out of scope and
   say so in `reference/limitations.md`, which is a defensible position for a framework whose thesis
   is minimal machinery.

---

## 8. Outcome: this became ZeroZ DB

This design was written alongside an equivalent one for a server-side framework with the same
underlying problem — one EclipseStore per tenant, no transactions, no rollback, no conflict
detection. Writing both at once was deliberate: two independent designs converging on the same
shape is the evidence that the shape is right rather than merely convenient.

They did converge — on per-store scoping, deferred flush, before-images in identity-keyed side
tables, a pluggable version accessor and a programmatic `run(...)` API — and the result was
extracted into **[ZeroZ DB](https://github.com/zeroz4j/zerozdb)**, which now backs this
framework's persistence. See [Store modes](../store-modes.md).

Where the two consumers genuinely differ, and why the library exposes both paths:

| | server-side framework | ZeroZ Stack |
|---|---|---|
| Enlistment | implicit, via a generated base class | **explicit only** — no base class is possible here |
| Dominant conflict axis | server-side, across HTTP requests | **client→server LiveSync frames** |
| Version carrier | an existing `modifiedAt` on the model | **synthetic counter in a side table** |
| Cross-store transactions | required for tenant provisioning | rarely needed |
| Priority order | lock → cross-store → rollback → version | **producer → unit of work → lock → rollback → wire version** |

The original note here said extracting a shared library was premature until one consumer had run
in anger. That turned out to be the right test and it has since been met.
