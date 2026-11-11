/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');

function fixture({ owned = true, removed = true, childrenRemoved = true } = {}) {
  const events = [];
  return {
    events,
    browser: { async close() { events.push('close browser'); } },
    sql: {
      value(query) {
        if (query.includes('last_name=')) return owned ? '1' : '0';
        if (query.includes('casemgmt_note_lock')) return childrenRemoved ? '0' : '1';
        return removed ? '0' : '1';
      },
      execute(query) {
        if (query.startsWith('DELETE FROM demographic WHERE')) {
          events.push('delete patient');
        } else {
          for (const table of ['casemgmt_note_lock', 'casemgmt_tmpsave', 'measurementsDeleted']) {
            assert.ok(query.includes(`DELETE FROM ${table} WHERE`), `Missing owned ${table} cleanup`);
          }
          assert.ok(!query.includes('DELETE FROM demographic WHERE'), 'Parent deletion must follow child verification');
          events.push('delete chart support rows');
        }
      },
      dispose() { events.push('dispose credentials'); },
    },
    patient: '42', marker: 'FAKE-PW-owned', cleanups: [],
  };
}

test('cleanup closes sessions before deleting children and removes the parent last', async () => {
  const f = fixture();
  f.cleanups.push(() => f.events.push('child one'), () => f.events.push('child two'));
  await cleanupOwnedWorkflow(f);
  assert.deepEqual(f.events, ['close browser', 'child two', 'child one', 'delete chart support rows', 'delete patient', 'dispose credentials']);
});

test('lost patient ownership refuses all child and parent deletion', async () => {
  const f = fixture({ owned: false });
  f.cleanups.push(() => f.events.push('delete child'));
  await assert.rejects(cleanupOwnedWorkflow(f), /ownership changed/);
  assert.deepEqual(f.events, ['close browser', 'dispose credentials']);
});

test('failed child cleanup retains the patient, tries remaining cleanup, and fails the check', async () => {
  const f = fixture();
  f.cleanups.push(() => f.events.push('remaining child'), () => { throw new Error('child delete failed'); });
  await assert.rejects(cleanupOwnedWorkflow(f), /child delete failed/);
  assert.deepEqual(f.events, ['close browser', 'remaining child', 'dispose credentials']);
});

test('browser teardown failure still removes owned rows and reports the original error', async () => {
  const f = fixture();
  f.browser.close = async () => { throw new Error('browser teardown failed'); };
  f.cleanups.push(() => f.events.push('delete child'));
  await assert.rejects(cleanupOwnedWorkflow(f), /browser teardown failed/);
  assert.deepEqual(f.events, ['delete child', 'delete chart support rows', 'delete patient', 'dispose credentials']);
});

test('browser and child cleanup failures retain the parent and report both errors', async () => {
  const f = fixture();
  f.browser.close = async () => { throw new Error('browser teardown failed'); };
  f.cleanups.push(() => { throw new Error('child delete failed'); });
  await assert.rejects(cleanupOwnedWorkflow(f), /browser teardown failed; child delete failed/);
  assert.deepEqual(f.events, ['dispose credentials']);
});

test('a delete which silently changes no rows cannot report successful cleanup', async () => {
  const f = fixture({ removed: false });
  await assert.rejects(cleanupOwnedWorkflow(f), /owned patient was not removed/);
  assert.equal(f.events.at(-1), 'dispose credentials');
});

test('directory-only workflows clean up their rows without needing a patient', async () => {
  const f = fixture();
  f.patient = undefined;
  f.cleanups.push(() => f.events.push('directory rows'));
  await cleanupOwnedWorkflow(f);
  assert.deepEqual(f.events, ['close browser', 'directory rows', 'dispose credentials']);
});

test('silently retained chart support rows fail cleanup before deleting the patient', async () => {
  const f = fixture({ childrenRemoved: false });
  await assert.rejects(cleanupOwnedWorkflow(f), /chart support rows were not removed/);
  assert.deepEqual(f.events, ['close browser', 'delete chart support rows', 'dispose credentials']);
});

for (const contextOptions of [undefined, { locale: 'en-US' }]) {
  test(`workflow browser context retains ${contextOptions ? 'the requested locale' : 'the default locale'}`, async t => {
    const h = require('./lib/playwright-harness');
    const { runWorkflow } = require('./lib/workflow-session');
    let received;
    let closed = false;
    const context = { setDefaultTimeout() {}, on() {} };
    const browser = {
      async newContext(options) { received = options; return context; },
      async close() { closed = true; },
    };
    t.mock.method(h, 'readConfig', () => ({ testUser: 'fixture', mysql: {}, ignoreHTTPSErrors: true }));
    t.mock.method(h, 'createSqlRunner', () => ({ value: () => '999998', dispose() {} }));
    t.mock.method(h, 'launchBrowser', async () => browser);
    t.mock.method(h, 'login', async () => ({}));
    t.mock.method(h, 'runCheck', async ({ run, cleanup }) => {
      try { await run({ cancellation: { run: fn => fn() } }); }
      finally { await cleanup(); }
      return { outcome: 'PASS' };
    });
    const result = await runWorkflow('context-options', async session => {
      assert.equal(session.context, context);
    }, { openPatient: false, contextOptions });
    assert.equal(result.outcome, 'PASS');
    assert.deepEqual(received, { ...(contextOptions || {}), ignoreHTTPSErrors: true });
    assert.equal(closed, true);
  });
}

