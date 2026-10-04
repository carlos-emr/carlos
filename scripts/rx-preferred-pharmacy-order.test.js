/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/SelectPharmacy2.jsp', 'utf8');
const start = source.indexOf('$(".pharmacyItem").click(function () {');
const end = source.indexOf('$(".deletePharm").click', start);
assert.ok(start >= 0 && end > start);

function choose(existing, id, ready = true) {
  const requests = [], alerts = [];
  const entries = existing.map(pharmId => ({ pharmId }));
  let handler, success, failure, reloads = 0;
  const row = { click: fn => { handler = fn; }, attr() { return row; }, css() { return row; } };
  function $(selector) {
    if (selector === '.pharmacyItem') return row;
    if (selector === 'html, body') return { animate() {} };
    if (typeof selector === 'object') return { attr: name => selector[name] };
    // The empty list contains presentation divs that must not count as pharmacies.
    const rows = selector === '#preferredList div' ? (entries.length ? entries : [{}, {}]) : entries;
    assert.ok(['#preferredList div', '#preferredList > div[pharmId]'].includes(selector));
    return { length: rows.length, each(fn) { for (const entry of rows) if (fn.call(entry) === false) break; } };
  }
  $.post = (url, data, callback) => {
    requests.push(new URLSearchParams(data));
    success = callback;
    return { fail(fn) { failure = fn; return this; } };
  };
  vm.runInNewContext(source.slice(start, end).replace(/<%=.*?%>/g, '/carlos/rx/managePharmacy'),
    { $, demo: '123', preferredListReady: ready, ShowSpin() {}, HideSpin() {},
      alert: msg => alerts.push(msg), window: { location: { reload() { reloads++; } } } });
  const click = pharmId => handler.call({ pharmId });
  click(id);
  return { requests, alerts, click, succeed: data => success(data), fail: () => failure?.(), reloads: () => reloads };
}

test('the first pharmacy has order 1 despite the empty-list placeholder', () => {
  const { requests } = choose([], '11');
  assert.equal(requests.length, 1);
  assert.equal(requests[0].get('preferredOrder'), '1');
});

test('a second pharmacy is appended after the first', () => {
  assert.equal(choose(['11'], '22').requests[0].get('preferredOrder'), '2');
});

test('selecting an existing pharmacy alerts without changing its order', () => {
  const { requests, alerts } = choose(['11', '22'], '11');
  assert.equal(alerts.length, 1);
  assert.equal(requests.length, 0);
});

test('selection cannot write before the preferred list finishes loading', () => {
  assert.equal(choose([], '22', false).requests.length, 0);
});

test('rapid clicks cannot submit the same or another pharmacy until the list reloads', () => {
  const selection = choose([], '11');
  selection.click('11');
  selection.click('22');
  assert.equal(selection.requests.length, 1);
  selection.succeed({ id: 1 });
  assert.equal(selection.reloads(), 1);
  selection.click('11');
  selection.click('22');
  assert.equal(selection.requests.length, 1);
});

test('a rejected response restores selection for a retry', () => {
  const selection = choose([], '11');
  selection.succeed({});
  assert.equal(selection.alerts.length, 1);
  selection.click('11');
  assert.equal(selection.requests.length, 2);
});

test('a transport or JSON failure restores selection for a retry', () => {
  const selection = choose([], '11');
  selection.fail();
  selection.click('11');
  assert.equal(selection.requests.length, 2);
});
