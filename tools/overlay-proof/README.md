# Overlay proof

A page of real overlays, driven by a real browser.

The component gallery has a Dialog page, and the dialog on it draws perfectly. That was never the
problem. A gallery shows you that a component **draws**; it cannot show you where the keyboard is,
what Escape does, or which of two overlapping things is really on top. Those are the questions that
were being got wrong, so this is what answers them.

## What it is

- `src/main/java/.../OverlayProofPage.java` — a page built out of the **real** components from
  `zerozstack-ui-components`. No hand-written HTML: hand-written HTML would prove that the markup
  behaves, not that the library produces that markup.
- `web/index.html` — the shell, with Tailwind and daisyUI from their usual addresses.
- `drive.mjs` — opens headless Chrome, does things to the page, and checks what happened.
- `shots/` — a screenshot of every state, written fresh on each run.

Deliberately **not** part of the Maven reactor. It compiles the library to JavaScript, which takes
about a minute, and nobody building or releasing ZeroZ Stack should pay for that.

## Running it

```bash
bash tools/overlay-proof/build.sh      # packages the library, then compiles the page to JavaScript
node tools/overlay-proof/drive.mjs     # drives it, and writes shots/
```

`build.sh` pins JDK 21; override with `JAVA_HOME_21` if yours is somewhere else.

`drive.mjs` borrows Playwright from a checkout that already has it — `G:/proj/trellis` by default.
Point it elsewhere with `--playwright <dir>`. Add `--headed` to watch it happen.

It exits non-zero if any check fails, and prints one line per check either way.

## What it checks

1. A dialog opens, is in the browser's top layer, and the keyboard goes into it.
2. Twelve Tabs never reach a control outside the dialog, and never the button on the page behind.
3. Escape closes it and the keyboard goes back to the button that opened it.
4. Clicking the dim closes a dialog that allows it.
5. Clicking the dim does nothing to one that refuses it.
6. A dialog the browser does not own (`setModal(false)`) still moves the keyboard in and gives it
   back — the one focus path the browser does not do for us.
7. The dialog is named by its own heading, and the heading is a real heading element.
8. A dialog opened over a message and an open menu covers both.
9. Every overlay is on its named layer, with the number that layer stands for.
10. A drawer opens, holds the keyboard inside it, closes on Escape and gives the keyboard back.
11. A menu closes on Escape.
12. Escape hides a tooltip that is showing.
13. Escape takes a message off the page.
14. A panel asked to be 56rem is 56rem on a wide window.
15. The same panel fits a 380-pixel window.

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
