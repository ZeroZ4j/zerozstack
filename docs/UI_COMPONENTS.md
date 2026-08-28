# ZEROZ4J UI COMPONENTS

This document outlines the UI component library in the `zerozstack-ui-components` module. It covers how layouts and views work, the optional HTML layout system, data binding, and a comprehensive list of all components categorized by their purpose.

## Building Views with Layouts

In zeroz4j, views are built programmatically using Layout components. Layouts act as containers that implement the `HasComponents` interface, allowing you to nest components hierarchically.
By adding child components to these layouts, developers can compose complex UIs purely in Java without writing custom CSS or HTML. The layout classes internally manage standard layout paradigms like Flexbox and CSS Grid. 

Common layouts include `VerticalLayout` (stacking children vertically), `HorizontalLayout` (stacking horizontally), and `FormLayout` for structured input forms.

### Code Example: Basic Layouts
Here is a simple example showing how to create layouts and attach components to them:

```java
// Create a main vertical layout
VerticalLayout mainLayout = new VerticalLayout();

// Create a title and add it to the main layout
CardTitle title = new CardTitle("User Registration");
mainLayout.add(title);

// Create a form layout for inputs
FormLayout formLayout = new FormLayout();
TextField nameField = new TextField("Name");
TextField emailField = new TextField("Email");

// Add components to the form layout
formLayout.add(nameField, emailField);

// Create a horizontal layout for actions
HorizontalLayout actionsLayout = new HorizontalLayout();
Button submitButton = new Button("Submit");
Button cancelButton = new Button("Cancel");

// Add buttons to the horizontal layout
actionsLayout.add(submitButton, cancelButton);

// Attach the nested layouts to the main layout
mainLayout.add(formLayout, actionsLayout);
```

## Optional HTML Layout Feature

While programmatic layouts are powerful, there are times when declaring a layout in HTML is more convenient. ZeroZ Stack provides an optional HTML layout feature via the `FlavourWrapper` component, leveraging TeaVM Flavour templates.
By passing a Flavour template object to the `FlavourWrapper`, the framework binds the template to a host `div` element (`Templates.bind(flavourTemplateObj, getElement())`). This allows developers to seamlessly mix declarative HTML templates for complex visual structures with programmatic component logic, without sacrificing type safety or data binding.

## Naming a field

An input needs a name the reader can see. Set it with `withLabel`, which returns the field so it
reads inside the expression that creates it:

```java
TextField folder = new TextField().withLabel("Primary folder path");
TextField email  = new TextField("you@example.com").withLabel("Email address");
Checkbox news    = new Checkbox().withLabel("Send me the occasional release note");
```

**A caption is not a placeholder.** The single-argument constructor sets the *placeholder* — the
grey words inside an empty box, which vanish the moment somebody types and are announced by nothing.
That is an example of what goes in the field, never its name. The two can be used together, as the
email field above does, and `setPlaceholder` changes one later.

The caption is a real `<label>` tied to the control by a generated id, so clicking the words focuses
the field — and ticks the box, for a checkbox — and a screen reader announces the two together. You
never write the id; if you set one of your own with `setId`, the caption follows it.

Three more things a field can say, all on the same class, so every input has them - text fields,
text areas, selects, checkboxes, toggles, ranges, ratings, radio groups, file pickers, swaps and
theme switches:

```java
field.setHelperText("An absolute path. It is created if it does not exist yet.");
field.setRequiredIndicatorVisible(true);       // the asterisk after the caption
field.setErrorMessage("A port is a number between 1 and 65535.");
```

`setErrorMessage` shows the sentence under the field, colours the control to match and marks it
invalid for assistive technology; passing `null` clears all three. A `Binder` calls it for you — see
[Forms and binding](guides/ui-forms-and-validation.md).

A checkbox, a toggle, a swap and a theme switch put the caption to the right of the control on one
line; everything else puts it above. A radio group and a rating are captioned as a named group,
since each of them is several controls and there is no single one for a label to point at: a screen
reader reads the caption, then "group", then each choice.

