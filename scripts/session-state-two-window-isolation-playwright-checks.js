#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Two patients' windows in one login keep their own working state (issue #4673).
 *
 * A clinician keeps several windows of one login open at once, for different patients. State one
 * window left in the login's session under a login-wide name used to be read by a request from
 * another window, which then displayed, printed, linked, sent or saved the other patient's data.
 * echart-two-patient-isolation drives the chart itself; this check drives the other windows the
 * fix covers, each right after the other patient's window wrote its own state:
 *
 *   1. Schedule ▸ M on A's appointment, then on B's ▸ Health Tracker ▸ Save All for A, then for B,
 *      then M on A's again and Save All for B: every measurement and its progress note is linked
 *      to its own patient's appointment. The day sheet kept one "current appointment" per login;
 *      ViewStoreApptInSession now files it per patient, and refuses a store that names no valid
 *      appointment (400) or comes by GET (405).
 *   2. E-Chart ▸ Messenger + for A and for B ▸ Attach Patient ▸ patient information ▸ Attach in
 *      both compose windows, then Send Message from each: each message carries only the PDF
 *      rendered for its own patient. Compose attachments lived on the login's messenger bean;
 *      each compose window now stages its own (MsgAttachmentStaging).
 *   3. Two received messages with chart items, each in its own message view ▸ Attachment ▸ Save
 *      Attachments; then the first view ▸ Search Patient ▸ pick A ▸ Link to Patient (which shows
 *      the view again) ▸ Forward, and the second view ▸ Reply, each ▸ Send Message: each new
 *      message forwards only its own view's items, and the forward is linked to A.
 *   4. Administration ▸ Billing ▸ End Year Statement for A and for B in two windows ▸ Print PDF
 *      in each, A's after its start date was changed without creating the statement again: each
 *      PDF is the statement on screen, with its own patient and invoice. The PDF printed the
 *      statement the last search in any window had left in the session.
 *   5. Report ▸ Waiting List showing a list that holds B, then a booking for A, who is on no list:
 *      the booked page must not offer to take A off that list; a booking for B still offers it.
 *      The booked page read the list name through EL, which fell back to the name the
 *      waiting-list page had left in the session.
 *   6. E-Chart ▸ Disease Registry for A and for B ▸ an unknown code in A's window: the refusal
 *      re-renders A's own diagnoses, and A's next code is registered for A only. The re-render
 *      read the registry opened last.
 *   7. Two compose windows, each with its own patient's PDF attached ▸ Clear New Message in A's:
 *      B's message still carries B's PDF and A's fresh page sends nothing. Clear emptied the
 *      login's messenger bean, so it also dropped every other compose window's attachments.
 *   8. A compose window for B open ▸ the transfer page's post (messenger/Transfer/PostItems, with
 *      A's items): the compose window the transfer opens carries them, and B's message carries none.
 *      The transfer left its items on the login's messenger bean for whichever window sent next.
 *      (The transfer page itself cannot render on any install; the step posts its form.)
 *   9. A received message linked to A ▸ Attachment ▸ Save Attachments ▸ Reply in its view,
 *      Forward in a second view of it and Compose in a third ▸ Attach Patient in the reply only ▸
 *      Send Message from the forward, the new message, then the reply: all three carry the saved
 *      items, and only the reply carries A's PDF; a reply opened afterwards starts without the
 *      items, as sending cleared them before. Every compose window opened from one message shared
 *      that message's key, so the forward sent the chart PDF attached in the reply.
 *  10. The PDF list (messenger/ViewPDFAttach) of A's sent message, then of B's, each from its own
 *      message view ▸ download in A's list, then in B's: each downloads its own message's PDF, byte
 *      for byte. A malformed index is refused (400); an index the message does not have, or no
 *      message, finds nothing (404). The download read the list that the last list opened in any
 *      window had left in the session (#4473).
 *  11. A's compose window with A's PDF attached ▸ Search Patient ▸ pick B ▸ Send Message, then
 *      Clear Selected Patient ▸ Send Message: each send is refused with its reason, and the page
 *      keeps the subject, the body and the attachment; linked to A again, the message goes out
 *      with A's PDF, linked to A. The PDF went out on whichever patient the message was linked to.
 *  12. B's compose window with B's PDF attached ▸ the inbox, A's message list and B's message list
 *      (messenger/DisplayDemographicMessages), each in a window of its own ▸ Send Message: B's
 *      message carries B's PDF. Each list shows only its own patient's messages, and a list that
 *      names no patient shows none. Opening the inbox or a message list emptied the login's
 *      messenger bean, so a compose window open meanwhile sent nothing.
 *  13. A's compose window (E-Chart ▸ Messenger +) and a second one in the same group of windows
 *      (Msg ▸ Compose, linked to B) ▸ Attach Patient in both before either attaches ▸ Attach in B's
 *      chooser, then in A's: each chooser is a window of its own, and each message carries only its
 *      own patient's PDF. The choosers shared one window name, so the second took over the first.
 *  14. A's compose window with A's PDF attached ▸ eight transfer posts, each opening a compose window
 *      of its own (the session keeps eight compose windows' attachments) ▸ Send Message in A's: it
 *      is refused once, saying its attachment was released, and sending again goes out without it.
 *      Without the notice, it went out without its attachment, unannounced.
 *
 * Fixtures: the workflow's FAKE patient (A, first name PatientAlpha) and a second FAKE patient (B,
 * PatientBravo, the same marker last name); one appointment today per patient and the bookings
 * this check makes; a Health Tracker item per patient; an owned messenger group holding the test
 * provider, the messages sent to it and three delivered messages with chart items, one linked to
 * A; one PAT bill per patient; an owned waiting list holding B; one registry code per patient.
 * Cleanup removes every owned row by key and asserts it gone.
 *
 * Not driven here: the BC bill and invoice receipt (BC installs only; billing-bc-two-window-isolation
 * drives them), the PMM "attach note to client" popup (pmm-attach-note-two-window-isolation drives
 * it) and the prescription pages (rx-drug-pick-patient-isolation, rx-stash-patient-isolation and the
 * other Rx checks with DrugRef; rx-preview-visit-site-isolation, the multisite print window, without).
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { statementInvoiceRows } = require('./lib/end-year-statement-pdf');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { seedOwnedBill } = require('./billing-on-invoice-third-party-playwright-checks');
const { pdfTextBuffer, requirePoppler } = require('./lib/export-content-helpers');

const TIMEOUT = 30000;
const A_FIRST_NAME = 'PatientAlpha';
const B_FIRST_NAME = 'PatientBravo';
const TRACKER_TYPE = 'WT';
const q = h.sqlString;

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/** The second patient, owned by this run; removed last, after every child row. */
function createSecondPatient(s) {
  const { sql, marker, provider } = s;
  const patient = h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status,
      lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR',
      NOW())`, 'second patient');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(marker)}`) === '1', 'The second patient fixture changed owner');
    const support = [
      ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
      ['measurementsDeleted', 'demographicNo'], ['demographicExt', 'demographic_no'],
      ['demographicArchive', 'demographic_no'],
    ];
    sql.execute(support.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${support.map(([table, column]) => `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient})`)
      .join(' + ')}`) === '0', 'The second patient\'s locks, drafts, deleted measurements or archive rows were not removed');
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
      'The second patient was not removed');
  });
  return patient;
}

/** Notes and measurements the Health Tracker and the charts write for the two patients. */
function ownChartRows(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${list})`)
      .map(([id]) => key(id, 'note'));
    if (notes.length) {
      const ids = notes.join(',');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${ids});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${ids});
        DELETE FROM casemgmt_note_link WHERE note_id IN (${ids});
        DELETE FROM casemgmt_note WHERE note_id IN (${ids}) AND demographic_no IN (${list})`);
    }
    sql.execute(`DELETE FROM measurements WHERE demographicNo IN (${list});
      DELETE FROM flowsheet_customization WHERE demographic_no IN (${patients.map(p => q(p)).join(',')})`);
    const children = notes.length ? ['casemgmt_issue_notes', 'casemgmt_note_ext', 'casemgmt_note_link']
      .map(table => ` + (SELECT COUNT(*) FROM ${table} WHERE note_id IN (${notes.join(',')}))`).join('') : '';
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo IN (${list}))
      + (SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no IN (${patients.map(p => q(p)).join(',')}))${children}`) === '0',
    'Owned notes, their links, measurements or tracker items were not removed');
  });
}

