#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Demographic Report Tool criteria, each checked against what the criterion means (gap-records, §3.6).
 * patient-set-cohort and demographic-report-favourites use only last name, sex and an id list; this one
 * drives the cohort selectors a clinic uses to build a recall population.
 * User path: Schedule ▸ Report ▸ Demographic Report Tool (report/DemographicReport) ▸ pick the columns, the
 * Demographic IDs of the six owned patients and ONE criterion at a time ▸ Run Query ▸ the result table.
 * Criteria asserted: Sex; Age younger than / older than / equal to / between, in "Exact" style and in the
 * "In the year" style (calendar-year difference, birthday ignored), and with an "As of" date; Roster Status
 * (one and several boxes); Patient Status (one and several); Provider; a combination of four; and Order By
 * First Name.
 * The expected set is derived from the fixtures (six owned patients with known sex, birth date, roster status,
 * patient status and provider), not from the application. Between probes use bounds that every reading of
 * "between" (inclusive or exclusive) answers alike.
 * Fixtures: six marker patients (first names P1..P6) plus the runWorkflow patient, which the id list excludes;
 * the five extra patients are removed in cleanup and checked gone. Nothing is saved (Run Query only).
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { removeMarkedPatients } = require('./lib/gap-records-fixtures');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker, context, recorder, provider } = s;
  const q = h.sqlString;
  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]{0,1}$'
    AND provider_no<>${q(provider)} AND COALESCE(last_name,'')<>'' ORDER BY CAST(provider_no AS UNSIGNED) LIMIT 1`);
  if (!other) throw new h.SkipCheck('The database has no second active provider for the provider criterion');
  const year = new Date().getFullYear();
  const today = new Date();
  if (today.getMonth() === 11 && today.getDate() >= 29) throw new h.SkipCheck('Run after 31 December: the in-year probe patient is born on 30 December');
  // [first name, sex, YYYY-MM-DD birth, roster, patient status, provider]
  const people = [
    ['P1', 'F', `${year - 5}-01-01`, 'RO', 'AC', provider],
    ['P2', 'M', `${year - 30}-01-01`, 'NR', 'AC', provider],
    ['P3', 'F', `${year - 30}-12-30`, 'RO', 'IN', other],
    ['P4', 'M', `${year - 64}-01-01`, 'TE', 'AC', provider],
    ['P5', 'F', `${year - 65}-01-01`, 'RO', 'AC', other],
    ['P6', 'M', `${year - 90}-01-01`, 'FS', 'DE', other],
  ];
  s.cleanup(() => removeMarkedPatients(sql, `${marker}-C`));
  const ids = people.map(([first, sex, dob, roster, status, prov]) => {
    const [y, m, d] = dob.split('-');
    return sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth, date_of_birth, sex, patient_status,
      provider_no, hc_type, province, roster_status, lastUpdateDate) VALUES (${q(`${marker}-C`)}, ${q(first)}, ${q(y)}, ${q(m)}, ${q(d)},
      ${q(sex)}, ${q(status)}, ${q(prov)}, 'ON', 'ON', ${q(roster)}, NOW()); SELECT LAST_INSERT_ID()`);
  });
  ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'A criteria fixture patient was not created'));
  const firstNames = people.map(person => person[0]);
  const byName = new Map(firstNames.map((name, index) => [name, ids[index]]));

  const { page: reportIndex } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a[title="Generate a report"]').first(),
    { context, recorder, label: 'criteria-report-index', timeout: TIMEOUT });
  const { page: tool } = await clickOpensPopupOrNavigates(reportIndex, reportIndex.locator('a[href*="ViewReportDemographicReport"]').first(),
    { context, recorder, label: 'criteria-tool', timeout: TIMEOUT });
  await tool.locator('#select_demographic_no').waitFor({ timeout: TIMEOUT });

  /** Reset every criterion, apply `criteria`, run, and return the first names listed (in order). */
  async function run(criteria = {}) {
    for (const id of ['#select_demographic_no', '#select_last_name', '#select_first_name']) await tool.locator(id).check();
    await tool.locator('#lastName').fill('');
    await tool.locator('#firstName').fill('');
    await tool.locator('textarea[name="demoIds"]').fill(ids.join('\n'));
    await tool.locator('#age').selectOption(criteria.age || '0');
    await tool.locator('input[name="startYear"]').fill(criteria.start || '');
    await tool.locator('input[name="endYear"]').fill(criteria.end || '');
    await tool.locator(`input[name="ageStyle"][value="${criteria.style || '1'}"]`).check();
    await tool.locator('#asofDate').fill(criteria.asof || '');
    await tool.locator('#asofDate').press('Escape');
    await tool.locator('#asofDate').press('Tab');
    await tool.locator('#sex').selectOption(criteria.sex || '0');
    await tool.locator('#orderBy').selectOption(criteria.order || '0');
    for (const group of ['rosterStatus', 'patientStatus', 'providerNo']) {
      const boxes = tool.locator(`input[name="${group}"]`);
      for (let index = 0; index < await boxes.count(); index++) await boxes.nth(index).uncheck();
      for (const value of criteria[group] || []) await tool.locator(`input[name="${group}"][value="${value}"]`).check();
    }
    await clickAndAwaitReload(tool, tool.getByRole('button', { name: 'Run Query', exact: true }), { timeout: TIMEOUT, label: 'Run Query' });
    await h.assertNotErrorPage(tool, 'Run Query');
    await tool.locator('.section-header').filter({ hasText: 'Search Returned:' }).waitFor({ timeout: TIMEOUT });
    const rows = await tool.locator('table tr').filter({ hasText: `${marker}-C` }).evaluateAll(trs =>
      trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.trim())));
    const idColumn = rows.length ? rows[0].findIndex(cell => ids.includes(cell)) : 0;
    return rows.map(cells => firstNames[ids.indexOf(cells[idColumn])]);
  }
  const expectSet = async (label, criteria, expected) => {
    const got = (await run(criteria)).slice().sort();
    h.assert(JSON.stringify(got) === JSON.stringify(expected.slice().sort()),
      `${label}: the report lists [${got.join(', ')}] but the owned patients that match are [${expected.slice().sort().join(', ')}]`);
  };

  await s.step('the id list alone returns exactly the six owned patients', async () => {
    await expectSet('no criterion', {}, firstNames);
  });
  await s.step('Sex filters', async () => {
    await expectSet('Female', { sex: '1' }, ['P1', 'P3', 'P5']);
    await expectSet('Male', { sex: '2' }, ['P2', 'P4', 'P6']);
  });
  await s.step('Roster Status and Patient Status checkboxes filter, singly and together', async () => {
    await expectSet('Roster RO', { rosterStatus: ['RO'] }, ['P1', 'P3', 'P5']);
    await expectSet('Roster TE', { rosterStatus: ['TE'] }, ['P4']);
    await expectSet('Roster RO+FS', { rosterStatus: ['RO', 'FS'] }, ['P1', 'P3', 'P5', 'P6']);
    await expectSet('Patient status IN', { patientStatus: ['IN'] }, ['P3']);
    await expectSet('Patient status IN+DE', { patientStatus: ['IN', 'DE'] }, ['P3', 'P6']);
  });
  await s.step('Provider checkboxes filter and combine with the other criteria', async () => {
    await expectSet('Provider (other)', { providerNo: [other] }, ['P3', 'P5', 'P6']);
    await expectSet('Provider (login)', { providerNo: [provider] }, ['P1', 'P2', 'P4']);
    await expectSet('Female + RO + AC + provider', { sex: '1', rosterStatus: ['RO'], patientStatus: ['AC'], providerNo: [other] }, ['P5']);
  });
  await s.step('Age (exact) younger than / older than / equal to / between', async () => {
    await expectSet('younger than 30', { age: '1', start: '30' }, ['P1', 'P3']);
    await expectSet('older than 64', { age: '2', start: '64' }, ['P5', 'P6']);
    await expectSet('equal to 30', { age: '3', start: '30' }, ['P2']);
    await expectSet('between 28 and 32', { age: '4', start: '28', end: '32' }, ['P2', 'P3']);
  });
  await s.step('Age "In the year" counts the calendar-year difference, birthday ignored', async () => {
    await expectSet('in the year, equal to 30', { age: '3', start: '30', style: '2' }, ['P2', 'P3']);
    await expectSet('in the year, younger than 30', { age: '1', start: '30', style: '2' }, ['P1']);
  });
  await s.step('Age "As of" a later date moves everyone on', async () => {
    await expectSet('as of ten years on, younger than 20', { age: '1', start: '20', asof: `${year + 10}-06-15` }, ['P1']);
    await expectSet('as of ten years on, equal to 40', { age: '3', start: '40', asof: `${year + 10}-06-15` }, ['P2']);
  });
  await s.step('Order By First Name lists the rows in that order', async () => {
    const got = await run({ order: 'First Name' });
    h.assert(JSON.stringify(got) === JSON.stringify(firstNames), `Order By First Name lists [${got.join(', ')}]`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-demographic-report-criteria', workflow, { openPatient: true, openMaster: false });
