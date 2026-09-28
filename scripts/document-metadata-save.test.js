/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../src/main/webapp');
const helper = fs.readFileSync(path.join(web, 'js/documentMetadataSave.js'), 'utf8');
const mds = fs.readFileSync(path.join(web, 'share/javascript/oscarMDSIndex.js'), 'utf8');
const read = name => fs.readFileSync(path.join(web, 'WEB-INF/jsp', name), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const committed = {success: true, accepted: true, document: 42, patientId: '77'};
const refused = {success: false, accepted: false, document: 42, retryable: false};

function fixture(host = 'inbox') {
    const nodes = new Map(), requests = [], events = new Map(), calls = [];
    function node(tag, id) {
        const item = {tagName: tag, id, style: {}, children: [], value: '', name: '', type: '', disabled: false,
            isConnected: true, parentNode: null, checked: false,
            setAttribute() {}, focus() {},
            appendChild(child) { child.parentNode = this; this.children.push(child); if (child.id) nodes.set(child.id, child); return child; },
            removeChild(child) { child.remove(); },
            remove() { this.isConnected = false; if (this.parentNode) this.parentNode.children = this.parentNode.children.filter(child => child !== this); this.parentNode = null; },
        };
        if (id) nodes.set(id, item);
        return item;
    }
    const csrf = {value: 'original-session-token'};
    function createForm(id = '42') {
        const form = node('FORM', 'forms_' + id); form.elements = [];
        const add = (name, value, type = 'hidden', elementId) => {
            const input = node('INPUT', elementId); Object.assign(input, {name, value, type});
            form.elements.push(input); form.elements[name] = input; form.appendChild(input); return input;
        };
        add('documentId', id); add('method', host === 'multipage' ? 'documentUpdate' : 'documentUpdateAjax');
        add('demog', '77', 'hidden', 'demofind' + id);
        add('observationDate', '2026-01-20', 'text'); add('documentDescription', 'Original draft', 'text');
        add('saved', 'true', 'hidden', 'saved' + id);
        add('save', 'Save', 'submit', 'save' + id);
        add('readonly', 'Unavailable', 'button', 'readonly' + id).disabled = true;
        form.querySelector = () => null;
        form.querySelectorAll = () => form.elements.filter(el => ['button', 'submit', 'reset'].includes(el.type));
        node('SPAN', 'saveSucessMsg_' + id).style.display = '';
        node('INPUT', 'autocompletedemo' + id).value = 'Selected patient';
        node('BUTTON', 'msgBtn_' + id);
        node('DIV', 'labdoc_' + id);
        const provider = node('LI', 'provider-' + id), anchor = node('A', 'unlink-' + id);
        form.appendChild(provider); provider.appendChild(anchor);
        const removeProvider = node('A', 'removeProv' + id); form.appendChild(removeProvider);
        return form;
    }
    const form = createForm();
    const ackForm = node('FORM', 'acknowledgeForm_42'); ackForm.elements = [];
    for (const [name, value] of [['segmentID', '42'], ['labType', 'DOC'], ['comment', 'Reviewed original'], ['ack', 'Acknowledge']]) {
        const input = node('INPUT', name === 'ack' ? 'ack42' : undefined);
        Object.assign(input, {name, value, type: name === 'ack' ? 'submit' : 'hidden'});
        ackForm.elements.push(input); ackForm.elements[name] = input; ackForm.appendChild(input);
    }
    ackForm.querySelector = () => null;
    ackForm.querySelectorAll = () => ackForm.elements.filter(el => el.type === 'submit');
    class FormDataStub {
        constructor(selectedForm) {
            this.entries = selectedForm.elements.filter(el => el.name && !el.disabled && !['submit', 'button', 'reset'].includes(el.type)).map(el => [el.name, el.value]);
        }
        [Symbol.iterator]() { return this.entries[Symbol.iterator](); }
        forEach(callback) { this.entries.forEach(([key, value]) => callback(value, key)); }
    }
    const messages = Object.fromEntries(['pending', 'rejected', 'uncertain', 'saved', 'queueRejected', 'queueUncertain'].map(key => [key, 'Localized ' + key]));
    const context = {URLSearchParams, FormData: FormDataStub, console, contextpath: '/carlos', _in_window: host === 'popup',
        alert(message) { calls.push(['alert', message]); }, confirm() { return true; },
        jQuery(selector) { const item = nodes.get(selector.slice(1)); return {length: item ? 1 : 0, val: () => item && item.value}; },
        $(id) { return nodes.get(id); },
        document: {getElementById: id => nodes.get(id) || null, createElement: tag => node(tag), querySelector: () => csrf},
        fetch(url, options) { return new Promise((resolve, reject) => requests.push({url, options, resolve, reject})); },
        self: {opener: {removeReport(...args) { calls.push(['removeReport', ...args]); }}},
        window: {CarlosDocumentMetadataContext: '/carlos', CarlosDocumentMutationMessages: messages,
            addEventListener(name, callback) { events.set(name, callback); }, close() { calls.push(['close']); }},
    };
    vm.createContext(context); vm.runInContext(helper, context, {filename: 'documentMetadataSave.js'});
    vm.runInContext(mds, context, {filename: 'oscarMDSIndex.js'});
    // Navigation models are outside this test's scope; the actual save wrappers and
    // transport/acceptance helper execute together, including date validation.
    for (const name of ['updateGlobalDataAndSideNav', 'updatePatientDocLabNav', 'addDocToPatient', 'updateSideNav', 'removeDocFromQueue', 'refreshView', 'refreshParent', 'updateDocLabData']) {
        context[name] = (...args) => { calls.push([name, ...args]); return true; };
    }
    context.labDocumentRows = (...args) => ({slideUp() { calls.push(['slideUp', ...args]); }});
    function fragment(file, from, to) {
        const source = read(file), start = source.indexOf(from), end = source.indexOf(to, start);
        assert(start >= 0 && end > start, 'Actual JSP handler boundaries exist');
        vm.runInContext(source.slice(start, end), context, {filename: file});
    }
    if (host === 'queue') {
        fragment('oscarMDS/documentsInQueues.jsp', '        function updateDocument(eleId, isNext)', '        function updateStatus(');
        fragment('oscarMDS/documentsInQueues.jsp', '        function removeLink(', '        function handleDocSave(');
        fragment('oscarMDS/documentsInQueues.jsp', '        function updateStatus(formid', '        function showPatientPreview');
    } else if (host === 'multipage') {
        fragment('documentManager/MultiPageDocDisplay.jsp', '                                        updateDocument = function', '                                        function checkObservationDate');
        fragment('documentManager/MultiPageDocDisplay.jsp', '                                        updateStatus = function', '                                        function sendMRP');
    } else if (host === 'popup') {
        fragment('documentManager/showDocument.jsp', '        window.removeLink = function', '\n    }\n');
        context.removeLink = context.window.removeLink;
    }
    return {context, form, ackForm, nodes, requests, calls, csrf, messages, createForm,
        save(next = false) { return host === 'queue' ? context.updateDocument('forms_42', next) : next ? context.updateDocumentAndNext('forms_42') : context.updateDocument('forms_42'); },
        acknowledge() { return context.updateStatus('acknowledgeForm_42', true); },
        file() { return context.fileDoc('42'); },
        unlink() { return context.removeLink('DOC', '42', 'p7', nodes.get('unlink-42')); },
        status() { return nodes.get('document-metadata-status-42')?.textContent; },
        event(name) { events.get(name)?.({persisted: true}); },
        async answer(index, {status = 200, body = committed, contentType = 'application/json;charset=UTF-8', malformed = false, redirected = false} = {}) {
            requests[index].resolve({status, ok: status >= 200 && status < 300, redirected,
                headers: {get: () => contentType}, async json() { if (malformed) throw new SyntaxError('Malformed'); return body; }});
            await flush();
        },
    };
}
function unchanged(f) {
    assert.equal(f.nodes.get('saved42').value, 'false');
    assert.equal(f.nodes.get('saveSucessMsg_42').style.display, 'none');
    assert.equal(f.form.elements.documentDescription.value, 'Original draft');
    assert.equal(f.form.elements.demog.value, '77');
    assert.equal(f.calls.length, 0);
}

for (const host of ['inbox', 'queue', 'multipage', 'popup']) {
    test(host + ': actual save handler waits for confirmed JSON, preserves payload and initial disabled controls', async () => {
        const f = fixture(host); assert.equal(f.save(), false); assert.equal(f.requests.length, 1); unchanged(f);
        const data = new URLSearchParams(f.requests[0].options.body);
        assert.equal(data.get('documentId'), '42'); assert.equal(data.get('method'), 'documentUpdateAjax');
        assert.equal(data.get('demog'), '77'); assert.equal(data.get('documentDescription'), 'Original draft');
        assert.equal(f.requests[0].options.headers['CSRF-TOKEN'], f.csrf.value);
        assert.equal(f.requests[0].url, '/carlos/documentManager/ManageDocument');
        assert(f.nodes.get('save42').disabled); assert(f.nodes.get('readonly42').disabled);
        await f.answer(0); assert.equal(f.nodes.get('saved42').value, 'true');
        assert.equal(f.nodes.get('saveSucessMsg_42').style.display, ''); assert.equal(f.nodes.get('save42').disabled, false);
        assert(f.nodes.get('readonly42').disabled); assert(f.calls.length > 0);
        if (host === 'multipage') assert(f.calls.some(call => call[0] === 'refreshParent'));
        if (host === 'popup') assert.deepEqual(f.calls[0], ['removeReport', '42', 'DOC']);
    });
    test(host + ': typed refusal preserves form and allows only a new manual attempt', async () => {
        const f = fixture(host); f.save(); await f.answer(0, {status: 403, body: refused}); unchanged(f);
        assert.equal(f.status(), f.messages.rejected); assert.equal(f.nodes.get('save42').disabled, false);
        await flush(); assert.equal(f.requests.length, 1); f.save(); assert.equal(f.requests.length, 2);
    });
}
for (const [name, response] of [
    ['HTML', {contentType: 'text/html'}], ['malformed JSON', {malformed: true}],
    ['untyped 403', {status: 403, body: {error: 'Denied'}}], ['500', {status: 500}],
    ['wrong document', {body: {...committed, document: 43}}], ['wrong patient', {body: {...committed, patientId: '78'}}],
    ['missing acceptance', {body: {patientId: '77'}}], ['unknown committed failure', {status: 500, body: {success: false, accepted: true, document: 42}}],
    ['redirected login', {redirected: true}], ['missing patient field', {body: {success: true, accepted: true, document: 42}}],
]) {
    test('unconfirmed ' + name + ' leaves inputs and navigation unchanged with no replay', async () => {
        const f = fixture(); f.save(); await f.answer(0, response); unchanged(f);
        assert.equal(f.status(), f.messages.uncertain); assert(f.nodes.get('save42').disabled);
        f.save(); f.unlink(); await flush(); assert.equal(f.requests.length, 1);
    });
}
test('network loss and double clicks never dispatch a second mutation', async () => {
    const f = fixture(); f.save(); f.save(true); f.unlink(); assert.equal(f.requests.length, 1);
    f.requests[0].reject(new Error('Connection lost')); await flush(); unchanged(f);
    assert.equal(f.status(), f.messages.uncertain); f.save(); assert.equal(f.requests.length, 1);
});
test('confirmed metadata-only provider response has no invented patient navigation', async () => {
    const f = fixture(); f.save(); await f.answer(0, {body: {...committed, patientId: null}});
    assert.equal(f.nodes.get('saved42').value, 'true'); assert.equal(f.calls.length, 0);
});
for (const change of ['patient', 'description', 'token', 'replaced form', 'pagehide']) {
    test('late acceptance cannot update a changed ' + change, async () => {
        const f = fixture(); f.save();
        if (change === 'patient') f.form.elements.demog.value = '88';
        if (change === 'description') f.form.elements.documentDescription.value = 'Later unsaved draft';
        if (change === 'token') f.csrf.value = 'another-session';
        if (change === 'replaced form') { f.form.isConnected = false; f.createForm(); }
        if (change === 'pagehide') f.event('pagehide');
        await f.answer(0); assert.equal(f.calls.length, 0); assert.equal(f.status(), f.messages.uncertain);
        f.save(); assert.equal(f.requests.length, 1);
        if (change === 'patient') assert.equal(f.form.elements.demog.value, '88');
        if (change === 'description') assert.equal(f.form.elements.documentDescription.value, 'Later unsaved draft');
        if (change === 'replaced form') assert.equal(f.nodes.get('saved42').value, 'true', 'Do not rewrite replacement form state');
    });
}
test('missing CSRF and inconsistent document aliases refuse before transport', () => {
    const f = fixture(); f.csrf.value = ''; f.save(); assert.equal(f.requests.length, 0);
    assert.equal(f.status(), f.messages.rejected); assert.equal(f.nodes.get('save42').disabled, false);
});
for (const host of ['inbox', 'queue', 'popup']) {
    test(host + ': actual unlink retains the row until matching accepted JSON confirms absent provider', async () => {
        const f = fixture(host), parent = f.nodes.get('provider-42'); f.unlink(); f.unlink(); f.save();
        assert.equal(f.requests.length, 1); assert(parent.isConnected); assert.equal(f.calls.length, 0);
        await f.answer(0, {body: {success: true, accepted: true, document: 42, linkedProviders: [{providerNo: 'p8'}]}});
        assert.equal(parent.isConnected, false); assert.equal(f.nodes.get('saved42').value, 'true');
        if (host === 'popup') assert.equal(f.calls.length, 0);
    });
}
for (const body of [{success: true, accepted: true, document: 43, linkedProviders: []},
    {success: true, accepted: true, document: 42, linkedProviders: [{providerNo: 'p7'}]},
    {success: true, accepted: true, document: 42}, {linkedProviders: []}]) {
    test('unlink refuses unconfirmed provider list ' + JSON.stringify(body), async () => {
        const f = fixture(); f.unlink(); await f.answer(0, {body}); assert(f.nodes.get('provider-42').isConnected);
        assert.equal(f.calls.length, 0); assert.equal(f.status(), f.messages.uncertain); f.unlink(); assert.equal(f.requests.length, 1);
    });
}
test('unlink typed denial allows manual retry but transport loss does not', async () => {
    const f = fixture('popup'); f.unlink(); await f.answer(0, {status: 403, body: refused});
    assert(f.nodes.get('provider-42').isConnected); f.unlink(); assert.equal(f.requests.length, 2);
    f.requests[1].reject(new Error('Lost response')); await flush(); f.unlink(); assert.equal(f.requests.length, 2);
    assert(f.nodes.get('provider-42').isConnected); assert.equal(f.calls.length, 0);
});
for (const host of ['inbox', 'queue', 'popup']) {
    test(host + ': Save & Next confirms queue disposition before removing or closing', async () => {
        const f = fixture(host); f.save(true); await f.answer(0);
        assert.equal(f.nodes.get('saved42').value, 'true'); assert.equal(f.calls.length, 0);
        assert.equal(f.requests.length, 2); assert.equal(f.requests[1].url, '/carlos/documentManager/inboxManage');
        assert.equal(new URLSearchParams(f.requests[1].options.body).get('docid'), '42');
        f.save(true); assert.equal(f.requests.length, 2);
        await f.answer(1, {body: {success: true, accepted: true, document: 42}});
        assert(f.calls.length > 0); if (host === 'popup') assert(f.calls.some(call => call[0] === 'close'));
    });
}
test('explicit queue rejection offers queue-only retry with the exact original body and token', async () => {
    const f = fixture(); f.save(true); await f.answer(0); await f.answer(1, {status: 403, body: refused});
    assert.equal(f.nodes.get('saved42').value, 'true'); assert.equal(f.calls.length, 0); assert.equal(f.status(), f.messages.queueRejected);
    f.save(true); assert.equal(f.requests.length, 3);
    assert.equal(f.requests[2].options.body, f.requests[1].options.body);
    assert.equal(f.requests[2].options.headers['CSRF-TOKEN'], f.csrf.value);
    assert.equal(f.requests.filter(request => request.url.endsWith('/ManageDocument')).length, 1);
    await f.answer(2, {body: {success: true, accepted: true, document: 42}}); assert(f.calls.length > 0);
});
for (const change of ['network', 'HTML', 'patient', 'token', 'pagehide']) {
    test('uncertain queue outcome ' + change + ' retains confirmed metadata without replay/navigation', async () => {
        const f = fixture(); f.save(true); await f.answer(0);
        if (change === 'network') { f.requests[1].reject(new Error('Lost queue response')); await flush(); }
        else {
            if (change === 'patient') f.form.elements.demog.value = '88';
            if (change === 'token') f.csrf.value = 'new-session';
            if (change === 'pagehide') f.event('pagehide');
            await f.answer(1, {contentType: change === 'HTML' ? 'text/html' : 'application/json', body: {success: true, accepted: true, document: 42}});
        }
        assert.equal(f.nodes.get('saved42').value, 'true'); assert.equal(f.calls.length, 0);
        assert.equal(f.status(), f.messages.queueUncertain); f.save(true); f.unlink(); assert.equal(f.requests.length, 2);
    });
}
test('all actual hosts load the shared helper once through the guarded include', () => {
    assert(read('documentManager/documentMutationScripts.jspf').includes('/js/documentMetadataSave.js'));
    for (const file of ['documentManager/MultiPageDocDisplay.jsp', 'documentManager/showDocument.jsp', 'oscarMDS/Index.jsp', 'oscarMDS/documentsInQueues.jsp']) {
        assert(read(file).includes('/WEB-INF/jsp/documentManager/documentMutationScripts.jspf'), file);
    }
    const f = fixture(); f.save(); vm.runInContext(helper, f.context); f.save(); assert.equal(f.requests.length, 1);
});

for (const host of ['inbox', 'queue', 'multipage', 'popup']) {
    test(host + ': actual DOC acknowledgement waits for typed count and confirmed queue completion', async () => {
        const f = fixture(host); f.acknowledge(); f.acknowledge(); f.file(); f.unlink();
        assert.equal(f.requests.length, 1); assert.equal(f.requests[0].url, '/carlos/oscarMDS/UpdateStatus');
        const body = new URLSearchParams(f.requests[0].options.body);
        assert.equal(body.get('ajaxcall'), 'yes'); assert.equal(body.get('segmentID'), '42'); assert.equal(body.get('labType'), 'DOC');
        assert(f.nodes.get('ack42').disabled); assert.equal(f.calls.length, 0);
        await f.answer(0, {body: {clearedCount: 1}});
        assert.equal(f.requests.length, 2); assert.equal(f.calls.length, 0);
        await f.answer(1, {body: {success: true, accepted: true, document: 42}});
        assert(f.calls.length > 0); assert.equal(f.nodes.get('ack42').disabled, false);
    });
    test(host + ': DOC filing requires exact accepted JSON and never sends a redundant queue mutation', async () => {
        const f = fixture(host); f.file(); f.file(); f.unlink(); assert.equal(f.requests.length, 1);
        assert.equal(f.requests[0].url, '/carlos/oscarMDS/FileLabs'); assert.equal(f.calls.length, 0);
        await f.answer(0, {body: {success: true, accepted: true, document: 42, retryable: false}});
        assert.equal(f.requests.length, 1); assert(f.calls.length > 0);
        if (host === 'queue') assert(f.calls.some(call => call[0] === 'removeDocFromQueue' && call[1] === '42'));
    });
}
for (const count of [-1, 1.5, '1', null, undefined]) {
    test('DOC acknowledgement rejects invalid clearedCount ' + String(count), async () => {
        const f = fixture(); f.acknowledge(); await f.answer(0, {body: {clearedCount: count}});
        assert.equal(f.requests.length, 1); assert.equal(f.calls.length, 0); assert.equal(f.status(), f.messages.uncertain);
        f.acknowledge(); assert.equal(f.requests.length, 1);
    });
}
test('acknowledgement HTML failure cannot close popup, file queues or refresh its parent', async () => {
    const f = fixture('multipage'); f.acknowledge(); await f.answer(0, {contentType: 'text/html', body: {clearedCount: 1}});
    assert.equal(f.calls.length, 0); assert.equal(f.requests.length, 1); assert.equal(f.status(), f.messages.uncertain);
});
test('acknowledgement comment change invalidates late acceptance without clearing newer input', async () => {
    const f = fixture(); f.acknowledge(); f.ackForm.elements.comment.value = 'Another unsaved comment';
    await f.answer(0, {body: {clearedCount: 1}}); assert.equal(f.requests.length, 1); assert.equal(f.calls.length, 0);
    assert.equal(f.ackForm.elements.comment.value, 'Another unsaved comment'); assert.equal(f.status(), f.messages.uncertain);
});
test('acknowledgement queue retry does not repeat the acknowledged clinical action', async () => {
    const f = fixture('queue'); f.acknowledge(); await f.answer(0, {body: {clearedCount: 0}});
    await f.answer(1, {status: 403, body: refused}); f.acknowledge();
    assert.equal(f.requests.length, 3); assert.equal(f.requests.filter(request => request.url.endsWith('/UpdateStatus')).length, 1);
    assert.equal(f.requests[2].options.body, f.requests[1].options.body);
    await f.answer(2, {body: {success: true, accepted: true, document: 42}}); assert(f.calls.length > 0);
});
for (const [name, response] of [['403', {status: 403, body: refused}], ['500', {status: 500, body: {...refused, accepted: true}}],
    ['HTML', {contentType: 'text/html'}], ['wrong source', {body: {...committed, document: 43}}]]) {
    test('filing ' + name + ' preserves document and forbids automatic mutation replay', async () => {
        const f = fixture('queue'); f.file(); await f.answer(0, response);
        assert.equal(f.calls.length, 0); assert.equal(f.requests.length, 1); assert.notEqual(f.nodes.get('labdoc_42').style.display, 'none');
        if (name !== '403') { f.file(); assert.equal(f.requests.length, 1); }
    });
}
test('filing cancellation dispatches no clinical write', () => {
    const f = fixture('multipage'); f.form.elements.demog.value = '-1'; f.context.confirm = () => false;
    assert.equal(f.file(), false); assert.equal(f.requests.length, 0); assert.equal(f.calls.length, 0);
});
test('legacy doc_no is copied exactly and conflicting aliases never dispatch', async () => {
    const f = fixture('multipage'); f.form.elements.documentId.name = 'doc_no'; f.save();
    assert.equal(new URLSearchParams(f.requests[0].options.body).get('doc_no'), '42');
    assert.equal(new URLSearchParams(f.requests[0].options.body).get('documentId'), '42');
    await f.answer(0); assert.equal(f.nodes.get('saved42').value, 'true');
    const g = fixture(); g.form.elements.push({name: 'doc_no', value: '43', disabled: false}); g.save(); assert.equal(g.requests.length, 0);
});