/** Every appointment of the two patients: the day-sheet visits and the bookings this check makes. */
function ownAppointments(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no IN (${list});
      DELETE FROM appointment WHERE demographic_no IN (${list})`);
    // An appointment left behind for a removed patient breaks the whole day sheet.
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${list}))`) === '0',
    'Owned appointments were not removed');
  });
}

/**
 * One step whose windows this check navigates and closes as a user would: a request a window
 * abandoned because its own document went away (navigatedAway, the harness's proof) is the
 * browser's doing, not a finding. Every other failure still fails the step.
 */
async function step(s, label, body) {
  await s.step(label, async () => {
    const since = s.recorder.requestFailures.length;
    await body();
    for (let i = s.recorder.requestFailures.length - 1; i >= since; i--) {
      const entry = s.recorder.requestFailures[i];
      if (/ERR_ABORTED/.test(entry.errorText || '') && typeof entry.navigatedAway === 'function'
        && entry.navigatedAway()) {
        s.recorder.requestFailures.splice(i, 1);
      }
    }
  });
}

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

async function openPage(s, path, label) {
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, path);
  await settle(page, label);
  return page;
}

async function healthTrackerVisits(s, A, B) {
  const { sql, marker, provider, context } = s;
  const day = sql.value('SELECT CURDATE()');
  const [year, month, dayOfMonth] = day.split('-');
  const appointmentOf = {};
  for (const [label, patient, first, start, end] of [
    ['A', A, A_FIRST_NAME, '09:00:00', '09:14:00'], ['B', B, B_FIRST_NAME, '09:15:00', '09:29:00']]) {
    appointmentOf[label] = h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time,
        end_time, name, demographic_no, notes, reason, status, createdatetime, updatedatetime, creator,
        lastupdateuser)
      VALUES (${q(provider)}, ${q(day)}, '${start}', '${end}', ${q(`${marker},${first}`)}, ${patient},
        ${q(marker)}, ${q(marker)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment');
  }
  // The tracker ships empty; one weight item per patient, the way Edit Flowsheet adds it.
  const item = `<item measurement_type="${TRACKER_TYPE}" display_name="Weight kg" guideline="" graphable="yes" value_name="Weight" />`;
  for (const patient of [A, B]) {
    sql.execute(`INSERT INTO flowsheet_customization (flowsheet, action, measurement, payload, provider_no,
        demographic_no, create_date, archived)
      VALUES ('tracker', 'add', NULL, ${q(item)}, ${q(provider)}, ${q(patient)}, NOW(), 0)`);
  }

  // The schedule the login landed on, shown for today with every column, as after a refresh.
  const daySheet = s.schedule;
  const query = new URLSearchParams({ year, month: String(Number(month)), day: String(Number(dayOfMonth)),
    view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' });

  /** Schedule ▸ M: opens the Master Record and records the appointment as the patient's visit. */
  async function openFromDaySheet(label, patient) {
    const link = daySheet.locator(`a.masterBtn[onclick*="appointment=${appointmentOf[label]}'"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    const [stored, master] = await Promise.all([
      daySheet.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/provider/ViewStoreApptInSession'), { timeout: TIMEOUT }),
      context.waitForEvent('page', { timeout: TIMEOUT }),
      link.click(),
    ]);
    h.assert(stored.status() === 204 && new URLSearchParams(stored.request().postData() || '')
      .get('appointment_no') === appointmentOf[label],
    `The day sheet did not record ${label}'s appointment (HTTP ${stored.status()})`);
    await master.waitForURL(url => url.pathname.endsWith('/demographic/DemographicEdit'), { timeout: TIMEOUT });
    await settle(master, `${label}'s Master Record from the day sheet`);
    h.assert(new URL(master.url()).searchParams.get('demographic_no') === patient,
      `The day sheet's M link opened another patient than ${label}`);
    await master.close();
  }

  /** Health Tracker ▸ weight ▸ Save All, in a window of its own. */
  async function saveTracker(label, patient, value) {
    const tracker = await openPage(s,
      `/encounter/oscarMeasurements/ViewHealthTracker?demographic_no=${patient}&template=tracker`,
      `${label}'s Health Tracker`);
    await tracker.locator(`#trackerForm input[name="${TRACKER_TYPE}"]`).fill(value);
    // "Add to note": the save also files a progress note, linked to the visit like the measurement.
    await tracker.locator(`#trackerForm input[name="${TRACKER_TYPE}_note"]`).check();
    const [saved] = await Promise.all([
      tracker.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/encounter/oscarMeasurements/HealthTrackerUpdate'), { timeout: TIMEOUT }),
      tracker.locator('button[form="trackerForm"][value="Save All"]').click(),
    ]);
    h.assert(saved.status() === 302, `${label}'s Health Tracker save answered HTTP ${saved.status()}`);
    await settle(tracker, `${label}'s Health Tracker after Save All`);
    await tracker.close();
    await expectValue(sql, `SELECT appointmentNo FROM measurements WHERE demographicNo=${patient}
      AND type=${q(TRACKER_TYPE)} AND dataField=${q(value)}`, appointmentOf[label],
    `${label}'s Health Tracker measurement ${value} was linked to another visit than ${label}'s own`);
    await expectValue(sql, `SELECT appointmentNo FROM casemgmt_note WHERE demographic_no=${patient}
      AND note LIKE ${q(`%${value}%`)} ORDER BY note_id DESC LIMIT 1`, appointmentOf[label],
    `${label}'s Health Tracker progress note for ${value} was linked to another visit than ${label}'s own`);
  }

  await step(s, 'a Health Tracker save links each patient\'s measurement and note to that patient\'s own visit', async () => {
    await h.gotoApp(daySheet, s.config.baseUrl, `/provider/providercontrol?${query}`);
    await settle(daySheet, 'day sheet');
    await openFromDaySheet('A', A);
    await openFromDaySheet('B', B);
    // A's visit was opened first and B's last: A's save must still name A's visit.
    await saveTracker('A', A, '61.2');
    await saveTracker('B', B, '87.4');
    // And the other way round: A's visit opened last, B's save names B's visit.
    await openFromDaySheet('A', A);
    await saveTracker('B', B, '88.1');
    // The store takes a POST naming one appointment; anything else is refused and files nothing,
    // so B's next save still names B's visit.
    const token = await ui.csrfTokenPresent(daySheet);
    const store = h.appUrl(s.config.baseUrl, '/provider/ViewStoreApptInSession');
    for (const [label, fields] of [['no appointment', []], ['a malformed one', [['appointment_no', '12x']]],
      ['appointment 0', [['appointment_no', '0']]]]) {
      const refused = await context.request.post(store, {
        data: new URLSearchParams([...fields, ['CSRF-TOKEN', token]]).toString(),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token }, maxRedirects: 0,
        failOnStatusCode: false });
      try {
        h.assert(refused.status() === 400, `The day sheet's visit store with ${label} answered HTTP ${refused.status()}, not 400`);
      } finally { await refused.dispose(); }
    }
    const read = await context.request.get(`${store}?appointment_no=${appointmentOf.A}`, { maxRedirects: 0, failOnStatusCode: false });
    try {
      h.assert(read.status() === 405, `The day sheet's visit store answered a GET with HTTP ${read.status()}, not 405`);
    } finally { await read.dispose(); }
    await saveTracker('B', B, '88.6');
  });

  return appointmentOf;
}

/** An owned messenger group holding the test provider, the recipient of every message sent here. */
function ownedMessengerGroup(s) {
  const { sql, marker, provider } = s;
  const like = q(`%${marker}%`);
  // Rows above these marks are this run's: demo installs ship orphan delivery rows.
  const listMark = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM messagelisttbl'));
  const demoMapMark = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM msgDemoMap'));
  const groupId = key(sql.value(`INSERT INTO groups_tbl(parentID, groupDesc) VALUES(0, ${q(marker)});
    SELECT LAST_INSERT_ID()`), 'messenger group');
  s.cleanup(() => {
    const ids = sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${like}`).map(([id]) => key(id, 'message'));
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list}) AND id > ${demoMapMark};
        DELETE FROM messagelisttbl WHERE message IN (${list}) AND id > ${listMark};
        DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${like}`);
    }
    sql.execute(`DELETE FROM groupMembers_tbl WHERE groupID=${groupId} AND provider_No=${q(provider)};
      DELETE FROM groups_tbl WHERE groupID=${groupId} AND groupDesc=${q(marker)}`);
    // Delivery and patient-link rows of the owned messages, above the marks (the demo's orphans stay).
    const deliveries = ids.length ? ` + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${ids.join(',')})
      AND id > ${demoMapMark}) + (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${ids.join(',')}) AND id > ${listMark})` : '';
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${like})
      + (SELECT COUNT(*) FROM groups_tbl WHERE groupID=${groupId})
      + (SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${groupId})${deliveries}`) === '0',
    'Owned messages, their deliveries or the owned messenger group were not removed');
  });
  sql.execute(`INSERT INTO groupMembers_tbl(groupID, provider_No, facilityId) VALUES(${groupId}, ${q(provider)}, 0)`);
  return { groupId, demoMapMark };
}

async function sendToSelf(s, page, groupId, subject) {
  await page.locator(`#member_group_${groupId}`).check();
  await page.locator('#subject').fill(subject);
  await page.locator('.toastui-editor-ww-container .ProseMirror').first().click();
  await page.keyboard.type(`${s.marker} body`);
  const [sent] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: TIMEOUT }),
    page.locator('button[type="submit"]', { hasText: /Send Message/i }).click(),
  ]);
  h.assert(sent.status() < 400, `Send Message answered HTTP ${sent.status()}`);
  await expectValue(s.sql, `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${q(subject)}`, '1',
    `Send Message did not store "${subject}" once`);
  return key(s.sql.value(`SELECT messageid FROM messagetbl WHERE thesubject=${q(subject)}`), 'message');
}

