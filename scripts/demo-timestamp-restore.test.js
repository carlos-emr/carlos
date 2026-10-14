/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { restore, snapshot } = require('./lib/demo-timestamp-restore');

/*
 * lib/demo-timestamp-restore.js puts back the audit columns the application moves on a demo prescription and pharmacy link that an Rx
 * check reprinted or switched. These tests give it a recording stub and pin which rows and which columns it may write.
 */

function stubSql({ scripts = [], links = [] } = {}) {
  const executed = [];
  const queries = [];
  return {
    executed, queries,
    rows(query) { queries.push(query); return /FROM prescription/.test(query) ? scripts : links; },
    execute(query) { executed.push(query); },
  };
}

test('shouldReadTheAuditColumnsOfThePatientsPrescriptionsAndPharmacyLinks_withANullFlag', () => {
  const sql = stubSql({ scripts: [['45', '', '1', '2024-04-02 22:38:16']], links: [['13', '2024-04-05 21:51:43']] });
  const state = snapshot(sql, '1');
  assert.deepEqual(state, { patient: '1', scripts: [['45', '', '1', '2024-04-02 22:38:16']], links: [['13', '2024-04-05 21:51:43']] });
  assert.match(sql.queries[0], /dates_reprinted IS NULL/, 'NULL and the text NULL are told apart by a flag');
  assert.match(sql.queries[0], /FROM prescription\s+WHERE demographic_no=1/);
  assert.match(sql.queries[1], /FROM demographicPharmacy WHERE demographic_no=1/);
});

test('shouldWriteBackOnlyTheAuditColumns_ofThePatientsRows_byTheirOwnKeys', () => {
  const sql = stubSql();
  restore(sql, { patient: '1', scripts: [['17', '', '1', '2023-12-08 15:11:07'], ['45', '2024-04-02 22:40:00;999998', '0', '2024-04-02 22:38:16']], links: [['13', '2024-04-05 21:51:43']] });
  assert.equal(sql.executed.length, 3);
  assert.match(sql.executed[0], /UPDATE prescription SET dates_reprinted=NULL, lastUpdateDate='2023-12-08 15:11:07'\s+WHERE script_no=17 AND demographic_no=1/,
    'a NULL that was NULL is put back as NULL, not as the text');
  assert.match(sql.executed[1], /dates_reprinted='2024-04-02 22:40:00;999998', lastUpdateDate='2024-04-02 22:38:16'\s+WHERE script_no=45 AND demographic_no=1/);
  assert.match(sql.executed[2], /UPDATE demographicPharmacy SET addDate='2024-04-05 21:51:43' WHERE id=13 AND demographic_no=1/);
  assert.doesNotMatch(sql.executed.join('\n'), /status|digital_signature_id|textView/, 'no other column is written');
});

test('shouldRefuse_aSnapshotRowThatIsNotAKeyAndATime_andAPatientThatIsNotANumber', () => {
  assert.throws(() => restore(stubSql(), { patient: '1', scripts: [['4; DROP', '', '1', '2024-04-02 22:38:16']], links: [] }), /script number and a time/);
  assert.throws(() => restore(stubSql(), { patient: '1', scripts: [['45', '', '1', 'yesterday']], links: [] }), /script number and a time/);
  assert.throws(() => restore(stubSql(), { patient: '1', scripts: [], links: [['x', '2024-04-05 21:51:43']] }), /id and a time/);
  assert.throws(() => snapshot(stubSql(), '1; DROP'), /not a number/);
});

test('shouldQuoteAReprintLogThatCarriesAQuote', () => {
  const sql = stubSql();
  restore(sql, { patient: '1', scripts: [['45', "it's;999998", '0', '2024-04-02 22:38:16']], links: [] });
  assert.match(sql.executed[0], /dates_reprinted='it''s;999998'/);
});
