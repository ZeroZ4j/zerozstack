# UI proof

Every control in the library, on one page, driven by a real browser with real key presses.

## Why there is one of these and not two

There were two, for about a week, written by two people who did not know about each other. One
built a page of form fields, compiled it with TeaVM on every build of `zerozstack-ui-components`,
ran Chrome with `--dump-dom` and read verdicts back out of the printed page. The other is this one.

This one survived for one reason: **only a real driver can press a key and move the keyboard.**
When JavaScript makes up a keyboard event and dispatches it, the browser runs the listeners and
moves nothing — focus stays exactly where it was. A harness built that way can never answer "does
Tab go where it should", "does Enter press this", "is there a control here Tab can never reach",
which are the questions this page exists for. Playwright's key presses go in through the browser's
own input path, so focus moves the way a person's would.

The second reason is that it is **deliberately not part of the Maven reactor.** It compiles the
library to JavaScript, which takes about a minute, and nobody building or releasing ZeroZ Stack
should pay for that. The retired harness did exactly that on every build of the components module,
which is why that module now builds faster.

The component gallery is not a third one of these. A gallery shows you that a component **draws**;
it cannot show you where the keyboard is, what Escape does, or which of two overlapping things is
really on top.

## What it is

- `src/main/java/.../KeyboardProofPage.java` — the page, built out of the **real** components from
  `zerozstack-ui-components`. No hand-written HTML: hand-written HTML would prove that the markup
  behaves, not that the library produces that markup.
- `web/index.html` — the shell, with Tailwind and daisyUI from their usual addresses.
- `drive.mjs` — opens Chrome, does things to the page, and checks what happened.
- `shots/` — a screenshot of every state, written fresh on each run.

The page has three sections, in this order.

1. **Keyboard.** One real instance of every component a person can operate, bracketed by a button
   before (`kb-before`) and a button after (`kb-after`). Each control carries a guessable id,
   `kb-<component>`, and sits next to a hidden `<span id="kb-<component>-fired">` that its own
   listener writes into — so the driver can tell whether a key press did anything, even for a
   control that changes nothing you can see.
2. **Overlays.** Dialogs, a drawer, a menu, a tooltip and a message. This was its own page,
   `OverlayProofPage`, before the merge; every element id it used is unchanged.
3. **Fields.** Every form field, captioned, explained and refused. It checks itself from inside the
   page — "is this sentence part of the text a person can read" can only be asked there — and
   writes one `PASS|name|detail` or `FAIL|name|detail` line into a hidden `<pre id="proof-results">`.
   The driver reads that element and turns every line into one of its own checks.

## Running it

```bash
bash tools/ui-proof/build.sh      # packages the library, then compiles the page to JavaScript
node tools/ui-proof/drive.mjs     # drives it, and writes shots/
```

`build.sh` pins JDK 21; override with `JAVA_HOME_21` if yours is somewhere else. It installs
nothing into the shared Maven repository: it packages the library, copies the jars into `lib/`, and
builds this page against those copies.

`drive.mjs` borrows Playwright from a checkout that already has it — `G:/proj/trellis` by default.
Point it elsewhere with `--playwright <dir>`. Add `--headed` to watch it happen.

It exits non-zero if any check fails, and prints one line per check either way.

## What it checks

**Keyboard**

1. Tab from the start of the page, and write down every stop: what it landed on, what tag and role
   that was, and the name a screen reader would announce for it.
2. Which controls in the keyboard section Tab **never reached**, by id. That list is the finding
   this harness exists to produce, so it is printed on its own, in full, either way.
3. Every stop has a computed accessible name — `aria-labelledby` text, else `aria-label`, else the
   label it is associated with (by `for`, or by being inside one), else its own words. An empty
   name fails the check, and every name found is printed beside its control.
4. Enter and then Space are pressed at every control that is meant to be activated, and the report
   says which of the two the control answered.
5. Pressing a control leaves the keyboard on it. Somebody who cannot use a mouse has no cheap way
   back from the document body: Tab starts again at the top of the page. Only a control whose whole
   job is to hand the keyboard somewhere else — a file chooser, an overlay opener — is excused.
6. A right-click menu answers the Menu key, which is the keyboard's right click. Enter and Space
   are pressed at it too and the answer printed, but neither is what opens a context menu.
