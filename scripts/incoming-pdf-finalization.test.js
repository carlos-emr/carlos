/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { assertClassicPdfFinalized, fixturePdf } = require('./incoming-pdf-extraction-playwright-checks');

test('accepts the complete synthetic PDF used by the browser extraction workflow', () => {
  assert.doesNotThrow(() => assertClassicPdfFinalized(fixturePdf('complete-fixture')));
});

test('rejects a PDF whose writer never emitted the final trailer', () => {
  const pdf = fixturePdf('unfinished-fixture');
  assert.throws(() => assertClassicPdfFinalized(pdf.subarray(0, pdf.lastIndexOf('startxref'))),
    /no complete final trailer/);
});

test('rejects a PDF with an EOF marker but a broken cross-reference offset', () => {
  const pdf = fixturePdf('broken-xref').toString('latin1').replace(/startxref\n\d+/, 'startxref\n1');
  assert.throws(() => assertClassicPdfFinalized(Buffer.from(pdf, 'latin1')),
    /does not point to its cross-reference table/);
});

test('rejects a final cross-reference offset beyond the written PDF', () => {
  const pdf = fixturePdf('missing-xref').toString('latin1').replace(/startxref\n\d+/, 'startxref\n999999999999');
  assert.throws(() => assertClassicPdfFinalized(Buffer.from(pdf, 'latin1')),
    /does not point to its cross-reference table/);
});
