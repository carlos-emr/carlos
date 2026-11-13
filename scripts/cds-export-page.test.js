/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {checkedJob, newest, presentation, responseFailure, submissionNotice, lookupNotice, randomKey, controlNotice, pollDelay} = require('../src/main/webapp/js/demographic/cds-export.js');
const id = 'a8fe0033-468d-468c-ae0e-b11d372b533a';
function job(values = {}) {
    return {id, state: 'ACCEPTED', revision: 0, epoch: 0, totalPatients: null, processedPatients: 0, blockedPatients: 0,
        cancelRequested: false, workerOwned: false, artifactAvailable: false, complete: false, heldBack: false, waiting: true,
        safeCode: null, ...values};
}
test('unknown discovery total stays unknown and acceptance stays waiting', () => {
    const value = presentation(job());
    assert.equal(value.complete, false); assert.equal(value.terminal, false);
    assert.equal(value.count, 'Patient total is not yet known.'); assert.match(value.label, /waiting/);
});
test('a running job shows its selection, not a frozen processed count', () => {
    // The ledger records processed patients only when the package is published.
    for (const state of ['DISCOVERING', 'CAPTURING', 'PACKAGING', 'ENCRYPTING']) {
        const value = presentation(job({state, totalPatients: 55, workerOwned: true, waiting: false}));
        assert.equal(value.count, 'Exporting 55 patients. The count of exported and held patients is shown when the job ends.');
        assert.equal(value.terminal, false);
    }
    assert.equal(presentation(job({state: 'READY', artifactAvailable: true, complete: true, waiting: false,
        totalPatients: 55, processedPatients: 55})).count, '55 of 55 patients processed.');
    assert.equal(presentation(job({state: 'FAILED', waiting: false, totalPatients: 55})).count, '0 of 55 patients processed.');
});
test('ready requires actual artifact availability', () => {
    assert.equal(presentation(job({state: 'READY', waiting: false})).complete, false);
    assert.match(presentation(job({state: 'READY', waiting: false})).label, /unavailable/);
    assert.equal(presentation(job({state: 'READY', artifactAvailable: true, complete: true, waiting: false,
        totalPatients: 1, processedPatients: 1})).complete, true);
    assert.throws(() => checkedJob(job({state: 'READY', complete: true, waiting: false})), /STATUS_INVALID/);
});
test('artifact availability cannot hide unknown, unaccounted, blocked or cancelled patients', () => {
    for (const counters of [{}, {totalPatients: 1}, {totalPatients: 1, processedPatients: 1, blockedPatients: 1},
        {totalPatients: 1, processedPatients: 1, cancelRequested: true}]) {
        const value = job({state: 'READY', artifactAvailable: true, waiting: false, ...counters});
        assert.equal(presentation(value).complete, false);
        assert.throws(() => checkedJob({...value, complete: true}), /STATUS_INVALID/);
    }
});
test('a package with held patients is downloadable but never a complete migration', () => {
    const value = job({state: 'READY', artifactAvailable: true, heldBack: true, waiting: false,
        totalPatients: 3, processedPatients: 3, blockedPatients: 1});
    const shown = presentation(value);
    assert.equal(shown.complete, false);
    assert.equal(shown.downloadable, true);
    assert.match(shown.label, /without 1 held patient\(s\) — not a complete migration/);
    assert.throws(() => checkedJob({...value, complete: true}), /STATUS_INVALID/);
    assert.throws(() => checkedJob({...value, heldBack: false}), /STATUS_INVALID/);
    for (const changed of [{blockedPatients: 3}, {cancelRequested: true}, {artifactAvailable: false}, {processedPatients: 2}])
        assert.throws(() => checkedJob({...value, ...changed}), /STATUS_INVALID/);
    assert.equal(presentation(job({state: 'READY', artifactAvailable: true, complete: true, waiting: false,
        totalPatients: 1, processedPatients: 1})).downloadable, true);
    assert.equal(presentation(job({state: 'REVIEW_REQUIRED', waiting: false})).downloadable, false);
});
test('failed, review, cancelled and expired can never imply a completed transfer', () => {
    for (const state of ['FAILED', 'REVIEW_REQUIRED', 'CANCELLED', 'EXPIRED']) {
        assert.equal(presentation(job({state, waiting: false})).complete, false);
        assert.throws(() => checkedJob(job({state, complete: true, waiting: false})), /STATUS_INVALID/);
    }
});
test('older poll or control response cannot replace a newer attempt or revision', () => {
    const current = job({epoch: 2, revision: 10});
    assert.equal(newest(current, job({epoch: 1, revision: 11})), current);
    assert.equal(newest(current, job({epoch: 2, revision: 9})), current);
    const updated = job({epoch: 2, revision: 11}); assert.equal(newest(current, updated), updated);
});
test('malformed counters, unknown states, string booleans and clinical error text refuse', () => {
    for (const changed of [{totalPatients: undefined}, {totalPatients: 1, processedPatients: 2}, {revision: 2 ** 54},
        {epoch: -1}, {processedPatients: -1}, {state: 'COMPLETE'}, {workerOwned: 'false'}, {safeCode: '/private/patient/file'},
        {waiting: false}, {id: 'not-an-id'}]) assert.throws(() => checkedJob(job(changed)), /STATUS_INVALID/);
});
test('current cancellation disables repeated cancel without hiding final state', () => {
    assert.equal(presentation(job({cancelRequested: true})).canCancel, false);
    assert.equal(presentation(job({state: 'CANCELLED', cancelRequested: true, waiting: false})).terminal, true);
    assert.equal(presentation(job({workerOwned: true, waiting: false})).canResume, false);
});

