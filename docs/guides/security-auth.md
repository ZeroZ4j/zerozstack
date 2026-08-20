# Authentication and authorization

How a connection acquires an identity, and how that identity gates what it may do.

## When to use this

Read this before deploying anything. ZeroZ Stack ships with a development authentication provider whose
credentials are hardcoded; replacing it is not optional.

## The shape of it

Authentication happens **once, at the WebSocket handshake**. The resulting identity — user name, roles,
and tenant — is attached to the session and used for every subsequent decision on that connection.

```
handshake ──▶ AuthenticationProvider ──▶ AuthenticatedPrincipal ──▶ session identity
                                          name / roles / tenant
```

Everything downstream reads from that: `@Secured`, `@RolesAllowed`, `@ClientWritable`,
`Scope.USER` and `Scope.TENANT` pushes, and `RmiRequestContext`.

**The framework puts no gate in front of HTTP.** Pages, the client bundle and any other static
resource are served to anyone who asks, signed in or not — which is what has to happen, since the
page is what opens the socket that decides identity in the first place. An anonymous visitor loads
the application and sees whatever it shows a signed-out visitor; a sign-in screen is a view like any
other. If you want HTTP itself gated — an intranet application behind SSO, say — that is your
container's job through a `<security-constraint>`, not the framework's.

Up to and including 0.5.0 a servlet filter in `zerozstack-server-core` contradicted this: deployed in
a WAR it answered 401 to every page unless the *container* had authenticated the request, which the
model above never does. It has been removed — see the 0.6.1 changelog.

## Replacing the development provider

Implement `AuthenticationProvider` and register it through `ServiceLoader`. It is discovered that way
rather than through CDI because the handshake runs before the endpoint exists.

```java
package com.example.auth;

import com.zeroz4j.server.AuthenticatedPrincipal;
import com.zeroz4j.server.AuthenticationProvider;
import com.zeroz4j.server.HandshakeCredentials;

public final class JwtAuthProvider implements AuthenticationProvider {

    @Override
    public AuthenticatedPrincipal authenticate(HandshakeCredentials credentials) {
        String token = credentials.parameter("token");
        if (token == null) {
            return null;                       // no credentials: stay anonymous
        }
        Claims claims = verifyOrThrow(token);  // your own verification
        return new AuthenticatedPrincipal(claims.subject(), claims.roles(), claims.tenant());
    }
}
```

Register it in
`src/main/resources/META-INF/services/com.zeroz4j.server.AuthenticationProvider`:

```
com.example.auth.JwtAuthProvider
```

That is the whole integration. Registering a provider disables the development fallback entirely.

!!! warning "Exactly one provider"
    Two registered providers is a startup error rather than an arbitrary choice, because picking one
    decides who can log in.

### What the provider receives

`HandshakeCredentials` is a read-only view of the handshake, so a provider can be unit-tested without
a container:

| Method | Use |
|---|---|
| `parameter(name)` | A query parameter from the WebSocket URL — where a token or credentials usually arrive |
| `header(name)` | A handshake request header |
| `containerPrincipal()` | The principal the container already authenticated, if the deployment sits behind container-managed security |

A provider behind container-managed security typically **enriches** rather than replaces:

```java
if (credentials.containerPrincipal() == null) {
    return null;
}
String name = credentials.containerPrincipal().getName();
return new AuthenticatedPrincipal(name, lookUpRoles(name), lookUpTenant(name));
```

### Return values

| Return | Meaning |
|---|---|
| An `AuthenticatedPrincipal` | Authenticated; roles and tenant are attached to the session |
| `null` | Declined. The connection proceeds **anonymously**, and every `@Secured` call on it fails |
| Throw | Refused. Logged, and the connection proceeds anonymously |

A failed authentication does not fail the upgrade. A rejected WebSocket handshake gives the client no
way to report *why*, so the connection is allowed and then denied at every secured call — which the
client can surface.

### What the client is told

The server reports its decision in an AUTH frame on every connection, refused ones included, and the
frame carries that decision as an explicit flag rather than leaving it to be inferred:

```java
RmiSecurityContext.isAuthenticated()   // true only when an identity was accepted
RmiSecurityContext.isResolved()        // whether the server has answered yet
RmiSecurityContext.onResolved(() -> mountUi());               // ready: fires either way
RmiSecurityContext.onAuthenticated(() -> mountProtectedView()); // identity: real sign-in only
RmiSecurityContext.onAuthenticationFailed(() -> showLoginError());
```

Three callbacks, and picking the wrong one is the mistake to avoid:

| Callback | Fires when | Use it for |
|---|---|---|
| `onResolved` | the server has answered, authenticated **or** anonymous | "the connection is usable" — mounting the UI |
| `onAuthenticated` | an identity was accepted | gating a protected view |
| `onAuthenticationFailed` | the provider declined | showing a sign-in error |

