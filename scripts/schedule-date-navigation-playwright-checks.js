#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for moving around the schedule by date.
 *
 * WHY THE ROLLOVERS ARE THE POINT. appointmentprovideradminday.jsp builds its
 * previous/next-day links with raw arithmetic on the day number:
 *
 *   day=<%= isWeekView ? (day - 7) : (day - 1) %>     (previous)
 *   day=<%= isWeekView ? (day + 7) : (day + 1) %>     (next)
 *
 * and the month view does the same with month-1 / month+1. Nothing there knows
 * how long a month is. So on the FIRST of a month the previous-day link asks for
 * day 0, on the LAST it asks for day 32, in January the previous-month link asks
 * for month 0 and in December the next asks for month 13. Whether those are
 * correct depends entirely on the server normalising them, and that is a
 * property nobody can see 28 days out of 30: a clinician hits it twice a month,
 * reports "the schedule jumped", and it cannot be reproduced the next day.
 *
 * This check goes and stands on those days deliberately.
 *
 * IT REACHES THEM THE WAY A USER DOES: the date in the schedule header is a link
 * that opens the calendar popup, and clicking a day there navigates the opener.
 * No typed date, and no URL built by the check -- which matters here more than
 * usual, since the thing under test IS the URL the page builds.
 *
 * READ-ONLY: it navigates between days and months. It books nothing, edits
 * nothing, and opens no patient.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:schedule-date-navigation-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   SCHEDULE_NAV_TIMEOUT_MS=20000   per-step allowance
 *
 * IMPLEMENTS: coverage plan section 2.3, `schedule-views` (the date-navigation
 * half). App defects this finds are recorded in docs/ui-tests/app-findings-log.md.
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopup } = require('./lib/playwright-ui');

/** The header's date, as `EEE, yyyy-MM-dd` (date.EEEyyyyMMdd in the bundle). */
const DATE_SELECTOR = 'span.dateAppointment';

/** Both arrows carry class="redArrow"; the icon inside says which is which. */
const PREVIOUS_DAY = 'a.redArrow:has(span.fa-backward-step)';
const NEXT_DAY = 'a.redArrow:has(span.fa-forward-step)';

/** Read the day the schedule is currently showing. */
async function shownDate(page, timeout) {
  const text = await page.locator(DATE_SELECTOR).first().innerText({ timeout });
  const found = text.match(/(\d{4})-(\d{2})-(\d{2})/);
  assert(found,
    `The schedule header shows ${JSON.stringify(text.trim())}, which carries no yyyy-MM-dd date. `
    + 'Either the date format changed or the page is not the day view.');
  return found[0];
}

/** yyyy-MM-dd for a Date, in the same shape the header renders. */
function iso(date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
}

const parseIso = (text) => {
  const [year, month, day] = text.split('-').map(Number);
  return new Date(year, month - 1, day);
};

const addDays = (date, days) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
const lastDayOfMonth = (date) => new Date(date.getFullYear(), date.getMonth() + 1, 0);

/**
 * Jump to a day of the CURRENT month through the calendar popup.
 *
 * The header date is an anchor that opens share/CalendarPopup; clicking a day
 * cell there runs typeInDate(), which sets opener.location.href and closes the
 * popup. So the navigation lands on the schedule, not in the popup.
 */
async function jumpToDay(context, schedulePage, day, recorder, timeout) {
  const popup = await clickOpensPopup(schedulePage, schedulePage.locator('a.clickable-date').first(), {
    context, label: 'calendar', recorder, timeout,
  });
  // Read the cells once and address the match by position, rather than building
  // a RegExp from the day number: the repo's Semgrep rules flag new RegExp(...)
  // and a substring match would make "1" also match "11", "21" and "31".
  const days = await popup.$$eval('td a', (cells) => cells.map((cell) => (cell.textContent || '').trim()));
  const index = days.indexOf(String(Number(day)));
  assert(index >= 0,
    `The calendar popup offers no cell for day ${day} of the shown month (it offers ${days.filter(Boolean).length} days)`);
  const cell = popup.locator('td a').nth(index);
  await clickAndAwaitReload(schedulePage, cell, { timeout, label: 'the calendar day cell' });
  await popup.close().catch(() => {});
  return shownDate(schedulePage, timeout);
}

/** Click one of the day arrows and return the day it landed on. */
async function step(schedulePage, selector, timeout) {
  const control = schedulePage.locator(selector).first();
  assert(await control.count() > 0, `The schedule header offers no ${selector} control`);
  await clickAndAwaitReload(schedulePage, control, { timeout, label: selector });
  return shownDate(schedulePage, timeout);
}

/**
 * The four days where the page's arithmetic runs off the end of the month.
 *
 * Each is asserted in both directions, because a server that normalises day 0
 * correctly may still not normalise day 32, and landing back where you started
 * is the property a clinician actually relies on.
 */
