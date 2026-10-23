/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./lib/playwright-harness');
const { preflight, workflow } = require('./rx-unique-medication-list-playwright-checks');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('the script entry point registers the prerequisite before the workflow creates drug fixtures', () => {
  const entry = { exports: {} };
  let registered = false;
  const requireStub = name => {
    if (name === './lib/playwright-harness') return h;
    assert.equal(name, './lib/workflow-session');
    return { runWorkflow(name, body, options) {
      registered = true;
      assert.equal(name, 'rx-unique-medication-list');
      assert.equal(typeof body, 'function');
      assert.throws(() => options.preflight(), error => error instanceof h.SkipCheck);
    } };
  };
  requireStub.main = entry;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'rx-unique-medication-list-playwright-checks.js'), 'utf8'),
    { require: requireStub, module: entry, process: { env: {} } });
  assert.equal(registered, true);
});

test('an unconfirmed or disabled auto-include prerequisite is a SKIP, never an application failure', () => {
  for (const value of [undefined, '', 'false', 'yes']) {
    assert.throws(() => preflight({ CONSULTATION_AUTO_INCLUDE_MEDICATIONS: value }), error =>
      error instanceof h.SkipCheck && /packaged default: false/.test(error.message)
      && /restart, then export CONSULTATION_AUTO_INCLUDE_MEDICATIONS=true/.test(error.message));
  }
  assert.doesNotThrow(() => preflight({ CONSULTATION_AUTO_INCLUDE_MEDICATIONS: 'true' }));
});

// Drive the workflow with the chart already showing the six expected entries.
// A confirmed prerequisite must never turn an empty/broken consultation into a SKIP.
function session(medications) {
  const marker = 'FAKE-PW5211';
  const lines = ['RENEWED', 'OVERLAP', 'RERX', 'STATUS', 'TWOREG 10 mg', 'TWOREG 20 mg']
    .map(name => `${marker}-${name}`);
  let closed = false;
  const steps = [];
  const cleanups = [];
  const chart = { locator: () => ({
    first() { return this; },
    locator() { return this; },
    waitFor: async () => {},
    evaluateAll: async () => lines,
  }) };
  return {
    marker, patient: '5211', provider: '999998', steps, cleanups,
    config: { baseUrl: new URL('http://127.0.0.1/carlos') },
    sql: {
      execute() {},
      value(query) {
        if (query.includes('INSERT INTO prescription')) return '1';
        if (query.includes('AND archived=1')) return '2';
        if (query.includes('SELECT (SELECT COUNT(*)')) return '0';
        if (query.includes('COUNT(*) FROM drugs')) return '10';
        assert.fail(`Unexpected query: ${query}`);
      },
    },
    cleanup(fn) { cleanups.push(fn); },
    chart: async () => chart,
    async step(label, fn) {
      steps.push(label);
      try { await fn(); } catch (error) { throw h.markFailedStep(error, label); }
    },
    popup: async () => ({
      locator: () => ({ waitFor: async () => {}, inputValue: async () => medications(lines) }),
      isClosed: () => closed,
      close: async () => { closed = true; },
    }),
    context: { request: { get: async () => ({
      status: () => 200,
      json: async () => ({ summaryItem: lines.map(displayName => ({ displayName })) }),
    }) } },
    isFormClosed: () => closed,
  };
}

test('enabled auto-include still fails for empty, incomplete and duplicate medication lists', async () => {
  for (const medications of [() => '', lines => lines.slice(1).join('\n'), lines => [...lines, lines[0]].join('\n')]) {
    const s = session(medications);
    await assert.rejects(workflow(s), error => !(error instanceof h.SkipCheck)
      && /a new consultation/.test(error.failedStep)
      && /not filled|lists RENEWED/.test(error.message));
    assert.equal(s.isFormClosed(), true);
    assert.equal(s.cleanups.length, 1);
    await s.cleanups[0]();
  }
});

test('all four workflow steps pass when chart, consultation and REST lists agree', async () => {
  const s = session(lines => lines.join('\n'));
  await workflow(s);
  assert.equal(s.steps.length, 4);
  assert.equal(s.isFormClosed(), true);
  await s.cleanups[0]();
});
