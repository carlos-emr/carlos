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
  assert.match(rules, /\$\(MVN\) -f "\$\$src\/pom\.xml"[^;]*\bclean package; \\\n\s*cp -f "\$\$src"\/target\/drugref2\*\.war/);
});

// The carlos-ctl(8) render gate moved to the carlos-ctl package with the man
// page (#4001). What dh_auto_test checks here is the payload that package
// reads: every shipped OSCAR 19 manifest parses and carries its kind's format -- a
// malformed one fails an import at a clinic, not a build, unless it is caught
// here. The recipe is run against a tree of manifests, good and bad.
for (const [label, body, expected] of [
  ['the shipped manifests', null, 0],
  ['an older schema format', '{"format": 1, "kind": "o19map-schema"}', 1],
  ['a newer schema format', '{"format": 4, "kind": "o19map-schema"}', 1],
  ['a props manifest with schema format', '{"format": 2, "kind": "o19map-props"}', 1],
  ['a preflight manifest with schema format', '{"format": 2, "kind": "o19-preflight"}', 1],
  ['an unknown kind', '{"format": 1, "kind": "unknown"}', 1],
  ['a boolean format', '{"format": true, "kind": "o19map-schema"}', 1],
  ['a manifest with no kind', '{"format": 1}', 1],
  ['a file that is not JSON', '{not json', 1],
]) {
  test(`dh_auto_test validates every shipped OSCAR 19 manifest (${label})`, () => {
    const os = require('node:os');
    const rules = fs.readFileSync(path.join(__dirname, '..', 'debian', 'rules'), 'utf8');
    const recipe = rules.split('override_dh_auto_test:\n')[1].split('\noverride_dh_auto_clean:')[0]
      .split('\n').filter(line => !line.trimStart().startsWith('#')).join('\n').replaceAll('$$', '$');
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-manifest-gate-'));
    try {
      const dir = path.join(root, 'debian', 'assets', 'o19-manifest');
      fs.mkdirSync(dir, { recursive: true });
      const shipped = path.join(__dirname, '..', 'debian', 'assets', 'o19-manifest');
      for (const name of fs.readdirSync(shipped).filter((n) => n.endsWith('.json'))) {
        fs.copyFileSync(path.join(shipped, name), path.join(dir, name));
      }
      // The package's manifest gate also hashes the stock collision assets.
      // Stage those exact source bytes in this isolated recipe fixture.
      const stock = JSON.parse(fs.readFileSync(path.join(shipped, 'stock-eform-assets.json'), 'utf8'));
      const sourceAssets = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'eform-assets');
      const fixtureAssets = path.join(root, 'src', 'main', 'webapp', 'WEB-INF', 'eform-assets');
      fs.mkdirSync(fixtureAssets, { recursive: true });
      for (const name of Object.keys(stock.assets)) {
        fs.copyFileSync(path.join(sourceAssets, name), path.join(fixtureAssets, name));
      }
      if (body !== null) fs.writeFileSync(path.join(dir, 'zz_bad.json'), body);
      const result = spawnSync('sh', ['-c', recipe], { cwd: root, encoding: 'utf8' });
      assert.ifError(result.error);
      assert.equal(result.status, expected, result.stderr);
      if (expected !== 0) assert.match(result.stderr, /zz_bad\.json/);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
}

test('packaged Tomcat starts with the Base64 intrinsic disabled on fresh and customized installs', async (t) => {
  const { once } = require('node:events');
  const net = require('node:net');
  const root = fs.mkdtempSync('/tmp/carlos-tomcat-');
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));

  // Relocate only the package directories. Run the complete launcher, including
  // its startup checks and its exec of catalina.sh, inside this fixture.
  const source = fs.readFileSync(path.join(__dirname, '..', 'debian/assets/bin/carlos-emr-tomcat'), 'utf8');
  const launcher = path.join(root, 'carlos-emr-tomcat');
  const packagePaths = /\/(?:etc\/carlos-emr|var\/lib\/carlos-emr|usr\/share\/tomcat11|run\/carlos-emr)\b/g;
  fs.writeFileSync(launcher, source.replace(packagePaths, directory => root + directory));
  for (const directory of [
    'etc/carlos-emr', 'var/lib/carlos-emr/catalina/conf', 'usr/share/tomcat11/bin', 'usr/share/tomcat11/lib', 'jdk/bin',
  ]) {
    fs.mkdirSync(path.join(root, directory), { recursive: true });
  }
  fs.writeFileSync(path.join(root, 'etc/carlos-emr/carlos.properties'), '');
  fs.writeFileSync(path.join(root, 'var/lib/carlos-emr/catalina/conf/server.xml'), '<Server/>');
  fs.writeFileSync(path.join(root, 'jdk/release'), 'JAVA_VERSION="25"\n');
  fs.symlinkSync('/bin/true', path.join(root, 'jdk/bin/java'));
  const catalina = path.join(root, 'usr/share/tomcat11/bin/catalina.sh');
  fs.writeFileSync(catalina, '#!/bin/sh\nset -eu\n[ "$1" = run ]\nprintf "%s\\n" "$CATALINA_OPTS"\n', { mode: 0o755 });

  const database = net.createServer(socket => socket.end());
  await once(database.listen(0, '127.0.0.1'), 'listening');
  t.after(() => new Promise(resolve => database.close(resolve)));

  for (const siteOptions of ['', '-Dclinic.example=true -XX:+UseBASE64Intrinsics']) {
    fs.writeFileSync(path.join(root, 'etc/carlos-emr/carlos-emr.env'), `CARLOS_JAVA_OPTS="${siteOptions}"\n`);
    const result = spawnSync('bash', [launcher, 'run'], {
      env: {
        PATH: process.env.PATH,
        JAVA_HOME: path.join(root, 'jdk'),
        CARLOS_TZ: 'UTC',
        CARLOS_DB_PORT: String(database.address().port),
      },
      encoding: 'utf8',
      timeout: 10_000,
    });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    const options = result.stdout.trim().split(/\s+/);
    if (siteOptions) assert.ok(options.includes('-Dclinic.example=true'));
    assert.equal(options.filter(option => /^-XX:[+-]UseBASE64Intrinsics$/.test(option)).at(-1),
      '-XX:-UseBASE64Intrinsics');
  }
});

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
  // The restart is also held back while a legacy carlos-emr-chromedriver that would not
  // stop still owns the port (OLD_RENDERER_LIVE, set by the move off the old names).
  assert.match(postinst, /if \[ "\$\{RENDER_PAYLOAD:-0\}" = 1 \] && \[ "\$\{RENDER_PROVISION:-1\}" = 1 \] \\\n        && \[ "\$\{OLD_RENDERER_LIVE:-0\}" = 0 \]; then\n        sd_invoke restart carlos-emr-render-browser\.service/);
});

