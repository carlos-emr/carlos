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
    [{ status: 500, responseJSON: { error: 'INCOMPLETE_RX_SAVE' } }, 'review the chart before retrying'],
    [{ status: 409, responseJSON: { error: 'STALE_RX_STASH' } }, 'review the current draft'],
    [{ status: 409, responseJSON: null, responseText: '<html>Conflict</html>' }, 'reopen the session'],
    [{ status: 409, responseJSON: { error: 'OTHER' } }, 'reopen the session'],
    [{ status: 500, responseJSON: { error: 'STALE_RX_STASH' } }, 'reopen the session (HTTP 500)'],
]) {
    test(`refused save explains the fixed stale-draft conflict only: ${JSON.stringify(transport)}`, () => {
        const alerts = [];
        const context = {
            jsMsg: { staleDraft: 'review the current draft', saveRefused: 'reopen the session', saveIncomplete: 'review the chart before retrying' },
            alert(message) { alerts.push(message); },
        };
        // No reload, retry or DOM mutation is available: refusal must preserve local edits.
        vm.runInNewContext(refusedSave, context);
        context.reportRefusedSave(transport);
        assert.deepEqual(alerts, [expected]);
    });
}

test('Save Only requests atomic cleanup and never clears the shared stash afterwards', () => {
    const calls = [];
    let cleared = 0;
    const context = {
        validateWrittenDate: () => true, validateRxDate: () => true,
        setPharmacyId() {}, ctx: '/carlos',
        document: { getElementById: () => ({}) },
        FormData: class { delete() {} }, URLSearchParams: class { toString() { return 'drugName_42=draft'; } },
        CarlosAjax: { request(url, options) { calls.push({url, options}); } },
        refreshDrugProfile() {}, clearStashDisplay() { cleared++; },
        reportRefusedSave() {},
    };
    const code = slice('function updateSaveAllDrugsContinue(', '/**')
        .replace(/<%if \(CarlosProperties[\s\S]*?<%}%>/, '');
    vm.runInNewContext(code, context);
    context.updateSaveAllDrugsContinue();
    assert.match(calls[0].options.postBody, /&clearSaved=true$/);
    assert.equal(cleared, 0);
    calls[0].options.onSuccess({status: 200, responseText: '<html>login</html>'});
    assert.equal(cleared, 0, 'a successful error/login page must not hide the draft');
    calls[0].options.onSuccess({status: 200, responseText: '{"scriptId":123}'});
    assert.equal(cleared, 1);
    assert.equal(calls.length, 1, 'a post-save clear can destroy another window\'s newer draft');
});

for (const name of ['updateSaveAllDrugsPrintContinue', 'updateSaveAllDrugsContinue']) {
    test(`${name} omits search text while preserving every prescription field`, () => {
        const fields = [
            ['searchString', 'http://10.0.0.5/pacs/study'],
            ['drugName_42', "Patient's custom medication"], ['instructions_42', '1 tab PO BID'],
            ['rxComment_42', 'Wound <?> follow up'], ['demographicNo', '123'],
            ['draftRevision_42', '7'], ['csrfToken', 'test-token'], ['selected', '1'], ['selected', '2'],
        ];
        const form = {};
        const calls = [];
        const context = {
            validateWrittenDate: () => true, validateRxDate: () => true,
            setPharmacyId() {}, ctx: '/carlos',
            document: { getElementById(id) { assert.equal(id, 'drugForm'); return form; } },
            FormData: class extends URLSearchParams {
                constructor(element) { assert.equal(element, form); super(fields); }
            }, URLSearchParams,
            CarlosAjax: { request(url, options) { calls.push({url, options}); } },
            reportRefusedSave() {},
        };
        const code = (name === 'updateSaveAllDrugsPrintContinue'
            ? slice('function updateSaveAllDrugsPrintContinue(', 'function updateSaveAllDrugsContinue(')
            : slice('function updateSaveAllDrugsContinue(', '/**'))
            .replace(/<%if \(CarlosProperties[\s\S]*?<%}%>/, '');
        vm.runInNewContext(code, context);
        context[name]();
        assert.equal(calls.length, 1);
        const posted = new URLSearchParams(calls[0].options.postBody);
        assert.equal(posted.has('searchString'), false);
        const expected = fields.filter(([key]) => key !== 'searchString');
        if (name === 'updateSaveAllDrugsContinue') expected.push(['clearSaved', 'true']);
        assert.deepEqual([...posted], expected);
        assert.equal(fields[0][1], 'http://10.0.0.5/pacs/study', 'the search box is not cleared by serialization');
    });
}

// The unstaged-ReRx warning's first sentence is each locale's key as it is: the page used to put an
// English "There " in front of it (#4894). The count goes in by replacing the key's literal {0}.
function bundle(locale) {
    const text = fs.readFileSync(
        path.join(__dirname, `../src/main/resources/oscarResources_${locale}.properties`), 'utf8');
    const values = {};
    for (const line of text.split('\n')) {
        const match = /^SearchDrug\.js\.(\w+)=(.*)$/.exec(line);
        if (match) values[match[1]] = match[2].replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
    }
    return values;
}

const confirmationMessage = slice('const CONFIRMATION_MESSAGE', '<%--');
for (const [locale, one, two, five] of [
    ['en', 'There is 1 unstaged ReRx drug.', 'There are 2 unstaged ReRx drugs.', 'There are 5 unstaged ReRx drugs.'],
    ['fr', 'Il y a 1 médicament ReRx non ajouté.', 'Il y a 2 médicaments ReRx non ajoutés.', 'Il y a 5 médicaments ReRx non ajoutés.'],
    ['es', 'Hay 1 medicamento ReRx sin agregar.', 'Hay 2 medicamentos ReRx sin agregar.', 'Hay 5 medicamentos ReRx sin agregar.'],
    ['pt_BR', 'Há 1 medicamento ReRx não adicionado.', 'Há 2 medicamentos ReRx não adicionados.', 'Há 5 medicamentos ReRx não adicionados.'],
    // Polish plural forms change at 5, so the count-taking sentence avoids them.
    ['pl', 'Jest 1 nieprzetworzona recepta ReRx.', 'Liczba nieprzetworzonych recept ReRx: 2.', 'Liczba nieprzetworzonych recept ReRx: 5.'],
]) {
    test(`the unstaged-ReRx warning is a whole ${locale} sentence for 1, 2 and 5 drugs`, () => {
        const jsMsg = bundle(locale);
        for (const [count, sentence] of [[1, one], [2, two], [5, five]]) {
            const message = vm.runInNewContext(`${confirmationMessage}\nbuildConfirmationMessage(${count});`,
                { jsMsg, selectedReRxIDs: [] });
            assert.deepEqual(message.split('\n'), [sentence, jsMsg.saveWarning, jsMsg.savePrompt]);
        }
    });
}
