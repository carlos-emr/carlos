#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for what the front desk does to an appointment AFTER it exists:
 * edit it, advance its status from the day sheet, cancel it, delete it.
 * Also verifies excessive durations are refused on add and edit with actionable
 * feedback, no database change, and successful saving after correction (#3702).
 *
 * schedule-quick-search-appointment covers booking from the quick-search widget
 * and echart-new-patient-notes books from a day-sheet slot, so the booking half
 * is already covered twice. Nothing drove `appointment/UpdateRecord`,
 * `appointment/DeleteRecord`, or the day sheet's own status-letter link
 * (`dboperation=updateapptstatus`) before this check — a regression in any of
 * them reaches an operator first, because the schedule keeps rendering.
 *
 * EVERY SURFACE IS REACHED BY CLICKING, never by a hand-built URL. The slot link
 * opens the add popup, the appointment's own link on the day sheet opens the edit
 * popup, the status letter is a link on the day sheet. That matters here more than
 * usual: the add and edit popups behave differently when opened cold, and the cold
 * shape is the one that already works.
 *
 * DELETE IS ARCHIVE-THEN-REMOVE. AppointmentDeleteRecord2Action writes an
 * `appointmentArchive` row before removing the `appointment` row, so both halves
 * are asserted. A delete that silently stopped archiving looks identical to a
 * working one, and the archive is the only record a clinic keeps of a booking
 * that was removed.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:appointment-lifecycle-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   APPOINTMENT_DEMOGRAPHIC_NO=1     patient to book (must exist and be active)
 *   APPOINTMENT_PROVIDER_NO=999998   provider whose day sheet is driven
 *   APPOINTMENT_DAYS_AHEAD=400       how far out to book, to stay clear of demo data
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Requires pdftotext (poppler-utils) to inspect the printed HTML labels and to
 * validate the printed receipt contents.
 *
 * Cleanup: one appointment is booked, its reason and notes carry a unique
 * PW_APPT_<millis> marker, and every appointment / appointmentArchive row with
 * that marker is deleted in a finally, including after a failure.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const demographicNo = process.env.APPOINTMENT_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.APPOINTMENT_PROVIDER_NO || '999998';
const daysAhead = Number(process.env.APPOINTMENT_DAYS_AHEAD || '400');
assert(/^\d+$/.test(demographicNo), 'APPOINTMENT_DEMOGRAPHIC_NO must be numeric');
assert(/^\d+$/.test(providerNo), 'APPOINTMENT_PROVIDER_NO must be numeric');
assert(Number.isInteger(daysAhead) && daysAhead > 0 && daysAhead < 3650,
  'APPOINTMENT_DAYS_AHEAD must be a day count between 1 and 3649');

const stamp = `PW_APPT_${Date.now()}`;
const bookedReason = `${stamp} booked`;
const bookedNotes = `${stamp} notes`;
const editedReason = `${stamp} edited`;
const editedNotes = `${stamp} notes edited`;

// Booked far enough out that the demo dataset's own appointments cannot occupy
// the slot this check clicks; a same-week date made it flaky against the demo
// data rather than against the code.
const target = new Date(Date.now() + daysAhead * 24 * 60 * 60 * 1000);
const targetYear = target.getUTCFullYear();
const targetMonth = target.getUTCMonth() + 1;
const targetDay = target.getUTCDate();
const targetDate = `${targetYear}-${String(targetMonth).padStart(2, '0')}-${String(targetDay).padStart(2, '0')}`;

const recorder = createRecorder();
const passed = [];

let mysqlDefaults = null;
// A MySQL option file interprets backslash escapes, so a password containing \ or "
// reaches the client mangled unless it is quoted and escaped here.
function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'appt-lifecycle-'));
  // A throw between mkdtemp and the assignment below would leave the directory --
  // and possibly a written password file -- with nothing tracking it for cleanup.
  try {
    const file = path.join(dir, 'mysql-defaults.cnf');
    fs.writeFileSync(file, `[client]\npassword=${encodeOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
    mysqlDefaults = { dir, file };
  } catch (error) {
    fs.rmSync(dir, { recursive: true, force: true });
    throw error;
  }
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}
function sqlRows(query) {
  const out = sql(query);
  return out ? out.split('\n').map((line) => line.split('\t')) : [];
}
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function stampedAppointments() {
  const like = escapeSql(`${stamp}%`);
  return sqlRows(
    'SELECT appointment_no, provider_no, appointment_date, start_time, end_time, demographic_no,'
    + ' status, reason, notes FROM appointment'
    + ` WHERE reason LIKE '${like}' OR notes LIKE '${like}' ORDER BY appointment_no`
  ).map(([id, provider, date, startTime, endTime, demographic, status, reason, notes]) => ({
    id, provider, date, startTime, endTime, demographic, status, reason, notes,
  }));
}

function stampedArchiveRows() {
  const like = escapeSql(`${stamp}%`);
  return sqlRows(
    'SELECT appointment_no, status FROM appointmentArchive'
    + ` WHERE reason LIKE '${like}' OR notes LIKE '${like}' ORDER BY id`
  ).map(([id, status]) => ({ id, status }));
}

function cleanupRows() {
  const like = escapeSql(`${stamp}%`);
  sql(`DELETE FROM appointmentArchive WHERE reason LIKE '${like}' OR notes LIKE '${like}'`);
  sql(`DELETE FROM appointment WHERE reason LIKE '${like}' OR notes LIKE '${like}'`);
}

async function waitFor(probe, description, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await probe();
    if (last) {
      return last;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`timed out waiting for ${description}; last value=${JSON.stringify(last)}`);
}

/**
 * (Re)navigates the day sheet to the target date.
 *
 * displaymode/dboperation/viewall are not optional: providercontrol answers a
 * request carrying only year/month/day with HTTP 200 and an EMPTY document, so
 * the full day-search parameter set the post-login landing page uses is what
 * actually renders a schedule. The retry covers the three wordings Playwright
 * uses for the same race — the day sheet navigating itself, either from a
 * popup's self.opener.refresh() or from the status letter's own link.
 */
async function openDaySheet(page) {
  const query = new URLSearchParams({
    year: String(targetYear),
    month: String(targetMonth),
    day: String(targetDay),
    view: '0',
    displaymode: 'day',
    dboperation: 'searchappointmentday',
    viewall: '1',
  });
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await gotoApp(page, config.baseUrl, `/provider/providercontrol?${query.toString()}`);
      await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
      await assertNotErrorPage(page, `day sheet ${targetDate}`);
      return;
    } catch (error) {
      if (attempt === 2
          || !/ERR_ABORTED|frame was detached|interrupted by another navigation/.test(error.message)) {
        throw error;
      }
      await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
      await new Promise((resolve) => setTimeout(resolve, 750));
    }
  }
}

/** Reject an excessive duration before any appointment write, then allow correction. */
async function checkInvalidDuration(popup, button, label) {
  const before = JSON.stringify(stampedAppointments());
  const duration = popup.locator('#duration');
  const original = await duration.inputValue();
  const writes = [];
  const recordWrite = (request) => {
    if (request.method() === 'POST'
        && /\/appointment\/(AddRecord|UpdateRecord)$/.test(new URL(request.url()).pathname)) {
      writes.push(request.url());
    }
  };
  popup.on('request', recordWrite);
  try {
    await duration.fill('7500');
    await button.click();
    await popup.locator('#jsAlertBanner').waitFor({ state: 'visible', timeout: 10000 });
    const message = (await popup.locator('#jsAlertText').innerText()).trim();
    assert(writes.length === 0, `${label}: invalid duration submitted an appointment write`);
    assert(JSON.stringify(stampedAppointments()) === before,
      `${label}: invalid duration changed the appointment rows`);
    assert(/start time/i.test(message) && /duration/i.test(message)
      && /same day/i.test(message) && !/!{2,}/.test(message),
      `${label}: duration feedback must explain the same-day constraint without repeated exclamation marks; got ${JSON.stringify(message)}`);
    pass(`${label}: duration 7500 refused with actionable feedback and no write`);
  } finally {
    popup.off('request', recordWrite);
    if (!popup.isClosed()) await duration.fill(original);
  }
}

/** Books the appointment this check then operates on, from an empty slot link. */
async function bookFromSlot(context, daySheet) {
  // Scoped to the target provider's COLUMN, not just the first slot on the sheet.
  // appointmentprovideradminday.jsp builds its columns from the logged-in provider's
  // group (curProvider_no) and only falls back to the logged-in provider alone when
  // that group is empty, so on a grouped deployment the leftmost slot belongs to
  // whoever sorts first. Each slot's onclick carries the provider it books for; the
  // trailing & keeps provider 999998 from matching 9999988.
  const slots = daySheet.locator(`a.adhour[onclick*="provider_no=${providerNo}&"]`);
  assert(await slots.count() > 0,
    `day sheet for ${targetDate} rendered no bookable slot link in provider ${providerNo}'s column`);

  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  await slots.first().click();
  const popup = await popupPromise;
  // Not every slot prompts; only one whose template carries a confirmation
  // message does. Accept whatever this popup raises during booking.
  wirePage(popup, 'add-appointment', recorder, async (dialog, entry) => {
    recorder.dialogs.push({ ...entry, accepted: true });
    await dialog.accept().catch(() => {});
  });
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'add-appointment popup');

  const surname = sql(`SELECT last_name FROM demographic WHERE demographic_no=${Number(demographicNo)}`);
  assert(surname, `APPOINTMENT_DEMOGRAPHIC_NO=${demographicNo} does not exist`);

  // The patient is chosen through the popup's own keyword search, the way the
  // front desk does it: the search posts the whole half-filled appointment form
  // to DemographicSearch and the results page posts it back, so a field this
  // round trip drops is a booking field an operator silently loses.
  await popup.locator('#keyword').fill(surname);
  await Promise.all([
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#searchBtn').click(),
  ]);
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'appointment patient search results');

  // The result row selects through a submit input whose NAME is demographic_no
  // and whose VALUE is the patient id, not through an anchor, so the click has to
  // land on that button for the id to travel back to the booking form.
  const row = popup.locator(`table tr input[type="submit"][name="demographic_no"][value="${demographicNo}"]`).first();
  await row.waitFor({ state: 'visible', timeout: 30000 });
  await Promise.all([
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    row.click(),
  ]);
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const selected = await popup.locator('#demographic_no').inputValue();
  assert(selected === demographicNo,
    `patient selection put demographic_no=${selected} on the booking form, expected ${demographicNo}`);

  const slotStart = await popup.locator('form#addappt input[name="start_time"]').inputValue();
  assert(/^\d{2}:\d{2}/.test(slotStart),
    `booking form start_time was not prefilled from the slot, got ${slotStart}`);
  await popup.locator('#reason').fill(bookedReason);
  await popup.locator('textarea[name="notes"]').fill(bookedNotes);
  const intendedDuration = Number(await popup.locator('#duration').inputValue());
  assert(Number.isInteger(intendedDuration) && intendedDuration > 0,
    'the selected slot must provide a positive duration');
  await checkInvalidDuration(popup, popup.locator('#addButton'), 'add appointment');

  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && /\/appointment\/AddRecord$/.test(new URL(r.url()).pathname), { timeout: 45000 }),
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#addButton').click(),
  ]);
  assert(response.status() < 400, `appointment AddRecord returned HTTP ${response.status()}`);

  const row2 = await waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 ? rows[0] : null;
  }, `the booked ${stamp} appointment to reach the database`);
  assert(row2.date === targetDate, `booked appointment landed on ${row2.date}, expected ${targetDate}`);
  assert(row2.demographic === demographicNo,
    `booked appointment landed on demographic ${row2.demographic}, expected ${demographicNo}`);
  // The slot was clicked in one provider's column; a booking that lands on a different
  // provider still shows up on the day sheet and would pass every other assertion here.
  assert(row2.provider === providerNo,
    `booked appointment landed on provider ${row2.provider}, expected the ${providerNo} column that was clicked`);
  assert(row2.startTime.startsWith(slotStart.slice(0, 5)),
    `booked appointment start_time ${row2.startTime} did not match the clicked slot ${slotStart}`);
  const startMinutes = Number(slotStart.slice(0, 2)) * 60 + Number(slotStart.slice(3, 5));
  const endMinutes = Number(row2.endTime.slice(0, 2)) * 60 + Number(row2.endTime.slice(3, 5));
  // The schedule stores an inclusive final minute (15 minutes at 08:00 ends at
  // 08:14). Check the saved value, so recovery cannot leave the rejected end time.
  assert(endMinutes - startMinutes === intendedDuration - 1,
    `corrected duration ${intendedDuration} saved end_time ${row2.endTime} for start ${slotStart}`);
  await popup.close().catch(() => {});
  return row2;
}

