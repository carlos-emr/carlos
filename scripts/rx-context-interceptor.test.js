#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const fetchCalls = [];
const openCalls = [];
const xhrCalls = [];
const listeners = {};

class FakeForm {
    querySelector() { return null; }
    appendChild() {}
    getAttribute() { return '/carlos/rx/stash'; }
    submit() {}
}

class FakeXhr {
    open(method, url) { xhrCalls.push({method, url}); }
}

const document = {
    baseURI: 'https://example.test/carlos/rx/searchDrug?rxContextId=context-1',
    documentElement: {tagName: 'HTML', getAttribute() {}, querySelectorAll() { return []; }},
    readyState: 'loading',
    querySelector(selector) {
        if (selector === 'meta[name="rx-context-id"]') return {getAttribute: () => 'context-1'};
        if (selector === 'meta[name="rx-context-path"]') return {getAttribute: () => '/carlos'};
        return null;
    },
    addEventListener(name, callback) { listeners[name] = callback; },
    createElement() { return {}; }
};

const window = {
    location: {origin: 'https://example.test'},
    addEventListener() {},
    fetch(input, init) {
        fetchCalls.push({input, init});
        return Promise.resolve();
    },
    XMLHttpRequest: FakeXhr,
    open(url) {
        openCalls.push(url);
        return {};
    }
};

const context = {
    URL,
    URLSearchParams,
    Headers,
    Request,
    FormData,
    Object,
    Array,
    MutationObserver: class { observe() {} },
    HTMLFormElement: FakeForm,
    XMLHttpRequest: FakeXhr,
    document,
    window
};

vm.runInNewContext(
        fs.readFileSync('src/main/webapp/oscarRx/js/rxSessionInterceptor.js', 'utf8'),
        context,
        {filename: 'rxSessionInterceptor.js'});

assert.equal(
        window.RxContext.addToUrl('/carlos/rx/stash'),
        'https://example.test/carlos/rx/stash?rxContextId=context-1');
assert.equal(
        window.RxContext.addToUrl('/carlos/rx/choosePatient?demographicNo=101'),
        '/carlos/rx/choosePatient?demographicNo=101');
assert.equal(window.RxContext.addToUrl('https://outside.test/rx/stash'), 'https://outside.test/rx/stash');

window.fetch('/carlos/rx/stash', {method: 'POST'});
assert.equal(fetchCalls[0].init.headers.get('X-Rx-Context'), 'context-1');
assert.equal(fetchCalls[0].init.headers.get('X-Requested-With'), 'XMLHttpRequest');
window.fetch('https://outside.test/rx/stash');
assert.equal(fetchCalls[1].init, undefined);

const xhr = new FakeXhr();
xhr.open('POST', '/carlos/rx/WriteScript');
assert.match(xhrCalls[0].url, /rxContextId=context-1/);

window.open('/carlos/rx/ViewPrintDrugProfile2');
assert.match(openCalls[0], /rxContextId=context-1/);

const ownerChannels = [];

class FakeBroadcastChannel {
    constructor(name) {
        this.name = name;
        this.onmessage = null;
        ownerChannels.push(this);
    }
    postMessage(data) {
        ownerChannels
            .filter((channel) => channel !== this && channel.name === this.name)
            .forEach((channel) => {
                if (channel.onmessage) channel.onmessage({data});
            });
    }
    close() {
        const index = ownerChannels.indexOf(this);
        if (index >= 0) ownerChannels.splice(index, 1);
    }
}

const ownerStorage = new Map();

