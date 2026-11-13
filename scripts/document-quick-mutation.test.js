/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Run BOTH complete production scripts. In particular, do not stub postForm,
// quickDocumentMutation, the shared retry controller or the success UI callbacks.
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/oscarMDSIndex.js'), 'utf8');
const controller = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/documentMutation.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const originalRevision = 'a'.repeat(64), committedRevision = 'b'.repeat(64);
const mutationBody = (method, id = '42', revision = originalRevision) => `method=${method}&document=${id}&sourceRevision=${revision}`;

function fixture({confirmDelete = true, disabled = [], display = 'IMAGE'} = {}) {
  const nodes = new Map(), requests = [], timers = [], listeners = new Map(), images = [], confirmations = [];
  const csrf = {value: 'test-csrf'};
  let nextTimer = 0;
  function connect(node, connected) {
    node.isConnected = connected;
    if (node.id) {
      if (connected) nodes.set(node.id, node);
      else if (nodes.get(node.id) === node) nodes.delete(node.id);
    }
    node.children.forEach(child => connect(child, connected));
  }
  function element(tagName = 'DIV', id) {
    const attributes = new Map(), classes = new Set(), events = new Map();
    let identifier = '', text = '';
    const node = {tagName, children: [], style: {}, value: '', disabled: false, hidden: false,
      parentNode: null, isConnected: true,
      classList: {add: name => classes.add(name), remove: name => classes.delete(name), contains: name => classes.has(name)},
      setAttribute(name, value) { attributes.set(name, String(value)); },
      getAttribute(name) { return attributes.get(name) ?? null; },
      appendChild(child) {
        if (child.parentNode) child.parentNode.children = child.parentNode.children.filter(value => value !== child);
        child.parentNode = this; this.children.push(child); connect(child, this.isConnected); return child;
      },
      addEventListener(name, callback) { events.set(name, callback); },
      click() { if (!this.disabled) events.get('click')?.({target: this}); },
      remove() {
        connect(this, false);
        if (this.parentNode) this.parentNode.children = this.parentNode.children.filter(child => child !== this);
        this.parentNode = null;
      },
    };
    Object.defineProperty(node, 'id', {get: () => identifier, set: value => {identifier = value; nodes.set(value, node);}});
    Object.defineProperty(node, 'textContent', {get: () => text, set: value => {text = String(value); node.children = [];}});
    if (id) node.id = id;
    return node;
  }
  function addDocument(id, initiallyDisabled = disabled) {
    const buttons = element('DIV', 'buttons_' + id);
    for (const name of ['rotate90', 'rotate180', 'removeFirstPage']) {
      const button = element('BUTTON', name + 'btn_' + id);
      button.disabled = initiallyDisabled.includes(name);
      buttons.appendChild(button);
    }
    element('SPAN', 'numPages_' + id).textContent = '9';
    nodes.get('numPages_' + id).classList.add('multiPage');
    element('INPUT', 'totalPage_' + id).value = '9';
    element('INPUT', 'curPage_' + id).value = '4';
    element('SPAN', 'viewedPage_' + id).textContent = '4';
    element('INPUT', 'displayDocumentAs_' + id).value = display;
    element('INPUT', 'sourceRevision_' + id).value = originalRevision;
    element('IMG', 'docImg_' + id);
    element('DIV', 'docDispPDF_' + id);
    for (const prefix of ['prevP', 'firstP', 'prevP2', 'firstP2', 'nextP', 'lastP', 'nextP2', 'lastP2']) {
      element('A', prefix + '_' + id).style.display = 'inline';
    }
  }
  addDocument('42'); addDocument('43');
  const messages = Object.fromEntries(['cancel', 'pending', 'waiting', 'cancelled', 'rejected', 'uncertain', 'saved', 'sourceChanged', 'sourceUnavailable']
    .map(name => [name, 'Localized ' + name]));
  const context = {
    contextpath: '/carlos', URLSearchParams, console,
    confirm(message) { confirmations.push(message); return confirmDelete; },
    document: {getElementById: id => nodes.get(id) || null, createElement: tag => element(tag.toUpperCase()),
      querySelector: selector => selector === 'input[name="CSRF-TOKEN"]' ? csrf : null},
    CarlosDocumentImages: {load(image, url) { images.push({image, url}); }},
    window: {
      CarlosDocumentMutationMessages: messages,
      crypto: {getRandomValues(bytes) { bytes[0] = 1073741824; return bytes; }},
      setTimeout(callback, delay) { const timer = {id: ++nextTimer, callback, delay, cancelled: false}; timers.push(timer); return timer.id; },
      clearTimeout(id) { const timer = timers.find(value => value.id === id); if (timer) timer.cancelled = true; },
      addEventListener(name, callback) { if (!listeners.has(name)) listeners.set(name, []); listeners.get(name).push(callback); },
    },
    fetch(url, options) { return new Promise((resolve, reject) => requests.push({url, options, resolve, reject})); },
  };
  vm.createContext(context);
  vm.runInContext(controller, context, {filename: 'documentMutation.js'});
  vm.runInContext(source, context, {filename: 'oscarMDSIndex.js'});
  function buttons(id = '42') { return ['rotate90', 'rotate180', 'removeFirstPage'].map(name => nodes.get(name + 'btn_' + id)).filter(Boolean); }
  return {context, nodes, requests, timers, images, confirmations, buttons, messages, csrf,
    replaceDocument(initiallyDisabled = [], id = '42') {
      // AJAX replaces actual DOM nodes, including the operation status container.
      // Captured old controls remain objects but getElementById sees only replacements.
      for (const node of [...nodes.values()]) {
        if (node.id.endsWith('_' + id) && !node.parentNode) node.remove();
      }
      addDocument(id, initiallyDisabled);
    },
    call(method, id = '42') { return context[method](id); },
    status(id = '42') { return nodes.get('document-mutation-status-' + id)?.children[0]; },
    cancel(id = '42') { return nodes.get('document-mutation-status-' + id)?.children[1]; },
    event(name) { for (const callback of listeners.get(name) || []) callback({persisted: true}); },
    async answer(index, {status = 200, data = {success: true, accepted: true, document: 42, pageCount: 7, sourceRevision: committedRevision}, retryAfter = '2', html = false} = {}) {
      requests[index].resolve({status, ok: status >= 200 && status < 300,
        headers: {get: name => name.toLowerCase() === 'retry-after' ? retryAfter : html ? 'text/html' : 'application/json'},
        async json() { if (html) throw new SyntaxError('HTML login response'); return data; }});
      await flush();
    },
    async busy(index) { await this.answer(index, {status: 503, data: {success: false, accepted: false, retryable: true}}); },
    async advance() { const timer = timers.find(value => !value.cancelled && !value.fired); assert(timer, 'Expected scheduled capacity retry'); timer.fired = true; timer.callback(); await flush(); },
  };
}

