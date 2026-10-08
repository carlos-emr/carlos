/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { readFaxSuffix, assertFaxDestination, installFaxRequestGuard } = require('./rx-fax-request-guard');
const { settleOperations } = require('./graceful-signal-cancellation');
const { fixtureErrorTag } = require('./rx-fax-pharmacy-fax-fixture');

for (const filename of ['rx-fax-record-binding-playwright-checks.js', 'rx-fax-signature-stamp-playwright-checks.js']) {
  const source = fs.readFileSync(path.join(__dirname, filename), 'utf8');
  function harness(overrides = {}) {
    const context = vm.createContext({ URLSearchParams, assertFaxDestination, faxNumber: '4161234567', pharmacyFaxNumber: '5551234567',
      faxConfig: null, findings: [], visited: [], demographicNo: '1',
      pharmacyFax: { restore: () => ({ restored: 0, untouched: 0 }) },
      fixtureErrorTag,
      customDrugName: 'owned-test', throwawayUnsignedScriptId: null, browserErrorClass: () => 'Error', ...overrides });
    for (const name of ['stageFaxConfig', 'selectOwnedFaxSender', 'assertOwnedFaxRequest', 'cleanupOwnedFaxSender', 'seedPharmacyFax', 'cleanupFixtures']) {
      const start = source.indexOf(`function ${name}(`);
      assert.notEqual(start, -1);
      const asyncStart = source.slice(start - 6, start) === 'async ' ? start - 6 : start;
      const end = source.indexOf('\n}', start) + 2;
      vm.runInContext(source.slice(asyncStart, end), context);
    }
    return context;
  }

  for (const collision of ['account', 'orphan job']) {
    test(`${filename}: ${collision} collision fails before mutation and cleanup preserves it`, () => {
      const queries = [];
      const h = harness({ sql: query => {
        queries.push(query);
        if (query.startsWith('SELECT id FROM fax_config')) return collision === 'account' ? '9' : '';
        if (query.startsWith('SELECT id FROM faxes')) return '17';
        return '';
      } });
      assert.throws(() => h.stageFaxConfig(), /collided/);
      h.cleanupFixtures();
      assert.equal(queries.some(query => /^(INSERT|UPDATE|DELETE)/.test(query)), false);
      assert.equal(h.findings.length, 0);
    });
  }

  test(`${filename}: creates a dedicated active sender with polling disabled`, () => {
    const queries = [];
    const h = harness({ sql: query => { queries.push(query); return query.startsWith('INSERT') ? '23' : ''; } });
    assert.equal(h.stageFaxConfig().id, '23');
    assert.match(queries[2], /VALUES \('SRFAX', 1, '4161234567'.*'', 0\)/);
  });

  test(`${filename}: selects own sender over an unrelated default and refuses failed selection`, async () => {
    const h = harness();
    let current = '4169999999';
    const frame = { locator: selector => {
      assert.equal(selector, '#faxNumber');
      return { selectOption: async value => { current = value; }, inputValue: async () => current };
    } };
    await h.selectOwnedFaxSender(frame);
    assert.equal(current, h.faxNumber);
    await assert.rejects(h.selectOwnedFaxSender({ locator: () => ({ selectOption: async () => {}, inputValue: async () => '4169999999' }) }), /not selected/);
  });

  test(`${filename}: verifies actual POST sender and synthetic destination`, () => {
    const h = harness();
    const request = fields => ({ url: () => 'https://localhost/carlos/form/createcustomedpdf', postData: () => new URLSearchParams(fields).toString() });
    h.assertOwnedFaxRequest(request({ clinicFax: h.faxNumber, pharmaFax: h.pharmacyFaxNumber }));
    assert.throws(() => h.assertOwnedFaxRequest(request({ clinicFax: '4169999999', pharmaFax: h.pharmacyFaxNumber })), /owned sender/);
    assert.throws(() => h.assertOwnedFaxRequest(request({ clinicFax: h.faxNumber, pharmaFax: '4169999999' })), /synthetic destination/);
    assert.throws(() => h.assertOwnedFaxRequest(request({})), /owned sender/);
  });

  test(`${filename}: cleanup fails visibly if the owned sender was replaced`, () => {
    const queries = [];
    const h = harness({ faxConfig: { id: '23', created: true }, sql: query => { queries.push(query); return '1'; } });
    assert.throws(() => h.cleanupOwnedFaxSender(), /ownership changed/);
    assert.match(queries[0], /WHERE id=23 AND faxNumber='4161234567' AND accountName='Playwright Fax'/);
  });

  test(`${filename}: takes the shared lock before staging every pharmacy destination`, async () => {
    const events = [];
    const h = harness({ pharmacyFax: {
      async lock() { events.push('lock'); return { journals: 1, restored: 2, untouched: 0 }; },
      seed() { events.push('seed'); return { active: 2, seeded: 2 }; },
    } });
    await h.seedPharmacyFax();
    assert.deepEqual(events, ['lock', 'seed']);
    assert.deepEqual(h.visited.map((entry) => entry.label), ['pharmacy-fax-recovery', 'pharmacy-fax']);
    assert.equal(h.findings.length, 0);
  });

  test(`${filename}: a patient without an active pharmacy is a finding`, async () => {
    const h = harness({ pharmacyFax: { async lock() { return { journals: 0 }; }, seed: () => ({ active: 0, seeded: 0 }) } });
    await h.seedPharmacyFax();
    assert.deepEqual(h.findings.map((finding) => finding.type), ['no-active-pharmacy']);
  });

  test(`${filename}: cleanup restores the pharmacy destinations and reports a failed restore`, () => {
    let restores = 0;
    const ok = harness({ sql: () => '', pharmacyFax: { restore: () => { restores++; return { restored: 2, untouched: 1 }; } } });
    ok.cleanupFixtures();
    assert.equal(restores, 1);
    assert.equal(JSON.stringify(ok.visited), JSON.stringify([{ label: 'pharmacy-fax-restore', untouched: 1 }]));
    assert.equal(ok.findings.length, 0);
    const failed = harness({ sql: () => '', pharmacyFax: { restore: () => { throw new Error('private database diagnostic'); } } });
    failed.cleanupFixtures();
    assert.equal(JSON.stringify(failed.findings), JSON.stringify([{ label: 'cleanup', type: 'cleanup-error', text: 'pharmacy-fax: Error' }]));
    const coded = harness({ sql: () => '', pharmacyFax: { restore: () => {
      throw Object.assign(new Error('private database diagnostic'), { code: 'RX_FAX_FIXTURE_RESTORE' });
    } } });
    coded.cleanupFixtures();
    assert.equal(coded.findings[0].text, 'pharmacy-fax: Error (RX_FAX_FIXTURE_RESTORE)');
  });

  test(`${filename}: stages the destination before the sender and releases the lock only after cleanup`, () => {
    const runChecks = source.slice(source.indexOf('async function runChecks('));
    assert(runChecks.indexOf('await seedPharmacyFax();') < runChecks.indexOf('stageFaxConfig()'));
    const cleanup = runChecks.indexOf('cleanupFixtures();');
    assert(cleanup > 0 && cleanup < runChecks.indexOf('await releaseFixtureLock();'));
  });
}

