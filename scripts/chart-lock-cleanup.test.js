/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { releaseChartLocks, closeBrowserWithChartCleanup } = require('./lib/chart-lock-cleanup');
const base = new URL('https://127.0.0.1/carlos');

test('cleanup never evaluates another application or sends its token to a redirect', async () => {
  const submitted = [];
  let disposed = false;
  const form = { method: 'releaseNoteLock', demographicNo: '17', noteId: '0', 'CSRF-TOKEN': 'fixture' };
  const context = {
    pages: () => [
      { isClosed: () => false, url: () => 'https://127.0.0.1/carlos-other/chart',
        evaluate: () => { throw new Error('Other application was evaluated'); } },
      { isClosed: () => false, url: () => 'https://127.0.0.1/carlos/CaseManagementEntry',
        evaluate: async () => form },
    ],
    request: { post: async (...args) => {
      submitted.push(args);
      return { status: () => 302, dispose: async () => { disposed = true; } };
    } },
  };
  await assert.rejects(releaseChartLocks(context, base), /HTTP 302/);
  assert.deepEqual(submitted, [['https://127.0.0.1/carlos/CaseManagementEntry', { form, maxRedirects: 0 }]]);
  assert.equal(disposed, true);
  assert.equal(Object.hasOwn(form, 'force'), false);
});

test('a failed release fails cleanup while still closing the browser', async () => {
  let closed = false;
  const browser = {
    contexts: () => [{ pages: () => { throw new Error('Release failed'); } }],
    close: async () => { closed = true; },
  };
  await assert.rejects(closeBrowserWithChartCleanup(browser, base), /Release failed/);
  assert.equal(closed, true);
});
