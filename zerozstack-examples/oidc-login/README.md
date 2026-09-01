# OIDC login (Keycloak)

A real login: the browser runs an authorization-code flow with PKCE against Keycloak, the server
verifies the resulting token, and three RMI calls show what that identity is worth — one open, one
requiring authentication, one requiring a role.

## 1. Start a Keycloak

Any Keycloak will do. A throwaway one:

```bash
docker run -d --name zeroz-kc -p 18081:8080 \
  -e KEYCLOAK_DATABASE_VENDOR=dev-file \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  bitnamilegacy/keycloak:26.3.2
```

## 2. Create the realm, client, role and user

```bash
KC=http://localhost:18081
TOKEN=$(curl -s -d client_id=admin-cli -d username=admin -d password=admin \
  -d grant_type=password $KC/realms/master/protocol/openid-connect/token \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

# realm
curl -s -X POST $KC/admin/realms -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"realm":"zeroz-tour","enabled":true}'

# public client with PKCE, redirecting back to this example
curl -s -X POST $KC/admin/realms/zeroz-tour/clients -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "clientId":"zeroz-app","publicClient":true,"standardFlowEnabled":true,
    "redirectUris":["http://localhost:8081/*"],"webOrigins":["http://localhost:8081"],
    "attributes":{"pkce.code.challenge.method":"S256"}}'

# a realm role the example checks for
curl -s -X POST $KC/admin/realms/zeroz-tour/roles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"planner"}'

# a user (assign the planner role and set a tenant attribute in the admin console)
curl -s -X POST $KC/admin/realms/zeroz-tour/users -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "username":"ada","enabled":true,"emailVerified":true,"email":"ada@example.com",
    "requiredActions":[],"attributes":{"tenant":["acme"]},
    "credentials":[{"type":"password","value":"ada","temporary":false}]}'
```

Two Keycloak details worth knowing, both of which cost time if you meet them cold:

- **Keycloak 26 discards "unmanaged" user attributes by default**, so a `tenant` attribute silently
  disappears. Enable them under *Realm settings → User profile → Unmanaged attributes*, then add a
  mapper (*Clients → zeroz-app → Client scopes → dedicated → Add mapper → User Attribute*) putting
  `tenant` into the access token.
- **A stock access token carries `aud: "account"`**, not your client id — it names the client in
  `azp`. This is why `zeroz.oidc.audience` is unset by default; see below.

## 3. Run it

```bash
mvn -pl zerozstack-examples/oidc-login/oidc-login-server -am install
java -jar zerozstack-examples/oidc-login/oidc-login-server/target/oidc-login-server-0.9.0-SNAPSHOT.jar
```

Open <http://localhost:8081/> and sign in as `ada` / `ada`. You are redirected to Keycloak and back.

## What to look at

**The redirect URL**, before you sign in — `code_challenge_method=S256`, a 43-character
`code_challenge`, and a `state`. That is real PKCE: an intercepted authorization code is worthless
without the verifier only this browser holds.

**The address bar afterwards** — the authorization code is gone. It is single-use and has no business
in history or bookmarks.

**The three buttons:**

| Button | Annotation | Result |
|---|---|---|
| Public call | none | Works for anyone, including a connection that never logged in |
| `@Secured` call | `@Secured` | Returns name, roles and tenant, as the *server* sees them |
| `@RolesAllowed("planner")` | `@RolesAllowed` | Works only because Keycloak granted that realm role |

Keycloak splits roles across `realm_access.roles` and `resource_access.<client>.roles`; both are read
and merged, and another client's roles are ignored.

## Configuration

Set on the command line, or in `OidcLoginServer` for this example:

```
-Dzeroz.oidc.issuer=http://localhost:18081/realms/zeroz-tour
-Dzeroz.oidc.clientId=zeroz-app
-Dzeroz.oidc.tenantClaim=tenant
```

`zeroz.oidc.audience` is deliberately **unset**. With nothing set, a token is accepted when `aud`
contains the client id *or* `azp` equals it — which is what a stock Keycloak token looks like. Set it
explicitly once your realm has an audience mapper. Full list in
[the OIDC guide](../../docs/guides/oidc-auth.md).

## What this does not protect

The access token lives in `sessionStorage`, which page script can read. That is unavoidable for a
browser client — the token has to be sent from script — and it is exactly why the framework's own
client-id cookie is kept separate and `HttpOnly`. The token also travels as a query parameter on the
WebSocket URL, because a browser cannot set headers on an upgrade, so do not log full request URLs.
