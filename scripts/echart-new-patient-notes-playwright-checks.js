#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the eChart notes-pagination loop, driven along
 * the exact path it was reported on: create a demographic, book an appointment
 * for it, open the eChart FROM that appointment, and watch the notes pane.
 *
 * The reported symptom was an eChart that "wants to keep loading notes" — the
 * console filling with `loading: offset: 0 / 20 / 40 ...` at one request per
 * second and a throbber that never cleared. A brand-new chart is what exposes
 * it: there are no notes to scroll away from, so the notes wrapper stays at the
 * top, which is the state that arms the 1s "load older notes" poll.
 *
 * The poll had no reachable stop condition. It decided "no more notes" by
 * testing the response body for emptiness, but the notes fragment always emits
 * bootstrap scripts, so an exhausted batch still came back non-empty; the one
 * branch meant to break the loop cleared an interval handle that did not exist.
 * The encounter layout also renders ChartNotes.jsp twice, which armed two polls
 * and left the first one unstoppable — the pre-fix chart issued TWO pagination
 * requests per second.
 *
 * What this check pins, in order:
 *   1. a chart opened from an appointment renders its note editor at all
 *      (the fix's first cut suppressed the second of the two initial loads and
 *      produced an EMPTY notes pane — green pagination, no chart),
 *   2. pagination settles instead of walking the offset forward forever,
 *   3. the loading throbber clears,
 *   4. only one poll is armed, so a settled chart makes no further requests.
 *
 * Step 2 is forced rather than hoped for: the poll only fires when the notes
 * wrapper overflows AND sits at scrollTop 0. That is the state the reporter's
 * window was in; a headless 1440x1100 window is not, so the check shrinks the
 * wrapper to put the page in the reported state deterministically. Without that
 * the check would pass on the broken build in a tall window.
 *
 * Requires the standard deb-install env contract (see
 * docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (fixture lookup and cleanup)
 * Optional: CHROME_PATH, ECHART_NEW_PATIENT_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.ECHART_NEW_PATIENT_SCREENSHOT_DIR || '/tmp',
};
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

// Unique, clearly synthetic name so a failed cleanup is identifiable and a
// stray record can never be mistaken for a real patient. Kept short: the
// column is varchar(30), and a name that gets truncated on save no longer
// matches the lookup that finds (and later deletes) the fixture.
const fixtureLastName = `PLAYWRIGHT-EC-${Date.now()}`;
const fixtureFirstName = 'Notes';
const appointmentReason = 'Notes pagination check';

// The poll ticks once a second. Settled means no new fetch for several ticks;
// the cap keeps a legitimately long chart from hanging the check while still
// failing a loop that never ends.
const POLL_QUIET_MS = 5000;
const POLL_TIMEOUT_MS = 40000;

const notesRequests = [];

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'echart-new-patient-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  try {
    fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  } catch (error) {
    // Never leave a half-written file holding the database password behind.
    fs.rmSync(dir, { recursive: true, force: true });
    throw error;
  }
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}

/**
 * Records every notes-pagination fetch the chart makes. The offset is what
 * exposes a runaway: a settled chart stops, a broken one climbs 20 at a time.
 */
function recordNotesRequests(page, label) {
  page.on('request', (request) => {
    const body = request.postData() || '';
    if (/(?:^|&)method=viewNotesOpt(?:&|$)/.test(body)) {
      const offsetMatch = /(?:^|&)offset=(\d+)/.exec(body);
      notesRequests.push({ label, offset: offsetMatch ? Number(offsetMatch[1]) : null });
    }
  });
}

/**
 * Replaces the shared dialog handler for the eChart window. The default one
 * dismisses, and the chart's note-lock confirm runs window.close() on dismiss —
 * which would close the page under test rather than fail with a useful message.
 */
function acceptDialogs(page, label, recorder) {
  page.removeAllListeners('dialog');
  page.on('dialog', async (dialog) => {
    recorder.dialogs.push({ label, type: dialog.type(), text: dialog.message() });
    await dialog.accept().catch(() => {});
  });
}

