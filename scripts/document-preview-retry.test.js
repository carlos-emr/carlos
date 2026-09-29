'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { create, replaceApproval, clearDisplay } = require('../src/main/webapp/js/document-preview-retry');

function fixture() {
    const requests = [], timers = [], events = [];
    let attached = true;
    const controller = create({
        send(parameters, callbacks) {
            const request = { parameters, callbacks, aborted: false, abort() {
                this.aborted = true;
                callbacks.error({ status: 0 }, 'abort', 'abort');
            } };
            requests.push(request);
            return request;
        },
        schedule(callback, delay) { const timer = { callback, delay }; timers.push(timer); return timer; },
        unschedule(timer) { timer.cancelled = true; },
        random: () => 0.5,
        isCurrent: () => attached
    });
    const handlers = {
        success: (data, parameters) => events.push(['success', data, parameters]),
        error: (xhr, status) => events.push(['error', xhr.status, status]),
        waiting: () => events.push(['waiting'])
    };
    function busy(index, token = null, overrides = {}) {
        requests[index].callbacks.error({ status: 503, responseJSON: {
            errorCode: 'eform_render_busy', retryable: true, retryAfterSeconds: 2,
            renderApproval: token, ...overrides
        } }, 'error', 'Service Unavailable');
    }
    return { controller, requests, timers, events, handlers, busy,
        detach() { attached = false; },
        start(parameters = 'method=renderEFormPDF&eFormId=42&demographicNo=123') {
            controller.start(parameters, handlers);
        }
    };
}

test('busy waits with jitter, then retries one request at a time until a real PDF succeeds', () => {
    const f = fixture(); f.start();
    f.busy(0); assert.equal(f.requests.length, 1);
    assert.equal(f.timers[0].delay, 2250);
    f.timers[0].callback(); f.busy(1); f.timers[1].callback();
    f.requests[2].callbacks.success({ base64Data: 'JVBERi0=', advisoryIssues: 1 });
    assert.deepEqual(f.events.map(event => event[0]), ['waiting', 'waiting', 'success']);
    assert.equal(f.events[2][1].advisoryIssues, 1);
});

test('rotates approval without duplicate parameters and preserves request scope', () => {
    const f = fixture(); f.start('method=renderEFormPDF&eFormId=42&demographicNo=123&renderApproval=old&renderApproval=older');
    f.busy(0, 'replacement'); f.timers[0].callback();
    const values = new URLSearchParams(f.requests[1].parameters);
    assert.deepEqual(values.getAll('renderApproval'), ['replacement']);
    assert.equal(values.get('eFormId'), '42'); assert.equal(values.get('demographicNo'), '123');
});

test('expired approval is removed and fresh omissions reach the consent callback', () => {
    const f = fixture(); f.start('method=renderEFormPDF&renderApproval=old');
    f.busy(0, null); f.timers[0].callback();
    assert.equal(new URLSearchParams(f.requests[1].parameters).has('renderApproval'), false);
    f.requests[1].callbacks.success({ missingContent: true, renderApproval: 'fresh-consent' });
    assert.equal(f.events.at(-1)[1].missingContent, true);
    assert.equal(f.events.at(-1)[1].renderApproval, 'fresh-consent');
});

for (const [name, xhr, status] of [
    ['generic 503', { status: 503, responseJSON: { retryable: true } }, 'error'],
    ['permanent render failure', { status: 500, responseJSON: { errorCode: 'eform_render_failed' } }, 'error'],
    ['invalid approval', { status: 403, responseJSON: { errorCode: 'eform_approval_invalid' } }, 'error'],
    ['expired login', { status: 401 }, 'error'],
    ['network uncertainty', { status: 0 }, 'error'],
    ['login HTML/parser error', { status: 200 }, 'parsererror']
]) {
    test(`${name} does not retry`, () => {
        const f = fixture(); f.start(); f.requests[0].callbacks.error(xhr, status, 'failure');
        assert.equal(f.timers.length, 0); assert.deepEqual(f.events, [['error', xhr.status, status]]);
    });
}

test('only exact boolean retryability and a bounded server delay are accepted', () => {
    for (const overrides of [{ retryable: 'true' }, { retryAfterSeconds: 0 },
        { retryAfterSeconds: 31 }, { retryAfterSeconds: '2' }, { retryAfterSeconds: 1.5 }]) {
        const f = fixture(); f.start(); f.busy(0, null, overrides);
        assert.equal(f.timers.length, 0); assert.equal(f.events[0][0], 'error');
    }
});

