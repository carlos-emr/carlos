/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp', 'utf8');
const source = jsp.slice(jsp.indexOf('        function unlinkDemographic(labNo)'), jsp.indexOf('        function addComment('));

function fixture() {
  const prompts = [], answers = [], alerts = [], requests = [];
  let reloads = 0, result = {ok: true, success: false};
  const context = {
    prompt(message, initial) { prompts.push(initial); return answers.shift(); },
    alert(message) { alerts.push(message); }, console: {error() {}},
    getCsrfToken() { return 'synthetic token'; },
    top: {opener: {location: {reload() { reloads++; }}}},
    window: {location: {reload() { reloads++; }}},
    fetch(url, options) {
      requests.push(options);
      if (result instanceof Error) return Promise.reject(result);
      return Promise.resolve({ok: result.ok, json: async () => ({success: result.success})});
    }
  };
  vm.runInNewContext(source, context);
  return {context, prompts, answers, alerts, requests,
    result(value) { result = value; }, reloads() { return reloads; },
    async unlink(id, answer) { answers.push(answer); context.unlinkDemographic(id); await new Promise(setImmediate); }};
}

test('failed unlink reasons belong only to their own report', async () => {
  const f = fixture();
  await f.unlink(17, 'first reason');
  await f.unlink(23, 'second reason');
  await f.unlink(17, 'retry first');
  assert.deepEqual(f.prompts, ['Incorrect demographic', 'Incorrect demographic', 'first reason']);
  assert.equal(f.reloads(), 0);
  assert.ok(f.alerts.every(message => message.includes('retained on this page') && message.includes('Refreshing will clear')));
});

test('a successful unlink clears only that report reason and refreshes both views', async () => {
  const f = fixture();
  await f.unlink(17, 'keep this reason');
  f.result({ok: true, success: true});
  await f.unlink(23, 'completed reason');
  assert.equal(f.context.unlinkDemographic.reasons['17'], 'keep this reason');
  assert.equal(f.context.unlinkDemographic.reasons['23'], undefined);
  assert.equal(f.reloads(), 2);
  assert.deepEqual(Object.fromEntries(new URLSearchParams(f.requests[1].body)),
    {reason: 'completed reason', labNo: '23', 'CSRF-TOKEN': 'synthetic token'});
});

test('transport and HTTP failures retain the reason without reporting success', async () => {
  for (const failure of [new Error('synthetic transport failure'), {ok: false, success: true}]) {
    const f = fixture(); f.result(failure);
    await f.unlink(17, 'retained reason');
    assert.equal(f.context.unlinkDemographic.reasons['17'], 'retained reason');
    assert.equal(f.reloads(), 0);
    assert.equal(f.alerts.length, 1);
  }
});

test('cancelled or empty reasons do not send an unlink request', async () => {
  const f = fixture();
  await f.unlink(17, null);
  await f.unlink(17, '');
  assert.equal(f.requests.length, 0);
  assert.equal(f.context.unlinkDemographic.reasons['17'], undefined);
});
