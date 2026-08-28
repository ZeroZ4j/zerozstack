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

// Drives the whole gallery with the keyboard and nothing else, and says what cannot be reached.
//
// There is no page.click, no page.hover and no mouse.move anywhere in this file. Everything —
// signing in, opening the menu, walking each page — is Tab, Enter, Space, Escape and typing. That
// is the point: a control a mouse can work and a keyboard cannot is invisible to this script, and
// invisible to anybody who does not use a mouse.
//
//   node keyboard-walkthrough.mjs [--url http://localhost:8095] [--headed]
//                                 [--playwright G:/proj/trellis] [--widths 1920,1280,380]
//                                 [--only composition,narrow]
//
// Screenshots land in shots/. The findings are printed and written to shots/findings.txt.

import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SHOTS = path.join(HERE, 'shots');

const args = process.argv.slice(2);

function option(name, fallback) {
    const i = args.indexOf('--' + name);
    return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
}

const BASE_URL = option('url', 'http://localhost:8095');
const PW_HOME = option('playwright', 'G:/proj/trellis');
const HEADED = args.includes('--headed');
const WIDTHS = option('widths', '1920,1280,380').split(',').map((w) => parseInt(w, 10));
const ONLY = option('only', '').split(',').map((s) => s.trim()).filter(Boolean);

// Playwright is borrowed from another checkout on this machine. Nothing is installed here.
const require = createRequire(path.join(PW_HOME, 'package.json'));
const { chromium } = require('playwright');

// The sidebar entries this walkthrough visits, by the words shown in the menu. The folding
// sections they live in are opened first, with the keyboard.
const SECTIONS = [
    {
        group: 'Under pressure',
        pages: [
            'One thing inside another',
            'The four states of a panel',
            'A form that fails and recovers',
            'A list that moves',
            'Long words, other languages',
            'Everything, 360 pixels wide',
        ],
    },
    {
        group: 'UI Components',
        sub: 'Data Display',
        pages: ['Table', 'Alert', 'Badge', 'Timeline', 'Tooltip', 'Loading', 'Skeleton'],
    },
    {
        group: 'UI Components',
        sub: 'Data Input',
        pages: ['Text Field', 'Text Area', 'Select', 'Checkbox', 'Radio Button Group',
            'Range', 'Rating', 'File Upload', 'Toggle'],
    },
    {
        group: 'UI Components',
        sub: 'Navigation',
        pages: ['Breadcrumbs', 'Menu', 'Pagination', 'Steps', 'Tab', 'Navbar',
            'Bottom Navigation'],
    },
    {
        group: 'UI Components',
        sub: 'Actions',
        pages: ['Button', 'Link', 'Swap', 'Theme Controller'],
    },
    {
        group: 'UI Components',
        sub: 'Feedback',
        pages: ['Dialog', 'Toast'],
    },
    {
        group: 'UI Components',
        sub: 'Layout',
        pages: ['Drawer', 'Footer', 'Divider', 'Hero', 'Join', 'Stack', 'Indicator'],
    },
    {
        group: 'UI Components',
        sub: 'Dashboard Panels',
        pages: ['Panel Frame', 'Metric Table', 'Virtual Scroller', 'Lane Timeline',
            'Property Grid', 'Log Viewer'],
    },
];

// ---------------------------------------------------------------- findings

const findings = [];
let currentPage = '(before any page)';

const alreadySaid = new Set();

function finding(kind, detail) {
    const key = `${currentPage}|${kind}|${detail}`;
    if (alreadySaid.has(key)) return;
    alreadySaid.add(key);
    findings.push({ page: currentPage, kind, detail });
    console.log(`    ${kind}: ${detail}`);
}

let shotNumber = 0;
async function shot(page, name) {
    shotNumber++;
    const file = path.join(SHOTS, `${String(shotNumber).padStart(3, '0')}-${slug(name)}.png`);
    await page.screenshot({ path: file, fullPage: false });
    return file;
}

function slug(text) {
    return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '').slice(0, 60);
}

// ---------------------------------------------------------------- in-page helpers
//
// Everything below runs inside the browser. The accessible name is computed the way a screen
// reader would look for one: aria-labelledby, then aria-label, then a <label> pointing at the
// control, then the control's own words, then its title.

