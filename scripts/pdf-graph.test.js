/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const test = require('node:test');
const zlib = require('node:zlib');
const pdfGraph = require('./lib/pdf-graph');

/*
 * lib/pdf-graph.js counts what FrmPDFServlet plotted from the bytes of a growth-chart PDF and draws a page as a PNG.
 * These tests build small PDFs the way the servlet's output is shaped: a page content stream that places the imported
 * template (`/Xf1 Do`), writes some text, and then draws one circle per point (four Bezier curves each), next to the
 * template's own stream, which also holds curves and must not be counted.
 */

/** A circle as OpenPDF's PdfContentByte.circle() writes it: a move and four curves, then the stroke. */
function circle(x, y) {
  return `${x + 1.5} ${y} m\n${x + 1.5} ${y + 0.8} ${x + 0.8} ${y + 1.5} ${x} ${y + 1.5} c\n${x - 0.8} ${y + 1.5} ${x - 1.5} ${y + 0.8} ${x - 1.5} ${y} c\n`
    + `${x - 1.5} ${y - 0.8} ${x - 0.8} ${y - 1.5} ${x} ${y - 1.5} c\n${x + 0.8} ${y - 1.5} ${x + 1.5} ${y - 0.8} ${x + 1.5} ${y} c\nS\n`;
}

function pageContent(template, points) {
  const draws = points.map(([x, y]) => circle(x, y)).join('');
  return `q\nBT\n36 756 Td\nET\nQ\nq 1 0 0 1 0 0 cm /Xf${template} Do Q\n0 0 1 RG\nBT\n/F1 6 Tf\n1 0 0 1 10 10 Tm\n(x)Tj\nET\n${draws}`;
}

/** A valid one-or-more page PDF whose page content streams are the given texts, plus a template stream full of curves. */
function buildPdf(pageTexts) {
  const objects = [];
  const count = pageTexts.length;
  const pageIds = pageTexts.map((_, i) => 3 + i * 2);
  objects[1] = '<< /Type /Catalog /Pages 2 0 R >>';
  objects[2] = `<< /Type /Pages /Kids [${pageIds.map(id => `${id} 0 R`).join(' ')}] /Count ${count} >>`;
  pageTexts.forEach((text, i) => {
    const id = 3 + i * 2;
    objects[id] = `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents ${id + 1} 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>`;
    objects[id + 1] = { stream: Buffer.from(text, 'latin1') };
  });
  // The template's own stream: text and many curves, never part of the count.
  objects[3 + count * 2] = { stream: Buffer.from(`BT\n/F1 6 Tf\n(percentile)Tj\nET\n${circle(5, 5).repeat(10)}`, 'latin1') };
  const chunks = [Buffer.from('%PDF-1.4\n', 'latin1')];
  const offsets = [];
  let length = chunks[0].length;
  for (let id = 1; id < objects.length; id++) {
    offsets[id] = length;
    const object = objects[id];
    const body = typeof object === 'string'
      ? Buffer.from(`${id} 0 obj\n${object}\nendobj\n`, 'latin1')
      : (() => {
        const deflated = zlib.deflateSync(object.stream);
        return Buffer.concat([Buffer.from(`${id} 0 obj\n<< /Filter /FlateDecode /Length ${deflated.length} >>\nstream\n`, 'latin1'), deflated, Buffer.from('\nendstream\nendobj\n', 'latin1')]);
      })();
    chunks.push(body);
    length += body.length;
  }
  const xref = [`xref\n0 ${objects.length}\n0000000000 65535 f \n`];
  for (let id = 1; id < objects.length; id++) xref.push(`${String(offsets[id]).padStart(10, '0')} 00000 n \n`);
  chunks.push(Buffer.from(`${xref.join('')}trailer\n<< /Size ${objects.length} /Root 1 0 R >>\nstartxref\n${length}\n%%EOF\n`, 'latin1'));
  return Buffer.concat(chunks);
}

const poppler = (() => {
  try { execFileSync('pdftoppm', ['-v'], { stdio: 'pipe' }); execFileSync('pdfinfo', ['-v'], { stdio: 'pipe' }); return true; } catch { return false; }
})();

test('shouldCountOneCirclePerPoint_forEachPageTheServletWrote', () => {
  const pdf = buildPdf([pageContent(1, [[10, 10], [20, 30], [40, 50]]), pageContent(2, [])]);
  assert.deepEqual(pdfGraph.plottedPoints(pdf), [3, 0]);
});

test('shouldNotCountTheTemplatesOwnCurves_whenNothingWasPlotted', () => {
  assert.deepEqual(pdfGraph.plottedPoints(buildPdf([pageContent(1, [])])), [0]);
});

test('shouldReportNoPages_forAPdfTheServletDidNotWrite', () => {
  const pdf = buildPdf([`BT\n/F1 6 Tf\n(Jasper)Tj\nET\n${circle(1, 1)}`]);
  assert.deepEqual(pdfGraph.plottedPoints(pdf), []);
});

test('shouldSkipAStreamThatIsNotFlate_andKeepCounting', () => {
  const real = buildPdf([pageContent(1, [[10, 10]])]);
  const noise = Buffer.from('1 0 obj\n<< /Length 4 >>\nstream\nJUNK\nendstream\nendobj\n', 'latin1');
  assert.deepEqual(pdfGraph.plottedPoints(Buffer.concat([noise, real])), [1]);
});

test('shouldRecogniseOnlyAPngSignature', () => {
  assert.equal(pdfGraph.isPng(Buffer.concat([pdfGraph.PNG_SIGNATURE, Buffer.from([0, 0, 0, 13])])), true);
  assert.equal(pdfGraph.isPng(pdfGraph.PNG_SIGNATURE), false, 'a signature with nothing after it is not an image');
  assert.equal(pdfGraph.isPng(Buffer.from('<html>error</html>')), false);
  assert.equal(pdfGraph.isPng(null), false);
});

test('shouldCountPagesAndDrawAPng_whenPopplerIsInstalled', { skip: poppler ? false : 'Poppler is not installed' }, () => {
  const pdf = buildPdf([pageContent(1, [[10, 10]]), pageContent(2, [])]);
  assert.equal(pdfGraph.pageCount(pdf), 2);
  const png = pdfGraph.renderPng(pdf, { page: 1, dpi: 20 });
  assert.equal(pdfGraph.isPng(png), true);
});
