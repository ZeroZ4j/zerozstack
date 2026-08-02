// End-to-end smoke test for a project generated from zerozstack-archetype.
// Asserts the three release-blocking defects are actually fixed at runtime:
//   1. a shared @DataModel signal arrives  → the annotation processor ran
//   2. the page renders                    → TeaVM main() was invoked
//   3. an RMI call returns                 → the server bean was discovered
import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8100';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1100, height: 700 } });

const consoleErrors = [];
const sockets = [];
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)); });
page.on('pageerror', e => consoleErrors.push('PAGEERROR: ' + String(e).slice(0, 300)));
page.on('websocket', ws => sockets.push(ws.url()));

await page.goto(BASE, { waitUntil: 'networkidle' });

const checks = [];
const check = (name, ok, detail) => {
  checks.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};

// Defect 2: main() invoked. Without the bootstrap the page keeps its "Loading…" placeholder.
let heading = '';
try {
  await page.waitForSelector('#app-root h1', { timeout: 15000 });
  heading = (await page.locator('#app-root h1').textContent()).trim();
} catch { /* left empty — reported below */ }
check('defect 2: TeaVM main() ran and the view rendered', heading.length > 0, heading || 'no <h1>; page still on placeholder');
check('defect 2: a WebSocket was opened', sockets.length > 0, sockets[0] || 'none');

// Defect 1: shared signal carrying a @DataModel arrives, which needs a generated serializer.
let tick = '';
try {
  await page.waitForFunction(
    () => (document.getElementById('tick')?.textContent || '').includes('tick-'),
    null, { timeout: 15000 });
  tick = (await page.locator('#tick').textContent()).trim();
} catch {
  tick = (await page.locator('#tick').textContent().catch(() => '')) || '(missing)';
}
check('defect 1: shared @DataModel signal arrived', tick.includes('tick-'), tick);

// Defect 3: RMI call reaches a discovered @ApplicationScoped implementation.
let echo = '';
try {
  await page.waitForFunction(
    () => (document.getElementById('echo')?.textContent || '').includes('echo:hello'),
    null, { timeout: 15000 });
  echo = (await page.locator('#echo').textContent()).trim();
} catch {
  echo = (await page.locator('#echo').textContent().catch(() => '')) || '(missing)';
}
check('defect 3: RMI call returned from a discovered bean', echo.includes('echo:hello'), echo);

check('no JavaScript errors', consoleErrors.length === 0, consoleErrors[0] || '');

await page.screenshot({ path: new URL('./shots/smokeapp.png', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1') });
await browser.close();

const failed = checks.filter(c => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} checks passed`);
process.exit(failed.length ? 1 : 0);
