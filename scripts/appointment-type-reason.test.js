/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const reason = require('../src/main/webapp/js/appointmentTypeReason');

test('replaces previous autofill across repeated type changes', () => {
    assert.equal(reason('Type A', 'Type A', 'Type B'), 'Type B');
    assert.equal(reason('Type B', 'Type B', 'Type A'), 'Type A');
});
test('preserves manually entered reason while replacing the type prefix', () => {
    assert.equal(reason('Patient request', '', 'Type A'), 'Type A -- Patient request');
    assert.equal(reason('Type A -- Patient request', 'Type A', 'Type B'), 'Type B -- Patient request');
});
test('keeps an edited autofill as manual text instead of discarding user edits', () => {
    assert.equal(reason('Edited Type A', 'Type A', 'Type B'), 'Type B -- Edited Type A');
});
test('empty types preserve only manual text and never add a separator', () => {
    assert.equal(reason('Type A -- Patient request', 'Type A', ''), 'Patient request');
    assert.equal(reason('Type A', 'Type A', ''), '');
    assert.equal(reason('Patient request', '', ''), 'Patient request');
    assert.equal(reason('', '', 'Type A'), 'Type A');
    assert.equal(reason(), '');
});

test('accepts the exact field boundary without truncating either part', () => {
    const manual = 'm'.repeat(35);
    const type = 't'.repeat(41);
    assert.equal(reason(manual, '', type), type + ' -- ' + manual);
    assert.equal(reason('old', 'old', 't'.repeat(80)), 't'.repeat(80));
});
test('reports overflow before the caller changes form state', () => {
    assert.throws(() => reason('m'.repeat(35), '', 't'.repeat(42)), RangeError);
    assert.throws(() => reason('', '', 't'.repeat(81)), RangeError);
});
