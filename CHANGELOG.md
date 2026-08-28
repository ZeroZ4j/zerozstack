# Changelog

All notable changes to ZeroZ4j are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) — with the pre-1.0 caveat that breaking
changes may land in a minor version while the design settles.

ZeroZ4j is an experimental proof-of-concept. Read each release's **Breaking** section before
upgrading.

## [0.8.0] — unreleased

Form fields can be given a caption at last. Plus two faults in `Dialog`, both found in an
application built on 0.7.0, one round of text that had been stored wrong since the library was
first published, and repairs to the two components whose labels an application could not control.

### Breaking

- **A dialog now takes over the page.** `open()` used to add a stylesheet class and nothing else, so
  the element never became a real dialog as far as the browser was concerned. Escape did nothing,
  the keyboard could wander out of it, the page behind it stayed live, and there was no dimmed area
  to click. A dialog shipped without a Close button was therefore a trap: in the application this
  came from, opening one froze everything until the page was reloaded, taking a half-written message
  with it. `open()` now hands the element to the browser, which is what Escape, the focus trap and
  the dim have always needed.

    **If your application relied on the page behind a dialog staying usable**, or on Escape doing
    nothing, two calls before opening give you the old behaviour back:

    ```java
    dialog.setModal(false);              // the browser does not own it
    dialog.setCloseOnOutsideClick(false); // and a click outside does not close it
    ```

    Both are needed. The first is about what the browser does; a click outside the panel is drawn
    and handled by the component itself, so it survives the first call on its own.

    **If you only want to stop the user walking away from a question**, keep the new behaviour and
    take the exits away one at a time with `setCloseOnEsc(false)` and
    `setCloseOnOutsideClick(false)`. Whenever you do, leave a button on the dialog: it becomes the
    only way out.

- **`setWidth` and `setHeight` on a dialog now size the visible panel, not the full-window
  overlay.** They did not exist on `Dialog` before this release, so nothing can break — but if you
  reached past the API for the first child element and set a width on it yourself, that code is now
  redundant and should be deleted before the two fight each other.

### Added

- **A form field can be given a caption.** Until now the only text a field could carry was the
  placeholder — the grey words inside the box — because `new TextField("Primary folder path")` sets
  a placeholder and there was nothing else to set. So every form written on this library named its
  fields with placeholders, which stop saying what the field is the moment somebody types in it,
  and leave a screen reader with nothing to announce at all. One application had grown two private
  helpers doing the same thing in two different styles before it settled on a third.

    ```java
    TextField folder = new TextField().withLabel("Primary folder path");
    TextField email  = new TextField("you@example.com").withLabel("Email address");
    ```

    The caption is a real label tied to the control, so clicking the words puts the cursor in the
    field — and ticks the box, for a checkbox — and assistive technology reads the two together.
    The identifier that ties them is generated, so nothing has to be invented per page; giving the
    field your own id afterwards moves the caption with it.

    A caption also gives the field somewhere to put the other three things a field needs to say:

    ```java
    field.setHelperText("An absolute path. It is created if it does not exist yet.");
    field.setRequiredIndicatorVisible(true);      // the asterisk after the caption
    field.setErrorMessage("A port is a number between 1 and 65535.");
    ```

    `withLabel` and `withHelperText` return the field, so they read inside the expression that
    creates it; `setLabel`, `setHelperText`, `setRequiredIndicatorVisible` and `setErrorMessage` are
    there for changing one later. They work on every input in the library, since they live on the
    class all of them extend: text fields, text areas, selects, checkboxes, toggles, ranges,
    ratings, radio groups and file pickers. A checkbox and a toggle put their caption on the right
    of the control, on the same line; everything else puts it above.

    **A caption works anywhere, not only inside a `FormLayout`.** That is deliberate, and it is why
    there is no "form item" to wrap a field in: most fields are not in a form layout, and a field
    that can only be named in one container is not much use. A field with a caption is inserted
    into its parent as a small group — caption, control, explanation, message — rather than as the
    bare control. A field with no caption is inserted exactly as it was before, so a page that does
    not use captions is unchanged, to the character.

    A method taking a stylesheet class for the caption, and one handing the caption out as a
    component to be styled, were both turned down for the reason `Dialog.setWidth` gave: they put
    stylesheet class names back into application code, which is the one thing this framework exists
    to keep out.

- **A dialog can be given a width.** A dialog is two boxes: a full-window overlay, and the panel
  you actually see. Everything worth changing lives on the panel, and until now the component
  handed out no way to reach it, so applications took the overlay's first child element and pushed
  stylesheet classes onto it — six separate hand-written copies of the same workaround across
  eleven places in one application, each written by somebody who could not find the previous one.

    ```java
    Dialog dialog = new Dialog();
    dialog.setWidth("56rem");
    ```

    The panel is never wider than the window, so a width chosen for a desktop still fits a phone.

    A method that adds a stylesheet class to the panel, or one that hands the panel out as a
    component, would have removed the same workaround. Both were turned down. They make the
    dialog's internal shape part of its public contract, so it could never be rebuilt; and they put
    stylesheet class names back into application code, which is the one thing this framework exists
    to keep out. A width is a width — the same `setWidth` every other sizeable component already
    has, taking a length rather than the name of a rule in somebody's stylesheet.

- **`addCloseListener`** — called once every time a dialog closes, however it closed: Escape, a
  click outside, or `close()` in your own code. `isFromClient()` on the event is false only when
  your code closed it. Use it to release whatever the dialog was holding, rather than trusting that
  your Close button is the only way out — it no longer is.

- **`setCloseOnEsc`, `setCloseOnOutsideClick`, `setModal`, `isOpened`** — see the Breaking section
  above for when you want each of them.

- **`setPlaceholder` and `getPlaceholder` on a text field and a text area.** The placeholder could
  only be set in the constructor, which is part of why it was doing the caption's job. Nothing has
  changed about what `new TextField("x")` does: it still sets the placeholder, and always will.

- **A status dot can be coloured by one word and read as another.** `StatusDot` took a single
  string and used it for both the colour and the hover text, so an application that has to colour a
  dot by an internal state was forced to show that state to the reader — every dot in one console
  hovered as `DISPATCHED`. The two are now separate, and the reader's words are announced by a
  screen reader as well, which a dot with no text in it previously had no way to be.

    ```java
    new StatusDot("DISPATCHED", "Sent to a worker");
    dot.setState("FAILED", "Could not finish");
    dot.setLabel("Waiting for a slot");        // change only the words
    ```

    Passing one string still works and still means both.

- **`LaneTimeline.setLabelWidth`** — pins the width of the name column, for lining several
  timelines up with each other. Leave it alone, or pass 0, and the column measures itself.

### Fixed

- **Text that had been saved as UTF-8 and read back as Windows-1252 was published in the component
  library.** Eight strings were stored that way, three rounds of it deep, so an application using
  them rendered `ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â` where a dash belonged. The affected components are
  `LaneTimeline` (its play, speed and pause labels, and the ellipsis it uses to shorten a label),
  `DiffView` (the minus sign in front of a deleted-line count), `PropertyGrid` (the dash it shows
  for a missing value) and `StreamingText` (the block cursor). The component gallery had the same
  fault on its keyboard, pagination, stat and swap pages.

    The build was never at fault and nothing could have caught this: the corruption was in the
    committed source, so every build reproduced it exactly, without a warning. A new test now reads
    every Java file in the checkout and fails on any text that survives being written as
    Windows-1252 and read back as UTF-8, naming the file, the line and the characters that were
    meant.

    A second test now guards the release itself. It reads the text of everything this project
    publishes — the words baked into the compiled program files, the resource files, the files the
    build generates, and the starter project the framework hands to anyone creating a new
    application — and stops the build if any of it is damaged in that way. The two checks together
    cover both ends: one watches what a developer types, the other watches what leaves the building.
    Checked against the 0.7.0 file that was actually published, the new test finds every damaged
    piece of text in it; checked against this release it reads several thousand files and reports
    nothing.

    It also refuses to pass quietly on a partial job. It works out for itself which parts of the
    project get published, by reading the build files rather than from a list that could go stale,
    and it fails and names the part it could not read. A check that silently looks at less than it
    claims to is the same kind of fault it was written to catch.

