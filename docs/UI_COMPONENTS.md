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
- **FileUpload**: A drop-or-pick box that sends files to the server, several at once, with a progress bar and a cancel button for each. Set the wording with `setTitle` / `setSubtitle`, limit the picker with `setAccept("image/*")`, allow or forbid several files with `setMultiple`, and hear each outcome with `addUploadListener((name, accepted, message) -> …)`. On the server one `@ApplicationScoped` class implementing `FileUploadHandler` is handed each finished file. See [Accepting file uploads](guides/file-uploads.md).

### Buttons & Navigation
Components that trigger actions or navigate between views.
- **Button**: A standard clickable button.
- **BottomNavigation**: A mobile-friendly navigation bar fixed to the bottom of the screen.
- **Breadcrumbs**: Displays the current navigational hierarchy and path.
- **Dropdown**: A contextual overlay menu triggered by an anchor element.
- **Menu**: A list of navigational or action items, often placed in sidebars or dropdowns.
- **Navbar**: A standard top navigation header.
- **Pagination**: Controls for navigating through paginated datasets.
- **Swap**: A component that toggles between two different states or icons on click.
- **Tab**: Represents individual selectable sections in a tabbed interface.
- **Link**: A standard hyperlink for navigation.
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
- **CodeBlock**: A styled container for displaying formatted source code.
- **Collapse**: A generic expand/collapse container.
- **ContextMenu**: A popup menu triggered by a right-click or long-press.
- **Countdown**: Displays a timer counting down to a specific event.
- **Dialog**: A panel that takes over the page until it is answered. Opening it hands the
  element to the browser, so Escape closes it, focus stays inside it, the page behind it stops
  responding, and it is drawn above everything else. A click on the dimmed area outside the
  panel closes it too. `setWidth` and `setHeight` size the visible panel rather than the
  full-window overlay, and the panel is never larger than the window. `addCloseListener` fires
  once per close however it closed. For a question that must be answered, take the exits away
  with `setCloseOnEsc(false)` and `setCloseOnOutsideClick(false)` — and leave the user a
  button. `setModal(false)` restores the appearance-only dialog of 0.7.0 and earlier.
- **Diff / DiffView**: Components for displaying file or text differences side-by-side.
- **Divider**: A visual separator between content sections.
- **Drawer**: A sliding side panel for navigation or secondary content.
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
- **Loading**: A spinner or indicator signifying a background process is running.
- **MarkdownView**: Renders Markdown text safely into HTML.
- **Mask**: A component for shaping or clipping elements (e.g., circular images).
- **PhoneMockup / BrowserMockup / WindowMockup / CodeMockup**: Decorative containers that frame content within stylized device or window borders.
- **Progress / RadialProgress**: Linear and circular progress bars to indicate completion percentage.
- **PropertyGrid**: A structured grid for displaying key-value pairs or object properties.
- **Resizer**: A drag handle component for resizable containers.
- **Skeleton**: A placeholder skeleton screen shown while data is loading.
- **Sparkline**: A tiny inline trend chart in `AREA`, `LINE` or `BAR` mode, auto-scaled to its
  data, with an optional baseline, min/max markers and delta colouring. Draws in `currentColor`
  by default, so it inherits the surrounding text colour and follows the theme for free.
- **SplitPane**: A container with two resizable panels separated by a divider.
- **Stack**: A layout utility for overlapping components.
- **Stat**: A component optimized for displaying a prominent statistic or metric.
- **StatusDot**: A small coloured indicator representing a status. It has two pieces of text and
  they are rarely the same one: the *state* decides the colour and the pulse, the *label* is what a
  person reads on hover and what a screen reader announces — `new StatusDot("DISPATCHED", "Sent to
  a worker")`. Given only a state it writes the hover text itself, in ordinary language:
  `DESIGN_REVIEW` hovers as "Design review". That is a fallback and not an excuse — it can only
  reword the name it was given — but it stops a console full of dots shouting `DISPATCHED` at
  somebody who does not work on the code. Text that already reads like a sentence is left alone.
- **StreamingText**: A component for displaying text that streams in dynamically (e.g., LLM responses).
- **SvgCanvas**: A container for drawing and displaying SVG graphics.
- **Table**: A structured grid for displaying tabular data.
- **ThemeController**: A component for managing and switching application themes (e.g., light/dark mode).
- **Timeline**: Events in the order they happened, across the page or down it
  (`new Timeline().vertical()`). Add them one at a time — `addEvent("09:14", "Order placed", "Paid
  by card")` — and the line joining one to the next is drawn and redrawn for you. An event's words
  are never shortened: a long description wraps inside its box, the box stops growing at about
  20rem (`setEventWidth`), and a timeline laid out in a row scrolls sideways rather than pushing
  the page out of shape. Any component can still be added as a step by hand.
- **Toast**: A brief, auto-expiring notification message overlaid on the screen.
- **TokenMeter**: A specialized visualization component (e.g., for showing API token usage).
- **Tooltip**: A small informational popup shown when hovering over an element.
- **VirtualScroller**: An optimized list container that only renders visible items for high performance with large datasets.

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
- **Component**: The base class for all UI elements, wrapping a DOM node.
- **AbstractField**: The foundational class for input components.
- **HasComponents**: Interface for containers that can hold child components.
- **HasValue**: Interface for components that handle data binding.
- **HasSize / HasStyle / HasText / HasEnabled**: Mixin interfaces for standard component properties.
- **DomListenerRegistration / EventListener / ComponentEvent / ClickEvent**: Infrastructure for DOM event handling and custom component events.
- **Focusable**: Interface for components that can receive keyboard focus.

### Shared Styling
Package `com.zeroz4j.ui.theme`. Not components — the small vocabulary every component and every
screen shares, so the same thing is asked for by name rather than described again.
- **TextStyle**: The five sizes of text, by name — see [Naming text sizes](#naming-text-sizes).
- **ThemeColor**: The DaisyUI colour names a component can be given, for the components that take
  one (`setThemeColor`).
- **ThemeSize**: The size names a component can be given, for the components that take one.
