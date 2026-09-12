/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { spawnSync } = require('node:child_process');

test('source Debian build clears stale versioned WARs before selecting its output', () => {
  const rules = fs.readFileSync(path.join(__dirname, '..', 'debian', 'rules'), 'utf8');
  assert.match(rules, /\$\(MVN\) \$\(MVN_FLAGS\) clean package; \\\n\s*cp -f target\/carlos-\*\.war/);
  // A supplied, independently verified WAR remains usable without compiling.
  assert.match(rules, /if \[ -n "\$\$CARLOS_WAR" \]; then/);
  assert.match(rules, /\$\(MVN\) -f "\$\$src\/pom\.xml" \$\(MVN_FLAGS\) -Dmaven\.test\.skip=true clean package; \\\n\s*cp -f "\$\$src"\/target\/drugref2\*\.war/);
});

for (const [code, diagnostic] of [[0, ''], [0, 'test groff warning'], [2, 'test groff error']]) {
  test(`manpage validation preserves diagnostics and fails closed (${code}, ${diagnostic || 'clean'})`, () => {
    const rules = fs.readFileSync(path.join(__dirname, '..', 'debian', 'rules'), 'utf8');
    const recipe = rules.split('override_dh_auto_test:\n')[1].split('\noverride_dh_auto_clean:')[0]
      .split('\n').filter(line => !line.trimStart().startsWith('#')).join('\n').replaceAll('$$', '$');
    const result = spawnSync('sh', ['-c', `groff() { printf '%s' '${diagnostic}' >&2; return ${code}; }\n${recipe}`], { encoding: 'utf8' });
    assert.ifError(result.error);
    assert.equal(result.status, diagnostic ? 1 : 0);
    assert.equal(result.stderr.trim(), diagnostic);
  });
}
