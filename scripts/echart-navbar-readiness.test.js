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
const { consumeInjectedFault } = require('./echart-navbar-retry-playwright-checks');
const start = source.indexOf('    function updateNavbarLoadState(');
const end = source.indexOf('// display in place editor', start);
assert.ok(start > 0 && end > start);
const script = source.slice(start, end).replace(/<%--[\s\S]*?--%>/g, '');
const loadDivStart = source.indexOf('    function loadDiv(');
const loadDivEnd = source.indexOf('\n    /*', loadDivStart);
assert.ok(loadDivStart > 0 && loadDivEnd > loadDivStart);
const loadDivScript = source.slice(loadDivStart, loadDivEnd);
const onIssueUpdateStart = source.indexOf('    function onIssueUpdate(');
const onIssueUpdateEnd = source.indexOf('    function submitIssue(', onIssueUpdateStart);
assert.ok(onIssueUpdateStart > 0 && onIssueUpdateEnd > onIssueUpdateStart);
const onIssueUpdateScript = source.slice(onIssueUpdateStart, onIssueUpdateEnd);

function makeDom() {
  const elements = {};
  function element(id = '') {
    return {
      id, attrs: {}, children: [], html: '', getHeight: () => 400,
      setAttribute(name, value) { this.attrs[name] = value; },
      querySelector() { return null; },
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

function harness({ throwFirstSend = false, patient = '2', appointment = 0 } = {}) {
  const { elements, element } = makeDom();
  const requests = []; const errors = []; const timers = [];
  const context = {
    window: {}, document: { getElementById: id => elements[id], createElement: () => element() },
    $: id => elements[id], Element: { remove: el => { delete elements[el.id]; } },
    Colour: new Proxy({}, { get: () => '#fff' }), ctx: '/carlos', providerNo: '9', demographicNo: patient, appointmentNo: appointment,
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
function nativeAjaxHarness({ patient = '2', appointment = 0 } = {}) {
  const { elements, element } = makeDom();
  const nativeRequests = []; const errors = []; const timers = []; const alerts = [];
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
    Colour: new Proxy({}, { get: () => '#fff' }), ctx: '/carlos', providerNo: '9', demographicNo: patient, appointmentNo: appointment,
    famHistoryLabel: 'Family', oMedsLabel: 'Other', riskFactorsLabel: 'Risk',
    chartLoadError: 'chart load failed',
    issueSidebarRefreshError: 'sidebar refresh failed; saving remains available',
    alert: message => alerts.push(message),
    console: { error: (...args) => errors.push(args) },
    setTimeout: callback => timers.push(callback),
  };
  context.chartContext = '';
  vm.createContext(context);
  vm.runInContext(ajaxSource, context);
  vm.runInContext(chartHelper + script + loadDivScript + onIssueUpdateScript, context);
  const loader = new context.navBarLoader(); loader.load();
  return {
    context, loader, elements, nativeRequests, errors, timers, alerts,
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

test('all read-only navbar display panels recover on a network error across both left and right columns', () => {
  const h = nativeAjaxHarness();
  assert.equal(h.nativeRequests.length, 22);
  const retryable = [];
  for (let i = 0; i < 22; i++) {
    const req = h.nativeRequests[i];
    if (req.url.includes('/encounter/display')) {
      retryable.push(i);
      req.failNetwork();
    } else {
      req.succeed();
    }
  }
  // 10 left display panels + 9 right display panels = 19.
  assert.equal(retryable.length, 19);
  assert.equal(h.timers.length, 19);
  assert.equal(h.state().pending, 19);

  while (h.timers.length) h.runRetry();
  assert.equal(h.nativeRequests.length, 41);
  for (let i = 22; i < 41; i++) {
    assert.ok(h.nativeRequests[i].timeout > 0);
    h.nativeRequests[i].succeed('<a>recovered</a>');
  }
  assert.equal(h.state().pending, 0);
  assert.deepEqual(Array.from(h.state().failed), []);
  assert.equal(h.state().modules.preventions.recovered, true);
  assert.equal(h.state().modules.Dx.recovered, true);
  assert.equal(h.state().modules.contacts.recovered, true);

  // Also verify displayEconsultation via requestNavbarColumn.
  h.context.requestNavbarColumn('/carlos/encounter/displayEconsultation?hC=fff#top', 'consultation', 'cmd=consultation', 'leftNavBar');
  h.nativeRequests[41].failNetwork();
  assert.equal(h.timers.length, 1);
  h.runRetry();
  h.nativeRequests[42].succeed('<a>econsult recovered</a>');
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

test('onIssueUpdate loadDiv refreshes supersede queued and in-flight navbar retries for unresolved and resolved issues', () => {
  const h = nativeAjaxHarness();
  const unresolvedReq = h.nativeRequests.find(r => r.url.includes('/encounter/displayIssues?'));
  const resolvedReq = h.nativeRequests.find(r => r.url.includes('/encounter/displayResolvedIssues?'));
  assert.ok(unresolvedReq && resolvedReq);
  for (const req of h.nativeRequests) {
    if (req !== unresolvedReq && req !== resolvedReq) req.succeed();
  }
  // Fail both initial issue-panel requests: queue retry for unresolvedIssues, start in-flight retry for resolvedIssues.
  unresolvedReq.failNetwork();
  resolvedReq.failNetwork();
  assert.equal(h.timers.length, 2);
  // Start only the second timer (resolvedIssues retry) so one retry is queued and one is in flight.
  const queuedUnresolvedRetry = h.timers.shift();
  h.runRetry();
  const inflightResolvedRetry = h.nativeRequests[22];
  assert.match(inflightResolvedRetry.url, /\/encounter\/displayResolvedIssues\?/);

  let editorReleased;
  h.context.onIssueUpdate(ok => { editorReleased = ok; });
  assert.equal(editorReleased, undefined);
  assert.equal(h.nativeRequests.length, 25);
  const freshUnresolved = h.nativeRequests[23];
  const freshResolved = h.nativeRequests[24];
  assert.match(freshUnresolved.url, /\/encounter\/displayIssues\?/);
  assert.match(freshResolved.url, /\/encounter\/displayResolvedIssues\?/);

  // The superseded queued timer must not send a stale retry.
  queuedUnresolvedRetry();
  assert.equal(h.nativeRequests.length, 25);

  // Complete both fresh loadDiv refreshes and verify the editor callback is released.
  freshUnresolved.succeed('<a>fresh unresolved</a>');
  assert.equal(editorReleased, undefined);
  freshResolved.succeed('<a>fresh resolved</a>');
  assert.equal(editorReleased, true);
  assert.equal(h.state().pending, 0);
  assert.equal(h.elements.rightNavBar.attrs['aria-busy'], 'false');
  assert.deepEqual(Array.from(h.state().failed), []);
  assert.deepEqual(h.alerts, []);

  // A late stale response or timeout/error from the superseded in-flight retry must be ignored.
  inflightResolvedRetry.succeed('<a>stale resolved</a>');
  inflightResolvedRetry.dispatch('timeout');
  assert.equal(h.elements.unresolvedIssues.html, '<a>fresh unresolved</a>');
  assert.equal(h.elements.resolvedIssues.html, '<a>fresh resolved</a>');
  assert.equal(h.state().modules.unresolvedIssues.status, 'loaded');
  assert.equal(h.state().modules.resolvedIssues.status, 'loaded');
  assert.deepEqual(Array.from(h.state().failed), []);
});

test('a failing onIssueUpdate loadDiv settles navbar state to error while still releasing the editor', () => {
  const h = nativeAjaxHarness();
  const unresolvedReq = h.nativeRequests.find(r => r.url.includes('/encounter/displayIssues?'));
  for (const req of h.nativeRequests) {
    if (req !== unresolvedReq) req.succeed();
  }
  unresolvedReq.failNetwork();
  assert.equal(h.timers.length, 1);

  let editorReleased;
  h.context.onIssueUpdate(ok => { editorReleased = ok; });
  // Queued retry timer fires after loadDiv took ownership: must be ignored.
  h.runRetry();
  assert.equal(h.nativeRequests.length, 24);
  h.nativeRequests[22].respondHttp(500, 'server error');
  h.nativeRequests[23].succeed('<a>fresh resolved</a>');

  assert.equal(editorReleased, true);
  assert.deepEqual(h.alerts, ['sidebar refresh failed; saving remains available']);
  assert.equal(h.state().modules.unresolvedIssues.status, 'error');
  assert.equal(h.state().modules.resolvedIssues.status, 'loaded');
  assert.deepEqual(Array.from(h.state().failed), ['unresolvedIssues']);
  assert.equal(h.state().pending, 0);
  assert.equal(h.elements.rightNavBar.attrs['aria-busy'], 'false');
});

test('a newer requestNavbarColumn supersedes an in-flight onIssueUpdate loadDiv without trapping the editor', () => {
  const h = nativeAjaxHarness();
  for (const req of h.nativeRequests) req.succeed();

  let editorReleased;
  h.context.onIssueUpdate(ok => { editorReleased = ok; });
  const staleLoadDivUnresolved = h.nativeRequests[22];
  const loadDivResolved = h.nativeRequests[23];

  h.context.requestNavbarColumn('/carlos/encounter/displayIssues?hC=fff', 'unresolvedIssues', 'cmd=unresolvedIssues', 'rightNavBar');
  const newerNavbarReq = h.nativeRequests[24];
  newerNavbarReq.succeed('<a>newer navbar unresolved</a>');

  staleLoadDivUnresolved.succeed('<a>stale loadDiv unresolved</a>');
  loadDivResolved.succeed('<a>fresh resolved</a>');

  assert.equal(h.elements.unresolvedIssues.html, '<a>newer navbar unresolved</a>');
  assert.equal(h.elements.resolvedIssues.html, '<a>fresh resolved</a>');
  assert.equal(editorReleased, true);
  assert.deepEqual(h.alerts, []);
  assert.equal(h.state().pending, 0);
  assert.deepEqual(Array.from(h.state().failed), []);
});

test('watchNavbarRecovery reconciles only confirmed recovered display failures and unambiguous console entries', async () => {
  const listeners = {};
  const mainFrame = { id: 'main' };
  const urlA = 'https://127.0.0.1/carlos/encounter/displayPrevention?hC=fff';
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
    preventions: { status: 'loaded', recovered: true },
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

  // Confirmed recovery on preventions (1 matching console entry -> both removed).
  const prevFail = req(urlA, 'cmd=preventions&demographicNo=10', null);
  const prevRetry = req(urlA, 'cmd=preventions&demographicNo=10', 200);
  // Confirmed recovery on Dx, but 2 duplicate console entries -> failure removed, ambiguous console kept.
  const dxFail = req(urlB, 'cmd=Dx&demographicNo=10', null);
  const dxRetry = req(urlB, 'cmd=Dx&demographicNo=10', 200);
  // Unrecovered failure on Rx (no retry) -> must remain fatal in recorder.
  const rxFail = req(urlC, 'cmd=Rx&demographicNo=10', null);
  // Null postData request -> safely ignored and kept in recorder.
  const nullBodyFail = req(urlC, null, null);
  for (const r of [prevFail, prevRetry, dxFail, dxRetry, rxFail, nullBodyFail]) {
    for (const fn of listeners.request) fn(r);
  }
  const makeFailure = (url, ref, errorText = 'net::ERR_FAILED') => {
    const entry = { label: 'echart', url, resourceType: 'xhr', errorText };
    Object.defineProperty(entry, 'requestRef', { enumerable: false, value: new WeakRef(ref) });
    return entry;
  };
  recorder.requestFailures.push(
    makeFailure(urlA, prevFail),
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

test('consumeInjectedFault removes only recorded injected request failures and unambiguous console errors', () => {
  const urlFam = 'https://127.0.0.1/carlos/CaseManagementView?method=listNotes&issue_code=FamHistory';
  const urlNotes = 'https://127.0.0.1/carlos/CaseManagementView?method=viewNotes';
  const urlMsgs = 'https://127.0.0.1/carlos/encounter/displayMessages?hC=009999';
  const reqInjectedFam = { id: 'injected-fam' };
  const reqUnrelatedNotes = { id: 'unrelated-notes' };
  const reqUnrelatedSameUrl = { id: 'unrelated-same-url' };
  const reqInjectedMsgs = { id: 'injected-msgs' };
  const makeFailure = (url, ref, label = 'echart') => {
    const entry = { label, url, resourceType: 'xhr', errorText: 'net::ERR_FAILED' };
    if (ref) Object.defineProperty(entry, 'requestRef', { enumerable: false, value: new WeakRef(ref) });
    return entry;
  };
  const recorder = {
    requestFailures: [
      makeFailure(urlFam, { id: 'prior' }),
      makeFailure(urlFam, reqInjectedFam),
      makeFailure(urlNotes, reqUnrelatedNotes),
      makeFailure(urlMsgs, reqInjectedMsgs),
      makeFailure(urlMsgs, reqUnrelatedSameUrl),
      makeFailure(urlFam, null),
    ],
    consoleIssues: [
      { label: 'echart', location: { url: urlFam }, text: 'Failed to load resource: net::ERR_FAILED' },
      { label: 'echart', location: { url: urlNotes }, text: 'Failed to load resource: net::ERR_FAILED' },
      { label: 'echart', location: { url: urlMsgs }, text: 'Failed to load resource: net::ERR_FAILED' },
      { label: 'echart', location: { url: urlMsgs }, text: 'Failed to load resource: net::ERR_FAILED' },
    ],
  };

  // Both urlFam (missing requestRef kept) and urlMsgs (unrelated same-URL failure kept) must retain ambiguous console entries.
  consumeInjectedFault(recorder, { failures: 1, console: 0 }, new Set([reqInjectedFam, reqInjectedMsgs]));
  assert.deepEqual(
    recorder.requestFailures.map(f => f.requestRef?.deref()?.id ?? 'missing'),
    ['prior', 'unrelated-notes', 'unrelated-same-url', 'missing'],
  );
  assert.equal(recorder.consoleIssues.length, 4);

  // When every failure for a (label, url) is an injected request and console count <= removed count, both are removed.
  const reqCleanA = { id: 'clean-a' };
  const reqCleanB = { id: 'clean-b' };
  const cleanRecorder = {
    requestFailures: [makeFailure(urlFam, reqCleanA), makeFailure(urlFam, reqCleanB), makeFailure(urlNotes, reqUnrelatedNotes)],
    consoleIssues: [
      { label: 'echart', location: { url: urlFam }, text: 'Failed to load resource: net::ERR_FAILED' },
      { label: 'echart', location: { url: urlFam }, text: 'Failed to load resource: net::ERR_ABORTED' },
      { label: 'echart', location: { url: urlNotes }, text: 'Failed to load resource: net::ERR_FAILED' },
    ],
  };
  consumeInjectedFault(cleanRecorder, { failures: 0, console: 0 }, new Set([reqCleanA, reqCleanB]));
  assert.deepEqual(cleanRecorder.requestFailures.map(f => f.url), [urlNotes]);
  assert.deepEqual(cleanRecorder.consoleIssues.map(c => c.location.url), [urlNotes]);
});

