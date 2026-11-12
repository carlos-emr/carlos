/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { preflight } = require('./report-fixed-jasper-playwright-checks');

function scratch(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'jasper-preflight-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  return directory;
}

test('bundled qualification refuses configured overrides and override qualification requires its own marker', t => {
  const directory = scratch(t);
  const env = { CARLOS_REPORT_SERVER_HOME: directory };
  assert.doesNotThrow(() => preflight('bundled', env));
  fs.writeFileSync(path.join(directory, 'Addresslabel.xml'), '<jasperReport/>');
  assert.throws(() => preflight('bundled', env), /requires no Addresslabel.xml/);
  assert.throws(() => preflight('override', env), error => error instanceof h.SkipCheck && /OVERRIDE_TEXT/.test(error.message));
  env.CARLOS_FIXED_JASPER_OVERRIDE_TEXT = 'Synthetic override';
  assert.doesNotThrow(() => preflight('override', env));
  assert.throws(() => preflight('bundled', env), /requires no Addresslabel.xml/);
});

test('missing fixtures skip but invalid configured paths fail with prerequisite guidance', t => {
  const directory = scratch(t);
  assert.throws(() => preflight('bundled', {}), error => error instanceof h.SkipCheck && /SERVER_HOME/.test(error.message));
  const env = { CARLOS_REPORT_SERVER_HOME: directory };
  assert.throws(() => preflight('override', env), error => error instanceof h.SkipCheck && /installed Addresslabel.xml/.test(error.message));
  assert.throws(() => preflight('bundled', { CARLOS_REPORT_SERVER_HOME: path.join(directory, 'missing') }),
    error => !(error instanceof h.SkipCheck) && /accessible server user.home directory/.test(error.message) && !/ENOENT/.test(error.message));
  fs.mkdirSync(path.join(directory, 'Addresslabel.xml'));
  assert.throws(() => preflight('override', env), /regular file/);
});

test('both entry points register environment prerequisites before SQL setup', () => {
  for (const script of ['report-fixed-jasper-playwright-checks.js', 'report-configured-jasper-playwright-checks.js']) {
    const filename = path.join(__dirname, script);
    const entry = { exports: {} };
    let registration;
    const load = createRequire(filename);
    const loader = name => {
      if (name === './lib/workflow-session') return { runWorkflow(_name, _workflow, options) { registration = options; } };
      if (name === './report-fixed-jasper-playwright-checks') return { overrideWorkflow() {}, preflight: mode => preflight(mode, {}) };
      return load(name);
    };
    loader.main = entry;
    vm.runInNewContext(fs.readFileSync(filename, 'utf8'), { require: loader, module: entry, process: { env: {} } });
    assert.equal(typeof registration.preflightConfig, 'function');
    assert.equal(registration.preflight, undefined);
    assert.throws(() => registration.preflightConfig(), error => error instanceof h.SkipCheck);
  }
});

test('a host without Poppler reports the shared actionable SKIP', t => {
  const directory = scratch(t);
  const program = `const h=require('./scripts/lib/playwright-harness');
    try { require('./scripts/report-fixed-jasper-playwright-checks').preflight('bundled',{}); process.exitCode=1; }
    catch(error) { if(!(error instanceof h.SkipCheck)||!error.message.includes('Poppler pdftotext')) throw error; }`;
  const result = spawnSync(process.execPath, ['-e', program], {
    cwd: path.join(__dirname, '..'), env: { ...process.env, PATH: directory }, encoding: 'utf8', timeout: 15000,
  });
  assert.equal(result.status, 0, result.stderr);
});
