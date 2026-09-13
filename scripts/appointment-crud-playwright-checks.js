#!/usr/bin/env node
/*
 * Browser CRUD checks for the CARLOS appointment / scheduling interface.
 *
 * WHAT THIS COVERS THAT NOTHING ELSE DID. `schedule-links-playwright-checks.js`
 * proves the schedule's top-nav links render and `schedule-setting-*` covers
 * template administration, but until this script nothing drove the workflow the
 * front desk spends its day in: book an appointment into a slot, find it on the
 * schedule, edit it, advance its status, cancel it, delete it. Every one of
 * `appointment/AddRecord`, `appointment/UpdateRecord`, `appointment/DeleteRecord`
 * and the day view's `dboperation=updateapptstatus` rotation was unexercised,
 * so a regression in any of them reached an operator before it reached CI.
 *
 * It reaches each surface the way the front desk does — an empty-slot link on
 * the day view opens the add popup, the patient is chosen through the keyword
 * search, the booked appointment is reopened through its own link on the
 * schedule — because the add and edit popups behave differently when opened
 * cold with a hand-built URL, and the cold shape is the one that already works.
 *
 * DELETE IS ARCHIVE-THEN-REMOVE. AppointmentDeleteRecord2Action writes an
 * `appointmentArchive` row before removing the `appointment` row, so the check
 * asserts both halves: a delete that silently stopped archiving would otherwise
 * look identical to a working one, and the archive is the only record a clinic
 * has of a booking that was removed.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:appointment-crud-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   APPOINTMENT_DEMOGRAPHIC_NO=1     patient to book (must exist and be active)
 *   APPOINTMENT_PROVIDER_NO=999998   provider whose day view is driven
 *   APPOINTMENT_DAYS_AHEAD=400       how far out to book, to stay clear of demo data
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Cleanup: the script books exactly one appointment, stamps its reason and notes
 * with a unique PW_APPT_CRUD_<millis> marker, and deletes every appointment and
 * appointmentArchive row carrying that marker in a finally — including after a
 * failure — so repeat runs stay clean.
 */

const { chromium } = require('playwright');
const {
  acceptNextDialog,
  clearPendingDialogAccepts,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  createRecorder,
  createSqlClient,
  escapeSql,
  getLaunchOptions,
  gotoApp,
  login,
  readConfig,
  readCsrfToken,
  requireId,
  waitFor,
  wirePage,
} = require('./carlos-playwright-harness');

const config = readConfig();
const demographicNo = requireId(process.env.APPOINTMENT_DEMOGRAPHIC_NO || '1', 'APPOINTMENT_DEMOGRAPHIC_NO');
const providerNo = requireId(process.env.APPOINTMENT_PROVIDER_NO || '999998', 'APPOINTMENT_PROVIDER_NO');
const daysAhead = Number(process.env.APPOINTMENT_DAYS_AHEAD || '400');
assert(Number.isInteger(daysAhead) && daysAhead > 0 && daysAhead < 3650,
  `APPOINTMENT_DAYS_AHEAD must be a day count between 1 and 3649, got ${process.env.APPOINTMENT_DAYS_AHEAD}`);

const stamp = `PW_APPT_CRUD_${Date.now()}`;
const createdReason = `${stamp} booked`;
const createdNotes = `${stamp} notes`;
const editedReason = `${stamp} edited`;
const editedNotes = `${stamp} notes edited`;

const recorder = createRecorder();
const sql = createSqlClient({ namespace: 'carlos-appt-crud' });
const passed = [];

// Booking far enough out that the demo dataset's own appointments cannot occupy
// the slot the check clicks; a same-week date made this flaky against the
// packaged demo data rather than against the code.
const target = new Date(Date.now() + daysAhead * 24 * 60 * 60 * 1000);
const targetYear = target.getUTCFullYear();
const targetMonth = target.getUTCMonth() + 1;
const targetDay = target.getUTCDate();
const targetDate = `${targetYear}-${String(targetMonth).padStart(2, '0')}-${String(targetDay).padStart(2, '0')}`;

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function stampedAppointments() {
  return sql.rows(
    `SELECT appointment_no, provider_no, appointment_date, start_time, end_time, demographic_no,`
    + ` status, reason, notes, creator FROM appointment`
    + ` WHERE reason LIKE '${escapeSql(`${stamp}%`)}' OR notes LIKE '${escapeSql(`${stamp}%`)}'`
    + ` ORDER BY appointment_no`
  ).map(([id, provider, date, startTime, endTime, demographic, status, reason, notes, creator]) => ({
    id, provider, date, startTime, endTime, demographic, status, reason, notes, creator,
  }));
}

