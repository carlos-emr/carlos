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

// One deploy per transaction: carlos-emr and carlos-emr-drugref must both
// activate the SAME trigger carlos-emr declares interest in, and the carlos-emr
// postinst must accept the `triggered` action instead of rejecting it as an
// unknown argument. A name mismatch silently brings back the double deploy (or
// no restart at all), and nothing else would notice.
test('every EMR restart point activates the carlos-emr-restart trigger carlos-emr declares', () => {
  const read = (...p) => fs.readFileSync(path.join(__dirname, '..', ...p), 'utf8');
  const triggers = read('debian', 'carlos-emr.triggers')
    .split('\n').filter(l => l.trim() && !l.trim().startsWith('#'));
  assert.deepEqual(triggers, ['interest-noawait carlos-emr-restart']);
  const postinst = read('debian', 'carlos-emr.postinst');
  const drugref = read('debian', 'carlos-emr-drugref.postinst');
  // DrugRef's removal restart too: an inline restart there would deploy the
  // application a second time when the same transaction upgrades carlos-emr.
  const drugrefPostrm = read('debian', 'carlos-emr-drugref.postrm');
  for (const script of [postinst, drugref, drugrefPostrm]) {
    assert.match(script, /dpkg-trigger --no-await carlos-emr-restart/);
    // Only under dpkg: dpkg-reconfigure sets no DPKG_RUNNING_VERSION and would
    // leave a trigger nobody processes.
    assert.match(script, /DPKG_RUNNING_VERSION/);
  }
  assert.match(postinst, /^    triggered\)$/m);
  // A configure that must not start the EMR (schema not ready, seed sentinel)
  // records a veto the trigger honours while the unit is down, so DrugRef's
  // activation of the same trigger cannot restart a `failed` unit into a
  // schema the application's boot gate will refuse again.
  assert.match(postinst, /elif \[ "\$\{MIGRATION_OK:-1\}" = 0 \]; then\n(.*\n){1,6}?.*: > "\$\{START_VETO\}"/);
  assert.match(postinst, /elif \[ -e "\$\{START_VETO\}" \] && ! systemctl is-active --quiet carlos-emr\.service; then/);
  const veto = postinst.indexOf('elif [ -e "${START_VETO}" ] && ! systemctl is-active');
  const anyState = postinst.indexOf('|| systemctl is-failed --quiet carlos-emr.service; then');
  assert.ok(veto > 0 && anyState > veto, 'the veto must be checked before the active/failed restart');
  assert.match(postinst, /if \[ "\$1" = triggered \] && \[ -d \/run\/systemd\/system \]; then/);
});

// Renderer provisioning (the one-time move, the url-base token, the browser
// restart) must wait for the provisioning lock like init-config does: another
// run's init-config reads renderer.env to render the service URL, and changing
// it underneath would leave carlos.properties naming a URL the browser no
// longer serves.
test('renderer provisioning and restart are gated on the provisioning lock', () => {
  const postinst = fs.readFileSync(path.join(__dirname, '..', 'debian', 'carlos-emr.postinst'), 'utf8');
  assert.match(postinst, /^        RENDER_PROVISION="\$\{PROVISION_LOCK_HELD\}"$/m);
  const move = postinst.indexOf('One-time move off the pre-2026.08.0~alpha14 names');
  const token = postinst.indexOf("printf 'CARLOS_RENDER_URL_BASE=%s\\n'");
  for (const at of [move, token]) {
    assert.ok(at > 0);
    const before = postinst.slice(0, at);
    const gate = before.lastIndexOf('if [ "${RENDER_PROVISION}" = 1 ]; then');
    assert.ok(gate > before.lastIndexOf('RENDER_PROVISION="${PROVISION_LOCK_HELD}"'));
    // ... and the guarded line is INSIDE that if: no `fi` at the gate's own
    // indentation closes it before the line is reached.
    const gateIndent = /^ */.exec(postinst.slice(postinst.lastIndexOf('\n', gate) + 1))[0];
    assert.doesNotMatch(postinst.slice(gate, at), new RegExp(`^${gateIndent}fi$`, 'm'));
  }
  assert.match(postinst, /if \[ "\$\{RENDER_PAYLOAD:-0\}" = 1 \] && \[ "\$\{RENDER_PROVISION:-1\}" = 1 \]; then\n        sd_invoke restart carlos-emr-render-browser\.service/);
});

// The render browser must not reuse any name the pre-2026.08.0~alpha14
// carlos-emr-eform-renderer postrm deletes or disables on purge.
test('render browser names avoid everything the old renderer purge touches', () => {
  const read = (...p) => fs.readFileSync(path.join(__dirname, '..', ...p), 'utf8');
  const shipped = [
    read('debian', 'rules'),
    read('debian', 'carlos-emr.tmpfiles'),
    read('debian', 'carlos-emr.sysusers'),
    read('debian', 'assets', 'systemd', 'carlos-emr.service.d', '10-eform-renderer.conf'),
    read('debian', 'assets', 'systemd', 'carlos-emr-render-browser.service'),
    read('debian', 'assets', 'carlos_ctl', 'util.py'),
  ].join('\n');
  assert.doesNotMatch(shipped, /carlos-emr-chromedriver|render-browser\.env|\/var\/lib\/carlos-emr\/render(?![a-z])/);
  assert.match(shipped, /carlos-emr-render-browser/);
});