**`onAuthenticated` is not a "connected" signal.** It is a statement about identity, and an
application with no login is anonymous by design, so it never fires — mount from it and the page stays
blank. That is what `onResolved` is for.

Conversely, gate a login screen on `onAuthenticated`: it fires only on a real sign-in, so no
additional role check is needed to tell one from a refusal, and `onAuthenticationFailed` gives the
positive signal a form needs, since silence cannot be distinguished from a slow network.

!!! warning "Suspending calls in these callbacks"
    They run on a stack that began in native JavaScript, where TeaVM cannot suspend a coroutine — so
    a view whose construction makes an RMI call must be built on a green thread:
    `onResolved(() -> new Thread(this::mountUi).start())`. Otherwise it fails with
    *"suspension point reached from non-threading context"*.

!!! warning "Fixed in 0.6.1"
    Before 0.6.1 the frame did not carry the flag, and the client marked *any* AUTH frame as
    authenticated. A connection the provider had declined arrived named `"anonymous"` with no roles
    and `isAuthenticated()` returned `true`, so a gate built on `onAuthenticated` let every credential
    through. If you worked around this by checking for a role your provider only grants on success,
    that check is no longer needed.

Neither of these is a security boundary. They decide what the client shows; the server re-checks every
call.

## Authorizing calls

Put security annotations on the **`@RmiService` interface**, not the implementation. The dispatcher
scans the interface only; an annotation on the bean is silently ignored and the method is left open.

```java
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.Secured;

@RmiService
public interface InvoiceService {

    @Secured                              // any authenticated user
    List<Invoice> myInvoices();

    @RolesAllowed("approver")             // implies @Secured
    void approve(String invoiceId);
}
```

These are `com.zeroz4j.api.Secured` and `com.zeroz4j.api.RolesAllowed` — **not** the Jakarta
annotations of the same name. Method-level roles override interface-level roles.

For LiveSync writes, `@ClientWritable("editor")` gates the whole model the same way.

## Reading the identity in a service

```java
import com.zeroz4j.server.RmiRequestContext;

String user = RmiRequestContext.getPrincipal().getName();
Set<String> roles = RmiRequestContext.getRoles();
String tenant = RmiRequestContext.getTenantId();   // null when single-tenant
String sessionId = RmiRequestContext.getSessionId();
```

Never take the caller's identity from a method argument. A client can send anything; the context is
derived from the authenticated handshake.

## Tenancy

A tenant reported by the provider becomes the session's tenant, which is what makes tenant-scoped
pushes possible:

```java
events.publish(MaintenanceEvents.WINDOW, window, Scope.TENANT, tenantId);
syncEngine.notifyChanged(config, Scope.TENANT, tenantId);
```

A session with no tenant — anonymous, or authenticated by a provider that reports none — **never**
matches a `Scope.TENANT` push, so tenant data cannot leak to an unauthenticated connection by default.

Tenancy at the storage layer is separate: see `TenantResolver` and the EclipseStore
`TenantStorageProvider`.

## Client identity without a login

Not every application has users. An open application still needs to keep one browser's state to
itself — that is what `Scope.CLIENT` and `Signals.scoped(..., Scope.CLIENT)` filter on — and the id
they filter on has to come from somewhere.

**It cannot come from the browser.** Anything a client says about its own identity is a claim it can
edit, so the server issues it instead:

* 256 bits from a secure random source, minted server-side when the page is served, and again at the
  handshake if the browser presents none.
* Signed with an HMAC, so tampering is detectable and verification needs no server-side registry —
  which is what lets it survive a restart and work across a cluster.
* Delivered in an **`HttpOnly`** cookie. This is the part that matters: page script cannot read it,
  so a cross-site scripting bug cannot steal it the way it could read browser storage. `Secure` and
  `SameSite=Strict` are set too.

Read it in a service with `RmiRequestContext.getClientId()`.

| Property | Meaning |
|---|---|
| `zeroz.clientId.secret` | HMAC key. **Set this in production** — without it a key is generated at startup, so a restart invalidates every id and other nodes reject them. |
| `zeroz.clientId.ttlDays` | How long an id stays valid; default 365. |
| `zeroz.clientId.secureCookie` | Forces the `Secure` attribute on or off. Set it to `true` behind a TLS-terminating proxy, where the application only ever sees plain HTTP. |

!!! warning "A browser, not a person"
    Two people sharing a machine share the id, and clearing cookies mints a new one. It is safe for
    keeping a browser's own state to itself and **unsafe** for keeping one person's data away from
    another. That needs `Scope.USER` or `Scope.TENANT`, which need real authentication.

## Origin checks

