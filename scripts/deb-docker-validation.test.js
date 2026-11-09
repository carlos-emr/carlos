/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Unit tests for scripts/deb-docker-validation.sh: everything that can be
// decided without a Docker daemon (the guard, the preseed, the cgroup mode
// and the package-set validation). A fake `docker` on PATH records every call
// so a test can prove the script refused BEFORE touching the daemon.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const SCRIPT = path.join(__dirname, 'deb-docker-validation.sh');

function sandbox(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-deb-docker-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const bin = path.join(dir, 'bin');
  fs.mkdirSync(bin);
  const calls = path.join(dir, 'docker-calls.log');
  // `docker inspect` answers "no such container"; anything else is recorded and fails.
  fs.writeFileSync(path.join(bin, 'docker'), `#!/bin/sh\necho "$*" >> '${calls}'\nexit 1\n`, { mode: 0o755 });
  return { dir, calls, env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, LOG_DIR: path.join(dir, 'logs') } };
}

function run(args, env) {
  return spawnSync('bash', [SCRIPT, ...args], { encoding: 'utf8', env });
}

test('should enable consultation auto-include before restarting the disposable validation install', (t) => {
  const box = sandbox(t);
  const source = fs.readFileSync(SCRIPT, 'utf8');
  const start = source.indexOf("    if grep -qE '^CONSULTATION_AUTO_INCLUDE_MEDICATIONS");
  const end = source.indexOf('    carlos-ctl restart', start);
  assert.ok(start > 0 && end > start);
  // Unquote the in_container string, then execute the real fixture commands
  // against a temporary properties file, never /etc or a running installation.
  const fixture = source.slice(start, end).replaceAll('\\"', '"').replaceAll('\\$', '$');
  const props = path.join(box.dir, 'carlos.properties');
  for (const before of ['', 'CONSULTATION_AUTO_INCLUDE_MEDICATIONS=false\n',
    'CONSULTATION_AUTO_INCLUDE_MEDICATIONS = false\n', 'CONSULTATION_AUTO_INCLUDE_MEDICATIONS: false\n']) {
    fs.writeFileSync(props, `other.setting=unchanged\n${before}`);
    const r = spawnSync('bash', ['-ec', fixture], { encoding: 'utf8', env: { ...box.env, P: props } });
    assert.equal(r.status, 0, r.stderr);
    assert.equal(fs.readFileSync(props, 'utf8'), 'other.setting=unchanged\nCONSULTATION_AUTO_INCLUDE_MEDICATIONS=true\n');
  }
  assert.match(source, /^export CONSULTATION_AUTO_INCLUDE_MEDICATIONS=true$/m);
});

test('should pass bash syntax checking', () => {
  assert.equal(spawnSync('bash', ['-n', SCRIPT]).status, 0);
});

test('image installs the lockfile-pinned axe-core beside Playwright', t => {
  const box = sandbox(t);
  const debs = path.join(box.dir, 'debs'), cgroups = path.join(box.dir, 'cgroups');
  fs.mkdirSync(debs); fs.mkdirSync(cgroups);
  fs.writeFileSync(path.join(cgroups, 'cgroup.controllers'), 'cpu memory');
  for (const name of ['carlos-emr_1_amd64.deb', 'carlos-emr-drugref_1_all.deb', 'carlos-ctl_1_all.deb']) {
    fs.writeFileSync(path.join(debs, name), '');
  }
  const captured = path.join(box.dir, 'Dockerfile');
  fs.writeFileSync(path.join(box.dir, 'bin/docker'), `#!/bin/sh
echo "$*" >> '${box.calls}'
if [ "$1" = build ]; then
  for context do :; done
  cp "$context/Dockerfile" '${captured}'
fi
exit 1
`, {mode:0o755});
  const r = run(['up'], {...box.env, CARLOS_DISPOSABLE_HOST:'true', DEBS_DIR:debs,
    CGROUP_ROOT:cgroups, CONTAINER_NETWORK:'bridge', AXE_CORE_VERSION:'', PLAYWRIGHT_VERSION:'', BUILD_CA_BUNDLE:''});
  assert.equal(r.status, 1);
  assert.match(r.stderr, /image build failed/);
  const dockerfile = fs.readFileSync(captured, 'utf8');
  assert.match(dockerfile, /npm install --save-exact[^\n]+"playwright@\$\{PLAYWRIGHT_VERSION\}" "axe-core@\$\{AXE_CORE_VERSION\}"/);
  const driver = require('../package-lock.json').packages['node_modules/playwright'].version;
  assert.equal(require('../package.json').devDependencies.playwright, driver);
  assert.ok(fs.readFileSync(box.calls, 'utf8').includes(`--build-arg PLAYWRIGHT_VERSION=${driver}`));
  const pin = require('../package-lock.json').packages['node_modules/axe-core'].version;
  assert.ok(fs.readFileSync(box.calls, 'utf8').includes(`--build-arg AXE_CORE_VERSION=${pin}`));
  const png = require('../package-lock.json').packages['node_modules/pngjs'].version;
  assert.equal(require('../package.json').devDependencies.pngjs, png);
  assert.ok(fs.readFileSync(box.calls, 'utf8').includes(`--build-arg PNGJS_VERSION=${png}`));
  assert.match(dockerfile, /"pngjs@\$\{PNGJS_VERSION\}"/);
  assert.match(dockerfile, /tesseract-ocr tesseract-ocr-eng/);
});

