#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-appointment-repeat-booking — booking a recurring appointment from the NEW appointment
 * popup (appointment/addappointment ▸ Repeat ▸ appointment/appointmentrepeatbooking ▸ Create).
 * appointment-recurrence-playwright-checks drives the repeat form from a SAVED appointment (edit
 * popup); the new-booking route, which forwards to the same form without an appointment number and
 * creates the first booking itself, had no check.
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ empty slot ▸ Add Appointment popup ▸
 * keyword search ▸ pick the owned patient ▸ reason/notes ▸ Repeat ▸ every N day/week until a date ▸
 * Create appointments ▸ Close; the day sheet shows the series.
 * Asserted: an end date before the appointment is refused with the message and creates nothing;
 * "every 1 week" for three weeks creates four identical appointments on the right dates (the first
 * one included) for the right patient, provider, times, reason and status; "every 2 days" over six
 * days creates four more on the 2-day grid; the opener day sheet shows the booking after Close; the
 * rows carry the creator; GET against the repeat route is refused and creates nothing.
 * Fixtures: the throwaway login (lib/throwaway-login-fixture.js, so the shared provider's schedule is
 * untouched) and one owned FAKE- patient. Cleanup deletes the owned appointments and archive rows,
 * then the throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

function isoDate(base, days) {
  const date = new Date(base.getFullYear(), base.getMonth(), base.getDate() + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function ddmmyyyy(iso) { const [y, m, d] = iso.split('-'); return `${d}/${m}/${y}`; }

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);
  const reasonA = `${marker} weekly`;
  const reasonB = `${marker} two-day`;
  s.cleanup(() => {
    const owned = `provider_no=${owner} AND reason LIKE ${h.sqlString(marker + '%')}`;
    sql.execute(`DELETE FROM appointmentArchive WHERE provider_no=${owner} AND reason LIKE ${h.sqlString(marker + '%')};
      DELETE FROM appointment WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${owner}`) === '0', 'Owned appointment rows were not removed');
  });
  const today = new Date();
  const todayIso = isoDate(today, 0);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const rowsFor = reason => sql.rows(`SELECT appointment_date,start_time,end_time,demographic_no,provider_no,status,creator FROM appointment
    WHERE provider_no=${owner} AND reason=${h.sqlString(reason)} ORDER BY appointment_date`);

  // Open the Add Appointment popup from the nth slot of the throwaway's own column, pick the owned
  // patient through the popup's keyword search, and fill the reason.
  async function openBooking(slotIndex, reason) {
    const slots = schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`);
    h.assert(await slots.count() > slotIndex, 'The day sheet offers no bookable slot in the throwaway column');
    const popup = await ui.clickOpensPopup(schedule, slots.nth(slotIndex), { context, recorder, label: 'add-appointment', timeout: 20000 });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await popup.locator('#keyword').fill(marker);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await pick.waitFor({ state: 'visible' });
    await ui.clickAndAwaitReload(popup, pick, { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await popup.locator('#demographic_no').inputValue() === patient, 'The booking form did not take the owned patient');
    await popup.locator('#reason').fill(reason);
    return popup;
  }
  async function openRepeat(popup) {
    await ui.clickAndAwaitReload(popup, popup.locator('#apptRepeatButton'), { required: false });
    await popup.locator('#endDate').waitFor({ state: 'attached' });
    await h.assertNotErrorPage(popup, 'repeat booking form');
  }
  async function setEnd(popup, iso) {
    // The end-date field is read-only: it is chosen with the calendar widget, like the front desk does.
    await ui.pickDate(popup, popup.locator('#endDate'), iso);
    h.assert(await popup.locator('#endDate').inputValue() === ddmmyyyy(iso), `The calendar did not set the end date ${iso}`);
  }
  async function create(popup, expected) {
    const [response] = await Promise.all([
      popup.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/appointment/appointmenteditrepeatbooking')),
      popup.getByRole('button', { name: /create/i }).click(),
    ]);
    h.assert(response.status() === 200, `Create answered HTTP ${response.status()}`);
    const result = popup.locator('#recurrence-result');
    await result.waitFor({ state: 'visible' });
    const message = (await result.innerText()).trim();
    h.assert(expected.test(message), `Unexpected recurrence result "${message}"`);
    return message;
  }

  await s.step('an end date before the appointment is refused with the message and creates nothing', async () => {
    const popup = await openBooking(0, reasonA);
    await openRepeat(popup);
    await setEnd(popup, isoDate(today, -3));
    const posts = [];
    popup.on('request', request => { if (request.method() === 'POST') posts.push(request.url()); });
    const message = await create(popup, /end date on or after the appointment date/i);
    h.assert(posts.length === 1 && rowsFor(reasonA).length === 0, `The refused repeat created ${rowsFor(reasonA).length} appointment(s): ${message}`);
    await popup.close();
  });

  await s.step('Repeat every 1 week for three weeks creates the four appointments, the first included', async () => {
    const popup = await openBooking(0, reasonA);
    const start = await popup.locator('form#addappt input[name="start_time"]').inputValue();
    // The status the booking form submits (a select in the default configuration), read before Repeat reloads the form.
    const submittedStatus = await popup.locator('form#addappt [name="status"]').first().inputValue();
    h.assert(submittedStatus !== '', 'The booking form carries no status to compare the series against');
    await openRepeat(popup);
    await popup.locator('select[name="everyNum"]').selectOption('1');
    await popup.locator('#dateUnitWeek').check();
    await setEnd(popup, isoDate(today, 21));
    await create(popup, /^4 appointment\(s\) created\.$/);
    const rows = rowsFor(reasonA);
    h.assert(rows.length === 4, `The weekly series has ${rows.length} appointments, not 4`);
    h.assert(JSON.stringify(rows.map(r => r[0])) === JSON.stringify([0, 7, 14, 21].map(d => isoDate(today, d))),
      'The weekly series is not on today and the three following weeks');
    h.assert(rows.every(r => r[1].startsWith(start.slice(0, 5)) && r[1] === rows[0][1] && r[2] === rows[0][2]),
      'The series appointments do not share the clicked slot\'s times');
    h.assert(rows.every(r => r[3] === patient && r[4] === fixture.providerNo && r[5] === submittedStatus && r[6] === fixture.providerNo),
      'The series appointments do not carry the owned patient, provider, the status the booking form submitted and creator');
    // A Close that leaves the popup open must fail here, so the day-sheet step below proves the advertised close flow.
    await Promise.all([
      popup.waitForEvent('close', { timeout: 20000 }),
      popup.getByRole('button', { name: /close/i }).click(),
    ]);
  });

  await s.step('the day sheet shows the new booking after the popup closes', async () => {
    await schedule.waitForFunction(reason => [...document.querySelectorAll('a.apptLink')].length > 0, reasonA, { timeout: 20000 });
    h.assert(await schedule.locator('a.apptLink').count() >= 1, 'The opener day sheet did not refresh to show the booking');
  });

  await s.step('Repeat every 2 days over six days creates four appointments on the 2-day grid', async () => {
    const popup = await openBooking(5, reasonB);
    await openRepeat(popup);
    await popup.locator('select[name="everyNum"]').selectOption('2');
    await popup.locator('#dateUnitDay').check();
    await setEnd(popup, isoDate(today, 6));
    await create(popup, /^4 appointment\(s\) created\.$/);
    const rows = rowsFor(reasonB);
    h.assert(JSON.stringify(rows.map(r => r[0])) === JSON.stringify([0, 2, 4, 6].map(d => isoDate(today, d))),
      'The 2-day series is not on days 0, 2, 4 and 6');
    h.assert(rowsFor(reasonA).length === 4, 'Booking the second series changed the first');
    await popup.close();
  });

  await s.step('GET against the repeat route is refused and creates nothing', async () => {
    const before = sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`);
    const params = new URLSearchParams({ groupappt: 'Add Group Appointment', provider_no: fixture.providerNo, appointment_date: todayIso,
      start_time: '12:00', end_time: '12:14', demographic_no: patient, keyword: marker, reason: `${marker} get`, everyNum: '1', everyUnit: 'day',
      endDate: ddmmyyyy(isoDate(today, 2)) });
    const response = await context.request.get(`${config.baseUrl}/appointment/appointmentrepeatbooking?${params}`);
    h.assert(response.status() === 405, `GET answered HTTP ${response.status()}, not 405`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`) === before, 'A GET created appointments');
  });
}

if (require.main === module) runWorkflow('gap-provider-appointment-repeat-booking', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
