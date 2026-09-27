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

const NAME_SUFFIX = " O'&<x>";
const REASON_SUFFIX = " <img src=x onerror=window.x=true> \"'&";
const FIXTURES = [
  { status: 't', label: 'todo' },
  { status: 'c', label: 'custom3' },
  { status: 'N', label: 'noshow' },
  { status: 'C', label: 'cancelled' },
  { status: 'B', label: 'billed' },
  { status: 'NV', label: 'noshow-verified' },
  { status: 'CS', label: 'cancelled-signed' },
];

const SCENARIOS = [
  { noShow: false, cancelled: false, expected: ['todo', 'custom3'] },
  { noShow: true, cancelled: false, expected: ['todo', 'custom3', 'noshow', 'noshow-verified'] },
  { noShow: false, cancelled: true, expected: ['todo', 'custom3', 'cancelled', 'cancelled-signed'] },
  { noShow: true, cancelled: true, expected: ['todo', 'custom3', 'noshow', 'noshow-verified', 'cancelled', 'cancelled-signed'] },
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
  return cells.map(text => text.trim().slice(marker.length + 1, -NAME_SUFFIX.length)).sort();
}

async function runReport(page, s, screen, date, scenario) {
  await h.gotoApp(page, s.config.baseUrl, screen.path);
  await h.assertNotErrorPage(page, `${screen.name} report control`);
  // The legacy markup nests <form name="serviceform"> directly inside <table>,
  // so the HTML parser leaves the controls outside the form element (they stay
  // form-associated, not descendants). Locate them page-wide: each page has one
  // report form.
  await page.locator('input[name="reportAction"][value="unbilled"]').check();
  await page.locator('select[name="providerview"]').selectOption(s.provider);
  await page.locator('input[name="xml_vdate"]').fill(date);
  await page.locator('input[name="xml_appointment_date"]').fill(date);
  await page.locator('input[name="includeNoShow"]').setChecked(scenario.noShow);
  await page.locator('input[name="includeCancelled"]').setChecked(scenario.cancelled);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    page.locator('input[type="submit"][value="Create Report"]').click(),
  ]);
  await h.assertNotErrorPage(page, `${screen.name} unbilled report`);
  h.assert(await page.locator('input[name="includeNoShow"]').isChecked() === scenario.noShow,
    `${screen.name}: Include No-Show checkbox state was not echoed after submit`);
  h.assert(await page.locator('input[name="includeCancelled"]').isChecked() === scenario.cancelled,
    `${screen.name}: Include Cancelled checkbox state was not echoed after submit`);
  for (const label of scenario.expected) {
    const index = FIXTURES.findIndex(f => f.label === label);
    const expectedTime = `${String(9 + Math.floor(index / 6)).padStart(2, '0')}:${String((index % 6) * 10).padStart(2, '0')}:00`;
    const row = page.locator('tr').filter({has:page.locator('td:nth-child(3)', {hasText: new RegExp(`^${s.marker} ${label}${NAME_SUFFIX}$`)})});
    h.assert((await row.locator('td:nth-child(2)').innerText()).trim() === expectedTime, `${screen.name}: appointment time changed`);
    h.assert((await row.locator('td:nth-child(4)').innerText()).trim() === `${s.marker} ${label}${REASON_SUFFIX}`, `${screen.name}: reason text changed`);
    h.assert(await row.locator('img').count() === 0, `${screen.name}: reason rendered as HTML`);
    h.assert(!await page.evaluate(() => window.x), `${screen.name}: reason script executed`);
    const link = row.locator('a').filter({hasText:'Bill'});
    // Exercise the actual click and popup URL. A neutral response isolates the report's
    // link contract from the separate province-specific billing editor workflow.
    const routePattern = '**/billing?**';
    await s.context.route(routePattern, route => route.fulfill({status:200, contentType:'text/html', body:'<!doctype html><title>Billing link check</title>'}));
    const reportUrl = page.url();
    let popup;
    let billing;
    try {
      [popup] = await Promise.all([page.waitForEvent('popup'), link.click()]);
      await popup.waitForURL(url => url.pathname.endsWith('/billing'), {waitUntil:'domcontentloaded'});
      billing = new URL(popup.url());
    } finally {
      if (popup) await popup.close();
      await s.context.unroute(routePattern);
    }
    h.assert(page.url().split('#')[0] === reportUrl.split('#')[0], `${screen.name}: opening a bill navigated away from the report`);
    h.assert(billing.searchParams.get('start_time') === expectedTime, `${screen.name}: billing link time changed`);
    h.assert(billing.searchParams.get('billRegion') === screen.name, `${screen.name}: billing link province changed`);
    h.assert(billing.searchParams.get('demographic_name') === `${s.marker} ${label}${NAME_SUFFIX}`, `${screen.name}: billing link patient name changed`);
  }
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
    const time = `${String(9 + Math.floor(index / 6)).padStart(2, '0')}:${String((index % 6) * 10).padStart(2, '0')}:00`;
    const id = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,
        demographic_no,program_id,reason,status,createdatetime,updatedatetime,creator,lastupdateuser)
      VALUES (${h.sqlString(provider)},${h.sqlString(date)},${h.sqlString(time)},${h.sqlString(time)},
        ${h.sqlString(`${marker} ${fixture.label}${NAME_SUFFIX}`)},${patient},0,${h.sqlString(`${marker} ${fixture.label}${REASON_SUFFIX}`)},
        ${h.sqlString(fixture.status)},NOW(),NOW(),'playwright',${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `appointment fixture ${fixture.label} was not created`);
    appointmentIds.push(id);
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no IN (${appointmentIds.join(',')})
      AND BINARY status IN ('t','c','N','C','B','NV','CS')`) === String(FIXTURES.length),
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
  await s.step('BC omitted dates retain report defaults', async () => {
    const response = await h.gotoApp(page, s.config.baseUrl,
      `/billing/CA/BC/ViewBillingReportControl?reportAction=unbilled&providerview=${encodeURIComponent(provider)}`);
    h.assert(response.status() === 200, 'BC report rejected omitted dates');
    await h.assertNotErrorPage(page, 'BC report omitted dates');
  });
  await s.step('BC provider management keeps the report open', async () => {
    const reportUrl = page.url();
    const pattern = '**/oscarReport/ViewManageProvider?**';
    await s.context.route(pattern, route => route.fulfill({status:200, contentType:'text/html', body:'<!doctype html><title>Provider link check</title>'}));
    let popup;
    try {
      [popup] = await Promise.all([page.waitForEvent('popup'), page.getByRole('link', {name:'Manage Provider'}).click()]);
      await popup.waitForURL(url => url.pathname.endsWith('/oscarReport/ViewManageProvider'), {waitUntil:'domcontentloaded'});
      h.assert(page.url().split('#')[0] === reportUrl.split('#')[0], 'BC provider management navigated away from the report');
    } finally {
      if (popup) await popup.close();
      await s.context.unroute(pattern);
    }
  });
  for (const [label, field] of [['Begin:', 'xml_vdate'], ['End:', 'xml_appointment_date']]) {
    await s.step(`BC ${label} calendar returns its date to the report`, async () => {
      const reportUrl = page.url();
      const [calendar] = await Promise.all([page.waitForEvent('popup'), page.getByRole('link', {name:label, exact:true}).click()]);
      try {
        await calendar.waitForURL(url => url.pathname.endsWith('/ViewBillingCalendarPopup'), {waitUntil:'domcontentloaded'});
        await h.assertNotErrorPage(calendar, 'BC date calendar');
        h.assert(page.url().split('#')[0] === reportUrl.split('#')[0], 'Calendar navigated away from its report');
        const day = calendar.locator('a[onclick^="typeMultiDate"]').first();
        const action = await day.getAttribute('onclick');
        const values = /^typeMultiDate\((\d+),(\d+),\s*(\d+)\)$/.exec(action);
        h.assert(values, 'Calendar day has no date');
        const expected = values.slice(1).map(Number).join('-');
        await Promise.all([calendar.waitForEvent('close'), day.click()]);
        h.assert(await page.locator(`input[name="${field}"]`).inputValue() === expected, 'Calendar date did not reach the report');
      } finally {
        if (!calendar.isClosed()) await calendar.close();
      }
    });
  }
  await page.close();
}

if (require.main === module) runWorkflow('billing-unbilled-report', workflow, { openPatient: true });
module.exports = { workflow, FIXTURES, SCENARIOS };