// A legacy renderer that refuses to stop keeps its home and blocks the new browser's start,
// rather than having its working directory deleted from under it.
test('a legacy renderer that would not stop keeps its home and holds the new browser', () => {
  const postinst = fs.readFileSync(path.join(__dirname, '..', 'debian', 'carlos-emr.postinst'), 'utf8');
  const unit = fs.readFileSync(path.join(__dirname, '..', 'debian/assets/systemd/carlos-emr-render-browser.service'), 'utf8');
  assert.match(unit, /^ConditionPathExists=!\/var\/lib\/carlos-emr\/\.legacy-renderer-live$/m);
  assert.match(postinst, /if \[ "\$\{OLD_RENDERER_LIVE\}" = 1 \]; then\n +: > "\$\{STATE\}\/\.legacy-renderer-live"/);
  assert.match(postinst, /else\n +rm -f "\$\{STATE\}\/\.legacy-renderer-live"/);
  assert.match(postinst, /deb-systemd-invoke stop carlos-emr-chromedriver\.service >\/dev\/null 2>&1 \|\| true\n +if systemctl is-active --quiet carlos-emr-chromedriver\.service; then\n +OLD_RENDERER_LIVE=1/);
  assert.match(postinst, /if \[ "\$\{OLD_RENDERER_LIVE\}" = 0 \] \\\n +&& \[ -d "\$\{STATE\}\/render" \] && \[ ! -L "\$\{STATE\}\/render" \]; then\n +rm -rf "\$\{STATE\}\/render"/);
});

