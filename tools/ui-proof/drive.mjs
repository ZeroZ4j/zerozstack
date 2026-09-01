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

// Drives a real headless Chrome against web/index.html, which is the real component library
// compiled to JavaScript. Every check here is something a rendering test cannot answer: where the
// keyboard is, what pressing a key does, what covers what, and whether a control has a name.
//
//   node drive.mjs [--headed] [--playwright <dir>]
//
// Screenshots land in shots/. Exits non-zero if any check fails.

import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SHOTS = path.join(HERE, 'shots');
const PAGE_URL = 'file:///' + path.join(HERE, 'web', 'index.html').replace(/\\/g, '/');

const args = process.argv.slice(2);
const headed = args.includes('--headed');
const pwIndex = args.indexOf('--playwright');
// Playwright lives in the archetype smoke folder, which is the one place in this checkout
// that installs it (npm install there; node_modules is ignored). It used to default to an
// unrelated project on the machine that happened to have one, which is not something a
// checkout can rely on.
const pwHome = pwIndex >= 0 ? args[pwIndex + 1]
    : path.join(HERE, '..', '..', 'zerozstack-archetype', 'smoke');

const require = createRequire(path.join(pwHome, 'package.json'));
const { chromium } = require('playwright');

fs.rmSync(SHOTS, { recursive: true, force: true });
fs.mkdirSync(SHOTS, { recursive: true });

let failures = 0;
let checks = 0;

function check(name, ok, detail) {
    checks++;
    if (ok) {
        console.log(`  PASS  ${name}`);
    } else {
        failures++;
        console.log(`  FAIL  ${name}${detail === undefined ? '' : ` — ${detail}`}`);
    }
}

let shotNumber = 0;
async function shot(page, name, bringIntoView) {
    // A screenshot is of the window, not of the page, and Tab has usually scrolled the window a
    // long way down by the time one is taken. Without this the picture of a section is a picture
    // of whatever happened to be under the last thing focused.
    if (bringIntoView) {
        await page.evaluate((selector) => {
            const el = document.querySelector(selector);
            if (el) { el.scrollIntoView({ block: 'start' }); }
        }, bringIntoView);
        await page.waitForTimeout(120);
    }
    shotNumber++;
    const file = path.join(SHOTS, `${String(shotNumber).padStart(2, '0')}-${name}.png`);
    await page.screenshot({ path: file });
    return file;
}

/** Where the keyboard is, and whether that is inside the given element. */
async function focusReport(page, containerId) {
    return page.evaluate((id) => {
        const active = document.activeElement;
        const container = document.getElementById(id);
        return {
            id: active ? active.id : null,
            tag: active ? active.tagName : null,
            inside: !!(container && active && container.contains(active)),
        };
    }, containerId);
}

// =====================================================================
// The keyboard section
// =====================================================================

// Every control the keyboard section puts on the page, and how it is meant to be worked.
//
//   activate  a button, a box, an entry: Tab to it, press Enter, press Space
//   drag      a handle, a divider, a canvas, a scrubbed strip: Tab to it, press the arrow keys
//   value     a field whose keyboard is typing, not Enter and Space
//   menukey   a right-click menu: the keyboard equivalent is the Menu key, not Enter
//   native    a control the browser itself operates, worked with the gesture the browser expects
//
// Dialog, Drawer and Dropdown are not here. They live in the overlays section and are driven
// there, at their own ids, so a second copy would prove nothing the first does not.
const KEYBOARD_CONTROLS = [
    { id: 'kb-button', what: 'Button', how: 'activate' },
    { id: 'kb-checkbox', what: 'Checkbox', how: 'activate' },
    { id: 'kb-codeblock', what: 'CodeBlock (copy)', how: 'activate' },
    { id: 'kb-contextmenu', what: 'ContextMenu (target)', how: 'menukey' },
    { id: 'kb-diffview', what: 'DiffView (file header)', how: 'activate' },
    // Opening the operating system's file chooser is meant to take the keyboard away.
    { id: 'kb-fileinput', what: 'FileInput', how: 'activate', movesTheKeyboard: true },
    { id: 'kb-fileupload', what: 'FileUpload (drop box)', how: 'activate', movesTheKeyboard: true },
    { id: 'kb-lanetimeline', what: 'LaneTimeline', how: 'drag',
        // Every arrow key redraws the strip, which throws away the element the id was on. The
        // component puts the keyboard on the new one; the driver finds it the same way.
        selector: '[data-kb-row="kb-lanetimeline"] [role="slider"]' },
    { id: 'kb-login', what: 'Login (sign in)', how: 'activate' },
    { id: 'kb-menu-item', what: 'Menu (entry)', how: 'activate' },
    { id: 'kb-propertygrid', what: 'PropertyGrid (copy)', how: 'activate' },
    { id: 'kb-radiobuttongroup', what: 'RadioButtonGroup', how: 'activate' },
    { id: 'kb-range', what: 'Range', how: 'value', key: 'ArrowRight' },
    { id: 'kb-rating', what: 'Rating (a star)', how: 'activate' },
    { id: 'kb-resizer', what: 'Resizer', how: 'drag' },
    { id: 'kb-select', what: 'Select', how: 'native', tag: 'SELECT' },
    { id: 'kb-splitpane', what: 'SplitPane (divider)', how: 'drag' },
    { id: 'kb-svgcanvas', what: 'SvgCanvas', how: 'drag' },
    { id: 'kb-swap', what: 'Swap', how: 'activate' },
    { id: 'kb-tab', what: 'Tab', how: 'activate' },
    { id: 'kb-textarea', what: 'TextArea', how: 'value', key: 'x' },
    { id: 'kb-textfield', what: 'TextField', how: 'value', key: 'x' },
    { id: 'kb-themecontroller', what: 'ThemeController', how: 'activate' },
];

