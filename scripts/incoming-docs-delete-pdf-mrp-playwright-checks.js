#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Two Incoming Docs controls no check presses: the viewer's Delete PDF, and the filing panel's MRP button.
 * incoming-pdf-extraction covers rotate, page delete, extract and filing, and sends Delete PDF only as a refused GET.
 * User path: Schedule ▸ Inbox ▸ Incoming Docs (documentManager/ViewIncomingDocs) ▸ queue 1, File ▸ a queued PDF ▸
 * Delete PDF (with its confirmation); and, for another queued PDF, the filing panel's patient search ▸ MRP ▸ Save.
 * What the page means (read from incomingDocs.jsp, IncomingDocUtil and ManageDocument2Action): Delete PDF asks first,
 * then removes the PDF from the queue, into the queue's <dir>_deleted recycle folder when INCOMINGDOCUMENT_RECYCLEBIN
 * is on (the packaged default); in the File folder the patient search does not flag the patient's MRP by itself, and
 * the MRP button adds exactly that provider to the flag list, which filing routes the document to.
 * Asserted, on the check's own queued PDFs only (each named with the run marker, so no real file has the name):
 *   - Delete PDF with its confirmation dismissed sends nothing, and the PDF and the queue are unchanged;
 *   - Delete PDF accepted removes exactly that PDF from the queue, with the same bytes in the recycle folder, and
 *     every other file in the queue is unchanged;
 *   - MRP before a patient is chosen alerts and flags nobody;
 *   - LAST: choosing the owned patient flags nobody by itself, MRP flags exactly the patient's MRP (the test login),
 *     and Save files the document for the patient, routed to that provider only, leaving the other queued files.
 * Fixtures: three synthetic PDFs written into queue 1's File folder (INCOMINGDOCUMENT_DIR/1/File, which must exist);
 * the owned FAKE patient, whose MRP is the test login; its program admission (incoming-filing-program-fixture), which
 * filing needs. The queue is shared by every login, so the check must not run concurrently with other checks (live
 * wrapper: EXCLUSIVE=1). Cleanup removes the filed document with its routing, links and stored file (DOCUMENT_DIR),
 * the queued PDFs left and the recycled copy, and asserts each is gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareIncomingFilingProgram } = require('./lib/incoming-filing-program-fixture');
const { cleanupFiling } = require('./lib/incoming-filing-capacity-check');
const { fixturePdf } = require('./incoming-pdf-extraction-playwright-checks');

const TIMEOUT = 60000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  dismiss: 'Delete PDF with its confirmation dismissed sends nothing and keeps the PDF',
  remove: 'Delete PDF removes exactly that PDF from the queue into its recycle folder and leaves every other file',
  mrpFirst: 'MRP before a patient is chosen alerts and flags nobody',
  mrp: 'MRP flags exactly the patient\'s MRP, and Save files the document routed to that provider only',
};

const digest = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');

/**
 * The regular files of a folder, by name, with their digests. The queue reaps its own abandoned ".carlos-" scratch
 * files when it is opened (incoming-pdf-extraction covers that), so those are left out.
 */
