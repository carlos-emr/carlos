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
 * route is refused before any write. Two program windows for the same patient retain their own
 * contexts: the first paste consumes only its program draft, and a later paste with no draft
 * creates a note in its own program without modifying the first note or a protected candidate.
 * Fifteen refusal controls
 * replay the real pending paste with invalid patient/login/context identities or a missing or
 * cross-patient draft-note reference, a noneditable note, or a note newer than the draft;
 * exact clinical snapshots must remain unchanged. Finally,
 * explicitly editing the first program's shared note from the second program autosaves and saves
 * through the native controls: the note keeps its program, only the second program draft clears,
 * and the first window retains both its persisted draft and unsaved editor text.
 * Replaying Rx from that older revision refuses without consuming the protected draft.
 * With the owned first-program membership temporarily absent, a further native Save from
 * the second program retains the note's original program/team and the writer's valid role;
 * a chart reload and native Edit prove the revision remains visible and readable there.
 *
 * Fixtures: one owned `prescription` row and two `drugs` rows (customName/special carry the run
 * marker) for the owned synthetic patient; native Save And Print operations add owned rows.
 * A throwaway login holds two owned programs and patient admissions; a second owned patient and
 * unsigned note prove draft references cannot cross patients. Cleanup verifies removal of both
 * patients' clinical fixtures, owned program memberships/defaults/admissions and login rows.
 * Requires pdftotext (poppler-utils) and a headless browser (page.pdf()).
 */
const { execFileSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { sessionToken } = require('./lib/mutation-replay');
const { waitUntil } = require('./lib/concurrency-support');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const isEncounterWrite = response => response.request().method() === 'POST'
  && /\/rx\/WriteToEncounter(?:\?|$)/.test(h.pathOnly(response.url()));

// PrintPaste closes its Rx page after accepting the write headers. Wait for that
// exact response to settle before the recorder checks for its known close abort.
async function finishAcknowledgedWrite(response, page) {
  h.assert(isEncounterWrite(response) && response.status() === 200
    && response.headers()['x-carlos-encounter-write'] === 'written',
  'Cannot settle an unacknowledged encounter write');
  try {
    await response.finished();
  } catch (error) {
    if (!page.isClosed() || response.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
  }
}

function hasChartContext(request, expected) {
  const contexts = [...new URL(request.url()).searchParams.getAll('chartContext'),
    ...new URLSearchParams(request.postData() || '').getAll('chartContext')];
  return contexts.length > 0 && contexts.every(context => context === expected);
}

/** Replay the real pending paste with one invalid identity; every clinical row must survive. */
async function verifyEncounterWriteRefusals(s, context, request, tokens, reference) {
  const original = new URLSearchParams(request.postData() || '');
  h.assert(original.get('demographicNo') === s.patient
    && original.get('expectedDemographicNo') === s.patient
    && original.get('chartContext') === tokens[0] && original.get('body').includes(s.marker),
  'The refusal probes did not capture the owned prescription paste');
  const snapshot = () => JSON.stringify([
    s.sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no IN (${s.patient},${reference.patient}) ORDER BY note_id`),
    s.sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${s.patient} ORDER BY id`),
    s.sql.rows(`SELECT * FROM prescription WHERE demographic_no=${s.patient} ORDER BY script_no`),
    s.sql.rows(`SELECT * FROM drugs WHERE demographic_no=${s.patient} ORDER BY drugid`),
  ]);
  const parts = tokens[0].split('.');
  const forged = `${parts[0]}.${parts[1][0] === 'A' ? 'B' : 'A'}${parts[1].slice(1)}`;
  const otherLoginCsrf = await sessionToken(s.master, s.config.baseUrl);
  const capturedHeaders = request.headers();
  const baseHeaders = { 'content-type': 'application/x-www-form-urlencoded',
    origin: new URL(s.config.baseUrl).origin };
  for (const name of ['referer', 'x-requested-with', 'csrf-token']) {
    if (capturedHeaders[name]) baseHeaders[name] = capturedHeaders[name];
  }
  const cases = [
    ['forged chart context', body => body.set('chartContext', forged), 403],
    ['another patient with this chart context', body => body.set('demographicNo', reference.patient), 403],
    ['conflicting chart contexts', body => body.append('chartContext', tokens[1]), 403],
    ['missing originating patient', body => body.delete('expectedDemographicNo'), 409],
    ['changed originating patient', body => body.set('expectedDemographicNo', reference.patient), 409],
    ['unnamed patient', body => body.delete('demographicNo'), 409],
    ['chart context from another login', () => {}, 403, s.context, otherLoginCsrf],
  ];
  for (const [label, change, status, target = context, csrf] of cases) {
    const before = snapshot();
    const body = new URLSearchParams(original);
    change(body);
    const headers = { ...baseHeaders };
    if (csrf) {
      headers['csrf-token'] = csrf;
      if (body.has('CSRF-TOKEN')) body.set('CSRF-TOKEN', csrf);
    }
    const response = await target.request.post(request.url(), {
      data: body.toString(), headers, maxRedirects: 0, failOnStatusCode: false,
    });
    h.assert(response.status() === status, `${label}: expected HTTP ${status}, got ${response.status()}`);
    if (status === 403) {
      // Production error pages intentionally omit sendError's internal message.
      // The same CSRF carrier must also reach the action's explicit 409 controls below.
      const refusalBody = await response.text();
      h.assert(Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
        && !h.isWafPage(response.status(), refusalBody),
      `${label}: the refusal did not come from the application`);
    } else {
      h.assert(response.headers()['x-carlos-encounter-write'] === 'not-written',
        `${label}: the action did not acknowledge that it refused before writing`);
    }
    h.assert(snapshot() === before, `${label}: a refused paste changed a note, draft or prescription`);
    await response.dispose();
  }
  const missing = s.sql.value('SELECT COALESCE(MAX(note_id),0)+1 FROM casemgmt_note');
  h.assert(/^[1-9]\d*$/.test(missing)
    && s.sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${missing}`) === '0',
  'The missing-note control unexpectedly identifies an existing note');
  for (const [label, id] of [['another patient draft reference', reference.note], ['missing draft reference', missing]]) {
    // Simulate a pre-existing bad reference rather than weakening the producer's autosave guard.
    // Leave all other draft fields intact, and restore the fixture after proving no mutation.
    s.sql.execute(`UPDATE casemgmt_tmpsave SET note_id=${id} WHERE id=${reference.draft}`);
    const before = snapshot();
    try {
      const response = await context.request.post(request.url(), {
        data: original.toString(), headers: baseHeaders, maxRedirects: 0, failOnStatusCode: false,
      });
      h.assert(response.status() === 409 && response.headers()['x-carlos-encounter-write'] === 'not-written',
        `${label}: the action did not refuse before writing or consuming the draft`);
      h.assert(snapshot() === before, `${label}: a refused paste changed either patient's note or the original draft`);
      await response.dispose();
    } finally {
      s.sql.execute(`UPDATE casemgmt_tmpsave SET note_id=0 WHERE id=${reference.draft}`);
    }
  }
  const q = h.sqlString;
  const baseline = s.sql.rows(`SELECT provider_no,signed,locked,archived,update_date FROM casemgmt_note
    WHERE note_id=${reference.guarded}`)[0];
  h.assert(baseline && baseline.length === 5, 'The owned direct-append guard fixture is missing');
  const restoreGuard = () => s.sql.execute(`UPDATE casemgmt_note SET provider_no=${q(baseline[0])},
    signed=${Number(baseline[1])},locked=${Number(baseline[2])},archived=${Number(baseline[3])},
    update_date=${q(baseline[4])} WHERE note_id=${reference.guarded}`);
  for (const [label, change] of [
    ['signed note', 'signed=1'], ['locked note', 'locked=1'], ['archived note', 'archived=1'],
    ['another author note', `provider_no=${q(s.provider)}`],
    ['note saved after its draft', 'update_date=DATE_ADD(NOW(),INTERVAL 1 MINUTE)'],
  ]) {
    s.sql.execute(`UPDATE casemgmt_note SET ${change} WHERE note_id=${reference.guarded};
      UPDATE casemgmt_tmpsave SET note_id=${reference.guarded} WHERE id=${reference.draft}`);
    const before = snapshot();
    try {
      const response = await context.request.post(request.url(), {
        data: original.toString(), headers: baseHeaders, maxRedirects: 0, failOnStatusCode: false,
      });
      h.assert(response.status() === 409 && response.headers()['x-carlos-encounter-write'] === 'not-written',
        `${label}: the direct append was not refused before writing`);
      h.assert(snapshot() === before, `${label}: the refusal changed a clinical note or consumed either program draft`);
      await response.dispose();
    } finally {
      restoreGuard();
      s.sql.execute(`UPDATE casemgmt_tmpsave SET note_id=0 WHERE id=${reference.draft}`);
    }
  }
  // The unchanged browser paste is the positive control for a real, owned, unsigned
  // reference whose persisted text predates the draft, rather than a new-note shortcut.
  s.sql.execute(`UPDATE casemgmt_tmpsave SET note_id=${reference.guarded} WHERE id=${reference.draft}`);
}

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

  await s.step('the Rx allergy patient-name link opens the owned Master Record through its native href', async () => {
    await Promise.all([rx.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      rx.locator('a[name="cmdAllergies"]').first().click()]);
    const link = rx.locator('#topLink #topLinkCenterColumn a[href*="/demographic/DemographicEdit"]').first();
    const expected = new URL(h.appUrl(s.config.baseUrl, '/demographic/DemographicEdit'));
    const href = new URL(await link.getAttribute('href'), rx.url());
    h.assert(href.origin === expected.origin && href.pathname === expected.pathname,
      'The Rx patient-name href has an incorrect application context or Master Record path');
    h.assert(JSON.stringify(href.searchParams.getAll('demographic_no')) === JSON.stringify([patient]),
      'The Rx patient-name href does not identify the owned patient exactly once');
    const record = await s.popup(rx, link, 'rx-patient-master-record');
    try {
      const destination = new URL(record.url());
      h.assert(destination.origin === expected.origin && destination.pathname === expected.pathname
        && JSON.stringify(destination.searchParams.getAll('demographic_no')) === JSON.stringify([patient]),
      'The Rx patient-name popup opened an incorrect application path or patient');
      h.assert(await record.locator('[name="last_name"]').first().inputValue() === marker,
        'The Rx patient-name popup did not render the owned Master Record');
    } finally { await record.close(); }
    await rx.goBack({ waitUntil: 'domcontentloaded' });
    await rx.locator('#searchString').waitFor({ state: 'visible' });
  });

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
    await finishAcknowledgedWrite(write, rx);
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
    const referencePatient = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${q(marker)},'RxReference','1975','03','04','M','AC',${owner},'ON','ON','NR',NOW())`,
    'Rx draft reference patient');
    s.cleanup(() => {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${referencePatient}
        AND last_name=${q(marker)}`) === '1', 'Rx reference patient ownership changed');
      sql.execute(`DELETE FROM casemgmt_note WHERE demographic_no=${referencePatient};
        DELETE FROM demographic WHERE demographic_no=${referencePatient} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${referencePatient})
        + (SELECT COUNT(*) FROM demographic WHERE demographic_no=${referencePatient})`) === '0',
      'Rx draft reference fixtures were not removed');
    });
    const referenceNoteFor = (targetPatient, program, label, { locked = false, newer = false } = {}) => {
      const date = newer ? 'DATE_ADD(NOW(),INTERVAL 1 MINUTE)' : 'DATE_SUB(NOW(),INTERVAL 1 DAY)';
      return h.insertId(sql, `INSERT INTO casemgmt_note
        (update_date,observation_date,demographic_no,provider_no,note,signed,include_issue_innote,
         signing_provider_no,encounter_type,program_no,reporter_caisi_role,reporter_program_team,
         history,locked,archived,position,uuid)
        VALUES (${date},${date},${targetPatient},${owner},${q(`${marker}-${label}-unchanged`)},
          0,0,'','',${program},${q(role)},'0',${q(`${marker}-${label}-history-unchanged`)},
          ${locked ? 1 : 0},0,0,UUID())`, 'Rx guarded note');
    };
    const referenceNote = referenceNoteFor(referencePatient, capturedProgram, 'foreign-note', { newer: true });
    const guardedNote = referenceNoteFor(patient, capturedProgram, 'direct-append-guard');
    const guardedOriginal = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${guardedNote}`));
    const capturedDraftId = sql.value(`SELECT id FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${owner} AND program_id=${capturedProgram} AND note_id=0`);
    h.assert(/^[1-9]\d*$/.test(capturedDraftId), 'The original program draft fixture was not found');
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
    let encounter;
    // Hold the actual browser request while replaying its exact payload with invalid identities.
    // The unchanged request then continues as the positive control, using the same CSRF carrier.
    let held;
    const intercept = route => {
      const request = route.request();
      if (request.method() !== 'POST' || request.frame().page() !== isolatedRx) return route.fallback();
      h.assert(!held, 'The isolated Rx window submitted more than one encounter write');
      held = route;
    };
    const expectedWriteUrl = new URL(h.appUrl(s.config.baseUrl, '/rx/WriteToEncounter'));
    const writePath = url => url.origin === expectedWriteUrl.origin && url.pathname === expectedWriteUrl.pathname;
    // The Rx page closes after acknowledging the paste. Keep interception on its live
    // browser context so it can be removed after continuing the captured request.
    // Removing the last interceptor while a request is held auto-continues that request.
    await context.route(writePath, intercept);
    let write;
    try {
      await Promise.all([
        waitUntil(() => held, 'the actual program-bound prescription paste', 30000),
        modal.locator('#printPasteButton').click(),
      ]);
      await s.step('invalid Rx paste identities refuse without changing either program draft or saved prescriptions',
        () => verifyEncounterWriteRefusals(s, context, held.request(), tokens,
          { patient: referencePatient, note: referenceNote, guarded: guardedNote, draft: capturedDraftId }));
      const response = isolatedRx.waitForResponse(isEncounterWrite);
      encounter = context.waitForEvent('page', { timeout: 20000 }).catch(() => null);
      await held.continue();
      write = await response;
    } catch (error) {
      if (held) await held.abort().catch(() => {});
      throw error;
    } finally {
      await context.unroute(writePath, intercept);
    }
    const posted = new URLSearchParams(write.request().postData() || '');
    h.assert(write.status() === 200 && write.headers()['x-carlos-encounter-write'] === 'written'
      && posted.get('chartContext') === tokens[0], 'Print & Paste lost or changed its chart context');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${owner} AND program_no=${q(capturedProgram)} AND LOCATE(${q(capturedDraft)},note)>0
      AND LOCATE(${q(marker)},note)>0 AND LOCATE('one tablet daily',note)>0`, '1',
    'Rx did not save the captured program draft and prescription in the originating program');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${guardedNote}`)) === guardedOriginal,
      'The Rx append changed its historical source row instead of creating a new note revision');
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
    await finishAcknowledgedWrite(write, isolatedRx);
    const aborted = s.recorder.requestFailures.slice(failuresBefore);
    h.assert(aborted.length <= 1 && aborted.every(entry => entry.label === 'rx-program-isolation'
      && entry.errorText === 'net::ERR_ABORTED' && entry.resourceType === 'fetch'
      && h.pathOnly(entry.url).endsWith('/rx/WriteToEncounter')),
    'A request other than the acknowledged encounter write failed during program-isolated Print & Paste');
    s.recorder.requestFailures.splice(failuresBefore, aborted.length);

    let defaultChartAfterPaste;
    await s.step('Print & Paste without a draft creates its own program note and preserves a protected automatic candidate', async () => {
      const capturedBefore = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note
        WHERE demographic_no=${patient} AND provider_no=${owner} AND program_no=${q(capturedProgram)} ORDER BY note_id`));
      h.assert(capturedBefore !== '[]', 'The cross-program preservation check has no existing note');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_no=${q(defaultProgram)}`) === '0',
      'The no-draft destination program already has a saved note');
      sql.execute(`DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_id=${defaultProgram}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${owner}`) === '0', 'The no-draft paste still has a provider draft');
      const protectedNote = referenceNoteFor(patient, defaultProgram, 'protected-default-candidate', { locked: true });
      const protectedBefore = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${protectedNote}`));
      const launch = await ui.clickOpensPopup(charts[1], charts[1].locator('a[onclick*="/rx/choosePatient"]').first(),
        { context, recorder: s.recorder, label: 'rx-program-isolation', timeout: 30000 });
      const target = new URL(launch.url());
      h.assert(target.searchParams.get('chartContext') === tokens[1], 'The second-program Rx launch lost its chart context');
      const popup = context.waitForEvent('page', { timeout: 30000 });
      await charts[1].evaluate(url => { window.open(url, '_blank', 'noopener'); }, target.href);
      const nextRx = await popup;
      await nextRx.locator('#searchString').waitFor({ state: 'visible' });
      const nextDrug = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}
        AND archived=0 AND LOCATE(${q(marker)},special)>0 ORDER BY drugid DESC LIMIT 1`);
      h.assert(/^[1-9]\d*$/.test(nextDrug), 'The no-draft paste has no active owned medication');
      await nextRx.locator(`#reRxCheckBox_${nextDrug}`).check();
      await nextRx.locator('#reRxConfirmBox input[name="stage"]').click();
      await nextRx.locator('[id^="quantity_"]').first().waitFor({ state: 'attached' });
      await nextRx.locator('#saveButton').click();
      const preview = nextRx.frameLocator('#carlosModalBody iframe');
      await preview.locator('#printPasteButton').waitFor({ state: 'visible' });
      await preview.frameLocator('#preview').locator('#preview2Form').waitFor({ state: 'attached' });
      const failureStart = s.recorder.requestFailures.length;
      const nextEncounter = context.waitForEvent('page', { timeout: 20000 });
      const [saved] = await Promise.all([
        nextRx.waitForResponse(isEncounterWrite), preview.locator('#printPasteButton').click(),
      ]);
      h.assert(saved.status() === 200 && saved.headers()['x-carlos-encounter-write'] === 'written'
        && new URLSearchParams(saved.request().postData() || '').get('chartContext') === tokens[1],
      'The no-draft paste was not acknowledged under its originating program');
      await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_no=${q(defaultProgram)} AND LOCATE(${q(marker)},note)>0
        AND LOCATE('one tablet daily',note)>0`, '1', 'The no-draft paste did not create its own program note');
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${protectedNote}`)) === protectedBefore,
        'The no-draft paste changed the protected automatic candidate instead of creating a fresh note');
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_no=${q(capturedProgram)} ORDER BY note_id`)) === capturedBefore,
      'The no-draft paste appended to or otherwise changed the other program note');
      const nextChart = await nextEncounter;
      defaultChartAfterPaste = nextChart;
      await nextChart.waitForURL(url => url.searchParams.get('chartContext') === tokens[1],
        { waitUntil: 'domcontentloaded', timeout: 20000 });
      await waitForNavbars(nextChart, 30000);
      h.assert(await nextChart.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue() === tokens[1],
        'The no-draft paste reopened the other chart program');
      await finishAcknowledgedWrite(saved, nextRx);
      const failures = s.recorder.requestFailures.slice(failureStart);
      h.assert(failures.length <= 1 && failures.every(entry => entry.label === 'rx-program-isolation'
        && entry.errorText === 'net::ERR_ABORTED' && entry.resourceType === 'fetch'
        && h.pathOnly(entry.url).endsWith('/rx/WriteToEncounter')),
      'An unrelated request failed during no-draft Print & Paste');
      s.recorder.requestFailures.splice(failureStart, failures.length);
    });

    await s.step('saving an explicitly shared note clears its chart program draft and preserves the other program window', async () => {
      const editor = page => page.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
      const protectedDraftRows = () => sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_id=${capturedProgram} ORDER BY id`);
      const protectedDraftEvidence = () => sql.rows(`SELECT id,program_id,note_id,update_date,
        SHA2(note,256),CHAR_LENGTH(note) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_id=${capturedProgram} ORDER BY id`);
      const checkpoints = {};
      const chartRequests = [];
      const observeChartRequest = request => {
        const target = new URL(request.url());
        if (!target.pathname.endsWith('/CaseManagementEntry')) return;
        const body = new URLSearchParams(request.postData() || '');
        const contextToken = target.searchParams.get('chartContext') || body.get('chartContext');
        chartRequests.push({ method: body.get('method') || target.searchParams.get('method'),
          program: body.get('programId'), chart: tokens.indexOf(contextToken),
          noteId: body.get('note_id') || body.get('noteId'),
          noteLength: (body.get('note') || body.get('caseNote_note') || '').length });
      };
      context.on('request', observeChartRequest);
      const autosave = async (page, text, token, program) => {
        await page.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation
          && window.issueUpdatesPending === 0);
        await page.evaluate(() => clearAutoSaveTimer());
        await editor(page).fill(text);
        const pending = page.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
          && new URLSearchParams(response.request().postData() || '').get('method') === 'autosave');
        await page.evaluate(() => autoSave());
        const saved = await pending;
        await saved.finished();
        h.assert(saved.status() === 200 && hasChartContext(saved.request(), token),
        'The shared-note autosave failed or changed its chart context');
        await page.waitForFunction(() => !window.autoSaveXhr);
        await page.evaluate(() => clearAutoSaveTimer());
        await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
          AND provider_no=${owner} AND program_id=${program}`, text,
        'The shared-note autosave did not retain its originating program');
      };
      const source = await opened.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
      h.assert(/^[1-9]\d*$/.test(source), 'The original program window has no saved note to share');
      const protectedText = `${await editor(opened).inputValue()}\n${marker}-keep-this-other-program-draft`;
      await autosave(opened, protectedText, tokens[0], capturedProgram);
      const protectedDraft = JSON.stringify(protectedDraftRows());
      checkpoints.beforeEdit = protectedDraftEvidence();
      const editLink = defaultChartAfterPaste.locator(`#edit${source}`);
      await editLink.waitFor({ state: 'visible' });
      const [edited, dialogs] = await Promise.all([
        defaultChartAfterPaste.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
          && new URLSearchParams(response.request().postData() || '').get('method') === 'edit'),
        h.withExpectedDialogs(defaultChartAfterPaste, () => editLink.click(), { accept: true }),
      ]);
      await edited.finished();
      checkpoints.afterEdit = protectedDraftEvidence();
      h.assert(edited.status() === 200 && hasChartContext(edited.request(), tokens[1]),
        'The explicitly selected shared note did not open in its originating chart context');
      h.assert(dialogs.length <= 1 && dialogs.every(dialog => dialog.type === 'confirm'
        && /You have started to edit this note in another window/.test(dialog.text)),
      'Editing the owned shared note raised an unexpected dialog');
      await defaultChartAfterPaste.waitForFunction(id =>
        document.forms.caseManagementEntryForm?.elements.noteId?.value === id, source);
      const sharedText = `${await editor(defaultChartAfterPaste).inputValue()}\n${marker}-shared-program-edit`;
      await autosave(defaultChartAfterPaste, sharedText, tokens[1], defaultProgram);
      checkpoints.afterSharedAutosave = protectedDraftEvidence();
      h.assert(sql.value(`SELECT note_id FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_id=${defaultProgram}`) === source,
      'The shared-note draft lost its explicitly selected source note');
      const [saved] = await Promise.all([
        defaultChartAfterPaste.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
          && new URLSearchParams(response.request().postData() || '').get('method') === 'save'),
        defaultChartAfterPaste.locator('#saveImg').first().click(),
      ]);
      await saved.finished();
      h.assert(saved.status() === 200 && hasChartContext(saved.request(), tokens[1]),
      'The explicit shared-note Save failed or changed its chart context');
      await defaultChartAfterPaste.waitForFunction(previous => {
        const id = document.forms.caseManagementEntryForm?.elements.noteId?.value;
        return /^[1-9]\d*$/.test(id || '') && id !== previous && !window.autoSaveXhr;
      }, source);
      const revision = await defaultChartAfterPaste.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${revision}
        AND demographic_no=${patient} AND program_no=${q(capturedProgram)} AND note=${q(sharedText)}`) === '1',
      'Saving the explicit shared note moved its program or changed its text');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${owner} AND program_id=${defaultProgram}`) === '0',
      'Saving the shared note retained its originating chart program draft');
      checkpoints.afterSharedSave = protectedDraftEvidence();
      const afterProtectedDraft = JSON.stringify(protectedDraftRows());
      context.off('request', observeChartRequest);
      if (afterProtectedDraft !== protectedDraft) {
        console.error('Shared-note draft evidence:', JSON.stringify({ checkpoints, chartRequests }));
      }
      h.assert(afterProtectedDraft === protectedDraft,
      'Saving the shared note consumed or changed the other program window draft');
      h.assert(await editor(opened).inputValue() === protectedText,
        'Saving the shared note replaced the unsaved text in the other program window');
      // The draft's source is now an older revision, although its own update timestamp
      // still predates the draft. The transactional UUID-chain check must refuse it.
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave draft JOIN casemgmt_note original_note
        ON original_note.note_id=draft.note_id WHERE draft.demographic_no=${patient}
        AND draft.provider_no=${owner} AND draft.program_id=${capturedProgram}
        AND draft.note_id=${source} AND original_note.update_date<=draft.update_date`) === '1',
      'The stale-revision control does not have a later draft with preserved time-of-day');
      const snapshot = () => JSON.stringify([
        sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`),
        sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patient} ORDER BY id`),
        sql.rows(`SELECT * FROM prescription WHERE demographic_no=${patient} ORDER BY script_no`),
        sql.rows(`SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`),
      ]);
      const before = snapshot();
      const csrf = await sessionToken(defaultChartAfterPaste, s.config.baseUrl);
      const body = new URLSearchParams(write.request().postData() || '');
      if (body.has('CSRF-TOKEN')) body.set('CSRF-TOKEN', csrf);
      const stale = await context.request.post(write.url(), {
        data: body.toString(), headers: { 'content-type': 'application/x-www-form-urlencoded',
          'x-requested-with': 'XMLHttpRequest', 'csrf-token': csrf,
          origin: new URL(s.config.baseUrl).origin, referer: defaultChartAfterPaste.url() },
        maxRedirects: 0, failOnStatusCode: false,
      });
      h.assert(stale.status() === 409 && stale.headers()['x-carlos-encounter-write'] === 'not-written',
        'An Rx append against an older note revision did not refuse safely');
      h.assert(snapshot() === before, 'The stale-revision refusal changed clinical rows or consumed the protected draft');
      await stale.dispose();
      h.assert(await editor(opened).inputValue() === protectedText,
        'The stale-revision refusal changed the other window unsaved text');
    });

    await s.step('a writer belonging only to the originating chart program can save and reread its shared note', async () => {
      const page = defaultChartAfterPaste;
      const editor = () => page.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
      const fingerprint = text => ({ length: text.length, sha256: createHash('sha256').update(text).digest('hex') });
      const departurePhases = new Map();
      const pingRequests = new WeakMap();
      const failedPings = [];
      const failedPingRequests = [];
      const reloadPings = [];
      const nativeBeacons = [];
      const beaconPrefix = `owned-rx-beacon-${marker}:`;
      const observeNativeBeacon = message => {
        if (message.type() === 'debug' && message.text().startsWith(beaconPrefix)) {
          nativeBeacons.push(JSON.parse(message.text().slice(beaconPrefix.length)));
        }
      };
      page.on('console', observeNativeBeacon);
      const pingParameters = (url, data) => {
        const query = new URL(url).searchParams;
        const body = new URLSearchParams(data || '');
        const param = name => {
          const values = [...query.getAll(name), ...body.getAll(name)];
          return values.length && values.every(value => value === values[0]) ? values[0] : null;
        };
        return { method: param('method'), provider: param('providerNo'), patient: param('demographicNo'),
          noteId: param('noteId'), chart: tokens.indexOf(param('chartContext')),
          csrfPresent: Boolean(param('CSRF-TOKEN')) };
      };
      const observePing = request => {
        if (request.resourceType() !== 'ping') return;
        const target = new URL(request.url());
        let originPage;
        try { originPage = request.frame().page(); } catch { /* Keep an unknown origin explicit. */ }
        pingRequests.set(request, { path: target.pathname, verb: request.method(),
          ...pingParameters(request.url(), request.postData()),
          phase: departurePhases.get(originPage) || 'unknown', originPage });
        if (originPage === page && departurePhases.get(page) === 'owned-Y-reload') reloadPings.push(request);
      };
      const observePingFailure = request => {
        const ping = pingRequests.get(request);
        if (!ping) return;
        const { originPage, ...evidence } = ping;
        failedPings.push({ ...evidence, pageClosed: originPage ? originPage.isClosed() : null,
          error: request.failure()?.errorText });
        failedPingRequests.push({ request, ping });
      };
      context.on('request', observePing);
      context.on('requestfailed', observePingFailure);
      // Close only this owned context's first-program windows before removing its membership.
      for (const chart of new Set([charts[0], opened, launchedRx, isolatedRx])) {
        h.assert(chart.context() === context, 'A first-program window belongs to another browser context');
        if (!chart.isClosed()) {
          departurePhases.set(chart, 'owned-X-close');
          await chart.evaluate(() => { if (typeof clearAutoSaveTimer === 'function') clearAutoSaveTimer(); });
          await chart.close();
        }
      }
      const membershipWhere = `provider_no=${owner} AND program_id=${capturedProgram}`;
      const memberships = sql.rows(`SELECT id,program_id,provider_no,role_id,team_id
        FROM program_provider WHERE ${membershipWhere} ORDER BY id`);
      h.assert(memberships.length === 1, 'The first-program membership is not one owned fixture row');
      const membership = memberships[0];
      h.assert(/^[1-9]\d*$/.test(membership[0])
        && JSON.stringify(membership.slice(1)) === JSON.stringify([capturedProgram, login.providerNo, role, '0']),
      'The membership to remove does not match the owned first-program fixture');
      const membershipId = membership[0];
      const membershipBefore = JSON.stringify(sql.rows(`SELECT * FROM program_provider WHERE id=${membershipId}`));
      h.assert(sql.value(`SELECT COUNT(*) FROM program_provider_team WHERE id=${membershipId}`) === '0',
        'The owned membership has unexpected team relationships');
      const writerRole = sql.value(`SELECT role_id FROM program_provider
        WHERE provider_no=${owner} AND program_id=${defaultProgram}`);
      h.assert(/^[1-9]\d*$/.test(writerRole)
        && sql.value(`SELECT COUNT(*) FROM caisi_role WHERE role_id=${writerRole}`) === '1',
      'The originating chart does not have a valid writer role');
      const source = await page.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
      h.assert(/^[1-9]\d*$/.test(source)
        && sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${source}
          AND demographic_no=${patient} AND provider_no=${owner} AND program_no=${q(capturedProgram)}`) === '1',
      'The Y-only edit does not start from the owned first-program note');
      const originalNote = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${source}`));
      const originalLocation = JSON.stringify(sql.rows(`SELECT program_no,reporter_program_team
        FROM casemgmt_note WHERE note_id=${source}`));
      const originalUuid = sql.value(`SELECT uuid FROM casemgmt_note WHERE note_id=${source}`);
      const otherDrafts = () => JSON.stringify(sql.rows(`SELECT * FROM casemgmt_tmpsave
        WHERE provider_no=${owner} AND demographic_no IN (${patient},${referencePatient})
        AND NOT (demographic_no=${patient} AND program_id=${defaultProgram}) ORDER BY id`));
      const providerAndPatients = () => JSON.stringify([
        sql.rows(`SELECT * FROM provider WHERE provider_no=${owner}`),
        sql.rows(`SELECT * FROM demographic WHERE demographic_no IN (${patient},${referencePatient}) ORDER BY demographic_no`),
        sql.rows(`SELECT * FROM program_provider WHERE provider_no=${owner} AND program_id=${defaultProgram} ORDER BY id`),
      ]);
      const draftsBefore = otherDrafts();
      const identitiesBefore = providerAndPatients();
      try {
        sql.execute(`DELETE FROM program_provider WHERE id=${membershipId} AND ${membershipWhere}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM program_provider WHERE ${membershipWhere}`) === '0',
          'The first-program membership was not removed before the native Save');
        // ProgramManager queries this membership through fresh, uncached JPQL on every request.
        // Its absence here makes the native Save exercise the originating-program role fallback.
        await page.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation
          && window.issueUpdatesPending === 0);
        await page.evaluate(() => clearAutoSaveTimer());
        const text = `${await editor().inputValue()}\n${marker}-originating-program-only-edit`;
        await editor().fill(text);
        const pendingDraft = page.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
          && new URLSearchParams(response.request().postData() || '').get('method') === 'autosave');
        await page.evaluate(() => autoSave());
        const draft = await pendingDraft;
        await draft.finished();
        h.assert(draft.status() === 200 && hasChartContext(draft.request(), tokens[1]),
          'The Y-only draft was not acknowledged in its originating chart');
        await page.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation);
        await page.evaluate(() => clearAutoSaveTimer());
        await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
          AND provider_no=${owner} AND program_id=${defaultProgram} AND note_id=${source}`, text,
        'The Y-only draft did not retain its selected shared note');
        const [saved] = await Promise.all([
          page.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(response.request().postData() || '').get('method') === 'save'),
          page.locator('#saveImg').first().click(),
        ]);
        await saved.finished();
        h.assert(saved.status() === 200 && hasChartContext(saved.request(), tokens[1]),
          'The Y-only native Save failed or changed its chart context');
        await page.waitForFunction(previous => {
          const current = document.forms.caseManagementEntryForm?.elements.noteId?.value;
          return /^[1-9]\d*$/.test(current || '') && current !== previous && !window.autoSaveXhr;
        }, source);
        const revision = await page.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
        h.assert(sql.value(`SELECT COUNT(*) FROM program_provider WHERE ${membershipWhere}`) === '0',
          'The Save did not execute while the writer lacked first-program membership');
        h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${revision}
          AND demographic_no=${patient} AND provider_no=${owner} AND uuid=${q(originalUuid)}
          AND reporter_caisi_role=${q(writerRole)} AND note=${q(text)}`) === '1',
        'The shared revision lost its text, UUID, ownership, or valid originating-program writer role');
        h.assert(JSON.stringify(sql.rows(`SELECT program_no,reporter_program_team FROM casemgmt_note
          WHERE note_id=${revision}`)) === originalLocation,
        'The Y-only Save changed the shared note program or team');
        h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${source}`)) === originalNote,
          'The Y-only Save changed the historical source row');
        h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
          AND provider_no=${owner} AND program_id=${defaultProgram}`) === '0'
          && otherDrafts() === draftsBefore && providerAndPatients() === identitiesBefore,
        'The Y-only Save changed another draft, provider, patient, or the originating membership');
        h.assert(await editor().inputValue() === text, 'The native Save did not retain the shared editor text');
        const storedRevision = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${revision}`));
        const readbackEvidence = { expected: fingerprint(text), beforeReload: fingerprint(await editor().inputValue()) };
        // Chromium omits sendBeacon Blob bodies from Playwright and CDP. Observe
        // this outgoing document's native arguments while forwarding them unchanged.
        // Only normalized identity/acknowledgment metadata leaves the page.
        await page.evaluate(({ prefix, token }) => {
          const NativeBlob = window.Blob;
          const nativeBeacon = navigator.sendBeacon;
          const payloads = new WeakMap();
          window.Blob = new Proxy(NativeBlob, { construct(target, args, newTarget) {
            const blob = Reflect.construct(target, args, newTarget);
            if (Array.isArray(args[0]) && args[0].every(part => typeof part === 'string')) {
              payloads.set(blob, args[0].join(''));
            }
            return blob;
          } });
          navigator.sendBeacon = function observedBeacon(url, blob) {
            const accepted = nativeBeacon.call(this, url, blob);
            const target = new URL(url, location.href);
            if (target.pathname.endsWith('/CaseManagementEntry')) {
              const body = new URLSearchParams(payloads.get(blob) || '');
              const param = name => {
                const values = [...target.searchParams.getAll(name), ...body.getAll(name)];
                return values.length && values.every(value => value === values[0]) ? values[0] : null;
              };
              console.debug(prefix + JSON.stringify({ path: target.pathname,
                bodyKnown: payloads.has(blob), method: param('method'), provider: param('providerNo'),
                patient: param('demographicNo'), noteId: param('noteId'),
                chartMatches: param('chartContext') === token, csrfPresent: Boolean(param('CSRF-TOKEN')), accepted }));
            }
            return accepted;
          };
        }, { prefix: beaconPrefix, token: tokens[1] });
        departurePhases.set(page, 'owned-Y-reload');
        await page.reload({ waitUntil: 'domcontentloaded' });
        await waitForNavbars(page, 30000);
        h.assert(await page.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue() === tokens[1],
          'The Y-only chart reload changed its originating program');
        const edit = page.locator(`#edit${revision}`);
        await edit.waitFor({ state: 'visible' });
        readbackEvidence.afterReload = fingerprint(await editor().inputValue());
        readbackEvidence.full = await page.locator(`#full${revision}`).inputValue();
        h.assert(['true', 'false'].includes(readbackEvidence.full), 'The shared-note view mode is unknown');
        // Full-view Edit presents its rendered note with one trailing newline.
        // Keep the stored bytes exact and assert this existing presentation exactly.
        const presentedText = readbackEvidence.full === 'true' ? `${text.trim()}\n` : text;
        const [readback, dialogs] = await Promise.all([
          page.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(response.request().postData() || '').get('method') === 'edit'),
          h.withExpectedDialogs(page, () => edit.click(), { accept: true }),
        ]);
        await readback.finished();
        const returnedEditor = await page.evaluate(({ html, id }) => {
          const document = new DOMParser().parseFromString(html, 'text/html');
          return document.getElementById(`caseNote_note${id}`)?.value ?? null;
        }, { html: await readback.text(), id: revision });
        readbackEvidence.responseEditor = returnedEditor === null ? null : fingerprint(returnedEditor);
        h.assert(readback.status() === 200 && hasChartContext(readback.request(), tokens[1])
          && dialogs.length <= 1 && dialogs.every(dialog => dialog.type === 'confirm'
            && /You have started to edit this note in another window/.test(dialog.text)),
        'The Y-only shared-note list readback failed or raised an unexpected dialog');
        await page.waitForFunction(id => document.forms.caseManagementEntryForm?.elements.noteId?.value === id, revision);
        await page.waitForFunction(() => window.issueUpdatesPending === 0);
        try {
          await waitUntil(async () => await editor().inputValue() === presentedText, 'the exact Y-only native Edit presentation', 5000);
        } catch (error) {
          const actual = await editor().inputValue();
          console.error('Y-only readback evidence:', JSON.stringify({ ...readbackEvidence,
            actual: fingerprint(actual), equalsExpectedPlusLF: actual === `${text}\n`,
            equalAfterTrimming: actual.trim() === text.trim(), failedPings }));
          throw error;
        }
        h.assert(await editor().inputValue() === presentedText,
          'The Y-only note disappeared or changed on native Edit readback');
        h.assert(sql.value(`SELECT HEX(note) FROM casemgmt_note WHERE note_id=${revision}`)
          === Buffer.from(text, 'utf8').toString('hex').toUpperCase(),
        'The Y-only readback changed the exact stored note bytes');
        h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${revision}`)) === storedRevision,
          'The Y-only readback changed the stored revision history or metadata');
        await page.evaluate(() => clearAutoSaveTimer());
        h.assert(otherDrafts() === draftsBefore && providerAndPatients() === identitiesBefore,
          'The Y-only readback changed other drafts, provider or patient rows');
        h.assert(failedPingRequests.length <= 1, 'More than one ping failed during the owned window departures');
        for (const { request, ping } of failedPingRequests) {
          // The layout calls onClosing from beforeunload and pagehide. Both native
          // calls must be the same owned release; only its one known abort is consumed.
          h.assert(ping.originPage === page && ping.phase === 'owned-Y-reload'
            && request.failure()?.errorText === 'net::ERR_ABORTED' && ping.verb === 'POST' && ping.chart === 1
            && nativeBeacons.length >= 1 && nativeBeacons.length <= 2 && reloadPings.includes(request)
            && reloadPings.length <= nativeBeacons.length && nativeBeacons.every(beacon => beacon.bodyKnown
              && beacon.path === ping.path && beacon.method === 'releaseNoteLock'
              && beacon.provider === login.providerNo && beacon.patient === patient
              && beacon.noteId === revision && beacon.chartMatches && beacon.csrfPresent && beacon.accepted),
          `The failed ping is not the exact owned reload lock-release beacon: ${JSON.stringify({
            failedPings, nativeBeacons, reloadPings: reloadPings.length })}`);
          const recorded = s.recorder.requestFailures.filter(entry => entry.label === 'rx-program-isolation'
            && entry.url === request.url() && entry.resourceType === 'ping' && entry.errorText === 'net::ERR_ABORTED');
          h.assert(recorded.length === 1, 'The proved reload beacon does not match exactly one recorder failure');
          s.recorder.requestFailures.splice(s.recorder.requestFailures.indexOf(recorded[0]), 1);
          console.log('  CHECK rx-print-profile: acknowledged only the proved owned reload lock-release beacon abort');
        }
      } finally {
        context.off('request', observePing);
        context.off('requestfailed', observePingFailure);
        page.off('console', observeNativeBeacon);
        const remaining = sql.rows(`SELECT * FROM program_provider WHERE id=${membershipId}`);
        if (!remaining.length) {
          sql.execute(`INSERT INTO program_provider (id,program_id,provider_no,role_id,team_id)
            VALUES (${membership.map(q).join(',')})`);
        }
        h.assert(JSON.stringify(sql.rows(`SELECT * FROM program_provider WHERE id=${membershipId}`)) === membershipBefore,
          'The exact owned first-program membership was not restored');
      }
    });
  });
}

if (require.main === module) runWorkflow('rx-print-profile', workflow, { openPatient: true });
module.exports = { workflow };
