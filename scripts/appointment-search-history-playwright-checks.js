#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Appointment search and appointment history, entered the way a clinician does:
 *   Schedule ▸ provider column header ▸ "S" (Search view) ▸ appointment/appointmentsearch
 *   (the next-free-slot search: provider, weekday, time of day, type, count ▸ Submit;
 *   a result row ▸ appointment/addappointment for that slot)
 *   Schedule ▸ Search ▸ Master Record ▸ Appt. History ▸ demographic/DemographicApptHistory
 *   (every row's date link ▸ appointment/editappointment for that appointment).
 * Asserts: the search popup opens for the day sheet's provider; a baseline search
 * offers free slots; after an owned appointment is seeded in the first offered
 * slot, that slot is no longer offered while the untouched slots still are; the
 * first offered slot opens the add-appointment popup carrying its provider, date
 * and time; the Appt. History lists exactly the owned patient's two appointments
 * (one past, one future) with date, times, status, reason and provider, excludes
 * a second patient's appointment carrying the same marker, and each row's link
 * opens the edit popup for that appointment_no with its reason.
 * Fixtures: the synthetic patient from lib/workflow-session.js, a second synthetic
 * patient, two `appointment` rows (name and reason carry the marker) plus one for
 * the second patient, and -- only when the login has no bookable schedule day --
 * one owned `scheduledate` + `scheduletemplate` pair. Cleanup deletes all of them
 * and asserts.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.3
 * appointment-search-history.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const RESULT_ROWS = 'table.table tr[onclick^="selectSlot"]';

/** One search result as the page encodes it in its own onclick handler. */
function parseSlot(onclick) {
  const match = /selectSlot\('([^']*)','(\d{4})','(\d{2})','(\d{2})','(\d{2}:\d{2})','(\d{2}:\d{2})','(\d+)'\)/.exec(onclick || '');
  h.assert(match, 'A search result row does not carry the selectSlot() arguments the page documents');
  return { provider: match[1], date: `${match[2]}-${match[3]}-${match[4]}`, start: match[5], end: match[6], duration: Number(match[7]) };
}

async function readResults(popup) {
  const rows = popup.locator(RESULT_ROWS);
  const slots = [];
  for (let i = 0; i < await rows.count(); i++) {
    const row = rows.nth(i);
    const slot = parseSlot(await row.getAttribute('onclick'));
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[0] === slot.date && cells[1] === slot.start,
      'A search result row renders a date or time other than the slot its click would book');
    slots.push({ ...slot, providerName: cells[2] });
  }
  return slots;
}

const key = slot => `${slot.date} ${slot.start}`;
const minutes = time => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));

