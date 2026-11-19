/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/web/dashboard/display/drilldownDisplayController.js'), 'utf8');
const providerContext = fs.readFileSync(path.join(__dirname, '../src/main/webapp/web/dashboard/display/dashboard-provider-context.js'), 'utf8');

/** Run the shipped controller and retain the handlers it installs on the page. */
function controller(selected = ['101', '102'], contextPath = '/clinic', ticklerFields = [], provider = '') {
  const handlers = new Map();
  const requests = [];
  const alerts = [];
  const document = {getElementById: id => id === 'dashboardProviderNo' ? {id, value: provider} : {id}};
  const buttons = new Map();
  const hidden = [];
  const rows = ['101', '102', '103'].map(id => ({ id, checked: selected.includes(id) }));
  function $(selector) {
    const chain = {
      ready(callback) { callback(); return chain; },
      on(event, callback) { handlers.set(selector, callback); return chain; },
      DataTable() { return chain; },
      draw() { return chain; },
      empty() { return chain; },
      // Unrelated filter-layout callbacks do not participate in selected-patient actions.
      prepend() { return chain; },
      each(callback) {
        if (selector === 'input:checkbox.patientChecked') rows.forEach(row => callback.call(row));
        return chain;
      },
      attr(name) { return selector && selector[name]; },
      closest(name) { assert.equal(name, 'form'); return $(selector.form); },
      serializeArray() { return (selector === '#ticklerAddForm' ? ticklerFields : selector.fields).map(field => ({ ...field })); },
    };
    return chain;
  }
  $.fn = { dataTableExt: { afnFiltering: [] }, dataTable: { moment() {}, ext: { order: {} } } };
  $.ajax = request => { requests.push(request); };
  const bootstrap = {Modal: {getOrCreateInstance: element => ({hide: () => hidden.push(element.id)})}};
  const window = {document, location: {href: `https://clinic.invalid${contextPath}/web/dashboard/display/DrilldownDisplay`}};
  const sandbox = { $, document, window, URL, bootstrap, ctx: contextPath, alert: message => alerts.push(message), console };
  vm.runInNewContext(providerContext, sandbox);
  sandbox.dashboardProviderUrl = window.dashboardProviderUrl;
  vm.runInNewContext(source, sandbox);
  function click(id, form) {
    let prevented = false;
    if (!buttons.has(id)) buttons.set(id, {id, form});
    const button = buttons.get(id); // Submit buttons deliberately have no href.
    if (id === 'assignTicklerChecked') button.href = `${contextPath}/web/dashboard/display/AssignTickler`;
    const handler = handlers.get(`#${id}`);
    assert.equal(typeof handler, 'function', `${id} has a registered handler`);
    handler.call(button, { preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
  }
  return { requests, alerts, click, buttons, hidden };
}

const cases = [
  ['confirmAddToDiseaseRegistry', 'addToDiseaseRegistry', { name: 'dxUpdateICD9Code', value: '250' }],
  ['confirmPatientExclusion', 'excludePatients', { name: 'indicatorId', value: '4245' }],
  ['confirmPatientStatusUpdate', 'setPatientsInactive', null],
];

for (const [button, method, extra] of cases) {
  test(`${button} posts its form action, protected fields and only selected patients`, () => {
    const run = controller();
    const fields = [{ name: 'method', value: method }, { name: 'CSRF-TOKEN', value: 'owned-token' }];
    if (extra) fields.push(extra);
    run.click(button, { action: '/clinic/web/dashboard/display/BulkPatientAction', fields });
    assert.equal(run.requests.length, 1);
    const request = run.requests[0];
    assert.equal(request.type, 'POST');
    assert.equal(request.url, '/clinic/web/dashboard/display/BulkPatientAction');
    assert.deepEqual(JSON.parse(JSON.stringify(request.data)), [...fields, { name: 'patientIds', value: '101,102' }]);
  });

  test(`${button} does not submit an empty selection`, () => {
    const run = controller([]);
    run.click(button, {});
    assert.equal(run.requests.length, 0);
    assert.equal(run.alerts.length, 1);
  });
}

for (const contextPath of ['', '/clinic']) {
  test(`Assign Tickler uses one context prefix at ${contextPath || 'the root'}`, () => {
    const run = controller(['101', '102'], contextPath);
    run.click('assignTicklerChecked', {});
    assert.equal(run.requests.length, 1);
    assert.equal(run.requests[0].url, `${contextPath}/web/dashboard/display/AssignTickler`);
    assert.equal(run.requests[0].type, 'POST');
    assert.equal(run.requests[0].data, 'demographics=101,102');
  });
}

for (const hiddenMethod of [[], [{ name: 'method', value: 'old-operation' }]]) {
  test(`Save Tickler supplies exactly one save operation with ${hiddenMethod.length} surviving method fields`, () => {
    const fields = [{ name: 'demographics', value: '101,102' }, { name: 'CSRF-TOKEN', value: 'owned-token' },
      { name: 'messageAppend', value: 'owned recall' }];
    const run = controller(['101', '102'], '/clinic', [...fields, ...hiddenMethod]);
    run.click('saveTicklerBtn', {});
    assert.equal(run.requests.length, 1);
    assert.equal(run.requests[0].url, '/clinic/web/dashboard/display/AssignTickler');
    assert.equal(run.requests[0].type, 'POST');
    assert.deepEqual(JSON.parse(JSON.stringify(run.requests[0].data)),
      [...fields, { name: 'method', value: 'saveTickler' }]);
  });
}

for (const [button, method] of cases) {
  test(`${button} prevents concurrent submissions and unlocks after a response`, () => {
    const run = controller();
    const form = {action: '/clinic/web/dashboard/display/BulkPatientAction', fields: [{name: 'method', value: method}]};
    run.click(button, form);
    run.click(button, form);
    assert.equal(run.requests.length, 1);
    assert.equal(run.buttons.get(button).disabled, true);
    run.requests[0].error();
    assert.equal(run.hidden.length, 0);
    assert.equal(run.alerts.length, 1);
    run.requests[0].complete();
    assert.equal(run.buttons.get(button).disabled, false);
    run.click(button, form);
    assert.equal(run.requests.length, 2);
    run.requests[1].success();
    assert.deepEqual(run.hidden, [`modal${button[0].toUpperCase()}${button.slice(1)}`]);
  });
}

for (const success of [true, 'true', false, 'false', undefined]) {
  test(`tickler save checks success=${success} and prevents repeated pending saves`, () => {
    const run = controller();
    run.click('saveTicklerBtn', {});
    run.click('saveTicklerBtn', {});
    assert.equal(run.requests.length, 1);
    assert.equal(run.requests[0].dataType, 'json');
    run.requests[0].success({success});
    const saved = success === true || success === 'true';
    assert.deepEqual(run.hidden, saved ? ['assignTickler'] : []);
    assert.equal(run.alerts.length, saved ? 0 : 1);
    run.requests[0].complete();
    assert.equal(run.buttons.get('saveTicklerBtn').disabled, false);
  });
}

test('a failed tickler request leaves the dialog open and releases the pending guard', () => {
  const run = controller();
  run.click('saveTicklerBtn', {});
  run.requests[0].error({}, 'error', 'connection lost');
  run.requests[0].complete();
  assert.equal(run.hidden.length, 0);
  assert.match(run.alerts[0], /could not be confirmed/);
  assert.equal(run.buttons.get('saveTicklerBtn').disabled, false);
});

for (const [button, method] of cases) {
  test(`${button} keeps its window provider while preserving protected fields and selected patients`, () => {
    const a = controller(['101'], '/clinic', [], 'FAKE-provider-A');
    const b = controller(['102'], '/clinic', [], 'FAKE-provider-B');
    const form = {action: '/clinic/web/dashboard/display/BulkPatientAction',
      fields: [{name: 'method', value: method}, {name: 'CSRF-TOKEN', value: 'FAKE owned token'}]};
    b.click(button, form); a.click(button, form);
    for (const [run, provider, selected] of [[a, 'FAKE-provider-A', '101'], [b, 'FAKE-provider-B', '102']]) {
      assert.equal(new URL(run.requests[0].url).searchParams.get('dashboardProviderNo'), provider);
      assert.equal(new URL(run.requests[0].url).pathname, form.action);
      assert.deepEqual(JSON.parse(JSON.stringify(run.requests[0].data)),
        [...form.fields, {name: 'patientIds', value: selected}]);
    }
  });
}