const IN_PAGE = `
window.__kw = window.__kw || {};
Object.assign(window.__kw, {
    controls: window.__kw.controls || [],
    reached: window.__kw.reached || new Set(),
    mutations: 0,

    // tabindex="-1" and aria-hidden are how a component says 'this is plumbing, not a
    // control' - a drawer's internal checkbox, for one. Counting those as controls would
    // report every deliberately skipped element as unreachable and bury the real findings.
    SELECTOR: 'a, button, input, select, textarea, summary, details, [tabindex]:not([tabindex="-1"]), ' +
        '[role="button"], [role="menuitem"], [role="tab"], [role="link"], [role="checkbox"], ' +
        '[role="separator"]',

    visible(el) {
        const style = getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        if (el.closest('[aria-hidden="true"]')) return false;
        if (el.getAttribute('tabindex') === '-1') return false;
        // Inside a folded-away section. The browser still gives these a size in some stylesheets,
        // so the rectangle alone does not catch them.
        const details = el.closest('details');
        if (details && !details.open && el.tagName !== 'SUMMARY' && !el.contains(details)) {
            const summary = details.querySelector(':scope > summary');
            if (!summary || !summary.contains(el)) return false;
        }
        if (el.closest('[hidden]')) return false;
        // Deliberately not a control: taken out of the keyboard's order and hidden from anything
        // that reads the page. A drawer's own tick box is one of these - the panel is worked
        // through its buttons, not through the box that records its state.
        if (el.getAttribute('tabindex') === '-1' && el.closest('[aria-hidden="true"]')) return false;
        if (el.getAttribute('aria-hidden') === 'true' && el.getAttribute('tabindex') === '-1') {
            return false;
        }
        const rect = el.getBoundingClientRect();
        return rect.width > 0 || rect.height > 0;
    },

    name(el) {
        if (!el) return '';
        const by = el.getAttribute('aria-labelledby');
        if (by) {
            const text = by.split(/\\s+/)
                .map((id) => { const t = document.getElementById(id); return t ? t.innerText : ''; })
                .join(' ').trim();
            if (text) return text;
        }
        const label = el.getAttribute('aria-label');
        if (label && label.trim()) return label.trim();
        if (el.id) {
            const forLabel = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
            if (forLabel && forLabel.innerText.trim()) return forLabel.innerText.trim();
        }
        const wrapping = el.closest('label');
        if (wrapping && wrapping.innerText.trim()) return wrapping.innerText.trim();
        if (el.innerText && el.innerText.trim()) return el.innerText.trim();
        // innerText is empty for anything the browser is not laying out - a folded-away menu
        // entry, for instance. The words are still there in textContent.
        if (el.textContent && el.textContent.trim()) return el.textContent.trim();
        const title = el.getAttribute('title');
        if (title && title.trim()) return title.trim();
        const placeholder = el.getAttribute('placeholder');
        if (placeholder && placeholder.trim()) return '(placeholder only) ' + placeholder.trim();
        return '';
    },

    describe(el) {
        if (!el) return 'nothing';
        if (el === document.body) return 'the page body';
        const tag = el.tagName.toLowerCase();
        const id = el.id ? '#' + el.id : '';
        const name = this.name(el);
        const stamp = el.dataset && el.dataset.kw !== undefined ? '(' + el.dataset.kw + ')' : '';
        // The stamp tells two unnamed inputs side by side apart; without it the walk
        // thinks Tab is stuck when it is simply passing a row of them.
        return tag + id + stamp + (name ? ' "' + name.replace(/\\s+/g, ' ').slice(0, 60) + '"' : ' (no name)');
    },

    /** Stamps every candidate control in the content area so the tab walk can be compared to it. */
    stamp(rootSelector) {
        const root = document.querySelector(rootSelector) || document.body;
        const all = Array.from(root.querySelectorAll(this.SELECTOR)).filter((el) => this.visible(el));
        this.controls = [];
        all.forEach((el, i) => {
            el.dataset.kw = String(i);
            const isRadio = el.tagName === 'INPUT' && el.getAttribute('type') === 'radio';
            this.controls.push({
                index: i,
                radioGroup: isRadio ? (el.getAttribute('name') || 'unnamed') : null,
                tag: el.tagName.toLowerCase(),
                id: el.id || '',
                name: this.name(el),
                disabled: el.disabled === true || el.getAttribute('aria-disabled') === 'true',
                tabindex: el.getAttribute('tabindex'),
                describe: this.describe(el),
            });
        });
        this.root = root;
        root.setAttribute('tabindex', '-1');
        this.reached = new Set();
        this.mutations = 0;
        if (this.observer) this.observer.disconnect();
        this.observer = new MutationObserver((records) => { this.mutations += records.length; });
        this.observer.observe(document.body, {
            subtree: true, childList: true, attributes: true, characterData: true,
        });
        return this.controls.length;
    },

    here() {
        const el = document.activeElement;
        const inside = !!(el && el.dataset && el.dataset.kw !== undefined);
        if (inside && this.reached) this.reached.add(el.dataset.kw);
        return {
            kw: inside ? el.dataset.kw : null,
            describe: this.describe(el),
            isBody: el === document.body || el === null,
            tag: el ? el.tagName.toLowerCase() : 'none',
            name: this.name(el),
            inDialog: !!(el && el.closest && el.closest('dialog, .modal, [role="dialog"]')),
        };
    },

    /**
     * What Tab never reached, minus the things Tab is not supposed to reach. A set of radio
     * buttons sharing a name is one stop on purpose - Tab gets you to the group, the arrow keys
     * move inside it - so only the first of each set counts as reachable-or-not.
     */
    missed() {
        const reachedGroups = new Set(this.controls
            .filter((c) => c.radioGroup && this.reached.has(String(c.index)))
            .map((c) => c.radioGroup));
        const seenRadioGroups = new Set();
        return this.controls.filter((c) => {
            if (c.disabled || this.reached.has(String(c.index))) return false;
            // A <details> is never focusable itself - its <summary> is the control. Reporting the
            // wrapper as unreachable says nothing about whether the section can be opened.
            if (c.tag === 'details') return false;
            if (c.radioGroup && reachedGroups.has(c.radioGroup)) return false;
            if (c.radioGroup) {
                if (seenRadioGroups.has(c.radioGroup)) return false;
                seenRadioGroups.add(c.radioGroup);
                // The group itself was not reached either, which is a real finding.
                return true;
            }
            return true;
        });
    },

    focusBody() {
        if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        document.body.focus();
    },

    /**
     * Puts the keyboard on the content area itself, so a page's own tab order can be walked
     * without crossing the whole sidebar again for every page. Reaching the sidebar by keyboard
     * is proved separately, by the fact that this walkthrough navigates with it.
     */
    focusRoot() {
        if (this.root) this.root.focus();
        return document.activeElement === this.root;
    },

    resetMutations() { this.mutations = 0; return true; },
    mutationCount() { return this.mutations; },
    openDialogs() { return document.querySelectorAll('dialog[open]').length; },

    scrollPosition() {
        const root = this.root;
        return String(window.scrollY) + ':' + (root ? root.scrollTop : 0);
    },

    /**
     * The things that are actually over the page right now. A drawer's panel carries
     * role="dialog" whether it is showing or not, so asking for the role alone finds closed ones
     * too; what says a drawer is open is its own tick box.
     */
    openOverlays() {
        const out = [];
        document.querySelectorAll('dialog[open]').forEach((d) => out.push(d));
        document.querySelectorAll('.drawer').forEach((drawer) => {
            const toggle = drawer.querySelector('input[type="checkbox"]');
            const panel = drawer.querySelector('.drawer-side [role="dialog"]');
            if (toggle && toggle.checked && panel) out.push(panel);
        });
        return out;
    },

    markOverlays() {
        this.overlaysBefore = new Set(this.openOverlays());
        return this.overlaysBefore.size;
    },

    /** The one that appeared since markOverlays, if any. */
    newOverlay() {
        const now = this.openOverlays();
        const fresh = now.find((el) => !this.overlaysBefore.has(el));
        this.overlay = fresh || null;
        return fresh ? this.describe(fresh) : null;
    },

    overlayStillOpen() {
        const el = this.overlay;
        if (!el || !el.isConnected) return false;
        return this.openOverlays().includes(el);
    },

    focusInsideOverlay() {
        return !!(this.overlay && this.overlay.contains(document.activeElement));
    },
    /**
     * How far the page reaches past its own right-hand edge. The window itself never scrolls in
     * this application - the content area does - so both are measured and the worse one wins.
     */
    horizontalOverflow() {
        const doc = document.documentElement.scrollWidth - document.documentElement.clientWidth;
        const area = this.root ? this.root.scrollWidth - this.root.clientWidth : 0;
        return Math.max(doc, area);
    },

    /** The widest thing sticking out, so the finding names something rather than a number. */
    widestOverflowingElement() {
        if (!this.root) return null;
        const edge = this.root.getBoundingClientRect().right;
        let worst = null;
        this.root.querySelectorAll('*').forEach((el) => {
            const right = el.getBoundingClientRect().right;
            if (right > edge + 2 && (!worst || right > worst.right)) {
                worst = { right, describe: this.describe(el) };
            }
        });
        return worst;
    },
});
true;
`;

