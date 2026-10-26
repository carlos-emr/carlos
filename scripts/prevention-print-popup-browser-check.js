#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');
const { createRecorder, assertStrictPage, wireStrictPage } = require('./lib/playwright-harness');
const { openVaccineEditor } = require('./prevention-print-playwright-checks');

// No application or database: keep the popup at its initial document until a
// separate control navigates it. Chromium versions differ in whether a direct
// window.open(url) emits its popup event before or after the response arrives.
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'],
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  try {
    for (const delayed of [true, false]) {
      const context = await browser.newContext();
      try {
        const recorder = createRecorder();
        const index = await context.newPage();
        wireStrictPage(index, 'prevention-index', recorder);
        await context.route('https://fixture.test/**', async route => {
          const url = new URL(route.request().url());
          if (url.pathname.endsWith('/ViewAddPreventionData')) {
            return route.fulfill({ contentType: 'text/html', body: '<!doctype html><input id="prevDate">' });
          }
          return route.fulfill({ contentType: 'text/html',
            body: `<!doctype html><input id="immunization" onkeydown="if(event.key === 'Enter') popup(600,900,'${delayed ? 'about:blank' : '/carlos/prevention/ViewAddPreventionData?prevention=Tdap&amp;brandName=ADACEL'}','AddPreventionWindow')">
              <button id="navigate" onclick="popup2.winRefs.AddPreventionWindow.location.href='/carlos/prevention/ViewAddPreventionData?prevention=Tdap&amp;brandName=ADACEL'">Finish opening editor</button>` });
        });
        await index.goto('https://fixture.test/carlos/prevention/ViewPreventionIndex');
        // Use the actual named-window helper used by the vaccine picker.
        await index.addScriptTag({ path: path.join(__dirname, '../src/main/webapp/share/javascript/Oscar.js') });
        const popupEvent = index.waitForEvent('popup', { timeout: 20000 });
        popupEvent.catch(() => {});
        let settled = false;
        const opening = openVaccineEditor(index, index.locator('#immunization'), recorder)
          .then(editor => { settled = true; return editor; });
        opening.catch(() => {});
        const initial = await popupEvent;
        if (delayed) {
          // This is the old wait: it returns for the empty initial document.
          await initial.waitForLoadState('domcontentloaded');
          // Drain the Enter command and its promise continuations before checking
          // whether the helper returned the still-blank editor.
          await index.locator('#immunization').inputValue();
          await new Promise(resolve => setImmediate(resolve));
          assert.ok(['', 'about:blank'].includes(initial.url()), 'The fixture must hold navigation before commit');
          assert.equal(settled, false, 'The editor must not be returned before navigation commits');
          await index.locator('#navigate').click();
        }
        const editor = await opening;
        assert.equal(editor, initial);
        const url = new URL(editor.url());
        assert.equal(url.pathname, '/carlos/prevention/ViewAddPreventionData');
        assert.equal(url.searchParams.get('prevention'), 'Tdap');
        assert.equal(url.searchParams.get('brandName'), 'ADACEL');
        assert.equal(await editor.locator('#prevDate').count(), 1);
        assertStrictPage(recorder);
        console.log(`PASS prevention-print popup (${delayed ? 'delayed' : 'immediate'} navigation)`);
      } finally {
        await context.close();
      }
    }
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
