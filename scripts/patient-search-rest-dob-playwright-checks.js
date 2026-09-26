#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Exercise the installed REST DOB search with an owned synthetic patient.
 * The workflow harness restricts the database to local targets, checks fixture
 * ownership and removes the patient on success or failure. Diagnostics contain
 * no patient identifiers, search terms or response bodies.
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(session) {
  const { sql, patient, context, config } = session;
  const usedYears = new Set(sql.rows('SELECT DISTINCT year_of_birth FROM demographic')
    .map(row => row[0]));
  const year = Array.from({ length: 100 }, (_, index) => String(1700 + index))
    .find(candidate => !usedYears.has(candidate));
  h.assert(year, 'No unused synthetic birth year is available for the REST fixture');
  sql.execute(`UPDATE demographic SET year_of_birth=${h.sqlString(year)}, month_of_birth='03',
    date_of_birth='05' WHERE demographic_no=${patient}`);

  async function search(term, active = true, startIndex = 0) {
    const url = h.appUrl(config.baseUrl,
      `/ws/rs/demographics/search?startIndex=${startIndex}&itemsToReturn=10`);
    const response = await context.request.post(url, {
      data: { type: 'DOB', term, active },
      headers: { Accept: 'application/json', Origin: config.baseUrl.origin },
      timeout: 30000,
    });
    h.assert(response.status() === 200, `REST DOB search answered HTTP ${response.status()}`);
    let data;
    try { data = await response.json(); } catch { throw new Error('REST DOB search did not answer JSON'); }
    h.assert(Array.isArray(data.content), 'REST DOB search did not return a result list');
    return data;
  }

  await session.step('partial, wildcard and padded DOB searches return the owned patient and matching count', async () => {
    for (const term of [year, `${year}-03`, `${year}-03-05`, `${year}-3-5`, `${year}-%-05`, `${year}-`]) {
      const data = await search(term);
      h.assert(data.total === 1 && data.content.length === 1, 'REST DOB count/results mismatch');
      h.assert(String(data.content[0].demographicNo) === patient, 'REST DOB search returned an unexpected patient');
    }
  });
  await session.step('malformed DOB searches return zero matches', async () => {
    for (const term of ['%', `${year}-13`, `${year}-03-32`, year.slice(0, 3), `${year}_03_05`]) {
      const data = await search(term);
      h.assert(data.total === 0 && data.content.length === 0, 'Invalid REST DOB input returned matches');
    }
  });
  await session.step('pagination preserves total count and active status remains enforced', async () => {
    const paged = await search(year, true, 1);
    h.assert(paged.total === 1 && paged.content.length === 0, 'REST DOB pagination lost the count or ignored the offset');
    sql.execute(`UPDATE demographic SET patient_status='IN' WHERE demographic_no=${patient}`);
    const active = await search(year);
    h.assert(active.total === 0 && active.content.length === 0, 'REST DOB active search included an inactive patient');
    const inactive = await search(year, false);
    h.assert(inactive.total === 1 && inactive.content.length === 1
      && String(inactive.content[0].demographicNo) === patient, 'REST DOB inactive search did not return its fixture');
  });
}

if (require.main === module) runWorkflow('patient-search-rest-dob', workflow);
module.exports = { workflow };
