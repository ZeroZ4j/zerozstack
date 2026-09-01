# Changelog

All notable changes to ZeroZ4j are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) — with the pre-1.0 caveat that breaking
changes may land in a minor version while the design settles.

ZeroZ4j is an experimental proof-of-concept. Read each release's **Breaking** section before
upgrading.

## [0.8.0] — 2026-09-01

Every control in the library can now be worked from a keyboard and says what it is; a build check
fails if a new one cannot. Overlays behave like overlays — a dialog takes over the page, Escape
closes things, and what sits above what is a named layer instead of a number somebody guessed. A
form field can be given a caption at last, and a refused value now says why in words the person can
read. On the wire, a `record` and a sealed family of types are both allowed, and fields inherited
from a base class stop vanishing. A server can be started inside a test in about a tenth of a
second. Leaving a screen now shuts the old screen down. And a project generated from the archetype
now comes up styled, out of real components, instead of as black-on-white text.

**Read the Breaking section before upgrading.** The three that catch most applications: a dialog now
takes the whole page and Escape closes it; `Drawer`, `Tooltip` and `Toast` are working components
rather than boxes you assembled yourself, so hand-written assembly around them now produces the
wrong result; and `onDetach` runs for the first time, so code you wrote and watched do nothing is
about to execute.

### Breaking

- **A dialog now takes over the page.** `open()` used to add a stylesheet class and nothing else, so
  the element never became a real dialog as far as the browser was concerned. Escape did nothing,
  the keyboard could wander out of it, the page behind it stayed live, and there was no dimmed area
  to click. A dialog shipped without a Close button was therefore a trap: in the application this
  came from, opening one froze everything until the page was reloaded, taking a half-written message
  with it. `open()` now hands the element to the browser, which is what Escape, the focus trap and
  the dim have always needed.

    **If your application relied on the page behind a dialog staying usable**, or on Escape doing
    nothing, two calls before opening give you the old behavior back:

    ```java
    dialog.setModal(false);              // the browser does not own it
    dialog.setCloseOnOutsideClick(false); // and a click outside does not close it
    ```

    Both are needed. The first is about what the browser does; a click outside the panel is drawn
    and handled by the component itself, so it survives the first call on its own.

    **If you only want to stop the user walking away from a question**, keep the new behavior and
    take the exits away one at a time with `setCloseOnEsc(false)` and
    `setCloseOnOutsideClick(false)`. Whenever you do, leave a button on the dialog: it becomes the
    only way out.

- **`setWidth` and `setHeight` on a dialog now size the visible panel, not the full-window
  overlay.** They did not exist on `Dialog` before this release, so nothing can break — but if you
  reached past the API for the first child element and set a width on it yourself, that code is now
  redundant and should be deleted before the two fight each other.

- **`Drawer` is now a working drawer, and it owns its own parts.** It used to be an empty box with a
  stylesheet class on it. To get a drawer out of it, an application had to build the hidden checkbox
  that opens it, the page area, the sliding panel, the dim beside it and the stacking number, and
  then add all five to the drawer itself. Every application did it slightly differently, and every
  one of them picked its own stacking number.

    The drawer now builds all of that. As a result **`add` no longer puts things on the drawer
    itself: it puts them in the sliding panel**, and `addToPage` puts them on the page the panel
    slides over. Old assembly code still compiles, so nothing will tell you — it will simply put
    your checkbox and your hand-built panel inside the real panel, and the drawer will look wrong.

    **If you assembled a drawer by hand, delete the assembly and keep only the contents:**

    ```java
    Drawer nav = new Drawer("Main menu");         // the heading, and the name a screen reader reads
    nav.addToPage(new Button("Menu", e -> nav.open()));   // the page the panel slides over
    nav.add(new Link("Home", "/"), new Link("Settings", "/settings"));   // the panel itself
    ```

    Escape now closes a drawer, a click on the dim beside it closes it, and while it is open the
    keyboard is held inside it. **If your drawer holds half-written input**, refuse the two exits
    with `setCloseOnEsc(false)` and `setCloseOnOutsideClick(false)`, and leave a button. **If you
    were using a drawer as a sidebar that lives beside the page** rather than over it, call
    `setModal(false)`: that turns off the dim and the hold, and leaves the page live.

- **A message is put on the page with `show()`.** `Toast` had no way to appear, so every caller
  appended the element itself — which meant the component was never started, and so Escape never
  closed a message, including in this library's own gallery. **Replace
  `body.appendChild(toast.getElement())` with `toast.show()`.**

- **A tooltip's words now go on the tip.** `setText` used to set the text of the wrapper the tooltip
  puts around your button, which put the words on the page next to the button, permanently and in
  the wrong place, while the tip itself stayed empty. They now go where the stylesheet reads the tip
  from, so the tip actually says something.

    **Nothing to change if you were using it as intended** — `new Tooltip("Deletes the file")`
    around a button now works instead of printing the words beside it. **If you were relying on
    `getText()` returning the wrapper's text**, it returns the tip's words now.

- **Taking something off the page now tells it.** Until now, `onDetach` almost never ran. Emptying a
  container by hand — `getElement().setInnerHTML("")` — is how every screen in every example was
  swapped for the next one, and it takes the old screen off the page without a word to it. So the
  screen you had just left kept working: its timer kept firing, its effect kept running, and both
  kept rebuilding a list nobody was looking at. In this library's own gallery that threw the
  keyboard back to the top of the page every second and a half, and every visit to that page added
  another timer that never stopped. Nothing errored and nothing was logged.

    There is now one way to swap what is inside something, and it runs `onDetach` on everything
    leaving, however deeply nested:

    ```java
    contentArea.replaceContents(nextScreen);                    // a container component
    Component.replaceContents(appRootElement, nextScreen);      // a plain element
    ```

    `removeAll()` and `remove(...)` on a container now do the same telling, and `add(...)` runs
    `onAttach` on everything it puts in rather than only the outermost part.

    **If you wrote `onDetach` and found it never ran, it runs now.** Read it again before you
    upgrade: code that was quietly dead is about to execute. **If you empty a container by hand
    anywhere, change it to `replaceContents` or `removeAll`** — a test now reads every Java file in
    the checkout on every build and fails it if anything writes an empty string into an element's
    HTML or takes every child out in a loop of its own.

- **A keyed list must be disposed.** `KeyedList` watches a signal for as long as it exists, and it
  never handed you anything to stop it with, so every one ever built is still watching. It now
  implements `Disposable`. **Keep the object you get back from `new KeyedList<>(...)` and call
  `dispose()` on it when the screen leaves**, normally from `onDetach`.

- **A tab is now a real button.** `Tab` was an `<a>` with nowhere to go. The browser leaves those
  out of the tab order entirely, so a row of tabs could not be reached by keyboard at all — not
  reached with difficulty, not reached in the wrong order: not reached. It is now a `<button>`, and
  it says which tab is showing.

    **If you styled tabs with a rule written as `a.tab`, change it to `.tab`.** If you colored the
    selected tab by adding `tab-active` yourself, use `setSelected(true)` instead: it adds the same
    class and tells a screen reader the same thing, so the two cannot drift apart.

- **A menu entry is now a real button, or a real link.** Every entry `Menu.addItem` built was an
  `<a>` with no address, which has the same problem: every menu built on this library was
  mouse-only, including the one down the side of its own component gallery. An entry that *does*
  something is now a `<button>`; an entry that *goes* somewhere is an `<a>` with a real address,
  built with the new `addLink`.

    ```java
    menu.addItem("Sign out", e -> signOut());       // does something: a button
    menu.addLink("Documentation", "/docs");         // goes somewhere: a link
    ```

    **If you styled menu entries with a rule written as `.menu a`, change it to `.menu li > *`**,
    which is what daisyUI itself uses and matches both shapes.

- **A link needs an address.** `Link` had no way to set one, so every link in every application
  built on this library was blue text the keyboard could not reach. `setHref`, `withHref` and a
  `Link(text, href)` constructor were added.

    **Go through your links and give each one a destination.** A `Link` with nothing to go to is not
    a link — if it does something, it is a `Button`, and `btn-link` makes a button look exactly like
    one.

- **Copying a value in a property grid is a button now, not a click on the text.** `PropertyGrid`
  copied a value when you clicked the value itself, and its only hint was a hover tip reading "click
  to copy" — an instruction somebody using a keyboard cannot follow. There is now a small copy
  button beside each value, named "Copy" plus the row's name, and the value is ordinary selectable
  text again.

    **If you were selecting on the shape of a row**, note that the value cell now holds a wrapper
    with the text and the button inside it, rather than the text on its own.

- **The copy control on a code block, and the fold-open heading on a diff, are buttons now.** Both
  were plain boxes with a click listener, so neither could be reached with Tab or pressed with
  Enter. The code block's word changed from `copy` / `copied!` to `Copy` / `Copied`, and the change
  is now announced as well as shown. **A stylesheet rule that named the old `div` will not match a
  `button`.**

