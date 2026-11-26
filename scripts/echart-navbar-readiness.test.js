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
const start = source.indexOf('    function updateNavbarLoadState(');
const end = source.indexOf('// display in place editor', start);
assert.ok(start > 0 && end > start);
const script = source.slice(start, end).replace(/<%--[\s\S]*?--%>/g, '');

function harness({ throwFirstSend = false, doctorPanels = [], patient = '2', appointment = 0 } = {}) {
  const elements = {}; const requests = []; const errors = []; const timers = [];
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
  const context = {
    window: {}, document: { getElementById: id => elements[id], createElement: () => element() },
    $: id => elements[id], Element: { remove: el => { delete elements[el.id]; } },
    Colour: new Proxy({}, { get: () => '#fff' }), ctx: '/carlos', echartDoctorPanels: doctorPanels, providerNo: '9', demographicNo: patient, appointmentNo: appointment,
    famHistoryLabel: 'Family', oMedsLabel: 'Other', riskFactorsLabel: 'Risk',
    console: { error: (...args) => errors.push(args) },
    setTimeout: callback => timers.push(callback),
    CarlosAjax: { request(url, options) {
      const listeners = {}; const xhr = { addEventListener: (name, fn) => { listeners[name] = fn; } };
      requests.push({ url, options, listeners });
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