for (const controlSucceeds of [true, false]) {
    test('in-flight polling is fenced across a ' + (controlSucceeds ? 'successful' : 'failed') + ' control', async () => {
        const vm = require('node:vm'), fs = require('node:fs');
        const nodes = new Map(), timers = new Map(), pending = [];
        let initialize, serial = 0;
        function node(id) {
            if (!nodes.has(id)) nodes.set(id, {value: '', dataset: {}, events: {},
                addEventListener(event, handler) { this.events[event] = handler; },
                removeAttribute() {}, setAttribute() {}, replaceChildren() {}, querySelectorAll() { return []; }});
            return nodes.get(id);
        }
        node('cdsExport').dataset = {context: '', owner: 'web-7', canResume: 'true'};
        const document = {getElementById: node, createElement: () => node('created-' + serial++),
            querySelector: () => ({value: 'synthetic-csrf'}), addEventListener(event, handler) {
                if (event === 'DOMContentLoaded') initialize = handler;
            }};
        const window = {document, location: {search: '?runId=' + id},
            sessionStorage: {getItem() { return ''; }, setItem() {}},
            crypto: require('node:crypto'), confirm: () => true, addEventListener() {}};
        vm.runInNewContext(fs.readFileSync(require.resolve('../src/main/webapp/js/demographic/cds-export.js'), 'utf8'), {
            window, document, URLSearchParams, AbortController,
            setTimeout(callback, delay) { const token = ++serial; timers.set(token, {callback, delay}); return token; },
            clearTimeout(token) { timers.delete(token); },
            fetch(url, options) { return new Promise(resolve => pending.push({url, options, resolve})); }
        });
        const settle = () => new Promise(resolve => setImmediate(resolve));
        function answer(call, data, ok = true) {
            call.resolve({ok, headers: {get(name) {
                return name === 'Content-Type' ? 'application/json' : '1';
            }}, async json() { return {apiVersion: 1, ok, ...data}; }});
        }
        initialize();
        answer(pending.shift(), {jobs: [], nextAfter: '', mayHaveMore: false});
        answer(pending.shift(), {job: job()});
        await settle();
        const [token, timer] = [...timers].find(([, value]) => value.delay === 5000);
        timers.delete(token); timer.callback();
        const oldPoll = pending.shift();
        const control = node('cancelExport').events.click();
        await settle();
        const command = pending.shift();
        assert.match(command.url, /cdsExportControl/);
        answer(command, controlSucceeds ? {job: job({revision: 1, cancelRequested: true})}
            : {code: 'CDS_EXPORT_REQUEST_UNAVAILABLE'}, controlSucceeds);
        await settle();
        const newPoll = pending.shift();
        assert.match(newPoll.url, /cdsExportStatus/);
        answer(oldPoll, {job: job()});
        await settle();
        assert.equal([...timers.values()].filter(value => value.delay === 5000).length, 0);
        answer(newPoll, {job: controlSucceeds ? job({state: 'CANCELLED', revision: 2,
            cancelRequested: true, waiting: false}) : job({revision: 1})});
        await control;
        assert.equal([...timers.values()].filter(value => value.delay === 5000).length, controlSucceeds ? 0 : 1);
        assert.equal(pending.length, 0);
    });
}

test('an access refusal names only a fixed missing right, never response text', () => {
    const named = responseFailure({code: 'CDS_EXPORT_ACCESS_DENIED', missingRight: '_lab_sensitive'});
    assert.equal(named.message, 'CDS_EXPORT_ACCESS_DENIED');
    assert.match(submissionNotice(named, 'key-1'), /lack read access to _lab_sensitive/);
    const hostile = responseFailure({code: '<b>x</b>', missingRight: '<script>'});
    assert.equal(hostile.message, 'CDS_EXPORT_RESPONSE_UNCONFIRMED');
    assert.equal(hostile.missingRight, undefined);
    assert.match(submissionNotice(hostile, 'key-1'), /could not be confirmed .*key-1/);
});