// A SKIP_EFORM_RENDERER build over a full one removes the render browser's unit
// with the payload; the postinst must purge the enablement the full build
// recorded (dangling wants symlink, deb-systemd-helper state), guarded on the
// unit file being absent so a partial payload keeps its unit enabled.
test('postinst purges a stale render-browser enablement when the unit is no longer shipped', () => {
  const postinst = fs.readFileSync(path.join(__dirname, '..', 'debian', 'carlos-emr.postinst'), 'utf8');
  const guard = postinst.indexOf('if [ "${RENDER_PAYLOAD}" = 0 ] \\\n            && [ ! -e /usr/lib/systemd/system/carlos-emr-render-browser.service ]');
  assert.ok(guard > 0, 'guard on RENDER_PAYLOAD=0 and the unit file being absent');
  const block = postinst.slice(guard, postinst.indexOf('\n        fi\n', guard));
  assert.match(block, /deb-systemd-helper purge carlos-emr-render-browser\.service/);
  assert.match(block, /rm -f \/etc\/systemd\/system\/multi-user\.target\.wants\/carlos-emr-render-browser\.service/);
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
    // the CLI is its own repository since #4001: its util.py is read from
    // the checkout CARLOS_CTL_SRC names, or the installed package, when present
    ...[process.env.CARLOS_CTL_SRC, '/usr/lib/carlos-ctl']
      .filter((dir) => dir && fs.existsSync(path.join(dir, 'carlos_ctl', 'util.py')))
      .slice(0, 1)
      .map((dir) => fs.readFileSync(path.join(dir, 'carlos_ctl', 'util.py'), 'utf8')),
  ].join('\n');
  assert.doesNotMatch(shipped, /carlos-emr-chromedriver|render-browser\.env|\/var\/lib\/carlos-emr\/render(?![a-z])/);
  assert.match(shipped, /carlos-emr-render-browser/);
});

test('source builds retain both dependency-lock gates', () => {
  const rules = fs.readFileSync(path.join(__dirname, '..', 'debian', 'rules'), 'utf8');
  const flags = rules.match(/^MVN_FLAGS\s*:=([^\n]*)/m)[1];
  assert.doesNotMatch(flags, /skip-dependency-lock/);
  const recipe = rules.match(/\$\(MVN\) -f "\$\$src\/pom\.xml"[\s\S]*?clean package;/)[0];
  assert.match(recipe, /se\.vandmo:dependency-lock-maven-plugin:1\.1\.1:check/);
  assert.match(recipe, /-DdependencyLock\.filename=\$\$lock/);
  assert.match(rules, /lock=.*debian\/drugref-dependencies-lock\.json/);
});

// ---- #6336 gap tests: packaged units, launcher and wrappers -------------------------------