**Captions work anywhere, not only inside a `FormLayout`.** There is deliberately no separate
"form item" wrapper: a field carries its own caption, so it is named the same way in a form layout,
a vertical layout, a dialog or a card. A field with a caption is inserted into its parent as a small
group — caption, control, explanation, message. A field with no caption is inserted exactly as
before, so a page that uses none is unchanged.

## Where the styles come from

**ZeroZ Stack ships no stylesheet.** Its components wear class names from two well-known style
libraries — Tailwind CSS and daisyUI — and the HTML page your application serves is what has to
bring those in. This is on purpose: a Java project should not have to install a JavaScript
toolchain before it can show a button.

So every page needs two lines. The examples use these, and they are the ones to copy:

```html
<script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4.1.16"></script>
<link href="https://cdn.jsdelivr.net/npm/daisyui@5.6.14/daisyui.css" rel="stylesheet" type="text/css" />
```

The first line is Tailwind doing its work **in the browser**, as the page loads: it reads the class
names your components put on the page and writes the matching CSS on the spot. Nothing to install,
nothing to build, and it costs a moment on every page load.

**That is right for an example and wrong for something you ship.** In a real application you install
Tailwind once, run it over your compiled client, and serve the one finished stylesheet it produces —
so visitors download a small CSS file instead of a compiler. Tailwind's own installation guide covers
this; nothing in ZeroZ Stack has to change for it, because all the framework ever does is put class
names on elements.

Pin both versions, as the lines above do. An unpinned address means the day the library changes is
the day your application looks different, with nothing in your repository having moved.

A project generated by the Maven archetype does not yet carry these two lines, so its first page is
unstyled. Copy them from any example's `index.html`.

## Naming text sizes

Every screen has text, and no component owns it. So text is the thing applications describe over
and over instead of asking for it — and because it is described rather than named, it comes out
slightly different every time. One application built on this library wrote out its own idea of
"quiet supporting text" a dozen times and ended up with three sizes and four degrees of grey, on
pages sitting next to each other.

There are five sizes of text, by name, in `com.zeroz4j.ui.theme.TextStyle`:

| Name | For |
|---|---|
| `PAGE_TITLE` | the name of the screen — one per page, at the top |
| `SECTION_TITLE` | the heading over a group of things: a card, a panel, a block of a form |
| `BODY` | ordinary prose, the words the reader came for |
| `SECONDARY` | supporting words, one step quieter: timestamps, counts, explanations |
| `CAPTION` | the smallest label there is: units, hints, the words under a picture |

Ask for one, rather than describing it:

```java
add(TextStyle.PAGE_TITLE.span("Deliveries"));
add(TextStyle.SECONDARY.paragraph("Nineteen stops left, updated a moment ago"));

TextStyle.CAPTION.applyTo(existingComponent);   // on something you already have
```

`span` makes inline text, `paragraph` makes a block that starts on its own line, and `applyTo`
styles a component you already built and returns it. Applying a second size replaces the first
rather than fighting it.

**Quiet is a fade, not a colour.** The two quiet sizes fade whatever colour they inherit instead of
naming one. The same words are therefore right on a page, on a tinted notice, on a coloured card
and on a dark background, without anybody choosing per surface — and two greys that were meant to
be the same one cannot drift apart.

Five is deliberate. A scale nobody can hold in their head is one that gets ignored and typed out
again, which is the whole problem.

### What the five names do not cover

Each of the five ties a size to how loud it is, and the two small ones are quiet by definition. So
there is no name for **small text at full strength** — a number in a dense table, a reading in a
chart tooltip, the words on an error line. The library's own dashboard components still write
`text-xs` by hand in those few places, on purpose: fading a measurement or an error would be wrong,
and inventing a sixth name is a decision for the scale, not for one component.

If you hit the same thing, keep the size class on its own and do not add a fade. Do not reach for
`SECONDARY` or `CAPTION` to get a smaller size when the words are not meant to be quiet.

## Data Binding to POJOs

Input components (those implementing `HasValue`) can be directly bound to data POJOs using the `Binder<BEAN>` class. The Binder facilitates a two-way data flow between your UI components and your Java model.

