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
 * WHY TWO PROVIDERS. The day sheet does not take its provider from provider_no: it lists the
 * providers of the VIEWER's saved schedule view and only picks a column among them. A link that
 * sent just provider_no would open the viewer's own week when the month shows somebody else (a
 * code-review finding on the first version of the fix). So the check seeds one appointment for
 * the signed-in provider and one for a second provider, opens each provider's month, follows the
 * week number, and asserts that the week shows THAT provider's appointment and not the other's.
 * When the signed-in provider's saved schedule view is one provider, the sheet shows that
 * provider whatever the URL says, and the other provider's week numbers must be plain text; the
 * check asserts whichever applies.
 *
 * WHY THE DATES COME FROM THE PAGE. The month JSP builds its last-month and next-month calendars
 * with new GregorianCalendar(year, month - 2, day), which rolls over on days such as the 31st
 * (the "next month" calendar can then show the month after), so a date computed from the clock
 * can disagree with the calendar the page rendered. The fixture dates are read from the week-number
 * links the page actually rendered.
 *
 * User path: Schedule (day view) > Month > the last week number > the provider's week view with
 * the seeded appointment under its Wednesday > Month > another provider in the provider select >
 * its last week number > that provider's week > Month > provider select "All" > the week numbers
 * are text, not links.
 *
 * The day sheet's schedule-view selector also hides the providers in the viewer's
 * MyGroupAccessRestriction rows, which the month page does not apply, so a week number for a
 * restricted provider must be plain text; a step inserts such a row and asserts that.
 *
 * Fixtures: owned `appointment` rows carrying the run marker, on the Wednesday of the rendered
 * week (shown whether or not schedule.week_view_weekends is on, so no preference is touched), at
 * a slot free for both providers inside the signed-in provider's schedule hours (deleted and
 * asserted gone), and one MyGroupAccessRestriction row restricting the second provider for the
 * signed-in provider (deleted and asserted gone). The session's owned patient is the appointments' patient.
 */
const h = require('./lib/playwright-harness');
const {clickAndAwaitReload} = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');

const MONTH_LINK = 'a[href*="displaymode=month&dboperation=searchappointmentmonth"]';
const DAY_MS = 24 * 60 * 60 * 1000;

function params(page) {
  return new URL(page.url()).searchParams;
}

/** The Wednesday of the Sunday-to-Saturday row that contains the given day, as a UTC date. */
function wednesdayOfRow(year, month, day) {
  const start = Date.UTC(year, month - 1, day);
  return new Date(start + (3 - new Date(start).getUTCDay()) * DAY_MS);
}

function isoDate(date) {
  return date.toISOString().slice(0, 10);
}

