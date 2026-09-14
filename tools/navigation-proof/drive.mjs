/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Drives a real Chrome against the routing tour and checks what a person sees while a page loads,
// when two navigations overlap, and when a page cannot be opened: the router's busy indicator and
// its failure message.
//
//   node drive.mjs [--label <name>] [--jar <server jar>] [--java <java executable>]
//                  [--port 8191] [--proxy-port 8192] [--playwright <dir>] [--headed]
//
// It starts the routing tour server itself, from the jar the build left behind, and puts a small
// TCP proxy of its own between the browser and that server. The proxy is how a connection is lost
// on purpose: it can cut every socket at once, refuse new ones, or - the case that matters most -
// stop forwarding anything while keeping every socket open, which is what a network that has died
// without saying so looks like to a browser.
//
// Screenshots land in shots/<label>/. Exits non-zero if any check fails.

import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import net from 'node:net';
import path from 'node:path';
import fs from 'node:fs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(HERE, '..', '..');

/**
 * The routing tour server jar, wherever the build left it. Its name carries the framework's
 * version, which moves at every release, so this looks rather than hard-codes it - the previous
 * hard-coded name went stale at the first version bump after this tool was written.
 */
function findServerJar() {
    const targetDir = path.join(ROOT, 'zerozstack-examples', 'routing-tour', 'routing-tour-server',
        'target');
    if (!fs.existsSync(targetDir)) {
        return path.join(targetDir, 'routing-tour-server.jar');   // reported missing below
    }
    const jars = fs.readdirSync(targetDir)
        .filter(name => /^routing-tour-server-.*\.jar$/.test(name) && !name.endsWith('-sources.jar'));
    if (jars.length === 0) {
        return path.join(targetDir, 'routing-tour-server.jar');   // reported missing below
    }
    jars.sort();
    return path.join(targetDir, jars[jars.length - 1]);
}

const args = process.argv.slice(2);
function option(name, fallback) {
    const index = args.indexOf(name);
    return index >= 0 ? args[index + 1] : fallback;
}
const headed = args.includes('--headed');
const label = option('--label', 'default');
const serverPort = Number(option('--port', '8191'));
const proxyPort = Number(option('--proxy-port', '8192'));
const java = option('--java', process.env.JAVA_HOME_21
    ? path.join(process.env.JAVA_HOME_21, 'bin', 'java') : 'java');
const jar = option('--jar', findServerJar());
const pwHome = option('--playwright', path.join(ROOT, 'zerozstack-archetype', 'smoke'));

const require = createRequire(path.join(pwHome, 'package.json'));
const { chromium } = require('playwright');

const SHOTS = path.join(HERE, 'shots', label);
fs.rmSync(SHOTS, { recursive: true, force: true });
fs.mkdirSync(SHOTS, { recursive: true });

const BASE = `http://localhost:${proxyPort}`;
const SIGN_IN = 'user=admin&password=admin';

// The sentences the framework puts on the screen, exactly as FrameworkText has them.
const CONNECTION_FAILED = 'We could not open this page. Check your connection and try again.';
const OTHER_FAILED = 'We could not open this page. Something went wrong while loading it.';
const WAITING = 'Reconnecting. Retry will work once the connection is back.';

let failures = 0;
let checks = 0;
function check(name, ok, detail) {
    checks++;
    if (ok) {
        console.log(`  PASS  ${name}${detail === undefined ? '' : `  (${detail})`}`);
    } else {
        failures++;
        console.log(`  FAIL  ${name}${detail === undefined ? '' : ` — ${detail}`}`);
    }
}

let shotNumber = 0;
async function shot(page, name) {
    shotNumber++;
    await page.screenshot({ path: path.join(SHOTS, `${String(shotNumber).padStart(2, '0')}-${name}.png`) });
}

const sleep = (millis) => new Promise((resolve) => setTimeout(resolve, millis));

// =====================================================================
// The proxy
// =====================================================================

