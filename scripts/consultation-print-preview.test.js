/**
 * Unit tests for the consultation print-preview check's PDF text reader.
 *
 * These pin `pdfText` because the browser check's strongest assertion is an ABSENCE one -- that
 * the preview no longer contains the text that was typed over. A reader that returned less than
 * the document's text would make that assertion pass for the wrong reason, and the browser check
 * could not tell the difference. These are characterization tests: they record what the reader
 * does today so that a future edit to it has to be deliberate.
 */
const test = require('node:test');
const assert = require('node:assert');
const zlib = require('zlib');

const { pdfText } = require('./consultation-print-preview-playwright-checks.js');

/** A minimal PDF carrying one Flate content stream per supplied string. */
function pdfWith(...bodies) {
  const parts = ['%PDF-1.4\n'];
  for (const body of bodies) {
    const deflated = zlib.deflateSync(Buffer.from(body, 'latin1')).toString('latin1');
    parts.push(`<< /Length ${deflated.length} /Filter /FlateDecode >>\nstream\n${deflated}endstream\n`);
  }
  parts.push('%%EOF\n');
  return Buffer.from(parts.join(''), 'latin1');
}

test('pdfText', async (t) => {
  await t.test('should read the drawn text_forOneStreamDocument', () => {
    assert.match(pdfText(pdfWith('(REASON ALPHA) Tj')), /REASON ALPHA/);
  });

  await t.test('should read every content stream_whenSeveralArePresent', () => {
    // A multi-page referral puts its later pages in later streams. If any of them were invisible
    // to the reader, the browser check's "typed-over text is gone" assertion would be worth
    // nothing for text on those pages.
    const text = pdfText(pdfWith('(FIRST PAGE) Tj', '(SECOND PAGE) Tj', '(THIRD PAGE) Tj'));
    assert.match(text, /FIRST PAGE/);
    assert.match(text, /SECOND PAGE/);
    assert.match(text, /THIRD PAGE/);
  });

  await t.test('should keep reading later streams_whenOneDoesNotInflate', () => {
    // Fonts and images are not Flate text; one of them must not cost the rest of the file.
    const good = zlib.deflateSync(Buffer.from('(AFTER THE FONT) Tj', 'latin1')).toString('latin1');
    const raw = Buffer.from(
      `%PDF-1.4\nstream\n\x00\x01\x02 not deflate at all\nendstream\nstream\n${good}endstream\n%%EOF\n`,
      'latin1',
    );
    assert.match(pdfText(raw), /AFTER THE FONT/);
  });

  await t.test('should return the empty string_forADocumentWithNoStreams', () => {
    assert.strictEqual(pdfText(Buffer.from('%PDF-1.4\n%%EOF\n', 'latin1')), '');
  });

  await t.test('should not throw_whenAStreamIsNeverClosed', () => {
    assert.doesNotThrow(() => pdfText(Buffer.from('%PDF-1.4\nstream\nnever closed', 'latin1')));
  });
});