/** Opens the edit popup from the appointment's own link on the day sheet. */
async function openEditPopup(context, daySheet, appointmentNo, dialogHandler = null) {
  await openDaySheet(daySheet);
  const link = daySheet.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 45000 });
  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  await link.click();
  const popup = await popupPromise;
  wirePage(popup, 'edit-appointment', recorder, dialogHandler);
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'edit-appointment popup');
  const formAppointmentNo = await popup.locator('input[name="appointment_no"]').inputValue();
  assert(formAppointmentNo === String(appointmentNo),
    `edit popup opened appointment ${formAppointmentNo}, expected ${appointmentNo}`);
  return popup;
}

/** Print labels through the appointment's Label link, not a constructed URL. */
async function checkAppointmentLabels(context, daySheet, appointmentNo) {
  const edit = await openEditPopup(context, daySheet, appointmentNo);
  await edit.evaluate(() => {
    const originalOpen = window.open;
    window.open = function (url, name, features) {
      window.__labelWindowFeatures = features;
      return originalOpen.call(this, url, name, features);
    };
  });
  const [labels] = await Promise.all([
    context.waitForEvent('page', { timeout: 45000 }),
    edit.locator('a[onclick*="ViewDemographicLabelPrintSetting"]').click(),
  ]);
  wirePage(labels, 'appointment-labels', recorder);
  await labels.waitForLoadState('domcontentloaded');
  await assertNotErrorPage(labels, 'appointment label settings');
  const features = await edit.evaluate(() => window.__labelWindowFeatures);
  assert(/resizable=yes/.test(features), 'label popup must be resizable');
  assert(Number(/width=(\d+)/.exec(features)?.[1]) >= 900
    && Number(/height=(\d+)/.exec(features)?.[1]) >= 750, 'label popup is too small for its settings');
  const preview = labels.locator('input[type="submit"]');
  assert(await preview.inputValue() === 'Preview/Print', 'label preview action has redundant wording');
  // Exercise explicit offsets independently of installation defaults. Existing
  // clinics may deliberately use zero to match calibrated label stock.
  for (const top of [24, 0]) {
    await labels.locator('input[name="top"]').fill(String(top));
    await labels.locator('input[name="left"]').fill('32');
    for (let n = 1; n <= 5; n++) {
      await labels.locator(`input[name="label${n}checkbox"]`).setChecked(n === 5);
    }
    await labels.locator('input[name="label5no"]').fill('1');
    await Promise.all([
      labels.waitForURL(/ViewDemographicPrintDemographic/, { timeout: 45000 }),
      preview.click(),
    ]);
    await assertNotErrorPage(labels, 'appointment label preview');
    const block = labels.locator('.label-block');
    assert(await block.count() === 1, 'label count/selection was not preserved');
    const position = await block.evaluate((element) => ({ top: element.offsetTop, left: element.offsetLeft }));
    assert(position.top === top && position.left === 32, 'label preview ignored the selected offsets');
    const controls = labels.locator('.print-controls input[type="button"]');
    assert(await controls.count() === 2, 'label preview needs Print and Back controls');
    assert(await controls.nth(0).isVisible() && await controls.nth(1).isVisible(), 'screen controls are hidden');
    const patient = sqlRows(`SELECT first_name, last_name FROM demographic WHERE demographic_no=${demographicNo}`)[0];
    await labels.emulateMedia({ media: 'print' });
    assert(!await controls.nth(0).isVisible() && !await controls.nth(1).isVisible(), 'Print/Back controls must not appear on paper');
    assert(await block.isVisible(), 'print styles also hid the patient label');
    const pdf = await labels.pdf({ format: 'Letter', margin: { top: 0, right: 0, bottom: 0, left: 0 } });
    const text = execFileSync('pdftotext', ['-layout', '-', '-'], { input: pdf, encoding: 'utf8', timeout: 15000 });
    assert(patient.every((name) => text.includes(name)), 'printed label is missing the patient');
    assert(!/\bPrint\b|\bBack\b/.test(text), 'printed PDF contains screen controls');
    await labels.emulateMedia({ media: 'screen' });
    await Promise.all([
      labels.waitForURL(/ViewDemographicLabelPrintSetting/, { timeout: 45000 }),
      controls.nth(1).click(),
    ]);
  }
  await labels.close();
  await edit.close();
  pass('appointment labels print patient data without controls and preserve calibrated offsets');
}