// ---------------------------------------------------------------- keyboard-only navigation

/** Presses Tab until the focused element's name matches, or gives up. No mouse anywhere. */
async function tabUntil(page, matches, limit = 200) {
    for (let i = 0; i < limit; i++) {
        await page.keyboard.press('Tab');
        const where = await page.evaluate(() => window.__kw.here());
        if (matches(where)) return where;
    }
    return null;
}

async function focusBody(page) {
    await page.evaluate(() => window.__kw.focusBody());
}

/** Closes anything the probing left open and puts the keyboard back at the start of the page. */
async function settle(page) {
    for (let i = 0; i < 3; i++) {
        await page.keyboard.press('Escape');
        await page.waitForTimeout(60);
    }
    await page.evaluate(() => {
        document.querySelectorAll('dialog[open]').forEach((d) => d.close());
        document.querySelectorAll('.toast').forEach((t) => {
            if (t.parentNode === document.body) t.remove();
        });
    });
    await focusBody(page);
}

/** Signs in with the keyboard alone. */
async function signIn(page) {
    console.log('\nSigning in with the keyboard only');
    await page.waitForFunction(() => document.querySelectorAll('input').length >= 2,
        null, { timeout: 60000 });
    await focusBody(page);

    const username = await tabUntil(page, (w) => /user/i.test(w.name), 40);
    if (!username) {
        finding('could not be reached', 'the username field could not be reached by Tab');
        return false;
    }
    await page.keyboard.type('demo');
    await page.keyboard.press('Tab');
    let where = await page.evaluate(() => window.__kw.here());
    if (!/password/i.test(where.name)) {
        finding('focus went somewhere unexpected',
            `after the username field, Tab landed on ${where.describe}, not the password field`);
    }
    await page.keyboard.type('demo');
    await page.keyboard.press('Enter');

    try {
        await page.waitForFunction(() => document.body.innerText.includes('Component Gallery')
            || document.body.innerText.includes('Under pressure'), null, { timeout: 20000 });
    } catch (e) {
        // Enter inside the field may not submit; find the button and press it instead.
        finding('Enter did nothing',
            'pressing Enter in the password field did not sign in; had to Tab to the button');
        await tabUntil(page, (w) => /sign in|log in/i.test(w.name), 20);
        await page.keyboard.press('Enter');
        await page.waitForFunction(() => document.body.innerText.includes('Component Gallery')
            || document.body.innerText.includes('Under pressure'), null, { timeout: 30000 });
    }
    console.log('  signed in');
    return true;
}

