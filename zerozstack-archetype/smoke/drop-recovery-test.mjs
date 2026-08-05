/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
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
// End-to-end proof of connection recovery (0.5.0), driven against the generated smoke app:
//   1. page works (tick advancing)
//   2. server killed -> built-in banner appears, page does not error
//   3. server restarted -> banner disappears, tick advances again (signal re-subscribed)
//
// Usage: node drop-recovery-test.mjs <path-to-smokeapp-server-module>
// The script starts and kills the server itself; do not start one beforehand.
import { chromium } from 'playwright';
import { spawn } from 'child_process';

const BASE = 'http://localhost:8100';
const SERVER_DIR = process.argv[2];
const CP_SEP = process.platform === 'win32' ? ';' : ':';

function startServer() {
  const child = spawn('java', ['-cp', `target/classes${CP_SEP}target/libs/*`, 'com.smoke.server.ServerApp'],
    { cwd: SERVER_DIR, stdio: 'ignore', detached: false });
  return child;
}

async function waitFor(page, fn, timeout, label) {
  try {
    await page.waitForFunction(fn, null, { timeout });
    return true;
  } catch {
    console.log(`TIMEOUT waiting for: ${label}`);
    return false;
  }
}

const tickValue = () => {
  const t = document.getElementById('tick');
  const m = (t?.textContent || '').match(/tick-(\d+)/);
  return m ? parseInt(m[1], 10) : -1;
};

let server = startServer();
await new Promise(r => setTimeout(r, 6000));

const browser = await chromium.launch();
const page = await browser.newPage();
const errors = [];
page.on('pageerror', e => errors.push(String(e).slice(0, 200)));

const checks = [];
const check = (name, ok, detail) => {
  checks.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

await page.goto(BASE, { waitUntil: 'networkidle' });

// 1. Baseline: the app works and ticks advance.
check('baseline: view rendered', await waitFor(page, () => !!document.querySelector('#app-root h1'), 15000, 'h1'));
await waitFor(page, () => (document.getElementById('tick')?.textContent || '').includes('tick-'), 15000, 'first tick');
const t1 = await page.evaluate(tickValue);
await page.waitForTimeout(2500);
const t2 = await page.evaluate(tickValue);
check('baseline: ticks advancing', t2 > t1 && t1 >= 0, `${t1} -> ${t2}`);

// 2. Kill the server: the banner must appear.
server.kill('SIGKILL');
const bannerShown = await waitFor(page,
  () => { const b = document.getElementById('zeroz4j-connection-banner'); return b && b.style.display !== 'none'; },
  20000, 'banner visible');
check('on drop: built-in banner appears', bannerShown);

// 3. Restart the server: banner must clear, ticks must resume.
server = startServer();
const bannerGone = await waitFor(page,
  () => { const b = document.getElementById('zeroz4j-connection-banner'); return b && b.style.display === 'none'; },
  40000, 'banner hidden');
check('on recovery: banner disappears', bannerGone);

// The restarted server's counter begins again at zero, so do not compare against pre-drop
// values: sample twice after recovery and require advancement between the two samples.
await page.waitForTimeout(2000);
const t3 = await page.evaluate(tickValue);
await page.waitForTimeout(3500);
const t4 = await page.evaluate(tickValue);
check('on recovery: signal updates flow again', t4 > t3 && t3 >= 0, `${t3} -> ${t4}`);

check('no page errors throughout', errors.length === 0, errors[0] || '');

await browser.close();
server.kill('SIGKILL');

const failed = checks.filter(c => !c).length;
console.log(`\n${checks.length - failed}/${checks.length} checks passed`);
process.exit(failed ? 1 : 0);
