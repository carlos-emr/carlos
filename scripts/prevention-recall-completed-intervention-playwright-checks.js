#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 10: the recall report before and after the patient's intervention is entered, and after it is
 * corrected. prevention-recall-report proves the report runs and lists a due patient; boundary-date-prevention-report
 * pins the as-of date's inclusivity. Neither reruns the report after a clinician acts on what it said.
 * User path: Schedule ▸ Report ▸ Preventions report ▸ the saved patient query, Breast Cancer (Mammogram), As Of
 * 2026-06-30 ▸ Run Report; the overdue patient's E-Chart ▸ Preventions ▸ MAM ▸ Completed 2026-05-15 (Result Pending)
 * ▸ Save; Run Report; the record ▸ Result Normal ▸ Save; Run Report; the record ▸ Date corrected to 2024-01-15 ▸
 * Save; Run Report.
 * The as-of date is fixed, so the classification never depends on the day the check runs. MammogramReport counts a
 * patient's latest mammogram on or before the as-of date: more than 30 months before it is Overdue, 24 to 30 months
 * due, within 24 months Up to date, or Pending while that mammogram's result is pending.
 * Asserts: both owned patients start Overdue (last mammogram 2023-06-01); after the mammogram is entered on the
 * patient's Preventions page the rerun reports that patient Pending with the new date; once its result is amended to
 * Normal, Up to date; once its date is corrected to 2024-01-15, due with that date, the archived date no longer
 * counting. The untreated control patient is Overdue with its 2023-06-01 mammogram on every run, and each rerun keeps
 * the query, type and as-of date the report was run with.
 * Fixtures: the runWorkflow FAKE- patient and a second FAKE- control patient (both women born 1960), each with a
 * mammogram dated 2023-06-01 seeded by SQL as their history, and one saved patient query naming the two. Cleanup
 * removes the query, both patients' preventions and the control patient, and asserts none remain.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { openPreventionIndex, ownPatientPreventions, preventionForms } = require('./lib/prevention-coverage');
const { runWorkflow } = require('./lib/workflow-session');