/** Opens a folding sidebar section and presses the entry for one page. Keyboard only. */
async function openPage(page, section, label) {
    await settle(page);
    let group = await tabUntil(page, (w) => w.name.trim() === section.group, 300);
    if (!group) {
        // One retry from a clean state. A page that left something open can swallow a whole
        // pass otherwise, and then every page after it looks unreachable too.
        await settle(page);
        group = await tabUntil(page, (w) => w.name.trim() === section.group, 300);
    }
    if (!group) {
        finding('could not be reached',
            `the sidebar group "${section.group}" could not be reached by Tab, even after `
            + 'closing everything the page had open');
        return false;
    }
    const wasOpen = await page.evaluate((name) => {
        const summaries = Array.from(document.querySelectorAll('summary'));
        const match = summaries.find((s) => s.innerText.trim() === name);
        return match ? match.closest('details').open : null;
    }, section.group);
    if (!wasOpen) {
        await page.keyboard.press('Enter');
        await page.waitForTimeout(120);
    }

    if (section.sub) {
        const sub = await tabUntil(page, (w) => w.name.trim() === section.sub, 60);
        if (!sub) {
            finding('could not be reached',
                `the sidebar section "${section.sub}" could not be reached by Tab`);
            return false;
        }
        const subOpen = await page.evaluate((name) => {
            const summaries = Array.from(document.querySelectorAll('summary'));
            const match = summaries.find((s) => s.innerText.trim() === name);
            return match ? match.closest('details').open : null;
        }, section.sub);
        if (!subOpen) {
            await page.keyboard.press('Enter');
            await page.waitForTimeout(120);
        }
    }

    const entry = await tabUntil(page, (w) => w.name.trim() === label, 200);
    if (!entry) {
        finding('could not be reached', `the sidebar entry "${label}" could not be reached by Tab`);
        return false;
    }
    await page.keyboard.press('Enter');
    await page.waitForTimeout(400);
    return true;
}