function unchanged(f) {
  assert.equal(f.nodes.get('numPages_42').textContent, '9');
  assert.equal(f.nodes.get('totalPage_42').value, '9');
  assert.equal(f.nodes.get('curPage_42').value, '4');
  assert.equal(f.images.length, 0);
}

test('missing initial CSRF token is refused locally without a transport or uncertain outcome', async () => {
  const f = fixture(); f.csrf.value = '';
  assert.equal(f.call('rotate90'), true); await flush();
  assert.equal(f.requests.length, 0);
  assert.equal(f.status().textContent, f.messages.rejected);
  assert(f.buttons().every(button => !button.disabled)); unchanged(f);
});

test('token change before initial dispatch cannot move the intent into another session', async () => {
  const f = fixture(); f.call('rotate90'); f.csrf.value = 'other-session'; await flush();
  assert.equal(f.requests.length, 0);
  assert.equal(f.status().textContent, f.messages.rejected);
  assert(f.buttons().every(button => !button.disabled)); unchanged(f);
});

test('AJAX token replacement cancels a queued intent; only a new manual action can capture the new token', async () => {
  const f = fixture(); f.call('rotate90'); await flush(); await f.busy(0);
  f.replaceDocument(); f.csrf.value = 'other-session';
  await f.advance();
  assert.equal(f.requests.length, 1);
  assert.equal(f.requests[0].options.headers['CSRF-TOKEN'], 'test-csrf');
  assert.equal(f.status().textContent, f.messages.rejected);
  assert(f.buttons().every(button => !button.disabled)); unchanged(f);
  assert.equal(f.call('rotate180'), true); await flush();
  assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].options.headers['CSRF-TOKEN'], 'other-session');
  assert.equal(f.requests[1].options.body, mutationBody('rotate180'));
});

