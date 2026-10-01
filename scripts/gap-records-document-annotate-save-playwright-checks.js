#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Annotate a stored document and save the marked copy (gap-records, documents).
 * User path: Schedule ▸ Inbox ▸ the document's patient link (documentManager/ViewShowDocument popup) ▸
 * Annotate (documentManager/AnnotateDocument viewer) ▸ Highlight (drag on the page), Add text (T) with its
 * "Note to add:" prompt, Remove mark ▸ "Save as new document" (documentManager/SaveAnnotatedDocument);
 * then Master Record ▸ Documents.
 * Asserts: the viewer renders one image per page and Save is disabled until a mark exists; Remove mark
 * takes the mark away again (Save disabled); with a highlight and a typed note the save answers success with
 * the new document number and the viewer says so; the new document row is "<original> (annotated)", linked to
 * the same patient, a two-page PDF in DOCUMENT_DIR whose text still carries the original page text; the
 * source document row and file are byte-identical afterwards; the patient's eDoc report lists both.
 * Fixtures: the runWorkflow FAKE- patient (its NULL HIN set to the empty HIN the inbox name search needs),
 * one owned two-page PDF with document, ctl_document and providerLabRouting rows. Cleanup deletes the marker
 * documents (source and annotated copy), their files and the routing row, and asserts them gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { settle } = require('./inboxhub-filters-playwright-checks');
