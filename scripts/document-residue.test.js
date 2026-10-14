/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { markDocumentResidue, removeDocumentResidue } = require('./lib/document-residue');

/*
 * lib/document-residue.js removes the rows the application writes for a document a check owns (the chart note and its link, the
 * Inbox queue links, the routing lock). These tests give it a recording stub of the harness client: they pin WHICH rows it may
 * touch (the owned document numbers, written after the mark), not that MariaDB accepts the statements, which the checks prove.
 */

function stubSql({ floor = { casemgmt_note_link: 331, queue_document_link: 25 }, noteIds = [], left = '0' } = {}) {
  const executed = [];
  const queries = [];
  return {
    executed, queries,
    value(query) {
      queries.push(query);
      if (/DATE_FORMAT\(NOW\(\)/.test(query)) return '2026-10-09 10:00:00';
      const table = /MAX\(id\), 0\) FROM (\w+)/.exec(query);
      if (table) return String(floor[table[1]]);
      return left;
    },
    rows(query) { queries.push(query); return noteIds.map((id) => [id]); },
    execute(query) { executed.push(query); },
  };
}

test('shouldTakeTheMarkBeforeTheCheckWritesAnything', () => {
  assert.deepEqual(markDocumentResidue(stubSql()), { noteLinkFloor: 331, queueLinkFloor: 25, startedAt: '2026-10-09 10:00:00' });
  const unreadable = stubSql();
  unreadable.value = () => 'not a time';
  assert.throws(() => markDocumentResidue(unreadable), /database clock was not readable/);
});

test('shouldDeleteOnlyRowsOfTheOwnedNumbers_writtenAfterTheMark', () => {
  const sql = stubSql({ noteIds: ['881'] });
  const mark = markDocumentResidue(sql);
  removeDocumentResidue(sql, mark, ['88', '88', '97']);
  const statements = sql.executed.join('\n');
  assert.match(statements, /DELETE FROM casemgmt_note_link WHERE table_name=5 AND table_id IN \(88,97\) AND id > 331/,
    'a demo link row for a number the owned document reuses (finding 143) is older than the mark');
  assert.match(statements, /DELETE FROM queue_document_link WHERE document_id IN \(88,97\) AND id > 25/);
  assert.match(statements, /DELETE FROM providerLabRoutingLock WHERE lab_no IN \(88,97\) AND lastUpdateDate >= '2026-10-09 10:00:00'/,
    'a lock row that was already there is not the run\'s to delete');
  assert.match(statements, /DELETE FROM casemgmt_note WHERE note_id IN \(881\) AND provider_no='-1' AND note LIKE 'Document % created at %'/,
    'only the application\'s own "document created" note, never one a clinician wrote');
  assert.match(statements, /DELETE FROM casemgmt_note_ext WHERE note_id IN \(SELECT note_id FROM casemgmt_note WHERE note_id IN \(881\)\s+AND provider_no='-1' AND note LIKE 'Document % created at %'\)/,
    'the extension rows carry the same provider -1 / "document created" guard as the note, through the note they hang off');
});

test('shouldDeleteTheExtensionRows_beforeTheNoteTheyHangOff', () => {
  const sql = stubSql({ noteIds: ['881'] });
  removeDocumentResidue(sql, markDocumentResidue(sql), ['88']);
  const statements = sql.executed.join('\n');
  assert.ok(statements.indexOf('DELETE FROM casemgmt_note_ext') < statements.indexOf('DELETE FROM casemgmt_note WHERE'),
    'the guard reads the note, so the extension rows must go while it still exists');
  assert.doesNotMatch(statements, /DELETE FROM casemgmt_note_ext WHERE note_id IN \(881\)/,
    'an unguarded extension delete would remove the rows of a clinician\'s note that shared the number');
});

test('shouldFindTheNotesThroughTheOwnedLinks_andDeleteTheLinksAfterwards', () => {
  const sql = stubSql({ noteIds: ['881', 'x', '882'] });
  removeDocumentResidue(sql, markDocumentResidue(sql), ['88']);
  assert.ok(sql.queries.some((query) => /SELECT note_id FROM casemgmt_note_link WHERE table_name=5 AND table_id IN \(88\) AND id > 331/.test(query)),
    'the notes are read through the links, which are keyed by document number');
  const statements = sql.executed;
  assert.ok(statements.findIndex((query) => /DELETE FROM casemgmt_note WHERE/.test(query))
    < statements.findIndex((query) => /DELETE FROM casemgmt_note_link/.test(query)), 'notes first, while the links still say which they are');
  assert.match(statements.join('\n'), /note_id IN \(881,882\)/, 'a value that is not an id is dropped');
});

test('shouldTouchNothing_whenNoOwnedNumberIsGiven_orNoneIsAnId', () => {
  const sql = stubSql();
  const mark = markDocumentResidue(sql);
  removeDocumentResidue(sql, mark, []);
  removeDocumentResidue(sql, mark, ['0', 'x', '', '9; DROP TABLE document']);
  assert.deepEqual(sql.executed, []);
});

test('shouldFail_whenARowOfTheRunIsStillThereAfterTheDeletes', () => {
  const sql = stubSql({ left: '2' });
  assert.throws(() => removeDocumentResidue(sql, markDocumentResidue(sql), ['88']), /were not removed/);
});