/** The text of every chart PDF stored on one message, how many there are, and their bytes. */
function storedPdfs(s, messageId) {
  const stored = s.sql.value(`SELECT CAST(pdfattachment AS CHAR) FROM messagetbl WHERE messageid=${messageId}`) || '';
  const entries = stored.match(/<PDF>.*?<\/PDF>/gs) || [];
  const bytes = entries.map(entry => Buffer.from((entry.match(/<CONTENT>([^<]*)<\/CONTENT>/) || [])[1] || '', 'base64'));
  return { count: entries.length, text: bytes.map(pdf => pdfTextBuffer(pdf)).join('\n'), bytes };
}

/** E-Chart ▸ Messenger + ▸ Attach Patient ▸ patient information ▸ Attach, in the patient's own chart. */
async function composeWithPatientPdf(s, chart, label, patient) {
  const compose = await openCompose(s, chart, label);
  await attachPatientPdf(s, compose, label, patient);
  return compose;
}

/** Attach Patient ▸ patient information ▸ Attach, in a compose window linked to the patient. */
async function attachPatientPdf(s, compose, label, patient) {
  await attachInChooser(await openChooser(s, compose, label, patient), compose);
}

/** Attach Patient, in a compose window linked to the patient: the chooser window it opens. */
async function openChooser(s, compose, label, patient) {
  h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === String(patient),
    `${label}'s compose window is not linked to its patient`);
  const [chooser] = await Promise.all([
    s.context.waitForEvent('page', { timeout: TIMEOUT }).catch(() => null),
    compose.locator('input[name="attachDemo"]').click(),
  ]);
  h.assert(chooser, `${label}'s Attach Patient opened no attachment chooser window of its own`);
  await chooser.waitForURL(url => url.pathname.endsWith('/messenger/attachmentFrameset'),
    { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
  let main;
  const deadline = Date.now() + TIMEOUT;
  while (!(main = chooser.frame({ name: 'main' })) && Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  h.assert(main, `${label}'s attachment chooser has no main frame`);
  await main.locator('form[action$="/messenger/Doc2PDF"]').waitFor({ timeout: TIMEOUT });
  h.assert(await main.locator('input[name="demographic_no"]').inputValue() === String(patient),
    `${label}'s attachment chooser is for another patient`);
  return { chooser, main };
}

/** Patient information ▸ Attach, in an open chooser; the compose window that opened it says so. */
async function attachInChooser({ chooser, main }, compose) {
  await main.locator('input[name="item"][value="demographic"]').check();
  // The chooser closes itself once the PDF is attached (its own late loads are abandoned).
  await Promise.all([
    chooser.waitForEvent('close', { timeout: 60000 }),
    main.locator('button[name="Attach"]').click(),
  ]);
  await compose.locator('#pdf-attachment-indicator').waitFor({ state: 'visible', timeout: TIMEOUT });
}

/** Search Patient ▸ pick, in a compose window: links the message it sends to `patient`. */
async function linkComposeTo(s, compose, label, patient) {
  // Typing a name clears the patient picked before, as the page's own name lookup does.
  await compose.locator('#keyword').fill(s.marker);
  const search = await s.popup(compose, compose.locator('input[name="searchDemo"]'), `compose-search-${label}`);
  const closed = search.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
  const pick = search.locator(`#patientResults a[onclick*="selectForMessenger"][onclick*="'${patient}'"]`);
  await pick.waitFor({ timeout: TIMEOUT });
  await pick.click();
  h.assert(await closed, `Picking the patient for ${label} did not close the search popup`);
  h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === String(patient),
    `Picking the patient did not link ${label}'s compose window to it`);
}

/** What the compose page's alert says when Send Message was refused (CreateMessage.jsp showAlert). */
async function refusalOf(page) {
  const alert = page.locator('#msg-alert-container .alert-danger').first();
  await alert.waitFor({ timeout: TIMEOUT });
  return (await alert.innerText()).trim();
}

/**
 * Send Message that must be refused: the compose page comes back saying why, with the subject and
 * the body typed in it, and nothing is stored.
 */
async function sendRefused(s, page, groupId, subject, reason, label) {
  const body = `${s.marker} ${label} body`;
  await page.locator(`#member_group_${groupId}`).check();
  await page.locator('#subject').fill(subject);
  await page.locator('.toastui-editor-ww-container .ProseMirror').first().click();
  await page.keyboard.type(body);
  const [answer] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: TIMEOUT }),
    page.locator('button[type="submit"]', { hasText: /Send Message/i }).click(),
  ]);
  h.assert(answer.status() === 200, `${label}: Send Message answered HTTP ${answer.status()}, not the compose page`);
  await settle(page, `${label}'s refused compose page`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject=${q(subject)}`) === '0',
    `${label}: the message was sent instead of refused`);
  h.assert(reason.test(await refusalOf(page)),
    `${label}: the compose page came back without saying why the message was not sent`);
  h.assert(await page.locator('#subject').inputValue() === subject, `${label}: the refused message lost its subject`);
  h.assert((await page.locator('textarea[name="message"]').inputValue()).includes(body),
    `${label}: the refused message lost its body`);
}

/**
 * The transfer page's post (messenger/Transfer/PostItems) with one item, from `page`. The transfer
 * page itself (messenger/Transfer/SelectItems) cannot render: it reads /DocXferConfig.xml from the
 * server's filesystem root, which no install has, so it fails for every patient. Its form posts
 * xmlDoc (the patient's document, Base64) and item<N>=on for each ticked item to PostItems, which
 * is live. The same form is built and submitted here, from a messenger page, so CSRFGuard adds its
 * token as it does for the page's own form. The post opens a compose window of its own in `page`.
 */
async function postTransfer(page, content) {
  const previous = new URL(page.url()).searchParams.get('attachmentKey');
  const xmlDoc = Buffer.from(`<root><table name="Demographics"><item itemId="1" removable="true">`
    + `<content>${content}</content></item></table></root>`).toString('base64');
  await Promise.all([
    page.waitForURL(url => url.pathname.endsWith('/messenger/ViewCreateMessage')
      && url.searchParams.get('attachmentKey') !== previous, { timeout: TIMEOUT }),
    page.evaluate(({ action, xmlDoc }) => {
      const form = document.createElement('form');
      form.method = 'post';
      form.action = action;
      for (const [name, value] of [['xmlDoc', xmlDoc], ['item1', 'on']]) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
      }
      document.body.appendChild(form);
      form.submit();
    }, { action: `${new URL(page.url()).pathname.replace(/\/messenger\/.*$/, '')}/messenger/Transfer/PostItems`, xmlDoc }),
  ]);
  await settle(page, 'the compose window the transfer opened');
  h.assert(/^[A-Za-z0-9_-]{16,}$/.test(new URL(page.url()).searchParams.get('attachmentKey') || ''),
    'The transfer did not open a compose window of its own (no attachment key)');
}

async function composeAttachments(s, A, B, messenger) {
  const { context, recorder, marker } = s;
  const chartOf = {};
  async function openCharts() {
    chartOf.A = await s.chart();
    // A second schedule tab, as a clinician opens one: its popups do not re-target A's windows.
    const second = await context.newPage();
    await h.gotoApp(second, s.config.baseUrl, new URL(s.schedule.url()).pathname.replace(/^\/[^/]+/, '')
      + new URL(s.schedule.url()).search);
    await settle(second, 'second schedule tab');
    const { masterPage: masterB } = await openMasterRecord(context, second, recorder, {
      searchTerm: marker, preferredDemographicNo: B, timeout: TIMEOUT });
    chartOf.B = await openChart(context, masterB, recorder, TIMEOUT);
    await waitForNavbars(chartOf.B, TIMEOUT);
  }


  await step(s, 'each compose window sends only the chart PDF attached in it, linked to its own patient', async () => {
    await openCharts();
    const composeA = await composeWithPatientPdf(s, chartOf.A, 'A', A);
    // B's compose window attaches B's chart after A's has attached A's.
    const composeB = await composeWithPatientPdf(s, chartOf.B, 'B', B);
    for (const [label, patient, compose, own, other] of [
      ['A', A, composeA, A_FIRST_NAME, B_FIRST_NAME], ['B', B, composeB, B_FIRST_NAME, A_FIRST_NAME]]) {
      const id = await sendToSelf(s, compose, messenger.groupId, `${marker} ${label} chart PDF`);
      const pdfs = storedPdfs(s, id);
      h.assert(pdfs.count === 1, `${label}'s message stored ${pdfs.count} chart PDFs instead of its own one`);
      h.assert(pdfs.text.includes(own) && !pdfs.text.includes(other),
        `${label}'s message carries ${pdfs.text.includes(other) ? 'the other patient\'s' : 'no'} patient information PDF`);
      h.assert(s.sql.value(`SELECT GROUP_CONCAT(demographic_no) FROM msgDemoMap WHERE messageID=${id}
        AND id > ${messenger.demoMapMark}`) === patient, `${label}'s message is not linked to ${label} alone`);
      await compose.close();
    }
  });
  return chartOf;
}

