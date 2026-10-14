/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute each real validator without starting its browser or touching fixtures.
function collectScripts(directory, prefix = '') {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const relative = path.join(prefix, entry.name);
    if (entry.isDirectory() && entry.name !== 'node_modules') return collectScripts(path.join(directory, entry.name), relative);
    return entry.isFile() && entry.name.endsWith('.js') && !entry.name.endsWith('.test.js') ? [relative] : [];
  });
}
const scripts = collectScripts(__dirname);
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

// Exercise every standalone validator that intentionally permits RFC1918 IPv4.
// Prefix matching is not sufficient: e.g. 10.attacker.invalid is a DNS name.
for (const name of scripts) {
  const source = fs.readFileSync(path.join(__dirname, name), 'utf8');
  const start = source.indexOf('function validateBaseUrl(');
  if (start < 0) continue;
  const end = source.indexOf('\n}', start) + 2;
  const validator = source.slice(start, end);
  if (!validator.includes('const isIpv4 =')) continue;
  function contextFor(env = {}) {
    const context = vm.createContext({URL, process: {env}});
    // Some guards distinguish reachability, cleartext and loopback with shared
    // host sets. Execute those real dependencies, not lookalike test stubs.
    for (const constant of ['LOOPBACK_HOSTS', 'LOCAL_HTTP_HOSTS']) {
      const declaration = source.split('\n').find(line => line.startsWith(`const ${constant} = new Set(`));
      if (declaration) vm.runInContext(declaration, context);
    }
    for (const helper of ['normalizeHost', 'isLoopbackHost', 'isLocalHttpHost']) {
      const dependencyStart = source.indexOf(`function ${helper}(`);
      if (dependencyStart >= 0) {
        vm.runInContext(source.slice(dependencyStart, source.indexOf('\n}', dependencyStart) + 2), context);
      }
    }
    vm.runInContext(validator, context);
    return context;
  }
  test(`${name}: private-IP-looking DNS names cannot receive credentials without remote opt-in`, () => {
    const context = contextFor();
    for (const host of ['10.attacker.invalid', '10.1.2.3.attacker.invalid', '192.168.attacker.invalid',
      '192.168.1.1.attacker.invalid', '172.16.attacker.invalid', '172.31.1.1.attacker.invalid', '172.32.0.1']) {
      assert.throws(() => context.validateBaseUrl(`https://${host}/carlos`), /[Rr]efusing non-local BASE_URL/);
    }
  });
  test(`${name}: valid private IPv4 and explicit remote HTTPS targets retain compatibility`, () => {
    const context = contextFor();
    for (const host of ['10.0.0.1', '10.255.255.254', '192.168.0.1', '172.16.0.1', '172.31.255.254']) {
      assert.doesNotThrow(() => context.validateBaseUrl(`https://${host}/carlos`));
    }
    const optedIn = contextFor({ALLOW_NON_LOCAL_BASE_URL: 'true'});
    assert.doesNotThrow(() => optedIn.validateBaseUrl('https://review.example.invalid/carlos'));
    assert.throws(() => optedIn.validateBaseUrl('https://user:secret@review.example.invalid/carlos'), /BASE_URL must not/);
  });
}
