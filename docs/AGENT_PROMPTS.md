# Agent Prompts: Build a ZeroZ Stack App

Ready-to-paste prompts for building ZeroZ Stack example applications with an AI coding agent. They serve three purposes:

1. **For users** — off-the-shelf starting points: paste a prompt into your agent, get a working app, then adapt it.
2. **For the project** — a growing set of showcase examples, buildable by any capable agent.
3. **As a DX test suite** — if a competent agent fails or goes off the rails with one of these prompts, that is a framework or documentation bug. Please open an issue with the transcript.

## How to use

1. Clone the repository and run `mvn clean install -DskipTests` once from the root, so the agent starts from a green build. Include `clean`: each example server's `target/libs` is never pruned, so stale jars otherwise accumulate and produce duplicate-bean warnings at startup.
2. Paste the **Common Context** block below into your agent, followed by exactly one **Task** block.
3. When the agent finishes, run the acceptance checks at the end of the task block yourself.

Prompts are ordered roughly by difficulty. Each is independent.

---

## Common Context (paste this first, before any task)

```text
You are implementing a new example application for ZeroZ Stack, a zero-impedance Java
web framework: one language (Java 21) end-to-end — the client UI compiles to Wasm/JS
via TeaVM, the server runs on Helidon with CDI, and the SAME POJOs travel from
EclipseStore persistence through RMI to the browser. No REST, no JSON, no DTOs,
no JavaScript, no SQL.

Before writing any code, read these files in the repository:
- docs/decide/index.md        (WHICH construct to use — read this first)
- docs/decide/antipatterns.md (named failure modes to avoid)
- docs/SIGNALS.md             (reactive state: local and shared)
- docs/SERVER_EVENTS.md       (typed server-to-client events)
- docs/VALIDATION.md          (model annotations enforced on both tiers)
- docs/guides/troubleshooting.md (this framework fails silently — know how)
- the README.md and full source of the REFERENCE EXAMPLE named in the task

Method — follow it exactly:
1. Copy the reference example's directory tree under zerozstack-examples/ to the new
   example name given in the task (copy the pom.xml files as-is, then rename the
   artifactIds and module names consistently). Do NOT write poms from scratch.
2. Register the new module in zerozstack-examples/pom.xml.
3. Keep the copied structure: <example>-shared (models + @RmiService interfaces),
   <example>-client (Wasm UI), <example>-server (Helidon server, EclipseStore
   boilerplate under .../server/store/).
4. Replace the domain code with the task's spec. Package conventions:
   com.zeroz4j.example.model / .api / .client / .server
5. Update the <title> in both index.html files (client resources and server webapp).
6. Write a README.md for the example: what it demonstrates, how to run it.

Framework rules (violating these produces silent runtime failures):
- Wire models: annotate @DataModel (one annotation, no interface), provide a public
  no-arg constructor plus getters/setters for every field. Add value-based
  equals/hashCode when the client needs to deduplicate instances.
- Services: @RmiService interface in the shared module; the APT generates a
  <Name>_Stub the client instantiates directly (new MyService_Stub()).
- Signals (client only): NEVER mutate a signal's value in place and set it back —
  ValueSignal.set() deduplicates by equals. Always build a new object/list in
  update(...). Collect every Disposable from Effect.create / ServerEvents.on and
  release them in a dispose() method; call dispose() on Computed instances too.
- Server events: declare EventTopic constants once in the shared module (explicit
  wire names like "job.statusChanged"); inject EventPublisher (never the transport
  engine) on the server; subscribe with ServerEvents.on BEFORE fetching initial
  state over RMI, then merge (events are at-most-once, no replay).
- Shared signals: for broadcast STATE (not occurrences), declare
  Signals.shared(initialValue) once as a constant in the shared module — the
  constant IS the signal (wire name defaults to the payload class; pass an
  explicit name only for multiple signals of one type). The server set()s it
  with a NEW immutable instance each time; client mirrors update automatically
  and late joiners receive the retained value. Shared signals are
  server-authoritative: a client-side set() throws, unless declared with
  Signals.sharedWritable(...) — then client writes are optimistic and the
  server accepts (roles + validation annotations) or rejects with a
  corrective update.
- Validation: declare field constraints (@NotBlank, @Size, @Min, @Max from
  com.zeroz4j.api.validation) on @DataModel fields. The APT generates
  <Model>_Rules; attach rules to UI fields with field.withRule(...) for live
  feedback, and rely on the server enforcing the same annotations on every
  RMI argument automatically — never re-implement per-field checks by hand.
- Broadcast scope: events.publish(...) and a Signals.shared set() reach EVERY
  connected session with no principal check. Scope anything belonging to somebody:
  events.publishToUser(topic, payload, principalName), publishToSession(...) or
  publishToClient(...); syncEngine.notifyChanged(obj, Scope.SESSION or
  Scope.USER, target); and for a signal, declare it with
  Signals.scoped(name, initial, scope) rather than Signals.shared — the server
  writes a target with forTarget(...), the client reads its own with mine().
  Take the target from RmiRequestContext, never from a method argument.
  Scope.CLIENT works with no login at all: it identifies a browser, not a person.
- LiveSync: annotate the state class @LiveSync, mutate it on the server, then call
  syncEngine.notifyChanged(state) — the client's instance updates in place.
  notifyChanged silently does NOTHING unless the object was already serialized to
  the client, so return it from an RMI method once first. LiveSync also does NOT
  notify the view that anything changed: copy the object into a signal on a timer
  (see chat-livesync) or announce the change with an event. For
  client-side edits, additionally annotate @ClientWritable (optionally with write
  roles): client setter calls then propagate automatically — the server
  authorizes, validates (model annotations), applies, and re-broadcasts, or
  reverts the writer on rejection. Persist accepted mutations in a
  LiveMutationListener bean. Mutations must go through setters; after in-place
  collection edits call LiveMutationTracker.touch(obj). Concurrent unlocked
  writes are last-write-wins — serialize editors with LiveMutex where needed.
  Read docs/LIVESYNC.md before using this.
- Persistence: the EclipseStore DataRoot pattern from the reference example
  (DataRoot + DefaultDataRootProvider + DefaultTenantResolver in .../server/store/).
  After mutating a collection, call storage.store(<the collection>). If the change also
  touched the owning object (e.g. an id counter), write both in ONE call:
  storage.storeAll(<the collection>, <the owner>) - two store() calls are two commits.
- Call RMI services directly from UI handlers and view code — the framework
  runs them on suspendable green threads (TeaVM coroutines). NEVER spawn
  new Thread(...): it is redundant, and signal or DOM updates made from a
  background thread do not repaint until the next UI event. For delayed work
  use Window.setTimeout.

Hard constraints:
- Do NOT modify any framework module (zeroz4j-* directories) — examples only.
- Do NOT add external dependencies.
- Prefer UI components that exist in
  zerozstack-ui-components/src/main/java/com/zeroz4j/ui/component/ — check before use.
  If no library component fits (e.g. you need a differently-typed input), a small
  app-local component extending AbstractField<C, T> is acceptable — note it in the
  README as a library gap.
- If the spec leaves a choice open, make a sensible one and record it in the README.

Definition of done: `mvn install` from the repository root succeeds (including the
TeaVM compile of your client), and the acceptance criteria in the task are met.
```

