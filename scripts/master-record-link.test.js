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
// The whole selector is one or more [onclick*=...] conditions, with or without the a, and nothing else (a list
// that also names the href form is fine).
const ONCLICK_ONLY = /[mM]aster[A-Za-z]*\.locator\(\s*(['"`])a?(?:\[onclick\*=(["'])[^"'\\]+\2\])+\1\s*\)/;

test('shouldFlagOnlyOnclickOnlyLocators_inTheGuard', () => {
  assert.ok(ONCLICK_ONLY.test("s.master.locator('a[onclick*=\"/rx/choosePatient\"]').first()"));
  assert.ok(ONCLICK_ONLY.test("master.locator('a[onclick*=\"/billing?\"][onclick*=\"apptProvider_no=none\"]')"));
  assert.ok(ONCLICK_ONLY.test("s.master.locator(`a[onclick*=\"/demographic/AddRelation?demo=${patient}\"]`)"));
  assert.ok(ONCLICK_ONLY.test("s.master.locator('[onclick*=\"Contact?method=manage\"]').first()"));
  assert.ok(!ONCLICK_ONLY.test("s.master.locator('a[onclick*=\"/x\"], .patient-nav a[href*=\"/x\"]')"));
  assert.ok(!ONCLICK_ONLY.test("s.master.locator('a[onclick*=\"/x\"],'"));
  assert.ok(!ONCLICK_ONLY.test("chart.locator('a[onclick*=\"showAllergy\"]')"));
});

test('shouldNotMatchMasterRecordLinksByOnclickAlone_inAnyCheck', () => {
  const files = [
    ...fs.readdirSync(__dirname).filter(name => name.endsWith('-playwright-checks.js')).map(name => path.join(__dirname, name)),
    ...fs.readdirSync(path.join(__dirname, 'lib')).filter(name => name.endsWith('.js')).map(name => path.join(__dirname, 'lib', name)),
  ];
  const offenders = [];
  for (const file of files) {
    const lines = fs.readFileSync(file, 'utf8').split('\n');
    lines.forEach((line, index) => {
      if (ONCLICK_ONLY.test(line)) offenders.push(`${path.basename(file)}:${index + 1}`);
    });
  }
  assert.deepEqual(offenders, [], 'Use masterRecordLink() (lib/master-record-link.js) for Master Record links');
});
