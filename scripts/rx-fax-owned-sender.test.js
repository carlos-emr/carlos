/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { readFaxSuffix, assertFaxDestination, installFaxRequestGuard } = require('./rx-fax-request-guard');
const { settleOperations } = require('./graceful-signal-cancellation');

for (const filename of ['rx-fax-record-binding-playwright-checks.js', 'rx-fax-signature-stamp-playwright-checks.js']) {
  const source = fs.readFileSync(path.join(__dirname, filename), 'utf8');
  function harness(overrides = {}) {
    const context = vm.createContext({ URLSearchParams, assertFaxDestination, faxNumber: '4161234567', pharmacyFaxNumber: '5551234567',
      faxConfig: null, seededPharmacyFaxes: [], findings: [], visited: [], demographicNo: '1',
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

  test(`${filename}: snapshots all pharmacy destinations and restores null, empty and existing values conditionally`, () => {
    const queries = [];
    const h = harness({ sql: query => {
      queries.push(query);
      return query.startsWith('SELECT p.recordId') ? '1\t1\t\n2\t0\t\n3\t0\t416 555 0100' : '';
    } });
    h.seedPharmacyFax();
    assert.equal(h.seededPharmacyFaxes.length, 3);
    assert.equal(queries.filter(query => query.startsWith("UPDATE pharmacyInfo SET fax = '5551234567'")).length, 3);
    h.cleanupFixtures();
    for (const [id, expected] of [[1, 'NULL'], [2, "''"], [3, "'416 555 0100'"]]) {
      assert(queries.some(query => query.includes(`SET fax = ${expected} WHERE recordId = ${id} AND fax = '5551234567'`)));
    }
    assert.equal(h.findings.length, 0);
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
