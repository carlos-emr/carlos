#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Browser regression for issue #3960: the unbilled-appointments billing report
 * must not list No-Show (N*) or Cancelled (C*) appointments as work to bill
 * unless the user opts in with the "Include No-Show" / "Include Cancelled"
 * checkboxes (filter UI ported from open-osp/Open-O PR #134 / #186).
 *
 * The check owns everything it touches: one synthetic FAKE- patient (via
 * runWorkflow), one appointment per status on an otherwise empty date for the
 * test provider, and -- only when missing -- the reportprovider row that puts
 * the test provider in the report's provider dropdown. Every row is removed in
 * cleanup, and removal is asserted.
 *
 * Both report screens that call OscarAppointmentDao.search_unbill_history_daterange
 * are driven through their real forms:
 *   - Ontario  /billing/CA/ON/ViewBillingReportControl  (POST, CSRF-guarded)
 *   - BC       /billing/CA/BC/ViewBillingReportControl  (GET)
 * and for each the four checkbox combinations are asserted against the rendered
 * rows, including that the checkbox state is echoed back after submit.
 *
 * Statuses seeded:
 *   t   To Do            always listed
 *   c   Customized 3     always listed (lowercase: must not be mistaken for C)
 *   N   No-Show          listed only with Include No-Show
 *   C   Cancelled        listed only with Include Cancelled
 *   B   Billed           never listed
 *
 *   npm run test:billing-unbilled-report-playwright
 *
 * Environment: the common contract in scripts/lib/playwright-harness.js
 * readConfig() (BASE_URL, TEST_USER/TEST_PASSWORD/TEST_PIN, MYSQL_*). MYSQL_PASSWORD
 * is required because the check seeds and verifies database rows.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const FIXTURES = [
  { status: 't', label: 'todo' },
  { status: 'c', label: 'custom3' },
  { status: 'N', label: 'noshow' },
  { status: 'C', label: 'cancelled' },
  { status: 'B', label: 'billed' },
];

const SCENARIOS = [
  { noShow: false, cancelled: false, expected: ['todo', 'custom3'] },
  { noShow: true, cancelled: false, expected: ['todo', 'custom3', 'noshow'] },
  { noShow: false, cancelled: true, expected: ['todo', 'custom3', 'cancelled'] },
  { noShow: true, cancelled: true, expected: ['todo', 'custom3', 'noshow', 'cancelled'] },
];

const SCREENS = [
  { name: 'ON', path: '/billing/CA/ON/ViewBillingReportControl' },
  { name: 'BC', path: '/billing/CA/BC/ViewBillingReportControl' },
];

/** A date on which the test provider has no appointments, so the report shows only fixtures. */
function pickEmptyDate(sql, provider) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const year = randomInt(1991, 2000);
    const month = String(randomInt(1, 13)).padStart(2, '0');
    const day = String(randomInt(1, 29)).padStart(2, '0');
    const date = `${year}-${month}-${day}`;
    if (sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${h.sqlString(provider)}
        AND appointment_date=${h.sqlString(date)}`) === '0') {
      return date;
    }
  }
  throw new Error('could not find an empty appointment date for the test provider');
}

async function listedLabels(page, marker) {
  const cells = await page.locator('tr').filter({ hasText: marker }).locator('td:nth-child(3)').allInnerTexts();
  return cells.map(text => text.trim().slice(marker.length + 1)).sort();
}

async function runReport(page, s, screen, date, scenario) {
  await h.gotoApp(page, s.config.baseUrl, screen.path);
  await h.assertNotErrorPage(page, `${screen.name} report control`);
  const form = page.locator('form[name="serviceform"]');
  await form.locator('input[name="reportAction"][value="unbilled"]').check();
  await form.locator('select[name="providerview"]').selectOption(s.provider);
  await form.locator('input[name="xml_vdate"]').fill(date);
  await form.locator('input[name="xml_appointment_date"]').fill(date);
  await form.locator('input[name="includeNoShow"]').setChecked(scenario.noShow);
  await form.locator('input[name="includeCancelled"]').setChecked(scenario.cancelled);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    form.locator('input[type="submit"]').click(),
  ]);
  await h.assertNotErrorPage(page, `${screen.name} unbilled report`);
  const echoed = page.locator('form[name="serviceform"]');
  h.assert(await echoed.locator('input[name="includeNoShow"]').isChecked() === scenario.noShow,
    `${screen.name}: Include No-Show checkbox state was not echoed after submit`);
  h.assert(await echoed.locator('input[name="includeCancelled"]').isChecked() === scenario.cancelled,
    `${screen.name}: Include Cancelled checkbox state was not echoed after submit`);
  return listedLabels(page, s.marker);
}

async function workflow(s) {
  const { sql, provider, marker, patient } = s;
  const date = pickEmptyDate(sql, provider);
  const appointmentIds = [];
  let reportProviderId = null;

  s.cleanup(() => {
    if (reportProviderId) {
      sql.execute(`DELETE FROM reportprovider WHERE id=${reportProviderId}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM reportprovider WHERE id=${reportProviderId}`) === '0',
        'reportprovider fixture was not removed');
    }
  });
  s.cleanup(() => {
    if (!appointmentIds.length) return;
    sql.execute(`DELETE FROM appointment WHERE appointment_no IN (${appointmentIds.join(',')})
      AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no IN (${appointmentIds.join(',')})`) === '0',
      'appointment fixtures were not removed');
  });

  if (sql.value(`SELECT COUNT(*) FROM reportprovider WHERE provider_no=${h.sqlString(provider)}
      AND action='billingreport' AND status<>'D'`) === '0') {
    reportProviderId = sql.value(`INSERT INTO reportprovider (provider_no,team,action,status)
      VALUES (${h.sqlString(provider)},'','billingreport','A'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(reportProviderId), 'reportprovider fixture was not created');
  }

  FIXTURES.forEach((fixture, index) => {
    const time = `09:${String(index * 10).padStart(2, '0')}:00`;
    const id = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,
        demographic_no,program_id,reason,status,createdatetime,updatedatetime,creator,lastupdateuser)
      VALUES (${h.sqlString(provider)},${h.sqlString(date)},${h.sqlString(time)},${h.sqlString(time)},
        ${h.sqlString(`${marker} ${fixture.label}`)},${patient},0,${h.sqlString(`${marker} ${fixture.label}`)},
        ${h.sqlString(fixture.status)},NOW(),NOW(),'playwright',${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `appointment fixture ${fixture.label} was not created`);
    appointmentIds.push(id);
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no IN (${appointmentIds.join(',')})
      AND BINARY status IN ('t','c','N','C','B')`) === String(FIXTURES.length),
  'appointment fixtures did not persist their exact-case statuses');

  const page = await s.context.newPage();
  for (const screen of SCREENS) {
    for (const scenario of SCENARIOS) {
      const label = `${screen.name} unbilled report, noShow=${scenario.noShow} cancelled=${scenario.cancelled}`;
      await s.step(label, async () => {
        const listed = await runReport(page, s, screen, date, scenario);
        const expected = [...scenario.expected].sort();
        h.assert(JSON.stringify(listed) === JSON.stringify(expected),
          `${label}: listed [${listed.join(', ')}], expected [${expected.join(', ')}]`);
      });
    }
  }
  await page.close();
}

if (require.main === module) runWorkflow('billing-unbilled-report', workflow, { openPatient: true });
module.exports = { workflow, FIXTURES, SCENARIOS };
