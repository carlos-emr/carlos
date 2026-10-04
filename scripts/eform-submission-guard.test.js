/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
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

function setup({ navigation = true, templateBindings = "" } = {}) {
  const listeners = {};
  const nextTasks = [];
  const button = { disabled: false };
  let submitted = 0;
  let spinnerHidden = 0;
  let throws = false;
  class Form {
    constructor(guarded = true) { this.guarded = guarded; }
    querySelector() { return this.guarded ? {} : null; }
    submit() { if (throws) throw new Error('synthetic navigation error'); submitted++; }
  }
  const form = new Form();
  vm.runInNewContext(templateBindings + source.slice(start, end), {
    HTMLFormElement: Form, WeakSet, WeakMap,
    HideSpin: () => spinnerHidden++,
    document: { getElementById: () => button },
    window: {
      addEventListener: (type, listener) => { listeners[type] = listener; },
      navigation: navigation ? { addEventListener: (type, listener) => { listeners[type] = listener; } } : undefined,
    },
    getEForm: () => form,
    setTimeout: callback => nextTasks.push(callback),
  });
  return { form, Form, button, listeners, submitted: () => submitted, hidden: () => spinnerHidden,
    fail: value => { throws = value; }, flush: () => { while (nextTasks.length) nextTasks.shift()(); } };
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
  assert.equal(s.hidden(), 1);
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
  assert.equal(s.hidden(), 1);
  s.form.submit(); assert.equal(s.submitted(), 1);
});
test('late handler can take over native submission with one explicit form.submit', () => {
  const s = setup(); const event = submitEvent(s.form);
  s.listeners.submit(event);
  event.preventDefault(); s.form.submit(); s.form.submit(); s.flush();
  assert.equal(s.submitted(), 1);
  assert.equal(s.button.disabled, true);
});
test('back-forward restoration unlocks editing while server token still guards replay', () => {
  const s = setup(); s.form.submit();
  s.listeners.pageshow({ persisted: true });
  assert.equal(s.button.disabled, false);
  s.form.submit(); assert.equal(s.submitted(), 2);
});

test('template lexical bindings do not collide with private guard state', () => {
  const s = setup({ templateBindings: 'const submittingEForms = 1, pendingEFormSubmits = 2, eformNativeSubmit = 3;\n' });
  s.form.submit(); s.form.submit(); assert.equal(s.submitted(), 1);
});
test('canceled navigation permits a subsequent actual submission', () => {
  const s = setup(); s.form.submit(); s.listeners.navigateerror();
  assert.equal(s.button.disabled, false);
  assert.equal(s.hidden(), 1);
  s.form.submit(); assert.equal(s.submitted(), 2);
});
test('legacy unload cancellation recovers while ordinary navigation remains guarded', () => {
  const s = setup({ navigation: false }); s.form.submit();
  s.listeners.beforeunload({ defaultPrevented: false, returnValue: '' }); s.flush();
  s.form.submit(); assert.equal(s.submitted(), 1);
  s.listeners.beforeunload({ defaultPrevented: true, returnValue: '' }); s.flush();
  assert.equal(s.button.disabled, false);
  s.form.submit(); assert.equal(s.submitted(), 2);
});
