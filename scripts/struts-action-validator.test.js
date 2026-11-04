/* Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');
const validator = path.join(__dirname, 'validate_struts_actions.sh');

function fixture(t, files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-struts-actions-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  fs.mkdirSync(path.join(root, 'java'), { recursive: true });
  for (const [name, text] of Object.entries(files)) {
    fs.mkdirSync(path.dirname(path.join(root, name)), { recursive: true });
    fs.writeFileSync(path.join(root, name), text);
  }
  return spawnSync('bash', [validator, '--config', path.join(root, 'config/struts.xml'),
    '--java-source', path.join(root, 'java')], { encoding: 'utf8' });
}
const action = '<action name="x" class="example.Handler"/>';
const handler = 'package example; public class Handler {}';

test('Struts validator follows nested classpath includes and multiline actions without fetching DTDs', t => {
  const result = fixture(t, {
    'config/struts.xml': '<!DOCTYPE struts SYSTEM "http://127.0.0.1:9/not-fetched.dtd"><struts><include file="nested/module.xml"/></struts>',
    'config/nested/module.xml': '<struts><include file="actions.xml"/></struts>',
    'config/actions.xml': '<struts><package name="p"><!-- <action class="missing.Commented"/> --><action\n name="x"\n class="example.Handler"/></package></struts>',
    'java/example/Handler.java': handler,
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /configuration files checked: 3/);
  assert.match(result.stdout, /Total actions found: 1/);
});

for (const [name, files, error] of [
  ['empty modular config', { 'config/struts.xml': '<struts/>' }, /no action elements/],
  ['missing include', { 'config/struts.xml': '<struts><include file="missing.xml"/></struts>' }, /missing Struts configuration\/include/],
  ['missing class', { 'config/struts.xml': `<struts>${action}</struts>` }, /class not found: example.Handler/],
  ['classless action', { 'config/struts.xml': '<struts><action name="x"/></struts>' }, /missing action class/],
  ['include cycle', { 'config/struts.xml': '<struts><include file="struts.xml"/></struts>' }, /cyclic Struts include/],
  ['malformed XML', { 'config/struts.xml': '<struts><action></struts>' }, /mismatched tag/],
  ['entity declaration', { 'config/struts.xml': '<!DOCTYPE struts [<!ENTITY x "unsafe">]><struts/>' }, /entity declarations/],
  ['missing Spring component', { 'config/struts.xml': '<struts><action name="x" class="handlerBean"/></struts>' }, /Spring component not found/],
]) {
  test(`Struts validator rejects ${name}`, t => {
    const result = fixture(t, files);
    assert.ifError(result.error);
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stderr, error);
  });
}

test('Struts validator resolves literal and SPRING_BEAN_NAME components without accepting comments', t => {
  const result = fixture(t, {
    'config/struts.xml': '<struts><action name="a" class="constantBean"/><action name="b" class="literalBean"/></struts>',
    'java/example/ConstantHandler.java': 'package example; @Component(ConstantHandler.SPRING_BEAN_NAME) public class ConstantHandler { public static final String SPRING_BEAN_NAME =\n"constantBean"; }',
    'java/example/LiteralHandler.java': 'package example; @Component("literalBean") public class LiteralHandler {}',
    'java/example/Commented.java': '/* @Component("literalBean") */ public class Commented {}',
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Total actions found: 2/);
});

test('Struts validator rejects duplicate Spring component names', t => {
  const result = fixture(t, {
    'config/struts.xml': '<struts><action name="x" class="duplicateBean"/></struts>',
    'java/example/One.java': '@Component("duplicateBean") public class One {}',
    'java/example/Two.java': '@Component("duplicateBean") public class Two {}',
  });
  assert.equal(result.status, 1, result.stdout);
  assert.match(result.stderr, /Spring component ambiguous: duplicateBean/);
});
