/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('fax cancellation, cross-session, replay and cleanup probes use the newly saved revision', () => {
  const source = fs.readFileSync(path.join(__dirname, 'eform-saved-render-playwright-checks.js'), 'utf8');
  const helper = source.indexOf('async function checkOwnedFaxPreview(');
  const start = source.indexOf('const form = overrides =>', helper);
  const end = source.indexOf('const readCsrfToken =', start);
  assert.ok(helper >= 0 && start > helper && end > start);
  // Execute the actual request builder with different opened and newly prepared IDs.
  const form = vm.runInNewContext(`${source.slice(start, end)}; form`, {
    fdid: '101', previewTransactionId: '202', demographicNo: '1',
    csrfToken: 'owner-token', faxFilePath: '/tmp/carlos-temp/owned.pdf',
  });
  for (const overrides of [{}, { demographicNo: '2' }, { 'CSRF-TOKEN': 'other-session-token' }, {}]) {
    const request = form(overrides);
    assert.equal(request.transactionId, '202');
    assert.equal(request.method, 'cancel');
    assert.equal(request.transactionType, 'EFORM');
    assert.equal(request.faxFilePath, '/tmp/carlos-temp/owned.pdf');
    assert.equal(request.demographicNo, overrides.demographicNo || '1');
    assert.equal(request['CSRF-TOKEN'], overrides['CSRF-TOKEN'] || 'owner-token');
  }
});