const REPO = path.join(__dirname, '..');
const readRepo = (...p) => fs.readFileSync(path.join(REPO, ...p), 'utf8');
/** A systemd unit or shell text without its comment lines, so prose cannot satisfy a match. */
const withoutComments = (text) => text.split('\n').filter(line => !/^\s*#/.test(line)).join('\n');
const megabytes = (value) => Number(/^(\d+)M$/.exec(value)[1]);

// The render browser's cgroup ceilings are sized for one browser per concurrent render slot
// (#5853): the two shared slots plus the chart-only one, 300M peak each beyond the old 1G/1536M.
test('render browser memory ceilings cover every browser the render slots can admit', () => {
  const unit = withoutComments(readRepo('debian/assets/systemd/carlos-emr-render-browser.service'));
  assert.match(unit, /^MemoryAccounting=yes$/m);
  const high = megabytes(/^MemoryHigh=(\S+)$/m.exec(unit)[1]);
  const max = megabytes(/^MemoryMax=(\S+)$/m.exec(unit)[1]);
  assert.ok(high < max, 'MemoryHigh throttles below the MemoryMax kill');

  const service = readRepo('src/main/java/io/github/carlos_emr/carlos/eform/util/EFormBrowserPdfService.java');
  const shared = Number(/private static final int MAX_CONCURRENT_RENDERS = (\d+);/.exec(service)[1]);
  const chartOnly = Number(/static final int CHART_ONLY_RENDERS = (\d+);/.exec(service)[1]);
  const browsers = shared + chartOnly;
  assert.equal(browsers, 3, 'the unit comment budgets three concurrent browsers');
  const browserPeak = 300;
  const baseHigh = 1024;
  const baseMax = 1536;
  // The ceilings are the pre-#5853 two-browser budget plus one peak per extra browser.
  assert.equal(high, baseHigh + (browsers - 2) * browserPeak);
  assert.equal(max, baseMax + (browsers - 2) * browserPeak);
});

// The root-run preflight wrapper (#6133), executed with its absolute paths relocated into a
// temporary tree and a shell stub in place of java.
function preflightFixture(t) {
  const root = fs.mkdtempSync('/tmp/carlos-preflight-');
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const source = readRepo('debian/assets/bin/carlos-emr-config-preflight');
  const wrapper = path.join(root, 'carlos-emr-config-preflight');
  fs.writeFileSync(wrapper, source.replace(/\/(?:usr\/lib\/jvm|usr\/share\/carlos-emr|etc\/carlos-emr)\b/g,
    directory => root + directory), { mode: 0o755 });
  const jdk = (name, version) => {
    const home = path.join(root, 'usr/lib/jvm', name);
    fs.mkdirSync(path.join(home, 'bin'), { recursive: true });
    fs.writeFileSync(path.join(home, 'release'), `JAVA_VERSION="${version}"\n`);
    fs.writeFileSync(path.join(home, 'bin/java'),
      `#!/bin/sh\n{ echo "JDK=${name}"; for a in "$@"; do echo "ARG=$a"; done; env; } > "${root}/seen"\n`, { mode: 0o755 });
  };
  const run = (args, env = {}) => {
    fs.rmSync(path.join(root, 'seen'), { force: true });
    const result = spawnSync('/bin/sh', [wrapper, ...args], {
      env: { PATH: process.env.PATH, ...env }, encoding: 'utf8', timeout: 10_000,
    });
    assert.ifError(result.error);
    const seen = fs.existsSync(path.join(root, 'seen')) ? fs.readFileSync(path.join(root, 'seen'), 'utf8') : null;
    return { ...result, seen };
  };
  return { root, jdk, run };
}

test('config preflight wrapper refuses a missing or unknown verb before running java', (t) => {
  const { jdk, run } = preflightFixture(t);
  jdk('java-25-openjdk-amd64', '25.0.1');
  for (const args of [[], ['frobnicate'], ['']]) {
    const result = run(args);
    assert.equal(result.status, 2, JSON.stringify(args));
    assert.match(result.stderr, /usage: carlos-emr-config-preflight reconcile\|verify/);
    assert.equal(result.seen, null, 'java must not run');
  }
});

test('config preflight wrapper strips JVM injection variables and runs the jar with verb and properties', (t) => {
  const { root, jdk, run } = preflightFixture(t);
  jdk('java-25-openjdk-amd64', '25.0.1');
  for (const verb of ['reconcile', 'verify']) {
    const result = run([verb], {
      JAVA_TOOL_OPTIONS: '-invalid-option', _JAVA_OPTIONS: '-Dx=1', JDK_JAVA_OPTIONS: '-Dy=2', CLASSPATH: '/evil',
    });
    assert.equal(result.status, 0, result.stderr);
    const lines = result.seen.split('\n');
    assert.deepEqual(lines.filter(l => l.startsWith('ARG=')), [
      'ARG=-jar',
      `ARG=${root}/usr/share/carlos-emr/lib/carlos-configuration-preflight.jar`,
      `ARG=${verb}`,
      `ARG=${root}/etc/carlos-emr/carlos.properties`,
    ]);
    for (const name of ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH']) {
      assert.ok(!lines.some(l => l.startsWith(`${name}=`)), `${name} must be scrubbed`);
    }
  }
});

test('config preflight wrapper skips a non-25 runtime and refuses to start without Java 25', (t) => {
  const { jdk, run } = preflightFixture(t);
  jdk('default-java', '21.0.5');
  jdk('java-25-openjdk-old', '2');
  let result = run(['verify']);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Java 25 is unavailable/);
  assert.equal(result.seen, null, 'a Java 21 runtime must not be used');

  jdk('java-25-openjdk-amd64', '25.0.1');
  result = run(['verify']);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.seen, /^JDK=java-25-openjdk-amd64$/m);
});

