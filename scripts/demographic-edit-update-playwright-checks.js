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
 * Browser regression check: editing a patient's demographics through the UI
 * actually writes every field, and the page shows what was written.
 *
 * WHY THIS EXISTS. Every other check on this page opens it; none of them changes
 * anything and reads the row back. The failure this is for is the one the suite's
 * own rules call out -- "a page that shows a success banner and writes nothing is
 * the failure these workflows actually have, and it is invisible to a check that
 * only reads the page". A silently dropped demographic field is worse than most:
 * a clinic corrects a patient's phone number or postal code, the page says saved,
 * and the correction is gone. Nothing in CARLOS tells anyone.
 *
 * THE ASSERTION IS THREE-SIDED, and all three are needed:
 *   1. the row in MariaDB carries the new value  -- proves the write happened;
 *   2. the re-opened form shows it               -- proves the read path agrees,
 *      which is what the clinic actually sees next time;
 *   3. a field left alone is unchanged           -- proves the update is not
 *      clobbering columns it was not asked to touch, which is the other way this
 *      screen can lose data.
 *
 * ENTERED THE WAY A CLINIC ENTERS IT: login, the schedule's Search control, the
 * patient row, the record's Edit link. Not a demographic URL.
 *
 * IT MUTATES, SO IT RESTORES. It edits an existing demo patient rather than
 * creating one (demographic-add-playwright-checks.js covers creation, and
 * deleting a patient is far messier than restoring five columns). The original
 * values are captured before the edit and written back in a finally, whether the
 * assertions passed or threw. Only the columns this check touched are restored,
 * and only for the one demographic_no it edited.
 *
 * NO REAL PATIENT DATA IS WRITTEN: every value is obviously synthetic and carries
 * this run's marker, and no value is ever logged -- the diagnostics name the
 * field, never its content.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:demographic-edit-update-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   DEMOGRAPHIC_EDIT_SEARCH=FAKE-        surname prefix to search for
 *   DEMOGRAPHIC_EDIT_DEMOGRAPHIC_NO=2    prefer this patient from the results
 *   DEMOGRAPHIC_EDIT_TIMEOUT_MS=20000
 *
 * IMPLEMENTS: coverage plan section 2.4, `demographic-edit-update`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  assert, assertStrictPage, createSqlRunner, createRecorder, launchBrowser, login, newContext, readConfig,
  runCheck, sqlString,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopup } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/*
 * The fields this check round-trips: form input name -> demographic column.
 *
 * Chosen to be free text (no dropdown whose options vary by deployment), clearly
 * synthetic when filled, and non-clinical -- a demographic's address and contact
 * details, not their care. `sin` and `hin` are deliberately excluded: they are
 * identifiers a check has no business writing, even on a FAKE- demo patient.
 */
const ROUND_TRIP_FIELDS = [
  { input: 'city', column: 'city', value: (marker) => `CITY-${marker}` },
  { input: 'postal', column: 'postal', value: () => 'X0X0X0' },
  // 555-01xx is the reserved fictional range, so this can never be a real number.
  { input: 'phone', column: 'phone', value: () => '555-0142' },
  { input: 'email', column: 'email', value: (marker) => `pw-${marker.toLowerCase()}@example.invalid` },
  // demographic.chart_no is varchar(10) (V1__baseline_schema.sql), so the full
  // marker does not fit. The last 8 digits of the timestamp still make a
  // leftover row traceable to the run that wrote it, and a value that overflows
  // the column would be truncated or rejected rather than round-tripped.
  { input: 'chart_no', column: 'chart_no', value: (marker) => `PW${marker.slice(-8)}` },
];

/** A column the check never writes, used to prove the update is not clobbering. */
const UNTOUCHED_COLUMN = 'last_name';

/**
 * Put the Master Record into edit mode.
 *
 * It is a BUTTON that toggles in place, not a link that navigates: edit.jsp
 * renders <button id="editBtn" onclick="showHideDetail()">, and showHideDetail()
 * reveals #editDemographic and #updateButton without a request. Waiting for a
 * navigation here would wait out the whole timeout and then type into inputs
 * that are still display:none.
 */
