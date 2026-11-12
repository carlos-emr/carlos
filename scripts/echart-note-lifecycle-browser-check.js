#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { noteContents, waitVisible } = require('./echart-note-lifecycle-playwright-checks');

// ChartNotesAjax.jsp nests n<note_id> inside nc<position>. editNote() removes
// txt<note_id> and creates a textarea, assigning its value as a DOM property.
// Exercise real Playwright locator re-resolution across that transition (#5254).
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'],
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  try {
    const page = await browser.newPage();
    await page.setContent(`<!doctype html><div id="encMainDiv">
      <div id="nc1"><div id="n42" class="note-contents">
        <a id="edit42" href="#">Edit</a><div id="txt42">FAKE signed note</div>
      </div></div>
      <div id="nc2"><div id="n99" class="note-contents">FAKE unrelated note</div></div>
    </div>`);
    await page.locator('#edit42').evaluate(link => link.addEventListener('click', event => {
      event.preventDefault();
      const text = document.getElementById('txt42');
      const editor = document.createElement('textarea');
      editor.name = 'caseNote_note';
      editor.value = text.innerText;
      text.replaceWith(editor);
      link.remove();
    }));
    const note = noteContents(page, '42');
    await waitVisible(note, 'Stored signed note');
    assert.equal(await note.count(), 1, 'Only the stored row contents, not its nc wrapper');
    const old = page.locator('#encMainDiv div[id^="n"]').filter({ hasText: 'FAKE signed note' }).last();
    assert.equal(await old.count(), 1, 'Negative control must initially find the note');
    await note.locator('#edit42').click();
    const editor = note.locator('textarea[name="caseNote_note"]');
    await waitVisible(editor, 'Edit signed note: caseNote_note textarea', 1000);
    assert.equal(await editor.inputValue(), 'FAKE signed note');
    assert.equal(await old.count(), 0, 'The former hasText locator loses the note after Edit');
    await editor.fill('FAKE edited note');
    assert.equal(await note.count(), 1, 'Changing the editor value must retain the stored note');
    assert.equal(await noteContents(page, '99').innerText(), 'FAKE unrelated note');
    await assert.rejects(waitVisible(noteContents(page, '404'), 'Missing stored note', 100),
      /Missing stored note: locator\.waitFor:/);
    console.log('PASS echart-note-lifecycle: stable note identity across Edit and named missing-control failures');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
