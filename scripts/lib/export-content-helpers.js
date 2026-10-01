/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Helpers for the export-content checks: save what the browser downloads, then read the file back
 * the way a recipient would (CSV parse, XLSX cell grid, pdftotext) so a check can compare the CONTENT
 * with the database rather than only the headers. Nothing here talks to the application.
 */
'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const { execFileSync } = require('node:child_process');
const h = require('./playwright-harness');

const TOOL_TIMEOUT = 20000;

/** A private scratch directory; the caller registers its removal with s.cleanup. */
function scratchDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-export-content-'));
}

/**
 * Runs `trigger` (a real click) and saves the file the browser downloads. `page` is the page whose
 * 'download' event fires (the opener, also for a click inside an iframe). Also records the response
 * headers of the request that produced the file when `route` matches its path.
 */
async function saveDownload(page, scratch, trigger, { route, timeout = 40000 } = {}) {
  const downloading = page.waitForEvent('download', { timeout });
  downloading.catch(() => {});
  const responding = route
    ? page.context().waitForEvent('response', { predicate: r => route.test(new URL(r.url()).pathname), timeout })
    : null;
  if (responding) responding.catch(() => {});
  await trigger();
  const download = await downloading;
  const file = path.join(scratch, `${Date.now()}-${download.suggestedFilename().replace(/[^\w.-]/g, '_')}`);
  await download.saveAs(file);
  const failure = await download.failure();
  h.assert(!failure, `The download failed (${failure})`);
  const response = responding ? await responding : null;
  return {
    file,
    name: download.suggestedFilename(),
    bytes: fs.readFileSync(file),
    headers: response ? response.headers() : {},
    status: response ? response.status() : null,
  };
}

/** Minimal RFC 4180 reader (quoted fields, doubled quotes, embedded commas and newlines). */
function parseCsv(text, delimiter = ',') {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; } else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === delimiter) { row.push(field); field = ''; } else if (c === '\r' || c === '\n') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  return rows;
}

/** Every entry of a ZIP (stored or deflated) as { name -> Buffer }, read from the central directory. */
function unzip(buffer) {
  let eocd = -1;
  for (let i = buffer.length - 22; i >= Math.max(0, buffer.length - 70000); i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  h.assert(eocd >= 0, 'The file is not a ZIP archive (no end-of-central-directory record)');
  const count = buffer.readUInt16LE(eocd + 10);
  let offset = buffer.readUInt32LE(eocd + 16);
  const entries = {};
  for (let n = 0; n < count; n++) {
    h.assert(buffer.readUInt32LE(offset) === 0x02014b50, 'The ZIP central directory is damaged');
    const method = buffer.readUInt16LE(offset + 10);
    const compressed = buffer.readUInt32LE(offset + 20);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const local = buffer.readUInt32LE(offset + 42);
    const name = buffer.subarray(offset + 46, offset + 46 + nameLength).toString('utf8');
    const dataStart = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
    const raw = buffer.subarray(dataStart, dataStart + compressed);
    entries[name] = method === 0 ? Buffer.from(raw) : zlib.inflateRawSync(raw);
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function xmlText(value) {
  return value.replace(/&#x([0-9a-f]+);/gi, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec) => String.fromCodePoint(Number(dec)))
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, '\'').replace(/&amp;/g, '&');
}

/** The first worksheet of an .xlsx as an array of rows of strings (blank cells are ''). */
function xlsxRows(buffer) {
  const entries = unzip(buffer);
  h.assert(entries['xl/workbook.xml'], 'The file is a ZIP but not an Excel workbook');
  const shared = [];
  if (entries['xl/sharedStrings.xml']) {
    for (const si of entries['xl/sharedStrings.xml'].toString('utf8').matchAll(/<si\b[^>]*>([\s\S]*?)<\/si>/g)) {
      shared.push(xmlText([...si[1].matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map(t => t[1]).join('')));
    }
  }
  const sheetName = Object.keys(entries).filter(name => /^xl\/worksheets\/sheet\d+\.xml$/.test(name)).sort()[0];
  h.assert(sheetName, 'The workbook has no worksheet');
  const rows = [];
  for (const row of entries[sheetName].toString('utf8').matchAll(/<row\b[^>]*>([\s\S]*?)<\/row>/g)) {
    const cells = [];
    for (const cell of row[1].matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const ref = /\br="([A-Z]+)\d+"/.exec(cell[1]);
      const type = /\bt="(\w+)"/.exec(cell[1]);
      let column = 0;
      for (const letter of ref[1]) column = column * 26 + letter.charCodeAt(0) - 64;
      const body = cell[2] || '';
      let value = '';
      if (type && type[1] === 's') { const v = /<v>([\s\S]*?)<\/v>/.exec(body); value = v ? shared[Number(v[1])] : ''; }
      else if (type && type[1] === 'inlineStr') value = xmlText([...body.matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map(t => t[1]).join(''));
      else { const v = /<v>([\s\S]*?)<\/v>/.exec(body); value = v ? xmlText(v[1]) : ''; }
      cells[column - 1] = value;
    }
    rows.push(Array.from(cells, v => v === undefined ? '' : v));
  }
  return rows;
}

/** SKIP when Poppler is not installed (preflight-friendly). */
function requirePoppler(...tools) {
  for (const tool of tools.length ? tools : ['pdftotext', 'pdfinfo']) {
    try { execFileSync(tool, ['-v'], { stdio: 'pipe', timeout: 5000 }); } catch (error) {
      if (error.code === 'ENOENT') throw new h.SkipCheck(`This check reads PDFs with Poppler ${tool}, which is not available`);
    }
  }
}

/** Text of a PDF file, in reading order (`layout` keeps the columns); asserts it is a complete PDF first. */
function pdfText(file, { layout = false } = {}) {
  const bytes = fs.readFileSync(file);
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The response is not a PDF');
  h.assert(/%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), 'The PDF is truncated (no %%EOF in its last KiB)');
  return execFileSync('pdftotext', [...(layout ? ['-layout'] : []), '-enc', 'UTF-8', file, '-'],
    { encoding: 'utf8', timeout: TOOL_TIMEOUT, maxBuffer: 8 * 1024 * 1024 });
}

function pdfPages(file) {
  const info = execFileSync('pdfinfo', [file], { encoding: 'utf8', timeout: TOOL_TIMEOUT });
  return Number(info.match(/^Pages:\s+(\d+)/m)?.[1]);
}

/** Whitespace-free, NFC form: PDF text extraction reflows lines, so compare without spacing. */
function squash(value) {
  return String(value).normalize('NFC').replace(/\s+/g, '');
}

/** Describes what differs without printing row contents: the label, then only positions and lengths. */
function sameText(label, actual, expected) {
  if (actual === expected) return;
  let at = 0;
  while (at < actual.length && at < expected.length && actual[at] === expected[at]) at++;
  throw new Error(`${label}: differs at character ${at} (found length ${actual.length}, expected length ${expected.length})`);
}

module.exports = { scratchDir, saveDownload, parseCsv, unzip, xlsxRows, requirePoppler, pdfText, pdfPages, squash, sameText };
