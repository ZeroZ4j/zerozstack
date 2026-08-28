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
Router.onError((path, reason) -> toast.show("Could not open " + path));
```

A navigation whose loader throws leaves the page as it was and reports through the error handler —
replacing a working view with a blank one because a fetch failed loses whatever the user was doing.
Without a handler the failure is logged to the console rather than vanishing.

## Rules the compiler enforces

The processor refuses, at compile time:

* a `@Route` class implementing neither `RouteView` nor `RouteLayout`
* a `@Route` class implementing both, where whether it renders a child would be ambiguous
* a `@Route` class without a public no-argument constructor — the router builds it without reflection
* a path not starting with `/`

## Limits

* **No parallel loaders**, as above.
* **No nested outlets beyond one child per layout.** A layout renders exactly one child; sibling
  outlets are not modelled.
* **No wildcard or optional segments.** Patterns are literal segments and `:params` with a fixed
  count; `/files/*path` is not supported.
* **No route-level transitions or scroll restoration.** The container's contents are replaced
  outright.
* **The whole view is rebuilt on every navigation**, including a layout that did not change. Layout
  loaders therefore re-run when navigating between two children of the same layout.
* **No lazy loading.** Everything is in one bundle; a route does not defer any code.
