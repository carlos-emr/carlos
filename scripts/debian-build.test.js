/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('source Debian build clears stale versioned WARs before selecting its output', () => {
  const rules = fs.readFileSync(path.join(__dirname, '..', 'debian', 'rules'), 'utf8');
  assert.match(rules, /\$\(MVN\) \$\(MVN_FLAGS\) clean package; \\\n\s*cp -f target\/carlos-\*\.war/);
  // A supplied, independently verified WAR remains usable without compiling.
  assert.match(rules, /if \[ -n "\$\$CARLOS_WAR" \]; then/);
});
