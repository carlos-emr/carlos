/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Helpers for the export-content checks: save what the browser downloads, then read the file back
 * the way a recipient would (CSV parse, XLSX / XLS cell grid, pdftotext) so a check can compare the CONTENT
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

/** The Workbook stream of an OLE2 compound file (the container of a BIFF8 .xls), main-FAT or mini-FAT sectors. */
function compoundStream(buffer, wanted) {
  h.assert(buffer.length > 512 && buffer.readUInt32BE(0) === 0xD0CF11E0 && buffer.readUInt32BE(4) === 0xA1B11AE1,
    'The file is not an Excel 97-2003 (.xls) compound document');
  const sectorSize = 1 << buffer.readUInt16LE(30);
  const miniSize = 1 << buffer.readUInt16LE(32);
  const sector = n => buffer.subarray(512 + n * sectorSize, 512 + (n + 1) * sectorSize);
  const fatSectors = [];
  for (let i = 0; i < 109; i++) {
    const n = buffer.readInt32LE(76 + i * 4);
    if (n >= 0) fatSectors.push(n);
  }
  for (let n = buffer.readInt32LE(68), left = buffer.readUInt32LE(72); left > 0 && n >= 0; left--) {
    const di = sector(n);
    for (let i = 0; i < sectorSize / 4 - 1; i++) { const v = di.readInt32LE(i * 4); if (v >= 0) fatSectors.push(v); }
    n = di.readInt32LE(sectorSize - 4);
  }
  const fat = [];
  for (const n of fatSectors) { const f = sector(n); for (let i = 0; i < sectorSize / 4; i++) fat.push(f.readInt32LE(i * 4)); }
  const chain = (start, table) => { const out = []; for (let n = start; n >= 0 && out.length <= table.length; n = table[n]) out.push(n); return out; };
  const read = start => Buffer.concat(chain(start, fat).map(sector));
  const directory = read(buffer.readInt32LE(48));
  let entry = null;
  for (let at = 0; at + 128 <= directory.length; at += 128) {
    const name = directory.subarray(at, at + Math.max(0, directory.readUInt16LE(at + 64) - 2)).toString('utf16le');
    if (name === wanted && directory[at + 66] === 2) { entry = { start: directory.readInt32LE(at + 116), size: directory.readUInt32LE(at + 120) }; break; }
  }
  h.assert(entry, `The compound document has no "${wanted}" stream`);
  if (entry.size >= buffer.readUInt32LE(56)) return read(entry.start).subarray(0, entry.size);
  const root = directory.subarray(0, 128);
  const miniStream = read(root.readInt32LE(116));
  const miniFat = [];
  for (const n of chain(buffer.readInt32LE(60), fat)) { const f = sector(n); for (let i = 0; i < sectorSize / 4; i++) miniFat.push(f.readInt32LE(i * 4)); }
  return Buffer.concat(chain(entry.start, miniFat).map(n => miniStream.subarray(n * miniSize, (n + 1) * miniSize))).subarray(0, entry.size);
}

/** Shared strings of a BIFF8 workbook: the SST record and its CONTINUE records (a string may be cut inside its characters). */
function biffSharedStrings(segments) {
  const strings = [];
  let seg = 0;
  let at = 8; // total and unique counts
  const data = () => segments[seg];
  const need = () => { if (at >= data().length && seg + 1 < segments.length) { seg++; at = 0; return true; } return false; };
  const byte = () => { need(); return data()[at++]; };
  const u16 = () => byte() | (byte() << 8);
  const u32 = () => (u16() | (u16() << 16)) >>> 0;
  const unique = segments[0].readUInt32LE(4);
  for (let n = 0; n < unique; n++) {
    need();
    const length = u16();
    const flags = byte();
    const runs = flags & 8 ? u16() : 0;
    const extra = flags & 4 ? u32() : 0;
    let wide = !!(flags & 1);
    let text = '';
    for (let i = 0; i < length; i++) {
      if (need()) wide = !!(byte() & 1); // a continuation inside the characters restates the width
      text += wide ? String.fromCharCode(u16()) : String.fromCharCode(byte());
    }
    for (let i = 0; i < runs * 4 + extra; i++) byte();
    strings.push(text);
  }
  return strings;
}

function rkNumber(rk) {
  let value;
  if (rk & 2) value = rk >> 2;
  else { const b = Buffer.alloc(8); b.writeUInt32LE((rk & 0xFFFFFFFC) >>> 0, 4); value = b.readDoubleLE(0); }
  return rk & 1 ? value / 100 : value;
}

/**
 * The first worksheet of a BIFF8 .xls (what Apache POI HSSF writes) as rows of cells { type: 'text' | 'number', value },
 * with an empty text cell where nothing is stored. The cell TYPE is kept because "123" stored as a number is a defect a
 * string comparison cannot see.
 */
