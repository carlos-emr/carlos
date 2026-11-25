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
 *     patient, note and Edit attempt) and the window closes only after that is confirmed; the note
 *     never became editable.
 *  3. Edit answered with a lock this request did not create (the chart's own editor holds it) is not
 *     released by Cancel.
 *  4. A page rendered after an ordinary Save that kept the lock goes straight to editing, sends no lock
 *     request and does not move the cursor; its Cancel gives back the lock of the Edit attempt the
 *     action names, then submits the cancel.
 *  5. An Edit the server refuses as stale or while another note is open keeps the page read-only.
 *  6. An Edit whose answer is lost, with Cancel pending: the page asks the server to settle that attempt.
 *  7. A release that fails (HTTP 500) keeps the window open and says so; Cancel asks again.
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
const NOT_RELEASED = 'Your note lock could not be released.';
const ATTEMPT = /^[0-9a-f]{32}$/;

function pageScript(ownedAtRender, attemptAtRender) {
  const fill = text => text
    .replace(/\$\{carlos:forJavaScriptBlock\((\w+)\)\}/g,
      (_, key) => ({ noteLockLostLabel: LOST, noteLockReleaseFailedLabel: NOT_RELEASED }[key] || key))
    .replace(/<%=request\.getContextPath\(\) %>/g, '/carlos')
    .replace(/var demographicNo = '\$\{carlos:forJavaScript\(param\.demographicNo\)\}';/g, "var demographicNo = '101';")
    .replace(/var noteId = '<carlos:encode[\s\S]+?<%--[\s\S]+?--%>/g, "var noteId = '5009';")
    .replace(/var chartContext = '<%=[\s\S]+?%>';/g, "var chartContext = 'valid';")
    .replace(/var legacyEditAttempt = '<%=[\s\S]+?%>';/g, `var legacyEditAttempt = '${attemptAtRender}';`);
  const first = jsp.indexOf('        var XMLHttpRequestObject = false;');
  const second = jsp.indexOf('        function init() {', first);
  const third = jsp.indexOf('        function restore() {', second);
  return `var noteLockOwnedAtRender = ${ownedAtRender};\n${fill(jsp.slice(first, second))}${fill(jsp.slice(second, third))}`;
}

function pageHtml(ownedAtRender, attemptAtRender) {
  return `<!doctype html><html><body onload="init()">
<form name="caseManagementEntryForm" id="caseManagementEntryForm" action="/carlos/CaseManagementEntry" method="post">
<input type="hidden" name="method" value="save"/>
<input type="hidden" name="editAttempt" id="editAttemptField" value=""/>
<button type="button" id="editLegacyNote" onclick="takeNoteLock()">Edit</button>
<textarea id="caseNote_note" readonly>FAKE note text</textarea>
<input type="checkbox" id="sign" disabled data-note-lock-edit/>
<input type="submit" id="save" value="Save" disabled data-note-lock-edit/>
<input type="button" id="cancel" value="cancel" onclick="return cancelLegacyNote(this.form);"/>
<span id="legacyAutosaveStatus"></span>
</form>
<script>${pageScript(ownedAtRender, attemptAtRender)}</script></body></html>`;
}

/** A page whose server requests this check answers by hand: `held` are the ones not yet answered. */
async function open(browser, { ownedAtRender = false, attemptAtRender = '' } = {}) {
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
    log.push({ method: body.get('method'), noteId: body.get('noteId'), demographicNo: body.get('demographicNo'),
      editAttempt: body.get('editAttempt') });
    held.push({ route, method: body.get('method') });
  });
  await page.route('http://clinic.test/', route => route.fulfill({ contentType: 'text/html', body: pageHtml(ownedAtRender, attemptAtRender) }));
  await page.goto('http://clinic.test/');
  const take = method => {
    const at = held.findIndex(entry => entry.method === method);
    assert.ok(at >= 0, `no pending ${method} request`);
    return held.splice(at, 1)[0];
  };
  const answer = async (method, json, status = 200) => {
    await page.waitForFunction(() => true); // let the request reach the route
    await take(method).route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(json) });
  };
  // The answer never reaches the page, as when the connection drops after the server acted.
  const lose = async method => {
    await page.waitForFunction(() => true);
    await take(method).route.abort('connectionreset');
  };
  const events = () => page.evaluate(() => window.__events.slice());
  const waitForRequest = async method => {
    for (let i = 0; i < 100; i += 1) {
      if (held.some(entry => entry.method === method)) return;
      await new Promise(resolve => setTimeout(resolve, 20));
    }
    throw new Error(`the page never sent ${method}`);
  };
  return { context, page, log, held, answer, lose, events, waitForRequest };
}

