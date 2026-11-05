/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

// Execute the actual Back-button navigation block, including its click
// options. The shorter implicit click-navigation wait must not override the
// explicit destination wait. The caller subsequently checks usable Rx
// controls and the originating patient.
const source = fs.readFileSync(path.join(__dirname, 'rx-favorites-choose-drug-playwright-checks.js'), 'utf8');
const start = source.indexOf('      await Promise.all([', source.indexOf('for (const page of [editor, copy])'));
const end = source.indexOf('\n      await page.locator', start);
assert(start >= 0 && end > start, 'The actual favourite Back navigation must be present');
const block = source.slice(start, end);
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function fixture({ clickError } = {}) {
  const document = deferred(), implicitNavigation = deferred(), clicked = deferred();
  let armed = false;
  const page = { waitForURL(pattern, options) {
    assert.equal(pattern.test('/rx/searchDrug?demographicNo=3153'), true);
    assert.equal(pattern.test('/rx/ViewEditFavorites2?demographicNo=3153'), false);
    assert.equal(options.timeout, 30000);
    assert.equal(options.waitUntil, 'domcontentloaded');
    armed = true;
    return document.promise;
  } };
  const back = { async click(options = {}) {
    assert.equal(armed, true, 'The destination wait must be armed before clicking');
    if (clickError) throw clickError;
    clicked.resolve();
    if (!options.noWaitAfter) await implicitNavigation.promise;
  } };
  const run = () => vm.runInNewContext(`(async () => { ${block} })()`, { page, back });
  return { document, implicitNavigation, clicked, run };
}
test('favourite Back uses the explicit destination wait rather than a second implicit click wait', { timeout: 1000 }, async () => {
  const f = fixture();
  const completion = f.run();
  await f.clicked.promise;
  f.document.resolve();
  // Deliberately keep the implicit click wait pending: reverting its options leaves
  // this test unfinished instead of silently accepting the old document.
  await completion;
});
test('an already loaded favourite editor cannot satisfy the Back destination wait', { timeout: 1000 }, async () => {
  const f = fixture();
  let completed = false;
  const completion = f.run().then(() => { completed = true; });
  await f.clicked.promise;
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(completed, false);
  f.document.resolve();
  await completion;
});
test('a Back click failure propagates even if navigation later times out', { timeout: 1000 }, async () => {
  const error = new Error('Back button could not be clicked');
  const f = fixture({ clickError: error });
  await assert.rejects(f.run(), actual => actual === error);
  f.document.reject(new Error('No destination document'));
  await new Promise(resolve => setImmediate(resolve));
});
test('a missing destination remains a failure with the original 30-second budget', { timeout: 1000 }, async () => {
  const f = fixture();
  const error = new Error('Destination document timed out');
  const completion = f.run();
  await f.clicked.promise;
  f.document.reject(error);
  await assert.rejects(completion, actual => actual === error);
});