async function submitEdit(popup) {
  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname), { timeout: 45000 }),
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#updateButton').click(),
  ]);
  assert(response.status() < 400, `appointment UpdateRecord returned HTTP ${response.status()}`);
  await assertNotErrorPage(popup, 'appointment update confirmation');
}

/** Exercise the real Update & Receipt popup, including its embedded PDF request. */
async function submitEditWithReceipt(context, popup, appointmentNo) {
  const receiptPromise = context.waitForEvent('page', { timeout: 45000 });
  // Register before the click: the receipt window is reserved synchronously and
  // navigated after UpdateRecord succeeds. Fetching the URL separately would miss
  // CSP blocking the browser's actual embedding request.
  const pdfPromise = context.waitForEvent('response', {
    predicate: (response) => /\/printAppointmentReceiptAction$/.test(new URL(response.url()).pathname)
      && new URL(response.url()).searchParams.get('appointment_no') === String(appointmentNo),
    timeout: 45000,
  });
  receiptPromise.catch(() => {});
  pdfPromise.catch(() => {});
  const [update, receipt] = await Promise.all([
    popup.waitForResponse((response) => response.request().method() === 'POST'
      && /\/appointment\/UpdateRecord$/.test(new URL(response.url()).pathname), { timeout: 45000 }),
    receiptPromise.then((page) => {
      wirePage(page, 'appointment-receipt', recorder);
      return page;
    }),
    popup.locator('#printReceiptButton').click(),
  ]);
  assert(update.status() === 200, `Update & Receipt returned HTTP ${update.status()}`);
  await receipt.waitForURL(/\/appointment\/printappointment/, { timeout: 45000 });
  await assertNotErrorPage(receipt, 'appointment receipt');
  const frame = receipt.locator('iframe#apptpdf');
  await frame.waitFor({ state: 'visible', timeout: 10000 });
  const frameUrl = new URL(await frame.getAttribute('src'), receipt.url());
  assert(frameUrl.origin === new URL(config.baseUrl).origin, 'receipt PDF must stay on the application origin');
  assert(frameUrl.searchParams.get('appointment_no') === String(appointmentNo), 'receipt targets a different appointment');
  assert(await frame.getAttribute('title'), 'receipt iframe needs an accessible title');
  const bounds = await frame.boundingBox();
  assert(bounds && bounds.width >= 200 && bounds.height >= 200, 'receipt viewer has no usable dimensions');
  const link = receipt.locator('#appointmentReceiptPdf');
  assert(await link.isVisible(), 'receipt needs a visible PDF link for browsers without an embedded PDF viewer');
  assert(new URL(await link.getAttribute('href'), receipt.url()).href === frameUrl.href,
    'receipt PDF link and viewer point to different receipts');
  const response = await pdfPromise;
  assert(response.status() === 200, `embedded receipt PDF returned HTTP ${response.status()}`);
  assert(/application\/pdf/i.test(response.headers()['content-type'] || ''), 'receipt response is not PDF');
  // Chromium exposes its internal PDF viewer HTML through response.body().
  // Keep the actual iframe HTTP assertions above, then fetch the same resource
  // with this authenticated browser context to inspect the original PDF bytes.
  const pdf = await context.request.get(frameUrl.href);
  assert(pdf.status() === 200 && /application\/pdf/i.test(pdf.headers()['content-type'] || ''),
    'receipt PDF content request failed');
  const bytes = await pdf.body();
  assert(bytes.subarray(0, 5).toString() === '%PDF-', 'receipt response has no PDF signature');
  const text = execFileSync('pdftotext', ['-layout', '-', '-'], {
    input: bytes, encoding: 'utf8', timeout: 15000, maxBuffer: 1024 * 1024,
  });
  const row = stampedAppointments().find((entry) => entry.id === String(appointmentNo));
  assert(row, 'receipt appointment disappeared');
  const [patient] = sqlRows(`SELECT first_name, last_name FROM demographic WHERE demographic_no=${demographicNo}`);
  for (const expected of [targetDate, row.startTime.slice(0, 5), ...patient]) {
    assert(text.includes(expected), `receipt is missing expected appointment content: ${expected}`);
  }
  assert(text.split(/\W+/).includes(String(appointmentNo)), 'receipt contains no matching appointment ID');
  await receipt.close();
  pass('Update & Receipt displayed its PDF under the application CSP with correct patient, date, time and appointment ID');
}

