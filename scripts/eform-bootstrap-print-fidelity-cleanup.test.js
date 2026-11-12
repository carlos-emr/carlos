/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./lib/playwright-harness');
const { cleanupResources } = require('./eform-bootstrap-print-fidelity-playwright-checks');

const marker = 'FAKE-PW0123456789abcdef';
const formName = `${marker} legacy layout fidelity`;

function fixture(failures = {}) {
  const calls = [];
  const templates = new Set([formName, 'unrelated template']);
  const patients = new Map([['42', marker], ['43', 'unrelated patient']]);
  const browser = { async close() { calls.push('browser'); if (failures.browser) throw failures.browser; } };
  const sql = {
    execute(query) {
      if (query.startsWith('DELETE FROM eform WHERE')) {
        calls.push('template');
        assert.equal(query, `DELETE FROM eform WHERE form_name=${h.sqlString(formName)}`);
        if (failures.template) throw failures.template;
        if (!failures.retainedTemplate) templates.delete(formName);
      } else if (query.startsWith('DELETE FROM demographic WHERE')) {
        assert.equal(query, `DELETE FROM demographic WHERE demographic_no=42 AND last_name='${marker}'`);
        if (patients.get('42') === marker) patients.delete('42');
      } else {
        assert.match(query, /WHERE (demographic_no|demographicNo)=42/);
        assert.doesNotMatch(query, /\b43\b/);
      }
    },
    value(query) {
      if (query.startsWith('SELECT COUNT(*) FROM eform WHERE')) {
        assert.equal(query, `SELECT COUNT(*) FROM eform WHERE form_name=${h.sqlString(formName)}`);
        return templates.has(formName) ? '1' : '0';
      }
      if (query.includes(`demographic_no=42 AND last_name='${marker}'`)) {
        calls.push('patient');
        if (failures.patient) throw failures.patient;
        return patients.get('42') === marker ? '1' : '0';
      }
      if (query === 'SELECT COUNT(*) FROM demographic WHERE demographic_no=42') return patients.has('42') ? '1' : '0';
      assert.match(query, /FROM casemgmt_note WHERE demographic_no=42/);
      return '0';
    },
    dispose() { calls.push('credentials'); if (failures.credentials) throw failures.credentials; },
  };
  return { state: { browser, sql, formName, patient: '42', marker, ownedRows: {} }, calls, templates, patients };
}

test('cleanup removes only its exact template and ownership-checked patient', async () => {
  const f = fixture();
  await cleanupResources(f.state);
  assert.deepEqual(f.calls, ['browser', 'template', 'patient', 'credentials']);
  assert.deepEqual([...f.templates], ['unrelated template']);
  assert.deepEqual([...f.patients], [['43', 'unrelated patient']]);
});

test('every finalizer runs and all original cleanup errors survive simultaneous failures', async () => {
  const failures = Object.fromEntries(['browser', 'template', 'patient', 'credentials']
    .map(label => [label, new Error(`${label} failed`)]));
  const f = fixture(failures);
  await assert.rejects(cleanupResources(f.state), error => {
    assert.ok(error instanceof AggregateError);
    assert.deepEqual(error.errors, Object.values(failures));
    assert.equal(error.cause, failures.browser);
    for (const failure of Object.values(failures)) assert.ok(error.message.includes(failure.message));
    return true;
  });
  assert.deepEqual(f.calls, ['browser', 'template', 'patient', 'credentials']);
});

test('failed template verification still removes the owned patient and disposes credentials', async () => {
  const f = fixture({ retainedTemplate: true });
  await assert.rejects(cleanupResources(f.state), /template this run inserted was not removed/);
  assert.deepEqual(f.calls, ['browser', 'template', 'patient', 'credentials']);
  assert.equal(f.patients.has('42'), false);
});

test('changed patient ownership is refused while other finalizers still run', async () => {
  const f = fixture();
  f.patients.set('42', 'another run');
  await assert.rejects(cleanupResources(f.state), /Patient fixture ownership changed/);
  assert.deepEqual(f.calls, ['browser', 'template', 'patient', 'credentials']);
  assert.equal(f.patients.get('42'), 'another run');
  assert.equal(f.templates.has(formName), false);
});

test('a browser close failure preserves the workflow error and still removes every owned fixture', async () => {
  const f = fixture({ browser: new Error('browser shutdown failed') });
  const processRef = { env: {}, on() {}, removeListener() {} };
  const result = await h.runCheck({
    name: 'fidelity-cleanup', processRef, stdout: { log() {} },
    run: async () => { throw new Error('original PDF geometry mismatch'); },
    cleanup: () => cleanupResources(f.state),
  });
  assert.equal(result.outcome, 'FAIL');
  assert.equal(result.cleanupFailed, true);
  assert.match(result.detail, /^original PDF geometry mismatch; cleanup failed: browser: browser shutdown failed$/);
  assert.equal(processRef.exitCode, 1);
  assert.deepEqual(f.calls, ['browser', 'template', 'patient', 'credentials']);
  assert.equal(f.templates.has(formName), false);
  assert.equal(f.patients.has('42'), false);
});
