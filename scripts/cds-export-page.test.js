/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {checkedJob, newest, presentation, responseFailure, submissionNotice} = require('../src/main/webapp/js/demographic/cds-export.js');
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
                removeAttribute() {}, replaceChildren() {}, querySelectorAll() { return []; }});
            return nodes.get(id);
        }
        node('cdsExport').dataset = {context: '', owner: 'web-7', canResume: 'true'};
        const document = {getElementById: node, createElement: () => node('created-' + serial++),
            querySelector: () => ({value: 'synthetic-csrf'}), addEventListener(event, handler) {
                if (event === 'DOMContentLoaded') initialize = handler;
            }};
        const window = {document, location: {search: '?runId=' + id},
            sessionStorage: {getItem() { return ''; }, setItem() {}},
            crypto: require('node:crypto'), addEventListener() {}};
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
