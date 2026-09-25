/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// SearchDrug3.jsp's Save / Save And Print checks: an empty stash is refused, and a ticked ReRx
// selection that was never staged is confirmed before saving without it (#3908).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(
    path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');

function slice(startMarker, endMarker) {
    const start = jsp.indexOf(startMarker);
    const end = jsp.indexOf(endMarker, start);
    assert.ok(start >= 0 && end > start, `could not find ${startMarker}`);
    return jsp.slice(start, end);
}

const saveChecks = slice('function updateSaveAllDrugsPrintCheckContinue(', 'const CONFIRMATION_MESSAGE');

function run(call, staged) {
    const calls = [];
    const context = {
        jsMsg: { pleaseAddDrugFirst: 'add a drug first' },
        alert: (message) => calls.push(['alert', message]),
        countStagedMedications: () => staged,
        showUnstagedReRxConfirmation: (onConfirm) => calls.push(['confirm', onConfirm.name]),
        updateSaveAllDrugsPrintContinue: function updateSaveAllDrugsPrintContinue() { calls.push(['save+print']); },
        updateSaveAllDrugsContinue: function updateSaveAllDrugsContinue() { calls.push(['save']); },
    };
    vm.runInNewContext(`${saveChecks}\n${call}`, context);
    return calls;
}

test('Save And Print asks about unstaged ReRx selections before saving', () => {
    assert.deepEqual(run('updateSaveAllDrugsPrintCheckContinue();', 2),
        [['confirm', 'updateSaveAllDrugsPrintContinue']]);
});

test('Save Only asks about unstaged ReRx selections before saving', () => {
    assert.deepEqual(run('updateSaveAllDrugsCheckContinue();', 1),
        [['confirm', 'updateSaveAllDrugsContinue']]);
});

test('an empty stash is refused before any confirmation or save', () => {
    assert.deepEqual(run('updateSaveAllDrugsPrintCheckContinue();', 0), [['alert', 'add a drug first']]);
    assert.deepEqual(run('updateSaveAllDrugsCheckContinue();', 0), [['alert', 'add a drug first']]);
});

const refusedSave = slice('function reportRefusedSave(', '/**');
for (const [transport, expected] of [
    [{ status: 409, responseJSON: { error: 'STALE_RX_STASH' } }, 'review the current draft'],
    [{ status: 409, responseJSON: null, responseText: '<html>Conflict</html>' }, 'reopen the session'],
    [{ status: 409, responseJSON: { error: 'OTHER' } }, 'reopen the session'],
    [{ status: 500, responseJSON: { error: 'STALE_RX_STASH' } }, 'reopen the session (HTTP 500)'],
]) {
    test(`refused save explains the fixed stale-draft conflict only: ${JSON.stringify(transport)}`, () => {
        const alerts = [];
        const context = {
            jsMsg: { staleDraft: 'review the current draft', saveRefused: 'reopen the session' },
            alert(message) { alerts.push(message); },
        };
        // No reload, retry or DOM mutation is available: refusal must preserve local edits.
        vm.runInNewContext(refusedSave, context);
        context.reportRefusedSave(transport);
        assert.deepEqual(alerts, [expected]);
    });
}