/** What the compose page says when the window has attachments staged (CreateMessage.jsp). */
const ATTACHMENTS_LABEL = 'Message has attachments!';

/** The chart items XML stored on one message ("" when it carries none). */
function storedItems(s, messageId) {
  return s.sql.value(`SELECT IFNULL(attachment, '') FROM messagetbl WHERE messageid=${messageId}`) || '';
}

async function clearNewMessage(s, A, B, messenger, chartOf) {
  const { marker } = s;
  await step(s, 'Clear New Message in one compose window leaves the other compose window\'s attachment', async () => {
    const composeA = await composeWithPatientPdf(s, chartOf.A, 'A', A);
    const composeB = await composeWithPatientPdf(s, chartOf.B, 'B', B);
    const clear = composeA.locator('a[href*="/messenger/ClearMessage?attachmentKey="]');
    h.assert(await clear.count() === 1, 'A\'s compose window offers no Clear New Message of its own');
    await Promise.all([
      composeA.waitForURL(url => url.pathname.endsWith('/messenger/ClearMessage'), { timeout: TIMEOUT }),
      clear.click(),
    ]);
    await settle(composeA, 'A\'s cleared compose window');
    h.assert(!(await composeA.locator('body').innerText()).includes(ATTACHMENTS_LABEL),
      'A\'s cleared compose window still says it has attachments');
    // B's window, whose attachment A's Clear must not touch, sends first.
    const idB = await sendToSelf(s, composeB, messenger.groupId, `${marker} B after A cleared`);
    const pdfsB = storedPdfs(s, idB);
    h.assert(pdfsB.count === 1 && pdfsB.text.includes(B_FIRST_NAME),
      `Clearing A's compose window left B's message with ${pdfsB.count} chart PDFs instead of B's own one`);
    const idA = await sendToSelf(s, composeA, messenger.groupId, `${marker} A after its Clear`);
    h.assert(storedPdfs(s, idA).count === 0 && storedItems(s, idA) === '',
      'A\'s cleared compose window still sent an attachment');
    await composeA.close();
    await composeB.close();
  });
}

async function transferredItems(s, A, B, messenger, chartOf) {
  const { marker, context } = s;
  await step(s, 'the eDocs posted by the transfer page go out only with the compose window they open', async () => {
    // B's compose window is open, with nothing attached, before the transfer posts its items.
    const chart = chartOf.B;
    const composeB = await s.popup(chart, chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first(),
      'compose-B-beside-transfer');
    await composeB.locator('#subject').waitFor({ timeout: TIMEOUT });
    const transfer = await context.newPage();
    await h.gotoApp(transfer, s.config.baseUrl, '/messenger/DisplayMessages');
    await settle(transfer, 'the inbox the transfer is posted from');
    const content = `${marker} ${A_FIRST_NAME} transferred eDoc`;
    await postTransfer(transfer, content);
    h.assert((await transfer.locator('body').innerText()).includes(ATTACHMENTS_LABEL),
      'The compose window the transfer opened shows no attachment');
    const idB = await sendToSelf(s, composeB, messenger.groupId, `${marker} B beside a transfer`);
    h.assert(storedItems(s, idB) === '' && storedPdfs(s, idB).count === 0,
      'B\'s message, sent from a compose window opened before the transfer, carries the transfer\'s items');
    const idT = await sendToSelf(s, transfer, messenger.groupId, `${marker} transfer of A`);
    h.assert(storedItems(s, idT).includes(content),
      'The transfer\'s own message does not carry the items it posted');
    await transfer.close();
    await composeB.close();
  });
}

/** E-Chart ▸ Messenger +, in the patient's own chart: a compose window linked to the patient. */
async function openCompose(s, chart, label) {
  const compose = await s.popup(chart, chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first(),
    `compose-${label}`);
  await compose.locator('#subject').waitFor({ timeout: TIMEOUT });
  return compose;
}

async function sendRefusals(s, A, B, messenger, chartOf) {
  const { marker } = s;
  await step(s, 'a chart PDF goes out only on a message linked to its own patient; a refused send keeps the message', async () => {
    const compose = await composeWithPatientPdf(s, chartOf.A, 'A (relinked)', A);
    const subject = `${marker} A's PDF relinked`;
    const refused = /prepared for a different patient/i;
    // The message is linked to B after A's chart was attached, then to no patient.
    await linkComposeTo(s, compose, 'A\'s compose window', B);
    await sendRefused(s, compose, messenger.groupId, subject, refused, 'The message linked to B');
    h.assert((await compose.locator('body').innerText()).includes(ATTACHMENTS_LABEL),
      'The refused compose page no longer shows the PDF attached in it');
    h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === String(B),
      'The refused compose page dropped the patient its message was linked to');
    await compose.locator('input[name="clearDemographic"]').click();
    await sendRefused(s, compose, messenger.groupId, subject, refused, 'The message linked to no patient');
    // Linked to A again, it goes out with A's PDF, linked to A.
    await linkComposeTo(s, compose, 'A\'s compose window', A);
    const id = await sendToSelf(s, compose, messenger.groupId, subject);
    const pdfs = storedPdfs(s, id);
    h.assert(pdfs.count === 1 && pdfs.text.includes(A_FIRST_NAME),
      `The message linked to A again stored ${pdfs.count} chart PDFs instead of A's one`);
    h.assert(s.sql.value(`SELECT GROUP_CONCAT(demographic_no) FROM msgDemoMap WHERE messageID=${id}
      AND id > ${messenger.demoMapMark}`) === String(A), 'The message linked to A again is not linked to A alone');
    await compose.close();
  });
}