test('should refuse every container command without CARLOS_DISPOSABLE_HOST=true and never call docker', (t) => {
  const box = sandbox(t);
  for (const command of ['up', 'suite', 'audit', 'down', 'upgrade']) {
    const r = run([command], { ...box.env, CARLOS_DISPOSABLE_HOST: '' });
    assert.equal(r.status, 1, `${command}: ${r.stdout}${r.stderr}`);
    assert.match(r.stderr, /refusing to run: set CARLOS_DISPOSABLE_HOST=true/);
  }
  assert.equal(fs.existsSync(box.calls), false, 'docker was called before the guard refused');
});

test('upgrade rejects missing, ambiguous or malformed release versions before Docker or downloads', t => {
  const box=sandbox(t),env={...box.env,CARLOS_DISPOSABLE_HOST:'true'};
  for(const args of [[],[''],['../2026.08.0-beta2'],['2026.08.0-beta2','extra'],['2026.09.0-beta1']]) {
    const result=run(['upgrade',...args],env);
    assert.equal(result.status,2);assert.match(result.stderr,/exactly one from-version release tag/);
  }
  const result=run(['upgrade','2026.08.0-beta2'],{...env,DEBS_DIR:''});
  assert.equal(result.status,1);assert.match(result.stderr,/DEBS_DIR/);
  assert.equal(fs.existsSync(box.calls),false,'Invalid upgrade input reached Docker');
});

test('should preseed the runbook answers for the selected province', (t) => {
  const box = sandbox(t);
  for (const province of ['on', 'bc', 'other']) {
    const r = run(['print-preseed'], { ...box.env, CARLOS_PROVINCE: province });
    assert.equal(r.status, 0, r.stderr);
    assert.match(r.stdout, new RegExp(`^carlos-emr carlos-emr/province select ${province}$`, 'm'));
    // The secure default stays on: the validation must prove the forced first-login reset.
    assert.match(r.stdout, /^carlos-emr carlos-emr\/reset-seed-admin boolean true$/m);
    assert.match(r.stdout, /^carlos-emr carlos-emr\/tls-mode select selfsigned$/m);
    assert.match(r.stdout, /^carlos-emr carlos-emr\/install-demo-data boolean true$/m);
  }
  const noDemo = run(['print-preseed'], { ...box.env, INSTALL_DEMO_DATA: 'false' });
  assert.match(noDemo.stdout, /^carlos-emr carlos-emr\/install-demo-data boolean false$/m);
});

test('should filter the suite on the province the package installs, and on Ontario for the other alias', (t) => {
  const box = sandbox(t);
  // `other` applies the Ontario migrations (runbook section 2): it must not leave the filter empty, which would run
  // every BC-only check against a schema without its tables.
  for (const [province, expected] of [['on', 'ON'], ['bc', 'BC'], ['other', 'ON']]) {
    const r = run(['print-suite-province'], { ...box.env, CARLOS_PROVINCE: province });
    assert.equal(r.status, 0, r.stderr);
    assert.equal(r.stdout.trim(), expected, `CARLOS_PROVINCE=${province}`);
  }
});

