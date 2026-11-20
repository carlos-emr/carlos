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

// The pre-write error list and isUncertainSaveOutcome come before reportRefusedSave and are part
// of its contract; setPharmacyId is the next function after them.
const refusedSave = slice('var RX_SAVE_PRE_WRITE_ERRORS', 'function setPharmacyId(');
const refusedMessages = {
    alreadySaved: 'already saved; review the profile', staleDraft: 'review the current draft', saveRefused: 'reopen the session',
    saveIncomplete: 'review the chart before retrying', saveUncertain: 'it may be saved; check the profile',
};

function reportRefused(transport) {
    const alerts = [];
    const events = [];
    let refreshed = 0;
    const context = {
        jsMsg: refusedMessages,
        alert(message) { alerts.push(message); events.push('alert'); },
        refreshDrugProfile() { refreshed++; events.push('refresh'); },
    };
    // No reload, retry or DOM mutation of the staged cards is available: a refusal must preserve
    // local edits, and an uncertain outcome must not resend cards a committed save consumed.
    vm.runInNewContext(refusedSave, context);
    context.reportRefusedSave(transport);
    // The alert blocks the page; reloading the profile after it shows what the server now holds.
    if (refreshed) assert.deepEqual(events, ['alert', 'refresh']);
    return { alerts, refreshed };
}

for (const responseText of ['<html>unexpected page</html>', '{"scriptI', '{}', '{"scriptId":0}']) {
    test(`Save And Print reports an unconfirmed 2xx save without opening a preview: ${responseText}`, () => {
        const events = [];
        const context = {
            jsMsg: refusedMessages,
            alert(message) { events.push(['alert', message]); },
            refreshDrugProfile() { events.push(['refresh']); },
            popForm2() { assert.fail('an unconfirmed save must not print'); },
        };
        vm.runInNewContext(refusedSave + slice('function openSavedPrescriptionPreview(', 'function popForm2('), context);
        context.openSavedPrescriptionPreview({status: 200, responseText,
            responseURL: 'https://clinic.test/carlos/rx/WriteScript'});
        assert.deepEqual(events, [['alert', refusedMessages.saveUncertain + ' (HTTP 200)'], ['refresh']]);
    });
}

for (const [transport, expected] of [
    [{ status: 500, responseJSON: { error: 'INCOMPLETE_RX_SAVE' } }, 'review the chart before retrying'],
    [{ status: 409, responseJSON: { error: 'STALE_RX_STASH' } }, 'review the current draft'],
    [{ status: 409, responseJSON: null, responseText: '<html>Conflict</html>' }, 'reopen the session'],
    [{ status: 409, responseJSON: { error: 'OTHER' } }, 'reopen the session'],
]) {
    test(`refused save explains the fixed stale-draft conflict only: ${JSON.stringify(transport)}`, () => {
        assert.deepEqual(reportRefused(transport), { alerts: [expected], refreshed: 0 });
    });
}

// #5686: only a refusal proves nothing was written. A 4xx is refused before any write, and so is a
// 5xx that names one of the action's pre-write refusal codes, and a 2xx page that a login or
// facility-selection redirect answered instead of the save action.
for (const transport of [
    { status: 200, responseJSON: null, responseText: '<html>login</html>', responseURL: 'https://clinic.test/carlos/login?timeout=1' },
    { status: 200, responseJSON: null, responseText: '<html>facility</html>', responseURL: 'https://clinic.test/carlos/select/facility' },
    { status: 400, responseJSON: null, responseText: '<html>Bad Request</html>' },
    { status: 403, responseJSON: null, responseText: 'CSRF validation failed' },
    { status: 405, responseJSON: null, responseText: '' },
    { status: 413, responseJSON: null, responseText: '<html>Request Entity Too Large</html>' },
    { status: 400, responseJSON: { error: 'INVALID_RX_DRAFT' } },
    { status: 500, responseJSON: { error: 'RX_DRAFT_VALIDATION_FAILED' } },
]) {
    test(`a definite refusal still says the prescription was not saved: ${JSON.stringify(transport)}`, () => {
        assert.deepEqual(reportRefused(transport),
            { alerts: [`reopen the session (HTTP ${transport.status})`], refreshed: 0 });
    });
}

