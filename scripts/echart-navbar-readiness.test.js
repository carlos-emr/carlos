/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const chartHelper = chartActionUrlSource(source);
const ajaxSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/carlos-ajax.js'), 'utf8');
const { watchNavbarRecovery } = require('./patient-photo-upload-playwright-checks');
const start = source.indexOf('    function updateNavbarLoadState(');
const end = source.indexOf('// display in place editor', start);
assert.ok(start > 0 && end > start);
const script = source.slice(start, end).replace(/<%--[\s\S]*?--%>/g, '');

function makeDom() {
  const elements = {};
  function element(id = '') {
    return {
      id, attrs: {}, children: [], html: '', getHeight: () => 400,
      setAttribute(name, value) { this.attrs[name] = value; },
      appendChild(child) {
        if (child.parent) child.parent.children = child.parent.children.filter(entry => entry !== child);
        this.children.push(child); child.parent = this; elements[child.id] = child;
      },
      closest(selector) { return this.parent && selector === '#' + this.parent.id ? this.parent : null; },
      update(html) { if (this.throwRender) throw new Error('fragment failed'); this.html = html; },
    };
  }
  for (const id of ['leftNavBar', 'rightNavBar', 'leftColLoader', 'rightColLoader']) elements[id] = element(id);
  return { elements, element };
}

function harness({ throwFirstSend = false, doctorPanels = [], patient = '2', appointment = 0 } = {}) {
  const { elements, element } = makeDom();
  const requests = []; const errors = []; const timers = [];
  const context = {
    window: {}, document: { getElementById: id => elements[id], createElement: () => element() },
    $: id => elements[id], Element: { remove: el => { delete elements[el.id]; } },
    Colour: new Proxy({}, { get: () => '#fff' }), ctx: '/carlos', echartDoctorPanels: doctorPanels, providerNo: '9', demographicNo: patient, appointmentNo: appointment,
    famHistoryLabel: 'Family', oMedsLabel: 'Other', riskFactorsLabel: 'Risk',
    console: { error: (...args) => errors.push(args) },
    setTimeout: callback => timers.push(callback),
    CarlosAjax: { request(url, options) {
      const listeners = {}; const xhr = { timeout: 0, addEventListener: (name, fn) => { listeners[name] = fn; } };
      requests.push({ url, options, listeners, xhr });
      if (throwFirstSend && requests.length === 1) throw new Error('send failed');
      return xhr;
    } },
  };
  context.chartContext = '';
  vm.createContext(context); vm.runInContext(chartHelper + script, context);
  const loader = new context.navBarLoader(); loader.load();
  function complete(index, { status = 200, html = '<a>loaded</a>', networkError = false } = {}) {
    const options = requests[index].options;
    const transport = { status, responseText: html, networkError };
    try { if (status === 200) options.onSuccess(transport); }
    finally { options.onComplete(transport); }
  }
  return {
    context, loader, elements, requests, errors, complete, timers,
    runRetry() { timers.shift()(); },
    state: () => context.window.carlosNavbarLoadState,
  };
}

/** Execute the real CarlosAjax request path and dispatch native XHR events. */
function nativeAjaxHarness({ doctorPanels = [], patient = '2', appointment = 0 } = {}) {
  const { elements, element } = makeDom();
  const nativeRequests = []; const errors = []; const timers = [];
  class NativeXhr {
    constructor() {
      this.timeout = 0; this.headers = {}; this.listeners = {};
      this.status = 0; this.statusText = ''; this.responseText = ''; this.responseURL = '';
      nativeRequests.push(this);
    }
    open(method, url, asynchronous) { this.method = method; this.url = url; this.asynchronous = asynchronous; }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send(body) { this.body = body; }
    addEventListener(name, fn) { (this.listeners[name] ||= []).push(fn); }
    dispatch(name) { for (const fn of this.listeners[name] || []) fn(); }
    succeed(html = '<a>loaded</a>', status = 200) {
      this.status = status; this.responseText = html; this.responseURL = this.url; this.onload();
    }
    respondHttp(status, responseText = '', responseURL = this.url) {
      this.status = status; this.responseText = responseText; this.responseURL = responseURL; this.onload();
    }
    failNetwork() { this.status = 0; this.responseText = ''; this.onerror(); }
  }
  const context = {
    window: {},
    document: {
      getElementById: id => elements[id],
      createElement: () => element(),
      querySelector: selector => selector === 'input[name="CSRF-TOKEN"]' ? { value: 'owned-csrf' } : null,
    },
    XMLHttpRequest: NativeXhr,
    $: id => elements[id], Element: { remove: el => { delete elements[el.id]; } },
    Colour: new Proxy({}, { get: () => '#fff' }), ctx: '/carlos', echartDoctorPanels: doctorPanels, providerNo: '9', demographicNo: patient, appointmentNo: appointment,
    famHistoryLabel: 'Family', oMedsLabel: 'Other', riskFactorsLabel: 'Risk',
    console: { error: (...args) => errors.push(args) },
    setTimeout: callback => timers.push(callback),
  };
  context.chartContext = '';
  vm.createContext(context);
  vm.runInContext(ajaxSource, context);
  vm.runInContext(chartHelper + script, context);
  const loader = new context.navBarLoader(); loader.load();
  return {
    context, loader, elements, nativeRequests, errors, timers,
    runRetry() { timers.shift()(); },
    state: () => context.window.carlosNavbarLoadState,
  };
}