async function openEditForm(masterPage, timeout) {
  const editButton = masterPage.locator('#editBtn');
  assert(await editButton.count() > 0,
    'The Master Record offers no Edit control, so a clinic cannot correct a patient record from it. '
    + 'It is rendered only with _demographic write rights and only on the head record of a merge.');
  await editButton.scrollIntoViewIfNeeded().catch(() => {});
  await editButton.click({ timeout });
  // The section becoming visible IS the assertion that edit mode opened; without
  // it every fill() below would fail one at a time with a less useful message.
  await masterPage.locator('#editDemographic').waitFor({ state: 'visible', timeout });
  await masterPage.locator('#updateButton').waitFor({ state: 'visible', timeout });
  return masterPage;
}

/** Which of the round-trip fields this deployment actually renders. */
async function presentFields(page) {
  const present = [];
  for (const field of ROUND_TRIP_FIELDS) {
    const locator = page.locator(`[name="${field.input}"]`).first();
    // Visible, not merely present: these inputs are in the DOM whether or not
    // edit mode is open, so counting them would pass on a collapsed form and
    // then fail one fill() at a time.
    if (await locator.isVisible().catch(() => false)) {
      present.push(field);
    }
  }
  assert(present.length >= 3,
    `The edit form rendered only ${present.length} of the ${ROUND_TRIP_FIELDS.length} fields this check writes; `
    + 'either the form changed or it failed to render, and a round-trip over one field proves little');
  return present;
}

/**
 * Every row the Audit Information popup is showing, as "time|provider|action".
 *
 * The audit trail is a compliance control: PIPEDA and HIPAA both require that
 * who changed a patient record, and when, is recorded. A trail that silently
 * stops recording looks exactly like one that is working -- the page renders, the
 * old rows are all there, and only the new edit is missing. Nothing else in this
 * suite looks at it.
 *
 * demographicAudit.jsp sorts ASCENDING by time and DataTables pages at ten, so
 * the newest entry is on the last page. The check selects "All" from the page's
 * own length menu rather than paging, which is what a user does when looking for
 * a recent change.
 */
