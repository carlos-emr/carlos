/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

for (const filename of ['rx-fax-reprint-represcribe-playwright-checks.js', 'rx-fax-signature-stamp-playwright-checks.js']) {
  for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
    test(`${filename}: ${signal} cleans fixtures and credentials before exiting ${exitCode}`, () => {
      const source = fs.readFileSync(path.join(__dirname, filename), 'utf8');
      const registration = source.match(/for \(const signal of \['SIGINT', 'SIGTERM'\]\) \{[\s\S]*?\n\}/);
      assert.ok(registration, 'actual signal registration must be present');
      const handlers = new Map();
      const events = [];
      vm.runInNewContext(registration[0], {
        cleanupFixtures() { events.push('fixtures'); },
        removeSecretsDir() { events.push('credentials'); },
        process: {
          on(name, handler) { handlers.set(name, handler); },
          exit(code) { events.push(code); },
        },
      });
      handlers.get(signal)();
      assert.deepEqual(events, ['fixtures', 'credentials', exitCode]);
    });
  }
}