To use the Binder:
1. Instantiate a `Binder<MyPojo>`.
2. Bind components to specific fields using the builder pattern, providing a getter and setter:
   ```java
   binder.forField(myTextField)
         .withValidator(...)
         .asRequired("This field is required")
         .bind(MyPojo::getName, MyPojo::setName);
   ```
3. Load a bean into the UI with `binder.setBean(myBean)`.
4. The Binder automatically reads from the bean into the fields. Upon a value change in the UI, it validates the input. If validation passes, it writes changes back from the fields into the bean. It also handles rendering error states directly onto the components (e.g., adding `input-error` classes).

## DOM Events and Server Communication

You can easily attach DOM event listeners to components to handle user interaction. In zeroz4j (which compiles to WebAssembly via TeaVM), network calls to the server (via RPC stubs) are synchronous and blocking from the developer's perspective. 

To prevent these blocking calls from freezing the browser's main UI thread, zeroz4j automatically wraps standard component event listeners (like button clicks or text input) in a TeaVM virtual thread. This allows you to write simple, sequential code: the thread is suspended during the network call and automatically resumed when the response arrives, all without any callbacks, `CompletableFuture`, or manual `new Thread()` boilerplate!

### Code Example: Button Click to Server Call
Here is an example showing how to attach a click listener to a button and make a server call. Notice how the RMI call looks completely synchronous!

```java
Button saveButton = new Button("Save Profile");

saveButton.addClickListener(event -> {
    // 1. DOM Event listener triggered on click. 
    // This is already running inside a TeaVM virtual thread!
    saveButton.setEnabled(false);
    saveButton.setText("Saving...");
    
    try {
        // 2. Call server method synchronously via an RPC stub.
        // The virtual thread will safely suspend here without freezing the UI!
        boolean success = userService.saveUserProfile(profile);
        
        // 3. Resumes execution here once the server responds
        if (success) {
            saveButton.setText("Saved Successfully!");
        } else {
            saveButton.setText("Save Failed");
        }
        
    } catch (Exception e) {
        // Handle network or server errors
        saveButton.setText("Error Occurred");
    } finally {
        saveButton.setEnabled(true);
    }
});
```

When building your own custom components that hook into low-level DOM events via `element.addEventListener`, be sure to use the `addDomEventListener()` helper or `Component.threaded()` from the base `Component` class to ensure your listeners execute in a safe, suspendable context.

---

## Overlays: what sits above what

Anything that draws over the page — a dialog, a drawer, a menu, a message, a tip — is put on a
**named layer** rather than given a stacking number:

```java
import com.zeroz4j.ui.theme.Layer;

Toast saved = new Toast("Saved");
saved.setLayer(Layer.TOAST);      // rarely needed: a Toast is already on this layer
```

Every overlay in the library sets its own layer when you create it, so most applications never call
this. The layers, from the bottom up, are `PAGE`, `STICKY`, `DROPDOWN`, `OVERLAY`, `TOAST` and
`TOOLTIP`.

**One thing beats all of them.** An open modal `Dialog` is in the browser's own *top layer*, which
is above every stacking number there is. Nothing on the scale can cover it. If something has to
cover a dialog, it has to be a dialog too.

Full explanation, including how to slot a layer of your own in between two of these:
[Stacking overlays](guides/ui-layering.md).

## Component Reference

The framework provides a rich set of 106 UI components, broken down into the following functional categories:

### Layout Components
Used for structuring the application and organizing other components.
- **Div**: A generic block-level container.
- **Span**: A generic inline container.
- **VerticalLayout**: Arranges child components in a vertical column.
- **HorizontalLayout**: Arranges child components in a horizontal row.
- **FlexLayout**: A generic flexbox container with customizable flex properties.
- **GridLayout**: A CSS grid container for two-dimensional layouts.
- **FormLayout**: A responsive layout optimized for aligning form fields and labels.
- **Scroller**: A container that provides scrollbars when its content exceeds its bounds.
- **FlavourWrapper**: A host container that binds TeaVM Flavour HTML templates.
- **FlexComponent**: Base layout class for flexbox-based containers.

