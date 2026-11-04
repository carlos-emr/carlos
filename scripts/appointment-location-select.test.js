/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Booking-screen Location List dropdown helpers (#3982): js/appointmentLocationSelect.js.
const test = require('node:test');
const assert = require('node:assert/strict');
const {
    selectAppointmentLocationByName,
    selectAppointmentLocationCode,
} = require('../src/main/webapp/js/appointmentLocationSelect');

/** A minimal stand-in for an HTMLSelectElement with the dropdown the tag renders. */
function dropdown() {
    const options = [
        {value: '', text: 'Not specified', attrs: []},
        {value: 'legacy', text: 'Legacy: Basement', attrs: ['data-legacy']},
        {value: '11', text: 'Virtual', attrs: []},
        {value: '12', text: ' Room 2 ', attrs: []},
        {value: '13', text: 'Satellite (disabled)', attrs: ['data-inactive']},
    ].map((o) => ({value: o.value, text: o.text, hasAttribute: (name) => o.attrs.includes(name)}));
    return {options, value: 'legacy'};
}

test('chooses the active location an appointment type names, ignoring case and spaces', () => {
    const select = dropdown();
    assert.equal(selectAppointmentLocationByName(select, '  room 2'), true);
    assert.equal(select.value, '12');
});

test('never chooses not specified, a legacy or a disabled location by name', () => {
    const select = dropdown();
    assert.equal(selectAppointmentLocationByName(select, 'Legacy: Basement'), false);
    assert.equal(selectAppointmentLocationByName(select, 'Satellite (disabled)'), false);
    assert.equal(selectAppointmentLocationByName(select, 'Not specified'), false);
    assert.equal(selectAppointmentLocationByName(select, ''), false);
    assert.equal(selectAppointmentLocationByName(select, null), false);
    assert.equal(select.value, 'legacy');
});

test('pastes a copied location code when the dropdown offers it', () => {
    const select = dropdown();
    selectAppointmentLocationCode(select, '11');
    assert.equal(select.value, '11');
    selectAppointmentLocationCode(select, 13);
    assert.equal(select.value, '13');
});

test('falls back to not specified for a code the dropdown does not offer or a legacy copy', () => {
    for (const code of ['99', 'legacy', '', null, undefined]) {
        const select = dropdown();
        selectAppointmentLocationCode(select, code);
        assert.equal(select.value, '', `code ${code}`);
    }
});

test('tolerates a page without the dropdown', () => {
    assert.equal(selectAppointmentLocationByName(null, 'Virtual'), false);
    assert.doesNotThrow(() => selectAppointmentLocationCode(undefined, '11'));
});
