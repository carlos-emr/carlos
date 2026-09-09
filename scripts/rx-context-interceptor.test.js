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

console.log('Rx context interceptor contract: PASS');