// The three overlays that are driven at their own ids, in the overlays section.
const REUSED_OVERLAY_CONTROLS = [
    { id: 'open-default', what: 'Dialog (opener)', how: 'activate', opens: 'dlg-default' },
    { id: 'open-drawer', what: 'Drawer (opener)', how: 'activate', opens: 'drawer-1' },
    { id: 'dd-1-summary', what: 'Dropdown (button)', how: 'activate', opens: 'dd-1' },
];

/**
 * The name a screen reader would announce, worked out in the page.
 *
 * aria-labelledby text, else aria-label, else the label the control is associated with - by
 * `for`, or by being inside one - else the words in the control itself.
 */
const ACCESSIBLE_NAME = `(el) => {
    if (!el) { return ''; }
    const by = el.getAttribute && el.getAttribute('aria-labelledby');
    if (by) {
        const text = by.split(/\\s+/).map((id) => {
            const n = document.getElementById(id);
            return n ? (n.innerText || n.textContent || '') : '';
        }).join(' ').replace(/\\s+/g, ' ').trim();
        if (text) { return text; }
    }
    const label = el.getAttribute && el.getAttribute('aria-label');
    if (label && label.trim()) { return label.trim(); }
    if (el.id) {
        const l = document.querySelector('label[for="' + el.id.replace(/"/g, '\\\\"') + '"]');
        if (l) {
            const t = (l.innerText || l.textContent || '').replace(/\\s+/g, ' ').trim();
            if (t) { return t; }
        }
    }
    const wrapping = el.closest ? el.closest('label') : null;
    if (wrapping) {
        const t = (wrapping.innerText || wrapping.textContent || '').replace(/\\s+/g, ' ').trim();
        if (t) { return t; }
    }
    return (el.innerText || '').replace(/\\s+/g, ' ').trim();
}`;

/** Puts the keyboard nowhere, so the next Tab starts at the first control on the page. */
async function toTheTop(page) {
    await page.evaluate(() => {
        if (document.activeElement && document.activeElement.blur) {
            document.activeElement.blur();
        }
    });
}

/** Where the keyboard is now, with the name a screen reader would announce for it. */
async function here(page) {
    return page.evaluate((nameFn) => {
        const el = document.activeElement;
        const accessibleName = new Function('return ' + nameFn)();
        return {
            id: el ? el.id : null,
            tag: el ? el.tagName : null,
            role: el && el.getAttribute ? el.getAttribute('role') : null,
            name: accessibleName(el),
            inKeyboardSection: !!(el && el.closest && el.closest('#section-keyboard')),
        };
    }, ACCESSIBLE_NAME);
}

/**
 * Walks the whole page with the Tab key and writes down every stop.
 *
 * Nothing here clicks. That is the point: a click puts the keyboard wherever it likes, which
 * would hide exactly the fault this is looking for - a control the Tab key can never reach.
 */
async function tabWalk(page, maxSteps) {
    await toTheTop(page);
    const stops = [];
    let first = null;
    for (let i = 0; i < maxSteps; i++) {
        await page.keyboard.press('Tab');
        const where = await here(page);
        if (first !== null && where.id && where.id === first) {
            break;   // wrapped round to the beginning
        }
        if (first === null && where.id) {
            first = where.id;
        }
        stops.push(where);
    }
    return stops;
}

/**
 * Puts the keyboard on one control using nothing but the Tab key.
 *
 * The walk already counted how many presses that takes, so the presses are made blind and the
 * arrival is checked once. When the count is wrong - a control that moved, or one that was never
 * reached - it falls back to stepping and looking.
 */
async function tabTo(page, target, knownIndex) {
    const wanted = target.selector
        ? { selector: target.selector }
        : { id: target.id };
    async function arrived() {
        return page.evaluate((w) => {
            const el = document.activeElement;
            if (!el) { return false; }
            return w.selector ? el.matches(w.selector) : el.id === w.id;
        }, wanted);
    }

    if (knownIndex >= 0) {
        await toTheTop(page);
        for (let i = 0; i <= knownIndex; i++) {
            await page.keyboard.press('Tab');
        }
        if (await arrived()) {
            return true;
        }
    }
    await toTheTop(page);
    for (let i = 0; i < 260; i++) {
        await page.keyboard.press('Tab');
        if (await arrived()) {
            return true;
        }
    }
    return false;
}

