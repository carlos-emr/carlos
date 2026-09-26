/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Pins the fixture that lab-line-break-rendering-playwright-checks.js seeds (issue #3953).
 *
 * The browser check can only prove the lab views render HL7 \.br\ as a line break if the
 * message it seeds actually carries \.br\ in every place the views print handler text, and
 * the hostile result it uses to prove encoding survives is real markup. A fixture edit that
 * dropped either would leave the check passing while it proves nothing, so this runs in the
 * browserless test:scripts job.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const { fixtureMessage } = require('./lab-line-break-rendering-playwright-checks');

const segments = fixtureMessage().split('\r');
const field = (segment, index) => segment.split('|')[index];
const find = (prefix) => segments.filter((segment) => segment.startsWith(`${prefix}|`));

test('the fixture is an Excelleris ORU^R01 the PATHL7 handler parses', () => {
  const [msh] = find('MSH');
  assert.equal(field(msh, 2), 'PATHL7');
  assert.equal(field(msh, 8), 'ORU^R01');
  assert.ok(segments.every((segment) => /^[A-Z][A-Z0-9]{2}\|/.test(segment)), 'segments are CR-separated');
});

test('every text field the lab views print carries the HL7 line-break escape', () => {
  const [ftResult, nmResult] = find('OBX');
  assert.match(field(ftResult, 5), /\S\\\.br\\\S/, 'OBX-5 must carry \\.br\\ between two lines');
  assert.match(field(nmResult, 7), /\S\\\.br\\\S/, 'OBX-7 reference range must carry \\.br\\');
  assert.match(field(find('NTE')[0], 3), /\S\\\.br\\\S/, 'NTE-3 must carry \\.br\\');
});

test('the hostile result is live markup that only encoding can neutralise', () => {
  const hostile = field(find('OBX')[2], 5);
  assert.match(hostile, /<script>[^<]+<\/script>/);
  assert.match(hostile, /<img [^>]*onerror=/);
  assert.match(hostile, /\\\.br\\/, 'the hostile value must also be split by a break marker');
});

test('the fixture patient is clearly synthetic', () => {
  assert.match(field(find('PID')[0], 5), /^FAKE-/);
});

test('the check is registered in the suite and exposed through npm', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, 'playwright-suite.json'), 'utf8'));
  const entry = manifest.checks.find((check) => check.name === 'lab-line-break-rendering');
  assert.ok(entry, 'playwright-suite.json has no lab-line-break-rendering entry');
  assert.equal(entry.assertsDatabase, true, 'the check seeds and removes lab rows');
  const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8'));
  assert.equal(pkg.scripts['test:lab-line-break-rendering-playwright'],
    'node scripts/lab-line-break-rendering-playwright-checks.js');
});
