/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { ROUTES, assertProtected } = require('./demographic-gates-playwright-checks');
test('every demographic gate probe names an actual release route', () => {
  const xml = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/classes/struts-demographic.xml'), 'utf8');
  assert.equal(new Set(ROUTES).size, 25);
  for (const route of ROUTES) assert.ok(xml.includes(`name="demographic/${route}"`), `Missing route ${route}`);
});
test('gate assertion rejects silent success, missing routes, errors and external redirects', () => {
  const base = 'http://127.0.0.1:8080/carlos';
  for (const status of [200, 404, 500]) assert.throws(() => assertProtected(status, null, base));
  assert.throws(() => assertProtected(302, 'https://example.com/login', base));
  assert.throws(() => assertProtected(302, '/carlos/demographic/ViewContact', base));
  assert.throws(() => assertProtected(302, null, base));
  for (const status of [401, 403]) assert.doesNotThrow(() => assertProtected(status, null, base));
  assert.doesNotThrow(() => assertProtected(302, '/carlos/login', base));
});
