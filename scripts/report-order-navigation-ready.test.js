/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

// Execute the workflow's actual move operation. A frame navigation commits before
// its HTML has been parsed; even a successful response can still have an empty body.
function operation(options = {}) {
  const source = fs.readFileSync(path.join(__dirname, 'report-typed-queries-playwright-checks.js'), 'utf8');
  const match = source.match(/    async function move\([\s\S]*?\n    }\n(?=    await move)/);
  assert.ok(match, 'The real report move operation must be exercised');
  const expected = ['2', '1', '3'];
  const state = {ready: false, loadWaits: 0, clicks: 0};
  let navigated;
  let loaded;
  const load = new Promise(resolve => { loaded = resolve; });
  const mainFrame = {};
  const button = {click: async () => {
    state.clicks++;
    navigated();
    setImmediate(() => { state.ready = true; loaded(); });
  }};
  const row = {locator: () => button};
  const input = {check: async () => {}, locator: () => row};
  const page = {
    mainFrame: () => mainFrame,
    waitForEvent: (event, {predicate}) => {
      assert.equal(event, 'framenavigated');
      assert.equal(predicate({}), false, 'Child frame navigation must not complete a move');
      assert.equal(predicate(mainFrame), true);
      return new Promise(resolve => { navigated = resolve; });
    },
    waitForLoadState: async name => {
      assert.equal(name, 'domcontentloaded');
      state.loadWaits++;
      await load;
    },
    locator: selector => selector === 'input[name="nameSelected"]'
      ? {evaluateAll: callback => callback((options.rendered || expected).map(value => ({value})))}
      : input
  };
  const h = {
    assert: (condition, message) => assert.ok(condition, message),
    assertNotErrorPage: async () => {
      assert.ok(state.ready, 'Read an incomplete document before DOM ready');
      assert.notEqual(options.response, 'blank', 'Completed response is blank');
      assert.notEqual(options.response, 'error', 'Completed response is an error page');
    }
  };
  const move = vm.runInNewContext('(' + match[0].trim() + ')', {
    page, h, metadata: () => options.metadata || 'unchanged', before: 'unchanged',
    rows: () => (options.stored || expected).map(id => [id])
  });
  return {run: () => move('1', '3', expected), state};
}

test('report move waits for the new document to render after its frame commits', async () => {
  const move = operation();
  await move.run();
  assert.equal(move.state.clicks, 1);
  assert.equal(move.state.loadWaits, 1);
});

for (const [name, options, error] of [
  ['blank completed response', {response: 'blank'}, /Completed response is blank/],
  ['error completed response', {response: 'error'}, /Completed response is an error page/],
  ['changed field metadata', {metadata: 'changed'}, /deleted or changed configured field metadata/],
  ['incorrect stored order', {stored: ['1', '2', '3']}, /requested field ordering/],
  ['incorrect rendered order', {rendered: ['1', '2', '3']}, /reopened ordering page disagrees/]
]) {
  test('report move still rejects ' + name + ' after DOM ready', async () => {
    await assert.rejects(operation(options).run(), error);
  });
}
