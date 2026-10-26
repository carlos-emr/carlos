/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { releaseChartLocks, closeBrowserWithChartCleanup } = require('./lib/chart-lock-cleanup');
const base = new URL('https://127.0.0.1/carlos');

test('cleanup never evaluates another application or sends its token to a redirect', async () => {
  const submitted = [];
  let disposed = false;
  const form = { method: 'releaseNoteLock', demographicNo: '17', noteId: '0', 'CSRF-TOKEN': 'fixture' };
  const context = {
    pages: () => [
      { isClosed: () => false, url: () => 'https://127.0.0.1/carlos-other/chart',
        evaluate: () => { throw new Error('Other application was evaluated'); } },
      { isClosed: () => false, url: () => 'https://127.0.0.1/carlos/CaseManagementEntry',
        evaluate: async () => form },
    ],
    request: { post: async (...args) => {
      submitted.push(args);
      return { status: () => 302, dispose: async () => { disposed = true; } };
    } },
  };
  await assert.rejects(releaseChartLocks(context, base), /HTTP 302/);
  assert.deepEqual(submitted, [['https://127.0.0.1/carlos/CaseManagementEntry', { form, maxRedirects: 0 }]]);
  assert.equal(disposed, true);
  assert.equal(Object.hasOwn(form, 'force'), false);
});

test('a failed release fails cleanup while still closing the browser', async () => {
  let closed = false;
  const browser = {
    contexts: () => [{ pages: () => { throw new Error('Release failed'); } }],
    close: async () => { closed = true; },
  };
  await assert.rejects(closeBrowserWithChartCleanup(browser, base), /Release failed/);
  assert.equal(closed, true);
});

test('final teardown releases its selected chart even while printing suppresses pagehide release', async () => {
  const vm = require('node:vm');
  let released = 0;
  const page = {
    isClosed: () => false,
    url: () => 'https://127.0.0.1/carlos/CaseManagementEntry',
    evaluate: async (fn, appRoot) => vm.runInNewContext(`(${fn.toString()})(appRoot)`, {
      appRoot, location: {href: 'https://127.0.0.1/carlos/CaseManagementEntry'},
      document: { forms: { caseManagementEntryForm: { elements: { noteId: { value: '0' } } } } },
      needToReleaseLock: false,
      demographicNo: '17', CarlosAjax: { getCsrfToken: () => 'fixture' },
    }),
  };
  const context = {
    pages: () => { throw new Error('Caller-owned pages must not be inspected'); },
    request: { post: async () => {
      released++;
      return { status: () => 200, dispose: async () => {} };
    } },
  };
  await releaseChartLocks(context, base, [page]);
  assert.equal(released, 1);
});

function navigatingChart({ failures = [new Error('Execution context was destroyed, most likely because of a navigation')], afterWait, waitError } = {}) {
  const vm = require('node:vm');
  const events = [], submitted = [];
  const state = { url: 'https://127.0.0.1/carlos/CaseManagementEntry', closed: false,
    patient: '17', note: '501', token: 'old-token', chart: true };
  const page = {
    isClosed: () => state.closed,
    url: () => state.url,
    async evaluate(fn, appRoot) {
      events.push('read chart');
      const failure = failures.shift();
      if (failure) {
        if (failure.closes) state.closed = true;
        throw failure.error || failure;
      }
      return structuredClone(vm.runInNewContext(`(${fn.toString()})(appRoot)`, {
        appRoot, location: {href: state.documentUrl || state.url},
        document: { forms: state.chart ? {caseManagementEntryForm: {elements: {noteId: {value: state.note}}}} : {} },
        demographicNo: state.patient, CarlosAjax: {getCsrfToken: () => state.token},
      }));
    },
    async waitForLoadState(event, options) {
      assert.equal(event, 'domcontentloaded');
      assert.deepEqual(options, {timeout: 20000});
      events.push('wait for document');
      if (afterWait) await afterWait(state);
      if (waitError) throw waitError;
    },
  };
  const context = { pages: () => [page], request: { post: async (url, options) => {
    events.push('release');
    submitted.push([url, options]);
    return {status: () => 200, dispose: async () => { events.push('dispose response'); }};
  } } };
  return {context, page, state, events, submitted};
}

