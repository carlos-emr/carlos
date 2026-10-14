/* SPDX-License-Identifier: GPL-2.0-or-later */
// Pins the synthetic Epsilon feed that lab-epsilon-playwright-checks.js uploads (#4124): the
// row indices it asserts on a live deployment must keep pointing at the PDF and non-PDF payloads.
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  buildFeed, PDF, HTML_PAYLOAD, PDF_ROW, HTML_ROW, HEADER, FT_LINE,
} = require('./lab-epsilon-playwright-checks');

const feed = buildFeed('PW4124TEST', 'FAKE-PWTEST');
const lines = feed.split('\r\n').filter(Boolean);

/** The OBX segments of the n-th PID, as the uploader stores them (one message per PID). */
function obxOfPatient(n) {
  const starts = lines.map((line, i) => (line.startsWith('PID') ? i : -1)).filter(i => i >= 0);
  const end = n + 1 < starts.length ? starts[n + 1] : lines.length;
  return lines.slice(starts[n], end).filter(line => line.startsWith('OBX')).map(line => line.split('|'));
}

test('is one Epsilon v2.3 ORU^R01 message with one MSH and two fictitious patients', () => {
  assert.ok(feed.includes('\r\n') && !/[^\r]\n/.test(feed), 'segments must be CR LF separated, as the samples are');
  assert.equal(lines.filter(line => line.startsWith('MSH')).length, 1);
  assert.match(lines[0], /^MSH\|\^~\\&\|Epsilon-System\|.*\|ORU\^R01\|.*\|2\.3\|/);
  const pids = lines.filter(line => line.startsWith('PID'));
  assert.equal(pids.length, 2);
  assert.match(pids[0], /\|FAKE-PWTEST\^Workflow\|\|19800102\|F$/);
  assert.match(pids[1], /\|FAKE-PW4124\^Second\|/);
});

test('groups every row under the header and names it by OBX-3.2', () => {
  for (const n of [0, 1]) {
    for (const obx of obxOfPatient(n)) assert.equal(obx[3].split('^')[0], HEADER);
  }
  const rows = obxOfPatient(0);
  assert.deepEqual(rows.map(obx => obx[2]), ['NM', 'FT', 'ED', 'ED']);
  assert.equal(rows[0][3].split('^')[1], 'Glucose Random');
  assert.equal(rows[1][5], FT_LINE);
});

test('puts the PDF and the non-PDF ED payload at the rows the check addresses', () => {
  const rows = obxOfPatient(0);
  assert.equal(PDF_ROW.segment, 0);
  assert.equal(HTML_ROW.segment, 0);
  assert.equal(rows[PDF_ROW.group][5], `^TEXT^PDF^Base64^${PDF.toString('base64')}`);
  assert.equal(rows[HTML_ROW.group][5], `^TEXT^HTML^Base64^${HTML_PAYLOAD.toString('base64')}`);
  assert.equal(PDF.subarray(0, 5).toString('ascii'), '%PDF-');
});
