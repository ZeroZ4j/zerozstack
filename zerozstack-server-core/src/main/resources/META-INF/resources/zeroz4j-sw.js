/*
 * zeroz4j service worker.
 *
 * WHAT THIS IS FOR, AND WHAT IT DELIBERATELY IS NOT:
 *
 * A zeroz4j client is a shell. Every view loads its data over the WebSocket, signals get their
 * retained values from the server on subscribe, and LiveSync objects live server-side. There is no
 * client-side store, so with no connection there is nothing to render. This worker therefore does
 * NOT try to make the application work offline — it caches the shell so startup is fast and survives
 * a flaky connection, and serves a plain "you are offline" page when the app is opened with no
 * network at all.
 *
 * If you are looking for offline data, this framework is the wrong shape for it, and no service
 * worker will change that.
 */
const VERSION = '${project.version}';
const CACHE = 'zeroz4j-shell-' + VERSION;

/*
 * Everything is resolved against the registration scope rather than '/', because a WAR is commonly
 * deployed under a context path and the shell then lives at /myapp/ rather than at the site root.
 * self.registration.scope is an absolute URL and always ends in a slash.
 */
const BASE = self.registration.scope;
const BASE_PATH = new URL(BASE).pathname;
const OFFLINE_PAGE = BASE + 'zeroz4j-offline.html';

/*
 * What is worth precaching.
 *
 * The client bundle, because it is by far the largest asset and caching it is what makes a second
 * launch instant. And the offline page, because it has to be there precisely when nothing can be
 * fetched.
 *
 * Not index.html: it is served network-first (see below), so a cached copy would never be read.
 */
const SHELL = [BASE + 'js/classes.js', OFFLINE_PAGE];

self.addEventListener('install', function (event) {
    // addAll fails the whole install if any entry 404s, which would leave the old worker in place
    // rather than installing a half-populated cache. Each is added individually so a missing
    // optional asset does not block the update.
    event.waitUntil(
        caches.open(CACHE).then(function (cache) {
            return Promise.all(SHELL.map(function (url) {
                return cache.add(url).catch(function () {
                    console.warn('[zeroz4j-sw] Could not precache ' + url);
                });
            }));
        }).then(function () {
            return self.skipWaiting();
        })
    );
});

self.addEventListener('activate', function (event) {
    // The cache name carries the build version, so a new deployment evicts the previous shell
    // instead of serving a stale bundle against a newer server.
    event.waitUntil(
        caches.keys().then(function (names) {
            return Promise.all(names.map(function (name) {
                if (name !== CACHE && name.indexOf('zeroz4j-shell-') === 0) {
                    return caches.delete(name);
                }
                return null;
            }));
        }).then(function () {
            return self.clients.claim();
        })
    );
});

self.addEventListener('fetch', function (event) {
    const request = event.request;

    if (request.method !== 'GET') {
        return;                                  // never interfere with a write
    }
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) {
        return;                                  // third-party styling, fonts, an identity provider
    }
    if (url.pathname === BASE_PATH + 'wasm-rmi') {
        return;                                  // the socket is not ours to touch
    }

    if (request.mode === 'navigate') {
        // Network first for pages, and the offline page when that fails.
        //
        // Serving a cached index.html instead would be worse, not better: the shell would load, find
        // no socket, and sit there reconnecting forever. It cannot show anything either way, so it
        // should say so. This is the same choice Vaadin Flow makes.
        event.respondWith(
            fetch(request).catch(function () {
                return caches.match(OFFLINE_PAGE).then(function (page) {
                    return page || new Response(
                        '<h1>You are offline</h1><p>This application needs a connection.</p>',
                        { status: 503, headers: { 'Content-Type': 'text/html' } });
                });
            })
        );
        return;
    }

    // Assets: cache first. The cache name is version-stamped, so "first" is never stale across a
    // deployment, and this is what makes a second launch instant.
    event.respondWith(
        caches.match(request).then(function (cached) {
            return cached || fetch(request).then(function (response) {
                if (response && response.status === 200 && response.type === 'basic') {
                    const copy = response.clone();
                    caches.open(CACHE).then(function (cache) { cache.put(request, copy); });
                }
                return response;
            });
        })
    );
});

/* Web push. Delivery is the server's job; this decides what the notification looks like. */
self.addEventListener('push', function (event) {
    let payload = {};
    try {
        payload = event.data ? event.data.json() : {};
    } catch (e) {
        payload = { body: event.data ? event.data.text() : '' };
    }
    const title = payload.title || 'Notification';
    event.waitUntil(self.registration.showNotification(title, {
        body: payload.body || '',
        icon: payload.icon,
        badge: payload.badge,
        tag: payload.tag,
        data: { url: payload.url || BASE }
    }));
});

self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    const target = (event.notification.data && event.notification.data.url) || BASE;
    // Focus an already-open window rather than opening a second copy of the app.
    event.waitUntil(
        self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (windows) {
            for (let i = 0; i < windows.length; i++) {
                if (windows[i].url.indexOf(BASE) === 0 && 'focus' in windows[i]) {
                    windows[i].navigate(target);
                    return windows[i].focus();
                }
            }
            return self.clients.openWindow(target);
        })
    );
});
