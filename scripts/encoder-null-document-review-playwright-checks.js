#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Document "Reviewed" sign-off for a document nobody has reviewed yet (NULL reviewer).
 * User path: Schedule > Search > Master Record > Documents (eDoc report popup) > Edit
 * (ViewEditDocument popup) > Reviewed.
 * Asserts: the report lists the owned unreviewed PDF; first review posts without a prompt,
 * closes the popup, and stores/displays the current provider with no extra-reviewer row.
 * A second document already reviewed by another provider preserves that primary review while
 * recording/displaying the current provider as an extra reviewer. Neither flow offers a
 * duplicate Reviewed action after reopening or raises a debug alert.
 * Fixtures: owned patient and two owned PDFs (rows + files under DOCUMENT_DIR); cleanup removes
 * both documents, links, files and extra-reviewer rows, and asserts each gone.
 * Risk sweep: encoder-null (null-sentinel regressions of the null-safe encoder migration).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { multipartFields } = require('./lib/get-reject-probe');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, marker, [{ key: 'R', pages: 1 }, { key: 'E', pages: 1 }]);
  const owned = { sql, marker, patient, docs, files: docs.map(doc => doc.file) };
  const extraRows = id => `SELECT COUNT(*) FROM DocumentExtraReviewer WHERE documentNo=${Number(id)}`;

  s.cleanup(() => {
    for (const doc of docs) {
      if (doc.id) {
        sql.execute(`DELETE FROM DocumentExtraReviewer WHERE documentNo=${Number(doc.id)};
          DELETE FROM log WHERE action='reviewed' AND content='document' AND contentId=${h.sqlString(doc.id)}
          AND demographic_no=${patient}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE action='reviewed' AND content='document'
          AND contentId=${h.sqlString(doc.id)} AND demographic_no=${patient}`) === '0', 'Owned review audit rows were not removed');
      }
    }
    h.assert(docs.every(doc => !doc.id || sql.value(extraRows(doc.id)) === '0'),
      'The extra-reviewer rows of the owned document were not removed');
    removeOwnedPdfDocuments(owned);
    assertOwnedPdfDocumentsRemoved(owned);
  });

  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  const [doc, extraDoc] = docs;
  const priorProvider = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${h.sqlString(provider)}
    AND status='1' ORDER BY provider_no LIMIT 1`);
  h.assert(priorProvider, 'A second active provider is required for the extra-review regression');
  const providerName = sql.value(`SELECT CONCAT(UPPER(last_name), ', ', UPPER(first_name)) FROM provider
    WHERE provider_no=${h.sqlString(provider)}`);
  const priorReviewTime = '2026-01-02 03:04:05';
  sql.execute(`UPDATE document SET reviewer=${h.sqlString(priorProvider)}, reviewdatetime=${h.sqlString(priorReviewTime)}
    WHERE document_no=${Number(extraDoc.id)}`);
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
    const submitted = new Map(multipartFields(post.request().postDataBuffer(), post.request().headers()['content-type']));
    h.assert(submitted.get('reviewerId') === '' && submitted.get('reviewDoc') === 'true',
      'First review must ask the server to assign the authenticated provider');
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
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='reviewed' AND content='document'
      AND contentId=${h.sqlString(doc.id)} AND provider_no=${h.sqlString(provider)} AND demographic_no=${patient}`,
    '1', 'First review did not write its provider/patient/document audit event');
    h.assert(dialogs.length === 0, `Reviewed raised ${dialogs.length} dialog(s): ${dialogs.map(d => d.text).join(' | ')}`);
  });

  await s.step('reopening the first document displays its reviewer and offers no duplicate Reviewed action', async () => {
    const row = report.locator('tr', {has:report.locator(`#docNo${doc.id}`)});
    editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-reviewed');
    h.assert(await editor.locator('input[name="reviewerId"]').inputValue() === provider, 'Redisplay lost the primary reviewer');
    const text = (await editor.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(`Reviewed: ${providerName}`), 'Redisplay did not name the primary reviewer');
    h.assert(await editor.locator('input[type="button"][value="Reviewed"]').count() === 0,
      'The same provider can submit a duplicate review');
    await editor.close();
  });

  await s.step('an extra review keeps the original reviewer and date, records the current provider and shows no debug alert', async () => {
    const row = report.locator('tr', {has:report.locator(`#docNo${extraDoc.id}`)});
    editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-extra-review');
    h.assert(await editor.locator('input[name="reviewerId"]').inputValue() === priorProvider,
      'The extra-review fixture did not retain its original reviewer');
    let post;
    const closed = editor.waitForEvent('close', {timeout:20000}).then(() => true, () => false);
    const dialogs = await h.withExpectedDialogs(editor, async () => {
      [post] = await Promise.all([
        editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
          && response.request().method() === 'POST', {timeout:20000}),
        editor.locator('input[type="button"][value="Reviewed"]').click(),
      ]);
    });
    h.assert(post.status() < 400 && await closed, 'Extra review did not complete and close its editor');
    h.assert(sql.value(`SELECT CONCAT(reviewer,'|',reviewdatetime) FROM document WHERE document_no=${extraDoc.id}`)
      === `${priorProvider}|${priorReviewTime}`, 'Extra review replaced the original reviewer or review time');
    h.assert(sql.value(`SELECT COUNT(*) FROM DocumentExtraReviewer WHERE documentNo=${extraDoc.id}
      AND reviewerProviderNo=${h.sqlString(provider)} AND reviewDateTime IS NOT NULL`) === '1',
    'Extra review did not record exactly one dated acknowledgement by the current provider');
    h.assert(sql.value(extraRows(extraDoc.id)) === '1', 'Extra review created another unexpected reviewer row');
    h.assert(dialogs.length === 0, 'Extra review raised a debug alert');
    editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-extra-reviewed');
    const text = (await editor.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(`Reviewed: ${providerName}`), 'Redisplay did not name the extra reviewer');
    h.assert(await editor.locator('input[type="button"][value="Reviewed"]').count() === 0,
      'The extra reviewer can submit a duplicate acknowledgement');
    await editor.close();
  });
}

if (require.main === module) runWorkflow('encoder-null-document-review', workflow, { openPatient: true });
module.exports = { workflow };