const {
  requirePoppler, pdfFacts, sha, directory, ownedPdfDocuments, seedOwnedPdfDocuments,
  removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker, patient, provider, context, recorder } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const [source] = ownedPdfDocuments(store, marker, [{ key: 'A', pages: 2 }]);
  const docs = [source];
  const files = [source.file];
  const owned = { sql, marker, patient, docs, files };
  s.cleanup(() => {
    // The annotated copy gets a generated file name: find it through its marker description first.
    for (const [name] of sql.rows(`SELECT docfilename FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`)) {
      const file = path.join(store, path.basename(name));
      if (!files.includes(file)) files.push(file);
    }
    sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN
      (SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)})`);
    removeOwnedPdfDocuments(owned);
    assertOwnedPdfDocumentsRemoved(owned);
  });
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  sql.execute(`INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${h.sqlString(provider)},${source.id},'DOC','N')`);
  const sourceHash = sha(source.file);

  let viewer;
  let annotate;
  await s.step('Inbox ▸ the document opens its viewer, whose Annotate button opens the annotation viewer', async () => {
    const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
      { context, recorder, label: 'annotate-inbox', timeout: 60000 });
    await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: 60000 });
    if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
    await settle(inbox, 60000);
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await inbox.locator('#anyProvider').check();
    await inbox.locator('#statusNew').check();
    await inbox.locator('#specificPatients').check();
    await inbox.locator('#inputLastName').fill(marker);
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, 60000);
    const row = inbox.locator(`#inboxhubListModeTableBody tr[data-segment-id="${source.id}"]`);
    viewer = await ui.clickOpensPopup(inbox, row.locator('a[onclick*="ViewShowDocument"]'),
      { context, recorder, label: 'annotate-document', timeout: TIMEOUT });
    const button = viewer.locator(`#annotateBtn_${source.id}, input[value="Annotate"]`).first();
    await button.waitFor({ state: 'visible', timeout: TIMEOUT });
    annotate = await ui.clickOpensPopup(viewer, button, { context, recorder, label: 'annotate-viewer', timeout: TIMEOUT });
    h.assert(new URL(annotate.url()).searchParams.get('docId') === source.id, 'The viewer opened another document');
    await annotate.locator('img').nth(1).waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await annotate.locator('img').count() === 2, 'The viewer does not show one image per page of the two-page document');
    h.assert(!(await annotate.locator('#btnSave').isEnabled()), 'Save as new document is enabled before any mark exists');
  });

  async function drag(page, x, y, width) {
    const box = await page.locator('img').first().boundingBox();
    await page.mouse.move(box.x + x, box.y + y);
    await page.mouse.down();
    await page.mouse.move(box.x + x + width, box.y + y + 14, { steps: 5 });
    await page.mouse.up();
  }

  await s.step('Highlight enables Save and Remove mark takes the mark away again', async () => {
    await annotate.getByRole('button', { name: 'Highlight' }).click();
    await drag(annotate, 60, 140, 220);
    await annotate.waitForFunction(() => !document.getElementById('btnSave').disabled, null, { timeout: 5000 });
    await annotate.getByRole('button', { name: 'Remove mark' }).click();
    const box = await annotate.locator('img').first().boundingBox();
    await annotate.mouse.click(box.x + 120, box.y + 147);
    await annotate.waitForFunction(() => document.getElementById('btnSave').disabled, null, { timeout: 5000 });
  });

  await s.step('a highlight and a typed note are saved as a new document', async () => {
    await annotate.getByRole('button', { name: 'Highlight' }).click();
    await drag(annotate, 60, 300, 240);
    const note = `${marker} note`;
    await annotate.getByRole('button', { name: 'Add text (T)' }).click();
    const box = await annotate.locator('img').first().boundingBox();
    const dialogs = await h.withExpectedDialogs(annotate, () => annotate.mouse.click(box.x + 80, box.y + 120),
      { accept: true, promptText: note });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Add text must ask for the note exactly once');
    await annotate.waitForFunction(() => !document.getElementById('btnSave').disabled, null, { timeout: 5000 });
    const [response] = await Promise.all([
      annotate.waitForResponse(candidate => new URL(candidate.url()).pathname.endsWith('/documentManager/SaveAnnotatedDocument')),
      annotate.locator('#btnSave').click(),
    ]);
    h.assert(response.status() === 200, `Save answered HTTP ${response.status()}`);
    const result = await response.json();
    h.assert(result.success === true && /^[1-9]\d*$/.test(String(result.documentNo)), 'Save did not report the new document number');
    await annotate.locator('#status.ok').waitFor({ timeout: 10000 });
    h.assert((await annotate.locator('#status').innerText()).includes(`#${result.documentNo}`),
      'The viewer does not report the new document number');
    s.annotated = String(result.documentNo);
  });

  await s.step('the annotated copy is a linked two-page PDF carrying the original text; the source is untouched', async () => {
    const id = s.annotated;
    const [row] = sql.rows(`SELECT docdesc, docfilename, status, contenttype, number_of_pages FROM document WHERE document_no=${id}`);
    h.assert(row && row[0] === `${source.label} (annotated)` && row[2] === 'A' && row[3] === 'application/pdf' && row[4] === '2',
      'The annotated copy is not an active two-page PDF named "<original> (annotated)"');
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no=${id} AND module='demographic' AND module_id=${patient}`) === '1',
      'The annotated copy is not linked to the same patient');
    const file = path.join(store, path.basename(row[1]));
    h.assert(fs.existsSync(file), 'The annotated PDF is missing from DOCUMENT_DIR');
    files.push(file);
    const facts = pdfFacts(file);
    h.assert(facts.pages === 2 && facts.text.includes(`${source.label} page 1`) && facts.text.includes(`${source.label} page 2`),
      'The annotated PDF does not carry both original pages');
    h.assert(facts.text.includes(`${marker} note`), 'The typed note is not drawn into the annotated PDF');
    h.assert(sha(source.file) === sourceHash && sql.value(`SELECT status FROM document WHERE document_no=${source.id}`) === 'A',
      'Annotating changed the source document');
  });

  await s.step('Master Record ▸ Documents lists the source and the annotated copy', async () => {
    const report = await s.popup(s.master, s.master.locator('a[onclick*="/documentManager/ViewDocumentReport"]').first(), 'annotate-edoc-report');
    await report.locator(`#docNo${source.id}`).waitFor({ state: 'attached', timeout: TIMEOUT });
    await report.locator(`#docNo${s.annotated}`).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await report.locator(`a[title="${source.label} (annotated)"]`).count() === 1, 'The report does not list the annotated copy by its description');
    await report.close();
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-document-annotate-save', workflow,
  { openPatient: true, preflight: () => requirePoppler('pdfinfo', 'pdftotext') });
