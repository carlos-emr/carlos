/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * Read a growth-chart PDF the way a clinician sees it, from the bytes the application answered.
 *
 * WHY. The Rourke, Growth Chart and Growth 0-36 forms answer a "graph" as a PDF: FrmPDFServlet
 * lays the patient's measurements over a WHO percentile template (the template is an imported page
 * and holds the curves; the page's OWN content stream holds only what the servlet added: the text and
 * one small stroked circle per plotted point). A browser handed a PDF shows its viewer, whose DOM
 * says nothing, and a PDF whose circles are missing is still a "valid PDF" that pdftotext can read
 * the template from. So a check that asked only for `%PDF` would pass a chart with nothing plotted.
 * Two independent reads here:
 *   plottedPoints  counts the circles the servlet drew, per page, straight from the content streams
 *                  (no rendering, so it does not depend on a resolution or a colour threshold);
 *   renderPng      draws a page with Poppler's pdftoppm, so the check can assert that what the
 *                  clinician would see is a non-trivial image (a PNG with the right signature that is
 *                  not the near-empty file a blank page renders to).
 * Nothing here talks to the application.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const { execFileSync } = require('node:child_process');

const TOOL_TIMEOUT = 30000;
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/** Whether the bytes start with the eight-byte PNG signature and are more than a header. */
function isPng(bytes) {
  return Buffer.isBuffer(bytes) && bytes.length > PNG_SIGNATURE.length && bytes.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE);
}

/** Every Flate stream of a PDF, inflated, in file order. A stream that does not inflate (an image, a font) is skipped. */
function contentStreams(pdf) {
  const text = pdf.toString('latin1');
  const streams = [];
  const open = /stream\r?\n/g;
  let match;
  while ((match = open.exec(text)) !== null) {
    const start = match.index + match[0].length;
    const end = text.indexOf('endstream', start);
    if (end < 0) break;
    // Resume AFTER the closing keyword: `endstream` itself contains `stream` and a line end, which would match as an opening.
    open.lastIndex = end + 'endstream'.length;
    try {
      streams.push(zlib.inflateSync(pdf.subarray(start, end)).toString('latin1'));
    } catch {
      // Not Flate (or not a stream the servlet wrote): nothing to count in it.
    }
  }
  return streams;
}

// A Bezier curve operator: six numbers then `c`. OpenPDF's PdfContentByte.circle() writes four of them.
const CURVE = /(?:^|\n)(?:-?\d+(?:\.\d+)?\s+){6}c(?=\s|$)/g;

// The line FrmPDFServlet writes to place the imported template page (`q 1 0 0 1 0 0 cm /Xf1 Do Q`): it marks a
// page's own content stream. The template's own streams (curves, text) are not counted, and nor is a font's.
const TEMPLATE_PLACEMENT = /\/Xf\d+\s+Do\b/;

/**
 * The circles drawn on each page by the servlet: one entry per page content stream, in page order.
 *
 * @param {Buffer} pdf a complete PDF written by FrmPDFServlet / OpenPDF (Flate content streams)
 * @returns {number[]} circles per page; empty for a PDF the servlet did not write
 */
function plottedPoints(pdf) {
  return contentStreams(pdf)
    .filter(stream => TEMPLATE_PLACEMENT.test(stream))
    .map(stream => Math.floor((stream.match(CURVE) || []).length / 4));
}

