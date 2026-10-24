/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
function setup(tab) {
  const calls = [];
  const measurements = [];
  const context = vm.createContext({
    ctx: '/carlos', demographicNo: '123', openEncounterInTab: tab,
    openWindows: {}, updateDivTimer: 1, self: {}, Event: {stop() {}},
    window: {open(...args) { calls.push(args); return {focus() {}}; }},
    registerMeasurementWindow(window) { measurements.push(window); },
    encodeURIComponent,
  });
  for (const name of ['popupPage', 'showHistory']) {
    const match = source.match(new RegExp('    function ' + name + '\\([^]*?\\n    }'));
    assert(match, name);
    vm.runInContext(match[0], context);
  }
  return {context, calls, measurements};
}
test('ordinary encounter links use tabs in tab mode and sized windows in popup mode', () => {
  for (const tab of [true, false]) {
    const {context, calls} = setup(tab);
    context.popupPage(500, 700, 'rx', '../rx/choosePatient?demographicNo=123');
    assert.equal(calls[0][0], '/carlos/rx/choosePatient?demographicNo=123');
    assert.equal(calls[0][1], tab ? '_blank' : 'rx');
    assert.equal(calls[0].length, tab ? 2 : 3);
  }
});
test('measurement pages remain registered opener windows under the tab preference', () => {
  for (const route of ['SetupMeasurements', 'ViewTemplateFlowSheet', 'ViewAddMeasurementData']) {
    const {context, calls, measurements} = setup(true);
    context.popupPage(500, 700, 'measurements', '../encounter/oscarMeasurements/' + route);
    assert.equal(calls[0][1], 'measurements');
    assert.equal(calls[0].length, 3);
    assert.equal(measurements.length, 1);
    assert.equal(measurements[0].opener, context.self);
  }
});
test('note history keeps the explicit patient and encoded note ID in both modes', () => {
  for (const tab of [true, false]) {
    const {context, calls} = setup(tab);
    context.showHistory('45&demographicNo=999', {});
    const url = new URL(calls[0][0], 'https://example.invalid');
    assert.equal(url.searchParams.get('demographicNo'), '123');
    assert.equal(url.searchParams.get('noteId'), '45&demographicNo=999');
    assert.equal(calls[0][1] === '_blank', tab);
  }
});