function listing(directory) {
  if (!fs.existsSync(directory)) return {};
  return Object.fromEntries(fs.readdirSync(directory).sort()
    .filter(name => !name.startsWith('.carlos-') && fs.lstatSync(path.join(directory, name)).isFile())
    .map(name => [name, digest(path.join(directory, name))]));
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  h.assert(process.env.INCOMINGDOCUMENT_DIR, 'Set INCOMINGDOCUMENT_DIR to the disposable deployment incoming-document directory');
  const root = fs.realpathSync(process.env.INCOMINGDOCUMENT_DIR);
  const directory = path.join(root, '1', 'File');
  h.assert(fs.existsSync(directory), 'Create the test queue 1/File directory with application ownership before this check');
  h.assert(fs.realpathSync(directory).startsWith(root + path.sep), 'Test queue resolves outside the incoming-document directory');
  const recycle = path.join(root, '1', 'File_deleted');
  const recycleOn = (process.env.INCOMINGDOCUMENT_RECYCLEBIN || 'true').trim() !== 'false';
  const documents = process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR;
  h.assert(documents, 'Set RX_FAX_DOCUMENT_DIR or DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(documents);
  h.assert(sql.value(`SELECT provider_no FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`) === provider,
    'The owned patient\'s MRP is not the test login');

  const name = { remove: `${marker}-delete.pdf`, keep: `${marker}-keep.pdf`, file: `${marker}-file.pdf` };
  const description = `${marker} incoming MRP filing`;
  const owned = [];
  // Before the filing cleanup, which the program fixture's own cleanup must follow (callbacks unwind in reverse).
  prepareIncomingFilingProgram(s);
  s.cleanup(() => {
    cleanupFiling(s, description, store);
    for (const file of owned) if (fs.existsSync(file)) fs.unlinkSync(file);
    h.assert(owned.every(file => !fs.existsSync(file)), 'An owned queue file was not removed');
  });
  const queueOwner = fs.statSync(directory);
  for (const key of ['remove', 'keep', 'file']) {
    const file = path.join(directory, name[key]);
    fs.writeFileSync(file, fixturePdf(`${marker} ${key}`), { flag: 'wx', mode: 0o640 });
    owned.push(file);
    // Owned like the queue, as a scanner or fax relay leaves its files, so the application can move them.
    if (fs.statSync(file).uid !== queueOwner.uid || fs.statSync(file).gid !== queueOwner.gid) {
      fs.chownSync(file, queueOwner.uid, queueOwner.gid);
    }
  }
  const others = before => Object.fromEntries(Object.entries(before).filter(([file]) => !Object.values(name).includes(file)));
  const queueBefore = listing(directory);

  const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
    { context: s.context, recorder: s.recorder, label: 'incoming-delete-mrp-inbox', timeout: TIMEOUT });
  const page = await s.popup(inbox, inbox.locator('a[href*="ViewIncomingDocs"]').first(), 'incoming-delete-mrp');
  async function reloadAfter(action) {
    await Promise.all([page.waitForEvent('domcontentloaded', { timeout: TIMEOUT }), action()]);
    await page.waitForLoadState('networkidle');
  }
  async function openPdf(file) {
    if (await page.locator('#queueList').inputValue() !== '1') await reloadAfter(() => page.locator('#queueList').selectOption('1'));
    await reloadAfter(() => page.locator('button[onclick="loadPdf(\'1\',\'File\');"]').first().click());
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(file));
    h.assert((await page.locator('fieldset legend').allTextContents()).some(text => text.includes(file)),
      `Incoming Docs did not open ${file}`);
  }
  const offered = () => page.locator('#SelectPdfList option').evaluateAll(options => options.map(option => option.value));
  const flagged = () => page.locator('#providerList input[name="flagproviders"]').evaluateAll(inputs => inputs.map(input => input.value));
  const deleteButton = () => page.locator('button[onclick^="deletePdf("]');

  await s.step(STEP.dismiss, async () => {
    await openPdf(name.remove);
    const posts = [];
    const onRequest = request => { if (request.method() !== 'GET') posts.push(request.url()); };
    page.on('request', onRequest);
    let dialogs;
    try {
      dialogs = await h.withExpectedDialogs(page, () => deleteButton().click(), { accept: false });
      await page.waitForLoadState('networkidle');
    } finally {
      page.off('request', onRequest);
    }
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text.includes(name.remove),
      `Delete PDF did not ask once to delete ${name.remove} (${JSON.stringify(dialogs)})`);
    h.assert(posts.length === 0, `The dismissed Delete PDF still sent ${JSON.stringify(posts)}`);
    h.assert(JSON.stringify(listing(directory)) === JSON.stringify(queueBefore), 'The dismissed Delete PDF changed the queue');
  });

  await s.step(STEP.remove, async () => {
    const recycleBefore = listing(recycle);
    const dialogs = await h.withExpectedDialogs(page, () => reloadAfter(() => deleteButton().click()));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text.includes(name.remove),
      `Delete PDF did not ask once to delete ${name.remove} (${JSON.stringify(dialogs)})`);
    const queueAfter = listing(directory);
    const expected = { ...queueBefore };
    delete expected[name.remove];
    h.assert(JSON.stringify(queueAfter) === JSON.stringify(expected),
      `Delete PDF did not remove exactly ${name.remove} from the queue (left: ${JSON.stringify(Object.keys(queueAfter))})`);
    h.assert(!(await offered()).includes(name.remove), 'The queue list still offers the deleted PDF');
    const recycleAfter = listing(recycle);
    const recycled = Object.keys(recycleAfter).filter(file => !(file in recycleBefore));
    for (const file of recycled) if (file.startsWith(marker)) owned.push(path.join(recycle, file));
    if (recycleOn) {
      h.assert(recycled.length === 1 && recycled[0].startsWith(marker) && recycleAfter[recycled[0]] === queueBefore[name.remove],
        `The recycle folder did not receive exactly the deleted PDF (${JSON.stringify(recycled)})`);
    } else {
      h.assert(recycled.length === 0, `Delete PDF with the recycle bin off still wrote ${JSON.stringify(recycled)}`);
    }
    h.assert(Object.keys(recycleBefore).every(file => recycleAfter[file] === recycleBefore[file]),
      'Delete PDF changed a file already in the recycle folder');
  });

  await s.step(STEP.mrpFirst, async () => {
    await openPdf(name.file);
    h.assert(await page.locator('#demofind').inputValue() === '-1' && (await flagged()).length === 0,
      'A newly opened queued PDF already has a patient or a flagged provider');
    const dialogs = await h.withExpectedDialogs(page, () => page.locator('input[type="button"][value="MRP"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert',
      `MRP with no patient chosen did not alert once (${JSON.stringify(dialogs)})`);
    h.assert((await flagged()).length === 0, 'MRP with no patient chosen flagged a provider');
  });

  await s.step(STEP.mrp, async () => {
    const type = await page.locator('#docType option').evaluateAll(options => options.find(option => option.value)?.value);
    h.assert(type, 'The filing panel offers no document type');
    await page.locator('#docType').selectOption(type);
    await page.locator('#documentDescription').fill(description);
    const searched = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/demographic/SearchDemographic')
      && response.request().method() === 'POST'
      && new URLSearchParams(response.request().postData() || '').get('query') === marker, { timeout: TIMEOUT });
    const input = page.locator('#autocompletedemo');
    await input.click();
    await input.fill('');
    await input.pressSequentially(marker, { delay: 30 });
    h.assert((await searched).status() === 200, 'The patient search failed');
    const candidates = page.locator('.ui-autocomplete:visible .ui-menu-item');
    await candidates.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    // Picked by the owned patient's identity, never by name text (the formatter may change case or mark the match).
    const matches = await candidates.evaluateAll((items, wanted) => items.map((item, index) => {
      const data = window.jQuery(item).data('ui-autocomplete-item');
      return data && String(data.demographicNo) === wanted ? index : -1;
    }).filter(index => index >= 0), patient);
    h.assert(matches.length === 1, 'The patient search does not offer the owned patient exactly once');
    await candidates.nth(matches[0]).click();
    h.assert(await page.locator('#demofind').inputValue() === patient, 'Choosing the patient did not select the owned patient');
    h.assert(await page.locator('#MRPNo').inputValue() === provider, 'The patient search did not offer the patient\'s MRP');
    h.assert((await flagged()).length === 0, 'Choosing the patient in the File folder flagged a provider by itself');
    await page.locator('input[type="button"][value="MRP"]').click();
    h.assert(JSON.stringify(await flagged()) === JSON.stringify([provider]),
      `MRP did not flag exactly the patient's MRP (${JSON.stringify(await flagged())})`);
    h.assert((await page.locator('#providerList').innerText()).includes('(MRP)'), 'The flag list does not mark the provider as MRP');
    await Promise.all([
      page.waitForURL(url => url.pathname.endsWith('/documentManager/ViewIncomingDocs') && url.searchParams.get('queueId') === '1',
        { timeout: TIMEOUT }),
      page.locator('#save').click(),
    ]);
    await page.waitForLoadState('networkidle');
    const filed = sql.rows(`SELECT document_no FROM document WHERE docdesc=${q(description)} AND doccreator=${q(provider)}`);
    h.assert(filed.length === 1, `Save filed ${filed.length} documents, not one`);
    const [[documentNo]] = filed;
    h.assert(sql.value(`SELECT GROUP_CONCAT(provider_no ORDER BY provider_no) FROM providerLabRouting
      WHERE lab_type='DOC' AND lab_no=${documentNo}`) === provider, 'The filed document is not routed to the MRP only');
    h.assert(sql.value(`SELECT GROUP_CONCAT(demographic_no) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${documentNo}`) === patient
      && sql.value(`SELECT module_id FROM ctl_document WHERE document_no=${documentNo} AND module='demographic'`) === patient,
    'The filed document is not the owned patient\'s');
    const expected = { ...queueBefore };
    delete expected[name.remove];
    delete expected[name.file];
    h.assert(JSON.stringify(listing(directory)) === JSON.stringify(expected), 'Filing did not take exactly its PDF from the queue');
    h.assert(JSON.stringify(others(listing(directory))) === JSON.stringify(others(queueBefore)), 'A queued file the check does not own changed');
  });
}

if (require.main === module) runWorkflow('incoming-docs-delete-pdf-mrp', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