async function editAppointment(context, daySheet, appointmentNo) {
  const popup = await openEditPopup(context, daySheet, appointmentNo);
  const prefilled = await popup.locator('#reason').inputValue();
  assert(prefilled === bookedReason,
    `edit popup prefilled reason ${prefilled}, expected the booked ${bookedReason}`);

  await checkInvalidDuration(popup, popup.locator('#updateButton'), 'edit appointment');
  await popup.locator('#reason').fill(editedReason);
  await popup.locator('textarea[name="notes"]').fill(editedNotes);
  await popup.locator('#duration').fill('30');
  await submitEditWithReceipt(context, popup, appointmentNo);
  await popup.close().catch(() => {});

  const row = await waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 && rows[0].reason === editedReason ? rows[0] : null;
  }, 'the edited reason to reach the database');
  assert(row.notes === editedNotes, `edited appointment notes were ${row.notes}`);
  assert(row.id === String(appointmentNo),
    `the edit created appointment ${row.id} instead of updating ${appointmentNo}`);

  // duration=30 must move end_time. An edit that persisted the text fields but
  // dropped the recomputed end leaves a booking that looks right on the form and
  // wrong on the schedule.
  const startMinutes = Number(row.startTime.slice(0, 2)) * 60 + Number(row.startTime.slice(3, 5));
  const endMinutes = Number(row.endTime.slice(0, 2)) * 60 + Number(row.endTime.slice(3, 5));
  assert(endMinutes - startMinutes >= 29,
    `duration=30 left end_time ${row.endTime} only ${endMinutes - startMinutes} minutes after ${row.startTime}`);
  return row;
}