test('definitive control refusals are named, other failures stay unconfirmed', () => {
    assert.match(controlNotice(new Error('CDS_EXPORT_WORKER_BUSY')), /worker is processing this job/);
    assert.match(controlNotice(new Error('CDS_EXPORT_RUNTIME_CHANGED')), /Start a new export/);
    assert.match(controlNotice(new Error('CDS_EXPORT_REQUEST_UNAVAILABLE')), /could not be confirmed/);
    assert.match(submissionNotice(new Error('CDS_EXPORT_ADMISSION_DISABLED'), 'k'), /turned off.*Nothing was submitted/);
});

test('status polling backs off after failures and stops after six', () => {
    assert.equal(pollDelay(0), 5000);
    assert.equal(pollDelay(1), 10000);
    assert.equal(pollDelay(4), 60000);
    assert.equal(pollDelay(5), 60000);
    assert.equal(pollDelay(6), null);
});
test('page text replaces the English defaults and fills its markers', () => {
    // A fresh copy of the script, so the shared module keeps its English defaults for the other tests.
    const vm = require('node:vm'), fs = require('node:fs');
    const sandbox = {module: {exports: {}}};
    vm.runInNewContext(fs.readFileSync(require.resolve('../src/main/webapp/js/demographic/cds-export.js'), 'utf8'), sandbox);
    const page = sandbox.module.exports;
    page.useMessages({heldBack: 'Dossier prêt sans {0} patient(s) retenu(s)', countProgress: '{0} sur {1}',
        stateAccepted: 'Acceptée', unknownKey: 'ignored', stateReady: 42, workerBusy: '',
        submissionUnconfirmed: 'Non confirmée ({0}); clé {1}'});
    const held = job({state: 'READY', artifactAvailable: true, heldBack: true, waiting: false,
        totalPatients: 3, processedPatients: 3, blockedPatients: 1});
    assert.equal(page.presentation(held).label, 'Dossier prêt sans 1 patient(s) retenu(s)');
    assert.equal(page.presentation(held).count, '3 sur 3');
    assert.equal(page.presentation(job()).label, 'Acceptée');
    // Non-text or empty values and unknown keys leave the English default in place.
    assert.equal(page.controlNotice(new Error('CDS_EXPORT_WORKER_BUSY')),
        'A worker is processing this job now, so it cannot be resumed or changed. Refresh to see its progress.');
    assert.equal(page.submissionNotice(new Error('CDS_EXPORT_RESPONSE_UNCONFIRMED'), 'key-1'),
        'Non confirmée (CDS_EXPORT_RESPONSE_UNCONFIRMED); clé key-1');
    // The shared module still speaks English.
    assert.equal(presentation(held).count, '3 of 3 patients processed.');
});

test('a fixed-code 4xx refusal says nothing was submitted; other failures stay unconfirmed', () => {
    const invalid = responseFailure({code: 'CDS_EXPORT_SELECTION_INVALID'}, 400);
    assert.match(submissionNotice(invalid, 'k'), /refused \(CDS_EXPORT_SELECTION_INVALID\).*Nothing was submitted/);
    assert.match(submissionNotice(responseFailure({code: 'CDS_EXPORT_RECIPIENT_UNAVAILABLE'}, 409), 'k'),
        /refused \(CDS_EXPORT_RECIPIENT_UNAVAILABLE\)\. Nothing was submitted/);
    assert.match(submissionNotice(responseFailure({code: 'CDS_EXPORT_SUBMISSION_CONFLICT'}, 409), 'key-9'),
        /key-9 was already used for a different export/);
    assert.match(submissionNotice(responseFailure({code: 'CDS_EXPORT_CLIENT_REFRESH_REQUIRED'}, 409), 'k'), /out of date/);
    // Rights of the signed-in account cannot be avoided by leaving out categories.
    assert.match(submissionNotice(responseFailure({code: 'CDS_EXPORT_ACCESS_DENIED', missingRight: '_cds_export'}, 403), 'k'),
        /your account lacks _cds_export/);
    for (const unknown of [responseFailure({code: 'CDS_EXPORT_REQUEST_UNAVAILABLE'}, 503),
        responseFailure({code: 'CDS_EXPORT_REQUEST_UNAVAILABLE'}, 429), responseFailure({}, 400), new Error('Failed to fetch')])
        assert.match(submissionNotice(unknown, 'key-1'), /could not be confirmed .*key-1/);
});