- **Six example applications had their logging setting saved in the wrong kind of file, so it did
  nothing.** The file said to log in full detail. It had been written by a Windows shell, which
  saves text in a form Java's settings reader does not understand, so the reader saw nonsense,
  ignored it, and the examples logged at their normal level instead. The files are now saved
  normally and the setting takes effect. Only the examples were affected; nothing in the framework
  itself read these files.

- **A dialog taken out of the page while it was open** used to leave the browser believing it was
  still open, so it could not be shown again. It is now closed properly when it is removed, its
  close listeners are told, and a leftover open marker is repaired before it is shown again.

- **A validation message had nowhere to appear.** `Binder` put the text of a failed check into a
  stylesheet variable on the field and stopped there, so unless the application had written a rule
  to display that variable — and none did — the field simply turned red and the person was left to
  guess what was wrong. The message now appears under the field, in words, and the field is marked
  invalid for assistive technology. `asRequired` puts the asterisk on the caption for you at the
  same time. A rule attached directly with `withRule` behaves the same way once the field has been
  typed in.

    Nothing about this needs code changes; forms that already validate simply start saying why. The
    stylesheet variable is still set, so an application that did write a rule for it keeps working —
    if you had one, delete it now, or the message will be shown twice.

- **A lane in `LaneTimeline` could only be named in twelve characters.** The name column was 90
  pixels wide and could not be changed, and anything longer was silently cut — `worker-0
  qwen36-27b` arrived as a stub. The column is now measured from the longest name, between 90 and
  260 pixels, hovering a name always shows it whole, and `setLabelWidth` pins the column where a
  fixed one is wanted.

## [Unreleased] — 0.8.0

Work in progress. This section is filled as features land; each entry says what changes and, for a
breaking change, what to do about it.


## [0.7.0] — 2026-08-20

File upload, plus a round of work on the live connection, the binary wire format, the HTTP addresses
and the object-locking service.

### Breaking

- **A live change is now checked against every object it reaches, not only the outermost one.**
  A `@LiveSync` model nested inside a `@ClientWritable` model needs its own `@ClientWritable`, and
  its own roles, before a client may edit it as part of the outer one. One refusal refuses the whole
  change: nothing is applied, nothing is broadcast, and the writer is told which type it was not
  allowed to write ("The change also alters a …").

    **If an edit that used to work now comes back refused**, pick one of two fixes: put
    `@ClientWritable` on the inner model, or stop sending that model to the client at all.

- **A client can read an object back only if the server actually sent it to that browser.**
  Every object travels with a name attached — a **handle** — and the client asks for an object again
  by naming it, after a dropped connection most of all. The server now keeps a record of which
  objects it sent to which browser, and answers only from that record. Naming an object that was
  never sent gets the same silent answer as naming one the server no longer has.

    The record is kept per browser, not per connection, so reconnecting still restores everything.
    It holds **10,000 objects per browser** (`zeroz.disclosure.maxHandlesPerClient`) and is dropped
    after **24 hours** with no activity (`zeroz.disclosure.idleHours`). A dropped record behaves
    like a server restart: the client is told nothing was found and fetches the objects the way it
    first obtained them.

    **A client that carries no browser cookie** — a test harness, a non-browser client — is
    remembered only for the life of one connection, so after a reconnect it re-fetches instead of
    re-syncing. The server log says so once.

- **The largest message the server accepts is now 4 MB** (`zeroz.ws.maxBinaryMessageBytes`).
  It used to be whatever the container allowed, which on Helidon was about 2 GB. A value you set
  yourself still wins, in either direction. The limit in force is logged once at startup, naming the
  property.

    **If a call starts closing the connection instead of returning**, its response is over 4 MB.
    Either raise the limit, or return less — page the results, or return identifiers and fetch the
    details on demand. File contents belong in the new file upload feature, not in an RMI call.

- **The example servers no longer switch on the built-in `demo` and `admin` logins by starting.**
  Pass `--dev-login` on the command line — the `run.bat` scripts already do — or set
  `-Dzeroz.security.mode=dev` yourself. A server running with them on prints a warning at startup
  and again at the first sign-in.

- **A lock is granted only on an object the server sent that browser.** `LiveMutex.lock()` on
  anything else is refused straight away with a sentence saying so, instead of waiting. No sign-in
  is required, because applications with no login use locking too. A deployment that does have
  logins can additionally allow locking only on signed-in connections with
  `zeroz.livemutex.requireAuthentication=true`.

    **If a lock is refused for an object you really did hold**, fetch it again from your service and
    lock the copy you get back — that browser's record had expired or filled up.

- **An unexpected server error no longer sends its own message to the client.** The caller now sees
  `The server could not complete this request. Reference: 4f2a91cc`, and the real message and stack
  trace go to the server log under the same code, so a user reading the code off their screen is
  enough to find the log line.

    Two kinds of message still travel word for word: the framework's own refusals — authentication
    required, access denied, unknown service, unknown method, failed argument validation — and
    anything you throw as the new `com.zeroz4j.server.ClientVisibleException`.

    **If your client showed the text of an application exception to the user**, wrap that case in
    `ClientVisibleException` and the text comes back.

### Added

- **File upload.** Drop files on a box or pick them, with a progress bar and a cancel button for
  each one. Put `FileUpload` on a screen, write one `@ApplicationScoped` class implementing
  `FileUploadHandler`, and that class is handed each finished file. Files travel over their own HTTP
  address rather than the live connection, which is what makes progress and cancelling work. The
  default limit is 25 MB per file. The WAR module and the standalone module both carry the address
  already, so there is no route to map. See [Accepting file uploads](docs/guides/file-uploads.md).
- **`zeroz.hosts`** — the host names this deployment answers for, comma-separated, e.g.
  `app.example.com`. A handshake addressed to any other name is refused. Unset, nothing changes.
- **A ceiling on how much one connection can have in flight.** 32 messages being handled at once
  (`zeroz.ws.maxConcurrentFramesPerSession`), and 256 messages or 8 MB waiting to go out
  (`zeroz.ws.maxPendingFramesPerSession`, `zeroz.ws.maxPendingBytesPerSession`). An empty outgoing
  queue always accepts the next message however large it is, so a single big response is never
  refused.
- **`zeroz.livemutex.waitSeconds`** — how long a caller waits for a lock somebody else holds, 30
  seconds by default. **`zeroz.livemutex.requireAuthentication`** — off by default; on, only
  signed-in connections may lock.
- **`zeroz.disclosure.maxHandlesPerClient`** and **`zeroz.disclosure.idleHours`** — the size and the
  lifetime of the record of what was sent to each browser. 10,000 and 24.
- **`zeroz.upload.maxBytes`, `zeroz.upload.passSeconds`, `zeroz.upload.tempDir`** — the largest file
  accepted (25 MB), how long an upload permission stays usable (60 seconds), and where a file is
  written while it arrives (a `zeroz4j-uploads` folder inside the system temporary directory).
- **`com.zeroz4j.server.ClientVisibleException`** — throw it from a service to send its message to
  the caller word for word.
- **A refused handshake now says which check refused it** — the page it came from, or the host name
  it was addressed to — in the WebSocket close reason. The full explanation, with the configured
  values, is in the server log.
- Every setting the framework reads is now listed in one table, in
  [Packaging and running](docs/guides/packaging.md).

### Fixed

- **A short message could make the server reserve gigabytes of memory.** Every length and element
  count in the binary format is a number the sender chose, and each one is now compared against the
  bytes actually present, at the width of the element it describes, before anything is allocated.
  Collections grow as items arrive instead of being sized from a claimed count, a negative length is
  refused with a readable message rather than escaping as `NegativeArraySizeException`, and nesting
  is capped at 256 levels. Applications see this only as a clearer exception on a damaged stream.
