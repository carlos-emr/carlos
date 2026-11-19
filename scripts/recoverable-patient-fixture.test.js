/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {createRecoverablePatientFixture} = require('./lib/owned-patient');
const {cleanupOwnedWorkflow} = require('./lib/workflow-session');

const marker = 'FAKE-PW0123456789abcdef';
const provider = '999998';
const id = '418';

function patientSql(loseReply) {
  const patients = new Map([['417', ['417', 'Unrelated', provider]]]);
  const sql = {
    rows(query, parameters) {
      assert.match(query, /FROM demographic WHERE last_name=\?$/);
      assert.deepEqual(parameters, [marker]);
      return patients.has(id) ? [patients.get(id)] : [];
    },
    value(query, parameters) {
      if (query.startsWith('INSERT INTO demographic')) {
        assert.ok(query.includes(marker));
        patients.set(id, [id, 'Workflow', provider]);
        if (loseReply) throw new Error('Synthetic lost insert reply');
        return id;
      }
      if (query.endsWith('last_name=?')) return String(this.rows(query, parameters).length);
      if (query.includes('information_schema.tables')) return '1';
      if (query.includes('AND last_name=')) return patients.has(id) ? '1' : '0';
      if (query === 'SELECT COUNT(*) FROM demographic WHERE demographic_no=418') return patients.has(id) ? '1' : '0';
      assert.match(query, /^SELECT \(SELECT COUNT\(\*\) FROM casemgmt_note/);
      return '0';
    },
    execute(query) {
      if (query.startsWith('DELETE FROM demographic WHERE')) {
        assert.equal(query, `DELETE FROM demographic WHERE demographic_no=${id} AND last_name='${marker}'`);
        patients.delete(id);
      } else {
        assert.match(query, /^DELETE FROM casemgmt_issue_notes/);
      }
    },
    dispose() {},
  };
  return {patients, sql};
}

test('cleanup finds a committed patient after the insert reply is lost', async () => {
  const {patients, sql} = patientSql(true);
  const patientFixture = createRecoverablePatientFixture({sql, marker, provider});

  assert.throws(() => patientFixture.create(), /Synthetic lost insert reply/);
  assert.equal(patients.size, 2);
  await cleanupOwnedWorkflow({sql, marker, patientFixture, cleanups: []});
  assert.deepEqual([...patients.keys()], ['417']);
});

test('cleanup keeps ownership by confirmed ID and marker after the patient is edited', async () => {
  const {patients, sql} = patientSql(false);
  const patientFixture = createRecoverablePatientFixture({sql, marker, provider});
  const patient = patientFixture.create();
  patients.set(patient, [patient, 'Updated', provider]);

  await cleanupOwnedWorkflow({sql, patient, marker, patientFixture, cleanups: []});
  assert.deepEqual([...patients.keys()], ['417']);
});