const AS_OF = '2026-06-30';
const REPORT = 'Mammogram';
const HISTORY = '2023-06-01';
const ENTERED = '2026-05-15';
const CORRECTED = '2024-01-15';

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const q = h.sqlString;
  const queryName = `${marker} recall`;
  const controlName = `${marker}-C`;
  ownPatientPreventions(s);
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicQueryFavourites WHERE queryName=${q(queryName)}`);
    const controls = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlName)}`).map(row => row[0]);
    controls.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'The control patient id is invalid'));
    if (controls.length) {
      sql.execute(`DELETE FROM preventionsExt WHERE prevention_id IN (SELECT id FROM preventions WHERE demographic_no IN (${controls.join(',')}));
        DELETE FROM preventions WHERE demographic_no IN (${controls.join(',')});
        DELETE FROM demographicArchive WHERE demographic_no IN (${controls.join(',')});
        DELETE FROM demographic WHERE demographic_no IN (${controls.join(',')}) AND last_name=${q(controlName)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE last_name=${q(controlName)})
      + (SELECT COUNT(*) FROM demographicQueryFavourites WHERE queryName=${q(queryName)})`) === '0',
    'The control patient or the saved query was not removed');
  });

  sql.execute(`UPDATE demographic SET sex='F', year_of_birth='1960', month_of_birth='03', date_of_birth='04'
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const control = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,hin,lastUpdateDate)
    VALUES (${q(controlName)},'Control','1960','03','04','F','AC',${q(provider)},'ON','ON','NR','',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(control), 'The control patient was not created');
  for (const id of [patient, control]) {
    sql.execute(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,deleted,refused,never,creator,lastUpdateDate)
      VALUES (${id},NOW(),${q(`${HISTORY} 00:00:00`)},${q(provider)},'MAM','0','0','0',${q(provider)},NOW())`);
  }
  sql.execute(`INSERT INTO demographicQueryFavourites (queryName, archived, demoIds, selects)
    VALUES (${q(queryName)}, '1', ${q(`${patient},${control}`)}, '<root><item value="demographic_no"/></root>')`);
  const favourite = sql.value(`SELECT favId FROM demographicQueryFavourites WHERE queryName=${q(queryName)}`);
  h.assert(/^[1-9]\d*$/.test(favourite), 'The saved patient query was not created');

  const { page: reportIndex } = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first(),
    { context: s.context, recorder: s.recorder, label: 'report-index', timeout: 30000 });
  const { page: report } = await ui.clickOpensPopupOrNavigates(reportIndex, reportIndex.locator("a[href*='/prevention/PreventionReport']").first(),
    { context: s.context, recorder: s.recorder, label: 'prevention-report', timeout: 30000 });
  await report.locator('select#patientSet').waitFor({ state: 'visible', timeout: 30000 });

  /** Runs the report with the form's Run Report button and returns each patient's state and last procedure date. */
  async function run() {
    await Promise.all([
      report.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 45000 }),
      report.locator('input[type="submit"]').first().click(),
    ]);
    await h.assertNotErrorPage(report, 'prevention report');
    h.assert(await report.locator('select#patientSet').inputValue() === favourite
      && await report.locator('select#prevention').inputValue() === REPORT
      && await report.locator('#asofDate').inputValue() === AS_OF, 'The rerun lost the query, the type or the as-of date');
    const headers = (await report.locator('#preventionTable thead th').allInnerTexts()).map(text => text.trim());
    const lastColumn = headers.indexOf('Last Procedure');
    h.assert(lastColumn >= 0, 'The report has no Last Procedure column');
    const rows = {};
    for (const [name, id] of [['patient', patient], ['control', control]]) {
      const row = report.locator('#preventionTable tbody tr').filter({ has: report.locator(`a[onclick*="demographic_no=${id}'"]`) });
      h.assert(await row.count() === 1, `The report lists the ${name} ${await row.count()} time(s)`);
      // MammogramReport prints the procedure's date and time ("2023-06-01 00:00"); the date is what is compared.
      const last = (await row.locator('td').nth(lastColumn).innerText()).trim();
      h.assert(/^(\d{4}-\d{2}-\d{2})( \d{2}:\d{2})?$/.test(last), `The ${name}'s last procedure reads "${last}"`);
      rows[name] = { state: (await row.locator('span.badge').first().innerText()).trim(), last: last.slice(0, 10) };
    }
    return rows;
  }
  const describe = rows => Object.entries(rows).map(([name, row]) => `${name} ${row.state} (${row.last})`).join(', ');
  const assertControlUnchanged = rows => h.assert(rows.control.state === 'Overdue' && rows.control.last === HISTORY,
    `The untreated control changed: ${describe(rows)}`);

  let index;
  let forms;
  let id;

  await s.step(`the ${REPORT} recall as of ${AS_OF} reports both patients overdue`, async () => {
    await report.locator('select#patientSet').selectOption(favourite);
    await report.locator('select#prevention').selectOption(REPORT);
    await report.locator('#asofDate').fill(AS_OF);
    const rows = await run();
    h.assert(rows.patient.state === 'Overdue' && rows.patient.last === HISTORY, `Before the intervention: ${describe(rows)}`);
    assertControlUnchanged(rows);
  });

  await s.step('a mammogram entered on the patient\'s Preventions page, result pending, makes the rerun report the patient Pending', async () => {
    index = await openPreventionIndex(s);
    forms = preventionForms(s, index, 'MAM');
    const editor = await forms.openNew();
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill(ENTERED);
    h.assert(await editor.locator('[name="result"][value="pending"]').isChecked(), 'A new mammogram did not open with Result Pending');
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close', { timeout: 30000 });
    const live = sql.rows(`SELECT id FROM preventions WHERE demographic_no=${patient} AND prevention_type='MAM' AND deleted=0
      AND DATE(prevention_date)=${q(ENTERED)}`);
    h.assert(live.length === 1, `Expected one entered mammogram, found ${live.length}`);
    id = live[0][0];
    h.assert(forms.ext(id, 'result') === 'pending', `The entered mammogram's result is ${forms.ext(id, 'result')}`);
    const rows = await run();
    h.assert(rows.patient.state === 'Pending' && rows.patient.last === ENTERED, `After the intervention: ${describe(rows)}`);
    assertControlUnchanged(rows);
  });

  /** Amends the entered mammogram on the open Preventions page; it is the patient's only live one besides history. */
  async function amend(change) {
    const editor = await forms.open(id);
    await change(editor);
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close', { timeout: 30000 });
    h.assert(sql.value(`SELECT deleted FROM preventions WHERE id=${id}`) === '1', 'The amended mammogram was not archived');
    const replacement = sql.value(`SELECT prevention_id FROM preventionsExt WHERE keyval='previousId' AND val=${q(id)}`);
    h.assert(/^[1-9]\d*$/.test(replacement), 'The amendment stored no replacement');
    id = replacement;
    await forms.record(id).waitFor({ state: 'visible' });
  }

  await s.step('amending the result to Normal makes the rerun report the patient up to date', async () => {
    await amend(editor => editor.locator('[name="result"][value="normal"]').check());
    h.assert(forms.ext(id, 'result') === 'normal', `The amended result is ${forms.ext(id, 'result')}`);
    const rows = await run();
    h.assert(rows.patient.state === 'Up to date' && rows.patient.last === ENTERED, `After the result was amended: ${describe(rows)}`);
    assertControlUnchanged(rows);
  });

  await s.step(`correcting the mammogram's date to ${CORRECTED} makes the rerun report the patient due, the archived date no longer counting`, async () => {
    await amend(editor => editor.locator('#prevDate').fill(CORRECTED));
    h.assert(sql.value(`SELECT DATE(prevention_date) FROM preventions WHERE id=${id}`) === CORRECTED, 'The corrected date was not stored');
    const rows = await run();
    h.assert(rows.patient.state === 'due' && rows.patient.last === CORRECTED, `After the date was corrected: ${describe(rows)}`);
    assertControlUnchanged(rows);
    await index.close();
  });
}

if (require.main === module) runWorkflow('prevention-recall-completed-intervention', workflow);
module.exports = { workflow };
