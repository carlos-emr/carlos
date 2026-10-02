#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const path = require('node:path');
const {chromium} = require('playwright');
const web = path.join(__dirname, '../src/main/webapp');

(async () => {
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
    console.log('PASS calendar preserves eForm initialization values and selects the correct date');
  } finally { await browser.close(); }
})().catch(error => {console.error(error); process.exitCode=1;});