// ---------------------------------------------------------------- the walk

const CONTENT = '#app-root > div > div:nth-child(2)';

/** How many controls per page get the Enter-and-Space treatment. Each one costs about a second. */
let PROBE_LIMIT = 12;

/**
 * At the widest and narrowest sizes only these pages are walked. Walking all sixty at three
 * widths takes over an hour and finds the same things three times; these are the ones where the
 * width is the question.
 */
const AT_EVERY_WIDTH = new Set([
    'One thing inside another', 'The four states of a panel', 'A form that fails and recovers',
    'A list that moves', 'Long words, other languages', 'Everything, 360 pixels wide',
    'Table', 'Select', 'Pagination', 'Steps', 'Tab', 'Menu', 'Breadcrumbs', 'Dialog', 'Drawer',
    'Metric Table', 'Lane Timeline', 'Virtual Scroller',
]);

/** The width at which every page is walked. */
const FULL_WIDTH = 1280;

async function walkPage(page, label) {
    currentPage = label;
    console.log(`\n--- ${label}`);

    const count = await page.evaluate((sel) => window.__kw.stamp(sel), CONTENT);
    if (count === 0) {
        console.log('    no controls on this page');
        return;
    }

    // 1. Tab all the way through the content area and record where the keyboard actually goes.
    //    Landing on the page body once is the browser's own cycle boundary and is not a fault;
    //    what matters is what Tab never reaches, and whether it ever refuses to move on.
    await page.evaluate(() => window.__kw.focusRoot());
    let previous = null;
    let stuck = 0;
    let wraps = 0;
    for (let i = 0; i < Math.min(count * 2 + 40, 600); i++) {
        await page.keyboard.press('Tab');
        const where = await page.evaluate(() => window.__kw.here());
        if (where.isBody) {
            wraps++;
            if (wraps > 2) break;
        }
        if (previous && !previous.isBody && where.describe === previous.describe) {
            stuck++;
            if (stuck > 3) {
                finding('Tab will not move on', `Tab stays on ${where.describe}`);
                await page.keyboard.press('Escape');
                await page.waitForTimeout(100);
                stuck = 0;
            }
        } else {
            stuck = 0;
        }
        previous = where;
    }

    // 2. What Tab never reached.
    const missed = await page.evaluate(() => window.__kw.missed());
    const reachedCount = await page.evaluate(() => window.__kw.reached.size);
    console.log(`    ${reachedCount} of ${count} controls reached by Tab`);
    const wholeGroupsMissed = new Set();
    for (const control of missed.slice(0, 25)) {
        if (control.radioGroup) {
            if (wholeGroupsMissed.has(control.radioGroup)) continue;
            wholeGroupsMissed.add(control.radioGroup);
            finding('never reached by Tab',
                `not one of the round buttons named "${control.radioGroup}" — a set is one stop, `
                + `and this set has none, starting with ${control.describe}`);
            continue;
        }
        finding('never reached by Tab', control.describe);
    }
    if (missed.length > 25) {
        finding('never reached by Tab',
            `...and ${missed.length - 25} more of the same kind on this page`);
    }

    // 3. What has no name.
    const nameless = await page.evaluate(() => window.__kw.controls
        .filter((c) => !c.name && !c.disabled && c.tag !== 'details')
        .map((c) => c.describe));
    for (const control of nameless) {
        finding('no accessible name', control);
    }

    // 4. Every overlay this page can open: does the keyboard go in, stay in, and come back out?
    await overlayWalk(page);
    await page.evaluate((sel) => window.__kw.stamp(sel), CONTENT);
    await page.evaluate(() => window.__kw.focusRoot());
    for (let i = 0; i < Math.min(count + 10, 200); i++) {
        await page.keyboard.press('Tab');
        const where = await page.evaluate(() => window.__kw.here());
        if (where.isBody) break;
    }

    // 5. Enter and Space on everything the keyboard could reach, up to a sensible number.
    await settle(page);
    const probeable = await page.evaluate(() => window.__kw.controls
        .filter((c) => !c.disabled
            // Links are left out on purpose: following a link is the browser's job, and a link to
            // a place on the page you are already at changes nothing without being broken. A link
            // with no destination is caught by the tab walk instead, because nothing can reach it.
            && ['button', 'summary'].includes(c.tag)
            && window.__kw.reached.has(String(c.index)))
        .map((c) => ({ index: c.index, describe: c.describe })));

    for (const control of probeable.slice(0, PROBE_LIMIT)) {
        const dead = await probe(page, control, 'Enter');
        if (dead) {
            const alsoDead = await probe(page, control, ' ');
            if (alsoDead) {
                finding('Enter and Space did nothing', control.describe);
            } else {
                finding('Enter did nothing but Space did', control.describe);
            }
        }
    }
    await settle(page);

    // 6. Does the keyboard stay where it was put, with nobody touching anything?
    await page.evaluate((sel) => window.__kw.stamp(sel), CONTENT);
    await page.evaluate(() => window.__kw.focusRoot());
    for (let i = 0; i < 3; i++) {
        await page.keyboard.press('Tab');
        await page.evaluate(() => window.__kw.here());
    }
    await focusSurvival(page);

    // 7. Anything that pushed the page sideways.
    const overflow = await page.evaluate(() => window.__kw.horizontalOverflow());
    if (overflow > 2) {
        const worst = await page.evaluate(() => window.__kw.widestOverflowingElement());
        finding('the page reaches past its right-hand edge',
            `${overflow} pixels too wide`
            + (worst ? `; the furthest out is ${worst.describe}` : ''));
    }
}