test('should reject a province or demo-data value the package does not offer', (t) => {
  const box = sandbox(t);
  const province = run(['print-preseed'], { ...box.env, CARLOS_PROVINCE: 'qc' });
  assert.equal(province.status, 1);
  assert.match(province.stderr, /CARLOS_PROVINCE must be on, bc or other/);
  const demo = run(['print-preseed'], { ...box.env, INSTALL_DEMO_DATA: 'yes' });
  assert.equal(demo.status, 1);
});

test('should classify the host cgroup hierarchy as v2, hybrid or v1', (t) => {
  const box = sandbox(t);
  const v2 = path.join(box.dir, 'v2');
  fs.mkdirSync(v2);
  fs.writeFileSync(path.join(v2, 'cgroup.controllers'), 'cpu memory\n');
  const hybrid = path.join(box.dir, 'hybrid');
  fs.mkdirSync(path.join(hybrid, 'unified'), { recursive: true });
  fs.writeFileSync(path.join(hybrid, 'unified', 'cgroup.controllers'), '\n');
  const v1 = path.join(box.dir, 'v1');
  fs.mkdirSync(path.join(v1, 'memory'), { recursive: true });
  for (const [root, mode] of [[v2, 'v2'], [hybrid, 'hybrid'], [v1, 'v1']]) {
    const r = run(['cgroup-mode'], { ...box.env, CGROUP_ROOT: root });
    assert.equal(r.stdout.trim(), mode);
  }
});

test('should require exactly one of each package before building anything', (t) => {
  const box = sandbox(t);
  const env = { ...box.env, CARLOS_DISPOSABLE_HOST: 'true' };
  const missing = run(['up'], { ...env, DEBS_DIR: '' });
  assert.equal(missing.status, 1);
  assert.match(missing.stderr, /DEBS_DIR must name the directory/);

  const debs = path.join(box.dir, 'debs');
  fs.mkdirSync(debs);
  for (const name of ['carlos-emr_2026.08.0~alpha19_amd64.deb', 'carlos-emr_2026.08.0~alpha18_amd64.deb',
    'carlos-emr-drugref_2026.08.0~alpha19_all.deb', 'carlos-ctl_1.1.2_all.deb']) {
    fs.writeFileSync(path.join(debs, name), '');
  }
  const twoEmr = run(['up'], { ...env, DEBS_DIR: debs });
  assert.equal(twoEmr.status, 1);
  assert.match(twoEmr.stderr, /exactly one carlos-emr_\*_amd64\.deb/);

  fs.rmSync(path.join(debs, 'carlos-ctl_1.1.2_all.deb'));
  fs.rmSync(path.join(debs, 'carlos-emr_2026.08.0~alpha18_amd64.deb'));
  const noCtl = run(['up'], { ...env, DEBS_DIR: debs });
  assert.equal(noCtl.status, 1);
  assert.match(noCtl.stderr, /exactly one carlos-ctl_\*_all\.deb/);
  assert.equal(fs.existsSync(box.calls), false, 'docker was called before the package set was validated');
});

test('should print usage for help and fail on an unknown command', (t) => {
  const box = sandbox(t);
  const help = run(['--help'], box.env);
  assert.equal(help.status, 0);
  assert.match(help.stdout, /CARLOS_DISPOSABLE_HOST=true DEBS_DIR=/);
  assert.equal(run(['frobnicate'], box.env).status, 2);
});

test('should hand the audit no empty argument and quote the ones it is given', (t) => {
  const box = sandbox(t);
  const env = { ...box.env, CARLOS_DISPOSABLE_HOST: 'true' };
  run(['audit'], env);
  run(['audit', '--since', '2026-10-08 04:40:00'], env);
  const calls = fs.readFileSync(box.calls, 'utf8').split('\n').filter((line) => line.includes('deb-server-log-audit.sh'));
  assert.equal(calls.length, 2, fs.readFileSync(box.calls, 'utf8'));
  assert.match(calls[0], /deb-server-log-audit\.sh $/);
  assert.match(calls[1], /deb-server-log-audit\.sh --since 2026-10-08\\ 04:40:00 $/);
});