async function createDemographic(context, schedulePage, recorder) {
  const searchPopup = context.waitForEvent('page');
  await schedulePage.locator('a').filter({ hasText: /^Search$/ }).click();
  const searchPage = await searchPopup;
  wirePage(searchPage, 'search', recorder);
  await searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 });

  // A no-match search renders the results page that carries "Create Demographic".
  await searchPage.locator('#keyword, input[name="keyword"]').first().fill(fixtureLastName);
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    searchPage.locator("input[type='submit']").first().click(),
  ]);
  await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await searchPage.locator("a[href*='ViewDemographicAddARecordHtm']").first().click();
  await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(searchPage, 'add-demographic form');

  const form = searchPage.locator('form[name="adddemographic"]');
  await form.locator('input[name="last_name"]').fill(fixtureLastName);
  await form.locator('input[name="first_name"]').fill(fixtureFirstName);
  await form.locator('select[name="sex"]').selectOption('F');
  await form.locator('input[name="inputDOB"]').fill('1990-01-15');
  // The save-path validator rejects an empty/invalid postal code with an alert.
  await form.locator('input[name="postal"]').fill('M5W 1E6');
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {}),
    searchPage.locator('input[type="submit"][value="Add Record"]').first().click(),
  ]);
  await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  assert(recorder.dialogs.length === 0,
    `add-form validation blocked the save with a dialog: ${JSON.stringify(recorder.dialogs)}`);
  const savedBody = await searchPage.locator('body').innerText();
  assert(/Successful Addition of a Demographic Record/i.test(savedBody),
    `post-save page did not confirm the save: ${savedBody.replace(/\s+/g, ' ').slice(0, 300)}`);

  // Continue the way a user does: "Go to record" opens the new master record.
  // It is also what settles the save before the record is looked up below.
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {}),
    searchPage.locator('a').filter({ hasText: /Go to record/i }).first().click(),
  ]);
  await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(searchPage, 'new master record');
  const masterBody = await searchPage.locator('body').innerText();
  assert(masterBody.includes(fixtureLastName),
    `master record does not show the new patient: ${masterBody.replace(/\s+/g, ' ').slice(0, 300)}`);

  const created = sql(`SELECT demographic_no FROM demographic WHERE last_name='${fixtureLastName}' AND first_name='${fixtureFirstName}'`);
  assert(/^\d+$/.test(created), `new patient not found in the database (got '${created}')`);
  await searchPage.close();
  return created;
}

async function bookAppointment(context, schedulePage, recorder, demographicNo) {
  // Any open slot on the day sheet; the last one keeps the check clear of the
  // demo data's morning bookings. Double booking only warns, so the exact slot
  // does not matter to the outcome.
  const slots = schedulePage.locator('a.adhour');
  const slotCount = await slots.count();
  assert(slotCount > 0, 'no bookable time slots on the schedule day sheet');

  const apptPopup = context.waitForEvent('page');
  await slots.nth(slotCount - 1).click();
  const apptPage = await apptPopup;
  wirePage(apptPage, 'appointment', recorder);
  await apptPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await assertNotErrorPage(apptPage, 'add-appointment form');

  // Patient selection is the jQuery UI autocomplete on #keyword, which is what
  // sets the hidden demographic_no the save reads.
  await apptPage.locator('#keyword').fill(fixtureLastName);
  const suggestion = apptPage.locator('ul.ui-autocomplete li').first();
  await suggestion.waitFor({ state: 'visible', timeout: 15000 });
  await suggestion.click();
  const selected = await apptPage.locator('#demographic_no').inputValue();
  assert(selected === demographicNo,
    `autocomplete selected demographic ${selected}, expected the new patient ${demographicNo}`);

  await apptPage.locator('#reason').fill(appointmentReason);
  await screenshot(apptPage, config.screenshotDir, 'echart-new-patient-appointment-form');

  // The success page calls opener.refresh() and self.close(), so the popup
  // closing is the signal that the booking went through.
  const closed = apptPage.waitForEvent('close', { timeout: 30000 });
  await apptPage.locator('#addButton').click();
  await closed;

  const appointmentNo = sql(`SELECT appointment_no FROM appointment WHERE demographic_no=${demographicNo} ORDER BY appointment_no DESC LIMIT 1`);
  assert(/^\d+$/.test(appointmentNo), `appointment was not saved for demographic ${demographicNo} (got '${appointmentNo}')`);
  return appointmentNo;
}

async function openEchartFromAppointment(context, schedulePage, recorder, appointmentNo) {
  // The booking popup calls opener.refresh() on its way out, so the schedule is
  // usually already reloading; only reload it here if that did not land.
  await schedulePage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await schedulePage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  // The appointment's encounter ("E") link is what passes appointment_no into
  // the chart — the reported path. Anchored on the appointment id rather than
  // the patient name: the day sheet truncates long names in the cell.
  const encounterLink = schedulePage
    .locator(`a.encounterBtn[onclick*=",${appointmentNo});"]`).first();
  try {
    await encounterLink.waitFor({ state: 'visible', timeout: 15000 });
  } catch (notThereYet) {
    await schedulePage.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
    await schedulePage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await encounterLink.waitFor({ state: 'visible', timeout: 15000 });
  }

  const echartPopup = context.waitForEvent('page');
  await encounterLink.click();
  const echart = await echartPopup;
  wirePage(echart, 'echart', recorder);
  acceptDialogs(echart, 'echart', recorder);
  recordNotesRequests(echart, 'echart');
  await echart.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await echart.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return echart;
}

/**
 * Puts the notes wrapper into the state that arms the poll — overflowing and
 * scrolled to the top — then waits for pagination to stop.
 */
