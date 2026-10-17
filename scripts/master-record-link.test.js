/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { masterRecordLink } = require('./lib/master-record-link');

const fakePage = () => ({ locator: selector => ({ selector }) });

test('shouldMatchTheNavigationHrefOrAnOnclick_forOneFragment', () => {
  assert.equal(masterRecordLink(fakePage(), '/rx/choosePatient').selector,
    '.patient-nav a[href*="/rx/choosePatient"], a[onclick*="/rx/choosePatient"]');
});

test('shouldRequireEveryFragment_inEachForm', () => {
  assert.equal(masterRecordLink(fakePage(), '/billing?', 'apptProvider_no=none').selector,
    '.patient-nav a[href*="/billing?"][href*="apptProvider_no=none"], a[onclick*="/billing?"][onclick*="apptProvider_no=none"]');
});

test('shouldRefuseFragments_thatWouldBreakTheSelector', () => {
  for (const bad of [[], [''], ['a"b'], ['a\\b'], [42]]) {
    assert.throws(() => masterRecordLink(fakePage(), ...bad), /plain URL fragments/);
  }
});

// #4468 moved the Master Record's navigation to hrefs; an onclick-only locator on the record finds nothing there.
test('shouldNotMatchMasterRecordLinksByOnclickAlone_inAnyCheck', () => {
  const files = [
    ...fs.readdirSync(__dirname).filter(name => name.endsWith('-playwright-checks.js')).map(name => path.join(__dirname, name)),
    ...fs.readdirSync(path.join(__dirname, 'lib')).filter(name => name.endsWith('.js')).map(name => path.join(__dirname, 'lib', name)),
  ];
  const offenders = [];
  for (const file of files) {
    const lines = fs.readFileSync(file, 'utf8').split('\n');
    lines.forEach((line, index) => {
      if (/[mM]aster[A-Za-z]*\.locator\(\s*['"`]a\[onclick\*=/.test(line)) offenders.push(`${path.basename(file)}:${index + 1}`);
    });
  }
  assert.deepEqual(offenders, [], 'Use masterRecordLink() (lib/master-record-link.js) for Master Record links');
});
