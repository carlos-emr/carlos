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

const isEncounterWrite = response => response.request().method() === 'POST'
  && /\/rx\/WriteToEncounter(?:\?|$)/.test(h.pathOnly(response.url()));

/** The drug lines the profile page renders (one ViewStaticScript2 link per drug). */
async function profileText(page) {
  return (await page.locator('a[href*="/rx/ViewStaticScript2"]').allTextContents()).join(' ').replace(/\s+/g, ' ');
}

/** Print the page the way window.print() would and return the PDF's text, after checking the bytes. */
function printedText(page, filePath) {
  return page.emulateMedia({ media: 'print' }).then(() => page.pdf({ path: filePath })).then(() => {
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
  s.cleanup(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^[1-9]\d*$/.test(id)), 'Owned note ID is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx fixtures were not removed');
  });
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
  const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
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
    const providerRows = prints.locator('tr').filter({ hasText: providerName });
    h.assert(await providerRows.count() === 2, `expected the original print and one reprint by the test provider, found ${await providerRows.count()} row(s)`);
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
  });

  await s.step('a GET against rx/WriteToEncounter is refused before any write', async () => {
    const before = noteCount();
    const rejected = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/rx/WriteToEncounter?demographicNo=${patient}&expectedDemographicNo=${patient}`), { maxRedirects: 0 });
    h.assert(rejected.status() === 405 && rejected.headers()['x-carlos-encounter-write'] === 'not-written',
      `A GET encounter write answered HTTP ${rejected.status()} instead of 405 not-written`);
    h.assert(noteCount() === before, 'A rejected GET changed the chart');
  });
}

if (require.main === module) runWorkflow('rx-print-profile', workflow, { openPatient: true });
module.exports = { workflow };
