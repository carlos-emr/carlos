/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const { assertEpochOnBoth, assertSeededInstant } = require('./oauth-rest-epoch-dates-playwright-checks');

/*
 * oauth-rest-epoch-dates-playwright-checks.js judges each timestamp with assertEpochOnBoth: the
 * /ws/services value must be an epoch-millisecond integer equal to the /ws/rs value for the same
 * row. These tests pin that judgement offline against the shapes issue #3877 reported, so a
 * deployed failure points at the server rather than at the check.
 */

const MIDNIGHT_MS = Date.UTC(2026, 2, 11);

test('a matching epoch-millisecond number on both surfaces passes', () => {
  assert.doesNotThrow(() => assertEpochOnBoth(MIDNIGHT_MS, MIDNIGHT_MS, 'field'));
});

test('the pre-fix shapes on /ws/services fail and name the issue', () => {
  for (const preFix of ['2026-03-11', '09:30:00']) {
    assert.throws(() => assertEpochOnBoth(preFix, MIDNIGHT_MS, 'field'), /not epoch milliseconds \(issue #3877\)/);
  }
});

test('a number that is a different instant from /ws/rs fails', () => {
  assert.throws(() => assertEpochOnBoth(MIDNIGHT_MS + 1000, MIDNIGHT_MS, 'field'), /different instants/);
});

test('a non-number on /ws/rs fails as a session-surface change', () => {
  assert.throws(() => assertEpochOnBoth(MIDNIGHT_MS, '2026-03-11', 'field'), /the session surface changed/);
});

test('a failure message names the type, never the value', () => {
  // Values can be clinical timestamps; the message carries only the JSON type and length.
  assert.throws(() => assertEpochOnBoth('2026-03-11', MIDNIGHT_MS, 'field'),
    (error) => !error.message.includes('2026-03-11') && error.message.includes('string of length 10'));
});

test('the seeded wall-clock time is accepted in any real time-zone offset', () => {
  // 1970-01-01 09:30 local in Toronto (UTC-5), UTC and Kolkata (UTC+5:30).
  for (const offsetMinutes of [-300, 0, 330]) {
    const epoch = Date.UTC(1970, 0, 1, 9, 30) - offsetMinutes * 60 * 1000;
    assert.doesNotThrow(() => assertSeededInstant(epoch, '1970-01-01 09:30:00', 'field'));
  }
});

test('a value that is not the seeded time in any zone is refused', () => {
  // Another row's timestamp: a day away, or off by a minute.
  assert.throws(() => assertSeededInstant(Date.UTC(2026, 2, 12), '2026-03-11 00:00:00', 'field'),
    /did not return the seeded time/);
  assert.throws(() => assertSeededInstant(Date.UTC(2026, 2, 11, 0, 1), '2026-03-11 00:00:00', 'field'),
    /did not return the seeded time/);
});
