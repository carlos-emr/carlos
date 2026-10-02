#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The day sheet's view modes around one seeded appointment. Implements coverage plan
 * §2.3 `schedule-views` (the view half; `schedule-date-navigation` owns the calendar and
 * month-boundary arithmetic, `schedule-links` the Find-a-Provider popup).
 *
 * User path: Schedule (day view, provider/providercontrol displaymode=day) ▸ provider
 * column "W" (week view, weekView=true) ▸ week column date (back to day) ▸ Month
 * (displaymode=month) ▸ today's cell (back to day) ▸ Flip View radio (schedule/FlipView)
 * ▸ date row (back to day) ▸ provider name (zoom, view=1) ▸ "W+" multiplier button ▸
 * Alt+T (today shortcut).
 *
 * Asserts: the appointment link (by its appointment_no) renders in the day view with
 * its E link, in the week view under today's column WITHOUT the E link (obs. 13), after
 * the month cell for today links back, in the Flip View row for today as a booking in
 * its time slot, and in the zoom view; every view's URL carries the expected
 * displaymode/dboperation/provider parameters; the multiplier moves a week ahead and
 * Alt+T returns to today. Fixtures: the session's owned patient and one `appointment`
 * row carrying the run marker at a free slot inside the provider's schedule hours
 * (deleted and asserted gone); the week-view weekend property is snapshotted and
 * enabled only when the run falls on a weekend, then restored.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {clickAndAwaitReload, pressShortcut} = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');

const WEEKENDS_PROPERTY = 'schedule.week_view_weekends';