**The rest of this section is framework-internal.** An application that writes `@RmiService`,
`@DataModel`, `@LiveSync` and signals is not affected. An application or test that reaches into the
framework's own classes may be — all of it follows from one change: the state a server keeps now
belongs to that server rather than to the whole Java process, so two servers can run side by side.

- **`Disclosures` — the record of what was sent to which browser — is no longer one record for the
  whole process.** Each server has its own, and the record is reached through that server.

    **`Disclosures.wasDisclosedTo(session, handleId)` still works unchanged**, because a connection
    knows which server it belongs to. It now throws if the connection belongs to no running server,
    rather than answering `false`.

    **If you called any of the other methods** — `record`, `sessionOpened`, `sessionClosed`,
    `disclosedCount` — **go through the server**: `runtime.disclosures().record(...)`. Get the
    runtime by injecting `ServerRuntime`, or from a test server with `server.disclosures()`.

    **`Disclosures.resetForTesting()` is gone.** Start a fresh server instead, or call
    `runtime.disclosures().clear()`.

- **Writing a frame now says which server is writing it, not only which connection.**
  `LazyHandles.setCurrentSession(id)` and the matching `setCurrentSession(null)` are gone.

    **Replace the pair with one bracket**, which closes itself:

    ```java
    try (LazyHandles.Write write = LazyHandles.writingTo(runtime, session)) {
        BinarySerializer.writeValue(buffer, object, mapper);
    }
    ```

    `LazyHandles.register(lazy)` and `LazyHandles.currentSession()` are unchanged, so a custom
    `LazyAdapter` needs no edit. **`LazyHandles.resolve(handle, sessionId)` now answers `null`
    outside a write bracket**, because outside one there is no server to ask. **`resetForTesting`,
    `sessionClosed` and `handleCount` are gone**; reach them through the server with
    `runtime.lazyHandles()`, or simply start a fresh server.

- **Anything that builds `WasmRmiServerEngine` or `SyncEngine` with `new` must give it a server.**
  Both take one by injection now.

    **In a test, set the field**: `engine.injectedRuntime = new ServerRuntime();` and
    `syncEngine.runtime = engine.injectedRuntime;`. **Better, use the new test harness**, which does
    it for you. Nothing changes for a real deployment: the container injects it.

- **`ServerSignalTransport.install(mapper)` now takes the server too:
  `install(runtime, mapper)`.** Every running server registers itself, and a shared-signal change is
  delivered to each server's own connections with that server's own mapper. **A shared signal's
  *value* is still one per process** — it is a `static final` field of one of your classes, which is
  what `Signals.shared` has always meant.

- **Broadcast helpers on `WasmRmiServerEngine` take the server as their first argument.**
  `broadcastSignalUpdate(name, value, mapper)` becomes
  `broadcastSignalUpdate(runtime, name, value, mapper)`, and likewise
  `broadcastSignalUpdateScoped`.

- **Three test hooks moved off the class and onto the instance.**
  `WasmRmiServerEngine.addActiveSessionForTesting(session)` and `clearKeepaliveBudgetForTesting()`
  are now called on an engine, not on the class. **`clearActiveSessionsForTesting()` is gone** —
  a new server starts with no connections, so **start a fresh one instead**.

- **`LiveMutexManager.configuredWaitSeconds()` is no longer static**, because the wait is now that
  server's setting rather than the whole process's. **Call it on the manager**:
  `locks.configuredWaitSeconds()`. `ownerOf(objectId)` and `trackedLockCount()` are public now, so a
  test outside the framework's own package can ask what is locked.

- **`RmiEndpointConfigurator.knownRoles` is gone.** The roles a sign-in is checked against are
  collected from each server's own services. **Read them with `runtime.knownRoleNames()`.**

- **`OriginPolicy`, `UploadLimits`, `UploadPasses` and `DevAuth` gained overloads that take a
  server's settings.** The old no-argument forms still work and still read the system properties, so
  nothing needs changing; the new ones are how one server applies a limit the process as a whole does
  not have.

- **Only a live object, and what is inside one, now has a lasting name on the wire.** Every object
  that crossed the wire used to be entered in a registry of names and left there for the life of the
  process. It now happens for a `@LiveSync` model and for the objects reachable inside one, and for
  nothing else — those are exactly the things a later message has to be able to point at again: an
  edit coming back from the browser, a re-sync after a dropped connection, a lock. Everything else
  travels as a value, with a short name that means nothing once its message has been read.

    Three things change that an application can notice.

    **An object a browser sends back as a call argument is now a copy.** Before, if a service handed
    an object to the browser and the browser passed it back into another call, the server did not
    build a new object from what arrived — it found its own object by name and wrote the incoming
    values straight into it. So a browser could rewrite data in the server's own graph simply by
    sending it back, with nothing checked. **If a call is meant to change server state, change it in
    the method**, from the values the caller sent. That is what the method is for, and it is where
    the check belongs.

    **A `LiveMutex` can be taken only on a live object.** Locking asks for an object by name, and an
    ordinary value no longer has one. This was never a usable feature for anything else: an object
    with no way to be synced or edited has nothing to serialize editors against.

    **A test that registers its own models by hand must say which of them are live.** The generated
    registrar does this for you in a real application. In a test that calls
    `BinaryRegistry.register(...)` itself, add
    `BinaryRegistry.registerHandleBearing(MyModel.class.getName())` for each model that stands in for
    a `@LiveSync` one.

- **The server keeps an object's name only while the application still holds the object.** Both the
  server's and the browser's registry of names now hold what they name weakly, so an entry
  disappears once the application itself has let go. Nothing changes for an object kept in a store, a
  root object or a field, which is where a live object already lives in any real application. An
  object that was built for one call and then dropped can no longer be restored after a reconnect:
  the client is told nothing was found — exactly as it is told after a server restart — the count is
  written to the server log, and the application fetches it again the way it first did.

- **A change a browser proposes is now checked against every model it contains, named or not.** The
  rule has not moved: a client may edit exactly the models marked `@ClientWritable`, wherever they
  appear. What has moved is that it is now enforced on a model the browser invented on the spot, and
  not only on one it pointed at by name. So a browser can no longer slip an unmarked model into a
  marked one by sending a fresh copy of it instead of naming the server's. **If a change starts being
  refused where it used to go through**, the message names the type: either mark that type
  `@ClientWritable`, or stop sending it up. A `record` is exempt — it has no setters, never changes,
  and travels as a value.

### Added

- **An application can be told when a change did not reach the server.**
  `LiveMutationRefusals.onRefused(...)` calls you back with the model and a sentence saying why,
  whenever an edit to a live object does not land — because the server refused it, or because the
  browser could not send it at all. With nothing listening, every refusal is written to the browser
  console as a full sentence. It is never silent.

    ```java
    LiveMutationRefusals.onRefused((model, reason) -> toast.show("Not saved: " + reason));
    ```

    The server already sent a reason back with every refusal, and the browser was throwing it away
    as an unrecognized message. It is now read, and the local failures were given the same route so
    that "your change did not happen" is one story rather than two.

- **The chat example now shows two-way editing.** `chat-livesync` grew a topic box: type in it and
  every other window follows, with no service call and no save button. It is the only example of
  the up direction of LiveSync, and its absence is part of why that direction stayed broken for a
  whole version — nothing anybody ran exercised it.

- **A form field can be given a caption.** Until now the only text a field could carry was the
  placeholder — the gray words inside the box — because `new TextField("Primary folder path")` sets
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

- **`setPlaceholder` and `getPlaceholder` on a text field and a text area.** The placeholder could
  only be set in the constructor, which is part of why it was doing the caption's job. Nothing has
  changed about what `new TextField("x")` does: it still sets the placeholder, and always will.

- **A dialog can be given a width, and a title.** A dialog is two boxes: a full-window overlay, and
  the panel you actually see. Everything worth changing lives on the panel, and until now the
  component handed out no way to reach it, so applications took the overlay's first child element
  and pushed stylesheet classes onto it — six separate hand-written copies of the same workaround
  across eleven places in one application, each written by somebody who could not find the previous
  one.

    ```java
    Dialog dialog = new Dialog("Delete the account?");
    dialog.setWidth("56rem");
    ```

    The panel is never wider than the window, so a width chosen for a desktop still fits a phone.
    The title becomes a heading at the top of the panel and the name the dialog is announced by;
    without one a screen reader announces nothing but the word "dialog". `setAriaLabel` names a
    dialog that has no room for a visible heading.

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

- **Escape closes every overlay that covers something.** A dialog, a drawer, a dropdown menu, a
  right-click menu and a message all answer to it. A tooltip hides until the pointer leaves and
  comes back. Anything with a reason to stay put can refuse — `setCloseOnEsc(false)`.

