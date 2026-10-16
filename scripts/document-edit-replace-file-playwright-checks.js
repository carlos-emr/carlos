#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Replacing a stored document's file from its Edit popup. With ALLOW_UPDATE_DOCUMENT_CONTENT=true
 * (the shipped default) the popup offers a File field ("blank to keep file"); a file chosen there is
 * written as a new stored file and the document points at it. document-refile-combine covers the
 * popup's description, type and date, with the File field left blank; nothing chose a file there.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Documents (eDoc report popup) ▸ Edit
 * (documentManager/ViewEditDocument popup) ▸ File: a new PDF ▸ Update (POST documentManager/addEditDocument).
 * Asserted: the document now names a new stored file that holds exactly the uploaded bytes, the old
 * file is left as it was, the description and patient link are kept, the report's own link serves exactly
 * the uploaded bytes, and the document records the new file's page count.
 * Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR). SKIP when the popup's File field is
 * hidden (ALLOW_UPDATE_DOCUMENT_CONTENT is not true).
 * Fixtures: the owned patient and one owned 1-page PDF document (row + file, name carries the marker);
 * the replacement is uploaded through the popup as "<marker>-R.pdf". Cleanup removes the row, its link,
 * the original file and every stored replacement with the marker's name, and asserts they are gone.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  textPdf, sha, directory, ownedPdfDocuments, seedOwnedPdfDocuments,
  removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-doc-replace-'));
  const [doc] = ownedPdfDocuments(store, marker, [{ key: 'A', pages: 1 }]);
  const uploadName = `${marker}-R.pdf`;
  // The uploader stores "<yyyyMMddHHmmss><name>"; the marker makes every such name this run's own.
  const replacements = () => fs.readdirSync(store).filter(name => /^\d{14}/.test(name) && name.slice(14) === uploadName)
    .map(name => path.join(store, name));
  const owned = () => ({ sql, marker, patient, docs: [doc], files: [doc.file, ...replacements()] });
  s.cleanup(() => {
    removeOwnedPdfDocuments(owned());
    fs.rmSync(scratch, { recursive: true, force: true });
    assertOwnedPdfDocumentsRemoved(owned());
  });
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs: [doc] });
  const originalSha = sha(doc.file);
  const replacement = path.join(scratch, uploadName);
  fs.writeFileSync(replacement, textPdf(`${marker} R`, 3));

  let report;
  let editor;
  await s.step('Master Record ▸ Documents ▸ Edit offers a File field for the owned document', async () => {
    report = await s.popup(s.master, s.master.locator('a[onclick*="/documentManager/ViewDocumentReport"]').first(), 'edoc-report');
    await report.locator(`#docNo${doc.id}`).waitFor({ state: 'attached' });
    const row = report.locator('tr', { has: report.locator(`#docNo${doc.id}`) });
    editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-edit');
    h.assert(await editor.locator('input[name="mode"]').inputValue() === doc.id, 'The edit popup opened another document');
    const field = editor.locator('input[type="file"][name="docFile"]');
    h.assert(await field.count() === 1, 'The edit popup has no File field');
    if (!await field.isVisible()) {
      await editor.close().catch(() => {});
      throw new h.SkipCheck('The edit popup hides its File field (ALLOW_UPDATE_DOCUMENT_CONTENT is not true)');
    }
  });

  let stored;
  await s.step('Update with a new PDF stores it as a new file and points the document at it', async () => {
    await editor.locator('input[type="file"][name="docFile"]').setInputFiles(replacement);
    const [post] = await Promise.all([
      editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
        && response.request().method() === 'POST'),
      report.waitForEvent('load'),
      editor.locator('input[name="Submit"]').click(),
    ]);
    h.assert(post.status() < 400, `The document edit POST answered HTTP ${post.status()}`);
    if (!editor.isClosed()) await editor.waitForEvent('close');
    const files = replacements();
    h.assert(files.length === 1, `Update stored ${files.length} replacement files, not one`);
    stored = path.basename(files[0]);
    await expectValue(sql, `SELECT docfilename FROM document WHERE document_no=${doc.id}`, stored,
      'The document does not point at the stored replacement');
    h.assert(sha(files[0]) === sha(replacement), 'The stored replacement does not hold the uploaded bytes');
    h.assert(sha(doc.file) === originalSha, 'Replacing the file changed the original file');
    h.assert(sql.value(`SELECT CONCAT_WS('|',docdesc,contenttype,status) FROM document WHERE document_no=${doc.id}`)
      === `${doc.label}|application/pdf|A`, 'Replacing the file changed the description, content type or status');
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no=${doc.id} AND module='demographic' AND module_id=${patient}`)
      === '1', 'Replacing the file dropped the patient link');
  });

  await s.step('the report\'s own link for the document serves the new PDF', async () => {
    await report.locator(`#docNo${doc.id}`).waitFor({ state: 'attached' });
    const onclick = await report.locator(`a[title="${doc.label}"]`).first().getAttribute('onclick');
    // The link opens its URL from a JavaScript string, where "&" is written "\x26".
    const match = /'(ManageDocument\?[^']+)'/.exec(onclick || '');
    const target = match ? match[1].replace(/\\x26/g, '&') : '';
    h.assert(/^ManageDocument\?method=display&/.test(target) && new URLSearchParams(target.split('?')[1]).get('doc_no') === doc.id,
      `The report's link for the document does not display it: ${onclick}`);
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/documentManager/${target}`));
    h.assert(response.ok(), `Displaying the document answered HTTP ${response.status()}`);
    const served = path.join(scratch, 'served.pdf');
    fs.writeFileSync(served, await response.body());
    h.assert(sha(served) === sha(replacement),
      `The document displays ${sha(served) === originalSha ? 'the old file' : 'bytes other than the uploaded file'}, not the replacement`);
  });

  await s.step('the document records the replacement\'s page count', async () => {
    const pages = sql.value(`SELECT number_of_pages FROM document WHERE document_no=${doc.id}`);
    h.assert(pages === '3', `The document still records ${pages} page(s) after its file was replaced by a 3-page PDF`);
  });
}

if (require.main === module) runWorkflow('document-edit-replace-file', workflow);
module.exports = { workflow };