/**
 * Opens each overlay this page offers, with the keyboard, and asks the three questions that only
 * matter once something is over the page: does the keyboard go inside it, does it stay inside,
 * and does Escape let it out again and put the keyboard back where it started.
 */
async function overlayWalk(page) {
    const openers = await page.evaluate(() => window.__kw.controls
        .filter((c) => !c.disabled && c.tag === 'button'
            && /\bopen\b|drawer|dialog|box|panel/i.test(c.name))
        .map((c) => ({ index: c.index, describe: c.describe })));

    for (const opener of openers.slice(0, 5)) {
        await settle(page);
        const landed = await page.evaluate((index) => {
            const el = document.querySelector('[data-kw="' + index + '"]');
            if (!el) return null;
            el.focus();
            window.__kw.markOverlays();
            return document.activeElement === el
                ? window.__kw.describe(document.activeElement) : null;
        }, opener.index);
        // Not landing here is a stamping artefact, not a finding: whether Tab reaches this
        // control was already decided by the walk above.
        if (!landed) continue;

        await page.keyboard.press('Enter');
        await page.waitForTimeout(500);

        const appeared = await page.evaluate(() => window.__kw.newOverlay());
        if (!appeared) continue;

        const inside = await page.evaluate(() => ({
            inside: window.__kw.focusInsideOverlay(),
            where: window.__kw.describe(document.activeElement),
        }));
        if (!inside.inside) {
            finding('the keyboard did not go into the overlay',
                `${opener.describe} opened ${appeared} and the keyboard stayed on ${inside.where}`);
        }

        // Twelve tabs. If the keyboard leaves, the page behind is reachable while it is covered.
        let escaped = null;
        for (let i = 0; i < 12; i++) {
            await page.keyboard.press('Tab');
            const still = await page.evaluate(() => ({
                inside: window.__kw.focusInsideOverlay(),
                isBody: document.activeElement === document.body || !document.activeElement,
                where: window.__kw.describe(document.activeElement),
            }));
            // Nothing focused at all is the browser's own cycle boundary - the keyboard has gone
            // to the browser's own furniture, not to the page behind. Only a real control behind
            // the overlay means the overlay is leaking.
            if (!still.inside && !still.isBody) {
                escaped = still.where;
                break;
            }
        }
        if (escaped && inside.inside) {
            finding('the keyboard left the overlay',
                `Tab walked out of ${appeared} onto ${escaped}, which is behind it`);
        }

        await page.keyboard.press('Escape');
        await page.waitForTimeout(400);
        const stillOpen = await page.evaluate(() => window.__kw.overlayStillOpen());
        if (stillOpen) {
            // Some of these refuse Escape on purpose, and say so on the page. Both are recorded;
            // reading the page says which is which.
            finding('Escape did not close it', `${appeared}, opened by ${opener.describe}`);
        } else {
            const back = await page.evaluate(() => window.__kw.describe(document.activeElement));
            if (back !== landed) {
                finding('the keyboard did not come back after closing',
                    `it started on ${landed}; after closing it was on ${back}`);
            }
        }
        await settle(page);
        await page.evaluate((sel) => window.__kw.stamp(sel), CONTENT);
    }
}

