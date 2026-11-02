/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { assertApplicationNotFound } = require('./billing-bc-province-guard-playwright-checks');

const route = '/billing/CA/BC/reprocessBill';
const marker = { 'x-permitted-cross-domain-policies': 'none' };
const response = (status, headers) => ({ status: () => status, headers: () => headers });

for (const method of ['GET', 'HEAD']) {
  test(`${method}: accepts the application's marked 404 without reading a response body`, () => {
    assert.doesNotThrow(() => assertApplicationNotFound(response(404, marker), method, route));
  });

  test(`${method}: rejects a proxy or container 404 without the application marker`, () => {
    assert.throws(() => assertApplicationNotFound(response(404, {}), method, route),
      /must carry the CARLOS response header/);
  });

  test(`${method}: rejects a 404 with an unexpected marker value`, () => {
    const unexpected = response(404, { 'x-permitted-cross-domain-policies': 'other' });
    assert.throws(() => assertApplicationNotFound(unexpected, method, route),
      /must carry the CARLOS response header/);
  });

  test(`${method}: rejects a marked server error`, () => {
    assert.throws(() => assertApplicationNotFound(response(500, marker), method, route),
      /must return 404, got 500/);
  });
}