async function assertNotesPaginationSettles(echart) {
  const geometry = await echart.locator('#encMainDivWrapper').first().evaluate((element) => {
    element.style.flex = 'none';
    element.style.height = '80px';
    element.scrollTop = 0;
    return { scrollHeight: element.scrollHeight, clientHeight: element.clientHeight };
  });
  assert(geometry.scrollHeight > geometry.clientHeight,
    `notes wrapper did not overflow, so the pagination poll was never armed: ${JSON.stringify(geometry)}`);

  const deadline = Date.now() + POLL_TIMEOUT_MS;
  let observed = notesRequests.length;
  let stableSince = Date.now();
  while (Date.now() < deadline) {
    await echart.waitForTimeout(500);
    if (notesRequests.length !== observed) {
      observed = notesRequests.length;
      stableSince = Date.now();
    } else if (Date.now() - stableSince >= POLL_QUIET_MS) {
      return;
    }
  }
  throw new Error('notes pagination never stopped on a chart with no notes; '
    + `${notesRequests.length} viewNotesOpt requests at offsets `
    + `${notesRequests.map((r) => r.offset).join(', ')}`);
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  initMysqlDefaults();
  let demographicNo = null;
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const schedulePage = await login(context, config, recorder);

    demographicNo = await createDemographic(context, schedulePage, recorder);
    const appointmentNo = await bookAppointment(context, schedulePage, recorder, demographicNo);
    const echart = await openEchartFromAppointment(context, schedulePage, recorder, appointmentNo);

    // A chart with no notes still renders the new-note editor. Asserting this
    // BEFORE the pagination assertions matters: an empty notes pane also makes
    // pagination trivially "settle", so the cheap green must not be reachable.
    const editor = echart.locator('#encMainDiv textarea[name="caseNote_note"]').first();
    await editor.waitFor({ state: 'attached', timeout: 30000 });
    const noteChildren = await echart.locator('#encMainDiv').first()
      .evaluate((element) => element.children.length);
    assert(noteChildren > 0, 'the notes container came up empty on a chart opened from an appointment');
    await screenshot(echart, config.screenshotDir, 'echart-new-patient-initial');

    await assertNotesPaginationSettles(echart);

    const throbber = await echart.locator('#notesLoading').first().evaluate((element) => ({
      display: getComputedStyle(element).display,
      visible: !!(element.getBoundingClientRect().height || element.getClientRects().length),
    }));
    assert(!throbber.visible && throbber.display === 'none',
      `notes loading throbber stayed visible after pagination stopped: ${JSON.stringify(throbber)}`);

    // An empty chart needs the initial load only. The encounter layout renders
    // ChartNotes.jsp twice, so two are expected; anything beyond that means a
    // poll walked the offset forward, or a second poll is still armed.
    const paginationRequests = notesRequests.filter((r) => r.offset > 0);
    assert(paginationRequests.length === 0,
      `a chart with no notes paged for more: ${JSON.stringify(notesRequests)}`);
    assert(notesRequests.length <= 2,
      `unexpected number of notes fetches on an empty chart: ${JSON.stringify(notesRequests)}`);
    await screenshot(echart, config.screenshotDir, 'echart-new-patient-settled');

    const fatalConsole = recorder.consoleIssues.filter((issue) => /is not defined|SyntaxError|ReferenceError|Cannot read/i.test(issue.text || ''));
    assert(fatalConsole.length === 0, `fatal console errors in the eChart: ${JSON.stringify(fatalConsole)}`);

    await context.close();
    console.log(`PASS new patient ${demographicNo}, appointment ${appointmentNo}: eChart rendered its note `
      + `editor, pagination settled after ${notesRequests.length} fetch(es), throbber cleared`);
  } catch (error) {
    console.error('FAIL eChart new-patient notes Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify({ notesRequests, ...buildFailureDetails(recorder) }, null, 2));
    process.exitCode = 1;
  } finally {
    // Remove the fixture patient and everything the flow hung off it. Looked up
    // by the unique per-run last name — never a bare number — so a bug can never
    // delete a pre-existing record, and a run that failed before capturing the
    // id still cleans up after itself.
    try {
      const leftover = demographicNo
        || sql(`SELECT demographic_no FROM demographic WHERE last_name='${fixtureLastName}' AND first_name='${fixtureFirstName}'`);
      if (/^\d+$/.test(leftover)) {
        sql(`DELETE FROM appointment WHERE demographic_no=${leftover}`);
        sql(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${leftover}`);
        sql(`DELETE FROM admission WHERE client_id=${leftover}`);
        sql(`DELETE FROM demographicArchive WHERE demographic_no=${leftover}`);
        sql(`DELETE FROM demographic WHERE demographic_no=${leftover} AND last_name='${fixtureLastName}'`);
      }
    } catch (cleanupError) {
      // A synthetic patient left in a clinical database is a failed run, not a warning:
      // the next operator has no way to tell it apart from a real record at a glance.
      console.error(`FAIL cleanup failed, fixture ${fixtureLastName} may remain: ${cleanupError.message}`);
      process.exitCode = 1;
    }
    cleanupMysqlDefaults();
    await browser.close();
  }
})();