test('every initial and refreshed navbar request carries its own chart patient and appointment', () => {
  const first = harness({patient: '101', appointment: 41});
  const second = harness({patient: '202', appointment: 52});
  first.context.requestNavbarColumn('/carlos/encounter/displayForms', 'forms', 'cmd=forms', null);
  second.context.requestNavbarColumn('/carlos/encounter/displayMeasurements', 'measurements', 'cmd=measurements', null);
  for (const [h, patient, appointment] of [[first, '101', '41'], [second, '202', '52']]) {
    assert.equal(h.requests.length, 23);
    for (const request of h.requests) {
      const body = new URLSearchParams(request.options.postBody);
      assert.equal(body.get('demographicNo'), patient);
      assert.equal(body.get('appointmentNo'), appointment);
    }
  }
});

test('a slow last module retains its own column loader after other modules finish', () => {
  const h = harness(); assert.equal(h.requests.length, 22);
  for (let i = 1; i < 22; i++) h.complete(i);
  assert.equal(h.state().pending, 1);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'true');
  assert.ok(h.elements.leftColLoader);
  assert.equal(h.elements.rightNavBar.attrs['aria-busy'], 'false');
  assert.equal(h.elements.rightColLoader, undefined);
  h.complete(0);
  assert.equal(h.state().pending, 0);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'false');
  assert.equal(h.elements.leftColLoader, undefined);
});

test('failed and legitimately empty modules both settle, while only failures remain errors', () => {
  const h = harness(); h.complete(0, { status: 503 }); h.complete(1, { html: '' });
  // An intentionally removed preference module may still have a request in flight.
  delete h.elements.msgs;
  for (let i = 2; i < 22; i++) h.complete(i);
  assert.equal(h.state().pending, 0);
  assert.deepEqual(Array.from(h.state().failed), ['preventions']);
  assert.equal(h.state().modules.tickler.status, 'loaded');
  assert.equal(h.state().modules.msgs.status, 'loaded');
  assert.equal(h.elements.preventions.textContent, 'preventions Error: 503');
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'false');
});

test('a new loader generation cannot be overwritten or completed by old responses', () => {
  const h = harness(); h.loader.load();
  assert.equal(h.state().generation, 2);
  assert.equal(h.elements.leftNavBar.children.length, 10);
  assert.equal(h.elements.rightNavBar.children.length, 12);
  h.complete(22, { html: 'new prevention' });
  h.complete(0, { html: 'stale prevention' });
  h.complete(1, { status: 500 });
  assert.equal(h.elements.preventions.html, 'new prevention');
  assert.equal(h.state().pending, 21);
  assert.equal(h.state().failed.length, 0);
  for (let i = 23; i < 44; i++) h.complete(i);
  assert.equal(h.state().pending, 0);
});

