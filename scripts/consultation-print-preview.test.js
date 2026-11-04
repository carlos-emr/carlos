/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const fixture = fs.readFileSync(path.join(__dirname, '../src/test/resources/pdf/unicode-clinical-print.pdf'));

const { pdfText } = require('./consultation-print-preview-playwright-checks');

test('reads Unicode text through embedded font mappings', () => {
  assert.match(pdfText(fixture), /Nguyễn Łukasz İstanbul ≥ 5 ≤ 9/);
});

test('reads every page so absence assertions cannot overlook later content', () => {
  const text = pdfText(fixture);
  for (const page of ['FIRST PAGE', 'SECOND PAGE', 'THIRD PAGE', 'LAST PAGE']) assert.ok(text.includes(page));
});

test('preserves parentheses while reflowing page whitespace', () => {
  const text = pdfText(fixture);
  assert.ok(text.includes('Dose (2 tabs) daily; after ) LAST PAGE'));
  assert.ok(!/[\r\n\f]/.test(text));
});

test('rejects non-PDF responses instead of treating them as empty previews', () => {
  assert.throws(() => pdfText(Buffer.from('<html>error</html>')), /not a PDF/);
});

test('rejects truncated PDFs instead of silently omitting text', () => {
  assert.throws(() => pdfText(fixture.subarray(0, fixture.length - 30)), /truncated/);
});