---

## Task 1 — `form-signup`: forms with reactive validation

Feature focus: form components, `Computed` validation, two-way `bindValue`. Reference: `todo-signals`.

```text
TASK: Build the example "form-signup" under zerozstack-examples/form-signup.
Reference example to copy: zerozstack-examples/todo-signals.

A signup form for a fictional developer conference:
- Shared model Registration (@DataModel) carrying the form data, with the
  rules declared as validation annotations ON THE MODEL:
  * fullName: @NotBlank @Size(min = 2, max = 60)
  * email: @NotBlank @Size(min = 5, max = 120)
  * experienceYears (int): @Min(0) @Max(50)
  * bio: @Size(max = 400) (optional)
- Fields: full name (TextField), email (TextField), years of Java experience
  (NOTE: the library Select is String-typed and Range is Double-typed — either
  parse a String Select, adapt Range, or write a small Integer-typed
  AbstractField subclass; state your choice in the README), T-shirt size
  (RadioButtonGroup or Select: S/M/L/XL), "subscribe to newsletter" (Toggle or
  Checkbox), short bio (TextArea).
- Bind every field to a ValueSignal via bindValue(...) AND attach the generated
  rules via withRule(Registration_Rules.<field>()). Lay the form out with
  FormLayout or VerticalLayout. Show each field's getViolations() in a small
  Div under it, driven by an Effect. KNOWN LIMITATION: isValid()/getViolations()
  are not reactive — read the field's bound signal in the same Effect/Computed
  so it re-runs on changes (the signal updates after validation, so violations
  read fresh).
- A Computed<Boolean> formValid combining the fields' isValid() plus any
  cross-field logic (read every bound signal inside the computation for
  tracking, per the note above; dispose this Computed in dispose()); the Submit
  button is disabled while formValid is false — driven by an Effect, never set
  manually in handlers.
- @RmiService RegistrationService { void register(Registration r);
  List<Registration> listRegistrations(); } — the server appends to the
  EclipseStore DataRoot and persists. Do NOT hand-write per-field checks in
  the service: the engine already enforces the model annotations on every
  argument. Add only genuinely business-level checks (e.g. duplicate email).
- After a successful submit: show a Toast or Alert, clear the form signals, and
  render the (refetched) registration list under the form in a Table. KNOWN
  LIMITATION: fields the user touched will re-show required-field errors after
  the clear (there is no touched-state reset yet) — mention this in the README
  rather than working around it.

ACCEPTANCE:
- mvn install passes from the root.
- Typing an invalid email shows its violation and keeps Submit disabled; fixing
  it enables Submit with no explicit "recheck" code path.
- The SAME rules hold server-side: a register() call carrying an invalid
  Registration (e.g. crafted past the UI) is rejected by the engine before the
  service method runs — demonstrate this in the README with one sentence on
  how you verified it.
- Submitted registrations survive a server restart (EclipseStore).
```