test('a same-module refresh supersedes its pending request and clears an earlier error', () => {
  const h = harness();
  h.context.requestNavbarColumn('/reload', 'preventions', '', null);
  assert.equal(h.state().pending, 22);
  h.complete(22, { status: 503 });
  assert.deepEqual(Array.from(h.state().failed), ['preventions']);
  h.context.requestNavbarColumn('/retry', 'preventions', '', null);
  h.complete(23, { html: 'retry succeeded' });
  h.complete(0, { html: 'stale initial' });
  assert.equal(h.elements.preventions.html, 'retry succeeded');
  assert.equal(h.state().pending, 21);
  assert.equal(h.state().failed.length, 0);
});

test('synchronous send failure does not prevent the remaining modules from loading', () => {
  const h = harness({ throwFirstSend: true });
  assert.equal(h.requests.length, 22);
  assert.equal(h.state().scheduled, true);
  for (let i = 1; i < 22; i++) h.complete(i);
  assert.equal(h.state().pending, 0);
  assert.deepEqual(Array.from(h.state().failed), ['preventions']);
  assert.equal(h.errors.length, 1);
});

test('fragment exceptions and aborted XHRs settle their module without concealing failure', () => {
  const h = harness(); h.elements.preventions.throwRender = true;
  assert.throws(() => h.complete(0), /fragment failed/);
  h.requests[1].listeners.abort();
  for (let i = 2; i < 22; i++) h.complete(i);
  assert.equal(h.state().pending, 0);
  assert.deepEqual(Array.from(h.state().failed), ['preventions', 'tickler']);
});

test('the provider\'s Family and Referring Doctor panels load first in the left column, in that order', () => {
  const h = harness({ doctorPanels: [
    { path: '/encounter/displayFamilyDoctor', id: 'fDoc' },
    { path: '/encounter/displayReferringDoctor', id: 'rDoc' },
  ] });
  assert.equal(h.requests.length, 24);
  assert.deepEqual(h.elements.leftNavBar.children.slice(0, 3).map(child => child.id), ['fDoc', 'rDoc', 'preventions']);
  assert.match(h.requests[0].url, /^\/carlos\/encounter\/displayFamilyDoctor\?hC=/);
  assert.match(h.requests[1].url, /^\/carlos\/encounter\/displayReferringDoctor\?hC=/);
  for (let i = 0; i < 24; i++) h.complete(i);
  assert.equal(h.state().pending, 0);
  assert.equal(h.state().modules.fDoc.column, 'leftNavBar');
});

test('without doctor panels the left column starts with Preventions', () => {
  const h = harness();
  assert.equal(h.elements.leftNavBar.children[0].id, 'preventions');
  assert.ok(!h.requests.some(request => /display(Family|Referring)Doctor/.test(request.url)));
});

test('a display fragment retries a network error once with the same chart context and stays busy', () => {
  const h = harness({patient: '101', appointment: 41});
  for (let i = 1; i < 22; i++) h.complete(i);
  h.complete(0, {status: 0, networkError: true});
  assert.equal(h.state().pending, 1);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'true');
  h.runRetry();
  assert.equal(h.requests[22].url, h.requests[0].url);
  assert.equal(h.requests[22].options.postBody, h.requests[0].options.postBody);
  h.complete(22, {html: 'recovered fragment'});
  assert.equal(h.elements.preventions.html, 'recovered fragment');
  assert.equal(h.state().modules.preventions.recovered, true);
  assert.equal(h.state().pending, 0);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'false');
});

test('a second network error fails the fragment and produces no further retry', () => {
  const h = harness();
  h.complete(0, {status: 0, networkError: true});
  h.runRetry();
  h.complete(22, {status: 0, networkError: true});
  assert.equal(h.elements.preventions.textContent, 'preventions Error: 0');
  assert.deepEqual(Array.from(h.state().failed), ['preventions']);
  assert.equal(h.timers.length, 0);
});

test('HTTP errors, aborts, timeouts and non-display POSTs do not schedule a retry', () => {
  const h = harness();
  h.complete(0, {status: 403});
  h.requests[1].listeners.abort();
  h.requests[2].listeners.timeout();
  h.complete(11, {status: 0, networkError: true}); // CPP list uses CaseManagementView.
  assert.deepEqual(Array.from(h.state().failed), ['preventions', 'tickler', 'msgs', 'FamHistory']);
  assert.equal(h.timers.length, 0);
});

