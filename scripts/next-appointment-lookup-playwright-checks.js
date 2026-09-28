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
 * Browser regression check for the patient search's "next appointment" column
 * (issue #2651).
 *
 * WHY A BROWSER CHECK. AppointmentUtil.getNextAppointment() guarded its input
 * backwards: a real demographic number hit the early "(none)" return before the
 * database was ever asked, and the formatted value came from a local that was
 * never assigned. A unit test pins the utility, but the column only exists as a
 * field on the JSON the schedule's quick-search widget receives from
 * /demographic/SearchDemographic, so only driving that widget shows the fix
 * reaching the surface an operator sees -- through the packaged front door,
 * CSRF header and WAF included.
 *
 * WHAT IT ASSERTS, in both directions:
 *   1. Before seeding, the column matches what the database says the patient's
 *      next appointment is -- the sentinel when they have none. This is the
 *      direction that catches a column quietly reporting something.
 *   2. With one appointment seeded for tomorrow, the column reports that date
 *      and not the sentinel. This is the assertion that fails on the bug.
 *   3. With the fixture removed, the column returns to its original value, so a
 *      passing run also proves the check left the dataset as it found it.
 *
 * The value is asserted against the same selection
 * OscarAppointmentDaoImpl.findNextAppointment() makes -- the earliest
 * uncancelled appointment that has not started yet -- rather than against the
 * seeded date alone, so the check stays honest on a dataset where the patient
 * already has appointments.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, then typing into the schedule's
 * #quickSearch box. The response that widget receives is the surface asserted
 * on, because no page renders this value today; the dropdown rendering from the
 * same payload is what proves the widget consumed it.
 *
 * FIXTURE: one appointment for tomorrow, removed in a finally.
 *
 * SKIPS, rather than passing vacuously, when `workflow_enhance` is false (the
 * package ships it false and the field is only produced when it is on). To run
 * it, set workflow_enhance = true in /etc/carlos-emr/carlos.properties and
 * `carlos-ctl restart`.
 *
 * Required environment:
 *   MYSQL_PASSWORD                the database password; the check compares the
 *                                 column against SQL and seeds its own fixture
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   NEXT_APPT_DEMOGRAPHIC_NO=1    the patient to search for
 *   NEXT_APPT_PROVIDER_NO=999998  the provider the seeded appointment belongs to
 *   NEXT_APPT_TIMEOUT_MS=30000    per-step allowance
 *
 * No patient identifier, name or appointment date is printed: the search term is
 * read from the database and only ever typed into the page.
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, createSqlRunner, launchBrowser, login,
  newContext, readConfig, runCheck, sqlString,
} = require('./lib/playwright-harness');

/** What AppointmentUtil renders when there is no next appointment to show. */
const NONE = '(none)';

/**
 * The same selection OscarAppointmentDaoImpl.findNextAppointment() makes, as SQL:
 * the earliest uncancelled appointment that has not started yet. Returns the
 * sentinel when there is none, so it can be compared against the column directly.
 */
function expectedNextAppointment(sql, demographicNo) {
  const date = sql.value(
    "SELECT IFNULL(DATE_FORMAT(MIN(appointment_date), '%Y-%m-%d'), '') FROM appointment"
    + ` WHERE demographic_no = ${Number(demographicNo)} AND status NOT LIKE '%C%'`
    + ' AND (appointment_date > CURDATE() OR (appointment_date = CURDATE() AND start_time >= CURTIME()))',
  );
  return date || NONE;
}

/**
 * Types the patient's name into the quick-search widget and returns their row
 * from the JSON the widget itself receives.
 */
async function searchRow(page, surname, demographicNo, timeout, throwIfCancelled = () => {}) {
  throwIfCancelled();
  // The trailing comma selects surname search explicitly. The widget's
  // detectSearchType() reads a bare surname that ends in digits -- a synthetic
  // one such as Pr3668 -- as a health-card number, and would then answer about
  // no patient at all.
  const term = `${surname},`;
  await page.reload({ waitUntil: 'domcontentloaded', timeout });
  throwIfCancelled();
  const quickSearch = page.locator('#quickSearch');
  await quickSearch.waitFor({ state: 'visible', timeout });
  throwIfCancelled();
  await quickSearch.click();
  throwIfCancelled();
  // The widget fires a request per keystroke and aborts the previous one, so
  // wait for the response to the request carrying the WHOLE term rather than to
  // a prefix of it, which would answer about a different set of patients. The
  // term is compared as a parsed parameter, not as a substring of the body: a
  // name that is a prefix of another patient's would otherwise match early.
  const [response] = await Promise.all([
    page.waitForResponse((candidate) => candidate.request().method() === 'POST'
      && new URL(candidate.url()).pathname.endsWith('/demographic/SearchDemographic')
      && new URLSearchParams(candidate.request().postData() || '').get('term') === term, { timeout }),
    page.keyboard.type(term, { delay: 40 }),
  ]);
  throwIfCancelled();
  assert(response.status() === 200, `the quick search answered HTTP ${response.status()}`);
  const contentType = response.headers()['content-type'] || '';
  assert(/application\/json/i.test(contentType),
    `the quick search answered ${contentType || 'no content type'} instead of JSON`);
  let results;
  try {
    results = await response.json();
  } catch (error) {
    throw new Error(`the quick search did not return JSON: ${error.message}`);
  }
  assert(Array.isArray(results), 'the quick search did not return a result array');
  // The dropdown rendering from this same payload is what proves the widget
  // consumed it rather than discarding the response into a catch block.
  await page.locator('#quickSearchDropdown .qs-result-row').first().waitFor({ state: 'visible', timeout });
  throwIfCancelled();
  const row = results.find((entry) => String(entry.demographicNo) === String(demographicNo));
  // No identifier in the message: runCheck() writes it to stdout and into
  // RESULT_JSON, and a demographic number joins straight back to a patient.
  assert(row, `the quick search returned ${results.length} row(s), none of them the patient this check was pointed at`);
  return row;
}

