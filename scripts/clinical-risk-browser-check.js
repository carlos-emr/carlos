#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// The checked-in HTML and JS execute unchanged in Chromium. This fixture
// covers DOM events and executed JS; the installed calculator check covers navigation.
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const {launchBrowser} = require('./lib/playwright-harness');
const {FRAMINGHAM_REFUSALS, FRAMINGHAM_VALID, FRAMINGHAM_VALID_CELL,
  UKPDS_REFUSALS, UKPDS_VALID, UKPDS_VALID_CELL} = require('./clinical-calculators-playwright-checks');
const root = path.resolve(__dirname, '../src/main/webapp');
const sha256 = data => crypto.createHash('sha256').update(data).digest('hex');

async function main() {
  const browser = await launchBrowser({headless: true, chromePath: process.env.CHROME_PATH || ''});
  const records = [];
  let assertions = 0;
  try {
    for (const [file, valid, refusals, highlighted] of [
      ['index.html', FRAMINGHAM_VALID, FRAMINGHAM_REFUSALS, FRAMINGHAM_VALID_CELL],
      ['diabetic.html', UKPDS_VALID, UKPDS_REFUSALS, UKPDS_VALID_CELL],
    ]) {
      const page = await browser.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.route('**/*', async route => {
        const url = new URL(route.request().url());
        const source = path.resolve(root, '.' + decodeURIComponent(url.pathname));
        if (url.origin !== 'http://127.0.0.1' || !source.startsWith(root + path.sep) || !fs.existsSync(source)) {
          await route.abort(); return;
        }
        await route.fulfill({path: source});
      });
      await page.coverage.startJSCoverage({resetOnNavigation: false});
      await page.goto(`http://127.0.0.1/encounter/calculators/riskcalc/${file}`, {waitUntil: 'load'});
      const calculate = page.locator('input[value="Calculate"]');
      async function fill(values) {
        for (const [id, value] of Object.entries(values)) await page.locator('#' + id).fill(value);
      }
      for (const refusal of refusals) {
        await fill(valid);
        await calculate.click();
        assert.match(await page.locator('#' + highlighted).innerText(), /%$/);
        assert.equal(await page.locator('#' + highlighted).evaluate(el => el.style.fontWeight), 'bold');
        await fill({...refusal.values, [refusal.field]: refusal.value});
        await calculate.click();
        assert.match(await page.locator('#riskInputRefused').innerText(), refusal.says);
        assert.equal(await page.locator('#' + refusal.field).getAttribute('aria-invalid'), 'true');
        assert.equal(await page.locator('#' + refusal.field).inputValue(), refusal.value);
        const cells = await page.locator('#FraminghamChart td[id], #UKPDSChart td[id]').evaluateAll(elements =>
          elements.filter(el => /^(?:bp\d+c\d+|UKPDS_bp\d+c\d+h\d+)$/.test(el.id))
            .map(el => ({text: el.textContent, weight: el.style.fontWeight})));
        assert.ok(cells.length > 0);
        assert.ok(cells.every(cell => cell.text === '' && cell.weight !== 'bold'));
        await fill(valid);
        await calculate.click();
        assert.equal(await page.locator('#riskInputRefused').count(), 0);
        assert.equal(await page.locator('#' + refusal.field).getAttribute('aria-invalid'), null);
        assert.match(await page.locator('#' + highlighted).innerText(), /%$/);
        assertions++;
      }
      // A distinct boundary oracle: a positive HDL immediately below total remains usable.
      await fill({...valid, cCholesterol: '4', cHDL: '3.9999'});
      await calculate.click();
      assert.equal(await page.locator('#riskInputRefused').count(), 0);
      assertions++;
      assert.deepEqual(errors, []);
      for (const entry of await page.coverage.stopJSCoverage()) {
        if (!entry.url.endsWith('/js/js.js') && !entry.url.endsWith('/clinicalCalculatorAge.js')) continue;
        const source = path.resolve(root, '.' + new URL(entry.url).pathname);
        const bytes = fs.readFileSync(source);
        assert.equal(entry.source, bytes.toString('utf8'), 'Covered source must equal the checked-in production bytes');
        assert.ok(entry.functions.some(fn => fn.ranges.some(range => range.count > 0)), 'No executed source ranges');
        records.push({path: path.relative(path.resolve(__dirname, '..'), source), sha256: sha256(bytes),
          functions: entry.functions});
      }
      await page.close();
    }
    assert.equal(records.length, 4, 'Both production scripts must execute on both pages');
    if (process.env.RELEASE_JS_COVERAGE) {
      const output = path.resolve(process.env.RELEASE_JS_COVERAGE);
      fs.mkdirSync(path.dirname(output), {recursive: true});
      fs.writeFileSync(output, JSON.stringify({schemaVersion: 1, kind: 'isolated-production-source',
        browserVersion: browser.version(), assertions, sources: records}, null, 2) + '\n', {flag: 'wx', mode: 0o600});
    }
    console.log(`PASS: ${assertions} clinical risk refusal/boundary cases; unchanged production HTML/JS in Chromium`);
  } finally {
    await browser.close();
  }
}
if (require.main === module) main().catch(error => {console.error(error); process.exitCode = 1;});
module.exports = {main};
