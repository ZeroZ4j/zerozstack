# Routing: URLs, Views and Colocated Loading

ZeroZ Stack maps real URLs to Java views, and each route declares **the data it needs** alongside the
path it answers to. The loading happens before the view is built, so a view never exists in a
half-loaded state and never fetches from inside a component that is already on screen.

Routes are found at compile time by the annotation processor and turned into a route table. Nothing
is discovered by reflection, because the browser runtime has none — which also means a route that
does not compile is not a route.

## A route

```java
@Route("/tasks/:id")
public class TaskDetailView implements RouteView<Task> {

    private final TaskService tasks = TaskService_Stub.create();

    @Override
    public Task load(RouteParams params) {
        return tasks.byId(params.getLong("id"));   // reads as blocking; suspends the coroutine
    }

    @Override
    public Component render(Task task, RouteParams params) {
        return new Div(new Span(task.getTitle()), new Span(task.getDetail()));
    }
}
```

`load` runs and completes first; `render` receives what it returned. A view that needs no data
implements `RouteView<Void>` and leaves `load` alone.

Start the router once, pointing at the element it owns:

```java
Zeroz4jClient.connect(wsUrl, () -> Router.start("app-root"));
```

`connect`'s second argument runs once, on the first connection. `RmiSecurityContext.onAuthenticated`
and `onResolved` do not: they run again after every reconnect, because every new connection signs in
again. Starting the router from one of those renders the current address again on each reconnect and
adds any listener registered alongside it once more, so guard it with a flag if that is where it
lives — `zerozstack-examples/routing-tour` shows how.

## Paths

Real paths through the history API — `/tasks/42`, not `#/tasks/42`. A segment beginning with `:` is a
parameter.

```java
@Route("/")                  // the landing view
@Route("/tasks")             // literal
@Route("/tasks/:id")         // one parameter
@Route("/teams/:team/tasks/:id")
```

Matching prefers the more specific pattern, so `/tasks/new` wins over `/tasks/:id` whatever order the
compiler emitted them in. Two routes claiming the same path is a startup error rather than a race
decided by build order.

Read parameters from `RouteParams`:

```java
params.get("id")               // "42"
params.getLong("id")           // 42 — throws if absent or not a number
params.query("page")           // from ?page=2
params.queryLong("page", 1)    // falls back instead of throwing
```

The difference is deliberate. A path parameter that will not parse means a broken link, so it throws.
A query parameter is usually a user-adjustable option, so it falls back.

**Because the paths are real, the browser asks the server for them.** Opening `/projects/42` from a
bookmark, reloading it, or sharing the link is an HTTP request for a path with no file behind it —
so the server has to answer with the application shell and let the router resolve it once the page
loads. `StaticContentResource` does this: an unmatched path that does not look like a file falls back
to `index.html`.

A missing *asset* still returns 404. Serving the shell for `/js/classes.js` would hand the browser a
page where it expected a script, and the failure would surface as an unreadable syntax error rather
than a missing file. The last path segment containing a dot is what tells them apart.

## Layouts

Chrome shared across routes — a navigation bar, a sidebar — is a `RouteLayout`, and children name it:

```java
@Route("/")
public class AppShell implements RouteLayout<User> {

    @Override
    public User load(RouteParams params) { return users.current(); }

    @Override
    public Component render(User user, RouteParams params, Component child) {
        return new Div(
                new Div(new Span(user.getName())),   // the chrome
                child);                              // where the matched route goes
    }
}
```

```java
@Route(value = "/tasks/:id", layout = AppShell.class)
public class TaskDetailView implements RouteView<Task> { ... }
```

Layouts nest: a layout may declare a `layout` of its own, and the chain is built outward from the
matched route. Loading the current user *once in the shell* is what stops every view underneath
fetching it separately.

## What happens on a navigation

1. The path is matched, most specific pattern first.
2. The layout chain is resolved outward from the matched route.
3. Every `@RequiresRole` in that chain is checked.
4. Each level's `load` runs, outermost first, so a nested route can rely on what its layout fetched.
5. Only then are components built, innermost first, each layout wrapping its child.
6. The container's contents are replaced in one go, and the view being left is shut down: its
   `onDetach` runs, and so does the `onDetach` of everything inside it.

Nothing reaches the screen until every loader has returned.

