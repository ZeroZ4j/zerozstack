# Stacking overlays: what sits above what

For anyone putting a menu, a message, a drawer or a dialog on a screen. By the end you will know
which layer each one belongs on, how to ask for it, and the one rule that beats every layer.

## When to use this

Read this when two things on your screen overlap and the wrong one is on top. Read it before you
type a number into `z-index` — that number is what causes this problem, not what fixes it.

## The short version

Ask for a layer **by name**:

```java
import com.zeroz4j.ui.theme.Layer;

Toast saved = new Toast("Saved");
saved.setLayer(Layer.TOAST);
```

You will rarely need even that. Every overlay in the library already puts itself on the right
layer when you create it. A `Toast` is on the toast layer. A `Dropdown` menu is on the menu layer.
You only set one when your application genuinely stacks things differently.

## Why names, not numbers

A stacking number is a bid. The higher number wins.

That works until two parts of the same application each pick their own number. Then whoever picked
higher wins, and that has nothing to do with which one *should* be on top. The usual next step is
somebody picking 9999 to be safe, and then somebody else picking 99999.

A named layer ends the bidding. There is one list, everybody uses it, and the order is decided
once.

## The layers

From the bottom up:

| Layer | Use it for |
|---|---|
| `Layer.PAGE` | Ordinary page content. Ask for this to take a layer back off something. |
| `Layer.STICKY` | A bar that stays put while the page scrolls under it — a header, a toolbar. |
| `Layer.DROPDOWN` | A menu opened from a control: a dropdown, a right-click menu. |
| `Layer.OVERLAY` | A panel that covers the page and dims it — a drawer. |
| `Layer.TOAST` | A short message: "Saved", "Could not reach the server". |
| `Layer.TOOLTIP` | A tip that appears next to whatever the pointer is on. |

The order is not a preference. Each one is above the one before it because something real breaks
otherwise:

- A menu is often opened from a button in a sticky header, so a **menu covers a header**.
- Opening a drawer over a menu that was already open should hide the menu, so an **overlay covers a
  menu**.
- A message about what you just did in a drawer is no use behind that drawer, so a **message covers
  an overlay**.
- A tip can be attached to a button inside anything, including inside a message, and it is small and
  brief — so a **tip covers everything**.

A tooltip is the one component that is not on its layer all the time. It wraps the control it
belongs to, so leaving it up there permanently would float that control over every drawer and
message on the page. It rises to `Layer.TOOLTIP` when the tip appears and drops back to ordinary
page content when it goes.

## The one rule that beats every layer

The browser keeps a place of its own, above the whole page. It is called the **top layer**.

Anything in it is drawn above every layer on the list, whatever number is on it. **You cannot
out-bid the top layer.** A number of a million loses to it.

An open modal `Dialog` is in the top layer, because that is what asking the browser to show a dialog
does. So if something has to cover a dialog, it has to be a dialog too. There is no number that
will do it.

This is what the whole list was written for. An application had picked its own numbers, one of them
very large, and an overlay still came out underneath a dialog. Nothing was wrong with the number.
The dialog was simply not playing the same game as the number.

### The two ways in

If something of yours genuinely has to cover a dialog, it has to be in the top layer too, and a
browser puts exactly two kinds of thing there:

- a **modal dialog** — `dialog.showModal()`. It takes the keyboard, blocks the page behind it and
  waits for an answer. Right for a question, wrong for anything the reader is meant to glance at;
- a **popover** — an element carrying `popover` that has been shown with `showPopover()`. It takes
  nothing. In `manual` state it does not close on a click elsewhere, it does not block the page, and
  the browser moves focus into it only if something inside it asks for focus. Somebody typing keeps
  typing.

The framework's own "Connection lost" bar (0.8.0+) is the second kind, for exactly that reason: a
connection can drop while a dialog is open, and before this the bar was drawn under the dialog and
the reader was told nothing at all. It carries one line of text and nothing focusable, so it appears
over the dialog and changes nothing else about the moment.

Two things to know if you do the same:

- **Inside the top layer, later wins.** Things are drawn in the order they arrived, so a dialog
  opened after your popover covers it. Re-showing the popover puts it back on top.
- **A browser too old for popovers ignores the attribute.** Support arrived in Chrome and Edge 114,
  Safari 17 and Firefox 125. Check for `showPopover` before using it and keep a plain fixed
  position as the fallback, which is what the connection bar does.

## Where the numbers come from

Each layer does put a real number on the element, and you can read it:

```java
int justAboveMenus = Layer.DROPDOWN.getZIndex() + 1;
```

Two things are worth knowing about those numbers.

**They start at 1000.** The stylesheet this library is built on uses numbers up to 999 inside its own
components. Starting above all of them means anything on a layer clears every piece of stylesheet
furniture without you having to check.

**They are a hundred apart.** If your application really does have a layer of its own — a loading
veil over the whole page, say — there are ninety-nine free values between any two of these. Read the
neighbors with `getZIndex()` rather than guessing.

## Seeing which layer something is on

Setting a layer also puts a marker class on the element: `zz-layer-toast`, `zz-layer-dropdown`, and
so on. It carries no styling. It is there so that you can see the layer when you inspect the page,
and so that a test can check it.

```java
Layer where = saved.getLayer();   // Layer.TOAST
```

## Limits

- **A layer needs a positioned element.** A stacking number does nothing to an element the
  stylesheet has left in the normal flow. Every component in this library that takes a layer already
  positions itself, so this only matters when you set a layer on something of your own: give it
  `position: relative`, `absolute` or `fixed` as well, or nothing will change.
- **Nothing stops an application writing its own number.** The layers are a convention with a list
  behind it, not a lock. A hand-written number on the same element will win, because it is set the
  same way.
- **There is no stylesheet file to edit.** The numbers are put on the element directly, because this
  library ships no CSS of its own — it is styled with Tailwind and daisyUI classes loaded by the
  application's own page.
