#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * As-of date inclusivity of the Prevention Report (wave 6, boundary values, Part 2).
 * User path: Schedule > Report > Preventions report (prevention/PreventionReport) > patient set, prevention type Flu,
 * As Of date > Run Report.
 * Asserts: with an as-of date inside the flu season, a flu shot given the day before the as-of date and a flu shot
 * given ON the as-of date are both counted ("Up to date"), and a shot dated the day after is still in the future
 * ("No Info"). A report run for today must count the shot given today.
 * Fixtures: three synthetic patients aged over 65 (surname = run marker + index) with one Flu prevention each, and one
 * saved patient query naming them; cleanup removes the preventions, the query and the patients and asserts none remain.
 * Implements the wave-6 "boundary values" pattern, Part 2 (prevention reports, end-date inclusivity).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const AS_OF = '2026-10-15';
const DAYS = { before: '2026-10-14', on: AS_OF, after: '2026-10-16' };

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const ids = {};
  const queryName = `${marker} boundary`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicQueryFavourites WHERE queryName=${q(queryName)}`);
    const list = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${q(`${marker}-P%`)}`).map(row => row[0]);
    list.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned patient id is invalid'));
    if (list.length) {
      sql.execute(`DELETE FROM preventionsExt WHERE prevention_id IN (SELECT id FROM preventions WHERE demographic_no IN (${list.join(',')}));
        DELETE FROM preventions WHERE demographic_no IN (${list.join(',')});
        DELETE FROM demographic WHERE demographic_no IN (${list.join(',')}) AND last_name LIKE ${q(`${marker}-P%`)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${q(`${marker}-P%`)})
      + (SELECT COUNT(*) FROM demographicQueryFavourites WHERE queryName=${q(queryName)})`) === '0', 'Owned prevention fixtures were not removed');
  });
  for (const [name, date] of Object.entries(DAYS)) {
    const id = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,hin,lastUpdateDate)
      VALUES (${q(`${marker}-P${name}`)},'Prev','1950','06','15','F','AC',${q(provider)},'ON','ON','NR','',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `Patient fixture ${name} was not created`);
    ids[name] = id;
    sql.execute(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,provider_name,prevention_type,deleted,refused,never,lastUpdateDate)
      VALUES (${id},NOW(),${q(`${date} 09:00:00`)},${q(provider)},'Fixture','Flu','0','0','0',NOW())`);
  }
  sql.execute(`INSERT INTO demographicQueryFavourites (queryName, archived, demoIds, selects)
    VALUES (${q(queryName)}, '1', ${q(Object.values(ids).join(','))}, '<root><item value="demographic_no"/></root>')`);
  const favourite = sql.value(`SELECT favId FROM demographicQueryFavourites WHERE queryName=${q(queryName)}`);
  h.assert(/^[1-9]\d*$/.test(favourite), 'The saved-query fixture was not created');

  const { page: index } = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first(),
    { context: s.context, recorder: s.recorder, label: 'report-index', timeout: 30000 });
  const { page: report } = await ui.clickOpensPopupOrNavigates(index, index.locator("a[href*='/prevention/PreventionReport']").first(),
    { context: s.context, recorder: s.recorder, label: 'prevention-report', timeout: 30000 });
  await report.locator('select#patientSet').waitFor({ state: 'visible', timeout: 30000 });

  await s.step(`the Flu report as of ${AS_OF} counts a shot given the day before and a shot given on the as-of date, and not one dated the day after`, async () => {
    await report.locator('select#patientSet').selectOption(favourite);
    await report.locator('select#prevention').selectOption('Flu');
    await report.locator('#asofDate').fill(AS_OF);
    await Promise.all([
      report.waitForResponse(r => /\/prevention\/PreventionReport/.test(r.url()) && r.request().method() === 'GET', { timeout: 45000 }),
      report.locator('input[type="submit"]').first().click(),
    ]);
    await report.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    await h.assertNotErrorPage(report, 'prevention report');
    const state = {};
    for (const [name, id] of Object.entries(ids)) {
      const row = report.locator('#preventionTable tbody tr').filter({ has: report.locator(`a[onclick*="demographic_no=${id}"]`) }).first();
      h.assert(await row.count() > 0, `The report did not list the ${name} fixture patient`);
      state[name] = (await row.locator('span.badge').first().innerText()).trim();
    }
    h.assert(state.before === 'Up to date', `A flu shot given the day before the as-of date is "${state.before}", expected "Up to date"`);
    h.assert(state.after === 'No Info', `A flu shot dated after the as-of date is "${state.after}", expected "No Info"`);
    h.assert(state.on === 'Up to date', `A flu shot given ON the as-of date is "${state.on}", expected "Up to date" `
      + '(FluReport.removeFutureItems keeps only prevDate.before(asOfDate); the typed as-of date is midnight, so the shot of that day is dropped as a future item)');
  });
}

if (require.main === module) runWorkflow('boundary-date-prevention-report', workflow, { openPatient: false });
module.exports = { workflow };
