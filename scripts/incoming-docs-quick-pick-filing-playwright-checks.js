#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Incoming Docs' Fast entry mode: the quick picks no check presses. incoming-pdf-extraction and
 * incoming-docs-delete-pdf-mrp file through the patient search in Normal mode.
 * User path: Schedule ▸ Inbox ▸ Incoming Docs (documentManager/ViewIncomingDocs) ▸ Data Entry Mode Fast ▸ queue 1,
 * File ▸ a queued PDF ▸ a document type button (selectDocType), a Last Patients button (loadRecentDemo) and a flag
 * provider button (addflagprovider) ▸ Save.
 * What the page means (read from incomingDocs.jsp and IncomingDocUtil): Fast mode adds a button per document type, a
 * button per patient filed for in this session (Last Patients, newest first, from the lastdemographic_no each save
 * passes back), and a button per provider the login's recent documents were routed to; each button fills the field it
 * names and nothing else.
 * Asserted, on the check's own queued PDFs and two owned FAKE patients only:
 *   - Data Entry Mode Fast stores the login's choice and offers the document type buttons;
 *   - filing a PDF for each owned patient through the patient search lists both under Last Patients, and offers the
 *     test login as a flag provider button;
 *   - LAST: on a third PDF, a document type button, the first patient's Last Patients button and the test login's
 *     provider button fill exactly the type, the patient and the flag list, and Save files the document for that
 *     patient only, with that type, routed to the test login only; the second patient's document and routing are
 *     unchanged, and the queue loses exactly that PDF.
 * Fixtures: three synthetic PDFs written into queue 1's File folder (INCOMINGDOCUMENT_DIR/1/File, which must exist);
 * the owned FAKE patient and a second owned FAKE patient, both with the test login as MRP; each one's program admission
 * (incoming-filing-program-fixture), which filing needs. The login's entry-mode preference is recorded and
 * restored exactly. The queue and the login's recent routing are shared, so the check must not run concurrently with
 * other checks (live wrapper: EXCLUSIVE=1). Cleanup removes the filed documents with their routing, links and stored
 * files (DOCUMENT_DIR), the queued PDFs left and the second patient, and asserts each is gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createOwnedPatient, removeOwnedPatient } = require('./lib/owned-patient');
const { prepareIncomingFilingProgram } = require('./lib/incoming-filing-program-fixture');
const { cleanupFiling } = require('./lib/incoming-filing-capacity-check');
const { fixturePdf } = require('./incoming-pdf-extraction-playwright-checks');

const TIMEOUT = 60000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  fast: 'Data Entry Mode Fast stores the choice and offers the document type buttons',
  recent: 'filing for two owned patients lists both under Last Patients and offers the test login as a provider button',
  quick: 'the quick picks file a PDF for the chosen last patient only, with the picked type, routed to the picked provider',
};