async function checkMonthBoundaries(context, schedulePage, recorder, timeout) {
  const today = parseIso(await shownDate(schedulePage, timeout));
  const results = [];

  // --- the FIRST of the month: the previous-day link asks for day 0 ---
  const first = new Date(today.getFullYear(), today.getMonth(), 1);
  const landedFirst = await jumpToDay(context, schedulePage, 1, recorder, timeout);
  assert(landedFirst === iso(first),
    `The calendar was asked for the 1st and the schedule shows ${landedFirst}`);

  const backOne = await step(schedulePage, PREVIOUS_DAY, timeout);
  const expectedBack = iso(addDays(first, -1));
  assert(backOne === expectedBack,
    `From the 1st, the previous-day arrow landed on ${backOne} instead of ${expectedBack}. `
    + 'The link is built as day=<%= day - 1 %>, so on the 1st it asks the server for day 0 of this month; '
    + 'a clinician looking at yesterday\'s appointments is being shown another day entirely.');
  results.push(`1st -> ${backOne}`);

  const forwardOne = await step(schedulePage, NEXT_DAY, timeout);
  assert(forwardOne === iso(first),
    `Stepping back from the 1st and forward again landed on ${forwardOne}, not ${iso(first)}. `
    + 'Moving one day in each direction has to return to where it started.');

  // --- the LAST of the month: the next-day link asks for day 32 ---
  const last = lastDayOfMonth(today);
  const landedLast = await jumpToDay(context, schedulePage, last.getDate(), recorder, timeout);
  assert(landedLast === iso(last),
    `The calendar was asked for the ${last.getDate()}th and the schedule shows ${landedLast}`);

  const overTheEnd = await step(schedulePage, NEXT_DAY, timeout);
  const expectedNext = iso(addDays(last, 1));
  assert(overTheEnd === expectedNext,
    `From the last day of the month, the next-day arrow landed on ${overTheEnd} instead of ${expectedNext}. `
    + `The link is built as day=<%= day + 1 %>, so it asks the server for day ${last.getDate() + 1} of a month `
    + `that has ${last.getDate()}.`);
  results.push(`${iso(last)} -> ${overTheEnd}`);

  const backOverTheEnd = await step(schedulePage, PREVIOUS_DAY, timeout);
  assert(backOverTheEnd === iso(last),
    `Crossing into the next month and stepping back landed on ${backOverTheEnd}, not ${iso(last)}`);

  return results;
}

/** The month view, and the way back from it. */
async function checkMonthView(schedulePage, timeout) {
  const before = await shownDate(schedulePage, timeout);
  const monthLink = schedulePage.locator('a').filter({ hasText: /^\s*Month\s*$/i }).first();
  if (await monthLink.count() === 0) {
    // ABSENCE IS LEGITIMATE, SILENCE IS NOT. appointmentprovideradminday.jsp
    // wraps this anchor in <security:oscarSec objectName="_month" rights="r">,
    // so a provider without that right sees no Month link at all. Returning
    // null and still reporting PASS meant the check claimed month-view coverage
    // it had not attempted -- and would have gone on claiming it if the
    // selector went stale.
    //
    // So tell the two apart: the route is still in the page's markup if the
    // anchor merely renamed itself.
    const byRoute = schedulePage.locator('a[href*="displaymode=month"]');
    assert(await byRoute.count() === 0,
      'The schedule renders a displaymode=month link but nothing whose text is "Month", so this check\'s '
      + 'selector is stale rather than the control being absent');
    return { covered: false, reason: 'the logged-in provider has no _month read right, so the schedule renders no Month link' };
  }
  await clickAndAwaitReload(schedulePage, monthLink, { timeout, label: 'the Month link' });

  const body = await schedulePage.locator('body').innerText({ timeout }).catch(() => '');
  assert(body.trim().length > 0, 'The month view rendered a blank page');
  assert(!/CARLOS has encountered an unexpected error|Exception Report|HTTP Status \d/i.test(body),
    'The month view rendered an error page');

  // Back the way a user goes back: the month view's own Today link.
  const today = schedulePage.locator('a').filter({ hasText: /^\s*Today\s*$/i }).first();
  assert(await today.count() > 0,
    'The month view offers no Today link, so a user who opened it cannot get back to a day sheet');
  await clickAndAwaitReload(schedulePage, today, { timeout, label: "the month view's Today link" });
  const after = await shownDate(schedulePage, timeout);
  return { covered: true, before, after };
}

async function main() {
  const config = readConfig();
  const timeout = Number(process.env.SCHEDULE_NAV_TIMEOUT_MS || '20000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);

    const boundaries = await checkMonthBoundaries(context, schedulePage, recorder, timeout);
    const monthView = await checkMonthView(schedulePage, timeout);
    if (!monthView.covered) {
      // Reported, not swallowed: a reader of a green run must be able to see
      // that this half did not run, and why.
      console.log(`  month view NOT COVERED: ${monthView.reason}`);
    }

    assertStrictPage(recorder);
    console.log(`  crossed ${boundaries.length} month boundary/boundaries: ${boundaries.join(', ')}`);
    if (monthView.covered) {
      console.log(`  month view opened and returned to ${monthView.after}`);
    }
    return { boundaries, monthView };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'schedule-date-navigation', run: main });
}

module.exports = {
  DATE_SELECTOR, NEXT_DAY, PREVIOUS_DAY, addDays, checkMonthBoundaries, iso, lastDayOfMonth, main, parseIso,
};
