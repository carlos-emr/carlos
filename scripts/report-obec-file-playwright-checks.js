#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Ontario Overnight Batch Eligibility Checking (OBEC) file: the list of health numbers a clinic sends the
 * Ministry the night before, so that patients with an invalid card can be called before their visit.
 *
 * User path: Administration ▸ Reports ▸ Overnight Batch (oscarReport/obec) ▸ Begin Date + Days ▸ Create Report ▸
 * "File Created OBECE….TXT" (servlet/OscarDownload?homepath=obecdownload).
 * Asserted:
 *   - the report creates a file and links it, and the download is that file;
 *   - the file holds one record per Ontario patient with a health number who has an appointment from the Begin
 *     Date through Begin Date + Days: "OBEC01", the health number padded to 10 and the version code padded to 2
 *     (blank when the patient has none), with CR LF between records. A patient with two appointments in the window
 *     is listed once. Appointments the day before and the day after the window, a patient whose card is from another
 *     province and a patient without a health number are left out;
 *   - the file is written to DOCUMENT_DIR and staged in ONEDT_OUTBOX (where Administration ▸ Billing ▸ MCEDT
 *     Mailbox ▸ Upload sends it from), each byte for byte the download;
 *   - a window with no appointments creates no file and says so.
 * Last: a patient whose version code was never set (NULL) is listed with a blank version. On release/2026.08 this fails
 * until #4967 is fixed: ObecData.space() pads the version without a null check, so Create Report answers 500.
 * The window is a random range decades ahead that the run first proves empty, so the file is the owned rows alone.
 * Fixtures: six FAKE patients (createOwnedPatient: last name = the run marker), given FAKE health numbers the
 * database does not hold, and their appointments on the test login's schedule (notes = the marker). The report reads
 * every appointment in the clinic, and the file is staged in the shared EDT outbox, so the check must not run
 * concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup deletes the appointments and the patients,
 * the generated file from DOCUMENT_DIR (only while it holds the bytes downloaded) and its copy from the outbox
 * (lib/mcedt-local-state.js createOutboxLedger), and asserts each is gone.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createOwnedPatient, removeOwnedPatient } = require('./lib/owned-patient');
const D = require('./lib/download-contract');
const L = require('./lib/mcedt-local-state');

const TIMEOUT = 30000;
// Creating the file reads every appointment in the window and writes two files; allow for a slow first run.
const REPORT_TIMEOUT = 60000;
const PROPERTIES_FILE = process.env.CARLOS_PROPERTIES_FILE || '/etc/carlos-emr/carlos.properties';
const NUM_DAYS = 3;
// The name ObecData.writeFile gives the file: OBECE, the time in milliseconds, .TXT.
const OBEC_NAME = /^OBECE\d{13}\.TXT$/;

const STEP = {
  setup: 'the install is Ontario, DOCUMENT_DIR and ONEDT_OUTBOX are usable, and the report window holds only the owned appointments',
  menu: 'Administration ▸ Reports ▸ Overnight Batch opens the OBEC form',
  create: 'Create Report for the window creates an OBECE file, links it, and the link downloads it',
  records: 'the file lists each Ontario patient with a health number seen in the window once, as OBEC01 + health number + version, and no one else',
  stored: 'the file is written to DOCUMENT_DIR and staged in ONEDT_OUTBOX, each byte for byte the download',
  empty: 'a window with no appointments creates no file and says so',
  unsetVersion: 'a patient whose version code was never set is listed with a blank version, not an error',
};

