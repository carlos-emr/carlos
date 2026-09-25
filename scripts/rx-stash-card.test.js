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
    const requests = [];
    const alerts = [];
    const byId = Object.fromEntries(cards.map(c => [c.id, c]));
    const pane = { querySelectorAll() { return cards.filter(c => c.id.startsWith('set_')); } };
    const context = {
        document: { getElementById(id) { return id === 'rxText' ? pane : (byId[id] || null); } },
        jQuery(selector) { return { remove() { const el = byId[selector.slice(1)]; if (el) el.remove(); } }; },
        CarlosAjax: { request(url, options) { requests.push({url, options}); } },
        ctx: '/carlos',
        jsMsg: { removeRefused: 'Removal failed' },
        alert: message => alerts.push(message),
        selectedReRxIDs: [],
        updateReRxStageConfirmBoxVisibility() {},
        String,
    };
    const deleteHelper = slice('function deletePrescribe(', '    skipParseInstr = false;');
    const removeHelper = slice('function removeReRxDrugId(', '//represcribe a drug');
    vm.runInNewContext(`${deleteHelper}\n${removeHelper}\n${helpers}\n${script}`, context);
    return { requests, alerts, context };
}

test('card X waits for deletion success before hiding the card and unchecking ReRx', () => {
    const staged = card('set_734512', '4242');
    const checkbox = { id: 'reRxCheckBox_4242', checked: true };
    const { requests } = run('removePrescribingDrug(document.getElementById("set_734512"), 4242);', [staged, checkbox]);
    assert.equal(requests.length, 1);
    assert.match(requests[0].options.parameters, /^randomId=734512&/);
    assert.equal(staged.removed, false);
    assert.equal(checkbox.checked, true);
    requests[0].options.onSuccess();
    assert.equal(staged.removed, true);
    assert.equal(checkbox.checked, false);
});

test('a refused card deletion stays visible and can be retried', () => {
    const staged = card('set_900001', '0');
    const { requests, alerts, context } = run('removePrescribingDrug(document.getElementById("set_900001"), 0);', [staged]);
    requests[0].options.onFailure({status: 409});
    assert.equal(staged.removed, false);
    assert.deepEqual(alerts, ['Removal failed']);
    context.removePrescribingDrug(staged, 0);
    requests[1].options.onSuccess();
    assert.equal(staged.removed, true);
});

test('unticking ReRx waits for success before removing every card for the source', () => {
    const other = card('set_111', '77');
    const target = card('set_222', '4242');
    const duplicate = card('set_333', '4242');
    const { requests } = run('removeDrugFromReRxList(4242);', [other, target, duplicate]);
    assert.equal(target.removed, false);
    assert.match(requests[0].options.parameters, /^reRxDrugId=4242&action=removeFromReRxDrugIdList/);
    requests[0].options.onSuccess();
    assert.equal(target.removed, true);
    assert.equal(duplicate.removed, true);
    assert.equal(other.removed, false);
});

test('a refused ReRx removal restores its checkbox and retains the staged card', () => {
    const staged = card('set_222', '4242');
    const checkbox = { id: 'reRxCheckBox_4242', checked: false };
    const { requests, alerts } = run('removeDrugFromReRxList(4242);', [staged, checkbox]);
    requests[0].options.onFailure({status: 403});
    assert.equal(staged.removed, false);
    assert.equal(checkbox.checked, true);
    assert.deepEqual(alerts, ['Removal failed']);
});

test('archive-on-close mechanism is gone (#3871)', () => {
    assert.ok(!jsp.includes('deleteOnCloseRxBox'));
    assert.ok(!jsp.includes('DeleteRxOnCloseRxBox'));
});

test('staged cards carry an encoded data-drug-ref-id (#3872)', () => {
    assert.match(prescribe, /<fieldset[^>]*id="<%=fieldSetId%>"[^>]*data-drug-ref-id="<carlos:encode value='<%= String\.valueOf\(DrugReferenceId\) %>' context="htmlAttribute"\/>"/);
});
