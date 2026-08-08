# Logging in with OpenID Connect (Keycloak)

How a browser gets an identity from an OpenID Connect provider, and how the server checks it.

## When to use this

When your application has real users. If it has none, you do not need this at all — see
[client identity without a login](security-auth.md#client-identity-without-a-login), which gives an
open application per-browser state with no provider involved.

## The shape of it

```
browser ──▶ Keycloak login ──▶ access token ──▶ WebSocket handshake ──▶ AuthenticatedPrincipal
        authorization code + PKCE                OidcAuthenticationProvider verifies it
```

Two halves, and you need both:

* **In the browser**, `OidcClient` runs the authorization-code flow with PKCE and holds the token.
* **On the server**, `OidcAuthenticationProvider` verifies that token at the handshake and maps its
  claims to a name, roles and a tenant.

## Server side

Add the module:

```xml
<dependency>
    <groupId>com.zeroz4j</groupId>
    <artifactId>zerozstack-auth-oidc</artifactId>
</dependency>
```

Register the provider in
`src/main/resources/META-INF/services/com.zeroz4j.server.AuthenticationProvider`:

```
com.zeroz4j.server.oidc.OidcAuthenticationProvider
```

Registering any provider disables the `demo`/`admin` development fallback entirely.

Configure it:

```
-Dzeroz.oidc.issuer=https://keycloak.example.com/realms/acme
-Dzeroz.oidc.clientId=zeroz-app
```

| Property | Meaning |
|---|---|
| `zeroz.oidc.issuer` | **Required.** The realm URL exactly as it appears in your tokens' `iss` claim. |
| `zeroz.oidc.jwksUri` | Where signing keys are published. Defaults to Keycloak's `<issuer>/protocol/openid-connect/certs`. |
| `zeroz.oidc.clientId` | This application's client id. Used to read client roles, and as the expected audience. |
| `zeroz.oidc.audience` | The `aud` a token must carry; defaults to `clientId`. `*` accepts any — only when something else constrains who the token was minted for. |
| `zeroz.oidc.principalClaim` | Which claim becomes the user name; default `preferred_username`, falling back to `sub`. |
| `zeroz.oidc.rolesClaim` | A flat roles claim, for providers that publish them that way. Unset reads Keycloak's structure. |
| `zeroz.oidc.tenantClaim` | Which claim carries the tenant. Unset means single-tenant. |
| `zeroz.oidc.tenantFromRealm` | `true` to use the realm name from the issuer URL as the tenant. |
| `zeroz.oidc.clockSkewSeconds` | Tolerance for `exp`/`nbf`; default 60. |

### Roles

Keycloak does not publish a flat roles claim. It splits them, and both are read and merged:

* `realm_access.roles` — realm roles
* `resource_access.<clientId>.roles` — roles for this client only

Roles granted for a *different* client are ignored. These become the roles that `@RolesAllowed`,
`@ClientWritable` and `@RequiresRole` check.

### Tenants

Two deployment shapes, both supported:

* **One realm, a claim per user** — set `zeroz.oidc.tenantClaim=tenant` and add that claim to the
  token in Keycloak. Simplest to operate.
* **A realm per customer** — set `zeroz.oidc.tenantFromRealm=true` and the tenant is the realm name
  from the issuer URL. Stronger isolation; realm provisioning becomes part of onboarding.

Neither set means single-tenant: the principal reports no tenant, so no `Scope.TENANT` push can reach
it.

## Browser side

Register the application in Keycloak as a **public client** with **Standard flow** enabled and
**PKCE method S256** required, and add your application's URL as a valid redirect URI and web origin.

```java
public static void main(String[] args) {
    OidcClient.start(
        new OidcClient.Config("https://keycloak.example.com/realms/acme", "zeroz-app"),
        () -> {
            Zeroz4jClient.connect(OidcClient.appendToken(wsUrl), () -> Router.start("app-root"));
        });
}
```

`start` works out for itself whether this page load is a fresh visit, a return from Keycloak, or an
already-authenticated reload. Only the last two reach the callback; the first navigates away and the
callback runs on the way back instead.

`appendToken` puts the token on the WebSocket URL **and** installs a provider so every reconnect
picks up whichever token is current by then.

Logging out clears the local session and ends the provider's:

```java
OidcClient.logout();
```

## Expiry and reconnection

Identity on a zeroz4j connection is fixed when the socket opens, so a token expiring later does not
interrupt anything in flight. It matters on **reconnect**, which uses whatever token is current then.
`OidcClient` therefore refreshes silently ahead of expiry rather than on demand — the reconnect path
needs a token synchronously and has no point at which it could wait for a round trip. A refresh that
fails sends the user back to log in.

## What this protects, and what it does not

* **PKCE** means an intercepted authorization code is worth nothing without the verifier that only
  this browser holds. The `plain` method is never used; if the browser cannot compute a SHA-256
  challenge — Web Crypto needs a secure context — the login refuses rather than downgrading.
* **The `state` parameter** is generated per attempt and checked on return, so a code delivered by a
  page the user did not start the login from is discarded.
* **The authorization code is stripped from the address bar** as soon as it is used, keeping it out
  of history and bookmarks.
* **The access token lives in `sessionStorage`, which page script can read.** That is unavoidable for
  a browser client — the token has to be sent from script. `sessionStorage` rather than
  `localStorage` means it dies with the tab. It is also why the client-id cookie is kept separate and
  `HttpOnly` rather than folded in here.
* **The token travels as a query parameter** on the WebSocket URL, because a browser cannot set
  headers on an upgrade. Do not log full request URLs on the server or in a proxy in front of it.
* **A rejected token is refused, never partially trusted.** Signature, issuer, audience and expiry
  are all checked; failure leaves the connection anonymous and every `@Secured` call on it fails.

## Verifying it

The module's tests mint tokens locally and cover the refusals that matter — expired, wrong issuer,
wrong audience, wrong signing key, tampered payload, and an unsigned `alg: none` token. Run them with:

```bash
mvn -pl zerozstack-auth-oidc test
```

For an end-to-end check against a real provider, start Keycloak, create the realm and public client
above, and point both the server properties and `OidcClient.Config` at it.

## See also

- [Authentication and authorization](security-auth.md) — the provider SPI, client identity, origin checks
- [Signals](../SIGNALS.md#scoped-signals-one-value-per-tenant-user-or-browser) — what a tenant is for
