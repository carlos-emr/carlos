#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of an uploaded document: add, then view (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record > E-Chart > Documents "+" (ViewDocumentReport, mode=add) > type,
 * description, a small PDF > Add; then the chart's Documents heading link (the document report popup) > the new
 * entry (ManageDocument method=display: the browser shows or downloads the PDF).
 *
 * Asserts the `document` and `ctl_document` rows, and, scoped to the owned patient and the document id: the
 * upload wrote one add/document row (provider, client address, demographic_no = the patient, contentId = the
 * document) without the description; one view of the document wrote exactly one read row for it, with the
 * provider and client address and demographic_no = the patient, and in the single content type spelling the
 * rest of the audit trail uses ("document", not "Document"); and no row of the view carries the description.
 * Every expectation is evaluated and the violated ones are reported together in the last step.
 *
 * Fixtures: the harness's owned synthetic patient and one uploaded PDF. Cleanup deletes the document and
 * ctl_document rows and the stored file (inside DOCUMENT_DIR only; SKIP when the store is not readable), and
 * the audit rows scoped to the patient or the document id, and asserts them gone.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { auditProbe, phiLeaks, incomplete, label } = require('./lib/audit-log-helpers');

const q = h.sqlString;

function tinyPdf(directory, name) {
  const body = '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';
  const file = path.join(directory, name);
  fs.writeFileSync(file, body);
  return file;
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const store = process.env.DOCUMENT_DIR;
  if (!store || !fs.existsSync(store)) throw new h.SkipCheck('DOCUMENT_DIR (the document store) is not readable here');
  const probe = auditProbe({ sql, patient });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const description = `${marker} audit document`;
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'audit-doc-'));
  let documentNo = null;
  const rows = () => sql.rows(`SELECT d.document_no, d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
    WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(description)}`);
  s.cleanup(() => {
    const files = new Set();
    for (const [no, file] of rows()) {
      h.assert(/^[1-9]\d*$/.test(no), 'Owned document id is invalid');
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${no}; DELETE FROM ctl_document WHERE document_no=${no} AND module_id=${patient}; DELETE FROM document WHERE document_no=${no}`);
      sql.execute(`DELETE FROM log WHERE content IN ('document','Document') AND (contentId=${q(no)} OR data=${q(`doc_no=${no}`)})`);
      files.add(file);
    }
    for (const file of files) {
      const target = path.join(store, path.basename(file));
      if (path.basename(file) === file && fs.existsSync(target)) fs.unlinkSync(target);
      h.assert(!fs.existsSync(target), 'An uploaded document file was not removed from the store');
    }
    fs.rmSync(scratch, { recursive: true, force: true });
    h.assert(rows().length === 0, 'Owned documents were not removed');
    probe.cleanup();
  });
  const chart = await s.chart();

  await s.step('Documents "+" > Add uploads a PDF onto the owned patient (audit rows observed)', async () => {
    const before = probe.mark();
    const add = await s.popup(chart, chart.locator('a[onclick*="ViewDocumentReport?"][onclick*="mode=add"]').first(), 'audit-document-add');
    const form = add.locator('form[action*="addEditDocument"]').first();
    await form.waitFor({ state: 'visible', timeout: 20000 });
    const types = await form.locator('select[name="docType"] option').evaluateAll(options => options.map(o => o.value).filter(Boolean));
    await form.locator('select[name="docType"]').selectOption(types[0]);
    await form.locator('input[name="docDesc"]').fill(description);
    await form.locator('input[type="file"]').setInputFiles(tinyPdf(scratch, 'audit.pdf'));
    await form.locator('input[name="Submit"]').first().click();
    await expectValue(sql, `SELECT COUNT(*) FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(description)}`, '1', 'The document was not attached to the owned patient');
    documentNo = rows()[0][0];
    probe.own('document', documentNo);
    if (!add.isClosed()) await add.close().catch(() => {});
    await probe.settle(2500);
    const adds = probe.since(before, `action='add' AND content='document' AND contentId=${q(documentNo)}`);
    h.assert(adds.length >= 1, 'The upload wrote no add/document row for the document');
    expect(adds.length === 1, `The upload wrote ${adds.length} add/document rows for the document, expected 1`);
    for (const r of adds) {
      const problems = incomplete([r], { provider, patient });
      expect(!problems.length, `The add/document row for an upload filed onto a patient is incomplete (${problems.join(', ')})`);
    }
    expect(!phiLeaks(probe.since(before), [description]).length, 'The upload audit rows carry the document description');
  });

  await s.step('the Documents report in the chart opens the document (audit rows observed)', async () => {
    const before = probe.mark();
    const report = await s.popup(chart, chart.locator('a[onclick*="ViewDocumentReport?"]:not([onclick*="mode=add"])').first(), 'audit-document-report');
    const link = report.locator('a').filter({ hasText: description }).first();
    await link.waitFor({ state: 'visible', timeout: 30000 });
    // A headless Chromium downloads the PDF, a headed one renders it in a popup: both are one view of the document.
    const opened = await ui.clickDownloadsOrOpens(report, link, { context: s.context, recorder: s.recorder, label: 'audit-document-view', timeout: 30000 });
    await probe.settle(3000);
    const reads = sql.rows(`SELECT id,COALESCE(provider_no,'~NULL~'),action,content,COALESCE(contentId,'~NULL~'),COALESCE(ip,'~NULL~'),COALESCE(CAST(demographic_no AS CHAR),'~NULL~'),COALESCE(data,'~NULL~')
      FROM log WHERE id>${before} AND action='read' AND content IN ('document','Document') AND (contentId=${q(documentNo)} OR data=${q(`doc_no=${documentNo}`)}) ORDER BY id`)
      .map(([id, who, action, content, contentId, ip, demographic, data]) => ({ id, provider: who, action, content, contentId, ip, demographic, data }));
    h.assert(reads.length >= 1, 'Viewing the document wrote no read row for it');
    expect(reads.length === 1, `One view of the document wrote ${reads.length} read rows (${reads.map(r => r.content).join(' + ')}), expected 1`);
    // The two-spelling case is reported by the next line; this one catches a lone row spelled otherwise.
    expect(new Set(reads.map(r => r.content)).size > 1 || reads.every(r => r.content === 'document'), `A view of the document wrote a read row whose content type is "${reads[0] ? reads[0].content : ''}", not "document"`);
    expect(new Set(reads.map(r => r.content)).size === 1, 'One view of the document wrote read rows under two spellings of the content type (document and Document)');
    for (const r of reads) {
      expect(r.provider === provider, `The ${label(r)} row does not carry the provider`);
      expect(r.ip !== '~NULL~' && r.ip !== '', `The ${label(r)} row (${r.content}) carries no client address`);
      expect(r.demographic === String(patient), `The read/${r.content} row carries no demographic_no, so the view cannot be found from the patient`);
    }
    expect(!phiLeaks(reads.map(r => ({ ...r, ip: undefined })), [description]).length, 'A read row carries the document description');
    if (opened.page) await opened.page.close().catch(() => {});
    await report.close().catch(() => {});
  });

  await s.step('every expectation of the document audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the document path:\n  - ${[...new Set(defects)].join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-document-read', workflow);
module.exports = { workflow };