A browser attaches cookies to **any** connection to your origin, including one opened by a page the
user happens to be visiting.
Since the handshake now carries an identity cookie, an unchecked `Origin` would hand that page the
victim's client id — so the handshake is refused unless the page that opened it is trusted.

| `zeroz.origins` | Behaviour |
|---|---|
| unset (default) | Same-origin only: `Origin` must match the `Host` the request was sent to. Correct for the usual deployment. |
| a comma-separated list | Exactly those origins, e.g. `https://app.example.com,https://admin.example.com`. Needed when the page is served from a different host than the socket. |
| `*` | No check. Only when something in front of the application already enforces one. |

A handshake carrying no `Origin` at all is allowed: browsers always send one, so its absence means a
non-browser client, which has no ambient cookies to abuse.

A refused handshake is closed immediately with WebSocket close code 1008.

## Naming the hosts you answer for

The origin check on its own compares two headers that the same attacker controls together.
Set `zeroz.hosts` to close that gap.

| `zeroz.hosts` | Behaviour |
|---|---|
| unset (default) | No host check, exactly as before this setting existed. |
| a comma-separated list | The `Host` header must be one of them, e.g. `app.example.com,app.example.com:8443`. An entry with no port accepts that name on any port. Case does not matter. |
| `*` | No host check, said out loud. |

```bash
java -Dzeroz.hosts=app.example.com -jar myapp-server.jar
```

**What goes wrong without it.** An attacker puts up a page at `evil.com`.
They also make the name `evil.com` point at your server's address, which anyone who owns a domain
name can do.
A visitor's browser then opens a socket to your server and sends `Origin: http://evil.com` and
`Host: evil.com`.
Those two match, so the same-origin rule lets the connection through, and the attacker's page is
talking to your application as the visitor's browser.
This is called **DNS rebinding**.

Listing `app.example.com` stops it, because `evil.com` is not a name your deployment answers for.
The check runs on every handshake, including one that sends no `Origin` header at all — the question
it asks is which name the request was addressed to, not which page sent it.

Two things to know:

* `zeroz.origins=*` turns the origin check off and leaves the host check running. They are separate.
* List every name the application is reached by, including the port when you pin one. A name you
  forget stops working, and the log line for the refusal says exactly what would have been accepted.

**Serve the application over HTTPS.** A rebound name has to present a certificate for itself, and it
cannot get one for yours — so TLS is what stops the browser accepting the rebinding in the first
place. The host allowlist is the second line, for the plain-HTTP case and for anything TLS misses.

## Development authentication

`DevAuth` gives you two accounts without an identity provider, for work on your own machine.
It is off unless the system property `zeroz.security.mode` is `dev`, and nothing sets that for you.

```bash
java -Dzeroz.security.mode=dev -jar myapp-server.jar
```

The examples take `--dev-login` on the command line instead, which sets the same property.

| Username | Password | Roles |
|---|---|---|
| `demo` | `demo` | `user` |
| `admin` | `admin` | `user`, `admin` |

Credentials arrive as `user` and `password` query parameters on the handshake.
A server that has this on prints a warning at startup and again on the first sign-in, naming the two
accounts, so nobody has to guess whether it is on.

**It has no place in a deployment.** The passwords are in the source code, and a password in a URL
ends up in browser history, in proxy access logs and in `Referer` headers.
The framework itself writes no log line containing a handshake password, but everything between the
browser and the server sees the URL.
Register an `AuthenticationProvider` and the fallback is gone.

## Static files

The server serves whatever sits under `META-INF/resources/`, plus the application shell for any path
the client router owns.

A request path is refused outright — the same 404 a missing file gets — when it contains:

* a `..` step, in any spelling, including one that was percent-encoded twice;
* a backslash;
* a null byte or any other control character;
* a first segment of `WEB-INF` or `META-INF`.

Both the JAX-RS binding and the servlet binding apply this before anything is looked up, so the two
cannot disagree.
Paths reach the framework already percent-decoded, because both the JAX-RS runtime and the servlet
container decode them first; the framework never decodes again, which is what keeps a file name with
a literal `%` in it from turning into something else.

## Limits

- **Identity is fixed for the life of the connection.** Roles are read once at handshake, so a user
  whose roles change must reconnect. Re-evaluating per frame would put a security check on the hot
  path.
- **No session expiry.** A connection stays authenticated until it closes.
- **Client-side checks are cosmetic.** Hiding a menu item is not authorization; the server decides.
- **Nothing gates HTTP.** Every page and asset is public; only RMI calls are checked. An application
  that needs the documents themselves protected uses a container `<security-constraint>`.
- **The host allowlist is off until you set it.** With `zeroz.hosts` unset, a name somebody has
  pointed at your server is accepted, as long as the page's `Origin` says the same name.

## See also

- [Choosing how state moves](../decide/index.md) — scoping a push is a security decision
- [Limitations](../reference/limitations.md)
