#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
/*
 * A real browser reads the markup an eForm select prefill produces (CARLOS #6284).
 *
 * scripts/fixtures/eform/select-prefill-cases.json holds, for each case, the select as the template has it,
 * the database output, the markup the server edit produces (EFormSelectApPrefillUnitTest asserts that
 * exactly), and the value a browser submits for it. This check loads each edited markup into Chromium and
 * asserts the select submits that value, so the server's HTML parser and the browser agree on which option
 * is shown. No CARLOS server or login is needed; everything is FAKE.
 *
 * Not part of the live-stack Playwright suite (no scripts/playwright-suite.json entry).
 * Run: NODE_PATH=$(npm root -g) node scripts/eform-select-prefill-fixture-check.js
 */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const { cases } = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/eform/select-prefill-cases.json'), 'utf8'));

(async () => {
  const browser = await chromium.launch(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
  try {
    const page = await browser.newPage();
    for (const testCase of cases) {
      await page.setContent(`<!doctype html><html><body><form id="f">${testCase.after}</form></body></html>`);
      const submitted = await page.evaluate(() => new FormData(document.getElementById('f')).get('a'));
      assert.equal(submitted, testCase.submitted, testCase.name);
      console.log(`ok - ${testCase.name}: submits ${JSON.stringify(submitted)}`);
    }
    console.log(`${cases.length} cases agree with Chromium`);
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exit(1);
});
