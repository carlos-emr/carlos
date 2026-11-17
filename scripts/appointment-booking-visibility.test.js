/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp'), 'utf8');
const match = jsp.match(/^([ \t]*)function updateBookingButtonVisibility\(locked\) \{[\s\S]*?^\1\}/m);
assert.ok(match, 'Expected updateBookingButtonVisibility in addappointment.jsp');
const source = match[0];
function form(ids = ['addButton', 'pasteButton', 'apptRepeatButton', 'groupButton']) {
    const buttons = Object.fromEntries(ids.map(id => [id, { style: { display: '' } }]));
    const context = vm.createContext({ document: { getElementById: id => buttons[id] }, haveLock: false, groupBookingRestricted: false });
    vm.runInContext(source, context);
    return { buttons, context, refresh: locked => context.updateBookingButtonVisibility(locked) };
}

test('foreign locks hide booking controls and releasing the lock restores them', () => {
    const f = form();
    f.refresh(true);
    for (const button of Object.values(f.buttons)) assert.equal(button.style.display, 'none');
    f.refresh(false);
    for (const button of Object.values(f.buttons)) assert.equal(button.style.display, '');
});
test('a same-day group restriction survives both an unlocked refresh and our own lock', () => {
    const f = form();
    f.context.groupBookingRestricted = true;
    f.refresh(false);
    for (const button of Object.values(f.buttons)) assert.equal(button.style.display, 'none');
    f.context.haveLock = true;
    f.refresh(true);
    for (const button of Object.values(f.buttons)) assert.equal(button.style.display, 'none');
    f.context.groupBookingRestricted = false;
    f.refresh(true);
    for (const button of Object.values(f.buttons)) assert.equal(button.style.display, '');
});
test('restricted layouts without Add or Repeat controls still keep Paste hidden', () => {
    const f = form(['pasteButton']);
    f.context.groupBookingRestricted = true;
    assert.doesNotThrow(() => f.refresh(false));
    assert.equal(f.buttons.pasteButton.style.display, 'none');
});

const localeMatch = jsp.match(/^([ \t]*)function locale\(\) \{[\s\S]*?^\1\}/m);
assert.ok(localeMatch, 'Expected location styling initializer in addappointment.jsp');
const onloadMatch = jsp.match(/<body onLoad="([^"]+)"/);
assert.ok(onloadMatch, 'Expected Add Appointment onload handler');
function initializeLocation(location) {
    const called = [];
    const context = vm.createContext({ document: { forms: { ADDAPPT: { location } } },
        setfocus: () => called.push('focus'), moveAppt: () => called.push('move'),
        updateTime: () => called.push('time') });
    vm.runInContext(localeMatch[0], context);
    vm.runInContext(onloadMatch[1], context);
    return called;
}

test('a provider without assigned active sites can initialize Add Appointment', () => {
    const location = { nodeName: 'SELECT', selectedIndex: -1, options: [], style: { backgroundColor: 'white' } };
    assert.deepEqual(initializeLocation(location), ['focus', 'move', 'time']);
    assert.equal(location.style.backgroundColor, 'white');
});
test('Add Appointment colors the selected site rather than another site', () => {
    const location = { nodeName: 'SELECT', selectedIndex: 1,
        options: [{ style: { backgroundColor: 'red' } }, { style: { backgroundColor: 'blue' } }], style: {} };
    initializeLocation(location);
    assert.equal(location.style.backgroundColor, 'blue');
});
test('free-text appointment locations initialize without select options', () => {
    const location = { nodeName: 'INPUT', style: { backgroundColor: 'white' } };
    assert.doesNotThrow(() => initializeLocation(location));
    assert.equal(location.style.backgroundColor, 'white');
});