test('should refuse a cgroup-v1-only host before building an image or starting a container', (t) => {
  const box = sandbox(t);
  const v1 = path.join(box.dir, 'v1');
  fs.mkdirSync(path.join(v1, 'memory'), { recursive: true });
  const debs = path.join(box.dir, 'debs');
  fs.mkdirSync(debs);
  for (const name of ['carlos-emr_1_amd64.deb', 'carlos-emr-drugref_1_all.deb', 'carlos-ctl_1_all.deb']) {
    fs.writeFileSync(path.join(debs, name), '');
  }
  const r = run(['up'], { ...box.env, CARLOS_DISPOSABLE_HOST: 'true', DEBS_DIR: debs, CGROUP_ROOT: v1 });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /cgroup-v1-only hierarchy/);
  const calls = fs.existsSync(box.calls) ? fs.readFileSync(box.calls, 'utf8') : '';
  assert.doesNotMatch(calls, /^(build|buildx|run) /m, `docker was asked to build or run: ${calls}`);
});

test('should refuse a reset password that quoting could not carry into the container', (t) => {
  const box = sandbox(t);
  for (const password of ["it's", 'Pa$$w0rd', 'a`id`', 'back\\slash', 'q"x']) {
    const r = run(['print-preseed'], { ...box.env, RESET_PASSWORD: password });
    assert.equal(r.status, 1, `accepted ${password}`);
    assert.match(r.stderr, /RESET_PASSWORD must not/);
  }
  assert.equal(run(['print-preseed'], { ...box.env, RESET_PASSWORD: 'Carlos2026!Verify' }).status, 0);
});

