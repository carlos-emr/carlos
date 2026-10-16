#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Ontario Billing Report Center as a user who is neither an administrator nor a doctor sees it.
 * Those two roles skip it for the new-report dashboard (ViewBillingReportCenter2Action), which is
 * what billing-on-reports-inr-eoy drives; anyone else with report rights (a clinic's own billing-clerk
 * role, for instance) gets the Report Center, whose Billed and Unsettled reports no check ran.
 *
 * User path (throwaway login holding one owned role with report, billing, schedule, messenger, patient and search read rights):
 * Schedule ▸ Report ▸ Generate a billing report (billing/CA/ON/ViewBillingReportCenter) ▸ Billed ▸
 * provider ▸ dates ▸ Create Report (billing/CA/ON/ViewBillingReportControl); the same form ▸ Unsettled ▸
 * Create Report.
 * Asserted: the login gets the Report Center (not the dashboard), which offers the owned report provider;
 * the Billed report lists the provider's open claim in the range and the Unsettled report its submitted
 * (billed, not yet paid) claim, as the dashboard lists them for administrators and doctors.
 * Fixtures: the owned FAKE- patient, a FAKE- billable provider with its "billingreport" reportprovider
 * row, an open and a submitted Ontario claim (billing_on_cheader1 + billing_on_item) for them, an owned
 * role and the throwaway login. Cleanup removes them all and asserts they are gone.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 30000;
const WINDOW = { from: '2025-02-01', to: '2025-02-28' };
// What a clinic's billing clerk needs to reach the schedule and its menus, plus report and billing read rights.
const GRANTS = [['_report', 'r'], ['_billing', 'r'], ['_appointment', 'r'], ['_msg', 'r'], ['_demographic', 'r'], ['_search', 'r']];