test('a new chart generation or a module refresh supersedes its queued retry', () => {
  const h = harness();
  h.complete(0, {status: 0, networkError: true});
  h.context.demographicNo = '202';
  h.loader.load();
  h.runRetry();
  assert.equal(h.requests.length, 44);
  assert.equal(new URLSearchParams(h.requests[22].options.postBody).get('demographicNo'), '202');
  h.complete(22, {status: 0, networkError: true});
  h.context.requestNavbarColumn('/carlos/encounter/displayPrevention', 'preventions', '', null);
  h.runRetry();
  assert.equal(h.requests.length, 45);
  h.complete(44, {html: 'fresh fragment'});
  assert.equal(h.elements.preventions.html, 'fresh fragment');
});

test('real CarlosAjax onerror marks transport with networkError and recovers through retry', () => {
  const h = nativeAjaxHarness({patient: '101', appointment: 41});
  assert.equal(h.nativeRequests.length, 22);
  // Verify both async and sync CarlosAjax network error paths mark transport.networkError.
  let observedTransport = null;
  h.context.CarlosAjax.request('/carlos/encounter/displayPrevention?hC=fff', {
    method: 'post', postBody: 'cmd=preventions',
    onComplete(transport) { observedTransport = transport; },
  });
  h.nativeRequests[22].failNetwork();
  assert.equal(observedTransport.status, 0);
  assert.equal(observedTransport.networkError, true);

  let syncTransport = null;
  const NativeXhr = h.context.XMLHttpRequest;
  h.context.XMLHttpRequest = class ThrowingSyncXhr extends NativeXhr {
    send() { throw new Error('sync connection reset'); }
  };
  try {
    h.context.CarlosAjax.request('/carlos/encounter/displayPrevention?hC=fff', {
      method: 'post', postBody: 'cmd=preventions', synchronous: true,
      onComplete(transport) { syncTransport = transport; },
    });
  } finally {
    h.context.XMLHttpRequest = NativeXhr;
  }
  assert.equal(syncTransport.status, 0);
  assert.equal(syncTransport.networkError, true);
  // Pop the sync probe from nativeRequests so indices below stay clear.
  h.nativeRequests.pop();

  for (let i = 1; i < 22; i++) h.nativeRequests[i].succeed();
  assert.equal(h.nativeRequests[0].timeout, 0);
  h.nativeRequests[0].failNetwork();
  assert.equal(h.state().pending, 1);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'true');
  assert.equal(h.timers.length, 1);

  h.runRetry();
  const retry = h.nativeRequests[23];
  assert.equal(retry.url, h.nativeRequests[0].url);
  assert.equal(retry.body, h.nativeRequests[0].body);
  assert.deepEqual(new URLSearchParams(retry.body).getAll('CSRF-TOKEN'), ['owned-csrf']);
  assert.equal(retry.timeout, 15000);
  retry.succeed('<a>recovered via real CarlosAjax</a>');
  assert.equal(h.elements.preventions.html, '<a>recovered via real CarlosAjax</a>');
  assert.equal(h.state().modules.preventions.recovered, true);
  assert.equal(h.state().pending, 0);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'false');
});

test('a stalled retry XHR through real CarlosAjax expires via its finite timeout and clears busy state', () => {
  const h = nativeAjaxHarness();
  for (let i = 1; i < 22; i++) h.nativeRequests[i].succeed();
  h.nativeRequests[0].failNetwork();
  assert.equal(h.state().pending, 1);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'true');

  h.runRetry();
  const retry = h.nativeRequests[22];
  assert.ok(Number.isFinite(retry.timeout) && retry.timeout > 0);
  retry.dispatch('timeout');
  assert.equal(h.state().modules.preventions.status, 'error');
  assert.equal(h.elements.preventions.textContent, 'preventions Error: 0');
  assert.deepEqual(Array.from(h.state().failed), ['preventions']);
  assert.equal(h.state().pending, 0);
  assert.equal(h.elements.leftNavBar.attrs['aria-busy'], 'false');
  assert.equal(h.timers.length, 0);
});

test('real CarlosAjax HTTP errors, CSRF redirects, and second network errors do not retry again', () => {
  const h = nativeAjaxHarness();
  h.nativeRequests[0].respondHttp(403, 'forbidden');
  h.nativeRequests[1].respondHttp(500, 'server error');
  h.nativeRequests[2].respondHttp(200, '<html>csrf</html>', '/carlos/errorpage.jsp');
  h.nativeRequests[3].failNetwork();
  assert.equal(h.timers.length, 1);
  h.runRetry();
  h.nativeRequests[22].failNetwork();
  assert.deepEqual(Array.from(h.state().failed), ['preventions', 'tickler', 'msgs', 'docs']);
  assert.equal(h.timers.length, 0);
});