async function firedText(page, id) {
    return page.evaluate((x) => {
        const el = document.getElementById(x + '-fired');
        return el ? el.textContent : null;
    }, id);
}

/** Everything about a drag surface that a key press could move. */
async function positionOf(page, target) {
    return page.evaluate((w) => {
        const el = w.selector ? document.querySelector(w.selector) : document.getElementById(w.id);
        if (!el) { return null; }
        const inner = el.querySelector('g') || el.querySelector('svg');
        return {
            valuenow: el.getAttribute('aria-valuenow'),
            valuetext: el.getAttribute('aria-valuetext'),
            left: Math.round(el.getBoundingClientRect().left),
            transform: inner ? inner.getAttribute('transform') : null,
        };
    }, target.selector ? { selector: target.selector } : { id: target.id });
}

async function driveKeyboard(page) {
    console.log('\n=====================================================================');
    console.log('KEYBOARD: every control, worked with nothing but the keyboard');
    console.log('=====================================================================');

    console.log('\nK1. Tab from the start of the page, and write down every stop');
    const stops = await tabWalk(page, 320);
    const indexOfId = new Map();
    stops.forEach((s, i) => {
        if (s.id && !indexOfId.has(s.id)) {
            indexOfId.set(s.id, i);
        }
    });

    const startAt = stops.findIndex((s) => s.id === 'kb-before');
    const endAt = stops.findIndex((s) => s.id === 'kb-after');
    check('Tab reaches the button before the keyboard section', startAt >= 0);
    check('Tab reaches the button after the keyboard section', endAt >= 0);

    const inSection = startAt >= 0 && endAt > startAt ? stops.slice(startAt + 1, endAt) : [];
    console.log(`\n  Tab landed on ${inSection.length} things between #kb-before and #kb-after:`);
    inSection.forEach((s, i) => {
        console.log(`    ${String(i + 1).padStart(3)}. ${(s.id || '(no id)').padEnd(28)}`
            + `<${s.tag.toLowerCase()}${s.role ? ' role=' + s.role : ''}>`
            + `  name: ${s.name ? '"' + s.name + '"' : '*** NONE ***'}`);
    });

    const reached = new Set(inSection.map((s) => s.id).filter(Boolean));
    const neverReached = KEYBOARD_CONTROLS.filter((c) => !reached.has(c.id));
    console.log('\n  ------------------------------------------------------------------');
    if (neverReached.length === 0) {
        console.log('  Tab reached every control in the keyboard section.');
    } else {
        console.log(`  Tab NEVER REACHED ${neverReached.length} control(s):`);
        neverReached.forEach((c) => console.log(`    ${c.id.padEnd(28)}${c.what}`));
    }
    console.log('  ------------------------------------------------------------------');
    check('every control in the keyboard section is reachable with Tab',
        neverReached.length === 0,
        neverReached.map((c) => c.id).join(', '));
    console.log('  shot: ' + (await shot(page, 'keyboard-section', '#section-keyboard')));

    console.log('\nK2. Everything the keyboard lands on has a name');
    const nameless = inSection.filter((s) => !s.name);
    if (nameless.length === 0) {
        console.log('  Everything Tab reached announces itself.');
    } else {
        console.log(`  ${nameless.length} thing(s) the keyboard reaches announce nothing:`);
        nameless.forEach((s) => console.log(`    ${(s.id || '(no id)').padEnd(28)}`
            + `<${s.tag.toLowerCase()}${s.role ? ' role=' + s.role : ''}>`));
    }
    for (const c of KEYBOARD_CONTROLS) {
        const stop = inSection.find((s) => s.id === c.id);
        if (!stop) {
            continue;   // already reported as unreachable
        }
        check(`${c.what} has a name`, !!stop.name,
            'a screen reader would announce only "' + stop.tag.toLowerCase() + '"');
    }

    console.log('\nK3. Enter and Space actually do something');
    for (const c of KEYBOARD_CONTROLS.filter((x) => x.how === 'activate')) {
        const landed = await tabTo(page, c, indexOfId.has(c.id) ? indexOfId.get(c.id) : -1);
        if (!landed) {
            check(`${c.what} answers Enter or Space`, false, 'Tab never got to it');
            continue;
        }
        const before = await firedText(page, c.id);
        await page.keyboard.press('Enter');
        await page.waitForTimeout(120);
        const afterEnter = await firedText(page, c.id);
        // Pressing a control must not throw the keyboard away. Somebody who cannot use a mouse has
        // no cheap way back: focus on the body means Tab starts again at the top of the page.
        if (c.movesTheKeyboard !== true) {
            const stillThere = await page.evaluate((id) =>
                document.activeElement && document.activeElement.id === id, c.id);
            check(`${c.what} keeps the keyboard on it after Enter`, stillThere,
                'the keyboard is now on '
                    + (await page.evaluate(() => document.activeElement
                        ? (document.activeElement.id || document.activeElement.tagName) : 'nothing')));
            if (!stillThere) {
                await tabTo(page, c, indexOfId.has(c.id) ? indexOfId.get(c.id) : -1);
            }
        }
        await page.keyboard.press('Space');
        await page.waitForTimeout(120);
        const afterSpace = await firedText(page, c.id);
        const enterDid = afterEnter !== before;
        const spaceDid = afterSpace !== afterEnter;
        check(`${c.what} answers Enter or Space`, enterDid || spaceDid,
            'neither key did anything the control noticed');
        console.log(`        Enter: ${enterDid ? 'yes' : 'no '}   `
            + `Space: ${spaceDid ? 'yes' : 'no '}   (report says "${afterSpace}")`);
        // Escape anything a press may have opened, so the next Tab starts from a quiet page.
        await page.keyboard.press('Escape');
        await page.waitForTimeout(80);
    }
    console.log('  shot: ' + (await shot(page, 'keyboard-after-activation', '#section-keyboard')));

    console.log('\nK4. A right-click menu answers the Menu key, the keyboard right click');
    for (const c of KEYBOARD_CONTROLS.filter((x) => x.how === 'menukey')) {
        const landed = await tabTo(page, c, indexOfId.has(c.id) ? indexOfId.get(c.id) : -1);
        if (!landed) {
            check(`${c.what} answers the Menu key`, false, 'Tab never got to it');
            continue;
        }
        const start = await firedText(page, c.id);
        await page.keyboard.press('Enter');
        await page.keyboard.press('Space');
        await page.waitForTimeout(120);
        const afterPlainKeys = await firedText(page, c.id);
        await page.keyboard.press('ContextMenu');
        await page.waitForTimeout(200);
        const afterMenuKey = await firedText(page, c.id);
        check(`${c.what} answers the Menu key`, afterMenuKey !== afterPlainKeys,
            'the keyboard has no way to open this menu at all');
        console.log(`        Enter and Space: ${afterPlainKeys !== start ? 'yes' : 'no '}`
            + `   Menu key: ${afterMenuKey !== afterPlainKeys ? 'yes' : 'no '}`);
        await page.keyboard.press('Escape');
        await page.waitForTimeout(80);
    }

    console.log('\nK5. A control the browser itself works from the keyboard');
    for (const c of KEYBOARD_CONTROLS.filter((x) => x.how === 'native')) {
        // daisyUI styles a select, and styling one in Chrome turns on `appearance: base-select`,
        // where the arrow keys no longer step the value. They open the list, move a highlight
        // inside it, and Enter takes the highlighted choice. So the gesture is three presses,
        // not one - and a driver that presses ArrowDown once concludes the control is dead when
        // it is only waiting for the other two.
        const start = await page.evaluate((id) => {
            const el = document.getElementById(id);
            if (el) el.focus();
            return el ? { tag: el.tagName, value: el.value, options: el.options.length } : null;
        }, c.id);
        check(`${c.what} is the browser's own <${c.tag.toLowerCase()}>`,
            !!start && start.tag === c.tag,
            start ? 'it is a <' + start.tag.toLowerCase() + '>' : 'it is not on the page');
        if (!start || start.tag !== c.tag) {
            continue;
        }
        await page.keyboard.press('ArrowDown');     // opens the list
        await page.waitForTimeout(250);
        await page.keyboard.press('ArrowDown');     // moves the highlight down one
        await page.waitForTimeout(250);
        await page.keyboard.press('Enter');         // takes that choice
        await page.waitForTimeout(300);
        const after = await page.evaluate((id) => {
            const el = document.getElementById(id);
            const span = document.getElementById(id + '-fired');
            return { value: el.value, fired: span ? span.textContent : '' };
        }, c.id);
        check(`${c.what} can be changed with nothing but the keyboard`,
            after.value !== start.value,
            `it was "${start.value}" and is still "${after.value}" after ArrowDown, ArrowDown, Enter`);
        check(`${c.what} tells the application it changed`, after.fired !== '',
            'the value moved and no change event reached the component');
        console.log(`        ${start.options} choices; "${start.value}" -> "${after.value}"`
            + ' with ArrowDown, ArrowDown, Enter');
    }

    console.log('\nK6. A field whose keyboard is typing, not Enter');
    for (const c of KEYBOARD_CONTROLS.filter((x) => x.how === 'value')) {
        const landed = await tabTo(page, c, indexOfId.has(c.id) ? indexOfId.get(c.id) : -1);
        if (!landed) {
            check(`${c.what} answers ${c.key}`, false, 'Tab never got to it');
            continue;
        }
        const before = await firedText(page, c.id);
        await page.keyboard.press(c.key);
        await page.waitForTimeout(120);
        const after = await firedText(page, c.id);
        check(`${c.what} answers ${c.key}`, after !== before,
            'the key changed nothing the control noticed');
    }

    console.log('\nK7. Anything you drag also answers the arrow keys');
    for (const c of KEYBOARD_CONTROLS.filter((x) => x.how === 'drag')) {
        const landed = await tabTo(page, c, indexOfId.has(c.id) ? indexOfId.get(c.id) : -1);
        if (!landed) {
            check(`${c.what} moves with the arrow keys`, false, 'Tab never got to it');
            continue;
        }
        // Move off whatever limit the surface starts on before measuring. A timeline starts at
        // the live end of the run, and a control already at its limit that correctly refuses to
        // go further would otherwise read as one that ignores the key. Not a softer check: both
        // directions still have to move it.
        for (let i = 0; i < 4; i++) {
            await page.keyboard.press('ArrowLeft');
        }
        await page.waitForTimeout(150);
        const before = await positionOf(page, c);
        for (let i = 0; i < 6; i++) {
            await page.keyboard.press('ArrowRight');
        }
        await page.waitForTimeout(150);
        const afterRight = await positionOf(page, c);
        for (let i = 0; i < 3; i++) {
            await page.keyboard.press('ArrowLeft');
        }
        await page.waitForTimeout(150);
        const afterLeft = await positionOf(page, c);

        const moved = (a, b) => a !== null && b !== null
            && (a.valuenow !== b.valuenow || a.left !== b.left || a.transform !== b.transform);
        check(`${c.what} moves with ArrowRight`, moved(before, afterRight),
            `nothing changed: ${JSON.stringify(before)}`);
        check(`${c.what} moves back with ArrowLeft`, moved(afterRight, afterLeft),
            `nothing changed: ${JSON.stringify(afterRight)}`);
        console.log(`        was ${JSON.stringify(before)}`);
        console.log(`        now ${JSON.stringify(afterLeft)}`);
        console.log('        shot: ' + (await shot(page, 'arrow-keys-' + c.id, '[data-kb-row="' + c.id + '"]')));
    }

    console.log('\nK8. The three overlays, driven where they live');
    // Give the dropdown's button an id of its own, so the walk and the driver can name it. It is
    // the <summary> daisyUI wants, and a <summary> takes no id from the component.
    await page.evaluate(() => {
        const s = document.querySelector('#dd-1 summary');
        if (s) { s.id = 'dd-1-summary'; }
    });
    const stopsAgain = await tabWalk(page, 320);
    const indexAgain = new Map();
    stopsAgain.forEach((s, i) => {
        if (s.id && !indexAgain.has(s.id)) {
            indexAgain.set(s.id, i);
        }
    });
    for (const c of REUSED_OVERLAY_CONTROLS) {
        const stop = stopsAgain.find((s) => s.id === c.id);
        check(`${c.what} is reachable with Tab`, !!stop, 'Tab never reached #' + c.id);
        if (stop) {
            check(`${c.what} has a name`, !!stop.name,
                'a screen reader would announce only "' + stop.tag.toLowerCase() + '"');
        }
        const landed = await tabTo(page, c, indexAgain.has(c.id) ? indexAgain.get(c.id) : -1);
        if (!landed) {
            check(`${c.what} opens from the keyboard`, false, 'Tab never got to it');
            continue;
        }
        await page.keyboard.press('Enter');
        await page.waitForTimeout(250);
        const opened = await page.evaluate((id) => {
            const el = document.getElementById(id);
            if (!el) { return false; }
            if (el.tagName === 'DIALOG' || el.tagName === 'DETAILS') { return !!el.open; }
            const toggle = document.getElementById('zeroz-' + id);
            return toggle ? !!toggle.checked : false;
        }, c.opens);
        check(`${c.what} opens from the keyboard`, opened, 'Enter did not open #' + c.opens);
        await page.keyboard.press('Escape');
        await page.waitForTimeout(250);
    }
    console.log('  shot: ' + (await shot(page, 'keyboard-overlays', '#section-overlays')));
}

