/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const { createRecorder } = require('./lib/playwright-harness');
const { assertExpectedRefusals } = require('./document-add-link-playwright-checks');
const manifest = require('./playwright-suite.json');
const refusal = [{ layer: 'front-door', pageLabel: 'reject-test' }];
const submitUrl = 'https://127.0.0.1/carlos/documentManager/addLink';

function expectedRecorder() {
  const recorder = createRecorder();
  recorder.badResponses.push({ label: 'reject-test', status: 403, method: 'POST', url: submitUrl });
  recorder.consoleIssues.push({ label: 'reject-test', type: 'error',
    text: 'Failed to load resource: the server responded with a status of 403 (Forbidden)',
    location: { url: submitUrl } });
  return recorder;
}

test('expected WAF refusal exempts only its 403 submission signals', () => {
  assert.doesNotThrow(() => assertExpectedRefusals(expectedRecorder(), refusal));
});

test('a runtime exception on a WAF probe page still fails the check', () => {
  const recorder = expectedRecorder();
  recorder.pageErrors.push({ label: 'reject-test', text: 'Unexpected runtime exception' });
  assert.throws(() => assertExpectedRefusals(recorder, refusal), /runtime exception/);
});

test('an unrelated failure on a WAF probe page still fails the check', () => {
  const recorder = expectedRecorder();
  recorder.badResponses.push({ label: 'reject-test', status: 500, method: 'GET', url: submitUrl });
  assert.throws(() => assertExpectedRefusals(recorder, refusal), /HTTP 500/);
});

test('the same 403 from an unapproved page or endpoint is not ignored', () => {
  for (const change of [{ label: 'other-page' }, { url: 'https://127.0.0.1/carlos/other' }]) {
    const recorder = expectedRecorder();
    Object.assign(recorder.badResponses[0], change);
    assert.throws(() => assertExpectedRefusals(recorder, refusal), /HTTP 403/);
  }
});

test('document Add Link runs in both core and front-door suites', () => {
  const entry = manifest.checks.find(check => check.name === 'document-add-link');
  assert.ok(entry.tiers.includes('core'));
  assert.ok(entry.tiers.includes('front-door'));
});