test('should preserve literal HOME_DIR through the container shell and generated environment', t => {
  const box = sandbox(t);
  const directory = path.join(box.dir, "clinic space $HOME `touch injected` $(touch injected2) 'single'");
  fs.mkdirSync(directory);
  const properties = path.join(box.dir, 'carlos.properties');
  fs.writeFileSync(properties, `HOME_DIR=${directory}\n`);
  const buildProperties = path.join(box.dir, 'carlos-build.properties');
  fs.writeFileSync(buildProperties, 'build.version=test\nbuild.job=validation\nbuild.number=1\n');
  const initialAdmin = path.join(box.dir, 'initial-admin.txt');
  fs.writeFileSync(initialAdmin, 'user: fixture\nPIN: 1234\n');
  const environment = path.join(box.dir, 'suite-env.sh');
  const childRunner = path.join(box.dir, 'container-shell.js');
  fs.writeFileSync(childRunner, `
    const { spawnSync } = require('node:child_process');
    let command = process.env.CONTAINER_COMMAND;
    command = command.replaceAll('node scripts/packaged-home-dir.js', 'node ' + process.env.TEST_HOME_SCRIPT + ' ' + process.env.TEST_PROPERTIES);
    command = command.replaceAll('node /root/carlos/scripts/packaged-home-dir.js', 'node ' + process.env.TEST_HOME_SCRIPT + ' ' + process.env.TEST_PROPERTIES);
    command = command.replaceAll('/root/suite-env.sh', process.env.TEST_SUITE_ENV);
    command = command.replaceAll('/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/carlos-build.properties', process.env.TEST_BUILD_PROPERTIES);
    command = command.replaceAll('/etc/carlos-emr/initial-admin.txt', process.env.TEST_INITIAL_ADMIN);
    command = command.replaceAll('/root/carlos', process.env.TEST_REPO);
    command = command.replaceAll('/tmp/carlos-shots', process.env.TEST_TEMP + '/shots');
    command = command.replaceAll('/tmp/carlos-artifacts', process.env.TEST_TEMP + '/artifacts');
    const result = spawnSync('bash', ['-c', command], { cwd: process.env.TEST_TEMP, env: process.env, encoding: 'utf8' });
    process.stdout.write(result.stdout || ''); process.stderr.write(result.stderr || ''); process.exit(result.status ?? 1);
  `);
  const source = fs.readFileSync(SCRIPT, 'utf8');
  const functionSource = source.slice(source.indexOf('\nwrite_suite_env() {'), source.indexOf('\ncmd_suite() {'));
  assert.match(functionSource, /^\nwrite_suite_env\(\) \{/);
  const result = spawnSync('bash', ['-c', `set -euo pipefail
    suite_province() { printf BC; }
    in_container() { CONTAINER_COMMAND="$1" node "$TEST_CHILD_RUNNER"; }
    ${functionSource}
    write_suite_env
  `], { encoding: 'utf8', cwd: box.dir, env: { ...box.env, RESET_PASSWORD: 'FixturePassword',
    TEST_CHILD_RUNNER: childRunner, TEST_TEMP: box.dir, TEST_HOME_SCRIPT: path.join(__dirname, 'packaged-home-dir.js'),
    TEST_PROPERTIES: properties, TEST_SUITE_ENV: environment, TEST_BUILD_PROPERTIES: buildProperties,
    TEST_INITIAL_ADMIN: initialAdmin, TEST_REPO: path.resolve(__dirname, '..') } });
  assert.equal(result.status, 0, result.stdout + result.stderr);
  const sourced = spawnSync('bash', ['-c', 'mariadb() { printf fixturehash; }; . "$1"; printf %s "$HOME_DIR"', 'source-env', environment],
    { encoding: 'utf8', cwd: box.dir });
  assert.equal(sourced.status, 0, sourced.stderr);
  assert.equal(sourced.stdout, directory);
  assert.equal(fs.existsSync(path.join(box.dir, 'injected')), false, 'backtick command ran');
  assert.equal(fs.existsSync(path.join(box.dir, 'injected2')), false, 'dollar command substitution ran');
});

test('should refuse an unknown profile, and every profile without CARLOS_DISPOSABLE_HOST=true, before calling docker', (t) => {
  const box = sandbox(t);
  const unknown = run(['profile', 'nonesuch'], { ...box.env, CARLOS_DISPOSABLE_HOST: 'true' });
  assert.equal(unknown.status, 1);
  assert.match(unknown.stderr, /profile must be legacy-teleplan or default \(got 'nonesuch'\)/);
  for (const name of ['legacy-teleplan', 'default']) {
    const r = run(['profile', name], { ...box.env, CARLOS_DISPOSABLE_HOST: '' });
    assert.equal(r.status, 1, `${name}: ${r.stdout}${r.stderr}`);
    assert.match(r.stderr, /refusing to run: set CARLOS_DISPOSABLE_HOST=true/);
  }
  assert.equal(fs.existsSync(box.calls), false, 'docker was called before the profile was refused');
});

test('should refuse the legacy-teleplan profile on an install that is not British Columbia', (t) => {
  const box = sandbox(t);
  // The fake docker answers nothing, so the installed province reads as unknown.
  const r = run(['profile', 'legacy-teleplan'], { ...box.env, CARLOS_DISPOSABLE_HOST: 'true' });
  assert.equal(r.status, 1);
  assert.match(r.stderr, /the legacy-teleplan profile is for a BC install \(this one is 'unknown'\)/);
  const calls = fs.readFileSync(box.calls, 'utf8');
  assert.doesNotMatch(calls, /carlos-ctl restart/, 'the install was changed although the province check failed');
});

/** Apply `profile NAME`'s carlos.properties edit to `file`, as the container does. */
function applyProfileEdit(name, file) {
  const edit = run(['print-profile-edit', name], process.env);
  assert.equal(edit.status, 0, edit.stderr);
  const r = spawnSync('bash', ['-ec', `P='${file}'\n${edit.stdout}`], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  return fs.readFileSync(file, 'utf8');
}

test('should switch carlos.properties to a profile and back byte for byte, whatever the file ends with', (t) => {
  const box = sandbox(t);
  const cases = {
    'with the setting': 'A=1\nNEW_BC_TELEPLAN=yes\nB=2\n',
    'without a final newline': 'A=1\nNEW_BC_TELEPLAN=yes\nB=2',
    'without the setting': 'A=1\nB=2',
    'with a spaced setting': 'A=1\nNEW_BC_TELEPLAN = yes\n',
  };
  for (const [label, original] of Object.entries(cases)) {
    const file = path.join(box.dir, 'carlos.properties');
    fs.writeFileSync(file, original);
    let text = applyProfileEdit('legacy-teleplan', file);
    // Java properties keep the last value of a key: the profile's own line must be the one that is read.
    const values = [...text.matchAll(/^NEW_BC_TELEPLAN\s*[=:]\s*(.*)$/gm)].map(m => m[1]);
    assert.deepEqual(values, ['no'], `${label}: NEW_BC_TELEPLAN values ${JSON.stringify(values)}`);
    if (/^B=2$/m.test(original)) assert.match(text, /^B=2$/m, `${label}: the last setting was changed`);
    assert.equal(applyProfileEdit('legacy-teleplan', file), text, `${label}: switching twice is not idempotent`);
    text = applyProfileEdit('default', file);
    assert.equal(text.replace(/\n$/, ''), original.replace(/\n$/, ''), `${label}: profile default did not restore the file`);
    assert.equal(applyProfileEdit('default', file), text, `${label}: profile default twice changed the file`);
  }
});

test('should refuse a suite run that does not match the profile the install is in', (t) => {
  const box = sandbox(t);
  // This fake docker answers the profile query with legacy-teleplan; anything else is recorded and fails.
  fs.writeFileSync(path.join(box.dir, 'bin', 'docker'),
    `#!/bin/sh\ncase "$*" in *carlos-validation-profile:*) echo legacy-teleplan; exit 0 ;; esac\necho "$*" >> '${box.calls}'\nexit 1\n`,
    { mode: 0o755 });
  const r = run(['suite', '--tier', 'smoke'], { ...box.env, CARLOS_DISPOSABLE_HOST: 'true' });
  assert.equal(r.status, 1);
  assert.match(r.stderr, /the install is in profile 'legacy-teleplan' but the run asks for 'default'/);
  assert.equal(fs.existsSync(box.calls), false, 'the suite ran although the install is in another profile');
});

test('unsupported container networks are refused before Docker',t=>{
  const box=sandbox(t),result=run(['up'],{...box.env,CARLOS_DISPOSABLE_HOST:'true',CONTAINER_NETWORK:'public-network'});
  assert.equal(result.status,1);assert.match(result.stderr,/CONTAINER_NETWORK must be host or bridge/);assert.equal(fs.existsSync(box.calls),false);
});

test('bridge setup refuses bare, ported and slash-terminated loopback proxies before Docker',t=>{
  const box=sandbox(t);
  for(const proxy of ['http://localhost','https://LOCALHOST','http://127.0.0.1','http://127.1',
    'http://localhost:8080','http://127.0.0.1/']) {
    const result=run(['up'],{...box.env,CARLOS_DISPOSABLE_HOST:'true',CONTAINER_NETWORK:'bridge',BUILD_PROXY:proxy});
    assert.equal(result.status,1);assert.match(result.stderr,/Bridge containers need a BUILD_PROXY address reachable/);
  }
  assert(!fs.existsSync(box.calls));
  const remote=run(['print-preseed'],{...box.env,CONTAINER_NETWORK:'bridge',BUILD_PROXY:'http://localhost.example.invalid'});
  assert.equal(remote.status,0,remote.stderr);
});

test('relative CA bundles with spaces reach docker run as absolute bind sources',t=>{
  const box=sandbox(t),debs=path.join(box.dir,'debs'),cgroups=path.join(box.dir,'cgroups');
  fs.mkdirSync(debs);fs.mkdirSync(cgroups);fs.writeFileSync(path.join(cgroups,'cgroup.controllers'),'cpu memory');
  for(const name of ['carlos-emr_1_amd64.deb','carlos-emr-drugref_1_all.deb','carlos-ctl_1_all.deb'])fs.writeFileSync(path.join(debs,name),'');
  const relative='trust files/proxy bundle.pem';fs.mkdirSync(path.join(box.dir,'trust files'));
  fs.writeFileSync(path.join(box.dir,relative),'synthetic trust bundle');
  fs.writeFileSync(path.join(box.dir,'bin','docker'),`#!${process.execPath}\nconst fs=require('node:fs');const args=process.argv.slice(2);
if(args[0]==='inspect')process.exit(1);if(args[0]==='build')process.exit(0);
if(args[0]==='run'){fs.writeFileSync(${JSON.stringify(box.calls)},JSON.stringify(args));process.exit(7);}process.exit(9);\n`,{mode:0o755});
  const result=spawnSync('bash',[SCRIPT,'up'],{cwd:box.dir,encoding:'utf8',env:{...box.env,CARLOS_DISPOSABLE_HOST:'true',
    DEBS_DIR:debs,CGROUP_ROOT:cgroups,CONTAINER_NETWORK:'bridge',BUILD_PROXY:'http://proxy:8080',BUILD_CA_BUNDLE:relative}});
  assert.equal(result.status,7,result.stdout+result.stderr);
  const args=JSON.parse(fs.readFileSync(box.calls,'utf8'));
  assert(args.includes(`${fs.realpathSync(path.join(box.dir,relative))}:/run/carlos-validation-ca.pem:ro`));
});
test('upgrade refreshes only build identity and preserves provisioned credentials and fixtures',t=>{
  const box=sandbox(t),environment=path.join(box.dir,'suite-env.sh'),properties=path.join(box.dir,'build.properties');
  const original="export TEST_PASSWORD='owned-password'\nexport CONSULT_DEMO_NO=42\nexport DOCUMENT_DIR='/owned/store'\nexport RX_EXPECTED_BUILD_TAG='beta2 (old 1)' EXPECTED_BUILD_VERSION='beta2'\n";
  fs.writeFileSync(environment,original,{mode:0o600});
  fs.writeFileSync(properties,"build.version=beta3\nbuild.job=review's job\nbuild.number=42\n");
  const source=fs.readFileSync(SCRIPT,'utf8');
  let helper=source.slice(source.indexOf('\nrefresh_suite_build_identity() {'),source.indexOf('\ncmd_upgrade() {'));
  helper=helper.replaceAll('/root/suite-env.sh',environment).replaceAll('/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/carlos-build.properties',properties);
  const result=spawnSync('bash',['-ec',`in_container() { bash -ec "$1"; }\n${helper}\nrefresh_suite_build_identity\nrefresh_suite_build_identity\n. "$1"\nprintf '%s\\n' "$RX_EXPECTED_BUILD_TAG" "$EXPECTED_BUILD_VERSION" "$TEST_PASSWORD" "$CONSULT_DEMO_NO" "$DOCUMENT_DIR"`,'refresh',environment],{encoding:'utf8',env:box.env});
  assert.equal(result.status,0,result.stderr);
  assert.equal(result.stdout,"beta3 (review's job 42)\nbeta3\nowned-password\n42\n/owned/store\n");
  const after=fs.readFileSync(environment,'utf8');assert(after.startsWith(original.split('export RX_EXPECTED')[0]));
  assert.equal((after.match(/^export RX_EXPECTED_BUILD_TAG=/gm)||[]).length,1);
  assert.equal(fs.statSync(environment).mode&0o777,0o600);
  const upgrade=source.slice(source.indexOf('\ncmd_upgrade() {'),source.indexOf('\ncmd_audit() {'));
  assert(upgrade.indexOf('refresh_suite_build_identity')>upgrade.indexOf('upgrade-canaries.js'));
  assert(upgrade.indexOf('refresh_suite_build_identity')<upgrade.indexOf('cmd_suite --tier smoke'));
});

test('packaged suites enforce strict outcomes and fail when the required report cannot be retained', t => {
  const box = sandbox(t);
  const docker = path.join(box.dir, 'bin/docker');
  for (const copyStatus of [0, 1]) {
    fs.writeFileSync(docker, `#!/bin/sh
printf '%s\\n' "$*" >> '${box.calls}'
if [ "$1" = cp ]; then exit ${copyStatus}; fi
case "$*" in *NRestarts*) echo 0;; esac
exit 0
`, {mode:0o755});
    fs.writeFileSync(box.calls, '');
    const result = run(['suite', '--only', 'demographic-cds-import'], { ...box.env, CARLOS_DISPOSABLE_HOST:'true' });
    assert.equal(result.status, copyStatus, result.stdout + result.stderr);
    const suiteCall = fs.readFileSync(box.calls, 'utf8').split('\n').find(line => line.includes('node scripts/run-playwright-suite.js'));
    assert.ok(suiteCall);
    assert.match(suiteCall, /CARLOS_DISPOSABLE_HOST=true node scripts\/run-playwright-suite\.js/);
    assert.doesNotMatch(suiteCall, /run-installed-error-sanitization/);
    assert.match(suiteCall, /--strict-release/);
    assert.match(suiteCall, /--residue-audit/);
    if (copyStatus) assert.match(result.stdout + result.stderr, /JUnit report could not be retained/);
  }
});

test('every automatic property profile reaches the supervised Node runner on the default install',t=>{
  const box=sandbox(t);
  fs.writeFileSync(path.join(box.dir,'bin/docker'),`#!/bin/sh
printf '%s\\n' "$*" >> '${box.calls}'
case "$*" in *NRestarts*) echo 0;; esac
exit 0
`,{mode:0o755});
  for(const profile of Object.keys(require('./lib/property-profiles').PROFILES)) {
    fs.writeFileSync(box.calls,'');
    const result=run(['suite','--profile',profile],{...box.env,CARLOS_DISPOSABLE_HOST:'true'});
    assert.equal(result.status,0,profile+': '+result.stdout+result.stderr);
    assert.match(fs.readFileSync(box.calls,'utf8'),new RegExp('node scripts/run-playwright-suite\\.js --profile '+profile));
  }
});

function securityfsFixture(t,{enabled='Y',restricted='1',mounted=false,visible=true,mountFails=false,disposable=true}={}){
  const box=sandbox(t),root=path.join(box.dir,'security fs'),aa=path.join(box.dir,'enabled'),restriction=path.join(box.dir,'restricted');
  fs.mkdirSync(root);fs.writeFileSync(aa,enabled);fs.writeFileSync(restriction,restricted);
  const policy=path.join(root,'apparmor','profiles');
  const expose=()=>{fs.mkdirSync(path.dirname(policy),{recursive:true});fs.writeFileSync(policy,'carlos-emr-chromium (unconfined)\n');};
  if(mounted&&visible)expose();
  const mountCalls=path.join(box.dir,'mount-calls');
  fs.writeFileSync(path.join(box.dir,'bin','mountpoint'),'#!/bin/sh\nexit '+(mounted?'0':'1')+'\n',{mode:0o755});
  fs.writeFileSync(path.join(box.dir,'bin','mount'),`#!/bin/sh
printf '%s\\n' "$*" >> '${mountCalls}'
${mountFails?'echo "securityfs fixture mount failed" >&2; exit 1':visible?`mkdir -p '${path.dirname(policy)}'\nprintf '%s\\n' 'carlos-emr-chromium (unconfined)' > '${policy}'`:'exit 0'}
`,{mode:0o755});
  const source=fs.readFileSync(path.join(__dirname,'ci/prepare-container-securityfs.sh'),'utf8')
    .replace('enabled_path=/sys/module/apparmor/parameters/enabled',`enabled_path='${aa}'`)
    .replace('restriction_path=/proc/sys/kernel/apparmor_restrict_unprivileged_userns',`restriction_path='${restriction}'`)
    .replace('security_root=/sys/kernel/security',`security_root='${root}'`);
  const result=spawnSync('bash',['-c',source],{encoding:'utf8',env:{...box.env,CARLOS_DISPOSABLE_HOST:disposable?'true':''}});
  return {result,calls:fs.existsSync(mountCalls)?fs.readFileSync(mountCalls,'utf8'):'',root};
}
test('restricted AppArmor container exposes securityfs for the shipped browser profile',t=>{
  const {result,calls,root}=securityfsFixture(t);assert.equal(result.status,0,result.stderr);
  assert.equal(calls,`-t securityfs securityfs ${root}\n`);
});
test('visible securityfs requires no second mount',t=>{
  const {result,calls}=securityfsFixture(t,{mounted:true});assert.equal(result.status,0,result.stderr);assert.equal(calls,'');
});
test('an unavailable policy interface or a failed mount blocks qualification',t=>{
  for(const options of [{visible:false},{mounted:true,visible:false},{mountFails:true}]){
    const {result}=securityfsFixture(t,options),context=JSON.stringify(options);
    assert.notEqual(result.status,0,context);
    assert.match(result.stderr,options.mountFails?/securityfs fixture mount failed/:/AppArmor policy interface is unavailable/,context);
  }
});
test('unrestricted or disabled AppArmor requires no kernel interface change',t=>{
  for(const options of [{enabled:'N'},{restricted:'0'}]){
    const {result,calls}=securityfsFixture(t,options);assert.equal(result.status,0,result.stderr);assert.equal(calls,'');
  }
});
test('securityfs preparation refuses a target without the disposable declaration',t=>{
  const {result,calls}=securityfsFixture(t,{disposable:false});assert.notEqual(result.status,0);assert.equal(calls,'');
});
