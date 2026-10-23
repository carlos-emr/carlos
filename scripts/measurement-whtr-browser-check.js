/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Real Chromium DOM/event regression for the measurement script resolved in the release/main merge.
// No running application, database or patient data. The installed measurement-whtr check covers persistence.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { launchBrowser } = require('./lib/playwright-harness');

const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/encounter/oscarMeasurements/Measurements.jsp'), 'utf8');
const definition = source.indexOf('function calcWHtR(');
assert.ok(definition >= 0, 'The page must define calcWHtR');
const open = source.lastIndexOf('<script>', definition);
const close = source.indexOf('</script>', definition);
assert.ok(open >= 0 && close > definition, 'The calculation must remain in one script block');
const script = source.slice(open + '<script>'.length, close);
assert.ok(!/<%|\$\{/.test(script), 'The browser fixture must run the page script without rewriting JSP expressions');

async function run() {
  const browser = await launchBrowser({ headless: true,
    chromePath: process.env.CHROME_PATH || process.env.CHROMIUM_PATH || '' });
  try {
    for (const code of ['WHTR', 'WHtR', 'whtr']) {
      const page = await browser.newPage();
      const errors = [];
      const dialogs = [];
      page.on('pageerror', error => errors.push(error.message));
      page.on('dialog', async dialog => { dialogs.push(dialog.message()); await dialog.accept(); });
      await page.route('**/*', route => {
        if (new URL(route.request().url()).pathname === '/calculation.js') {
          return route.fulfill({ contentType: 'text/javascript', body: script });
        }
        // Representative rendered rows: value is in the third cell, as Measurements.jsp emits it.
        const rows = ['HT', 'WAIS', code].map(type =>
          `<tr id="row-${type}"><td>${type}</td><td></td><td><input aria-label="${type}"></td><td></td><td></td></tr>`).join('');
        return route.fulfill({ contentType: 'text/html', body:
          `<!doctype html><html><body><table>${rows}</table><script src="/calculation.js"></script></body></html>` });
      });
      await page.goto('http://127.0.0.1/measurement-fixture');
      const height = page.getByRole('textbox', { name: 'HT', exact: true });
      const waist = page.getByRole('textbox', { name: 'WAIS', exact: true });
      const ratio = page.getByRole('textbox', { name: code, exact: true });
      await height.fill('170');
      await waist.fill('85');
      await waist.blur();
      assert.equal(await ratio.inputValue(), '0.50', `${code}: blur must calculate in the real DOM`);
      await ratio.fill('0.50');
      await waist.fill('');
      await waist.blur();
      assert.equal(await ratio.inputValue(), '0.50', `${code}: retyping the calculated value makes it manual`);
      await waist.fill('85');
      await waist.blur();
      await height.fill('0');
      await height.blur();
      assert.equal(await ratio.inputValue(), '', `${code}: zero height must clear an automatic ratio`);
      await height.fill('5\'10"');
      await height.dblclick();
      await waist.fill('34');
      await waist.dblclick();
      assert.equal(await height.inputValue(), '177.8');
      assert.equal(await waist.inputValue(), '86.4');
      assert.equal(await ratio.inputValue(), '0.49', `${code}: double-click conversion must recalculate immediately`);
      assert.equal(dialogs.length, 2, 'Each conversion must ask for confirmation once');
      await waist.fill('86 cm');
      await waist.blur();
      assert.equal(await ratio.inputValue(), '', `${code}: unit-bearing text must not leave a stale ratio`);
      assert.deepEqual(errors, [], `${code}: browser script errors`);
      await page.close();
    }
    console.log('PASS: real measurement script in Chromium; all three WHtR code cases, manual values, stale clearing and unit conversions');
  } finally {
    await browser.close();
  }
}
run().catch(error => { console.error(error); process.exitCode = 1; });
