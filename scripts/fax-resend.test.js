/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/manageFaxes.jsp'), 'utf8');
const start = jsp.indexOf('function resend(');
const end = jsp.indexOf('function cancel(', start);
assert.ok(start >= 0 && end > start);
function run(answer) {
  const requests = [], alerts = [];
  const element = { attr: () => '/admin/ManageFaxes.do', prop: () => element };
  const jquery = () => element;
  jquery.ajax = (request) => requests.push(request);
  const context = vm.createContext({ $: jquery, prompt: () => answer,
    alert: (message) => alerts.push(message), manageFaxesResendPrompt: 'Resend',
    manageFaxesFaxNumbersInvalid: 'Invalid' });
  vm.runInContext(jsp.slice(start, end), context);
  context.resend(42, '+442079460100', 'status42');
  return { requests, alerts };
}
for (const [input, normalized] of [
  ['+44 20 7946 0100', '+442079460100'], ['011442079460100', '011442079460100'],
  ['(250) 555-1234', '2505551234'], ['1234567', '1234567'],
  ['+123456789012345', '+123456789012345'],
]) {
  test(`resend submits ${input} as an encoded form field without losing international intent`, () => {
    const { requests, alerts } = run(input);
    assert.equal(alerts.length, 0);
    assert.equal(requests.length, 1);
    assert.equal(requests[0].method, 'POST');
    assert.equal(typeof requests[0].data, 'object');
    assert.equal(requests[0].data.faxNumber, normalized);
    assert.equal(new URLSearchParams(requests[0].data).get('faxNumber'), normalized);
    if (normalized.startsWith('+')) assert.match(new URLSearchParams(requests[0].data).toString(), /faxNumber=%2B/);
  });
}
for (const input of [null, '', '123', '+1234567890123456', '1234567abc', '++1234567']) {
  test(`resend does not submit cancelled or malformed input ${JSON.stringify(input)}`, () => {
    const { requests, alerts } = run(input);
    assert.equal(requests.length, 0);
    assert.equal(alerts.length, input === null ? 0 : 1);
  });
}
