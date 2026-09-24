/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// Regression checks for the staged-card helpers in SearchDrug3.jsp:
//   #3871 - the card X button must send the card's stash random id, not the drug id;
//   #3872 - unticking ReRx must find the card through data-drug-ref-id, not set_<drugId>.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.join(__dirname, '..');
const jsp = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
const prescribe = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/rx/prescribe.jsp'), 'utf8');

function slice(startMarker, endMarker) {
    const start = jsp.indexOf(startMarker);
    const end = jsp.indexOf(endMarker, start);
    assert.ok(start >= 0 && end > start, `could not find ${startMarker}`);
    return jsp.slice(start, end);
}

const helpers = slice('function removeDrugFromReRxList(', 'function updateQty(');

function card(id, drugRefId) {
    return {
        id,
        removed: false,
        getAttribute(name) { return name === 'data-drug-ref-id' ? drugRefId : null; },
        remove() { this.removed = true; },
    };
}

function run(script, cards) {
    const calls = { deletePrescribe: [], removeReRxDrugId: [] };
    const byId = Object.fromEntries(cards.map(c => [c.id, c]));
    const pane = {
        querySelectorAll(selector) {
            assert.equal(selector, 'fieldset[data-drug-ref-id]');
            return cards;
        },
    };
    const context = {
        document: {
            getElementById(id) { return id === 'rxText' ? pane : (byId[id] || null); },
        },
        deletePrescribe: randomId => calls.deletePrescribe.push(randomId),
        removeReRxDrugId: drugId => calls.removeReRxDrugId.push(drugId),
        String,
    };
    vm.runInNewContext(`${helpers}\n${script}`, context);
    return calls;
}

test('card X button on a ReRx card sends only the stash deletion by random id (#3871, #3908)', () => {
    const staged = card('set_734512', '4242');
    const checkbox = { id: 'reRxCheckBox_4242', checked: true, getAttribute() { return null; } };
    const calls = run('removePrescribingDrug(document.getElementById("set_734512"), 4242);', [staged, checkbox]);

    // deletePrescribe removes exactly this card and un-ticks its source server-side. A second
    // removeFromReRxDrugIdList request would remove the first stash entry for the source in an
    // unordered request and could drop another draft.
    assert.deepEqual(calls.deletePrescribe, ['734512']);
    assert.equal(staged.removed, true);
    assert.deepEqual(calls.removeReRxDrugId, []);
    assert.equal(checkbox.checked, false);
});

test('card X button on a new drug does not touch the ReRx list (#3871)', () => {
    const staged = card('set_900001', '0');
    const calls = run('removePrescribingDrug(document.getElementById("set_900001"), 0);', [staged]);

    assert.deepEqual(calls.deletePrescribe, ['900001']);
    assert.deepEqual(calls.removeReRxDrugId, []);
});

test('unticking ReRx removes the card linked by data-drug-ref-id (#3872)', () => {
    const other = card('set_111', '77');
    const target = card('set_222', '4242');
    const calls = run('removeDrugFromReRxList(4242);', [other, target]);

    assert.equal(target.removed, true);
    assert.equal(other.removed, false);
    assert.deepEqual(calls.removeReRxDrugId, [4242]);
});

test('archive-on-close mechanism is gone (#3871)', () => {
    assert.ok(!jsp.includes('deleteOnCloseRxBox'));
    assert.ok(!jsp.includes('DeleteRxOnCloseRxBox'));
});

test('staged cards carry an encoded data-drug-ref-id (#3872)', () => {
    assert.match(prescribe, /<fieldset[^>]*id="<%=fieldSetId%>"[^>]*data-drug-ref-id="<carlos:encode value='<%= String\.valueOf\(DrugReferenceId\) %>' context="htmlAttribute"\/>"/);
});
