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
const { planDocumentFixtureId } = require('./document-fixture-allocation');

const TOOL_TIMEOUT = 15000;
/** The document type every owned fixture document is seeded with; the Inbox shows it under Discipline. */
const OWNED_DOCUMENT_TYPE = 'others';

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
function seedOwnedPdfDocuments({ sql, store, patient, provider, docs, onFileCreated }) {
  const owner = fs.statSync(store);
  for (const doc of docs) {
    // Do not inherit a dangling DOC route or a type-less routing lock from an
    // older document. The planner includes both in its identity ceiling.
    const plannedId = planDocumentFixtureId(sql).sourceId;
    const { expectedSplitRecipients } = require('./stored-document-mutation-fixture');
    const routingProviders = expectedSplitRecipients(sql, plannedId, provider);
    const fd = fs.openSync(doc.file, 'wx', 0o640);
    try {
      if (onFileCreated) onFileCreated(doc.file, fs.fstatSync(fd));
      fs.writeFileSync(fd, textPdf(doc.label, doc.pages));
      fs.fchownSync(fd, owner.uid, owner.gid);
    } finally { fs.closeSync(fd); }
    doc.id = sql.value(`SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;START TRANSACTION;
      SELECT lab_no FROM providerLabRouting WHERE lab_no=${plannedId} FOR UPDATE;
      SELECT lab_no FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${plannedId} FOR UPDATE;
      SELECT lab_no FROM providerLabRoutingLock WHERE lab_no=${plannedId} FOR UPDATE;
      CREATE TEMPORARY TABLE carlos_pdf_seed_guard(ok INT NOT NULL CHECK(ok=1));
      INSERT INTO carlos_pdf_seed_guard VALUES(IF(
        (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${plannedId})=0
        AND (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${plannedId})=0
        AND (SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no=${plannedId})=0,1,0));
      INSERT INTO document (document_no,doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,
        status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
      VALUES (${plannedId},${h.sqlString(OWNED_DOCUMENT_TYPE)},${h.sqlString(doc.label)},${h.sqlString(doc.filename)},${h.sqlString(provider)},${h.sqlString(provider)},
        '',NOW(),'A','application/pdf',NOW(),0,'2026-01-02',${doc.pages},0,0);
      INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${plannedId},'A');
      COMMIT;SELECT ${plannedId}`);
    h.assert(doc.id === plannedId, 'The owned document fixture was not created');
    doc.routingProviders = routingProviders;
    doc.routingFloor = plannedId;
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
function removeOwnedPdfDocuments({ sql, marker, patient, files, docs, routingProof }) {
  const byMarker = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]);
  const ids = [...new Set([...byMarker, ...capturedIds(docs)])];
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned document identity');
  if (ids.length) {
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND NOT (module='demographic' AND module_id=${h.sqlString(String(patient))})`)==='0','Owned document acquired an external document link; retain it');
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${ids.join(',')}) AND demographic_no<>${patient}`)==='0','Owned document acquired another patient link; retain it');
    // A seeded source reserves an identity above every old DOC reference/lock.
    // Native uploads/copies carrying this marker then use the advancing allocator.
    const proofs = [...(docs || []), routingProof].filter(doc => doc
      && /^[1-9]\d*$/.test(doc.routingFloor) && Array.isArray(doc.routingProviders) && doc.routingProviders.length);
    const proofFor = id => proofs.find(doc => doc.id === id
      || (byMarker.includes(id) && BigInt(id) >= BigInt(doc.routingFloor)));
    const routeScopes = ids.map(id => {
      const providers = proofFor(id)?.routingProviders;
      return providers ? `(lab_no=${id} AND COALESCE(lab_type,'')='DOC' AND COALESCE(provider_no,'') IN (${providers.map(h.sqlString).join(',')}))` : '0';
    }).join(' OR ');
    const ownedLocks = ids.filter(id => proofFor(id));
    const externalRoutes = `lab_no IN (${ids.join(',')}) AND NOT (${routeScopes})`;
    const externalLocks = `lab_no IN (${ids.join(',')})${ownedLocks.length ? ` AND lab_no NOT IN (${ownedLocks.join(',')})` : ''}`;
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE ${externalRoutes}`)==='0','Owned document acquired an unproven provider route; retain it');
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRoutingLock WHERE ${externalLocks}`)==='0','Document routing lock ownership is unproven; retain it');
    sql.execute(`SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;START TRANSACTION;
      SELECT document_no FROM document WHERE document_no IN (${ids.join(',')}) FOR UPDATE;
      SELECT document_no FROM ctl_document WHERE document_no IN (${ids.join(',')}) FOR UPDATE;
      SELECT lab_no FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${ids.join(',')}) FOR UPDATE;
      SELECT lab_no FROM providerLabRouting WHERE lab_no IN (${ids.join(',')}) FOR UPDATE;
      SELECT lab_no FROM providerLabRoutingLock WHERE lab_no IN (${ids.join(',')}) FOR UPDATE;
      CREATE TEMPORARY TABLE carlos_pdf_cleanup_guard(ok INT NOT NULL CHECK(ok=1));
      INSERT INTO carlos_pdf_cleanup_guard VALUES(IF(
        (SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND NOT (module='demographic' AND module_id=${h.sqlString(String(patient))}))=0
        AND (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${ids.join(',')}) AND demographic_no<>${patient})=0
        AND (SELECT COUNT(*) FROM providerLabRouting WHERE ${externalRoutes})=0
        AND (SELECT COUNT(*) FROM providerLabRoutingLock WHERE ${externalLocks})=0,1,0));
      DELETE FROM providerLabRouting WHERE ${routeScopes};
      ${ownedLocks.length ? `DELETE FROM providerLabRoutingLock WHERE lab_no IN (${ownedLocks.join(',')});` : ''}
      DELETE FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${ids.join(',')}) AND demographic_no=${patient};
      DELETE FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND module='demographic' AND module_id=${h.sqlString(String(patient))};
      DELETE FROM document WHERE document_no IN (${ids.join(',')});COMMIT;`);
  }
  for (const file of files) if (fs.existsSync(file)) fs.unlinkSync(file);
}

function assertOwnedPdfDocumentsRemoved({ sql, marker, patient, files, docs }) {
  const ids = capturedIds(docs);
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned document identity');
  h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`) === '0'
    && (!ids.length || sql.value(`SELECT COUNT(*) FROM document WHERE document_no IN (${ids.join(',')})`) === '0')
    && sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${h.sqlString(String(patient))}`) === '0'
    && (!ids.length || (sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${ids.join(',')})`) === '0'
      && sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${ids.join(',')})`) === '0'
      && sql.value(`SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no IN (${ids.join(',')})`) === '0')),
  'Owned document rows were not removed');
  h.assert(!files.some(file => fs.existsSync(file)), 'Owned document files were not removed');
}

module.exports = {
  OWNED_DOCUMENT_TYPE,
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
