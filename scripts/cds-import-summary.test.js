/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {webcrypto} = require('node:crypto');
const client = require('../src/main/webapp/js/demographic/cds-import.js');
const id = '11111111-1111-4111-8111-111111111111';
const clean = {cleanComplete: true, clinicalComplete: true, discoveryComplete: true, reportsDelivered: true,
  cleanupComplete: true, pendingUnits: 0, unresolvedReviewItems: 0, expectedPatients: 1, outcomes: {COMPLETE: 1}};

test('only reconciled clinical completion with delivered reports is styled as clean', () => {
  assert.equal(client.statusPresentation({id, state: 'COMPLETE'}, clean).clean, true);
  assert.equal(client.statusPresentation({id, state: 'ALREADY_COMPLETE'}, {...clean, outcomes: {ALREADY_COMPLETE: 1}}).clean, true);
});
for (const state of ['ACCEPTED', 'RUNNING', 'REFUSED', 'FAILED_BEFORE_WRITE', 'NOT_STARTED',
  'PARTIAL_COMMITTED', 'RECONCILIATION_REQUIRED', 'CANCELLED', 'DRY_RUN_COMPLETE']) {
  test(`${state} cannot imply clinical success even when a contradictory flag says clean`, () => {
    assert.equal(client.statusPresentation({id, state}, clean).clean, false);
  });
}
for (const changes of [{reportsDelivered: false}, {cleanupComplete: false}, {discoveryComplete: false},
  {clinicalComplete: false}, {pendingUnits: 1}, {unresolvedReviewItems: 1}, {expectedPatients: 2},
  {outcomes: {REFUSED: 1}}, {outcomes: {COMPLETE: 1, PARTIAL_COMMITTED: 1}}, {outcomes: undefined},
  {outcomes: {COMPLETE: '1'}}, {outcomes: {COMPLETE: -1}}]) {
  test(`incomplete, unknown or contradictory accounting ${JSON.stringify(changes)} cannot be clean`, () => {
    assert.equal(client.statusPresentation({id, state: 'COMPLETE'}, {...clean, ...changes}).clean, false);
  });
}
test('invalid or unavailable job status cannot be success', () => {
  assert.deepEqual(client.statusPresentation(null, clean), {state: 'UNAVAILABLE', clean: false, final: false});
});
test('only the durable operation terminal receipt stops polling', () => {
  for (const summary of [undefined, {}, clean, {reportsDelivered: true}, {reportDeliveryFailed: true},
    {cleanupComplete: true}, {operationTerminal: false}, {operationTerminal: 'true'}]) {
    assert.equal(client.mayStopPolling(summary), false);
  }
  assert.equal(client.mayStopPolling({...clean, operationTerminal: true}), true);
  assert.equal(client.mayStopPolling({operationTerminal: true, reportDeliveryFailed: true}), true);
});
test('history stores bounded opaque IDs, deduplicated, without source filenames', () => {
  const ids = Array.from({length: 25}, (_, n) => `${String(n).padStart(8, '0')}-1111-4111-8111-111111111111`);
  assert.equal(client.boundedIds([...ids, id, id, 'Patient-1971-03-04.xml']).length, 20);
  assert.deepEqual(client.boundedIds([id, id, 'not-a-run']), [id]);
});
test('effective options preserve nondefault bindings and reject unsafe synthetic merge', () => {
  const options = client.effectiveOptions({providerNo: '999998', sourceNamespace: 'clinic', sourceProfile: 'omd-5.2',
    programId: '2', facilityId: '3', dryRun: true, mergeExisting: true, matchProviderNames: false});
  assert.equal(options.mergeExisting, true); assert.equal(options.dryRun, true);
  assert.equal(options.matchProviderNames, false); assert.equal(options.programId, 2); assert.equal(options.facilityId, 3);
  assert.equal(Object.isFrozen(options), true);
  assert.throws(() => client.effectiveOptions({...options, synthetic: true, courseId: 4}), /IMPORT_OPTIONS_INVALID/);
});
test('real SHA-256 uses only the provided bounded blob', async () => {
  assert.equal(await client.sha256(new Blob(['abc']), webcrypto),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
});
test('resume verifies every prior chunk across bounded receipt pages', async () => {
  const block = 65536; const chunks = 101;
  const file = new Blob([Buffer.alloc(block * chunks, 7)]);
  Object.defineProperty(file, 'name', {value: 'source.xml'});
  const hash = await client.sha256(file.slice(0, block), webcrypto);
  const pages = [];
  const receipt = {fileName: file.name, expectedBytes: file.size, receivedBytes: file.size,
    chunkBytes: block, committedChunks: chunks};
  let verified = 0;
  const result = await client.verifyPrefix(file, receipt, async after => {
    pages.push(after);
    return Array.from({length: Math.min(100, chunks - after)}, (_, n) => ({index: after + n,
      offset: (after + n) * block, bytes: block, sha256: hash}));
  }, blob => client.sha256(blob, webcrypto), bytes => { verified = bytes; });
  assert.deepEqual(pages, [0, 100]); assert.equal(result, file.size); assert.equal(verified, file.size);
});
test('resume refuses changed bytes, missing receipts, gaps and mismatched source metadata', async () => {
  const file = new Blob([Buffer.alloc(65536, 1)]); Object.defineProperty(file, 'name', {value: 'source.xml'});
  const receipt = {fileName: file.name, expectedBytes: file.size, receivedBytes: file.size, chunkBytes: 65536, committedChunks: 1};
  const good = {index: 0, offset: 0, bytes: 65536, sha256: await client.sha256(file, webcrypto)};
  await assert.rejects(client.verifyPrefix(file, receipt, async () => [{...good, sha256: '0'.repeat(64)}], blob => client.sha256(blob, webcrypto)), /MISMATCH/);
  await assert.rejects(client.verifyPrefix(file, receipt, async () => []), /MISSING/);
  await assert.rejects(client.verifyPrefix(file, receipt, async () => [{...good, offset: 1}]), /CONFLICT/);
  await assert.rejects(client.verifyPrefix(file, {...receipt, fileName: 'other.xml'}, async () => [good]), /MISMATCH/);
});