test('all read-only navbar display panels recover on a network error, including doctor and right-column panels', () => {
  const h = nativeAjaxHarness({
    doctorPanels: [
      { path: '/encounter/displayFamilyDoctor', id: 'fDoc' },
      { path: '/encounter/displayReferringDoctor', id: 'rDoc' },
    ],
  });
  assert.equal(h.nativeRequests.length, 24);
  const retryable = [];
  for (let i = 0; i < 24; i++) {
    const req = h.nativeRequests[i];
    if (req.url.includes('/encounter/display')) {
      retryable.push(i);
      req.failNetwork();
    } else {
      req.succeed();
    }
  }
  // 2 doctor panels + 10 left display panels + 9 right display panels = 21.
  assert.equal(retryable.length, 21);
  assert.equal(h.timers.length, 21);
  assert.equal(h.state().pending, 21);

  while (h.timers.length) h.runRetry();
  assert.equal(h.nativeRequests.length, 45);
  for (let i = 24; i < 45; i++) {
    assert.ok(h.nativeRequests[i].timeout > 0);
    h.nativeRequests[i].succeed('<a>recovered</a>');
  }
  assert.equal(h.state().pending, 0);
  assert.deepEqual(Array.from(h.state().failed), []);
  assert.equal(h.state().modules.fDoc.recovered, true);
  assert.equal(h.state().modules.rDoc.recovered, true);
  assert.equal(h.state().modules.Dx.recovered, true);
  assert.equal(h.state().modules.contacts.recovered, true);

  // Also verify displayEconsultation via requestNavbarColumn.
  h.context.requestNavbarColumn('/carlos/encounter/displayEconsultation?hC=fff#top', 'consultation', 'cmd=consultation', 'leftNavBar');
  h.nativeRequests[45].failNetwork();
  assert.equal(h.timers.length, 1);
  h.runRetry();
  h.nativeRequests[46].succeed('<a>econsult recovered</a>');
  assert.equal(h.state().modules.consultation.recovered, true);
});

test('non-display, mutating, cross-origin, and suffixed URLs never schedule a retry', () => {
  const h = nativeAjaxHarness();
  for (let i = 0; i < 22; i++) h.nativeRequests[i].succeed();
  const blocked = [
    '/carlos/encounter/SaveEncounter2',
    'https://evil.example/carlos/encounter/displayPrevention?hC=fff',
    '/carlos/encounter/displayPrevention/extra',
    '/carlos/CaseManagementView?method=save',
  ];
  for (const url of blocked) {
    h.context.requestNavbarColumn(url, 'preventions', 'cmd=preventions', 'leftNavBar');
    h.nativeRequests.at(-1).failNetwork();
    assert.equal(h.timers.length, 0, `Unexpected retry scheduled for ${url}`);
    assert.equal(h.state().modules.preventions.status, 'error');
  }
});

test('stale retry timeout or post-load abort cannot clobber a settled or superseded module', () => {
  const h = nativeAjaxHarness();
  for (let i = 1; i < 22; i++) h.nativeRequests[i].succeed();
  h.nativeRequests[0].failNetwork();
  h.runRetry();
  const firstRetry = h.nativeRequests[22];
  // Supersede with a fresh request, finish it, then fire a late timeout on the old retry.
  h.context.requestNavbarColumn('/carlos/encounter/displayPrevention?hC=fff', 'preventions', 'cmd=preventions', 'leftNavBar');
  const fresh = h.nativeRequests[23];
  fresh.succeed('<a>fresh</a>');
  firstRetry.dispatch('timeout');
  fresh.dispatch('abort');
  assert.equal(h.state().modules.preventions.status, 'loaded');
  assert.equal(h.elements.preventions.html, '<a>fresh</a>');
  assert.deepEqual(Array.from(h.state().failed), []);
});

