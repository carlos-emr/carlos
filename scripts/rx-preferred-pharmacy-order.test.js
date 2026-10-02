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
  const selected = { pharmId: id };
  const entries = existing.map(pharmId => ({ pharmId }));
  function $(selector) {
    if (selector === '.pharmacyItem') return { click: handler => handler.call(selected) };
    if (typeof selector === 'object') return { attr: name => selector[name] };
    // The empty list contains presentation divs that must not count as pharmacies.
    const rows = selector === '#preferredList div' ? (entries.length ? entries : [{}, {}]) : entries;
    assert.ok(['#preferredList div', '#preferredList > div[pharmId]'].includes(selector));
    return { length: rows.length, each(fn) { for (const row of rows) if (fn.call(row) === false) break; } };
  }
  $.post = (url, data) => requests.push(new URLSearchParams(data));
  vm.runInNewContext(source.slice(start, end).replace(/<%=.*?%>/g, '/carlos/rx/managePharmacy'),
    { $, demo: '123', preferredListReady: ready, ShowSpin() {}, alert: msg => alerts.push(msg) });
  return { requests, alerts };
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