function stampedArchiveRows() {
  return sql.rows(
    `SELECT appointment_no, status, reason FROM appointmentArchive`
    + ` WHERE reason LIKE '${escapeSql(`${stamp}%`)}' OR notes LIKE '${escapeSql(`${stamp}%`)}'`
    + ` ORDER BY id`
  ).map(([id, status, reason]) => ({ id, status, reason }));
}

function cleanupRows() {
  const like = escapeSql(`${stamp}%`);
  sql.exec(`DELETE FROM appointmentArchive WHERE reason LIKE '${like}' OR notes LIKE '${like}'`);
  sql.exec(`DELETE FROM appointment WHERE reason LIKE '${like}' OR notes LIKE '${like}'`);
}

async function openDayView(context, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await navigateDayView(page, label);
  return page;
}

/**
 * (Re)navigates an existing page to the target day view.
 *
 * A plain reload() is not usable here: appointmentaddarecord.jsp calls
 * self.opener.refresh() on success, so the schedule is already navigating when
 * the check comes back to it and reload() aborts with a detached frame. A fresh
 * navigation is idempotent against that race.
 */
async function navigateDayView(page, label) {
  // displaymode/dboperation/viewall are not optional: providercontrol answers a
  // request carrying only year/month/day with HTTP 200 and an empty document, so
  // the full day-search parameter set the login landing page uses is what
  // actually renders a schedule.
  const query = {
    year: targetYear,
    month: targetMonth,
    day: targetDay,
    view: 0,
    displaymode: 'day',
    dboperation: 'searchappointmentday',
    viewall: 1,
  };
  // The schedule navigates itself from two directions the check does not
  // control: self.opener.refresh() from a popup that just saved, and the status
  // letter's own self-navigation. Either can abort a navigation issued at the
  // same moment, so one retry after the page has gone quiet is the difference
  // between a real failure and a race in the harness.
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await gotoApp(page, config.baseUrl, '/provider/providercontrol', 'domcontentloaded', query);
      await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
      await assertNotErrorPage(page, `${label} day view`);
      return;
    } catch (error) {
      if (attempt === 2 || !/ERR_ABORTED|frame was detached/.test(error.message)) {
        throw error;
      }
      await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
      await new Promise((resolve) => setTimeout(resolve, 750));
    }
  }
}

/**
 * Opens the add-appointment popup from an empty slot link.
 *
 * The slot link calls confirmPopupPage(), which raises a confirm() only for
 * slots whose template carries a confirmation message; the dialog is accepted
 * per-click rather than globally so the harness default stays "dismiss".
 */
async function openAddPopupFromSlot(context, dayPage) {
  const slots = dayPage.locator('a.adhour');
  const slotCount = await slots.count();
  assert(slotCount > 0, `day view for ${targetDate} rendered no bookable slot links`);

  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  const dialogPromise = acceptNextDialog(dayPage, recorder, 'slot-confirm');
  await slots.first().click();
  // Not every slot prompts -- only one whose template carries a confirmation
  // message does -- so the armed accept is dropped again if nothing consumed it,
  // or it would silently accept a later dialog this check means to dismiss.
  await Promise.race([dialogPromise, new Promise((resolve) => setTimeout(resolve, 1500))]);
  clearPendingDialogAccepts(dayPage);
  const popup = await popupPromise;
  wirePage(popup, 'add-appointment-popup', recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'add-appointment popup');
  return popup;
}

/**
 * Chooses the patient through the popup's own keyword search.
 *
 * This is the path the front desk uses and the reason it is driven here rather
 * than passing demographic_no in the URL: the search posts the whole
 * half-filled appointment form to DemographicSearch and the results page posts
 * it back, so a parameter this round trip drops is a booking field an operator
 * silently loses.
 */
