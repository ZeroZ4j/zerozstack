# PWA: Installing, Fast Startup and Push

A ZeroZ Stack application can be installed — a home-screen icon, its own window, no browser chrome —
and it can receive web push. One call and three tags get you there.

Read the next paragraph before you build anything on this.

> **Installing does not make the application work offline, and it is not meant to.** Every view loads
> its data over the WebSocket, signals get their retained values from the server on subscribe, and
> LiveSync objects live server-side. There is no client-side store, so with no connection there is
> nothing to render. Opened offline, the application shows a page saying so. Vaadin Flow does the
> same thing for the same reason. An application that must function without a network needs a
> different architecture, not a different service worker.

What you do get is worth having: a real installed app, a fast second start, and a readable page
instead of a browser error when there is no connection.

## The whole opt-in

In your client's `main`:

```java
public static void main(String[] args) {
    Pwa.install();                      // registers the framework's service worker
    Zeroz4jClient.connect(wsUrl, () -> RmiSecurityContext.onResolved(App::buildUi));
}
```

`Pwa.install()` goes **before** `connect`. It has nothing to do with the socket, and doing it first
means a reload during a server outage still finds the cached bundle.

In your `index.html`:

```html
<link rel="manifest" href="/manifest.webmanifest">
<meta name="theme-color" content="#1f2937">
<link rel="apple-touch-icon" href="/icons/icon-192.png">
```

And a manifest. That is the whole thing — the service worker ships inside `zerozstack-server-core`
and is served from `/zeroz4j-sw.js` without any application copying it.

Browsers offer installation and allow push only on a **secure origin**. `http://localhost` counts;
anything else needs HTTPS.

## The manifest

For an application with one identity, drop a static `manifest.webmanifest` into
`src/main/webapp` (or `META-INF/resources`). It is served with `application/manifest+json` already —
several browsers refuse a manifest served as `application/octet-stream`.

```json
{
  "name": "Acme Portal",
  "short_name": "Acme",
  "start_url": "/",
  "scope": "/",
  "display": "standalone",
  "theme_color": "#1f2937",
  "background_color": "#1f2937",
  "icons": [
    { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
  ]
}
```

In a multi-tenant product the manifest is not one document: the name, icons and color belong to the
tenant and are only known per request. Build it with `PwaManifest` and serve it from your own
endpoint:

```java
@GET
@Path("/manifest.webmanifest")
@Produces("application/manifest+json")
public String manifest() {
    Tenant tenant = tenants.current();
    return PwaManifest.named(tenant.displayName(), tenant.shortName())
            .description(tenant.tagline())
            .themeColor(tenant.brandColour())
            .icon("/icons/" + tenant.id() + "-192.png", 192)
            .icon("/icons/" + tenant.id() + "-512.png", 512)
            .toJson();
}
```

A literal path outranks the framework's static-content catch-all, so this wins with no configuration.

Supply at least 192px and 512px icons, and one marked `maskable` — without it Android puts your
square in a white circle instead of filling the shape it wants. A maskable icon keeps its important
content inside the middle 80%, because the edges get cropped.

## The install button

Browsers decide for themselves when to offer installation, and they fire `beforeinstallprompt` on
their own schedule — usually a second or two after load, sometimes only on a return visit, never when
the app is already installed. So bind a button's visibility to a signal rather than reading a boolean
once:

```java
Button install = new Button("Install");
Effect.create(() -> install.setVisible(Pwa.installable().get()));

install.addClickListener(e -> Pwa.promptInstall(outcome -> {
    // "accepted", "dismissed", or "unavailable" when there was no offer to show
}));
```

`promptInstall` must be called from a click handler — browsers refuse a prompt that did not come from
a user gesture. The offer is single-use: after a prompt, `installable()` is false until the browser
offers again.

`Pwa.isInstalled()` tells you whether the current page is running as an installed app rather than in
a tab.

## Web push

Push works whether or not the application is installed, and delivers with the app closed.

