/* Regression coverage for browser-check transport requirements. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

for (const name of ['login-playwright-checks.js', 'application-health-playwright-checks.js']) {
  const source = fs.readFileSync(path.join(__dirname, name), 'utf8');
  const validation = source.slice(source.indexOf('function validateBaseUrl('), source.indexOf('\nfunction appUrl('));
  const validate = vm.runInNewContext(`(${validation})`, { URL, process: { env: {} } });

  test(`${name} defaults to the existing devcontainer TLS connector`, () => {
    assert.match(source, /process\.env\.BASE_URL \|\| 'https:\/\/127\.0\.0\.1:8443\/carlos'/);
    assert.equal(validate('https://127.0.0.1:8443/carlos').protocol, 'https:');
  });

  test(`${name} rejects HTTP before attempting authenticated checks`, () => {
    assert.throws(() => validate('http://127.0.0.1:8080/carlos'), /must use HTTPS/);
  });

  test(`${name} retains its credential and remote-target guards`, () => {
    assert.throws(() => validate('https://user:password@localhost:8443/carlos'), /username or password/);
    assert.throws(() => validate('https://example.org/carlos'), /Refusing non-local/);
  });
}