/**
 * Puts the keyboard on a control, presses nothing for three seconds, and reports if it moved.
 * A page that redraws itself on a timer is the one that fails this.
 */
async function focusSurvival(page) {
    // Three depths, not one. The start of a page is often a filter box that nothing redraws,
    // while the control that gets thrown away is in the middle of the list below it. The keyboard
    // is put there by tabbing rather than by a remembered element, because on a page that redraws
    // itself the remembered element is gone by the time it is asked for.
    for (const depth of [3, 12, 24]) {
        await page.evaluate(() => window.__kw.focusRoot());
        let landed = null;
        for (let i = 0; i < depth; i++) {
            await page.keyboard.press('Tab');
            const where = await page.evaluate(() => window.__kw.here());
            if (where.isBody) {
                // Stepped off the end of the page. Step back onto the last real control, or the
                // check would compare "on the last control" with "already on the body" and call
                // every short page a fault.
                await page.keyboard.press('Shift+Tab');
                const back = await page.evaluate(() => window.__kw.here());
                landed = back.isBody ? null : back.describe;
                break;
            }
            landed = where.describe;
        }
        if (!landed) continue;

        await page.waitForTimeout(3000);
        const after = await page.evaluate(() => window.__kw.describe(document.activeElement));
        if (after !== landed) {
            finding('the keyboard moved on its own',
                `it was on ${landed}; three seconds later, with nothing pressed, it was on `
                + after);
            // One report per page is enough - the fault is the page, not each control on it.
            return;
        }
    }
}

/**
 * Focuses one control without a mouse, presses a key, and says whether anything changed at all.
 *
 * <p>"Changed" is deliberately broad: the page's own elements, the address, what is scrolled to,
 * and whether something opened over the page. A component that only sets a property — a tick box
 * ticked from code — changes nothing a mutation observer can see, and reporting that as a dead
 * control would be wrong.</p>
 */