const proxy = (() => {
    let mode = 'pass';                  // pass | refuse | silent
    const pairs = new Set();
    const server = net.createServer((client) => {
        if (mode === 'refuse') {
            client.destroy();
            return;
        }
        const upstream = net.connect(serverPort, '127.0.0.1');
        const pair = { client, upstream };
        pairs.add(pair);
        const forget = () => {
            pairs.delete(pair);
            client.destroy();
            upstream.destroy();
        };
        // Data that arrives while silent is dropped, not buffered: a dead network does not deliver
        // it later either.
        client.on('data', (chunk) => { if (mode === 'pass') { upstream.write(chunk); } });
        upstream.on('data', (chunk) => { if (mode === 'pass') { client.write(chunk); } });
        client.on('error', forget);
        upstream.on('error', forget);
        client.on('close', forget);
        upstream.on('close', forget);
    });
    return {
        listen: () => new Promise((resolve) => server.listen(proxyPort, '127.0.0.1', resolve)),
        close: () => new Promise((resolve) => server.close(resolve)),
        /** Stops forwarding in both directions, closing nothing. */
        silence() { mode = 'silent'; },
        /** Cuts every socket now and refuses new ones. */
        cutAndRefuse() {
            mode = 'refuse';
            for (const pair of [...pairs]) { pair.client.destroy(); pair.upstream.destroy(); }
        },
        /** Cuts every socket, including any opened while silent, and forwards again. */
        restore() {
            for (const pair of [...pairs]) { pair.client.destroy(); pair.upstream.destroy(); }
            mode = 'pass';
        },
    };
})();

// =====================================================================
// The server
// =====================================================================

async function startServer() {
    if (!fs.existsSync(jar)) {
        console.log(`No server jar at ${jar}. Build the routing tour first (see README.md).`);
        process.exit(2);
    }
    const child = spawn(java, ['-jar', jar, '--dev-login', '--port', String(serverPort)],
        { stdio: ['ignore', 'pipe', 'pipe'] });
    const log = fs.createWriteStream(path.join(SHOTS, 'server.log'));
    child.stdout.pipe(log);
    child.stderr.pipe(log);
    for (let attempt = 0; attempt < 120; attempt++) {
        try {
            const response = await fetch(`http://localhost:${serverPort}/`);
            if (response.ok) {
                return child;
            }
        } catch {
            // not up yet
        }
        await sleep(500);
    }
    child.kill();
    throw new Error('the routing tour server did not start within a minute');
}

// =====================================================================
// In the page
// =====================================================================

// Records every change of the busy class on <html> with a timestamp, and every click, so the
// driver can say how long after a click the indicator appeared and whether it ever did.
const RECORDER = () => {
    window.__proof = { busy: [], clicks: [] };
    const now = () => performance.now();
    // Observes the document rather than <html>, which may not exist yet when this runs.
    new MutationObserver(() => {
        const busy = !!document.documentElement && document.documentElement.classList.contains('zeroz4j-busy');
        const log = window.__proof.busy;
        if ((log.length === 0 && busy) || (log.length > 0 && log[log.length - 1].busy !== busy)) {
            log.push({ at: now(), busy });
        }
    }).observe(document, { attributes: true, attributeFilter: ['class'], subtree: true });
    document.addEventListener('click', () => window.__proof.clicks.push(now()), true);
};

async function screenText(page) {
    return page.evaluate(() => document.getElementById('app-root')?.textContent || '');
}

async function waitForText(page, text, timeout = 15000) {
    try {
        await page.waitForFunction((wanted) =>
            (document.getElementById('app-root')?.textContent || '').includes(wanted), text, { timeout });
        return true;
    } catch {
        return false;
    }
}

async function isBusy(page) {
    return page.evaluate(() => document.documentElement.classList.contains('zeroz4j-busy'));
}

async function markLog(page) {
    return page.evaluate(() => window.__proof.busy.length);
}

async function busyLogSince(page, mark) {
    return page.evaluate((from) => window.__proof.busy.slice(from), mark);
}

async function failureMessage(page) {
    return page.evaluate(() => {
        const box = document.getElementById('zeroz4j-navigation-failure');
        if (!box) {
            return null;
        }
        const retry = box.querySelector('.zeroz4j-navigation-failure-retry');
        return {
            text: box.querySelector('.zeroz4j-navigation-failure-text').textContent,
            note: box.querySelector('.zeroz4j-navigation-failure-note').textContent,
            role: box.getAttribute('role'),
            retry: retry ? { text: retry.textContent, tag: retry.tagName,
                waiting: retry.getAttribute('aria-disabled') === 'true' } : null,
            visible: box.getBoundingClientRect().height > 0,
        };
    });
}

async function waitForFailure(page, timeout) {
    try {
        await page.waitForFunction(() => !!document.getElementById('zeroz4j-navigation-failure'),
            null, { timeout });
        return true;
    } catch {
        return false;
    }
}