for (const digits of [6, 7]) {
  test(`reserved fax suffix requires exactly ${digits} digits before using any randomness`, () => {
    let calls = 0;
    const rng = (min, max) => { calls++; assert.equal(min, 10 ** (digits - 1)); assert.equal(max, 10 ** digits); return min; };
    for (const value of ['', '0'.repeat(digits), '1'.repeat(digits - 1), '1'.repeat(digits + 1), ' ' + '1'.repeat(digits), '1'.repeat(digits) + '\n', "1';DROP"]) {
      assert.throws(() => readFaxSuffix(value, digits, rng), /PR4055_RX_FAX_SUFFIX/);
    }
    assert.equal(readFaxSuffix('1'.repeat(digits), digits, rng), '1'.repeat(digits));
    assert.equal(calls, 0);
    assert.equal(readFaxSuffix(undefined, digits, rng), String(10 ** (digits - 1)));
    assert.equal(calls, 1);
  });
}

for (const suffix of ['record-binding', 'signature-stamp', 'pharmacy-phone']) {
  test(`${suffix}: rejects invalid reserved suffix before SQL, credential files or browser launch`, () => {
    const source = fs.readFileSync(path.join(__dirname, `rx-fax-${suffix}-playwright-checks.js`), 'utf8');
    const declaration = source.match(/const run(?:Fax)?Suffix = readFaxSuffix[^;]+;/)[0];
    const index = source.indexOf(declaration);
    for (const operation of ['const mysqlBin = resolveMysqlBinary()', 'const config = readConfig()', '(async () =>']) {
      const operationIndex = source.indexOf(operation);
      if (operationIndex !== -1) assert(index < operationIndex);
    }
    assert.throws(() => vm.runInNewContext(declaration, {
      readFaxSuffix, process: { env: { PR4055_RX_FAX_SUFFIX: "1';DROP" } }, randomInt() { throw new Error('randomness used'); },
    }), /PR4055_RX_FAX_SUFFIX/);
  });
}

