/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const { EventEmitter, once } = require('node:events');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, 'lib/legacy-full-form-save-checks.js'), 'utf8');
const start = source.indexOf('async function withOpenerReload(');
const end = source.indexOf('/** Two legacy windows', start);
assert.ok(start >= 0 && end > start, 'legacy opener reload helper exists');
const { withOpenerReload } = vm.runInNewContext(`(() => { ${source.slice(start, end)}; return { withOpenerReload }; })()`);
const tick = () => new Promise(resolve => setImmediate(resolve));

function opener() {
  const page = new EventEmitter();
  page.waitForLoadState = () => Promise.resolve(); // Its previous document is already loaded.
  page.waitForEvent = event => once(page, event);
  return page;
}

test('an already loaded opener waits for the new load requested by the legacy editor', async () => {
  const page = opener();
  let completed = false;
  const editor = {};
  const opened = withOpenerReload(page, async () => {
    assert.equal(page.listenerCount('load'), 1, 'observe the next load before opening the editor');
    return editor;
  });
  opened.then(() => { completed = true; });
  await tick();
  assert.equal(completed, false, 'the old loaded document cannot acknowledge the new reload');
  page.emit('load');
  assert.equal(await opened, editor);
  assert.equal(page.listenerCount('load'), 0);
});

test('the next legacy window cannot open until the preceding opener reload has finished', async () => {
  const page = opener();
  const actions = [];
  const sequence = (async () => {
    await withOpenerReload(page, async () => { actions.push('first'); });
    await withOpenerReload(page, async () => { actions.push('second'); });
  })();
  await tick();
  assert.deepEqual(actions, ['first']);
  page.emit('load');
  await tick();
  assert.deepEqual(actions, ['first', 'second']);
  page.emit('load');
  await sequence;
  assert.equal(page.listenerCount('load'), 0);
});
