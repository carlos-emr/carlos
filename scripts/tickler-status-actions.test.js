/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/tickler/ticklerMain.jsp'), 'utf8');
const start = jsp.indexOf('let ticklerResultsTable;');
const end = jsp.indexOf('function openTicklerEdit(', start);
assert.ok(start >= 0 && end > start, 'The task-list initialization and action controls must be exercised');

// Execute the page's real table setup, filter listener, request/error callbacks and draw callback.
// The table double lets a request remain pending or fail while old, checked rows remain displayed.
function taskList(initialStatus) {
  const actions = [{ hidden: true }, { hidden: true }];
  const listeners = {};
  const status = { value: initialStatus, addEventListener: (event, fn) => { listeners[event] = fn; } };
  const settings = { iDraw: 0 };
  let options;
  let ready;
  let latest;
  const table = { ajax: { reload: request } };
  const api = {
    rows: () => ({ nodes: () => [], data: () => ({ each() {} }) }),
    page: { len: () => 50 },
  };
  const chain = { dialog() {}, off() { return chain; }, on() { return chain; }, prepend() {} };
  function request() {
    latest = { draw: ++settings.iDraw };
    options.ajax.data(latest);
    settings.oAjaxData = latest;
    return latest;
  }
  const jQuery = selector => selector === '.status-action-live'
    ? { each: fn => actions.forEach(action => fn.call(action)) }
    : selector === '#ticklerResults'
      ? { DataTable(config) { options = config; request(); return table; } }
      : chain;
  vm.runInNewContext(jsp.slice(start, end), {
    ctx: '/carlos', jQuery, window: {},
    document: {
      addEventListener: (event, fn) => { assert.equal(event, 'DOMContentLoaded'); ready = fn; },
      getElementById: id => id === 'ticklerview' ? status : null,
      querySelector: () => ({ value: '0' }),
    },
    localStorage: { getItem: () => null, setItem() {} },
    console: { error() {} },
    i18nAllLabs: 'All', i18nEditTickler: 'Edit', i18nAddNote: 'Note', i18nErrorLoadFailed: 'Failed',
  });
  ready();
  return {
    hidden: () => actions.every(action => action.hidden),
    visible: () => actions.every(action => !action.hidden),
    pending: () => latest,
    change(value) { status.value = value; listeners.change(); return latest; },
    reload: request,
    // DataTables calls drawCallback after accepting and rendering a server response.
    draw(response = latest, extra = {}) {
      settings.json = { draw: response.draw, data: [], ...extra };
      options.drawCallback.call({ api: () => api }, settings);
    },
    fail() { options.ajax.error({ status: 503 }, 'error', 'Service Unavailable'); },
  };
}

for (const status of ['A', 'C']) {
  test(`initial ${status} actions wait for the first successful draw`, () => {
    const list = taskList(status);
    assert.ok(list.hidden(), 'Actions must be hidden while the first request is pending');
    list.draw();
    assert.ok(list.visible());
  });

  test(`Deleted to ${status} cannot act on retained rows during a delayed or failed reload`, () => {
    const list = taskList('D');
    list.draw();
    const pending = list.change(status);
    assert.ok(list.hidden(), 'Checked Deleted rows remain displayed until the response draws');
    list.fail();
    assert.ok(list.hidden(), 'A failed request leaves the old Deleted rows displayed');
    list.reload();
    list.draw(pending);
    assert.ok(list.hidden(), 'An earlier response must not reveal actions during the retry');
    list.draw();
    assert.ok(list.visible());
  });
}

test('Deleted keeps actions hidden after its successful draw', () => {
  const list = taskList('A');
  list.draw();
  list.change('D');
  assert.ok(list.hidden());
  list.draw();
  assert.ok(list.hidden());
});

test('rapid status changes only reveal actions for the latest request', () => {
  const list = taskList('D');
  list.draw();
  const earlier = list.change('A');
  list.change('C');
  list.draw(earlier);
  assert.ok(list.hidden());
  list.draw();
  assert.ok(list.visible());
});

test('search, paging and popup reloads also hide actions until a successful draw', () => {
  const list = taskList('A');
  list.draw();
  list.reload();
  assert.ok(list.hidden());
  list.fail();
  assert.ok(list.hidden());
  list.reload();
  list.draw();
  assert.ok(list.visible());
});

test('an error payload cannot reveal actions even when its draw matches', () => {
  const list = taskList('A');
  list.draw(list.pending(), { error: 'Unable to load tasks' });
  assert.ok(list.hidden());
});
