/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

const source = fs.readFileSync(path.join(__dirname, 'appointment-lifecycle-playwright-checks.js'), 'utf8');
const start = source.indexOf('async function clickDismissed(');
assert(start >= 0, 'Appointment lifecycle must define its native dismissal control');

function fixture({ clickDialogs = [{ type: 'confirm', text: 'Delete this appointment?' }], afterClick = false } = {}) {
  const recorder = h.createRecorder();
  const listeners = new Map();
  let dismissals = 0;
  let accepted = 0;
  const page = {
    on(name, callback) { if (!listeners.has(name)) listeners.set(name, []); listeners.get(name).push(callback); },
    off(name, callback) { listeners.set(name, (listeners.get(name) || []).filter(fn => fn !== callback)); },
    async emit(name, value) { for (const callback of listeners.get(name) || []) await callback(value); },
    locator: () => ({ count: async () => 1, click: async () => {
      for (const entry of clickDialogs) await dialog(entry);
    } }),
    waitForTimeout: async () => { if (afterClick) await dialog({ type: 'confirm', text: 'Unrelated later action?' }); },
    close: async () => {},
  };
  const dialog = entry => page.emit('dialog', {
    type: () => entry.type, message: () => entry.text,
    accept: async () => { accepted += 1; }, dismiss: async () => { dismissals += 1; },
  });
  h.wireStrictPage(page, 'owned-appointment', recorder, { baseline: [] });
  const sandbox = vm.createContext({
    assert: h.assert, recorder, withExpectedDialogs: h.withExpectedDialogs,
    openEditPopup: async (...args) => {
      assert.equal(args.length, 3, 'The native control must not install a popup-lifetime handler');
      return page;
    },
  });
  const run = vm.runInContext(`${source.slice(start, source.indexOf('\n}', start) + 2)}\nclickDismissed;`, sandbox);
  return {
    recorder, run: () => run({}, {}, '321', '#deleteButton', /\/appointment\/DeleteRecord$/),
    counts: () => ({ accepted, dismissals }),
    requestListeners: () => (listeners.get('request') || []).length,
  };
}

test('the actual owned dismissal click answers exactly one confirm without accepting it', async () => {
  const f = fixture();
  const outcome = await f.run();
  assert.equal(outcome.confirmText, 'Delete this appointment?');
  assert.equal(outcome.posts, 0);
  assert.deepEqual(f.counts(), { accepted: 0, dismissals: 1 });
  assert.equal(f.requestListeners(), 1, 'only the permanent strict request observer remains');
  h.assertStrictPage(f.recorder);
});

for (const [name, clickDialogs] of [
  ['no confirmation', []],
  ['a validation alert instead of a confirmation', [{ type: 'alert', text: 'Something failed' }]],
  ['duplicate confirmations', [{ type: 'confirm', text: 'Delete?' }, { type: 'confirm', text: 'Delete again?' }]],
]) {
  test(`the actual native control rejects ${name}`, async () => {
    const f = fixture({ clickDialogs });
    await assert.rejects(f.run(), /exactly one confirmation/);
    assert.equal(f.requestListeners(), 1, 'only the permanent strict observer remains after failure');
    assert.equal(f.counts().accepted, 0);
  });
}

test('a later unrelated dialog stays fatal after the expected control has completed', async () => {
  const f = fixture({ afterClick: true });
  const outcome = await f.run();
  assert.equal(outcome.confirmText, 'Delete this appointment?', 'later dialogs cannot replace the proven control text');
  assert.equal(f.recorder.unexpectedDialogs.length, 1);
  assert.throws(() => h.assertStrictPage(f.recorder), /unexpected confirm/);
});