/** Clicks a link the way a person does: a real mouse click on a real anchor. */
async function clickLink(page, href) {
    await page.locator(`#app-root a[data-route][href="${href}"]`).first().click();
}

/** Puts a real routed link on the page for an address the tour does not link to, and clicks it. */
async function clickAddedLink(page, href) {
    await page.evaluate((target) => {
        const anchor = document.createElement('a');
        anchor.setAttribute('data-route', '');
        anchor.setAttribute('href', target);
        anchor.id = 'proof-link';
        anchor.textContent = 'proof link ' + target;
        anchor.style.cssText = 'position:fixed;bottom:8px;right:8px;z-index:1;background:#fff;color:#000;padding:4px';
        document.getElementById('proof-link')?.remove();
        document.body.appendChild(anchor);
    }, href);
    await page.locator('#proof-link').click();
}

async function setTheme(page, theme) {
    await page.evaluate((name) => document.body.setAttribute('data-theme', name), theme);
}

async function waitConnected(page, timeout = 30000) {
    // The failure message's note is empty exactly when the connection is up; with no message on
    // screen, a trivial fast navigation tells us.
    await page.waitForFunction(() => {
        const box = document.getElementById('zeroz4j-navigation-failure');
        const retry = box?.querySelector('.zeroz4j-navigation-failure-retry');
        return !!retry && retry.getAttribute('aria-disabled') !== 'true';
    }, null, { timeout });
}

async function openTour(page, query = '') {
    await page.goto(`${BASE}/?${SIGN_IN}${query}`);
    const ok = await waitForText(page, 'Routing tour', 30000);
    if (!ok) {
        throw new Error('the routing tour did not load');
    }
}

// =====================================================================
// The checks
// =====================================================================

async function slowLinkShowsAndHides(page, what, act, expectText) {
    const mark = await markLog(page);
    const started = await page.evaluate(() => performance.now());
    await act();
    const shown = await page.waitForFunction(() => document.documentElement.classList.contains('zeroz4j-busy'),
        null, { timeout: 5000 }).then(() => true, () => false);
    check(`${what}: the busy indicator appears`, shown);
    const arrived = await waitForText(page, expectText, 10000);
    check(`${what}: the page arrives`, arrived);
    check(`${what}: the indicator is gone the moment the page is on screen`, !(await isBusy(page)));
    const log = await busyLogSince(page, mark);
    const on = log.find((entry) => entry.busy);
    const off = log.find((entry) => !entry.busy);
    const delay = on ? Math.round(on.at - started) : -1;
    check(`${what}: it waited about 300 ms before appearing`, delay >= 280 && delay <= 1200, `${delay} ms`);
    check(`${what}: it showed once and hid once`, log.length === 2 && on && off && off.at > on.at,
        JSON.stringify(log.map((entry) => entry.busy)));
}

