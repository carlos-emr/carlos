/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Load the entire production controller; unrelated table/page initialization is inert.
const $ = () => ({ready() {}});
$.fn = {dataTableExt: {afnFiltering: []}, dataTable: {moment() {}, ext: {order: {}}}};
const context = vm.createContext({$, document: {}, Date});
vm.runInContext(fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/web/dashboard/display/drilldownDisplayController.js'), 'utf8'), context);

for (const value of ['31/12/2030', '12/11/2030', '2030-02-30', '2030-13-01', '2030-00-01',
  '2030-01-00', '2030-01-32', '2030-1-01', '2030', '2030-12', '2030-12-31junk',
  '2030-12-31T00:00:00Z', '0000-01-01', '1900-02-29', 'today', ' 2030-12-31', '']) {
  test(`Service Date rejects ${JSON.stringify(value)}`, () => {
    assert.equal(context.parseTicklerValue(value, 'Y-m-d'), undefined);
  });
}
for (const value of ['2030-12-31', '2028-02-29', '2000-02-29', '0099-01-01']) {
  test(`Service Date preserves ${value}`, () => {
    const date = context.parseTicklerValue(value, 'Y-m-d');
    const [year, month, day] = value.split('-').map(Number);
    assert.deepEqual([date.getFullYear(), date.getMonth() + 1, date.getDate()], [year, month, day]);
  });
}
for (const value of ['25:90', '00:30 AM', '13:30 PM', '10:60 AM', '10:30garbage', '10:30 AMjunk',
  '24:00', '10:30 AM ', 'today', '']) {
  test(`Service Time rejects ${JSON.stringify(value)}`, () => {
    assert.equal(context.parseTicklerValue(value, 'h:i K'), undefined);
  });
}
for (const [value, hour, minute] of [['14:30', 14, 30], ['10:30 am', 10, 30], ['2:30 Pm', 14, 30], ['00:00', 0, 0], ['23:59', 23, 59], ['12:00 AM', 0, 0], ['12:00 PM', 12, 0], ['10:30 AM', 10, 30], ['11:59 PM', 23, 59], ['1:30 PM', 13, 30], ['01:30 AM', 1, 30]]) {
  test(`Service Time preserves ${value}`, () => {
    const date = context.parseTicklerValue(value, 'h:i K');
    assert.deepEqual([date.getHours(), date.getMinutes()], [hour, minute]);
  });
}