---

## Task 2 — `job-monitor`: a shared signal fed by a long-running job

Feature focus: a backend service writing a **shared signal** from a fake external source; every client's UI updates automatically, including late joiners. Reference: `chat-events` (structure) + `todo-signals` (signals usage).

```text
TASK: Build the example "job-monitor" under zerozstack-examples/job-monitor.
Reference example to copy: zerozstack-examples/chat-events.

Simulate monitoring a long-running deployment pipeline:
- Shared model JobStatus (@DataModel): jobId (long), phase (String: one of
  "Queued", "Building", "Testing", "Deploying", "Done", "Failed"), percent (int
  0–100), message (String), running (boolean). Value-based equals/hashCode and
  a static JobStatus idle() factory.
- Shared signal declaration (this replaces any event topic — there are NO
  EventTopics in this example):
      public final class JobSignals {
          public static final ValueSignal<JobStatus> STATUS =
                  Signals.shared(JobStatus.idle());
      }
- @RmiService JobService { void startJob(); } — note: NO status getter; the
  shared signal makes one unnecessary.
- Server: an @ApplicationScoped JobRunner bean simulating the fake external
  source. startJob() launches a virtual thread (Thread.ofVirtual()) that
  advances through the phases over ~20 seconds, updating percent in increments
  (Thread.sleep between ticks; small random variation is fine), and on every
  tick simply calls:
      JobSignals.STATUS.set(new JobStatus(...));
  Always set a NEW JobStatus instance — never mutate the current one. Roughly
  1 run in 4 should end in "Failed" partway through. Ignore startJob() while
  JobSignals.STATUS.get().isRunning() is true.
- Client: derive the whole UI from JobSignals.STATUS with Effects/Computed,
  nothing else:
  * a Progress (or RadialProgress) bar bound to percent;
  * a Steps component showing the phases with the current one highlighted;
  * a StatusDot + Badge for the phase, styled differently for Done/Failed;
  * a "Start job" Button disabled (via an Effect) while running is true.
  There is NO status fetch, NO subscribe call, and NO merge logic anywhere in
  the client — the framework delivers the retained value on connect.
- Open two browser windows: both must track the same job in real time.
- Provide dispose() releasing all Effects.

ACCEPTANCE:
- mvn install passes from the root.
- Clicking "Start job" animates the progress bar to completion (or failure)
  WITHOUT any client-side polling and WITHOUT any status-fetching RMI method —
  the only client->server call in the whole example is startJob().
- A second browser window opened mid-job shows the current progress immediately
  (retained value), then tracks live.
- grep the client module for "subscribe", "getCurrentStatus", "merge": no hits.
```

---

## Task 3 — `inventory-crud`: master-detail persistence

Feature focus: EclipseStore CRUD, one POJO end-to-end, `Table`, master-detail editing. Reference: `todo-signals`.