test('packaged Tomcat launcher hands catalina the preflight agent and refuses a database that never answers', async (t) => {
  const { once } = require('node:events');
  const net = require('node:net');
  const root = fs.mkdtempSync('/tmp/carlos-tomcat-agent-');
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const packagePaths = /\/(?:etc\/carlos-emr|var\/lib\/carlos-emr|usr\/share\/tomcat11|usr\/share\/carlos-emr|run\/carlos-emr)\b/g;
  const source = readRepo('debian/assets/bin/carlos-emr-tomcat');
  // Shorten the 120 s database wait so the refusal branch can run in a test.
  const shortened = source.replace('SECONDS + 120 ', 'SECONDS + 1 ');
  assert.notEqual(shortened, source, 'the database wait deadline must be found');
  const launcher = path.join(root, 'carlos-emr-tomcat');
  fs.writeFileSync(launcher, shortened.replace(packagePaths, directory => root + directory));
  for (const directory of ['etc/carlos-emr', 'var/lib/carlos-emr/catalina/conf', 'usr/share/tomcat11/bin', 'usr/share/tomcat11/lib', 'jdk/bin']) {
    fs.mkdirSync(path.join(root, directory), { recursive: true });
  }
  fs.writeFileSync(path.join(root, 'etc/carlos-emr/carlos.properties'), '');
  fs.writeFileSync(path.join(root, 'etc/carlos-emr/carlos-emr.env'), 'CARLOS_JAVA_OPTS=""\n');
  fs.writeFileSync(path.join(root, 'var/lib/carlos-emr/catalina/conf/server.xml'), '<Server/>');
  fs.writeFileSync(path.join(root, 'jdk/release'), 'JAVA_VERSION="25"\n');
  fs.symlinkSync('/bin/true', path.join(root, 'jdk/bin/java'));
  fs.writeFileSync(path.join(root, 'usr/share/tomcat11/bin/catalina.sh'),
    '#!/bin/sh\nset -eu\nprintf "%s\\n" "$CATALINA_OPTS"\n', { mode: 0o755 });
  const run = (port) => spawnSync('bash', [launcher, 'run'], {
    env: { PATH: process.env.PATH, JAVA_HOME: path.join(root, 'jdk'), CARLOS_TZ: 'UTC', CARLOS_DB_PORT: String(port) },
    encoding: 'utf8', timeout: 20_000,
  });

  const sockets = new Set();
  const database = net.createServer(socket => { sockets.add(socket); socket.on('error', () => {}); socket.end(); });
  await once(database.listen(0, '127.0.0.1'), 'listening');
  t.after(() => { sockets.forEach(s => s.destroy()); database.close(); });
  const livePort = database.address().port;
  const started = run(livePort);
  assert.equal(started.status, 0, started.stderr);
  const options = started.stdout.trim().split(/\s+/);
  const properties = `${root}/etc/carlos-emr/carlos.properties`;
  assert.ok(options.includes(`-javaagent:${root}/usr/share/carlos-emr/lib/carlos-configuration-preflight.jar=${properties}`),
    'the JVM verifies the effective configuration through the preflight agent');
  assert.ok(options.includes(`-Dcarlos_override_properties=${properties}`));

  sockets.forEach(s => s.destroy());
  await new Promise(resolve => database.close(resolve)); // the port is now closed
  const refused = run(livePort);
  assert.equal(refused.status, 1);
  assert.match(refused.stderr, /did not accept a connection within 120s/);
  assert.equal(refused.stdout, '', 'catalina must not start without a database');
});

