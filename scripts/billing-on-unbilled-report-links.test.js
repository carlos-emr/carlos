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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { unbilledReportParams, isConfiguredUnbilledLink } = require('./lib/billing-on-unbilled-report-links');

const base = new URL('https://localhost:8443/carlos');
const fixture = { appointmentNo: '11', demographicNo: '1', providerNo: '999998', appointmentDate: '2024-04-16' };
function billLink(change = {}) {
  const params = new URLSearchParams({
    billRegion: 'ON', billForm: '', hotclick: '', appointment_no: fixture.appointmentNo,
    demographic_name: "FAKE,O'Neil", demographic_no: fixture.demographicNo,
    user_no: fixture.providerNo, apptProvider_no: fixture.providerNo,
    appointment_date: fixture.appointmentDate, start_time: '12:00:00', bNewForm: '1', ...change,
  });
  return `/carlos/billing?${params}`;
}

test('both reports receive an explicit single-day window for the configured provider', () => {
  const params = unbilledReportParams(fixture.providerNo, fixture.appointmentDate);
  assert.equal(params.get('xml_vdate'), fixture.appointmentDate);
  assert.equal(params.get('xml_appointment_date'), fixture.appointmentDate);
  assert.equal(params.get('providerview'), fixture.providerNo);
  assert.equal(params.get('reportAction'), 'unbilled');
});

test('the actual Ontario bill-link shape proves the configured visit in a context deployment', () => {
  assert.equal(isConfiguredUnbilledLink(billLink(), base, fixture), true);
  assert.equal(isConfiguredUnbilledLink(`https://localhost:8443${billLink()}`, base, fixture), true);
  assert.equal(isConfiguredUnbilledLink(billLink().replace('/carlos/', '/'), 'https://localhost:8443', fixture), true);
});

test('a foreign appointment, patient, provider, date or province cannot satisfy coverage', () => {
  for (const change of [
    { appointment_no: '12' }, { demographic_no: '2' }, { apptProvider_no: '999999' },
    { appointment_date: '2024-04-17' }, { billRegion: 'BC' },
  ]) assert.equal(isConfiguredUnbilledLink(billLink(change), base, fixture), false, JSON.stringify(change));
});

test('missing or repeated ownership parameters cannot create ambiguous proof', () => {
  for (const key of ['appointment_no', 'demographic_no', 'apptProvider_no', 'appointment_date', 'billRegion']) {
    const url = new URL(billLink(), base);
    const value = url.searchParams.get(key);
    url.searchParams.delete(key);
    assert.equal(isConfiguredUnbilledLink(url.href, base, fixture), false, `missing ${key}`);
    url.searchParams.append(key, value);
    url.searchParams.append(key, value);
    assert.equal(isConfiguredUnbilledLink(url.href, base, fixture), false, `duplicate ${key}`);
  }
});

test('a foreign origin, wrong context, wrong action or malformed URL cannot prove coverage', () => {
  for (const raw of [
    `https://example.invalid${billLink()}`, billLink().replace('/carlos/', '/other/'),
    billLink().replace('/billing?', '/billingWrong?'), 'http://[bad',
  ]) assert.equal(isConfiguredUnbilledLink(raw, base, fixture), false, raw);
  assert.equal([billLink({ appointment_no: '99' })].some(raw => isConfiguredUnbilledLink(raw, base, fixture)), false);
  assert.equal([].some(raw => isConfiguredUnbilledLink(raw, base, fixture)), false);
});
