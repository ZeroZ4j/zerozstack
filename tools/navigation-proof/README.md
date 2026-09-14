# Navigation proof

What a person sees while a page loads, when two navigations overlap, and when a page cannot be
opened - checked in a real Chrome, against the real routing tour, with real clicks and key presses.

## Why it exists

The router's busy indicator and failure message are the kind of feature that only goes wrong in a
browser. Whether the indicator appears after 300 milliseconds, whether it is gone the moment the
page is on screen, whether a slow answer arriving late overwrites the page somebody clicked since,
whether a network that dies without closing anything ends in a message rather than a spinner that
never stops: none of that can be answered on the JVM. The JVM tests in `zerozstack-client`
(`RouterLifecycleTest`, `ConnectionLivenessTest`) prove the order of events and the timers with a
clock the test moves by hand; this proves the browser does what those events promise.

It is not part of the Maven reactor, for the same reason `tools/ui-proof` is not: it needs a built
server, a browser, and about two and a half minutes.

## What it does

`drive.mjs` starts the routing tour server itself, from the jar the build leaves in
`zerozstack-examples/routing-tour/routing-tour-server/target`, on port 8191. It puts a small TCP proxy
of its own on port 8192 and points Chrome at that. The proxy is how the connection is lost on
purpose. It can cut every socket and refuse new ones, which is what a server going down looks like.
It can also stop forwarding in both directions while keeping every socket open, which is what a
network that has died without saying so looks like, and is the case a browser takes minutes to
notice by itself.

It checks, in this order:

1. **A link to the slow page.** The indicator appears between 280 and 1200 ms after the click,
   shows once and hides once, and is gone when the page is on screen. While it shows: the bar is
   3 px, the card is 72 px and centered, the ring is 48 px and spinning, the card is a polite status
   saying "Loading" in visually hidden text, the wait cursor wins over a link, and neither the bar
   nor the card takes clicks.
2. **A link to the page already on screen.** The indicator never shows, nothing stays on, and the
   address does not change.
3. **Back and Forward** to the slow page, with the same timing checks as a link.
4. **A fast link clicked while the slow page loads.** The fast page stays after the slow answer
   arrives, and the address matches it.
5. **The link for the page still showing, clicked while another page loads.** That page stays, and
   the address matches it.
6. **Reduced motion.** The ring pulses instead of spinning.
7. **The connection is cut mid-navigation and new connections are refused.** The failure message
   appears with the connection sentence, as an alert; the indicator is gone; the old page is still
   there; the message says Retry is waiting and pressing Retry does nothing. Once connections are
   allowed again and the tab has reconnected by itself, the note goes, Tab from the top of the page
   reaches a real button named Retry, Enter on it opens the page, and the address matches.
8. **The network goes silent mid-navigation.** Ten seconds in the indicator is still up. The
   navigation then fails with the connection sentence in under 19 seconds - the keepalive's
   five-second probe, ten-second answer and one-second timer - rather than hanging, and Retry works
   once the network is back.
9. **A call the server holds on a live connection**, opened as `/stalled` with `?timeout=20000`. It
   fails at the 20-second request timeout and not before, as a timeout: the keepalive's probe pings
   were answered while the server was busy, so the connection was never given up on.
10. **A call the server does not answer**, opened as `/stalled` with `?timeout=3000`. The navigation
    fails with the connection sentence between 3 and 8 seconds, and the indicator was up until then.
11. **A loader the server refuses** (`/projects/999`). The message says something went wrong rather
    than blaming the connection, still offers Retry, and Dismiss closes it.

And that no page threw an uncaught error. Screenshots of the indicator and the message, light and
dark, land in `shots/<label>/`, with the server's log beside them.

## Running it

Build the routing tour, then run the driver:

```bash
mvn -pl zerozstack-examples/routing-tour/routing-tour-server -am install -DskipTests
node tools/navigation-proof/drive.mjs --java "<JDK 21>/bin/java"
```

The driver takes Playwright from `zerozstack-archetype/smoke`, like `tools/ui-proof` does - run
`npm install` there once, or point elsewhere with `--playwright <dir>`. `--headed` shows the browser.
`--port` and `--proxy-port` move the two ports. It exits non-zero if any check fails and prints one
line per check either way.

## Against a minified build

The ordinary build is unminified, and minification is what has broken this framework's embedded
scripts before: the connection bar read `[object HTMLDivElement]` for two releases. So run it a
second time against the shape a user receives:

```bash
mvn -pl zerozstack-examples/routing-tour/routing-tour-client,zerozstack-examples/routing-tour/routing-tour-server install -DskipTests -Pproduction
node tools/navigation-proof/drive.mjs --java "<JDK 21>/bin/java" --label minified
```

`-Pproduction` on the routing tour's client turns on TeaVM's `ADVANCED` optimization and
minification, exactly as a generated application's `production` profile does. Build it again without
the profile afterwards to go back to the ordinary, readable bundle.
