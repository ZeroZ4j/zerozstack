# Routing tour

Every routing feature the framework has, in one running application: real URLs, path parameters,
nested layouts, data loaded by the route rather than by the component, role guards, and fallbacks for
paths that do not match or are not permitted.

## Run it

```bash
mvn -pl zerozstack-examples/routing-tour/routing-tour-server -am install
java -jar zerozstack-examples/routing-tour/routing-tour-server/target/routing-tour-server-0.9.0.jar --dev-login
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

## What it does not show

Loaders run one after another, not in parallel — the browser runtime is a single cooperative
scheduler and cannot overlap them. The value here is the ordering guarantee and loading shared data
once in the layout. See [ROUTING.md](../../docs/ROUTING.md) for the full list of limits.
