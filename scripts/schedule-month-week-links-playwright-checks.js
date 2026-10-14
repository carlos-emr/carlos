#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The week numbers of the schedule month view (issue #4540, finding 136 of
 * docs/ui-tests/app-findings-log.md).
 *
 * THE DEFECT. appointmentprovideradminmonth.jsp rendered every week number as a link to
 * providercontrol?...&displaymode=week&dboperation=searchapptweek. `week` has never been in
 * providercontrol.jsp's dispatch table, so since #4381 closed unknown display modes with
 * sendError(404) a click on a week number landed on "CARLOS Error: 404". The week view that does
 * exist is the day sheet with weekView=true for ONE provider (the "W" button of a provider
 * column), so a month showing one provider now links there, and a month showing several (All
 * providers, a group) shows a plain number, because no week view spans several providers.
 *
 * User path: Schedule (day view) > Month > a week number of the next-month calendar > the
 * provider's week view with the seeded appointment under its day > Month > provider select "All" >
 * the week numbers are text, not links.
 *
 * Asserts: the month view offers week-number links and none of them targets the retired mode;
 * the link of the first full week of next month opens a 200 week view (header "Week", the
 * provider_no/weekView/viewall parameters of the "W" button) in which the seeded appointment sits
 * under the Wednesday of that week; with "All" providers selected no week number is a link.
 *
 * Fixtures: one `appointment` row carrying the run marker for the session's provider on that
 * Wednesday, at a free slot inside the provider's schedule hours (deleted and asserted gone). The
 * session's owned patient is the appointment's patient. Wednesday, because it is shown whether or
 * not the provider's schedule.week_view_weekends property is on, so no preference is touched.
 */
const h = require('./lib/playwright-harness');
const {clickAndAwaitReload} = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');

const MONTH_LINK = 'a[href*="displaymode=month&dboperation=searchappointmentmonth"]';

function params(page) {
  return new URL(page.url()).searchParams;
}

