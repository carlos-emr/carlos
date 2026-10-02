#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Linking an unmatched lab to a patient from the lab display (gap-records, labs).
 * provider-linking-rules posts the match from an address; this check enters the way a clinician does:
 * Schedule ▸ Inbox ▸ HL7 Lab Upload (a synthetic CML lab for a name no patient holds) ▸ Inbox ▸ Unmatched ▸
 * the lab's link (labDisplay popup) ▸ E-Chart / patient link (oscarMDS/SearchPatient: for an unmatched lab the
 * Patient Matching popup, prefilled with the lab's own name) ▸ search the owned patient ▸ pick the row
 * (oscarMDS/PatientMatch) ▸ reopen the lab ▸ E-Chart (SearchPatient now redirects to oscarMDS/ViewOpenEChart).
 * Asserts: the upload lands unmatched (no patientLabRouting demographic); the matching popup carries the lab's
 * name and lists no candidate for it; searching the owned patient lists exactly that patient; picking the row
 * writes the patientLabRouting row for the lab; the reopened lab shows the patient and its E-Chart button opens
 * the owned patient's chart; and, last, the lab display that was open during the match shows the patient without
 * a reload (it does not: the matching popup is redirected and loses its opener, ISSUES L220).
 * Fixtures: the runWorkflow FAKE- patient (its NULL HIN set to the empty HIN the inbox name search needs) and
 * one synthetic CML HL7 lab (unique accession; patient name = marker + "-U"); cleanup removes the lab rows and
 * its archived upload file and asserts them gone.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { settle } = require('./inboxhub-filters-playwright-checks');
const { syntheticCmlLab } = require('./lab-upload-playwright-checks');
const { removeOwnedHl7Labs } = require('./lab-forwarding-rules-playwright-checks');

const TIMEOUT = 30000;

/**
 * Removes the run's own archived upload(s), found by the known upload name in LAB_UPLOAD_DOCUMENT_STORE, and asserts
 * they are gone. Without the store it warns (as removeOwnedHl7Labs does) and keeps them.
 */
function removeNamedArchives(fileName) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived lab upload may be retained: set LAB_UPLOAD_DOCUMENT_STORE to remove LabUpload.${fileName}.*`);
    return;
  }
  const root = fs.realpathSync(store);
  const prefix = `LabUpload.${fileName}.`;
  const mine = fs.readdirSync(root).filter(name => name.startsWith(prefix));
  // Validate EVERY matching name before unlinking any of them, so an unexpected archive cannot leave the run's evidence
  // half-removed.
  for (const name of mine) h.assert(/^\d+$/.test(name.slice(prefix.length)), `Unexpected archive name for this run's upload: ${name}`);
  for (const name of mine) {
    // name is the run's generated archive name (prefix + digits, no separators) joined to the resolved store root.
    const file = path.join(root, name); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    fs.unlinkSync(file);
    h.assert(!fs.existsSync(file), 'An archived synthetic lab upload was not removed');
  }
}

