/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {completeEncounterForm} = require('../src/main/webapp/form/form-completion');

const legacySource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/encounter/js/encounter.js'), 'utf8');
const modernSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const modernStart = modernSource.indexOf('    function loadDiv(');
const modernEnd = modernSource.indexOf('\n    /*', modernStart);
assert(modernStart > 0 && modernEnd > modernStart, 'The modern popup refresh function must be present');

function harness(kind, patient, appointment) {
  const requests = [];
  const events = [];
  const timers = [];
  const notifications = [];
  const panels = {};
  for (const id of ['forms', 'measurements']) {
    panels[id] = {
      id, children: [{old: true}], html: 'previous content',
      get firstChild() { return this.children[0]; },
      removeChild(child) { this.children.splice(this.children.indexOf(child), 1); },
      update(html) { this.html = html; },
    };
  }
  const reloadField = {value: 'forms'};
  const editor = {value: 'Own draft', scrollHeight: 80, dispatchEvent(event) { events.push(event.type); }};
  const context = {
    URL, Event, ctx: '/carlos', location: new URL(`https://fixture.invalid/carlos/encounter?demographicNo=${patient}`),
    // Legacy windows must use encounterConfig even when obsolete globals describe another chart.
    demographicNo: kind === 'legacy' ? '999' : patient,
    appointmentNo: kind === 'legacy' ? '999' : appointment,
    cur_appointment_no: '888',
    encounterConfig: {demographicNo: patient, appointmentNo: appointment,
      urls: {navSections: {forms: '/carlos/encounter/displayForms?hC=917611'}}},
    document: {
      getElementById(id) { return id === 'reloadDiv' ? reloadField : panels[id]; },
      querySelectorAll() { return [{value: patient}]; },
      querySelector(selector) { return selector === 'textarea[name="caseNote_note"]' ? editor : null; },
    },
    $: id => panels[id],
    CarlosAjax: {request(url, options) { requests.push({url, options}); }},
    carlosExtractAndExecScripts(panel, html) { panel.html = html; },
    notifyDivLoaded(id) { notifications.push(id); },
    addEventListener() {},
    setTimeout(callback, delay) { timers.push({callback, delay}); },
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(kind === 'legacy' ? legacySource : modernSource.slice(modernStart, modernEnd), context);
  // Expansion is a separate widget; retain its call while testing refresh requests and rendering.
  context.listDisplay = value => notifications.push(value);
  return {context, requests, panels, editor, events, timers, reloadField, notifications};
}

function assertIdentity(request, patient, appointment) {
  assert.equal(request.options.method, 'post');
  const body = new URLSearchParams(request.options.postBody);
  assert.deepEqual(body.getAll('demographicNo'), [patient]);
  assert.deepEqual(body.getAll('appointmentNo'), [appointment]);
  const url = new URL(request.url, 'https://fixture.invalid');
  for (const value of url.searchParams.getAll('demographicNo')) assert.equal(value, patient);
  for (const value of url.searchParams.getAll('appointmentNo')) assert.equal(value, appointment);
  assert(![...body.values()].includes('999') && ![...body.values()].includes('888'), 'A stale global escaped into the request');
  return body;
}

for (const kind of ['legacy', 'modern']) {
  test(`${kind} loadDiv keeps A and B request identities and response panels separate`, () => {
    const a = harness(kind, '101', '41');
    const b = harness(kind, '202', '52');
    const url = '/carlos/encounter/displayForms?cmd=forms&hC=917611';
    assert.equal(a.context.loadDiv('forms', url, 0), false);
    assert.equal(b.context.loadDiv('forms', url, 0), false);
    assertIdentity(a.requests[0], '101', '41');
    assertIdentity(b.requests[0], '202', '52');
    assert.equal(a.requests[0].url, url);
    b.requests[0].options.onSuccess({status: 200, responseText: 'Patient B forms'});
    assert.equal(a.panels.forms.html, 'previous content');
    a.requests[0].options.onSuccess({status: 200, responseText: 'Patient A forms'});
    assert.equal(a.panels.forms.html, 'Patient A forms');
    assert.equal(b.panels.forms.html, 'Patient B forms');
    assert.equal(a.panels.measurements.html, 'previous content');
    assert.equal(b.panels.measurements.html, 'previous content');
  });

  test(`${kind} loadDiv sends explicit appointment zero when no appointment is present`, () => {
    for (const appointment of [undefined, null, 0, '0']) {
      const f = harness(kind, '101', appointment);
      f.context.loadDiv('measurements', '/carlos/encounter/displayMeasurements?cmd=measurements', 0);
      assertIdentity(f.requests[0], '101', '0');
    }
  });
}

test('legacy popLeftColumn sends its own patient and appointment and renders only its own panel', () => {
  const a = harness('legacy', '101', '41');
  const b = harness('legacy', '202', '52');
  const url = '/carlos/encounter/displayMeasurements';
  a.context.popLeftColumn(url, 'measurements', 'measurements');
  b.context.popLeftColumn(url, 'measurements', 'measurements');
  assert.equal(assertIdentity(a.requests[0], '101', '41').get('cmd'), 'measurements');
  assert.equal(assertIdentity(b.requests[0], '202', '52').get('cmd'), 'measurements');
  b.requests[0].options.onSuccess({responseText: 'Patient B measurements'});
  a.requests[0].options.onSuccess({responseText: 'Patient A measurements'});
  assert.equal(a.panels.measurements.html, 'Patient A measurements');
  assert.equal(b.panels.measurements.html, 'Patient B measurements');
  assert.deepEqual(a.panels.measurements.children, []);
  assert.deepEqual(b.panels.measurements.children, []);
  assert.equal(a.panels.forms.html, 'previous content');
});

test('legacy popup-close updateDiv posts explicit patient and appointment zero through popLeftColumn', () => {
  for (const appointment of [undefined, null, '', 0, '0']) {
    const f = harness('legacy', '101', appointment);
    f.context.updateNeeded = true;
    f.context.updateDiv();
    assert.equal(f.requests.length, 1);
    assert.equal(f.requests[0].url, f.context.encounterConfig.urls.navSections.forms);
    assert.equal(assertIdentity(f.requests[0], '101', '0').get('cmd'), 'forms');
    assert.equal(f.context.updateNeeded, false);
    f.context.updateDiv();
    assert.equal(f.requests.length, 1, 'A consumed popup-close notification must not send another refresh');
  }
});

test('modern loadDiv treats an undeclared appointment global as no appointment instead of throwing', () => {
  // ChartNotes.jsp's standalone route loads newCaseManagementView.js.jsp without the layout's
  // `var appointmentNo`; a bare reference there would throw before any request is sent.
  const f = harness('modern', '101', '41');
  delete f.context.appointmentNo;
  assert.equal(Object.hasOwn(f.context, 'appointmentNo'), false);
  assert.equal(f.context.loadDiv('measurements', '/carlos/encounter/displayMeasurements?cmd=measurements', 0), false);
  assert.equal(f.requests.length, 1);
  assertIdentity(f.requests[0], '101', '0');
});

test('modern loadDiv retains success and failure completion callbacks with explicit patient identity', () => {
  const f = harness('modern', '101', '0');
  const completed = [];
  for (const status of [200, 503]) {
    f.context.loadDiv('forms', '/carlos/encounter/displayForms?cmd=forms', 0, success => completed.push(success));
    const request = f.requests.at(-1);
    assertIdentity(request, '101', '0');
    const response = {status, responseText: status === 200 ? 'Saved form' : 'Unavailable'};
    (status === 200 ? request.options.onSuccess : request.options.onFailure)(response);
    request.options.onComplete(response);
  }
  assert.deepEqual(completed, [true, false]);
  assert.deepEqual(f.notifications, ['forms']);
  assert.match(f.panels.forms.html, /503/);
});

test('form completion refreshes both own panels through the real modern hook with appointment zero and no stale global', () => {
  for (const patient of ['101', '202']) {
    const f = harness('modern', patient, 0);
    let closed = false;
    completeEncounterForm({opener: f.context, close() { closed = true; }}, patient, 'See Vascular Tracker Template');
    assert.equal(closed, true);
    assert.equal(f.editor.value, 'Own draft\nSee Vascular Tracker Template');
    assert.deepEqual(f.events, ['input', 'change']);
    assert.equal(f.requests.length, 2);
    for (const request of f.requests) {
      assertIdentity(request, patient, '0');
      const url = new URL(request.url);
      assert.equal(url.origin, 'https://fixture.invalid');
      const panel = url.searchParams.get('cmd');
      assert(['forms', 'measurements'].includes(panel));
      request.options.onSuccess({status: 200, responseText: `Own ${panel}`});
    }
    assert.equal(f.panels.forms.html, 'Own forms');
    assert.equal(f.panels.measurements.html, 'Own measurements');
  }
});