test('a settled navigation rereads the new chart identity and releases only that lock', async () => {
  const f = navigatingChart({afterWait(state) {
    state.patient = '24'; state.note = '802'; state.token = 'new-token';
  }});

  await releaseChartLocks(f.context, base);

  assert.deepEqual(f.events, ['read chart', 'wait for document', 'read chart', 'release', 'dispose response']);
  assert.deepEqual(f.submitted, [['https://127.0.0.1/carlos/CaseManagementEntry', {
    form: {method: 'releaseNoteLock', demographicNo: '24', noteId: '802', 'CSRF-TOKEN': 'new-token'},
    maxRedirects: 0,
  }]]);
});

test('a navigation to a nonchart document does not release a stale chart lock', async () => {
  const f = navigatingChart({afterWait(state) { state.chart = false; }});
  await releaseChartLocks(f.context, base);
  assert.deepEqual(f.events, ['read chart', 'wait for document', 'read chart']);
  assert.deepEqual(f.submitted, []);
});

test('a navigation outside the application is skipped before its new document is evaluated', async () => {
  const f = navigatingChart({afterWait(state) { state.url = 'https://other.example/carlos/CaseManagementEntry'; }});
  await releaseChartLocks(f.context, base);
  assert.deepEqual(f.events, ['read chart', 'wait for document']);
  assert.deepEqual(f.submitted, []);
});

test('a page that closes while its navigation settles is skipped without posting stale identity', async () => {
  const f = navigatingChart({afterWait(state) { state.closed = true; }});
  await releaseChartLocks(f.context, base);
  assert.deepEqual(f.events, ['read chart', 'wait for document']);
  assert.deepEqual(f.submitted, []);
});

test('a recognized closed-page error is skipped only after the page is actually closed', async () => {
  const f = navigatingChart({failures: [{error: new Error('Target page, context or browser has been closed'), closes: true}]});
  await releaseChartLocks(f.context, base);
  assert.deepEqual(f.events, ['read chart']);
  assert.deepEqual(f.submitted, []);
});

test('a closed-page error during the settled-document wait is skipped only for a closed page', async () => {
  const f = navigatingChart({afterWait(state) { state.closed = true; },
    waitError: new Error('Target page, context or browser has been closed')});
  await releaseChartLocks(f.context, base);
  assert.deepEqual(f.events, ['read chart', 'wait for document']);
  assert.deepEqual(f.submitted, []);
});

test('an unrelated evaluate error propagates without a navigation retry or release', async () => {
  const error = new TypeError('Unexpected chart script failure');
  const f = navigatingChart({failures: [error]});
  await assert.rejects(releaseChartLocks(f.context, base), observed => observed === error);
  assert.deepEqual(f.events, ['read chart']);
  assert.deepEqual(f.submitted, []);
});

for (const message of ['Execution context was destroyed, most likely because of a navigation',
  'Cannot find context with specified id', 'Target page, context or browser has been closed']) {
  test(`a live page with persistent ${message} fails after one bounded wait and reread`, async () => {
    const error = new Error(message);
    const f = navigatingChart({failures: [error, error]});
    await assert.rejects(releaseChartLocks(f.context, base), observed => observed === error);
    assert.deepEqual(f.events, ['read chart', 'wait for document', 'read chart']);
    assert.deepEqual(f.submitted, []);
  });
}

test('a document which never reaches DOMContentLoaded fails without another read or release', async () => {
  const error = new Error('waitForLoadState timed out');
  const f = navigatingChart({waitError: error});
  await assert.rejects(releaseChartLocks(f.context, base), observed => observed === error);
  assert.deepEqual(f.events, ['read chart', 'wait for document']);
  assert.deepEqual(f.submitted, []);
});

test('an origin change between the URL check and callback execution cannot disclose or submit a form', async () => {
  const f = navigatingChart({failures: []});
  f.state.documentUrl = 'https://other.example/carlos/CaseManagementEntry';
  await releaseChartLocks(f.context, base);
  assert.deepEqual(f.events, ['read chart']);
  assert.deepEqual(f.submitted, []);
});

for (const [field, value] of [['patient', 'invalid'], ['note', 'invalid'], ['token', '']]) {
  test(`the reread still requires a valid ${field} before releasing any lock`, async () => {
    const f = navigatingChart({afterWait(state) { state[field] = value; }});
    await assert.rejects(releaseChartLocks(f.context, base), /requires the chart identity and CSRF token/);
    assert.deepEqual(f.events, ['read chart', 'wait for document', 'read chart']);
    assert.deepEqual(f.submitted, []);
  });
}