test('watchNavbarRecovery reconciles only confirmed recovered display failures and unambiguous console entries', async () => {
  const listeners = {};
  const mainFrame = { id: 'main' };
  const urlA = 'https://127.0.0.1/carlos/encounter/displayFamilyDoctor?hC=fff';
  const urlB = 'https://127.0.0.1/carlos/encounter/displayDisease?hC=fff';
  const urlC = 'https://127.0.0.1/carlos/encounter/displayRx?hC=fff';
  const req = (url, body, status) => ({
    method: () => 'POST',
    resourceType: () => 'xhr',
    url: () => url,
    postData: () => body,
    serviceWorker: () => null,
    frame: () => mainFrame,
    response: async () => (status == null ? null : { status: () => status }),
  });
  const modules = {
    fDoc: { status: 'loaded', recovered: true },
    Dx: { status: 'loaded', recovered: true },
    Rx: { status: 'loaded', recovered: false },
  };
  const chart = {
    mainFrame: () => mainFrame,
    on(event, fn) { (listeners[event] ||= []).push(fn); },
    locator: () => ({ first: () => ({ waitFor: async () => {} }) }),
    waitForFunction: async (fn) => {
      const prevDoc = global.document; const prevWin = global.window;
      global.document = { getElementById: () => ({ querySelectorAll: () => ({ length: 3 }) }) };
      global.window = { carlosNavbarLoadState: { scheduled: true, pending: 0, failed: [], modules } };
      try { const value = fn(); return { jsonValue: async () => value }; }
      finally {
        if (prevDoc === undefined) delete global.document; else global.document = prevDoc;
        if (prevWin === undefined) delete global.window; else global.window = prevWin;
      }
    },
    evaluate: async (fn) => {
      const prevWin = global.window;
      global.window = { carlosNavbarLoadState: { modules } };
      try { return fn(); }
      finally { if (prevWin === undefined) delete global.window; else global.window = prevWin; }
    },
  };
  const recorder = { requestFailures: [], consoleIssues: [] };
  const waitForReloadedNavbars = watchNavbarRecovery(chart, recorder);
  for (const fn of listeners.framenavigated) fn(mainFrame);

  // Confirmed recovery on fDoc (1 matching console entry -> both removed).
  const fDocFail = req(urlA, 'cmd=fDoc&demographicNo=10', null);
  const fDocRetry = req(urlA, 'cmd=fDoc&demographicNo=10', 200);
  // Confirmed recovery on Dx, but 2 duplicate console entries -> failure removed, ambiguous console kept.
  const dxFail = req(urlB, 'cmd=Dx&demographicNo=10', null);
  const dxRetry = req(urlB, 'cmd=Dx&demographicNo=10', 200);
  // Unrecovered failure on Rx (no retry) -> must remain fatal in recorder.
  const rxFail = req(urlC, 'cmd=Rx&demographicNo=10', null);
  // Null postData request -> safely ignored and kept in recorder.
  const nullBodyFail = req(urlC, null, null);
  for (const r of [fDocFail, fDocRetry, dxFail, dxRetry, rxFail, nullBodyFail]) {
    for (const fn of listeners.request) fn(r);
  }
  const makeFailure = (url, ref, errorText = 'net::ERR_FAILED') => {
    const entry = { label: 'echart', url, resourceType: 'xhr', errorText };
    Object.defineProperty(entry, 'requestRef', { enumerable: false, value: new WeakRef(ref) });
    return entry;
  };
  recorder.requestFailures.push(
    makeFailure(urlA, fDocFail),
    makeFailure(urlB, dxFail),
    makeFailure(urlC, rxFail),
    makeFailure(urlC, nullBodyFail),
  );
  recorder.consoleIssues.push(
    { label: 'echart', location: { url: urlA }, text: 'Failed to load resource: net::ERR_FAILED' },
    { label: 'echart', location: { url: urlB }, text: 'Failed to load resource: net::ERR_FAILED' },
    { label: 'echart', location: { url: urlB }, text: 'Failed to load resource: net::ERR_FAILED' },
    { label: 'echart', location: { url: urlC }, text: 'Failed to load resource: net::ERR_FAILED' },
  );

  await waitForReloadedNavbars(0);
  assert.deepEqual(recorder.requestFailures.map(f => f.url), [urlC, urlC]);
  assert.deepEqual(recorder.consoleIssues.map(c => c.location.url), [urlB, urlB, urlC]);
});
