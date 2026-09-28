/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp', 'utf8');
const start = source.indexOf('    function useFav2(favoriteId)');
const end = source.indexOf('    function calculateRxData(', start);
assert.ok(start > 0 && end > start);

function harness() {
  let request; const alerts = []; let renders = 0;
  const context = { ctx: '/carlos', skipParseInstr: false, jsMsg: { favoriteLoadFailed: 'Favorite unavailable' },
    alert: message => alerts.push(message), renderRxStage: () => renders++,
    CarlosAjax: { updater: (target, url, options) => { request = { target, url, options }; } } };
  vm.runInNewContext(source.slice(start, end), context);
  context.useFav2(42);
  return { context, alerts, get request() { return request; }, get renders() { return renders; } };
}

for (const status of [0, 400, 403, 404, 409, 500]) {
  test(`favorite HTTP ${status} shows an error and leaves staged markup and rendering untouched`, () => {
    const h = harness();
    assert.equal(h.request.target.success, 'rxText');
    assert.equal(h.request.target.failure, undefined, 'error responses must not replace prescription markup');
    h.request.options.onFailure({ status });
    h.request.options.onComplete({ status });
    assert.deepEqual(h.alerts, ['Favorite unavailable']);
    assert.equal(h.renders, 0);
    assert.equal(h.context.skipParseInstr, false);
  });
}

test('favorite success renders only after the updater installs its returned markup', () => {
  const h = harness();
  assert.equal(h.request.options.onSuccess, undefined);
  assert.equal(h.renders, 0);
  h.request.options.onComplete({ status: 200 });
  assert.equal(h.renders, 1);
  assert.equal(h.context.skipParseInstr, true);
  assert.deepEqual(h.alerts, []);
});

const { consumeExpectedFavoriteFailure } = require('./rx-legacy-null-fields-playwright-checks');
const h = require('./lib/playwright-harness');
const failureUrl = 'https://127.0.0.1/carlos/rx/useFavorite';
function expectedFailure() {
  const recorder = h.createRecorder();
  recorder.badResponses.push({ url: failureUrl, status: 404, method: 'POST' });
  recorder.consoleIssues.push({ location: { url: failureUrl }, type: 'error', text: 'Failed to load resource: 404 (Not Found)' });
  return recorder;
}

test('the deliberate missing-favorite response excludes only its exact 404 signals', () => {
  const recorder = expectedFailure();
  consumeExpectedFavoriteFailure(recorder, failureUrl, 0, 0);
  assert.equal(recorder.badResponses.length, 0);
  assert.equal(recorder.consoleIssues.length, 0);
});

test('the deliberate save failure excludes its asserted500 but keeps an unrelated404', () => {
  const recorder = h.createRecorder();
  const saveUrl = 'https://127.0.0.1/carlos/rx/updateFavorite2?method=ajaxEditFavorite';
  recorder.badResponses.push({ url: saveUrl, status: 500, method: 'POST' });
  recorder.consoleIssues.push({ location: { url: saveUrl }, type: 'error', text: 'Failed to load resource: 500 (Internal Server Error)' });
  consumeExpectedFavoriteFailure(recorder, saveUrl, 0, 0, 500);
  assert.equal(recorder.badResponses.length, 0);
  recorder.badResponses.push({ url: saveUrl, status: 404, method: 'POST' });
  assert.throws(() => h.assertStrictPage(recorder), /HTTP 404/);
});

test('unrelated console, HTTP and runtime errors remain failures during the negative control', () => {
  for (const kind of ['http', 'console', 'runtime']) {
    const recorder = expectedFailure();
    consumeExpectedFavoriteFailure(recorder, failureUrl, 0, 0);
    if (kind === 'http') recorder.badResponses.push({ url: failureUrl, status: 500, method: 'POST' });
    if (kind === 'console') recorder.consoleIssues.push({ location: { url: failureUrl }, type: 'error', text: 'Unexpected failure' });
    if (kind === 'runtime') recorder.pageErrors.push({ label: 'favorite', text: 'Uncaught ReferenceError' });
    const expected = { http: /HTTP 500/, console: /console error: Unexpected failure/,
      runtime: /uncaught Uncaught ReferenceError/ };
    assert.throws(() => h.assertStrictPage(recorder), expected[kind]);
  }
});