async function workflow(s) {
  const {sql, provider, patient, marker} = s;
  const providerKey = h.sqlString(provider);
  // A second active provider for the "month shows somebody else" step.
  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no<>${providerKey}
    AND provider_no NOT LIKE '-%' ORDER BY provider_no LIMIT 1`);
  // The viewer's saved schedule view. When it is a provider, the day sheet shows that provider whatever the URL asks for.
  const savedView = sql.value(`SELECT COALESCE(MAX(myGroupNo),'') FROM ProviderPreference WHERE providerNo=${providerKey}`);
  const pinnedTo = savedView && sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(savedView)}`) === '1'
    ? savedView : null;
  const linksExpectedFor = (providerNo) => !pinnedTo || pinnedTo === providerNo;
  const [[startHour, endHour, everyMin]] = sql.rows(`SELECT COALESCE(MAX(startHour),8),COALESCE(MAX(endHour),18),COALESCE(MAX(everyMin),15)
    FROM ProviderPreference WHERE providerNo=${providerKey}`);

  // The restriction step's fixture row; registered before it exists so a failure still removes it.
  let restrictionId = null;
  s.cleanup(() => {
    if (restrictionId) {
      sql.execute(`DELETE FROM MyGroupAccessRestriction WHERE id=${restrictionId} AND providerNo=${providerKey} AND myGroupNo=${h.sqlString(other)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM MyGroupAccessRestriction WHERE id=${restrictionId}`) === '0', 'The restriction fixture row was not removed');
    }
  });
  const appointments = {};
  s.cleanup(() => {
    for (const appointmentNo of Object.values(appointments)) {
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointmentNo} AND name=${h.sqlString(marker)} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointmentNo}`) === '0', 'An owned appointment was not removed');
    }
  });
  /** One appointment per provider at the same free slot of that day: a second booking at a slot shortens the name and hides the links. */
  function seedWeek(dateIso, providerNos) {
    for (let hour = Number(startHour) + 2; hour < Number(endHour); hour++) {
      const start = `${String(hour).padStart(2, '0')}:00:00`;
      const end = sql.value(`SELECT ADDTIME(${h.sqlString(start)}, SEC_TO_TIME(${Number(everyMin) * 60}))`);
      const busy = providerNos.some((providerNo) => sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${h.sqlString(providerNo)}
        AND appointment_date=${h.sqlString(dateIso)} AND start_time<${h.sqlString(end)} AND end_time>${h.sqlString(start)}`) !== '0');
      if (busy) continue;
      for (const providerNo of providerNos) {
        const appointmentNo = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
            notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, lastupdateuser)
          VALUES (${h.sqlString(providerNo)}, ${h.sqlString(dateIso)}, ${h.sqlString(start)}, ${h.sqlString(end)}, ${h.sqlString(marker)}, ${patient},
            '', 'FAKE schedule-month-week-links check', '', '', NULL, '', '', 't', NOW(), NOW(), ${h.sqlString(s.config.testUser)}, ${h.sqlString(provider)});
          SELECT LAST_INSERT_ID()`);
        h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The appointment fixture was not created');
        appointments[providerNo] = appointmentNo;
      }
      return;
    }
    throw new Error(`No slot on ${dateIso} is free for every provider inside the schedule hours`);
  }

  const schedule = s.schedule;
  const weekLinks = () => schedule.locator('a[href*="providercontrol"][href*="weekView=true"]');
  const retiredLinks = () => schedule.locator('a[href*="displaymode=week"], a[href*="searchapptweek"]');
  // The week-number column is the first cell of each calendar row; its number is the row's position in the month.
  const weekNumberCells = () => schedule.locator('td[bgcolor="#FOFOFO"]').filter({hasText: /^\s*\d\s*$/});
  const apptLink = (providerNo) => schedule.locator(`a.apptLink[onclick*="appointment_no=${appointments[providerNo]}&"]`);

  // The month the first Month click opened, without a provider choice. The Month link of a week view opens the month of the
  // week's last day (a week that straddles two months lands on the later one), so later steps return to this URL, which the
  // application itself produced, to compare providers on the same calendar.
  let monthUrl = null;
  async function openMonth(label) {
    if (params(schedule).get('displaymode') === 'month') {
      // Already on a month page: its first Month link is the "last month" arrow.
    } else if (monthUrl) {
      await schedule.goto(monthUrl, {waitUntil: 'domcontentloaded'});
      await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    } else {
      await clickAndAwaitReload(schedule, schedule.locator(MONTH_LINK).first(), {label});
      const opened = new URL(schedule.url());
      opened.searchParams.delete('providerview');
      monthUrl = opened.toString();
    }
    const query = params(schedule);
    h.assert(query.get('displaymode') === 'month' && query.get('dboperation') === 'searchappointmentmonth', `${label}: the Month link does not request the month view`);
    await weekNumberCells().first().waitFor({timeout: 20000});
  }
  /**
   * Picks a value in the month's provider select and waits for the reload. A user whose saved schedule view is a group lands
   * on that group's month (whose week numbers are text), so a step that needs one provider's month chooses it instead of
   * relying on the default.
   */
  async function chooseProvider(value, label) {
    const select = schedule.locator('select[name="provider_no"]');
    await select.waitFor({timeout: 20000});
    if (await select.inputValue() !== value) {
      const reloaded = schedule.waitForEvent('framenavigated', {predicate: frame => frame === schedule.mainFrame(), timeout: 20000});
      reloaded.catch(() => {});
      await select.selectOption(value);
      await reloaded;
      await schedule.waitForLoadState('domcontentloaded', {timeout: 20000}).catch(() => {});
      await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
      h.assert(params(schedule).get('providerview') === value, `${label}: selecting ${value} did not reload the month for providerview=${value}`);
    }
    await weekNumberCells().first().waitFor({timeout: 20000});
  }
  /** What a month shows for one provider: week-number links to that provider, or, when the viewer's saved view hides it, none. */
  async function assertMonthWeekNumbers(providerNo, label) {
    const cells = await weekNumberCells().count();
    h.assert(cells >= 8 && cells <= 12, `${label}: expected 8 to 12 week numbers on the month view, found ${cells}`);
    h.assert(await retiredLinks().count() === 0, `${label}: a week number links to displaymode=week / searchapptweek, a mode providercontrol.jsp answers with 404`);
    if (!linksExpectedFor(providerNo)) {
      h.assert(await weekLinks().count() === 0,
        `${label}: the viewer's saved schedule view is provider ${pinnedTo}, which the day sheet shows whatever the URL asks for, so week numbers for ${providerNo} must be plain text`);
      return null;
    }
    h.assert(await weekLinks().count() === cells, `${label}: every week number should be a link when the month shows one provider (${await weekLinks().count()} links, ${cells} numbers)`);
    h.assert(await weekLinks().locator('xpath=ancestor::td[@bgcolor="#FOFOFO"]').count() === cells, `${label}: a week link is outside the week-number column`);
    const hrefs = await weekLinks().evaluateAll((anchors) => anchors.map((anchor) => anchor.getAttribute('href')));
    for (const href of hrefs) {
      const query = new URL(href, schedule.url()).searchParams;
      h.assert(query.get('weekView') === 'true' && query.get('provider_no') === providerNo && query.get('curProvider') === providerNo
        && query.get('view') === '1' && query.get('displaymode') === 'day' && query.get('dboperation') === 'searchappointmentday'
      && (query.get('curProviderName') || '').length > 0,
      `${label}: a week-number link does not ask for provider ${providerNo} alone in week view: ${href}`);
    }
    // Each calendar panel (last month, next month) has its own month, so a week link's date has to be the date of ITS row:
    // the first day link of the row is the row's Sunday, or the 1st when the month starts mid-week.
    const rowDates = await weekLinks().evaluateAll((anchors) => anchors.map((anchor) => {
      const first = anchor.closest('tr') && anchor.closest('tr').querySelector('a[href*="displaymode=day"]');
      return [anchor.getAttribute('href'), first ? first.getAttribute('href') : null];
    }));
    for (const [weekHref, dayHref] of rowDates) {
      h.assert(dayHref, `${label}: a week-number row has no day link to compare its date with`);
      const week = new URL(weekHref, schedule.url()).searchParams;
      const day = new URL(dayHref, schedule.url()).searchParams;
      for (const key of ['year', 'month', 'day']) {
        h.assert(Number(week.get(key)) === Number(day.get(key)),
          `${label}: a week link carries ${key}=${week.get(key)} but its row starts on ${key}=${day.get(key)} (${weekHref} / ${dayHref})`);
      }
    }
    // The rendered calendar is the source of truth for dates (see the header): the last link is the last week row.
    const last = new URL(hrefs[hrefs.length - 1], schedule.url()).searchParams;
    return wednesdayOfRow(Number(last.get('year')), Number(last.get('month')), Number(last.get('day')));
  }
  /** Follows the last week number and asserts the week view shows `providerNo`'s seeded appointment and not `otherNo`'s. */
  async function followLastWeekNumber(providerNo, otherNo, wednesday, label) {
    await clickAndAwaitReload(schedule, weekLinks().last(), {label});
    const query = params(schedule);
    h.assert(query.get('weekView') === 'true' && query.get('provider_no') === providerNo && query.get('curProvider') === providerNo
      && query.get('displaymode') === 'day' && query.get('dboperation') === 'searchappointmentday',
    `${label}: the week number did not open ${providerNo}'s week view: ${schedule.url()}`);
    h.assert(/Week/i.test(await schedule.locator('span.dateAppointment').innerText()), `${label}: the header does not say which week is shown`);
    h.assert(await schedule.getByText('CARLOS has encountered an unexpected error').count() === 0, `${label}: the week number landed on the CARLOS error page`);
    const [year, month, day] = [wednesday.getUTCFullYear(), wednesday.getUTCMonth() + 1, wednesday.getUTCDate()];
    const dayHeader = schedule.locator(`b > a[href*="year=${year}&month=${month}&day=${day}&view=0&displaymode=day"]`).first();
    await dayHeader.waitFor({timeout: 20000});
    // The sheet lists the providers it shows in a select; the one asked for must be the one selected.
    h.assert(await schedule.locator('select[name="provider_select"]').inputValue() === providerNo,
      `${label}: the week view is not for provider ${providerNo}`);
    const column = dayHeader.locator('xpath=ancestor::td[@valign="top"][1]');
    h.assert(await column.locator(`a.apptLink[onclick*="appointment_no=${appointments[providerNo]}&"]`).count() === 1,
      `${label}: the week view does not place provider ${providerNo}'s appointment under ${isoDate(wednesday)}`);
    if (otherNo) {
      h.assert(await apptLink(otherNo).count() === 0, `${label}: the week view also shows provider ${otherNo}'s appointment, so it is not provider ${providerNo}'s week alone`);
    }
  }

  let wednesday = null;
  await s.step('Month offers week-number links to the provider and none targets the retired week mode', async () => {
    await schedule.reload({waitUntil: 'domcontentloaded'});
    await schedule.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    await openMonth('Month');
    await chooseProvider(provider, 'Month of the signed-in provider');
    wednesday = await assertMonthWeekNumbers(provider, 'Month of the signed-in provider');
  });

  if (wednesday) {
    await s.step('a week number opens that week of the provider, with their appointment and not another provider\'s', async () => {
      const seeded = other && linksExpectedFor(other) ? [provider, other] : [provider];
      seedWeek(isoDate(wednesday), seeded);
      await followLastWeekNumber(provider, seeded.length > 1 ? other : null, wednesday, 'week number');
    });
  }

  if (!other) {
    console.log('  observed: no second active provider in this database, so the other-provider step is skipped');
  } else {
    await s.step(linksExpectedFor(other)
      ? 'another provider\'s month opens that provider\'s own week, not the signed-in provider\'s'
      : 'another provider\'s week numbers are plain text while the saved schedule view is pinned to a provider', async () => {
      await openMonth('Month from the week view');
      await chooseProvider(other, `Month of provider ${other}`);
      const otherWednesday = await assertMonthWeekNumbers(other, `Month of provider ${other}`);
      if (otherWednesday) {
        if (wednesday) {
          h.assert(isoDate(otherWednesday) === isoDate(wednesday), `The calendar changed between the two providers' months (${isoDate(wednesday)} then ${isoDate(otherWednesday)})`);
        } else {
          // The saved schedule view is this provider: only its month has links, so only its week is seeded.
          wednesday = otherWednesday;
          seedWeek(isoDate(wednesday), [other]);
        }
        await followLastWeekNumber(other, appointments[provider] ? provider : null, wednesday, `week number of provider ${other}`);
      }
    });
  }

  if (other && linksExpectedFor(other)) {
    await s.step('a provider the viewer is restricted from gets plain-text week numbers', async () => {
      // The day sheet's schedule-view selector hides providers in the viewer's MyGroupAccessRestriction rows; the
      // month page does not apply them, so the week link has to, or it would be a new route to a hidden schedule.
      restrictionId = sql.value(`INSERT INTO MyGroupAccessRestriction (myGroupNo, providerNo, lastUpdateUser, lastUpdateDate)
        VALUES (${h.sqlString(other)}, ${providerKey}, ${h.sqlString(s.config.testUser)}, NOW()); SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(restrictionId), 'The restriction fixture row was not created');
      await openMonth('Month before the restricted provider');
      await chooseProvider(other, `Month of restricted provider ${other}`);
      h.assert(await weekNumberCells().count() >= 8, 'The week numbers disappeared for the restricted provider; they should stay as text');
      h.assert(await weekLinks().count() === 0, `A week number links to the week of provider ${other}, whom the viewer is restricted from`);
      sql.execute(`DELETE FROM MyGroupAccessRestriction WHERE id=${restrictionId} AND providerNo=${providerKey} AND myGroupNo=${h.sqlString(other)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM MyGroupAccessRestriction WHERE id=${restrictionId}`) === '0', 'The restriction fixture row was not removed');
      restrictionId = null;
    });
  }

  await s.step('with All providers selected the week numbers are plain text', async () => {
    await openMonth('Month before All providers');
    await chooseProvider('all', 'Month of All providers');
    h.assert(await weekNumberCells().count() >= 8, 'The week numbers disappeared with All providers selected; they should stay as text');
    h.assert(await weekLinks().count() === 0, 'A week number is still a link in the All providers month, which has no single provider week view');
    h.assert(await retiredLinks().count() === 0, 'A week number targets the retired week mode in the All providers month');
  });
}
if (require.main === module) runWorkflow('schedule-month-week-links', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