/**
 * Advances the status from the day sheet's status letter.
 *
 * This is the one status path with no form behind it: the letter is a link that
 * self-navigates the schedule with dboperation=updateapptstatus, so it breaks
 * independently of the edit popup's status field.
 */
async function rotateStatus(daySheet, appointmentNo, statusBefore) {
  await openDaySheet(daySheet);
  const statusLink = daySheet.locator(`a.apptStatus[onclick*="appointment_no=${appointmentNo}"]`).first();
  // A missing status letter is a regression in the day sheet, not a configuration
  // choice to skip past: every status in the demo set has a nextStatus, so the only
  // way the link disappears is the schedule failing to render it.
  assert(await statusLink.count() > 0,
    `the day sheet rendered no a.apptStatus rotation link for appointment ${appointmentNo};`
    + ` the status letter is the only no-form status path and it is gone (status before: ${statusBefore})`);
  await Promise.all([
    daySheet.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    statusLink.click(),
  ]);
  await daySheet.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(daySheet, 'day sheet after status rotation');
  await daySheet.waitForLoadState('load', { timeout: 20000 }).catch(() => {});

  return waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 && rows[0].status !== statusBefore ? rows[0] : null;
  }, `appointment status to advance away from ${statusBefore}`);
}

async function cancelAppointment(context, daySheet, appointmentNo) {
  const popup = await openEditPopup(context, daySheet, appointmentNo);
  // The status field has two shapes and a deployment property decides which:
  // editappointment.jsp builds the <select> only when ENABLE_EDIT_APPT_STATUS=yes,
  // and otherwise presents status as free text that AppointmentUpdateRecord2Action
  // persists verbatim. Both are driven so the check works either way.
  const statusSelect = popup.locator('select[name="status"]');
  const statusText = popup.locator('input[type="text"][name="status"]');
  if (await statusSelect.count() > 0) {
    const options = await statusSelect.locator('option').evaluateAll((nodes) => nodes.map((n) => n.value));
    const cancelled = options.find((value) => value === 'C' || value.startsWith('C'));
    assert(cancelled, `edit popup status select offered no Cancelled option: ${JSON.stringify(options)}`);
    const current = await statusSelect.inputValue();
    // A status outside the configured list leaves editappointment.jsp's curSelect
    // at -1; assert the current one IS in the list so a failure here names the
    // cause instead of surfacing later as a 500.
    assert(options.includes(current),
      `edit popup pre-selected status ${current} is not among ${JSON.stringify(options)}`);
    await statusSelect.selectOption(cancelled);
  } else {
    assert(await statusText.count() > 0, 'edit popup rendered neither a status select nor a status text field');
    await statusText.fill('C');
  }
  await submitEdit(popup);
  await popup.close().catch(() => {});

  return waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 && rows[0].status === 'C' ? rows[0] : null;
  }, 'appointment status to become Cancelled (C)');
}

