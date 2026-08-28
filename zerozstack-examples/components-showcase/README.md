# Zeroz4j Example: components-showcase

The gallery for `zerozstack-ui-components`. If you want to know what a component looks like, how it is
constructed, or which variants it supports, this is the example to run and to read.

It is also the only example that exercises the component library broadly: 80 registered component
showcases, plus a data-binding view, a role-gated admin view and an HTML-template view.

## Under pressure — the hard pages

A showcase proves a component renders. It does not prove the component can be used. The **Under
pressure** group in the sidebar exists because that difference cost this library six real faults in
one week, all of them underneath a gallery that drew every overlay perfectly.

Each page there takes the hardest realistic case rather than the easiest possible one, and each
carries a short note saying what a reader should try and what would count as broken.

| Page | What it pushes on |
|---|---|
| **One thing inside another** | A drawer holding a form holding a button that opens a dialog that opens a second dialog. A dropdown, a right-click menu, a tooltip and a toast raised from inside a dialog. |
| **The four states of a panel** | Nothing asked for yet, waiting, failed, arrived — switched between, so each affordance (`EmptyState`, `Skeleton`, `Loading`, `Alert`, `PanelFrame`) is seen where it belongs rather than in a row. |
| **A form that fails and recovers** | Twelve bound fields filled in wrongly on purpose: every error at once, then corrected one at a time, then saving, then a server refusal the browser could not have predicted. One field is valid alone and wrong in combination. |
| **A list that moves** | Rows arriving, changing and leaving on a timer while the reader scrolls, types in a filter and has a row's button focused. Two lists side by side — one rebuilt from scratch, one patched by key — and a running count of how often the keyboard was thrown away. |
| **Long words, other languages** | A 63-letter German compound, an 80-letter one, a Japanese sentence, an Arabic sentence that runs right to left, and 400 characters with no space in them — in table cells, tabs, menus, badges, KPI tiles, timeline events, breadcrumbs, tooltips and dialog titles. |
| **Everything, 360 pixels wide** | `Table`, `MetricTable`, `SplitPane`, `LaneTimeline`, `PanelFrame` and a dialog asked for 56 rem, all inside a box the width of a telephone, so a narrow window can be judged without resizing the browser. |

## Driving the whole gallery with the keyboard

`keyboard-walkthrough.mjs` signs in, reaches every page in the sidebar and walks every control —
**with no mouse at all**. There is no `page.click`, no `page.hover` and no `mouse.move` anywhere in
it: only Tab, Enter, Space, Escape and typing. A control a mouse can work and a keyboard cannot is
invisible to it, which is the point.

It reports, per page: what Tab never reached, what Enter and Space did nothing to, what has no
name a screen reader could read, where the keyboard went on its own, and anything it could not get
out of with Escape.

Start the server (below), then:

```bash
node keyboard-walkthrough.mjs --url http://localhost:8080
```

Options: `--headed` to watch it, `--widths 1920,1280,380` (the default; every page is walked at
1280 and the width-sensitive ones at the other two), `--only "Table,Dialog"` to walk a few pages,
and `--playwright <dir>` to say where Playwright lives. It **borrows** Playwright from another
checkout on this machine — nothing is installed here, and `npm install` is never run.

Screenshots and `findings.txt` land in `shots/`.

**What it cannot see.** It is a machine pressing keys, not a person reading a screen. It does not
know whether a name is a *good* name, whether the focus ring is visible, whether the colors have
enough contrast, or whether a message makes sense. It also cannot tell a deliberate refusal from a
fault: the two dialogs that ignore Escape on purpose are reported the same way as one that ignores
it by accident, and the page itself is what says which is which. Treat its output as a list of
places to look, not a verdict.

It also cannot always tell what is on the screen. A folding section that is shut still has a size
in Chrome — the browser lays its contents out and then declines to paint them or let the keyboard
into them — so anything the walkthrough decides by measuring a rectangle has to be checked against
the folding sections above it as well. It does that now, after a run in which it reported a
nested section's handle as unreachable at all three widths and the browser had been right all
along.

## What it demonstrates

- **The component catalog** — 80 showcase panels, one per component, registered in
  `ShowcaseRegistry` and grouped into categories (charts, dashboard panels, actions, data display,
  navigation, data input, layout, feedback, mockups). Each panel is a `ComponentShowcase` subclass
  showing the component in its variants with a short description.
- **The chart set** — the *Charts* and *Dashboard Panels* groups cover
  `com.zeroz4j.ui.chart`: time series, rolling telemetry, gauges, bar gauges, bar charts, heatmaps,
  histograms, scatter, donut, treemap, state timelines, status history, plus the panel chrome
  (`PanelFrame`, `TimeRangePicker`, `RefreshControl`, `MetricTable`, `LogViewer`,
  `ColorScaleLegend`). Sample data comes from `DemoData`, which is seeded rather than random so the
  gallery renders identically on every load and can be screenshot-tested.