```text
TASK: Build the example "inventory-crud" under zerozstack-examples/inventory-crud.
Reference example to copy: zerozstack-examples/todo-signals.

A small warehouse inventory manager:
- Shared model Product (@DataModel): id (long), name, category (String),
  quantity (int), unitPrice (double). Server assigns ids from a counter stored
  in the DataRoot (persist the counter too).
- @RmiService ProductService { List<Product> list(); Product save(Product p);
  void delete(long id); } — save() creates when id == 0, else updates; every
  mutation persists via storage.store(...).
- Client, master-detail layout (HorizontalLayout or SplitPane):
  * master: a Table of products (name, category, quantity, price) plus a
    TextField filter; hold products and the filter in ValueSignals, derive the
    filtered rows and a Stat/KpiTile header (product count, total stock value)
    via Computed, render via Effects;
  * detail: an edit form (TextFields, Select for category from a fixed list)
    that fills when a row is selected; New / Save / Delete buttons calling the
    service on background threads, then refreshing the products signal from the
    server's return value.
- Show an EmptyState component when the (filtered) list is empty.

ACCEPTANCE:
- mvn install passes from the root.
- Create, edit, delete round-trips update the table and the header stats without
  any manual "refresh" beyond re-setting the products signal.
- Data survives a server restart. The same Product class is used in storage, on
  the wire, and in the UI — verify no mapping code exists anywhere.
```

---

## Task 4 — `secure-admin`: authentication and role-based UI

Feature focus: `@Secured`, `@RolesAllowed`, `RmiSecurityContext`, role-dependent rendering. Reference: `chat-events` (its ChatService already uses @Secured/@RolesAllowed — study how).

```text
TASK: Build the example "secure-admin" under zerozstack-examples/secure-admin.
Reference example to copy: zerozstack-examples/chat-events.

A support-ticket queue with two roles:
- Shared model Ticket (@DataModel): id, title, description, status
  ("Open"/"Closed"), reporter (String).
- @RmiService @Secured TicketService:
  * List<Ticket> myTickets();            // tickets reported by the caller
  * void submit(String title, String description);
  * @RolesAllowed("admin") List<Ticket> allTickets();
  * @RolesAllowed("admin") void close(long id);
  Determine the caller via RmiRequestContext.getPrincipal() on the server —
  never trust a client-supplied username.
- Reuse the authentication mechanism exactly as the reference example's server
  is configured — study how the reference example authenticates and how the
  client learns its identity/roles (RmiSecurityContext.onResolved to mount the
  UI once the server has answered either way, onAuthenticated to reveal a
  protected view, onAuthenticationFailed to show a sign-in error, plus
  getUsername and hasAnyRole). Never mount the UI from onAuthenticated: it does
  not fire for an anonymous connection, and the page renders blank. Do not build
  a custom login screen unless the existing mechanism requires one.
- Client: after authentication, render:
  * for every user: a submit form and "my tickets" list;
  * ONLY when RmiSecurityContext.hasAnyRole("admin"): an admin panel listing all
    tickets with Close buttons.
  The admin panel must not merely be hidden — verify the server also rejects
  close()/allTickets() for non-admins (client-side hiding is cosmetic; the
  @RolesAllowed annotations are the actual security boundary).
- Handle the rejection gracefully: catching the RMI error and showing an Alert.
- Maintenance banner via a client-writable shared signal: declare
      Signals.sharedWritable("support.banner", "", "admin")
  in the shared module. Every user renders the banner text at the top of the
  view via an Effect (hidden when empty). Admins get a TextField bound to the
  signal (bindValue) — typing updates every connected client live. The role
  gate is enforced SERVER-side: a non-admin write is snapped back by a
  corrective update, not merely hidden in the UI.

ACCEPTANCE:
- mvn install passes from the root.
- A non-admin user sees no admin panel AND gets a server-side error (surfaced as
  an Alert, not a crash) if close() is invoked anyway.
- Banner text set by an admin appears in a non-admin window without a reload;
  a forced non-admin signal write reverts within a round-trip.
- README documents which users/roles exist and how to log in as each.
```

---

## Task 5 — `metrics-live`: implicit sync with @LiveSync

Feature focus: `@LiveSync` state mirrored automatically — the contrast to explicit events. Reference: `chat-livesync`.

