/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const {chartActionUrlSource} = require('./lib/chart-action-url-source');
const {NOTES_ROUTE, NOTE_SAVE_ROUTE, paginationParams} = require('./echart-notes-pagination-retry-playwright-checks');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');

test('pagination fault injection and save waits match real chart action URLs', () => {
  for (const chartContext of ['', 'chart context/5356']) {
    const context = vm.createContext({ctx: '/carlos', chartContext});
    vm.runInContext(chartActionUrlSource(source), context);
    for (const [action, matcher] of [['CaseManagementView', NOTES_ROUTE], ['CaseManagementEntry', NOTE_SAVE_ROUTE]]) {
      const url = `https://127.0.0.1${context.chartActionUrl(`/${action}`)}`;
      assert.ok(matcher.test(url), `${action} must match with chartContext=${chartContext}`);
      assert.ok(matcher.test(`${url}${chartContext ? '&' : '?'}method=save`));
      assert.ok(!matcher.test(`https://127.0.0.1/carlos/${action}Other`), 'a different action must not match');
      assert.ok(!matcher.test(`https://127.0.0.1/carlos/${action}/other`), 'a child path must not match');
    }
  }
});

test('fault injection selects only notes pagination POSTs', () => {
  const request = (method, body) => ({method: () => method, postData: () => body});
  assert.deepEqual(paginationParams(request('POST', 'method=viewNotesOpt&offset=20&numToReturn=20&demographicNo=1')),
    {offset: 20, numToReturn: 20});
  assert.equal(paginationParams(request('GET', 'method=viewNotesOpt&offset=20')), null);
  assert.equal(paginationParams(request('POST', 'method=listNotes&offset=20')), null);
  assert.equal(paginationParams(request('POST', null)), null);
});