function assertColumn(row, expected, when) {
  assert(row.nextAppointment === expected,
    `the next-appointment column disagrees with the database ${when}`);
  assert(row.nextAppt === row.nextAppointment,
    'the nextAppt alias disagrees with nextAppointment');
}

async function main({ throwIfCancelled = () => {} } = {}) {
  throwIfCancelled();
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const demographicNo = process.env.NEXT_APPT_DEMOGRAPHIC_NO || '1';
  const providerNo = process.env.NEXT_APPT_PROVIDER_NO || '999998';
  const timeout = Number(process.env.NEXT_APPT_TIMEOUT_MS || '30000');
  assert(/^\d+$/.test(demographicNo), 'NEXT_APPT_DEMOGRAPHIC_NO must be digits only');
  assert(/^\d+$/.test(providerNo), 'NEXT_APPT_PROVIDER_NO must be digits only');
  assert(Number.isFinite(timeout) && timeout > 0, 'NEXT_APPT_TIMEOUT_MS must be a positive finite number');
  const stamp = `PW_NEXT_APPT_${Date.now()}`;

  const sql = createSqlRunner(config.mysql);
  // Everything between createSqlRunner and the try/finally has to clean up after
  // itself: createSqlRunner writes MYSQL_PASSWORD to a 0600 option file that only
  // dispose() removes, so a throw out here would leave it behind.
  const recorder = createRecorder();
  let browser;
  try {
    browser = await launchBrowser(config);
  } catch (error) {
    sql.dispose();
    throw error;
  }

  let primaryError;
  try {
    throwIfCancelled();
    // Never printed: it is a patient's name.
    const surname = sql.value(`SELECT last_name FROM demographic WHERE demographic_no = ${Number(demographicNo)}`);
    assert(surname, 'NEXT_APPT_DEMOGRAPHIC_NO names a patient that does not exist in this database');

    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    throwIfCancelled();

    const before = await searchRow(schedulePage, surname, demographicNo, timeout, throwIfCancelled);
    if (!Object.prototype.hasOwnProperty.call(before, 'nextAppointment')) {
      throw new SkipCheck('the search result carries no nextAppointment field, so workflow_enhance is false on this'
        + ' deployment; set workflow_enhance = true in carlos.properties and restart to run this check');
    }
    const expectedBefore = expectedNextAppointment(sql, demographicNo);
    assertColumn(before, expectedBefore, 'before the fixture was seeded');

    sql.execute('INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,'
      + ' program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,'
      + ' updatedatetime, creator, remarks, urgency)'
      + ` SELECT ${sqlString(providerNo)}, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00', '09:15:00',`
      + ` CONCAT(last_name, ',', first_name), ${Number(demographicNo)}, 0, ${sqlString(stamp)},`
      + ` ${sqlString(stamp)}, '', '', '', '', '', 't', NOW(), NOW(), ${sqlString(providerNo)}, '', ''`
      + ` FROM demographic WHERE demographic_no = ${Number(demographicNo)}`);
    assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE notes = ${sqlString(stamp)}`) === '1',
      'the fixture appointment was not created');

    const after = await searchRow(schedulePage, surname, demographicNo, timeout, throwIfCancelled);
    const expectedAfter = expectedNextAppointment(sql, demographicNo);
    assert(expectedAfter !== NONE, 'the seeded appointment is not the next one the DAO would select');
    // The assertion that fails on issue #2651: before the fix the column was the
    // sentinel for every patient, whatever they had booked.
    assert(after.nextAppointment !== NONE,
      `the next-appointment column reported ${NONE} for a patient with an appointment tomorrow;`
      + ' the issue #2651 guard is inverted again');
    assertColumn(after, expectedAfter, 'with an appointment seeded for tomorrow');

    sql.execute(`DELETE FROM appointment WHERE notes = ${sqlString(stamp)}`);
    const restored = await searchRow(schedulePage, surname, demographicNo, timeout, throwIfCancelled);
    assertColumn(restored, expectedBefore, 'after the fixture was removed');

    throwIfCancelled();
    assertStrictPage(recorder);
    console.log('  the next-appointment column tracked the seeded appointment and returned to its original value');
    return { seeded: true };
  } catch (error) {
    primaryError = error;
    throw error;
  } finally {
    // Attempt every cleanup operation and retain the original diagnostic if
    // cleanup also fails; a finally exception must not erase the failed assertion.
    const cleanupErrors = [];
    for (const cleanup of [
      () => sql.execute(`DELETE FROM appointment WHERE notes = ${sqlString(stamp)}`),
      () => sql.dispose(),
      () => browser.close(),
    ]) {
      try {
        await cleanup();
      } catch (error) {
        cleanupErrors.push(error);
      }
    }
    if (cleanupErrors.length) {
      const detail = cleanupErrors.map((error) => error.message).join('; ');
      throw new Error(`${primaryError ? `${primaryError.message}; ` : ''}cleanup failed: ${detail}`);
    }
    throwIfCancelled();
  }
}

if (require.main === module) {
  runCheck({ name: 'next-appointment-lookup', run: main });
}

module.exports = {
  NONE, assertColumn, expectedNextAppointment, main, searchRow,
};
