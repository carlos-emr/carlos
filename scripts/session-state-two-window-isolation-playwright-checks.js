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
 *      ViewStoreApptInSession now files it per patient.
 *   2. E-Chart ▸ Messenger + for A and for B ▸ Attach Patient ▸ patient information ▸ Attach in
 *      both compose windows, then Send Message from each: each message carries only the PDF
 *      rendered for its own patient. Compose attachments lived on the login's messenger bean;
 *      each compose window now stages its own (MsgAttachmentStaging).
 *   3. Two received messages with chart items, each in its own message view ▸ Attachment ▸ Save
 *      Attachments, then Compose ▸ Send Message from each view: each new message forwards only
 *      its own view's items.
 *   4. Administration ▸ Billing ▸ End Year Statement for A and for B in two windows ▸ Print PDF
 *      in each: each PDF names its own patient and invoice. The PDF printed the statement the last
 *      search in any window had left in the session.
 *   5. Report ▸ Waiting List showing a list that holds B, then a booking for A, who is on no list:
 *      the booked page must not offer to take A off that list; a booking for B still offers it.
 *      The booked page read the list name through EL, which fell back to the name the
 *      waiting-list page had left in the session.
 *   6. E-Chart ▸ Disease Registry for A and for B ▸ an unknown code in A's window: the refusal
 *      re-renders A's own diagnoses, and A's next code is registered for A only. The re-render
 *      read the registry opened last.
 *
 * Fixtures: the workflow's FAKE patient (A, first name PatientAlpha) and a second FAKE patient (B,
 * PatientBravo, the same marker last name); one appointment today per patient and the bookings
 * this check makes; a Health Tracker item per patient; an owned messenger group holding the test
 * provider and two delivered messages with chart items; one PAT bill per patient; an owned
 * waiting list holding B; one registry code per patient. Cleanup removes every owned row by key
 * and asserts it gone.
 *
 * Not driven here: the BC bill and invoice receipt (BC installs only; their unit tests cover two
 * bills), the PMM "attach note to client" popup (its search form renders the text criteria as
 * checkboxes, so it finds no client to attach to; ClientSearchAttachNoteUnitTest covers it) and the
 * prescription pages (they need DrugRef).
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
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
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo IN (${list}))
      + (SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no IN (${patients.map(p => q(p)).join(',')}))`) === '0',
    'Owned notes, measurements or tracker items were not removed');
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
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${like})
      + (SELECT COUNT(*) FROM groups_tbl WHERE groupID=${groupId})
      + (SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${groupId})`) === '0',
    'Owned messages or the owned messenger group were not removed');
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