async function choosePatientByKeyword(popup) {
  const surname = sql.scalar(`SELECT last_name FROM demographic WHERE demographic_no=${demographicNo}`);
  assert(surname, `APPOINTMENT_DEMOGRAPHIC_NO=${demographicNo} does not exist`);

  await popup.locator('#keyword').fill(surname);
  await Promise.all([
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#searchBtn').click(),
  ]);
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'appointment patient search results');

  const searchToken = await readCsrfToken(popup);
  assert(searchToken, 'appointment patient search results page carried no CSRFGuard token');

  // The result row selects through a submit input named demographic_no whose
  // value IS the patient id (the row's onclick sets the same field), not through
  // an anchor -- so the click has to land on that button for the id to travel
  // back to the booking form.
  const row = popup.locator(`table tr input[type="submit"][name="demographic_no"][value="${demographicNo}"]`).first();
  await row.waitFor({ state: 'visible', timeout: 30000 });
  await Promise.all([
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    row.click(),
  ]);
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'appointment form after patient selection');

  await popup.locator('#demographic_no').waitFor({ state: 'attached', timeout: 30000 });
  const selected = await popup.locator('#demographic_no').inputValue();
  assert(selected === demographicNo,
    `patient selection put demographic_no=${selected} on the booking form, expected ${demographicNo}`);
  return surname;
}

async function submitBooking(popup) {
  const slotStart = await popup.locator('form#addappt input[name="start_time"]').inputValue();
  assert(/^\d{2}:\d{2}/.test(slotStart), `booking form start_time was not prefilled from the slot, got ${slotStart}`);

  const token = await readCsrfToken(popup);
  assert(token, 'appointment booking form carried no CSRFGuard token');

  await popup.locator('#reason').fill(createdReason);
  await popup.locator('textarea[name="notes"]').fill(createdNotes);

  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/appointment/AddRecord'), { timeout: 45000 }),
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#addButton').click(),
  ]);
  assert(response.status() < 400, `appointment AddRecord returned HTTP ${response.status()}`);
  const confirmationText = await assertNotErrorPage(popup, 'appointment add confirmation');
  assert(!/addition has failed/i.test(confirmationText),
    `appointment add confirmation reported failure: ${confirmationText.slice(0, 200)}`);
  const selfClosed = await popup.evaluate(() => window.__carlosSelfCloseRequested === true);
  assert(selfClosed,
    'appointment add confirmation did not ask the popup to close, which is how the schedule learns the booking succeeded');

  const row = await waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 ? rows[0] : null;
  }, { description: `the booked ${stamp} appointment to reach the database` });

  assert(row.provider === providerNo, `booked appointment landed on provider ${row.provider}, expected ${providerNo}`);
  assert(row.date === targetDate, `booked appointment landed on ${row.date}, expected ${targetDate}`);
  assert(row.demographic === demographicNo,
    `booked appointment landed on demographic ${row.demographic}, expected ${demographicNo}`);
  assert(row.reason === createdReason, `booked appointment reason was ${row.reason}`);
  assert(row.notes === createdNotes, `booked appointment notes were ${row.notes}`);
  assert(row.startTime.startsWith(slotStart.slice(0, 5)),
    `booked appointment start_time ${row.startTime} did not match the clicked slot ${slotStart}`);
  return row;
}

/** Finds the booked appointment on the schedule and returns its edit-link handle. */
async function findOnSchedule(dayPage, appointmentNo) {
  await navigateDayView(dayPage, 'schedule');
  const link = dayPage.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 45000 });
  return link;
}

async function openEditPopup(context, dayPage, appointmentNo) {
  const link = await findOnSchedule(dayPage, appointmentNo);
  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  await link.click();
  const popup = await popupPromise;
  wirePage(popup, 'edit-appointment-popup', recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'edit-appointment popup');
  return popup;
}