test('pagehide before quick edit transport cancels it permanently without marking an unknown outcome', async () => {
  const f = fixture(); f.call('rotate90'); f.event('pagehide'); f.event('pageshow'); await flush();
  assert.equal(f.requests.length, 0);
  assert.equal(f.status().textContent, f.messages.cancelled);
  assert(f.buttons().every(button => !button.disabled)); unchanged(f);
});

test('missing observed source revision prevents any page mutation transport', async () => {
  const f = fixture(); f.nodes.get('sourceRevision_42').value = '';
  f.call('rotate90'); await flush();
  assert.equal(f.requests.length, 0);
  assert.equal(f.status().textContent, f.messages.sourceUnavailable);
  unchanged(f);
});

test('changed AJAX revision refuses queued intent without rebasing its selected pages', async () => {
  const f = fixture(); f.call('removeFirstPage'); await flush(); await f.busy(0);
  f.replaceDocument(); f.nodes.get('sourceRevision_42').value = 'c'.repeat(64);
  await f.advance();
  assert.equal(f.requests.length, 1);
  assert.equal(new URLSearchParams(f.requests[0].options.body).get('sourceRevision'), originalRevision);
  assert.equal(f.status().textContent, f.messages.sourceChanged);
  assert.equal(f.nodes.get('sourceRevision_42').value, 'c'.repeat(64));
  unchanged(f);
});

test('server source conflict preserves observed revision and warns to refresh without automatic retry', async () => {
  const f = fixture(); f.call('removeFirstPage'); await flush();
  await f.answer(0, {status: 409, data: {success: false, accepted: false, retryable: false, sourceChanged: true}});
  assert.equal(f.status().textContent, f.messages.sourceChanged);
  assert.equal(f.nodes.get('sourceRevision_42').value, originalRevision);
  assert.equal(f.timers.length, 0); assert.equal(f.requests.length, 1);
  unchanged(f);
});

test('only confirmed success advances revision for the next explicit action', async () => {
  const f = fixture(); f.call('rotate90'); await flush(); await f.answer(0);
  assert.equal(f.nodes.get('sourceRevision_42').value, committedRevision);
  f.call('rotate180'); await flush();
  assert.equal(f.requests[0].options.body, mutationBody('rotate90'));
  assert.equal(f.requests[1].options.body, mutationBody('rotate180', '42', committedRevision));
});

test('late success cannot overwrite a replacement row that already observes a newer source', async () => {
  const f = fixture(); f.call('rotate90'); await flush();
  f.replaceDocument(); f.nodes.get('sourceRevision_42').value = 'c'.repeat(64);
  await f.answer(0);
  assert.equal(f.nodes.get('sourceRevision_42').value, 'c'.repeat(64));
  assert.equal(f.status().textContent, f.messages.sourceChanged);
  unchanged(f);
});