- **Signal-driven navigation** — `MainLayout` holds `ValueSignal<ViewType> currentViewSignal` and
  `ValueSignal<String> currentComponentSignal`; an `Effect` swaps the content area when either
  changes, with `contentArea.replaceContents(view)`. No router is involved (`@Route` is not
  implemented).
- **A page that shuts itself down when you leave it** — *A list that moves* starts a timer, an
  effect and a keyed list, and stops all three in `onDetach`. That is the half an application has
  to write; `replaceContents` is the half the framework supplies. Emptying the content area by hand
  instead — which is what this example did until 0.8.0 — left two timers running per visit, for as
  long as the page was open.
- **`bindText` with `Computed`** — the "Current value:" readouts under the input components derive
  their text from the field's signal, so they cannot drift from what the field holds.
- **Role-gated UI** — the Admin menu item is only added when
  `RmiSecurityContext.hasAnyRole("admin")` (`MainLayout.java:110`). Sign in as `admin` to see it.
  Remember that hiding a menu item is cosmetic; the server-side `@RolesAllowed` check is the boundary.
- **The `Binder` API** — `ProfileView` uses `com.zeroz4j.ui.binding.Binder` for bean-style binding.
  Note that this is a *different* binding path from the `bindValue` / `withRule` approach the other
  examples use; see the caveat below.
- **An HTML template view** — `HtmlExampleView`, for the cases where you want markup rather than
  programmatic composition.

## Run it

Build once from the repository root, then start this example. It binds port 8090, which no other
example uses, so it can run beside them. Add `--port 9000` (or `run.bat 9000`) to move it.

```bash
mvn clean install -DskipTests
```

**Windows**

```bat
cd zerozstack-examples\components-showcase
run.bat
```

**Any OS**

```bash
cd zerozstack-examples/components-showcase/showcase-server
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer --dev-login
```

Several examples in this repository want port 8080 and a machine can only give it to one of them.
Pass `--port` to move this one out of the way:

```bash
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer --dev-login --port 8095
```

Use `:` as the classpath separator on Linux and macOS. There is no executable jar, so `java -jar` does
not work.

Then open <http://localhost:8090> and sign in:

| Username | Password | Roles | Sees the Admin view |
|---|---|---|---|
| `demo` | `demo` | `user` | no |
| `admin` | `admin` | `user`, `admin` | yes |

## Structure

```
components-showcase/
├─ showcase-shared/    models and @RmiService interfaces
├─ showcase-client/
│  └─ .../client/
│     ├─ ExampleClientApp.java    entry point — Zeroz4jClient.connect(...)
│     ├─ MainLayout.java          navigation, theme toggle, signal-driven view swapping
│     ├─ ShowcaseView.java        hosts the selected component showcase
│     ├─ DashboardView.java       landing view
│     ├─ ProfileView.java         Binder-based data binding
│     ├─ AdminView.java           role-gated view
│     ├─ HtmlExampleView.java     HTML-template alternative
│     └─ showcase/                ComponentShowcase base class, ShowcaseRegistry, the panels
│                                 (including the six Under pressure pages and FieldStates,
│                                  the shared "every state a field really has" helper)
├─ showcase-server/    Helidon server, EclipseStore boilerplate
├─ keyboard-walkthrough.mjs   drives the whole gallery with the keyboard alone
└─ shots/              screenshots and findings.txt, written by the walkthrough
```

To add a showcase: extend `ComponentShowcase` (it gives you `addTitle`, `addDescription` and
`addSection`), then register it in `ShowcaseRegistry` under its DaisyUI-style key.

## Caveats — do not copy these patterns

Two things in this example are not the recommended approach.

**Unconsumed pushes.** `GalleryServiceImpl` and `UserServiceImpl` call the low-level
`WasmRmiServerEngine.broadcastPush(String, Object)` for topics such as `gallery.slider_updated` and
`user-notifications` — and **no client registers a listener for any of them**. For server-to-client
events use a typed `EventTopic` with `EventPublisher` and `ServerEvents.on`, as `chat-events` does.
See [Anti-patterns](../../docs/decide/antipatterns.md#unconsumed-push).

**Two binding APIs.** This example uses `Binder`, while every other example uses `field.bindValue(signal)`
with `field.withRule(...)`. Both exist in the library; the signal-based path is the one the rest of
the documentation describes. Pick one per application rather than mixing them.

## See also

- [UI components](../../docs/UI_COMPONENTS.md) — the component documentation
- [Choosing how state moves](../../docs/decide/index.md) — before you wire state into a view
- [Examples index](../../docs/examples/index.md)
