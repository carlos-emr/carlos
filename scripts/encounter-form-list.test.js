/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { formListAsSeen } = require('./lib/encounter-form-list');

// Shaped like the BC seed's shown forms, which share display orders, plus one form off the menu.
const SEED = [
  ['BC Birth Sum 2008', '../form/formbcbirthsummo2008.jsp?demographic_no=', 'formBCBirthSumMo2008', '2'],
  ['BC-AR 2007', '../form/formbcar2007.jsp?demographic_no=', 'formBCAR2007', '1'],
  ['BC-LabourBirth', '../form/formbcbirthsummo.jsp?demographic_no=', 'formBCBirthSumMo', '1'],
  ['BC-NewBorn', '../form/formbcnewborn.jsp?demographic_no=', 'formBCNewBorn', '1'],
  ['BC-NewBorn 2008', '../form/formBCNewBorn2008.jsp?demographic_no=', 'formBCNewBorn2008', '2'],
  ['BC-WCB', '../form/formbcwcb.jsp?demographic_no=', 'formWCB', '0'],
  ['Rourke2017', '../form/formrourke2017complete.jsp?demographic_no=', 'formRourke2017', '38'],
];
const withOrders = orders => SEED.map(row => [...row.slice(0, 3), String(orders[row[0]] ?? row[3])]);

test('shouldTreatTheListAsUnchanged_whenSelectFormsRenumbersTheShownForms', () => {
  // What Select Forms leaves after an add and a remove (#4952): the same menu order, numbered 1..n.
  const renumbered = withOrders({ 'BC-AR 2007': 1, 'BC-LabourBirth': 2, 'BC-NewBorn': 3, 'BC Birth Sum 2008': 4,
    'BC-NewBorn 2008': 5, Rourke2017: 6 });
  assert.equal(formListAsSeen(renumbered), formListAsSeen(SEED));
});

test('shouldSeeAChange_whenAShownFormMoves', () => {
  const moved = withOrders({ Rourke2017: '1' });
  assert.notEqual(formListAsSeen(moved), formListAsSeen(SEED));
});

test('shouldSeeAChange_whenAFormIsShownOrHidden', () => {
  assert.notEqual(formListAsSeen(withOrders({ 'BC-WCB': '3' })), formListAsSeen(SEED));
  assert.notEqual(formListAsSeen(withOrders({ 'BC-NewBorn': '0' })), formListAsSeen(SEED));
});

test('shouldTreatARemovedCaisiForm_asNeverShown', () => {
  // An imported list with Discharge Summary still shown: Select Forms hides it when it renumbers (#4952).
  const before = [...SEED, ['Discharge Summary', '../form/formdischargesummary.jsp?demographic_no=', 'formDischargeSummary', '3']];
  const after = [...SEED, ['Discharge Summary', '../form/formdischargesummary.jsp?demographic_no=', 'formDischargeSummary', '0']];
  assert.equal(formListAsSeen(after), formListAsSeen(before));
  assert.ok(!JSON.parse(formListAsSeen(before)).shown.includes('Discharge Summary'));
});

test('shouldOrderTiedFormsByName_asSelectFormsLists', () => {
  assert.deepEqual(JSON.parse(formListAsSeen(SEED)).shown,
    ['BC-AR 2007', 'BC-LabourBirth', 'BC-NewBorn', 'BC Birth Sum 2008', 'BC-NewBorn 2008', 'Rourke2017']);
});
