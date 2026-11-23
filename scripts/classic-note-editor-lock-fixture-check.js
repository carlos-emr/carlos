#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The classic note editor's Edit, Cancel and Save-forward behaviour in a real browser (CARLOS #6132).
 *
 * The page script is taken from the shipping CaseManagementEntry.jsp, with its server-rendered values
 * filled in, and loaded into a minimal copy of the editor's form. The server is a request route that
 * this check controls, so the in-flight cases can be held and released deterministically; no CARLOS
 * server, login or patient data is needed. Everything here is FAKE.
 *
 * Checks:
 *  1. Opening the page for reading sends no request and leaves the note read-only (a fresh GET).
 *  2. Cancel chosen while the Edit request is in flight does not close the window until the answer is
 *     in; a lock that request created is then released (a method=releaseNoteLock POST for the same
 *     patient and note) and the window closes only after that answers; the note never became editable.
 *  3. Edit answered with a lock this request did not create (the chart's own editor holds it) is not
 *     released by Cancel.
 *  4. A page rendered after an ordinary Save that kept the lock goes straight to editing, sends no lock
 *     request and does not move the cursor.
 *  5. An Edit the server refuses as stale or while another note is open keeps the page read-only.
 *
 * Not part of the live-stack Playwright suite (no scripts/playwright-suite.json entry): it needs no CARLOS server or login.
 * Run: NODE_PATH=$(npm root -g) node scripts/classic-note-editor-lock-fixture-check.js
 */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp'), 'utf8');
const LOST = 'Your note lock has been lost.';

function pageScript(ownedAtRender) {
  const fill = text => text
    .replace(/\$\{carlos:forJavaScriptBlock\((\w+)\)\}/g, (_, key) => (key === 'noteLockLostLabel' ? LOST : key))
    .replace(/<%=request\.getContextPath\(\) %>/g, '/carlos')
    .replace(/var demographicNo = '\$\{carlos:forJavaScript\(param\.demographicNo\)\}';/g, "var demographicNo = '101';")
    .replace(/var noteId = '<carlos:encode[\s\S]+?<%--[\s\S]+?--%>/g, "var noteId = '5009';")
    .replace(/var chartContext = '<%=[\s\S]+?%>';/g, "var chartContext = 'valid';");
  const first = jsp.indexOf('        var XMLHttpRequestObject = false;');
  const second = jsp.indexOf('        function init() {', first);
  const third = jsp.indexOf('        function restore() {', second);
  return `var noteLockOwnedAtRender = ${ownedAtRender};\n${fill(jsp.slice(first, second))}${fill(jsp.slice(second, third))}`;
}

function pageHtml(ownedAtRender) {
  return `<!doctype html><html><body onload="init()">
<form name="caseManagementEntryForm" id="caseManagementEntryForm" action="/carlos/CaseManagementEntry" method="post">
<input type="hidden" name="method" value="save"/>
<button type="button" id="editLegacyNote" onclick="takeNoteLock()">Edit</button>
<textarea id="caseNote_note" readonly>FAKE note text</textarea>
<input type="checkbox" id="sign" disabled data-note-lock-edit/>
<input type="submit" id="save" value="Save" disabled data-note-lock-edit/>
<input type="button" id="cancel" value="cancel" onclick="return cancelLegacyNote(this.form);"/>
<span id="legacyAutosaveStatus"></span>
</form>
<script>${pageScript(ownedAtRender)}</script></body></html>`;
}

/** A page whose server requests this check answers by hand: `held` are the ones not yet answered. */
async function open(browser, { ownedAtRender = false } = {}) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const log = [];
  const held = [];
  await page.addInitScript(() => {
    window.__events = [];
    window.close = () => window.__events.push('close');
  });
  await page.route('http://clinic.test/carlos/CaseManagementEntry', route => {
    const body = new URLSearchParams(route.request().postData() || '');
    log.push({ method: body.get('method'), noteId: body.get('noteId'), demographicNo: body.get('demographicNo') });
    held.push({ route, method: body.get('method') });
  });
  await page.route('http://clinic.test/', route => route.fulfill({ contentType: 'text/html', body: pageHtml(ownedAtRender) }));
  await page.goto('http://clinic.test/');
  const answer = async (method, json) => {
    await page.waitForFunction(() => true); // let the request reach the route
    const at = held.findIndex(entry => entry.method === method);
    assert.ok(at >= 0, `no pending ${method} request`);
    const [entry] = held.splice(at, 1);
    await entry.route.fulfill({ contentType: 'application/json', body: JSON.stringify(json) });
  };
  const events = () => page.evaluate(() => window.__events.slice());
  const waitForRequest = async method => {
    for (let i = 0; i < 100; i += 1) {
      if (held.some(entry => entry.method === method)) return;
      await new Promise(resolve => setTimeout(resolve, 20));
    }
    throw new Error(`the page never sent ${method}`);
  };
  return { context, page, log, held, answer, events, waitForRequest };
}

const readOnly = page => page.$eval('#caseNote_note', note => note.readOnly);

(async () => {
  const browser = await chromium.launch();
  try {
    // 1. A fresh read-only open sends nothing.
    {
      const h = await open(browser);
      assert.equal(await readOnly(h.page), true);
      assert.equal(h.log.length, 0, 'opening for reading sends no lock request');
      assert.equal(await h.page.$eval('#save', save => save.disabled), true);
      await h.context.close();
    }

    // 2. Cancel while Edit is in flight: wait, release the lock Edit created, then close.
    {
      const h = await open(browser);
      await h.page.click('#editLegacyNote');
      await h.waitForRequest('isNoteEdited');
      await h.page.click('#cancel');
      assert.deepEqual(await h.events(), [], 'the window is not closed while Edit is unanswered');
      assert.equal(h.log.length, 1, 'no release before the lock is known');
      await h.answer('isNoteEdited', { isNoteEdited: 'unlocked', ownedBySession: true, lockTaken: true });
      await h.waitForRequest('releaseNoteLock');
      assert.deepEqual(h.log[1], { method: 'releaseNoteLock', noteId: '5009', demographicNo: '101' });
      assert.deepEqual(await h.events(), [], 'the window closes only after the release answers');
      assert.equal(await readOnly(h.page), true, 'a cancelled reader never becomes editable');
      await h.answer('releaseNoteLock', {});
      for (let i = 0; i < 100 && (await h.events()).length === 0; i += 1) await new Promise(r => setTimeout(r, 20));
      assert.deepEqual(await h.events(), ['close']);
      await h.context.close();
    }

    // 3. A lock the request did not create is not the page's to release.
    {
      const h = await open(browser);
      await h.page.click('#editLegacyNote');
      await h.waitForRequest('isNoteEdited');
      await h.page.click('#cancel');
      await h.answer('isNoteEdited', { isNoteEdited: 'user', ownedBySession: true, lockTaken: false });
      for (let i = 0; i < 100 && (await h.events()).length === 0; i += 1) await new Promise(r => setTimeout(r, 20));
      assert.deepEqual(await h.events(), ['close']);
      assert.equal(h.log.length, 1, 'no release request');
      await h.context.close();
    }

    // 4. After a Save that kept the lock the page goes straight to editing.
    {
      const h = await open(browser, { ownedAtRender: true });
      assert.equal(await readOnly(h.page), false);
      assert.equal(await h.page.$eval('#save', save => save.disabled), false);
      assert.equal(await h.page.$eval('#editLegacyNote', edit => edit.hidden), true);
      assert.equal(await h.page.evaluate(() => document.activeElement && document.activeElement.id === 'caseNote_note'), false);
      assert.equal(h.log.length, 0, 'no lock request after a Save');
      await h.context.close();
    }

    // 5. A refused Edit keeps the page read-only and says so.
    for (const state of ['stale', 'editorOpen', 'other', 'reload']) {
      const h = await open(browser);
      const dialogs = [];
      h.page.on('dialog', dialog => { dialogs.push(dialog.message()); dialog.dismiss(); });
      await h.page.click('#editLegacyNote');
      await h.waitForRequest('isNoteEdited');
      await h.answer('isNoteEdited', { isNoteEdited: state, ownedBySession: false, lockTaken: false });
      for (let i = 0; i < 100 && dialogs.length === 0; i += 1) await new Promise(r => setTimeout(r, 20));
      assert.deepEqual(dialogs, [LOST], state);
      assert.equal(await readOnly(h.page), true, state);
      assert.equal(await h.page.$eval('#editLegacyNote', edit => edit.disabled), false, `${state}: Edit can be tried again`);
      await h.context.close();
    }
    console.log('classic note editor lock fixture checks: PASS');
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exit(1);
});
