#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #3950 regression: the BC billing simulation (billing/CA/BC/ViewBillingSim ->
 * ViewGenSimulation -> billingSim.jsp) prints server-built report HTML raw, so every
 * claim-record value must be encoded where ExtractBean/HtmlTeleplanHelper build the rows.
 *
 * The check owns one throwaway provider (FAKE- marker, unique OHIP number) and one MSP
 * claim (billing + billingmaster) whose patient name, PHN and fee code carry markup.
 * It drives the real simulation form, then asserts that:
 *   - the markup renders as literal text in the report row (no element, no script ran);
 *   - the simulation stayed a dry run (claim statuses and teleplan log untouched);
 *   - every owned row is removed afterwards.
 *
 * BC-only: skipped when the BC billingmaster table is absent. Uses the common harness
 * environment contract (BASE_URL, TEST_USER/TEST_PASSWORD/TEST_PIN, MYSQL_*); no extra env.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

// Short enough for billing.demographic_name (60), billingmaster.phn (10), billing_code (10).
const NAME_PAYLOAD = '<img src=x onerror="window.__carlos3950=1">';
const PHN_PAYLOAD = '<b>12</b>';
const CODE_PAYLOAD = '<u>01</u>';

function unusedValue(sql, table, column, prefix) {
  for (let i = 0; i < 20; i++) {
    const value = `${prefix}${randomInt(10000, 99999)}`;
    if (sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${column}=${h.sqlString(value)}`) === '0') return value;
  }
  throw new Error(`Could not find an unused ${table}.${column}`);
}

async function workflow(s) {
  if (s.sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  const providerNo = unusedValue(s.sql, 'provider', 'provider_no', '9');
  const ohipNo = unusedValue(s.sql, 'provider', 'ohip_no', '8');
  let billingNo;
  let billingmasterNo;
  const teleplanLogCount = s.sql.value('SELECT COUNT(*) FROM log_teleplantx');

  s.cleanup(() => {
    if (billingmasterNo) s.sql.execute(`DELETE FROM billingmaster WHERE billingmaster_no=${billingmasterNo} AND billing_no=${billingNo}`);
    if (billingNo) s.sql.execute(`DELETE FROM billing WHERE billing_no=${billingNo} AND provider_ohip_no=${h.sqlString(ohipNo)}`);
    s.sql.execute(`DELETE FROM provider WHERE provider_no=${h.sqlString(providerNo)} AND last_name=${h.sqlString(s.marker)}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(providerNo)})
      + (SELECT COUNT(*) FROM billing WHERE provider_ohip_no=${h.sqlString(ohipNo)})
      + (SELECT COUNT(*) FROM billingmaster WHERE billing_no=${billingNo || 0})`) === '0',
    'Owned BC simulation fixtures were not removed');
  });

  s.sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,ohip_no,billing_no,status,lastUpdateDate)
    VALUES (${h.sqlString(providerNo)},${h.sqlString(s.marker)},'Sim','doctor','','F',${h.sqlString(ohipNo)},${h.sqlString(ohipNo)},'1',NOW())`);
  billingNo = s.sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,appointment_no,demographic_name,
      billing_date,billing_time,update_date,update_time,status,provider_ohip_no,billingtype,creator,total)
    VALUES (0,0,${h.sqlString(providerNo)},0,${h.sqlString(NAME_PAYLOAD)},CURDATE(),CURTIME(),CURDATE(),CURTIME(),
      'O',${h.sqlString(ohipNo)},'MSP',${h.sqlString(s.provider)},'23.00'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingNo), 'BC billing fixture was not created');
  billingmasterNo = s.sql.value(`INSERT INTO billingmaster (billing_no,createdate,billingstatus,demographic_no,appointment_no,
      phn,billing_unit,billing_code,bill_amount,service_date,dx_code1,paymentMethod)
    VALUES (${billingNo},NOW(),'O',0,0,${h.sqlString(PHN_PAYLOAD)},'001',${h.sqlString(CODE_PAYLOAD)},'23.00',
      DATE_FORMAT(CURDATE(),'%Y%m%d'),'250',6); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingmasterNo), 'BC billingmaster fixture was not created');

  const page = await s.context.newPage();
  h.wireStrictPage(page, 'billing-bc-simulation', s.recorder);

  await s.step('simulation form renders for the billing admin', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/billing/CA/BC/ViewBillingSim');
    await h.assertNotErrorPage(page, 'BC billing simulation form');
    h.assert(await page.locator('input[name="xml_appointment_date"]').count() === 1, 'Simulation form did not render');
  });

  await s.step('patient-record markup renders as text in the simulation report', async () => {
    const today = s.sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
    // The date inputs are readonly calendar targets; set them the way the popup would.
    await page.evaluate(({ from, to }) => {
      document.querySelector('input[name="xml_vdate"]').value = from;
      document.querySelector('input[name="xml_appointment_date"]').value = to;
    }, { from: today, to: today });
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      page.locator('input[type="submit"][value="Create Report"]').click(),
    ]);
    await h.assertNotErrorPage(page, 'BC billing simulation report');

    const invoiceLink = page.locator('a', { hasText: new RegExp(`^\\s*${billingNo}\\s*$`) });
    h.assert(await invoiceLink.count() >= 1, 'The owned claim row was not in the simulation report');
    const row = invoiceLink.first().locator('xpath=ancestor::tr[1]');
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells.includes(NAME_PAYLOAD), `Patient name was not rendered as literal text: ${JSON.stringify(cells[1])}`);
    h.assert(cells.includes(PHN_PAYLOAD), 'PHN was not rendered as literal text');
    h.assert(cells.includes(CODE_PAYLOAD), 'Fee code was not rendered as literal text');
    h.assert(await row.locator('img, b, u, script').count() === 0, 'Claim markup was parsed into report elements');
    h.assert(await page.locator('img[src="x"]').count() === 0, 'Injected image element exists on the report page');
    h.assert(await page.evaluate(() => window.__carlos3950) === undefined, 'Injected onerror handler executed');
    const onClick = await invoiceLink.first().getAttribute('onclick');
    h.assert(/adjustBill\.jsp\?billingmaster_no=\d{7}'/.test(onClick || ''),
      `Adjustment link lost its shape: ${onClick}`);
  });

  await s.step('simulation stayed a dry run', async () => {
    h.assert(s.sql.value(`SELECT status FROM billing WHERE billing_no=${billingNo}`) === 'O', 'Simulation marked the claim billed');
    h.assert(s.sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`) === 'O',
      'Simulation marked the billingmaster row billed');
    h.assert(s.sql.value('SELECT COUNT(*) FROM log_teleplantx') === teleplanLogCount, 'Simulation wrote teleplan log rows');
  });
  await page.close();
}

if (require.main === module) runWorkflow('billing-bc-simulation-encoding', workflow, { openPatient: false });
module.exports = { workflow };