```text
TASK: Build the example "metrics-live" under zerozstack-examples/metrics-live.
Reference example to copy: zerozstack-examples/chat-livesync.

A live server-metrics dashboard, fed by a fake metrics source:
- Shared model LiveMetrics (@LiveSync @DataModel): cpuPercent (int),
  requestsPerSecond (int), activeUsers (int), lastUpdated (long), plus a
  List<Integer> of the last 30 cpu samples for a sparkline.
- @RmiService MetricsService { LiveMetrics getMetrics(); }.
- Server: an @ApplicationScoped MetricsTicker that starts a virtual thread on
  first use (or eagerly) and every 2 seconds writes plausible drifting fake
  values into the LiveMetrics instance held in the DataRoot, appends the cpu
  sample (cap the list at 30), then calls syncEngine.notifyChanged(metrics) —
  exactly the pattern chat-livesync's ChatServiceImpl uses after mutations.
- Client: fetch LiveMetrics once via RMI, keep the reference, and render it into
  KpiTile/Stat components, a RadialProgress for cpu, and a Sparkline for the
  history. The framework updates the OBJECT'S FIELDS IN PLACE when the server
  notifies — there is no callback hook in this example, so re-render on a simple
  client-side interval (a background thread loop with Thread.sleep(1000) that
  re-reads the fields and updates the components). Note this honestly in the
  README: LiveSync moves the DATA automatically; re-rendering is the app's job.
- Two browser windows must show the same numbers changing in step.

ACCEPTANCE:
- mvn install passes from the root.
- Numbers and sparkline update continuously in the browser with NO RMI calls
  after the initial getMetrics() (verify: network tab shows only WebSocket
  frames).
- README contrasts this style with job-monitor's explicit events in 2–3
  sentences.
```

---

## Task 6 — Capstone: `zeroboard`, a real-time team kanban

Feature focus: everything composed — persistence, RMI, typed events, signals, security. This is the hardest task; attempt it only with the other examples green.

```text
TASK: Build the capstone example "zeroboard" under zerozstack-examples/zeroboard.
Reference examples: copy zerozstack-examples/chat-events for structure; study
todo-signals for the client state model before writing the UI.

A multi-user kanban board ("zeroboard") with three fixed columns: Backlog,
In Progress, Done.

Shared module:
- CardItem (@DataModel): id, title, column (String), createdBy, createdAt.
  Value-based equals/hashCode.
- BoardEvents: EventTopic<CardItem> CARD_UPSERTED ("board.cardUpserted"),
  EventTopic<Long> CARD_DELETED ("board.cardDeleted") — deleting broadcasts the id.
- @RmiService @Secured BoardService:
  * List<CardItem> getBoard();
  * CardItem addCard(String title);                  // into Backlog, server ids
  * CardItem moveCard(long id, String targetColumn); // validate target column
  * @RolesAllowed("admin") void deleteCard(long id);

Server (single BoardServiceImpl):
- EclipseStore persistence via the DataRoot pattern; server-assigned ids from a
  persisted counter; createdBy from RmiRequestContext.getPrincipal().
- After every successful mutation, persist, then publish the matching event via
  the injected EventPublisher. Reject moves to unknown columns.

Client (this is where the composition shows — follow it exactly):
- State: ONE ValueSignal<List<CardItem>> cards is the single source of truth,
  plus ValueSignal<String> searchText.
- Events feed the state: subscribe to CARD_UPSERTED (reduce: replace-by-id or
  append, immutably) and CARD_DELETED (reduce: remove-by-id) BEFORE calling
  getBoard(), then merge the snapshot (dedupe by id, keep the newer of
  duplicates). Own mutations do NOT update state directly — the echoed event is
  the single update path, so every window (including your own) renders identically.
- Derived state: Computed per column (filtered by column + searchText match) and
  a Computed count per column shown as a Badge in the column header.
- Rendering: three columns (GridLayout or HorizontalLayout of Cards); each card
  shows title + createdBy and Buttons to move left/right (no drag & drop); an
  add-card TextField+Button; a search TextField bound to searchText; admins
  additionally see a delete Button per card (RmiSecurityContext.hasAnyRole).
- All rendering via Effects; full dispose() for subscriptions, effects, computeds.

ACCEPTANCE:
- mvn install passes from the root.
- Two browser windows: adding/moving a card in one appears in the other (and in
  the acting window itself) via the event echo — there is no direct local-state
  write in any button handler.
- Search filters all three columns live; column counts always match visible cards.
- Non-admins see no delete buttons, and a forced deleteCard() is rejected
  server-side.
- README explains the event-echo pattern and why it guarantees convergence.
```

---

## Task 7 — `collab-editor`: bidirectional LiveSync with edit locking