async function listsKeepAttachments(s, A, B, messenger, chartOf) {
  const { marker } = s;
  await step(s, 'the inbox and each patient\'s message list, opened before a send, leave the compose window\'s attachment', async () => {
    const compose = await composeWithPatientPdf(s, chartOf.B, 'B (lists open)', B);
    const inbox = await openPage(s, '/messenger/DisplayMessages', 'messenger inbox');
    const listOf = {};
    for (const [label, patient] of [['A', A], ['B', B]]) {
      listOf[label] = await openPage(s, `/messenger/DisplayDemographicMessages?demographic_no=${patient}`,
        `${label}'s message list`);
    }
    // Each list shows the messages linked to its own patient (sent by the first compose step).
    for (const [label, other] of [['A', 'B'], ['B', 'A']]) {
      const text = await listOf[label].locator('body').innerText();
      h.assert(text.includes(`${marker} ${label} chart PDF`) && !text.includes(`${marker} ${other} chart PDF`),
        `${label}'s message list does not list exactly the messages linked to ${label}`);
    }
    // Without a patient the list names none, rather than the patient of another window's list.
    const unnamed = await openPage(s, '/messenger/DisplayDemographicMessages', 'message list without a patient');
    const unnamedText = await unnamed.locator('body').innerText();
    h.assert(!unnamedText.includes(`${marker} A chart PDF`) && !unnamedText.includes(`${marker} B chart PDF`),
      'The message list opened without a patient lists another window\'s patient\'s messages');
    const id = await sendToSelf(s, compose, messenger.groupId, `${marker} B after the lists`);
    const pdfs = storedPdfs(s, id);
    h.assert(pdfs.count === 1 && pdfs.text.includes(B_FIRST_NAME),
      `Opening the inbox and the message lists left B's message with ${pdfs.count} chart PDFs instead of B's one`);
    for (const page of [unnamed, listOf.B, listOf.A, inbox, compose]) await page.close();
  });
}

async function twoChoosers(s, A, B, messenger, chartOf) {
  const { marker, context, recorder } = s;
  await step(s, 'two compose windows\' attachment choosers, open at once, each attach to their own compose window', async () => {
    const composeA = await openCompose(s, chartOf.A, 'A (two choosers)');
    // A second compose window in the same group of windows as A's chart, as a clinician opens one
    // from the day sheet: Msg ▸ Compose, linked to B. (The schedule's own tab shows the messenger
    // by default; it is put back afterwards.)
    const schedule = new URL(s.schedule.url());
    const { page: composeB, isPopup } = await ui.clickOpensPopupOrNavigates(s.schedule,
      s.schedule.locator('a:has(#oscar_new_msg)').first(), { context, recorder, label: 'messenger-inbox', timeout: TIMEOUT });
    try {
      await settle(composeB, 'the messenger opened from the schedule');
      await Promise.all([
        composeB.waitForURL(url => url.pathname.endsWith('/messenger/ViewCreateMessage'), { timeout: TIMEOUT }),
        composeB.locator('a.nav-link[href*="/messenger/ViewCreateMessage"]').first().click(),
      ]);
      await settle(composeB, 'Compose from the messenger');
      await linkComposeTo(s, composeB, 'the second compose window', B);
      // Both choosers are open before either attaches.
      const chooserA = await openChooser(s, composeA, 'A', A);
      const chooserB = await openChooser(s, composeB, 'B', B);
      // A's chooser still shows A's own attachment page: one window name would have navigated it to B's.
      h.assert(!chooserA.chooser.isClosed() && await chooserA.main.locator('input[name="demographic_no"]')
        .inputValue({ timeout: 5000 }).catch(() => null) === String(A),
      'B\'s attachment chooser took over A\'s chooser window');
      await attachInChooser(chooserB, composeB);
      h.assert(!(await composeA.locator('#pdf-attachment-indicator').isVisible())
          && !(await composeA.locator('body').innerText()).includes(ATTACHMENTS_LABEL),
        'Attaching in B\'s chooser marked A\'s compose window as having attachments');
      await attachInChooser(chooserA, composeA);
      for (const [label, patient, compose, own, other] of [
        ['B', B, composeB, B_FIRST_NAME, A_FIRST_NAME], ['A', A, composeA, A_FIRST_NAME, B_FIRST_NAME]]) {
        const id = await sendToSelf(s, compose, messenger.groupId, `${marker} ${label} from its own chooser`);
        const pdfs = storedPdfs(s, id);
        h.assert(pdfs.count === 1 && pdfs.text.includes(own) && !pdfs.text.includes(other),
          `${label}'s message, attached from its own chooser, stored ${pdfs.count} chart PDFs instead of ${label}'s one`);
        h.assert(s.sql.value(`SELECT GROUP_CONCAT(demographic_no) FROM msgDemoMap WHERE messageID=${id}
          AND id > ${messenger.demoMapMark}`) === String(patient), `${label}'s message is not linked to ${label} alone`);
      }
      await composeA.close();
    } finally {
      if (isPopup) {
        if (!composeB.isClosed()) await composeB.close();
      } else {
        await h.gotoApp(s.schedule, s.config.baseUrl, schedule.pathname.replace(/^\/[^/]+/, '') + schedule.search);
        await settle(s.schedule, 'the schedule, shown again');
      }
    }
  });
}

/** How many compose windows' attachments the session keeps (MsgAttachmentStaging.MAX_ENTRIES). */
const STAGED_WINDOWS = 8;

async function releasedAttachments(s, A, messenger, chartOf) {
  const { marker, context } = s;
  await step(s, 'a compose window whose attachment was released for newer windows says so once, then sends without it', async () => {
    const compose = await composeWithPatientPdf(s, chartOf.A, 'A (released)', A);
    // As many compose windows as the session keeps stage attachments after it: the transfer
    // page's post, each opening a compose window of its own.
    const transfer = await context.newPage();
    await h.gotoApp(transfer, s.config.baseUrl, '/messenger/DisplayMessages');
    await settle(transfer, 'the inbox the transfers are posted from');
    for (let n = 1; n <= STAGED_WINDOWS; n++) await postTransfer(transfer, `${marker} transfer ${n}`);
    await transfer.close();
    const subject = `${marker} A after its PDF was released`;
    await sendRefused(s, compose, messenger.groupId, subject, /attachments were released/i, 'The released compose window');
    h.assert(!(await compose.locator('body').innerText()).includes(ATTACHMENTS_LABEL),
      'The compose page still shows the attachment that was released');
    const id = await sendToSelf(s, compose, messenger.groupId, subject);
    h.assert(storedPdfs(s, id).count === 0 && storedItems(s, id) === '',
      'The message sent after the notice carries an attachment, or another window\'s');
    await compose.close();
  });
}