// #5686: an outcome that may follow a commit must not claim "NOT saved"; it asks for a check of
// the medication profile, which it reloads, so a retry does not issue a duplicate prescription.
for (const [transport, expected] of [
    [{ status: 0, responseJSON: null, responseText: 'Network error: Failed to execute send' }, ''],
    [{ status: 0, responseJSON: null, responseText: '' }, ''],
    [undefined, ''],
    [{ status: 500, responseJSON: null, responseText: '<html>Internal Server Error</html>' }, ' (HTTP 500)'],
    [{ status: 500, responseJSON: { error: 'OTHER' } }, ' (HTTP 500)'],
    // STALE_RX_STASH is only ever a 409: a 500 that carries it is not a refusal the action sends.
    [{ status: 500, responseJSON: { error: 'STALE_RX_STASH' } }, ' (HTTP 500)'],
    [{ status: 502, responseJSON: null, responseText: '<html>Bad Gateway</html>' }, ' (HTTP 502)'],
    [{ status: 503, responseJSON: null, responseText: '' }, ' (HTTP 503)'],
    [{ status: 504, responseJSON: null, responseText: '<html>Gateway Time-out</html>' }, ' (HTTP 504)'],
    [{ status: 200, responseJSON: null, responseText: '<html>login</html>' }, ' (HTTP 200)'],
    [{ status: 200, responseJSON: { scriptId: 'x' }, responseText: '{"scriptId":"x"}' }, ' (HTTP 200)'],
    [{ status: 200, responseJSON: null, responseText: '{"scriptI',
        responseURL: 'https://clinic.test/carlos/rx/WriteScript?parameterValue=updateSaveAllDrugs&rand=7' }, ' (HTTP 200)'],
]) {
    test(`an unconfirmed save reports an uncertain outcome: ${JSON.stringify(transport)}`, () => {
        assert.deepEqual(reportRefused(transport),
            { alerts: [refusedMessages.saveUncertain + expected], refreshed: 1 });
    });
}

test('every locale ships the uncertain-save message the page reads', () => {
    assert.match(jsp, /<fmt:message key="SearchDrug\.js\.saveUncertain"\s+var="msg_saveUncertain"\/>/);
    assert.match(jsp, /saveUncertain: '\$\{carlos:forJavaScript\(msg_saveUncertain\)\}',/);
    const english = bundle('en').saveUncertain;
    assert.ok(english && !/NOT saved/i.test(english), 'the English message must not claim the save failed');
    for (const locale of ['fr', 'es', 'pl', 'pt_BR']) {
        const message = bundle(locale).saveUncertain;
        assert.ok(message, `${locale} has no SearchDrug.js.saveUncertain`);
        assert.notEqual(message, english, `${locale} SearchDrug.js.saveUncertain is untranslated`);
        assert.notEqual(message, bundle(locale).saveRefused, `${locale} reuses the refusal text`);
    }
});

