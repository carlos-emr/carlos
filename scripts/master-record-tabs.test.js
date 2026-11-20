/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

/*
 * The one rule this file exists to hold: a Master Record diagnostic names no
 * patient.
 *
 * openMasterRecord() is the shared entry point for five checks, and it handles
 * exactly the values CLAUDE.md calls sensitive: MASTER_RECORD_SEARCH is a
 * patient surname, and the result-row and landed values are demographic
 * numbers, which join straight back to the patient. runCheck() writes a thrown
 * message to stdout AND into RESULT_JSON, which CI archives -- so an assertion
 * message here is an artifact, not a console line that scrolls away.
 *
 * This class has now been caught four times on this branch in four different
 * files, which is why it gets a test rather than a comment.
 */

const SOURCE = fs.readFileSync(
  path.join(__dirname, 'master-record-tabs-playwright-checks.js'),
  'utf8',
);

/** Assertion and throw messages, with comments stripped so prose does not count. */
function messages() {
  const executable = SOURCE
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/[^\n]*/g, ' ');
  const found = [...executable.matchAll(/assert\(([\s\S]*?)\);\n/g)].map((match) => match[1]);
  assert.ok(found.length > 0, 'the assertions must actually be found');
  return found;
}

test('no assertion message carries the configured search term', () => {
  // MASTER_RECORD_SEARCH is a surname. Naming the variable is useful; printing
  // its value puts a patient name into every archived failure.
  for (const message of messages()) {
    assert.ok(!/\$\{[^}]*searchTerm[^}]*\}/.test(message),
      `an assertion message may not interpolate the search term: ${message.slice(0, 80)}`);
  }
});

test('no assertion message carries a demographic number', () => {
  // chosenNo and landed[1] are demographic numbers. The reader needs to know
  // the popup opened the wrong record, not which two records they were.
  for (const message of messages()) {
    assert.ok(!/\$\{[^}]*\bchosenNo\b[^}]*\}/.test(message),
      `an assertion message may not interpolate the clicked demographic number: ${message.slice(0, 80)}`);
    assert.ok(!/\$\{[^}]*\blanded\[\d\][^}]*\}/.test(message),
      `an assertion message may not interpolate the landed demographic number: ${message.slice(0, 80)}`);
  }
});

test('the url in the landing diagnostic is stripped of its query', () => {
  // The Master Record url is DemographicEdit?demographic_no=NNN, so the whole
  // address is the identifier the message above is careful not to print.
  const landing = SOURCE.slice(SOURCE.indexOf('const landed ='), SOURCE.indexOf('const body ='));
  assert.match(landing, /masterPage\.url\(\)\.split\('\?'\)\[0\]/);
  assert.ok(!/\$\{masterPage\.url\(\)\}/.test(landing),
    'the raw url must not reach a message runCheck() archives');
});

test('openMasterRecord hands its callers no patient identifier', () => {
  // All five callers destructure { masterPage } alone, so nothing reads it.
  // Not because a returned object is archived -- runCheck() writes only
  // { name, outcome, detail, durationMs } -- but because an identifier spread
  // across five call sites is one interpolation away from the thrown message
  // that IS archived.
  const returned = SOURCE.slice(SOURCE.lastIndexOf('return { masterPage'));
  assert.match(returned, /return \{ masterPage, searchPage \};/);
  assert.ok(!/demographicNo:/.test(SOURCE),
    'openMasterRecord must not return a demographic number');
});