- **Opening an overlay moves the keyboard into it, and closing puts it back.** A dialog and a drawer
  both do this, so pressing Escape leaves you exactly where you were. A drawer also **holds** the
  keyboard while it is open: Tab goes round the panel and never reaches the dimmed page behind it. A
  dialog gets that from the browser; a drawer could not, so the library does it.

- **`Drawer` gained `open`, `close`, `isOpened`, `addCloseListener`, `setTitle`, `setModal`,
  `setCloseOnEsc`, `setCloseOnOutsideClick`, `setSlideFromEnd`, `add` and `addToPage`.**

- **`Dropdown` gained `open`, `close`, `isOpened`, `setCloseOnEsc` and `setLabel`,** and now shuts
  when you click anywhere else on the page.

- **A right-click menu can be used from the keyboard.** Opening it moves the keyboard onto the first
  entry, entries can be walked with Tab and chosen with Enter or Space, and Escape shuts it and puts
  the keyboard back on the row that was right-clicked.

- **A message is announced when it appears.** `Toast` is read out politely — it waits for whatever is
  being read to finish rather than interrupting. `setUrgent(true)` makes it interrupt, for the rare
  message that cannot wait. It never takes the keyboard, deliberately: a message arriving while
  somebody is typing must not move them out of the box they are typing in. `close()` takes it away,
  and so does Escape.

- **Overlays are put on a named layer instead of a number.** A stacking number is a bid, and the
  higher number wins — which works until two parts of one application each pick their own. In the
  application this came from, somebody picked a large number and an overlay still came out
  underneath something it should have covered.

    ```java
    import com.zeroz4j.ui.theme.Layer;

    Toast saved = new Toast("Saved");
    saved.setLayer(Layer.TOAST);
    ```

    You will rarely write even that: every overlay in the library puts itself on the right layer
    when you create it. The one exception is a tooltip, which rises to its layer only while the tip
    is showing — it wraps your control, and floating that control over every drawer on the page
    would be worse than the problem being solved. The layers, from the bottom up, are `PAGE`,
    `STICKY`, `DROPDOWN`, `OVERLAY`, `TOAST` and `TOOLTIP`. They are a hundred apart, so an
    application with a tier of its own has room to slot it in between two of them.

    **One thing beats all of them, and no number can reach it.** The browser keeps a place of its
    own above the whole page, called the top layer. An open modal `Dialog` is in it, so it is above
    everything else whatever the numbers say. If something has to cover a dialog, it has to be a
    dialog too. That is the whole reason the earlier hand-picked numbers lost. See
    [Stacking overlays](docs/guides/ui-layering.md).

- **Every component can be given a name for anybody who cannot see it.** `setAriaLabel` and
  `getAriaLabel` are on `Component`, so they are on all of them. Most controls name themselves out
  of the words inside them — a button that says "Save" is announced as "Save" — and this is for the
  ones that cannot: an icon on its own, a splitter, a canvas, a spinner. Passing `null` takes the
  name away again.

- **A button made of nothing but a picture can now be given words.**

    ```java
    new Button(Icon.of("trash"), "Delete this row");
    ```

    The old one-argument `new Button(icon)` still works and is now deprecated. A button with no
    words is announced as "button" and nothing else, and somebody using voice control has nothing to
    say to press it.

- **Four things that could only be dragged can now be worked from the keyboard.** A resize handle, a
  splitter, a drawing you pan and zoom, and the strip you drag to replay a run at a chosen moment.
  All four are reachable with Tab and moved with the arrow keys — a small step on its own, a large
  step with Shift, and Home and End for the two ends. The drawing also zooms with `+` and `-` and
  goes back to the start with `0`. Each says where it currently sits, so somebody who cannot see it
  still knows how far it has moved, and each takes a name of its own with `setAriaLabel`, which
  matters as soon as a page has two splitters on it.

    Dragging behaves exactly as it did. The keyboard writes the new position through the same code
    the mouse uses, so the two cannot drift apart.

- **The box you drop files onto is a control.** Tab reaches it, Enter and Space open the file
  picker, and it announces itself using the words `setTitle` and `setSubtitle` were given. Before
  this the only way to choose a file was to click the box.

- **A long list can be scrolled without a mouse.** `VirtualScroller` was not in the tab order, so
  Page Down and the arrow keys did nothing to it. It is now.

- **Things that draw now say what they are.** A spinner announces itself as "Loading" and
  `withAriaLabel("Loading your orders")` says what is loading. A percentage ring and a budget meter
  announce their number as it changes. The trail of links at the top of a page, and the main bar
  across it, announce themselves as navigation, so a screen reader can jump straight to either.
  A failed sign-in is now spoken, not only shown. Text arriving a word at a time — `StreamingText`,
  most often a language model's answer — is announced as it grows rather than sitting there
  silently.

- **And things that are only decoration now keep quiet.** The gray blocks standing in for content
  that has not arrived, the sun and moon on the light/dark switch, the blinking cursor in a
  streaming answer, and the tiny trend charts beside a number are all skipped by screen readers
  instead of being read out as noise. A trend chart that stands alone can still be named with
  `setAriaLabel`, which brings it back.

- **A keyboard and naming contract the build enforces.** `KeyboardAndNamingContractTest` reads the
  source of every component on every build and fails it when something can be clicked and cannot be
  used from a keyboard, when a control has no name, when a surface can only be moved by dragging it,
  or when a link has nowhere to go.

    It works out which element each listener was put on and what tag that element is, rather than
    searching for the word "aria" — a test that searched for a word would pass while a component
    stayed unusable, which is the fault it exists to stop. Which components count as controls is
    derived from the code, so a new one takes on the obligation the moment it is written, and there
    is no list to forget to update. The whole rule is written out in
    [Keyboard and naming](docs/guides/ui-keyboard-and-naming.md).

    **It cannot tell you whether a name is a good one**, and it says nothing about color contrast,
    focus rings, motion, or whether the order controls appear in makes sense. Those need eyes.

- **One browser proof harness, in `tools/ui-proof`.** It compiles the real component library to
  JavaScript, drives a real headless Chrome against it with real key presses, and asks the questions
  no rendering test can answer: where the keyboard actually is, whether Tab ever reaches the page
  behind an open dialog, what Escape does, which of two overlapping overlays is really on top, and —
  for every form field — whether the caption, the explanation, the asterisk and above all the
  sentence explaining a refused value are part of the text a person can see. It takes a screenshot
  of every state.

    ```bash
    bash tools/ui-proof/build.sh
    node tools/ui-proof/drive.mjs
    ```

    It found five faults in the overlays that the component gallery, which draws all of them
    correctly, showed no sign of. It also caught the field faults listed under Fixed below: a
    message can be a perfectly good sentence held on a perfectly good object while no user ever sees
    it, and only a real browser can tell you which.

    Two of these harnesses arrived in the same week, written by two people who did not know about
    each other. This is the survivor, and it now covers the form fields the other one covered as
    well as every overlay and every control in the library. It stays **outside the Maven build on
    purpose** — it compiles the library to JavaScript, which takes about a minute, and nobody
    building or releasing ZeroZ Stack should pay for that. **Building `zerozstack-ui-components` is
    faster as a result**: the retired harness compiled itself on every build unless you passed
    `-DskipDomProof`, and that flag no longer exists because there is nothing left to skip.

- **A notice can say what kind of thing it is, and carry a heading and a button.** `Alert` could
  only be a line of text in a colored box, and the color had to be spelled out as a stylesheet
  class name — `new Alert(msg, "alert-error")`, or `setThemeColor(ThemeColor.ERROR)`. So an
  application that needed a warning with a title above it, or a "Try again" button beside it, built
  its own. One built the same tinted box twice, in two files, neither knowing about the other.

    ```java
    add(Alert.caution("The disk is nearly full."));

    add(Alert.danger("Nothing was saved.")
             .withHeading("The upload failed")
             .withAction("Try again", e -> upload()));
    ```

    The four tones are named for what you are saying rather than for a color: `info`, `success`,
    `caution`, `danger`. Each notice carries a small mark showing its tone, so the four are still
    told apart by somebody who cannot separate the colors — `setIconVisible(false)` takes it away.
    A notice now tells a screen reader that it is a notice, and a failure interrupts where the
    other three wait their turn. Long text wraps instead of running off the side.

    `setThemeColor` and `new Alert(text, "alert-info")` still work and are now marked as things not
    to use. They put a stylesheet class name into application code, where nothing checks the
    spelling and no reader understands it.

