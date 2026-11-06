/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {workflow} = require('./lab-upload-playwright-checks');
const {cleanupOwnedWorkflow} = require('./lib/workflow-session');

async function fixture(t, type, fault) {
  const settings = {EXCLUSIVE: '1', CARLOS_DISPOSABLE_VM: 'true', LAB_UPLOAD_DOCUMENT_STORE: undefined};
  const previousSettings = {};
  for (const [name, value] of Object.entries(settings)) {
    previousSettings[name] = process.env[name];
    if (value === undefined) delete process.env[name];
    else process.env[name] = value;
  }
  t.mock.method(console, 'warn', () => {});
  const sources = new Set(['100', '101']);
  // Source 100 shares a numeric ID with an older lock owned by another lab type.
  const locks = new Set(['100', '999']);
  let failLockDelete = fault === 'delete';
  let patientPresent = true;
  const cleanups = [];
  const sql = {
    rows(query) {
      if (query === 'SELECT lab_no FROM providerLabRoutingLock ORDER BY lab_no') {
        return [...locks].map(id => [id]);
      }
      if (query.includes('SELECT lab_no FROM providerLabRoutingLock WHERE')) {
        const ids = query.match(/IN \(([\d,]+)\)/)[1].split(',');
        return [...locks].filter(id => ids.includes(id)).sort((a, b) => Number(a) - Number(b)).map(id => [id]);
      }
      if (query.includes('SELECT id FROM labPatientPhysicianInfo')) {
        return type === 'CML' ? [...sources].map(id => [id]) : [];
      }
      if (query.includes('SELECT lab_no FROM hl7TextInfo')) {
        return type === 'HL7' ? [...sources].map(id => [id]) : [];
      }
      if (query.includes('SELECT measurement_id FROM measurementsExt')) return [];
      throw new Error(`Unexpected row query: ${query}`);
    },
    value(query) {
      if (/FROM demographic\s/.test(query)) return patientPresent ? '1' : '0';
      if (query.includes('(SELECT COUNT(*) FROM hl7TextInfo')) return String(sources.size);
      // No checksum or chart-support rows are needed for this teardown probe.
      if (query.includes('FROM fileUploadCheck') || query.includes('casemgmt_note_lock')) return '0';
      throw new Error(`Unexpected value query: ${query}`);
    },
    execute(query) {
      for (const statement of query.split(';').map(value => value.trim())) {
        assert.match(statement, /^DELETE FROM /);
        if (statement.startsWith('DELETE FROM providerLabRoutingLock ')) {
          if (failLockDelete) {
            failLockDelete = false;
            throw new Error('synthetic routing lock delete failure');
          }
          for (const id of statement.match(/IN \(([\d,]+)\)/)[1].split(',')) locks.delete(id);
        }
        if (statement.startsWith(type === 'CML' ? 'DELETE FROM labPatientPhysicianInfo ' : 'DELETE FROM hl7TextInfo ')) {
          sources.clear();
        }
        if (statement.startsWith('DELETE FROM demographic ')) patientPresent = false;
      }
    },
    dispose() {},
  };
  const marker = 'FAKE-PW-lock-cleanup';
  t.after(async () => {
    // Also remove the workflow's temporary file if an assertion interrupts the test.
    failLockDelete = false;
    locks.add('100');
    try {
      if (cleanups.length) await cleanups[0]();
    } finally {
      for (const [name, value] of Object.entries(previousSettings)) {
        if (value === undefined) delete process.env[name];
        else process.env[name] = value;
      }
    }
  });
  await assert.rejects(workflow({sql, patient: '42', marker, config: {},
    cleanup(fn) {cleanups.push(fn);},
    step() {throw new Error('stop before browser');},
  }), /stop before browser/);
  assert.equal(cleanups.length, 1);
  // The real workflow registered its cleanup before the synthetic upload acquired a lock.
  locks.add('101');
  if (fault === 'borrowed') locks.delete('100');
  return {sources, locks, patientPresent: () => patientPresent,
    cleanup: () => cleanupOwnedWorkflow({sql, patient: '42', marker, cleanups})};
}

for (const type of ['HL7', 'CML']) {
  test(`${type} lock deletion failure retains source IDs and the patient until cleanup can retry`, async t => {
    const f = await fixture(t, type, 'delete');
    await assert.rejects(f.cleanup(), /synthetic routing lock delete failure/);
    assert.deepEqual([...f.sources], ['100', '101'], 'The accession must still identify the owned locks');
    assert.equal(f.patientPresent(), true, 'A child cleanup failure must retain its parent fixture');
    await f.cleanup();
    assert.deepEqual([...f.sources], []);
    assert.deepEqual([...f.locks], ['100', '999'], 'Remove the introduced lock and preserve borrowed/foreign locks');
    assert.equal(f.patientPresent(), false);
  });

  test(`${type} missing borrowed lock refuses source deletion and cannot become a successful retry`, async t => {
    const f = await fixture(t, type, 'borrowed');
    await assert.rejects(f.cleanup(), /routing lock presence was not restored/);
    await assert.rejects(f.cleanup(), /routing lock presence was not restored/);
    assert.deepEqual([...f.sources], ['100', '101']);
    assert.equal(f.patientPresent(), true);
    f.locks.add('100'); // Reconcile the borrowed row before retrying owned cleanup.
    await f.cleanup();
    assert.deepEqual([...f.locks].sort(), ['100', '999']);
    assert.deepEqual([...f.sources], []);
    assert.equal(f.patientPresent(), false);
  });
}
