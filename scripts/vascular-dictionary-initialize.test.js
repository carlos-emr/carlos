/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Unit tests for scripts/vascular-dictionary-initialize.js: no browser or database is started.
const test = require('node:test');
const assert = require('node:assert/strict');
const { workflow } = require('./vascular-dictionary-initialize');

function session({ status = 200, missing = [] } = {}) {
  const visits = [];
  const queries = [];
  let closed = 0;
  return {
    visits, queries, closed: () => closed,
    config: { baseUrl: new URL('https://127.0.0.1/carlos') },
    patient: '42',
    context: {
      async newPage() {
        return {
          async goto(url) { visits.push(url); return status === null ? null : { status: () => status }; },
          async close() { closed += 1; },
        };
      },
    },
    sql: {
      rows(query) { queries.push(query); return missing; },
      execute() { assert.fail('The initializer must leave dictionary writes to the application'); },
    },
    async step(label, body) { await body(); },
  };
}

test('opens the shipped Vascular Tracker route for its own patient, then requires the dictionary', async () => {
  const s = session();
  await workflow(s);
  assert.deepEqual(s.visits, ['https://127.0.0.1/carlos/form/SetupForm?formName=VTForm&demographic_no=42']);
  assert.equal(s.closed(), 1);
  assert.equal(s.queries.length, 1, 'one read-only existence query after the visit');
  assert.match(s.queries[0], /FROM measurementType/);
});

test('a refused or missing setup response fails before the dictionary is read', async () => {
  for (const status of [400, 500, null]) {
    const s = session({ status });
    await assert.rejects(workflow(s), /Vascular Tracker setup answered/);
    assert.equal(s.queries.length, 0);
  }
});

test('a dictionary the visit did not complete is reported, not ignored', async () => {
  const s = session({ missing: [['BP', 'Sitting Position']] });
  await assert.rejects(workflow(s), /VT dictionary initialization is required/);
});
