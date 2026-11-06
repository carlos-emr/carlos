#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Issue #3966: run the shipping quick-pick functions and styles in Chromium.
// A real wheel event must chain from an exhausted nested scroller to the page.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { launchBrowser } = require('./lib/playwright-harness');
const web = path.resolve(__dirname, '../src/main/webapp');
const styles = [
  'css/encounterStyles.css', 'css/receptionistapptstyle.css', 'css/searchDrug3.css',
  'css/allergies.css', 'rx/styles.css', 'appointment/appointmentstyle.css',
  'WEB-INF/jsp/appointment/editappointment.jsp', 'WEB-INF/jsp/documentManager/documentReport.jsp',
  'WEB-INF/jsp/eform/efmformslistadd.jsp', 'WEB-INF/jsp/eform/efmpatientformlist.jsp',
  'WEB-INF/jsp/eform/efmpatientformlistdeleted.jsp',
  'WEB-INF/jsp/encounter/oscarMeasurements/TemplateFlowSheetPage.jspf',
  'WEB-INF/jsp/rx/Preview2.jsp', 'WEB-INF/jsp/scratch/index.jsp', 'WEB-INF/jsp/scratch/version.jsp',
];

async function main() {
  const browser = await launchBrowser({ headless: true,
    chromePath: process.env.CHROME_PATH || process.env.CHROMIUM_PATH || '' });
  try {
    const page = await browser.newPage({ viewport: { width: 900, height: 600 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    // Use a routed loopback origin so an accidental href="#" navigation is observable.
    await page.route('http://127.0.0.1/fixture', route => route.fulfill({
      contentType: 'text/html', body: '<!doctype html><html><body></body></html>',
    }));
    for (const file of styles) {
      await page.goto('http://127.0.0.1/fixture');
      const source = fs.readFileSync(path.join(web, file), 'utf8');
      const css = file.endsWith('.css') ? source : [...source.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)]
        .map(match => match[1]).filter(style => style.includes('overscroll-behavior')).join('\n');
      assert.ok(css.includes('overscroll-behavior'), `Missing shipping scroll styles: ${file}`);
      await page.addStyleTag({ content: css });
      await page.addStyleTag({ content: `html, body { height: auto !important; overflow: visible !important; }
        body { display: block !important; margin: 0 !important; }
        #scrollFixture { height: 160px; width: 300px; overflow: auto; margin: 20px; }
        #scrollContent { height: 800px; } #pageSpacer { height: 2000px; }` });
      await page.locator('body').evaluate(body => {
        const scroller = document.createElement('div');
        scroller.id = 'scrollFixture';
        const content = document.createElement('div');
        content.id = 'scrollContent';
        content.textContent = 'Nested list';
        scroller.append(content);
        const spacer = document.createElement('div');
        spacer.id = 'pageSpacer';
        body.append(scroller, spacer);
      });
      assert.equal(await page.locator('html').evaluate(el => getComputedStyle(el).overscrollBehaviorY), 'none', file);
      assert.equal(await page.locator('#scrollFixture').evaluate(el => getComputedStyle(el).overscrollBehaviorY), 'auto', file);
      await page.locator('#scrollFixture').evaluate(el => { el.scrollTop = el.scrollHeight; });
      await page.locator('#scrollFixture').hover();
      await page.mouse.wheel(0, 240);
      await page.waitForFunction(() => window.scrollY > 0, null, { timeout: 3000 });
      if (file === 'css/encounterStyles.css') {
        await page.locator('#scrollFixture').evaluate(el => { el.id = 'encMainDivWrapper'; });
        assert.equal(await page.locator('#encMainDivWrapper').evaluate(el => getComputedStyle(el).overscrollBehaviorY),
          'contain', 'The eChart notes container keeps its intentional scroll containment');
      }
    }
    await page.goto('http://127.0.0.1/fixture');
    await page.setContent('<form name="serviceform"><input name="xml_appointment_date"></form>' +
      '<div style="height:800px"></div><div id="quickPickDateOptions"></div><div style="height:1000px"></div>');
    const tickler = fs.readFileSync(path.join(web, 'WEB-INF/jsp/tickler/ticklerEdit.jsp'), 'utf8');
    const start = tickler.indexOf('function addQuickPick(');
    const end = tickler.indexOf('function DateAdd(', start);
    assert.ok(start >= 0 && end > start, 'Missing shipping quick-pick/date functions');
    await page.clock.install({ time: new Date('2026-06-15T12:00:00Z') });
    await page.addScriptTag({ content: tickler.slice(start, end) });
    await page.evaluate(() => addQuickPick());
    assert.equal(await page.locator('#quickPickDateOptions a').count(), 40);
    for (const [label, expected] of [['1d', '2026-06-16'], ['10w', '2026-08-24'],
      ['2m', '2026-08-15'], ['3y', '2029-06-15']]) {
      await page.evaluate(() => window.scrollTo(0, 600));
      const before = await page.evaluate(() => ({ url: location.href, scroll: window.scrollY }));
      await page.getByRole('link', { name: label, exact: true }).click();
      // Flush the click's native default action before comparing scroll and URL.
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(resolve)));
      assert.equal(await page.locator('[name="xml_appointment_date"]').inputValue(), expected, label);
      assert.deepEqual(await page.evaluate(() => ({ url: location.href, scroll: window.scrollY })), before, label);
    }
    assert.deepEqual(errors, []);
    console.log(`PASS: ${styles.length} shipping scroll surfaces and four tickler quick-pick ranges in Chromium`);
  } finally {
    await browser.close();
  }
}
if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1; });
module.exports = { main };