/** English bundle text for a key, read from the source tree when present. */
function bundleMessage(key, fallback) {
  try {
    const bundle = path.join(__dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties');
    const line = fs.readFileSync(bundle, 'utf8').split('\n').find(candidate => candidate.startsWith(`${key}=`));
    return line ? line.slice(key.length + 1).trim() : fallback;
  } catch (error) {
    return fallback;
  }
}

function params(page) {
  return new URL(page.url()).searchParams;
}

async function workflow(s) {
  const {sql, provider, patient, marker} = s;
  const providerKey = h.sqlString(provider);
  const [[today, dayOfWeek, nextWeek]] = sql.rows('SELECT CURDATE(), DAYOFWEEK(CURDATE()), DATE_ADD(CURDATE(), INTERVAL 7 DAY)');
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(today), 'The database did not report today');
  const [year, month, day] = today.split('-').map(Number);
  const [[startHour, endHour, everyMin]] = sql.rows(`SELECT COALESCE(MAX(startHour),8),COALESCE(MAX(endHour),18),COALESCE(MAX(everyMin),15)
    FROM ProviderPreference WHERE providerNo=${providerKey}`);
  // A slot of the provider's own day with nothing already booked: a second
  // appointment at the same time shortens the name and hides the E/B links.
  let hour = null;
  let start;
  let end;
  for (let candidate = Number(startHour) + 2; candidate < Number(endHour); candidate++) {
    start = `${String(candidate).padStart(2, '0')}:00:00`;
    end = sql.value(`SELECT ADDTIME(${h.sqlString(start)}, SEC_TO_TIME(${Number(everyMin) * 60}))`);
    if (sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${providerKey} AND appointment_date=CURDATE()
      AND start_time<${h.sqlString(end)} AND end_time>${h.sqlString(start)}`) === '0') { hour = candidate; break; }
  }
  if (hour === null) throw new h.SkipCheck('No free slot inside the provider schedule hours today');
  const weekend = dayOfWeek === '1' || dayOfWeek === '7';
  const weekendRows = () => sql.rows(`SELECT id,COALESCE(value,''),value IS NULL FROM property WHERE provider_no=${providerKey} AND name=${h.sqlString(WEEKENDS_PROPERTY)} ORDER BY id`);
  const weekendSnapshot = weekendRows();
  let appointmentNo;
  s.cleanup(() => {
    if (appointmentNo) {
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointmentNo} AND name=${h.sqlString(marker)} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointmentNo}`) === '0', 'The owned appointment was not removed');
    }
    if (weekend) {
      const statements = [`DELETE FROM property WHERE provider_no=${providerKey} AND name=${h.sqlString(WEEKENDS_PROPERTY)}`];
      for (const [id, value, isNull] of weekendSnapshot) {
        h.assert(/^\d+$/.test(id), 'Invalid weekend property snapshot');
        statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${providerKey},${h.sqlString(WEEKENDS_PROPERTY)},${isNull === '1' ? 'NULL' : h.sqlString(value)})`);
      }
      sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
      h.assert(JSON.stringify(weekendRows()) === JSON.stringify(weekendSnapshot), 'The weekend property was not restored');
    }
  });
  if (weekend) {
    // The week view starts on Monday and shows five days unless weekends are enabled.
    sql.execute(`DELETE FROM property WHERE provider_no=${providerKey} AND name=${h.sqlString(WEEKENDS_PROPERTY)};
      INSERT INTO property(provider_no,name,value) VALUES (${providerKey},${h.sqlString(WEEKENDS_PROPERTY)},'true')`);
  }
  appointmentNo = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
      notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, lastupdateuser)
    VALUES (${providerKey}, CURDATE(), ${h.sqlString(start)}, ${h.sqlString(end)}, ${h.sqlString(marker)}, ${patient},
      '', 'FAKE schedule-views check', '', '', NULL, '', '', 't', NOW(), NOW(), ${h.sqlString(s.config.testUser)}, ${providerKey});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The appointment fixture was not created');
  const schedule = s.schedule;
  const apptLink = () => schedule.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"]`);
  const apptCell = () => schedule.locator('td.appt').filter({has: apptLink()});
  const dayHeader = () => schedule.locator(`b > a[href*="year=${year}&month=${month}&day=${day}&view=0&displaymode=day"]`).first();
  async function assertDayView(label) {
    const query = params(schedule);
    h.assert(query.get('displaymode') === 'day' && query.get('dboperation') === 'searchappointmentday' && query.get('weekView') === null
      && Number(query.get('year')) === year && Number(query.get('month')) === month && Number(query.get('day')) === day,
    `${label}: the day sheet did not come back on today's day view`);
    await apptLink().waitFor({timeout: 20000});
    h.assert(await apptLink().count() === 1, `${label}: the seeded appointment is not on the day sheet exactly once`);
  }
  await s.step('day view shows the seeded appointment on today with its E link', async () => {
    await schedule.reload({waitUntil: 'domcontentloaded'});
    await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    await assertDayView('day view');
    h.assert((await schedule.locator('span.dateAppointment').innerText()).includes(today), 'The day header does not show today');
    h.assert(await apptCell().locator('a.encounterBtn').count() === 1, 'The day view appointment has no E link');
  });
  await s.step('W opens the week view with the appointment under today and no E link', async () => {
    await clickAndAwaitReload(schedule, schedule.locator(`input[name="weekview"][onclick*="goWeekView('${provider}')"]`), {label: 'W'});
    const query = params(schedule);
    h.assert(query.get('weekView') === 'true' && query.get('provider_no') === provider && query.get('viewall') === '1'
      && query.get('displaymode') === 'day', 'The week view link does not carry weekView/provider_no/viewall');
    h.assert(/Week/i.test(await schedule.locator('span.dateAppointment').innerText()), 'The header does not say which week is shown');
    await dayHeader().waitFor({timeout: 20000});
    const column = dayHeader().locator('xpath=ancestor::td[@valign="top"][1]');
    h.assert(await column.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"]`).count() === 1,
      'The week view does not place the appointment under today');
    h.assert(await apptCell().locator('a.encounterBtn').count() === 0, 'The week view still renders the E link (obs. 13)');
    await clickAndAwaitReload(schedule, dayHeader(), {label: 'week column date'});
    await assertDayView('week column date');
  });
  await s.step('Month marks today with a day-view link that returns to the appointment', async () => {
    await clickAndAwaitReload(schedule, schedule.locator('a[href*="displaymode=month&dboperation=searchappointmentmonth"]').first(), {label: 'Month'});
    const query = params(schedule);
    h.assert(query.get('displaymode') === 'month' && query.get('dboperation') === 'searchappointmentmonth', 'The Month link does not request the month view');
    const padded = `year=${year}&month=${String(month).padStart(2, '0')}&day=${String(day).padStart(2, '0')}&view=0&displaymode=day&dboperation=searchappointmentday`;
    const cell = schedule.locator(`a[href*="${padded}"]`).filter({has: schedule.locator('span.date')}).first();
    await cell.waitFor({timeout: 20000});
    h.assert((await cell.locator('span.date').innerText()).trim() === String(day), "Today's month cell does not show its day number");
    await clickAndAwaitReload(schedule, cell, {label: 'month cell'});
    await assertDayView('month cell');
  });
  await s.step('Flip View shows the provider column with the booking in its slot', async () => {
    await clickAndAwaitReload(schedule, schedule.locator(`input[name="flipview"][onclick*="goFilpView('${provider}')"]`), {label: 'Flip View'});
    const url = new URL(schedule.url());
    h.assert(url.pathname.endsWith('/schedule/FlipView') && url.searchParams.get('provider_no') === provider
      && url.searchParams.get('startDate') === `${year}-${month}-${day}` && (url.searchParams.get('originalpage') || '').endsWith('/provider/providercontrol'),
    'Flip View did not open for this provider from today');
    // scheduleflipview.jsp fills its provider dropdown from the CURRENT GROUP's members
    // only, so with the ".default" (no group) preference it renders empty although the
    // column shown is this provider's. Asserted when it has options, reported otherwise.
    if (await schedule.locator('select[name="provider_no"] option').count() > 0) {
      h.assert(await schedule.locator('select[name="provider_no"]').inputValue() === provider, 'Flip View does not select the provider column');
    } else {
      console.log('  observed: Flip View provider dropdown is empty (no group selected in the schedule preference)');
    }
    const dateLink = schedule.locator(`a[href*="year=${year}&month=${month}&day=${day}&view=0&displaymode=day&dboperation=searchappointmentday"]`).first();
    await dateLink.waitFor({timeout: 20000});
    const row = schedule.locator('tr').filter({has: dateLink}).last();
    const slot = row.locator(`td[title="${hour}:00"]`);
    h.assert(await slot.count() === 1, "Today's Flip View row has no cell for the appointment slot");
    const bookings = slot.locator(`td[title="${bundleMessage('schedule.scheduleflipview.msgbookings', 'current # of bookings')}"]`);
    h.assert(Number((await bookings.innerText()).trim()) >= 1, 'The Flip View slot does not count the booking');
    await clickAndAwaitReload(schedule, dateLink, {label: 'Flip View date'});
    await assertDayView('Flip View date');
  });
  await s.step('the provider name zooms to a single-provider view with the appointment', async () => {
    await clickAndAwaitReload(schedule, schedule.locator(`a[onclick*="goZoomView('${provider}'"]`).first(), {label: 'zoom'});
    const query = params(schedule);
    h.assert(query.get('view') === '1' && query.get('curProvider') === provider && query.get('displaymode') === 'day', 'Zoom did not request the single-provider view');
    await apptLink().waitFor({timeout: 20000});
    h.assert(await apptLink().count() === 1, 'The zoom view does not show the appointment');
  });
  await s.step('W+ moves a week ahead and Alt+T returns to today', async () => {
    await schedule.locator('#dateMultiplier').fill('1');
    await clickAndAwaitReload(schedule, schedule.locator('input.quick-btn[onclick*="weekForward"][onclick*="dateMultiplier"]'), {label: 'W+'});
    const [nextYear, nextMonth, nextDay] = nextWeek.split('-').map(Number);
    const query = params(schedule);
    h.assert(Number(query.get('year')) === nextYear && Number(query.get('month')) === nextMonth && Number(query.get('day')) === nextDay
      && query.get('displaymode') === 'day', 'W+ did not move exactly one week ahead');
    h.assert(await apptLink().count() === 0, "Next week's day sheet still lists today's appointment");
    const navigated = schedule.waitForEvent('framenavigated', {predicate: frame => frame === schedule.mainFrame(), timeout: 20000});
    navigated.catch(() => {});
    await pressShortcut(schedule, 'Alt+KeyT');
    await navigated;
    await schedule.waitForLoadState('domcontentloaded', {timeout: 20000}).catch(() => {});
    await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    await assertDayView('Alt+T');
    h.assert((await schedule.locator('span.dateAppointment').innerText()).includes(today), 'Alt+T did not return the header to today');
  });
}
if (require.main === module) runWorkflow('schedule-views', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