/** Observes the response to a request `trigger` makes, without changing what the browser gets. */
async function captureResponse(page, pathname, trigger) {
  let captured;
  const handler = async route => {
    const response = await route.fetch();
    captured = { status: response.status(), body: await response.body(), post: route.request().postData() || '' };
    await route.fulfill({ response });
  };
  const pattern = `**${pathname}`;
  await page.context().route(pattern, handler);
  try {
    await trigger();
    const deadline = Date.now() + TIMEOUT;
    while (!captured && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
  } finally {
    await page.context().unroute(pattern, handler);
  }
  h.assert(captured, `${pathname} was not requested`);
  return captured;
}

async function pdfDownloads(s) {
  const { sql, marker, context } = s;
  await step(s, 'each message\'s PDF list downloads its own message\'s PDF after another message\'s list was opened', async () => {
    const idOf = {};
    for (const label of ['A', 'B']) {
      idOf[label] = key(sql.value(`SELECT messageid FROM messagetbl WHERE thesubject=${q(`${marker} ${label} chart PDF`)}`),
        `${label}'s sent message`);
    }
    const inbox = await openPage(s, '/messenger/DisplayMessages', 'messenger inbox');
    const viewOf = {};
    const listOf = {};
    // A's list, then B's, each from its own message view.
    for (const label of ['A', 'B']) {
      viewOf[label] = await openPage(s, await messageViewPath(inbox, { id: idOf[label] }, label), `${label}'s message view`);
      listOf[label] = await s.popup(viewOf[label], viewOf[label].locator('a[href*="ViewPDFAttach?attachId="]').first(),
        `pdf-list-${label}`);
      await settle(listOf[label], `${label}'s PDF list`);
    }
    let csrf;
    for (const [label, own, other] of [['A', A_FIRST_NAME, B_FIRST_NAME], ['B', B_FIRST_NAME, A_FIRST_NAME]]) {
      const list = listOf[label];
      const download = await captureResponse(list, '/messenger/ViewPDFFile',
        () => list.locator('form[action$="/messenger/ViewPDFFile"] button[type="submit"]').first().click());
      h.assert(download.status === 200 && download.body.subarray(0, 5).toString('latin1') === '%PDF-',
        `The download from ${label}'s PDF list answered HTTP ${download.status} without a PDF`);
      const text = pdfTextBuffer(download.body);
      h.assert(text.includes(own) && !text.includes(other),
        `The download from ${label}'s PDF list is ${text.includes(other) ? 'the other message\'s' : 'not its message\'s'} PDF`);
      h.assert(download.body.equals(storedPdfs(s, idOf[label]).bytes[0]),
        `The download from ${label}'s PDF list is not the PDF stored on its message, byte for byte`);
      h.assert(new URLSearchParams(download.post).get('attId') === idOf[label],
        `The download from ${label}'s PDF list does not name its message`);
      // CSRFGuard's client adds the token to the form as it is submitted.
      csrf = new URLSearchParams(download.post).get('CSRF-TOKEN');
    }
    h.assert(csrf, 'The PDF list\'s download carried no CSRF token');
    // A malformed index is refused; an index the message does not have, or no message, finds nothing.
    const url = h.appUrl(s.config.baseUrl, '/messenger/ViewPDFFile');
    for (const [form, status, what] of [
      [{ file_id: 'x', attId: idOf.A }, 400, 'a malformed file index'],
      [{ file_id: '1', attId: idOf.A }, 404, 'an index past the message\'s PDFs'],
      [{ file_id: '0' }, 404, 'no message']]) {
      const answer = await context.request.post(url, { headers: { 'CSRF-TOKEN': csrf },
        form: { ...form, 'CSRF-TOKEN': csrf }, maxRedirects: 0 });
      h.assert(answer.status() === status, `A download for ${what} answered HTTP ${answer.status()}, not ${status}`);
    }
    for (const label of ['A', 'B']) {
      await listOf[label].close();
      await viewOf[label].close();
    }
    await inbox.close();
  });
}

/** A delivered message to the test provider carrying one chart item, as a received message. */
function receiveMessageWithItem(s, label) {
  const { sql, marker, provider } = s;
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
  const item = `${marker} ${label} item`;
  const xml = `<root><table name="${marker} ${label} table"><item itemId="0" name="${item}" value="FAKE-PW value"`
    + ` removable="false"><content><fld name="FAKE-PW field" value="FAKE-PW ${label} detail"/></content><data/></item>`
    + '</table></root>';
  const id = h.insertId(sql, `INSERT INTO messagetbl(thedate, theime, themessage, thesubject, sentby, sentto,
      sentbyNo, sentByLocation, type, attachment)
    VALUES(CURDATE(), CURTIME(), 'FAKE-PW received', ${q(`${marker} received ${label}`)}, 'FAKE-PW sender',
      'FAKE-PW recipient', ${q(provider)}, ${location}, 0, ${q(xml)})`, 'received message');
  sql.execute(`INSERT INTO messagelisttbl(message, provider_no, status, remoteLocation, destinationFacilityId,
      sourceFacilityId) VALUES(${id}, ${q(provider)}, 'new', ${location}, 0, 0)`);
  return { id, item };
}

/** The app path of a received message's view, as the inbox links it. */
async function messageViewPath(inbox, message, label) {
  const href = await inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${message.id}&"]`).first()
    .getAttribute('href');
  h.assert(href, `The inbox does not list the ${label} received message`);
  const target = new URL(href, inbox.url());
  return target.pathname.replace(/^\/[^/]+/, '') + target.search;
}

/** Attachment ▸ Save Attachments, in the view's own attachment window. */
async function saveViewAttachments(s, view, label) {
  const items = await s.popup(view, view.locator('a[href*="ViewAttach?attachId="]').first(), `items-${label}`);
  await settle(items, `${label} message's attachment window`);
  const [saved] = await Promise.all([
    items.waitForResponse(r => new URL(r.url()).pathname.endsWith('/messenger/AdjustAttachments'), { timeout: TIMEOUT }),
    items.locator('form[action$="/messenger/AdjustAttachments"] input[type="submit"]').click(),
  ]);
  h.assert(saved.status() === 302 && /\/demographic\/DemographicLinkMsg$/.test(saved.headers().location || ''),
    `Save Attachments in the ${label} message's window did not hand over to the patient search`);
  await Promise.race([
    items.waitForEvent('close', { timeout: TIMEOUT }),
    items.waitForURL(/\/demographic\/DemographicLinkMsg/, { timeout: TIMEOUT, waitUntil: 'load' }),
  ]).catch(() => {});
  if (!items.isClosed()) await items.close();
}

/** Reply, Reply All or Forward: the view's own form opens the compose page in its window. */
async function composeFromView(view, label, button) {
  const [opened] = await Promise.all([
    view.waitForResponse(r => r.request().isNavigationRequest()
      && new URL(r.url()).pathname.endsWith('/messenger/HandleMessages'), { timeout: TIMEOUT }),
    view.locator(`button[name="${button}"]`).click(),
  ]);
  h.assert(opened.status() === 200, `${button} in the ${label} message view answered HTTP ${opened.status()}`);
  await settle(view, `${button} from the ${label} message view`);
}

async function forwardedItems(s, A, messenger) {
  const { sql, marker } = s;
  const received = { first: receiveMessageWithItem(s, 'first'), second: receiveMessageWithItem(s, 'second') };

  const viewOf = {};
  let inbox;
  /** The inbox sets up the login's messenger; each message then opens in a window of its own. */
  async function openViews() {
    inbox = await openPage(s, '/messenger/DisplayMessages', 'messenger inbox');
    for (const label of ['first', 'second']) {
      viewOf[label] = await openPage(s, await messageViewPath(inbox, received[label], label), `${label} message view`);
    }
  }

  /**
   * Search Patient ▸ pick ▸ Link to Patient, which posts the view and shows it again: the view's
   * saved items must survive being shown again.
   */
  async function linkPatient(label, patient) {
    const view = viewOf[label];
    await view.locator('#keyword').fill(marker);
    const search = await s.popup(view, view.locator('input[name="searchDemo"]'), `patient-search-${label}`);
    const closed = search.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
    const pick = search.locator(`#patientResults a[onclick*="selectForMessenger"][onclick*="'${patient}'"]`);
    await pick.waitFor({ timeout: TIMEOUT });
    await pick.click();
    h.assert(await closed, `Picking the patient for the ${label} message did not close the search popup`);
    h.assert(await view.locator('input[name="demographic_no"]').inputValue() === String(patient),
      `Picking the patient did not fill the ${label} message view's link form`);
    await Promise.all([
      view.waitForResponse(r => r.request().isNavigationRequest() && r.frame() === view.mainFrame()
        && new URL(r.url()).pathname.endsWith('/messenger/ViewMessage'), { timeout: TIMEOUT }),
      view.locator('input[name="linkDemo"]').click(),
    ]);
    await settle(view, `${label} message view after Link to Patient`);
    await expectValue(sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${received[label].id}
      AND demographic_no=${patient}`, '1', `Link to Patient did not link the ${label} message`);
  }

  await step(s, 'each message view forwards only its own message\'s chart items, by Forward or Reply', async () => {
    await openViews();
    await saveViewAttachments(s, viewOf.first, 'first');
    // The second view saves its items after the first one did.
    await saveViewAttachments(s, viewOf.second, 'second');
    // The first view is shown again for its linked patient before it forwards.
    await linkPatient('first', A);
    for (const [label, other, button] of [['first', 'second', 'forward'], ['second', 'first', 'reply']]) {
      const view = viewOf[label];
      await composeFromView(view, label, button);
      h.assert(/Attachments/i.test(await view.locator('#scrollNumber1').innerText()),
        `${button} from the ${label} message view does not show the items saved in it`);
      const id = await sendToSelf(s, view, messenger.groupId, `${marker} forwarded ${label}`);
      const stored = sql.value(`SELECT attachment FROM messagetbl WHERE messageid=${id}`) || '';
      h.assert(stored.includes(`name="${received[label].item}"`) && !stored.includes(`name="${received[other].item}"`),
        `The message sent by ${button} from the ${label} message view does not forward exactly its own items`);
      if (label === 'first') {
        await expectValue(sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${id} AND demographic_no=${A}`, '1',
          'The forward of the linked message is not linked to its patient');
      }
      await view.close();
    }
    await inbox.close();
  });
}

