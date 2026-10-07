#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Validate DOB JSON through the installed REST service using an owned patient.
 * Run against a JVM in a non-UTC timezone to exercise issue #3861. The validation
 * runner verifies the server JVM timezone; the browser's timezone is irrelevant.
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(session) {
  const { sql, patient, context, config } = session;
  for (const date of ['1980-06-15', '1980-01-15', '2000-02-29', '1970-01-01', '2018-11-04']) {
    await session.step(`REST birth date is an ISO date string for ${date}`, async () => {
      const [year, month, day] = date.split('-');
      sql.execute(`UPDATE demographic SET year_of_birth=${h.sqlString(year)},
        month_of_birth=${h.sqlString(month)}, date_of_birth=${h.sqlString(day)}
        WHERE demographic_no=${patient}`);
      const response = await context.request.get(h.appUrl(config.baseUrl, `/ws/rs/demographics/${patient}`), {
        headers: { Accept: 'application/json' }, timeout: 30000,
      });
      h.assert(response.status() === 200, `REST demographic answered HTTP ${response.status()}`);
      let record;
      try { record = await response.json(); } catch { throw new Error('REST demographic did not return JSON'); }
      h.assert(String(record.demographicNo) === patient, 'REST returned a different patient');
      h.assert(typeof record.dateOfBirth === 'string' && record.dateOfBirth === date,
        'REST dateOfBirth must preserve the stored birth date as yyyy-MM-dd');
      h.assert(record.dobYear === year && record.dobMonth === month && record.dobDay === day,
        'REST birth date components changed');
    });
  }
}

if (require.main === module) runWorkflow('rest-birth-date', workflow, { openMaster: false });
module.exports = { workflow };