- **One browser that stopped reading could stall the whole server.** Sending held a lock on the
  connection while it waited, and on JDK 21 that pinned a platform thread for every stalled send —
  measured, not assumed. Each connection now has its own outgoing queue and its own writer, so a
  browser that has stopped reading delays only its own messages. Past the queue limits that
  connection is closed with WebSocket code `1013`, and the log names the limit that was reached.
- **Two owners could hold the same lock at once**, because releasing a lock and closing the
  connection could both hand it on. Ownership is now removed in one step.
- **Locks no longer pile up.** An object has an entry in the lock table only while somebody holds its
  lock or is waiting for it, and the entry goes as soon as the last of them leaves.
- **A request path is answered with 404 before any file is looked up** when it contains `..` in any
  spelling, a backslash, a control character, or a first segment of `WEB-INF` or `META-INF`. Both
  the WAR binding and the standalone binding apply this, so the two cannot disagree.
- **The caller's identity is established before a call's arguments are decoded**, not after, so
  everything decoding does runs under the right identity.

## [0.6.2] — 2026-08-20

### Fixed

- **Every application broke the moment it was opened at a plain `http://` address on a network.**
  Pressing anything that made the framework hand out an identifier died with
  `TypeError: crypto.randomUUID is not a function`, and there was nothing the application could do
  about it. Opened at `localhost` the identical steps worked perfectly, which is the worst shape a
  defect can have: it is invisible to whoever built it and it is all anybody else ever sees.

  The cause is a browser rule rather than a bug. `crypto.randomUUID()` exists only in a **secure
  context** — an `https://` page, or `localhost`. A page served over plain `http://` from a machine's
  address on the house network is not one, so the function is simply absent. TeaVM compiles
  `UUID.randomUUID()` straight into a call to it, and `ObjectMapper` — which names every object that
  crosses the wire — called that on the client for every object it had not seen before. Self-hosting
  on a LAN is the ordinary way to run one of these applications, so this was every application, on
  every press, for every user who was not sitting at the machine.

  Identifiers now come from `Ids.newId()`, which builds a version-4 UUID out of `SecureRandom`
  instead. That is the same generator `UUID.randomUUID()` draws on when it runs on the server, so
  the server side is unchanged in strength; in a browser TeaVM implements it with
  `crypto.getRandomValues()`, which is **not** restricted to a secure context and is therefore there
  on a plain `http://` page — and is still a cryptographic generator. Only where no `crypto` object
  exists at all does it fall back to an ordinary pseudo-random one, which no browser released this
  decade does. The output is byte-for-byte the same shape as before — thirty-six characters, lower
  case, four hyphens — so nothing already written down has to change.

  `Ids` is public, because an application that needs an identifier of its own has exactly the same
  problem. Treat what comes out of it as a name for something, not as a secret: a token that must be
  unguessable should draw on `SecureRandom` directly and fail loudly rather than degrade quietly.

  `IdsTest` pins the shape, the uniqueness, the pseudo-random last resort, and — the part that
  matters — reads the compiled `Ids` and `ObjectMapper` classes back and fails if either one so much
  as mentions `randomUUID` again.

  **Still secure-context-only, and deliberately left that way:** logging in with OpenID Connect.
  `OidcBrowser.codeChallenge` needs `crypto.subtle`, which browsers also withhold from a plain
  `http://` page. Weakening the PKCE challenge to make it work there would defeat the point of PKCE,
  so a login flow over plain `http://` fails, and should. Put the application behind HTTPS if it
  needs to log people in.

## [0.6.1] — 2026-08-17

### Added

- **A keepalive, so an idle connection is not closed by the proxy in front of it.** A WebSocket that
  carries nothing is cut by whichever proxy in the path has the shortest idle timeout: nginx defaults
  to **60 seconds** (`proxy_read_timeout`), and Cloudflare cuts at **100** and is not the
  application's to configure. Measured in a real deployment — sockets opened, authenticated, and died
  at exactly 60 seconds, over and over, each reconnect re-sending a growing pile of live objects.

  **An application could not fix this itself.** Browsers do not expose WebSocket ping frames to page
  script, so the only remedy available was to invent a service method whose sole purpose was to make
  a byte travel, and then declare it on every service interface the application owned — where it sat
  among the real operations looking like one of them. An application did exactly that, which is why
  this now belongs to the transport.

  The client sends a five-byte fire-and-forget frame to `zeroz4j.keepalive` after **25 seconds of
  silence**; the server answers with one empty `PONG` (`0x19`). The answer matters as much as the
  ping: a proxy times each **direction** separately, so a ping the server merely swallowed would keep
  only one of the two timers alive. Any real traffic — a call, a signal update, a sync frame —
  postpones the next ping, so a connection in use sends none at all.

  On by default; `Keepalive.configure(seconds)` changes the interval and zero turns it off. Answering
  costs the server nothing: no service lookup, nothing checked beyond the connection already being
  open, no request context. Pinned by `KeepaliveFrameTest`.

  Raising the proxy's own timeout is still worth doing where you control it. This exists because
  usually you do not.

### Fixed

- **A failed asset fetch in the service worker escaped as an uncaught rejection.** The cache-first
  branch rethrew nothing and caught nothing, so any request the worker could not fetch — an offline
  load, a URL the container refuses — surfaced as `Uncaught (in promise) TypeError: Failed to fetch`
  in the console and turned the response into a generic network error. That is a worse answer than
  the one the network actually gave, and the application could neither catch it nor explain it. The
  branch now answers `504` with the URL in the body, which says what happened and is visible in the
  network panel.

## [0.6.0] — 2026-08-17

The biggest release so far. Four things every non-trivial application had to build for itself now
ship with the framework: state that belongs to one tenant, user or browser is a first-class kind of
signal; logging in against an OpenID Connect provider is a dependency rather than a project; URLs map
to views with their data declared alongside them; and an application can be installed on a phone.

It also deploys as a WAR on a Jakarta EE server for the first time. That is listed as an addition
because the module is new, but most of the work was in Fixed: the first real WildFly deployment found
four separate faults that no Helidon example could ever have shown, including a servlet filter that
answered 401 to every page.

Read **Breaking** before upgrading. The authentication callbacks changed shape, and the AUTH frame
gained two bytes.

### Added

