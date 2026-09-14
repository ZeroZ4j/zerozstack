# Routing tour

Every routing feature the framework has, in one running application: real URLs, path parameters,
nested layouts, data loaded by the route rather than by the component, role guards, fallbacks for
paths that do not match or are not permitted, and what a person sees while a page loads and when it
cannot be opened.

## Run it

```bash
mvn -pl zerozstack-examples/routing-tour/routing-tour-server -am install
java -jar zerozstack-examples/routing-tour/routing-tour-server/target/routing-tour-server-0.10.0.jar --dev-login
```

Then open <http://localhost:8091/?user=admin&password=admin>.

`--dev-login` switches on the framework's built-in development accounts, so the guarded route has
something to check: `demo`/`demo` holds `user`, `admin`/`admin` also holds `admin`. Without the flag
nothing can sign in, and the server says so when it starts. Sign in as `demo` and `/admin` bounces to `/forbidden`.

## What each route demonstrates

| Route | Class | Shows |
|---|---|---|
| `/` | `HomeView` | The simplest route: a path, no parameters, no data |
| `/projects` | `ProjectListView` | A loader, and query parameters (`?sort=name`) read with a fallback |
| `/projects/new` | `NewProjectView` | A literal beating `/projects/:id`, whatever order the compiler emitted |
| `/projects/:id` | `ProjectDetailView` | A path parameter, and one route loading two things before it renders |
| `/projects/:projectId/tasks/:taskId` | `TaskDetailView` | Two parameters in one pattern |
| `/slow` | `SlowView` | A loader that takes two seconds, so the router's busy indicator shows |
| `/stalled` | `StalledView` | A loader the server holds for 45 seconds, past the request timeout. Not linked; see below |
| `/admin` | `AdminView` | `@RequiresRole("admin")` |
| `/not-found`, `/forbidden` | — | Where unmatched and refused navigations land |
| *(all of the above)* | `AppShell` | A layout loading the account once per navigation for every view under it |

## Things worth trying

- **Reload on a deep link.** Open `/projects/1/tasks/11` and press refresh. The browser asks the
  server for that path; the server has no file there and serves the application shell, and the router
  resolves it. This is the case that 404s if the server is not set up for real URLs.
- **Sign in as `demo`** and click Admin. The guard sends you to `/forbidden` — and note the guard is
  only deciding what to *show*; the server re-checks every call regardless.
- **Press Back** after a few navigations. Ordinary history entries, except `/not-found` and
  `/forbidden`, which replace rather than push so Back does not walk into them.
- **Watch the network panel.** Each navigation issues its loader calls before anything renders, and
  the view appears once. Nothing fetches after mounting.
- **Click Slow page.** After 300 milliseconds a bar sweeps along the top, a spinner appears in the
  middle and the cursor turns to a wait cursor. They go the moment the page is on screen. None of it
  is code in a view: the router's busy indicator is on by default, and every other page here is too
  fast for it to appear.
- **Click Slow page, then Projects before it finishes.** Projects stays, with `/projects` in the
  address bar, even though the slow page's answer arrives afterwards. The same happens if you press
  Back while it loads, or click the link for the page still showing.
- **Stop the server while Slow page is loading**, then start it again. The failure message appears at
  the bottom: "We could not open this page. Check your connection and try again." While the server is
  down it says Retry will work once the connection is back, and Retry does nothing. Once the tab has
  reconnected by itself, Retry opens the page. The page is not opened again on its own: that would be
  repeating a call, and calls are never repeated for you. This is the router's failure message, also
  on by default.
- **Open `/stalled?user=admin&password=admin&timeout=3000`** from the home page's address. The server
  does not answer in time, and four or five seconds later the navigation fails with the same message.
  `timeout` sets the client's request timeout for this tab; without it the wait is the default 30
  seconds. The server keeps working on that call for 45 seconds, and this connection's later calls
  wait behind it, so reload the page afterwards rather than clicking on.

## The browser test

`tools/navigation-proof` starts this server, puts a small proxy of its own between Chrome and it so
it can cut, refuse or silence the connection, and checks everything in the list above with real
clicks and real key presses. Its README says how to run it, including against a minified build.

## What it does not show

Loaders run one after another, not in parallel — the browser runtime is a single cooperative
scheduler and cannot overlap them. The value here is the ordering guarantee and loading shared data
once in the layout. See [ROUTING.md](../../docs/ROUTING.md) for the full list of limits.