async function guardHarness() {
  let match, handler;
  const events = [];
  await installFaxRequestGuard({ async route(predicate, callback) { match = predicate; handler = callback; } },
    'https://localhost/carlos/', '4161234567', '5551234567', () => events.push('violation'));
  async function send(fields, query = '__method=oscarRxFax', method = 'POST') {
    const request = { method: () => method, url: () => `https://localhost/carlos/form/createcustomedpdf?${query}`,
      postData: () => typeof fields === 'string' ? fields : new URLSearchParams(fields).toString() };
    await handler({ request: () => request, async abort(reason) { events.push(reason); },
      async continue() { events.push('sent'); }, async fallback() { events.push('fallback'); } });
  }
  return { events, match, send };
}

test('fax route only intercepts the exact app endpoint and preserves unrelated methods', async () => {
  const h = await guardHarness();
  assert(h.match(new URL('https://localhost/carlos/form/createcustomedpdf?__method=oscarRxFax')));
  for (const url of ['https://other/carlos/form/createcustomedpdf', 'https://localhost/other/form/createcustomedpdf', 'https://localhost/carlos/form/createcustomedpdfSuffix']) assert.equal(h.match(new URL(url)), false);
  await h.send({}, '__method=preview');
  await h.send({}, '__method=oscarRxFax', 'GET');
  assert.deepEqual(h.events, ['fallback', 'fallback']);
});

test('fax route sends valid query-method and body-method POSTs unchanged', async () => {
  const h = await guardHarness();
  await h.send({ clinicFax: '4161234567', pharmaFax: '5551234567' });
  await h.send({ __method: 'oscarRxFax', clinicFax: '4161234567', pharmaFax: '5551234567' }, '');
  assert.deepEqual(h.events, ['sent', 'sent']);
});

for (const [label, body, query] of [
  ['wrong sender', 'clinicFax=4169999999&pharmaFax=5551234567'],
  ['real destination', 'clinicFax=4161234567&pharmaFax=4169999999'],
  ['missing destination', 'clinicFax=4161234567'],
  ['duplicate body sender', 'clinicFax=4161234567&clinicFax=4169999999&pharmaFax=5551234567'],
  ['conflicting query destination', 'clinicFax=4161234567&pharmaFax=5551234567', '__method=oscarRxFax&pharmaFax=4169999999'],
]) {
  test(`fax route aborts ${label} before transmission`, async () => {
    const h = await guardHarness();
    await h.send(body, query);
    assert.deepEqual(h.events, ['violation', 'blockedbyclient']);
  });
}

test('stamp fax drains pending response and click after its request observer rejects', async () => {
  const source = fs.readFileSync(path.join(__dirname, 'rx-fax-signature-stamp-playwright-checks.js'), 'utf8');
  const start = source.indexOf('const [, faxResponse] = await settleOperations([');
  const end = source.indexOf('      assertOwnedFaxRequest', start);
  assert(start > 0 && end > start);
  const releases = [];
  let finished = false;
  const pending = () => new Promise(resolve => releases.push(resolve));
  const requestFailure = new Error('request failed');
  const operation = vm.runInNewContext(`(async () => { let faxRequest; ${source.slice(start, end)} })()`, {
    settleOperations, faxRequestPromise: Promise.reject(requestFailure), faxResponsePromise: pending(),
    modalFrame: { locator: () => ({ click: pending }) },
  }).catch(error => { assert.equal(error, requestFailure); }).finally(() => { finished = true; });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(finished, false);
  releases[0]();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(finished, false);
  releases[1]();
  await operation;
  assert.equal(finished, true);
});
