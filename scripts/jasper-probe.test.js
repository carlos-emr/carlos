/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createJasperProbe } = require('./lib/jasper-probe');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'jasper-probe-test-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const webapp = path.join(root, 'webapp');
  const classes = path.join(root, 'classes');
  fs.mkdirSync(webapp); fs.mkdirSync(classes);
  const probe = createJasperProbe(webapp, classes);
  return { root, webapp, classes, probe, target: path.join(webapp, probe.filename) };
}

test('probe cleanup removes only its source and generated classes, and is repeatable', t => {
  const { classes, probe, target } = fixture(t);
  const keep = path.join(classes, 'unrelated_jsp.class');
  fs.writeFileSync(keep, 'retain');
  for (const suffix of ['.java', '.class', '$Helper.class', '.smap']) {
    fs.writeFileSync(path.join(classes, `${probe.stem}_jsp${suffix}`), 'synthetic compiled fixture');
  }
  assert.match(fs.readFileSync(target, 'utf8'), /probe-version">one/);
  probe.replace();
  assert.match(fs.readFileSync(target, 'utf8'), /probe-version">two/);
  probe.cleanup(); probe.cleanup();
  assert.equal(fs.existsSync(target), false);
  assert.deepEqual(fs.readdirSync(classes), ['unrelated_jsp.class']);
  assert.equal(fs.readFileSync(keep, 'utf8'), 'retain');
});

test('cleanup refuses externally changed source bytes', t => {
  const { probe, target } = fixture(t);
  fs.writeFileSync(target, 'external replacement');
  assert.throws(() => probe.cleanup(), /changed outside/);
  assert.equal(fs.readFileSync(target, 'utf8'), 'external replacement');
});

test('replacement and cleanup refuse a source symlink without touching its target', t => {
  const { root, probe, target } = fixture(t);
  const external = path.join(root, 'external');
  fs.writeFileSync(external, 'retain');
  fs.unlinkSync(target); fs.symlinkSync(external, target);
  assert.throws(() => probe.replace(), /was replaced/);
  assert.throws(() => probe.cleanup(), /was replaced/);
  assert.equal(fs.readFileSync(external, 'utf8'), 'retain');
});

test('cleanup refuses a generated-class symlink and retains the source for recovery', t => {
  const { root, classes, probe, target } = fixture(t);
  const external = path.join(root, 'external');
  fs.writeFileSync(external, 'retain');
  fs.symlinkSync(external, path.join(classes, `${probe.stem}_jsp.class`));
  assert.throws(() => probe.cleanup(), /compiled probe path/);
  assert.equal(fs.existsSync(target), true);
  assert.equal(fs.readFileSync(external, 'utf8'), 'retain');
});
