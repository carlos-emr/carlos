/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// SearchDrug3.jsp's drug search: DrugRef answers a search with no match with one placeholder row, "None found"
// (id 0). The page used to list it as a product, so ArrowDown+Enter or a click staged drug id 0. It is now a
// translated note that cannot be picked; every other row is still a product.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(
    path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');

function slice(startMarker, endMarker) {
    const start = jsp.indexOf(startMarker);
    const end = jsp.indexOf(endMarker, start + startMarker.length);
    assert.ok(start >= 0 && end > start, `could not find ${startMarker}`);
    return jsp.slice(start, end);
}

const jsMsg = { drugSearchNoMatch: 'Aucun médicament ne correspond à cette recherche.' };
// Each case slices the page's own code when it runs, so one missing piece fails only the cases that use it.
const toDrugSearchItem = (row, keyword) => vm.runInNewContext(
    `${slice('function toDrugSearchItem(', 'function searchResultsHandler(')}\ntoDrugSearchItem(row, keyword);`,
    { jsMsg, row, keyword });

/** Runs the autocomplete's own select handler; returns the stage and box calls it made. */
function select(event, ui) {
    const calls = [];
    const handler = vm.runInNewContext(
        `(${slice('select: function (event, ui) {', '\n\t\t\t\t\t},').slice('select: '.length)}\n\t\t\t\t\t})`,
        { searchResultsHandler: (...args) => calls.push(['stage', ...args]), jQuery: () => ({ val: v => calls.push(['val', v]) }) });
    handler(event, ui);
    return calls;
}

/** A minimal jQuery for the renderer: each element records its markup, text, data and children. */
function fakeJQuery() {
    const make = markup => {
        const el = { markup, children: [] };
        return Object.assign(el, {
            data(key, value) { el.stored = { key, value }; return el; },
            append(child) { el.children.push(child); return el; },
            appendTo(parent) { parent.push(el); return el; },
            text(value) { el.textValue = value; return el; },
        });
    };
    const jQuery = markup => make(markup);
    jQuery.ui = { autocomplete: { escapeRegex: s => s } };
    return jQuery;
}
function render(item) {
    const ul = [];
    const renderItem = slice('._renderItem = function (ul, item) {', '\n\t\t\t\t};').slice('._renderItem = '.length) + '\n\t\t\t\t}';
    vm.runInNewContext(`(${renderItem})(ul, item);`, { ul, item, jQuery: fakeJQuery(), replaceAll: label => label });
    assert.equal(ul.length, 1, 'the renderer appends one row');
    return ul[0];
}

test('the "None found" row becomes the translated no-match note, whether its id is a string or a number', () => {
    for (const id of ['0', 0]) {
        const item = toDrugSearchItem({ id, name: 'None found', description: '', isInactive: false }, 'ZZQX');
        assert.equal(item.noMatch, true);
        assert.equal(item.label, jsMsg.drugSearchNoMatch);
        assert.equal(item.id, null, 'the note carries no drug id to stage');
    }
});

test('a product row keeps its name, description, id and inactive flag', () => {
    const item = toDrugSearchItem({ id: '1866', name: 'LIPITOR 10MG', description: 'ATORVASTATIN', isInactive: true }, 'LIP');
    assert.deepEqual({ ...item }, { label: 'LIPITOR 10MG', value: 'ATORVASTATIN', id: '1866', active: true, keyword: 'LIP' });
});

test('selecting the no-match note stages nothing and keeps the typed text', () => {
    let prevented = 0;
    const calls = select({ preventDefault() { prevented++; } }, { item: { noMatch: true, label: jsMsg.drugSearchNoMatch, id: null } });
    assert.equal(prevented, 1);
    assert.deepEqual(calls, []);
});

test('selecting a product stages it and clears the search box', () => {
    const item = { label: 'LIPITOR 10MG', id: '1866' };
    assert.deepEqual(select({ preventDefault() {} }, { item }), [['stage', null, item], ['val', '']]);
});

test('the no-match note renders as a disabled row with no link', () => {
    const row = render({ noMatch: true, label: jsMsg.drugSearchNoMatch, id: null });
    assert.match(row.markup, /^<li class='ui-state-disabled'><\/li>$/);
    assert.equal(row.children.length, 1);
    assert.equal(row.children[0].markup, '<div></div>');
    assert.equal(row.children[0].textValue, jsMsg.drugSearchNoMatch, 'the note is set as text, not markup');
});

test('a product renders as a link carrying its drug id', () => {
    const row = render({ label: 'LIPITOR 10MG', id: '1866', active: false, keyword: 'LIP' });
    assert.equal(row.markup, '<li></li>');
    assert.match(row.children[0], /^<a href='#' id='1866' class='drugitem' >LIPITOR 10MG<\/a>$/);
});
