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
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const {getLaunchOptions} = require('./lib/playwright-harness');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/select.js'), 'utf8');
(async () => {
  const chromePath = process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.CARLOS_SMOKE_CHROMIUM;
  const browser = await chromium.launch(getLaunchOptions(chromePath));
  try {
    for (const layout of ['document', 'positioned', 'scrolled']) {
      const page = await browser.newPage({viewport: {width: 1100, height: 800}});
      try {
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.setContent('<!doctype html><style>body {margin: 0} '
          + '#parent {margin: 180px 0 500px 100px; height: 360px; width: 700px; border: 7px solid; overflow: auto} '
          + '#content {padding: 100px 90px 600px; width: 1000px} select,input {width: 240px; height: 28px}</style>'
          + '<div id="parent"><div id="content"><select id="encType">'
          + '<option>face to face encounter with client</option><option>telephone encounter with client</option>'
          + '<option>email encounter with client</option><option>other encounter</option>'
          + '</select></div></div>');
        await page.evaluate(layout => {
          const parent = document.querySelector('#parent');
          if (layout !== 'document') parent.style.position = 'relative';
          else parent.style.overflow = 'visible';
          if (layout === 'scrolled') {
            parent.style.marginTop = '550px';
            parent.scrollTop = 65;
            parent.scrollLeft = 35;
            window.scrollTo(0, 320);
          }
          window.selectedChanges = 0;
          document.querySelector('#encType').addEventListener('change', () => window.selectedChanges++);
        }, layout);
        await page.addScriptTag({content: source});
        await page.evaluate(() => { new Autocompleter.SelectBox('encType'); });
        const input = page.locator('#encType_combo');
        await input.click();
        const inputBox = await input.boundingBox();
        const dropdownBox = await page.locator('#encType_options').boundingBox();
        assert(dropdownBox && inputBox, layout + ': dropdown and input must be visible');
        assert(Math.abs(dropdownBox.x - inputBox.x) < 1, layout + ': dropdown must align with its input');
        assert(Math.abs(dropdownBox.y - inputBox.y - inputBox.height) < 1,
          layout + ': dropdown must sit directly below its input');
        await page.locator('#encType_options li').getByText('email encounter with client', {exact: true}).click({timeout: 5000});
        assert.equal(await page.locator('#encType').inputValue(), 'email encounter with client');
        assert.equal(await page.evaluate(() => window.selectedChanges), 1);
        assert.equal(await page.locator('#encType_options').isVisible(), false);
        assert.deepEqual(errors, []);
      } finally {
        await page.close();
      }
    }
    console.log('PASS select-box positioning and native option selection in document, positioned and scrolling layouts');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
