#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #3950 regression: the BC billing simulation (billing/CA/BC/ViewBillingSim ->
 * ViewGenSimulation -> billingSim.jsp) prints server-built report HTML raw, so every
 * claim-record value must be encoded where ExtractBean/HtmlTeleplanHelper build the rows.
 *
 * The check owns one throwaway provider (FAKE- marker, unique OHIP number, created through
 * Admin > Add Provider so the cached active-provider list is evicted) and one MSP claim
 * (billing + billingmaster) whose patient name, PHN and fee code carry markup.
 * It drives the real simulation form, then asserts that:
 *   - the simulation form submits without a page error;
 *   - the markup renders as literal text in the report row (no element, no script ran);
 *   - the simulation stayed a dry run (claim statuses and teleplan log untouched);
 *   - every owned row is removed afterwards. The provider is first deactivated through
 *     Admin > Update Provider so the cached active-provider list is evicted again; a SQL
 *     delete alone would leave a ghost provider cached for later checks.
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

async function deactivateProvider(page, s, providerNo) {
  if (!providerNo) return;
  await h.gotoApp(page, s.config.baseUrl, `/admin/ViewProviderUpdateProvider?keyword=${encodeURIComponent(providerNo)}`);
  const form = page.locator('form[name="updatearecord"]');
  await form.locator('#statusInactive').check();
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    form.locator('input[type="submit"][name="subbutton"]').click(),
  ]);
  h.assert(s.sql.value(`SELECT status FROM provider WHERE provider_no=${h.sqlString(providerNo)}`) === '0',
    'Provider update did not deactivate the fixture provider');
}

async function workflow(s) {
  if (s.sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  const ohipNo = unusedValue(s.sql, 'provider', 'ohip_no', '8');
  const firstName = `Sim${ohipNo}`;
  const ownedProvider = `last_name=${h.sqlString(s.marker)} AND first_name=${h.sqlString(firstName)}`;
  let providerNo;
  let billingNo;
  let billingmasterNo;
  const teleplanLogCount = s.sql.value('SELECT COUNT(*) FROM log_teleplantx');

  s.cleanup(() => {
    if (billingmasterNo) s.sql.execute(`DELETE FROM billingmaster WHERE billingmaster_no=${billingmasterNo} AND billing_no=${billingNo}`);
    if (billingNo) s.sql.execute(`DELETE FROM billing WHERE billing_no=${billingNo} AND provider_ohip_no=${h.sqlString(ohipNo)}`);
    // Matched by the unique marker/name stamp, so an app-assigned provider_no is still found.
    const owned = `provider_no IN (SELECT provider_no FROM provider WHERE ${ownedProvider})`;
    s.sql.execute(['program_provider', 'provider_facility', 'secUserRole', 'providersite']
      .map(table => `DELETE FROM ${table} WHERE ${owned}`).join(';'));
    s.sql.execute(`DELETE FROM provider WHERE ${ownedProvider}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE ${ownedProvider})
      + (SELECT COUNT(*) FROM billing WHERE provider_ohip_no=${h.sqlString(ohipNo)})
      + (SELECT COUNT(*) FROM billingmaster WHERE billing_no=${billingNo || 0})`) === '0',
    'Owned BC simulation fixtures were not removed');
  });

  const page = await s.context.newPage();
  h.wireStrictPage(page, 'billing-bc-simulation', s.recorder);

  await s.step('billing provider is created through Add Provider', async () => {
    // Through the app, not SQL: genSimulation iterates ProviderDao.getActiveProviders(), which is
    // @Cacheable (5-minute TTL). Only an app-side provider save evicts that cache, so a provider
    // inserted behind the app's back would be invisible to the simulation on a warm install.
    await h.gotoApp(page, s.config.baseUrl, '/admin/ViewProviderAddARecordHtm');
    const form = page.locator('form[name="searchprovider"]');
    const providerNoInput = form.locator('input[name="provider_no"]').first();
    await providerNoInput.waitFor({ state: 'visible' });
    if ((await providerNoInput.getAttribute('readonly')) === null) {
      await providerNoInput.fill(unusedValue(s.sql, 'provider', 'provider_no', '9'));
    }
    await form.locator('input[name="last_name"]').fill(s.marker);
    await form.locator('input[name="first_name"]').fill(firstName);
    await form.locator('select[name="provider_type"]').selectOption('doctor');
    await form.locator('select[name="sex"]').selectOption('F');
    await form.locator('input[name="ohip_no"]').fill(ohipNo);
    await form.locator('input[name="billing_no"]').fill(ohipNo);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
      form.locator('input[type="submit"]').first().click(),
    ]);
    providerNo = s.sql.value(`SELECT provider_no FROM provider WHERE ${ownedProvider} AND ohip_no=${h.sqlString(ohipNo)} AND status='1'`);
    h.assert(providerNo, 'Add Provider did not create the active billing provider');
  });

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

  try {
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

      // The invoice cell is exactly the billing number, so an exact accessible-name match finds the row.
      const invoiceLink = page.getByRole('link', { name: billingNo, exact: true });
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
  } finally {
    // Deactivate through the app so ProviderDao.updateProvider() evicts the 5-minute
    // ACTIVE_PROVIDERS cache. The SQL cleanup alone would leave the deleted provider in the
    // cached active list for later checks against the same Tomcat. Best effort: never mask
    // the step failure that brought us here.
    try {
      await deactivateProvider(page, s, providerNo);
    } catch (error) {
      console.error(`  WARN billing-bc-simulation-encoding: provider cache eviction failed: ${error.message}`);
    }
  }
  await page.close();
}

if (require.main === module) runWorkflow('billing-bc-simulation-encoding', workflow, { openPatient: false });
module.exports = { workflow };