async function deleteAppointment(context, daySheet, appointmentNo) {
  // The delete confirm is raised by the form's own onSubmit, not by the click, so
  // the handler has to be installed with the popup: dismissing it cancels the
  // submit and no POST is ever made.
  let confirmText = null;
  const popup = await openEditPopup(context, daySheet, appointmentNo, async (dialog, entry) => {
    confirmText = dialog.message();
    recorder.dialogs.push({ ...entry, accepted: true });
    await dialog.accept().catch(() => {});
  });

  const deleteButton = popup.locator('#deleteButton');
  assert(await deleteButton.count() > 0, 'edit popup rendered no delete control');
  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && /\/appointment\/DeleteRecord$/.test(new URL(r.url()).pathname), { timeout: 45000 }),
    deleteButton.click(),
  ]);
  assert(response.status() < 400, `appointment DeleteRecord returned HTTP ${response.status()}`);
  assert(confirmText && /delete/i.test(confirmText),
    `appointment delete raised an unexpected confirmation: ${confirmText}`);
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(popup, 'appointment delete confirmation');
  await popup.close().catch(() => {});

  await waitFor(() => (stampedAppointments().length === 0 ? true : null),
    'the deleted appointment row to disappear');

  const archived = stampedArchiveRows();
  assert(archived.length >= 1,
    'appointment delete removed the appointment without writing an appointmentArchive row');
  assert(archived.some((entry) => entry.id === String(appointmentNo)),
    `appointmentArchive has no row for deleted appointment ${appointmentNo}: ${JSON.stringify(archived)}`);
}

