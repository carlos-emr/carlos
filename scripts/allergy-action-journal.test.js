/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { journalActionErrors } = require('./allergy-add-penicillin-playwright-checks');
const UNIT = 'carlos-emr.service';
const SINCE = new Date('2026-10-19T17:24:00.123Z');
const entry = (message = 'Application startup completed', fields = {}) => ({
  __CURSOR: 's=123;i=456;b=789', _SYSTEMD_UNIT: UNIT, MESSAGE: message, ...fields,
});
const success = (records) => ({ status: 0, signal: null, stdout: records.map((record) => JSON.stringify(record)).join('\n'), stderr: '' });
function harness(results) {
  const calls = [];
  return { calls, run(command, args, options) {
    calls.push({ command, args, options });
    assert(results.length, 'unexpected additional journal read');
    const result = results.shift();
    if (result instanceof Error) throw result;
    return result;
  } };
}

test('quiet action window passes only after a readable historical entry from the actual unit', () => {
  const h = harness([success([entry()]), success([])]);
  assert.equal(journalActionErrors(UNIT, SINCE, h), 0);
  assert.equal(h.calls.length, 2);
  assert.deepEqual(h.calls[0].args.slice(-2), ['-n', '1']);
  assert.deepEqual(h.calls[1].args.slice(-2), ['--since', '2026-10-19 17:24:00 UTC']);
  for (const call of h.calls) {
    assert.equal(call.command, 'journalctl');
    assert.deepEqual(call.args.slice(0, 2), ['-u', UNIT]);
    assert(call.args.includes('json'));
    assert.equal(call.args.includes('--quiet'), false, 'Access warnings must remain visible');
    assert.equal(call.options.timeout, 30000);
    assert.deepEqual(call.options.stdio, ['ignore', 'pipe', 'pipe']);
  }
});

test('exact no-entries sentinel is accepted only for the interval after actual history proof', () => {
  const h = harness([success([entry()]), {status:0,stdout:'-- No entries --\n',stderr:''}]);
  assert.equal(journalActionErrors(UNIT, SINCE, h), 0);
});

test('no-entries sentinel cannot establish readable historical unit evidence', () => {
  const h = harness([{status:0,stdout:'-- No entries --\n',stderr:''}]);
  assert.throws(() => journalActionErrors(UNIT, SINCE, h), /invalid structured evidence/);
  assert.equal(h.calls.length, 1);
});

test('sentinel mixed with another line or any access warning remains a failure', () => {
  for (const interval of [
    {status:0,stdout:'-- No entries --\n'+JSON.stringify(entry('ERROR RxAddAllergy2Action failed')),stderr:''},
    {status:0,stdout:'-- No entries --\n',stderr:'not seeing private unit records'},
  ]) {
    const h = harness([success([entry()]), interval]);
    assert.throws(() => journalActionErrors(UNIT, SINCE, h), /journal/i);
  }
});

test('historical action errors are excluded, but new action ERROR events are still counted', () => {
  const h = harness([success([entry('ERROR RxAddAllergy2Action previous run')]), success([
    entry('INFO RxAddAllergy2Action completed'), entry('ERROR RxAddAllergy2Action current failure'),
    entry('ERROR RxAddAllergy2Action second current failure'),
  ])]);
  assert.equal(journalActionErrors(UNIT, SINCE, h), 2);
});

test('systemd manager entry naming this unit also proves readable unit history', () => {
  const h = harness([success([entry('Started application', { _SYSTEMD_UNIT: 'init.scope', UNIT })]), success([])]);
  assert.equal(journalActionErrors(UNIT, SINCE, h), 0);
});

test('an unknown unit with no historical entries fails before the quiet interval can pass', () => {
  const h = harness([success([])]);
  assert.throws(() => journalActionErrors(UNIT, SINCE, h), /no readable journal history/);
  assert.equal(h.calls.length, 1);
});

for (const [label, result] of [
  ['wrong unit', success([entry('unrelated entry', { _SYSTEMD_UNIT: 'other.service' })])],
  ['missing cursor', success([entry('entry', { __CURSOR: undefined })])],
  ['malformed JSON', { status: 0, stdout: 'not structured data', stderr: '' }],
  ['permission warning despite zero exit', { status: 0, stdout: JSON.stringify(entry()), stderr: 'private diagnostic' }],
  ['nonzero exit', { status: 1, stdout: 'private response', stderr: 'access denied' }],
  ['timeout', { status: null, signal: 'SIGTERM', stdout: '', stderr: '' }],
  ['command error', { status: null, error: new Error('private command path'), stdout: '', stderr: '' }],
  ['thrown read error', new Error('private exception data')],
]) {
  test(`unreadable or invalid history is refused: ${label}`, () => {
    const h = harness([result]);
    assert.throws(() => journalActionErrors(UNIT, SINCE, h), (error) => {
      assert.doesNotMatch(error.message, /private|access denied/);
      return /journal/i.test(error.message);
    });
  });
}

test('readable history does not permit a later permission failure or malformed interval', () => {
  for (const failed of [{ status: 0, stdout: '', stderr: 'permission warning' },
    { status: 1, stdout: '', stderr: 'denied' }, success([entry('entry', { _SYSTEMD_UNIT: 'other.service' })]),
    { status: 0, stdout: '{invalid', stderr: '' }]) {
    const h = harness([success([entry()]), failed]);
    assert.throws(() => journalActionErrors(UNIT, SINCE, h), /journal/i);
  }
});

test('invalid unit and invalid start date are rejected before any command executes', () => {
  const h = harness([]);
  for (const unit of ['', '--system', 'missing-suffix', 'two.service\nother.service']) {
    assert.throws(() => journalActionErrors(unit, SINCE, h), /must name one systemd/);
  }
  assert.throws(() => journalActionErrors(UNIT, new Date('invalid'), h), /valid date/);
  assert.equal(h.calls.length, 0);
});
