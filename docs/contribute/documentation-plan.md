# Documentation rewrite plan

Working document. Status: **phases 0 and 1 landed**; phases 2 onward outstanding. Target framework
version 0.3.0.

What has been done:

- `context7.json`, `AGENTS.md`, `llms.txt`, `mkdocs.yml`, `docs/requirements.txt`, and the Pages
  workflow (`.github/workflows/docs.yml`, building `--strict` on pull requests).
- The `decide/` pack: [index](../decide/index.md),
  [events vs signals vs LiveSync](../decide/events-vs-signals-vs-livesync.md),
  [RMI vs state sync](../decide/rmi-vs-state-sync.md),
  [local vs shared signal](../decide/local-vs-shared-signal.md),
  [anti-patterns](../decide/antipatterns.md).
- [Glossary](../reference/glossary.md), [Limitations](../reference/limitations.md),
  [Troubleshooting](../guides/troubleshooting.md), [Quickstart](../start/quickstart.md), and this
  style guide.
- `docs/AGENT_PROMPTS.md`: fixed the `new Thread` self-contradiction, added the broadcast-scope and
  LiveSync-callback rules, pointed its reading list at `decide/`.
- `README.md`: corrected the WebAssembly claim and added a "start here" routing block.

Everything below from Part 2 onward is the original analysis, retained because the migration map and
the outstanding code fixes still apply.

---

## Part 0 — The three decisions up front

### 0.1 GitHub Wiki? No.

A wiki is the wrong container for this project, for four concrete reasons:

1. **Context7 cannot index it.** Context7 ingests a *GitHub repository* (`owner/repo`) and walks
   Markdown/RST/notebooks inside it, controlled by `context7.json` at the repo root. A wiki lives in
   a **separate** git repository (`zeroz4j.wiki.git`) that is not the submitted repo. Moving docs to
   the wiki would remove them from Context7's reach — the exact opposite of a stated goal.
2. **No review, no CI.** Wiki edits bypass pull requests. Code samples can never be compile-checked.
3. **No version coupling.** The framework is at 0.3.0 with tags v0.1.0/v0.2.0. Docs in `/docs` are
   branched and tagged with the code; a wiki has one mutable state and silently describes a version
   that no longer exists.
4. **Not the accepted practice.** The 2020s consensus for framework docs is *docs-as-code*: Markdown
   in the repo, rendered by a static site generator, published by CI. Wikis are now used for
   scratch/community notes, not for a product's documentation pack.

**Decision:** keep docs as plain Markdown in `/docs`, publish a static site from it, and leave the
wiki either disabled or reduced to a single page pointing at the site.

### 0.2 Publishing target

Markdown in `/docs` → **MkDocs Material** → GitHub Pages → CNAME `docs.zeroz4j.com`
(`www.zeroz4j.com` already exists as a landing page).

Why MkDocs Material over Docusaurus / Starlight / VitePress:

- The project's entire thesis is "no JavaScript, no TypeScript, no npm". Introducing a Node
  toolchain to document that thesis is an on-brand embarrassment; Python is neutral.
- One `mkdocs.yml`, a 6-line CI job, no `node_modules`.
- Best-in-class offline search, versioning via `mike` when the time comes, admonitions, tabbed code
  blocks, and — critically — `pymdownx.snippets` for including **real code from the example modules**
  (see 0.3).
- The rendered site is a *projection* of the Markdown. The Markdown stays canonical and
  Context7-friendly. No MDX, no JSX-in-Markdown, no site-generator-specific syntax that would
  poison the source files for other consumers.

Hard rule: **never use generator-specific syntax that breaks plain-Markdown reading.** Docs must
render acceptably on github.com and parse cleanly for Context7. Snippet includes are the one
exception, and they degrade to a visible comment line rather than to garbage.

### 0.3 Samples must be compile-checked

Every non-trivial code sample is extracted from a module under `zerozstack-examples/`, which CI already
builds on every push (`.github/workflows/build.yml` runs `mvn -B clean install` on the whole
reactor). Mark regions in the real source:

```java
// --8<-- [start:publish]
events.publish(ChatEvents.MESSAGE_POSTED, msg);
// --8<-- [end:publish]
```

and include them:

```markdown
;--8<-- "zerozstack-examples/chat-events/.../ChatServiceImpl.java:publish"
(the leading semicolon above only escapes the directive so this page can describe it;
omit it in real usage)
```

Result: a sample cannot rot without turning CI red. This is the single highest-leverage change in
the whole plan, because the current docs' credibility problem is stale snippets.

Trade-off accepted: included snippets are not visible when reading the raw `.md` on GitHub, and
Context7 will index the include directive rather than the code. Mitigation: the **reference** pages
and the **decision guide** — the pages Context7 answers from most — keep their snippets inline and
short; snippet-includes are used for the long end-to-end tutorial/how-to walkthroughs where GitHub
readers are rare and the site is the intended surface. A CI check asserts inline snippets in
reference pages still compile via a `docs-samples` test module.

---

## Part 1 — Information architecture

Organised on **Diátaxis** (tutorial / how-to / reference / explanation), because the audit shows the
current failure mode is exactly the one Diátaxis exists to fix: explanation, instruction and
reference interleaved in the same file, so a beginner cannot find a path and an expert cannot find a
fact.

Two additions beyond vanilla Diátaxis, both justified by this project specifically:

- a **Decision Guides** section, because the framework's hardest problem is *choosing between
  constructs*, and that is neither a how-to nor an explanation;
- an **AI Agents** section, because "this stack exists so agents can work safely" is the project's
  thesis, and the docs are where that claim is either honoured or exposed as marketing.

