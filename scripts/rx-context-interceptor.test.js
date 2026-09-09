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

function ownerBrowser(loadTwice = false) {
    const replacements = [];
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
        BroadcastChannel: FakeBroadcastChannel,
        fetch() { return Promise.resolve(); },
        XMLHttpRequest: FakeXhr,
        open() { return {}; },
        setTimeout,
        setInterval,
        clearInterval,
        addEventListener() {}
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
    return replacements;
}

(async () => {
    const firstReplacements = ownerBrowser(true);
    await new Promise((resolve) => setTimeout(resolve, 250));
    const duplicateReplacements = ownerBrowser();
    await new Promise((resolve) => setTimeout(resolve, 250));

    assert.equal(firstReplacements.length, 0);
    assert.equal(duplicateReplacements.length, 1);
    const reopened = new URL(duplicateReplacements[0]);
    assert.equal(reopened.pathname, '/carlos/rx/choosePatient');
    assert.equal(reopened.searchParams.get('demographicNo'), '101');
    assert.equal(reopened.searchParams.get('appointmentNo'), '88');
    assert.equal(reopened.searchParams.get('programId'), '12');
    assert.equal(reopened.searchParams.get('rxContextId'), null);
    assert.equal(reopened.searchParams.get('rxDuplicate'), '1');
    console.log('Rx context interceptor contract: PASS');
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