7. A control the browser itself works — a `<select>` — is a real one and not a box of divs, its
   value really can be changed with nothing but the keyboard, and the change reaches the component.
8. A field whose keyboard is typing answers typing, not Enter and Space.
9. Anything you drag also answers the arrow keys: the handle, the divider, the canvas and the
   scrubbed timeline all move, and move back, and say where they now are.
10. The dialog, the drawer and the menu open from the keyboard, at their own ids in the overlays
    section — no second copy of them is built.

**Overlays**

11. A dialog opens, is in the browser's top layer, and the keyboard goes into it.
12. Twelve Tabs never reach a control outside the dialog, and never the button on the page behind.
13. Escape closes it and the keyboard goes back to the button that opened it.
14. Clicking the dim closes a dialog that allows it, and does nothing to one that refuses it.
15. A dialog the browser does not own (`setModal(false)`) still moves the keyboard in and gives it
    back — the one focus path the browser does not do for us.
16. The dialog is named by its own heading, and the heading is a real heading element.
17. A dialog opened over a message and an open menu covers both.
18. Every overlay is on its named layer, with the number that layer stands for.
19. A drawer opens, holds the keyboard inside it, closes on Escape and gives the keyboard back.
20. Escape closes a menu, hides a showing tooltip, and takes a message off the page.
21. A panel asked to be 56rem is 56rem on a wide window, and fits a 380-pixel one.

**Fields**

22. Every field type shows its caption, its explanation and its required mark, and the caption is
    the field's name to a screen reader — a `<label for>` where there is one control to point at,
    a named group where there is not.
23. A field already on the page, given a message and then a caption afterwards, shows both, is
    marked invalid, and keeps its place among its neighbors.
24. A real edit that breaks a rule puts a readable sentence on the screen and takes it back.
25. The same, through `Binder`, which is how forms are actually written.

## The build-failing counterpart

`KeyboardAndNamingContractTest`, in `zerozstack-ui-components`, is the gate that fails the build.
It reads the library's own source text on every build, works out which components a person can
operate, and requires **every one of them to appear on this page** — so a component written
tomorrow takes on the obligation the moment it is written, with no list for anybody to forget to
update. Text is all it can read, though: it can see that a keydown handler exists and cannot see
whether pressing the key does anything. That is this harness's job, and the two only work together.

## Notes for whoever runs it next

- **Element ids are the contract** between the page and the driver. Change one and change both.
- **Measure with `offsetWidth`, not `getBoundingClientRect`.** A dialog panel scales up as it opens,
  and a rectangle measured mid-animation is the scaled one — 891 pixels for a panel that is really
  896.
- **Chrome parks focus on the document body for one step** as it wraps round the end of a modal
  dialog. That is the wrap, not an escape; the driver allows it and checks that no *control* outside
  the dialog is ever reached.
- **A message disappears when you press Escape**, which is correct and also means the driver cannot
  keep one on the page across steps. There is a button on the page to make a fresh one.
- **Changing a `<select>` from the keyboard takes three presses, not one.** daisyUI styles the
  select, and styling one in Chrome turns on `appearance: base-select`, where the arrow keys no
  longer step the value where it stands: the first ArrowDown opens the list, the next moves a
  highlight inside it, and Enter takes the highlighted choice. A driver that presses ArrowDown once
  and looks at the value concludes the control is dead when it is only waiting for the other two.
- **`LaneTimeline` throws its scrub strip away on every arrow key** and builds another, which takes
  any id and any listener on it away too. The component puts the keyboard on the new one, so a
  person notices nothing; the driver finds that strip by role inside its row rather than by id, and
  the page listens for the key on the surrounding host, which survives.
- **Nudge a drag surface off its limit before measuring it.** A timeline starts at the live end of
  the run, and a control already at its stop that correctly refuses to go further would otherwise
  read as one that ignores the key.
- **Listen where the effect lands, not where the press does.** `FileUpload` opens the file chooser
  by clicking a hidden input, so a listener on its drop box sees a mouse click and never a key
  press. The page listens on the hidden input instead, which both paths reach.
- **The fields section finishes after the page has loaded.** It runs itself a browser turn at a
  time, because a typed character is not answered in the same breath, and it moves the keyboard
  about while it works. The driver waits for its `PASS|harness completed|` line before doing
  anything else.