const digest = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
/** The regular files of a folder by name, with digests; the queue's own ".carlos-" scratch files are left out. */
function listing(directory) {
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
  const documents = process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR;
  h.assert(documents, 'Set RX_FAX_DOCUMENT_DIR or DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(documents);

  const name = { a: `${marker}-first.pdf`, b: `${marker}-second.pdf`, quick: `${marker}-quick.pdf` };
  const description = { a: `${marker} quick-pick first`, b: `${marker} quick-pick second`, quick: `${marker} quick-pick filed` };
  const owned = [];
  // Callbacks unwind in reverse: the second patient's admission, files and filings, the preference, the second patient,
  // then the first patient's program fixture.
  prepareIncomingFilingProgram(s);
  let second = null;
  s.cleanup(() => {
    if (second) removeOwnedPatient(sql, second, marker);
    h.assert(!second || sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${second}`) === '0',
      'The second owned patient was not removed');
  });
  const key = 'incoming_document_entry_mode';
  const preference = sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE provider_no=${q(provider)} AND name=${q(key)} ORDER BY id`);
  s.cleanup(() => {
    const ids = preference.map(([id]) => id);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid preference fixture identity');
    sql.execute(`DELETE FROM property WHERE provider_no=${q(provider)} AND name=${q(key)}${ids.length ? ` AND id NOT IN(${ids.join(',')})` : ''}`);
    for (const [id, value, isNull] of preference) {
      sql.execute(`UPDATE property SET value=${isNull === '1' ? 'NULL' : q(value)} WHERE id=${id} AND provider_no=${q(provider)} AND name=${q(key)}`);
    }
    h.assert(JSON.stringify(sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE provider_no=${q(provider)} AND name=${q(key)} ORDER BY id`))
      === JSON.stringify(preference), 'The entry-mode preference was not restored');
  });
  s.cleanup(() => {
    cleanupFiling(s, description.a, store);
    cleanupFiling(s, description.quick, store);
    if (second) cleanupFiling({ ...s, patient: second }, description.b, store);
    for (const file of owned) if (fs.existsSync(file)) fs.unlinkSync(file);
    h.assert(owned.every(file => !fs.existsSync(file)), 'An owned queue file was not removed');
  });
  second = createOwnedPatient(sql, { marker, provider });
  // Filing needs the patient in the login's program domain; each owned patient gets its own admission. Its cleanup is
  // registered last, so it runs first, while the second patient still exists.
  prepareIncomingFilingProgram({ ...s, patient: second });
  const queueOwner = fs.statSync(directory);
  for (const k of ['a', 'b', 'quick']) {
    const file = path.join(directory, name[k]);
    fs.writeFileSync(file, fixturePdf(`${marker} ${k}`), { flag: 'wx', mode: 0o640 });
    owned.push(file);
    if (fs.statSync(file).uid !== queueOwner.uid || fs.statSync(file).gid !== queueOwner.gid) {
      fs.chownSync(file, queueOwner.uid, queueOwner.gid);
    }
  }
  const queueBefore = listing(directory);

  const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
    { context: s.context, recorder: s.recorder, label: 'incoming-quick-inbox', timeout: TIMEOUT });
  const page = await s.popup(inbox, inbox.locator('a[href*="ViewIncomingDocs"]').first(), 'incoming-quick');
  async function reloadAfter(action) {
    await Promise.all([page.waitForEvent('domcontentloaded', { timeout: TIMEOUT }), action()]);
    await page.waitForLoadState('networkidle');
  }
  async function openPdf(file) {
    if (await page.locator('#queueList').inputValue() !== '1') await reloadAfter(() => page.locator('#queueList').selectOption('1'));
    await reloadAfter(() => page.locator('button[onclick="loadPdf(\'1\',\'File\');"]').first().click());
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(file));
    h.assert((await page.locator('fieldset legend').allTextContents()).some(text => text.includes(file)), `Incoming Docs did not open ${file}`);
  }
  const flagged = () => page.locator('#providerList input[name="flagproviders"]').evaluateAll(inputs => inputs.map(input => input.value));
  async function save() {
    await Promise.all([
      page.waitForURL(url => url.pathname.endsWith('/documentManager/ViewIncomingDocs') && url.searchParams.get('queueId') === '1',
        { timeout: TIMEOUT }),
      page.locator('#save').click(),
    ]);
    await page.waitForLoadState('networkidle');
  }
  /** Files the open PDF for a patient chosen through the patient search, flagged to the patient's MRP. */
  async function fileThroughSearch(who, desc) {
    const type = await page.locator('#docType option').evaluateAll(options => options.find(option => option.value)?.value);
    h.assert(type, 'The filing panel offers no document type');
    await page.locator('#docType').selectOption(type);
    await page.locator('#documentDescription').fill(desc);
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
    // Picked by identity, never by name text: both owned patients carry the run marker.
    const matches = await candidates.evaluateAll((items, wanted) => items.map((item, index) => {
      const data = window.jQuery(item).data('ui-autocomplete-item');
      return data && String(data.demographicNo) === wanted ? index : -1;
    }).filter(index => index >= 0), who);
    h.assert(matches.length === 1, 'The patient search does not offer the owned patient exactly once');
    await candidates.nth(matches[0]).click();
    h.assert(await page.locator('#demofind').inputValue() === who, 'The patient search did not select the owned patient');
    await page.locator('input[type="button"][value="MRP"]').click();
    h.assert(JSON.stringify(await flagged()) === JSON.stringify([provider]), 'MRP did not flag the test login');
    await save();
  }
  const filed = desc => sql.rows(`SELECT document_no FROM document WHERE docdesc=${q(desc)} AND doccreator=${q(provider)}`).map(([id]) => id);
  const docState = id => JSON.stringify({
    links: sql.rows(`SELECT module,module_id FROM ctl_document WHERE document_no=${id} ORDER BY module,module_id`),
    patients: sql.rows(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${id} ORDER BY demographic_no`),
    providers: sql.rows(`SELECT provider_no,status FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${id} ORDER BY provider_no`),
  });

  await s.step(STEP.fast, async () => {
    await openPdf(name.a);
    if (await page.locator('#entryModeList').inputValue() !== 'Fast') {
      await reloadAfter(() => page.locator('#entryModeList').selectOption('Fast'));
    }
    h.assert(await page.locator('#entryModeList').inputValue() === 'Fast', 'Data Entry Mode did not switch to Fast');
    h.assert(sql.value(`SELECT value FROM property WHERE provider_no=${q(provider)} AND name=${q(key)} ORDER BY id DESC LIMIT 1`) === 'Fast',
      'Fast was not stored as the login\'s entry mode');
    h.assert(await page.locator('input[type="button"][onclick^="selectDocType("]').count() > 0, 'Fast mode offers no document type buttons');
  });

  await s.step(STEP.recent, async () => {
    await openPdf(name.a);
    await fileThroughSearch(patient, description.a);
    await openPdf(name.b);
    await fileThroughSearch(second, description.b);
    h.assert(filed(description.a).length === 1 && filed(description.b).length === 1, 'The two set-up filings did not each file one document');
    await openPdf(name.quick);
    for (const who of [patient, second]) {
      h.assert(await page.locator(`#demvalueid${who}`).count() === 1, `Last Patients does not offer owned patient ${who}`);
    }
    const order = await page.locator('input[id^="demvalueid"]').evaluateAll(buttons => buttons.map(button => button.id.slice('demvalueid'.length)));
    h.assert(order.indexOf(second) >= 0 && order.indexOf(second) < order.indexOf(patient),
      `Last Patients does not list the newest first (${JSON.stringify(order)})`);
    h.assert(await page.locator(`input[type="button"][onclick^="addflagprovider("][onclick$="'${provider}');"]`).count() === 1,
      'Fast mode offers no provider button for the test login');
  });

  await s.step(STEP.quick, async () => {
    const secondDocument = filed(description.b)[0];
    const secondBefore = docState(secondDocument);
    const secondCount = sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${second}`);
    const typeButton = page.locator('input[type="button"][onclick^="selectDocType("]').first();
    const wantedType = await typeButton.getAttribute('title');
    await typeButton.click();
    const chosen = await page.locator('#docType').evaluate(select => select.options[select.selectedIndex].text.trim());
    h.assert(chosen === wantedType, `The document type button chose "${chosen}", not "${wantedType}"`);
    await page.locator('#documentDescription').fill(description.quick);
    await page.locator(`#demvalueid${patient}`).click();
    h.assert(await page.locator('#demofind').inputValue() === patient, 'The Last Patients button did not choose the first owned patient');
    h.assert((await page.locator('#autocompletedemo').inputValue()).toUpperCase().includes(marker.toUpperCase()),
      'The Last Patients button did not show the chosen patient');
    h.assert((await flagged()).length === 0, 'Choosing a Last Patient flagged a provider by itself');
    await page.locator(`input[type="button"][onclick^="addflagprovider("][onclick$="'${provider}');"]`).click();
    h.assert(JSON.stringify(await flagged()) === JSON.stringify([provider]), `The provider button did not flag exactly the test login (${JSON.stringify(await flagged())})`);
    await save();
    const quick = filed(description.quick);
    h.assert(quick.length === 1, `Save filed ${quick.length} documents, not one`);
    const [documentNo] = quick;
    const state = JSON.parse(docState(documentNo));
    h.assert(JSON.stringify(state.links) === JSON.stringify([['demographic', patient]])
      && JSON.stringify(state.patients) === JSON.stringify([[patient]]), `The quick-pick document is not the first patient's only (${JSON.stringify(state)})`);
    h.assert(JSON.stringify(state.providers.map(([no]) => no)) === JSON.stringify([provider]), 'The quick-pick document is not routed to the test login only');
    h.assert(sql.value(`SELECT doctype FROM document WHERE document_no=${documentNo}`) === wantedType, 'The quick-pick document does not carry the picked type');
    h.assert(docState(secondDocument) === secondBefore
      && sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${second}`) === secondCount,
    'The second patient\'s documents or routing changed');
    const expected = { ...queueBefore };
    for (const k of ['a', 'b', 'quick']) delete expected[name[k]];
    h.assert(JSON.stringify(listing(directory)) === JSON.stringify(expected), 'Filing did not take exactly the owned PDFs from the queue');
  });
}

if (require.main === module) runWorkflow('incoming-docs-quick-pick-filing', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