test('Save Only requests atomic cleanup and never clears the shared stash afterwards', () => {
    const calls = [];
    let cleared = 0;
    const context = {
        rxSaveNeedsReconciliation: true,
        validateWrittenDate: () => true, validateRxDate: () => true, validateCardProperties: () => true,
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
    assert.equal(context.rxSaveNeedsReconciliation, true, 'an unconfirmed response must preserve the earlier uncertainty');
    calls[0].options.onSuccess({status: 200, responseText: '{"scriptId":123}'});
    assert.equal(cleared, 1);
    assert.equal(context.rxSaveNeedsReconciliation, false, 'a validated plain Save must clear the earlier uncertainty');
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
            validateWrittenDate: () => true, validateRxDate: () => true, validateCardProperties: () => true,
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

for (const name of ['updateSaveAllDrugsPrintContinue', 'updateSaveAllDrugsContinue']) {
    test(`${name} cannot save an older staged value while an inline edit is refused`, () => {
        const code = (name === 'updateSaveAllDrugsPrintContinue'
            ? slice('function updateSaveAllDrugsPrintContinue(', 'function updateSaveAllDrugsContinue(')
            : slice('function updateSaveAllDrugsContinue(', '/**'))
            .replace(/<%if \(CarlosProperties[\s\S]*?<%}%>/, '');
        const context = { validateCardProperties: () => false };
        // Any validation, serialization or request after the rejected edit would fail here.
        vm.runInNewContext(code, context);
        assert.equal(context[name](), false);
    });
}

for (const [error, message] of [['RX_ALREADY_SAVED', refusedMessages.alreadySaved], ['RX_SAVE_OUTCOME_UNKNOWN', refusedMessages.saveUncertain]]) {
    test(`a durable receipt reports ${error} without a false not-saved refusal`, () => {
        assert.deepEqual(reportRefused({status: 409, responseJSON: {error}}), {alerts: [message], refreshed: 1});
    });
}

test('a later login, CSRF, empty or stale refusal cannot erase an earlier uncertain save outcome', () => {
    const alerts=[]; let refreshes=0;
    const context={jsMsg: refusedMessages, alert: message=>alerts.push(message), refreshDrugProfile:()=>refreshes++};
    vm.runInNewContext(refusedSave, context);
    context.reportRefusedSave({status:504});
    for (const status of [400,403,409]) context.reportRefusedSave({status,responseJSON:{error:'STALE_RX_STASH'}});
    assert.equal(alerts.length,4);
    assert(alerts.every(message=>message.includes(refusedMessages.saveUncertain)));
    assert.equal(refreshes,4);
    context.reportRefusedSave({status:409,responseJSON:{error:'RX_ALREADY_SAVED'}});
    assert.equal(alerts.at(-1),refusedMessages.alreadySaved);
});

// A card edited after a lost reply is posted with the same draft revisions as the save that
// consumed them. The server tells the two apart by what was posted: RX_ALREADY_SAVED only for an
// exact replay, RX_SAVED_EARLIER_VERSION when the fields differ from what that save stored.
const recoveryMessages = {
    ...refusedMessages, removeRefused: 'still staged; try again',
    earlierVersionSaved: 'earlier version saved; your changes were NOT saved',
    reopenToContinue: 'this page can no longer save or remove its cards; reopen it',
};

function recoveryPage(revisions) {
    const fields = {};
    for (const [key, value] of Object.entries(revisions || {})) fields[`draftRevision_${key}`] = { value };
    const elements = {
        rxRecoveryNotice: { style: { display: 'none' } },
        rxRecoveryMessage: { textContent: '' },
        rxRecoveryOpenSaved: { style: { display: 'none' }, onclick: null },
        drugForm: { elements: { namedItem: (name) => fields[name] || null } },
    };
    const page = { alerts: [], refreshes: 0, cleared: 0, previews: [], fields, elements };
    page.context = {
        jsMsg: recoveryMessages,
        alert(message) { page.alerts.push(message); },
        refreshDrugProfile() { page.refreshes++; },
        clearStashDisplay() { page.cleared++; },
        popForm2(scriptId, saveAndPrint) { page.previews.push([scriptId, saveAndPrint]); },
        document: { getElementById: (id) => elements[id] || null },
    };
    vm.runInNewContext(refusedSave, page.context);
    return page;
}

for (const earlier of [null, { status: 0 }, { status: 504 }]) {
    test(`an edited resubmission of a saved draft is never reported as already saved (after ${JSON.stringify(earlier)})`, () => {
        const page = recoveryPage({ 42: 'rendered' });
        if (earlier) page.context.reportRefusedSave(earlier);
        page.alerts.length = 0;
        page.context.reportRefusedSave({ status: 409,
            responseJSON: { error: 'RX_SAVED_EARLIER_VERSION', scriptId: 91, draftRevisions: { 42: 'current' } } });
        // One plain statement: not the reassurance, and no longer "it may be saved" either.
        assert.deepEqual(page.alerts, [recoveryMessages.earlierVersionSaved]);
        assert.equal(page.context.rxSaveNeedsReconciliation, false);
        assert.equal(page.context.rxPageNeedsReopen, true);
        // The edited cards stay to read from, untouched and unsaveable; nothing is offered to print.
        assert.equal(page.cleared, 0);
        assert.equal(page.fields.draftRevision_42.value, 'rendered');
        assert.equal(page.elements.rxRecoveryNotice.style.display, '');
        assert.equal(page.elements.rxRecoveryMessage.textContent, recoveryMessages.earlierVersionSaved);
        assert.equal(page.elements.rxRecoveryOpenSaved.style.display, 'none');
        assert.equal(page.elements.rxRecoveryOpenSaved.onclick, null);
        assert.deepEqual(page.previews, []);
    });
}

test('an exact replay of Save And Print takes the acknowledged card versions and offers the saved prescription', () => {
    const page = recoveryPage({ 42: 'rendered', 43: 'rendered too' });
    page.context.reportRefusedSave({ status: 0 });
    page.context.reportRefusedSave({ status: 409, responseJSON: { error: 'RX_ALREADY_SAVED', scriptId: 91,
        draftRevisions: { 42: 'acknowledged', 43: 'acknowledged too', 'not a key': 'ignored', 99: 'no such card' } } });
    assert.equal(page.alerts.at(-1), recoveryMessages.alreadySaved);
    assert.equal(page.context.rxSaveNeedsReconciliation, false);
    assert.equal(page.context.rxPageNeedsReopen, false, 'the page can carry on');
    // As the lost reply would have left them: still staged, and saveable or removable again.
    assert.deepEqual([page.fields.draftRevision_42.value, page.fields.draftRevision_43.value],
        ['acknowledged', 'acknowledged too']);
    assert.equal(page.cleared, 0);
    assert.equal(page.elements.rxRecoveryNotice.style.display, '');
    assert.equal(page.elements.rxRecoveryMessage.textContent, recoveryMessages.alreadySaved);
    // Opening for print can sign the prescription, so it is offered and never done unasked.
    assert.deepEqual(page.previews, []);
    assert.equal(page.elements.rxRecoveryOpenSaved.style.display, '');
    page.elements.rxRecoveryOpenSaved.onclick();
    assert.deepEqual(page.previews, [[91, true]]);
});

for (const answer of [{ error: 'RX_ALREADY_SAVED', scriptId: 91 }, { error: 'RX_ALREADY_SAVED', scriptId: 91, draftRevisions: {} }]) {
    test(`an exact replay whose cards the server no longer holds clears them from the page: ${JSON.stringify(answer)}`, () => {
        const page = recoveryPage({ 42: 'rendered' });
        page.context.reportRefusedSave({ status: 409, responseJSON: answer });
        assert.deepEqual(page.alerts, [recoveryMessages.alreadySaved]);
        assert.equal(page.cleared, 1);
        assert.equal(page.refreshes, 1);
        assert.equal(page.context.rxPageNeedsReopen, false);
        assert.equal(page.elements.rxRecoveryOpenSaved.style.display, '');
    });
}

test('a save the server cannot settle says the page must be reopened', () => {
    const page = recoveryPage({ 42: 'rendered' });
    page.context.reportRefusedSave({ status: 409, responseJSON: { error: 'RX_SAVE_OUTCOME_UNKNOWN' } });
    assert.deepEqual(page.alerts, [recoveryMessages.saveUncertain]);
    assert.equal(page.context.rxPageNeedsReopen, true);
    assert.equal(page.cleared, 0);
    assert.equal(page.elements.rxRecoveryNotice.style.display, '');
    assert.equal(page.elements.rxRecoveryMessage.textContent, recoveryMessages.reopenToContinue);
    assert.equal(page.elements.rxRecoveryOpenSaved.style.display, 'none');
    // A further conflict cannot settle it either: the same way on stays in view.
    page.elements.rxRecoveryNotice.style.display = 'none';
    page.context.reportRefusedSave({ status: 409, responseJSON: { error: 'STALE_RX_STASH' } });
    assert.equal(page.alerts.at(-1), recoveryMessages.saveUncertain);
    assert.equal(page.elements.rxRecoveryNotice.style.display, '');
});

test('a login or CSRF refusal after a lost reply does not claim the page is spent', () => {
    const page = recoveryPage({ 42: 'rendered' });
    page.context.reportRefusedSave({ status: 0 });
    page.context.reportRefusedSave({ status: 403, responseJSON: null, responseText: 'CSRF validation failed' });
    assert.deepEqual(page.alerts, [recoveryMessages.saveUncertain, recoveryMessages.saveUncertain]);
    assert.equal(page.elements.rxRecoveryNotice.style.display, 'none');
    // The same draft refused as a conflict is: the server no longer accepts these cards from here.
    page.context.reportRefusedSave({ status: 409, responseJSON: { error: 'STALE_RX_STASH' } });
    assert.equal(page.elements.rxRecoveryNotice.style.display, '');
    assert.equal(page.elements.rxRecoveryMessage.textContent, recoveryMessages.reopenToContinue);
});

test('a lost reply alone leaves the page as it is, so the retry can still settle it', () => {
    const page = recoveryPage({ 42: 'rendered' });
    page.context.reportRefusedSave({ status: 0 });
    assert.equal(page.context.rxPageNeedsReopen, false);
    assert.equal(page.elements.rxRecoveryNotice.style.display, 'none');
    assert.equal(page.cleared, 0);
});

const refusedRemoval = slice('function reportRefusedRemoval(', 'function reportRefusedRequest(');
for (const [state, transport, expected] of [
    // No save in doubt: the card is still staged and the removal can simply be tried again.
    [{}, { status: 409 }, 'removeRefused'],
    [{ rxSaveNeedsReconciliation: false, rxPageNeedsReopen: false }, { status: 409 }, 'removeRefused'],
    // An unconfirmed save may never have reached the server; only a conflict shows spent cards.
    [{ rxSaveNeedsReconciliation: true, rxPageNeedsReopen: false }, { status: 0 }, 'removeRefused'],
    [{ rxSaveNeedsReconciliation: true, rxPageNeedsReopen: false }, undefined, 'removeRefused'],
    [{ rxSaveNeedsReconciliation: true, rxPageNeedsReopen: false }, { status: 409 }, 'reopenToContinue'],
    // The server has said these cards are spent: no removal from this page can work.
    [{ rxSaveNeedsReconciliation: false, rxPageNeedsReopen: true }, { status: 409 }, 'reopenToContinue'],
    [{ rxSaveNeedsReconciliation: false, rxPageNeedsReopen: true }, undefined, 'reopenToContinue'],
]) {
    test(`a refused card removal says "try again" only while that can work: ${JSON.stringify([state, transport])}`, () => {
        const alerts = [];
        const shown = [];
        const context = { ...state, transport, jsMsg: recoveryMessages,
            alert: (message) => alerts.push(message), showRxRecoveryNotice: (message) => shown.push(message) };
        vm.runInNewContext(`${refusedRemoval}\nreportRefusedRemoval(transport);`, context);
        assert.deepEqual(alerts, [recoveryMessages[expected]]);
        assert.deepEqual(shown, expected === 'reopenToContinue' ? [recoveryMessages.reopenToContinue] : []);
    });
}

test('the reopen action returns to this patient\'s prescribing page', () => {
    const window = { location: { href: '' } };
    const context = { jsMsg: recoveryMessages, ctx: '/carlos', window,
        RxPatientContext: { withPatient: (url) => `${url}?demographicNo=7` } };
    vm.runInNewContext(refusedSave, context);
    context.reopenRxPage();
    assert.equal(window.location.href, '/carlos/rx/choosePatient?demographicNo=7');
});

test('every locale ships the earlier-version and reopen messages the page reads', () => {
    assert.match(jsp, /<fmt:message key="oscarRx\.saveEarlierVersionSaved" var="msg_saveEarlierVersionSaved"\/>/);
    assert.match(jsp, /earlierVersionSaved: '\$\{carlos:forJavaScript\(msg_saveEarlierVersionSaved\)\}',/);
    assert.match(jsp, /reopenToContinue: '\$\{carlos:forJavaScript\(msg_reopenToContinue\)\}',/);
    assert.match(jsp, /id="rxRecoveryOpenSaved"[^>]*><fmt:message key="SearchDrug\.recovery\.btnOpenSaved"\/>/);
    assert.match(jsp, /id="rxRecoveryReopen"[^>]*onclick="reopenRxPage\(\);"><fmt:message key="SearchDrug\.recovery\.btnReopen"\/>/);
    const read = (locale, key) => {
        const text = fs.readFileSync(
            path.join(__dirname, `../src/main/resources/oscarResources_${locale}.properties`), 'utf8');
        const line = text.split('\n').find((candidate) => candidate.startsWith(`${key}=`));
        return line ? line.slice(key.length + 1) : undefined;
    };
    const keys = ['oscarRx.saveEarlierVersionSaved', 'SearchDrug.js.reopenToContinue',
        'SearchDrug.recovery.btnReopen', 'SearchDrug.recovery.btnOpenSaved'];
    const earlier = read('en', keys[0]);
    assert.match(earlier, /NOT saved/, 'the English message must say the changes were not saved');
    assert.doesNotMatch(earlier, /do not enter it again/i, 'it must not repeat the already-saved reassurance');
    assert.match(earlier, /medication profile/);
    for (const key of keys) {
        assert.ok(read('en', key), `en has no ${key}`);
        for (const locale of ['fr', 'es', 'pl', 'pt_BR']) {
            assert.ok(read(locale, key), `${locale} has no ${key}`);
            assert.notEqual(read(locale, key), read('en', key), `${locale} ${key} is untranslated`);
        }
    }
    for (const locale of ['en', 'fr', 'es', 'pl', 'pt_BR']) {
        assert.notEqual(read(locale, keys[0]), read(locale, 'oscarRx.saveAlreadySaved'),
            `${locale} reuses the already-saved text`);
    }
});
