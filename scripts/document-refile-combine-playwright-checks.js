#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Stored-document editing, combining, browsing and refiling, driven from the chart.
 * User path: Schedule > Search > Master Record > Documents (eDoc report popup) >
 *   Edit (ViewEditDocument popup) > Update; tick two PDFs > Combine PDF (combinePDFs);
 *   Browse (ViewDocumentBrowser) > select one / two documents > Refile to the default queue
 *   (DocumentRefile), with the browser's two-document combined preview.
 * Asserts: the edit popup posts, closes and reloads the report, and the document row carries
 * the new description, type and observation date; the combined download is a %PDF whose
 * page count (pdfinfo) is the sum of both owned PDFs and whose text carries both; the
 * browser renders without script errors, previews the combined PDF, refiles the selected
 * document as an exact byte copy under INCOMINGDOCUMENT_DIR/1/Refile, and DocumentRefile
 * refuses GET without copying anything.
 * Fixtures: owned patient, two owned PDFs (rows + files under DOCUMENT_DIR, names carry the
 * marker), the refiled copy; all removed and verified gone. Turns on the test provider's
 * edoc_browser_in_document_report preference (the only opener of the browser) and restores
 * it exactly, so run with EXCLUSIVE=1.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md eDocument section
 * (document-refile-combine). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR), INCOMINGDOCUMENT_DIR,
 * plus the harness contract; needs pdfinfo/pdftotext.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  requirePoppler, pdfFacts, sha, directory, ownedPdfDocuments, seedOwnedPdfDocuments,
  removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

