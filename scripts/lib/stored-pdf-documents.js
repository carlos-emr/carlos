/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Owned stored-PDF documents for the chart document workflows
 * (document-refile-combine, note-browser-documents) and the Poppler tools that read them.
 *
 * A check names its documents with ownedPdfDocuments() and registers its own cleanup
 * (removeOwnedPdfDocuments, then assertOwnedPdfDocumentsRemoved) BEFORE seedOwnedPdfDocuments()
 * writes the first file or row. Only rows whose docdesc starts with the run marker or whose id
 * seedOwnedPdfDocuments() captured (pass the same `docs` to cleanup), their ctl_document links to
 * the owned patient, and the named files are ever touched.
 */
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { execFileSync, spawnSync } = require('node:child_process');
const h = require('./playwright-harness');

const TOOL_TIMEOUT = 15000;

/**
 * SKIP (not a raw ENOENT mid-workflow) when a Poppler tool is missing or does not answer.
 * Suitable as a runWorkflow preflight, which runs before any fixture is created.
 */
function requirePoppler(...tools) {
  for (const tool of tools) {
    const probe = spawnSync(tool, ['-v'], { stdio: 'pipe', timeout: 5000 });
    if (probe.error) throw new h.SkipCheck(`This check reads PDFs with Poppler ${tool} (poppler-utils), which is not available`);
  }
}

/** pdftotext of a file, bounded so a hung child fails the step instead of the whole run. */
function pdfText(file) {
  return execFileSync('pdftotext', [file, '-'], { encoding: 'utf8', timeout: TOOL_TIMEOUT, maxBuffer: 4 * 1024 * 1024 });
}

/** Page count (pdfinfo) and text (pdftotext) of a file that must be a PDF. */
function pdfFacts(file) {
  h.assert(fs.readFileSync(file).subarray(0, 5).toString('latin1') === '%PDF-', 'The response is not a PDF');
  const info = execFileSync('pdfinfo', [file], { encoding: 'utf8', timeout: TOOL_TIMEOUT });
  return { pages: Number(info.match(/^Pages:\s+(\d+)/m)?.[1]), text: pdfText(file) };
}

/** A classic-xref PDF with one text line per page ("<label> page <n>"), so pdftotext can prove which file is which. */
function textPdf(label, pages = 1) {
  const kids = Array.from({ length: pages }, (_, index) => `${4 + index * 2} 0 R`).join(' ');
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', `<< /Type /Pages /Kids [${kids}] /Count ${pages} >>`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  for (let page = 1; page <= pages; page++) {
    const content = `BT /F1 12 Tf 40 700 Td (${label} page ${page}) Tj ET\n`;
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents ${objects.length + 2} 0 R >>`);
    objects.push(`<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`);
  }
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

const sha = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');

/** The real path of a configured installed directory; SKIP when the variable is unset. */
function directory(name, ...keys) {
  const configured = keys.map(key => process.env[key]).find(Boolean);
  if (!configured) throw new h.SkipCheck(`Set ${name} to the installed directory`);
  const real = fs.realpathSync(configured);
  h.assert(fs.statSync(real).isDirectory(), `${name} is not a directory`);
  return real;
}

/**
 * Name (without writing) the owned documents: label "<marker> <key>", a stored name with the
 * uploader's 14-digit timestamp prefix (the refile copy drops it), and the file under `store`.
 */
function ownedPdfDocuments(store, marker, specs) {
  return specs.map(({ key, pages = 1 }) => {
    const filename = `20260101000000${marker}-${key}.pdf`;
    return { key, pages, label: `${marker} ${key}`, filename, file: path.join(store, filename) };
  });
}

/** Write each file (owned like the store) and its document row linked to the patient; sets doc.id. */
function seedOwnedPdfDocuments({ sql, store, patient, provider, docs }) {
  const owner = fs.statSync(store);
  for (const doc of docs) {
    fs.writeFileSync(doc.file, textPdf(doc.label, doc.pages), { flag: 'wx', mode: 0o640 });
    fs.chownSync(doc.file, owner.uid, owner.gid);
    doc.id = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,
        status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
      VALUES ('others',${h.sqlString(doc.label)},${h.sqlString(doc.filename)},${h.sqlString(provider)},${h.sqlString(provider)},
        '',NOW(),'A','application/pdf',NOW(),0,'2026-01-02',${doc.pages},0,0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(doc.id), 'The owned document fixture was not created');
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${doc.id},'A')`);
  }
}

/** Ids seedOwnedPdfDocuments() captured; they stay owned even if an edit rewrote the description. */
function capturedIds(docs = []) {
  return docs.map(doc => doc.id).filter(Boolean);
}

/**
 * Delete the marker's document rows and the captured fixture rows, their links to the owned
 * patient, and the given files.
 */
function removeOwnedPdfDocuments({ sql, marker, patient, files, docs }) {
  const byMarker = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]);
  const ids = [...new Set([...byMarker, ...capturedIds(docs)])];
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned document identity');
  if (ids.length) {
    sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND module='demographic' AND module_id=${patient};
      DELETE FROM document WHERE document_no IN (${ids.join(',')})`);
  }
  for (const file of files) if (fs.existsSync(file)) fs.unlinkSync(file);
}

function assertOwnedPdfDocumentsRemoved({ sql, marker, patient, files, docs }) {
  const ids = capturedIds(docs);
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned document identity');
  h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`) === '0'
    && (!ids.length || sql.value(`SELECT COUNT(*) FROM document WHERE document_no IN (${ids.join(',')})`) === '0')
    && sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${patient}`) === '0',
  'Owned document rows were not removed');
  h.assert(!files.some(file => fs.existsSync(file)), 'Owned document files were not removed');
}

module.exports = {
  requirePoppler,
  pdfText,
  pdfFacts,
  textPdf,
  sha,
  directory,
  ownedPdfDocuments,
  seedOwnedPdfDocuments,
  removeOwnedPdfDocuments,
  assertOwnedPdfDocumentsRemoved,
};