- **Five sizes of text, by name — and a separate answer to how loud.** Every screen has text and no
  component owns it, so text is what applications describe over and over instead of asking for. One
  application wrote out its own idea of "quiet supporting text" a dozen times and finished with
  three sizes and four degrees of gray, on pages sitting next to each other. This library had done
  the same to itself: its own components spelled out quiet text thirteen different ways, in five
  degrees of fade, across fifty-six places.

    ```java
    add(TextStyle.PAGE_TITLE.span("Deliveries"));
    add(TextStyle.SECONDARY.paragraph("Nineteen stops left, updated a moment ago"));

    TextStyle.CAPTION.applyTo(somethingYouAlreadyBuilt);
    ```

    The five are `PAGE_TITLE` (the name of the screen), `SECTION_TITLE` (the heading over a group),
    `BODY` (ordinary prose), `SECONDARY` (supporting words, a step quieter) and `CAPTION` (the
    smallest label there is). There is one definition of each and no way to say "nearly that". Five
    is deliberate: a scale nobody can hold in their head gets ignored and typed out again, which is
    the problem it exists to end.

    How big a piece of text is and how loud it is were at first answered by that one name, so
    "small" and "faded" always arrived together. That left nowhere to put small text that must be
    fully present — an error line under a field, a value in a dense table. There is now a second
    axis, `com.zeroz4j.ui.theme.Emphasis`, with three steps: `FULL` (as present as the words around
    it), `QUIET` (a step back) and `FAINT` (as far back as text goes and still be text). Each of the
    five sizes names one of these as its own, so asking for a size alone is unchanged and still
    right nearly every time. Say a loudness as well only where the text disagrees with its size:

    ```java
    import com.zeroz4j.ui.theme.Emphasis;
    import com.zeroz4j.ui.theme.TextStyle;

    TextStyle.CAPTION.applyTo(errorLine, Emphasis.FULL);      // small, and nothing taken off it
    TextStyle.SECONDARY.span("3 of 12", Emphasis.FAINT);      // there, and out of the way
    ```

    **Quiet is a fade, not a color.** Both the quiet sizes and the three loudnesses fade whatever
    color they inherit rather than naming one, so the same words are right on a page, on a tinted
    notice, on a colored card and on a dark background — and two grays that were meant to match
    cannot drift apart.

    Each loudness carries that fade **twice**: as the class names the browser applies, and as a
    plain number for text drawn into a picture, where class names do not reach. One definition, two
    mechanisms — which is what stops a chart's axis labels and the words underneath the chart from
    fading by two different amounts.

- **The words a chart draws inside its own picture have four names.** A chart is drawn, not laid
  out, and text inside a drawing carries its size and its fade as numbers on the element rather than
  as stylesheet classes — so the type scale above stopped at the edge of the picture. The drawing
  then drifted exactly as everything else had: the twenty-four labels in the chart package were
  written at two sizes and **seven** different degrees of fade.

    `com.zeroz4j.ui.chart.PlotText` is the same idea with deliberately the same vocabulary, so
    somebody who has learned one can guess the other:

    ```java
    add(text(PlotText.LABEL, x, y, "Monday", "middle"));                 // the usual case
    add(monoText(PlotText.CAPTION, x, y, "41", "start"));                // a number on a bar
    add(monoText(PlotText.FIGURE, dialSize, cx, cy, "96 %", "middle"));  // the space picks the size
    ```

    `FIGURE` is the one big number in the middle of a dial or a ring, and comes at full strength.
    `LABEL` names a position — ticks, categories, axis titles, row names. `CAPTION` is a number
    printed inside the plot beside the mark it measures. `MESSAGE` is the sentence a panel shows when
    it has nothing to draw, and is a step *larger* than a label rather than smaller, because it is
    the only prose a chart writes and somebody has to read it rather than glance at it.

    Strength is the same second question here, answered by the same `Emphasis`, so an axis label and
    the legend beneath it fade by one number kept in one place. A test fails the build if a chart
    ever draws text without naming a role. You need none of this unless you are writing a chart of
    your own on top of `ChartBase`.

- **A timeline can be given its events, instead of being handed hand-built list items.** `Timeline`
  was an empty container: it drew the line but knew nothing about what went on it, so every
  application wrote its own forty lines of markup to make one step, and the component gallery did
  too.

    ```java
    Timeline history = new Timeline().vertical();
    history.addEvent("09:14", "Order placed", "Paid by card, delivered to the office address.");
    history.addEvent("Tomorrow", "Expected");
    ```

    The line joining one event to the next is drawn and redrawn as events are added, so nothing has
    to be told which one is first or last. An event's words are never shortened: a long description
    wraps inside its box, the box stops growing at about 20rem — `setEventWidth` changes that — and
    a timeline laid out in a row scrolls sideways rather than pushing the page out of shape. A step
    built by hand is still welcome, and `add` still takes one.

- **A status dot can be colored by one word and read as another.** `StatusDot` took a single
  string and used it for both the color and the hover text, so an application that has to color a
  dot by an internal state was forced to show that state to the reader — every dot in one console
  hovered as `DISPATCHED`. The two are now separate, and the reader's words are announced by a
  screen reader as well, which a dot with no text in it previously had no way to be.

    ```java
    new StatusDot("DISPATCHED", "Sent to a worker");
    dot.setState("FAILED", "Could not finish");
    dot.setLabel("Waiting for a slot");        // change only the words
    ```

    Passing one string still works and still means both.

- **`LaneTimeline.setLabelWidth` and `LaneTimeline.setLabelWrap`.** The first pins the width of the
  name column, for lining several timelines up with each other; leave it alone, or pass 0, and the
  column measures itself. The second lets a lane name too long for its column run onto more lines,
  growing that lane to fit; it is off by default, because lanes of one height are easier to scan.
  Both came out of the lane-name fault under Fixed below.

