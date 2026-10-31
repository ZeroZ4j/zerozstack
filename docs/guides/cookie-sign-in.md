# Signing in with a cookie

A username and password form, a session cookie, and everything around them: renewal, the page the
person asked for, sign-out, and revocation that closes live connections. Available from 0.11.0.

## When to use this

When your application keeps its own accounts and wants the sign-in every web application has. If a
provider such as Keycloak holds the accounts, use [OpenID Connect](oidc-auth.md). If you want to
decide identity yourself from something else on the handshake, write an
[`AuthenticationProvider`](security-auth.md#replacing-the-development-provider). All three can sit
in one application.

## The shape of it

```
Login form ──POST──▶ /zeroz4j-session/sign-in ──▶ SignInHandler.signIn
    ◀── 303 and an HttpOnly cookie, to the page that was asked for

page loads, opens WebSocket, browser attaches the cookie
    ──▶ handshake ──▶ cookie verified ──▶ SignInHandler.resume ──▶ fresh roles

after 8 hours ──▶ server closes the connection ──▶ client reconnects ──▶ checked again
cookie over an hour old ──▶ AUTH frame says so ──▶ client posts /zeroz4j-session/renew
```

The cookie is read only at the WebSocket handshake. HTTP requests for pages and scripts ignore it,
so authentication stays where it has always been in this framework: a `@Secured` call on a
connection.

## Server side

Write one `@ApplicationScoped` bean implementing `SignInHandler`. That bean is what turns the feature
on. With none, the addresses below answer 404 and no cookie is read.

```java
@ApplicationScoped
public class Accounts implements SignInHandler {

    @Inject UserStore users;

    @Override
    public AuthenticatedPrincipal signIn(SignInAttempt attempt) {
        User user = users.byEmail(attempt.username());
        if (user == null || !user.passwordMatches(attempt.password())) {
            return null;                          // the form comes back with an error
        }
        return new AuthenticatedPrincipal(user.email(), user.roles(), user.tenant());
    }

    @Override
    public AuthenticatedPrincipal resume(SignInSession session) {
        User user = users.byEmail(session.name());
        if (user == null || user.disabled()
                || session.startedAt().isBefore(user.passwordChangedAt())) {
            return null;                          // this session is over
        }
        return new AuthenticatedPrincipal(user.email(), user.roles(), user.tenant());
    }
}
```

`signIn` checks the password. `resume` says who a valid cookie is **now**, and runs at every
handshake and every renewal. The cookie carries the name, the tenant and three times. It never
carries roles, so a role change reaches the next connection by itself.

Hash passwords with a slow function, and hash against a dummy value when the name is unknown, or the
time an answer takes says which names exist. `Accounts` in the `sign-in` example does both with
nothing but the JDK.

## Settings

Put them in the application's property file. The server reads `zeroz4j.properties` from the working
directory, then from the classpath, and `-Dzeroz.settings.file=path` names another file. A `-D` beats
the file. Environment variables are not read. Every other ZeroZ Stack setting can live in the same
file.

```properties
# Keep this file out of version control.
zeroz.signin.secret=at-least-32-characters-from-a-password-generator
```

| Setting | Default | Meaning |
|---|---|---|
| `zeroz.signin.secret` | generated at startup | The signing key, at least 32 characters. **Set it in production.** Generated, every restart signs everybody out and other nodes refuse the cookies. Shorter than 32 is refused. Changing it signs everybody out. |
| `zeroz.signin.days` | 30 | How long a cookie lasts with no renewal. |
| `zeroz.signin.maxDays` | 365 | How long renewal can carry a session, counted from the password. |
| `zeroz.signin.renewMinutes` | 60 | How old a cookie must be before it is renewed. |
| `zeroz.signin.connectionMinutes` | 480 | How long a connection on a session cookie stays open. |
| `zeroz.signin.maxAttempts` | 10 | Sign-in attempts in a row for one name. |
| `zeroz.signin.lockMinutes` | 15 | How long after the last attempt the count starts again. |
| `zeroz.signin.cookieName` | `zeroz_session` | The cookie's name. |
| `zeroz.signin.sameSite` | `Strict` | `Lax` is the other value. |
| `zeroz.signin.secureCookie` | on, except a page on this machine | Forces `Secure` on or off. |

`Secure` stays on when the server sees plain HTTP on a real host name, because that is what a server
behind a proxy ending TLS sees. If you really serve plain HTTP on a real name, the browser drops the
cookie and sign-in appears to do nothing; set `zeroz.signin.secureCookie=false`, and a warning in the
log says so.

An application at the site root on HTTPS can set `zeroz.signin.cookieName=__Host-zeroz_session`. The
browser then refuses that name unless it came from this exact host, which stops a sibling subdomain
planting a cookie.

## Browser side

```java
public static void main(String[] args) {
    Zeroz4jClient.connect(AppBase.webSocketUrl(),
            () -> RmiSecurityContext.onResolved(() -> new Thread(App::show).start()));
}

private static void show() {
    if (shown) {               // onResolved runs again after every reconnect
        return;
    }
    shown = true;
    HTMLElement root = Window.current().getDocument().getElementById("app-root");
    Component.replaceContents(root,
            RmiSecurityContext.isAuthenticated() ? new HomePage() : new Login());
}
```

`new Login()` is a real `<form method="post">` with `name` and `autocomplete` on both fields, so a
password manager fills it and the browser offers to save the password. It carries the page the
person was on in a hidden field, and shows the reason when the server sends it back. With a router,
gate on the same two calls.

To sign out, `SignInSession.signOut()`. That is the only call the client needs. Renewal is
automatic. So is going back to the form: when a connection that came in on a session cookie
reconnects anonymous, the page reloads, which brings up `Login` with the address kept.
`SignInSession.reloadWhenSignedOut(false)` turns that off for an application with its own screen.

`login.withUsernameLabel("Username")` changes the caption, and `login.setHiddenField(name, value)`
sends one more field, which the handler reads with `attempt.field(name)`.

`new Login(listener)` still hands the two values to your code. The form is a real form either way,
but a form that never really submits gets a save offer from fewer browsers.

## Ending a session

```java
SignInSessions.current().revokeUser(name, tenantId, "Access withdrawn.");   // every device
SignInSessions.current().revokeSession(sessionId, "Ended by an administrator.");   // one
```

Both close the live connections at once and stop the cookies working. `revokeUser` leaves sessions
started afterwards alone, so a person with a new password can sign in. Pass the tenant, or null in a
single-tenant application: one name in two tenants is two people.

**These records are in memory.** A restart forgets them. For anything that must stay ended, make
`resume` return null from your own records, as the example above does with `passwordChangedAt`, and
use `signedOut(session)` to record `session.id()` if a copied cookie must stay dead too.

To change a role at once rather than at the next reconnect, change it and call `revokeUser`. The
client reconnects, and `resume` returns the new roles.

## Application state on a connection

A provider or a handler can leave values on the connection it is opening, which is the only moment
before the first call:

```java
return new AuthenticatedPrincipal(user.email(), user.roles(), user.tenant())
        .withAttribute("caller", resolvedCaller);
```

```java
Caller caller = RmiRequestContext.getSessionAttribute("caller");
```

`RmiRequestContext.setSessionAttribute(name, value)` adds one during a call. Attributes stay on the
server and end with the connection, so set them again in `resume`. This works with any
`AuthenticationProvider`, not only with sign-in.

## With OpenID Connect and the development logins

The handshake decides in this order: the origin check, then an `AuthenticationProvider` or the
development logins, then the session cookie if the connection is still anonymous. So an explicit
credential beats the cookie, a provider that returns null lets the cookie work, and all of them can
be present together. A provider's connections have no time cap and are not closed by these
revocation calls; `EventPublisher.disconnect` closes those.

## The addresses

All three take POST and nothing else. Each deployment shape maps them for you: `SignInResource` under
Helidon, `SignInServlet` in a WAR, and the Vidocq binding's own routing. Every rule is in
`SignInSessions`, so all three answer alike.

| Address | Does | Answers |
|---|---|---|
| `zeroz4j-session/sign-in` | Takes the form: `username`, `password`, `return`, and anything else | 303 to the page with the cookie, or to the page with `?signin=failed`, `locked` or `error` |
| `zeroz4j-session/sign-out` | Ends the session, clears the session cookie and the browser id | 303 to the root |
| `zeroz4j-session/renew` | Issues a new cookie when the old one is old enough | 204, or 401 with the cookie cleared |

## What this protects

* **A stolen cookie is harder to get.** `HttpOnly` keeps it from page script, `Secure` off plain
  connections, and it never appears in a URL or a log.
* **A forged cookie is no cookie.** An HMAC-SHA256 over every field, checked in constant time before
  any field is read. Anything else is an anonymous visit.
* **Another site cannot use the cookie.** Every POST passes `CrossSiteGuard`: the origin check the
  handshake uses, then `Sec-Fetch-Site`. A handshake from another origin is closed before it is told
  anything. That also stops an attacker signing you in to their account.
* **A planted session is never promoted.** Every sign-in makes a new session and ends the one it
  replaced.
* **Guessing is slow.** Attempts are counted per name before the handler runs, so a parallel burst
  cannot get past the limit. An unknown name is counted the same way, so being stopped proves nothing.
* **A link cannot send somebody elsewhere.** `ReturnPage` accepts a path inside the application and
  nothing else: no other host in any spelling, no `..`, no line break, no framework address. Anything
  else lands on the root.
* **The next person on a shared computer starts clean.** Sign-out also clears the browser id, so what
  was kept under `Scope.CLIENT` and the record of what this browser was sent do not carry over.
* **A long-open tab is checked again.** Eight hours at most, and never past the cookie's own expiry.

`SameSite=Strict` works for a link opened from an email. The first page request carries no cookie,
which does not matter, and the page's own WebSocket handshake is same-site, so it does.

What it does not do is in [Limitations](../reference/limitations.md#cookie-sign-in).

## Verifying it

```bash
mvn -pl zerozstack-server-test -am test -Dtest=SignInSessionsTest -Dsurefire.failIfNoSpecifiedTests=false
```

`SignInSessionsTest` attacks the feature on a real `TestServer`. In your own tests,
`server.connectWithHeaders(Map.of("Cookie", ...))` runs the real handshake. See
[Testing](testing.md).

## See also

- [The `sign-in` example](https://github.com/ZeroZ4j/zerozstack/tree/main/zerozstack-examples/sign-in)
- [Authentication and authorization](security-auth.md) — the provider SPI, roles, tenants
- [Logging in with OpenID Connect](oidc-auth.md)
