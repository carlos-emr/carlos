/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute the actual fixture reader without launching its browser driver.
const source = fs.readFileSync(path.join(__dirname, 'rx-fax-reprint-represcribe-playwright-checks.js'), 'utf8');
const start = source.indexOf('function prescriptionSnapshot(');
const end = source.indexOf('function prescriptionCount(', start);
assert.ok(start >= 0 && end > start);
for (const [label, row, expected] of [
  ['unsigned', '\t2026-09-18\t999998\n', ['', '2026-09-18', '999998']],
  ['undated', '7\t\t999998\n', ['7', '', '999998']],
  ['missing provider', '7\t2026-09-18\t\n', ['7', '2026-09-18', '']],
]) {
  test(`Rx fixture preserves empty columns: ${label}`, () => {
    const context = vm.createContext({ sql: () => row });
    vm.runInContext(source.slice(start, end), context);
    const result = context.prescriptionSnapshot('42');
    assert.deepEqual([result.signatureId, result.datePrescribed, result.prescriber], expected);
  });
}