async function run(browser) {
    const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    await context.addInitScript(RECORDER);
    const page = await context.newPage();
    const consoleErrors = [];
    page.on('pageerror', (error) => consoleErrors.push(String(error)));

    await openTour(page);

    // ------------------------------------------------ a slow link, with a look at the indicator
    console.log('\nA link to a slow page');
    {
        const mark = await markLog(page);
        await clickLink(page, '/slow');
        await page.waitForFunction(() => document.documentElement.classList.contains('zeroz4j-busy'),
            null, { timeout: 5000 }).catch(() => {});
        const look = await page.evaluate(() => {
            const bar = document.getElementById('zeroz4j-busy-bar');
            const card = document.getElementById('zeroz4j-busy-spinner');
            const ring = card?.querySelector('.zeroz4j-busy-ring');
            const link = document.querySelector('#app-root a[data-route]');
            const cardBox = card?.getBoundingClientRect();
            return {
                barHeight: bar ? bar.getBoundingClientRect().height : 0,
                barOpacity: bar ? getComputedStyle(bar).opacity : '0',
                cardSize: cardBox ? Math.round(cardBox.width) : 0,
                cardCenterX: cardBox ? Math.round(cardBox.left + cardBox.width / 2) : 0,
                cardCenterY: cardBox ? Math.round(cardBox.top + cardBox.height / 2) : 0,
                ringSize: ring ? Math.round(ring.getBoundingClientRect().width) : 0,
                ringAnimation: ring ? getComputedStyle(ring).animationName : '',
                role: card?.getAttribute('role'),
                live: card?.getAttribute('aria-live'),
                label: card?.querySelector('.zeroz4j-busy-label')?.textContent,
                labelWidth: card?.querySelector('.zeroz4j-busy-label')?.getBoundingClientRect().width,
                linkCursor: link ? getComputedStyle(link).cursor : '',
                cardTakesClicks: card ? getComputedStyle(card).pointerEvents : '',
                barTakesClicks: bar ? getComputedStyle(bar).pointerEvents : '',
            };
        });
        check('the bar is 3 px high and visible', look.barHeight === 3 && look.barOpacity !== '0',
            `${look.barHeight} px`);
        check('the card is 72 px and centered in the window',
            look.cardSize === 72 && Math.abs(look.cardCenterX - 640) <= 1 && Math.abs(look.cardCenterY - 400) <= 1,
            `${look.cardSize} px at ${look.cardCenterX},${look.cardCenterY}`);
        check('the spinner ring is 48 px and spinning', look.ringSize === 48 && look.ringAnimation === 'zeroz4j-busy-spin',
            `${look.ringSize} px, ${look.ringAnimation}`);
        check('the card is a polite status saying "Loading", visually hidden',
            look.role === 'status' && look.live === 'polite' && look.label === 'Loading' && look.labelWidth <= 1);
        check('the wait cursor wins over a link', look.linkCursor === 'wait', look.linkCursor);
        check('neither the bar nor the card takes clicks',
            look.cardTakesClicks === 'none' && look.barTakesClicks === 'none');
        await sleep(250);   // past the card's 150 ms fade-in, so the picture is of what a person sees
        await shot(page, 'busy-dark');
        await setTheme(page, 'light');
        await shot(page, 'busy-light');
        await setTheme(page, 'dark');
        const arrived = await waitForText(page, 'A slow page', 10000);
        check('the slow page arrives', arrived);
        check('and the indicator hides with it', !(await isBusy(page)));
        const log = await busyLogSince(page, mark);
        check('one show and one hide', log.length === 2, JSON.stringify(log.map((entry) => entry.busy)));
        const label = await page.evaluate(() =>
            document.querySelector('#zeroz4j-busy-spinner .zeroz4j-busy-label').textContent);
        check('the hidden card says nothing, so "Loading" is announced afresh next time', label === '');
    }

    // ------------------------------------------------ the same page's own link
    console.log('\nA link to the page already on screen');
    {
        const mark = await markLog(page);
        const before = page.url();
        await clickLink(page, '/slow');
        await sleep(1200);
        const log = await busyLogSince(page, mark);
        check('the indicator never shows', log.length === 0, JSON.stringify(log));
        check('nothing stays on', !(await isBusy(page)));
        check('the address is unchanged', page.url() === before, page.url());
    }

    // ------------------------------------------------ Back and Forward
    console.log('\nBack and Forward');
    {
        await clickLink(page, '/projects');
        await waitForText(page, 'Sort:');
        await slowLinkShowsAndHides(page, 'Back to the slow page', () => page.goBack(), 'A slow page');
        await page.goBack();
        await waitForText(page, 'Routing tour');
        await slowLinkShowsAndHides(page, 'Forward to the slow page', () => page.goForward(), 'A slow page');
    }

    // ------------------------------------------------ overlapping navigations
    console.log('\nA fast link clicked while a slow page is loading');
    {
        await clickLink(page, '/');
        await waitForText(page, 'Routing tour');
        await clickLink(page, '/slow');
        await sleep(500);
        await clickLink(page, '/projects');
        await waitForText(page, 'Sort:');
        await sleep(2600);   // well past the moment the slow page's answer arrives
        const text = await screenText(page);
        check('the fast page stays on screen after the slow answer arrives',
            text.includes('Sort:') && !text.includes('A slow page'));
        check('the address matches the screen', new URL(page.url()).pathname === '/projects', page.url());
        check('nothing is left busy', !(await isBusy(page)));
    }

    console.log('\nThe link for the screen still showing, clicked while another page loads');
    {
        // On /projects. Click the slow page, then - while it loads - the link for /projects,
        // which is still what the screen shows.
        await clickLink(page, '/slow');
        await sleep(500);
        await clickLink(page, '/projects');
        await sleep(2600);
        const text = await screenText(page);
        check('the screen stays on the page clicked last', text.includes('Sort:') && !text.includes('A slow page'));
        check('the address matches it', new URL(page.url()).pathname === '/projects', page.url());
        check('nothing is left busy', !(await isBusy(page)));
    }

    // ------------------------------------------------ reduced motion
    console.log('\nReduced motion');
    {
        await page.emulateMedia({ reducedMotion: 'reduce' });
        await clickLink(page, '/slow');
        await page.waitForFunction(() => document.documentElement.classList.contains('zeroz4j-busy'),
            null, { timeout: 5000 }).catch(() => {});
        const animation = await page.evaluate(() => getComputedStyle(
            document.querySelector('#zeroz4j-busy-spinner .zeroz4j-busy-ring')).animationName);
        check('the ring pulses instead of spinning', animation === 'zeroz4j-busy-pulse', animation);
        await waitForText(page, 'A slow page');
        await page.emulateMedia({ reducedMotion: 'no-preference' });
    }

    // ------------------------------------------------ a dropped connection
    console.log('\nThe connection drops while a page is loading');
    {
        await clickLink(page, '/');
        await waitForText(page, 'Routing tour');
        await clickLink(page, '/slow');
        await sleep(500);
        proxy.cutAndRefuse();
        const appeared = await waitForFailure(page, 5000);
        check('the failure message appears', appeared);
        let message = await failureMessage(page);
        check('it says to check the connection', message?.text === CONNECTION_FAILED, message?.text);
        check('it is an alert', message?.role === 'alert');
        check('the indicator is gone', !(await isBusy(page)));
        check('the page that was showing is still there', (await screenText(page)).includes('Routing tour'));
        await page.waitForTimeout(300);
        message = await failureMessage(page);
        check('while the connection is down it says Retry is waiting', message?.note === WAITING, message?.note);
        check('and Retry waits', message?.retry?.waiting === true);
        await shot(page, 'failure-waiting-dark');

        // Retry pressed while down does nothing. Forced, because Playwright itself declines to click
        // an element marked aria-disabled - which is a person's click we want to see ignored.
        await page.locator('#zeroz4j-navigation-failure .zeroz4j-navigation-failure-retry').click({ force: true });
        await sleep(300);
        check('pressing Retry while down does nothing', (await failureMessage(page)) !== null && !(await isBusy(page)));

        proxy.restore();
        await waitConnected(page).catch(() => {});
        message = await failureMessage(page);
        check('once reconnected the note goes and Retry is ready',
            message && message.note === '' && message.retry && !message.retry.waiting, JSON.stringify(message));
        await shot(page, 'failure-dark');
        await setTheme(page, 'light');
        await shot(page, 'failure-light');
        await setTheme(page, 'dark');

        // The keyboard: the message is first in the page, so Tab from the top reaches Retry first.
        // A throwaway focusable element at the very top of <body> stands in for "the top of the
        // page", because where Tab starts from otherwise is wherever the last click landed.
        await page.evaluate(() => {
            const start = document.createElement('span');
            start.id = 'proof-tab-start';
            start.tabIndex = 0;
            document.body.insertBefore(start, document.body.firstChild);
            start.focus();
        });
        await page.keyboard.press('Tab');
        await page.evaluate(() => document.getElementById('proof-tab-start')?.remove());
        const focused = await page.evaluate(() => ({
            tag: document.activeElement?.tagName,
            name: document.activeElement?.textContent,
            isRetry: document.activeElement?.classList.contains('zeroz4j-navigation-failure-retry'),
        }));
        check('Tab reaches Retry, a real button named "Retry"',
            focused.isRetry && focused.tag === 'BUTTON' && focused.name === 'Retry', JSON.stringify(focused));
        await page.keyboard.press('Enter');
        const gone = await page.waitForFunction(() => !document.getElementById('zeroz4j-navigation-failure'),
            null, { timeout: 3000 }).then(() => true, () => false);
        check('Enter on Retry starts the navigation again and the message goes', gone);
        const retried = await waitForText(page, 'A slow page', 10000);
        check('the page opens on retry', retried);
        check('with the address to match', new URL(page.url()).pathname === '/slow');
    }

    // ------------------------------------------------ a network that goes silent
    console.log('\nThe network goes silent while a page is loading (about 16 seconds)');
    {
        await clickLink(page, '/');
        await waitForText(page, 'Routing tour');
        proxy.silence();
        const mark = await markLog(page);
        const started = Date.now();
        await clickLink(page, '/slow');
        await sleep(10000);
        check('ten seconds in, the indicator is still up: it has no timer of its own', await isBusy(page));
        const appeared = await waitForFailure(page, 20000);
        const elapsed = Date.now() - started;
        check('the navigation ends in the failure message rather than hanging', appeared, `${elapsed} ms`);
        check('within the liveness bound (five-second probe, ten-second answer, one-second tick)',
            elapsed < 19000, `${elapsed} ms`);
        const message = await failureMessage(page);
        check('it says to check the connection', message?.text === CONNECTION_FAILED, message?.text);
        const log = await busyLogSince(page, mark);
        check('the indicator showed once and hid when the failure arrived', log.length === 2,
            JSON.stringify(log.map((entry) => entry.busy)));
        proxy.restore();
        await waitConnected(page).catch(() => {});
        await page.locator('#zeroz4j-navigation-failure .zeroz4j-navigation-failure-retry').click();
        check('Retry works after the network comes back', await waitForText(page, 'A slow page', 10000));
    }

    // ------------------------------------------------ a slow call is not a dead connection
    console.log('\nA call held by the server on a live connection, with a 20-second request timeout (about 22 seconds)');
    {
        // Past the keepalive's five-second probe and ten-second answer twice over. If the probe's
        // answer were not arriving while the server is busy with the call, the connection would be
        // closed at about fifteen seconds and the reason would be "connection lost".
        const heldPage = await context.newPage();
        const lines = [];
        heldPage.on('console', (msg) => lines.push(msg.text()));
        heldPage.on('pageerror', (error) => consoleErrors.push(String(error)));
        await openTour(heldPage, '&timeout=20000');
        const started = Date.now();
        await clickAddedLink(heldPage, '/stalled');
        const appeared = await waitForFailure(heldPage, 30000);
        const elapsed = Date.now() - started;
        const reason = lines.find((line) => line.includes('Could not open /stalled')) || '';
        check('the navigation fails at the request timeout, not earlier', appeared && elapsed >= 20000,
            `${elapsed} ms`);
        check('as a timeout: the connection was never given up on',
            reason.includes('timed out') && !lines.some((line) => line.includes('Giving up on the connection')),
            reason);
        await heldPage.close();
    }

    // ------------------------------------------------ a call the server never answers
    console.log('\nA call the server does not answer, with a three-second request timeout');
    {
        const stalledPage = await context.newPage();
        stalledPage.on('pageerror', (error) => consoleErrors.push(String(error)));
        await openTour(stalledPage, '&timeout=3000');
        const mark = await markLog(stalledPage);
        const started = Date.now();
        await clickAddedLink(stalledPage, '/stalled');
        const appeared = await waitForFailure(stalledPage, 15000);
        const elapsed = Date.now() - started;
        check('the navigation ends in the failure message rather than hanging', appeared, `${elapsed} ms`);
        check('at the request timeout, not before', elapsed >= 3000 && elapsed < 8000, `${elapsed} ms`);
        const message = await failureMessage(stalledPage);
        check('a timeout counts as a connection problem', message?.text === CONNECTION_FAILED, message?.text);
        const log = await busyLogSince(stalledPage, mark);
        check('the indicator was up until the failure and hid with it', log.length === 2 && !(await isBusy(stalledPage)),
            JSON.stringify(log.map((entry) => entry.busy)));
        await stalledPage.close();
    }

    // ------------------------------------------------ a loader the server refuses
    console.log('\nA loader the server refuses');
    {
        await clickLink(page, '/');
        await waitForText(page, 'Routing tour');
        await clickAddedLink(page, '/projects/999');
        const appeared = await waitForFailure(page, 5000);
        const message = await failureMessage(page);
        check('the failure message appears', appeared);
        check('it says something went wrong, not the connection', message?.text === OTHER_FAILED, message?.text);
        check('it still offers Retry', message?.retry?.text === 'Retry');
        await page.locator('#zeroz4j-navigation-failure .zeroz4j-navigation-failure-dismiss').click();
        check('Dismiss closes it', (await failureMessage(page)) === null);
    }

    check('no uncaught error on any page', consoleErrors.length === 0, consoleErrors.join(' | '));
    await context.close();
}

// =====================================================================

await proxy.listen();
const serverProcess = await startServer();
const browser = await chromium.launch({ headless: !headed });
try {
    console.log(`Navigation proof (${label}) against ${BASE}`);
    await run(browser);
} catch (error) {
    failures++;
    console.log(`  FAIL  the run stopped: ${error.stack || error}`);
} finally {
    await browser.close();
    serverProcess.kill();
    await proxy.close().catch(() => {});
}

console.log(`\n${checks - failures}/${checks} checks passed (${label}). Screenshots in ${SHOTS}`);
process.exit(failures === 0 ? 0 : 1);
