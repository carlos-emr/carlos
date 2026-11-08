/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const support = require('./lib/concurrency-support');

// Exercise the workflow's real assertions and diagnostics consumption with deterministic browser
// and database doubles. Negative cases must fail even when B's original text is still present.
async function run({ status = 409, extraResponse, extraConsole, revision = false,
  changedText = false, changedIdentity = false, missingB = false, persistedStale = false, kept = true,
  responseUrl, lockMessage = 'The note lock was lost',
  alerts = [{ type: 'alert', text: lockMessage }] } = {}) {
  const recorder = h.createRecorder();
  const marker = 'FAKE-PW-takeover';
  const textB = `${marker} note typed in the window that took the lock`;
  let staleSaved = false;
  let bSaved = false;
  const url = 'http://127.0.0.1:8080/carlos/CaseManagementEntry';
  const response = (code, destination = url) => ({ status: () => code, url: () => destination,
    request: () => ({ method: () => 'POST', postData: () => 'method=save' }) });
  const bad = { status: 409, method: 'POST', url, resourceType: 'xhr' };
  const resourceError = { text: 'Failed to load resource: the server responded with a status of 409 ()', location: { url } };
  const chart = stale => {
    let text = '';
    return {
      async waitForResponse(predicate) {
        const result = response(stale ? status : 200, stale ? responseUrl : undefined);
        assert.equal(predicate(result), true);
        return result;
      },
      async waitForTimeout() {},
      async evaluate() { return lockMessage; },
      on() {}, off() {},
      locator(selector) {
        if (selector === 'input[name="CSRF-TOKEN"]') return { first() { return this; }, async inputValue() { return 'owned-csrf-token'; } };
        if (selector === 'body') return { async innerText() { return 'E-Chart'; } };
        if (selector === '#saveImg') return { first() { return this; }, async click() {
          if (!stale) { bSaved = !missingB; return; }
          staleSaved = true;
          if (status >= 400) recorder.badResponses.push({ ...bad, status });
          if (status === 409) recorder.consoleIssues.push(resourceError);
          if (extraResponse) recorder.badResponses.push(extraResponse);
          if (extraConsole) recorder.consoleIssues.push(extraConsole);
        } };
        return { first() { return this; }, async waitFor() {}, async click() {}, async fill(value) { text = value; },
          async count() { return staleSaved && !kept ? 0 : 1; }, async inputValue() { return text; } };
      },
    };
  };
  const aChart = chart(true), bChart = chart(false);
  const sandbox = { module: { exports: {} }, URLSearchParams, URL,
    require(id) {
      if (id === './lib/playwright-harness') return { ...h,
        async withExpectedDialogs(page, body) { await body(); return alerts; } };
      if (id === './lib/workflow-session') return { async expectValue(sql, query, expected, message) {
        assert.equal(sql.value(query), expected, message);
      } };
      if (id === './lib/concurrency-support') return { ...support, async openSecondSession(s, options) {
        await options.dialogHandler({ type: () => 'confirm',
          message: () => 'You have started to edit this note in another window at 127.0.0.1.\nDo you wish to continue?',
          async accept() {} }, {});
        return {};
      } };
      if (id === './echart-navbar-modules-playwright-checks') return {
        async openChart() { return bChart; }, async waitForNavbars() {},
      };
      throw new Error(`Unexpected import: ${id}`);
    },
  };
  vm.runInNewContext(fs.readFileSync(require.resolve('./concurrency-chart-note-takeover-playwright-checks'), 'utf8'), sandbox);
  const steps = [];
  await sandbox.module.exports.workflow({ patient: '42', marker, recorder,
    config: { baseUrl: new URL('http://127.0.0.1:8080/carlos') },
    context: { request: { async post(url, options) {
      assert.equal(url, 'http://127.0.0.1:8080/carlos/CaseManagementEntry');
      assert.equal(options.form.method, 'save'); assert.equal(options.form.demographicNo, '42');
      assert.equal(options.headers['CSRF-TOKEN'], 'owned-csrf-token');
      const escaped = options.form.caseNote_note.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      return { status: () => 200, text: async () => `exclusive note lock<textarea>${escaped}</textarea>`,
        headers: () => ({ 'cache-control': 'no-store' }), async dispose() {} };
    } } },
    sql: {
      value(query) {
        if (query.includes('casemgmt_note_lock')) return '1';
        if (query.includes(h.sqlString(`${marker} note typed in the stale window`)) || query.includes(h.sqlString(`${marker} note typed in the stale window <b>&`))) return staleSaved && persistedStale ? '1' : '0';
        if (query.includes(h.sqlString(textB))) return bSaved ? '1' : '0';
        throw new Error(`Unexpected SQL count: ${query}`);
      },
      rows(query, parameters) {
        assert.match(query, /SELECT n\.note_id, n\.uuid, n\.note,/);
        assert.match(query, /WHERE n\.demographic_no=\? ORDER BY n\.note_id/);
        assert.deepEqual(Array.from(parameters), ['42']);
        if (!bSaved) return [];
        const rows = [[staleSaved && changedIdentity ? '103' : '101', 'note-uuid', staleSaved && changedText ? 'changed' : textB, staleSaved && revision ? '2' : '1']];
        if (staleSaved && revision) rows.push(['102', 'note-uuid', textB, '2']);
        return rows;
      },
    },
    cleanup() {}, async chart() { return aChart; },
    async step(label, body) { await body(); h.assertStrictPage(recorder); steps.push(label); },
  });
  assert.equal(steps.length, 6, 'The refusal must reach persistence, editor, Sign & Save and native refusal-page assertions');
  h.assertStrictPage(recorder);
}

