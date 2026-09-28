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

test('Tomcat startup invalidates only generated JSP code without following cache symlinks', () => {
  const os = require('node:os');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-jsp-cache-'));
  try {
    const source = fs.readFileSync(path.join(__dirname, '..', 'debian/assets/bin/carlos-emr-tomcat'), 'utf8');
    const fn = source.match(/clear_jsp_cache\(\) \{[\s\S]*?\n\}/)[0];
    const cache = path.join(root, 'work/Catalina/localhost/carlos');
    const generated = path.join(cache, 'org/apache/jsp');
    fs.mkdirSync(generated, {recursive:true});
    const removed = ['page_jsp.class', 'page_jsp$1.class', 'page_jsp.java'].map(name => path.join(generated,name));
    const retained = [path.join(cache,'SESSIONS.ser'), path.join(cache,'other.class'), path.join(generated,'image.png')];
    for (const file of [...removed,...retained]) fs.writeFileSync(file,'fixture');
    const outside = path.join(root,'outside'); fs.mkdirSync(outside); fs.writeFileSync(path.join(outside,'other.class'),'keep');
    fs.symlinkSync(outside,path.join(generated,'linked'));
    const run = () => spawnSync('bash',['-c',`${fn}\nclear_jsp_cache`],{env:{...process.env,CATALINA_BASE:root},encoding:'utf8'});
    const result=run(); assert.equal(result.status,0,result.stderr);
    for(const file of removed) assert.equal(fs.existsSync(file),false,file);
    for(const file of retained) assert.equal(fs.readFileSync(file,'utf8'),'fixture');
    assert.equal(fs.readFileSync(path.join(outside,'other.class'),'utf8'),'keep');
    assert.equal(run().status,0,'idempotent cleanup');
    fs.renameSync(path.join(root,'work'),path.join(root,'old-work')); fs.symlinkSync(path.join(root,'old-work'),path.join(root,'work'));
    assert.notEqual(run().status,0,'reject a symlinked work root');
    assert.match(source,/run\)\s+clear_jsp_cache\s+cd/);
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