// Run the actual session module with browser entry points doubled, preserving its
// real result reporting and owned cleanup. No browser or database is needed.
async function runChartSession(t, { options, navbarError, navigationError } = {}) {
  const h = require('./lib/playwright-harness');
  const realRunCheck = h.runCheck;
  const f = fixture();
  const sqlValue = f.sql.value;
  f.sql.value = query => query.includes('FROM security') ? '999998'
    : query.includes('INSERT INTO demographic') ? '42' : sqlValue(query);
  const timeouts = { context: [], navigation: [], navbar: [] };
  const readiness = [];
  const context = { setDefaultTimeout(value) { timeouts.context.push(value); }, on() {} };
  f.browser.newContext = async () => context;
  const master = { url: () => 'http://fixture.test/demographic/edit?demographic_no=42' };
  const chart = { isClosed: () => false };
  const chartUi = {
    async openChart(receivedContext, receivedMaster, recorder, timeout) {
      assert.equal(receivedContext, context);
      assert.equal(receivedMaster, master);
      assert.ok(recorder);
      timeouts.navigation.push(timeout);
      readiness.push('navigation');
      if (navigationError) throw navigationError;
      return chart;
    },
    async waitForNavbars(receivedChart, timeout) {
      assert.equal(receivedChart, chart);
      timeouts.navbar.push(timeout);
      readiness.push('waiting for modules');
      if (navbarError) throw navbarError;
      await Promise.resolve();
      readiness.push('modules ready');
    },
  };
  const processRef = { exitCode: null, env: {}, on() {}, removeListener() {} };
  t.mock.method(h, 'readConfig', () => ({ testUser: 'fixture', mysql: {}, ignoreHTTPSErrors: true }));
  t.mock.method(h, 'createSqlRunner', () => f.sql);
  t.mock.method(h, 'launchBrowser', async () => f.browser);
  t.mock.method(h, 'login', async () => ({}));
  t.mock.method(h, 'runCheck', args => realRunCheck({ ...args, processRef, stdout: { log() {} } }));
  t.mock.method(console, 'log', () => {});
  t.mock.method(console, 'error', () => {});
  const sandbox = { module: { exports: {} }, console, URL, setTimeout, require(name) {
    if (name === 'node:crypto') return require(name);
    if (name === './playwright-harness') return h;
    if (name === './playwright-ui') return require('./lib/playwright-ui');
    if (name === '../master-record-tabs-playwright-checks') return {
      openMasterRecord: async () => ({ masterPage: master }),
    };
    if (name === '../echart-navbar-modules-playwright-checks') return chartUi;
    throw new Error(`Unexpected workflow dependency: ${name}`);
  } };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'lib/workflow-session.js'), 'utf8'), sandbox);
  let returned = false;
  const result = await sandbox.module.exports.runWorkflow('chart-timeout', async session => {
    await session.step('chart setup', async () => {
      assert.equal(await session.chart(options), chart);
      returned = true;
      readiness.push('chart returned');
      assert.equal(await session.chart(), chart, 'an already-ready chart must be reused');
    });
  });
  return { result, timeouts, readiness, returned, cleanupEvents: f.events };
}

for (const options of [undefined, { navbarTimeout: 90000 }]) {
  const label = options ? 'the requested module timeout' : 'the default module timeout';
  test(`chart uses ${label} while navigation and context remain at 20 seconds`, async t => {
    const actual = await runChartSession(t, { options });
    assert.equal(actual.result.outcome, 'PASS');
    assert.deepEqual(actual.timeouts, { context: [20000], navigation: [20000], navbar: [options?.navbarTimeout || 20000] });
    assert.deepEqual(actual.readiness, ['navigation', 'waiting for modules', 'modules ready', 'chart returned']);
    assert.equal(actual.returned, true);
    assert.deepEqual(actual.cleanupEvents, ['close browser', 'delete chart support rows', 'delete patient', 'dispose credentials']);
  });

  test(`chart propagates module failure with ${label} and still cleans its owned fixture`, async t => {
    const actual = await runChartSession(t, { options, navbarError: new Error('Navigation module failed: rules') });
    assert.equal(actual.result.outcome, 'FAIL');
    assert.equal(actual.result.detail, 'Navigation module failed: rules');
    assert.equal(actual.result.failedStep, 'chart setup');
    assert.equal(actual.result.cleanupFailed, undefined);
    assert.equal(actual.returned, false);
    assert.deepEqual(actual.timeouts, { context: [20000], navigation: [20000], navbar: [options?.navbarTimeout || 20000] });
    assert.deepEqual(actual.readiness, ['navigation', 'waiting for modules']);
    assert.deepEqual(actual.cleanupEvents, ['close browser', 'delete chart support rows', 'delete patient', 'dispose credentials']);
  });
}

