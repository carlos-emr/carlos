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

test('should pass bash syntax checking', () => {
  assert.equal(spawnSync('bash', ['-n', SCRIPT]).status, 0);
});

test('should refuse every container command without CARLOS_DISPOSABLE_HOST=true and never call docker', (t) => {
  const box = sandbox(t);
  for (const command of ['up', 'suite', 'audit', 'down']) {
    const r = run([command], { ...box.env, CARLOS_DISPOSABLE_HOST: '' });
    assert.equal(r.status, 1, `${command}: ${r.stdout}${r.stderr}`);
    assert.match(r.stderr, /refusing to run: set CARLOS_DISPOSABLE_HOST=true/);
  }
  assert.equal(fs.existsSync(box.calls), false, 'docker was called before the guard refused');
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