async function probe(page, control, key) {
    // Anything an earlier press left open is shut first. Without this, a control pressed while a
    // box from the control before it is still covering the page reads as dead when it is not.
    if (await page.evaluate(() => window.__kw.openOverlays().length) > 0) {
        await settle(page);
    }
    const focused = await page.evaluate((index) => {
        const el = document.querySelector('[data-kw="' + index + '"]');
        if (!el || !el.focus) return false;
        el.focus();
        window.__kw.resetMutations();
        return document.activeElement === el;
    }, control.index);
    if (!focused) return false;

    const urlBefore = page.url();
    const overlaysBefore = await page.evaluate(() => window.__kw.openOverlays().length);
    const scrollBefore = await page.evaluate(() => window.__kw.scrollPosition());

    await page.keyboard.press(key === ' ' ? 'Space' : key);
    // TeaVM runs a component's handler on a green thread, so the effect is not on the very next
    // frame. Anything under about a third of a second reports working controls as dead.
    await page.waitForTimeout(400);

    const overlaysAfter = await page.evaluate(() => window.__kw.openOverlays().length);
    const scrollAfter = await page.evaluate(() => window.__kw.scrollPosition());
    const changed = await page.evaluate(() => window.__kw.mutationCount())
        + (page.url() === urlBefore ? 0 : 1)
        + (overlaysAfter === overlaysBefore ? 0 : 1)
        + (scrollAfter === scrollBefore ? 0 : 1);

    // Whatever this opened is closed again here; the overlay walk is where opening and closing is
    // actually judged.
    if (overlaysAfter > 0) {
        await settle(page);
    }
    return changed === 0;
}

// ---------------------------------------------------------------- run

async function run() {
    fs.rmSync(SHOTS, { recursive: true, force: true });
    fs.mkdirSync(SHOTS, { recursive: true });

    const browser = await chromium.launch({ headless: !HEADED });
    try {
        for (const width of WIDTHS) {
            console.log(`\n================ ${width} pixels wide ================`);
            const context = await browser.newContext({
                viewport: { width, height: width < 500 ? 800 : 1000 },
            });
            const page = await context.newPage();
            const errors = [];
            page.on('pageerror', (e) => errors.push(String(e)));

            await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });
            await page.addInitScript(IN_PAGE);
            await page.evaluate(IN_PAGE);
            await page.waitForTimeout(500);
            await page.evaluate(IN_PAGE);

            currentPage = `sign-in at ${width}px`;
            const signedIn = await signIn(page);
            await shot(page, `${width}-signed-in`);
            if (!signedIn) {
                await context.close();
                continue;
            }

            PROBE_LIMIT = width === FULL_WIDTH ? 12 : 4;
            for (const section of SECTIONS) {
                for (const label of section.pages) {
                    if (ONLY.length && !ONLY.some((o) => slug(label).includes(slug(o)))) continue;
                    if (!ONLY.length && width !== FULL_WIDTH && !AT_EVERY_WIDTH.has(label)) continue;
                    await settle(page);
                    await page.evaluate(IN_PAGE);
                    const opened = await openPage(page, section, label);
                    if (!opened) continue;
                    currentPage = `${label} at ${width}px`;
                    await page.evaluate(IN_PAGE);
                    await walkPage(page, currentPage);
                    await shot(page, `${width}-${label}`);
                    await settle(page);
                }
            }

            for (const error of errors) {
                currentPage = `${width}px`;
                finding('the page threw an error', error.split('\n')[0]);
            }
            await context.close();
        }
    } finally {
        await browser.close();
    }

    report();
}

function report() {
    const byKind = new Map();
    for (const f of findings) {
        if (!byKind.has(f.kind)) byKind.set(f.kind, []);
        byKind.get(f.kind).push(f);
    }
    const lines = [];
    lines.push('KEYBOARD-ONLY WALKTHROUGH — FINDINGS');
    lines.push('');
    for (const [kind, list] of byKind) {
        lines.push(`${kind} (${list.length})`);
        const seen = new Set();
        for (const f of list) {
            const line = `  ${f.page}: ${f.detail}`;
            if (seen.has(line)) continue;
            seen.add(line);
            lines.push(line);
        }
        lines.push('');
    }
    lines.push(`${findings.length} findings in total.`);
    const text = lines.join('\n');
    console.log('\n' + text);
    fs.writeFileSync(path.join(SHOTS, 'findings.txt'), text, 'utf8');
    console.log(`\nScreenshots and findings: ${SHOTS}`);
}

run().catch((e) => {
    console.error(e);
    process.exitCode = 1;
});
