/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {checkedJob, newest, presentation} = require('../src/main/webapp/js/demographic/cds-export.js');
const id = 'a8fe0033-468d-468c-ae0e-b11d372b533a';
function job(values = {}) {
    return {id, state: 'ACCEPTED', revision: 0, epoch: 0, totalPatients: null, processedPatients: 0, blockedPatients: 0,
        cancelRequested: false, workerOwned: false, artifactAvailable: false, complete: false, waiting: true, safeCode: null, ...values};
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