/** Runs `body(file)` with the bytes written to a private temporary file, and removes it afterwards. */
function withTempFile(bytes, name, body) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-pdf-graph-'));
  try {
    const file = path.join(dir, name);
    fs.writeFileSync(file, bytes);
    return body(file, dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

/** The number of pages pdfinfo reports. */
function pageCount(pdf) {
  return withTempFile(pdf, 'in.pdf', file => {
    const out = execFileSync('pdfinfo', [file], { encoding: 'utf8', timeout: TOOL_TIMEOUT });
    const found = /^Pages:\s+(\d+)/m.exec(out);
    if (!found) throw new Error('pdfinfo reported no page count');
    return Number(found[1]);
  });
}

/** The document title pdfinfo reports ('' when the PDF carries none): what a browser's PDF viewer shows as the window title. */
function pdfTitle(pdf) {
  return withTempFile(pdf, 'in.pdf', file => {
    const out = execFileSync('pdfinfo', ['-enc', 'UTF-8', file], { encoding: 'utf8', timeout: TOOL_TIMEOUT });
    const found = /^Title:[ \t]*(.*)$/m.exec(out);
    return found ? found[1].trim() : '';
  });
}

/** The text of one page (1-based) in reading order, from the bytes. */
function pageText(pdf, page) {
  return execFileSync('pdftotext', ['-f', String(page), '-l', String(page), '-enc', 'UTF-8', '-', '-'],
    { input: pdf, encoding: 'utf8', timeout: TOOL_TIMEOUT, maxBuffer: 16 * 1024 * 1024 });
}

const ENTITIES = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'" };

/**
 * The words of one page (1-based) with the box each occupies, from `pdftotext -bbox`. The box is in the page's own units
 * measured from the TOP left corner, which for a Jasper page of pixels is the template's own x and y.
 *
 * @param {Buffer} pdf
 * @param {number} page
 * @returns {{width: number, height: number, words: Array<{text: string, xMin: number, yMin: number, xMax: number, yMax: number}>}}
 */
function pageWords(pdf, page) {
  const html = execFileSync('pdftotext', ['-bbox', '-f', String(page), '-l', String(page), '-enc', 'UTF-8', '-', '-'],
    { input: pdf, encoding: 'utf8', timeout: TOOL_TIMEOUT, maxBuffer: 16 * 1024 * 1024 });
  const size = /<page width="([\d.]+)" height="([\d.]+)"/.exec(html);
  if (!size) throw new Error('pdftotext -bbox reported no page');
  const words = [];
  const word = /<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">([^<]*)<\/word>/g;
  let match;
  while ((match = word.exec(html)) !== null) {
    words.push({
      text: match[5].replace(/&(?:amp|lt|gt|quot|#39);/g, entity => ENTITIES[entity]),
      xMin: Number(match[1]), yMin: Number(match[2]), xMax: Number(match[3]), yMax: Number(match[4]),
    });
  }
  return { width: Number(size[1]), height: Number(size[2]), words };
}

/**
 * The words whose centre lies inside a box (x, y, width, height from the top left), grown by `slack` on every side, left to right.
 *
 * @param {Array<{text: string, xMin: number, yMin: number, xMax: number, yMax: number}>} words  pageWords().words
 * @param {{x: number, y: number, width: number, height: number}} box
 * @param {number} [slack]
 */
function wordsIn(words, box, slack = 0) {
  return words
    .filter(word => {
      const cx = (word.xMin + word.xMax) / 2;
      const cy = (word.yMin + word.yMax) / 2;
      return cx >= box.x - slack && cx <= box.x + box.width + slack && cy >= box.y - slack && cy <= box.y + box.height + slack;
    })
    .sort((a, b) => a.xMin - b.xMin);
}

/**
 * One page drawn as a PNG by pdftoppm.
 *
 * @param {Buffer} pdf
 * @param {{page?: number, dpi?: number}} [options] page is 1-based; 40 dpi keeps a letter page under 100 KB
 * @returns {Buffer} the PNG file
 */
function renderPng(pdf, { page = 1, dpi = 40 } = {}) {
  return withTempFile(pdf, 'in.pdf', (file, dir) => {
    const base = path.join(dir, 'page');
    execFileSync('pdftoppm', ['-png', '-r', String(dpi), '-f', String(page), '-l', String(page), '-singlefile', file, base],
      { timeout: TOOL_TIMEOUT });
    return fs.readFileSync(`${base}.png`);
  });
}

module.exports = { PNG_SIGNATURE, contentStreams, isPng, pageCount, pageText, pageWords, pdfTitle, plottedPoints, renderPng, wordsIn };