### Input & Data-Backed Components
Components that accept user input and can be bound to data models (implementing `HasValue`).
All of these carry a caption, an explanation, a required mark and a message line — see
[Naming a field](#naming-a-field).

- **TextField**: Standard single-line text input. The single-argument constructor sets the
  placeholder; `withLabel` sets the caption.
- **TextArea**: Multi-line text input area. Same two, same meanings.
- **Checkbox**: A standard boolean toggle for individual options.
- **RadioButtonGroup**: A set of mutually exclusive radio options.
- **Select**: A dropdown list for selecting a single item from a collection.
- **Range**: A slider for selecting numeric values within a defined range.
- **Rating**: An interactive star-based rating selector.
- **Toggle**: A switch component, often used as an alternative to a checkbox.
- **FileInput**: A control for selecting files from the user's system. It reports the chosen file's name and sends nothing anywhere.
- **FileUpload**: A drop-or-pick box that sends files to the server, several at once, with a progress bar and a cancel button for each. Set the wording with `setTitle` / `setSubtitle`, limit the picker with `setAccept("image/*")`, allow or forbid several files with `setMultiple`, and hear each outcome with `addUploadListener((name, accepted, message) -> …)`. The drop area is a control in its own right: Tab reaches it, Enter and Space open the picker, and it
  announces itself using the words you set. On the server one `@ApplicationScoped` class implementing
  `FileUploadHandler` is handed each finished file. See [Accepting file uploads](guides/file-uploads.md).

### Buttons & Navigation
Components that trigger actions or navigate between views.
- **Button**: A standard clickable button. A button made of nothing but a picture needs words
  of its own: `new Button(Icon.of("trash"), "Delete this row")`. The one-argument
  `new Button(icon)` still works and is deprecated — without words it is announced as "button"
  and nothing else, and voice control has nothing to say to press it.
- **BottomNavigation**: A mobile-friendly navigation bar fixed to the bottom of the screen.
- **Breadcrumbs**: The trail showing where in a site you are. It is a real `<nav>` named
  "Breadcrumb", so a screen reader can jump straight to it; `withAriaLabel` renames it.
- **Dropdown**: A button that drops a small panel of choices open underneath it. Clicking the
  button opens it, clicking anywhere else shuts it, and Escape shuts it and puts the keyboard back
  on the button. Open and close it from code with `open()` and `close()`. The panel sits on
  `Layer.DROPDOWN`. The keyboard is not held inside — Tab walks into the panel and out the far
  side, which is right for something that does not take the page over.
- **Menu**: A list of entries, usually down the side of a screen or inside a dropdown. An entry
  that *does* something is a real `<button>` — `addItem("Sign out", e -> signOut())`; an entry
  that *goes* somewhere is a real link — `addLink("Documentation", "/docs")`, which is also what
  lets somebody middle-click it or see where it goes first. **Changed in 0.8.0** — every entry
  used to be an `<a>` with no address, so no menu could be reached by keyboard at all.
- **Navbar**: The bar across the top of a page. It announces itself as navigation named "Main";
  `withAriaLabel` renames it, which matters as soon as a page has two of them.
- **Pagination**: Controls for navigating through paginated datasets.
- **Swap**: A component that toggles between two different states or icons on click.
- **Tab**: One heading in a row of tabs. It is a real `<button>`, so the keyboard reaches it and
  Enter presses it; `setSelected(true)` colours it and says it is the one showing, in one call,
  so the colour and the announcement cannot drift apart. **Changed in 0.8.0** — it used to be an
  `<a>` with nowhere to go, which the browser leaves out of the tab order entirely.
- **Link**: A hyperlink. **Give it a destination** — `new Link("Read the guide", "/docs/guide")`,
  or `setHref` / `withHref` later. An `<a>` with no address is not a link: the browser leaves it
  out of the tab order, so it cannot be reached by keyboard, and a screen reader reads it as
  ordinary text. If it does something rather than going somewhere, it is a **Button** —
  `btn-link` makes one look exactly like a link.
- **Steps**: A wizard-like component showing progression through a sequence of steps.

### Data Display & Content
Components used to present data, alerts, and content to the user.
- **Accordion**: An expandable/collapsible list of panels for dense content.
- **Alert**: The tinted notice — a strip of prose the reader is meant to act on. Pick it by what
  you are saying rather than by a colour: `Alert.info`, `Alert.success`, `Alert.caution`,
  `Alert.danger`. A notice can carry a short bold heading and one button:
  `Alert.danger("Nothing was saved.").withHeading("The upload failed").withAction("Try again",
  e -> upload())`. It shows a small mark for its tone, so the four are told apart by somebody who
  cannot separate the colours (`setIconVisible(false)` removes it); it announces itself to a screen
  reader as a notice; and long text wraps instead of running off the side. `setThemeColor` and
  `new Alert(text, "alert-info")` still work and are deprecated — they spell out a stylesheet class,
  which nothing checks and no reader understands.
- **Avatar**: Represents a user or entity with an image or initials.
- **Badge**: A small label or indicator, often used for counts or status.
- **Card**: A bounded container for grouping related content and actions.
- **CardTitle / CardActions**: Sub-components for structuring content within a Card.
- **Carousel**: A slideshow component for cycling through elements like images.
- **ChatBubble**: Displays a single message within a conversational UI.
- **CodeBlock**: Source code, shown as it was written, with a control that copies it. **Changed in
  0.8.0** - that control is a real button, so Tab reaches it and Enter presses it, and the change
  from "Copy" to "Copied" is announced rather than only shown.
- **Collapse**: A generic expand/collapse container.
- **ContextMenu**: The menu that appears where you right-click. Opening it moves the keyboard onto
  the first entry, so entries can be walked with Tab and chosen with Enter; Escape shuts it and puts
  the keyboard back on the row that was right-clicked. It sits on `Layer.DROPDOWN`. The keyboard is
  not held inside it; if you want something the keyboard cannot leave, you want a **Dialog**.
- **Countdown**: Displays a timer counting down to a specific event.
- **Dialog**: A panel that takes over the page until it is answered. Opening it hands the
  element to the browser, so Escape closes it, focus stays inside it, the page behind it stops
  responding, and it is drawn above everything else. A click on the dimmed area outside the
  panel closes it too. `setWidth` and `setHeight` size the visible panel rather than the
  full-window overlay, and the panel is never larger than the window. `addCloseListener` fires
  once per close however it closed. For a question that must be answered, take the exits away
  with `setCloseOnEsc(false)` and `setCloseOnOutsideClick(false)` — and leave the user a
  button. `setModal(false)` restores the appearance-only dialog of 0.7.0 and earlier.
  `new Dialog("Delete the account?")` puts a heading at the top of the panel and gives the dialog a
  name a screen reader announces; `setAriaLabel` names one that has no room for a heading. Opening
  moves the keyboard into the dialog and closing puts it back on whatever opened it. An open modal
  dialog is in the browser's **top layer**, so it is above everything else on the page whatever
  stacking numbers are involved — see [Stacking overlays](guides/ui-layering.md).
- **Diff / DiffView**: Two versions of a file or a piece of text, side by side. **Changed in
  0.8.0** - the heading that folds a file open and shut is a real button carrying `aria-expanded`,
  so it can be reached and pressed by keyboard and says whether the file is open.
- **Divider**: A visual separator between content sections.
- **Drawer**: A panel that slides in from the side of the window. `add` puts things in the sliding
  panel; `addToPage` puts things on the page it slides over. Open and close it with `open()` and
  `close()`; Escape closes it, and so does clicking the dimmed page beside it —
  `setCloseOnEsc(false)` and `setCloseOnOutsideClick(false)` take those away. Opening moves the
  keyboard into the panel and **holds it there** until the drawer closes, then puts it back on
  whatever opened it. `new Drawer("Settings")` gives it a heading and a name a screen reader
  announces. `setModal(false)` turns the dim and the hold off, for a sidebar that lives beside the
  page rather than over it. The panel sits on `Layer.OVERLAY`.
- **EmptyState**: A placeholder view shown when there is no data to display.
- **Footer**: A standard page footer element.
- **Hero**: A large, prominent banner often used at the top of landing pages.
- **Icon**: A scalable vector icon component.
- **Indicator**: A visual marker, often attached to other elements (like badges on icons).
- **Join**: A layout utility for seamlessly connecting grouped elements.
- **Js**: A component wrapper for embedding custom JavaScript execution.
- **Kbd**: Represents keyboard input visually (e.g., styling for `Ctrl+C`).
- **KeyedList**: An optimized list component that efficiently manages DOM nodes based on keys.
- **KpiTile**: A dashboard stat tile: label, big value with an optional unit, a computed movement
  line (absolute change, percentage and direction arrow) and a trend sparkline. `setDirection`
  decides whether a rise is coloured as good news — falling free memory is bad, falling latency is
  good, and that is a judgement rather than arithmetic.
- **LaneTimeline**: A timeline view segmented into multiple lanes. The name column is measured
  from the longest lane name, between 90 and 260 pixels; hovering a name shows it whole however
  narrow the column had to be. `setLabelWidth(px)` pins the column for lining several timelines up
  with each other, and `setLabelWidth(0)` goes back to measuring. A name too long for the column is
  never shortened in Java: the whole name is in the page and the browser fades out the end of it,
  so it can still be selected, searched for and read out. `setLabelWrap(true)` runs it onto more
  lines instead and grows that lane to fit.
- **Loading**: The spinner that says something is happening. It announces itself as "Loading";
  `withAriaLabel("Loading your orders")` says what, which is nearly always worth doing.
- **MarkdownView**: Renders Markdown text safely into HTML.
- **Mask**: A component for shaping or clipping elements (e.g., circular images).
- **PhoneMockup / BrowserMockup / WindowMockup / CodeMockup**: Decorative containers that frame content within stylized device or window borders.
- **Progress / RadialProgress**: A bar and a ring showing how far along something is. The ring
  announces its percentage as it changes. Neither invents a name for itself - say what is
  progressing with `withAriaLabel`, or put the words next to it.
- **PropertyGrid**: Names and values in two columns - ids, paths, hashes. **Changed in 0.8.0** -
  copying is a real button beside the value, named "Copy " plus the row's name, instead of a
  click on the text itself. The value is ordinary selectable text again.
- **Resizer**: A handle you drag to resize what it sits on. **Changed in 0.8.0** - it is in the tab
  order and the arrow keys move it, so it works without a mouse. `setAriaLabel` says what it
  resizes.
- **Skeleton**: The grey blocks standing in for content that has not arrived. They are decoration
  and are skipped by screen readers; put a "Loading" message on the region around them, not on
  each block.
- **Sparkline**: A tiny inline trend chart in `AREA`, `LINE` or `BAR` mode, auto-scaled to its
  data, with an optional baseline, min/max markers and delta colouring. Draws in `currentColor`
  by default, so it inherits the surrounding text colour and follows the theme for free.
- **SplitPane**: Two panels with a divider between them. **Changed in 0.8.0** - the divider is in
  the tab order, the arrow keys move it, Home and End send it to the ends, and it says where it
  is as it moves. `setAriaLabel` says what it divides.
- **Stack**: A layout utility for overlapping components.
- **Stat**: A component optimized for displaying a prominent statistic or metric.
- **StatusDot**: A small coloured indicator representing a status. It has two pieces of text and
  they are rarely the same one: the *state* decides the colour and the pulse, the *label* is what a
  person reads on hover and what a screen reader announces — `new StatusDot("DISPATCHED", "Sent to
  a worker")`. Given only a state it writes the hover text itself, in ordinary language:
  `DESIGN_REVIEW` hovers as "Design review". That is a fallback and not an excuse — it can only
  reword the name it was given — but it stops a console full of dots shouting `DISPATCHED` at
  somebody who does not work on the code. Text that already reads like a sentence is left alone.
- **StreamingText**: Text arriving a word at a time, the way a language model answers. It is read
  out as it arrives, and the blinking caret is not read out as a character.
- **SvgCanvas**: A drawing surface you pan and zoom. **Changed in 0.8.0** - it is in the tab order,
  the arrow keys pan it, `+` and `-` zoom and `0` goes back to the start. `setAriaLabel` says what
  is drawn on it.
- **Table**: A structured grid for displaying tabular data.
- **ThemeController**: A component for managing and switching application themes (e.g., light/dark mode).
- **Timeline**: Events in the order they happened, across the page or down it
  (`new Timeline().vertical()`). Add them one at a time — `addEvent("09:14", "Order placed", "Paid
  by card")` — and the line joining one to the next is drawn and redrawn for you. An event's words
  are never shortened: a long description wraps inside its box, the box stops growing at about
  20rem (`setEventWidth`), and a timeline laid out in a row scrolls sideways rather than pushing
  the page out of shape. Any component can still be added as a step by hand.
- **Toast**: A brief, auto-expiring notification message overlaid on the screen.

- **Timeline**: A linear representation of events ordered by time.
- **Toast**: A short message that floats in a corner of the window. It is announced by a screen
  reader when it appears, politely; `setUrgent(true)` makes it interrupt, for the rare message that
  cannot wait. It **never takes the keyboard** — a message arriving while somebody is typing must not
  move them out of the box they are typing in. Escape removes it, and `close()` does the same from
  code; `setCloseOnEsc(false)` keeps it put. It sits on `Layer.TOAST`, above panels and menus.
- **TokenMeter**: How much of a budget has been used - tokens, most often. It announces the
  percentage as a progress bar. With no cap set there is no percentage, and it now says so instead
  of leaving the last one showing.
- **Tooltip**: A few words that appear next to whatever the pointer is resting on. `setText` sets
  the words the tip shows; wrap the control it belongs to with `add`. It takes no keyboard focus and
  holds nothing — put nothing in one that has to be clicked, because it cannot be reached. Escape
  hides it until the pointer leaves and comes back. While the tip is showing it is on
  `Layer.TOOLTIP`, the top of the scale; the rest of the time it is ordinary page content, because
  it wraps your control and floating that over every drawer would be wrong.
- **VirtualScroller**: A long list that only draws the rows on screen, so ten thousand of them cost
  what ten do. **Changed in 0.8.0** - it is in the tab order, so Page Down and the arrow keys
  scroll it; before this a keyboard could not scroll it at all. `withAriaLabel` says what the list
  holds.

### Charts & Dashboards
Package `com.zeroz4j.ui.chart`. Built in Java against SVG and DOM — no JavaScript charting library is
wrapped or loaded. Series colours resolve to DaisyUI theme tokens (`var(--color-primary)`), so
switching `data-theme` recolours every chart with no redraw and no listener.

**Charts**
- **TimeSeriesChart**: The workhorse panel — multiple metrics over time as lines, filled areas or a
  stack, with a shared crosshair, a hover tooltip and a live legend. Uses the *aligned* data model:
  one timestamp array plus one value array per series. `NaN` is a gap, not a zero.
- **RollingChart**: Live telemetry with a sliding window. `push()` samples in; redraw is decoupled
  from data arrival, so the trace scrolls smoothly at any sample rate and a stalled feed shows as a
  growing gap rather than a frozen chart. Bounded ring buffer.
- **Gauge**: One value against a range, coloured by threshold, with threshold arcs outside the dial.
  Where `RadialProgress` shows a percentage, a Gauge shows a *reading* — min, max, unit and judgement.
- **BarGauge**: A stack of labelled meters on a shared scale (`BASIC`, `LCD`, `GRADIENT`; horizontal
  or vertical). The densest way to show one measurement across many subjects.
- **BarChart**: Categorical bars, grouped or stacked, vertical or horizontal.
- **Heatmap**: Histograms over time — time buckets across, value bands up, colour by count. Shows
  whether a tail moved because everything slowed or because a second mode appeared.
- **Histogram**: Distribution of a sample set, with automatic nice-numbered bucketing.
- **ScatterChart**: Two measurements against each other, with optional category colour and bubble
  size (scaled by area, not radius).
- **DonutChart**: Composition of a whole, with the total in the centre.
- **Treemap**: Proportional area via the squarified algorithm, up to two levels. For "what is taking
  up all the space".
- **StateTimeline**: Discrete state over time as bands whose edges are the transitions.
- **StatusHistory**: One mark per periodic sample, so a missed poll leaves a visible hole —
  "the probe stopped answering" looks different from "the value did not change".

**Dashboard surfaces**
- **PanelFrame**: Panel chrome — title, subtitle, header actions, footer — and the four states a
  panel really has: ready, loading, error, no-data.
- **TimeRangePicker**: Quick ranges published as a `ValueSignal`, so panels bind with an `Effect`
  rather than being wired up by hand. A range is a duration resolved against the clock, so "last
  hour" keeps meaning the last hour.
- **RefreshControl**: Manual refresh, an auto-refresh interval, and the age of the current data —
  so "the number has not moved" is distinguishable from "the number has not been fetched".
- **MetricTable**: A sortable table whose cells are measurements: threshold-coloured numbers, inline
  sparklines, in-cell bars and state pills.
- **LogViewer**: Level-coloured, filterable, tail-following log pane on `VirtualScroller`.
- **ColorScaleLegend**: The key for a colour-encoded chart — a continuous ramp or discrete thresholds.

**Supporting types**
- **Series**: A named value array plus its draw style (filled, stepped, dashed, points, hidden).
- **Threshold**: A value band and the colour it paints, shared by every threshold-aware component.
- **ValueFormat**: How a number is rendered — percent, bytes, gigabytes, duration, or your own lambda.
- **StateColor**: Maps a discrete state name to a colour; the default knows up/down, running/exited,
  healthy/unhealthy.
- **Scales**: Nice tick selection, local-time tick alignment, and TeaVM-safe number formatting
  (`String.format` does not exist in the TeaVM classlib).
- **Palette**: DaisyUI token series colours plus perceptual ramps (`HEAT`, `VIRIDIS`, `BLUES`).
- **ChartBase / CartesianChart**: Measure-then-draw lifecycle, SVG factories, tooltip, legend, empty
  state; axes, grid, threshold bands and crosshair. Extend these to add a chart type.

> **Constructor caveat.** A component must not read a `Signal` in its constructor. A signal read
> registers a dependency on whichever `Effect` is currently running — and components are typically
> constructed *inside* the effect that swaps views, so the component would end up invalidating the
> view that built it. Mirror the value in a plain field and read that. See `TimeRangePicker`.

### Base Classes & Interfaces
Core building blocks that other components extend or implement.
- **Component**: The base class for all UI elements, wrapping a DOM node. `setAriaLabel` gives any
  component words for somebody who cannot see it - for the ones with no words of their own, such
  as an icon button, a splitter or a spinner. See
  [Keyboard and naming](guides/ui-keyboard-and-naming.md).
- **AbstractField**: The foundational class for input components.
- **HasComponents**: Interface for containers that can hold child components.
- **HasValue**: Interface for components that handle data binding.
- **HasSize / HasStyle / HasText / HasEnabled**: Mixin interfaces for standard component properties.
- **HasLayer / Layer**: How high above the page something floats, asked for by name rather than by
  number — `overlay.setLayer(Layer.TOAST)`. Every overlay in the library comes with the right layer
  already set. See [Stacking overlays](guides/ui-layering.md).
- **DomListenerRegistration / EventListener / ComponentEvent / ClickEvent**: Infrastructure for DOM event handling and custom component events.
- **Focusable**: Interface for components that can receive keyboard focus.

### Shared Styling
Package `com.zeroz4j.ui.theme`. Not components — the small vocabulary every component and every
screen shares, so the same thing is asked for by name rather than described again.
- **TextStyle**: The five sizes of text, by name — see [Naming text sizes](#naming-text-sizes).
- **ThemeColor**: The DaisyUI colour names a component can be given, for the components that take
  one (`setThemeColor`).
- **ThemeSize**: The size names a component can be given, for the components that take one.
