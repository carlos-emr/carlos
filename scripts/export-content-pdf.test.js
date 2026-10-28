/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pdfText, pdfTextBuffer, assertEmbeddedFonts, squash } = require('./lib/export-content-helpers');
const lab = path.join(__dirname, '../src/test/resources/pdf/unicode-lab-print.pdf');

test('content order preserves wrapped accession hyphens without interleaving adjacent columns', () => {
  const text = pdfText(lab, { raw: true });
  assert.ok(squash(text).includes('EXPORT-9f05fb7fae'));
  assert.ok(squash(text).includes('FAKE-PW8a68869f05fb7fae'));
  assert.ok(squash(text).includes(squash('Spécimen hémolysé — recoller')));
  assert.match(text, /Value ≥ 5 µg\/L/);
  assert.match(text, /and ≤ 9, Łódź/);
});

test('layout order keeps result values and abnormal flags on their rows', () => {
  const text = pdfText(lab, { layout: true });
  assert.match(text, /Hemoglobin\s+98\s+L\s+120-160\s+g\/L/);
  assert.match(text, /Platelets\s+450\s+H\s+150-400/);
});

test('layout extraction from downloaded PDF bytes keeps values in their columns', () => {
  const text = pdfTextBuffer(fs.readFileSync(lab), { layout: true });
  assert.match(text, /Hemoglobin\s+98\s+L\s+120-160\s+g\/L/);
  assert.match(text, /Platelets\s+450\s+H\s+150-400/);
});

test('the clinical PDF embeds every font', () => {
  assert.doesNotThrow(() => assertEmbeddedFonts(lab));
});


test('buffer PDF layout order keeps values and abnormal flags on their rendered rows', () => {
  const text = pdfTextBuffer(fs.readFileSync(lab), { layout: true });
  assert.match(text, /Hemoglobin\s+98\s+L\s+120-160\s+g\/L/);
  assert.match(text, /Platelets\s+450\s+H\s+150-400/);
});

test('buffer PDF raw order preserves wrapped identifiers across adjacent columns', () => {
  const text = pdfTextBuffer(fs.readFileSync(lab), { raw: true });
  assert.ok(squash(text).includes('EXPORT-9f05fb7fae'));
  assert.ok(squash(text).includes('FAKE-PW8a68869f05fb7fae'));
});
