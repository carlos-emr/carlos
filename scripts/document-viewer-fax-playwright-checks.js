#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The document viewer's Fax: a stored PDF document goes to the shared fax cover page, and Cancel discards it. Send is never
 * pressed, so nothing can leave the host.
 *
 * WHY THIS CHECK EXISTS. A clinician faxes a patient document from its viewer (documentManager/FaxDocument). The action
 * hands the document to the shared fax pipeline (fax/faxAction method=prepareFax through EFormFaxPreparation's auto-submit),
 * which stages a copy and renders the cover page; Cancel (method=cancel) deletes that copy. Only the live-external manual
 * check scripts/e2e/fax/annotate-document drove it; nothing in the suite did. A cover page staging the wrong document, or a
 * Cancel that queues or leaves the copy behind, faxes or keeps patient information it should not.
 *
 * User path (as the shared test login): Schedule > Search > Master Record > E-Chart > the document's entry in Documents (the
 * document viewer) > Fax (the fax popup) > the cover page > Cancel.
 * Asserted:
 *   - With an active fax account: Fax opens the cover page for exactly this document (transactionType DOCUMENT, its id and
 *     its patient), offering the active accounts; the staged PDF it previews carries the document's text; no fax job exists
 *     for the patient after Fax, after the preparation page's auto-submit or after the cover page loads. Cancel answers, the
 *     staged copy is no longer served, and still no fax job exists.
 *   - With no active fax account: the viewer offers no Fax button, the button's own route (as a stale page or
 *     bookmark opens it) explains that no fax account is configured, and no fax job exists.
 * Send is pressed in neither case. Fax recipient fields are left as the page fills them.
 * Fixtures: viewerDocuments (document-viewer-review-actions-playwright-checks.js): the owned FAKE- patient and one owned
 * PDF routed New to the test login and another active provider, with an active queue link, so the check must not run
 * concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup removes the document's rows and file and asserts them
 * gone. Needs a pdftotext (Poppler) to read the staged copy.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { runWorkflow } = require('./lib/workflow-session');
const { viewerDocuments } = require('./document-viewer-review-actions-playwright-checks');

const NAME = 'document-viewer-fax';
const TIMEOUT = 60000;

async function workflow(s) {
  const { sql, patient, marker } = s;
  requirePoppler('pdftotext');
  const faxJobs = () => sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient}`);
  h.assert(faxJobs() === '0', 'The owned patient already has fax jobs');
  const accounts = sql.rows(`SELECT faxNumber FROM fax_config WHERE active=1 AND faxNumber IS NOT NULL ORDER BY id`).map(([n]) => n);
  const { docs, openViewer } = viewerDocuments(s, ['fax']);
  const doc = docs.fax;

  let fax;
  if (accounts.length === 0) {
    await s.step('with no active fax account, the viewer offers no Fax and its fax route explains that none is configured', async () => {
      const viewer = await openViewer(doc);
      // showDocument renders the button only when FaxManager.isEnabled() finds an active account.
      h.assert(await viewer.locator('#printBtn_' + doc.id).count() === 1
        && await viewer.locator(`[id="faxBtn_${doc.id}"]`).count() === 0, 'The viewer offers Fax with no active fax account');
      // The button's own route, as a stale page or bookmark would open it: it must refuse, not stage a copy.
      fax = await s.context.newPage();
      await h.gotoApp(fax, s.config.baseUrl, `/documentManager/FaxDocument?docId=${doc.id}`);
      await fax.getByText('No active fax accounts are configured').first().waitFor({ timeout: TIMEOUT });
      h.assert(faxJobs() === '0', 'Opening the fax route queued a fax job');
      await fax.close();
    });
    return;
  }

  let cover;
  await s.step('Fax opens the cover page for exactly this document and queues nothing', async () => {
    const viewer = await openViewer(doc);
    const faxButton = viewer.locator(`[id="faxBtn_${doc.id}"]`);
    h.assert(await faxButton.isEnabled(), 'The viewer\'s Fax button is disabled for a PDF document');
    const prepared = s.context.waitForEvent('response', { predicate: (r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/fax/faxAction'), timeout: TIMEOUT });
    fax = await clickOpensPopup(viewer, faxButton, { context: s.context, recorder: s.recorder, label: 'document-fax', timeout: TIMEOUT });
    h.assert(faxJobs() === '0', 'Fax queued a fax job before the cover page');
    // EFormFaxPreparation submits itself to fax/faxAction method=prepareFax, which answers with the cover page.
    const preparation = await prepared;
    const sent = new URLSearchParams(new URL(preparation.url()).search);
    h.assert(sent.get('method') === 'prepareFax' && sent.get('transactionType') === 'DOCUMENT' && sent.get('transactionId') === doc.id
      && sent.get('demographicNo') === patient, 'The preparation page did not hand exactly this document and patient to prepareFax');
    cover = fax.locator('#coverPageForm');
    await cover.waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(faxJobs() === '0', 'Preparing the fax queued a fax job');
    const field = (name) => cover.locator(`input[type="hidden"][name="${name}"]`).inputValue();
    h.assert(await field('transactionType') === 'DOCUMENT' && await field('transactionId') === doc.id
      && await field('demographicNo') === patient, 'The cover page is not for exactly this document and its patient');
    const offered = await fax.locator('#senderFaxAccount option').evaluateAll((options) => options.map((o) => o.value));
    h.assert(JSON.stringify([...offered].sort()) === JSON.stringify([...accounts].sort()),
      `The cover page offers sender accounts ${JSON.stringify(offered)}, not the active ones`);
    const preview = await fax.context().request.get(await fax.locator('#previewPdfLink').evaluate((a) => a.href));
    const bytes = await preview.body();
    h.assert(preview.status() === 200 && bytes.subarray(0, 4).toString('latin1') === '%PDF', 'The cover page\'s staged PDF is not a PDF');
    h.assert(pdfTextBuffer(bytes).replace(/\s+/g, ' ').includes(`${marker} fax`), 'The staged PDF is not the document being faxed');
    h.assert(faxJobs() === '0', 'Loading the cover page queued a fax job');
  });

  await s.step('Cancel discards the staged copy and queues nothing', async () => {
    const previewHref = await fax.locator('#previewPdfLink').evaluate((a) => a.href);
    const [cancel] = await Promise.all([
      fax.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/fax/faxAction')
        && new URLSearchParams(r.request().postData() || '').get('method') === 'cancel', { timeout: TIMEOUT }),
      fax.locator('#btnCancel').click(),
    ]);
    h.assert(cancel.status() < 400, `Cancel answered HTTP ${cancel.status()}`);
    h.assert(faxJobs() === '0', 'Cancel queued a fax job');
    const after = await s.context.request.get(previewHref);
    const body = await after.body();
    h.assert(!(after.status() === 200 && body.subarray(0, 4).toString('latin1') === '%PDF'),
      'The staged copy is still served after Cancel');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