```
docs/
├─ index.md                          Landing: what it is, who it's for, 3 paths in
│
├─ start/                            ── TUTORIALS (learning-oriented, ordered, must work verbatim)
│  ├─ index.md                       Which tutorial to pick
│  ├─ prerequisites.md               JDK 21 (exact), Maven, browser WasmGC support, disk, OS notes
│  ├─ quickstart.md                  10 min: archetype → build → run → see it in the browser
│  ├─ first-feature.md               30 min: @DataModel + @RmiService + a button, end to end
│  ├─ making-it-live.md              30 min: add a shared signal, then an event, then LiveSync —
│  │                                 the same feature three ways, so the distinction is *felt*
│  └─ next-steps.md                  Where to go per goal
│
├─ guides/                           ── HOW-TO (task-oriented, "I already know the concepts")
│  ├─ index.md
│  ├─ models-and-serialization.md    @DataModel, supported types, UUID/Instant/enum, evolution
│  ├─ rmi-services.md                Declare, implement, inject, async/suspension, errors
│  ├─ ui-building-views.md           Composition, layout, styling with DaisyUI/Tailwind
│  ├─ ui-forms-and-validation.md     Binder + annotations, client feedback, server enforcement
│  ├─ ui-multiple-views.md           Navigation between screens; @Route status; the manual pattern
│  ├─ signals-local-state.md         ValueSignal/Computed/Effect, immutability, disposal
│  ├─ signals-shared-state.md        Signals.shared / sharedWritable, roles, retained value
│  ├─ server-events.md               EventTopic, EventPublisher, ServerEvents.on, snapshot race
│  ├─ livesync-objects.md            @LiveSync, @ClientWritable, LiveMutationListener, LiveMutex
│  ├─ persistence-eclipsestore.md    Storage root, store(), what must be re-stored, multi-tenancy
│  ├─ security-auth.md               Auth frame, @Secured, @RolesAllowed, RmiSecurityContext, dev auth
│  ├─ testing.md                     Unit-testing signals/services without a transport
│  ├─ build-and-package.md           TeaVM config, APT wiring, output layout, build times
│  ├─ deploying.md                   Packaging, classpath launch, ports, reverse proxy, wss://
│  └─ troubleshooting.md             Symptom → cause → fix table (see Part 3)
│
├─ decide/                           ── DECISION GUIDES (the project's real pain point)
│  ├─ index.md                       Master decision tree across all five mechanisms
│  ├─ events-vs-signals-vs-livesync.md   The deep comparison + worked scenarios
│  ├─ rmi-vs-state-sync.md           "State edits sync, operations call"
│  ├─ local-vs-shared-signal.md      When state should cross the wire at all
│  └─ antipatterns.md                Named failure modes, why they hurt, the fix
│
├─ reference/                        ── REFERENCE (information-oriented, dry, complete)
│  ├─ index.md
│  ├─ annotations.md                 Every annotation: attributes, targets, defaults, generated output
│  ├─ api-shared.md                  com.zeroz4j.api / .signals / .events
│  ├─ api-client.md                  Zeroz4jClient, RmiSecurityContext, scheduler
│  ├─ api-server.md                  Zeroz4jServer, SyncEngine, EventPublisher, listeners
│  ├─ ui-components.md               Component catalogue: class, constructors, key methods, sample
│  ├─ wire-protocol.md               Frames, opcodes, type IDs (the current PROTOCOL.md, tightened)
│  ├─ configuration.md               Every knob: system properties, ports, paths, TeaVM flags
│  ├─ supported-types.md             Serializable type matrix, incl. what is NOT supported
│  ├─ limitations.md                 Honest single list of every known limit and "not yet"
│  └─ glossary.md                    Event / Signal / Push / Message / Sync / Mutation, defined once
│
├─ explain/                          ── EXPLANATION (understanding-oriented)
│  ├─ index.md
│  ├─ why-zero-impedance.md          The thesis, moved out of the README
│  ├─ architecture.md                Tiers, modules, request lifecycle diagrams
│  ├─ how-rmi-works.md               APT → stub → frame → dispatcher → virtual thread
│  ├─ how-state-sync-works.md        Object mapper, handles, _Live subclasses, broadcast
│  ├─ threading-model.md             Loom on the server, cooperative UI scheduler on the client
│  ├─ comparison-vaadin-hilla.md     Honest positioning vs the obvious neighbours
│  └─ project-status.md              PoC status, roadmap, what "experimental" means here
│
├─ agents/                           ── AI AGENT ENABLEMENT
│  ├─ index.md                       How to point an agent at ZeroZ4j
│  ├─ rules.md                       The canonical do/don't rule list (source for context7 `rules`)
│  ├─ prompts.md                     Curated task prompts (from AGENT_PROMPTS.md, trimmed)
│  └─ recipes.md                     Copy-paste-correct minimal end-to-end skeletons
│
├─ contribute/
│  ├─ index.md                       (CONTRIBUTING.md expanded, or kept at root and linked)
│  ├─ docs-style-guide.md            Rules in Part 4 of this plan
│  └─ release-process.md
│
└─ examples/
   └─ index.md                       Table: example → concepts shown → run command → doc links
```

Root files that stay at root (GitHub conventions, and Context7 default-excludes most of them):
`README.md`, `CONTRIBUTING.md`, `LICENSE`, `NOTICE`, plus new `AGENTS.md`, `llms.txt`,
`context7.json`, `CHANGELOG.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`.

### The README's new job

The README becomes a **router**, not a thesis. Target ~120 lines:

1. One-paragraph what-it-is + the honest status line ("experimental proof-of-concept").
2. A 20-line code sample showing model → service → UI, so the reader instantly sees the shape.
3. Install/quickstart in 5 commands.
4. A short table linking the four doc entry points (Learn / Build / Decide / Look up).
5. Module table.
6. Status, licence, author, consulting link — at the bottom, compressed.

The current thesis prose moves verbatim to `explain/why-zero-impedance.md`, where it is an asset
rather than an obstacle between the reader and `mvn`.

---

## Part 2 — The Events / Signals / LiveSync problem

This is the section that earns the rewrite. It gets a **top-level section of its own** (`decide/`),
is linked from every related how-to and from the README, and is written as a decision procedure
rather than as a comparison of features.

### 2.1 Frame the field as five mechanisms, not three

The confusion partly exists because the docs discuss three constructs while developers actually
choose between five. Name all five, always in the same order:

| # | Mechanism | Direction | Carries | Identity | Client writes |
|---|-----------|-----------|---------|----------|---------------|
| 1 | Local signal (`ValueSignal`/`Computed`/`Effect`) | none — client only | a value | no | n/a |
| 2 | RMI call (`@RmiService`) | client → server → reply | a request + a result | no | yes, by definition |
| 3 | Server event (`EventTopic`) | server → all clients | an occurrence | no | no |
| 4 | Shared signal (`Signals.shared`) | server ⇄ clients | one current value | by wire name | opt-in (`sharedWritable`) |
| 5 | LiveSync (`@LiveSync`) | server ⇄ clients | an object's fields, in place | by object handle | opt-in (`@ClientWritable`) |