async function workflow(s) {
  const { sql, marker, patient, context, recorder } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const accession = `PL${stamp}`;
  const fileName = `lab-patient-link-${stamp}.hl7`;
  const labName = `${marker}-U`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-link-'));
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointment WHERE demographic_no=${patient} AND name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND name=${h.sqlString(marker)}`) === '0',
      'The owned next-appointment fixture was not removed');
    fs.rmSync(workDir, { recursive: true, force: true });
    removeOwnedHl7Labs(sql, sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`).map(row => row[0]));
    // A failed or partial upload can leave its archive (and a checksum row) with no hl7TextInfo row to find them
    // through; the run's unique upload name (LabUpload.<fileName>.<millis>) identifies both.
    const uploadName = `LabUpload.${fileName}.%`;
    // Archives first: their names are validated before any checksum evidence is deleted.
    removeNamedArchives(fileName);
    sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${h.sqlString(uploadName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '0', 'The synthetic lab was not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${h.sqlString(uploadName)}`) === '0', 'The run\'s lab upload checksum row was not removed');
  });
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  sql.execute(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
    demographic_no, status, creator, lastUpdateUser) VALUES (${h.sqlString(s.provider)}, '2099-04-17',
    '09:00:00', '09:15:00', ${h.sqlString(marker)}, ${patient}, 't', ${h.sqlString(s.provider)}, ${h.sqlString(s.provider)})`);
  const file = path.join(workDir, fileName);
  fs.writeFileSync(file, syntheticCmlLab(accession, labName));
  let inbox;
  let labNo;

  /** Inbox list (any provider, all patients, the given review status) ▸ this lab's link ▸ its labDisplay popup. */
  async function openLab(statusId, label) {
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await inbox.locator('#anyProvider').check();
    await inbox.locator(statusId).check();
    await inbox.locator('#allPatients').check();
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, 60000);
    // The matching popup's BroadcastChannel refresh and this search race: the list request the refresh started is
    // superseded and aborted by the browser. That abort is the page's own refresh, not a failure of the list.
    for (let i = recorder.requestFailures.length - 1; i >= 0; i--) {
      const failure = recorder.requestFailures[i];
      if (failure.label === 'patient-link-inbox' && /\/web\/inboxhub\/Inboxhub/.test(failure.url) && /ERR_ABORTED/.test(failure.errorText || '')) recorder.requestFailures.splice(i, 1);
    }
    const row = inbox.locator(`#inboxhubListModeTableBody tr[data-segment-id="${labNo}"]`);
    await row.waitFor({ timeout: TIMEOUT });
    const lab = await ui.clickOpensPopup(inbox, row.locator('a[onclick*="ViewLabDisplay"]'), { context, recorder, label, timeout: TIMEOUT });
    await lab.locator('input[value*="E-Chart"]').first().waitFor({ timeout: TIMEOUT });
    return lab;
  }

  await s.step('Inbox ▸ HL7 Lab Upload stores a lab for a name no patient holds, unmatched', async () => {
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
      { context, recorder, label: 'patient-link-inbox', timeout: 60000 }));
    await settle(inbox, 60000);
    const popup = await ui.clickOpensPopup(inbox, inbox.locator('a[href*="ViewInsideLabUpload"]').first(), { context, recorder, label: 'patient-link-upload', timeout: TIMEOUT });
    await popup.locator('#importFiles').setInputFiles(file);
    await popup.locator('#type').selectOption('CML');
    await Promise.all([
      popup.waitForResponse(r => r.request().method() === 'POST' && /insideLabUpload/.test(r.url()), { timeout: 60000 }),
      popup.locator('#uploadForm button[type="submit"]').click(),
    ]);
    const item = popup.locator('#file-list .file-item').filter({ hasText: fileName }).first();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert((await item.locator('.upload-text').innerText()).includes('Uploaded successfully'), 'The lab upload was not accepted');
    await popup.close();
    labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`);
    h.assert(/^[1-9]\d*$/.test(labNo), 'The uploaded lab has no hl7TextInfo row');
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${labNo} AND demographic_no>0`) === '0',
      'The lab for an unknown name was linked to a patient at upload');
    await inbox.reload();
    await settle(inbox, 60000);
  });

  let lab;
  let inlineLab;
  let matching;
  let openerUpdated = false;
  let demoTableColor = '';
  let chartGateAborted = false;
  await s.step('the lab\'s E-Chart button opens Patient Matching prefilled with the lab\'s own name and no candidate', async () => {
    lab = await openLab('#statusNew', 'patient-link-lab');
    demoTableColor = await lab.locator(`#DemoTable${labNo}`).evaluate(el => getComputedStyle(el).backgroundColor);
    h.assert(demoTableColor !== 'rgb(255, 255, 255)', 'The unmatched lab\'s patient box is not highlighted, so the refresh after matching cannot be observed');
    // Load the same owned lab through the real inline queue host as well. Its client-side
    // fixture map selects this lab without changing any shared queue membership.
    inlineLab = await context.newPage();
    await h.gotoApp(inlineLab, s.config.baseUrl, '/documentManager/inboxManage?method=getDocumentsInQueues');
    await h.assertNotErrorPage(inlineLab, 'inline queue host');
    await inlineLab.evaluate(({ id, provider }) => {
      window.docType[id] = 'HL7';
      const panel = document.createElement('div');
      panel.id = 'ownedLabMatchPanel';
      document.getElementById('docs').appendChild(panel);
      window.showDocLab(panel.id, id, provider, provider, 'N', '', 0);
    }, { id: labNo, provider: s.provider });
    await inlineLab.locator(`#DemoTable${labNo}`).waitFor({ timeout: TIMEOUT });
    h.assert(await inlineLab.locator(`#DemoTable${labNo}`).evaluate(el => getComputedStyle(el).backgroundColor) !== 'rgb(255, 255, 255)',
      'The inline unmatched lab is not highlighted before matching');
    matching = await ui.clickOpensPopup(lab, lab.locator('input[value*="E-Chart"]').first(), { context, recorder, label: 'patient-link-match', timeout: TIMEOUT });
    await matching.locator('#keyword').waitFor({ timeout: TIMEOUT });
    h.assert((await matching.locator('#keyword').inputValue()).includes(labName), 'The matching popup is not prefilled with the lab\'s patient name');
    h.assert(/No data available in table/i.test(await matching.locator('body').innerText()), 'The matching popup lists a candidate for a name no patient holds');
  });

  await s.step('searching the owned patient lists exactly that patient and picking the row links the lab', async () => {
    await matching.locator('#keyword').fill(marker);
    await matching.locator('button[name="displaymode"], button:has-text("Search")').first().click();
    const rows = matching.locator('#patientsTable tbody tr').filter({ hasText: marker });
    await rows.first().waitFor({ timeout: TIMEOUT });
    h.assert(await rows.count() === 1 && (await rows.first().innerText()).includes(patient), 'The search does not list exactly the owned patient');
    const [post] = await Promise.all([
      matching.waitForResponse(r => r.request().method() === 'POST' && /\/oscarMDS\/PatientMatch/.test(r.url()), { timeout: TIMEOUT }),
      rows.first().click(),
    ]);
    h.assert(post.status() === 200, `Patient Match answered HTTP ${post.status()}`);
    await expectValue(sql, `SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}`, patient,
      'Picking the patient did not link the lab to the owned patient');
    for (const view of [lab, inlineLab]) {
      await view.locator(`#labNextAppointment${labNo}`).filter({ hasText: '2099-04-17' }).waitFor({ timeout: TIMEOUT });
    }
    // Read now, before the named lab window is reused below, and asserted in the last step.
    await lab.waitForTimeout(2000);
    // A fetch match must complete without following a chart redirect that closing the popup aborts.
    for (let i = recorder.requestFailures.length - 1; i >= 0; i--) {
      const failure = recorder.requestFailures[i];
      if (failure.label === 'patient-link-match' && /\/oscarMDS\/ViewOpenEChart/.test(failure.url) && /ERR_ABORTED/.test(failure.errorText || '')) {
        recorder.requestFailures.splice(i, 1);
        chartGateAborted = true;
      }
    }
    // updateLabDemoStatus() (labDisplay.jsp:443) is what the matching popup calls on its opener: it whitens the patient box.
    openerUpdated = !lab.isClosed() && await lab.locator(`#DemoTable${labNo}`).evaluate(el => getComputedStyle(el).backgroundColor).catch(() => '') === 'rgb(255, 255, 255)';
    await inlineLab.waitForFunction(id => getComputedStyle(document.getElementById('DemoTable' + id)).backgroundColor === 'rgb(255, 255, 255)', labNo);
    await inlineLab.close();
    await lab.close();
  });

  await s.step('the reopened lab shows the patient and its E-Chart button opens that patient\'s chart', async () => {
    const fresh = await openLab('#statusAll', 'patient-link-lab-reopened');
    h.assert((await fresh.locator('body').innerText()).includes(marker), 'The reopened lab does not show the matched patient');
    const chartUrls = [];
    context.on('page', page => { page.on('framenavigated', frame => { if (frame === page.mainFrame()) chartUrls.push(page.url()); }); });
    const gate = await ui.clickOpensPopup(fresh, fresh.locator('input[value*="E-Chart"]').first(), { context, recorder, label: 'patient-link-chart', timeout: TIMEOUT });
    await gate.waitForLoadState('domcontentloaded').catch(() => {});
    const chartPattern = new RegExp(`CaseManagementEntry[^\\s]*demographicNo=${patient}(&|$)`);
    for (let waited = 0; waited < TIMEOUT && !chartUrls.concat(gate.isClosed() ? [] : [gate.url()]).some(url => chartPattern.test(url)); waited += 500) await gate.waitForTimeout(500).catch(() => {});
    h.assert(chartUrls.concat(gate.isClosed() ? [] : [gate.url()]).some(url => chartPattern.test(url)),
      `The lab's E-Chart button did not open the owned patient's chart (saw ${JSON.stringify(chartUrls.map(url => url.replace(/\?.*/, '')))})`);
    await gate.close().catch(() => {});
    await fresh.close();
  });

  await s.step('the lab display open during the match shows the patient without a reload', async () => {
    const problems = [];
    if (!openerUpdated) problems.push('the lab display that was open while the patient was chosen still shows the unmatched lab highlight (the matching popup is redirected and loses its opener, ISSUES L220)');
    if (chartGateAborted) problems.push('the match answers with a redirect to oscarMDS/ViewOpenEChart that the popup\'s fetch() follows, so the chart-gate page is requested and then aborted by window.close() (PatientMatch2Action.java:123 sendRedirect for a fetch caller)');
    h.assert(problems.length === 0, problems.join('; '));
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-lab-patient-link', workflow, { openPatient: true, openMaster: false });