/** YYYY-MM-DD plus `days` (UTC calendar arithmetic). */
function addDays(iso, days) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** The OBECE file names in `dir`. */
function obecFiles(dir) {
  return fs.readdirSync(dir).filter(name => OBEC_NAME.test(name)).sort();
}

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const hex = marker.slice('FAKE-PW'.length);
  const n = BigInt(`0x${hex}`);

  // ---- configuration the application read at start --------------------------------------------------------------
  const propertiesText = (() => { try { return fs.readFileSync(PROPERTIES_FILE, 'utf8'); } catch (error) { return null; } })();
  if (propertiesText === null) throw new h.SkipCheck(`the properties file ${PROPERTIES_FILE} is unreadable, so the billing region and ONEDT_OUTBOX are unknown`);
  const billRegion = D.propertyValue(propertiesText, 'billregion');
  if (billRegion !== 'ON') throw new h.SkipCheck(`billregion=${billRegion === undefined ? '(not set)' : billRegion}: the OBEC file is Ontario`);
  const documentUsable = D.usableDirectory(process.env.DOCUMENT_DIR || D.propertyValue(propertiesText, 'DOCUMENT_DIR'), 'DOCUMENT_DIR');
  if (!documentUsable.ok) throw new h.SkipCheck(documentUsable.reason);
  const documentDir = fs.realpathSync(documentUsable.dir);
  const outboxUsable = D.usableDirectory(process.env.ONEDT_OUTBOX || D.propertyValue(propertiesText, 'ONEDT_OUTBOX'), 'ONEDT_OUTBOX');
  if (!outboxUsable.ok) throw new h.SkipCheck(outboxUsable.reason);
  const outbox = fs.realpathSync(outboxUsable.dir);

  // ---- the windows: decades ahead, chosen from the marker, proven empty ---------------------------------------------
  const pickStart = (salt) => {
    const k = (n / salt) % 100000n;
    const year = 2070 + Number(k % 25n);
    const month = 3 + Number((k / 25n) % 9n);
    const day = 6 + Number((k / 225n) % 13n);
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  };
  const appointmentsBetween = (from, to) => sql.value(`SELECT COUNT(*) FROM appointment
    WHERE appointment_date BETWEEN ${q(from)} AND ${q(to)}`);
  const start = pickStart(1n);
  const end = addDays(start, NUM_DAYS);
  const emptyStart = pickStart(7919n);
  const emptyEnd = addDays(emptyStart, NUM_DAYS);
  const unsetStart = pickStart(104729n);
  const unsetEnd = addDays(unsetStart, NUM_DAYS);

  // ---- patients and the records they should (or should not) give -----------------------------------------------------
  const hin = (k) => `9${String((n / BigInt(k)) % 1000000000n).padStart(9, '0')}`;
  const PATIENTS = [
    { key: 'twice', firstName: 'Twice', hin: hin(3), ver: 'AB', hcType: 'ON', dates: [start, end], listed: true },
    { key: 'noVersion', firstName: 'NoVersion', hin: hin(5), ver: '', hcType: 'ON', dates: [addDays(start, 1)], listed: true },
    { key: 'outside', firstName: 'Outside', hin: hin(11), ver: 'CD', hcType: 'ON', dates: [addDays(start, -1), addDays(end, 1)], listed: false },
    { key: 'otherProvince', firstName: 'OtherProvince', hin: hin(13), ver: 'EF', hcType: 'BC', dates: [addDays(start, 1)], listed: false },
    { key: 'noNumber', firstName: 'NoNumber', hin: '', ver: 'GH', hcType: 'ON', dates: [addDays(start, 2)], listed: false },
    // ver NULL, as a patient made by an HL7 feed or the REST API without a version code is stored. Its own window: the last step.
    { key: 'unsetVersion', firstName: 'UnsetVersion', hin: hin(17), ver: null, hcType: 'ON', dates: [addDays(unsetStart, 1)], listed: false },
  ];
  const owned = [];
  const files = { names: [], bytes: new Map() };
  const documentsBefore = obecFiles(documentDir);
  const ledger = L.createOutboxLedger({ outbox });
  let appointmentsSeeded = false;
  const ownedIds = () => owned.map(p => p.id).join(',') || '0';

  // Cleanup runs last-registered first: the patients go last, after their appointments.
  s.cleanup(() => {
    for (const p of owned.slice().reverse()) removeOwnedPatient(sql, p.id, marker, { extra: [['appointmentArchive', 'demographic_no']] });
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${ownedIds()})`) === '0', 'The owned patients were not removed');
  });
  s.cleanup(() => {
    if (!appointmentsSeeded) return;
    sql.execute(`DELETE FROM appointment WHERE notes=${q(marker)} AND demographic_no IN (${ownedIds()})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE notes=${q(marker)}`) === '0', 'The owned appointments were not removed');
  });
  // Registered before the file cleanup so that it runs after it (LIFO): that cleanup tells the ledger about a leftover file.
  s.cleanup(() => ledger.restore());
  s.cleanup(() => {
    const problems = [];
    // A run that failed between Create Report and reading the link never learned the file's name: a new OBECE file
    // whose every record is one of this run's FAKE health numbers is the run's own, and goes too (with its outbox copy).
    const ownHins = new Set(owned.filter(p => p.hin).map(p => p.hin));
    for (const name of obecFiles(documentDir).filter(entry => !documentsBefore.includes(entry) && !files.names.includes(entry))) {
      const bytes = fs.readFileSync(path.join(documentDir, name));
      const records = bytes.toString('latin1').split(/\r\n?|\n/).filter(Boolean);
      if (records.length && records.every(record => ownHins.has(record.slice(6, 16).trim()))) {
        files.names.push(name);
        files.bytes.set(name, bytes);
        ledger.expect(name, bytes);
      }
    }
    for (const name of files.names) {
      const file = path.join(documentDir, name);
      if (!fs.existsSync(file)) continue;
      if (fs.readFileSync(file).equals(files.bytes.get(name))) fs.unlinkSync(file);
      else problems.push(`${name} in DOCUMENT_DIR holds other bytes and was left in place`);
    }
    const left = obecFiles(documentDir).filter(name => !documentsBefore.includes(name));
    if (left.length) problems.push(`${left.length} OBECE file(s) the run caused are still in DOCUMENT_DIR: ${left.join(', ')}`);
    h.assert(problems.length === 0, problems.join('; '));
  });

  // Before anything is written: a window that already holds appointments would put other patients in the file.
  for (const [from, to, what] of [[addDays(start, -1), addDays(end, 1), 'report'], [emptyStart, emptyEnd, 'empty'], [unsetStart, unsetEnd, 'unset-version']]) {
    const already = appointmentsBetween(from, to);
    if (already !== '0') throw new h.SkipCheck(`the ${what} window ${from}..${to} already holds ${already} appointments, so the file could not be asserted exactly`);
  }

  await s.step(STEP.setup, async () => {
    h.assert(start > '2069-12-31' && emptyStart > '2069-12-31', 'The report windows are not in the far future');
    const apart = (a, b) => a[0] > addDays(b[1], 1) || b[0] > addDays(a[1], 1);
    const windows = [[addDays(start, -1), addDays(end, 1)], [emptyStart, emptyEnd], [unsetStart, unsetEnd]];
    h.assert(apart(windows[0], windows[1]) && apart(windows[0], windows[2]) && apart(windows[1], windows[2]), 'The report windows overlap');
    for (const p of PATIENTS) {
      if (p.hin) h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${q(p.hin)}`) === '0', `The FAKE health number for ${p.key} is already in use`);
      const id = createOwnedPatient(sql, { marker, provider, firstName: p.firstName });
      owned.push({ ...p, id });
      sql.execute(`UPDATE demographic SET hin=${q(p.hin)}, ver=${p.ver === null ? 'NULL' : q(p.ver)}, hc_type=${q(p.hcType)}
        WHERE demographic_no=${id} AND last_name=${q(marker)}`);
    }
    appointmentsSeeded = true;
    const values = owned.flatMap(p => p.dates.map(date => `(${[q(provider), q(date), "'09:00:00'", "'09:14:59'", q(`${marker},${p.firstName}`),
      p.id, q(marker), "''", "''", "''", "''", "''", "''", "'t'", 'NOW()', q(provider)].join(',')})`));
    sql.execute(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
      notes, reason, location, resources, type, style, billing, status, createdatetime, creator) VALUES ${values.join(',')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE notes=${q(marker)}`) === String(values.length), 'The owned appointments were not all created');
    const inReportWindow = owned.flatMap(p => p.dates).filter(date => date >= addDays(start, -1) && date <= addDays(end, 1)).length;
    h.assert(appointmentsBetween(addDays(start, -1), addDays(end, 1)) === String(inReportWindow),
      'The report window holds appointments other than the owned ones');
  });

  let admin;
  await s.step(STEP.menu, async () => {
    ({ page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'obec-admin', timeout: TIMEOUT }));
    const link = admin.locator('a[href$="/oscarReport/obec"]').first();
    const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (await panel.count() && !await panel.isVisible()) {
      await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
    }
    await link.click();
    await admin.locator('#obecForm').waitFor({ state: 'visible', timeout: TIMEOUT });
    await admin.waitForFunction(() => !!window.jQuery && !!window.jQuery('#obecForm').data('validator'));
  });

  /**
   * Fill the form and submit it. Inside the Administration shell the page's registerFormSubmit() sends the form as an
   * XHR and puts the answer into #dynamic-content, so the shell's URL does not change: wait for that request's answer
   * and for the report's verdict (a created file or "File not created!") in the content area.
   */
  async function createReport(page, begin) {
    const content = page.locator('#dynamic-content');
    await content.locator('#obecForm input[name="xml_vdate"]').fill(begin);
    await content.locator('#obecForm input[name="numDays"]').fill(String(NUM_DAYS));
    // Mark the form being submitted: the answer replaces the whole content area, so a form without the mark is the
    // new page (and the verdict below is not the previous report's).
    await content.locator('#obecForm').evaluate(form => { form.dataset.submitted = 'yes'; });
    // Every answer of oscarReport/obec is collected, so a request the page sent with other values is reported as such.
    const answers = [];
    const collect = r => { if (new URL(r.url()).pathname.endsWith('/oscarReport/obec')) answers.push(r); };
    page.on('response', collect);
    try {
      await content.locator('#obecForm input[type="submit"][value="Create Report"]').click();
      for (const end = Date.now() + REPORT_TIMEOUT; answers.length === 0 && Date.now() < end;) await page.waitForTimeout(200);
    } finally {
      page.off('response', collect);
    }
    h.assert(answers.length === 1, `Create Report for ${begin} + ${NUM_DAYS} days got ${answers.length} answers from oscarReport/obec`);
    const response = answers[0];
    // Create Report writes the OBEC file, so the page posts its form: Begin Date and Days are in the request body.
    h.assert(response.request().method() === 'POST', `Create Report sent ${response.request().method()}, not the form's POST`);
    const sent = new URLSearchParams(response.request().postData() || '');
    h.assert(sent.get('xml_vdate') === begin && sent.get('numDays') === String(NUM_DAYS),
      `Create Report sent Begin Date ${sent.get('xml_vdate')} and Days ${sent.get('numDays')}, not ${begin} and ${NUM_DAYS}`);
    h.assert(response.status() === 200, `Create Report for ${begin} + ${NUM_DAYS} days answered HTTP ${response.status()}`);
    await content.locator('#obecForm:not([data-submitted])').waitFor({ state: 'attached', timeout: TIMEOUT });
    const verdict = content.locator('.alert-success a[href*="/servlet/OscarDownload"], .alert:has-text("File not created!")');
    await verdict.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    return content;
  }

  let download;
  await s.step(STEP.create, async () => {
    const report = await createReport(admin, start);
    const created = report.locator('.alert-success a[href*="/servlet/OscarDownload"]');
    h.assert(await created.count() === 1, `The report did not link a created file: ${(await report.innerText()).replace(/\s+/g, ' ').slice(0, 200)}`);
    const name = /File\s+Created\s+(\S+)/.exec(await created.innerText())?.[1] || '';
    h.assert(OBEC_NAME.test(name), `The created file is not named like an OBEC file: ${name}`);
    files.names.push(name);
    const href = new URL(await created.getAttribute('href'), admin.url());
    h.assert(href.searchParams.get('homepath') === 'obecdownload' && href.searchParams.get('filename') === name,
      'The link does not download the created file');
    const [event] = await Promise.all([admin.waitForEvent('download', { timeout: TIMEOUT }), created.click()]);
    const saveDir = fs.mkdtempSync(path.join(os.tmpdir(), 'obec-download-'));
    const saved = path.join(saveDir, `obec-${hex}.txt`);
    await event.saveAs(saved);
    download = { name, bytes: fs.readFileSync(saved) };
    fs.rmSync(saveDir, { recursive: true, force: true });
    files.bytes.set(name, download.bytes);
    ledger.expect(name, download.bytes);
    h.assert(download.bytes.length > 0 && !D.startsLikeHtml(download.bytes), 'The download is empty or an HTML page');
  });

  await s.step(STEP.records, async () => {
    const text = download.bytes.toString('latin1');
    h.assert(/^[^\n]*\r(\n[^\n]*\r)*\n?$/.test(text) && !/\r(?!\n|$)/.test(text),
      `The records are not separated by CR LF: ${JSON.stringify(text.slice(0, 60))}`);
    const records = text.replace(/\r\n?$/, '').split('\r\n');
    const shape = /^OBEC01[0-9 ]{10}[A-Z ]{2}$/;
    const odd = records.filter(record => !shape.test(record));
    h.assert(odd.length === 0, `${odd.length} record(s) are not OBEC01 + 10 + 2 characters: ${JSON.stringify(odd.slice(0, 3))}`);
    const expected = owned.filter(p => p.listed).map(p => `OBEC01${p.hin.padEnd(10)}${(p.ver || '').padEnd(2)}`).sort();
    const got = records.slice().sort();
    const problems = [];
    for (const p of owned) {
      const count = p.hin ? records.filter(record => record.slice(6, 16).trim() === p.hin).length : 0;
      if (p.listed && count !== 1) problems.push(`${p.key} is listed ${count} times, expected once`);
      if (!p.listed && count !== 0) problems.push(`${p.key} is listed although it should be left out`);
    }
    if (JSON.stringify(got) !== JSON.stringify(expected)) problems.push(`the file holds ${JSON.stringify(got)}, expected ${JSON.stringify(expected)}`);
    h.assert(problems.length === 0, problems.join('; '));
  });

  await s.step(STEP.stored, async () => {
    const stored = path.join(documentDir, download.name);
    h.assert(fs.existsSync(stored) && fs.readFileSync(stored).equals(download.bytes), 'DOCUMENT_DIR does not hold the downloaded file byte for byte');
    const staged = path.join(outbox, download.name);
    h.assert(fs.existsSync(staged) && fs.readFileSync(staged).equals(download.bytes), 'ONEDT_OUTBOX does not hold the downloaded file byte for byte');
    h.assert(obecFiles(documentDir).filter(name => !documentsBefore.includes(name)).length === 1, 'The report wrote more than one OBECE file to DOCUMENT_DIR');
  });

  await s.step(STEP.empty, async () => {
    const before = obecFiles(documentDir);
    const outboxBefore = obecFiles(outbox);
    const report = await createReport(admin, emptyStart);
    const body = (await report.innerText()).replace(/\s+/g, ' ');
    h.assert(await report.locator('.alert-success a[href*="/servlet/OscarDownload"]').count() === 0, 'An empty window linked a created file');
    h.assert(/Warning!\s*File not created!/.test(body), `An empty window did not say that no file was created: ${body.slice(0, 200)}`);
    h.assert(JSON.stringify(obecFiles(documentDir)) === JSON.stringify(before), 'An empty window wrote an OBECE file to DOCUMENT_DIR');
    h.assert(JSON.stringify(obecFiles(outbox)) === JSON.stringify(outboxBefore), 'An empty window staged an OBECE file in ONEDT_OUTBOX');
  });

  // Last: ObecData.space() pads the version code without a null check, so one such patient in the window answers 500.
  await s.step(STEP.unsetVersion, async () => {
    const p = owned.find(entry => entry.key === 'unsetVersion');
    const report = await createReport(admin, unsetStart);
    const created = report.locator('.alert-success a[href*="/servlet/OscarDownload"]');
    h.assert(await created.count() === 1, 'The report did not link a created file for a window whose one patient has no version code');
    const name = /File\s+Created\s+(\S+)/.exec(await created.innerText())?.[1] || '';
    h.assert(OBEC_NAME.test(name), `The created file is not named like an OBEC file: ${name}`);
    const bytes = fs.readFileSync(path.join(documentDir, name));
    files.names.push(name);
    files.bytes.set(name, bytes);
    ledger.expect(name, bytes);
    const records = bytes.toString('latin1').replace(/\r\n?$/, '').split('\r\n');
    h.assert(JSON.stringify(records) === JSON.stringify([`OBEC01${p.hin.padEnd(10)}  `]),
      `The file for the patient without a version code holds ${JSON.stringify(records)}`);
  });
}

if (require.main === module) runWorkflow('report-obec-file', workflow, { openPatient: false });
module.exports = { workflow, addDays };