The full comparison table for `decide/events-vs-signals-vs-livesync.md`, all verified against the
source. **Several rows are decision-changing and appear in no current doc:**

| | Server event | Shared signal | LiveSync |
|---|---|---|---|
| Declared as | `EventTopic.of(Class, "name")` constant | `Signals.shared[Writable](…)` constant | `@DataModel @LiveSync [@ClientWritable]` |
| Annotations needed | none | none | 2–3 |
| Server→client opcode | `0x02 RPC_PUSH` | `0x17 SIGNAL_UPD` | `0x10` (constant misleadingly named `SUBSCRIBE`) |
| Granularity | whole payload per occurrence | whole value, `equals`-deduped | whole object graph, applied **in place** |
| Late joiner sees | nothing | the retained latest value, on subscribe | nothing until something pushes |
| **Scoping** | **GLOBAL only** | **GLOBAL only** (JVM-wide static registry) | **`GLOBAL` / `SESSION` / `USER`** |
| Client write | n/a | `sharedWritable`, optimistic + corrective snap-back | `@ClientWritable`, server applies then re-broadcasts |
| Inbound dispatched to UI thread | yes | yes | **no** |
| **Tells the view it changed** | the handler is the notification | yes, via `Effect`/`Computed` | **no — nothing at all** |

Two of those rows should drive the guide's headline advice:

- **LiveSync is the only mechanism with per-session/per-user scoping** (`SyncScope.SESSION`/`USER`).
  Events and shared signals broadcast to a JVM-wide static session set with no principal or tenant
  filter. That is a *selection criterion*, and it is currently documented nowhere.
- **LiveSync has no change callback.** Inbound syncs write the object's fields and notify nobody. The
  only LiveSync example in the repo works around this with a **500 ms `Window.setInterval` poll**
  (`chat-livesync/…/ChatView.java:149-153`) copying the object into a signal. A doc that presents
  LiveSync as the premium "it just updates" option without stating this sets every reader up to
  fail. State it in the first paragraph, and document the poll-into-a-signal bridge as *the* pattern
  until a callback exists.

### 2.2 The decision procedure (the canonical artifact)

Four questions, asked in order. Each has one answer.

```
Q1. Does anything outside this browser tab need to know?
    NO  → 1. Local signal. Stop. (Most UI state lands here. Say so loudly.)
    YES → Q2

Q2. Who initiates, and does the caller need an answer?
    The client asks and needs a result, or is performing a named operation
    ("approve", "checkout", "delete", "log in")
        → 2. RMI call. Stop.
    The server has news to volunteer, or the client is *editing shared state*
        → Q3

Q3. Does a *latest value* fully describe it, or does each occurrence matter on its own?
    Each occurrence matters — you would lose information by keeping only the last one
    (a chat message, a log line, "job N failed", "user X joined")
        → 3. Server event.
    Only the current value matters; a client that connects late must see it
    (a job's progress, a document's contents, a price, presence)
        → Q4

Q4. Is this data the same for everyone, or private to one user or session?
    Private to a user/session
        → 5. LiveSync with SyncScope.SESSION or USER — it is the ONLY mechanism
          that can scope a push. Events and shared signals go to every connected
          session with no principal or tenant filter. Do not put per-user or
          sensitive data in them.
    Same for everyone → Q5

Q5. Is it one value, or an identified object that views hold references to and edit field by field?
    One value, replaced wholesale                    → 4. Shared signal
    An object with identity, edited through setters,
    where several views hold the same instance       → 5. LiveSync
                                                       (and read the callback caveat below)
```

Then the two tie-breakers, stated as slogans that survive being remembered badly:

- **"State edits sync, operations call."** (Already in LIVESYNC.md — promote it, it's good.)
- **"Events are news, signals are facts."** News is missed if you weren't listening; facts are true
  when you arrive.

### 2.3 Worked scenarios (the part that actually teaches)

A table of ~14 concrete features, each with the chosen mechanism *and the plausible wrong answer
plus why it fails*. This is what transfers, and it is what the current docs lack entirely.

| Feature | Use | Tempting wrong choice → why it fails |
|---|---|---|
| Chat message stream | Event | Shared signal — only the last message survives; history is lost |
| "Someone is typing…" | Shared signal (`sharedWritable`) | Event — the indicator never clears if you miss the stop event |
| Job progress bar | Shared signal | Event — a client opening the page mid-job sees 0% until the next tick |
| Job finished, show a toast | Event | Shared signal — a late joiner replays a toast for a job that ended an hour ago |
| Collaborative document body | LiveSync + `@ClientWritable` | Shared signal — every keystroke replaces the whole doc, last-writer-wins across fields |
| "Add to cart" | RMI | LiveSync — an operation with rules and a result masquerading as an edit |
| Form field contents while typing | Local signal | Anything shared — you are broadcasting keystrokes to the server |
| Filter/sort/tab selection | Local signal | Shared signal — one user's filter changes everyone's view |
| Inventory quantity, editable by staff | LiveSync + `@ClientWritable("staff")` | RMI per keystroke — chatty, no propagation to other viewers |
| Online-user list | Shared signal | Event (join/leave) — the list is wrong forever after one missed event |
| Audit log tail | Event | LiveSync — you don't want the past mutated, you want appends |
| Feature flag / config | Shared signal | RMI at startup — never updates without a reload |
| "Your session expired" | Event | — |
| Derived count/badge from a list | `Computed` over whatever holds the list | Recomputing in every render path — drifts |
| A user's own notifications | LiveSync, `SyncScope.USER` | Event or shared signal — **broadcasts to every session, including other tenants** |

Caveat to state honestly in this section: **no example in the repo uses `@ClientWritable`** — the up
direction is exercised only in `ServerLiveMutationTest`. Either build `collab-editor` to demonstrate
it (recommended, it also covers `LiveMutex`) or mark it clearly as lightly-travelled. Writing a
tutorial around an undemonstrated code path is how docs acquire their next generation of stale
claims.

### 2.4 Named anti-patterns (`decide/antipatterns.md`)

Give each a name so it can be referenced in review comments:

1. **Event-as-state** — broadcasting state via events; late joiners see nothing. → shared signal.
2. **Signal-as-log** — pushing a growing list through a shared signal; whole-list resend per item,
   equality dedup surprises. → event + local reduction.
3. **In-place mutation** — `list.add(x); signal.set(list)`; `equals` swallows it, nothing re-renders.
   → `update()` with a new instance. (Also the LiveSync collection variant:
   `obj.getTags().add(...)` is invisible.)
4. **Operation-as-edit** — modelling "approve" as a `@ClientWritable` field flip; no name, no
   security point, no validation point, no audit. → RMI method.
5. **Leaked effect** — `Effect.create` in a view that is removed without disposing; upstream signal
   keeps the view alive. → own a `List<Disposable>`.
6. **Snapshot race** — fetch-then-subscribe; events during the fetch are lost.
   → subscribe first, then fetch, then merge.
7. **Polling in a Loom world** — a client timer calling RMI on an interval. → shared signal or event.
8. **Unbounded broadcast** — sending per-user or tenant-scoped data through events or shared
   signals. Both fan out to a **static, JVM-wide** session set with no principal or tenant filter
   (`WasmRmiServerEngine.activeSessions:94`; `Signals.registry` is a static map). This is a
   **security** finding, not a performance note, and it appears in no current doc. Only
   `SyncScope.SESSION`/`USER` can scope a push.
9. **Assuming LiveSync re-renders** — it updates fields and notifies nobody. → poll into a signal.
10. **Un-consumed push** — the repo itself contains this: `components-showcase` calls
    `broadcastPush("gallery.slider_updated", …)` and no client ever registers a listener for it.
    Fix the example; don't let a shipped example model a dead pattern.

### 2.5 The silent-failure catalogue — the highest-value page in the pack

This is my strongest recommendation in the whole plan. The framework's characteristic failure is not
an exception, it is **nothing happening**, and every one of these is verified in the source. A
developer who hits one has no error message to search for, which is exactly the experience that
makes people conclude a framework "doesn't work". Give them one page, titled so they can find it,
with symptom-first entries:

| Symptom: nothing happens | Cause | Fix |
|---|---|---|
| `syncEngine.notifyChanged(obj)` does nothing | The object was never serialized to a client, so it has no `ObjectMapper` handle. The method returns early and silently (`SyncEngine.java:114-117`). | Return the object from an RMI call at least once first — that is what registers the handle. |
| A client mutation is ignored | Class lacks `@ClientWritable`, or the role check or validation failed. The reason is **logged on the server and never sent to the client**; the writer just gets snapped back. | Check server logs; add `@ClientWritable("role")`. |
| `events.publish(...)` reaches nobody | Payload isn't wire-serializable. The exception is caught per session and logged as `[zeroz4j] Push error for session …` (`WasmRmiServerEngine.java:451`). | Annotate the payload `@DataModel`. |
| A shared signal never propagates | Same — serialization failure is caught and printed to stderr in both transports. | As above. |
| A `_Live` setter isn't tracked | The field has no setter. APT emits a **warning, not an error**, and skips it (`RmiAnnotationProcessor.java:470-475`). | Add the setter; read your build warnings. |
| `@ClientWritable` without `@LiveSync` | Mutations go up but nothing comes back down. APT warns only (`:447-452`). | Add `@LiveSync`. |
| Two `Signals.shared(x)` calls return the same signal | The default wire name is the payload's class name, so there is one default shared signal per type. Re-declaration is idempotent and **silently ignores** the second initial value, writability and roles (`Signals.java:148-151`). | Use `Signals.shared("explicit.name", …)`. |
| A LiveSync'd object updates but the UI doesn't | There is no change callback. Nothing notifies the view. | Poll into a signal (as `chat-livesync` does), or bridge via an event. |
| `Effect` doesn't re-run | The value was mutated in place; `equals` dedup swallowed it. | `update()` with a new instance. |
| `bindValue` doesn't write back | Two-way binding only engages for a `ValueSignal`; a `Computed` silently degrades to read-only (`AbstractField.java:76-96`). | Bind a `ValueSignal`. |
| A binding leaks | `bindText`, `bindValue` and `KeyedList` create effects and **discard the `Disposable`** — those bindings cannot be released and live as long as the upstream signal. | Known limitation; document it, and consider fixing the API. |

Note the last two are arguably API bugs surfaced by writing the docs. That is the rewrite doing its
job — documentation that cannot be written honestly is a design review in disguise.

### 2.6 Placement

- `decide/index.md` holds the tree in 2.2 and nothing else — it must fit on one screen.
- Every one of `guides/server-events.md`, `guides/signals-*.md`, `guides/livesync-objects.md` opens
  with the same one-line "use this when…, not this when…" callout and links to `decide/`.
- `reference/glossary.md` defines Event / Signal / Push / Message / Sync / Mutation exactly once.
  SERVER_EVENTS.md's terminology table is the seed — it is the best thing in the current docs and it
  is currently buried on page 1 of a mid-tier file.

---

## Part 3 — Beginner-friendliness: what has to be added

The current pack assumes a reader who already understands the architecture. Concretely missing and
required for "beginner friendly":

1. **Prerequisites with exact versions.** JDK 21 (CI uses Temurin 21; `<release>21</release>`),
   Maven 3.9+, a WasmGC-capable browser and how to check, expected first-build time and disk use.
2. **A quickstart that works verbatim.** Current one cannot: it names archetype version
   `1.0.0-SNAPSHOT` while the project is at `0.3.0`, and it says `java -jar …-server.jar` while the
   examples launch `java -cp "target/classes;target/libs/*" <MainClass>`. Both commands must be
   copied from something CI executes, and given for Windows *and* POSIX (the repo currently ships
   only `run.bat`). See 6.2 — this requires code fixes first.
3. **The login step.** Every example requires signing in with `demo`/`demo`, and **no doc in the
   repo says so**. A beginner runs the app, sees a login screen, and is stuck at minute three. This
   single omission probably accounts for more abandonment than anything else in the pack.
4. **A "what you should see" checkpoint** after every step, with the actual expected console line
   and screenshot. Beginners cannot distinguish "still compiling" from "silently broken".
5. **Troubleshooting table.** Blank page / Wasm not loading / `NoSuchMethodError` from a stale APT
   run / WebSocket 404 on `/wasm-rmi` / mutation silently ignored (missing `@ClientWritable`) /
   nothing re-renders (in-place mutation) / build OOM. Symptom → cause → fix.
6. **A mental-model page before any code.** One diagram: browser Wasm heap ⇄ binary WebSocket ⇄
   CDI beans ⇄ object graph on disk. Beginners need the shape before the syntax.
7. **Honest expectation-setting.** `explain/project-status.md` says plainly: experimental PoC,
   broadcast-only, no per-session filtering, no field-level merge, tracked collections not
   implemented. Trust is a beginner feature — surprises are what make people quit.
8. **A glossary**, because this framework overloads words that mean other things elsewhere
   (Signal ≠ Vaadin Signal, RMI ≠ java.rmi, LiveSync ≠ CRDT).
9. **How to style a component.** `new Button("Submit")` is the only styling example in 210 lines of
   `UI_COMPONENTS.md`; the variant mixins that actually make it primary/large/outline are
   undocumented. A beginner cannot produce a non-default-looking UI from the docs today.
10. **A multi-view / navigation story.** `@Route` exists but its router is unimplemented, so there is
    *no* documented way to build an app with more than one screen. Either document the manual
    pattern the examples use, or implement the router — but the docs must stop being silent.
11. **A testing guide.** `CONTRIBUTING.md` says "add tests" with no example of testing a
    `@DataModel`, an RMI service, or a signal — and `Signals.resetForTesting()` exists but is
    undocumented.
12. **`SECURITY.md` and a security-model page.** That client-side validation is cosmetic and the
    server is the only real gate is stated only in passing, in two places. It deserves a page.
13. **`CHANGELOG.md` and a stated version policy** — that this is `0.3.0`, pre-1.0, and what that
    implies for API stability.

---

## Part 4 — Context7 optimisation

Context7 crawls the **GitHub repo**, parses Markdown/RST/notebooks, extracts code snippets, embeds
them, and answers queries with reranked snippets. It de-duplicates near-identical snippets and
prefers a single current version. Optimising for it means: *make each page answer one question, with
one canonical, self-contained snippet.*

### 4.1 `context7.json` at the repo root

```json
{
  "$schema": "https://context7.com/schema/context7.json",
  "projectTitle": "ZeroZ4j",
  "description": "Pure-Java full-stack framework: Java UI compiled to WebAssembly via TeaVM, binary RPC over WebSocket, and EclipseStore object-graph persistence. No JavaScript, JSON, REST or ORM.",
  "folders": ["docs/**", "zerozstack-examples/**"],
  "excludeFolders": ["target", ".idea", ".github", "zerozstack-archetype/target"],
  "excludeFiles": ["CHANGELOG.md", "CODE_OF_CONDUCT.md", "LICENSE", "NOTICE"],
  "rules": [
    "Requires JDK 21 or later. Client code compiles to WebAssembly with TeaVM; only TeaVM-supported JDK APIs are available on the client.",
    "Every type crossing the wire must be annotated @DataModel and have a public no-arg constructor plus getters and setters.",
    "Declare RMI contracts as @RmiService interfaces in the shared module; implement them as @ApplicationScoped CDI beans in the server module.",
    "Choose the propagation mechanism deliberately: local ValueSignal for client-only state, RMI for named operations and request/response, EventTopic for discrete occurrences with no retained value, Signals.shared for one current value that late joiners must see, @LiveSync for an identified object edited field by field.",
    "Never carry state in server events: events have no retained value and no replay, so a client connecting later sees nothing. Use Signals.shared instead.",
    "Never mutate a signal's value in place. ValueSignal.set skips notification when the new value equals the old one; use update() and return a new instance.",
    "Client writes are denied by default. Opt in explicitly with Signals.sharedWritable or @ClientWritable; the server stays authoritative and re-validates every mutation.",
    "Model operations such as approve, checkout or delete as RMI methods, not as LiveSync field edits. State edits sync, operations call.",
    "Dispose what you create: Effect.create and ServerEvents.on return a Disposable that a view must release when it is removed.",
    "Events and shared signals broadcast to every connected session with no principal or tenant filter, so never push per-user or sensitive data through them. Only LiveSync can scope a push, via SyncEngine.notifyChanged(obj, SyncScope.SESSION or USER, target).",
    "SyncEngine.notifyChanged does nothing unless the object has already been serialized to a client, so return it from an RMI call at least once before relying on sync.",
    "LiveSync updates an object's fields in place but does not notify the view. Copy the object into a signal on a timer, or bridge via an event, to trigger re-rendering.",
    "Client-side UI code runs on a cooperative scheduler. Never create a java.lang.Thread in client code; RMI calls suspend and resume on their own.",
    "Do not mutate a shared or live object in place through a collection getter. Setters are the tracking boundary; reassign via the setter or call LiveMutationTracker.touch(obj).",
    "ZeroZ4j is an experimental proof-of-concept, currently version 0.3.0."
  ]
}
```

Notes:
- `excludeFolders` beats `folders`, so `target` exclusion is safe and necessary — otherwise
  generated sources and `target/classes/archetype-resources` duplicates get indexed.
- Including `zerozstack-examples/**` is deliberate: the example READMEs plus real `.java` files give
  Context7 grounded, compiling snippets. This is where Context7's "generate examples from source"
  behaviour helps rather than hurts.
- `rules` is the highest-value field and is almost never used well. It is our chance to inject the
  Events/Signals/LiveSync decision procedure directly into every agent that touches the library —
  which is precisely the problem the user reports seeing in downstream projects. `agents/rules.md`
  is the human-readable source of truth; this array is its compressed projection, and they must be
  kept in sync (add a CI check).

### 4.2 Page shape rules that make snippets retrievable

Enforced by the style guide (Part 5):

- **One question per page**, stated in the `<h1>` as a noun phrase a developer would type
  ("Broadcasting server events", not "Events, deeply").
- **A `## When to use this` section as the first section** of every guide, ~3 lines. This is what
  Context7 returns for "should I use X or Y" queries.
- **Self-contained snippets.** Every fenced block includes its imports or a comment naming the type,
  and does not depend on a variable defined three blocks earlier. Context7 returns snippets *without
  their page*, so a snippet that only makes sense in context is a snippet that produces broken
  generated code.
- **Always tag the language** (` ```java `, ` ```xml `, ` ```bash `). Untagged fences extract badly.
- **Prefer one good snippet to three variations.** Near-duplicates get de-duplicated anyway, and
  variations dilute which one is canonical.
- **Correct-by-default snippets.** Never show the wrong version first without an inline comment on
  the wrong line itself — a `// WRONG` block extracted out of context becomes advice. Current
  SIGNALS.md does this correctly (`// WRONG —` inside the block); make it a rule.
- **Stable headings**, because they anchor retrieval; avoid renaming them casually.
- **No unlabelled tables of nuance** — put the nuance in prose near the snippet, since tables survive
  extraction poorly.

### 4.3 `llms.txt`

Add `/llms.txt` (llmstxt.org convention) at the repo root and served at `docs.zeroz4j.com/llms.txt`:
an H1, a blockquote summary, then curated link lists (Docs / Guides / Decision guides / Reference /
Examples) with a one-line description each. This serves agents that don't use Context7, and doubles
as a machine-readable table of contents. Generate it from `mkdocs.yml` nav in CI so it cannot drift.

### 4.4 `AGENTS.md`

Root `AGENTS.md` (the emerging cross-tool convention, read by Claude Code, Codex, Cursor and others):
prerequisites, build/test commands, module map, the decision procedure in ~15 lines, and the
anti-pattern list. Keep it under 200 lines. `CLAUDE.md` becomes a one-line pointer to it.

### 4.5 Operational

- Claim the library at context7.com to unlock `context7.json` control and higher refresh limits.
- Add the Context7 GitHub Action to `main` pushes so docs reindex on merge.
- Set `"branch": "main"` explicitly if docs ever live elsewhere.
- After the first index, spot-check with real queries: *"zeroz4j when to use events vs signals"*,
  *"zeroz4j @ClientWritable example"*, *"zeroz4j quickstart"* — and iterate on the pages that answer
  badly. Treat retrieval quality as a testable output, not a hope.

---

## Part 5 — Style guide (`docs/contribute/docs-style-guide.md`)

Short, enforceable rules; this is what keeps the pack coherent as it grows.

1. **Second person, present tense, active voice.** "You declare the topic once." Not "The topic
   should be declared".
2. **Instructions have no thesis.** Architectural justification lives only in `explain/`. A how-to
   page never explains *why the industry is wrong*.
3. **Every page opens with one sentence naming its audience and outcome**, then the content.
4. **Limits are stated where the feature is taught**, not in a distant caveats file. The current
   docs' "stated plainly" sections are genuinely good practice — keep the habit, and keep the
   phrase.
5. **No superlatives.** Ban "incredible", "ultra-fast", "blazing", "radically". Give a number or say
   nothing. (Current docs contain "ultra-fast binary serializer" and "incredible performance" with
   no benchmark behind either — that costs credibility with exactly the senior audience this project
   targets.)
6. **No forward promises without a status label.** "Tracked collections are planned" is fine; a
   feature described in present tense that doesn't exist is not.
7. **Terminology is fixed by the glossary.** Event/Signal/Push/Message/Sync/Mutation never drift.
8. **Cross-link on first mention** of any other construct, always to the same canonical page.
9. **British/American spelling: pick American**, consistent with the code identifiers.
10. **Line length 100**, one sentence per line where it eases review diffs.

---

## Part 6 — Audit findings and migration map

### 6.1 The audit's headline: two voices, and the good one is the minority

`LIVESYNC.md`, `SIGNALS.md`, `SERVER_EVENTS.md`, `VALIDATION.md` (all written 23 Jul) are in a
plain, limits-first engineering voice, with explicit "stated plainly" sections — and they are the
only docs whose API claims all verify against the code. `README.md`, `CONCEPTS.md`,
`ARCHITECTURE.md`, `UI_COMPONENTS.md` carry a marketing register **and** hold essentially every
stale claim. That correlation is the whole thesis of this rewrite: **the voice that admits limits is
the voice that stays true.** Propagate the first four files' register; regenerate the other four.

### 6.2 Blocking prerequisite: the build/run story is broken in *code*

The quickstart cannot be written until these are fixed, because there is currently no correct
command to document:

| # | Problem | Fix |
|---|---|---|
| B1 | `zerozstack-archetype/src/main/resources/archetype-resources/pom.xml:11` pins `<zeroz4j.version>1.0.0-SNAPSHOT</zeroz4j.version>` while the project is `0.3.0` — **a generated project does not build** | Filter the version from the archetype build, or set it to `${project.version}` |
| B2 | No `exec-maven-plugin` anywhere, yet 4 example READMEs say `mvn exec:java` | Add and configure `exec-maven-plugin` in the examples parent, or drop the instruction |
| B3 | No shade/assembly plugin, yet `GETTING_STARTED.md:38` and `inventory-crud/README.md:54` say `java -jar …-server.jar` | Add `maven-shade-plugin` with `mainClass` (best — it makes the documented command true), or standardise on the classpath launch |
| B4 | Only `run.bat` exists; no POSIX equivalent | Add `run.sh` per example (or one `run` script at the examples root taking the example name) |
| B5 | `CONTRIBUTING.md:21` says `mvn clean test`, which cannot work — APT/shared artifacts must be installed first | `mvn install -DskipTests` then `mvn test` |

Recommendation: do B1–B4 as one small PR *before* Phase 2, then document what CI actually runs. Add a
CI job that runs the quickstart end to end on ubuntu and windows, so it can never rot again.

### 6.3 Undocumented features found in the code (the real gap list)

These are not "nice to have" — several are things a beginner hits in the first hour.

| Feature | Where | Doc home in the new IA |
|---|---|---|
| **Login / auth story** — `ui/component/Login.java`, `server-core/DevAuth.java`, `RmiSecurityContext.onAuthenticated/getUsername/hasAnyRole`, and how to replace dev auth in production | every example requires `demo/demo` login and **no doc says so** | `guides/security-auth.md` (new, high priority) |
| **Variant / styling mixins** — `HasColorVariants`, `HasSizeVariants`, `HasOutlineVariant`, `HasPositionVariant`, `ThemeColor`, `ThemeSize` | i.e. **how to make a Button primary/large/outline** — undocumented, while the README sells DaisyUI styling | `guides/ui-building-views.md` + `reference/ui-components.md` |
| **Persistence & multi-tenancy SPI** — `store/DataRootProvider`, `store/TenantResolver`, the `DataRoot` pattern | README:39 claims multi-tenancy "out-of-the-box"; described only in a parenthetical in an agent prompt | `guides/persistence-eclipsestore.md` (new) |
| **`@Route`** | exists, but `RouteRegistry`/router is **not implemented** and it has zero usages | `reference/limitations.md` as "declared, not implemented"; and the framework needs a documented multi-view story either way |
| **`@RequiresRole`** | zero docs, zero usages; overlaps `@RolesAllowed` with no stated difference | `reference/annotations.md` — document the difference or deprecate one |
| **`LiveMutex` / `LiveMutexProvider`** | one clause in `LIVESYNC.md:53`, pointing at a non-existent example | `guides/livesync-objects.md` |
| **`BinaryPackable`, `BinaryRegistrar`, `BinaryRegistry`, `BinarySerializerDelegate`** — the custom wire-type seam | undocumented | `reference/supported-types.md` + an advanced guide |
| **`Signals.resetForTesting()`** | `Signals.java:194`, undocumented; `SIGNALS.md:74` alludes to test behaviour without naming it | `guides/testing.md` (new) |
| **`components-showcase` example** | registered in the pom, has `run.bat`, is the only exercise of the ~90-class component library, has **no README and is mentioned in no doc** | `docs/examples/index.md` + linked from `reference/ui-components.md` as the live gallery |
| **`Binder` vs `bindValue`/`withRule`** | two competing, mutually-unaware binding stories, both real in code; every example uses the latter, `Binder` appears in no example | **Pick one canonical path** (recommend `bindValue`/`withRule`), document the other as advanced/legacy in `guides/ui-forms-and-validation.md`. This is an API decision the docs cannot paper over. |

### 6.4 Contradictions to resolve by deciding, not by rewording

The rewrite must settle these; they are currently ambiguous *in the docs because they are ambiguous
in the project*:

1. **Client bootstrap:** `Zeroz4jClient.connect(url, onReady)` (correct, used by all 7 examples) vs
   the obsolete `BinaryPackableRegistrar.registerAll()` + `WasmRmiClientChannel` +
   `WasmRmiClient.initialize()` path in `CODE_WALKTHROUGH.md:82-105`. → Document only `connect`.
   Note that APT now emits `BinaryPackableRegistrar_<suffix>` per module (commit `e925bab`),
   auto-discovered via `META-INF/services`, so the manual call is not just obsolete but wrong.
2. **RMI stub acquisition:** `new UserService_Stub()` (correct) vs
   `WasmRmiClient.create(UserService.class)` (**does not exist**, `ARCHITECTURE.md:88,96`).
3. **Threading vocabulary:** "TeaVM virtual thread" / "`@Async` continuation coroutines" /
   "suspendable green threads (TeaVM coroutines)" — three names for one mechanism across three docs.
   → Pick one term, put it in the glossary, use it everywhere. And fix
   `AGENT_PROMPTS.md:437` which tells agents to `new Thread(...)` in direct contradiction of
   `AGENT_PROMPTS.md:91-93` and of commit `607f430`, which purged exactly that anti-pattern. **This
   is very likely a direct cause of the downstream-project problems the user has observed** — a
   widely-copied prompt file that instructs agents to do the thing the framework forbids.
4. **Which binding API is canonical** (see 6.3).
5. **Frame/opcode names:** docs use `SUCCESS`/`PUSH`/`ERROR` and `RMI_RESPONSE`/`RMI_ERROR`; code
   uses `RPC_RESPONSE`/`RPC_PUSH`/`RPC_ERROR` plus a *distinct* `PUSH = 0x18`. → Generate the opcode
   and type-tag tables from `SyncFrameTypes` and `BinarySerializer` rather than hand-maintaining
   them; both tables are currently a release behind (`PROTOCOL.md` type tags stop at `0x0D`, missing
   `TAG_REF 0x0E`, `TAG_UUID 0x0F`, `TAG_INSTANT 0x10`, `TAG_ENUM 0x11` from commit `0c90590`).
6. **Dangling example references:** `collab-editor`, `secure-admin`, `metrics-live`, `zeroboard` are
   cited as if they exist (`LIVESYNC.md:53`, `AGENT_PROMPTS.md`). → Either build them or stop citing
   them. Recommend: build `collab-editor`, since it is the one that demonstrates `LiveMutex` and
   concurrent-editor semantics — the hardest thing in the framework to get right.

### 6.5 File-by-file migration map

| Current file | Fate | Destination |
|---|---|---|
| `README.md` | **Rewrite as router** (~120 lines) | thesis prose → `explain/why-zero-impedance.md`; author/consulting → bottom, compressed; Maven consumption section → `start/prerequisites.md`, using `zerozstack-bom` (done) |
| `CONTRIBUTING.md` | Expand, fix `mvn clean test` → install-first | stays at root; deep content → `contribute/` |
| `docs/CONCEPTS.md` | **Delete.** Its job splits cleanly | glossary → `reference/glossary.md`; orientation → `start/index.md` + `explain/architecture.md`. (It is also mistitled: "10 Core Concepts" with 11 sections.) |
| `docs/GETTING_STARTED.md` | **Delete and rewrite from scratch** — 6 of its ~8 factual claims are wrong (archetype version, `java -jar`, `com.zeroz4j.ui.components.*` packages, `onClick`) | `start/prerequisites.md` + `start/quickstart.md` + `start/first-feature.md` |
| `docs/CODE_WALKTHROUGH.md` | **Delete.** Steps 1–3 are sound but redundant with the new tutorial; step 4 is wholesale obsolete | `start/first-feature.md` |
| `docs/ARCHITECTURE.md` | **Split and largely rewrite** — the most stale file | rationale → `explain/why-zero-impedance.md`; pipeline → `explain/how-rmi-works.md`; frame diagrams → **deleted** in favour of `reference/wire-protocol.md`; Loom → `explain/threading-model.md` |
| `docs/PROTOCOL.md` | **Keep, tighten, generate the tables** | `reference/wire-protocol.md` |
| `docs/SIGNALS.md` | **Keep — best-in-class.** Split by scope | local → `guides/signals-local-state.md`; shared → `guides/signals-shared-state.md`; "when to use" → `decide/` |
| `docs/SERVER_EVENTS.md` | **Keep.** Terminology table is the single best asset in the docs | body → `guides/server-events.md`; terminology → `reference/glossary.md`; decision content → `decide/` |
| `docs/LIVESYNC.md` | **Keep — best-written file.** Its closing decision table is the seed of `decide/index.md` | `guides/livesync-objects.md` + `decide/` |
| `docs/VALIDATION.md` | **Keep**, merge with the binding story | `guides/ui-forms-and-validation.md` + `reference/annotations.md` |
| `docs/UI_COMPONENTS.md` | **Split; catalogue regenerated from source.** Currently a flat 87-name list with no signatures, constructors, or imports — unusable as reference, and the "87" is wrong (90 classes in `ui/component/` alone) | how-to → `guides/ui-building-views.md`; catalogue → **generated** `reference/ui-components.md`; drop the hard count, or generate it |
| `docs/AGENT_PROMPTS.md` | **Split.** Its "Framework rules" (`:48-104`) is the most current API cheat-sheet in the repo — promote it | rules → `agents/rules.md` (→ `context7.json` `rules`); prompts → `agents/prompts.md`, with references to non-existent examples removed and the `new Thread` contradiction fixed; DX harness → `contribute/` |
| 6 example READMEs | Normalise to one template; fix run commands | `chat-events/README.md` is the model to copy |
| `components-showcase` | **Write a README** | + `docs/examples/index.md` |

### 6.6 Javadoc and protocol-constant fixes (docs work that lives in `.java` files)

Javadoc is documentation, and Context7 indexes source. These are wrong in the source today:

- `LiveSync.java:36` — "Uses WebSocket opcode 0x02 (PUSH) / 0x11 (SNAPSHOT)". Actual: `0x10`;
  `0x11` is never used.
- `ServerSignalTransport.java:34` and `ClientSignalTransport.java:36` — "0x05 SIGNAL_UPDATE frames".
  Actual: `0x17`. (`0x05` is `TAG_BOOLEAN`.) `docs/PROTOCOL.md:69` has it right; the source doesn't.
- `SyncFrameTypes.java:63` documents `0x10` as *client→server subscribe, payload = class FQCN*; the
  implemented direction is server→client with a serialized object. The constant's **name** is
  actively misleading — consider renaming it `SYNC_UPDATE` while it is still cheap.

**Dead protocol constants** — declared and referenced nowhere: `0x11 SNAPSHOT`, `0x12 UNSUBSCRIBE`,
`0x13 MUTATE`, `0x14 ACK`, `0x15 REJECT`, `0x16 SIGNAL_SUB`, `0x18 PUSH`. `docs/PROTOCOL.md:52-64`
describes versioned `MUTATE`/`ACK`/`REJECT` payloads **that do not exist in the implementation**.
The reference page must mark these "reserved, not implemented" rather than describing them in the
present tense — this is precisely the kind of claim that misleads an agent into generating code
against a protocol that isn't there.

Note on Maven consumption: the path is the `zerozstack-bom`
(`pom.xml:62`, used by `archetype-resources/pom.xml:26-28`). The README was corrected in
0.4.0; JitPack is no longer offered, since the family publishes to Maven Central.

---

## Part 7 — Execution plan

Sequenced so the pack is usable after every phase, and so the highest-value/lowest-risk work lands
first. Phases 0–2 are the ones that change the downstream-project problem the user described.

### Phase 0 — Foundations (no prose yet)
- `context7.json`, `AGENTS.md`, `llms.txt`, `mkdocs.yml`, Pages workflow, CNAME.
- `docs/contribute/docs-style-guide.md`.
- Verify: the site builds and deploys; Context7 reindexes; nav renders.
- *Ship immediately — this alone improves agent behaviour before a single page is rewritten.*

### Phase 1 — The decision pack (`decide/`) + glossary
The highest-value content, and independent of everything else. Write `decide/index.md`,
`events-vs-signals-vs-livesync.md`, `antipatterns.md`, `reference/glossary.md`, and add the "When to
use this" callout to the three existing feature docs in place.

### Phase 2 — The path in (`start/`)
`prerequisites`, `quickstart`, `first-feature`, `making-it-live`. Every command executed on a clean
machine (and in CI) before merge. Rewrite the README as a router at the end of this phase.

### Phase 3 — Reference
`annotations`, `api-*`, `ui-components`, `supported-types`, `configuration`, `limitations`,
`wire-protocol`. Mostly reorganisation plus gap-filling against the actual source; the tedious but
mechanical phase. Generate the component catalogue from source where possible.

### Phase 4 — How-to guides
Convert the existing SIGNALS/SERVER_EVENTS/LIVESYNC/VALIDATION/UI_COMPONENTS bodies into task-shaped
guides, add the missing ones (testing, deploying, persistence, security, troubleshooting).

### Phase 5 — Explanation + agents
Move the thesis out of the README; write `architecture`, `how-*-works`, `threading-model`,
`comparison-vaadin-hilla`, `project-status`. Restructure `AGENT_PROMPTS.md` into `agents/`.

### Phase 6 — Harden
Snippet-include conversion for tutorials, `docs-samples` compile test, link checker in CI,
`rules`↔`agents/rules.md` sync check, `llms.txt` generation, Context7 retrieval spot-checks.

### Phase 7 — Examples
Bring all seven example READMEs to one template: what it demonstrates, which mechanism and *why that
one*, run command (both OSes), and links to the relevant guide + decision page. Add
`docs/examples/index.md` as the index. `chat-livesync` (17 lines) and `job-monitor` (28) are the
weakest and matter most, since they demonstrate the two mechanisms people confuse.

---

## Part 8 — Definition of done

- A developer who has never seen the project has a running app in under 15 minutes, following one
  page, with no guesswork and no command that fails.
- Any developer can answer "Events, Signals or LiveSync?" for a new feature in under a minute, from
  one page, and can name the anti-pattern they avoided.
- No command, coordinate, version number or signature in the docs is unverified against the code.
- Context7 returns the decision procedure for "when to use events vs signals in zeroz4j", and a
  correct compiling skeleton for "zeroz4j rmi service example".
- Every documented limitation is findable in one place, and nothing is described in present tense
  that does not exist.