- **A record can now be a wire type.** Until now every type that crossed the wire had to be a class
  with a public no-argument constructor, a getter for each field and a setter for each field. That
  is a lot of typing for something whose only job is to carry three values from one side to the
  other, and it was never a design choice — it was a consequence of how the sending and receiving
  code was written. The receiving code made an empty object and then filled it in, which needs an
  empty constructor and needs setters.

    A record cannot work that way, so the generated code was changed. It now reads all the values
    first and builds the record last, in one go. Ten lines become one:

    ```java
    // Before
    @DataModel
    public class Money {
        private long amount;
        private String currency;
        public Money() { }
        public Money(long amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    // Now
    @DataModel
    public record Money(long amount, String currency) { }
    ```

    Everything a class could carry, a record can carry: text, numbers, dates, lists, sets, maps,
    other records, and ordinary classes. Validation annotations work the same way. It runs in the
    browser too — the browser compiler turns a record into ordinary JavaScript, including the
    equality and hashing it writes for you.

    Two things a record cannot do. It cannot be marked `@LiveSync` or `@ClientWritable`, because
    those are about editing an object after it exists and a record never changes — use a class.
    And a record cannot be part of a loop: if A holds B and B holds A, one of the two has to be a
    class. Sending a loop of records is refused with a message that says so, rather than failing
    somewhere far away. The reason is the same one that made records possible at all: the receiver
    cannot build a record until it has read every value inside it, and a loop needs the record to
    exist before that has finished.

    A record also cannot be a persistence root, for an unrelated reason given in
    [the wire protocol reference](docs/PROTOCOL.md#records).

- **A type can now be declared as "one of a known set".** Applications fake this today by adding a
  kind field and casting on the other side, which the compiler cannot check and which quietly goes
  wrong when somebody adds a new kind. Java already has the right tool — a `sealed` interface, which
  lists every type allowed to implement it — and the framework now understands it:

    ```java
    @DataModel
    public sealed interface Message permits Text, Ping, Attachment { }

    @DataModel public record Text(String author, String body) implements Message { }
    @DataModel public record Ping(long sentAt) implements Message { }
    @DataModel public final class Attachment implements Message { /* ... */ }
    ```

    A field, a list, a call argument or a return value can now be a `Message`, and what comes out
    the other end is a `Text`, a `Ping` or an `Attachment` — the real type, not a cast. A sealed
    abstract class works the same way, and anything the base class declares travels with each
    member.

    It is also safer than the kind-field version it replaces. Because the list of allowed types is
    fixed when the code is compiled, the receiving side knows it, and a message naming anything else
    is turned away before that type is created at all. The refusal says which type was named and
    which ones were allowed.

- **Two new type markers on the wire**, `0x22` for a record and `0x23` for a value of a sealed type.
  Applications never see these; they are listed in
  [the wire protocol reference](docs/PROTOCOL.md) for anyone reading the bytes.

- **A server can now be started inside a test, in the same process, in about a tenth of a second.**
  Take the new `zerozstack-server-test` artifact with **test scope** and start one:

    ```java
    try (TestServer server = TestServer.builder()
                 .named("orders")
                 .beans(OrderServiceImpl.class)
                 .start();
         TestConnection browser = server.connect("alice", "admin")) {

        server.bean(OrderService.class).approve(17L);

        assertEquals(1, browser.pushCount());
    }
    ```

    `connect(...)` gives back something the server treats as a real browser connection; the bytes
    land in a list the test can count and read instead of on a network. Every read waits for the
    server's writer to catch up first, so there is no sleep to guess at.

    The new module is separate because it starts a bean container, and a bean container has no
    business on an application's production classpath.

    Full instructions: [Testing an application](docs/guides/testing.md).

- **A server can be given settings of its own, so two servers in one process can be configured
  differently.** Settings are still system properties by default and nothing about an existing
  deployment changes. What is new is that a server may be handed its own:

    ```java
    TestServer small = TestServer.builder()
            .ignoringSystemProperties()
            .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 1024)
            .start();
    ```

    A setting a server is not given still comes from the system property. `ignoringSystemProperties()`
    cuts even that, which is what a test asserting on a limit wants: the answer then depends only on
    what the test set, not on which `-D` flags the build happened to run with.

    Every setting the framework reads is now named by a constant in `com.zeroz4j.server.ServerSettings`,
    in one place, with what it means and what it defaults to.

- **A generated project now tells an AI coding assistant what it is.** Every project the archetype
  produces gets its own `AGENTS.md`, stamped with the framework version that project resolves. It
  is one page: where the documentation lives, the three-module shape, how to build and run, and the
  ten rules that contradict ordinary Java habits and are therefore the ones an assistant gets wrong
  — annotations on the interface and not the bean, no thread around a click handler, no
  `setInnerHTML("")`, no shading, and the rest. It is deliberately not a copy of the framework's own
  `AGENTS.md`, most of which is about building the framework. Until now a new application started
  with an assistant that knew nothing, and the material lived in a repository the application
  developer had no reason to have cloned.

- **The rules now travel inside the jar, stamped with the version.** `zerozstack-shared-api` carries
  `META-INF/zeroz4j/AGENTS.md`, written during the build from the rule list in `context7.json`, with
  the version of that build in its heading. Every application resolves that artifact, so an
  assistant can read the rules for **the version the project actually depends on**, offline, with no
  documentation service involved. This matters because the Context7 index follows the framework's
  main line and has no versions: the moment 0.8.0 lands there, it starts describing 0.8.0 features
  to somebody working in an 0.7.0 project. Read it with:

    ```bash
    unzip -p ~/.m2/repository/com/zeroz4j/zerozstack-shared-api/0.8.0/zerozstack-shared-api-0.8.0.jar META-INF/zeroz4j/AGENTS.md
    ```

    It is generated rather than written, so it cannot drift from `context7.json`, and it costs the
    browser nothing: TeaVM does not put classpath resources in the client bundle, measured
    byte-for-byte before and after.

### Changed

- **A server setting has a new name: `zeroz.ws.maxQueuedFramesPerSession`.** It used to be called
  `zeroz.ws.maxConcurrentFramesPerSession` and it capped how many messages from one connection ran at
  the same time. Now that one connection's messages are handled one at a time — see Fixed — there is
  no such concurrency, and what the number bounds is how many may be *waiting*. The default is
  unchanged at 32, and a connection that fills the queue is slowed down rather than refused.

    **A deployment that set the old name keeps working: the old name is still read** when the new one
    is not set. Nothing has to change. Rename it when convenient.

- **Typing into a live object no longer sends a message for every character.** A change made on the
  client used to go to the server the instant the setter returned, so somebody typing into a field
  wired straight to a live object sent one whole-object message per key press. Measured in a real
  browser against the `chat-livesync` example, counting on the server: a short sentence, 38
  characters at ordinary typing speed, sent **38 messages before this change and 4 after it.**

    A change now waits for the changes to stop for **150 milliseconds**, and everything changed in
    that burst goes in one message. There is also a ceiling of **1 second**, measured from the first
    unsent change, so that somebody who types steadily and never pauses still has their work sent
    about once a second rather than not at all. Both numbers are settings:
    `LiveMutations.configure(pauseMillis, ceilingMillis)`, called before `Zeroz4jClient.connect`.

    **Nothing your application sends can arrive ahead of a waiting edit.** Somebody types into a
    field and immediately presses a button; the button's service call now goes on the wire behind
    the typing, so the server is never asked to act on a value the person has already replaced.
    Service calls, `LiveMutex` locks and shared-signal writes are all covered, and it needs no code.

    **If you wrote to the live object on blur instead of on every keystroke to avoid the cost, you
    can stop.** The advice to do that is gone from the documentation. Nothing breaks if you keep it.

    **If some code of yours genuinely needs the old behavior — a message the instant the setter
    returns — call `LiveMutations.configure(0, 0)` before connecting.** That switches the waiting
    off entirely.

    Two things to know, both stated plainly in [LiveSync](docs/LIVESYNC.md):

    - **Somebody who closes the tab or follows a link mid-burst loses what was still waiting**, up
      to the ceiling — about a second of typing. There is deliberately no rescue: a handler on the
      browser's page-leaving events was built and measured, and whether the browser gets the bytes
      out of the socket before it takes the page apart went both ways on the same machine on the
      same day. Something that works half the time invites an application to rely on it. Lower the
      ceiling for a screen where a second matters.
    - **Do not copy the server's broadcast back into a field somebody is typing in.** An accepted
      edit comes back to its own author carrying the value the server had a moment ago; writing that
      into the box deletes what has been typed since. This was nearly invisible before, because the
      value came back after every character and almost always matched. It now comes back up to a
      second late. Follow the incoming value everywhere except the field that has the keyboard —
      the `chat-livesync` example shows the pattern, and it was fixed there.

- **Every example now has a web address of its own, so you can leave several running.** Seven of
  them all answered on `localhost:8080`, which meant starting a second one killed the first with an
  error about the address being in use. Two people lost an afternoon to that in one week; one of
  them ended up writing a throwaway program just to see two examples at the same time.

    **If you have a bookmark to an example, it has moved.** The new numbers — and note that no
    example uses 8080 any more, because on a working developer's machine that is the number
    something else has already taken:

    | Example | Address | Example | Address |
    |---|---|---|---|
    | `routing-tour` | `localhost:8091` | `job-monitor` | `localhost:8087` |
    | `oidc-login` | `localhost:8081` | `form-signup` | `localhost:8088` |
    | `scoped-signals` | `localhost:8082` | `inventory-crud` | `localhost:8089` |
    | `pwa-install` | `localhost:8083` | `components-showcase` | `localhost:8090` |
    | `todo-signals` | `localhost:8084` | | |
    | `chat-events` | `localhost:8085` | | |
    | `chat-livesync` | `localhost:8086` | | |

    **If a number is already taken on your machine, say so when you start the example.** Every one
    of them understands the same three ways of being told, and each prints the address it settled
    on:

    ```bash
    run.bat 9000                                       # Windows, the seven with a script
    java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer --port 9000
    java -Dzeroz.port=9000 -cp "..." com.zeroz4j.example.server.ExampleServer
    ```

    Each example's own number is a constant at the top of its server file, so somebody copying an
    example as the start of an application can see it and change it.

- **The examples no longer load a stylesheet that warns about itself.** Every example page pulled
  Tailwind CSS from `cdn.tailwindcss.com`, and that address prints "should not be used in
  production" into the browser console on every single page load. In a framework whose examples are
  what people copy into their own projects, shipping a line that warns against itself — with no
  word anywhere about what to do instead — is not good enough.

    The examples now load Tailwind's own browser build from a pinned address instead. It does the
    same job, prints nothing, and is a published, versioned package rather than a preview service.
    Nothing looks different; this was checked page by page, light and dark.

    **What to do in your own application:** neither line belongs in something you ship. Both of
    these compile your styles in the visitor's browser, every time the page opens. A real
    application installs Tailwind once, builds one finished stylesheet, and serves that. There is a
    new section, "Where the styles come from", in `docs/UI_COMPONENTS.md` saying so, and every
    example page now carries the same note in a comment at the top.

    Both addresses are also now pinned to an exact version. They were not before, so the day the
    style library changed was the day the examples changed, with nothing in the repository having
    moved.

- **The library's own components stopped describing their text and started naming it.** Eighty
  places now ask for a size by name instead of writing out their own idea of it: thirty-five in the
  library itself — the whole chart and dashboard set, and the sign-in card — and forty-five across
  seven of the example applications. Inside the charts, twenty-four hand-written labels became four
  named roles. Three examples had gone a step further and grown a private
  helper of their own for making a piece of text with a list of style names attached; those three
  helpers are deleted.

    What you may see: a handful of labels that were 10 pixels are now 12, because the smallest
    named size is 12 and 10 was below what anybody should be asked to read. Quiet text is now a
    fade of the color around it rather than a named gray, so it stays correct on a dark page, a
    light one and a tinted panel without anybody choosing per surface.

### Fixed

- **A server started inside a test knew none of the application's own wire types.** Every real
  binding calls `BinaryRegistry.init()` at start-up — Helidon in `Zeroz4jServer.start`, a servlet
  container in `Zeroz4jServletBootstrap` — and `TestServer` did not. So the first test that sent or
  received any model of its own failed with `Unsupported type for GrowableBuffer:
  com.example.MyModel`, which reads like a fault in the model rather than a missing step in the
  harness, and there was nothing in the guide to say otherwise. `TestServer.start()` now does what
  every binding does. Nothing to change in a test that already worked around it: registering twice
  is harmless.

- **The server could handle one person's messages in the wrong order, and usually did.** Somebody
  types into a field that is kept in step with the server, then presses a button that calls a
  service. The typing is written to the connection first and the connection delivers it first — but
  the server then handed every message straight to a thread of its own, up to 32 from one browser at
  a time, and whichever finished first won. Measured in a browser: the button's call was decided on
  the value the person had already replaced in **15 runs out of 15**.

    **The server now handles one connection's messages one at a time, in the order they were sent.**
    Nothing sent after an edit can be handled before it. The same measurement on the fixed server
    used the value that had just been typed in all 15 runs. Nothing to change in your application.

    **If you added a `LiveMutex` around a save so it would see a recent edit, you can delete it.**
    That was the only fix before this release and the documentation told you to do it. A lock is for
    two *people* editing the same thing at the same time; it was never the right tool for putting one
    person's own messages in order.

    Two things stay outside the queue on purpose. The keepalive — the five-byte message that stops a
    proxy cutting an idle connection — is still answered immediately, so a busy connection is never
    cut for being busy. And a lock request that is waiting for somebody else to finish (up to 30
    seconds) lets the messages behind it through, because it has read something and changed nothing:
    without that, one person waiting for a lock would freeze everything else on their screen for half
    a minute.

    Ordering is per connection and nothing else. A slow call for one person does not delay anybody
    else: measured with one connection blocked on a call that never returned, a second connection was
    answered 19 ms later.

- **Two-way live editing did not work at all, and said nothing.** A person edited a field bound to
  a `@ClientWritable` object. Their own screen updated, because the change is shown before it is
  sent. Nothing reached the server, ever, in any application. The only trace was one line in the
  browser console.

    The browser does not hold the class you wrote — it holds a generated subclass of it, which
    reports edits and re-draws the screen. The code that puts an object on the wire looked up how to
    write it by the name of the class in front of it, and that generated name was registered
    nowhere. So the write threw, the client caught the error, printed the line and dropped the
    change.

    The generated registration now records both names, so the writer puts the name **you** wrote on
    the wire, which is the only one the server can read. Nothing to change in your application: an
    edit that has been going nowhere since this version began now arrives. `LiveUpDirectionWireTest`
    is the test that says so, and a browser proof types into a real field in one window and watches
    a second window receive it.

    **If you worked around this by adding a service method to save an edit**, that method still
    works and there is no need to remove it. Delete it only when you want the shorter form back.

- **A change that could not be sent was thrown away in silence.** This is what let the fault above
  survive a whole release: when the write failed, the client printed one console line, kept the
  change on the screen, and told nobody. A person was left looking at a value the server had never
  heard of, and no code anywhere could find out.

    A change that cannot be sent now does two things. The client asks the server to re-send that
    object, which puts the screen back to what is actually stored. And the failure is reported to
    `LiveMutationRefusals` — see Added — so the application can say so. With no listener registered
    it is written to the console as a sentence saying the change was not saved.

- **The "connection lost" bar was invisible behind an open dialog.** The bar that appears when the
  connection to the server drops carried the largest stacking number a browser accepts — and still
  lost to a dialog, because a dialog is drawn in a place of the browser's own that sits above the
  whole page and that no number can reach. So the one moment a person most needs telling that their
  work is not being saved — halfway through filling in a dialog — was the one moment they were told
  nothing at all.

    The bar is now put in that same place, as a popover rather than a dialog of its own. It appears
    over everything, and it takes nothing: the keyboard stays where it was, half-typed text is
    untouched, and the page behind carries on. Nothing to change in your application.

    Two limits worth knowing. A dialog **opened after** the bar appears is drawn over it, because
    inside that top place things stack in the order they arrived. And a browser older than Chrome
    114, Safari 17 or Firefox 125 has nowhere to put it, so there the bar behaves exactly as it did
    before — visible everywhere except under a dialog.

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

- **The old stylesheet variable held on to a message after the value was corrected.** `Binder` set
  `--error-message` when a check failed and never emptied it again, so an application that did
  display it kept showing the old complaint about a value the person had already fixed. It is now
  emptied when the field goes back to being valid.

- **A rating, a swap and a theme switch could be given a caption that named nothing.** All three are
  built from several parts — five stars, or a hidden checkbox inside a decorative wrapper — and the
  caption was tied to the wrapper rather than to anything a browser treats as a control. The words
  appeared, and that was all: clicking them did nothing, and a screen reader read the field as
  having no name at all, which is the fault the caption was added to fix. A rating is now announced
  as a named group, like a radio group; a swap and a theme switch name the checkbox inside them, so
  clicking the caption focuses and flips it. Nothing changes in your code.

- **The theme switch had no name.** `ThemeController` is a tick box wearing two pictures, and
  pictures say nothing, so a screen reader announced "checkbox" and stopped and voice control had
  nothing to say to it. It sits in the side panel of nearly every application built on this
  library, so that was every page of every one of them. It is now called "Dark theme" by default.
  Give it a caption of your own with `setLabel("...")` and that replaces the built-in name, so the
  two cannot disagree.

- **The five stars of a rating announced nothing.** Each star is a radio button drawn by the
  stylesheet, so there was nothing inside it to read and a screen reader said "radio button" five
  times. They now say "1 star", "2 stars", and so on.

- **Copying something threw the keyboard away.** Both Copy buttons — the one on a code block and
  the one beside a row of properties — copy by making an invisible text box, selecting it and
  deleting it again. Selecting it took the keyboard off the button, and deleting it left the
  keyboard on nothing, so anybody who pressed Copy without a mouse was dumped to the top of the
  page and had to Tab all the way back down. The keyboard now goes back where it was. The browser
  proof found this; no amount of looking at the screen would have.

- **A drawer put a control in the tab order that nobody should ever reach.** The hidden checkbox
  the stylesheet watches to decide whether the panel is in or out is plumbing, not a control, but
  a checkbox is in the tab order by default. The keyboard stopped on it and a screen reader
  announced nothing at all. It is now skipped.

- **A menu entry never let go of its listener.** `MenuItem.addClickListener` handed back something
  that looked like a way to unregister and removed nothing: it built a second wrapper around the
  listener and asked the browser to remove that one instead of the one it had added. A screen that
  rebuilt its menu leaked a listener every time.

- **A budget meter kept showing a percentage after the budget was taken away.** `TokenMeter` set
  its hover text on the branch that has a cap and returned early on the branch that does not, so
  switching to "no limit" left the old sentence in place claiming a percentage that was no longer
  true.

- **A dialog taken out of the page while it was open** used to leave the browser believing it was
  still open, so it could not be shown again. It is now closed properly when it is removed, its
  close listeners are told, and a leftover open marker is repaired before it is shown again.

- **A dialog given a width hung off both edges of a narrow window.** `setWidth` capped the panel at
  100% of its parent, but the panel's parent is a box sized by the panel — so the cap was the
  panel's own width and capped nothing. A panel asked to be 56rem stayed 56rem in a 380-pixel
  window. It is now capped against the window itself, and keeps a small margin. `setHeight` had the
  same fault and the same fix.

- **Three overlays picked their own stacking number, and one of them lost.** A drawer's number was
  written by hand in every application that used one, because the component supplied none — its
  panel is now on `Layer.OVERLAY`. A dropdown's panel sat one step above the page, which lost to
  almost everything, and is now on `Layer.DROPDOWN`, above a sticky header, which is where dropdowns
  are usually opened from. A right-click menu carried a hand-written 1000 — exactly the guess the
  layer scale replaces — and lost to an open dialog anyway; it is now on `Layer.DROPDOWN` too.

- **Setting the text on a notice replaced everything inside it.** `Alert` kept its words in a box of
  its own, but `setText` wrote over the notice's whole contents and threw that box away. Nothing
  visible broke, because a notice held nothing but its words — but now that one can hold a heading
  and a button, changing the message would have thrown those away too. `setText` now changes the
  message and leaves the rest of the notice alone.

- **One long word pushed the whole page sideways, in five components.** Words in a menu entry, a
  tab heading, a step name, a badge and the name of a metric have nowhere to break, and each
  control's width follows its words — so one German compound noun, long file path or email address
  made the whole page scroll sideways. 2,773 pixels of it, in this library's own gallery, in a
  window the width of a telephone. `Menu` (entries and section titles), `Tab`, `Steps`, `Badge` and
  `KpiTile` now let a long word break and never grow wider than what they were put in. A trail of
  steps still scrolls inside itself when the steps genuinely do not fit, which is what it was
  always meant to do.

- **A tooltip whose words had no spaces in them drew 2,719 pixels wide.** The tip is capped at
  20rem and wraps, but only where the words give it somewhere to wrap — so a long address, file
  path or stack frame ignored the cap and took the page sideways. It now breaks a word that has no
  break in it. A tip is still drawn on the side it was told to sit on, so one placed against the
  right-hand edge of the window is still partly off it.

- **A replay timeline was drawn at least 600 pixels wide whatever it was put in,** so on a
  telephone-width panel it hung out past the edge and took the page with it. It is now drawn as
  wide as it is given, down to a name column plus 160 pixels, and scrolls inside itself below that.
  Its row of play-speed buttons wraps onto a second line instead of sticking out.

- **A lane in `LaneTimeline` could only be named in twelve characters.** The name column was 90
  pixels wide and could not be changed, and anything longer was silently cut — `worker-0
  qwen36-27b` arrived as a stub. The column is now measured from the longest name, between 90 and
  260 pixels, hovering a name always shows it whole, and `setLabelWidth` pins the column where a
  fixed one is wanted.

- **A lane name was still being cut down to a shorter string before it was drawn.** Widening the
  column moved the line where the cut happened; it did not stop the cutting. A name too long for
  whatever width the column ended up with was chopped in Java and the short copy was drawn, so the
  characters past the cut existed nowhere on the page: they could not be selected, could not be
  found by the browser's search, and were never read out. A shortened name also looks exactly like
  a short one, which is why nobody noticed.

    The whole name now goes into the page and the browser decides what to do when it does not fit —
    it fades the end away, and hovering still shows the lot. `setLabelWrap(true)` runs it onto more
    lines instead. Either way nothing is thrown away, and a test now fails if this component ever
    starts cutting text again.

- **A status dot given only a state hovered as that state.** `new StatusDot("DESIGN_REVIEW")` put
  `DESIGN_REVIEW` under the mouse and read `DESIGN_REVIEW` out to a screen reader, so a console
  full of dots shouted the code's own vocabulary at people who do not work on the code. A state on
  its own is now reworded into ordinary language — "Design review" — before it is shown.

    This is a fallback, not a substitute for saying what you mean: it can only reword the name it
    was given, so `new StatusDot("FSM_7", "Waiting for a slot")` is still the right way to write it.
    Words that already read like a sentence are left exactly as they were.

    **If you were relying on the hover text being the constant** — a diagnostic console, say — pass
    it twice: `new StatusDot("DESIGN_REVIEW", "DESIGN_REVIEW")`.

- **A model that extended another model silently lost the base class's fields.** Moving what several
  models share up into a base class is the most ordinary refactor in Java. The generated code only
  ever looked at the fields a class declared itself, so everything on the base stopped arriving —
  no error when you compiled, no error on the wire, just missing data, and nothing pointing at
  inheritance as the cause. Base fields now travel with the model.

    An abstract model now gets no serializer and no registry entry: nothing can construct one, so it
    exists only to hand its fields down. Declaring a field as an abstract model type still works.

    Two shapes are refused when you compile, rather than losing data quietly. Extending a class that
    is not a `@DataModel` and that has fields of its own — annotate the base, or move the fields
    down; a base class with no fields is fine as it is. And declaring a field with a name a base
    class already uses, where one value would overwrite the other.

- **Two models that referred to each other crashed with a stack overflow.** `A` holds a `B`, `B`
  holds an `A`, and the generated code never stopped. The same models reached through a list always
  worked, which is why this survived: only a field declared as a model type took the broken path.
  Such fields are now written the same way as everything else, so a loop closes on the same object it
  started from, and the same object in two fields arrives once instead of twice.

    A model nested inside a `@LiveSync` one was also being rebuilt as a plain object, so edits to it
    were invisible. Same cause, fixed by the same change.

    This makes a nested model a few bytes larger — it now carries an identifier and its type name.
    Lists were already paying that, so the cost is set by how many model-typed fields you have, not
    by how much data you send.

- **A model class nested inside another class got a serializer that did not compile.** The generated
  code referred to it by the wrong name. Nesting is the natural way to write a sealed family, so this
  surfaced immediately once sealed types were supported.

- **Two servers started in one Java process used to share the list of open connections, and several
  other things besides.** An event published on the second server arrived at the first server's
  browsers. The record of which objects had been sent to which browser was one record, so an object
  one server sent could be read back — or locked — through the other. Deferred-data names, the
  keepalive budget and the shared-signal delivery path were shared the same way.

    The worst of it was not that the answers were wrong, it was that they looked right. An
    application that hit this had two of its three browser tests passing while asserting nothing at
    all: they were watching a connection somebody really was writing to, just not the server under
    test. Its only way out was one test per process.

    All of that state now belongs to one server. A new `ServerRuntime` object owns it, one per
    server, and every connection carries a reference to the server it was opened on, so code holding
    only a connection reaches the right one.

- **The framework looked its own beans up in whichever bean container had started first.** Several
  places asked `CDI.current()`, which picks a container by thread context class loader — so with two
  servers in one process, the second server's engine could resolve the first server's services, and
  a connection closing on one could fire its event into the other. The engine now uses the container
  it was itself built by.

- **Silent failure is now loud.** Four cases that used to succeed quietly while doing nothing now
  throw, with a sentence saying what is wrong:

    - handing one server's connection to another server, which names both servers;
    - writing to a connection that was never opened on any running server;
    - driving a server that has already been shut down;
    - a WebSocket endpoint that reached no server at all, which used to surface as a
      `NullPointerException` on the first connection.

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

- **That published-text check called a Spanish name corruption.** It reverses the misreading
  arithmetically, so any two bytes that happen to form valid UTF-8 come back looking like "the
  original" — and `Íñ`, in a name like `Íñigo`, comes back as a Hebrew accent that attaches to a
  letter and cannot stand on its own. Nobody typed that, so nothing was misread. The check now
  ignores a repair made only of marks that attach to another character, or of characters with no
  printed form. Every corruption it was written for repaired to ordinary text — dashes, curly
  quotes, umlauts, the play and pause marks — so nothing it was catching stops being caught.

- **The keyboard walkthrough reported a nested folding section as unreachable, and it was not.** The
  gallery's walkthrough looked only at the nearest `<details>` above a control when deciding whether
  it was folded away. Chrome lays out everything inside a shut section and then declines to paint or
  focus it, so a section nested inside a shut one still has a size — and its handle was reported as
  a control Tab could not reach, on every run, at all three widths. It now checks every folding
  section above the control. Nothing in the component library was wrong.

- **Six example applications had their logging setting saved in the wrong kind of file, so it did
  nothing.** The file said to log in full detail. It had been written by a Windows shell, which
  saves text in a form Java's settings reader does not understand, so the reader saw nonsense,
  ignored it, and the examples logged at their normal level instead. The files are now saved
  normally and the setting takes effect. Only the examples were affected; nothing in the framework
  itself read these files.

- **A project generated from the archetype came up completely unstyled.** Its page did not load the
  two stylesheets every component in this library wears, so a brand-new application opened as
  black-on-white text with no card, no spacing and no colors — the single most-seen screen the
  framework has, and broken, because nobody who works on the framework ever generates a fresh
  project. The generated `index.html` now loads the same pinned, warning-free stylesheet the examples
  do, and the generated first screen is built out of real components with its text asking for a size
  by name. The archetype smoke test now checks that the page is styled, because the checks that were
  there only read words and would have gone on passing.

- **The generated project taught the very habit this release fixes.** Its startup code emptied the
  page element with `setInnerHTML("")` — so every application ever generated would have started life
  with the fault described under Breaking above, copied from the template. It now uses
  `Component.replaceContents(appRoot, card)`, and the comment beside it says why. The detach check
  added this release is what caught it.

- **Seven examples carried a second, dead copy of their page.** Each client module held an
  `index.html` under `src/main/resources/public/`, which is never served: static files are served
  from `META-INF/resources`, and each example's server module already supplies one there. The dead
  copies had drifted — they still carried the old light-gray body the served page stopped using —
  so anyone reading them was reading the wrong file. They are gone.

- **An application ran out of memory because nothing was ever forgotten.** Every object that had ever
  been sent to a browser was kept, on the server and in the browser both, and there was no code
  anywhere that removed one. A screen that refreshes itself builds new objects every time it draws,
  so the pile grew for as long as the program ran. Measured on a case built to match the report — two
  hundred rows redrawn five thousand times — the old code held two million objects and 365 MB, and
  could not finish at all in the half-gigabyte of memory the real application had. The same run now
  holds nothing and finishes comfortably. Two separate changes were needed and both are in: only the
  objects that need a lasting name get one, and a name no longer keeps its object alive.

- **A browser tab in that state could never connect again.** After a dropped connection the browser
  asks the server to send back the current state of everything it holds, and it asked for everything
  it had ever seen — a list of two million names in one 84 MB message. A message that size is refused
  (the limit is 4 MB), which closes the connection, which makes the browser reconnect and send the
  identical message. The list never got shorter, so the tab was finished: no amount of waiting fixed
  it, and only the person reloading the page did. Three things end that loop. The list is now only
  what the screen still holds. It is capped at ten thousand names, and a browser over the cap throws
  the list away rather than sending it, writing one line to the console that says so and says why.
  And a browser that has thrown its list away simply fetches its objects again, the same as after a
  server restart.

- **A browser could overwrite the server's own data by handing an object back.** An object a service
  returned came back with the server's name for it attached. Passing that object into another call
  made the server find its own object by that name and write the browser's values into it — before
  the method ran, with nothing checked, and with no way for the application to know. Ordinary values
  no longer carry a name that survives their message, so what arrives is a new object built from what
  was sent, and the server's own data is untouched.

- **A model a browser invented could be smuggled into one it was allowed to edit.** A change reaching
  a model that is not `@ClientWritable` was refused, but only when the browser pointed at the
  server's own copy by name. Sending a fresh copy of the same restricted model instead went straight
  through: nothing was overwritten, but the browser's version was attached to the object it was
  editing and then broadcast to everybody, which is the same outcome by another route. Every model in
  a proposed change is now checked, named or not.

### Documentation

- **A twelfth example, and it is the one that exercises the new wire shapes.**
  [`payments-datamodels`](zerozstack-examples/payments-datamodels) is a small payments desk on port
  **8092**: put things in a basket, say how the customer paid, take the payment, give money back.
  All three shapes a wire type can take are in it because the domain wanted them — amounts and
  basket lines are records, the way somebody paid is a sealed family with a record per kind, and a
  payment and a refund share a base class. They travel nested inside each other and inside
  collections, in both directions.

    Until this shipped, **not one example used a record, a sealed family, or a model extending
    another model.** All three were new in this release, so nothing anybody could run demonstrated
    any of them, and a total failure would have looked exactly like nothing happening — which is how
    the whole up direction of LiveSync stayed broken for a version.

    Both halves of the round trip are visible on purpose: the server logs every call field by field,
    naming the real kind of each sealed value it received, and the screen prints what came back and
    what shape it was in. Its test drives real frames through a `TestServer` rather than calling the
    service, because a direct call runs no serializer and would pass whatever was broken.

- **Three examples now test a service through a real connection instead of around it.**
  `payments-datamodels` proves its models survive the wire. `chat-livesync`'s test used to be one
  assertion of `true` and a comment wondering how the service might be given a store; it now checks
  that a message one person sends is pushed to every open browser, that clearing the history needs
  the admin role, and that a visitor who never signed in is refused. `form-signup` gained a test
  that a browser ignoring the validation rules is stopped at the server, which is the point that
  example exists to make and was checked nowhere.

    [Testing an application](docs/guides/testing.md) gained the two things that catch people out
    when driving a frame in: the answer arrives a moment after `send` returns, and it is not
    necessarily the last frame — match it by the number the request was sent under.

- **`chat-livesync` now tells the person when their edit was refused.** It registers a
  `LiveMutationRefusals.onRefused(...)` listener, shows the reason above the topic box, and puts the
  box back to the server's value — which the box otherwise avoids doing while somebody is typing in
  it, and must do here, because after a refusal what is in it is a value that exists nowhere else.
  The refusal is easy to cause on purpose: the topic is capped at eighty characters by an annotation
  on the model and the box does not stop you typing more.

- **How far object identity reaches is now written down.** The same object in two fields of one model
  arrives once; the same object as two separate items of a top-level list arrives twice. So `==` is
  not a safe way to ask whether two things that came off the wire are the same one — compare by
  identifier, or with `equals`. This was true before and stated nowhere.

- **Four new pages.** [Keyboard and naming](docs/guides/ui-keyboard-and-naming.md) writes out what
  every control in the library owes a person with no mouse, and what the build check does and does
  not enforce. [Stacking overlays](docs/guides/ui-layering.md) explains the layer scale and the one
  thing no number can beat. [Testing an application](docs/guides/testing.md) covers starting a
  server inside a test. [Declaring the types that cross the
  wire](docs/guides/data-models.md) says when to write a class, a record or a sealed family, and how
  to share fields with a base class. `docs/UI_COMPONENTS.md` gained sections on naming a field,
  naming text sizes, where the styles come from, and swapping what is inside something.

- **The documentation is American English, and a check now says so.** The style guide had asked for
  American spelling since it was written, and every page ever written was British — `colour`,
  `behaviour`, `catalogue`, `grey`. One of the two had to give, and the guide won: 110 lines across 25
  files are converted. `DocumentationSpellingTest` reads every Markdown file in the checkout plus
  `llms.txt` and `context7.json` on every build and fails naming the file, the line, the word and
  the replacement.

    Backticks and code fences are exempt, and so is `Flavour` with a capital F, because
    `FlavourWrapper` is a real class and the documentation has to spell a real name the way the code
    does. The word list is explicit rather than a "-ise becomes -ize" rule, which would fire on
    *advertise*, *exercise* and *surprise*.

    **The source code was deliberately left alone.** 388 British spellings remain in comments,
    javadoc and user-facing strings across 111 Java files — 66 of them inside strings a test could be
    asserting on. Converting those is a code change and is a separate decision, so for now the
    documentation is American and the code is not.

- **A changelog entry now has written rules.** What one has to do, and why, is in
  [CONTRIBUTING.md](CONTRIBUTING.md#write-the-changelog-entry-for-the-person-upgrading): prose rather
  than commit subjects, every breaking change naming its fix, concrete numbers, ordinary words, and
  one merged entry per release rather than one block per branch. This entry was assembled from nine
  branches that had each appended their own, and the format existed only in one person's habit.

- **`docs/UI_COMPONENTS.md` no longer prints a component count.** It said 106 against roughly 115
  concrete classes, and it had been wrong before in the same direction: written once, then left
  behind as the library grew. The list is the answer.

- **A stale version number now fails the build.** `VersionStatementTest` reads every Markdown file
  in the checkout plus `llms.txt` and `context7.json`, and compares what they claim the framework
  is against `<revision>` in the root `pom.xml`. `llms.txt` was found saying 0.4.0 and calling the
  router unimplemented — two releases stale, with nothing to catch it.

    The hard part is that a sentence about the past keeps its own number. A previous bump walked the
    documentation incrementing every version it saw, leaving pages that claimed work done in 0.5.0
    had been done in the version being prepared. So each mention is classified from the words next
    to it: `(0.7.0+)`, `since 0.6.1`, `before 0.8.0`, `added in 0.6.0` and the like are history and
    are left alone; anything with no marker saying *when* is read as a claim about the version you
    are on, and has to match. A bare `in` is not a marker, deliberately — "every known gap in 0.8.0"
    goes stale and "the fix landed in 0.6.0" does not, and the difference is the verb. Anything it
    cannot place is reported rather than passed.

    It found five wrong numbers on its first run: four example READMEs telling you to launch a jar
    two releases old, and a guide crediting the `package` profile to 0.5.1, a release that was never
    made.

- **The release checklist now covers the documents an AI coding assistant reads.** `AGENTS.md`,
  `llms.txt`, `context7.json` and `docs/AGENT_PROMPTS.md` state the version in prose and nothing in
  `${revision}` reaches them; the checklist had mentioned none of the four. It is now step 7 in
  [RELEASING.md](RELEASING.md), with the check above doing the finding.


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
  address rather than the live connection, which is what makes progress and canceling work. The
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
  manifest per request, because in a multi-tenant product the name, icons and color belong to the
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
  old behavior, since nothing read the annotation.
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
  and DOM — no JavaScript charting library is wrapped or loaded. Series colors resolve to DaisyUI
  semantic tokens (`var(--color-primary, …)`), so a theme switch recolors every chart with no
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
  an optional baseline, min/max markers, delta coloring (green when the series ends above where it
  started, red below) and an explicit color override. Gaps are honored: a `NaN` breaks the line
  rather than reading as zero. The zero-argument constructor behaves exactly as before.
- **`KpiTile` computes its own movement** — `setDelta(current, previous, unit)` renders the absolute
  change, the percentage and a direction arrow. `setDirection` says whether a rise is good news,
  because that is a judgment and not arithmetic: falling free memory is bad, falling latency is
  good. Also a separate unit in smaller type, `setValueColor` for threshold coloring, and
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
- **`zeroz4j.store.mode` chooses where data lives** — `EMBEDDED` (default, unchanged behavior),
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
- **A structured documentation pack** organized on Diátaxis, published as a MkDocs site, with a
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
