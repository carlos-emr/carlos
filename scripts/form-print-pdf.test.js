/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const fixture = fs.readFileSync(path.join(__dirname, '../src/test/resources/pdf/unicode-clinical-print.pdf'));

const { CLIENT_REFERENCE, pdfText } = require('./form-print-pdf-playwright-checks');

test('reads form markers and Unicode from an embedded-font PDF', () => {
  const text = pdfText(fixture);
  assert.match(text, /PW_FORM_PRINT_42/);
  assert.match(text, /Nguyễn Łukasz İstanbul ≥ 5 ≤ 9/);
});

test('reads all pages and preserves escaped PDF string characters', () => {
  const text = pdfText(fixture);
  for (const page of ['FIRST PAGE', 'SECOND PAGE', 'THIRD PAGE']) assert.ok(text.includes(page));
  assert.ok(text.includes('Dose (2 tabs) daily; after ) LAST PAGE'));
});

test('rejects malformed downloads instead of reporting an empty form', () => {
  assert.throws(() => pdfText(Buffer.from('<html>error</html>')), /not a PDF/);
  assert.throws(() => pdfText(fixture.subarray(0, fixture.length - 30)), /truncated/);
});

test('the client reference matches the rendered form and captures its whole id', () => {
  assert.equal(CLIENT_REFERENCE.exec(pdfText(fixture))[1], '30');
  assert.equal(CLIENT_REFERENCE.exec('Client Reference No. : 30')[1], '30');
  assert.equal(CLIENT_REFERENCE.exec('M9A 3N5'), null);
  assert.equal(CLIENT_REFERENCE.exec('30'), null);
  assert.equal(CLIENT_REFERENCE.exec('Client Reference No.:3')[1], '3');
});