// =====================================================================
// The fields section, which checks itself and reports in the page
// =====================================================================

/**
 * Turns every verdict line the fields section wrote into one check of ours.
 *
 * The page writes PASS|name|detail or FAIL|name|detail into a hidden <pre>, because the questions
 * it asks - is this sentence part of the text on the screen, does clicking the caption focus the
 * field - can only be asked from inside the page.
 */
async function reportFields(page) {
    console.log('\n=====================================================================');
    console.log('FIELDS: every form field says what it is, and says why it refused');
    console.log('=====================================================================\n');
    const lines = await page.evaluate(() =>
        Array.from(document.querySelectorAll('#proof-results .proof-line'))
             .map((el) => el.textContent));
    check('the fields section produced verdicts', lines.length > 0);
    check('the fields section ran to the end', lines.includes('PASS|harness completed|'),
        'it stopped part way through');
    for (const line of lines) {
        const bar = line.indexOf('|');
        const bar2 = line.indexOf('|', bar + 1);
        const verdict = line.substring(0, bar);
        const name = line.substring(bar + 1, bar2 < 0 ? line.length : bar2);
        const detail = bar2 < 0 ? '' : line.substring(bar2 + 1);
        if (name === 'harness completed') {
            continue;
        }
        check(name, verdict === 'PASS', detail);
    }
    console.log('  shot: ' + (await shot(page, 'fields-section', '#section-fields')));
}