Step 6 is where a view stops whatever it started. Put a timer, an `Effect` or a `ServerEvents`
subscription on the screen in `onAttach` and stop it in `onDetach`, and navigating away really does
stop it — before 0.8.0 the container was emptied by hand and `onDetach` never ran, so the screen
somebody had left went on working. See
[Swapping what is inside something](UI_COMPONENTS.md#swapping-what-is-inside-something).

### Loaders run in sequence, not in parallel

Client code runs on a single cooperative scheduler and cannot create threads, so a layout's loader
and its child's cannot overlap — two round trips are two round trips. **The win here is the ordering
guarantee, not concurrency**: data is fetched before rendering rather than from inside a mounted
component, and shared data is fetched once in a layout instead of repeatedly in its children. If you
need one round trip, make it one service call.

## Navigating

```java
Router.navigate("/tasks/42");    // adds a history entry
Router.replace("/login");        // replaces it — for a redirect Back should not re-enter
```

In markup, add `data-route` to an anchor and the router takes it over:

```html
<a data-route href="/tasks/42">Open task</a>
```

Only anchors carrying that attribute are intercepted — taking over every link would swallow links to
other sites and to downloads. Modified clicks (new tab, new window) are always left to the browser.

A link or `navigate` to the route already on the screen does nothing, and neither does one to the
route already loading. The query string counts: `/projects?sort=name` from `/projects` is a
navigation. The one exception is a route whose last navigation failed — asking for it again runs it
again. `replace`, Back and Forward always run.

## Showing that a page is loading

```java
Router.showBusyIndicator(true);
Router.showFailureMessage(true);
```

Two lines, usually next to `Router.start`. Both are off unless switched on. Neither needs a click
listener, a popstate listener or a wrapper around `navigate` and `replace`: they follow every
navigation the router runs, however it started.

**The busy indicator** appears once a navigation has been running for 300 milliseconds, so a fast
page shows nothing at all. It is three things at once: a 3-pixel bar sweeping along the top edge, a
48-pixel spinner on a small card in the middle of the window, and a wait cursor over the whole page,
which wins over the pointer a link or a button would show. The card is a `role="status"`,
`aria-live="polite"` region, so a screen reader hears "Loading". Under `prefers-reduced-motion` the
spinner and the bar pulse instead of moving. Nothing covers the page, and nothing takes a click.

It hides the moment the navigation started last finishes or fails. **It has no timeout of its own.**
It is never hidden on a timer while the work is still running; a navigation that cannot complete
ends because its call really fails, and the indicator goes with it. See
[When a page cannot be opened](#when-a-page-cannot-be-opened) for how long that takes.

**Its look is yours to set** with CSS custom properties, set anywhere the elements inherit from —
`:root`, `body`, or the element carrying a DaisyUI `data-theme`:

| Property | Default | What it is |
|---|---|---|
| `--zeroz4j-busy-color` | `var(--color-primary)` | The bar, the spinner, the Retry button |
| `--zeroz4j-busy-bar-height` | `3px` | The bar's thickness |
| `--zeroz4j-busy-spinner-size` | `48px` | The spinner's diameter |
| `--zeroz4j-busy-card-size` | `72px` | The card's width and height |
| `--zeroz4j-busy-card-background` | the page's text color at 7% over `var(--color-base-100)` | The card |
| `--zeroz4j-busy-card-border` | the page's text color at 14% | The card's edge |
| `--zeroz4j-busy-card-radius` | `16px` | The card's corners |
| `--zeroz4j-busy-offset-x`, `--zeroz4j-busy-offset-y` | `0px` | How far the card sits from the middle of the window |
| `--zeroz4j-busy-z-index` | `9999` | What it is drawn above |

Every default is a DaisyUI token with a plain color behind it, so it is right in a light theme and a
dark one without a rule for either, and still draws in a page with no DaisyUI at all.

The offset is for a layout with a side menu, where the middle of the window is not the middle of the
content. With an 18rem menu on the left from 1024 pixels up:

```css
@media (min-width: 1024px) {
    html:has(.side-menu) { --zeroz4j-busy-offset-x: 9rem; }
}
```

For anything the properties do not reach, the elements are a contract: `#zeroz4j-busy-bar`,
`#zeroz4j-busy-spinner` (holding `.zeroz4j-busy-ring` and the visually hidden `.zeroz4j-busy-label`),
and the class `zeroz4j-busy` on `<html>`, which is present while the indicator shows and only then.
The router's stylesheet goes in first in `<head>`, so a stylesheet of the application's own wins
over it at equal specificity.

## When a page cannot be opened

A navigation whose loader throws leaves the page as it was — replacing a working view with a blank
one because a fetch failed loses whatever the user was doing. The address bar keeps the address
that failed, so reloading the page tries it again.

**The failure message** (`Router.showFailureMessage(true)`) is a short box at the bottom of the
window, announced to a screen reader as an alert, with a **Retry** button and a **Dismiss** button.
Both are real buttons, first in the page's Tab order, pressed with Enter or Space. What it says
depends on why the navigation failed:

| Why | It says | Retry |
|---|---|---|
| The connection dropped, went silent, or the server did not answer in time | We could not open this page. Check your connection and try again. | yes |
| A loader's call failed any other way — the server refused it, or it threw | We could not open this page. Something went wrong while loading it. | yes |
| No route matches, and no not-found route is set | There is no page at this address. | no |
| A `@RequiresRole` refuses, and no forbidden route is set | You do not have access to this page. | no |

It goes away when the next navigation starts, Retry's included, and when Dismiss is pressed. Only the
navigation started last can show it: a slow page overtaken by a click elsewhere never puts its
failure over the page that was asked for. The words come from the framework's own catalog, so a
deployment offering German gets them in German. The elements are `#zeroz4j-navigation-failure`, holding
`.zeroz4j-navigation-failure-text`, `.zeroz4j-navigation-failure-note`,
`.zeroz4j-navigation-failure-retry` and `.zeroz4j-navigation-failure-dismiss`. Its look is set with
`--zeroz4j-failure-background`, `--zeroz4j-failure-color`, `--zeroz4j-failure-accent`,
`--zeroz4j-failure-radius`, `--zeroz4j-failure-bottom` (default `24px`) and
`--zeroz4j-failure-offset-x`, which defaults to the busy indicator's offset.

**What reconnecting does, and does not do.** The framework reconnects a dropped socket by itself.
It does **not** run the failed navigation again by itself: a navigation is loader calls, and RMI
calls are never replayed, because the framework cannot know whether repeating one is safe. While
the connection is still down the message adds "Reconnecting. Retry will work once the connection is
back." and Retry does nothing; once the connection is back that line goes and Retry runs the
navigation again. `Router.retry()` does the same from code.

**How long a lost connection takes to show.** A socket the browser sees closing fails every call at
once. A network that goes silent without closing anything is found by the keepalive: a call that has
waited five seconds with nothing arriving from the server sends a ping, and a ping with no answer
within ten seconds closes the connection, so the page fails about fifteen seconds after it was
asked for. The server answers pings ahead of everything else on the connection, so a slow call on a
live connection is never mistaken for a dead one. A call the server simply never answers fails at
the request timeout, 30 seconds by default, with a `RequestTimeoutException`. Change those with
`Keepalive.configureLiveness(seconds)` and `WasmRmiClient.setRequestTimeout(millis)`.

## Navigation events

The indicator and the message are built on a public listener, and an application drawing its own
uses the same one:

```java
Disposable events = Router.addLifecycleListener(new Router.LifecycleListener() {
    @Override public void onNavigationStarted(Navigation navigation) { spinner.show(); }
    @Override public void onNavigationFinished(Navigation navigation, RouteParams params) { spinner.hide(); }
    @Override public void onNavigationFailed(Navigation navigation, Throwable reason) { spinner.hide(); }
});
```

What is promised:

- **Every way in raises "started"**: a `data-route` link, `navigate`, `replace`, Back and Forward,
  the first render in `start`, a redirect to the not-found or forbidden route, and `retry`.
  `Navigation.trigger()` says which.
- **The navigation started last always ends in exactly one "finished" or "failed".** So a listener
  that turns something on at "started" and off at the other two is never left on.
- **An older navigation still loading when a newer one starts is superseded.** It raises nothing
  more: not "finished", not "failed", even when its loader comes back afterwards. Its view is not
  mounted and `currentPath()` does not change. Every navigation carries a sequence number,
  `Navigation.id()`, and the newer one names the one it overtook in `supersededId()`. A superseded
  navigation also stops before its remaining loaders, so a page nobody will see costs no more round
  trips than it had already started.
- **A redirect is a navigation of its own.** An address nothing matches, with a not-found route set,
  raises "started" for the address, then "started" for the not-found route — which supersedes the
  first and names it in `redirectedFrom()` — then "finished" for the not-found route.
- **A link or `navigate` to what is already on screen, or already loading, raises nothing at all**,
  unless the last navigation to it failed.
- **Events arrive one at a time, in order.** A listener that navigates from inside a callback does
  not interrupt the event being delivered: every listener hears the current event first.

`Router.latestNavigation()` is the navigation started last, whatever became of it.

## Deployed somewhere other than the site root

A WAR is usually deployed under a context path — `/coachapp`, `/clientportal` — and then the browser
shows `/coachapp/tasks/42` for the route `/tasks/42`. **Route paths never change.** `@Route` declares
`/tasks/:id`, `Router.navigate("/tasks/42")` takes that, and `RouteParams.getPath()` reports it; the
router translates to and from browser locations through `AppBase`, which reads the application's root
from `document.baseURI`.

That works because the server serves the shell with a `<base href>` for its own context path —
`StaticContent` does this for both bindings, so no application configures it and nothing has to be
rebuilt to move a deployment. It is also what makes a deep link's relative asset references resolve:
`js/classes.js` in a shell served for `/coachapp/tasks/42` means `/coachapp/js/classes.js`, not
`/coachapp/tasks/js/classes.js`.

Two things an application still writes for itself, and both have a helper:

```java
Zeroz4jClient.connect(Zeroz4jClient.defaultWebSocketUrl(), () -> Router.start("app-root"));
anchor.setAttribute("href", AppBase.location("/tasks/42"));   // /coachapp/tasks/42
```

An `href` has to carry the context path, because middle-click and "open in new tab" go to the server
rather than through the router. The router accepts either form on the way back in, so a click on such
an anchor still resolves to the route `/tasks/42`.

Write relative references in `index.html` (`js/classes.js`, `manifest.webmanifest`), not absolute
ones. An absolute `/js/classes.js` ignores the base element and escapes the context path.

## Guarding routes

```java
@Route(value = "/admin", layout = AppShell.class)
@RequiresRole("admin")
public class AdminView implements RouteView<Void> { ... }
```

Checked against `RmiSecurityContext`, which is populated from the server at connect. Every layout in
the chain is checked too, so a guarded shell protects everything inside it.

**This check decides nothing on its own.** A client-side check decides what to show; the server
checks every RMI call again against `@Secured` and `@RolesAllowed`, and that is the check that
counts. Skipping the annotation only means the user reaches a view whose calls then fail.

## Fallbacks

```java
Router.notFoundRoute("/not-found");
Router.forbiddenRoute("/login");
Disposable errors = Router.addErrorListener((path, reason) -> toast.show("Could not open " + path));
```

A navigation that fails reports to every error listener, in the order they were added.
`Router.onError(handler)` is the same call under its older name; up to and including 0.9.0 it
replaced the previous handler, and it now adds. With no error listener at all, the failure is
written to the console rather than vanishing. The reason is a `RouteNotFoundException` for an
address nothing matches and a `RouteForbiddenException` for a refused `@RequiresRole`, when no
fallback route is set; otherwise it is whatever the loader threw — `DisconnectedException` or
`RequestTimeoutException` when the connection was the problem.

## Rules the compiler enforces

The processor refuses, at compile time:

* a `@Route` class implementing neither `RouteView` nor `RouteLayout`
* a `@Route` class implementing both, where whether it renders a child would be ambiguous
* a `@Route` class without a public no-argument constructor — the router builds it without reflection
* a path not starting with `/`

## Limits

* **No parallel loaders**, as above.
* **No nested outlets beyond one child per layout.** A layout renders exactly one child; sibling
  outlets are not modeled.
* **No wildcard or optional segments.** Patterns are literal segments and `:params` with a fixed
  count; `/files/*path` is not supported.
* **No route-level transitions or scroll restoration.** The container's contents are replaced
  outright.
* **The whole view is rebuilt on every navigation**, including a layout that did not change. Layout
  loaders therefore re-run when navigating between two children of the same layout.
* **No lazy loading.** Everything is in one bundle; a route does not defer any code.
* **A superseded navigation's call in flight is not canceled.** The server still runs it; only its
  answer is ignored. Overtaking a navigation stops it before its next loader, not during the current
  one.