async function submitSearch(popup) {
  await ui.clickAndAwaitReload(popup, popup.locator('form[name="searchForm"] button[type="submit"]'),
    { timeout: 20000, label: 'appointment search Submit' });
  await h.assertNotErrorPage(popup, 'the appointment search results');
  h.assert(new URL(popup.url()).searchParams.get('method') === 'search', 'Submit did not run the search');
  return readResults(popup);
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const owned = { appointments: [], template: null, scheduleDate: null, otherPatient: null };
  s.cleanup(() => {
    for (const demographic of [patient, owned.otherPatient].filter(Boolean)) {
      sql.execute(`DELETE FROM appointment WHERE demographic_no=${demographic} AND name LIKE ${h.sqlString(`${marker}%`)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${demographic}`) === '0',
        'Owned appointments were not removed');
    }
    if (owned.otherPatient) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${owned.otherPatient} AND last_name=${h.sqlString(`${marker}-B`)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${owned.otherPatient}`) === '0',
        'The second synthetic patient was not removed');
    }
    if (owned.scheduleDate) {
      sql.execute(`DELETE FROM scheduledate WHERE id=${owned.scheduleDate} AND hour=${h.sqlString(owned.template)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM scheduledate WHERE id=${owned.scheduleDate}`) === '0',
        'The owned schedule day was not removed');
    }
    // The template is inserted before the schedule day, so it is removed on its own.
    if (owned.template) {
      sql.execute(`DELETE FROM scheduletemplate WHERE provider_no=${h.sqlString(provider)} AND name=${h.sqlString(owned.template)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${h.sqlString(provider)}
        AND name=${h.sqlString(owned.template)}`) === '0', 'The owned schedule template was not removed');
    }
  });

  let search;
  await s.step('the day sheet Search view opens the appointment search for its provider', async () => {
    search = await s.popup(s.schedule, s.schedule.locator(`input[name="searchview"][onclick*="'${provider}'"]`).first(), 'appointment-search');
    const url = new URL(search.url());
    h.assert(url.pathname.endsWith('/appointment/appointmentsearch') && url.searchParams.get('provider_no') === provider,
      'The Search view did not open appointment/appointmentsearch for the day sheet provider');
    h.assert(await search.locator('#provider_no').inputValue() === provider, 'The search form did not preselect the provider');
  });

  let baseline;
  await s.step('a baseline search offers free slots for the provider', async () => {
    await search.locator('#numberOfResults').selectOption('10');
    baseline = await submitSearch(search);
    if (!baseline.length) {
      // No bookable day inside the 180-day window: give the provider one owned
      // weekday with four 15-minute slots from 09:00, the shape the search reads.
      const code = sql.value('SELECT code FROM scheduletemplatecode ORDER BY id LIMIT 1') || 'A';
      const day = sql.value(`SELECT d FROM (SELECT DATE_ADD(CURDATE(), INTERVAL seq DAY) d FROM
        (SELECT 2 seq UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8
         UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13) x) y
        WHERE DAYOFWEEK(d) BETWEEN 2 AND 6 AND NOT EXISTS (SELECT 1 FROM scheduledate WHERE provider_no=${h.sqlString(provider)} AND sdate=y.d)
        ORDER BY d LIMIT 1`);
      h.assert(/^\d{4}-\d{2}-\d{2}$/.test(day), 'No free weekday to seed a schedule day on');
      owned.template = marker.slice(0, 20);
      const timecode = `${'_'.repeat(36)}${code.repeat(4)}${'_'.repeat(56)}`;
      sql.execute(`INSERT INTO scheduletemplate (provider_no, name, summary, timecode)
        VALUES (${h.sqlString(provider)}, ${h.sqlString(owned.template)}, ${h.sqlString(marker)}, ${h.sqlString(timecode)})`);
      owned.scheduleDate = sql.value(`INSERT INTO scheduledate (sdate, provider_no, available, priority, reason, hour, creator, status)
        VALUES (${h.sqlString(day)}, ${h.sqlString(provider)}, '1', 'a', '', ${h.sqlString(owned.template)}, ${h.sqlString(provider)}, 'A');
        SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(owned.scheduleDate), 'The schedule day fixture was not created');
      baseline = await submitSearch(search);
      h.assert(baseline.some(slot => slot.date === day && slot.start === '09:00'),
        'The search does not offer the 09:00 slot of the seeded schedule day');
    }
    h.assert(baseline.every(slot => slot.provider === provider), 'The search offered slots of another provider');
    h.assert(new Set(baseline.map(key)).size === baseline.length, 'The search offered the same slot twice');
  });

  const taken = baseline[0];
  const futureReason = `${marker} future reason`;
  const pastReason = `${marker} past reason`;
  await s.step('seed one past and one future appointment in the first offered slot', async () => {
    const insert = (demographic, date, start, end, reason) => sql.value(`INSERT INTO appointment
      (provider_no, appointment_date, start_time, end_time, name, demographic_no, program_id, notes, reason,
       location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, remarks, urgency)
      VALUES (${h.sqlString(provider)}, ${date}, ${h.sqlString(start)}, ${h.sqlString(end)}, ${h.sqlString(`${marker},Workflow`)},
       ${demographic}, 0, '', ${h.sqlString(reason)}, '', '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(provider)}, '', '');
      SELECT LAST_INSERT_ID()`);
    const endMinutes = minutes(taken.start) + taken.duration - 1;
    const end = `${String(Math.floor(endMinutes / 60)).padStart(2, '0')}:${String(endMinutes % 60).padStart(2, '0')}:00`;
    owned.appointments.push({ id: insert(patient, h.sqlString(taken.date), `${taken.start}:00`, end, futureReason), reason: futureReason,
      date: taken.date, start: taken.start, end: end.slice(0, 5) });
    const pastDate = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 7 DAY)');
    owned.appointments.push({ id: insert(patient, h.sqlString(pastDate), '10:00:00', '10:14:00', pastReason), reason: pastReason,
      date: pastDate, start: '10:00', end: '10:14' });
    h.assert(owned.appointments.every(a => /^[1-9]\d*$/.test(a.id)), 'An appointment fixture was not created');
    owned.otherPatient = sql.value(`INSERT INTO demographic
      (last_name, first_name, year_of_birth, month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
      VALUES (${h.sqlString(`${marker}-B`)}, 'Other', '1981', '02', '03', 'M', 'AC', ${h.sqlString(provider)}, 'ON', 'ON', 'NR', NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(owned.otherPatient), 'The second synthetic patient was not created');
    const otherId = insert(owned.otherPatient, h.sqlString(pastDate), '11:00:00', '11:14:00', `${marker} other patient`);
    h.assert(/^[1-9]\d*$/.test(otherId), 'The second patient appointment fixture was not created');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient}`) === '2', 'The owned patient does not have exactly two appointments');
  });

  await s.step('the booked slot disappears from the search while untouched slots remain', async () => {
    const after = await submitSearch(search);
    h.assert(!after.some(slot => key(slot) === key(taken)), 'The search still offers the slot the owned appointment occupies');
    // NextAppointmentSearchHelper.checkAvailability drops a slot whose own duration
    // overlaps the booking; every other slot must still be offered, in order.
    const untouched = baseline.filter(slot => {
      const offset = minutes(slot.start) - minutes(taken.start);
      return slot.date !== taken.date || offset >= taken.duration || -offset >= slot.duration;
    });
    const remaining = after.map(key);
    let cursor = -1;
    for (const slot of untouched) {
      const index = remaining.indexOf(key(slot));
      h.assert(index > cursor, 'A slot untouched by the booking is no longer offered, or the order changed');
      cursor = index;
    }
    h.assert(after.length > 0, 'The search offers nothing after one booking');
  });

  await s.step('clicking the first offered slot opens the add-appointment popup for that slot', async () => {
    const first = parseSlot(await search.locator(RESULT_ROWS).first().getAttribute('onclick'));
    const add = await s.popup(search, search.locator(RESULT_ROWS).first(), 'add-appointment');
    const url = new URL(add.url());
    const [year, month, day] = first.date.split('-');
    h.assert(url.pathname.endsWith('/appointment/addappointment')
      && url.searchParams.get('provider_no') === provider && url.searchParams.get('year') === year
      && url.searchParams.get('month') === month && url.searchParams.get('day') === day
      && url.searchParams.get('start_time') === first.start && url.searchParams.get('end_time') === first.end
      && url.searchParams.get('duration') === String(first.duration), 'The slot did not open addappointment with its own provider, date and times');
    await add.close();
    await search.close();
  });

  let history;
  await s.step('Master Record ▸ Appt. History lists exactly the owned appointments with status, reason and provider', async () => {
    const outcome = await ui.clickOpensPopupOrNavigates(s.master, s.master.locator('#appt_hx a'),
      { context: s.context, recorder: s.recorder, label: 'appt-history', timeout: 20000 });
    history = outcome.page;
    await history.waitForURL(/\/demographic\/DemographicApptHistory/, { timeout: 20000 });
    h.assert(new URL(history.url()).searchParams.get('demographic_no') === patient, 'Appt. History opened for another patient');
    const { count } = await ui.dataTableRows(history, '#apptHistoryTbl', { timeout: 20000 });
    h.assert(count === 2, `Appt. History lists ${count} row(s) for a patient with two appointments`);
    const status = sql.value("SELECT description FROM appointment_status WHERE status='t'");
    const providerName = sql.value(`SELECT CONCAT(last_name, ',', first_name) FROM provider WHERE provider_no=${h.sqlString(provider)}`);
    // Rows are matched by their appt_no attribute: the page orders the table in
    // the browser (DataTables sorts the date column), so position proves nothing.
    for (const [index, appointment] of owned.appointments.entries()) {
      const row = history.locator(`#apptHistoryTbl tbody tr[appt_no="${appointment.id}"]`);
      h.assert(await history.locator(`#apptHistoryTbl tbody tr[appt_no="${appointment.id}"]`).count() === 1,
        `Appt. History does not list owned appointment ${index + 1} exactly once`);
      const cells = (await row.locator('td').allInnerTexts()).map(text => text.replace(/ /g, ' ').trim());
      h.assert(cells[0] === appointment.date && cells[1] === appointment.start && cells[2] === appointment.end,
        `Appt. History row ${index + 1} does not show the seeded date and times`);
      h.assert(cells[3] === status.trim(), `Appt. History row ${index + 1} does not show the status description`);
      h.assert(cells[5] === appointment.reason, `Appt. History row ${index + 1} does not show the reason`);
      h.assert(cells[6] === providerName, `Appt. History row ${index + 1} does not show the provider`);
    }
    h.assert(!(await history.locator('#apptHistoryTbl').innerText()).includes('other patient'),
      "Appt. History shows another patient's appointment");
  });

  await s.step("each history row's date link opens the edit popup for that appointment", async () => {
    for (const appointment of owned.appointments) {
      const row = history.locator(`#apptHistoryTbl tbody tr[appt_no="${appointment.id}"]`);
      const edit = await s.popup(history, row.locator('td a').first(), 'edit-appointment');
      const url = new URL(edit.url());
      h.assert(url.pathname.endsWith('/appointment/editappointment') && url.searchParams.get('appointment_no') === appointment.id
        && url.searchParams.get('demographic_no') === patient, 'The history link did not open editappointment for its own row');
      h.assert(await edit.locator('input[name="appointment_no"]').inputValue() === appointment.id, 'The edit popup carries another appointment');
      h.assert(await edit.locator('#reason').inputValue() === appointment.reason, 'The edit popup does not show the reason');
      await edit.close();
    }
  });
}

if (require.main === module) runWorkflow('appointment-search-history', workflow, { openPatient: true });
module.exports = { workflow, parseSlot };
