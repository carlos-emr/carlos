/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Reads the server's own log for the span of one check, so a browser workflow can assert on what an
 * administrator would read after a failure, not only on the page and the database (#4436).
 *
 * The application logs to its console (log4j2.xml: "%d %-5p %C{2} (%F:%L) - %m%n"), which a Debian
 * install keeps in the journal. Two sources are supported, both optional; a workflow that finds
 * neither skips its log half with a notice, as it does for CML_UPLOAD_KEY:
 *   LAB_UPLOAD_JOURNAL_UNIT  the systemd unit whose journal holds the log (`carlos-emr` on a package
 *                            install). Needs a user who may read it, such as root or group adm.
 *   LAB_UPLOAD_SERVER_LOG    a console log file, such as Tomcat's catalina.out in the devcontainer.
 */
'use strict';

const childProcess = require('node:child_process');
const fs = require('node:fs');

// Start of one Log4j2 event: "2026-10-08 15:01:34,702 ERROR hibernate.AssertionFailure (File.java:23) - ".
// Stack-trace lines and wrapped messages that follow belong to the event above them.
const EVENT_START = /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) +([A-Z]+) +(\S+) \(/;

// What Hibernate reports when a session is used after a failed flush or insert. It is logged by
// Hibernate itself at ERROR, so it appears even when the application catches the exception.
const SESSION_ASSERTION = /HHH000099|hibernate\.AssertionFailure|org\.hibernate\.AssertionFailure|has a null identifier/;

/**
 * Splits console-log text into events, each carrying the lines that follow its first line.
 *
 * @param {string} text raw log text
 * @returns {{level: string, logger: string, text: string}[]} the events in order; text that precedes the first
 *     event header is dropped, since its start is unknown
 */
function splitLogEvents(text) {
  const events = [];
  let current = null;
  for (const line of String(text || '').split(/\r?\n/)) {
    const header = EVENT_START.exec(line);
    if (header) {
      current = { level: header[2], logger: header[3], text: line };
      events.push(current);
    } else if (current) {
      current.text += `\n${line}`;
    }
  }
  return events;
}

/**
 * Judges the log of a lab upload whose database write was rejected.
 *
 * <p>A reader needs the database's own message at ERROR. Before #4436 the upload's cleanup ran in the
 * rolled-back session, so Hibernate logged HHH000099 instead and the real cause appeared only as a WARN.</p>
 *
 * @param {string} text log text recorded while the upload ran
 * @param {string} databaseMessage the rejection's message, such as a trigger's MESSAGE_TEXT
 * @returns {string[]} what is wrong; empty when the log is what an administrator needs
 */
function storageFailureLogProblems(text, databaseMessage) {
  const events = splitLogEvents(text);
  const problems = [];
  const assertion = events.find((event) => SESSION_ASSERTION.test(event.text));
  if (assertion) {
    problems.push(`a Hibernate session assertion was logged (${assertion.level} ${assertion.logger}), `
      + 'so the cleanup ran in the failed session');
  }
  if (!events.some((event) => event.level === 'ERROR' && event.text.includes(databaseMessage))) {
    problems.push(`no ERROR event carries the database's own message "${databaseMessage}"`);
  }
  return problems;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Asserts, once the log has settled, that a rejected lab upload was logged the way an administrator needs.
 *
 * <p>Polls because the journal lags the HTTP response, then re-reads after a short pause so a line that
 * follows the first good snapshot is still judged. A workflow without a configured source skips with a
 * notice instead of passing silently.</p>
 *
 * @param {{reader: object, mark: object|null, databaseMessage: string, assert: function(boolean, string): void}} options
 *     `reader` from {@link serverLogFromEnvironment}; `mark` taken before the upload; `assert` is the
 *     harness assertion that throws on failure
 * @param {{timeoutMs?: number, intervalMs?: number, settleMs?: number}} [timing] polling bounds
 * @returns {Promise<void>}
 */
async function verifyStorageFailureLog({ reader, mark, databaseMessage, assert }, timing = {}) {
  if (!reader.configured) {
    console.log('    SKIP server-log half: neither LAB_UPLOAD_JOURNAL_UNIT nor LAB_UPLOAD_SERVER_LOG is set');
    return;
  }
  const { timeoutMs = 15000, intervalMs = 500, settleMs = 1000 } = timing;
  const deadline = Date.now() + timeoutMs;
  let problems = storageFailureLogProblems(reader.since(mark), databaseMessage);
  while (problems.length > 0 && Date.now() < deadline) {
    await sleep(intervalMs);
    problems = storageFailureLogProblems(reader.since(mark), databaseMessage);
  }
  if (problems.length === 0) {
    await sleep(settleMs);
    problems = storageFailureLogProblems(reader.since(mark), databaseMessage);
  }
  assert(problems.length === 0, `The server log (${reader.source}) after the rejected upload: ${problems.join('; ')}`);
}

/**
 * Builds the reader from the environment.
 *
 * @param {NodeJS.ProcessEnv} [env] environment to read; defaults to the process environment
 * @returns {{configured: boolean, source: string, mark: function(): (object|null), since: function(object|null): string}}
 *     `mark()` records the current end of the log; `since(mark)` returns what was logged after it
 */
function serverLogFromEnvironment(env = process.env) {
  const unit = env.LAB_UPLOAD_JOURNAL_UNIT;
  const file = env.LAB_UPLOAD_SERVER_LOG;
  if (unit) {
    return {
      configured: true,
      source: `journal unit ${unit}`,
      mark: () => journalMark(unit),
      since: (mark) => journalSince(unit, mark),
    };
  }
  if (file) {
    return {
      configured: true,
      source: `log file ${file}`,
      mark: () => ({ size: fs.statSync(file).size }),
      since: (mark) => fileSince(file, mark),
    };
  }
  return { configured: false, source: 'none', mark: () => null, since: () => '' };
}

function journal(unit, extraArgs) {
  // execFileSync passes the unit as one argument and starts no shell.
  return childProcess.execFileSync('journalctl',
    ['-u', unit, '--no-pager', '-q', '-o', 'cat', ...extraArgs],
    { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
}

function journalMark(unit) {
  const cursor = /^-- cursor: (.+)$/m.exec(journal(unit, ['-n', '1', '--show-cursor']));
  // An empty journal has no cursor; everything in it was logged after the mark.
  return { cursor: cursor ? cursor[1].trim() : null };
}

function journalSince(unit, mark) {
  return journal(unit, mark && mark.cursor ? [`--after-cursor=${mark.cursor}`] : []);
}

function fileSince(file, mark) {
  const size = fs.statSync(file).size;
  // A log that shrank was rotated or truncated; read it from the start.
  const start = mark && mark.size <= size ? mark.size : 0;
  const fd = fs.openSync(file, 'r');
  try {
    const buffer = Buffer.alloc(size - start);
    fs.readSync(fd, buffer, 0, buffer.length, start);
    return buffer.toString('utf8');
  } finally {
    fs.closeSync(fd);
  }
}

module.exports = { splitLogEvents, storageFailureLogProblems, serverLogFromEnvironment, verifyStorageFailureLog };
