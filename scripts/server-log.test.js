/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * scripts/lib/server-log.js lets the lab-upload rollback and signed-feed checks assert on the server
 * log (#4436): after a database rejection the ERROR log must name the database's own message, and
 * Hibernate's HHH000099 session assertion must be absent.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { splitLogEvents, storageFailureLogProblems, serverLogFromEnvironment } = require('./lib/server-log');

const PROBE = 'Synthetic upload rollback probe';

// The log of a rejected insert as 2026.08.0~alpha19 wrote it: the database error only at WARN, then
// Hibernate's own assertion, then the upload action's ERROR with the cleanup in its stack.
const BEFORE_FIX = [
  `2026-10-08 11:00:01,101 WARN  hibernate.SqlExceptionHelper (SqlExceptionHelper.java:145) - SQL Error: 1644, SQLState: 45000`,
  `2026-10-08 11:00:01,102 WARN  hibernate.SqlExceptionHelper (SqlExceptionHelper.java:146) - (conn=44) ${PROBE}`,
  `2026-10-08 11:00:01,140 ERROR hibernate.AssertionFailure (AssertionFailure.java:23) - HHH000099: an assertion failure occurred (this may indicate a bug in Hibernate, but is more likely due to unsafe use of the session): org.hibernate.AssertionFailure: Entry for instance of 'io.github.carlos_emr.carlos.commn.model.Hl7TextInfo' has a null identifier`,
  `org.hibernate.AssertionFailure: Entry for instance of 'io.github.carlos_emr.carlos.commn.model.Hl7TextInfo' has a null identifier`,
  `\tat io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader.clean(MessageUploader.java:644)`,
  `2026-10-08 11:00:01,150 ERROR lab.InsideLabUpload2Action (InsideLabUpload2Action.java:205) - Error occurred while processing uploaded lab file`,
  '',
].join('\n');

// What a rejected insert logs once clean no longer runs in the failed session.
const AFTER_FIX = [
  `2026-10-08 11:00:01,101 WARN  hibernate.SqlExceptionHelper (SqlExceptionHelper.java:145) - SQL Error: 1644, SQLState: 45000`,
  `2026-10-08 11:00:01,140 INFO  upload.MessageUploader (MessageUploader.java:662) - Not cleaning up a failed lab upload: its transaction is already rollback-only, so nothing it wrote can commit`,
  `2026-10-08 11:00:01,141 ERROR handlers.HL7Handler (HL7Handler.java:88) - Could not upload message`,
  `org.hibernate.exception.GenericJDBCException: could not execute statement [(conn=44) ${PROBE}] [insert into hl7TextInfo (...) values (...)]`,
  `\tat org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:63)`,
  `Caused by: java.sql.SQLException: (conn=44) ${PROBE}`,
  '',
].join('\n');

test('splitLogEvents keeps stack lines with the event that precedes them', () => {
  const events = splitLogEvents(AFTER_FIX);
  assert.deepEqual(events.map((event) => [event.level, event.logger]), [
    ['WARN', 'hibernate.SqlExceptionHelper'],
    ['INFO', 'upload.MessageUploader'],
    ['ERROR', 'handlers.HL7Handler'],
  ]);
  assert.match(events[2].text, /Caused by: java\.sql\.SQLException/);
});

test('splitLogEvents drops text before the first event header', () => {
  assert.deepEqual(splitLogEvents('\tat truncated.Frame(Frame.java:1)\nnot a header').length, 0);
  assert.deepEqual(splitLogEvents(null), []);
});

test('storageFailureLogProblems reports the pre-fix log: a session assertion and no real cause at ERROR', () => {
  // The database message is present, but only at WARN, which is all the issue says an administrator got.
  const problems = storageFailureLogProblems(BEFORE_FIX, PROBE);
  assert.equal(problems.length, 2);
  assert.match(problems[0], /Hibernate session assertion.*hibernate\.AssertionFailure/);
  assert.match(problems[1], /no ERROR event carries the database's own message/);
});

test('storageFailureLogProblems accepts the fixed log', () => {
  assert.deepEqual(storageFailureLogProblems(AFTER_FIX, PROBE), []);
});

test('storageFailureLogProblems fails when the log is empty, e.g. the wrong unit was named', () => {
  assert.equal(storageFailureLogProblems('', PROBE).length, 1);
});

test('storageFailureLogProblems recognizes the assertion by its message when the logger is renamed', () => {
  const log = `2026-10-08 11:00:01,140 ERROR hibernate.Something (X.java:1) - HHH000099: an assertion failure occurred\n${AFTER_FIX}`;
  assert.match(storageFailureLogProblems(log, PROBE)[0], /session assertion/);
});

test('serverLogFromEnvironment is not configured without a source and reads nothing', () => {
  const reader = serverLogFromEnvironment({});
  assert.equal(reader.configured, false);
  assert.equal(reader.mark(), null);
  assert.equal(reader.since(null), '');
});

test('serverLogFromEnvironment reads only what a log file gained after the mark', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'server-log-test-'));
  try {
    const file = path.join(dir, 'catalina.out');
    fs.writeFileSync(file, '2026-10-08 10:00:00,000 ERROR old.Logger (Old.java:1) - before the check\n');
    const reader = serverLogFromEnvironment({ LAB_UPLOAD_SERVER_LOG: file });
    assert.equal(reader.configured, true);
    const mark = reader.mark();
    fs.appendFileSync(file, AFTER_FIX);
    const events = splitLogEvents(reader.since(mark));
    assert.equal(events.length, 3);
    assert.ok(events.every((event) => !event.text.includes('before the check')));

    // A log rotated or truncated below the mark is read from its start rather than skipped.
    fs.writeFileSync(file, '2026-10-08 12:00:00,000 ERROR new.Logger (New.java:1) - after rotation\n');
    assert.match(reader.since(mark), /after rotation/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('serverLogFromEnvironment brackets the journal with a cursor and passes the unit as one argument', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'server-log-test-'));
  const originalPath = process.env.PATH;
  try {
    const argsFile = path.join(dir, 'args.log');
    // Stands in for journalctl: prints a cursor for `-n 1 --show-cursor`, and a log for `--after-cursor`.
    fs.writeFileSync(path.join(dir, 'journalctl'), [
      '#!/bin/sh',
      `printf '%s\\n' "$*" >> '${argsFile}'`,
      'case "$*" in',
      '  *--show-cursor*) printf "2026-10-08 10:00:00,000 INFO  old.Logger (Old.java:1) - old\\n-- cursor: s=abc;i=1\\n" ;;',
      '  *--after-cursor=s=abc\\;i=1*) printf "%s" "$LOG_AFTER" ;;',
      '  *) printf "unexpected arguments\\n" ;;',
      'esac',
      '',
    ].join('\n'), { mode: 0o755 });
    process.env.PATH = `${dir}${path.delimiter}${originalPath}`;
    process.env.LOG_AFTER = AFTER_FIX;
    const reader = serverLogFromEnvironment({ LAB_UPLOAD_JOURNAL_UNIT: 'carlos-emr' });
    assert.equal(reader.configured, true);
    const mark = reader.mark();
    assert.deepEqual(mark, { cursor: 's=abc;i=1' });
    assert.deepEqual(storageFailureLogProblems(reader.since(mark), PROBE), []);
    const calls = fs.readFileSync(argsFile, 'utf8').trim().split('\n');
    assert.equal(calls.length, 2);
    assert.match(calls[0], /^-u carlos-emr --no-pager -q -o cat -n 1 --show-cursor$/);
    assert.match(calls[1], /^-u carlos-emr --no-pager -q -o cat --after-cursor=s=abc;i=1$/);
  } finally {
    process.env.PATH = originalPath;
    delete process.env.LOG_AFTER;
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