- **Scoped signals — one value per tenant, user, browser or session.** `Signals.shared` is one value
  the whole server agrees on, which is wrong for anything belonging to somebody.
  `Signals.scoped(name, initialValue, scope)` declares the same thing narrowed. The server names the
  target; the client calls `get()` and is never told its own, so it cannot ask for another's, and the
  wire frame carries the family name only — no client learns that other targets exist. See
  [SIGNALS.md](docs/SIGNALS.md#scoped-signals-one-value-per-tenant-user-or-browser).

- **`Scope.CLIENT` and a client identity that works with no login at all.** An open application still
  needs to keep one browser's state to itself. The id cannot come from the browser — anything a
  client claims about itself it can edit — so the server mints 256 random bits, signs them, and
  delivers them in an **`HttpOnly`** cookie that page script cannot read. Unlike a session id it
  survives reconnects and reloads. It identifies a browser, not a person, and the docs say so.

- **Origin checking at the handshake.** Required by the above rather than optional: a browser attaches
  cookies to any connection to your origin, including one opened by a page the user is merely
  visiting, so an unchecked `Origin` would have handed that page the visitor's identity. Same-origin
  by default, `zeroz.origins` for an allowlist.

- **OpenID Connect authentication, both halves.** A new optional module `zerozstack-auth-oidc`
  verifies an access token at the handshake — signature, issuer, audience and expiry, against a cached
  JWKS — and maps its claims to a name, roles and a tenant, reading Keycloak's split
  `realm_access`/`resource_access` role structure. In the browser, `OidcClient` runs the
  authorization-code flow with PKCE (S256 only; it refuses rather than downgrading to `plain`),
  checks `state`, strips the used code from the address bar, and refreshes ahead of expiry so
  reconnects carry a live token. See [the OIDC guide](docs/guides/oidc-auth.md).

- **Routing, with each route's data declared alongside its path.** `@Route("/tasks/:id")` on a
  `RouteView<Task>`: `load` runs to completion before `render` is called, so a view never exists
  half-loaded and never fetches from inside a mounted component. Real URLs through the history API,
  nested layouts, typed parameters, and `@RequiresRole` guards. The route table is generated at
  compile time, so there is no reflection and four classes of mistake are compile errors. See
  [ROUTING.md](docs/ROUTING.md).

- **The framework deploys as a WAR on a Jakarta EE server.** Three things blocked it, all fixed
  together:
  - **`SessionThreadFactoryProvider`**, a `ServiceLoader` SPI supplying the threads RMI calls run on.
    The engine previously created its own virtual threads, and a thread the application server did
    not create carries none of the container's thread-locals — no naming context behind
    `java:comp/env/…`, no transaction context, no caller identity. A service doing a JNDI lookup
    failed a long way from the cause. It cannot be repaired from *inside* such a thread by handing
    work to a `ManagedExecutorService`, because there is no context there to capture; it has to be
    right at thread creation, hence a factory. No provider registered means virtual threads, exactly
    as before.
  - **`zeroz.ws.maxBinaryMessageBytes` and `zeroz.ws.idleTimeoutMinutes`.** `@OnMessage` takes a whole
    message — there is no partial-message handling — so a response larger than the container's binary
    buffer did not raise an error, it closed the socket. Both properties are unset by default, leaving
    the container's own values rather than imposing a framework default.
  - **`zerozstack-server-jakarta`**, the servlet-container counterpart to `-helidon`: WebSocket
    endpoint registration, `BinaryRegistry.init()` at startup, the container's `ManagedThreadFactory`,
    and an optional shell servlet for deep links. Adding the dependency is normally the whole
    integration.

- **PWA: an application can be installed, and can receive web push.** `Pwa.install()` in the client's
  `main`, a manifest and three tags in `index.html` — that is the whole opt-in. It buys a home-screen
  launch in its own window, a fast second start because the client bundle is cached, an install
  button that appears when the browser actually offers (`Pwa.installable()` is a signal, because
  `beforeinstallprompt` arrives long after the UI is built), and `Pwa.subscribeToPush(...)` returning
  the endpoint and keys a push library needs.

  **It does not make an application work offline, and that is deliberate.** Every view loads its data
  over the WebSocket and there is no client-side store, so with no connection there is nothing to
  render; opened offline, the app shows `/zeroz4j-offline.html` and says why. Vaadin Flow makes the
  same trade for the same reason. The service worker ships inside `zerozstack-server-core` — no
  application copies it — and its cache name carries the build version, so a deployment evicts the
  previous shell instead of serving stale JavaScript against a newer server. `PwaManifest` builds the
  manifest per request, because in a multi-tenant product the name, icons and colour belong to the
  tenant. See [PWA.md](docs/PWA.md).

- **`EventPublisher.publishToClient(...)`** and `RmiRequestContext.getClientId()`.

- **`RmiSecurityContext.onAuthenticationFailed(...)`, `onResolved(...)` and `isResolved()`.** Three
  callbacks that used to be one: `onResolved` fires once the server has answered either way and is
  the "connection is usable" signal an application mounts its UI from; `onAuthenticated` is now
  strictly about identity; `onAuthenticationFailed` lets a login form show an error rather than wait
  forever on silence.

  `onResolved` exists because making `onAuthenticated` honest (see Fixed) broke every application
  that connects anonymously and used it as a readiness signal — including three of the examples,
  which rendered a blank page. Mounting a UI is a question about the connection, not about identity,
  and the two now have separate hooks.

### Fixed

- **The RMI endpoint is now the CDI bean on every container, including Tomcat.**
  `WasmRmiServerEngine` is `@ApplicationScoped` with three injected collaborators, and the container
  asks the endpoint's configurator to create it. The Jakarta API delegates that to the container's
  default configurator, and whether it knows about CDI is the container's business: WildFly's does,
  and Tomcat's is literally `clazz.getConstructor().newInstance()`. Deployed to Tomcat the engine
  therefore came up with three null fields and the first connection died in `onOpen` with
  `NullPointerException: ... "this.syncEngine" is null`, followed by a client reconnecting for ever
  against a server that failed it every time.

  `RmiEndpointConfigurator.getEndpointInstance` now asks CDI first and falls back to the container
  when the endpoint is not a bean or CDI is not running. Where the container already resolved the
  bean this returns the same one, so nothing changes on WildFly or Helidon. Pinned by
  `EndpointInstanceFromCdiTest`.

- **An application deployed under a context path now works.** Nothing had ever been served from
  anywhere but `/`, and four independent things assumed it: the router matched
  `/coachapp/messages/42` against a route table written as `/messages/:id` and found nothing;
  `navigate` pushed `/messages/42`, outside the deployment, so the next reload 404-ed;
  `Pwa.install()` registered `/zeroz4j-sw.js`, a 404 under a context path, taking web push and the
  offline page with it; and the shell's own relative asset references resolved against whichever
  route the browser had asked for, so a hard refresh two segments deep returned a page with no
  bundle.

  The server now serves the shell with a `<base href>` for its context path — both HTTP bindings,
  through one method in `StaticContent`, so they cannot drift — and the new
  **`com.zeroz4j.client.AppBase`** reads the application's root from it. `Router` translates between
  route paths and browser locations by itself; `Pwa.install()` registers the worker inside the
  application; `Zeroz4jClient.defaultWebSocketUrl()` is the endpoint URL applications were writing by
  hand, usually in one of the two ways that break.

  Route tables, `@Route` paths and `navigate` calls are unchanged: a route path never carries a
  context path. `AppBase.location(...)` is needed for anchors an application writes itself, because
  an `href` has to be a real URL for middle-click to work.

  The service worker was already scope-relative and needed no change. See
  [ROUTING.md](docs/ROUTING.md#deployed-somewhere-other-than-the-site-root).

- **`Zeroz4jShellServlet` now serves the WAR's own web content, not only the classpath.** A WAR keeps
  `index.html` and the client bundle in `src/main/webapp`, which lands in the archive root — and a
  WAR's classloader sees `WEB-INF/classes` and `WEB-INF/lib`, not the root. Mapped at `/` this
  servlet *replaces* the container's default servlet, so nothing else was left to serve them: a WAR
  packaged the obvious way answered **404 to every request, its own shell included**, and only a
  deployment would have said so.

  It now asks the classpath first — so a jar-packaged asset, the service worker above all, cannot be
  shadowed by a file dropped into the archive root — and the `ServletContext` second. `WEB-INF` and
  `META-INF` are never served from the archive root. `StaticContent` gained an `Assets` seam for
  this; every existing single-argument method still means the classpath.


- **`RmiSecurityContext.isAuthenticated()` returned `true` for a connection the application's
  `AuthenticationProvider` had rejected.** The server sent an AUTH frame on every connection, naming a
  refused one `"anonymous"` with no roles, and the client set `authenticated = true` for any AUTH
  frame it received. A login gate built the documented way — hanging the protected view off
  `onAuthenticated(...)` — therefore let every credential through, and the only way to tell a real
  sign-in from a refused one was to check for a role the provider granted on success. Reported
  against a consuming application whose beta login gate this silently defeated.

  The AUTH frame now carries the server's decision explicitly (protocol version 2), separate from the
  name and roles, because neither can stand in for it: a refused connection still has a name, and a
  genuinely authenticated user may hold no application roles at all. `isAuthenticated()` now means
  what its name says with no additional role check. The frame is still sent for refused and anonymous
  connections — silence cannot distinguish a rejection from a slow network — and a client talking to
  an older server treats the connection as unauthenticated rather than guessing.

- **An anonymous connection never mounted its UI.** Making `onAuthenticated` fire only on a real
  sign-in was right, but `todo-signals`, `form-signup` and `inventory-crud` used it as a "connection
  ready" hook, so they rendered a blank page. They now mount from `onResolved`. Fixing it also
  uncovered a second fault the blank page had been hiding: those examples built their view — and so
  made their first RMI call — directly inside the callback, which runs on a stack that began in
  native JavaScript, where TeaVM cannot suspend a coroutine. They failed with *"suspension point
  reached from non-threading context"* the moment they did render. Each now builds its view on a
  green thread, as the router already does internally.

- **Every stock Keycloak token was rejected.** A normal Keycloak access token carries
  `aud: "account"` and names the client in `azp`; the provider defaulted the expected audience to the
  client id, so no real token could pass. The unit tests missed it because they minted the audience
  the implementation expected rather than the one Keycloak issues. `zeroz.oidc.audience` is now unset
  by default, and with nothing configured a token is accepted when `aud` contains the client id *or*
  `azp` equals it — while a token issued to a *different* client on the same realm is still refused.
  Found by running the new example against a real Keycloak.

- **A deep link into a client route returned 404.** Real URLs mean the browser asks the *server* for
  `/projects/42` whenever such a link is opened, reloaded or shared, and `StaticContentResource`
  answered 404 for any path with no file behind it — so every client route worked exactly until it
  was refreshed. An unmatched path that does not look like a file now falls back to the application
  shell; a missing *asset* still returns 404, because serving HTML where a script was expected turns
  a missing file into an unreadable syntax error. Found by running the routing tour, not by a test.

- **Navigation failed outright wherever a loader had to suspend.** Navigations start in browser
  callbacks — a click, a popstate, the frame that reports authentication — and TeaVM cannot suspend a
  coroutine on a stack that began in native JavaScript, so a loader making an RMI call died with
  "suspension point reached from non-threading context". Each navigation now runs on a green thread,
  which re-enters TeaVM's own scheduler. Also found by running the tour.

- **Tenant-scoped pushes reached nobody.** `onOpen` never copied the tenant from the handshake into
  the session, but the scope filter reads it from the session — so `Scope.TENANT` events and LiveSync
  updates matched no session in a real deployment. Tests passed because they set session properties
  directly. Found while adding scoped signals; `SyncEngine`'s duplicate copy of the scope filter has
  been collapsed onto the shared one, which is what let the bug hide in one path and not the other.

- **A WAR deployment answered 401 to every page.** `RmiSecurityFilter` in `zerozstack-server-core`
  carried `@WebFilter("/*")`, so it installed itself into any application that depended on the
  framework and refused every request that was not a `.js`/`.css`/`.png`/`.svg`/`.ico`/`.wasm`/`.jpg`
  file unless the *container* had authenticated it. No application authenticated that way: the
  framework's whole model — including its own OIDC module — decides identity at the WebSocket
  handshake, which produces no `getUserPrincipal()`. So the shell, every client route, the PWA
  manifest and the URL that redeems an emailed sign-in link were all 401, while the script bundle
  loaded normally. There was no configuration in which the filter's happy path was reachable.

  It went unnoticed for four releases because `jakarta.servlet` is absent from the Helidon runtime:
  the class could not load, every example is a Helidon jar, and the module's `beans.xml` excluded it
  from bean discovery for exactly that reason. In a servlet container it loads and self-registers.
  Reported against the first WAR anyone deployed.

  **`RmiSecurityFilter` and `DevLoginServlet` are deleted** rather than gated. Nothing referenced
  either one, neither had a test or a line of documentation, and the model they implemented — a
  container-managed login gate in front of HTTP — is not this framework's and was never finished:
  the servlet's `/dev-login` page stored a principal in the HTTP session while the socket still
  authenticated from query parameters, so the two never met. An application that genuinely wants HTTP
  gated has a container `<security-constraint>`, which is enforced ahead of any filter anyway.

  `zerozstack-server-core` now references no servlet type at all — the thing three documents already
  claimed — and `NoServletTypesTest` reads the compiled classes each build to keep it that way.
  `DevAuth`, which is what the handshake and the examples actually use, is untouched.

### Breaking

- **`RmiSecurityContext.populate(String, Set)` is now `populate(String, Set, boolean)`.** Framework-
  internal — only the client runtime calls it — but a fork or a test that calls it directly must pass
  the authentication outcome rather than having it assumed `true`.
- **The AUTH frame gained a byte** (protocol version 2). Client and server ship together, so this
  only matters if you mix versions across the wire.
- **`Zeroz4jApplication` and `StaticContentResource` moved to a new `zerozstack-server-jaxrs`
  module.** Both are catch-alls at `/`, and living in `zerozstack-server-core` meant *any* WAR
  depending on the framework acquired a JAX-RS application answering every unmatched path — a
  collision the deployer could not opt out of, because an auto-discovered JAX-RS application is very
  hard to suppress from outside. `zerozstack-server-helidon` depends on the new module, so a
  standalone server is unaffected; a WAR simply does not take it. `zerozstack-server-core` now
  contains no JAX-RS type at all. The shell-fallback and content-type rules moved to
  `StaticContent` in core, shared by the JAX-RS resource and the new servlet so the two cannot drift.

- **`RmiSecurityFilter` and `DevLoginServlet` are gone**, with the `/dev-login` page and the HTTP
  gate they formed. Neither could run outside a servlet container, and inside one the gate made the
  application unreachable (see Fixed). An application that mapped `/dev-login` deliberately must
  provide its own; nothing else can be affected, because nothing else could reach them.

- **`@DataModel` on a record, interface or enum is now a compile error.** It was silently skipped —
  no serializer, no warning — and the failure arrived at runtime on the first call that tried to send
  one. Records are the obvious shape to reach for, so this was a trap rather than an edge case. The
  message names the element and, for a record, explains that a persistence root must be a plain class
  in any case because EclipseStore reaches fields directly. `@Route` had the identical silence and
  the identical fix. **Records are still not supported as wire types** — this makes that visible at
  compile time rather than at runtime.

- **`@Route` changed shape.** It previously declared hash-fragment paths for a router that was never
  implemented; it now takes real paths and an optional `layout`. Nothing could have depended on the
  old behaviour, since nothing read the annotation.
- **`ScopedSignal.get()` is now `ScopedSignal.mine()`.** It returns the *signal*, not the value, so
  sharing a name with `ValueSignal.get()` made `BASKET.get().get()` read as a mistake.
  `BASKET.mine().get()` says what it does. Renamed before release rather than lived with.

### Examples

- **`routing-tour`** — every routing feature in one application: nested layouts, path and query
  parameters, literal-beats-parameter, a role guard, and not-found/forbidden fallbacks.
- **`scoped-signals`** — the three reaches side by side. Open it twice in one browser as two different
  users and watch the basket stay shared while the per-user notice does not.
- **`oidc-login`** — a real Keycloak login with PKCE, then three RMI calls showing what the identity
  is worth server-side. The README includes the realm setup and the two Keycloak defaults that
  otherwise cost an afternoon.
- **`pwa-install`** — installability, a per-request manifest, an install button bound to a signal, and
  a push subscription round trip with the VAPID key generated by the server. Stop the server and
  reload to see the one thing installing does not buy you.

  The twelve example pages also lost a leftover snippet that *unregistered* service workers on every
  load — a workaround for cache pain during development, which would have quietly defeated the real
  worker.

### Dependencies

- **ZeroZ DB 0.1.0 → 0.2.0.** Purely additive; no API this framework uses changed shape. It brings
  diagnostics for a write-block that was never ended, and one of them fixes a real failure mode in
  `TenantStorageProvider.shutdownAll()` without any change on our side: `close()` used to park for
  ever on a write transaction that was never ended, so one wedged tenant hung shutdown and every remaining tenant
  went unclosed. It is now bounded (30s, or `-Dzerozdb.closeTimeoutSeconds=N`) and throws
  `StoreBusyException` naming the thread that holds the block — which the existing per-node `catch`
  logs before moving on to the next tenant.

  Also available to applications, though nothing in the framework calls them yet:
  `hasOpenWriteBlock()`, `writeBlockOwner()` and `describeLockState()` answer "is a block open, and
  who opened it" across threads, which `isWriteActive()` cannot because it reads a ThreadLocal;
  `ZeroZDb.traceWriteBlockOrigins(true)` records the stack that opened each block; and a
  `WriteTransaction` collected without `commit()`, `rollback()` or `close()` now logs a warning
  naming the thread that opened it.

### Packaging (also new in 0.6.0)

- **A packaging story, so "how do I ship this" stops being every application's research
  project.** Shading was the recurring dead end: Weld treats each jar as its own bean archive,
  and a merged jar breaks CDI discovery far from the cause. Projects generated from the
  archetype now carry two shade-free paths, both keeping every jar intact:
  - **`mvn verify -Ppackage`** runs the JDK's own `jpackage` and produces a self-contained
    folder at `<app>-server/target/dist/<app>/` — launcher executable, all jars unmodified in
    `app/`, bundled Java runtime. Ship the folder; the target machine needs no Java. Verified by
    running the full archetype smoke test against the packaged `.exe`: 5/5, zero Weld warnings.
  - **A `Dockerfile`** at the project root with the dependency jars as their own image layer
    below the app jar, so a routine rebuild pushes kilobytes rather than the framework.
  - A new guide, [Packaging and running](docs/guides/packaging.md), states the never-shade rule
    and why, and when to pick which shape.

- **WebSocket handshakes no longer 404 on Linux.** A `libs/*` classpath wildcard expands in
  directory order — alphabetical on Windows, arbitrary on Linux — and one of the arbitrary
  orders loads Helidon's CDI extensions in a sequence where the WebSocket routing registers
  after the server was already built. Same jars: Windows fine, Linux container dead, with HTTP
  and static content working and only the upgrade answering 404. Found by running the archetype
  smoke test against the generated container image; proven by showing any deterministic jar
  order fixes it. The generated `Dockerfile` now builds a sorted explicit classpath, and the
  packaging guide, troubleshooting page and AGENTS.md tell Linux classpath launches to sort.

## [0.5.0] — 2026-08-05

Dropped WebSockets happen constantly in practice — proxies time out, laptops sleep, phones change
networks — and 0.4.x left everything above the transport broken after one: reconnection restored
the pipe, and the application then ran on quietly stale data. Every application was forced to build
its own recovery. 0.5.0 moves all of it into the framework. An application that configures nothing
now gets a visible outage, an automatic reconnect, and a correct screen afterwards.

### Added

- **Automatic re-sync after a reconnect.** On reconnection the client re-subscribes every shared
  signal (each answered with the current retained value, so updates missed during the outage are
  not silently absent until the next change) and sends one `zeroz4j.resync` request naming every
  object handle it holds; the server re-sends each object's current state as ordinary in-place
  updates. Re-serializing also re-registers lazy-field handles for the new session, so unresolved
  `Lazy` references work again. One honest boundary: a **server restart** empties the in-memory
  handle registry, so re-sync cannot restore live objects fetched before it — the server logs how
  many handles were unknown, and the application re-fetches them as it first obtained them.
- **Nothing the user did while offline is lost.** Writes to `sharedWritable` signals made while
  disconnected are queued (last value per signal) and flushed on reconnect; edits to
  `@ClientWritable` live objects are retained and flushed the same way — previously both were
  silently discarded while the user's screen showed them applied. Flushes go out *before* the
  re-sync request, so the fetched server truth already includes them.
- **The connection is a signal.** `WasmRmiClient.connectionState()` returns a `ValueSignal` of
  CONNECTING / CONNECTED / RECONNECTING / CLOSED. Read it in an `Effect` to disable controls or
  render an indicator — no listener wiring.
- **A built-in connection banner.** "Connection lost — reconnecting…" appears fixed to the top of
  the page while the channel is down and disappears on recovery. Raw DOM with inline styles, so it
  renders identically with or without any CSS framework. On by default;
  `Zeroz4jClient.showConnectionBanner(false)` turns it off for applications that render their own.
- **Lock loss is reported.** The server has always released a session's `LiveMutex` locks when the
  socket closed; now the client-side holder learns of it: `mutex.setLostListener(...)` fires the
  moment the drop is detected, on the UI scheduler. A lost lock is not re-acquired — the callback
  is where an editor stops accepting input.
- **`SessionClosedEvent`.** A CDI event fired after framework cleanup when a WebSocket session
  closes, carrying the session id and principal name. Applications keep registries keyed by
  session id (scoped pushes, rooms, dashboards) and previously had no way to learn a session was
  gone — they coped with bounded collections and eviction heuristics. Observe the event and remove
  the entry instead.
- **`WasmRmiClientChannel.addStateListener` / `removeStateListener`.** The channel now supports
  any number of state listeners. `setStateListener` keeps its exact old contract — it replaces the
  listener it previously set — because applications register from view constructors and rebuilt
  views must not leave a trail of stale listeners behind.

### Fixed

- **`@Inject ZeroZDbNode` works.** It could not, in any application, throughout 0.4.x — which is
  awkward, because it is the documented way to reach the store and what the `inventory-crud`
  example does. `EclipseStoreProducer.getNode()` was `@RequestScoped`; a normal scope makes the
  container inject a client proxy; a proxy is a generated subclass; and `ZeroZDbNode` is `final`
  with a private constructor. Deployment failed with **WELD-001410**, or **WELD-001437** at first
  use when the node was reached through `Instance`. Neither the scope nor the finality was
  something an application could change, so there was no way to write the documented injection
  correctly — the only workaround was to inject `TenantStorageProvider` and call
  `getNode(tenantId)`. The producer is now `@Dependent`, which needs no proxy. It also takes an
  `InjectionPoint` parameter, which CDI permits only on a `@Dependent` bean, so the scope cannot
  silently regress. Reported against the Prashna Chakra application; thanks for the diagnosis.
- **A `@Dependent` node is reachable off-request.** The old request scope also meant the node was
  unusable from any thread with no active request context — a scheduler, a virtual thread, startup
  code. Those work now. `EmbeddedStorageManager` remains `@RequestScoped` deliberately: it is an
  interface, so it proxies, and the proxy is what re-resolves the tenant on each request.
- **This module now has a CDI test.** Every existing test built `ZeroZDbNode` by hand, which is
  precisely why the defect above shipped. `NodeInjectionTest` starts a real Weld container and
  injects the node into an `@ApplicationScoped` service, with no request context active.

### Changed

- **RMI calls fail fast when the connection is down.** A call made while disconnected, or in
  flight when the socket drops, now fails immediately with the new typed
  `com.zeroz4j.api.DisconnectedException`. Previously it hung — not for the 30-second timeout but
  indefinitely, because the timeout sweep only ran on traffic and a dead socket produces none; the
  browser meanwhile either discarded the frame silently or threw a raw `InvalidStateError` that
  died invisibly in the calling green thread. Calls are deliberately **never queued or replayed**:
  the framework cannot know whether repeating a call is safe, and silently replaying "submit
  order" after an outage places the order twice. Catch the exception to retry, or disable controls
  while `connectionState()` is not CONNECTED.

### Breaking

- Application code that already implements `WasmWebSocketChannel` gains a default `isOpen()`
  returning true — no action needed unless the transport can actually be down, in which case
  override it so fail-fast works.
- Applications that built their own outage banner will now show two. Either delete yours or call
  `Zeroz4jClient.showConnectionBanner(false)`.

## [0.4.1] — 2026-08-01

A generated project from `zerozstack-archetype:0.4.0` compiled cleanly, started cleanly, served
pages — and did not work. Three separate defects, each surfacing far from its cause and none
producing an error at the point of the mistake. All three are fixed here, and the archetype now has
an end-to-end smoke test so they cannot come back silently.

### Fixed

- **The annotation processor is no longer skipped on JDK 23+.** `zerozstack-apt` relied on javac
  discovering it from the classpath, and [JEP 470](https://openjdk.org/jeps/470) disabled implicit
  annotation processing in JDK 23 — so on a modern JDK no `*_Serializer`, no `*_Stub` and no
  `BinaryPackableRegistrar` was generated, with no error and no warning. The first shared-signal
  broadcast then failed at runtime telling the developer to annotate a type that was already
  annotated. The archetype now declares `annotationProcessorPaths` in its root pom, so the processor
  runs on every JDK and in every module, and the shared module — where `@DataModel` types live by
  convention — depends on `zerozstack-apt` at last.
- **`assertSerializable` no longer misdirects.** When a type carries `@DataModel` but has no
  generated serializer, the message now says exactly that and names the likely cause (the processor
  did not run) instead of telling you to add an annotation that is already there.
- **A generated project renders without hand-editing `index.html`.** TeaVM's JavaScript backend
  emits a UMD module that *exports* `main` rather than calling it, and the archetype's page never
  invoked it: the browser sat on `Loading…` forever with HTTP 200, zero JavaScript errors and no
  WebSocket — no signal of any kind that anything was wrong. The archetype page now calls `main()`
  and, just as importantly, reports it visibly when the bundle did not load. The dead
  `<mainPageIncluded>` parameter, which `teavm-maven-plugin` 0.15.0 silently ignores, is gone.
- **RMI services are discovered again.** The archetype's server module shipped no
  `META-INF/beans.xml`, so it was not an explicit bean archive and Weld did not reliably find the
  `@ApplicationScoped` implementations the RMI engine resolves through the bean manager — every call
  failed with "Rejected RMI call to unregistered service". The archetype now ships one with
  `bean-discovery-mode="annotated"`, which also keeps `@DataModel` POJOs from becoming beans.
- **Three `WELD-000119` warnings no longer appear on every boot.** `RmiSecurityFilter` and
  `DevLoginServlet` reference the servlet API, which is absent from the Helidon runtime classpath.
  They are now excluded from bean discovery when `jakarta.servlet.Filter` is not available, so they
  stay beans in a servlet container and go quiet everywhere else.
- **A version bump no longer produces "WELD-001409: Ambiguous dependencies".** `copy-dependencies`
  only adds to `target/libs`, so changing a dependency version without a `mvn clean` left both the
  old and the new jar there, every framework class on the classpath twice, and the app dead at
  startup with a message pointing nowhere near the cause. The archetype's server module now empties
  `target/libs` before refilling it, and tolerates a jar held open by a running server rather than
  failing the build.
- **Applications no longer fail to start unless they configure client-mode storage.** Helidon treats
  `@ConfigProperty(defaultValue = "")` as *no default*, so `TenantStorageProvider`'s `serverHost` and
  `serverSecret` failed CDI validation with "Cannot find value for key" in every application that
  never intended to use `CLIENT` mode — `components-showcase` among them. Both are now
  `Optional<String>`.

### Documentation

- `docs/index.md` and `docs/GETTING_STARTED.md` imported `com.zeroz4j.ui.components` — plural, and
  it does not exist. Corrected to `com.zeroz4j.ui.component` and `com.zeroz4j.ui.layout`, and
  `onClick(...)` corrected to `addClickListener(...)`.
- `docs/reference/glossary.md` claimed `@DataModel` requires getters and setters. Public fields work
  and always have — the archetype's own `Message` example uses one. The requirement now reads "a
  public no-arg constructor, and public fields or standard accessors".

### Added

- **A dashboard chart set** in the new `com.zeroz4j.ui.chart` package, written in Java against SVG
  and DOM — no JavaScript charting library is wrapped or loaded. Series colours resolve to DaisyUI
  semantic tokens (`var(--color-primary, …)`), so a theme switch recolours every chart with no
  redraw and no listener; each token carries a literal fallback so charts stay legible in
  applications that do not load DaisyUI.
  - **Charts** — `TimeSeriesChart` (lines, areas, stacks, shared crosshair, live legend),
    `RollingChart` (sliding window; redraw decoupled from data arrival so a stalled feed shows as a
    growing gap rather than a frozen chart), `Gauge`, `BarGauge`, `BarChart`, `Heatmap`,
    `Histogram`, `ScatterChart`, `DonutChart`, `Treemap` (squarified), `StateTimeline`,
    `StatusHistory`.
  - **Dashboard surfaces** — `PanelFrame` (title, actions, footer, and the ready/loading/error/
    no-data states), `TimeRangePicker` (selection published as a `ValueSignal`), `RefreshControl`
    (interval plus the age of the current data), `MetricTable`, `LogViewer`, `ColorScaleLegend`.
  - **Supporting types** — `Series`, `Threshold`, `ValueFormat`, `StateColor`, `Scales` (nice ticks,
    local-time tick alignment, TeaVM-safe formatting), `Palette`, `ChartBase`, `CartesianChart`.
- **`Sparkline` gained modes and annotation** — `AREA` (unchanged default), `LINE` and `BAR`, plus
  an optional baseline, min/max markers, delta colouring (green when the series ends above where it
  started, red below) and an explicit colour override. Gaps are honoured: a `NaN` breaks the line
  rather than reading as zero. The zero-argument constructor behaves exactly as before.
- **`KpiTile` computes its own movement** — `setDelta(current, previous, unit)` renders the absolute
  change, the percentage and a direction arrow. `setDirection` says whether a rise is good news,
  because that is a judgement and not arithmetic: falling free memory is bad, falling latency is
  good. Also a separate unit in smaller type, `setValueColor` for threshold colouring, and
  `sparkline()` to configure the trend. The existing `value`/`delta`/`trend` methods are unchanged.
- **`Js.onResize`** wraps `ResizeObserver`, so a component can redraw when its container resizes.
  A window `resize` listener misses a drawer opening or a split pane being dragged.
- **26 new showcase panels** in `components-showcase`, under new *Charts* and *Dashboard Panels*
  menu groups. This also backfills panels for `KpiTile`, `Sparkline`, `StatusDot`, `TokenMeter`,
  `LaneTimeline`, `SvgCanvas`, `PropertyGrid` and `VirtualScroller`, which the gallery had never
  covered. Sample data is seeded rather than random, so the gallery renders identically on every
  load and can be screenshot-tested.

### Notes

- **A component must not read a `Signal` in its constructor.** A signal read registers a dependency
  on whichever `Effect` is currently running, and components are typically constructed *inside* the
  effect that swaps views — so the component ends up invalidating the view that built it, which
  rebuilds the component, until the stack overflows. Mirror the value in a plain field and read
  that. `TimeRangePicker` documents the pattern.

## [0.4.0] — 2026-07-28

The last release was `v0.2.0`. **Version 0.3.0 was never tagged**, so its changes — UUID, `Instant`
and enum wire support, and per-module serializer registrars — ship as part of this release. If you are
upgrading from `v0.2.0`, everything below applies.

This release makes the framework fail loudly. Most of the changes below exist because a mistake used
to produce *nothing happening*: no exception, no log line, no clue.

### Breaking

- **Renamed to ZeroZ Stack.** Every module is now `zerozstack-*` instead of `zeroz4j-*`
  (`zerozstack-server-core`, `zerozstack-client`, `zerozstack-shared-api`,
  `zerozstack-ui-components`, `zerozstack-store-eclipsestore`, `zerozstack-apt`, `zerozstack-bom`,
  `zerozstack-archetype`). The groupId stays `com.zeroz4j`, and Java packages are unchanged.
  `zeroz4j` is now the family name only — the umbrella over this framework and
  [ZeroZ DB](https://github.com/ZeroZ4j/zerozdb) — so no product carries it and neither reads as
  the other's module. The repository moved to `github.com/ZeroZ4j/zerozstack`.

### Added

- **Persistence runs on ZeroZ DB**, bringing transactions, indexes, constraints and an optional
  network server. Inject `ZeroZDbNode` and send `DbCommand`/`DbQuery`: everything a command
  enlists commits atomically, and a command that throws persists nothing and restores the objects
  it touched in memory.
- **`zeroz4j.store.mode` chooses where data lives** — `EMBEDDED` (default, unchanged behaviour),
  `AUTO_SERVER` (own the store if free, otherwise join whoever has it, take over if they die), or
  `CLIENT` (connect to a ZeroZ DB server, so instances hold no data and can be restarted or scaled
  freely). The same service code runs in all three, so this is a deployment decision rather than an
  application one. See [docs/store-modes.md](docs/store-modes.md).
- **`node.localReads()`** for heap-speed reads: the live graph when this process owns the store, a
  continuously refreshed replica when it does not.

- **`zerozstack-client-wasm` is renamed to `zerozstack-client`.** A module should not be named after its
  compilation backend; the new name stays correct after the eventual move to WasmGC. Update the
  artifactId in your client module. The Java package `com.zeroz4j.client` is unchanged.
- **`SyncEngine.SyncScope` is replaced by `com.zeroz4j.api.Scope`**, now shared by LiveSync and
  events. Replace `SyncEngine.SyncScope.USER` with `Scope.USER`.
- **`SyncEngine.notifyChanged` throws** when the object has never been serialized to a client, instead
  of returning silently. Return the object from an `@RmiService` method at least once first — that is
  what registers its handle.
- **An unserializable event or shared-signal payload throws to the caller.** It was previously caught
  per session and logged, so `publish()` and `set()` appeared to succeed while reaching nobody.
- **A conflicting shared-signal declaration throws.** Two declarations colliding on one wire name used
  to be resolved by silently keeping the first. Re-running an identical declaration is still
  idempotent. The default wire name is the payload's class name, so give signals explicit names when
  you need more than one per type.
- **`bindValue` requires a writable signal** and throws otherwise, instead of silently degrading to a
  one-way binding. Use the new `bindValueReadOnly(signal)` when one-way is what you want.
- **`bindText` and `bindValue` return a `Disposable`** instead of `void`. They previously discarded it,
  so the binding could never be released.
- **`HasValue.addValueChangeListener` throws by default** rather than doing nothing, and
  `removeValueChangeListener` is added. A no-op made `Binder.setBean` appear to work while never
  writing to the bean. Fields extending `AbstractField` are unaffected.
- **`Binder.readBean` releases the bean held by `setBean`.** Previously `setBean(a); readBean(b);` left
  edits silently writing into `a`.
- **Three annotation-processor conditions are now compile errors**, not warnings:
  `@ClientWritable` without `@LiveSync`; `@ClientWritable` on a field with no setter; and a
  `@DataModel` field whose type cannot be serialized.
- **A `_Live` subclass is generated for every `@LiveSync` model**, not only `@ClientWritable` ones.

### Added

- **LiveSync objects are reactive.** Reading a `@LiveSync` object's getter inside an `Effect` or
  `Computed` subscribes to it, and an inbound sync re-runs whatever read it. Models stay plain POJOs.
  Notification is per object; updates arriving in one frame are batched so effects run against a fully
  applied graph. This removes the polling workaround previously required.
- **EclipseStore `Lazy<T>` fields on the wire.** A `@DataModel` may declare `Lazy<T>`; the reference
  travels as a session-scoped handle and never as its contents, and the client resolves it with a
  suspending RMI round trip on first `get()`. Handles are bound to the session they were disclosed to
  and released when it closes. No EclipseStore implementation class reaches the browser bundle.
- **An authentication SPI.** Implement `AuthenticationProvider`, register it through
  `META-INF/services`, and the development fallback is gone. It reports a name, roles and a **tenant**,
  receives query parameters, headers and any container principal, and can decline or refuse. Two
  registered providers is a startup error rather than an arbitrary choice.
- **`Scope.TENANT`.** Tenant-scoped events and LiveSync pushes, filtered on the tenant the provider
  attached to the session. A session with no tenant never matches, so tenant data cannot reach an
  unauthenticated connection by default.
- **`RmiRequestContext.getTenantId()`**, alongside the existing principal, roles and session id.
- **Scoped event publishing.** `publishToUser(topic, payload, principalName)` and
  `publishToSession(topic, payload, sessionId)`, plus `publish(topic, payload, scope, target)`.
  Previously every event reached every connected session with no principal check.
- **Rejected LiveSync mutations report a reason.** The writer receives a `0x15 REJECT` frame naming
  the model and the cause, alongside the corrective sync.
- **Serializer support for 16 more types** (tags `0x12`–`0x21`): `Set`, `BigDecimal`, `BigInteger`,
  `LocalDate`, `LocalTime`, `LocalDateTime`, `Duration`, `Optional`, all primitive arrays, and
  EclipseStore `Lazy`. `BigDecimal` travels as its exact `toString()` form, so scale and precision
  survive — safe for monetary amounts.
- **`Binder` gains `refreshFields()`, `hasChanges()` and `withRule(...)`**, the last letting
  constraints declared once on a `@DataModel` be reused in a `Binder` instead of restated.
- **`zerozstack-bom` manages the TeaVM artifacts**, so a client module cannot drift from the TeaVM the
  framework was built against.
- **A structured documentation pack** organised on Diátaxis, published as a MkDocs site, with a
  `decide/` section for choosing between the five state-propagation mechanisms.
- **Agent-facing configuration**: `context7.json`, `AGENTS.md` and `llms.txt`.

### Fixed

- **The test suite was red on `main`.** `RmiAnnotationProcessorTest` still expected
  `BinaryPackableRegistrar` after it was renamed per module.
- **The Maven archetype produced a project that could neither build nor run.** Four defects: a
  `1.0.0-SNAPSHOT` version pin against published artifacts, a missing `teavm-classlib`, a `<resources>`
  block that dropped `logging.properties` and served no web assets, and no `target/libs`. The version
  default is now filtered from the reactor so it cannot drift again.
- **A failed role check on a LiveSync mutation logged nothing at all**, making an absent log entry
  indistinguishable from an accepted write.
- **`Binder.removeBinding` left its listener attached**, so an unbound field kept writing to the bean.
- **The EclipseStore version is a single property.** A client/server mismatch previously surfaced as an
  obscure `cannot access UsageMarkable` compile error.

### Documentation

- The README described client code as compiled to WebAssembly. Every client module targets TeaVM's
  **JavaScript** backend — a deliberate interim choice while WasmGC support matures — and this is now
  stated as such.
- `@Secured` and `@RolesAllowed` are read **only from the `@RmiService` interface**. Two samples placed
  them on the implementation, where they are silently ignored.
- Only four of the seven examples require signing in; the docs claimed all of them did.
- `docs/AGENT_PROMPTS.md` instructed agents to spawn `new Thread(...)` while forbidding it elsewhere in
  the same file.
- Documents predating this release carry banners naming their specific known errors.

### Known gaps

Stated plainly, and listed in full in [Limitations](docs/reference/limitations.md):

- **Shared signals cannot be scoped.** A shared signal is one value the whole server agrees on, so
  per-user state belongs in a scoped event or in LiveSync.
- **Identity is fixed for the life of a connection.** Roles are read once at handshake, so a user whose
  roles change must reconnect. There is no handshake origin check and no session expiry.
- **A rejected `sharedWritable` write is still logged nowhere** and sends no reason, unlike a rejected
  LiveSync mutation.
- **`ObjectMapper` handles are never evicted**, so they accumulate for the process lifetime. Lazy
  handles do evict on session close.
- **`@Route` has no router**, so there is no framework-provided navigation story.
- **No example exercises `@ClientWritable`**; the LiveSync up-direction is covered only by tests.

## [0.3.0] — Never released

Developed on `main` but never tagged. Included in 0.4.0.

### Added

- Wire support for `UUID`, `Instant` and enums.
- Per-module serializer registrars (`BinaryPackableRegistrar_<suffix>`), so two modules with
  `@DataModel` types no longer collide on one classpath.

## [0.2.0] — 2026-07-23

Shared signals, server events, validation and the LiveSync up-direction; the `job-monitor`,
`inventory-crud`, `chat-events`, `chat-livesync`, `form-signup` and `todo-signals` examples.

## [0.1.0]

Initial public proof-of-concept: binary RMI over WebSocket, `@DataModel` serialization, EclipseStore
persistence, and the TeaVM UI component library.

[0.8.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.6.2...v0.7.0
[0.6.2]: https://github.com/ZeroZ4j/zerozstack/compare/v0.6.1...v0.6.2
[0.6.1]: https://github.com/ZeroZ4j/zerozstack/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/ZeroZ4j/zerozstack/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.2.0...v0.4.0
[0.2.0]: https://github.com/ZeroZ4j/zerozstack/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ZeroZ4j/zerozstack/releases/tag/v0.1.0