// =====================================================================

async function run() {
    const browser = await chromium.launch({ headless: !headed });
    try {
        await drive(browser);
    } finally {
        await browser.close();
    }
    console.log(`\n${checks - failures}/${checks} checks passed.`);
    console.log(`Screenshots: ${SHOTS}`);
    if (failures > 0) {
        process.exitCode = 1;
    }
}

async function drive(browser) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    const consoleErrors = [];
    page.on('pageerror', (e) => consoleErrors.push(String(e)));
    // A file input and a drop box both open the operating system's file chooser when pressed.
    // Listening for it keeps Playwright in charge of the dialog instead of the machine.
    page.on('filechooser', () => { });

    await page.goto(PAGE_URL);
    await page.waitForFunction(() => window.__proofReady === true);
    await page.waitForSelector('#open-default');
    // The stylesheet arrives from a CDN; without it every measurement below is of an unstyled page.
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.modal')).position === 'fixed');
    // The fields section runs itself a browser turn at a time, because a typed character is not
    // answered in the same breath. Nothing else may start until it has finished moving focus about.
    await page.waitForFunction(() => Array.from(
        document.querySelectorAll('#proof-results .proof-line'))
        .some((el) => el.textContent === 'PASS|harness completed|'), null, { timeout: 60000 });

    await driveKeyboard(page);

    console.log('\n=====================================================================');
    console.log('OVERLAYS: what is on top, and where the keyboard is');
    console.log('=====================================================================');

    console.log('\n1. A dialog opens, and the keyboard goes into it');
    await page.click('#open-default');
    await page.waitForTimeout(150);
    let modal = await page.evaluate(() => document.getElementById('dlg-default').matches(':modal'));
    check('the dialog is in the browser top layer (:modal)', modal);
    let focus = await focusReport(page, 'dlg-default');
    check('the keyboard is inside the dialog', focus.inside, `it is on ${focus.tag}#${focus.id}`);
    console.log('  shot: ' + (await shot(page, 'dialog-open-focus-inside')));

    console.log('\n2. Tab goes round the dialog and never reaches the page behind');
    const walked = [];
    let escaped = false;
    for (let i = 0; i < 12; i++) {
        await page.keyboard.press('Tab');
        const where = await page.evaluate(() => {
            const a = document.activeElement;
            const d = document.getElementById('dlg-default');
            return {
                id: a ? a.id : null,
                tag: a ? a.tagName : null,
                inside: !!(d && a && d.contains(a)),
            };
        });
        walked.push(where);
        // Chrome parks focus on the document body for one step as it wraps round the end of a modal
        // dialog. That is the wrap, not an escape: no control on the page behind is ever reachable.
        if (!where.inside && where.tag !== 'BODY' && where.tag !== 'HTML') {
            escaped = true;
        }
    }
    const walkedIds = walked.map((w) => w.id || w.tag);
    check('twelve Tabs never reach a control outside the dialog', !escaped,
        `landed on ${walkedIds.join(', ')}`);
    check('the page button behind was never reached', !walked.some((w) => w.id === 'page-button'));
    check('Tab visited more than one control inside', new Set(walkedIds).size > 1, walkedIds.join(', '));
    console.log('  shot: ' + (await shot(page, 'dialog-tab-cycles-inside')));

    console.log('\n3. Escape closes it, and the keyboard goes back to the button that opened it');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    let open = await page.evaluate(() => document.getElementById('dlg-default').open);
    check('the dialog is closed', !open);
    focus = await focusReport(page, 'dlg-default');
    check('the keyboard is back on the opening button', focus.id === 'open-default',
        `it is on ${focus.tag}#${focus.id}`);
    console.log('  shot: ' + (await shot(page, 'dialog-closed-focus-restored')));

    console.log('\n4. A click on the dim closes it when it is allowed to');
    await page.click('#open-default');
    await page.waitForTimeout(150);
    await page.mouse.click(8, 8);
    await page.waitForTimeout(200);
    open = await page.evaluate(() => document.getElementById('dlg-default').open);
    check('clicking the dim closed the default dialog', !open);
    console.log('  shot: ' + (await shot(page, 'dim-click-closes')));

    console.log('\n5. ...and does not when it is refused');
    await page.click('#open-strict');
    await page.waitForTimeout(150);
    await page.mouse.click(8, 8);
    await page.waitForTimeout(200);
    open = await page.evaluate(() => document.getElementById('dlg-strict').open);
    check('clicking the dim left the refusing dialog open', open);
    console.log('  shot: ' + (await shot(page, 'dim-click-refused')));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);

    console.log('\n5b. A dialog the browser does not own still moves the keyboard, and gives it back');
    await page.click('#open-unowned');
    await page.waitForTimeout(300);
    focus = await focusReport(page, 'dlg-unowned');
    check('the keyboard is inside the unowned dialog', focus.inside, `it is on ${focus.tag}#${focus.id}`);
    console.log('  shot: ' + (await shot(page, 'unowned-dialog-focus-inside')));
    await page.click('#dlg-unowned-close');
    await page.waitForTimeout(300);
    focus = await focusReport(page, 'dlg-unowned');
    check('the keyboard is back on the button that opened it', focus.id === 'open-unowned',
        `it is on ${focus.tag}#${focus.id}`);

    console.log('\n6. The dialog has a name, taken from its own heading');
    const named = await page.evaluate(() => {
        const d = document.getElementById('dlg-default');
        const by = d.getAttribute('aria-labelledby');
        const h = by ? document.getElementById(by) : null;
        return { by, text: h ? h.textContent : null, tag: h ? h.tagName : null };
    });
    check('the dialog points at a heading for its name', !!named.by);
    check('the heading holds the title text', named.text === 'Confirm the change', named.text);
    check('the heading is a real heading element', named.tag === 'H2', named.tag);

    console.log('\n7. A dialog opened over a toast and an open menu is above both');
    await page.click('#show-toast');
    await page.waitForSelector('#toast-1');
    await page.evaluate(() => { document.getElementById('dd-1').open = true; });
    await page.waitForTimeout(150);
    console.log('  shot: ' + (await shot(page, 'toast-and-menu-before-dialog')));
    await page.click('#open-default');
    await page.waitForTimeout(200);
    const covered = await page.evaluate(() => {
        function centreHit(id) {
            const box = document.getElementById(id).getBoundingClientRect();
            const hit = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
            const dialog = document.getElementById('dlg-default');
            return {
                hit: hit ? (hit.id || hit.tagName + '.' + hit.className) : null,
                dialogWins: !!(hit && (hit === dialog || dialog.contains(hit))),
            };
        }
        return { toast: centreHit('toast-1'), menu: centreHit('dd-1') };
    });
    check('the dialog covers the toast', covered.toast.dialogWins, `the point hits ${covered.toast.hit}`);
    check('the dialog covers the open menu', covered.menu.dialogWins, `the point hits ${covered.menu.hit}`);
    console.log('  shot: ' + (await shot(page, 'dialog-above-toast-and-menu')));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);

    console.log('\n8. Every overlay is on its named layer');
    await page.evaluate(() => { document.getElementById('dd-1').open = true; });
    if (!(await page.evaluate(() => !!document.getElementById('toast-1')))) {
        await page.click('#show-toast');
        await page.waitForSelector('#toast-1');
    }
    const layers = await page.evaluate(() => {
        function read(selector) {
            const el = document.querySelector(selector);
            if (!el) return null;
            return { cls: el.className, z: getComputedStyle(el).zIndex };
        }
        return {
            toast: read('#toast-1'),
            dropdown: read('#dd-1 .dropdown-content'),
            drawerSide: read('#drawer-1 .drawer-side'),
            tooltip: read('#tip-1'),
            dialog: read('#dlg-default'),
        };
    });
    check('the toast is on the toast layer', layers.toast.cls.includes('zz-layer-toast') && layers.toast.z === '1300', JSON.stringify(layers.toast));
    check('the menu is on the dropdown layer', layers.dropdown.cls.includes('zz-layer-dropdown') && layers.dropdown.z === '1100', JSON.stringify(layers.dropdown));
    check('the drawer panel is on the overlay layer', layers.drawerSide.cls.includes('zz-layer-overlay') && layers.drawerSide.z === '1200', JSON.stringify(layers.drawerSide));
    // A tooltip wraps the control it belongs to, so it is ordinary page content until the pointer
    // arrives. Leaving it on the top layer permanently would float that control over every drawer.
    check('a resting tooltip is ordinary page content',
        !layers.tooltip.cls.includes('zz-layer-'), JSON.stringify(layers.tooltip));
    await page.hover('#tip-1-target');
    await page.waitForTimeout(300);
    const tipUp = await page.evaluate(() => {
        const el = document.getElementById('tip-1');
        return { cls: el.className, z: getComputedStyle(el).zIndex };
    });
    check('a showing tooltip is on the tooltip layer',
        tipUp.cls.includes('zz-layer-tooltip') && tipUp.z === '1400', JSON.stringify(tipUp));
    await page.mouse.move(1200, 700);
    await page.waitForTimeout(200);
    check('a message is above a menu', Number(layers.toast.z) > Number(layers.dropdown.z));
    check('a menu is below a drawer', Number(layers.dropdown.z) < Number(layers.drawerSide.z));

    console.log('\n9. The drawer opens, holds the keyboard, and Escape closes it');
    await page.click('#open-drawer');
    await page.waitForTimeout(400);
    focus = await page.evaluate(() => {
        const a = document.activeElement;
        const panel = document.querySelector('#drawer-1 .drawer-side [role=dialog]');
        return { id: a ? a.id : null, inside: !!(panel && a && panel.contains(a)) };
    });
    check('the keyboard is inside the drawer panel', focus.inside, `it is on #${focus.id}`);
    const drawerWalk = [];
    for (let i = 0; i < 6; i++) {
        await page.keyboard.press('Tab');
        drawerWalk.push(await page.evaluate(() => {
            const a = document.activeElement;
            const panel = document.querySelector('#drawer-1 .drawer-side [role=dialog]');
            return { id: a ? a.id : null, inside: !!(panel && a && panel.contains(a)) };
        }));
    }
    check('six Tabs never leave the drawer panel', drawerWalk.every((w) => w.inside),
        drawerWalk.map((w) => w.id).join(', '));
    console.log('  shot: ' + (await shot(page, 'drawer-open-traps-keyboard')));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);
    const drawerOpen = await page.evaluate(() => document.getElementById('zeroz-drawer-1').checked);
    check('Escape closed the drawer', !drawerOpen);
    focus = await focusReport(page, 'drawer-1');
    check('the keyboard is back on the button that opened the drawer', focus.id === 'open-drawer',
        `it is on #${focus.id}`);
    console.log('  shot: ' + (await shot(page, 'drawer-closed-focus-restored')));

    console.log('\n10. The menu closes on Escape and gives the keyboard back');
    await page.click('#dd-1 summary');
    await page.waitForTimeout(150);
    check('the menu is open', await page.evaluate(() => document.getElementById('dd-1').open));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(150);
    check('Escape closed the menu', !(await page.evaluate(() => document.getElementById('dd-1').open)));
    console.log('  shot: ' + (await shot(page, 'menu-escape-closes')));

    console.log('\n11. Escape hides a tooltip that is showing');
    await page.hover('#tip-1-target');
    await page.waitForTimeout(400);
    let tipShown = await page.evaluate(() => document.getElementById('tip-1').hasAttribute('data-tip'));
    check('the tip has words to show', tipShown);
    console.log('  shot: ' + (await shot(page, 'tooltip-showing')));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    tipShown = await page.evaluate(() => document.getElementById('tip-1').hasAttribute('data-tip'));
    check('Escape took the tip away', !tipShown);
    console.log('  shot: ' + (await shot(page, 'tooltip-dismissed')));

    console.log('\n12. Escape takes a message off the page');
    if (!(await page.evaluate(() => !!document.getElementById('toast-1')))) {
        await page.click('#show-toast');
        await page.waitForSelector('#toast-1');
    }
    check('a message is on the page', await page.evaluate(() => !!document.getElementById('toast-1')));
    console.log('  shot: ' + (await shot(page, 'toast-showing')));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
    check('Escape took the message away',
        !(await page.evaluate(() => !!document.getElementById('toast-1'))));
    console.log('  shot: ' + (await shot(page, 'toast-escape-removed')));

    console.log('\n13. Sizing is honoured on a wide window');
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.click('#open-wide');
    await page.waitForTimeout(250);
    // offsetWidth, not getBoundingClientRect: the panel scales up as it opens, and a rectangle
    // measured mid-animation is the scaled one — 891 for a panel that is really 896.
    let panel = await page.evaluate(() => {
        const box = document.querySelector('#dlg-wide .modal-box');
        return { w: box.offsetWidth, window: window.innerWidth };
    });
    check('the wide panel is the 56rem it was asked for', Math.abs(panel.w - 896) <= 2,
        `it is ${panel.w}px`);
    console.log('  shot: ' + (await shot(page, 'sizing-wide-viewport')));

    console.log('\n14. ...and the same panel still fits a narrow window');
    await page.setViewportSize({ width: 380, height: 720 });
    await page.waitForTimeout(250);
    panel = await page.evaluate(() => {
        const box = document.querySelector('#dlg-wide .modal-box');
        return { w: box.offsetWidth, window: window.innerWidth };
    });
    check('the panel is no wider than the window', panel.w <= panel.window,
        `panel ${panel.w}px, window ${panel.window}px`);
    console.log('  shot: ' + (await shot(page, 'sizing-narrow-viewport')));
    await page.keyboard.press('Escape');
    await page.setViewportSize({ width: 1600, height: 900 });

    await reportFields(page);

    check('nothing threw in the browser', consoleErrors.length === 0, consoleErrors.join(' | '));
}

run().catch((e) => {
    console.error(e);
    process.exitCode = 1;
});