async function repliesFromOneMessage(s, A, messenger) {
  const { sql, marker } = s;
  const shared = receiveMessageWithItem(s, 'shared');
  // Linked to A, as Link to Patient does: its reply and forward are linked to A and can attach A's chart.
  sql.execute(`INSERT INTO msgDemoMap(messageID, demographic_no) VALUES(${shared.id}, ${A})`);

  await step(s, 'a reply, a forward and a new message opened from one message each send only their own attachments', async () => {
    const inbox = await openPage(s, '/messenger/DisplayMessages', 'messenger inbox');
    const path = await messageViewPath(inbox, shared, 'shared');
    const replyWindow = await openPage(s, path, 'message view for the reply');
    await saveViewAttachments(s, replyWindow, 'shared');
    const forwardWindow = await openPage(s, path, 'message view for the forward');
    const composeWindow = await openPage(s, path, 'message view for its Compose link');
    await composeFromView(replyWindow, 'shared', 'reply');
    await composeFromView(forwardWindow, 'shared', 'forward');
    await Promise.all([
      composeWindow.waitForURL(url => url.pathname.endsWith('/messenger/ViewCreateMessage'), { timeout: TIMEOUT }),
      composeWindow.locator('a[href*="/messenger/ViewCreateMessage"]').first().click(),
    ]);
    await settle(composeWindow, 'Compose from the shared message view');
    for (const [label, page] of [['reply', replyWindow], ['forward', forwardWindow], ['new message', composeWindow]]) {
      h.assert(/Attachments/i.test(await page.locator('#scrollNumber1').innerText()),
        `The ${label} does not show the items saved for its message`);
    }
    // The reply's window attaches A's chart; the forward and the new message, opened from the
    // same message, send first.
    await attachPatientPdf(s, replyWindow, 'The reply', A);
    for (const [label, page] of [['forward', forwardWindow], ['new message', composeWindow]]) {
      const sent = await sendToSelf(s, page, messenger.groupId, `${marker} shared ${label}`);
      const pdfs = storedPdfs(s, sent);
      h.assert(pdfs.count === 0, `The ${label} carries ${pdfs.count} chart PDF(s): the one attached in the reply's`
        + ' window, opened from the same message');
      h.assert(storedItems(s, sent).includes(`name="${shared.item}"`),
        `The ${label} does not carry the items saved for its message`);
    }
    const replied = await sendToSelf(s, replyWindow, messenger.groupId, `${marker} shared reply`);
    const repliedPdfs = storedPdfs(s, replied);
    h.assert(repliedPdfs.count === 1 && repliedPdfs.text.includes(A_FIRST_NAME),
      `The reply stored ${repliedPdfs.count} chart PDFs instead of the one of A attached in its window`);
    h.assert(storedItems(s, replied).includes(`name="${shared.item}"`),
      'The reply does not carry the items saved for its message');
    // Sending released the items saved for the message, as sending cleared the messenger bean:
    // a reply opened now starts without them instead of attaching them again.
    const later = await openPage(s, path, 'message view after its replies were sent');
    await composeFromView(later, 'shared', 'reply');
    h.assert(!(await later.locator('body').innerText()).includes(ATTACHMENTS_LABEL),
      'A reply opened after the message\'s saved items were sent attaches them again');
    await later.close();
    await replyWindow.close();
    await forwardWindow.close();
    await composeWindow.close();
    await inbox.close();
  });
}

async function yearEndStatements(s, A, B) {
  const { sql, marker } = s;
  const billOf = {
    A: seedOwnedBill(s, { payProgram: 'PAT', status: 'O', date: '2025-03-04', code: 'A007A', fee: '34.70' }),
    B: seedOwnedBill({ ...s, patient: B }, { payProgram: 'PAT', status: 'O', date: '2025-05-06', code: 'K030A', fee: '45.25' }),
  };
  sql.execute(`UPDATE billing_on_cheader1 SET demographic_name=${q(`${marker},${B_FIRST_NAME}`)}
    WHERE id=${billOf.B.headerId} AND demographic_no=${B}`);

  /** End Year Statement ▸ patient search ▸ pick ▸ dates ▸ Create Statement, in its own window. */
  async function statementFor(label, patient) {
    const page = await openPage(s, '/billing/CA/ON/endYearStatement', `${label}'s year-end statement`);
    await page.locator('#nameForlooksOnly').fill(marker);
    await Promise.all([
      page.waitForNavigation({ timeout: TIMEOUT }),
      page.locator('button[onclick*="demographicSearch"]').click(),
    ]);
    await settle(page, `${label}'s year-end patient search`);
    await Promise.all([
      page.waitForNavigation({ timeout: TIMEOUT }),
      page.locator(`input[name="pick_demographic"][value="${patient}"]`).click(),
    ]);
    await settle(page, `${label}'s year-end statement form`);
    h.assert(await page.locator('#demographicNoParam').inputValue() === patient, `${label}'s statement picked another patient`);
    await page.locator('#fromDateParam').fill('2025-01-01');
    await page.locator('h3').first().click();
    await page.locator('#toDateParam').fill('2025-12-31');
    await page.locator('h3').first().click();
    await Promise.all([
      page.waitForNavigation({ timeout: TIMEOUT }),
      page.locator('input[type="submit"][value="Create Statement"]').click(),
    ]);
    await settle(page, `${label}'s year-end statement`);
    return page;
  }

  /** Print PDF: the statement arrives as a download, whose bytes are read from the saved file. */
  async function printPdf(label, page) {
    const [response, download] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/endYearStatement/pdf'), { timeout: TIMEOUT }),
      page.waitForEvent('download', { timeout: TIMEOUT }),
      page.locator('input[type="submit"][value="Print PDF"]').click(),
    ]);
    h.assert(response.status() === 200 && /^application\/pdf/.test(response.headers()['content-type'] || ''),
      `${label}'s Print PDF answered HTTP ${response.status()} instead of a PDF`);
    const file = await download.path();
    h.assert(file, `${label}'s year-end PDF was not saved`);
    return pdfTextBuffer(fs.readFileSync(file), { layout: true });
  }

  await step(s, 'each year-end statement window prints its own statement on screen after the other window searched', async () => {
    const pageOf = { A: await statementFor('A', A) };
    // B's statement is created in a second window after A's.
    pageOf.B = await statementFor('B', B);
    // A's start date changed after its statement was created, past A's March invoice: the PDF is
    // still the statement on screen, January to December.
    await pageOf.A.locator('#fromDateParam').fill('2025-06-01');
    await pageOf.A.locator('h3').first().click();
    for (const [label, own, other] of [['A', A_FIRST_NAME, B_FIRST_NAME], ['B', B_FIRST_NAME, A_FIRST_NAME]]) {
      const pdfText = await printPdf(label, pageOf[label]);
      const text = pdfText.split(/\s+/).join(' ');
      h.assert(text.includes(own) && !text.includes(other),
        `${label}'s year-end PDF names ${text.includes(other) ? 'the other window\'s patient' : 'no patient'}`);
      const expectedRows = [{ id: billOf[label].headerId, date: label === 'A' ? '2025-03-04' : '2025-05-06',
        invoiced: label === 'A' ? '34.70' : '45.25', paid: '0.00' }];
      h.assert(JSON.stringify(statementInvoiceRows(pdfText)) === JSON.stringify(expectedRows),
        `${label}'s year-end PDF does not list exactly ${label}'s own invoice`);
      await pageOf[label].close();
    }
  });
}