function ownerBrowser(loadTwice = false, useStorage = false) {
    const replacements = [];
    const events = {};
    const attributes = new Map();
    const documentElement = {
        tagName: 'HTML',
        getAttribute() {},
        querySelectorAll() { return []; },
        appendChild() {},
        setAttribute(name, value) { attributes.set(name, value); },
        removeAttribute(name) { attributes.delete(name); }
    };
    const meta = {
        'meta[name="rx-context-id"]': 'shared-context',
        'meta[name="rx-context-path"]': '/carlos',
        'meta[name="rx-demographic-no"]': '101',
        'meta[name="rx-appointment-no"]': '88',
        'meta[name="rx-program-id"]': '12',
        'meta[name="rx-context-owner"]': 'true'
    };
    const ownerDocument = {
        baseURI: 'https://example.test/carlos/rx/choosePatient?rxContextId=shared-context',
        documentElement,
        querySelector(selector) {
            return Object.hasOwn(meta, selector) ? {getAttribute: () => meta[selector]} : null;
        },
        addEventListener() {},
        createElement() {
            return {setAttribute() {}, appendChild() {}, style: {}, textContent: ''};
        }
    };
    const ownerWindow = {
        location: {
            origin: 'https://example.test',
            href: ownerDocument.baseURI,
            replace(url) { replacements.push(url); }
        },
        history: {state: null, replaceState() {}},
        alert() {},
        crypto: {randomUUID: () => `id-${Math.random()}`},
        BroadcastChannel: useStorage ? undefined : FakeBroadcastChannel,
        localStorage: {
            getItem(key) { return ownerStorage.get(key) || null; },
            setItem(key, value) { ownerStorage.set(key, value); },
            removeItem(key) { ownerStorage.delete(key); }
        },
        fetch() { return Promise.resolve(); },
        XMLHttpRequest: FakeXhr,
        open() { return {}; },
        setTimeout,
        setInterval() { return 1; },
        clearInterval() {},
        addEventListener(name, callback, options) {
            (events[name] ||= []).push({callback, once: options?.once});
        }
    };
    ownerWindow.top = ownerWindow;
    const ownerContext = {
        URL,
        URLSearchParams,
        Headers,
        Request,
        FormData,
        Object,
        Array,
        Date,
        Math,
        MutationObserver: class { observe() {} },
        HTMLFormElement: FakeForm,
        XMLHttpRequest: FakeXhr,
        document: ownerDocument,
        window: ownerWindow
    };
    const interceptor = fs.readFileSync('src/main/webapp/oscarRx/js/rxSessionInterceptor.js', 'utf8');
    vm.runInNewContext(interceptor, ownerContext, {filename: 'rxSessionInterceptor.js'});
    if (loadTwice) {
        vm.runInNewContext(interceptor, ownerContext, {filename: 'rxSessionInterceptor.js'});
    }
    return {
        replacements,
        dispatch(name, event = {}) {
            const callbacks = events[name] || [];
            events[name] = callbacks.filter((entry) => !entry.once);
            callbacks.forEach((entry) => entry.callback(event));
        }
    };
}

function leaseBrowser() {
    const fetches = [];
    const beacons = [];
    const intervals = [];
    const windowListeners = {};
    const documentElement = {
        tagName: 'HTML',
        getAttribute() {},
        querySelectorAll() { return []; }
    };
    const meta = {
        'meta[name="rx-context-id"]': 'lease-context',
        'meta[name="rx-context-path"]': '/carlos',
        'meta[name="rx-demographic-no"]': '101',
        'meta[name="rx-csrf-token"]': 'csrf-token'
    };
    const leaseDocument = {
        baseURI: 'https://example.test/carlos/rx/searchDrug?rxContextId=lease-context',
        documentElement,
        querySelector(selector) {
            return Object.hasOwn(meta, selector) ? {getAttribute: () => meta[selector]} : null;
        },
        addEventListener() {},
        createElement() { return {}; }
    };
    const leaseWindow = {
        location: {origin: 'https://example.test', href: leaseDocument.baseURI},
        fetch(input, init) {
            fetches.push({input: String(input), init});
            return Promise.resolve();
        },
        XMLHttpRequest: FakeXhr,
        open() { return {}; },
        navigator: {
            sendBeacon(url, body) {
                beacons.push({url, body});
                return true;
            }
        },
        setInterval(callback, delay) {
            intervals.push({callback, delay});
            return intervals.length;
        },
        clearInterval() {},
        addEventListener(name, callback) {
            windowListeners[name] = windowListeners[name] || [];
            windowListeners[name].push(callback);
        }
    };
    leaseWindow.top = leaseWindow;
    const leaseContext = {
        URL,
        URLSearchParams,
        Headers,
        Request,
        FormData,
        Blob,
        Object,
        Array,
        MutationObserver: class { observe() {} },
        HTMLFormElement: FakeForm,
        XMLHttpRequest: FakeXhr,
        document: leaseDocument,
        window: leaseWindow
    };
    vm.runInNewContext(
            fs.readFileSync('src/main/webapp/oscarRx/js/rxSessionInterceptor.js', 'utf8'),
            leaseContext,
            {filename: 'rxSessionInterceptor.js'});
    return {fetches, beacons, intervals, windowListeners};
}