test('a longer module timeout does not extend navigation or hide its failure', async t => {
  const actual = await runChartSession(t, { options: { navbarTimeout: 90000 }, navigationError: new Error('Chart navigation timed out') });
  assert.equal(actual.result.outcome, 'FAIL');
  assert.equal(actual.result.detail, 'Chart navigation timed out');
  assert.equal(actual.result.failedStep, 'chart setup');
  assert.equal(actual.returned, false);
  assert.deepEqual(actual.timeouts, { context: [20000], navigation: [20000], navbar: [] });
  assert.deepEqual(actual.cleanupEvents, ['close browser', 'delete chart support rows', 'delete patient', 'dispose credentials']);
});

/*
 * The runner compares a check's failing step with the manifest's expectedFailure.step, so step()
 * must hand the label of the step that threw to runCheck's result. Everything else in the
 * session is a double; runCheck is the real one with a process stand-in, so the exit code of
 * the test process is untouched.
 */
async function runStepped(t, workflow, { cleanupFails = false } = {}) {
  const h = require('./lib/playwright-harness');
  const { runWorkflow } = require('./lib/workflow-session');
  const realRunCheck = h.runCheck;
  const processRef = { exitCode: null, env: {}, on() {}, removeListener() {} };
  const browser = { async newContext() { return { setDefaultTimeout() {}, on() {} }; }, async close() {} };
  t.mock.method(h, 'readConfig', () => ({ testUser: 'fixture', mysql: {}, ignoreHTTPSErrors: true }));
  t.mock.method(h, 'createSqlRunner', () => ({ value: () => '999998', dispose() {} }));
  t.mock.method(h, 'launchBrowser', async () => browser);
  t.mock.method(h, 'login', async () => ({}));
  t.mock.method(h, 'runCheck', (options) => realRunCheck({ ...options, processRef, stdout: { log() {} } }));
  t.mock.method(console, 'log', () => {});
  t.mock.method(console, 'error', () => {});
  return runWorkflow('stepped-workflow', async (session) => {
    if (cleanupFails) session.cleanup(() => { throw new Error('DELETE failed'); });
    await workflow(session);
  }, { openPatient: false });
}

test('shouldRecordFailedStep_whenAStepThrows', async (t) => {
  const result = await runStepped(t, async (s) => {
    await s.step('first step', async () => {});
    await s.step('second step', async () => { throw new Error('boom'); });
    await s.step('third step', async () => { throw new Error('never reached'); });
  });
  assert.equal(result.outcome, 'FAIL');
  assert.equal(result.failedStep, 'second step');
});

test('shouldRecordTheInnermostStep_whenStepsNest', async (t) => {
  const result = await runStepped(t, async (s) => {
    await s.step('outer wrapper', async () => {
      await s.step('inner step', async () => { throw new Error('boom'); });
    });
  });
  assert.equal(result.failedStep, 'inner step');
});

test('shouldAttributeAStrictPageErrorToTheStepThatCausedIt', async (t) => {
  const result = await runStepped(t, async (s) => {
    await s.step('quiet step', async () => {});
    await s.step('noisy step', async () => { s.recorder.pageErrors.push({ label: 'page', text: 'ReferenceError: x is not defined' }); });
  });
  assert.equal(result.outcome, 'FAIL');
  assert.equal(result.failedStep, 'noisy step');
});

test('shouldOmitFailedStep_whenTheWorkflowFailsOutsideAnyStep', async (t) => {
  const result = await runStepped(t, async (s) => {
    await s.step('only step', async () => {});
    throw new Error('thrown between steps');
  });
  assert.equal(result.outcome, 'FAIL');
  assert.equal('failedStep' in result, false);
});

test('shouldRecordNoFailedStep_whenEveryStepPasses', async (t) => {
  const result = await runStepped(t, async (s) => { await s.step('only step', async () => {}); });
  assert.equal(result.outcome, 'PASS');
  assert.equal('failedStep' in result, false);
});

test('shouldFlagCleanupFailure_whenOwnedCleanupFailsAfterAStepFailure', async (t) => {
  const result = await runStepped(t, async (s) => {
    await s.step('the known step', async () => { throw new Error('boom'); });
  }, { cleanupFails: true });
  assert.equal(result.outcome, 'FAIL');
  assert.equal(result.failedStep, 'the known step');
  assert.equal(result.cleanupFailed, true);
});
