/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * scripts/page-health-patient-hubs-playwright-checks.js requires each Master Record hub to offer a
 * minimum number of links before it crawls them. "Create Invoice: at least one" was calibrated on the
 * Ontario bill form; the BC form offers none, so the check failed on BC before it crawled anything.
 * These cases pin which minimum applies on which install.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const { HUBS, hubMinimum } = require('./page-health-patient-hubs-playwright-checks');

const hub = name => HUBS.find(candidate => candidate.name === name);

test('Create Invoice must offer a link on Ontario and when the run names no province, and none on BC', () => {
  const invoice = hub('Create Invoice');
  assert.equal(hubMinimum(invoice, 'ON'), 1);
  assert.equal(hubMinimum(invoice, 'on'), 1);
  assert.equal(hubMinimum(invoice, undefined), 1);
  assert.equal(hubMinimum(invoice, ''), 1);
  assert.equal(hubMinimum(invoice, 'BC'), 0);
  assert.equal(hubMinimum(invoice, ' bc '), 0);
});

test('every other hub keeps its minimum on every province', () => {
  const others = HUBS.filter(candidate => candidate.name !== 'Create Invoice');
  assert.deepEqual(others.map(candidate => candidate.name), ['Prescriptions', 'Consultations', 'Manage Contacts', 'Preventions']);
  for (const candidate of others) {
    assert.equal(candidate.minimumByProvince, undefined, `${candidate.name} has no province exception`);
    for (const province of ['ON', 'BC', '']) assert.equal(hubMinimum(candidate, province), candidate.minimum);
    assert.ok(candidate.minimum >= 1);
  }
});

test('an unknown province is an error of the run, not a default', () => {
  assert.throws(() => hubMinimum(hub('Create Invoice'), 'AB'), /CARLOS_SUITE_PROVINCE must be ON or BC/);
});