async function editBooking(popup, appointmentNo) {
  const formAppointmentNo = await popup.locator('input[name="appointment_no"]').inputValue();
  assert(formAppointmentNo === String(appointmentNo),
    `edit popup opened appointment ${formAppointmentNo}, expected ${appointmentNo}`);
  const prefilledReason = await popup.locator('#reason').inputValue();
  assert(prefilledReason === createdReason,
    `edit popup prefilled reason ${prefilledReason}, expected the booked ${createdReason}`);

  assert(await readCsrfToken(popup), 'appointment edit form carried no CSRFGuard token');

  await popup.locator('#reason').fill(editedReason);
  await popup.locator('textarea[name="notes"]').fill(editedNotes);
  await popup.locator('#duration').fill('30');

  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/appointment/UpdateRecord'), { timeout: 45000 }),
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#updateButton').click(),
  ]);
  assert(response.status() < 400, `appointment UpdateRecord returned HTTP ${response.status()}`);
  await assertNotErrorPage(popup, 'appointment update confirmation');

  const row = await waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 && rows[0].reason === editedReason ? rows[0] : null;
  }, { description: 'the edited reason to reach the database' });
  assert(row.notes === editedNotes, `edited appointment notes were ${row.notes}`);
  assert(row.id === String(appointmentNo),
    `the edit created appointment ${row.id} instead of updating ${appointmentNo}`);

  // A 30-minute duration must move end_time; an edit that persisted the text
  // fields but dropped the recomputed end leaves a booking that looks right on
  // the form and wrong on the schedule.
  const startMinutes = Number(row.startTime.slice(0, 2)) * 60 + Number(row.startTime.slice(3, 5));
  const endMinutes = Number(row.endTime.slice(0, 2)) * 60 + Number(row.endTime.slice(3, 5));
  assert(endMinutes - startMinutes >= 29,
    `duration=30 left end_time ${row.endTime} only ${endMinutes - startMinutes} minutes after ${row.startTime}`);
  return row;
}

/**
 * Advances the appointment status from the day view's status letter.
 *
 * This is the one status path with no form behind it: the letter is a link that
 * self-navigates the schedule with dboperation=updateapptstatus, so it breaks
 * independently of the edit popup's status select.
 */
async function rotateStatusFromSchedule(dayPage, appointmentNo, statusBefore) {
  await navigateDayView(dayPage, 'schedule');
  const statusLink = dayPage.locator(`a.apptStatus[onclick*="appointment_no=${appointmentNo}"]`).first();
  if (await statusLink.count() === 0) {
    console.log('WARN day view rendered no status-rotation link for the booked appointment (no nextStatus configured); skipping the rotation check');
    return null;
  }
  await Promise.all([
    dayPage.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    statusLink.click(),
  ]);
  await dayPage.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(dayPage, 'day view after status rotation');

  const row = await waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 && rows[0].status !== statusBefore ? rows[0] : null;
  }, { description: `appointment status to advance away from ${statusBefore}` });
  return row;
}

async function cancelBooking(context, dayPage, appointmentNo) {
  const popup = await openEditPopup(context, dayPage, appointmentNo);
  // The status field has two shapes and which one renders is a deployment
  // property, not a code path the check can choose: editappointment.jsp only
  // builds the <select> when ENABLE_EDIT_APPT_STATUS=yes, and otherwise presents
  // status as free text that AppointmentUpdateRecord2Action persists verbatim.
  // Both shapes are driven so the check works on a default packaged install and
  // on a clinic that turned the property on.
  const statusSelect = popup.locator('select[name="status"]');
  const statusText = popup.locator('input[type="text"][name="status"]');
  if (await statusSelect.count() > 0) {
    const options = await statusSelect.locator('option').evaluateAll(
      (nodes) => nodes.map((node) => node.value));
    assert(options.some((value) => value === 'C' || value.startsWith('C')),
      `edit popup status select offered no Cancelled (C) option: ${JSON.stringify(options)}`);
    // A status outside the configured list leaves editappointment.jsp's
    // curSelect at -1; assert the current status IS in the list so a check
    // failure here names the cause instead of surfacing as a later 500.
    const current = await statusSelect.inputValue();
    assert(options.includes(current),
      `edit popup pre-selected status ${current} is not among the configured statuses ${JSON.stringify(options)}`);
    await statusSelect.selectOption(options.find((value) => value === 'C' || value.startsWith('C')));
  } else {
    assert(await statusText.count() > 0, 'edit popup rendered neither a status select nor a status text field');
    await statusText.fill('C');
  }
  await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/appointment/UpdateRecord'), { timeout: 45000 }),
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('#updateButton').click(),
  ]);
  await assertNotErrorPage(popup, 'appointment cancel confirmation');
  await popup.close().catch(() => {});

  const row = await waitFor(() => {
    const rows = stampedAppointments();
    return rows.length === 1 && rows[0].status === 'C' ? rows[0] : null;
  }, { description: 'appointment status to become Cancelled (C)' });
  return row;
}

