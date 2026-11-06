/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {workflow} = require('./lab-upload-playwright-checks');
const {cleanupOwnedWorkflow} = require('./lib/workflow-session');

async function fixture(t, type, fault) {
  const archiveStore = fault.startsWith('archive-')
    ? fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-cleanup-test-')) : undefined;
  const settings = {EXCLUSIVE: '1', CARLOS_DISPOSABLE_VM: 'true', LAB_UPLOAD_DOCUMENT_STORE: archiveStore};
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
  const measurementFault = fault.startsWith('measurement-');
  const measurements = new Set(measurementFault ? ['501', '999'] : []);
  const measurementLinks = new Map(measurementFault ? [['501', '100'], ['999', '999']] : []);
  let measurementFailures = fault === 'measurement-delete' ? 2 : measurementFault ? 1 : 0;
  let failFinalCheck = fault === 'archive-final-check';
  let uploadStamp;
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
      if (query.includes('SELECT measurement_id FROM measurementsExt')) {
        const labs = query.match(/val IN \(([^)]+)\)/)[1].split(',').map(value => value.replaceAll("'", ''));
        return [...measurementLinks].filter(([, lab]) => labs.includes(lab)).map(([id]) => [id]);
      }
      throw new Error(`Unexpected row query: ${query}`);
    },
    value(query) {
      if (/FROM demographic\s/.test(query)) return patientPresent ? '1' : '0';
      if (query.includes('(SELECT COUNT(*) FROM hl7TextInfo')) {
        if (failFinalCheck) {
          failFinalCheck = false;
          throw new Error('synthetic final residue verification failure');
        }
        return String(sources.size);
      }
      if (query.includes('(SELECT COUNT(*) FROM measurementsExt')) {
        const ids = query.match(/IN \(([\d,]+)\)/)[1].split(',');
        return String(ids.filter(id => measurements.has(id)).length
          + ids.filter(id => measurementLinks.has(id)).length);
      }
      // No checksum or chart-support rows are needed for this teardown probe.
      if (query.includes('FROM fileUploadCheck')) {
        const stamp = query.match(/lab-upload-probe-([A-F0-9]{8})\.hl7/);
        if (stamp) uploadStamp = stamp[1];
        return '0';
      }
      if (query.includes('casemgmt_note_lock')) return '0';
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
        if (statement.startsWith('DELETE FROM measurementsExt ')) {
          for (const id of statement.match(/IN \(([\d,]+)\)/)[1].split(',')) measurementLinks.delete(id);
        }
        if (statement.startsWith('DELETE FROM measurements ')) {
          if (measurementFailures && fault === 'measurement-delete') {
            measurementFailures -= 1;
            throw new Error('synthetic measurement delete failure');
          }
          if (measurementFailures && fault === 'measurement-noop') {
            measurementFailures -= 1;
            continue;
          }
          for (const id of statement.match(/IN \(([\d,]+)\)/)[1].split(',')) measurements.delete(id);
          if (measurementFailures && fault === 'measurement-ack') {
            measurementFailures -= 1;
            throw new Error('synthetic lost measurement delete acknowledgement');
          }
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
    measurementFailures = 0;
    failFinalCheck = false;
    locks.add('100');
    try {
      if (archiveStore && uploadStamp) {
        // A deliberately missing archive must not prevent the test's own teardown.
        fs.writeFileSync(path.join(archiveStore, `LabUpload.lab-upload-probe-${uploadStamp}.hl7.999`), 'synthetic');
      }
      if (cleanups.length) await cleanups[0]();
    } finally {
      if (archiveStore) fs.rmSync(archiveStore, {recursive: true, force: true});
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
  if (archiveStore) {
    assert.match(uploadStamp, /^[A-F0-9]{8}$/);
    const foreignStamp = uploadStamp === '00000000' ? '11111111' : '00000000';
    fs.writeFileSync(path.join(archiveStore, `LabUpload.lab-upload-probe-${foreignStamp}.hl7.1`), 'foreign');
    if (fault !== 'archive-missing') {
      fs.writeFileSync(path.join(archiveStore, `LabUpload.lab-upload-probe-${uploadStamp}.hl7.1`), 'synthetic');
    }
  }
  return {sources, locks, measurements, measurementLinks, archiveStore, uploadStamp, patientPresent: () => patientPresent,
    cleanup: () => cleanupOwnedWorkflow({sql, patient: '42', marker, cleanups})};
}

test('measurement deletion retries retain owned IDs after link deletion and preserve foreign rows', async t => {
  const f = await fixture(t, 'HL7', 'measurement-delete');
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await assert.rejects(f.cleanup(), /synthetic measurement delete failure/);
    assert.equal(f.patientPresent(), true, 'Keep the patient while its lab measurement survives');
    assert.deepEqual([...f.sources], ['100', '101']);
    assert.deepEqual([...f.measurementLinks], [['999', '999']], 'The owned discovery link has already gone');
    assert.deepEqual([...f.measurements], ['501', '999']);
  }
  await f.cleanup();
  assert.deepEqual([...f.measurements], ['999']);
  assert.deepEqual([...f.measurementLinks], [['999', '999']]);
  assert.equal(f.patientPresent(), false);
});

test('confirmed archive removal survives a later database verification failure and retry', async t => {
  const f = await fixture(t, 'HL7', 'archive-final-check');
  await assert.rejects(f.cleanup(), /synthetic final residue verification failure/);
  assert.equal(f.patientPresent(), true);
  assert.deepEqual([...f.sources], []);
  const remaining = fs.readdirSync(f.archiveStore);
  assert.equal(remaining.length, 1);
  assert.ok(!remaining[0].includes(f.uploadStamp), 'Only the foreign archived upload remains');
  await f.cleanup();
  assert.equal(f.patientPresent(), false);
  assert.deepEqual(fs.readdirSync(f.archiveStore), remaining);
});

test('missing initial archive cannot become confirmed removal on a retry', async t => {
  const f = await fixture(t, 'HL7', 'archive-missing');
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await assert.rejects(f.cleanup(), /No archived .* file was found/);
    assert.equal(f.patientPresent(), true);
  }
  fs.writeFileSync(path.join(f.archiveStore, `LabUpload.lab-upload-probe-${f.uploadStamp}.hl7.1`), 'synthetic');
  await f.cleanup();
  assert.equal(f.patientPresent(), false);
  assert.equal(fs.readdirSync(f.archiveStore).length, 1, 'The foreign archived upload is preserved');
});

test('lost measurement DELETE acknowledgement remains safe to retry', async t => {
  const f = await fixture(t, 'HL7', 'measurement-ack');
  await assert.rejects(f.cleanup(), /synthetic lost measurement delete acknowledgement/);
  assert.equal(f.patientPresent(), true);
  assert.deepEqual([...f.measurements], ['999']);
  await f.cleanup();
  assert.deepEqual([...f.measurements], ['999']);
  assert.deepEqual([...f.measurementLinks], [['999', '999']]);
  assert.equal(f.patientPresent(), false);
});

test('retained measurement rows refuse source and parent deletion until verified cleanup', async t => {
  const f = await fixture(t, 'HL7', 'measurement-noop');
  await assert.rejects(f.cleanup(), /synthetic lab measurements were not all removed/);
  assert.equal(f.patientPresent(), true);
  assert.deepEqual([...f.sources], ['100', '101']);
  await f.cleanup();
  assert.deepEqual([...f.measurements], ['999']);
  assert.deepEqual([...f.measurementLinks], [['999', '999']]);
  assert.equal(f.patientPresent(), false);
});

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
