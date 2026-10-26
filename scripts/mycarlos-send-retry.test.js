/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Exercise the actual shipped script with deterministic DOM and fetch boundaries.
class Element {
    constructor(tag = 'div') { this.tag = tag; this.dataset = {}; this.children = []; this.listeners = {}; }
    append(...children) { this.children.push(...children.filter(child => typeof child !== 'string')); }
    replaceChildren(...children) { this.children = []; this.append(...children); }
    get rows() { return this.children.filter(child => child.tag === 'tr'); }
    get options() { return this.children; }
    get lastElementChild() { return this.children.at(-1); }
    get value() { return this._value || ''; }
    set value(value) { this._value = this.tag === 'select' && !this.options.some(option => option.value === value) ? '' : value; }
    addEventListener(type, handler) { this.listeners[type] = handler; }
    click() { if (!this.disabled && this.listeners.click) this.listeners.click({preventDefault() {}}); }
    querySelectorAll() {
        return this.children.flatMap(child => [
            ...('retry' in child.dataset ? [child] : []), ...child.querySelectorAll()
        ]);
    }
    querySelector() { return this.querySelectorAll()[0] || null; }
}
const rowData = (index, status = 'picked_up') => ({
    deliveryId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
    deviceKeyId: `FAKE-historical-device-${index}`, queuedAt: '2026-10-26T00:00:00Z', status
});
const tick = () => new Promise(resolve => setImmediate(resolve));
function mount(rows) {
    const ids = Object.fromEntries(['mycarlos-send', 'delivery-form', 'delivery-message', 'delivery-history',
        'delivery-more', 'delivery-send', 'delivery-check', 'delivery-empty', 'delivery-no-device'].map(id => [id, new Element()]));
    ids['delivery-device'] = new Element('select');
    const option = new Element('option'); option.value = 'FAKE-current-device';
    ids['delivery-device'].append(option); ids['delivery-device'].value = option.value;
    ids['mycarlos-send'].dataset = {context: '/FAKE', patient: '6001', document: '44',
        delivery: rowData(999).deliveryId, next: '20'};
    for (const item of rows) {
        const row = new Element('tr');
        row.dataset = {delivery: item.deliveryId, device: item.deviceKeyId, status: item.status};
        row.append(new Element('td'), new Element('td')); ids['delivery-history'].append(row);
    }
    const copies = ['retry', 'retried', 'error', 'busy', 'sync'].map(key => {
        const element = new Element(); element.dataset.copy = key; element.textContent = key; return element;
    });
    const statuses = ['staged', 'waiting', 'picked_up', 'error'].map(key => {
        const element = new Element(); element.dataset.status = key; element.textContent = key; return element;
    });
    const calls = [];
    const context = {URL, URLSearchParams, Date, console,
        document: {getElementById: id => ids[id], createElement: tag => new Element(tag),
            querySelectorAll: selector => selector.includes('data-copy') ? copies : statuses,
            querySelector: () => ({value: 'FAKE-CSRF'})},
        window: {location: {href: 'https://localhost/FAKE/demographic/PortalDelivery?demographicNo=6001&documentId=44'},
            history: {replaceState() {}}, csrfTokenReady: Promise.resolve()},
        fetch: async (url, options) => {
            const call = {url, options}; calls.push(call);
            return new Promise((resolve, reject) => { call.resolve = payload => resolve({ok: true, json: async () => payload}); call.reject = reject; });
        }};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,
        '../src/main/webapp/share/javascript/demographic/mycarlos-send.js'), 'utf8'), context);
    return {ids, calls, payload: deliveries => ({ok: true, devices: [{deviceKeyId: option.value,
        acceptedAt: '2026-10-26T00:00:00Z', fingerprint: '0'.repeat(64)}], deliveries, nextCursor: null})};
}

test('21st pending copy reached through Older deliveries retries its exact UUID/device with CSRF', async () => {
    const recent = Array.from({length: 20}, (_, index) => rowData(index + 1));
    const {ids, calls, payload} = mount(recent);
    assert.equal(ids['delivery-history'].querySelectorAll().length, 0, 'terminal copies have no retry');
    ids['delivery-more'].click(); await tick();
    assert.equal(new URL(calls[0].url, 'https://localhost').searchParams.get('before'), '20');
    const older = rowData(21, 'staged'); calls[0].resolve(payload([older])); await tick();
    assert.equal(ids['delivery-history'].rows.length, 21);
    const retry = ids['delivery-history'].rows[20].querySelector('[data-retry]');
    assert.ok(retry, 'older pending row has a reachable retry');
    retry.click(); retry.click(); await tick();
    assert.equal(calls.length, 2, 'busy protection prevents a duplicate click upload');
    const sent = new URLSearchParams(calls[1].options.body);
    assert.equal(calls[1].options.method, 'POST');
    assert.equal(sent.get('deliveryId'), older.deliveryId);
    assert.equal(sent.get('deviceKeyId'), older.deviceKeyId, 'use stored row device, not current chooser');
    assert.equal(sent.get('demographicNo'), '6001'); assert.equal(sent.get('documentId'), '44');
    assert.equal(sent.get('CSRF-TOKEN'), 'FAKE-CSRF');
    assert.equal(ids['mycarlos-send'].dataset.delivery, rowData(999).deliveryId, 'new-copy operation remains separate');
    calls[1].resolve(payload(recent)); await tick();
    assert.equal(ids['delivery-message'].textContent, 'retried');
});

test('lost reply leaves the pending row retryable with exactly the same identity and body', async () => {
    const pending = rowData(21, 'waiting'); const {ids, calls, payload} = mount([pending]);
    const retry = ids['delivery-history'].rows[0].querySelector('[data-retry]');
    retry.click(); await tick(); calls[0].reject(new Error('FAKE lost reply')); await tick();
    assert.equal(retry.disabled, false); assert.equal(ids['delivery-message'].textContent, 'error');
    retry.click(); await tick();
    assert.equal(calls[1].options.body, calls[0].options.body);
    calls[1].resolve(payload([pending])); await tick();
});
