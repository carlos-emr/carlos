/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');

function fixture({ owned = true, removed = true, childrenRemoved = true } = {}) {
  const events = [];
  return {
    events,
    browser: { async close() { events.push('close browser'); } },
    sql: {
      value(query) {
        if (query.includes('last_name=')) return owned ? '1' : '0';
        if (query.includes('casemgmt_note_lock')) return childrenRemoved ? '0' : '1';
        return removed ? '0' : '1';
      },
      execute(query) {
        if (query.startsWith('DELETE FROM demographic WHERE')) {
          events.push('delete patient');
        } else {
          for (const table of ['casemgmt_note_lock', 'casemgmt_tmpsave', 'measurementsDeleted']) {
            assert.ok(query.includes(`DELETE FROM ${table} WHERE`), `Missing owned ${table} cleanup`);
          }
          assert.ok(!query.includes('DELETE FROM demographic WHERE'), 'Parent deletion must follow child verification');
          events.push('delete chart support rows');
        }
      },
      dispose() { events.push('dispose credentials'); },
    },
    patient: '42', marker: 'FAKE-PW-owned', cleanups: [],
  };
}

test('cleanup closes sessions before deleting children and removes the parent last', async () => {
  const f = fixture();
  f.cleanups.push(() => f.events.push('child one'), () => f.events.push('child two'));
  await cleanupOwnedWorkflow(f);
  assert.deepEqual(f.events, ['close browser', 'child two', 'child one', 'delete chart support rows', 'delete patient', 'dispose credentials']);
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

test('silently retained chart support rows fail cleanup before deleting the patient', async () => {
  const f = fixture({ childrenRemoved: false });
  await assert.rejects(cleanupOwnedWorkflow(f), /chart support rows were not removed/);
  assert.deepEqual(f.events, ['close browser', 'delete chart support rows', 'dispose credentials']);
});
