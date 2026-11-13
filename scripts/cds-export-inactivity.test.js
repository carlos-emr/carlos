/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/demographic/cds-export.js'), 'utf8');
const runId = '11111111-1111-4111-8111-111111111111';
const settle = () => new Promise(resolve => setImmediate(resolve));

// Exercise the actual page controller and fetch URLs, including automatic retries.
function browser() {
    const nodes = new Map(), requests = [], timers = new Map();
    let nextTimer = 0, failNext = false;
    const node = id => {
        if (!nodes.has(id)) nodes.set(id, {value: '', dataset: {}, listeners: new Map(),
            addEventListener(type, handler) { this.listeners.set(type, handler); },
            removeAttribute(name) { delete this[name]; }, replaceChildren() {}});
        return nodes.get(id);
    };
    node('cdsExport').dataset = {context: '/carlos', owner: 'web-fixture'};
    node('runId').value = runId;
    const document = {getElementById: node, createElement: () => node('history-button'),
        addEventListener(type, handler) { if (type === 'DOMContentLoaded') this.ready = handler; }};
    const sandbox = vm.createContext({document, URLSearchParams, AbortController,
        crypto: {randomUUID: () => runId}, location: {search: ''}, addEventListener() {},
        sessionStorage: {getItem: () => null, setItem() {}},
        setTimeout(callback, delay) { const id = ++nextTimer; timers.set(id, {callback, delay}); return id; },
        clearTimeout(id) { timers.delete(id); },
        async fetch(url) {
            const request = new URL(url, 'https://localhost'); requests.push(request);
            if (request.searchParams.has('runId') && failNext) { failNext = false; throw new Error('synthetic disconnect'); }
            const result = request.searchParams.has('runId')
                ? {job: {id: runId, state: 'ACCEPTED', revision: 0, epoch: 0, totalPatients: null,
                    processedPatients: 0, blockedPatients: 0, cancelRequested: false, workerOwned: false,
                    artifactAvailable: false, complete: false, heldBack: false, waiting: true, safeCode: null}}
                : {jobs: [], nextAfter: '', mayHaveMore: false};
            return new Response(JSON.stringify({ok: true, apiVersion: 1, ...result}), {
                headers: {'Content-Type': 'application/json', 'X-CARLOS-CDS-Export-API-Version': '1'}});
        }
    });
    vm.runInContext(source, sandbox); document.ready();
    return {requests, jobs: () => requests.filter(url => url.searchParams.has('runId')),
        open: () => node('openExport').listeners.get('click')(),
        disconnectNext() { failNext = true; },
        async tick() {
            // 5 s while healthy; a retry after a failure backs off (review W7).
            const polls = [...timers].filter(([, timer]) => timer.delay >= 5000 && timer.delay <= 60000);
            assert.equal(polls.length, 1, 'exactly one status poll must be scheduled');
            timers.delete(polls[0][0]); await polls[0][1].callback();
        }};
}

test('automatic export polling preserves inactivity and explicit job opens remain user activity', async () => {
    const page = browser(); await settle(); await page.open();
    assert.equal(page.requests[0].searchParams.has('autoRefresh'), false, 'initial history is a page action');
    assert.equal(page.jobs()[0].searchParams.has('autoRefresh'), false);
    await page.tick();
    assert.equal(page.jobs()[1].searchParams.get('autoRefresh'), 'true');
    await page.open();
    assert.equal(page.jobs()[2].searchParams.has('autoRefresh'), false);
    await page.tick();
    assert.equal(page.jobs()[3].searchParams.get('autoRefresh'), 'true');
});

test('automatic export retries after a connection failure remain background activity', async () => {
    const page = browser(); await settle(); await page.open(); page.disconnectNext();
    await page.tick(); await page.tick();
    assert.deepEqual(page.jobs().map(url => url.searchParams.get('autoRefresh')), [null, 'true', 'true']);
});
