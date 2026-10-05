/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/web/dashboard/display/drilldownDisplayController.js'), 'utf8');

/** Run the shipped controller and retain the handlers it installs on the page. */
function controller(selected = ['101', '102'], contextPath = '/clinic') {
  const handlers = new Map();
  const requests = [];
  const alerts = [];
  const document = {};
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
      serializeArray() { return selector.fields.map(field => ({ ...field })); },
    };
    return chain;
  }
  $.fn = { dataTableExt: { afnFiltering: [] }, dataTable: { moment() {}, ext: { order: {} } } };
  $.ajax = request => { requests.push(request); };
  vm.runInNewContext(source, { $, document, ctx: contextPath, alert: message => alerts.push(message), console });
  function click(id, form) {
    let prevented = false;
    const button = { id, form }; // Submit buttons deliberately have no href.
    if (id === 'assignTicklerChecked') button.href = `${contextPath}/web/dashboard/display/AssignTickler`;
    const handler = handlers.get(`#${id}`);
    assert.equal(typeof handler, 'function', `${id} has a registered handler`);
    handler.call(button, { preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
  }
  return { requests, alerts, click };
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
