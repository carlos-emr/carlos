/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { markEformInstances, removeEformInstancesSince } = require('./lib/eform-instance-residue');

/*
 * lib/eform-instance-residue.js removes the eForm instances a check saved for a demo patient it keeps (the Rich Text Letter
 * attachment checks assert that patient's documents, labs and reports, so they cannot use an owned one). These tests give it a
 * recording stub of the harness client and pin WHICH rows it may touch: the patient's instances above the mark, their values, and
 * the attachment rows above the id mark (the demo's stale attachment rows are older than any mark).
 */

function stubSql({ fdid = '1238', docs = '4676', ids = ['1239', '1240'], left = '0' } = {}) {
  const executed = [];
  const queries = [];
  return {
    executed, queries,
    value(query) {
      queries.push(query);
      if (/MAX\(fdid\), 0\) FROM eform_data/.test(query)) return fdid;
      if (/MAX\(id\), 0\) FROM EFormDocs/.test(query)) return docs;
      return left;
    },
    rows(query) { queries.push(query); return ids.map((id) => [id]); },
    execute(query) { executed.push(query); },
  };
}

test('shouldTakeTheInstanceAndAttachmentMarks_beforeTheCheckSavesAnything', () => {
  assert.deepEqual(markEformInstances(stubSql()), { fdid: 1238, docs: 4676 });
});

test('shouldFail_whenAMarkIsNotReadable', () => {
  const unreadable = stubSql({ fdid: 'x' });
  assert.throws(() => markEformInstances(unreadable), /mark was not readable/);
});

test('shouldDeleteOnlyThePatientsInstancesAboveTheMark_withTheirValues', () => {
  const sql = stubSql();
  removeEformInstancesSince(sql, { fdid: 1238, docs: 4676 }, '1');
  assert.match(sql.queries[0], /SELECT fdid FROM eform_data WHERE demographic_no=1 AND fdid > 1238/, 'the instances are found by patient and above the mark');
  const statements = sql.executed.join('\n');
  assert.match(statements, /DELETE FROM eform_values WHERE fdid IN \(1239,1240\)/);
  assert.match(statements, /DELETE FROM EFormDocs WHERE fdid IN \(1239,1240\) AND id > 4676/,
    'a stale demo attachment row an instance inherited is older than the mark and is not the run\'s to delete');
  assert.match(statements, /DELETE FROM eform_data WHERE fdid IN \(1239,1240\) AND demographic_no=1/);
  assert.ok(statements.indexOf('DELETE FROM eform_values') < statements.indexOf('DELETE FROM eform_data'), 'values and attachments go while the instances exist');
  assert.doesNotMatch(statements, /DELETE FROM eform WHERE/, 'the template is not an instance');
});

test('shouldTouchNothing_whenThePatientSavedNothingAfterTheMark', () => {
  const sql = stubSql({ ids: [] });
  removeEformInstancesSince(sql, { fdid: 1238, docs: 4676 }, '1');
  assert.deepEqual(sql.executed, []);
});

test('shouldDropAValueThatIsNotAnId_andRefuseABadPatientOrMark', () => {
  const sql = stubSql({ ids: ['1239', 'x', '0', '9; DROP TABLE eform_data'] });
  removeEformInstancesSince(sql, { fdid: 1238, docs: 4676 }, '1');
  assert.match(sql.executed.join('\n'), /fdid IN \(1239\)/);
  assert.throws(() => removeEformInstancesSince(stubSql(), { fdid: 1, docs: 1 }, '1; DROP'), /demographic_no/);
  assert.throws(() => removeEformInstancesSince(stubSql(), { fdid: '1', docs: 1 }, '1'), /not a mark/);
});

test('shouldFail_whenAnInstanceOrItsRowsAreStillThereAfterTheDeletes', () => {
  assert.throws(() => removeEformInstancesSince(stubSql({ left: '3' }), { fdid: 1238, docs: 4676 }, '1'), /were not removed/);
});