function xlsCells(buffer) {
  const stream = compoundStream(buffer, 'Workbook');
  const sst = [];
  let sstSegments = null;
  const cells = new Map();
  let sheets = 0;
  let inSheet = false;
  for (let at = 0; at + 4 <= stream.length;) {
    const id = stream.readUInt16LE(at);
    const body = stream.subarray(at + 4, at + 4 + stream.readUInt16LE(at + 2));
    at += 4 + body.length;
    if (sstSegments && id === 0x003C) { sstSegments.push(body); continue; }
    if (sstSegments) { sst.push(...biffSharedStrings(sstSegments)); sstSegments = null; }
    if (id === 0x0809) { inSheet = body.readUInt16LE(2) === 0x0010 && ++sheets === 1; continue; }
    if (id === 0x00FC) { sstSegments = [body]; continue; }
    if (!inSheet) continue;
    if (id === 0x000A) break; // EOF of the first worksheet
    const cell = (row, col, type, value) => cells.set(`${row},${col}`, { row, col, type, value });
    if (id === 0x00FD) cell(body.readUInt16LE(0), body.readUInt16LE(2), 'text', sst[body.readUInt32LE(6)]);
    else if (id === 0x0203) cell(body.readUInt16LE(0), body.readUInt16LE(2), 'number', body.readDoubleLE(6));
    else if (id === 0x027E) cell(body.readUInt16LE(0), body.readUInt16LE(2), 'number', rkNumber(body.readUInt32LE(6)));
    else if (id === 0x00BD) {
      const first = body.readUInt16LE(2);
      for (let i = 0; i < (body.length - 6) / 6; i++) cell(body.readUInt16LE(0), first + i, 'number', rkNumber(body.readUInt32LE(4 + i * 6 + 2)));
    } else if (id === 0x0204) {
      // LABEL: row, col, xf, then an XLUnicodeString: cch (2 bytes), fHighByte (1 byte), then cch characters,
      // one byte each (compressed latin1) or two bytes each (UTF-16LE) when bit 0 of fHighByte is set.
      const count = body.readUInt16LE(6);
      const wide = (body[8] & 0x01) === 1;
      const chars = body.subarray(9, 9 + count * (wide ? 2 : 1));
      cell(body.readUInt16LE(0), body.readUInt16LE(2), 'text', chars.toString(wide ? 'utf16le' : 'latin1'));
    }
  }
  const rows = [];
  for (const { row, col, type, value } of cells.values()) {
    rows[row] = rows[row] || [];
    rows[row][col] = { type, value };
  }
  return Array.from(rows, r => Array.from(r || [], c => c || { type: 'text', value: '' }));
}

/** SKIP when Poppler is not installed (preflight-friendly). */
function requirePoppler(...tools) {
  for (const tool of tools.length ? tools : ['pdftotext', 'pdfinfo']) {
    try { execFileSync(tool, ['-v'], { stdio: 'pipe', timeout: 5000 }); } catch (error) {
      if (error.code === 'ENOENT') throw new h.SkipCheck(`This check reads PDFs with Poppler ${tool}, which is not available`);
    }
  }
}

/** Text of a complete PDF: reading order by default, layout for columns, or raw content order for wrapped cells. */
function pdfText(file, { layout = false, raw = false } = {}) {
  const bytes = fs.readFileSync(file);
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The response is not a PDF');
  h.assert(/%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), 'The PDF is truncated (no %%EOF in its last KiB)');
  return execFileSync('pdftotext', [...(raw ? ['-raw'] : layout ? ['-layout'] : []), '-enc', 'UTF-8', file, '-'],
    { encoding: 'utf8', timeout: TOOL_TIMEOUT, maxBuffer: 8 * 1024 * 1024 });
}

/** Reads rendered PDF text, including CID/ToUnicode fonts, directly from response bytes. */
function pdfTextBuffer(bytes) {
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The response is not a PDF');
  h.assert(/%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), 'The PDF is truncated (missing %%EOF)');
  return execFileSync('pdftotext', ['-enc', 'UTF-8', '-', '-'], {
    input: bytes, encoding: 'utf8', timeout: TOOL_TIMEOUT, maxBuffer: 8 * 1024 * 1024,
  });
}

/** Requires every font in a generated PDF to be embedded, so recipients need no installed fonts. */
function assertEmbeddedFonts(file) {
  const output = execFileSync('pdffonts', [file], { encoding: 'utf8', timeout: TOOL_TIMEOUT });
  const rows = output.trim().split(/\r?\n/).slice(2);
  h.assert(rows.length > 0, 'The PDF has no text fonts');
  for (const row of rows) {
    const flags = row.match(/\s+(yes|no)\s+(yes|no)\s+(yes|no)\s+\d+\s+\d+\s*$/);
    h.assert(flags && flags[1] === 'yes', `The PDF contains an unembedded or unreadable font: ${row}`);
  }
}

/** Whitespace-free, NFC form: PDF text extraction reflows lines, so compare without spacing. */
function squash(value) {
  return String(value).normalize('NFC').replace(/\s+/g, '');
}

module.exports = { scratchDir, saveDownload, parseCsv, unzip, xlsxRows, xlsCells, requirePoppler, pdfText, pdfTextBuffer, assertEmbeddedFonts, squash };
