#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const {chromium} = require('playwright');
const web = path.join(__dirname, '../src/main/webapp');

// Serves the shipped scripts so calendar.js can resolve flatpickr and the lang file can resolve its
// locale relative to their own URLs, exactly as on a deployed page.
const queuedPage = `<!doctype html><html><head>
<script src="/share/calendar/calendar.js"></script>
<script src="/share/calendar/lang/calendar-fr.js"></script>
<script src="/share/calendar/calendar-setup.js"></script>
</head><body><input id="today" value="2026-09-30"><input id="saved" value="21-Aug-2026"><input id="empty">
<script>
  // Setup runs while flatpickr.min.js is still being injected, so every call must queue.
  window.queuedResults = ['today','saved','empty'].map(id =>
    Calendar.setup({inputField:id, ifFormat:'%d-%b-%Y', button:id}));
  window.queuedBeforeLoad = Calendar._pendingSetups.length;
  window.valuesBeforeLoad = ['today','saved','empty'].map(id => document.getElementById(id).value);
</script></body></html>`;
// flatpickr and its locale are held behind a gate the check releases, so the queued state is
// observed deterministically instead of racing the injected script.
let releaseFlatpickr;
const flatpickrGate = new Promise(resolve => { releaseFlatpickr = resolve; });
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/queued') {res.setHeader('Content-Type', 'text/html'); res.end(queuedPage); return;}
  if (url.pathname.startsWith('/library/flatpickr/')) await flatpickrGate;
  const target = path.resolve(web, '.' + url.pathname);
  if (target.startsWith(web + path.sep) && fs.existsSync(target) && fs.statSync(target).isFile()) {
    res.setHeader('Content-Type', 'text/javascript');
    res.end(fs.readFileSync(target)); return;
  }
  res.statusCode = 404; res.end();
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless:true, args:['--no-sandbox'],
    ...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {})});
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setContent('<input id="today" value="2026-09-30"><input id="saved" value="21-Aug-2026"><input id="empty">');
    await page.addScriptTag({path:path.join(web, 'library/flatpickr/flatpickr.min.js')});
    // Exercise the shipped setup shim with the already-loaded bundled library.
    await page.evaluate(() => {window.Calendar = {_flatpickrReady:true};});
    await page.addScriptTag({path:path.join(web, 'share/calendar/calendar-setup.js')});
    await page.evaluate(() => {
      for (const id of ['today','saved','empty']) {
        Calendar.setup({inputField:id, ifFormat:'%d-%b-%Y', button:id});
      }
    });
    assert.equal(await page.locator('#today').inputValue(), '2026-09-30');
    assert.equal(await page.locator('#saved').inputValue(), '21-Aug-2026');
    assert.equal(await page.locator('#empty').inputValue(), '');
    assert.deepEqual(await page.evaluate(() => {
      const date = document.getElementById('today')._flatpickr.selectedDates[0];
      return [date.getFullYear(), date.getMonth(), date.getDate()];
    }), [2026,8,30]);
    // Model the real NeuPath form's onload formatter, which expects the original ISO value.
    await page.evaluate(() => {
      const field = document.getElementById('today');
      const [year, month, day] = field.value.split('-');
      const shortMonth = new Date(Number(year), Number(month)-1, Number(day))
        .toLocaleString('en', {month:'short'});
      field.value = `${day}/${shortMonth}/${year}`;
    });
    assert.equal(await page.locator('#today').inputValue(), '30/Sep/2026');
    await page.locator('#today').click();
    await page.locator('.flatpickr-calendar.open .flatpickr-day[aria-label="September 29, 2026"]').click();
    assert.equal(await page.locator('#today').inputValue(), '29-Sep-2026');
    assert.deepEqual(errors, []);

    // Production path: flatpickr and the French locale load asynchronously, so the setups are
    // queued and replayed once both have arrived, still without rewriting the fields.
    const queued = await browser.newPage();
    const queuedErrors = [];
    queued.on('pageerror', error => queuedErrors.push(error.message));
    await queued.goto(`http://127.0.0.1:${server.address().port}/queued`, {waitUntil: 'domcontentloaded'}); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- the URL is this script's own loopback fixture server bound to 127.0.0.1 on an ephemeral port and the path is a string literal
    assert.deepEqual(await queued.evaluate(() => window.queuedResults), [null, null, null]);
    assert.equal(await queued.evaluate(() => window.queuedBeforeLoad), 3);
    assert.deepEqual(await queued.evaluate(() => window.valuesBeforeLoad), ['2026-09-30', '21-Aug-2026', '']);
    // Still held: nothing has been replayed yet.
    assert.deepEqual(await queued.evaluate(() => [typeof window.flatpickr, Calendar._flatpickrReady,
      Calendar._pendingSetups.length, ['today', 'saved', 'empty'].some(id => document.getElementById(id)._flatpickr)]),
      ['undefined', false, 3, false]);
    releaseFlatpickr();
    await queued.waitForFunction(() => ['today', 'saved', 'empty']
      .every(id => document.getElementById(id)._flatpickr) && Calendar._pendingSetups.length === 0);
    assert.equal(await queued.locator('#today').inputValue(), '2026-09-30');
    assert.equal(await queued.locator('#saved').inputValue(), '21-Aug-2026');
    assert.equal(await queued.locator('#empty').inputValue(), '');
    assert.deepEqual(await queued.evaluate(() => {
      const instance = document.getElementById('today')._flatpickr;
      const date = instance.selectedDates[0];
      return [date.getFullYear(), date.getMonth(), date.getDate(), instance.l10n.months.longhand[8]];
    }), [2026, 8, 30, 'septembre']);
    assert.deepEqual(queuedErrors, []);
    console.log('PASS calendar preserves eForm initialization values and selects the correct date, directly and after queued replay');
  } finally { releaseFlatpickr(); await browser.close(); server.close(); }
})().catch(error => {console.error(error); releaseFlatpickr(); server.close(); process.exitCode=1;});