const PREFERENCE = 'edoc_browser_in_document_report';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const incoming = directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-refile-combine-'));
  const docs = ownedPdfDocuments(store, marker, [{ key: 'A', pages: 2 }, { key: 'B', pages: 3 }]);
  const refiled = path.join(incoming, '1', 'Refile', `R${marker}-A.pdf`);
  const owned = { sql, marker, patient, docs, files: [...docs.map(doc => doc.file), refiled] };
  const preferenceWhere = `provider_no=${h.sqlString(provider)} AND name=${h.sqlString(PREFERENCE)}`;
  const preference = sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE ${preferenceWhere} ORDER BY id`);
  h.assert(preference.length <= 1, 'The test provider has duplicate document-browser preference rows');

  s.cleanup(() => {
    removeOwnedPdfDocuments(owned);
    fs.rmSync(scratch, { recursive: true, force: true });
    if (preference.length) {
      const [id, value, isNull] = preference[0];
      sql.execute(`UPDATE property SET value=${isNull === '1' ? 'NULL' : h.sqlString(value)} WHERE id=${id} AND ${preferenceWhere}`);
    } else sql.execute(`DELETE FROM property WHERE ${preferenceWhere}`);
    h.assert(JSON.stringify(sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE ${preferenceWhere} ORDER BY id`))
      === JSON.stringify(preference), 'The document-browser preference was not restored');
    assertOwnedPdfDocumentsRemoved(owned);
  });

  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  if (preference.length) sql.execute(`UPDATE property SET value='yes' WHERE id=${preference[0][0]} AND ${preferenceWhere}`);
  else sql.execute(`INSERT INTO property (provider_no,name,value) VALUES (${h.sqlString(provider)},${h.sqlString(PREFERENCE)},'yes')`);
  const [docA, docB] = docs;

  let report;
  await s.step('Master Record > Documents lists both owned PDFs', async () => {
    report = await s.popup(s.master, s.master.locator('a[onclick*="/documentManager/ViewDocumentReport"]').first(), 'edoc-report');
    for (const doc of docs) {
      await report.locator(`#docNo${doc.id}`).waitFor({ state: 'attached' });
      h.assert(await report.locator(`a[title="${doc.label}"]`).count() === 1, 'An owned document is missing from the report');
    }
  });

  const edited = { desc: `${docA.label} edited`, type: 'consult', date: '2026-02-03' };
  await s.step('Edit popup (ViewEditDocument) saves description, type and date, then reloads the report', async () => {
    const row = report.locator('tr', { has: report.locator(`#docNo${docA.id}`) });
    const editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-edit');
    h.assert(await editor.locator('input[name="mode"]').inputValue() === docA.id, 'The edit popup opened another document');
    await editor.locator('select[name="docType"]').selectOption(edited.type);
    await editor.locator('input[name="docDesc"]').fill(edited.desc);
    await editor.locator('#observationDate').fill(edited.date);
    const [post] = await Promise.all([
      editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
        && response.request().method() === 'POST'),
      report.waitForEvent('load'),
      editor.locator('input[name="Submit"]').click(),
    ]);
    h.assert(post.status() < 400, `The document edit POST answered HTTP ${post.status()}`);
    if (!editor.isClosed()) await editor.waitForEvent('close');
    await report.locator(`a[title="${edited.desc}"]`).waitFor({ state: 'attached' });
    await expectValue(sql, `SELECT CONCAT_WS('|',docdesc,doctype,observationdate,status) FROM document WHERE document_no=${docA.id}`,
      `${edited.desc}|${edited.type}|${edited.date}|A`, 'The edited description, type or observation date did not reach the document row');
    h.assert(sql.value(`SELECT CONCAT_WS('|',docfilename,contenttype) FROM document WHERE document_no=${docA.id}`)
      === `${docA.filename}|application/pdf`, 'Editing metadata changed the stored file reference');
  });

  await s.step('Combine PDF downloads one PDF holding both owned documents (pdfinfo page count)', async () => {
    for (const doc of docs) await report.locator(`#docNo${doc.id}`).check();
    const button = report.locator('input[type="button"][onclick*="submitForm"]');
    // Settled either way so a refused click cannot leave an unhandled rejection behind.
    const downloaded = report.waitForEvent('download').catch(error => error);
    const [request] = await Promise.all([
      report.waitForRequest(candidate => candidate.url().includes('combinePDFs') && candidate.method() === 'POST'),
      button.click(),
    ]);
    h.assert(new URL(request.url()).pathname === new URL(h.appUrl(s.config.baseUrl, '/documentManager/combinePDFs')).pathname,
      'Combine PDF posts somewhere other than the combinePDFs action');
    const download = await downloaded;
    if (download instanceof Error) throw download;
    h.assert(!(await download.failure()), 'The combined PDF download failed');
    const file = path.join(scratch, 'combined.pdf');
    await download.saveAs(file);
    const facts = pdfFacts(file);
    h.assert(facts.pages === docA.pages + docB.pages, `The combined PDF has ${facts.pages} pages, expected ${docA.pages + docB.pages}`);
    for (const doc of docs) for (let page = 1; page <= doc.pages; page++) {
      h.assert(facts.text.includes(`${doc.label} page ${page}`), 'The combined PDF is missing a page of an owned document');
    }
  });

  let browser;
  await s.step('Browse opens the document browser without script errors', async () => {
    const link = report.locator('a[href*="/documentManager/ViewDocumentBrowser"]').first();
    await Promise.all([report.waitForURL(url => url.pathname.endsWith('/documentManager/ViewDocumentBrowser')), link.click()]);
    browser = report;
    await browser.locator('#doclist').waitFor({ state: 'attached' });
    h.assertStrictPage(s.recorder);
    for (const doc of docs) h.assert(await browser.locator(`#doclist option[value^="${doc.id}-"]`).count() === 1,
      'An owned document is missing from the document browser');
  });

  await s.step('Selecting two PDFs previews them combined (combinePDFs inline)', async () => {
    const values = docs.map(doc => `${doc.id}-application/pdf`);
    const [preview] = await Promise.all([
      browser.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/combinePDFs')),
      browser.locator('#doclist').selectOption(values),
    ]);
    h.assert(preview.status() === 200 && /application\/pdf/.test(preview.headers()['content-type'] || ''),
      'The combined preview did not answer a PDF');
    h.assert(await browser.locator('#docdisp iframe[src*="combinePDFs"]').count() === 1, 'The combined preview frame was not shown');
  });

  await s.step('Refile copies the selected PDF byte-for-byte into the default queue', async () => {
    await browser.locator('#doclist').selectOption(`${docA.id}-application/pdf`);
    await browser.locator('#refilebutton').waitFor({ state: 'visible' });
    await browser.locator('#queueList').selectOption('1');
    const [post] = await Promise.all([
      browser.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/DocumentRefile')
        && response.request().method() === 'POST'),
      browser.waitForURL(url => url.pathname.endsWith('/documentManager/ViewDocumentBrowser')),
      browser.locator('#refilebutton input[type="button"]').click(),
    ]);
    h.assert(post.status() === 302, `The refile POST answered HTTP ${post.status()}`);
    await browser.locator('#doclist').waitFor({ state: 'attached' });
    h.assert(!new URL(browser.url()).searchParams.get('errorMessage'), 'The document browser reported a refile failure');
    h.assert(fs.existsSync(refiled) && sha(refiled) === sha(docA.file), 'The refiled copy is missing or differs from the stored PDF');
    h.assert(sql.value(`SELECT CONCAT_WS('|',status,docfilename) FROM document WHERE document_no=${docA.id}`)
      === `A|${docA.filename}`, 'Refiling changed the source document row');
  });

  await s.step('DocumentRefile refuses GET and copies nothing', async () => {
    fs.unlinkSync(refiled);
    const query = new URLSearchParams({ refileDocumentNo: docA.id, queueId: '1' }).toString();
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/documentManager/DocumentRefile?${query}`), { maxRedirects: 0, failOnStatusCode: false });
    h.assert(response.status() === 405, `GET DocumentRefile answered HTTP ${response.status()}, expected 405`);
    h.assert(!fs.existsSync(refiled), 'GET DocumentRefile copied the document');
  });
}

if (require.main === module) runWorkflow('document-refile-combine', workflow, {
  openPatient: true, preflight: () => requirePoppler('pdfinfo', 'pdftotext'),
});
module.exports = { workflow };
