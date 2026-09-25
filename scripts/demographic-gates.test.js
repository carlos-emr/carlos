/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { ROUTES, assertProtected } = require('./demographic-gates-playwright-checks');
test('every demographic gate probe names an actual release route', () => {
  const xml = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/classes/struts-demographic.xml'), 'utf8');
  assert.equal(new Set(ROUTES).size, 27);
  const configured = [...xml.matchAll(/<action\b[^>]*\bname="demographic\/([^"]+)"[^>]*\bclass="[^"]*\.gate\.[^"]+"/g)]
    .map(match => match[1]);
  assert.deepEqual([...ROUTES].sort(), configured.sort(), 'Every demographic gate action must be probed');
});
test('gate assertion rejects silent success, missing routes, errors and external redirects', () => {
  const base = 'http://127.0.0.1:8080/carlos';
  const requestUrl = `${base}/demographic/ViewContact`;
  for (const status of [200, 404, 500]) assert.throws(() => assertProtected(status, null, requestUrl, base));
  assert.throws(() => assertProtected(302, 'https://example.com/login', requestUrl, base));
  assert.throws(() => assertProtected(302, '/carlos/demographic/ViewContact', requestUrl, base));
  assert.throws(() => assertProtected(302, null, requestUrl, base));
  // A same-origin page that merely ends in a login-surface name is not the login surface.
  assert.throws(() => assertProtected(302, '/carlos/administration/index', requestUrl, base));
  assert.throws(() => assertProtected(302, '/carlos/demographic/login', requestUrl, base));
  assert.throws(() => assertProtected(302, '/other/logoutPage', requestUrl, base));
  for (const status of [401, 403]) assert.doesNotThrow(() => assertProtected(status, null, requestUrl, base));
  assert.doesNotThrow(() => assertProtected(302, '/carlos/login', requestUrl, base));
  assert.doesNotThrow(() => assertProtected(302, '/carlos/logoutPage', requestUrl, base));
  // Relative references resolve against the requested route, not the base url.
  assert.doesNotThrow(() => assertProtected(302, '../logoutPage', requestUrl, base));
  assert.throws(() => assertProtected(302, 'carlos/login', requestUrl, base));
  assert.doesNotThrow(() => assertProtected(302, 'http://127.0.0.1:8080/carlos/logoutPage', requestUrl, new URL(base)));
});