```java
String key = pushService.vapidPublicKey();          // your server's VAPID public key

Pwa.subscribeToPush(key, (endpoint, p256dh, auth, error) -> {
    if (error != null) {
        return;                                     // the user refused, or the browser declined
    }
    new Thread(() -> pushService.register(endpoint, p256dh, auth)).start();
});
```

The callback arrives from the browser on a native stack, so an RMI call inside it needs a green
thread — the same rule as any click handler.

Subscribing prompts for notification permission if it has not been granted. Ask at a moment the user
understands why; a prompt on page load is usually refused, and a refusal is sticky.

**Delivery is not part of this framework.** Posting to that endpoint needs a VAPID JWT signed with
your private key and a payload encrypted per RFC 8291 — use a library such as
[webpush-java](https://github.com/web-push-libs/webpush-java). The subscription collected above is
exactly what those libraries take. Delete a subscription when the push service answers 404 or 410;
that is how it tells you the subscription is gone for good.

What a notification looks like is decided by the shipped service worker, from the JSON you push:

```json
{ "title": "Build finished", "body": "main is green", "url": "/builds/42", "tag": "build" }
```

Clicking it focuses an already-open window rather than opening a second copy of the app.

## What the service worker caches

| Request | Strategy | Why |
|---|---|---|
| Navigations (`/`, `/projects/42`) | network first, offline page on failure | the server owns routing, and a cached shell with no socket would just sit there reconnecting |
| `/js/classes.js` and other same-origin assets | cache first | the client bundle is the large one, and this is what makes a second launch instant |
| `/wasm-rmi` | never intercepted | the socket is not the worker's business |
| Anything cross-origin | never intercepted | a CDN, a font, an identity provider |
| Any non-GET | never intercepted | a write must never be answered from a cache |

The cache name carries the build version — `zeroz4j-shell-0.9.0` — so a deployment evicts
the previous shell instead of serving stale JavaScript against a newer server. This is the trap that
makes hand-rolled service workers a support burden, and it is handled.

## The offline page

The framework ships `/zeroz4j-offline.html`: one self-contained document with no stylesheet, font,
script or image from anywhere else. A page whose job is to appear when the network is gone cannot
depend on the network.

It distinguishes the two failures that land on it — no network at all, versus a server that did not
answer — and reloads by itself when the connection comes back.

To replace it, put your own `zeroz4j-offline.html` in your application's `META-INF/resources`. Yours
is found first.

Note this is only about **opening** the application with no connection. A connection that drops while
the app is running is unrelated and already handled: `Zeroz4jClient` shows a reconnecting banner and
recovers by itself.

## Under a context path

`Pwa.install()` registers the worker at **`AppBase.url("zeroz4j-sw.js")`** — inside the application,
not at the site root. That matters twice over. A worker registered at `/zeroz4j-sw.js` from an
application deployed at `/coachapp` is a 404, and a failed registration means no install, no offline
page and no push, reported as one line in the browser console. And a worker's scope can never be
wider than the directory it is served from, so even a copy placed at the site root would control the
wrong tree.

Everything inside the worker is already scope-relative: `self.registration.scope` is what it caches,
what it treats as the offline page, and where a clicked notification with no `url` sends the browser.

The manifest follows the same rule — reference it relatively and give it a relative `start_url` and
`scope`:

```html
<link rel="manifest" href="manifest.webmanifest">
```

```json
{ "start_url": ".", "scope": "." }
```

## Your own service worker

```java
Pwa.install(AppBase.url("my-sw.js"));
```

Before you do: the framework's worker caches only the shell, and anything more ambitious runs into
the fact that there is no client-side data to cache. If you write your own, keep the version-stamped
cache name and keep `/wasm-rmi` out of the fetch handler.

## Try it

The `pwa-install` example wires all of this end to end — a per-request manifest built with
`PwaManifest`, an install button bound to `installable()`, VAPID key generation with no dependencies,
and the offline page.

```bash
mvn -pl zerozstack-examples/pwa-install/pwa-install-server -am install
java -jar zerozstack-examples/pwa-install/pwa-install-server/target/pwa-install-server-0.9.0.jar
```

Then open <http://localhost:8083/>, install it, and stop the server and reload.