test('documents-in-queues inline functions delegate to the shared guarded mutation controller', async () => {
  const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/oscarMDS/documentsInQueues.jsp'), 'utf8');
  for (const method of ['rotate90', 'rotate180', 'removeFirstPage']) {
    const f = fixture();
    const definition = jsp.match(new RegExp('function ' + method + '\\(id\\) \\{[\\s\\S]*?\\n        \\}'));
    assert(definition, 'Expected queue host function ' + method);
    vm.runInContext(definition[0], f.context);
    f.call(method); await flush(); await f.busy(0);
    assert.equal(f.status().textContent, f.messages.waiting);
    assert.equal(f.requests[0].options.body, mutationBody(method));
    f.cancel().click(); await flush();
    assert.equal(f.requests.length, 1); unchanged(f);
  }
});

test('cancelled destructive first-page deletion issues no POST and changes no controls or count', async () => {
  const f = fixture({confirmDelete: false});
  assert.equal(f.call('removeFirstPage'), false);
  await flush();
  assert.equal(f.confirmations.length, 1);
  assert.equal(f.requests.length, 0);
  assert.equal(f.status(), undefined);
  assert(f.buttons().every(button => !button.disabled));
  unchanged(f);
});

test('invalid document identifiers and mutation methods issue no requests', async () => {
  const f = fixture();
  for (const id of ['0', '-1', '42&document=43', '2147483648', '42.5', '', ' 42']) {
    assert.equal(f.call('rotate90', id), false);
  }
  assert.equal(f.context.quickDocumentMutation('42', 'deleteDocument'), false);
  await flush();
  assert.equal(f.requests.length, 0);
});

test('one accepted mutation disables every mutator for that document while another document can progress', async () => {
  const f = fixture();
  assert.equal(f.call('rotate90'), true);
  assert(f.buttons().every(button => button.disabled));
  assert.equal(f.call('rotate180'), false);
  assert.equal(f.call('removeFirstPage'), false);
  assert.equal(f.confirmations.length, 0, 'A locked document must not prompt for a second destructive operation');
  assert.equal(f.call('rotate180', '43'), true);
  await flush();
  assert.deepEqual(f.requests.map(request => request.options.body), [mutationBody('rotate90'), mutationBody('rotate180', '43')]);
  const request = f.requests[0];
  assert.equal(request.url, '/carlos/documentManager/SplitDocument');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.credentials, 'same-origin');
  assert.equal(request.options.headers['CSRF-TOKEN'], 'test-csrf');
  assert.equal(request.options.headers['X-Requested-With'], 'XMLHttpRequest');
  unchanged(f);
  await f.answer(0);
  assert(f.buttons().every(button => !button.disabled));
  assert(f.buttons('43').every(button => button.disabled));
  await f.answer(1, {data: {success: true, accepted: true, document: 43, pageCount: 4, sourceRevision: committedRevision}});
});

test('five real contract busy results preserve the immutable deletion payload and server page count controls refresh', async () => {
  const f = fixture();
  assert.equal(f.call('removeFirstPage'), true);
  await flush();
  for (let i = 0; i < 5; i++) {
    await f.busy(i);
    assert.equal(f.status().textContent, f.messages.waiting);
    assert.equal(f.cancel().hidden, false);
    assert(f.buttons().every(button => button.disabled));
    assert.equal(f.call('rotate90'), false);
    unchanged(f);
    const timer = f.timers.at(-1);
    assert(timer.delay >= 2000 && timer.delay <= 10000, 'Backoff obeys Retry-After and bounded jitter');
    await f.advance();
  }
  assert.deepEqual(f.requests.map(request => request.options.body), Array(6).fill(mutationBody('removeFirstPage')));
  assert.equal(f.confirmations.length, 1, 'Retries must not repeat the destructive confirmation');
  await f.answer(5, {data: {success: true, accepted: true, document: 42, pageCount: 3, sourceRevision: committedRevision}});
  assert.equal(f.nodes.get('numPages_42').textContent, '3', 'Use committed count, not old9 minus1');
  assert.equal(f.nodes.get('totalPage_42').value, '3');
  assert.equal(f.nodes.get('curPage_42').value, '1');
  assert.equal(f.nodes.get('viewedPage_42').textContent, '1');
  assert.equal(f.nodes.get('prevP_42').style.display, 'none');
  assert.equal(f.nodes.get('nextP_42').style.display, 'inline');
  assert.equal(f.status().textContent, f.messages.saved);
  assert.equal(f.cancel().hidden, true);
  assert.equal(f.images.length, 1);
  assert.equal(f.images[0].image, f.nodes.get('docImg_42'));
  const imageUrl = new URL(f.images[0].url, 'https://example.test');
  assert.equal(imageUrl.searchParams.get('doc_no'), '42');
  assert.equal(imageUrl.searchParams.get('curPage'), '1');
  assert(imageUrl.searchParams.has('rand'));
});

