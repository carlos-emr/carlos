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
