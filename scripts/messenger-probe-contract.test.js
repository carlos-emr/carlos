/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { createRecorder } = require('./lib/playwright-harness');
const { assertExpectedProbeResponses } = require('./messenger-group-admin-playwright-checks');

const url = 'https://localhost/carlos/messenger?method=add&member=101-0-1&group=7';
const expected = [{ method: 'POST', url, status: 409, count: 1 }];
function fixture() {
  const recorder = createRecorder();
  recorder.badResponses.push({ label: 'duplicate-probe', method: 'POST', url, status: 409, resourceType: 'xhr' });
  recorder.consoleIssues.push({ label: 'duplicate-probe', type: 'error',
    text: 'Failed to load resource: the server responded with a status of 409 (Conflict)', location: { url } });
  return recorder;
}

test('an exact expected response and its browser diagnostic pass without erasing the audit record', () => {
  const recorder = fixture();
  const before = structuredClone(recorder);
  assert.doesNotThrow(() => assertExpectedProbeResponses(recorder, expected));
  assert.deepEqual(recorder, before);
});

for (const [label, change] of [
  ['different URL', r => { r.badResponses[0].url += '&extra=1'; }],
  ['different HTTP method', r => { r.badResponses[0].method = 'GET'; }],
  ['different status', r => { r.badResponses[0].status = 500; }],
  ['missing response', r => { r.badResponses = []; }],
  ['extra response', r => { r.badResponses.push({ ...r.badResponses[0] }); }],
  ['other console error', r => { r.consoleIssues[0].text = 'Application failed'; }],
  ['console diagnostic from another URL', r => { r.consoleIssues[0].location.url += '&extra=1'; }],
  ['extra console diagnostic', r => { r.consoleIssues.push({ ...r.consoleIssues[0] }); }],
  ['failed request', r => { r.requestFailures.push({ label: 'duplicate-probe', resourceType: 'xhr', errorText: 'net::ERR_FAILED' }); }],
  ['unexpected dialog', r => { r.unexpectedDialogs.push({ label: 'duplicate-probe', type: 'confirm' }); }],
  ['JavaScript exception', r => { r.pageErrors.push({ label: 'duplicate-probe', text: 'TypeError: failed' }); }],
]) {
  test(`the deliberate-response contract still rejects: ${label}`, () => {
    const recorder = fixture();
    change(recorder);
    assert.throws(() => assertExpectedProbeResponses(recorder, expected));
  });
}

test('an explicitly expected GET failure and its diagnostic pass', () => {
  const recorder = fixture();
  recorder.badResponses[0].method = 'GET';
  assert.doesNotThrow(() => assertExpectedProbeResponses(recorder, [{ ...expected[0], method: 'GET' }]));
});