async function auditRows(context, masterPage, recorder, timeout) {
  const control = masterPage.locator('input[value="Audit Information"]').first();
  assert(await control.count() > 0,
    'The Master Record offers no Audit Information control, so a clinic cannot see who changed a patient record');
  const audit = await clickOpensPopup(masterPage, control, {
    context, label: 'demographic-audit', recorder, timeout,
  });
  try {
    await audit.locator('#auditLog').waitFor({ state: 'visible', timeout });
    // "All", so the newest row is on the page being read. -1 is the value the
    // page's own lengthMenu uses for it.
    // NOT swallowed. demographicAudit.jsp sorts ascending and DataTables pages
    // at ten, so the row this check is looking for is on the LAST page. If the
    // length menu does not take, auditRows() reads page one, the new entry is
    // absent, and the check reports that CARLOS failed to write an audit record
    // it actually wrote.
    await audit.locator('select[name="auditLog_length"]').first()
      .selectOption('-1', { timeout });
    return audit.$$eval('#auditLog tbody tr', (rows) => rows
      .map((row) => Array.from(row.querySelectorAll('td'))
        .slice(0, 3)
        .map((cell) => (cell.textContent || '').trim())
        .join('|'))
      .filter((row) => row.replace(/\|/g, '').length > 0));
  } finally {
    await audit.close().catch(() => {});
  }
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.DEMOGRAPHIC_EDIT_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.DEMOGRAPHIC_EDIT_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.DEMOGRAPHIC_EDIT_TIMEOUT_MS || '20000');
  const marker = `EDIT${Date.now()}`;

  const recorder = createRecorder();
  const sql = createSqlRunner(config.mysql);
  // The runner has already written a 0600 option file holding MYSQL_PASSWORD, so
  // the browser launch has to be inside its cleanup boundary: a Chromium that is
  // missing or fails to start would otherwise leave that file in the temp
  // directory with nothing left running to remove it.
  let browser;
  try {
    browser = await launchBrowser(config);
  } catch (error) {
    sql.dispose();
    throw error;
  }

  let demographicNo = null;
  let original = null;
  let fields = [];

  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });

    // Take the id from the page the UI landed on, not from the environment: the
    // check must assert against the patient it actually opened.
    const landed = masterPage.url().match(/demographic_no=(\d+)/);
    assert(landed, 'Could not determine which patient the Master Record opened');
    [, demographicNo] = landed;

    // The audit trail as it stands BEFORE the edit, so the new row can be found
    // by difference rather than by guessing at its timestamp.
    const auditBefore = await auditRows(context, masterPage, recorder, timeout);

    await openEditForm(masterPage, timeout);
    fields = await presentFields(masterPage);

    // Capture the originals from the DATABASE, not the form: a form that renders
    // a field blank when the column is populated is itself a defect, and
    // restoring from the form would then silently erase the real value.
    const columns = [...fields.map((field) => field.column), UNTOUCHED_COLUMN];
    // Each column is selected TWICE: its value, and an explicit IS NULL flag.
    // `mysql -B` prints SQL NULL and the string 'NULL' the same way, so without
    // the flag the restore below would write a NULL column over a patient whose
    // chart number really is the text "NULL" -- destroying the value this check
    // exists to put back.
    const selected = columns
      .map((column) => `\`${column}\`, \`${column}\` IS NULL`)
      .join(', ');
    const [before] = sql.rows(
      `SELECT ${selected} FROM demographic WHERE demographic_no = ${Number(demographicNo)}`,
    );
    // No identifier in the message: runCheck() writes it to stdout and into
    // RESULT_JSON, and demographic_no joins straight back to a patient.
    assert(before, 'No demographic row for the patient the UI opened');
    original = Object.fromEntries(columns.map((column, index) => [
      column,
      // The flag, not the parsed token, decides.
      before[index * 2 + 1] === '1' ? null : before[index * 2],
    ]));

    for (const field of fields) {
      const input = masterPage.locator(`[name="${field.input}"]`).first();
      await input.scrollIntoViewIfNeeded().catch(() => {});
      await input.fill(field.value(marker));
    }

    const save = masterPage.locator('input[value="Update Record"], button:has-text("Update Record")').first();
    assert(await save.count() > 0, 'The edit form offers no "Update Record" control');
    // The database is read back on the very next line, so a wait that returns
    // before the POST has even started would race the write it is meant to
    // observe -- and the comparison would then blame the application for
    // dropping a field it had simply not stored yet.
    await clickAndAwaitReload(masterPage, save, { timeout, label: 'Update Record' });

    // 1. The write reached the database.
    const [after] = sql.rows(
      `SELECT ${columns.map((column) => `\`${column}\``).join(', ')} FROM demographic WHERE demographic_no = ${Number(demographicNo)}`,
    );
    assert(after, 'The demographic row disappeared after the update');
    const stored = Object.fromEntries(columns.map((column, index) => [column, after[index]]));

    const dropped = fields.filter((field) => stored[field.column] !== field.value(marker));
    assert(dropped.length === 0,
      `The form reported the record saved, but ${dropped.length} field(s) did not reach the database: `
      + `${dropped.map((field) => field.input).join(', ')}. `
      + 'A correction typed here would be silently lost.');

    // 2. A field the check never touched is untouched.
    assert(stored[UNTOUCHED_COLUMN] === original[UNTOUCHED_COLUMN],
      `Updating the patient changed ${UNTOUCHED_COLUMN}, which the check never edited; the update is clobbering columns it was not asked to touch`);

    // 3. The read path agrees -- what the clinic sees the next time it opens the
    // record. A write that the form cannot read back is still a lost correction.
    await openEditForm(masterPage, timeout);
    const notShown = [];
    for (const field of fields) {
      const shown = await masterPage.locator(`[name="${field.input}"]`).first().inputValue().catch(() => '');
      if (shown !== field.value(marker)) {
        notShown.push(field.input);
      }
    }
    assert(notShown.length === 0,
      `${notShown.length} field(s) are stored correctly but the re-opened form does not show them: ${notShown.join(', ')}`);

    // 4. The edit was RECORDED. DemographicUpdate2Action writes
    // LogAction.addLog(provider, "update", "demographic", ...), and the audit
    // page is where a clinic reads it back. A trail that silently stops
    // recording looks exactly like a working one -- the page renders, every old
    // row is there, and only this edit is missing.
    // POLLED, NOT READ ONCE. LogAction.addLog hands the write to a background
    // executor, so the row is not guaranteed to be committed by the time the
    // update response comes back. A single read here is a race: it passes on a
    // quick machine and reports "CARLOS wrote no audit record" on a loaded one,
    // which is the worst kind of failure to hand a maintainer -- a compliance
    // alarm that is really a timing artefact. Re-opening the audit popup until
    // a new row appears turns a slow write into a slow check rather than a
    // false finding; a write that never happens still fails, just later.
    const auditDeadline = Date.now() + timeout;
    let auditAfter = await auditRows(context, masterPage, recorder, timeout);
    while (auditAfter.length <= auditBefore.length && Date.now() < auditDeadline) {
      await masterPage.waitForTimeout(500);
      auditAfter = await auditRows(context, masterPage, recorder, timeout);
    }
    // MULTIPLICITY, NOT SET MEMBERSHIP. The row key is the first three cells,
    // and demographicAudit.jsp formats `created` with
    // SimpleDateFormat("yyyy-MM-dd HH:mm:ss") -- second precision, with the log
    // id and content deliberately not shown. Two updates by the same provider
    // in the same second are therefore the SAME STRING, so a Set said the new
    // row was already known and the check reported that CARLOS had failed to
    // write an audit record it had in fact written. Counting occurrences tells
    // a duplicated row from an absent one.
    const tally = (rows) => rows.reduce(
      (counts, row) => counts.set(row, (counts.get(row) || 0) + 1),
      new Map(),
    );
    const countsBefore = tally(auditBefore);
    const added = [];
    for (const [row, count] of tally(auditAfter)) {
      for (let copy = countsBefore.get(row) || 0; copy < count; copy += 1) {
        added.push(row);
      }
    }
    assert(added.length > 0,
      `Updating the patient added no row to the audit trail (${auditBefore.length} rows before, `
      + `${auditAfter.length} after). Who changed a patient record and when is a compliance control, not a log.`);
    const updates = added.filter((row) => /\|\s*update\s*$/i.test(row));
    assert(updates.length > 0,
      `The audit trail gained ${added.length} row(s) but none records an "update" action, so the change is not `
      + 'attributable to what was done');
    // Rows are "time|provider|action"; an entry that cannot say WHO is not an
    // audit entry. The provider name is not printed -- only whether it is there.
    const anonymous = updates.filter((row) => !row.split('|')[1].trim());
    assert(anonymous.length === 0,
      `${anonymous.length} of the new audit row(s) name no provider, so the record cannot say who made the change`);

    // Everything above asserts what the database holds; this asserts what the
    // browser reported while getting there. Without it an uncaught page error,
    // a failed request or an unexpected dialog on the Master Record is recorded
    // and then thrown away, which is the Phase 0 contract this suite exists for.
    assertStrictPage(recorder);

    console.log(`  round-tripped ${fields.length} demographic field(s) through the UI and the database, `
      + `and the edit added ${added.length} audit row(s)`);
    // No demographicNo. Correcting an earlier claim in this file's history:
    // runCheck() does NOT serialise a check's return value -- the RESULT_JSON
    // record is { name, outcome, detail, durationMs }, and only `detail` (the
    // thrown message) comes from the check. So this object reaches the process
    // that spawned the check, not the archived artifact.
    //
    // It is still not the place for a demographic number. The value is logged
    // by callers, carried across a module boundary, and one `...spread` away
    // from a message that IS archived. The field names say what was covered
    // without saying who it happened to, and cost nothing.
    return { fields: fields.map((field) => field.input) };
  } finally {
    // EVERY CAPTURED COLUMN, not only the edited ones. UNTOUCHED_COLUMN is
    // captured and asserted precisely because the application might clobber it,
    // and restoring only `fields` meant that when it DID -- the one case the
    // assertion exists to catch -- the assertion threw and this block left the
    // shared test patient permanently modified. The check for fixture
    // corruption must not be the thing that leaves fixture corruption behind.
    try {
      if (demographicNo && original) {
        const assignments = Object.keys(original)
          .map((column) => `\`${column}\` = ${original[column] === null ? 'NULL' : sqlString(original[column])}`)
          .join(', ');
        if (assignments) {
          sql.execute(`UPDATE demographic SET ${assignments} WHERE demographic_no = ${Number(demographicNo)}`);
        }
      }
    } finally {
      sql.dispose();
      await browser.close().catch(() => {});
    }
  }
}

if (require.main === module) {
  runCheck({ name: 'demographic-edit-update', run: main });
}

module.exports = { ROUND_TRIP_FIELDS, UNTOUCHED_COLUMN, main, openEditForm, presentFields };