test('one expected save 409 and its console error reach the note and editor assertions', async () => {
  await run();
});

test('localized save refusal feedback is recognized by its HTTP status', async () => {
  await run({ lockMessage: 'Le verrou de la note a été perdu', alerts: [{ type: 'alert', text: 'Le verrou de la note a été perdu' }] });
});

for (const status of [200, 403, 500]) {
  test(`an unexpected HTTP ${status} is still fatal`, async () => {
    await assert.rejects(run({ status }), /expected 409/);
  });
}

test('a second 409 is not consumed', async () => {
  await assert.rejects(run({ extraResponse: { status: 409, method: 'POST', url: 'http://127.0.0.1:8080/carlos/CaseManagementEntry' } }), /exactly one/);
});

test('an unrelated HTTP failure is not consumed', async () => {
  await assert.rejects(run({ extraResponse: { status: 404, method: 'GET', url: 'http://127.0.0.1/missing' } }), /outside the configured application endpoint/);
});

test('a save response from another origin is rejected', async () => {
  await assert.rejects(run({ responseUrl: 'https://other.invalid/carlos/CaseManagementEntry' }), /left the configured application endpoint/);
});

test('an extra failure from another origin is not consumed', async () => {
  await assert.rejects(run({ extraResponse: { status: 409, method: 'POST', url: 'https://other.invalid/carlos/CaseManagementEntry' } }), /outside the configured application endpoint/);
});

test('an extra matching resource console error is still fatal', async () => {
  await assert.rejects(run({ extraConsole: { text: 'Failed to load resource: the server responded with a status of 409 ()',
    location: { url: 'http://127.0.0.1:8080/carlos/CaseManagementEntry' } } }), /more than one matching resource-console error/);
});

test('an unrelated resource console error is still fatal', async () => {
  await assert.rejects(run({ extraConsole: { text: 'Failed to load resource: the server responded with a status of 409 ()',
    location: { url: 'http://127.0.0.1/another-resource' } } }), /JavaScript-layer problem/);
});

test('an extra revision preserving B\'s text fails the persistence assertion', async () => {
  await assert.rejects(run({ revision: true }), /note text or revision/);
});

test('a changed saved note fails the persistence assertion', async () => {
  await assert.rejects(run({ changedText: true }), /note text or revision/);
});

test('a changed note identity fails even with unchanged text', async () => {
  await assert.rejects(run({ changedIdentity: true }), /note text or revision/);
});

test('session B must actually persist its note before the stale save', async () => {
  await assert.rejects(run({ missingB: true }), /note did not reach casemgmt_note/);
});

test('persisting the stale editor text fails even if B\'s note is unchanged', async () => {
  await assert.rejects(run({ persistedStale: true }), /text was written to the chart/);
});

test('losing the editor after refusal still fails', async () => {
  await assert.rejects(run({ kept: false }), /lost the editor/);
});

test('silently refusing the save still fails', async () => {
  await assert.rejects(run({ alerts: [] }), /must warn exactly once/);
});
