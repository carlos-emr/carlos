/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');

function fixture({ owned = true, removed = true } = {}) {
  const events = [];
  let reads = 0;
  return {
    events,
    browser: { async close() { events.push('close browser'); } },
    sql: {
      value() { return ++reads === 1 ? (owned ? '1' : '0') : (removed ? '0' : '1'); },
      execute() { events.push('delete patient'); },
      dispose() { events.push('dispose credentials'); },
    },
    patient: '42', marker: 'FAKE-PW-owned', cleanups: [],
  };
}

test('cleanup closes sessions before deleting children and removes the parent last', async () => {
  const f = fixture();
  f.cleanups.push(() => f.events.push('child one'), () => f.events.push('child two'));
  await cleanupOwnedWorkflow(f);
  assert.deepEqual(f.events, ['close browser', 'child two', 'child one', 'delete patient', 'dispose credentials']);
});

test('lost patient ownership refuses all child and parent deletion', async () => {
  const f = fixture({ owned: false });
  f.cleanups.push(() => f.events.push('delete child'));
  await assert.rejects(cleanupOwnedWorkflow(f), /ownership changed/);
  assert.deepEqual(f.events, ['close browser', 'dispose credentials']);
});

test('failed child cleanup retains the patient, tries remaining cleanup, and fails the check', async () => {
  const f = fixture();
  f.cleanups.push(() => f.events.push('remaining child'), () => { throw new Error('child delete failed'); });
  await assert.rejects(cleanupOwnedWorkflow(f), /child delete failed/);
  assert.deepEqual(f.events, ['close browser', 'remaining child', 'dispose credentials']);
});

test('a delete which silently changes no rows cannot report successful cleanup', async () => {
  const f = fixture({ removed: false });
  await assert.rejects(cleanupOwnedWorkflow(f), /owned patient was not removed/);
  assert.equal(f.events.at(-1), 'dispose credentials');
});

test('directory-only workflows clean up their rows without needing a patient', async () => {
  const f = fixture();
  f.patient = undefined;
  f.cleanups.push(() => f.events.push('directory rows'));
  await cleanupOwnedWorkflow(f);
  assert.deepEqual(f.events, ['close browser', 'directory rows', 'dispose credentials']);
});
