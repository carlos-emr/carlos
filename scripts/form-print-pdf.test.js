/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The Print Pdf check decides whether a printed requisition is real or blank by reading the text
 * drawn inside the returned PDF, so the extraction that answers that question is worth asserting
 * on its own.
 *
 * Getting it wrong is silent in the worst direction: an extractor that finds nothing makes every
 * run fail with "the print lost its configuration", and one that finds text where there is none
 * would pass over the blank page the check exists to catch.
 */

const test = require('node:test');
const assert = require('node:assert');
const zlib = require('zlib');

const { CLIENT_REFERENCE, pdfText } = require('./form-print-pdf-playwright-checks');

/** A buffer shaped like the parts of a PDF this extractor looks at. */
function pdf(...streams) {
  const parts = ['%PDF-2.0\n'];
  for (const { body, compress = true } of streams) {
    const payload = compress ? zlib.deflateSync(Buffer.from(body, 'latin1')) : Buffer.from(body, 'latin1');
    parts.push('4 0 obj\n<< /Length 0 >>\nstream\n');
    parts.push(payload.toString('latin1'));
    parts.push('\nendstream\nendobj\n');
  }
  parts.push('%%EOF');
  return Buffer.from(parts.join(''), 'latin1');
}

test('reads the strings iText draws with Tj', () => {
  const buffer = pdf({ body: 'BT /F1 8 Tf 22 200 Td (PW_FORM_PRINT_1790000000000) Tj ET' });
  assert.match(pdfText(buffer), /PW_FORM_PRINT_1790000000000/);
});

test('reads every stream, not only the first', () => {
  const buffer = pdf(
    { body: '(page one) Tj' },
    { body: '(Client Reference No.:30) Tj' },
  );
  const drawn = pdfText(buffer);
  assert.match(drawn, /page one/);
  assert.match(drawn, /Client Reference No\.:30/);
});

test('skips a stream it cannot inflate instead of failing on it', () => {
  // An image or font stream is not Flate text; missing it must not cost the streams around it.
  const buffer = pdf(
    { body: '\x89PNG\r\n\x1a\n not deflate data', compress: false },
    { body: '(survived) Tj' },
  );
  assert.match(pdfText(buffer), /survived/);
});

test('keeps an escaped parenthesis inside a drawn string', () => {
  const buffer = pdf({ body: '(Dose \\(2 tabs\\) daily) Tj' });
  assert.match(pdfText(buffer), /Dose \(2 tabs\) daily/);
});

test('an escaped parenthesis does not end the string early', () => {
  // The naive /\([^)]*\)/ would stop at the first "\)" and lose everything after it, which is
  // exactly where a stamp or a client reference would sit.
  const buffer = pdf({ body: '(before \\) PW_FORM_PRINT_42) Tj' });
  assert.match(pdfText(buffer), /PW_FORM_PRINT_42/);
});

test('finds nothing in a PDF that draws nothing', () => {
  // What FrmPDFServlet returns when __cfgfile never reaches it: a valid PDF with no placed text.
  assert.strictEqual(pdfText(pdf()).trim(), '');
});

test('the client reference matches the rendered form and captures its id', () => {
  assert.strictEqual(CLIENT_REFERENCE.exec('Client Reference No.:30')[1], '30');
  assert.strictEqual(CLIENT_REFERENCE.exec('Client Reference No. : 30')[1], '30');
  // The label is required, so a bare number elsewhere on the form is not mistaken for one.
  assert.strictEqual(CLIENT_REFERENCE.exec('M9A 3N5'), null);
  assert.strictEqual(CLIENT_REFERENCE.exec('30'), null);
});

test('the captured id is compared whole, not as a prefix', () => {
  // 3 must not satisfy a check for row 30, which an unanchored substring match would allow.
  const drawn = 'Client Reference No.:3\nother text';
  assert.notStrictEqual(CLIENT_REFERENCE.exec(drawn)[1], '30');
  assert.strictEqual(CLIENT_REFERENCE.exec(drawn)[1], '3');
});
