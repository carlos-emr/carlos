/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'), 'utf8');
const start = source.indexOf('// Guard the actual submission');
const end = source.indexOf('/*\n * The server adds a hidden newForm', start);
assert.ok(start >= 0 && end > start);

function setup() {
  const listeners = {};
  const microtasks = [];
  const button = { disabled: false };
  let submitted = 0;
  let throws = false;
  class Form {
    constructor(guarded = true) { this.guarded = guarded; }
    querySelector() { return this.guarded ? {} : null; }
    submit() { if (throws) throw new Error('synthetic navigation error'); submitted++; }
  }
  const form = new Form();
  vm.runInNewContext(source.slice(start, end), {
    HTMLFormElement: Form, WeakSet,
    document: { getElementById: () => button },
    window: { addEventListener: (type, listener) => { listeners[type] = listener; } },
    getEForm: () => form,
    queueMicrotask: callback => microtasks.push(callback),
  });
  return { form, Form, button, listeners, submitted: () => submitted,
    fail: value => { throws = value; }, flush: () => { while (microtasks.length) microtasks.shift()(); } };
}
function submitEvent(form, prevented = false) {
  return { target: form, defaultPrevented: prevented, preventDefault() { this.defaultPrevented = true; } };
}

test('direct submit only navigates once and disables toolbar Save', () => {
  const s = setup();
  s.form.submit(); s.form.submit();
  assert.equal(s.submitted(), 1);
  assert.equal(s.button.disabled, true);
});
test('unrelated forms retain native submission behavior', () => {
  const s = setup(); const form = new s.Form(false);
  form.submit(); form.submit();
  assert.equal(s.submitted(), 2);
  assert.equal(s.button.disabled, false);
});
test('native submit exception unlocks the next attempt', () => {
  const s = setup(); s.fail(true);
  assert.throws(() => s.form.submit(), /synthetic/);
  assert.equal(s.button.disabled, false);
  s.fail(false); s.form.submit(); assert.equal(s.submitted(), 1);
});
test('template validation cancellation does not lock Save', () => {
  const s = setup();
  s.listeners.submit(submitEvent(s.form, true)); s.flush();
  assert.equal(s.button.disabled, false);
  s.form.submit(); assert.equal(s.submitted(), 1);
});
test('native submit blocks the next activation without changing the submitter', () => {
  const s = setup(); const first = submitEvent(s.form);
  first.submitter = { name: 'newForm', value: 'false', disabled: false };
  s.listeners.submit(first); s.flush();
  assert.equal(first.defaultPrevented, false);
  assert.equal(first.submitter.disabled, false);
  assert.equal(first.submitter.value, 'false');
  const second = submitEvent(s.form); s.listeners.submit(second);
  assert.equal(second.defaultPrevented, true);
});
test('late cancellation releases a native submit reservation', () => {
  const s = setup(); const event = submitEvent(s.form);
  s.listeners.submit(event); event.preventDefault(); s.flush();
  assert.equal(s.button.disabled, false);
  s.form.submit(); assert.equal(s.submitted(), 1);
});
test('back-forward restoration unlocks editing while server token still guards replay', () => {
  const s = setup(); s.form.submit();
  s.listeners.pageshow({ persisted: true });
  assert.equal(s.button.disabled, false);
});