async function workflow(s) {
  const {sql, provider, patient, marker} = s;
  const providerKey = h.sqlString(provider);
  // First Sunday of NEXT month, so its row is the first full week row of the next-month calendar,
  // and the Wednesday of that row. The month view opens on the current month; the next-month
  // calendar beside it is where the week numbers of that month are.
  const [[sundayDate, wednesdayDate]] = sql.rows(`SELECT
      DATE_ADD(DATE_ADD(LAST_DAY(CURDATE()), INTERVAL 1 DAY), INTERVAL
        MOD(8 - DAYOFWEEK(DATE_ADD(LAST_DAY(CURDATE()), INTERVAL 1 DAY)), 7) DAY),
      DATE_ADD(DATE_ADD(DATE_ADD(LAST_DAY(CURDATE()), INTERVAL 1 DAY), INTERVAL
        MOD(8 - DAYOFWEEK(DATE_ADD(LAST_DAY(CURDATE()), INTERVAL 1 DAY)), 7) DAY), INTERVAL 3 DAY)`);
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(sundayDate) && /^\d{4}-\d{2}-\d{2}$/.test(wednesdayDate), 'The database did not report the week dates');
  const [sundayYear, sundayMonth, sundayDay] = sundayDate.split('-').map(Number);
  const [wedYear, wedMonth, wedDay] = wednesdayDate.split('-').map(Number);
  const [[startHour, endHour, everyMin]] = sql.rows(`SELECT COALESCE(MAX(startHour),8),COALESCE(MAX(endHour),18),COALESCE(MAX(everyMin),15)
    FROM ProviderPreference WHERE providerNo=${providerKey}`);
  // A slot of that Wednesday with nothing booked: a second appointment at the same time shortens
  // the name and hides the links this check looks for.
  let start = null;
  let end;
  for (let hour = Number(startHour) + 2; hour < Number(endHour); hour++) {
    const candidate = `${String(hour).padStart(2, '0')}:00:00`;
    const candidateEnd = sql.value(`SELECT ADDTIME(${h.sqlString(candidate)}, SEC_TO_TIME(${Number(everyMin) * 60}))`);
    if (sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${providerKey} AND appointment_date=${h.sqlString(wednesdayDate)}
      AND start_time<${h.sqlString(candidateEnd)} AND end_time>${h.sqlString(candidate)}`) === '0') { start = candidate; end = candidateEnd; break; }
  }
  if (start === null) throw new h.SkipCheck(`No free slot inside the provider schedule hours on ${wednesdayDate}`);
  let appointmentNo;
  s.cleanup(() => {
    if (appointmentNo) {
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointmentNo} AND name=${h.sqlString(marker)} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointmentNo}`) === '0', 'The owned appointment was not removed');
    }
  });
  appointmentNo = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
      notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, lastupdateuser)
    VALUES (${providerKey}, ${h.sqlString(wednesdayDate)}, ${h.sqlString(start)}, ${h.sqlString(end)}, ${h.sqlString(marker)}, ${patient},
      '', 'FAKE schedule-month-week-links check', '', '', NULL, '', '', 't', NOW(), NOW(), ${h.sqlString(s.config.testUser)}, ${providerKey});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The appointment fixture was not created');
  const schedule = s.schedule;
  const weekLinks = () => schedule.locator('a[href*="providercontrol"][href*="weekView=true"]');
  const retiredLinks = () => schedule.locator('a[href*="displaymode=week"], a[href*="searchapptweek"]');
  // The week-number column is the first cell of each calendar row; its number is the row's position in the month.
  const weekNumberCells = () => schedule.locator('td[bgcolor="#FOFOFO"]').filter({hasText: /^\s*\d\s*$/});

  async function openMonth(label) {
    await clickAndAwaitReload(schedule, schedule.locator(MONTH_LINK).first(), {label});
    const query = params(schedule);
    h.assert(query.get('displaymode') === 'month' && query.get('dboperation') === 'searchappointmentmonth', `${label}: the Month link does not request the month view`);
  }

  await s.step('Month offers week-number links and none of them targets the retired week mode', async () => {
    await schedule.reload({waitUntil: 'domcontentloaded'});
    await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    await openMonth('Month');
    await weekLinks().first().waitFor({timeout: 20000});
    const links = await weekLinks().count();
    // Two calendars (last month, next month) of 4 to 6 week rows each.
    h.assert(links >= 8 && links <= 12, `Expected 8 to 12 week-number links on the month view, found ${links}`);
    h.assert(await retiredLinks().count() === 0, 'A week number still links to displaymode=week / searchapptweek, a mode providercontrol.jsp answers with 404');
    h.assert(await weekNumberCells().count() === links, 'Every week number should be a link when the month shows one provider');
  });

  await s.step('a week number opens that week of the provider with the seeded appointment under its Wednesday', async () => {
    const link = schedule.locator(
      `a[href*="providercontrol"][href*="weekView=true"][href*="year=${sundayYear}&month=${sundayMonth}&day=${sundayDay}&"]`).first();
    await link.waitFor({timeout: 20000});
    const href = await link.getAttribute('href');
    h.assert(!/displaymode=week|searchapptweek/.test(href), `The week-number link still targets the retired mode: ${href}`);
    await clickAndAwaitReload(schedule, link, {label: 'week number'});
    const query = params(schedule);
    h.assert(query.get('weekView') === 'true' && query.get('provider_no') === provider && query.get('viewall') === '1'
      && query.get('displaymode') === 'day' && query.get('dboperation') === 'searchappointmentday',
    `The week number did not open the provider's week view (the "W" button's parameters): ${schedule.url()}`);
    h.assert(/Week/i.test(await schedule.locator('span.dateAppointment').innerText()), 'The header does not say which week is shown');
    h.assert(await schedule.getByText('CARLOS has encountered an unexpected error').count() === 0, 'The week number landed on the CARLOS error page');
    const dayHeader = schedule.locator(`b > a[href*="year=${wedYear}&month=${wedMonth}&day=${wedDay}&view=0&displaymode=day"]`).first();
    await dayHeader.waitFor({timeout: 20000});
    const column = dayHeader.locator('xpath=ancestor::td[@valign="top"][1]');
    h.assert(await column.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"]`).count() === 1,
      `The week view does not place the appointment under ${wednesdayDate}, so the link opened another week or provider`);
  });

  await s.step('with All providers selected the week numbers are plain text', async () => {
    await openMonth('Month from the week view');
    h.assert(await weekLinks().count() > 0, 'The provider month view lost its week-number links');
    const select = schedule.locator('select[name="provider_no"]');
    await select.waitFor({timeout: 20000});
    const reloaded = schedule.waitForEvent('framenavigated', {predicate: frame => frame === schedule.mainFrame(), timeout: 20000});
    reloaded.catch(() => {});
    await select.selectOption('all');
    await reloaded;
    await schedule.waitForLoadState('domcontentloaded', {timeout: 20000}).catch(() => {});
    await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    h.assert(params(schedule).get('providerview') === 'all', 'Selecting All providers did not reload the month for providerview=all');
    h.assert(await weekNumberCells().count() >= 8, 'The week numbers disappeared with All providers selected; they should stay as text');
    h.assert(await weekLinks().count() === 0, 'A week number is still a link in the All providers month, which has no single provider week view');
    h.assert(await retiredLinks().count() === 0, 'A week number targets the retired week mode in the All providers month');
  });
}
if (require.main === module) runWorkflow('schedule-month-week-links', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
