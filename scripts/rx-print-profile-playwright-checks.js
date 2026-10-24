#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-print-profile — coverage plan §3.2 (`rx-print-profile` and `rx-write-to-encounter`).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions (the Rx module popup for the
 * owned patient) ▸ Drug Profile "Print" (rx/ViewPrintDrugProfile2, Show All / Show Current) ▸
 * Reprint panel ▸ "N Print(s)" (rx/ViewShowPreviousPrints) ▸ ReRx ▸ Stage medication ▸
 * Save And Print ▸ "Print & Paste" (rx/WriteToEncounter).
 *
 * Asserted: the drug profile lists the owned ACTIVE drug and hides the ARCHIVED one until
 * "Show All"; the page's own print rendering (Chromium print media, which is what window.print()
 * hands the printer — the application produces no server-side PDF) is a real PDF whose text
 * carries the same drugs; the print history popup lists the seeded original print and reprint
 * for the owned script; "Print & Paste" posts the owned prescription text to rx/WriteToEncounter,
 * which acknowledges the write and leaves one casemgmt_note carrying it; a GET against the same
 * route is refused before any write.
 *
 * Fixtures: one owned `prescription` row and two `drugs` rows (customName/special carry the run
 * marker) for the owned synthetic patient; the Save And Print adds a second prescription and drug.
 * Cleanup deletes the owned patient's drugs, prescription and casemgmt_note rows and asserts.
 * Requires pdftotext (poppler-utils) and a headless browser (page.pdf()).
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const isEncounterWrite = response => response.request().method() === 'POST'
  && /\/rx\/WriteToEncounter(?:\?|$)/.test(h.pathOnly(response.url()));

/** The drug lines the profile page renders (one ViewStaticScript2 link per drug). */
async function profileText(page) {
  return (await page.locator('a[href*="/rx/ViewStaticScript2"]').allTextContents()).join(' ').replace(/\s+/g, ' ');
}

