/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Classification rules of the get-reject ledger: unchanged rows alone never prove the
 * application refused a GET/HEAD. Only a 405, or a 403 carrying the application's own header,
 * counts; a WAF page, a 5xx and an unmarked 403 are inconclusive and fail the ledger.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { createLedger } = require('./lib/get-reject-probe');

const APP = { 'x-permitted-cross-domain-policies': 'none' };

function session(responses) {
  const queue = [...responses];
  return {
    config: { baseUrl: new URL('https://localhost/carlos/') },
    context: {
      request: {
        fetch: async () => {
          const next = queue.shift();
          return { status: () => next.status, headers: () => next.headers || {}, text: async () => next.body || '' };
        },
      },
    },
  };
}

async function run(responses, options = {}) {
  const ledger = createLedger('unit');
  await ledger.probe(session(responses), {
    label: 'route', path: '/carlos/x', params: new URLSearchParams('a=1'), snapshot: () => 'same', ...options,
  });
  return ledger;
}

test('shouldAcceptRefusal_whenBothVerbsAre405', async () => {
  const ledger = await run([{ status: 405 }, { status: 405 }]);
  assert.equal(ledger.assertAllRefused(), 1);
});

test('shouldAcceptRefusal_whenBothVerbs403CarryTheApplicationHeader', async () => {
  const ledger = await run([{ status: 403, headers: APP, body: '<html>denied</html>' }, { status: 403, headers: APP }]);
  assert.equal(ledger.assertAllRefused(), 1);
});

test('shouldReject_whenA403HasNoApplicationHeader', async () => {
  const ledger = await run([{ status: 403, body: '' }, { status: 403 }]);
  assert.throws(() => ledger.assertAllRefused(), /could not be attributed to the application/);
});

test('shouldReject_whenHeadIs403WithoutTheApplicationHeader', async () => {
  const ledger = await run([{ status: 405 }, { status: 403 }]);
  assert.throws(() => ledger.assertAllRefused(), /could not be attributed to the application/);
});

test('shouldReject_whenTheGetIsAWafPage', async () => {
  const ledger = await run([{ status: 403, body: '<center>nginx</center>' }, { status: 403, headers: APP }]);
  assert.throws(() => ledger.assertAllRefused(), /blocked by the WAF/);
});

test('shouldReject_whenTheAnswerIsA5xxEvenWithoutAStatusRequirement', async () => {
  const ledger = await run([{ status: 500 }, { status: 500 }], { requireStatus: false });
  assert.throws(() => ledger.assertAllRefused(), /server error/);
});

test('shouldAcceptNoWrite_whenAnIncludeAnswers200WithoutAStatusRequirement', async () => {
  const ledger = await run([{ status: 200 }, { status: 200 }], { requireStatus: false });
  assert.equal(ledger.assertAllRefused(), 1);
});

test('shouldReject_whenTheOwnedRowsChange', async () => {
  let reads = 0;
  const ledger = await run([{ status: 200 }, { status: 200 }], { snapshot: () => (++reads > 1 ? 'changed' : 'same') });
  assert.throws(() => ledger.assertAllRefused(), /CHANGED the owned rows/);
});
