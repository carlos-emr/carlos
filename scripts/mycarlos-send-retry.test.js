/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {FakeDocument, sendRoot, load, tick, delivery, state} = require('./lib/mycarlos-send-fake-dom');

// Exercise the actual shipped script on the standalone page, with deterministic DOM and fetch boundaries.
function mount(rows) {
    const document = new FakeDocument();
    const {root, parts} = sendRoot(document, {data: {delivery: delivery(999).deliveryId, next: '20'}, rows,
        devices: ['FAKE-current-device']});
    document.ids['mycarlos-send'] = root;
    const {window, calls} = load(document);
    return {root, parts, calls, window};
}

test('the standalone page keeps its operation id in its URL', () => {
    const {window} = mount([]);
    assert.equal(new URL(window.history.replaced[0]).searchParams.get('deliveryId'), delivery(999).deliveryId);
});

test('21st pending copy reached through Older deliveries retries its exact UUID/device with CSRF', async () => {
    const recent = Array.from({length: 20}, (_, index) => delivery(index + 1));
    const {root, parts, calls} = mount(recent);
    assert.equal(parts.history.querySelectorAll('[data-retry]').length, 0, 'terminal copies have no retry');
    parts.more.click(); await tick();
    assert.equal(new URL(calls[0].url).searchParams.get('before'), '20');
    const older = delivery(21, 'staged'); calls[0].resolve(state([older])); await tick();
    assert.equal(parts.history.rows.length, 21);
    const retry = parts.history.rows[20].querySelector('[data-retry]');
    assert.ok(retry, 'older pending row has a reachable retry');
    retry.click(); retry.click(); await tick();
    assert.equal(calls.length, 2, 'busy protection prevents a duplicate click upload');
    const sent = new URLSearchParams(calls[1].options.body);
    assert.equal(calls[1].options.method, 'POST');
    assert.equal(sent.get('deliveryId'), older.deliveryId);
    assert.equal(sent.get('deviceKeyId'), older.deviceKeyId, 'use stored row device, not current chooser');
    assert.equal(sent.get('demographicNo'), '6001'); assert.equal(sent.get('documentId'), '44');
    assert.equal(sent.get('CSRF-TOKEN'), 'FAKE-CSRF');
    assert.equal(root.dataset.delivery, delivery(999).deliveryId, 'new-copy operation remains separate');
    calls[1].resolve(state(recent)); await tick();
    assert.equal(parts.message.textContent, 'retried');
});

test('lost reply leaves the pending row retryable with exactly the same identity and body', async () => {
    const pending = delivery(21, 'staged'); const {parts, calls} = mount([pending]);
    const retry = parts.history.rows[0].querySelector('[data-retry]');
    retry.click(); await tick(); calls[0].reject(new Error('FAKE lost reply')); await tick();
    assert.equal(retry.disabled, false); assert.equal(parts.message.textContent, 'error');
    retry.click(); await tick();
    assert.equal(calls[1].options.body, calls[0].options.body);
    calls[1].resolve(state([pending])); await tick();
});

test('waiting-for-pickup rows offer status checking rather than an upload retry', () => {
    const {parts} = mount([delivery(21, 'waiting')]);
    assert.equal(parts.history.querySelectorAll('[data-retry]').length, 0);
    assert.equal(parts.check.disabled, false);
});

test('service date display distinguishes explicit unknown from a known saved date', async () => {
    const {parts, calls} = mount([]);
    parts.check.click(); await tick(); calls[0].resolve(state([])); await tick();
    assert.equal(parts['service-date'].textContent, 'dateNotRecorded');
    parts.check.click(); await tick();
    calls[1].resolve(state([], {serviceDate: '2026-10-17'})); await tick();
    assert.equal(parts['service-date'].textContent, '2026-10-17');
});

test('a send says it is sending, then that it was sent', async () => {
    const {parts, calls} = mount([]);
    parts.form.dispatch('submit'); await tick();
    assert.equal(parts.message.textContent, 'sending');
    calls[0].resolve(state([{...delivery(999, 'staged'), deviceKeyId: 'FAKE-current-device'}])); await tick();
    assert.equal(parts.message.textContent, 'sent');
    assert.equal(parts.send.disabled, true, 'this operation id is queued: the same copy is not sent twice');
});
