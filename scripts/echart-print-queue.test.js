/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
function functions(from, to) {
  const start = source.indexOf(`function ${from}(`);
  const end = source.indexOf(`function ${to}(`, start + 1);
  assert.ok(start >= 0 && end > start);
  return source.slice(start, end);
}
function setup(queue, maxNcId = 0) {
  const elements = {notes2print: {value: queue}, printStartDate: {value: '01-Oct-2026'},
    printEndDate: {value: '03-Oct-2026'}, printopDates: {checked: true},
    printopAll: {checked: true}, printopSelected: {checked: false}};
  for (const section of ['CPP', 'Rx', 'Labs', 'Preventions', 'Allergies']) {
    elements[`print${section}`] = {value: 'true'};
    elements[`imgPrint${section}`] = {src: 'green'};
  }
  for (const id of ['11', '12']) elements[`print${id}`] = {id: `print${id}`, src: 'green'};
  const context = vm.createContext({ctx: '/carlos', maxNcId,
    $: id => typeof id === 'string' ? elements[id] || null : id,
    $F: id => elements[id].value,
    Event: {stop() {}},
    document: {querySelectorAll: () => [elements.print11, elements.print12]},
  });
  vm.runInContext(functions('printInfo', 'togglePrint')
    + functions('removePrintQueue', 'printDateRange')
    + functions('clearAll', 'printAll'), context);
  return {context, elements};
}
test('Clear empties explicit queued IDs even when paging bookkeeping is exhausted', () => {
  const {context, elements} = setup('11,12');
  context.clearAll({});
  assert.equal(elements.notes2print.value, '');
  assert.equal(elements.print11.src, '/carlos/encounter/graphics/printer.png');
  assert.equal(elements.print12.src, '/carlos/encounter/graphics/printer.png');
});
test('Clear removes ALL_NOTES and resets every section, date and scope', () => {
  const {context, elements} = setup('ALL_NOTES');
  context.clearAll({});
  assert.equal(elements.notes2print.value, '');
  for (const section of ['CPP', 'Rx', 'Labs', 'Preventions', 'Allergies']) {
    assert.equal(elements[`print${section}`].value, 'false');
    assert.equal(elements[`imgPrint${section}`].src, '/carlos/encounter/graphics/printer.png');
  }
  assert.equal(elements.printStartDate.value, '');
  assert.equal(elements.printEndDate.value, '');
  assert.equal(elements.printopDates.checked, false);
  assert.equal(elements.printopAll.checked, false);
  assert.equal(elements.printopSelected.checked, true);
});
test('paging retains loaded container IDs through an empty final batch and resets on a fresh chart', () => {
  const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotesAjax.jsp'), 'utf8');
  const assignment = jsp.match(/maxNcId = .*;/)[0];
  const context = vm.createContext({maxNcId: 9});
  const batch = (offset, maxId) => vm.runInContext(assignment.replaceAll('<%=offset%>', offset).replaceAll('<%=maxId%>', maxId), context);
  batch(20, 2020);
  assert.equal(context.maxNcId, 2020);
  batch(40, 0);
  assert.equal(context.maxNcId, 2020);
  batch(0, 3);
  assert.equal(context.maxNcId, 3);
});