function unusedProviderNo(sql) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM billing_on_cheader1 WHERE provider_no=${h.sqlString(candidate)})`) === '0') return candidate;
  }
  throw new Error('No unused provider number was found');
}

async function workflow(s) {
  const { sql, marker, patient, provider, config, recorder } = s;
  const q = h.sqlString;
  // secObjPrivilege.roleUserGroup holds 30 characters: the 23-character marker plus "-clerk" fits.
  const role = `${marker}-clerk`;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const ids = { providerNo: '', report: '', claims: [] };
  let roleOwned = false;
  s.cleanup(() => {
    fixture.cleanup();
    if (ids.claims.length) {
      const list = ids.claims.map(c => c.id).join(',');
      sql.execute(`DELETE FROM billing_on_item WHERE ch1_id IN (${list});
        DELETE FROM billing_on_cheader1 WHERE id IN (${list}) AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE id IN (${list}))
        + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id IN (${list}))`) === '0', 'The owned claims were not removed');
    }
    if (ids.report) sql.execute(`DELETE FROM reportprovider WHERE id=${ids.report} AND provider_no=${q(ids.providerNo)}`);
    if (ids.providerNo) sql.execute(`DELETE FROM provider WHERE provider_no=${q(ids.providerNo)} AND last_name=${q(marker)}`);
    if (roleOwned) {
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)}; DELETE FROM secRole WHERE role_name=${q(role)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM reportprovider WHERE id=${ids.report || 0})
      + (SELECT COUNT(*) FROM provider WHERE provider_no=${q(ids.providerNo)})
      + (SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}) + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)})`) === '0',
    'The owned provider, report-provider row or role was not removed');
  });

  // A role of the clinic's own: report, billing and appointment read rights, and neither an admin nor a doctor role.
  h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'The owned role already exists');
  roleOwned = true;
  sql.execute(`INSERT INTO secRole(role_name,description) VALUES(${q(role)},'Owned billing clerk');
    INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
    VALUES ${GRANTS.map(([object, privilege]) => `(${q(role)},${q(object)},${q(privilege)},0,${q(provider)})`).join(',')}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === String(GRANTS.length),
    'The owned role\'s grants were not stored under its exact name');
  fixture.create({ roleNames: [role] });
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(fixture.providerNo)} AND role_name<>${q(role)}`) === '0',
    'The throwaway login holds other roles');

  ids.providerNo = unusedProviderNo(sql);
  const p = q(ids.providerNo);
  const ohip = String(randomInt(100000, 999999));
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,ohip_no,rma_no,status,
      lastUpdateUser,lastUpdateDate) VALUES (${p},${q(marker)},'Reports','doctor','','F',${q(ohip)},'','1',${q(provider)},NOW())`);
  ids.report = sql.value(`INSERT INTO reportprovider (provider_no,team,action,status) VALUES (${p},'','billingreport','A');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ids.report), 'The report-provider fixture was not created');
  const demoName = `${marker},Workflow`;
  // An open claim (O) for Billed, and a claim submitted to OHIP but not yet paid (B) for Unsettled.
  for (const [status, date] of [['O', '2025-02-10'], ['B', '2025-02-17']]) {
    const id = sql.value(`INSERT INTO billing_on_cheader1 (header_id,transc_id,rec_id,hin,ver,dob,pay_program,payee,ref_num,
        facilty_num,admission_date,ref_lab_num,man_review,location,demographic_no,provider_no,appointment_no,demographic_name,
        sex,province,billing_date,billing_time,total,paid,status,comment1,visittype,provider_ohip_no,provider_rma_no,
        apptProvider_no,asstProvider_no,creator,clinic)
      VALUES (0,'HE','H','','','19800102','HCP','P','','','','','','',${patient},${p},0,${q(demoName)},'2','ON',
        ${q(date)},'10:00:00','33.70','0.00',${q(status)},${q(marker)},'00',${q(ohip)},'','','',${q(provider)},NULL);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A claim fixture was not created');
    sql.execute(`INSERT INTO billing_on_item (ch1_id,transc_id,rec_id,service_code,fee,ser_num,service_date,dx,dx1,dx2,status)
      VALUES (${id},'HE','T','A007A','33.70','1',${q(date)},'250','','',${q(status)})`);
    ids.claims.push({ id, status, date });
  }

  const context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close());
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'billing-clerk', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'billing-clerk-login' });

  let center;
  await s.step('a login that is neither administrator nor doctor gets the Report Center, offering the owned provider', async () => {
    const link = schedule.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first();
    const { page: index } = await ui.clickOpensPopupOrNavigates(schedule, link, { context, recorder, label: 'report-index', timeout: TIMEOUT });
    ({ page: center } = await ui.clickOpensPopupOrNavigates(index, index.locator('a[href*="/billing/CA/ON/ViewBillingReportCenter"]').first(),
      { context, recorder, label: 'billing-report-center', timeout: TIMEOUT }));
    await center.locator('form[name="serviceform"]').waitFor({ timeout: TIMEOUT });
    await h.assertNotErrorPage(center, 'billing report center');
    h.assert(new URL(center.url()).pathname.endsWith('/billing/CA/ON/ViewBillingReportCenter')
      && await center.locator('input[name="reportAction"][value="unsettled"]').count() === 1,
    `The login did not get the Report Center (landed on ${h.pathOnly(center.url())})`);
    const offered = await center.locator('select[name="providerview"] option').evaluateAll(options => options.map(o => o.value));
    h.assert(offered.includes(ids.providerNo), 'The Report Center does not offer the owned report provider');
  });

  // Run one report from the serviceform on the page (the Report Center, then the report page it posts to).
  // The report page opens its <form> inside a <table>, so the parser leaves the controls outside the form
  // element (still owned by it): they are found on the page, not under the form.
  const run = async action => {
    const form = center;
    await form.locator(`input[name="reportAction"][value="${action}"]`).check();
    await form.locator('select[name="providerview"]').selectOption(ids.providerNo);
    await form.locator('input[name="xml_vdate"]').fill(WINDOW.from);
    await form.locator('input[name="xml_appointment_date"]').fill(WINDOW.to);
    await ui.clickAndAwaitReload(center, form.locator('input[type="submit"][value="Create Report"]'), { timeout: TIMEOUT, label: `the ${action} report` });
    await h.assertNotErrorPage(center, `${action} report`);
    h.assert(new URL(center.url()).pathname.endsWith('/billing/CA/ON/ViewBillingReportControl'), `The ${action} report did not open`);
    return (await center.locator('body').innerText()).includes(demoName);
  };

  await s.step('the Billed and Unsettled reports list the provider\'s Ontario claims in the range', async () => {
    const missing = [];
    if (!await run('billed')) missing.push('Billed does not list the open claim');
    if (!await run('unsettled')) missing.push('Unsettled does not list the submitted, unpaid claim');
    h.assert(missing.length === 0, missing.join('; '));
  });
}

if (require.main === module) runWorkflow('billing-on-report-center-claims', workflow, { openMaster: false });
module.exports = { workflow };
