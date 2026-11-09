/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, 'tickler-crud-playwright-checks.js'), 'utf8');
function section(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert(from >= 0 && to > from, 'Tickler synchronization helpers are missing');
  return source.slice(from, to);
}
const searchSource = section('async function searchTicklerList(', 'async function openDemoTicklerList(');
const refreshSource = section('async function waitForTicklerDraw(', 'async function setTicklerListStatus(');
const statusSource = section('async function waitForTicklerStatus(', 'function getCommentRows(');
const createSource = section('async function createTickler(', 'async function editTicklerFromList(');
const editSource = section('async function editTicklerFromList(', 'async function completeTicklerViaEdit(');
const completeSource = section('async function completeTicklerViaEdit(', 'async function deleteTicklerFromList(');

for (const commitDelay of [0, 1250]) {
  test(`tickler creation waits for persistence ${commitDelay}ms after iframe acknowledgement`, async () => {
    let now = 0, acknowledged = false, closedAt = null;
    const row = { id: '42', status: 'A', priority: 'High', serviceDate: '2026-02-18', assignee: '999998' };
    const field = {
      async waitFor() {}, async fill() {}, async selectOption() {},
      first() { return this; }, async click() {},
    };
    const page = {
      locator: () => field,
      async waitForFunction() { acknowledged = true; },
      async waitForTimeout(delay) { now += delay; },
      async close() {
        assert(acknowledged);
        closedAt = now;
      },
    };
    const sandbox = {
      assert: (condition, message) => assert(condition, message),
      Date: { now: () => now },
      setTimeout(resolve, delay) { now += delay; resolve(); },
      getTicklerRows() {
        assert(acknowledged, 'Persistence must be checked after the save acknowledgement');
        return now >= commitDelay ? [row] : [];
      },
      wirePage() {}, async gotoApp() {}, async selectTicklerAssignee() {},
      demographicNo: '1', providerNo: row.assignee, createdMessage: 'owned tickler',
    };
    vm.runInNewContext(statusSource + createSource + '\nthis.create = createTickler;', sandbox);
    assert.equal(await sandbox.create({ newPage: async () => page }), row.id);
    assert(closedAt !== null && closedAt >= commitDelay, 'Page closed before the created row was committed');
  });
}

test('tickler persistence polling rejects a missing, duplicate, or wrong-status row', async () => {
  for (const rows of [[], [{status: 'C'}], [{status: 'A'}, {status: 'A'}]]) {
    let now = 0;
    const sandbox = {
      Date: { now: () => now },
      setTimeout(resolve, delay) { now += delay; resolve(); },
      getTicklerRows: () => rows,
    };
    vm.runInNewContext(statusSource + '\nthis.wait = waitForTicklerStatus;', sandbox);
    await assert.rejects(sandbox.wait('A', 1000), /tickler did not reach status A/);
  }
});

function searchFixture(found = true) {
  const events = [], requests = [], scheduled = [];
  let input = '', search = '', rows = [];
  const settings = {bDrawing: false, jqXHR: {readyState: 4}};
  const table = {
    search: () => search,
    settings: () => [settings],
    rows: () => ({data: () => ({toArray: () => rows})}),
  };
  const window = {jQuery: () => ({DataTable: () => table})};
  const document = {
    querySelector(selector) {
      assert.equal(selector, '#ticklerResults_filter input[type="search"]');
      return {value: input};
    },
  };
  const evaluate = (callback, argument) => vm.runInNewContext('(' + callback.toString() + ')(argument)',
    {window, document, argument});
  const page = {
    locator(selector) {
      assert.equal(selector, '#ticklerResults_filter input[type="search"]');
      return {
        inputValue: async () => input,
        async fill(value) {
          input = value;
          events.push(['input', value]);
          // Only input schedules a draw. The polling stub merely observes it.
          scheduled.push(() => {
            search = value;
            settings.jqXHR = {readyState: 1};
            requests.push(value);
          }, () => {
            settings.jqXHR.readyState = 4;
            settings.bDrawing = true;
          }, () => {
            rows = found && value ? [{message: value}] : [];
            settings.bDrawing = false;
            events.push(['drawn', value]);
          });
        },
      };
    },
    async waitForFunction(callback, argument) {
      while (!evaluate(callback, argument)) {
        assert(scheduled.length, 'Predicate did not settle after all scheduled input/draw work');
        await scheduled.shift()();
      }
    },
    async evaluate(callback) { return evaluate(callback); },
  };
  const sandbox = {};
  vm.runInNewContext(searchSource + '\nthis.probe = probeTicklerSearch; this.search = searchTicklerList;', sandbox);
  return {page, events, requests, probe: sandbox.probe, search: sandbox.search};
}

for (const found of [true, false]) {
  test('tickler search waits for native search and reset draws with ' + (found ? 'matching' : 'missing') + ' rows', async () => {
    const fixture = searchFixture(found);
    assert.equal(await fixture.probe(fixture.page, 'owned tickler'), found);
    assert.deepEqual(fixture.events, [
      ['input', 'owned tickler'], ['drawn', 'owned tickler'], ['input', ''], ['drawn', ''],
    ]);
    assert.deepEqual(fixture.requests, ['owned tickler', '']);
  });
}

