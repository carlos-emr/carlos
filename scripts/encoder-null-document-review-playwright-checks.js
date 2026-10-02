#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Document "Reviewed" sign-off for a document nobody has reviewed yet (NULL reviewer).
 * User path: Schedule > Search > Master Record > Documents (eDoc report popup) > Edit
 * (ViewEditDocument popup) > Reviewed.
 * Why: editDocument.jsp reviewed() decides between "first review" and "extra reviewer" with
 * `reviewerId.value == 'null'`. The hidden reviewerId used to render a NULL reviewer as the
 * literal "null" (Encode/e:forHtmlAttribute); the null-safe <carlos:encode> now renders "", so
 * the first review takes the extra-reviewer branch (a debug alert, a DocumentExtraReviewer row,
 * and the document's own reviewer left NULL).
 * Asserts: the report lists the owned unreviewed PDF; the edit popup offers Reviewed with an
 * empty reviewer; Reviewed posts without a prompt, closes the popup, and records the logged-in
 * provider on document.reviewer/reviewdatetime with no DocumentExtraReviewer row.
 * Fixtures: owned patient (runWorkflow) and one owned PDF (row + file under DOCUMENT_DIR,
 * lib/stored-pdf-documents); cleanup removes the document, its ctl_document link, the file and
 * any DocumentExtraReviewer row for that document id, and asserts each gone.
 * Risk sweep: encoder-null (null-sentinel regressions of the null-safe encoder migration).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, marker, [{ key: 'R', pages: 1 }]);
  const owned = { sql, marker, patient, docs, files: docs.map(doc => doc.file) };
  const extraRows = id => `SELECT COUNT(*) FROM DocumentExtraReviewer WHERE documentNo=${Number(id)}`;

  s.cleanup(() => {
    for (const doc of docs) {
      if (doc.id) sql.execute(`DELETE FROM DocumentExtraReviewer WHERE documentNo=${Number(doc.id)}`);
    }
    h.assert(docs.every(doc => !doc.id || sql.value(extraRows(doc.id)) === '0'),
      'The extra-reviewer rows of the owned document were not removed');
    removeOwnedPdfDocuments(owned);
    assertOwnedPdfDocumentsRemoved(owned);
  });

  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  const [doc] = docs;
  // The column defaults to '' but every document the application stores unreviewed carries
  // NULL (EDocUtil.editDocument sets it explicitly), which is the value this check is about.
  sql.execute(`UPDATE document SET reviewer=NULL, reviewdatetime=NULL WHERE document_no=${Number(doc.id)}`);
  h.assert(sql.value(`SELECT IF(reviewer IS NULL AND reviewdatetime IS NULL,1,0) FROM document WHERE document_no=${doc.id}`) === '1',
    'The owned document fixture is not unreviewed');

  let report;
  await s.step('Master Record > Documents lists the owned unreviewed PDF', async () => {
    report = await s.popup(s.master, s.master.locator('a[onclick*="/documentManager/ViewDocumentReport"]').first(), 'edoc-report');
    await report.locator(`#docNo${doc.id}`).waitFor({ state: 'attached' });
    h.assert(await report.locator(`a[title="${doc.label}"]`).count() === 1, 'The owned document is missing from the report');
  });

  let editor;
  await s.step('Edit popup offers Reviewed for the unreviewed document and names no reviewer', async () => {
    const row = report.locator('tr', { has: report.locator(`#docNo${doc.id}`) });
    editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-edit');
    h.assert(await editor.locator('input[name="mode"]').inputValue() === doc.id, 'The edit popup opened another document');
    h.assert(await editor.locator('input[type="button"][value="Reviewed"]').count() === 1,
      'The edit popup does not offer Reviewed for an unreviewed document');
    h.assert(await editor.locator('input[name="reviewerId"]').inputValue() === '',
      'The edit popup carries a reviewer for a document nobody has reviewed');
  });

  await s.step('Reviewed records the logged-in provider as the document reviewer, with no prompt or extra-reviewer row', async () => {
    let post;
    const closed = editor.waitForEvent('close', { timeout: 20000 }).then(() => true, () => false);
    const dialogs = await h.withExpectedDialogs(editor, async () => {
      [post] = await Promise.all([
        editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
          && response.request().method() === 'POST', { timeout: 20000 }),
        editor.locator('input[type="button"][value="Reviewed"]').click(),
      ]);
    });
    h.assert(post.status() < 400, `The Reviewed POST answered HTTP ${post.status()}`);
    h.assert(await closed, 'The edit popup did not close after Reviewed');
    // Report, don't encode: the first review must land on the document itself. The regression
    // instead raises the debug alert "set extra" and writes a DocumentExtraReviewer row.
    const extra = sql.value(extraRows(doc.id));
    const reviewer = sql.value(`SELECT CASE WHEN reviewer IS NULL THEN 'NULL' WHEN reviewer='' THEN 'empty'
      WHEN reviewer=${h.sqlString(provider)} THEN 'provider' ELSE 'another value' END FROM document WHERE document_no=${doc.id}`);
    h.assert(reviewer === 'provider',
      `Reviewed did not record the provider on the document (reviewer is ${reviewer}; DocumentExtraReviewer rows: ${extra}; `
      + `dialogs raised: ${dialogs.map(d => `${d.type} "${d.text}"`).join(', ') || 'none'})`);
    h.assert(sql.value(`SELECT IF(reviewdatetime IS NULL,0,1) FROM document WHERE document_no=${doc.id}`) === '1',
      'Reviewed did not stamp the review time on the document');
    h.assert(sql.value(extraRows(doc.id)) === '0', 'The first review was stored as an extra reviewer');
    h.assert(dialogs.length === 0, `Reviewed raised ${dialogs.length} dialog(s): ${dialogs.map(d => d.text).join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('encoder-null-document-review', workflow, { openPatient: true });
module.exports = { workflow };