test('single-page committed outcome removes destructive button and refreshes actual PDF object', async () => {
  const f = fixture({display: 'PDF'});
  f.call('removeFirstPage'); await flush();
  await f.answer(0, {data: {success: true, accepted: true, document: 42, pageCount: 1, sourceRevision: committedRevision}});
  assert.equal(f.nodes.get('numPages_42').textContent, '1');
  assert.equal(f.nodes.get('numPages_42').classList.contains('multiPage'), false);
  assert.equal(f.nodes.has('removeFirstPagebtn_42'), false);
  assert.equal(f.nodes.get('nextP_42').style.display, 'none');
  assert.equal(f.images.length, 0);
  const pdf = f.nodes.get('docDispPDF_42').children[0];
  assert.equal(pdf.type, 'application/pdf');
  assert.match(pdf.data, /method=display&doc_no=42&rand=.*#view=fitV&page=1$/);
});

const uncertainCases = [
  ['network failure', null],
  ['HTML login response', {html: true}],
  ['different document', {data: {success: true, accepted: true, document: 43, pageCount: 2, sourceRevision: committedRevision}}],
  ['string document identity', {data: {success: true, accepted: true, document: '42', pageCount: 2, sourceRevision: committedRevision}}],
  ...[0, -1, 1.5, '2', Number.MAX_SAFE_INTEGER + 1, null].map(pageCount =>
    ['invalid count ' + JSON.stringify(pageCount), {data: {success: true, accepted: true, document: 42, pageCount, sourceRevision: committedRevision}}]),
  ...[undefined, '', 'not-a-revision'].map(sourceRevision =>
    ['invalid committed revision ' + JSON.stringify(sourceRevision), {data: {success: true, accepted: true, document: 42, pageCount: 2, sourceRevision}}]),
  ['missing acceptance receipt', {data: {success: true, document: 42, pageCount: 2}}],
  ['ambiguous server failure', {status: 500, data: {success: false}}],
  ['accepted but unconfirmed result', {status: 500, data: {success: false, accepted: true, retryable: false}}],
  ['untyped503', {status: 503, data: {error: 'busy'}}],
];
for (const [label, response] of uncertainCases) test(`${label} remains locked without replay, count change or misleading success`, async () => {
  const f = fixture();
  f.call('rotate180'); await flush();
  if (response) await f.answer(0, response);
  else { f.requests[0].reject(new TypeError('Network failed')); await flush(); }
  assert.equal(f.status().textContent, f.messages.uncertain);
  assert.equal(f.status().getAttribute('role'), 'alert');
  assert(f.buttons().every(button => button.disabled));
  assert.equal(f.cancel().hidden, true);
  assert.equal(f.call('rotate90'), false);
  assert.equal(f.call('removeFirstPage'), false);
  f.event('pagehide'); f.event('pageshow'); await flush();
  assert.equal(f.requests.length, 1);
  assert.equal(f.timers.length, 0);
  unchanged(f);
});

test('explicit unaccepted rejection permits a manual retry and preserves initially disabled controls', async () => {
  const f = fixture({disabled: ['rotate90']});
  assert.equal(f.call('rotate180'), true); await flush();
  await f.answer(0, {status: 403, data: {success: false, accepted: false, retryable: false}});
  assert.equal(f.status().textContent, f.messages.rejected);
  assert.deepEqual(f.buttons().map(button => button.disabled), [true, false, false]);
  assert.equal(f.requests.length, 1); assert.equal(f.timers.length, 0); unchanged(f);
  assert.equal(f.call('rotate180'), true); await flush();
  assert.equal(f.requests[1].options.body, f.requests[0].options.body);
  await f.answer(1);
  assert.deepEqual(f.buttons().map(button => button.disabled), [true, false, false]);
});

test('cancelling a typed capacity wait invalidates queued timer and allows only a new manual action', async () => {
  const f = fixture({disabled: ['rotate90']});
  f.call('rotate180'); await flush(); await f.busy(0);
  const timer = f.timers[0];
  f.cancel().click();
  assert.equal(timer.cancelled, true);
  assert.equal(f.status().textContent, f.messages.cancelled);
  assert.deepEqual(f.buttons().map(button => button.disabled), [true, false, false]);
  timer.callback(); await flush();
  assert.equal(f.requests.length, 1); unchanged(f);
  f.call('removeFirstPage'); await flush();
  assert.equal(f.requests[1].options.body, mutationBody('removeFirstPage'));
});

test('pagehide cancels a queued retry; history return never silently resubmits it', async () => {
  const f = fixture();
  f.call('rotate90'); await flush(); await f.busy(0);
  const timer = f.timers[0];
  f.event('pagehide');
  assert.equal(f.call('rotate90'), false);
  f.event('pageshow'); timer.callback(); await flush();
  assert.equal(f.requests.length, 1); unchanged(f);
  assert.equal(f.status().textContent, f.messages.cancelled);
  assert.equal(f.call('rotate90'), true); await flush();
  assert.equal(f.requests.length, 2);
});

test('accepted result arriving after pagehide stays locked until history return applies it exactly once', async () => {
  const f = fixture();
  f.call('removeFirstPage'); await flush(); f.event('pagehide');
  await f.answer(0, {data: {success: true, accepted: true, document: 42, pageCount: 4, sourceRevision: committedRevision}});
  unchanged(f);
  assert(f.buttons().every(button => button.disabled));
  assert.equal(f.call('rotate180'), false);
  f.event('pageshow'); await flush();
  assert.equal(f.nodes.get('numPages_42').textContent, '4');
  assert.equal(f.images.length, 1);
  f.event('pageshow'); await flush();
  assert.equal(f.images.length, 1);
  assert.equal(f.requests.length, 1);
  assert.equal(f.confirmations.length, 1);
});

test('typed busy arriving after pagehide proves no acceptance and does not schedule a hidden retry', async () => {
  const f = fixture();
  f.call('rotate180'); await flush(); f.event('pagehide'); await f.busy(0);
  assert.equal(f.timers.length, 0);
  assert.equal(f.status().textContent, f.messages.cancelled);
  f.event('pageshow'); await flush();
  assert.equal(f.requests.length, 1); unchanged(f);
  assert.equal(f.call('rotate180'), true);
});

test('AJAX replacement during pending mutation reattaches status and locks new controls before rejecting a duplicate click', async () => {
  const f = fixture();
  f.call('rotate90'); await flush();
  const oldButtons = f.buttons(), originalStatus = f.status();
  f.replaceDocument(['rotate180']);
  assert(oldButtons.every(button => !button.isConnected));
  assert.equal(originalStatus.isConnected, false);
  assert.equal(f.status(), undefined);
  assert.equal(f.call('removeFirstPage'), false);
  assert.equal(f.confirmations.length, 0);
  assert.equal(f.requests.length, 1);
  assert(f.buttons().every(button => button.disabled));
  assert.equal(f.status(), originalStatus);
  assert.equal(originalStatus.isConnected, true);
  assert.equal(originalStatus.parentNode.parentNode, f.nodes.get('buttons_42'));
  assert.equal(f.status().textContent, f.messages.pending);
  await f.answer(0, {status: 403, data: {success: false, accepted: false, retryable: false}});
  assert.deepEqual(f.buttons().map(button => button.disabled), [false, true, false],
    'Replacement row read-only controls remain disabled after known rejection');
  unchanged(f);
  assert.equal(f.call('rotate90'), true); await flush();
  assert.equal(f.requests.length, 2);
});

test('AJAX replacement during capacity waiting preserves the frozen payload, cancellation control and new read-only baseline', async () => {
  const f = fixture();
  f.call('removeFirstPage'); await flush(); await f.busy(0);
  const originalStatus = f.status(), originalCancel = f.cancel();
  f.replaceDocument(['rotate90']);
  assert.equal(f.call('rotate180'), false);
  assert.equal(f.status(), originalStatus);
  assert.equal(f.cancel(), originalCancel);
  assert.equal(f.cancel().hidden, false);
  assert.equal(f.status().textContent, f.messages.waiting);
  assert(f.buttons().every(button => button.disabled));
  assert.equal(f.requests.length, 1);
  await f.advance();
  assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].options.body, mutationBody('removeFirstPage'));
  await f.answer(1, {data: {success: true, accepted: true, document: 42, pageCount: 5, sourceRevision: committedRevision}});
  assert.equal(f.nodes.get('numPages_42').textContent, '5');
  assert.deepEqual(f.buttons().map(button => button.disabled), [true, false, false]);
  assert.equal(f.images[0].image, f.nodes.get('docImg_42'));
  assert.equal(f.confirmations.length, 1);
});