(async () => {
  initMysqlDefaults();
  let browser = null;
  let context = null;
  // Setup runs inside the try so a failure before the browser opens still reaches the
  // finally: initMysqlDefaults has already written a 0600 file holding the database
  // password, and a throw here used to leave it on disk for the life of the host.
  try {
    cleanupRows();

    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    context = await browser.newContext({ ignoreHTTPSErrors: true });
    // appointmentaddarecord.jsp calls self.close() on a SUCCESSFUL add, tearing the
    // confirmation down before it can be read. Neutralising close keeps the page
    // inspectable; the flag it sets is what the booking asserts instead.
    await context.addInitScript(() => {
      window.close = () => {
        window.__carlosSelfCloseRequested = true;
      };
    });

    await login(context, config, recorder);

    const daySheet = await context.newPage();
    wirePage(daySheet, 'day-sheet', recorder);
    await openDaySheet(daySheet);
    pass(`day sheet renders bookable slots for ${targetDate}`);

    const booked = await bookFromSlot(context, daySheet);
    pass(`booked appointment ${booked.id} from a day-sheet slot for demographic ${demographicNo}`);

    await checkAppointmentLabels(context, daySheet, booked.id);

    const edited = await editAppointment(context, daySheet, booked.id);
    pass(`edit persisted reason, notes and the recomputed end_time (${edited.startTime}-${edited.endTime})`);

    const rotated = await rotateStatus(daySheet, booked.id, edited.status);
    pass(`day sheet status letter advanced the status ${edited.status} -> ${rotated.status}`);

    const cancelled = await cancelAppointment(context, daySheet, booked.id);
    pass(`edit popup cancelled the appointment (status=${cancelled.status}) without deleting it`);

    await deleteAppointment(context, daySheet, booked.id);
    pass('delete removed the appointment and left an appointmentArchive record behind');

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0,
      `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0,
      `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`\nPASS appointment lifecycle: ${passed.length} checks, 0 failures`);
  } catch (error) {
    console.error(`FAIL appointment lifecycle: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (context) await context.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
    try {
      cleanupRows();
    } finally {
      cleanupMysqlDefaults();
    }
  }
})();
