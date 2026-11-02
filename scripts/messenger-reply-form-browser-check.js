#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
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
        <textarea name="message"></textarea>
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
    for (const [field, value] of [['archiveMessageId', '999'], ['subject', expected.subject.trim()],
      ['message', expected.message.replace('café', 'cafe')], ['provider', '1-0']]) {
      const changed = new URLSearchParams(posted);
      changed.set(field, value);
      assert.throws(() => assertReplyPost(changed, expected));
    }
    const missing = new URLSearchParams(posted);
    missing.delete('message');
    assert.throws(() => assertReplyPost(missing, expected));
    console.log('PASS messenger reply native form encoding and refusal assertions');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