async function deleteBooking(context, dayPage, appointmentNo) {
  const popup = await openEditPopup(context, dayPage, appointmentNo);
  const deleteButton = popup.locator('#deleteButton');
  assert(await deleteButton.count() > 0, 'edit popup rendered no delete control');

  // The delete confirm is raised by the form's own onSubmit, not by the click, so
  // the accept has to be queued before the click and awaited without a timeout
  // race: dismissing it cancels the submit and no POST is ever made.
  const dialogPromise = acceptNextDialog(popup, recorder, 'delete-confirm');
  const responsePromise = popup.waitForResponse((r) => r.request().method() === 'POST'
    && new URL(r.url()).pathname.endsWith('/appointment/DeleteRecord'), { timeout: 45000 });
  await deleteButton.click();
  const confirmText = await dialogPromise;
  assert(/delete/i.test(confirmText),
    `appointment delete raised an unexpected confirmation: ${confirmText}`);
  const response = await responsePromise;
  assert(response.status() < 400, `appointment DeleteRecord returned HTTP ${response.status()}`);
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(popup, 'appointment delete confirmation');
  await popup.close().catch(() => {});

  await waitFor(() => stampedAppointments().length === 0 ? true : null,
    { description: 'the deleted appointment row to disappear' });

  const archived = stampedArchiveRows();
  assert(archived.length >= 1,
    'appointment delete removed the appointment without writing an appointmentArchive row');
  assert(archived.some((entry) => entry.id === String(appointmentNo)),
    `appointmentArchive has no row for deleted appointment ${appointmentNo}: ${JSON.stringify(archived)}`);
}

(async () => {
  cleanupRows();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  // appointmentaddarecord.jsp calls self.close() on a SUCCESSFUL add, so the
  // confirmation page tears itself down before it can be read. Neutralising
  // window.close for this context keeps the page inspectable; the flag it sets
  // is asserted instead, because "the popup closed itself" IS the success
  // signal an operator sees.
  await context.addInitScript(() => {
    window.close = () => {
      window.__carlosSelfCloseRequested = true;
    };
  });
  try {
    await login(context, config, recorder);

    const dayPage = await openDayView(context, 'schedule');
    pass(`day view renders bookable slots for ${targetDate}`);

    const addPopup = await openAddPopupFromSlot(context, dayPage);
    pass('empty schedule slot opens the add-appointment popup prefilled from the slot');

    await choosePatientByKeyword(addPopup);
    pass('patient keyword search round trip returns to the booking form with the patient attached');

    const booked = await submitBooking(addPopup);
    await addPopup.close().catch(() => {});
    pass(`booking persisted appointment ${booked.id} with the slot time, provider, patient, reason and notes`);

    await findOnSchedule(dayPage, booked.id);
    pass('the booked appointment appears on the day view with its own edit link');

    const editPopup = await openEditPopup(context, dayPage, booked.id);
    const edited = await editBooking(editPopup, booked.id);
    await editPopup.close().catch(() => {});
    pass(`edit persisted the reason, notes and recomputed end_time (${edited.startTime}-${edited.endTime})`);

    const rotated = await rotateStatusFromSchedule(dayPage, booked.id, edited.status);
    if (rotated) {
      pass(`day view status letter advanced the appointment status ${edited.status} -> ${rotated.status}`);
    }

    const cancelled = await cancelBooking(context, dayPage, booked.id);
    pass(`edit popup cancelled the appointment (status=${cancelled.status}) without deleting it`);

    await deleteBooking(context, dayPage, booked.id);
    pass('delete removed the appointment and left an appointmentArchive record behind');

    assertNoPageErrors(recorder, 'appointment CRUD');
    console.log(`\nCompleted ${passed.length} Playwright checks, 0 failures`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupRows();
    sql.close();
  }
})().catch((error) => {
  console.error(`FAIL appointment CRUD interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