test('AJAX replacement in uncertain state keeps all current mutators locked and cannot hide the warning or replay on history return', async () => {
  const f = fixture();
  f.call('rotate180'); await flush();
  f.requests[0].reject(new TypeError('Network failed')); await flush();
  const originalStatus = f.status();
  f.replaceDocument(['removeFirstPage']);
  assert.equal(f.call('rotate90'), false);
  assert.equal(f.status(), originalStatus);
  assert.equal(f.status().textContent, f.messages.uncertain);
  assert.equal(f.status().getAttribute('role'), 'alert');
  assert.equal(f.cancel().hidden, true);
  assert(f.buttons().every(button => button.disabled));
  f.event('pagehide'); f.event('pageshow'); await flush();
  assert.equal(f.call('removeFirstPage'), false);
  assert.equal(f.requests.length, 1);
  assert.equal(f.timers.length, 0);
  unchanged(f);
});

test('response state transition binds an AJAX replacement even when the user does not click again', async () => {
  const f = fixture();
  f.call('rotate90'); await flush();
  const originalStatus = f.status();
  f.replaceDocument(['rotate180']);
  await f.busy(0);
  assert.equal(f.status(), originalStatus);
  assert.equal(f.status().textContent, f.messages.waiting);
  assert(f.buttons().every(button => button.disabled));
  f.cancel().click();
  assert.deepEqual(f.buttons().map(button => button.disabled), [false, true, false]);
  assert.equal(f.requests.length, 1);
  unchanged(f);
});

test('accepted response binds the replacement row and restores its baseline without replaying the mutation', async () => {
  const f = fixture();
  f.call('rotate180'); await flush();
  f.replaceDocument(['rotate90']);
  await f.answer(0, {data: {success: true, accepted: true, document: 42, pageCount: 6, sourceRevision: committedRevision}});
  assert.equal(f.status().textContent, f.messages.saved);
  assert.equal(f.nodes.get('numPages_42').textContent, '6');
  assert.deepEqual(f.buttons().map(button => button.disabled), [true, false, false]);
  assert.equal(f.images.length, 1);
  assert.equal(f.images[0].image, f.nodes.get('docImg_42'));
  assert.equal(f.requests.length, 1);
});
