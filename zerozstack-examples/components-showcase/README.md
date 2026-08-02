# Zeroz4j Example: components-showcase

The gallery for `zerozstack-ui-components`. If you want to know what a component looks like, how it is
constructed, or which variants it supports, this is the example to run and to read.

It is also the only example that exercises the component library broadly: 80 registered component
showcases, plus a data-binding view, a role-gated admin view and an HTML-template view.

## What it demonstrates

- **The component catalogue** — 80 showcase panels, one per component, registered in
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
  changes. No router is involved (`@Route` is not implemented).
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

Build once from the repository root, then start this example. It binds port 8080, so stop any other
example first.

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
java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer
```

Use `:` as the classpath separator on Linux and macOS. There is no executable jar, so `java -jar` does
not work.

Then open <http://localhost:8080> and sign in:

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
│     └─ showcase/                ComponentShowcase base class, ShowcaseRegistry, 54 panels
└─ showcase-server/    Helidon server, EclipseStore boilerplate
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