Feature focus: the safe bidirectional pattern — **commands up (RMI), state down (LiveSync)** — coordinated with `LiveMutex` so concurrent editors cannot clobber each other. Reference: `chat-livesync`.

```text
TASK: Build the example "collab-editor" under zerozstack-examples/collab-editor.
Reference example to copy: zerozstack-examples/chat-livesync. Also study
com.zeroz4j.api.LiveMutex and its providers (zerozstack-client
ClientLiveMutexProvider, zerozstack-server-core ServerLiveMutexProvider, and the
LiveMutexRpc service) before writing any code.

A team profile card that several users can view live but only one can edit at
a time:
- Shared model TeamProfile (@LiveSync @DataModel): teamName, mission
  (String), headcount (int), editingBy (String, empty when nobody edits),
  version (long).
- @RmiService ProfileService:
  * TeamProfile getProfile();
  * void beginEdit();                     // marks editingBy = caller, notifyChanged
  * void save(TeamProfile updated);       // apply fields, version++, clear editingBy, notifyChanged
  * void cancelEdit();                    // clear editingBy, notifyChanged
  All mutations follow the chat-livesync pattern exactly: mutate the DataRoot's
  TeamProfile on the server, persist via storage.store(...), then call
  syncEngine.notifyChanged(profile). The server determines the caller from
  RmiRequestContext.getPrincipal() — never from a client-supplied argument.
  save() must reject (throw) if the caller is not the current editor.
- Locking: the EDIT button's click handler acquires the distributed lock
  BEFORE calling beginEdit():
      LiveMutex.get(profile).lock();   // suspends the Wasm coroutine if held
      profileService.beginEdit();
  and releases it after save()/cancel completes:
      profileService.save(updated);    // or cancelEdit()
      LiveMutex.get(profile).unlock();
  Call this flow directly from the click handler. lock() and the RMI calls
  suspend the client coroutine and resume on their own — do NOT wrap it in
  new Thread(...), which the framework rules above forbid. If client-side
  LiveMutex proves unreliable in practice, fall back to enforcing mutual
  exclusion purely server-side inside beginEdit() (reject when editingBy is
  non-empty) — and document which variant you shipped in the README.
- Client UI (one view):
  * a read-only card rendering teamName/mission/headcount/version, refreshed on
    a 1-second background re-render loop (LiveSync updates the object's fields
    in place; re-rendering is the app's job — state this in the README);
  * an "Edit" Button that runs the lock+beginEdit flow, then swaps the card for
    a form (TextFields + Range or TextField for headcount) pre-filled from the
    profile, with Save and Cancel buttons wired per the locking flow above;
  * while ANOTHER user edits (editingBy non-empty and not me): disable the Edit
    button and show a Badge "Editing: <name>".
- Two browser windows: edits saved in one appear in the other within a second;
  while one window edits, the other's Edit button is disabled with the badge
  visible.

ACCEPTANCE:
- mvn install passes from the root.
- Concurrent-edit safety: with two windows, clicking Edit in both at once never
  results in both forms open — one acquires, the other waits (or is rejected,
  in the fallback variant).
- A save() forced from a non-editor window (e.g. via a stale form) is rejected
  server-side, not just hidden client-side.
- README explains the pattern in one paragraph: LiveSync carries state DOWN,
  RMI commands carry writes UP, LiveMutex serializes editors — clients never
  write shared object fields directly.

VARIANT B (optional, only after the command-based version works): reimplement
the edit flow with two-way LiveSync — annotate TeamProfile @ClientWritable,
replace save() with direct setter calls on the profile (the framework
propagates them), persist via a LiveMutationListener bean, and keep the
LiveMutex serialization. Document in the README which trade-offs changed
(no named save operation; validation now rides the model annotations;
last-write-wins under the hood, masked by the lock).
```

---

## Evaluating agent runs (for framework contributors)

When using these prompts to test how well agents handle ZeroZ Stack, record per run:

| Check | Pass/Fail |
|---|---|
| `mvn install` green on first agent-declared completion | |
| Acceptance criteria met without human fixes | |
| Framework rules honored (immutable signal updates, dispose, subscribe-before-fetch, no DTOs) | |
| Number of human interventions needed, and what unblocked each | |

Treat every failure as a signal: if the agent needed information that exists only in framework source code (not in docs/), that is a documentation gap; if it needed information that exists nowhere, that is an API usability gap. File issues accordingly — these prompts double as the framework's developer-experience regression suite.