// The service unit reconciles the packaged properties after the guards and before Tomcat starts.
test('carlos-emr service reconciles configuration after the O19 guard and before ExecStart', () => {
  const unit = withoutComments(readRepo('debian/carlos-emr.carlos-emr.service'));
  const lines = unit.split('\n');
  const at = (re) => lines.findIndex(line => re.test(line));
  const guard = at(/^ExecCondition=\+\/usr\/lib\/carlos-emr\/carlos-emr-o19-guard$/);
  const reconcile = at(/^ExecStartPre=\+\/usr\/lib\/carlos-emr\/carlos-emr-config-preflight reconcile$/);
  const start = at(/^ExecStart=\/usr\/lib\/carlos-emr\/carlos-emr-tomcat run$/);
  assert.ok(guard >= 0 && reconcile >= 0 && start >= 0, 'guard, reconcile and start are all declared');
  assert.ok(guard < reconcile && reconcile < start, 'order: O19 guard, preflight reconcile, ExecStart');
  assert.equal(lines.filter(line => /^ExecStartPre=/.test(line)).length, 1);
});

// The migration README names files by path; each must exist and be in the merge ledger, in order.
// Its "highest migration" sentence is NOT asserted: it is stale (see the Task 6 report).
test('every migration the README layout names exists on disk and is ledgered in Flyway order', () => {
  const { MIGRATION_ROOT, readLedger, versionOf } = require('./lib/flyway-ledger');
  const compareVersions = (a, b) => {
    for (let i = 0; i < Math.max(a.length, b.length); i++) { const d = (a[i] || 0) - (b[i] || 0); if (d) return d; }
    return 0;
  };
  const readme = fs.readFileSync(path.join(MIGRATION_ROOT, 'README.md'), 'utf8');
  const layout = readme.slice(readme.indexOf('```text'), readme.indexOf('```', readme.indexOf('```text') + 7));
  let location = null;
  const named = [];
  for (const line of layout.split('\n')) {
    const heading = /^\s{2}(common|on|bc)\/\s+(V\S+\.sql)/.exec(line);
    if (heading) location = heading[1];
    const file = heading ? heading[2] : /^\s+(V\S+\.sql)/.exec(line)?.[1];
    if (file) named.push(`${location}/${file}`);
  }
  assert.ok(named.length > 50, 'the layout lists the migrations');
  const ledger = readLedger(path.join(__dirname, 'flyway-merge-order.txt')).map(([file]) => file);
  for (const file of named) {
    assert.ok(fs.existsSync(path.join(MIGRATION_ROOT, file)), `${file} exists`);
    assert.ok(ledger.includes(file), `${file} is in scripts/flyway-merge-order.txt`);
  }
  // The README groups files by location; within each group they are listed in version order, and
  // the ledger lists them in that same relative order.
  for (const loc of ['common', 'on', 'bc']) {
    const mine = named.filter(file => file.startsWith(`${loc}/`));
    for (let i = 1; i < mine.length; i++) {
      assert.ok(compareVersions(versionOf(mine[i - 1]), versionOf(mine[i])) < 0,
        `${mine[i - 1]} sorts before ${mine[i]}`);
      assert.ok(ledger.indexOf(mine[i - 1]) < ledger.indexOf(mine[i]), `${mine[i]} follows ${mine[i - 1]} in the ledger`);
    }
  }
});
