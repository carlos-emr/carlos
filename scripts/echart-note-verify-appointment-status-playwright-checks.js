#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Verify & Sign on a chart note, the appointment status letters it sets, and the note's Encounter Date and
 * encounter type.
 *
 * WHY THIS CHECK EXISTS. echart-note-sign-bill proves that Save, Sign & Save and Sign Save & Bill write a
 * signed casemgmt_note. It never presses Verify & Sign (the chart toolbar's fifth button, #signVerifyImg),
 * never reads what signing does to the appointment the chart was opened from, and never looks at the two
 * fields every note carries beside its text: the Encounter Date (observation_date) and the encounter type. A
 * clinician relies on the day sheet's status icon to see which visits still need a signature, so the letter
 * appended to the status (S for signed, V for verified) is the only trace of the signing on the schedule.
 *
 * User path: Schedule day sheet > the appointment's "E" link (the chart opens carrying the appointment) > type
 * a note > Sign & Save or Verify & Sign (CaseManagementEntry method=saveAndExit with sign=on and, for Verify &
 * Sign, verify=on) > the chart closes and reloads the day sheet. Also Schedule > Search > Master Record >
 * E-Chart (no appointment), where the Encounter Date is picked in the calendar and Save / Sign & Save are used.
 *
 * What the application does, read from CaseManagementManagerImpl.saveCaseManagementNote and ApptStatusData:
 * the note gets the signature line of its kind appended (ECHART_SIGN_LINE, or ECHART_VERSIGN_LINE when the
 * verify flag is on, which wins over the plain signature); the appointment's status keeps its first letter and
 * its second letter becomes S or V (t -> tS, tS -> tV, H -> HV); the day sheet draws "<second letter><icon of
 * the first letter>" (Sstarbill.gif, Vhere.gif) titled "<description>/Signed" or "<description>/Verified".
 * Notes carry the Encounter Date the clinician picked; the chart lists notes by it, oldest first. A date in
 * the future is stopped by the browser (alert "Observation date must be in the past" for a day after today)
 * and, if one still reaches the server, rolled back to the current time with encounter.futureDate.Msg.
 *
 * Asserted, in order, on an owned FAKE patient with two owned appointments on today's day sheet (To Do and
 * Here, the provider's own column, free slots inside the provider's hours):
 *   1. The day sheet lists both appointments with the icons of their stored statuses and an E link each.
 *   2. A chart opened from the Master Record carries no appointment (its appointmentNo is 0).
 *   3. Sign & Save on that chart signs the note (signature line, signing provider, no appointment number) and
 *      leaves both appointments, and their archive rows, exactly as they were (a digest taken before and after).
 *   4. Sign & Save from the To Do appointment's chart signs the note for that appointment and the status
 *      becomes tS (and the digest of step 3 does move, so it can tell).
 *   5. The day sheet then shows the Signed icon, title and status link for it.
 *   6. Verify & Sign from the same appointment's chart writes a signed note carrying the verify signature line
 *      (and not the plain one) and the status becomes tV (V takes S's place).
 *   7. The day sheet shows the Verified icon, title and status link.
 *   8. Verify & Sign from the Here appointment's chart (never signed) makes the status HV, the note signed with
 *      the verify line, and the day sheet shows Vhere.gif titled "Here/Verified".
 *   9. After charts were opened from appointments, a chart opened from the Master Record again carries no
 *      appointment and signing there changes neither appointment (the session still remembers the others).
 *  10. A back-dated Encounter Date, picked in the calendar and saved with Save, is stored exactly as shown.
 *  11. The same on a note signed with Sign & Save.
 *  12. Reopening the chart lists every note in Encounter Date order, oldest first: the two back-dated notes,
 *      written last (one unsigned, the chart's editor; one signed), come first, each showing its date.
 *  13. A future day picked in the calendar is stopped in the browser with the past-date message, posts
 *      no save and writes no note.
 *  14. A future Encounter Date that reaches the server (the browser's check is stepped around by rewriting the
 *      posted date in flight, because it only refuses days after today) is stored as the current time, not the
 *      future date.
 *  15. The encounter type picked in the chart's combo box is stored with the note (Save) and shown when the
 *      chart is reopened.
 *  16. LAST, pinned to finding 223: the clinician is told with encounter.futureDate.Msg ("Observation Date set
 *      in future, rolled back to current time") when Save rolls the date back. Today Save says nothing; the
 *      message only appears after Sign & Save has saved and signed the note. A script stops at its first failing
 *      step, so the pinned step is the last one.
 *
 * ECHART_VERIFY_PIN=archive (entry echart-note-verify-appointment-status-archive) runs steps 1 and 4 and then
 * the pin of finding 224: the appointmentArchive row a chart sign writes holds the status the appointment was
 * given, not the one it replaced (the other writers archive first).
 *
 * Fixtures: the workflow's owned FAKE patient; two appointment rows (marker in the reason); the notes written
 * through the chart. Cleanup deletes, by the patient key, every note (with its link, extension and issue
 * rows), draft, note lock and eChart row of the patient, the appointment rows and their archive rows, and
 * asserts them all gone. No clinic-wide state is changed.
 *
 * Expected: every step passes except the pinned one of each entry (see the manifest); a step named in an
 * expectedFailure contains only the assertion its finding breaks.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const TIMEOUT = 30000;
const NOTE = 'textarea[name="caseNote_note"]';
const SIGN_SAVE = '#signSaveImg';
const VERIFY_SIGN = '#signVerifyImg';
const SAVE = '#saveImg';
/** The CARLOS encounter types offered by noteIssueList.jsp's combo box (group types need group notes enabled). */
const ENCOUNTER_TYPE = 'email encounter with client';

/** The step labels the manifest's expectedFailure entries name; one place, so the script and the manifest agree. */
const STEP = {
  told: 'the clinician is told with encounter.futureDate.Msg when Save rolls a future Encounter Date back',
  archive: 'the appointment archive row Sign & Save writes holds the status the appointment had before, not the one it was given',
};

// ECHART_VERIFY_PIN selects which finding the run pins, because a script stops at its first failing step and so
// cannot pin two defects in one run. Unset: the full flow, pinned (last) to finding 223 (Save rolls a future
// Encounter Date back without the message). `archive` (entry echart-note-verify-appointment-status-archive): the
// day sheet control, Sign & Save from the To Do appointment, then finding 224. The variant runs only the steps its
// finding needs.
const PIN = (process.env.ECHART_VERIFY_PIN || '').trim();
/** ECHART_VERIFY_PIN must be unset or archive. Judged when the check runs (workflow), never when the module is required. */
function validatePin(value = PIN) {
  if (!['', 'archive'].includes(value)) throw new Error(`ECHART_VERIFY_PIN must be unset or archive, not ${value}`);
}

/** English bundle text for a key, read from the source tree (the same file the application's tests use). */
function bundleMessage(key) {
  const bundle = path.join(__dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties');
  const line = fs.readFileSync(bundle, 'utf8').split(/\r?\n/).find(candidate => candidate.startsWith(`${key}=`)
    || candidate.startsWith(`${key} =`));
  h.assert(line, `The English resource bundle has no ${key}, so the expected text cannot be read`);
  return line.slice(line.indexOf('=') + 1).trim();
}
const escapeRegExp = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * The E link stores the appointment number in the session with a fire-and-forget fetch
 * (schedulePage.js.jsp storeApptNo, POST /provider/ViewStoreApptInSession) whose body nothing reads, and
 * Chromium reports that request as ERR_ABORTED when the day sheet is reloaded, which this check does after
 * every chart. That is the browser abandoning an unread response, not an application failure (the number is
 * proven stored by the chart carrying the appointment). Excuse exactly those entries and nothing else.
 */
function excuseStoreAppointmentAborts(recorder) {
  for (let i = recorder.requestFailures.length - 1; i >= 0; i--) {
    const entry = recorder.requestFailures[i];
    if (entry.resourceType === 'fetch' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/provider/ViewStoreApptInSession')) {
      recorder.requestFailures.splice(i, 1);
    }
  }
}

async function workflow(s) {
  validatePin();
  const full = PIN === '';
  const { sql, patient, marker, provider, config, schedule } = s;
  const q = h.sqlString;
  const signedLabel = bundleMessage('encounter.class.EctSaveEncounterAction.msgSigned');
  const verifiedLabel = bundleMessage('encounter.class.EctSaveEncounterAction.msgVerAndSig');
  const byLabel = bundleMessage('encounter.class.EctSaveEncounterAction.msgSigBy');
  const pastDateMessage = bundleMessage('encounter.pastObservationDateError.msg');
  const futureDateMessage = bundleMessage('encounter.futureDate.Msg');

  // ---- Fixtures --------------------------------------------------------------------------------
  const appointments = {};
  s.cleanup(() => {
    // The patient is owned by this run, so everything below is the run's, deleted by the patient key.
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM appointmentArchive WHERE demographic_no=${patient};
      DELETE FROM appointment WHERE demographic_no=${patient} AND reason LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})`) === '0',
    'Owned chart notes, drafts, locks, appointments or archive rows were not removed');
  });

  const [[today, startHour, endHour, everyMin]] = sql.rows(`SELECT CURDATE(), COALESCE(MAX(startHour),8),
    COALESCE(MAX(endHour),18), COALESCE(MAX(everyMin),15) FROM ProviderPreference WHERE providerNo=${q(provider)}`);
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(today), 'The database did not report today');
  const [year, month, day] = today.split('-').map(Number);
  const slotLength = Number(everyMin) * 60;
  /** The icon file the application draws for a status code: "<second letter><icon of the first letter>". */
  const statusIcons = Object.fromEntries(sql.rows('SELECT status, icon, description FROM appointment_status')
    .map(([status, icon, description]) => [status, { icon, description }]));
  const expectedIcon = (status) => {
    const base = statusIcons[status[0]];
    h.assert(base, `The appointment_status table has no row for the status letter ${status[0]}`);
    return { file: `${status.length === 2 ? status[1] : ''}${base.icon}`,
      title: `${base.description}${status.length === 2 ? `/${status[1] === 'S' ? 'Signed' : 'Verified'}` : ''}` };
  };

  /** Book an owned appointment for the patient in the next free slot of the provider's day, status as given. */
  function bookAppointment(status, label, fromHour) {
    for (let candidate = fromHour; candidate < Number(endHour); candidate++) {
      const start = `${String(candidate).padStart(2, '0')}:00:00`;
      const end = sql.value(`SELECT ADDTIME(${q(start)}, SEC_TO_TIME(${slotLength}))`);
      if (sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${q(provider)} AND appointment_date=CURDATE()
        AND start_time<${q(end)} AND end_time>${q(start)}`) !== '0') continue;
      const id = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
          notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, lastupdateuser)
        VALUES (${q(provider)}, CURDATE(), ${q(start)}, ${q(end)}, ${q(`${marker},Workflow`)}, ${patient},
          '', ${q(`${marker} ${label}`)}, '', '', NULL, '', '', ${q(status)}, NOW(), NOW(), ${q(config.testUser)}, ${q(provider)});
        SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id), `The ${label} appointment fixture was not created`);
      return { id, candidate };
    }
    throw new h.SkipCheck(`No free slot inside the provider's hours today for the ${label} appointment`);
  }
  const todo = bookAppointment('t', 'to-do visit', Number(startHour) + 2);
  const here = bookAppointment('H', 'here visit', todo.candidate + 1);
  appointments.todo = todo.id;
  appointments.here = here.id;

  // ---- Database helpers ------------------------------------------------------------------------
  const statusOf = id => sql.value(`SELECT status FROM appointment WHERE appointment_no=${id}`);
  /** One digest of every column a note could change on the patient's appointments, plus their archive rows. */
  const appointmentsFingerprint = () => sql.value(`SELECT CONCAT(
    (SELECT COALESCE(MD5(GROUP_CONCAT(CONCAT_WS('|', appointment_no, status, provider_no, appointment_date, start_time,
      end_time, name, demographic_no, notes, reason, type, style, billing, location, resources, urgency, remarks,
      program_id, updatedatetime, lastupdateuser) ORDER BY appointment_no)), 'none')
      FROM appointment WHERE demographic_no=${patient}), '/',
    (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient}))`);
  /** The note whose text carries `text`; the check gives every note its own text. */
  const noteWith = (text) => {
    const rows = sql.rows(`SELECT note_id, signed, signing_provider_no, provider_no, appointmentNo, encounter_type,
      DATE_FORMAT(observation_date, '%Y-%m-%d %H:%i:%s'), TIMESTAMPDIFF(SECOND, observation_date, NOW()), note
      FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${q(`%${text}%`)} ORDER BY note_id`);
    h.assert(rows.length <= 1, `More than one note holds the text of this step (${rows.length})`);
    if (!rows.length) return null;
    const [id, signed, signer, owner, appointment, encounterType, observed, secondsAgo, body] = rows[0];
    return { id, signed, signer, owner, appointment, encounterType, observed, secondsAgo: Number(secondsAgo), body };
  };
  const noteCount = () => Number(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`));
  /** The note is a durable row (polled: the chart answers before the page is done with it). */
  async function waitForNote(text, message) {
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND note LIKE ${q(`%${text}%`)}`, '1', message);
    return noteWith(text);
  }
  const locks = () => sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}`);
  const signatureName = sql.value(`SELECT COALESCE(NULLIF(TRIM(e.signature), ''), CONCAT(p.first_name, ' ', p.last_name))
    FROM provider p LEFT JOIN providerExt e ON e.provider_no = p.provider_no WHERE p.provider_no=${q(provider)}`);
  h.assert(signatureName, 'The test provider has no signature name');
  /** The signature line the application appends, as the stored note's last line. */
  const signatureLine = body => body.replace(/\s+$/, '').split('\n').pop();
  const signatureShape = label => new RegExp(`^\\[${escapeRegExp(label)} \\d{2}-[A-Z][a-z]{2}-\\d{4} \\d{1,2}:\\d{2} ${escapeRegExp(byLabel)} ${escapeRegExp(signatureName)}\\]$`);

  // ---- Page helpers ----------------------------------------------------------------------------
  async function reloadDaySheet() {
    await schedule.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await schedule.reload({ waitUntil: 'domcontentloaded' });
    await schedule.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await schedule.locator('span.dateAppointment').waitFor({ timeout: TIMEOUT });
    excuseStoreAppointmentAborts(s.recorder);
  }
  const statusLink = id => schedule.locator(`a.apptStatus[onclick*="appointment_no=${id}&"]`);
  /** The status letter's link, icon and titles the day sheet shows for an appointment. */
  async function daySheetStatus(id) {
    await reloadDaySheet();
    const link = statusLink(id);
    await link.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await link.count() === 1, `The day sheet does not show the status letter of appointment ${id} exactly once`);
    const image = link.locator('img');
    const onclick = await link.getAttribute('onclick');
    return {
      image: new URL(await image.getAttribute('src'), schedule.url()).pathname.split('/').pop(),
      alt: await image.getAttribute('alt'),
      title: await link.getAttribute('title'),
      current: new URLSearchParams((onclick.match(/\?([^']*)'/) || [])[1]?.replace(/&amp;/g, '&')).get('currentstatus'),
    };
  }
  async function assertDaySheetShows(id, status) {
    const shown = await daySheetStatus(id);
    const expected = expectedIcon(status);
    h.assert(shown.current === status, `The day sheet's status link for appointment ${id} carries status ${shown.current}, the row holds ${status}`);
    h.assert(shown.image === expected.file,
      `The day sheet draws ${shown.image} for status ${status}, expected ${expected.file}`);
    h.assert(shown.alt === expected.title && shown.title === expected.title,
      `The day sheet titles status ${status} as "${shown.title}" / "${shown.alt}", expected "${expected.title}"`);
  }

  const noteField = chart => chart.locator(NOTE).first();
  /** The listed note's whole block (text, Encounter Date, editors, Enc Type), found by its note id. */
  const noteContainer = (chart, id) => chart.locator('div[id^="nc"]').filter({ has: chart.locator(`div#n${id}`) }).first();
  /** Open the chart from an appointment's E link on the day sheet. */
  async function openAppointmentChart(id) {
    await reloadDaySheet();
    const link = schedule.locator(`a.encounterBtn[onclick*=",${id});"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    const chart = await ui.clickOpensPopup(schedule, link, {
      context: s.context, recorder: s.recorder, label: 'appointment-chart', timeout: TIMEOUT });
    await noteField(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await chart.locator('input[name="appointmentNo"]').first().inputValue() === id,
      `The chart opened from the E link does not carry appointment ${id}`);
    return chart;
  }
  /** Leave a chart that stays open after Save: release its note lock, then close the window. */
  async function leaveChart(chart, savedNoteId = null) {
    // The ajax save re-renders the notes and only then binds the saved note's id into the form; the lock is
    // released by that id, so release before the binding and the new note's lock stays behind.
    if (savedNoteId) {
      await chart.waitForFunction(id => document.querySelector('input[name="noteId"]')?.value === id, savedNoteId, { timeout: TIMEOUT });
    }
    await releaseChartLocks(s.context, config.baseUrl, [chart]);
    await chart.close();
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}`, '0',
      'Leaving the chart left a note lock behind');
  }
  const chartSaveRequest = method => request => request.method() === 'POST' && /\/CaseManagementEntry(\?|$)/.test(request.url())
    && new URLSearchParams(request.postData() || '').get('method') === method;
  /** Press a toolbar button that submits the page and closes the chart window (saveAndExit). */
  async function pressAndExit(chart, button, opener = null, method = 'saveAndExit') {
    const closed = chart.waitForEvent('close', { timeout: TIMEOUT });
    closed.catch(() => {});
    // close.jsp reloads the window that opened the chart. Wait for that reload to finish, or the next
    // navigation of the day sheet collides with it (page.reload: net::ERR_ABORTED).
    const openerReloaded = opener ? opener.waitForEvent('load', { timeout: TIMEOUT }) : null;
    openerReloaded?.catch(() => {});
    const [response] = await Promise.all([
      chart.waitForResponse(response => chartSaveRequest(method)(response.request()), { timeout: TIMEOUT }),
      chart.locator(button).first().click({ timeout: TIMEOUT }).catch((error) => {
        // The window closes under the click when the save succeeds; that is the outcome, not an error.
        if (!chart.isClosed()) throw error;
      }),
    ]);
    h.assert(response.status() < 400, `${button} answered HTTP ${response.status()}`);
    await closed;
    if (openerReloaded) {
      await openerReloaded;
      await opener.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      excuseStoreAppointmentAborts(s.recorder);
    }
  }
  /** Press Save (ajax) and return once the chart has re-rendered with the saved note. */
  async function pressSave(chart) {
    const [response] = await Promise.all([
      chart.waitForResponse(response => chartSaveRequest('save')(response.request()), { timeout: TIMEOUT }),
      chart.locator(SAVE).first().click({ timeout: TIMEOUT }),
    ]);
    h.assert(response.status() < 400, `Save answered HTTP ${response.status()}`);
    return response;
  }

  // ---- Steps -----------------------------------------------------------------------------------
  await s.step('the day sheet lists both owned appointments with the icons of their stored statuses and an E link each', async () => {
    h.assert(statusOf(appointments.todo) === 't' && statusOf(appointments.here) === 'H', 'The appointment fixtures do not hold the statuses this step expects');
    await assertDaySheetShows(appointments.todo, 't');
    await assertDaySheetShows(appointments.here, 'H');
    for (const id of [appointments.todo, appointments.here]) {
      h.assert(await schedule.locator(`a.encounterBtn[onclick*=",${id});"]`).count() === 1, `Appointment ${id} has no E link on the day sheet`);
    }
  });

  if (full) {
    await s.step('a chart opened from the Master Record carries no appointment', async () => {
      const chart = await s.chart();
      await noteField(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
      h.assert(await chart.locator('input[name="appointmentNo"]').first().inputValue() === '0',
        'The chart opened from the Master Record carries an appointment number');
    });

    await s.step('Sign & Save on a chart with no appointment signs the note and changes no appointment', async () => {
      const chart = await s.chart();
      const text = `${marker} no-appointment sign one`;
      const before = appointmentsFingerprint();
      await noteField(chart).fill(text);
      await pressAndExit(chart, SIGN_SAVE, s.master);
      const note = await waitForNote(text, 'Sign & Save did not write the note');
      h.assert(note.signed === '1' && note.signer === provider, `The note was not signed by the provider (${note.signed}/${note.signer})`);
      h.assert(note.appointment === '0' || note.appointment === 'NULL', `The note carries appointment ${note.appointment}`);
      h.assert(signatureShape(signedLabel).test(signatureLine(note.body)), 'The note does not end with the signature line');
      h.assert(appointmentsFingerprint() === before, 'Signing a note with no appointment changed an appointment row or wrote an archive row');
    });
  }

  await s.step('Sign & Save from the To Do appointment\'s chart signs the note for it and the status becomes tS', async () => {
    const chart = await openAppointmentChart(appointments.todo);
    const text = `${marker} signed from the to-do visit`;
    const before = appointmentsFingerprint();
    await noteField(chart).fill(text);
    await pressAndExit(chart, SIGN_SAVE, schedule);
    const note = await waitForNote(text, 'Sign & Save did not write the note');
    h.assert(note.signed === '1' && note.signer === provider, `The note was not signed by the provider (${note.signed}/${note.signer})`);
    h.assert(note.appointment === appointments.todo, `The note carries appointment ${note.appointment}, expected ${appointments.todo}`);
    h.assert(signatureShape(signedLabel).test(signatureLine(note.body)), 'The note does not end with the Signed signature line');
    await expectValue(sql, `SELECT status FROM appointment WHERE appointment_no=${appointments.todo}`, 'tS',
      `Signing did not make the appointment's status tS (it is ${statusOf(appointments.todo)})`);
    h.assert(statusOf(appointments.here) === 'H', 'Signing from one appointment changed the other appointment');
    // Control for the no-appointment steps: the digest they rely on does move when an appointment is signed.
    h.assert(appointmentsFingerprint() !== before, 'The appointment digest did not change when an appointment was signed');
  });

  if (!full) {
    // Pinned to finding 224. AppointmentUpdateRecord2Action and AppointmentStatusTransitionService archive the
    // appointment BEFORE changing it, so the archive row is the version being replaced; the chart's Sign path
    // (CaseManagementManagerImpl.saveCaseManagementNote) sets the new status first and archives afterwards.
    await s.step(STEP.archive, async () => {
      const rows = sql.rows(`SELECT status FROM appointmentArchive WHERE appointment_no=${appointments.todo} ORDER BY id`).map(([status]) => status);
      h.assert(rows.includes('t'), `The appointment archive holds [${rows.join(', ')}] for an appointment that was t and became tS: the status it replaced is in no archive row`);
    });
    return;
  }

  await s.step('the day sheet shows the Signed icon, title and status link for the signed appointment', async () => {
    await assertDaySheetShows(appointments.todo, 'tS');
    await assertDaySheetShows(appointments.here, 'H');
  });

  await s.step('Verify & Sign from the same appointment writes a signed note with the verify line and the status becomes tV', async () => {
    const chart = await openAppointmentChart(appointments.todo);
    const text = `${marker} verified from the to-do visit`;
    await noteField(chart).fill(text);
    await pressAndExit(chart, VERIFY_SIGN, schedule);
    const note = await waitForNote(text, 'Verify & Sign did not write the note');
    h.assert(note.signed === '1' && note.signer === provider, `The verified note was not signed by the provider (${note.signed}/${note.signer})`);
    h.assert(note.appointment === appointments.todo, `The note carries appointment ${note.appointment}, expected ${appointments.todo}`);
    h.assert(signatureShape(verifiedLabel).test(signatureLine(note.body)),
      `The note does not end with the Verified and Signed line: "${signatureLine(note.body)}"`);
    h.assert(!note.body.includes(`[${signedLabel} `), 'The verified note also carries the plain Signed line');
    await expectValue(sql, `SELECT status FROM appointment WHERE appointment_no=${appointments.todo}`, 'tV',
      `Verify & Sign did not make the appointment's status tV (it is ${statusOf(appointments.todo)})`);
  });

  await s.step('the day sheet shows the Verified icon, title and status link', async () => {
    await assertDaySheetShows(appointments.todo, 'tV');
  });

  await s.step('Verify & Sign from the Here appointment (never signed) makes the status HV and the day sheet shows it', async () => {
    const chart = await openAppointmentChart(appointments.here);
    const text = `${marker} verified from the here visit`;
    await noteField(chart).fill(text);
    await pressAndExit(chart, VERIFY_SIGN, schedule);
    const note = await waitForNote(text, 'Verify & Sign did not write the note');
    h.assert(note.signed === '1' && note.signer === provider, `The verified note was not signed by the provider (${note.signed}/${note.signer})`);
    h.assert(note.appointment === appointments.here, `The note carries appointment ${note.appointment}, expected ${appointments.here}`);
    h.assert(signatureShape(verifiedLabel).test(signatureLine(note.body)), 'The note does not end with the Verified and Signed line');
    await expectValue(sql, `SELECT status FROM appointment WHERE appointment_no=${appointments.here}`, 'HV',
      `Verify & Sign did not make the Here appointment's status HV (it is ${statusOf(appointments.here)})`);
    h.assert(statusOf(appointments.todo) === 'tV', 'Verifying one appointment changed the other appointment');
    await assertDaySheetShows(appointments.here, 'HV');
  });

  await s.step('after charts were opened from appointments, a chart from the Master Record still signs with no appointment and changes none', async () => {
    const chart = await s.chart();
    h.assert(await chart.locator('input[name="appointmentNo"]').first().inputValue() === '0',
      'The Master Record chart carries an appointment number after charts were opened from appointments');
    const text = `${marker} no-appointment sign two`;
    const before = appointmentsFingerprint();
    await noteField(chart).fill(text);
    await pressAndExit(chart, SIGN_SAVE, s.master);
    const note = await waitForNote(text, 'Sign & Save did not write the note');
    h.assert(note.signed === '1' && (note.appointment === '0' || note.appointment === 'NULL'),
      `The note was not signed with no appointment (${note.signed}/${note.appointment})`);
    h.assert(appointmentsFingerprint() === before, 'Signing a note with no appointment changed an appointment row or wrote an archive row');
  });

  // ---- Encounter Date --------------------------------------------------------------------------
  const backDated = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 3 DAY)');
  const backDatedText = `${marker} back-dated note`;
  let backDatedShown;
  await s.step('a back-dated Encounter Date picked in the calendar and saved is stored exactly as shown', async () => {
    const chart = await s.chart();
    await noteField(chart).fill(backDatedText);
    backDatedShown = await ui.pickDate(chart, '#observationDate', backDated, { timeout: TIMEOUT });
    h.assert(await chart.locator('#observationDate').inputValue() === backDatedShown, 'The Encounter Date field changed after it was picked');
    await pressSave(chart);
    const note = await waitForNote(backDatedText, 'Save did not write the back-dated note');
    h.assert(note.observed.startsWith(backDated), `The note's observation_date is ${note.observed}, not on ${backDated}`);
    const stored = sql.value(`SELECT DATE_FORMAT(observation_date, '%d-%b-%Y %H:%i') FROM casemgmt_note WHERE note_id=${note.id}`);
    h.assert(stored === backDatedShown, `The note's observation_date reads "${stored}", the field showed "${backDatedShown}"`);
    h.assert(note.signed === '0', 'Save signed the note');
    await leaveChart(chart, note.id);
  });

  const signedBackDated = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 5 DAY)');
  const signedBackDatedText = `${marker} back-dated signed note`;
  let signedBackDatedShown;
  await s.step('a back-dated Encounter Date on a note signed with Sign & Save is stored exactly as shown', async () => {
    const chart = await s.chart();
    // The provider's unsigned back-dated note is the chart's editor; start a fresh note for this one.
    await chart.locator('#newNoteImg').click({ timeout: TIMEOUT });
    await noteField(chart).fill(signedBackDatedText);
    signedBackDatedShown = await ui.pickDate(chart, '#observationDate', signedBackDated, { timeout: TIMEOUT });
    await pressAndExit(chart, SIGN_SAVE, s.master);
    const note = await waitForNote(signedBackDatedText, 'Sign & Save did not write the back-dated note');
    h.assert(note.signed === '1' && note.signer === provider, `The note was not signed by the provider (${note.signed}/${note.signer})`);
    const stored = sql.value(`SELECT DATE_FORMAT(observation_date, '%d-%b-%Y %H:%i') FROM casemgmt_note WHERE note_id=${note.id}`);
    h.assert(note.observed.startsWith(signedBackDated) && stored === signedBackDatedShown,
      `The signed note's observation_date reads "${stored}", the field showed "${signedBackDatedShown}" (${signedBackDated})`);
  });

  await s.step('reopening the chart lists every note in Encounter Date order, oldest first, back-dated notes included, each with its date', async () => {
    const chart = await s.chart();
    const backDatedId = noteWith(backDatedText).id;
    const signedBackDatedId = noteWith(signedBackDatedText).id;
    await chart.locator(`div#n${backDatedId}`).waitFor({ timeout: TIMEOUT });
    const listed = await chart.evaluate(() => [...document.querySelectorAll('div[id^="nc"]')]
      .map(container => (container.querySelector('div[id^="n"]:not([id^="nc"])') || {}).id || '')
      .filter(id => /^n[1-9]\d*$/.test(id)).map(id => id.slice(1)));
    // Oldest first by observation_date, not by the order the notes were written: the two back-dated notes were
    // written last, one of them unsigned (the chart's editor) and one signed, and must still come first.
    const expected = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}
      ORDER BY observation_date ASC, note_id ASC`).map(([id]) => id);
    h.assert(expected.length >= 7 && expected[0] === signedBackDatedId && expected[1] === backDatedId,
      'The fixture notes are not the shape this step expects (the two back-dated notes oldest)');
    h.assert(JSON.stringify(listed) === JSON.stringify(expected),
      `The chart lists the notes as ${listed.join(',')}, in Encounter Date order they are ${expected.join(',')}`);
    // The unsigned note is listed in edit mode, where the date is the field's value, not text.
    for (const [id, shownDate] of [[backDatedId, backDatedShown], [signedBackDatedId, signedBackDatedShown]]) {
      const container = noteContainer(chart, id);
      const field = container.locator('#observationDate');
      const shown = await field.count() ? await field.inputValue() : await container.innerText();
      h.assert(shown.includes(shownDate), `Note ${id} does not show its Encounter Date "${shownDate}": ${shown.replace(/\s+/g, ' ').slice(0, 200)}`);
    }
    await leaveChart(chart);
  });

  await s.step('a future day picked in the calendar is stopped in the browser with the past-date message and nothing is posted', async () => {
    const chart = await s.chart();
    const text = `${marker} future day note`;
    const tomorrow = sql.value('SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY)');
    const posted = [];
    // Saves only: the draft autosave posts to the same route on its own timer.
    const watch = request => { if (chartSaveRequest('save')(request)) posted.push(request); };
    await noteField(chart).fill(text);
    await ui.pickDate(chart, '#observationDate', tomorrow, { timeout: TIMEOUT });
    const before = noteCount();
    chart.on('request', watch);
    const dialogs = await h.withExpectedDialogs(chart, async () => {
      await chart.locator(SAVE).first().click({ timeout: TIMEOUT });
      await chart.waitForTimeout(1500);
    });
    chart.off('request', watch);
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === pastDateMessage,
      `Expected one alert "${pastDateMessage}", got ${JSON.stringify(dialogs.map(d => d.text))}`);
    h.assert(posted.length === 0, `The browser posted ${posted.length} request(s) to the chart despite refusing the date`);
    h.assert(noteCount() === before && noteWith(text) === null, 'A note was written for the refused future day');
    await leaveChart(chart);
  });

  const rolledBackText = `${marker} date rolled back`;
  const toldAbout = { dialogs: [], page: '', response: '' };
  await s.step('a future Encounter Date that reaches the server is stored as the current time, not the future date', async () => {
    const chart = await s.chart();
    await noteField(chart).fill(rolledBackText);
    // The browser refuses a day after today, so the posted date is rewritten in flight to tomorrow. The
    // same request is what the browser sends for a time later today (the calendar's default time is 12:00).
    const tomorrow = sql.value(`SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '%d-%b-%Y 09:00')`);
    await chart.route('**/CaseManagementEntry*', async (route) => {
      const params = new URLSearchParams(route.request().postData() || '');
      if (route.request().method() === 'POST' && params.get('method') === 'save') {
        params.set('observation_date', tomorrow);
        await route.continue({ postData: params.toString() });
      } else {
        await route.continue();
      }
    });
    toldAbout.dialogs = (await h.withExpectedDialogs(chart, async () => {
      const response = await pressSave(chart);
      toldAbout.response = await response.text();
      await chart.waitForTimeout(2000);
    })).map(dialog => dialog.text);
    toldAbout.page = await chart.locator('body').innerText();
    await chart.unroute('**/CaseManagementEntry*');
    const note = await waitForNote(rolledBackText, 'Save did not write the note whose date reached the server in the future');
    h.assert(note.secondsAgo >= 0 && note.secondsAgo < 300,
      `The note's observation_date is ${note.observed} (${note.secondsAgo}s before now), not the current time`);
    await leaveChart(chart, note.id);
  });

  // ---- Encounter type --------------------------------------------------------------------------
  const typedText = `${marker} encounter type note`;
  await s.step('the encounter type picked in the combo box is stored with the note and shown when the chart is reopened', async () => {
    const chart = await s.chart();
    // The provider's earlier unsaved-to-sign notes open in the editor, so start a fresh note with the New button.
    await chart.locator('#newNoteImg').click({ timeout: TIMEOUT });
    await noteField(chart).fill(typedText);
    const combos = chart.locator('input.encTypeCombo[id$="_combo"]');
    await combos.first().waitFor({ state: 'attached', timeout: TIMEOUT });
    const combo = combos.filter({ visible: true });
    h.assert(await combo.count() === 1, `The chart offers ${await combo.count()} visible encounter type boxes, not one`);
    const comboId = await combo.getAttribute('id');
    const selectId = comboId.replace(/_combo$/, '');
    await combo.click({ timeout: TIMEOUT });
    const option = chart.locator(`#${selectId}_options`).getByText(ENCOUNTER_TYPE, { exact: true });
    await option.first().click({ timeout: TIMEOUT });
    h.assert(await chart.locator(`#${selectId}`).inputValue() === ENCOUNTER_TYPE, 'Picking the type did not set the encounter type field');
    await pressSave(chart);
    const note = await waitForNote(typedText, 'Save did not write the note');
    h.assert(note.encounterType === ENCOUNTER_TYPE, `The note's encounter_type is "${note.encounterType}", not "${ENCOUNTER_TYPE}"`);
    await leaveChart(chart, note.id);
    const reopened = await s.chart();
    // Unsigned notes of the provider are listed in edit mode, where the type is the select's value.
    const container = noteContainer(reopened, note.id);
    const select = container.locator('select[name="caseNote.encounter_type"]');
    const shown = await select.count() ? await select.inputValue() : await container.innerText();
    h.assert(shown.includes(ENCOUNTER_TYPE), `The reopened note does not show its encounter type: ${shown.replace(/\s+/g, ' ').slice(0, 200)}`);
    await leaveChart(reopened);
  });

  // LAST, because a script stops at its first failing step: pinned to the finding in the manifest entry.
  await s.step(STEP.told, async () => {
    const told = toldAbout.dialogs.some(text => text.includes(futureDateMessage))
      || toldAbout.page.includes(futureDateMessage) || toldAbout.response.includes(futureDateMessage);
    h.assert(told, `Save rolled the date back without showing "${futureDateMessage}" (alerts: ${JSON.stringify(toldAbout.dialogs)}; `
      + 'neither the chart page nor the save response contains it)');
  });
}

module.exports = { workflow, STEP, validatePin };
if (require.main === module) runWorkflow('echart-note-verify-appointment-status', workflow, { openPatient: true });
