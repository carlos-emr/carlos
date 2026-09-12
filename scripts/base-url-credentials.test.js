/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute each real validator without starting its browser or touching fixtures.
const scripts = fs.readdirSync(__dirname).filter(name => name.endsWith('.js') && !name.endsWith('.test.js'));
for (const name of scripts) {
  const source = fs.readFileSync(path.join(__dirname, name), 'utf8');
  const start = source.indexOf('function validateBaseUrl(');
  if (start < 0) continue;
  const end = source.indexOf('\n}', start) + 2;
  test(`${name}: URL credentials are rejected even with remote-target opt-in`, () => {
    const context = vm.createContext({ URL, process: { env: { ALLOW_NON_LOCAL_BASE_URL: 'true' } } });
    vm.runInContext(source.slice(start, end), context);
    for (const credentials of ['user:secret', 'user', ':secret', 'user:%73ecret']) {
      for (const host of ['127.0.0.1:8080', 'example.invalid']) {
        assert.throws(() => context.validateBaseUrl(`http://${credentials}@${host}/carlos`), error => {
          assert.match(error.message, /^BASE_URL must not (?:embed a username or password|embed credentials|contain embedded credentials)$/);
          assert.doesNotMatch(error.message, /secret|%73ecret|http:/);
          return true;
        });
      }
    }
  });
}
