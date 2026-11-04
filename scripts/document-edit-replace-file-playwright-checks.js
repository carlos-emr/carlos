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
 * the uploaded bytes, and the document records the new file's page count. Last, the document's viewer
 * (documentManager/ViewShowDocument, the Inbox's document pane) offers the new file's pages: it counts 3,
 * it offers Remove first page, Split lists 3 pages, and, when it shows page images (the login's document
 * display preference; the default), its page links reach page 3.
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
const { observeStoredPreviewCapacity } = require('./lib/stored-preview-capacity-check');
const { masterRecordLink } = require('./lib/master-record-link');

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
    report = await s.popup(s.master, masterRecordLink(s.master, '/documentManager/ViewDocumentReport').first(), 'edoc-report');
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

  await s.step('the document viewer offers the replacement\'s pages: a count of 3, Remove first page, a 3-page Split and page links', async () => {
    // The viewer and Split draw their pages from the stored page count, so these follow the step above.
    const manageDocument = new URL('documentManager/ManageDocument', String(s.config.baseUrl).replace(/\/?$/, '/')).href;
    const viewer = await s.context.newPage();
    // Page images load through CarlosDocumentImages, which may meet the server's render-capacity 503 (Retry-After:
    // 1) and retry; the shared observer accepts only refusals that recover to a decoded image of the same page.
    const viewerPreviews = observeStoredPreviewCapacity({ page: viewer, label: 'document-edit-replace-file',
      recorder: s.recorder, documentId: doc.id, endpoint: manageDocument });
    try {
      await h.gotoApp(viewer, s.config.baseUrl, `/documentManager/ViewShowDocument?segmentID=${doc.id}&inWindow=true`);
      await viewer.locator(`#numPages_${doc.id}`).waitFor({ state: 'attached' });
      const counted = (await viewer.locator(`#numPages_${doc.id}`).textContent() || '').trim();
      h.assert(counted === '3', `The viewer counts ${counted} page(s), not the replacement's 3`);
      // Page links are drawn only for page images; with the PDF preference the browser's PDF viewer pages.
      const mode = await viewer.locator(`#displayDocumentAs_${doc.id}`).inputValue();
      console.log(`    viewer display: ${mode}`);
      if (mode === 'Image') {
        // The image's page is in data-document-image-src; CarlosDocumentImages fetches it and fills src itself.
        const pageShown = page => viewer.waitForFunction(({ id, page }) => {
          const image = document.getElementById(`docImg_${id}`);
          const source = image && image.getAttribute('data-document-image-src');
          return !!source && new URL(source, location.href).searchParams.get('curPage') === String(page)
            && image.complete && image.naturalWidth > 0 && image.getAttribute('data-document-image-state') === 'loaded';
        }, { id: doc.id, page });
        const decoded = [];
        const source = async () => viewer.locator(`#docImg_${doc.id}`).getAttribute('data-document-image-src');
        await pageShown(1);
        decoded.push(await source());
        h.assert(await viewer.locator(`#nextP_${doc.id}`).count() === 1, 'The viewer offers no page links');
        for (let page = 2; page <= 3; page++) {
          await viewer.locator(`#nextP_${doc.id}`).click();
          await viewer.waitForFunction(({ id, page }) => document.getElementById(`viewedPage_${id}`)?.textContent.trim() === String(page),
            { id: doc.id, page });
          await pageShown(page);
          decoded.push(await source());
        }
        await viewerPreviews.finish(decoded);
      }
      const remove = viewer.locator(`#removeFirstPagebtn_${doc.id}`);
      h.assert(await remove.count() === 1 && await remove.isVisible(), 'The viewer does not offer Remove first page');
      // Split opens the page picker; only open it, nothing is split. Its page images load together, and the
      // server may refuse some with its render-capacity 503 (Retry-After: 1) before the page retries them; the
      // shared observer accepts only refusals that recover to a decoded image of the same page.
      let previews;
      s.context.once('page', page => {
        previews = observeStoredPreviewCapacity({ page, label: 'document-split', recorder: s.recorder, documentId: doc.id,
          endpoint: new URL('documentManager/ManageDocument', String(s.config.baseUrl).replace(/\/?$/, '/')).href });
      });
      const splitter = await s.popup(viewer, viewer.locator('input[onclick^="split("]'), 'document-split');
      try {
        const images = splitter.locator('#picker img.page');
        await images.first().waitFor({ state: 'attached' });
        const listed = await images.count();
        h.assert(listed === 3, `Split lists ${listed} page(s), not the replacement's 3`);
        for (let index = 0; index < listed; index++) {
          await images.nth(index).scrollIntoViewIfNeeded();
          await splitter.waitForFunction(index => {
            const image = document.querySelectorAll('#picker img.page')[index];
            return image && image.complete && image.naturalWidth > 0 && image.getAttribute('data-document-image-state') === 'loaded';
          }, index);
        }
        await previews.finish(await images.evaluateAll(list => list.map(image => image.getAttribute('data-document-image-src'))));
      } finally {
        if (previews) previews.close();
        await splitter.close().catch(() => {});
      }
    } finally {
      viewerPreviews.close();
      await viewer.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('document-edit-replace-file', workflow);
module.exports = { workflow };
