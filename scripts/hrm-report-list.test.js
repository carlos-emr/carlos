/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {hrmReportLink} = require('./lib/hrm-report-list');

test('owned HRM selector retains the exact ID boundary before patient context', () => {
  let actual;
  const link = {};
  assert.equal(hrmReportLink({locator: selector => { actual = selector; return link; }}, 12), link);
  assert.equal(actual, '#tblHRM a[onclick*="/hospitalReportManager/Display?id=12&"]');
});

test('malformed or unowned report IDs are rejected before touching the page', () => {
  for (const value of [0, -1, '', null, undefined, '12&demographicNo=2', '1.5', '01', ' 12']) {
    assert.throws(() => hrmReportLink({locator: () => assert.fail('page must not be queried')}, value), /Invalid HRM/);
  }
});
