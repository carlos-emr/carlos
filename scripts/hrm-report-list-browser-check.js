#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { hrmReportLink } = require('./lib/hrm-report-list');
const { getLaunchOptions } = require('./lib/playwright-harness');

// ViewDocList's popup links include the patient context added in #5065. Exercise
// the shared locator in Chromium, including IDs that start with the owned ID.
(async () => {
  const chromePath = process.env.CHROME_PATH || process.env.CHROMIUM_PATH
    || process.env.CHROME_BIN || process.env.CARLOS_SMOKE_CHROMIUM;
  const browser = await chromium.launch(getLaunchOptions(chromePath));
  try {
    const context = await browser.newContext();
    await context.route('https://hrm.invalid/**', route => route.fulfill({
      contentType: 'text/html', body: '<p>FAKE HRM viewer</p>',
    }));
    const page = await context.newPage();
    await page.goto('https://hrm.invalid/carlos/');
    await page.setContent(`<!doctype html>
      <script>function popupPage(url) { window.open(url, '_blank'); }</script>
      <a onclick="popupPage('/carlos/hospitalReportManager/Display?id=45&demographicNo=7')">Outside list</a>
      <table id="tblHRM"><tbody>
        <tr><td><a href="#" ONCLICK="popupPage('/carlos/hospitalReportManager/Display?id=450&demographicNo=7', 'HRM Report'); return false;">Other report</a></td></tr>
        <tr><td><a href="#" ONCLICK="popupPage('/carlos/hospitalReportManager/Display?id=45&demographicNo=7', 'HRM Report'); return false;">Owned report</a></td></tr>
      </tbody></table>`);
    assert.equal(await page.locator('#tblHRM a[onclick*="Display?id=45\'"]').count(), 0,
      'Negative control: the former selector cannot find the current link');
    const owned = hrmReportLink(page, '45');
    assert.equal(await owned.count(), 1, 'Select exactly the owned report within the list');
    assert.equal(await owned.innerText(), 'Owned report');
    assert.equal(await hrmReportLink(page, '4').count(), 0, 'A report ID prefix must not match');
    assert.throws(() => hrmReportLink(page, '45&other=1'), /Invalid HRM report ID/);
    const popupPromise = page.waitForEvent('popup');
    await owned.click();
    const popup = await popupPromise;
    await popup.waitForLoadState();
    const url = new URL(popup.url());
    assert.equal(url.pathname, '/carlos/hospitalReportManager/Display');
    assert.equal(url.searchParams.get('id'), '45');
    assert.equal(url.searchParams.get('demographicNo'), '7');
    console.log('PASS HRM report list: owned report popup retains ID and patient context');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
