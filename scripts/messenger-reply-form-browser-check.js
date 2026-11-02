#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { assertReplyPost } = require('./messenger-reply-send-archive-playwright-checks');

// Exercise native submission: URLSearchParams alone does not reproduce the
// browser's textarea newline encoding or omission of disabled checked controls.
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'],
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  try {
    const page = await browser.newPage();
    await page.route('https://fixture.test/**', route => route.fulfill({ contentType: 'text/html; charset=utf-8',
      body: `<!doctype html><meta charset="utf-8"><form method="post" accept-charset="UTF-8">
        <input name="archiveMessageId" value="123">
        <input name="subject">
        <textarea name="message"></textarea><div id="messagediv"></div>
        <input name="provider" type="checkbox" value="1-0" checked disabled>
        <button type="submit">Send</button></form>` }));
    await page.goto('https://fixture.test/');
    const expected = { messageId: '123', subject: '  Re:reply & archive  ',
      message: 'retry H&P + café 中文\nfirst line\nsecond line' };
    await page.locator('input[name="subject"]').fill(expected.subject);
    await page.locator('textarea').fill(expected.message);
    const [request] = await Promise.all([
      page.waitForRequest(request => request.method() === 'POST'),
      page.locator('button').click(),
    ]);
    const posted = new URLSearchParams(request.postData());
    assert.equal(posted.get('message'), expected.message.replace(/\n/g, '\r\n'));
    assert.notEqual(posted.get('message'), expected.message, 'Reproduce the old byte-for-byte assertion failure');
    assertReplyPost(posted, expected);
    // Only newline encoding is equivalent: missing carriers, whitespace loss,
    // changed text, and a recipient that should be disabled must still fail.
    for (const [field, value, error] of [['archiveMessageId', '999', /lost the original reply carrier/],
      ['subject', expected.subject.trim(), /changed the typed subject/],
      ['message', expected.message.replace('café', 'cafe'), /changed the typed message/],
      ['provider', '1-0', /disabled recipient was included/]]) {
      const changed = new URLSearchParams(posted);
      changed.set(field, value);
      assert.throws(() => assertReplyPost(changed, expected), error);
    }
    for (const [field, error] of [['archiveMessageId', /lost the original reply carrier/],
      ['subject', /changed the typed subject/], ['message', /changed the typed message/]]) {
      const missing = new URLSearchParams(posted);
      missing.delete(field);
      assert.throws(() => assertReplyPost(missing, expected), error);
    }
    console.log('PASS messenger reply native form encoding and refusal assertions');

    // Use the actual compose-page initialization and editor. A restored draft
    // used to gain a leading newline on every refusal, hidden by textarea-only checks.
    await page.goto('https://fixture.test/');
    await page.locator('textarea').fill(expected.message);
    await page.addScriptTag({ path: path.join(__dirname, '../src/main/webapp/library/toastui/toastui-editor-all.min.js') });
    const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/messenger/CreateMessage.jsp'), 'utf8');
    const initialize = jsp.match(/editor\.setMarkdown\([^;]+;\s*editor\.moveCursorToStart\(\);/)[0];
    const recovered = await page.evaluate(source => {
      const editor = new toastui.Editor({ el: document.getElementById('messagediv'), initialEditType: 'wysiwyg',
        usageStatistics: false });
      const initializeEditor = new Function('editor', 'submissionerror', source);
      initializeEditor(editor, 'No valid recipients selected');
      const draft = editor.getMarkdown();
      initializeEditor(editor, 'No valid recipients selected');
      const repeated = editor.getMarkdown();
      initializeEditor(editor, '');
      return { draft, repeated, freshReply: editor.getMarkdown() };
    }, initialize);
    assert.equal(recovered.draft, expected.message, 'The restored editor must retain the draft without a new blank line');
    assert.equal(recovered.repeated, expected.message, 'Repeated refusal must preserve the draft');
    assert.equal(recovered.freshReply, `\n${expected.message}`, 'Fresh replies retain the blank line before their quote');
    console.log('PASS messenger refused draft editor restoration');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
