#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Adding a document with a new document type. The Add Document form's New Type button asks for a type
 * (prompt) and adds it to the Type list, chosen; Add then stores the type for patient documents
 * (ctl_doctype, module demographic, active) along with the document. edoc-schedule-navigation reaches
 * New Type only when the install has no document type at all, and asserts nothing about the type.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Documents (eDoc report popup) ▸ Add Document ▸
 * New Type ▸ prompt ▸ Add (POST documentManager/addEditDocument), then the report's type filter.
 * Asserted:
 *   1. New Type dismissed adds no type and keeps the choice; left blank, it adds none and says
 *      "Invalid entry"; neither stores anything.
 *   2. New Type with a marker-named type adds it last in the Type list, chosen, and stores nothing
 *      yet; Add saves exactly one document for the owned patient with that type and the uploaded
 *      bytes, and exactly one new type row, for patient documents and active.
 *   3. LAST: the reloaded report still offers the type in Add Document's Type list and in its type
 *      filter; the filter lists the new document and not the owned document of another type, which
 *      the unfiltered list shows.
 * Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR).
 * Fixtures: the owned patient, one owned 1-page PDF document of type "others" (row + file, name
 * carries the marker), and the upload "<marker>-N.pdf". The new type is named with the marker. It is
 * visible to every login's Type lists while the run lasts, so the check runs alone (EXCLUSIVE=1).
 * Cleanup removes both documents with their patient links and stored files, the chart note and link
 * the upload filed (lib/document-residue), and the type row this run added, and asserts each is gone.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const {
  textPdf, sha, directory, ownedPdfDocuments, seedOwnedPdfDocuments,
  removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');
const { markDocumentResidue, removeDocumentResidue } = require('./lib/document-residue');
const { masterRecordLink } = require('./lib/master-record-link');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-doc-new-type-'));
  const typeName = `${marker} type`;
  const added = { key: 'N', label: `${marker} N`, id: null };
  const uploadName = `${marker}-N.pdf`;
  const [other] = ownedPdfDocuments(store, marker, [{ key: 'A', pages: 1 }]);
  // The uploader stores "<yyyyMMddHHmmss><name>"; the marker makes every such name this run's own.
  const uploads = () => fs.readdirSync(store).filter(name => /^\d{14}/.test(name) && name.slice(14) === uploadName)
    .map(name => path.join(store, name));
  const typeFloor = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM ctl_doctype'));
  const typeRows = () => sql.value(`SELECT COUNT(*) FROM ctl_doctype WHERE doctype=${h.sqlString(typeName)}`);
  const residue = markDocumentResidue(sql);
  const owned = () => ({ sql, marker, patient, docs: [other, added], files: [other.file, ...uploads()] });
  s.cleanup(() => {
    // Add may save the upload before its redirect fails, leaving added.id unset.
    const ids = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]);
    removeDocumentResidue(sql, residue, [...ids, other.id, added.id].filter(Boolean));
    removeOwnedPdfDocuments(owned());
    // Only the type this run named, and only a row added after the mark.
    sql.execute(`DELETE FROM ctl_doctype WHERE doctype=${h.sqlString(typeName)} AND id > ${typeFloor}`);
    fs.rmSync(scratch, { recursive: true, force: true });
    h.assert(typeRows() === '0', 'The document type this run added was not removed');
    assertOwnedPdfDocumentsRemoved(owned());
  });
  h.assert(typeRows() === '0', 'A document type with this run\'s marker exists before the run');
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs: [other] });
  const upload = path.join(scratch, uploadName);
  fs.writeFileSync(upload, textPdf(added.label, 1));

  const typeList = page => page.locator('#addDocDiv #docType option')
    .evaluateAll(options => options.map(option => ({ value: option.value, selected: option.selected })));
  const newType = page => page.locator('#addDocDiv #docTypeinput');
  let report;
  await s.step('Add Document ▸ New Type dismissed adds no type; left blank it adds none and says Invalid entry', async () => {
    report = await s.popup(s.master, masterRecordLink(s.master, '/documentManager/ViewDocumentReport').first(), 'edoc-report');
    await report.locator(`#docNo${other.id}`).waitFor({ state: 'attached' });
    await report.locator('button[data-bs-target="#addDocDiv"]').click();
    await report.locator('#addDocDiv #docDesc').waitFor({ state: 'visible' });
    const before = await typeList(report);
    h.assert(!before.some(option => option.value === typeName), 'The Type list offers this run\'s type before New Type');

    const dismissed = await h.withExpectedDialogs(report, () => newType(report).click(), { accept: false });
    h.assert(dismissed.length === 1 && dismissed[0].type === 'prompt'
      && dismissed[0].text === 'Please enter new document type:',
    `New Type did not ask for the type once: ${JSON.stringify(dismissed)}`);
    h.assert(JSON.stringify(await typeList(report)) === JSON.stringify(before),
      'New Type dismissed changed the Type list or its choice');
    h.assert(await report.locator('#docAlertContainer .alert').count() === 0, 'New Type dismissed showed an alert');

    const blank = await h.withExpectedDialogs(report, () => newType(report).click(), { promptText: '' });
    h.assert(blank.length === 1 && blank[0].type === 'prompt', `New Type did not ask for the type once: ${JSON.stringify(blank)}`);
    const alert = report.locator('#docAlertContainer .alert');
    await alert.waitFor({ state: 'visible' });
    h.assert((await alert.innerText()).includes('Invalid entry'), `A blank New Type says "${await alert.innerText()}"`);
    h.assert(JSON.stringify(await typeList(report)) === JSON.stringify(before),
      'New Type left blank changed the Type list or its choice');
    h.assert(typeRows() === '0', 'New Type stored a document type before any Add');
  });

  await s.step('New Type with a marker-named type adds it, chosen, and Add saves the document with it and one new active type', async () => {
    const before = await typeList(report);
    const typesBefore = sql.value('SELECT COUNT(*) FROM ctl_doctype');
    const asked = await h.withExpectedDialogs(report, () => newType(report).click(), { promptText: typeName });
    h.assert(asked.length === 1 && asked[0].type === 'prompt', `New Type did not ask for the type once: ${JSON.stringify(asked)}`);
    const after = await typeList(report);
    h.assert(after.length === before.length + 1 && JSON.stringify(after.slice(0, -1).map(option => option.value))
      === JSON.stringify(before.map(option => option.value)), 'New Type did not add exactly one type at the end of the Type list');
    h.assert(after[after.length - 1].value === typeName && after.filter(option => option.selected).length === 1
      && after[after.length - 1].selected, `New Type did not add "${typeName}" chosen: ${JSON.stringify(after.slice(-2))}`);
    h.assert(typeRows() === '0', 'New Type stored the type before Add');

    await report.locator('#addDocDiv #docDesc').fill(added.label);
    await report.locator('#addDocDiv #docFile').setInputFiles(upload);
    const observed = await report.locator('#addDocDiv #observationDate').inputValue();
    h.assert(/^\d{4}-\d\d-\d\d$/.test(observed), `The Add Document form offers observation date "${observed}"`);
    const expectedReport = new URL(h.appUrl(s.config.baseUrl, '/documentManager/ViewDocumentReport')).pathname;
    const [post] = await Promise.all([
      report.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
        && response.request().method() === 'POST'),
      report.waitForURL(url => url.pathname === expectedReport && url.searchParams.get('docerrors') === 'docerrors',
        { waitUntil: 'domcontentloaded' }),
      report.locator('#addDocDiv input[name="Submit"]').click(),
    ]);
    h.assert(post.status() === 302, `The Add Document POST answered HTTP ${post.status()}, not its success redirect`);

    const saved = sql.rows(`SELECT document_no,doctype,status,contenttype,docfilename FROM document
      WHERE docdesc=${h.sqlString(added.label)}`);
    h.assert(saved.length === 1, `Add saved ${saved.length} documents, not one`);
    added.id = saved[0][0];
    h.assert(JSON.stringify(saved[0].slice(1, 4)) === JSON.stringify([typeName, 'A', 'application/pdf']),
      `The saved document's type, status or content type is ${JSON.stringify(saved[0].slice(1, 4))}`);
    h.assert(sql.value(`SELECT GROUP_CONCAT(CONCAT_WS(':',module,module_id)) FROM ctl_document WHERE document_no=${added.id}`)
      === `demographic:${patient}`, 'The saved document is not linked to the owned patient only');
    const files = uploads();
    h.assert(files.length === 1 && path.basename(files[0]) === saved[0][4], `Add stored ${files.length} files for the upload`);
    h.assert(sha(files[0]) === sha(upload), 'The stored file does not hold the uploaded bytes');

    h.assert(sql.value('SELECT COUNT(*) FROM ctl_doctype') === String(Number(typesBefore) + 1),
      'Add did not store exactly one new document type row');
    h.assert(sql.value(`SELECT GROUP_CONCAT(CONCAT_WS(':',module,status)) FROM ctl_doctype
      WHERE doctype=${h.sqlString(typeName)}`) === 'demographic:A',
    'The new type is not stored once, for patient documents and active');
  });

  await s.step('the reloaded report still offers the type, and its type filter lists the new document only', async () => {
    await report.locator(`#docNo${added.id}`).waitFor({ state: 'attached' });
    h.assert(await report.locator(`#docNo${other.id}`).count() === 1, 'The unfiltered list does not show the other owned document');
    h.assert((await typeList(report)).filter(option => option.value === typeName).length === 1,
      'The reloaded Add Document Type list does not offer the new type once');
    const filter = report.locator('#viewdoctype0');
    h.assert(await filter.locator('option').evaluateAll((options, name) => options.filter(option => option.value === name).length,
      typeName) === 1, 'The type filter does not offer the new type once');
    await Promise.all([
      report.waitForURL(url => url.searchParams.get('view') === typeName, { waitUntil: 'domcontentloaded' }),
      filter.selectOption(typeName),
    ]);
    await report.locator(`#docNo${added.id}`).waitFor({ state: 'attached' });
    h.assert(await report.locator(`#docNo${other.id}`).count() === 0, 'The type filter still lists the document of another type');
    h.assert(await report.locator('#viewdoctype0').inputValue() === typeName, 'The filtered report does not show its type chosen');
  });
}

if (require.main === module) runWorkflow('document-add-new-type', workflow);
module.exports = { workflow };
