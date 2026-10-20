#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Age calculation on the day of a birthday in the Age-Sex Report (wave 6, boundary values, Part 2).
 * User path: Schedule > Administration > Reports > Age-Sex Report (regenerates the reportagesex cache, then
 * Create Report in the administration frame) for the owned provider and an owned join date.
 * Asserts: eighteen owned patients whose birthday is TODAY (born exactly 5, 10 ... 90 years ago) each count in
 * the five-year bucket that starts at their age (a person who turns 25 today is 25, not 24), and a patient whose
 * birthday is tomorrow counts one bucket younger. Every number is the delta the owned fixtures add, read before
 * and after seeding them, in a join date only the fixtures carry.
 * Fixtures: nineteen synthetic patients (surname = run marker + index) inserted by SQL; cleanup removes them and
 * their reportagesex cache rows and asserts none remain.
 * Implements the wave-6 "boundary values" pattern, Part 2 (age on / before / after a birthday).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const JOINED = '1948-06-15';
const BUCKETS = [[0, 4], [5, 9], [10, 14], [15, 19], [20, 24], [25, 29], [30, 34], [35, 39], [40, 44],
  [45, 49], [50, 54], [55, 59], [60, 64], [65, 69], [70, 74], [75, 79], [80, 84], [85, 89], [90, 94], [95, 200]];

async function tableCells(scope, selector) {
  return scope.locator(selector).evaluateAll(rows => rows.map(row =>
    [...row.querySelectorAll('td')].map(cell => cell.textContent.replace(/\s+/g, ' ').trim())));
}

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const ids = [];
  s.cleanup(() => {
    const list = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${q(`${marker}-A%`)}`).map(row => row[0]);
    list.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned patient id is invalid'));
    if (list.length) {
      // Patients first: the report regeneration rebuilds the cache from `demographic`, so once they are gone nothing
      // (this run or a parallel one) can write a cache row for them again.
      sql.execute(`START TRANSACTION;
        DELETE FROM demographic WHERE demographic_no IN (${list.join(',')}) AND last_name LIKE ${q(`${marker}-A%`)};
        DELETE FROM reportagesex WHERE demographic_no IN (${list.join(',')});
        COMMIT`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${q(`${marker}-A%`)}`) === '0', 'Owned patients were not removed');
    if (list.length) {
      h.assert(sql.value(`SELECT COUNT(*) FROM reportagesex WHERE demographic_no IN (${list.join(',')})`) === '0',
        'Owned Age-Sex Report cache rows were not removed');
    }
  });

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'report-admin', timeout: 20000 });
  await admin.locator('#adminNav').waitFor();

  async function regenerate() {
    const link = admin.locator('#adminNav a.xlink[data-submit-form="ageSexForm"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.waitFor({ state: 'visible' });
    const posted = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/oscarReport/DbReportAgeSex') && r.request().method() === 'POST', { timeout: 30000 });
    await link.click();
    h.assert((await posted).status() === 200, 'Age-Sex Report regeneration did not answer 200');
    const frame = admin.frame({ name: 'myFrame' });
    h.assert(frame, 'Age-Sex Report did not open in the administration frame');
    await frame.locator('select[name="providerview"]').waitFor();
    return frame;
  }
  async function counts(frame) {
    await frame.locator('input[name="reportAction"][value="TO"]').check();
    await frame.locator('select[name="providerview"]').selectOption(provider);
    await frame.locator('input[name="xml_vdate"]').fill(JOINED);
    await frame.locator('input[name="xml_appointment_date"]').fill(JOINED);
    await Promise.all([
      frame.waitForURL(url => url.pathname.endsWith('/oscarReport/ViewOscarReportAgeSex') && url.searchParams.get('reportAction') === 'TO'),
      frame.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    await frame.locator('select[name="providerview"]').waitFor();
    await h.assertNotErrorPage(frame, 'Age-Sex Report');
    const buckets = {};
    for (const cells of await tableCells(frame, 'table tr')) {
      if (/^\d+-\d+$/.test(cells[0] || '')) buckets[cells[0]] = Number(cells[1]);
    }
    h.assert(Object.keys(buckets).length === 20, 'Age-Sex Report did not render its 20 age rows');
    return buckets;
  }

  let baseline;
  await s.step('Age-Sex Report reports a baseline for the owned join date', async () => {
    baseline = await counts(await regenerate());
  });

  const ages = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90];
  await s.step('birthday-today patients and a birthday-tomorrow patient are seeded', async () => {
    const insert = (index, dob) => sql.execute(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
        patient_status,provider_no,hc_type,province,roster_status,date_joined,lastUpdateDate)
      SELECT ${q(`${marker}-A${index}`)},'Age',YEAR(d),LPAD(MONTH(d),2,'0'),LPAD(DAY(d),2,'0'),'F','AC',${q(provider)},'ON','ON','RO',${q(JOINED)},NOW()
      FROM (SELECT ${dob} AS d) x`);
    ages.forEach((age, index) => insert(index, `DATE_SUB(CURDATE(), INTERVAL ${age} YEAR)`));
    insert(99, 'DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 25 YEAR), INTERVAL 1 DAY)');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${q(`${marker}-A%`)}`) === String(ages.length + 1), 'The age fixtures were not created');
    ids.push(...sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${q(`${marker}-A%`)}`).map(row => row[0]));
  });

  await s.step('every birthday-today patient counts in the bucket that starts at their age; birthday tomorrow counts one bucket younger', async () => {
    const after = await counts(await regenerate());
    const expected = {};
    for (const [from, to] of BUCKETS) expected[`${from}-${to}`] = 0;
    for (const age of ages) expected[BUCKETS.find(([from, to]) => age >= from && age <= to).join('-')] += 1;
    expected['20-24'] += 1; // the patient who turns 25 tomorrow is still 24
    const wrong = [];
    for (const key of Object.keys(expected)) {
      const delta = after[key] - baseline[key];
      if (delta !== expected[key]) wrong.push(`${key}: +${delta} (expected +${expected[key]})`);
    }
    const stored = sql.rows(`SELECT age FROM reportagesex WHERE demographic_no IN (${ids.join(',')}) ORDER BY demographic_no`).map(row => Number(row[0]));
    h.assert(wrong.length === 0, `Age-Sex Report buckets are off on the day of a birthday: ${wrong.join('; ')}. The cached ages of the birthday-today patients `
      + `(born 5, 10 ... 90 years ago) are [${stored.slice(0, ages.length).join(',')}]; `
      + 'ReportAgeSexDaoImpl.populateAll computes FLOOR(DATEDIFF(CURRENT_DATE, dob) / 365.25), which is one year short on the birthday itself');
  });
}

if (require.main === module) runWorkflow('boundary-age-report', workflow, { openPatient: false });
module.exports = { workflow };