/** The text of every chart PDF stored on one message, and how many there are. */
function storedPdfs(s, messageId) {
  const stored = s.sql.value(`SELECT CAST(pdfattachment AS CHAR) FROM messagetbl WHERE messageid=${messageId}`) || '';
  const entries = stored.match(/<PDF>.*?<\/PDF>/gs) || [];
  const text = entries.map(entry => {
    const content = (entry.match(/<CONTENT>([^<]*)<\/CONTENT>/) || [])[1] || '';
    return pdfTextBuffer(Buffer.from(content, 'base64'));
  }).join('\n');
  return { count: entries.length, text };
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

  /** E-Chart ▸ Messenger + ▸ Attach Patient ▸ patient information ▸ Attach. */
  async function composeWithPatientPdf(label, patient) {
    const chart = chartOf[label];
    const compose = await s.popup(chart, chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first(),
      `compose-${label}`);
    await compose.locator('#subject').waitFor({ timeout: TIMEOUT });
    h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === patient,
      `${label}'s compose window is not linked to ${label}`);
    const [chooser] = await Promise.all([
      context.waitForEvent('page', { timeout: TIMEOUT }),
      compose.locator('input[name="attachDemo"]').click(),
    ]);
    await chooser.waitForURL(url => url.pathname.endsWith('/messenger/attachmentFrameset'),
      { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    let main;
    const deadline = Date.now() + TIMEOUT;
    while (!(main = chooser.frame({ name: 'main' })) && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    h.assert(main, `${label}'s attachment chooser has no main frame`);
    await main.locator('form[action$="/messenger/Doc2PDF"]').waitFor({ timeout: TIMEOUT });
    h.assert(await main.locator('input[name="demographic_no"]').inputValue() === patient,
      `${label}'s attachment chooser is for another patient`);
    await main.locator('input[name="item"][value="demographic"]').check();
    // The chooser closes itself once the PDF is attached (its own late loads are abandoned).
    await Promise.all([
      chooser.waitForEvent('close', { timeout: 60000 }),
      main.locator('button[name="Attach"]').click(),
    ]);
    await compose.locator('#pdf-attachment-indicator').waitFor({ state: 'visible', timeout: TIMEOUT });
    return compose;
  }

  await step(s, 'each compose window sends only the chart PDF attached in it, linked to its own patient', async () => {
    await openCharts();
    const composeA = await composeWithPatientPdf('A', A);
    // B's compose window attaches B's chart after A's has attached A's.
    const composeB = await composeWithPatientPdf('B', B);
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

async function forwardedItems(s, messenger) {
  const { sql, marker, provider, context } = s;
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
  const received = {};
  for (const label of ['first', 'second']) {
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
    received[label] = { id, item };
  }

  const viewOf = {};
  let inbox;
  /** The inbox sets up the login's messenger; each message then opens in a window of its own. */
  async function openViews() {
    inbox = await openPage(s, '/messenger/DisplayMessages', 'messenger inbox');
    for (const label of ['first', 'second']) {
      const href = await inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${received[label].id}&"]`).first()
        .getAttribute('href');
      h.assert(href, `The inbox does not list the ${label} received message`);
      const target = new URL(href, inbox.url());
      viewOf[label] = await openPage(s, target.pathname.replace(/^\/[^/]+/, '') + target.search, `${label} message view`);
    }
  }

  /** Attachment ▸ Save Attachments, in the view's own attachment window. */
  async function saveAttachments(label) {
    const view = viewOf[label];
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

  await step(s, 'each message view forwards only its own message\'s chart items', async () => {
    await openViews();
    await saveAttachments('first');
    // The second view saves its items after the first one did.
    await saveAttachments('second');
    for (const [label, other] of [['first', 'second'], ['second', 'first']]) {
      const view = viewOf[label];
      const [opened] = await Promise.all([
        view.waitForResponse(r => r.request().isNavigationRequest()
          && new URL(r.url()).pathname.endsWith('/messenger/ViewCreateMessage'), { timeout: TIMEOUT }),
        view.locator('a[href*="/messenger/ViewCreateMessage"]:visible').first().click(),
      ]);
      h.assert(opened.status() === 200, `Compose from the ${label} message view answered HTTP ${opened.status()}`);
      await settle(view, `compose from the ${label} message view`);
      h.assert(/Attachments/i.test(await view.locator('#scrollNumber1').innerText()),
        `Compose from the ${label} message view does not show the items saved in it`);
      const id = await sendToSelf(s, view, messenger.groupId, `${marker} forwarded ${label}`);
      const stored = sql.value(`SELECT attachment FROM messagetbl WHERE messageid=${id}`) || '';
      h.assert(stored.includes(`name="${received[label].item}"`) && !stored.includes(`name="${received[other].item}"`),
        `The message composed from the ${label} message view does not forward exactly its own items`);
      await view.close();
    }
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
    return pdfTextBuffer(fs.readFileSync(file));
  }

  await step(s, 'each year-end statement window prints its own patient after the other window searched', async () => {
    const pageOf = { A: await statementFor('A', A) };
    // B's statement is created in a second window after A's.
    pageOf.B = await statementFor('B', B);
    for (const [label, own, other] of [['A', A_FIRST_NAME, B_FIRST_NAME], ['B', B_FIRST_NAME, A_FIRST_NAME]]) {
      const otherLabel = label === 'A' ? 'B' : 'A';
      const words = (await printPdf(label, pageOf[label])).split(/\s+/);
      const text = words.join(' ');
      h.assert(text.includes(own) && !text.includes(other),
        `${label}'s year-end PDF names ${text.includes(other) ? 'the other window\'s patient' : 'no patient'}`);
      h.assert(words.includes(billOf[label].headerId) && !words.includes(billOf[otherLabel].headerId),
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
  await composeAttachments(s, A, B, messenger);
  await forwardedItems(s, messenger);
  await yearEndStatements(s, A, B);
  await waitingListPrompt(s, A, B);
  await diseaseRegistry(s, A, B);
}

if (require.main === module) runWorkflow('session-state-two-window-isolation', workflow, { openPatient: true });
module.exports = { workflow };