/** Print the page the way window.print() would and return the PDF's text, after checking the bytes. */
function printedText(page, filePath) {
  // Print media hides the page's .noPrint controls (Show All / Show Current); restore screen media after.
  return page.emulateMedia({ media: 'print' }).then(() => page.pdf({ path: filePath }))
    .then(() => page.emulateMedia({ media: null })).then(() => {
    const bytes = fs.readFileSync(filePath);
    h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The printed drug profile is not a PDF');
    h.assert(bytes.length > 1000, `The printed drug profile is unexpectedly small (${bytes.length} bytes)`);
    h.assert(bytes.subarray(-128).toString('latin1').includes('%%EOF'), 'The printed drug profile has no %%EOF trailer');
    return execFileSync('pdftotext', ['-raw', filePath, '-'],
      { encoding: 'utf8', timeout: 15000, stdio: ['ignore', 'pipe', 'pipe'] }).replace(/\s+/g, ' ');
  });
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const activeText = `${marker} ACTIVE one tablet daily`;
  const archivedText = `${marker} ARCHIVED stopped`;
  const reprintStamp = `${sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 1 DAY)')} 09:15`;
  const providerName = sql.value(`SELECT CONCAT(last_name, ', ', first_name) FROM provider WHERE provider_no=${h.sqlString(provider)}`);
  h.assert(providerName, 'The test provider has no name to look for in the print history');
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-rx-print-'));
  const noteCount = () => sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`);
  const cleanupRxRows = () => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^[1-9]\d*$/.test(id)), 'Owned note ID is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    // Save And Print stamps the script with a DigitalSignature row (FK to demographic); it must go
    // after the prescription that references it and before the owned patient.
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION';
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})`) === '0', 'Owned Rx fixtures were not removed');
  };
  s.cleanup(cleanupRxRows);
  // One printed and once reprinted script, with an active and an archived drug on it. The
  // `special` text is what the drug profile prints (Drug.getFullOutLine), so it carries the marker.
  const script = sql.value(`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,date_printed,dates_reprinted,textView,lastUpdateDate)
    VALUES(${h.sqlString(provider)},${patient},CURDATE(),CURDATE(),${h.sqlString(`${reprintStamp};${provider}`)},'Synthetic prescription',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(script), 'Owned prescription was not created');
  const seedDrug = (name, special, archived) => sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,special,archived,archived_reason,archived_date,
      script_no,position,dispenseInternal,create_date,lastUpdateDate)
    VALUES(${h.sqlString(provider)},${patient},CURDATE(),${archived ? 'DATE_SUB(CURDATE(),INTERVAL 1 DAY)' : 'DATE_ADD(CURDATE(),INTERVAL 30 DAY)'},CURDATE(),
      ${h.sqlString(name)},0,${h.sqlString(name)},1,1,'OD','30','D','30',0,${h.sqlString(special)},${archived ? 1 : 0},${archived ? "'discontinued'" : "''"},${archived ? 'NOW()' : 'NULL'},
      ${script},0,0,NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  const activeDrug = seedDrug(`${marker}-A`, activeText, false);
  const archivedDrug = seedDrug(`${marker}-B`, archivedText, true);
  h.assert(/^[1-9]\d*$/.test(activeDrug) && /^[1-9]\d*$/.test(archivedDrug), 'Owned drugs were not created');

  // Master Record ▸ Prescriptions opens the Rx module for this patient. Entered from the Master
  // Record rather than the E-Chart on purpose: with an encounter as opener, ViewScript2 pastes into
  // the open note editor and never calls rx/WriteToEncounter, which is the route under test.
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });

  await s.step('drug profile Print lists the active drug, hides the archived one until Show All, and prints as a PDF saying the same', async () => {
    const profile = await s.popup(rx, rx.locator('a[onclick*="printDrugProfile"]').first(), 'print-profile');
    h.assert(h.pathOnly(profile.url()).endsWith('/rx/ViewPrintDrugProfile2'), 'Print did not open the drug profile route');
    let text = await profileText(profile);
    h.assert(text.includes(activeText), 'The current drug profile omits the owned active drug');
    h.assert(!text.includes(archivedText), 'The current drug profile lists the archived drug');
    let printed = await printedText(profile, path.join(tmpDir, 'current.pdf'));
    h.assert(printed.includes(activeText) && !printed.includes(archivedText) && printed.includes(marker),
      'The printed current profile does not match the rendered page (active drug and patient present, archived absent)');
    await ui.clickAndAwaitReload(profile, profile.locator('a[href*="show=all"]').first(), { label: 'Show All' });
    h.assert(new URL(profile.url()).searchParams.get('demographicNo') === patient, 'Show All reloaded the profile for another patient');
    text = await profileText(profile);
    h.assert(text.includes(activeText) && text.includes(archivedText), 'Show All does not list both the active and the archived drug');
    printed = await printedText(profile, path.join(tmpDir, 'all.pdf'));
    h.assert(printed.includes(activeText) && printed.includes(archivedText), 'The printed Show All profile lost a drug');
    await ui.clickAndAwaitReload(profile, profile.locator('a[href*="/rx/ViewPrintDrugProfile2"]:not([href*="show=all"])').first(), { label: 'Show Current' });
    text = await profileText(profile);
    h.assert(text.includes(activeText) && !text.includes(archivedText), 'Show Current did not return to the unarchived view');
    await profile.close();
  });

  await s.step('Reprint panel ▸ Print(s) lists the original print and the reprint of the owned script', async () => {
    await rx.locator('a[onclick*="getElementById(\'reprint\')"]').first().click();
    await rx.locator('#reprint').waitFor({ state: 'visible' });
    const prints = await s.popup(rx, rx.locator(`#reprint a[onclick*="showPreviousPrints(${script})"]`).first(), 'previous-prints');
    h.assert(new URL(prints.url()).searchParams.get('scriptNo') === script, 'The print history opened for another script');
    const body = (await prints.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(/Prescription Print History/.test(body), 'The print history page did not render its heading');
    h.assert(body.includes(reprintStamp), 'The print history omits the seeded reprint date');
    // Exact text: the page nests tables, so any ancestor row also "has" the name.
    const providerCells = prints.getByText(providerName, { exact: true });
    h.assert(await providerCells.count() === 2, `expected the original print and one reprint by the test provider, found ${await providerCells.count()} row(s)`);
    await prints.close();
  });

  await s.step('ReRx ▸ Save And Print ▸ Print & Paste writes the owned prescription text to the chart through rx/WriteToEncounter', async () => {
    await rx.locator(`#reRxCheckBox_${activeDrug}`).check();
    const stage = rx.locator('#reRxConfirmBox input[name="stage"]');
    await stage.waitFor({ state: 'visible' });
    await stage.click();
    await rx.locator('[id^="quantity_"]').first().waitFor({ state: 'attached' });
    await rx.locator('#saveButton').click();
    const modal = rx.frameLocator('#carlosModalBody iframe');
    await modal.locator('#printPasteButton').waitFor({ state: 'visible', timeout: 30000 });
    await modal.frameLocator('#preview').locator('#preview2Form').waitFor({ state: 'attached', timeout: 30000 });
    h.assert(await modal.locator('#printPasteButton').isEnabled(), 'Print & Paste is disabled on a freshly saved prescription');
    await expectValue(sql, `SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}`, '2', 'Save And Print did not persist a second prescription');
    h.assert(noteCount() === '0', 'A chart note existed before Print & Paste');
    // A successful paste opens the encounter in a new window; take it so it cannot leak.
    const failuresBefore = s.recorder.requestFailures.length;
    const encounter = s.context.waitForEvent('page', { timeout: 20000 }).catch(() => null);
    const [write] = await Promise.all([
      rx.waitForResponse(isEncounterWrite, { timeout: 30000 }),
      modal.locator('#printPasteButton').click(),
    ]);
    h.assert(write.status() === 200 && write.headers()['x-carlos-encounter-write'] === 'written',
      `The encounter write was not acknowledged (HTTP ${write.status()}, outcome ${write.headers()['x-carlos-encounter-write'] || 'absent'})`);
    const posted = new URLSearchParams(write.request().postData() || '');
    h.assert(posted.get('expectedDemographicNo') === patient && posted.get('demographicNo') === patient, 'The paste was bound to another patient');
    const sent = posted.get('body') || '';
    h.assert(sent.includes(marker) && sent.includes('one tablet daily'), 'The pasted text does not carry the owned prescription');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} AND note LIKE ${h.sqlString(`%${marker}%`)}`, '1', 'The chart note carrying the prescription was not written');
    const note = sql.value(`SELECT note FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${h.sqlString(`%${marker}%`)} ORDER BY note_id DESC LIMIT 1`);
    h.assert(note.includes('one tablet daily') && /\.:Rx\]/.test(note), 'The chart note lacks the Rx header or the drug instructions');
    h.assert(noteCount() === '1', 'Print & Paste wrote more than one chart note');
    const opened = await encounter;
    if (opened) {
      await opened.waitForLoadState('domcontentloaded', { timeout: 20000 }).catch(() => {});
      await opened.close().catch(() => {});
    }
    // ViewScript2 reads only the headers of the acknowledged write and never consumes its body;
    // printIframe() then closes the Rx window, so Chromium reports that one fetch as aborted
    // (net::ERR_ABORTED) AFTER the write was acknowledged and asserted above. Consume exactly that
    // artefact; any other request failure stays for the strict assertion.
    const aborted = s.recorder.requestFailures.slice(failuresBefore);
    h.assert(aborted.length <= 1 && aborted.every(entry => entry.label === 'rx-module' && entry.errorText === 'net::ERR_ABORTED'
      && entry.resourceType === 'fetch' && h.pathOnly(entry.url).endsWith('/rx/WriteToEncounter')),
    'A request other than the acknowledged encounter write failed during Print & Paste');
    s.recorder.requestFailures.splice(failuresBefore, aborted.length);
  });

  await s.step('a GET against rx/WriteToEncounter is refused before any write', async () => {
    const before = noteCount();
    const rejected = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/rx/WriteToEncounter?demographicNo=${patient}&expectedDemographicNo=${patient}`), { maxRedirects: 0 });
    h.assert(rejected.status() === 405 && rejected.headers()['x-carlos-encounter-write'] === 'not-written',
      `A GET encounter write answered HTTP ${rejected.status()} instead of 405 not-written`);
    h.assert(noteCount() === before, 'A rejected GET changed the chart');
  });

  await s.step('Print & Paste keeps the originating chart program after the same patient opens in another program', async () => {
    const q = h.sqlString;
    const login = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
    s.cleanup(() => login.cleanup());
    login.create();
    const owner = q(login.providerNo);
    const programs = [];
    s.cleanup(() => {
      sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${owner}`);
      for (const program of programs) {
        sql.execute(`DELETE FROM admission WHERE client_id=${patient} AND program_id=${program};
          DELETE FROM program_provider WHERE provider_no=${owner} AND program_id=${program};
          DELETE FROM program WHERE id=${program} AND name LIKE ${q(`${marker}-rx-program-%`)}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE id=${program})
          + (SELECT COUNT(*) FROM admission WHERE program_id=${program})
          + (SELECT COUNT(*) FROM program_provider WHERE program_id=${program})`) === '0',
        'The owned Rx program or its relationships were not removed');
      }
      h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${owner}`) === '0',
        'The throwaway provider default was not removed');
    });
    // Remove the clinical rows before their owned provider/program parents during reverse cleanup.
    s.cleanup(cleanupRxRows);
    const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)}
      AND role_id IS NOT NULL ORDER BY id LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(role), 'The test provider has no program role to copy');
    for (const label of ['captured', 'default']) {
      const program = h.insertId(sql, `INSERT INTO program
        (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,
         physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax,
         enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
        SELECT id,${q(`${marker}-rx-program-${label}`)},'service',1,'active',0,0,0,0,0,0,'none',0,150,0,0,0
        FROM Facility ORDER BY id LIMIT 1`, 'Rx program');
      h.assert(/^[1-9]\d*$/.test(program), 'An owned Rx program was not created');
      programs.push(program);
      sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
        VALUES (${program},${owner},${role},0);
        INSERT INTO admission (client_id,program_id,provider_no,admission_date,
          admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
        VALUES (${patient},${program},${owner},NOW(),0,0,'current',NOW())`);
    }
    const [capturedProgram, defaultProgram] = programs;
    sql.execute(`INSERT INTO provider_default_program (provider_no,program_id,signnote)
      VALUES (${owner},${defaultProgram},0)`);
    h.assert(capturedProgram !== defaultProgram, 'The program isolation fixture does not distinguish the provider default');
    const context = await h.newContext(s.context.browser(), s.config);
    context.setDefaultTimeout(30000);
    context.on('page', page => h.wireStrictPage(page, 'rx-program-isolation', s.recorder));
    await h.login(context, { ...s.config, testUser: login.username }, s.recorder);
    const charts = [];
    const tokens = [];
    for (const program of programs) {
      const chart = await context.newPage();
      await h.gotoApp(chart, s.config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${patient}
        &providerNo=${login.providerNo}&programId=${program}`.replace(/\s+/g, ''));
      await waitForNavbars(chart, 30000);
      const token = await chart.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
      h.assert(token, 'A chart window has no bound program context');
      charts.push(chart);
      tokens.push(token);
    }
    h.assert(tokens[0] !== tokens[1], 'The two program windows share one chart context');
    const launchedRx = await ui.clickOpensPopup(charts[0], charts[0].locator('a[onclick*="/rx/choosePatient"]').first(),
      { context, recorder: s.recorder, label: 'rx-program-isolation', timeout: 30000 });
    const rxUrl = new URL(launchedRx.url());
    h.assert(rxUrl.searchParams.get('chartContext') === tokens[0], 'Rx launch lost its originating chart context');
    // Reopen the browser-observed destination in a script-opened, no-opener window. Like the
    // Master Record entry above, it exercises the real server append instead of an editor shortcut.
    const openedRx = context.waitForEvent('page', { timeout: 30000 });
    await charts[0].evaluate(target => { window.open(target, '_blank', 'noopener'); }, rxUrl.href);
    const isolatedRx = await openedRx;
    await isolatedRx.locator('#searchString').waitFor({ state: 'visible' });
    h.assert(await isolatedRx.locator('script[src*="rx-patient-context.js"]').getAttribute('data-chart-context') === tokens[0],
      'The prescribing page lost the originating chart context');
    const capturedDraft = `${marker}-captured-program-draft`;
    const otherDraft = `${marker}-default-program-draft-unchanged`;
    for (const [program, text] of [[capturedProgram, capturedDraft], [defaultProgram, otherDraft]]) {
      sql.execute(`INSERT INTO casemgmt_tmpsave (demographic_no,provider_no,program_id,note,update_date,note_id)
        VALUES (${patient},${owner},${program},${q(text)},NOW(),0)`);
    }
    const otherBefore = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_tmpsave
      WHERE demographic_no=${patient} AND provider_no=${owner} AND program_id=${defaultProgram}`));
    // The earlier ReRx archived its original source; stage the replacement that is now active.
    const currentDrug = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}
      AND archived=0 AND LOCATE(${q(marker)},special)>0 ORDER BY drugid DESC LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(currentDrug), 'The program isolation workflow has no owned active medication');
    await isolatedRx.locator(`#reRxCheckBox_${currentDrug}`).check();
    await isolatedRx.locator('#reRxConfirmBox input[name="stage"]').click();
    await isolatedRx.locator('[id^="quantity_"]').first().waitFor({ state: 'attached' });
    await isolatedRx.locator('#saveButton').click();
    const modal = isolatedRx.frameLocator('#carlosModalBody iframe');
    await modal.locator('#printPasteButton').waitFor({ state: 'visible' });
    await modal.frameLocator('#preview').locator('#preview2Form').waitFor({ state: 'attached' });
    const failuresBefore = s.recorder.requestFailures.length;
    const encounter = context.waitForEvent('page', { timeout: 20000 }).catch(() => null);
    const [write] = await Promise.all([
      isolatedRx.waitForResponse(isEncounterWrite),
      modal.locator('#printPasteButton').click(),
    ]);
    const posted = new URLSearchParams(write.request().postData() || '');
    h.assert(write.status() === 200 && write.headers()['x-carlos-encounter-write'] === 'written'
      && posted.get('chartContext') === tokens[0], 'Print & Paste lost or changed its chart context');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${owner} AND program_no=${q(capturedProgram)} AND LOCATE(${q(capturedDraft)},note)>0
      AND LOCATE(${q(marker)},note)>0 AND LOCATE('one tablet daily',note)>0`, '1',
    'Rx did not save the captured program draft and prescription in the originating program');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${owner} AND program_id=${capturedProgram}`) === '0', 'The consumed captured-program draft remains');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${owner} AND program_id=${defaultProgram}`)) === otherBefore,
    'Rx changed or consumed the provider-default program draft');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${owner} AND program_no=${q(defaultProgram)}`) === '0', 'Rx created a note in the other program');
    const opened = await encounter;
    h.assert(opened, 'Acknowledged Print & Paste did not open the originating chart');
    await opened.waitForURL(url => url.searchParams.get('chartContext') === tokens[0],
      { waitUntil: 'domcontentloaded', timeout: 20000 });
    await opened.waitForLoadState('domcontentloaded', { timeout: 20000 });
    await waitForNavbars(opened, 30000);
    const reopenedToken = new URL(opened.url()).searchParams.get('chartContext');
    h.assert(reopenedToken === tokens[0]
      && await opened.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue() === tokens[0],
    'Print & Paste reopened the chart under another program context');
    const aborted = s.recorder.requestFailures.slice(failuresBefore);
    h.assert(aborted.length <= 1 && aborted.every(entry => entry.label === 'rx-program-isolation'
      && entry.errorText === 'net::ERR_ABORTED' && entry.resourceType === 'fetch'
      && h.pathOnly(entry.url).endsWith('/rx/WriteToEncounter')),
    'A request other than the acknowledged encounter write failed during program-isolated Print & Paste');
    s.recorder.requestFailures.splice(failuresBefore, aborted.length);
  });
}

if (require.main === module) runWorkflow('rx-print-profile', workflow, { openPatient: true });
module.exports = { workflow };
