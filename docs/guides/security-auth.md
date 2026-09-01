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
model above never does. It has been removed — see the 0.6.0 changelog.

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

!!! warning "Changed in 0.6.0"
    Before 0.6.0 the frame did not carry the flag, and the client treated *any* AUTH frame as a
    successful sign-in. A connection the provider had declined arrived named `"anonymous"` with no
    roles, and `isAuthenticated()` still returned `true`. If you worked around that by checking for a
    role your provider only grants on success, that check is no longer needed.

Neither of these decides anything. They decide what the client shows; the server checks every call
again for itself.

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

For LiveSync writes, `@ClientWritable("editor")` gates the whole model the same way — and it gates
every model a client's change reaches, not only the outermost one. A model nested inside a
`@ClientWritable` model needs its own `@ClientWritable` before a client can edit it as part of the
outer one, and one refusal refuses the whole change. See
[LiveSync](../LIVESYNC.md#every-object-the-change-touches-is-checked-not-just-the-outer-one).

## What a client is allowed to read back

Every object the server sends a client travels with a name attached, called a **handle**, and the
client asks for an object again by naming it — after a dropped connection, most of all.

**A name is not a permission.** The server keeps a record of which objects it has actually sent to
which browser, and answers a request to re-read an object only when that record says the object was
sent there. Naming an object you were never given gets you nothing, and is not reported as an error:
it is treated exactly like naming an object the server no longer has.

Names travel further than the objects do. An object nested inside a broadcast event or a shared
signal goes out with its own name attached, so everybody who received the outer payload also learned
the names of the parts inside it. The record is what separates knowing a name from having been sent
the object, and only the second one counts.

Three consequences:

* **Reconnecting still works.** The record is kept per browser, and the browser id outlives the
  connection, so a client that drops and reconnects gets everything it holds back.
* **A non-browser client with no cookie re-fetches instead of re-syncing.** It is remembered only for
  the life of one connection. The server log says so once.
* **The record is bounded and can expire.** At most 10,000 objects per browser
  (`zeroz.disclosure.maxHandlesPerClient`), and a browser's record is dropped after 24 hours of
  inactivity (`zeroz.disclosure.idleHours`). An expired record behaves like a server restart: the
  client is told nothing was found and fetches the objects the way it first obtained them.

Ask the same question yourself with `Disclosures.wasDisclosedTo(session, handleId)` before doing
anything on a client's behalf with an object it named.

## What an error tells the caller

A failed call answers with a message, and most messages are not fit to send.

**Two kinds travel word for word.** One is a refusal your application wrote for the caller to read.
Throw `com.zeroz4j.server.ClientVisibleException` to say so:

```java
import com.zeroz4j.server.ClientVisibleException;

@Override
public void approve(String invoiceId) {
    Invoice invoice = invoices.byId(invoiceId);
    if (invoice.isApproved()) {
        throw new ClientVisibleException("That invoice was already approved.");
    }
    ...
}
```

The other is the framework's own refusals — authentication required, access denied, unknown service,
unknown method, an argument that failed validation. Those exist to be read, and clients already act
on them.

!!! note "A refusal can now be said in the caller's language"
    Give `ClientVisibleException` a message from a catalog instead of a sentence and the caller
    reads the refusal in their own language, while the server log keeps English:

    ```java
    throw new ClientVisibleException(AppText_Text.invoiceAlreadyApproved(invoiceId));
    ```

    The framework's own refusals work the same way, and a deployment that adds no language sees
    exactly the English it has always seen — character for character, including in tests that
    assert on the wording. What changes for a project that does add one: **a test asserting on the
    exact English of a framework refusal is asserting on a translation.** Assert on which refusal
    it was instead — `Refusals.assertRefusedWith(FrameworkKeys.ACCESS_DENIED, thrown)` — and on
    which language your own connection reads. See
    [Answering in the reader's language](language.md).

**Everything else becomes one sentence and a code.** The caller sees
`The server could not complete this request. Reference: 4f2a91cc`, and the real message and stack
trace go to the server log under the same code. A user quoting the code from their screen is enough
to find the log line.

An unplanned failure's message describes the machinery — class names, field names, query fragments,
container internals — which is useful in a log and useless on a screen. If your client used to show
the text of an application exception to the person using it, wrap that case in
`ClientVisibleException` and write the sentence you actually want them to read.

## Reading the identity in a service

```java
import com.zeroz4j.server.RmiRequestContext;

String user = RmiRequestContext.getPrincipal().getName();
Set<String> roles = RmiRequestContext.getRoles();
String tenant = RmiRequestContext.getTenantId();   // null when single-tenant
String sessionId = RmiRequestContext.getSessionId();
Locale language = RmiRequestContext.getLocale();   // never null
```

Never take the caller's identity from a method argument. A client can send anything; the context is
derived from the authenticated handshake. The caller's language is on the context for the same
reason and follows the same rule.

## Tenancy

A tenant reported by the provider becomes the session's tenant, which is what makes tenant-scoped
pushes possible:

```java
events.publish(MaintenanceEvents.WINDOW, window, Scope.TENANT, tenantId);
syncEngine.notifyChanged(config, Scope.TENANT, tenantId);
```

A session with no tenant — anonymous, or authenticated by a provider that reports none — **never**
matches a `Scope.TENANT` push. So a connection that did not sign in receives nothing sent that way.

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
* Delivered in an **`HttpOnly`** cookie, which means page script cannot read it — unlike anything
  kept in browser storage. `Secure` and `SameSite=Strict` are set too.

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

A browser sends your cookies with **any** connection to your address, whichever page opened it.
So the server decides for itself which pages it will accept a connection from, and refuses the rest.
The `Origin` header names the page; the rule below decides what counts.

| `zeroz.origins` | Behavior |
|---|---|
| unset (default) | Same-origin only: `Origin` must match the `Host` the request was sent to. Correct for the usual deployment. |
| a comma-separated list | Exactly those origins, e.g. `https://app.example.com,https://admin.example.com`. Needed when the page is served from a different host than the socket. |
| `*` | No check. Only when something in front of the application already enforces one. |

A handshake carrying no `Origin` at all is allowed: browsers always send one, so its absence means
the caller is not a browser and carries no cookies of its own.

A refused handshake is closed immediately with WebSocket close code 1008. The close reason names
which check refused it — the page it came from, or the host name it was addressed to — and nothing
else about the deployment. The full explanation, with the configured values, is in the server log.

## Naming the hosts you answer for

`zeroz.hosts` is the second rule, and it asks a different question: not *which page opened this
connection*, but *which name was it addressed to*.

| `zeroz.hosts` | Behavior |
|---|---|
| unset (default) | No host check: a handshake addressed to any name at all is accepted. |
| a comma-separated list | The `Host` header must be one of them, e.g. `app.example.com,app.example.com:8443`. An entry with no port accepts that name on any port. Case does not matter. |
| `*` | No host check, said out loud. |

```bash
java -Dzeroz.hosts=app.example.com -jar myapp-server.jar
```

**Why it is a separate setting.** Anyone who owns a domain name can point it at any address,
including yours, and a browser that reaches your server under that name will say so in both headers
at once. The default rule only asks whether the two headers agree, so they do. Listing the names you
actually serve is what makes the second question answerable.

The check runs on every handshake, including one that sends no `Origin` header at all.

Three things to know:

* `zeroz.origins=*` turns the page check off and leaves the host check running. They are separate.
* List every name the application is reached by, including the port when you pin one. A name you
  forget stops working, and the log line for the refusal says exactly what would have been accepted.
* **Serve the application over HTTPS.** A certificate names the host it is for, so a name pointed at
  your server has nothing valid to present and the browser stops there. `zeroz.hosts` is the second
  rule, for the plain-HTTP case.

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
  whose roles change must reconnect. Working them out again on every frame would put that lookup on
  the busiest path there is.
- **A record of what was sent is not a record of who may see it.** It is keyed by browser, so two
  people sharing a machine share it, and it says only that the server sent the object once — not that
  the reason for sending it still holds. Data that must follow a person needs `Scope.USER` or
  `Scope.TENANT`.
- **The record lives in memory.** A restart empties it, and clients re-fetch. In a cluster each node
  remembers only what it sent, so a client that lands on a different node after a reconnect re-fetches
  as well.
- **No session expiry.** A connection stays authenticated until it closes.
- **Client-side checks are cosmetic.** Hiding a menu item is not authorization; the server decides.
- **Nothing gates HTTP.** Every page and asset is public; only RMI calls are checked. An application
  that needs the documents themselves protected uses a container `<security-constraint>`.
- **The list of host names is off until you set it.** With `zeroz.hosts` unset, the server accepts a
  handshake addressed to any name at all, as long as the page's `Origin` says the same name.
- **Uploading a file inherits the connection's identity and nothing more.** The framework checks that
  the browser has a live connection, and applies one size limit. Who may upload, how often, and how
  much in total are the application's to decide — see
  [Accepting file uploads](file-uploads.md#what-the-application-must-check-itself).

## See also

- [Choosing how state moves](../decide/index.md) — which scope you give a push decides who receives it
- [Accepting file uploads](file-uploads.md) — how an upload borrows the connection's identity
- [Limitations](../reference/limitations.md)