async function verifyPreviewClose(inModal) {
    const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp', 'utf8');
    const start = jsp.indexOf('function resetStashAndClose()');
    const end = jsp.indexOf('var pendingNotesSave', start);
    assert.ok(start >= 0 && end > start, 'Preview close handler must be present');
    const events = [];
    let workspaceActive = true;
    const browser = {
        resetStash() { events.push('reset-stash'); return Promise.resolve(); },
        resetReRxDrugList() { events.push('reset-rerx'); return Promise.resolve(); },
        clearPending(action) {
            events.push('clear-pending-' + action);
            if (action === 'close') workspaceActive = false;
        },
        parent: {
            document: {getElementById() { return inModal ? {} : null; }},
            bootstrap: {Modal: {getInstance() { return {hide() { events.push('hide-modal'); }}; }}},
            window: {close() { events.push('close-window'); }}
        },
        console
    };
    vm.runInNewContext(jsp.slice(start, end) + '\nresetStashAndClose();', browser);
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(events, inModal
            ? ['reset-stash', 'reset-rerx', 'hide-modal']
            : ['reset-stash', 'reset-rerx', 'clear-pending-close', 'close-window']);
    assert.equal(workspaceActive, inModal, 'A visible parent prescription page must retain its workspace');
}

(async () => {
    await verifyPreviewClose(true);
    await verifyPreviewClose(false);
    const firstOwner = ownerBrowser(true);
    await new Promise((resolve) => setTimeout(resolve, 250));
    const duplicateOwner = ownerBrowser();
    await new Promise((resolve) => setTimeout(resolve, 250));

    assert.equal(firstOwner.replacements.length, 0);
    assert.equal(duplicateOwner.replacements.length, 1);
    const reopened = new URL(duplicateOwner.replacements[0]);
    assert.equal(reopened.pathname, '/carlos/rx/choosePatient');
    assert.equal(reopened.searchParams.get('demographicNo'), '101');
    assert.equal(reopened.searchParams.get('appointmentNo'), '88');
    assert.equal(reopened.searchParams.get('programId'), '12');
    assert.equal(reopened.searchParams.get('rxContextId'), null);
    assert.equal(reopened.searchParams.get('rxDuplicate'), '1');

    firstOwner.dispatch('pagehide');
    assert.equal(ownerChannels.length, 0);
    firstOwner.dispatch('pageshow', {persisted: true});
    await new Promise((resolve) => setTimeout(resolve, 250));
    const restoredDuplicate = ownerBrowser();
    await new Promise((resolve) => setTimeout(resolve, 250));
    assert.equal(firstOwner.replacements.length, 0);
    assert.equal(restoredDuplicate.replacements.length, 1,
            'A restored owner must still isolate a subsequently duplicated tab');

    const storageOwner = ownerBrowser(false, true);
    const storageDuplicate = ownerBrowser(false, true);
    assert.equal(storageOwner.replacements.length, 0);
    assert.equal(storageDuplicate.replacements.length, 1);
    storageOwner.dispatch('pagehide');
    assert.equal(ownerStorage.size, 0);
    storageOwner.dispatch('pageshow', {persisted: true});
    assert.equal(ownerBrowser(false, true).replacements.length, 1,
            'The storage fallback must also reacquire ownership after a cache restore');

    const lease = leaseBrowser();
    assert.equal(lease.fetches.length, 1);
    assert.match(lease.fetches[0].input, /\/rx\/workspaceHeartbeat\?rxContextId=lease-context/);
    assert.equal(lease.fetches[0].init.method, 'GET');
    assert.equal(lease.intervals.length, 1);
    assert.equal(lease.intervals[0].delay, 60000);

    lease.windowListeners.pagehide.forEach((listener) => listener());
    assert.equal(lease.beacons.length, 1);
    assert.match(lease.beacons[0].url, /\/rx\/workspaceClose\?rxContextId=lease-context/);
    assert.equal(await lease.beacons[0].body.text(), 'CSRF-TOKEN=csrf-token');
    lease.windowListeners.pageshow.forEach((listener) => listener({persisted: false}));
    assert.equal(lease.fetches.length, 1, 'An ordinary load must not start a second heartbeat');
    lease.windowListeners.pageshow.forEach((listener) => listener({persisted: true}));
    assert.equal(lease.fetches.length, 2, 'Restoring a cached page must renew its workspace');
    assert.equal(lease.intervals.length, 2, 'Restoring a cached page must restart the heartbeat');
    console.log('Rx context interceptor contract: PASS');
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
