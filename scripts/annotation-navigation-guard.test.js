/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, 'e2e/fax/annotate-document-playwright-checks.js'), 'utf8');

// Execute the real validation/navigation boundary without booting a browser,
// loading private credentials or running the clinical fixture workflow.
function navigation(env = {}) {
  const context = vm.createContext({URL, process: {env}});
  for (const name of ['validateBaseUrl', 'navigateAnnotationViewer']) {
    const marker = name === 'validateBaseUrl' ? 'function ' : 'async function ';
    const start = source.indexOf(marker + name + '(');
    const end = source.indexOf('\n}', start) + 2;
    assert.ok(start >= 0 && end > start, `missing real ${name} implementation`);
    vm.runInContext(source.slice(start, end), context);
  }
  const calls = [];
  const page = {goto: async (url, options) => {calls.push({url, options}); return 'response';}};
  return {calls, go: (base, id, until) => context.navigateAnnotationViewer(page, base, id, until)};
}

test('annotation navigation preserves the validated application origin/path and only the fixed route/integer query', async () => {
  const n = navigation();
  assert.equal(await n.go('https://192.168.2.10:8443/team/carlos/?ignored=1#fragment', 42, 'domcontentloaded'), 'response');
  assert.equal(n.calls[0].url, 'https://192.168.2.10:8443/team/carlos/documentManager/AnnotateDocument?docId=42');
  assert.equal(n.calls[0].options.waitUntil, 'domcontentloaded');
  await n.go('http://[::1]:8080/carlos', '7');
  assert.equal(n.calls[1].options.waitUntil, 'load');
});

test('DNS names resembling private IPv4 ranges require explicit remote-target opt-in', async () => {
  const n = navigation();
  for (const host of ['10.attacker.invalid', '10.1.2.3.attacker.invalid', '192.168.attacker.invalid',
    '172.16.attacker.invalid', '172.31.4.5.attacker.invalid', 'localhost.attacker.invalid', '172.32.0.1', '192.169.1.1']) {
    await assert.rejects(n.go(`http://${host}/carlos`, '1'), /Refusing non-local BASE_URL/);
  }
  assert.equal(n.calls.length, 0, 'no rejected target may reach credentialed browser navigation');
});

test('actual private IPv4 ranges and explicit development hosts remain usable', async () => {
  const n = navigation();
  for (const host of ['10.0.0.1', '192.168.255.254', '172.16.0.1', '172.31.255.254', 'localhost', 'carlos', 'host.docker.internal']) {
    await n.go(`http://${host}:8080/carlos`, '1');
  }
  assert.equal(n.calls.length, 7);
});

test('remote-target opt-in permits HTTPS remote deployments but never credentials or non-web protocols', async () => {
  const n = navigation({ALLOW_NON_LOCAL_BASE_URL: 'true'});
  await n.go('https://review.example.invalid/carlos', 9);
  assert.equal(n.calls.length, 1);
  for (const base of ['javascript:alert(1)', 'file:///tmp/carlos', 'ftp://example.invalid/carlos',
    'https://user:secret@example.invalid/carlos', 'http://user:secret@127.0.0.1/carlos']) {
    await assert.rejects(n.go(base, '1'), /BASE_URL must/);
  }
  assert.equal(n.calls.length, 1, 'opt-in must not bypass scheme or credential validation');
});

test('viewer document IDs cannot inject a route/query, omit identity or select invalid numeric values', async () => {
  const n = navigation();
  for (const id of ['', 0, -1, 1.5, null, undefined, '1&docId=2', '1#fragment', '//elsewhere.invalid', '../1', '1e3']) {
    await assert.rejects(n.go('http://localhost:8080/carlos', id), /positive integer/);
  }
  assert.equal(n.calls.length, 0);
});