test('an unknown, foreign or outdated job lookup ends, a failure that may pass does not', () => {
    assert.match(lookupNotice(responseFailure({code: 'CDS_EXPORT_RUN_NOT_FOUND'}, 404)), /No export job/);
    assert.match(lookupNotice(responseFailure({code: 'CDS_EXPORT_ACCESS_DENIED'}, 403)), /cannot view/);
    assert.match(lookupNotice(responseFailure({code: 'CDS_EXPORT_CLIENT_REFRESH_REQUIRED'}, 409)), /out of date/);
    assert.match(lookupNotice(responseFailure({code: 'CDS_EXPORT_PARAMETERS_INVALID'}, 400)), /valid job ID/);
    assert.equal(lookupNotice(responseFailure({code: 'CDS_EXPORT_REQUEST_UNAVAILABLE'}, 503)), null);
    assert.equal(lookupNotice(new Error('CDS_EXPORT_RESPONSE_UNCONFIRMED')), null);
});

test('a submission key is a version 4 UUID even where randomUUID is withheld', () => {
    const key = randomKey({getRandomValues: bytes => require('node:crypto').getRandomValues(bytes)});
    assert.match(key, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    assert.equal(randomKey({randomUUID: () => 'from-randomUUID'}), 'from-randomUUID');
    assert.equal(randomKey(undefined), null);
    assert.equal(randomKey({}), null);
});

function pageHarness(search) {
    const vm = require('node:vm'), fs = require('node:fs');
    const nodes = new Map(), timers = new Map(), pending = [];
    let initialize, serial = 0;
    function node(nodeId) {
        if (!nodes.has(nodeId)) nodes.set(nodeId, {value: '', textContent: '', dataset: {}, events: {},
            addEventListener(event, handler) { this.events[event] = handler; },
            removeAttribute() {}, setAttribute() {}, replaceChildren() {}, querySelectorAll() { return []; }});
        return nodes.get(nodeId);
    }
    node('cdsExport').dataset = {context: '', owner: 'web-7', canResume: 'true'};
    const document = {getElementById: node, createElement: () => node('created-' + serial++),
        querySelector: () => ({value: 'synthetic-csrf'}), addEventListener(event, handler) {
            if (event === 'DOMContentLoaded') initialize = handler;
        }};
    const window = {document, location: {search}, sessionStorage: {getItem() { return ''; }, setItem() {}},
        crypto: require('node:crypto'), confirm: () => true, addEventListener() {}};
    vm.runInNewContext(fs.readFileSync(require.resolve('../src/main/webapp/js/demographic/cds-export.js'), 'utf8'), {
        window, document, URLSearchParams, AbortController,
        setTimeout(callback, delay) { const token = ++serial; timers.set(token, {callback, delay}); return token; },
        clearTimeout(token) { timers.delete(token); },
        fetch(url, options) { return new Promise(resolve => pending.push({url, options, resolve})); }
    });
    function answer(call, data, status = 200) {
        call.resolve({ok: status < 300, status, headers: {get(name) {
            return name === 'Content-Type' ? 'application/json' : '1';
        }}, async json() { return {apiVersion: 1, ok: status < 300, ...data}; }});
    }
    return {node, timers, pending, answer, initialize: () => initialize(),
        settle: () => new Promise(resolve => setImmediate(resolve))};
}

test('opening an unknown job says so at once instead of retrying for minutes', async () => {
    const page = pageHarness('?runId=' + id);
    page.initialize();
    page.answer(page.pending.shift(), {jobs: [], nextAfter: '', mayHaveMore: false});
    page.answer(page.pending.shift(), {code: 'CDS_EXPORT_RUN_NOT_FOUND'}, 404);
    await page.settle();
    assert.equal(page.timers.size, 0);
    assert.equal(page.node('exportState').textContent, 'No export job with this ID was found.');
    assert.equal(page.node('runId').value, id);
});

test('a failed submission lookup leaves the shown job refreshing', async () => {
    const page = pageHarness('?runId=' + id);
    page.initialize();
    page.answer(page.pending.shift(), {jobs: [], nextAfter: '', mayHaveMore: false});
    page.answer(page.pending.shift(), {job: job({state: 'CAPTURING', workerOwned: true, waiting: false})});
    await page.settle();
    assert.equal([...page.timers.values()].filter(value => value.delay === 5000).length, 1);
    page.node('savedSubmission').value = 'never-used-key';
    const lookup = page.node('findSubmission').events.click();
    page.answer(page.pending.shift(), {code: 'CDS_EXPORT_RUN_NOT_FOUND'}, 404);
    await lookup;
    assert.equal([...page.timers.values()].filter(value => value.delay === 5000).length, 1);
    assert.equal(page.node('reportExport').disabled, false);
    assert.match(page.node('exportNotice').textContent, /No job uses submission key never-used-key/);
});
