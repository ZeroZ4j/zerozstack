# Keyboard and naming: what every control owes

This page is for anyone writing or changing a component in `zerozstack-ui-components`.
It states the two things a control has to do, and what the build checks before it will let the
change through.

## When to use this

Read it before you add a component, and before you add a listener to one that already exists.
If your change makes something clickable, this page is the contract that change has to meet.
If your change only draws, none of it applies to you.

## The two obligations

**A control can be operated from the keyboard.**
Somebody reaches it with Tab and works it with Enter, Space or the arrow keys.
No exceptions, and no "the mouse is the main way to use it".

**A control has a name.**
Words that say what it does, which a screen reader can read out and voice control can listen for.
An icon is not a name. A color is not a name. A tip that only appears under a pointer is not a
name.

That is the whole contract. The rest of this page is how it is enforced and how to satisfy it.

## What counts as a control

Nothing is listed by hand, because a hand-written list goes stale and then quietly covers less than
it claims. A component is a control when its own code reacts to something a person did: it
registers a browser listener for a pointer, key or value event, or it publishes a method an
application uses to hear one, or it is an input field.

Everything else is decoration. It draws, it is never selected, and it owes nothing — so it needs no
exemption and cannot be granted one. That is deliberate: a list of exemptions grows until it is the
rule.

## How to satisfy it

### Use a button

Almost every failure here is the same one: a click listener on a `Div`. The fix is almost always
the same one too.

```java
// WRONG - a div is not in the tab order, cannot be pressed with Enter, and is announced as nothing.
Div copy = new Div();
copy.addClassName("cursor-pointer");
copy.getElement().addEventListener("click", threaded(e -> copyToClipboard(text)));

Button copy = new Button("Copy");
copy.addClickListener(e -> copyToClipboard(text));
```

A `<button>` is in the tab order, answers Enter and Space, and announces itself, without a line of
code from you. `btn-ghost` and `btn-link` make one look like anything you want, so "it must not
look like a button" is never a reason to avoid being one.

### Give a link somewhere to go

An `<a>` with no `href` is not a link. The browser leaves it out of the tab order entirely, so it
cannot be reached by keyboard at all, and a screen reader reads it as ordinary text. It looks
exactly right and nobody notices.

```java
Link docs = new Link("Read the guide", "/docs/guide");
```

If it does something rather than going somewhere, it is a `Button`.

### Build one by hand only when it really is not a button

Sometimes the thing being operated is a whole panel, a menu entry, or a surface. Then you build the
control yourself, and you do the whole job — all three parts, on the same element:

```java
row.getElement().setAttribute("role", "menuitem");   // what it is
row.getElement().setAttribute("tabindex", "0");      // the keyboard can reach it
row.getElement().addEventListener("keydown", e -> {  // the keyboard can press it
    String key = Js.eventKey(e);
    if ("Enter".equals(key) || " ".equals(key)) {
        choose();
    }
});
```

Two of the three gives you a control that can be focused and not pressed, or pressed and never
found. Both are still broken.

### Anything you drag also answers the arrow keys

A splitter, a resize handle, a scrubbed timeline and a panned canvas are the same shape: a surface
that does nothing until a pointer is dragged across it. Dragging is the one gesture the browser
gives no keyboard equivalent for, so the component has to give it one. The surface takes a
`tabindex` and the arrow keys move it, writing to exactly the same state the drag writes to.

### Name it

Prefer visible words. They name the control for everybody at once, and voice control listens for
what a person can see, so a hidden name that disagrees with the visible one is worse than no name.

When there are no words to show — an icon, a canvas, a spinner — set one:

```java
new Button(Icon.of("trash"), "Delete this row");
splitter.setAriaLabel("Move the divider between the list and the details");
```

Say what pressing it does, in the words somebody would use out loud. "Delete this row", not "trash"
and not `delete-row`.

## What the build checks

`KeyboardAndNamingContractTest` in `zerozstack-ui-components` runs on every build and fails it.
It reads the source of every component and works out, for each listener, **which element** the
listener was put on and **what tag that element is**, by following the receiver back to where it
was created.

It checks four things:

| Check | What fails it |
|---|---|
| Everything you click can also be reached and pressed | A click listener on an element that is not natively operable and has no role, tabindex and keydown handler of its own |
| Every control has words | An operated element with no text inside it, no `aria-label`, no `aria-labelledby` and no `title` |
| Anything you drag also answers the arrow keys | A drag surface with no tabindex, or one whose code never mentions an arrow key |
| Every anchor has somewhere to go | A component that builds an `<a>` and never gives it an `href` |

It also refuses to pass when it **cannot tell** what an element is. An interaction the test cannot
read is one it cannot vouch for, so build things in the ordinary way — a field or a local with its
type written out — rather than in a shape it has to guess at.

### And every control is driven by a real browser

Source text can show that a keydown handler exists. It cannot show that pressing Enter does
anything, that Tab arrives where it should, or that a screen reader would hear a name.

So a fifth check requires every interactive component to appear on the keyboard proof page in
`tools/ui-proof`, where a real headless browser presses real keys at it and reads the accessible
name back out. Because the list of components is derived from the code rather than typed out, a new
component joins that obligation the moment it is written — the build fails until it is on the page.

`tools/ui-proof` is deliberately outside the Maven build: it compiles the library to JavaScript,
which takes about a minute, and nobody building or releasing ZeroZ Stack should pay for that on
every build. Run it by hand:

```bash
bash tools/ui-proof/build.sh
node tools/ui-proof/drive.mjs
```

## Limits

The gate reads source text. It proves that the wiring is present and that it is on the right
element; it does not prove the behavior is sensible. A keydown handler that answers Enter by doing
nothing passes the gate and fails the browser proof, which is why both exist.

It says nothing about color contrast, focus rings, motion, or the order controls appear in. Those
are real and they are not checked anywhere yet.

It covers the component library only. An application can still put a click listener on its own
`Div`, and nothing will stop it.

## A control whose name is translated

`LanguageSelector` is the first control in this library whose accessible name is not a literal. It
reads its name from the framework's own catalog inside an effect, so the name changes with the
language like any other word on the screen — and `setLabel(...)` takes the built-in name away, the
way `ThemeController` does, so the caption and the built-in name can never disagree.

Two things follow for anything else that grows a translated name.

**The name has to be read inside an effect.** Read at construction it is right once and wrong from
the first language switch onward, and a screen reader announces the old language forever.
`MessageReadContractTest` fails the build on that, and its javadoc is honest about the reads it
cannot see.

**Read it in the effect, not in a helper the effect calls.** The check reads one file's text and
cannot follow an ordinary method call, so a helper is reported as a mistake even when it is correct.
Putting the read in the effect body keeps the check useful instead of teaching people to silence it.
