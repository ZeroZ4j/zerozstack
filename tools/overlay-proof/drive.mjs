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
// keyboard is, what Escape does, what covers what.
//
//   node drive.mjs [--headed] [--playwright <dir>]
//
// Screenshots land in shots/. Exits non-zero on the first failed check.

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
const pwHome = pwIndex >= 0 ? args[pwIndex + 1] : 'G:/proj/trellis';

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
async function shot(page, name) {
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

async function run() {
    const browser = await chromium.launch({ headless: !headed });
    try {
        await drive(browser);
    } finally {
        await browser.close();
    }
    console.log(`
${checks - failures}/${checks} checks passed.`);
    console.log(`Screenshots: ${SHOTS}`);
    if (failures > 0) {
        process.exitCode = 1;
    }
}

async function drive(browser) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    const consoleErrors = [];
    page.on('pageerror', (e) => consoleErrors.push(String(e)));

    await page.goto(PAGE_URL);
    await page.waitForFunction(() => window.__proofReady === true);
    await page.waitForSelector('#open-default');
    // The stylesheet arrives from a CDN; without it every measurement below is of an unstyled page.
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.modal')).position === 'fixed');

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

    check('nothing threw in the browser', consoleErrors.length === 0, consoleErrors.join(' | '));

    await browser.close();

    console.log(`\n${checks - failures}/${checks} checks passed.`);
    console.log(`Screenshots: ${SHOTS}`);
    if (failures > 0) {
        process.exitCode = 1;
    }
}

run().catch((e) => {
    console.error(e);
    process.exitCode = 1;
});