test('an already settled search does not send another list request', async () => {
  const fixture = searchFixture();
  await fixture.search(fixture.page, '');
  assert.deepEqual(fixture.events, []);
  assert.deepEqual(fixture.requests, []);
});

function refreshFixture(status = 200, bodyFailure = null) {
  const events = [];
  let deliver, refuse;
  let finished = false;
  const response = {
    request: () => ({method: () => 'GET'}),
    url: () => 'https://fixture.invalid/carlos/tickler/ListTicklers',
    status: () => status,
    async finished() { events.push('body'); finished = true; return bodyFailure; },
  };
  const settings = {bInitialised: true, bDrawing: false, jqXHR: {readyState: 4}};
  const table = {settings: () => [settings]};
  const jQuery = () => ({DataTable: () => table});
  jQuery.fn = {DataTable: {isDataTable: () => true}};
  const page = {
    waitForResponse(predicate) {
      assert(predicate(response));
      assert.equal(predicate({...response, request: () => ({method: () => 'POST'})}), false);
      assert.equal(predicate({...response, url: () => 'https://fixture.invalid/carlos/Other'}), false);
      events.push('listener');
      return new Promise((resolve, reject) => { deliver = resolve; refuse = reject; });
    },
    locator() {
      return {async waitFor() { assert(finished, 'List readiness must follow the complete response body'); }};
    },
    async waitForFunction(callback, argument) {
      const evaluate = () => vm.runInNewContext('(' + callback.toString() + ')(argument)',
        {window: {jQuery}, argument});
      assert(evaluate());
      // The draw wait must reject both an unfinished request and unfinished rendering.
      if (callback.toString().includes('settings.jqXHR')) {
        settings.jqXHR.readyState = 3;
        assert.equal(evaluate(), false);
        settings.jqXHR.readyState = 4;
        settings.bDrawing = true;
        assert.equal(evaluate(), false);
        settings.bDrawing = false;
        assert(evaluate());
        events.push('drawn');
      }
    },
  };
  const sandbox = {assert, URL};
  vm.runInNewContext(refreshSource + '\nthis.refresh = refreshTicklerListAfter;', sandbox);
  return {
    events, page, refresh: sandbox.refresh,
    async save() { events.push('save'); deliver(response); },
    failRefresh(message) { refuse(new Error(message)); },
  };
}

for (const action of ['edit', 'complete']) {
  test(`the ${action} popup stays alive when its broadcast follows the iframe acknowledgement`, async () => {
    const fixture = refreshFixture();
    let acknowledged = false, closed = false;
    const field = {
      async waitFor() {}, async fill() {}, async selectOption() {},
      async click() {
        // The sentinel can be read before the iframe load handler gets its turn.
        setImmediate(() => { if (!closed) fixture.save(); });
      },
    };
    const popup = {
      locator: () => field,
      async waitForLoadState() {},
      async waitForURL(predicate) {
        assert(predicate(new URL('https://fixture.invalid/carlos/tickler/ViewTicklerEdit?tickler_no=42')));
      },
      async waitForFunction() { acknowledged = true; },
      // A delay may expire while the broadcast is still queued; elapsed time is
      // not evidence that the list received it.
      async waitForTimeout() {},
      async close() {
        assert(acknowledged);
        closed = true;
        if (!fixture.events.includes('drawn')) fixture.failRefresh('popup closed before its broadcast refreshed the list');
        fixture.events.push('closed');
      },
    };
    const listLocator = fixture.page.locator;
    fixture.page.locator = selector => {
      if (selector !== '#ticklerResults tbody tr') return listLocator(selector);
      const row = { filter: () => row, first: () => row, locator: () => ({async click() {}}) };
      return row;
    };
    const sandbox = {
      assert, URL, wirePage() {}, async findRowInList() {},
      getTicklerRows: () => [{id: '42', status: 'C', priority: 'Low', serviceDate: '2026-02-19'}],
      getCommentRows: () => ['edited task'], createdMessage: 'owned task', editedMessage: 'edited task',
    };
    vm.runInNewContext(refreshSource + editSource + completeSource
      + '\nthis.edit = editTicklerFromList; this.complete = completeTicklerViaEdit;', sandbox);
    await sandbox[action]({waitForEvent: async () => popup}, fixture.page, 42);
    assert.deepEqual(fixture.events, ['listener', 'save', 'body', 'drawn', 'closed']);
  });
}

test('tickler edit observes its own broadcast refresh before saving and waits through its draw', async () => {
  const fixture = refreshFixture();
  await fixture.refresh(fixture.page, fixture.save);
  assert.deepEqual(fixture.events, ['listener', 'save', 'body', 'drawn']);
});

test('tickler synchronization rejects redirects, HTTP errors, and aborted bodies', async () => {
  for (const [status, failure, message] of [[302, null, /HTTP 302/], [500, null, /HTTP 500/],
    [200, new Error('aborted'), /did not finish/]]) {
    const fixture = refreshFixture(status, failure);
    await assert.rejects(fixture.refresh(fixture.page, fixture.save), message);
    assert(!fixture.events.includes('drawn'));
  }
});

test('tickler synchronization preserves save failures', async () => {
  const fixture = refreshFixture();
  await assert.rejects(fixture.refresh(fixture.page, async () => { throw new Error('save failed'); }), /save failed/);
});