async function waitingListPrompt(s, A, B) {
  const { sql, marker, provider, context } = s;
  const listName = `${marker} list`;
  const group = sql.value(`SELECT COALESCE(myGroupNo, '') FROM ProviderPreference WHERE providerNo=${q(provider)}`) || '';
  const listId = h.insertId(sql, `INSERT INTO waitingListName (name, group_no, provider_no, create_date, is_history)
    VALUES (${q(listName)}, ${q(group)}, ${q(provider)}, NOW(), 'N')`, 'waiting list');
  s.cleanup(() => {
    sql.execute(`DELETE FROM waitingList WHERE listID=${listId} AND demographic_no IN (${A},${B});
      DELETE FROM waitingListName WHERE ID=${listId} AND name=${q(listName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM waitingList WHERE listID=${listId})
      + (SELECT COUNT(*) FROM waitingListName WHERE ID=${listId})`) === '0',
    'The owned waiting list was not removed');
  });
  sql.execute(`INSERT INTO waitingList (listID, demographic_no, note, position, onListSince, is_history)
    VALUES (${listId}, ${B}, ${q(marker)}, 1, NOW(), 'N')`);
  const day = sql.value('SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY)');
  const [year, month, dayOfMonth] = day.split('-');

  let listPage;

  /** The booking form for one patient, as the Master Record's appointment grid opens it. */
  async function book(label, patient, first, start, end, expectPrompt) {
    const path = `/appointment/addappointment?demographic_no=${patient}&name=${encodeURIComponent(`${marker},${first}`)}`
      + `&provider_no=${encodeURIComponent(provider)}&bFirstDisp=true&year=${year}&month=${month}&day=${dayOfMonth}`
      + `&start_time=${start}&end_time=${end}&duration=15&search=true`;
    // Opened by script from a tab of its own, as the schedule opens it: the booked page refreshes
    // and then closes the window that opened it, which must not be the waiting-list page.
    const opener = await context.newPage();
    const [form] = await Promise.all([
      context.waitForEvent('page', { timeout: TIMEOUT }),
      opener.evaluate(url => { window.open(url, 'bookingForm'); }, String(h.appUrl(s.config.baseUrl, path))),
    ]);
    await settle(form, `${label}'s booking form`);
    h.assert(await form.locator('#demographic_no').inputValue() === patient, `${label}'s booking form names another patient`);
    await form.locator('#reason').fill(marker);
    const add = async () => {
      await Promise.all([
        form.waitForEvent('close', { timeout: TIMEOUT }),
        form.locator('#addButton').click(),
      ]);
    };
    const dialogs = expectPrompt ? await h.withExpectedDialogs(form, add, { accept: false }) : (await add(), []);
    await opener.close();
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient}
      AND appointment_date=${q(day)} AND reason=${q(marker)}`, '1', `${label}'s appointment was not booked once`);
    return dialogs;
  }

  await step(s, 'booking offers waiting-list removal only for a patient on that list, after the list page showed it', async () => {
    // Report ▸ Waiting List, showing the owned list (it holds B).
    listPage = await openPage(s, `/waitinglist/SetupDisplayWaitingList?waitingListId=${listId}`, 'waiting list');
    h.assert((await listPage.locator('body').innerText()).includes(listName), 'The waiting-list page does not show the owned list');
    // A is on no list: no prompt. An unexpected confirm() fails the step through the strict page wiring.
    await book('A', A, A_FIRST_NAME, '10:00', '10:14', false);
    // B is on the owned list: the prompt names it; dismissing it keeps B on the list.
    const dialogs = await book('B', B, B_FIRST_NAME, '10:15', '10:29', true);
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text.includes(listName),
      'Booking B, who is on the list, did not offer to remove B from it once');
    h.assert(sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${B}
      AND is_history='N'`) === '1', 'Dismissing the prompt took B off the list');
    await listPage.close();
  });
}

async function diseaseRegistry(s, A, B) {
  const { sql, provider } = s;
  const describe = code => sql.value(`SELECT description FROM icd9 WHERE icd9=${q(code)}`);
  const codeOf = { A: '401', B: '250' };
  const added = '493';
  for (const code of [codeOf.A, codeOf.B, added]) h.assert(describe(code), `The icd9 table has no ${code}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no IN (${A},${B})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${A},${B})`) === '0',
      'Owned registry codes were not removed');
  });
  for (const [patient, code] of [[A, codeOf.A], [B, codeOf.B]]) {
    sql.execute(`INSERT INTO dxresearch (demographic_no, start_date, update_date, status, dxresearch_code,
        coding_system, providerNo)
      VALUES (${patient}, CURDATE(), NOW(), 'A', ${q(code)}, 'icd9', ${q(provider)})`);
  }
  const registry = patient => `/oscarResearch/dxresearch/setupDxResearch?demographicNo=${patient}`
    + `&providerNo=${encodeURIComponent(provider)}&quickList=`;
  const listed = async page => page.locator('#displayDxCodeTable').innerText();

  await step(s, 'a refused registry code re-renders this window\'s own patient, whose next code is filed for it', async () => {
    const windowA = await openPage(s, registry(A), 'A\'s Disease Registry');
    const windowB = await openPage(s, registry(B), 'B\'s Disease Registry');
    await windowA.locator('input[name="xml_research1"]').fill('99999');
    await Promise.all([
      windowA.waitForNavigation({ timeout: TIMEOUT }),
      windowA.locator('input[name="codeAdd"]').click(),
    ]);
    await settle(windowA, 'A\'s Disease Registry after a refused code');
    const shown = await listed(windowA);
    h.assert(shown.includes(describe(codeOf.A)) && !shown.includes(describe(codeOf.B)),
      `A's registry, re-rendered after the refusal, lists ${shown.includes(describe(codeOf.B)) ? 'B\'s' : 'no'} diagnoses`);
    h.assert(await windowA.locator('form[action$="/oscarDxResearch/dxResearch"] input[name="demographicNo"]').inputValue() === A,
      'A\'s re-rendered registry form names another patient');
    await windowA.locator('input[name="xml_research1"]').fill(added);
    await Promise.all([
      windowA.waitForNavigation({ timeout: TIMEOUT }),
      windowA.locator('input[name="codeAdd"]').click(),
    ]);
    await settle(windowA, 'A\'s Disease Registry after adding a code');
    await expectValue(sql, `SELECT GROUP_CONCAT(dxresearch_code ORDER BY dxresearch_code) FROM dxresearch
      WHERE demographic_no=${A} AND status='A'`, [codeOf.A, added].sort().join(','),
    'A\'s next code was not registered for A');
    h.assert(sql.value(`SELECT GROUP_CONCAT(dxresearch_code) FROM dxresearch WHERE demographic_no=${B}`) === codeOf.B,
      'A\'s window changed B\'s registry');
    await windowA.close();
    await windowB.close();
  });
}

async function workflow(s) {
  const { sql, marker } = s;
  requirePoppler('pdftotext');
  const A = key(s.patient, 'patient');
  sql.execute(`UPDATE demographic SET first_name=${q(A_FIRST_NAME)} WHERE demographic_no=${A} AND last_name=${q(marker)}`);
  // Registered first, so it runs after every child cleanup below.
  const B = key(createSecondPatient(s), 'second patient');
  ownChartRows(s, [A, B]);
  ownAppointments(s, [A, B]);
  const messenger = ownedMessengerGroup(s);

  await healthTrackerVisits(s, A, B);
  const charts = await composeAttachments(s, A, B, messenger);
  await pdfDownloads(s);
  await sendRefusals(s, A, B, messenger, charts);
  await listsKeepAttachments(s, A, B, messenger, charts);
  await twoChoosers(s, A, B, messenger, charts);
  await clearNewMessage(s, A, B, messenger, charts);
  await transferredItems(s, A, B, messenger, charts);
  await releasedAttachments(s, A, messenger, charts);
  await forwardedItems(s, A, messenger);
  await repliesFromOneMessage(s, A, messenger);
  await yearEndStatements(s, A, B);
  await waitingListPrompt(s, A, B);
  await diseaseRegistry(s, A, B);
}

if (require.main === module) runWorkflow('session-state-two-window-isolation', workflow, { openPatient: true });
module.exports = { workflow };
