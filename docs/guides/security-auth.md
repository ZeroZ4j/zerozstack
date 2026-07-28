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

## Development authentication

With no provider registered and `zeroz.security.mode=dev` set, `DevAuth` accepts two hardcoded users:

| Username | Password | Roles |
|---|---|---|
| `demo` | `demo` | `user` |
| `admin` | `admin` | `user`, `admin` |

Credentials arrive as `user` and `password` query parameters on the handshake. Four of the seven
examples enable this. **It has no place in a deployment** — register a provider and the fallback is
gone.

## Limits

- **Identity is fixed for the life of the connection.** Roles are read once at handshake, so a user
  whose roles change must reconnect. Re-evaluating per frame would put a security check on the hot
  path.
- **No handshake origin check.** ZeroZ Stack does not verify the `Origin` header; put that in front of the
  application, or in your provider.
- **No session expiry.** A connection stays authenticated until it closes.
- **Client-side checks are cosmetic.** Hiding a menu item is not authorization; the server decides.

## See also

- [Choosing how state moves](../decide/index.md) — scoping a push is a security decision
- [Limitations](../reference/limitations.md)
