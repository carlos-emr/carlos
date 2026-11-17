/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const assert = require('node:assert/strict');

/** Parse the complete measured journal, retaining only the expected SQL stacks.
 * A recognized non-error JUL record is a boundary, never part of an ERROR stack.
 * Unknown records, severe/fatal records and unrelated exception traces fail closed.
 */
function faultErrorRecords(text, expectedSqlMessage) {
  assert.equal(typeof expectedSqlMessage, 'string');
  assert(expectedSqlMessage && !/[\r\n]/.test(expectedSqlMessage));
  const log4j = /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) +(TRACE|DEBUG|INFO|WARN|ERROR|FATAL) ([^\r\n]+)$/;
  const jul = /^(\d{1,2}-(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-\d{4} \d{2}:\d{2}:\d{2}\.\d{3}) (SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST) \[[^\]\r\n]+\] (?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]* [^\r\n]+$/;
  const frame = /^[\t ]+at [^\s()]+\([^()\r\n]*\)$/;
  const omitted = /^[\t ]+\.\.\. \d+ (?:more|common frames omitted)$/;
  const expectedCause = 'Caused by: java.sql.SQLException: ' + expectedSqlMessage;
  const errors = [];
  let current = null;
  function finish() {
    if (current?.error) {
      assert(current.rootSeen && current.causeSeen, 'Incomplete measured CML exception trace');
    }
  }
  for (const line of String(text).split(/\r?\n/)) {
    if (!line.trim()) continue;
    const header = log4j.exec(line);
    if (header) {
      finish();
      assert.notEqual(header[2], 'FATAL', 'Unexpected fatal Log4j record');
      current = {error: header[2] === 'ERROR', rootSeen: false, causeSeen: false};
      if (current.error) { current.parts = [line, header[1], header[3], '']; errors.push(current.parts); }
      continue;
    }
    const java = jul.exec(line);
    if (java) {
      finish();
      assert.notEqual(java[2], 'SEVERE', 'Unexpected severe JUL record');
      current = {error: false};
      continue;
    }
    assert(current?.error, 'Unknown journal record or unexpected non-error continuation');
    if (!current.rootSeen) {
      assert(line.startsWith('org.hibernate.exception.GenericJDBCException:'), 'Unexpected root exception trace');
      current.rootSeen = true;
    } else if (line.startsWith('Caused by:')) {
      assert(line === expectedCause, 'Unexpected SQL cause or independent exception trace');
      assert.equal(current.causeSeen, false, 'Duplicate exception cause');
      current.causeSeen = true;
    } else {
      assert(frame.test(line) || omitted.test(line), 'Unknown exception-stack continuation');
    }
    current.parts[3] += line + '\n';
  }
  finish();
  return errors;
}
module.exports = {faultErrorRecords};