const readOnly = page => page.$eval('#caseNote_note', note => note.readOnly);
const settle = async (until) => {
  for (let i = 0; i < 100 && !(await until()); i += 1) await new Promise(resolve => setTimeout(resolve, 20));
};

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
      assert.match(h.log[0].editAttempt, ATTEMPT);
      assert.deepEqual(h.log[1], { method: 'releaseNoteLock', noteId: '5009', demographicNo: '101', editAttempt: h.log[0].editAttempt });
      assert.deepEqual(await h.events(), [], 'the window closes only after the release answers');
      assert.equal(await readOnly(h.page), true, 'a cancelled reader never becomes editable');
      await h.answer('releaseNoteLock', { releaseNoteLock: 'released' });
      await settle(async () => (await h.events()).length > 0);
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
      await settle(async () => (await h.events()).length > 0);
      assert.deepEqual(await h.events(), ['close']);
      assert.equal(h.log.length, 1, 'no release request');
      await h.context.close();
    }

    // 4. After a Save that kept the lock the page goes straight to editing; its Cancel gives back the
    //    lock of the attempt the action names, then submits the cancel.
    {
      const attempt = '0123456789abcdef0123456789abcdef';
      const h = await open(browser, { ownedAtRender: true, attemptAtRender: attempt });
      assert.equal(await readOnly(h.page), false);
      assert.equal(await h.page.$eval('#save', save => save.disabled), false);
      assert.equal(await h.page.$eval('#editLegacyNote', edit => edit.hidden), true);
      assert.equal(await h.page.evaluate(() => document.activeElement && document.activeElement.id === 'caseNote_note'), false);
      assert.equal(await h.page.$eval('#editAttemptField', field => field.value), attempt);
      assert.equal(h.log.length, 0, 'no lock request after a Save');
      await h.page.click('#cancel');
      await h.waitForRequest('releaseNoteLock');
      assert.equal(h.log[0].editAttempt, attempt);
      assert.equal(h.log.length, 1, 'nothing is submitted before the lock is given back');
      await h.answer('releaseNoteLock', { releaseNoteLock: 'released' });
      await h.waitForRequest('cancel');
      assert.deepEqual(h.log.map(entry => entry.method), ['releaseNoteLock', 'cancel']);
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
      await settle(async () => dialogs.length > 0);
      assert.deepEqual(dialogs, [LOST], state);
      assert.equal(await readOnly(h.page), true, state);
      assert.equal(await h.page.$eval('#editLegacyNote', edit => edit.disabled), false, `${state}: Edit can be tried again`);
      await h.context.close();
    }
    // 6. The Edit answer is lost while Cancel waits: the page asks the server to settle that attempt.
    {
      const h = await open(browser);
      await h.page.click('#editLegacyNote');
      await h.waitForRequest('isNoteEdited');
      await h.page.click('#cancel');
      await h.lose('isNoteEdited');
      await h.waitForRequest('releaseNoteLock');
      assert.equal(h.log[1].editAttempt, h.log[0].editAttempt, 'Cancel names the attempt whose answer was lost');
      assert.deepEqual(await h.events(), []);
      await h.answer('releaseNoteLock', { releaseNoteLock: 'cancelled' });
      await settle(async () => (await h.events()).length > 0);
      assert.deepEqual(await h.events(), ['close']);
      assert.equal(await readOnly(h.page), true);
      await h.context.close();
    }

    // 7. A failed release keeps the window open and says so; Cancel asks again for the same attempt.
    {
      const h = await open(browser);
      const dialogs = [];
      h.page.on('dialog', dialog => { dialogs.push(dialog.message()); dialog.dismiss(); });
      await h.page.click('#editLegacyNote');
      await h.waitForRequest('isNoteEdited');
      await h.answer('isNoteEdited', { isNoteEdited: 'unlocked', ownedBySession: true, lockTaken: true });
      await settle(async () => !(await readOnly(h.page)));
      await h.page.click('#cancel');
      await h.waitForRequest('releaseNoteLock');
      await h.answer('releaseNoteLock', { releaseNoteLock: 'failed' }, 500);
      await settle(async () => dialogs.length > 0);
      assert.deepEqual(dialogs, [NOT_RELEASED]);
      assert.deepEqual(await h.events(), [], 'the window stays open while the lock may stand');
      assert.equal(await readOnly(h.page), false, 'the clinician keeps editing');
      await h.page.click('#cancel');
      await h.waitForRequest('releaseNoteLock');
      assert.equal(h.log[2].editAttempt, h.log[0].editAttempt);
      await h.answer('releaseNoteLock', { releaseNoteLock: 'released' });
      await h.waitForRequest('cancel');
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