test('other preview methods cannot enter the eForm retry flow', () => {
    const f = fixture(); f.start('method=renderLabPDF&segmentId=42'); f.busy(0);
    assert.equal(f.timers.length, 0); assert.equal(f.events[0][0], 'error');
});

test('cancel aborts the active request and suppresses its abort callback and late PDF', () => {
    const f = fixture(); f.start(); f.controller.cancel();
    assert.equal(f.requests[0].aborted, true);
    f.requests[0].callbacks.success({ base64Data: 'old-patient-data' });
    assert.deepEqual(f.events, []);
});

test('cancel during waiting clears the timer and even a queued callback cannot retry', () => {
    const f = fixture(); f.start(); f.busy(0); f.controller.cancel();
    assert.equal(f.timers[0].cancelled, true); f.timers[0].callback();
    assert.equal(f.requests.length, 1);
});

test('switching attachments suppresses old results and old retry timers', () => {
    const f = fixture(); f.start(); f.busy(0);
    f.start('method=renderEFormPDF&eFormId=99');
    f.requests[0].callbacks.success({ base64Data: 'old' }); f.timers[0].callback();
    f.requests[1].callbacks.success({ base64Data: 'new' });
    assert.equal(f.requests.length, 2);
    assert.deepEqual(f.events.filter(event => event[0] === 'success').map(event => event[1].base64Data), ['new']);
});

test('removing or hiding the attachment view prevents a delayed retry and stale success', () => {
    const f = fixture(); f.start(); f.busy(0); f.detach(); f.timers[0].callback();
    f.requests[0].callbacks.success({ base64Data: 'stale' });
    assert.equal(f.requests.length, 1); assert.deepEqual(f.events, [['waiting']]);
});

test('approval replacement handles changed omissions and reserved token characters', () => {
    const parameters = replaceApproval('method=renderEFormPDF&renderApproval=first&renderApproval=second', 'third+&=');
    assert.deepEqual(new URLSearchParams(parameters).getAll('renderApproval'), ['third+&=']);
    assert.equal(new URLSearchParams(replaceApproval(parameters, null)).has('renderApproval'), false);
});

test('a synchronous transport response leaves no stale request to abort', () => {
    let aborted = false, calls = 0;
    const controller = create({ send(parameters, callbacks) {
        callbacks.success({ base64Data: 'pdf' });
        return { abort() { aborted = true; } };
    } });
    controller.start('method=renderEFormPDF', { success() { calls++; } });
    controller.cancel(); assert.equal(calls, 1); assert.equal(aborted, false);
});

test('an uncached selection clears the previous PDF and advisory before transport, throughout busy waiting and after cancellation', () => {
    function node(classes, attributes = {}) {
        const values = new Set(classes);
        return { textContent: 'previous document warning', attributes,
            classList: { add: value => values.add(value), remove: value => values.delete(value), contains: value => values.has(value) },
            removeAttribute: name => { delete attributes[name]; } };
    }
    const nodes = {
        pdfObject: node([], { src: 'blob:previous-document' }),
        'preview-advisory': node([]),
        'preview-filler': node(['d-none'])
    };
    const revoked = [];
    let url = 'blob:previous-document', callbacks, timer, requests = 0;
    function assertCleared() {
        assert(nodes.pdfObject.classList.contains('d-none'));
        assert.equal(nodes.pdfObject.attributes.src, undefined);
        assert(nodes['preview-advisory'].classList.contains('d-none'));
        assert.equal(nodes['preview-advisory'].textContent, '');
        assert(!nodes['preview-filler'].classList.contains('d-none'));
        assert.equal(url, null);
    }
    const controller = create({
        beforeStart() { url = clearDisplay({ getElementById: id => nodes[id] }, value => revoked.push(value), url); },
        send(parameters, handlers) { assertCleared(); requests++; callbacks = handlers; return { abort() {} }; },
        schedule(callback) { timer = callback; return 1; }, unschedule() {}
    });
    controller.start('method=renderEFormPDF&eFormId=99', {
        waiting: assertCleared, success() { assert.fail('Cancelled request cannot display a PDF'); }, error() { assert.fail('No error expected'); }
    });
    callbacks.error({ status: 503, responseJSON: { errorCode: 'eform_render_busy', retryable: true, retryAfterSeconds: 2 } }, 'error');
    assertCleared();
    controller.cancel(); timer();
    assertCleared(); assert.equal(requests, 1);
    assert.deepEqual(revoked, ['blob:previous-document']);
});
