# PWA install

What installing a zeroz4j application actually does — and, just as plainly, what it does not.

## Run it

```bash
mvn -pl zerozstack-examples/pwa-install/pwa-install-server -am install
java -jar zerozstack-examples/pwa-install/pwa-install-server/target/pwa-install-server-0.6.0.jar
```

Open <http://localhost:8083/>. No login: installability has nothing to do with authentication.

`http://localhost` counts as a secure origin, so installation and push both work as they stand. Any
other hostname needs HTTPS — browsers will not offer either over plain HTTP.

## The whole client-side opt-in

```java
public static void main(String[] args) {
    Pwa.install();                       // registers the service worker
    Zeroz4jClient.connect(webSocketUrl(), ...);
}
```

and three tags in `index.html`:

```html
<link rel="manifest" href="/manifest.webmanifest">
<meta name="theme-color" content="#1f2937">
<link rel="apple-touch-icon" href="/icons/icon-192.png">
```

That is it. The service worker ships with the framework and is served from
`zerozstack-server-core`, so no application copies it.

## Four things to try

**1. Install it.** The install button is hidden until the browser offers, which it does on its own
schedule — usually a second or two after load, sometimes only after a return visit. The button binds
to a signal rather than reading a boolean once:

```java
Effect.create(() -> install.setVisible(Pwa.installable().get()));
```

Once installed it opens in its own window with no browser chrome, and `Pwa.isInstalled()` is true.

**2. Change the identity.** Open <http://localhost:8083/?brand=sunset>. The manifest is built per
request by `PwaManifest`, so the installed name and color change:

```java
@GET @Path("/manifest.webmanifest")
@Produces("application/manifest+json")
public String manifest(@QueryParam("brand") String brand) {
    return PwaManifest.named(name, shortName)
            .themeColor(color)
            .icon("/icons/icon-192.png", 192)
            .icon("/icons/icon-512.png", 512)
            .toJson();
}
```

A single-identity application drops a static `manifest.webmanifest` into `src/main/webapp` instead —
the framework already serves it with the right content type. The builder is for the case a static
file cannot cover, which in a multi-tenant product is every case.

**3. Subscribe to push.** The server generates a VAPID key pair at startup and hands the public half
to the browser; the browser hands back an endpoint and two keys; the server stores them. It could
then post to that endpoint with this application closed.

Actually *delivering* a push is out of scope for the framework and for this example: it needs a
signed VAPID JWT and a payload encrypted per RFC 8291. Use a library
([web-push-java](https://github.com/web-push-libs/webpush-java) or similar) — the subscription this
example collects is exactly what it wants.

The key pair here is regenerated on every restart, which invalidates every subscription taken out
against the old one. A deployment generates one pair and configures both halves.

**4. Go offline.** Stop the server and reload. You get a page saying you are offline — not a broken
application and not a browser error page.

That last one is the important one, so it is worth being blunt about it:

> **Installing does not make a zeroz4j application work offline, and it is not meant to.** Every view
> loads its data over the WebSocket, signals get their retained values from the server on subscribe,
> and LiveSync objects live server-side. There is no client-side store, so with no connection there
> is nothing to render. Vaadin Flow shows an offline page for exactly the same reason. An application
> that must function without a network needs a different architecture, not a different service
> worker.

What the service worker *does* buy you is a fast second start — the shell is already cached — and a
readable page instead of a browser error when there is no connection. The cache name carries the
build version, so a deployment evicts the old shell rather than serving stale JavaScript against a
newer server.

## Files worth reading

| File | Why |
|---|---|
| `pwa-install-client/.../PwaInstallApp.java` | the entire client-side opt-in, plus the install and push buttons |
| `pwa-install-server/.../ManifestResource.java` | `PwaManifest` used for real |
| `pwa-install-server/.../PushServiceImpl.java` | VAPID key generation with no dependencies |
| `pwa-install-server/src/main/webapp/index.html` | the three tags |
