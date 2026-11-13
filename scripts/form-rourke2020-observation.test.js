/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const claims = require('./lib/form-claims');
const check = require('./form-rourke2020-growth-playwright-checks');
const source = fs.readFileSync(path.join(__dirname, 'form-rourke2020-growth-playwright-checks.js'), 'utf8');

function request(submit, overrides = {}) {
  const data = new URLSearchParams({ form_class: 'Rourke2020', demographic_no: '41', ...overrides.data });
  if (submit === 'autosaveAjax') data.set('submit', submit);
  return {
    method: () => overrides.method || 'POST',
    url: () => overrides.url || `https://example.invalid/carlos/form/formname${submit === 'autosaveAjax' ? '' : `?submit=${submit}`}`,
    postData: () => data.toString(),
  };
}

function ledger(baseline = '0') {
  const context = new EventEmitter();
  const queries = [];
  // Deliberately provide no SQL write method: observing writes must remain read only.
  const sql = { value(query) { queries.push(query); return baseline; } };
  return { context, queries, state: check.trackRourkeWrites(context, sql, 41) };
}

// Execute the candidate's real final persistence assertion, so the regression must
// cover the observation window without changing exact equality to a lower bound.
const equality = source.match(/h\.assert\(after - entry\.before === entry\.writes, `Save wrote[^\n]+/)[0];
function checkRows(before, after, writes) {
  vm.runInNewContext(equality, { after, entry: { before, writes }, h: { assert: assert.ok } });
}

test('counts an autosave started before manual Save without accepting duplicate persistence', () => {
  const { context, state, queries } = ledger('3');
  context.emit('request', request('autosaveAjax'));
  // The old per-Save observer starts here and misses the already-started autosave.
  let lateRequests = 0;
  context.on('request', () => lateRequests++);
  context.emit('request', request('save'));
  assert.equal(lateRequests, 1);
  assert.equal(state.requests.size, 2);
  assert.equal(state.manual, 1);
  assert.throws(() => checkRows(3, 5, lateRequests), /Save wrote 2 rows for 1 requests/);
  assert.doesNotThrow(() => checkRows(state.before, 5, state.requests.size));
  // A genuine extra row for a single request remains a failure.
  assert.throws(() => checkRows(state.before, 6, state.requests.size), /Save wrote 3 rows for 2 requests/);
  assert.deepEqual(queries, ['SELECT COUNT(*) FROM formRourke2020 WHERE demographic_no=41']);
  state.stop();
});

test('observes only owned Rourke mutations and detaches on cleanup', () => {
  const { context, state } = ledger();
  for (const foreign of [
    request('save', { method: 'GET' }), request('print'), request('graph'),
    request('save', { data: { demographic_no: '42' } }),
    request('save', { data: { form_class: 'VT' } }),
    request('save', { url: 'https://example.invalid/carlos/form/print' }),
  ]) context.emit('request', foreign);
  assert.equal(state.requests.size, 0);
  const first = request('save');
  context.emit('request', first);
  context.emit('request', first);
  context.emit('request', request('autosaveAjax'));
  context.emit('request', request('exit'));
  assert.equal(state.requests.size, 3);
  assert.equal(state.manual, 2);
  state.stop();
  assert.equal(context.listenerCount('request'), 0);
  context.emit('request', request('save'));
  assert.equal(state.requests.size, 3);
});

test('rejects a missing or invalid row baseline before registering an observer', () => {
  for (const baseline of ['', null, undefined, 'not a count', '-1', '1.5', '9007199254740992']) {
    const context = new EventEmitter();
    assert.throws(() => check.trackRourkeWrites(context, { value: () => baseline }, 41), /baseline count is invalid/);
    assert.equal(context.listenerCount('request'), 0);
  }
});

test('an unselected graphgrowth fallback cannot turn an unrelated selected claim into partial coverage', () => {
  const fallback = source.slice(source.indexOf("  const rourkeEntry = byKey('rourke2020');"), source.indexOf('  // ---- Phase 3:'));
  assert.ok(fallback.includes('blocked(rourkeEntry'), 'execute the actual final fallback');
  for (const concern of ['measuredate', 'storage', 'printsex', 'graphgrowth']) {
    const blocked = [];
    vm.runInNewContext(fallback, {
      wanted: new Set([claims.claimKey('rourke2020', concern)]), claims,
      byKey: () => ({ form: { key: 'rourke2020' } }),
      blocked: (...args) => blocked.push(args),
    });
    assert.equal(blocked.length, concern === 'graphgrowth' ? 1 : 0, concern);
  }
});

test('observes before chart opening and preserves one manual request per click', () => {
  const observer = source.indexOf('trackRourkeWrites(s.context, sql, patient)');
  const chart = source.indexOf('const chart = await s.chart()');
  assert.ok(observer > 0 && observer < chart);
  assert.ok(source.includes('s.cleanup(rourkeWrites.stop)'));
  const manualCheck = source.match(/h\.assert\(rourkeWrites\.manual === manualBefore \+ 1,[^\n]+/)[0];
  for (const [before, after, pass] of [[0, 1, true], [1, 2, true], [0, 0, false], [0, 2, false]]) {
    const run = () => vm.runInNewContext(manualCheck, { rourkeWrites: { manual: after }, manualBefore: before, h: { assert: assert.ok } });
    if (pass) assert.doesNotThrow(run); else assert.throws(run, /exactly one manual Rourke write/);
  }
});
