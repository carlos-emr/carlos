#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Disposable BC install, NEW_BC_TELEPLAN=no, exclusive execution and runner access to HOME_DIR.
// Uses the actual Administration menu and generation form; nothing is transmitted to MSP.
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { authenticatedProbe } = require('./lib/authenticated-probe');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { withProviderCleanup } = require('./billing-bc-simulation-encoding-playwright-checks');
const VIEW = '/billing/CA/BC/ViewBillingTeleplanGroupReport';
const GENERATE = '/billing/CA/BC/ViewGenTeleplanGroupReport';

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
  if (!process.env.HOME_DIR || !fs.statSync(process.env.HOME_DIR).isDirectory()) {
    throw new h.SkipCheck('HOME_DIR must name the owned server output directory visible to this runner');
  }
}

async function workflow(s) {
  const { sql } = s; const q = h.sqlString;
  const physician = bc.createBillingPhysician(s);
  bc.makeBcPatient(s); bc.registerOwnedBcBillCleanup(s);
  const claim = bc.seedOwnedBcBill({ ...s, provider: physician.providerNo }, { amount: '12.34' });
  sql.execute(`UPDATE billingmaster SET payee_no=${q(physician.payeeNo)},practitioner_no=${q(physician.ohipNo)} WHERE billingmaster_no=${claim.billingmasterNo}`);
  const beforeLog = sql.value('SELECT COALESCE(MAX(log_no),0) FROM log_teleplantx');
  const home = path.resolve(process.env.HOME_DIR);
  s.cleanup(() => {
    const rows = sql.rows(`SELECT ohipfilename,htmlfilename FROM billactivity WHERE providerohipno=${q(physician.ohipNo)}`);
    for (const files of rows) for (const filename of files) {
      h.assert(new RegExp(`^H[A-L](?:${physician.payeeNo}\\.[0-9]{3}|${physician.ohipNo}_[0-9]{3}\\.htm)$`).test(filename), 'Unexpected owned legacy filename');
      fs.rmSync(path.join(home, filename), { force: true });
    }
    // This check requires an exclusive disposable installation: no other generator may run.
    sql.execute(`DELETE FROM billactivity WHERE providerohipno=${q(physician.ohipNo)}; DELETE FROM log_teleplantx WHERE log_no>${beforeLog}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billactivity WHERE providerohipno=${q(physician.ohipNo)}`) === '0', 'Owned legacy activities remain');
  });
  const count = () => sql.value(`SELECT COUNT(*) FROM billactivity WHERE providerohipno=${q(physician.ohipNo)}`);
  const fixturePage = await s.context.newPage();
  h.wireStrictPage(fixturePage, 'legacy-provider-fixture', s.recorder);
  s.cleanup(() => {
    for (const table of ['program_provider','provider_facility','secUserRole','providersite','providerbillcenter','property','providerArchive']) {
      sql.execute(`DELETE FROM ${table} WHERE provider_no=${q(physician.providerNo)}`);
    }
  });
  async function saveFixtureProvider(deactivate) {
    // Fixture maintenance: use the app's writer so its active-provider cache is evicted.
    await h.gotoApp(fixturePage, s.config.baseUrl, `/admin/ViewProviderUpdateProvider?keyword=${physician.providerNo}`);
    const form = fixturePage.locator('form[name="updatearecord"]');
    if (deactivate) await form.locator('#statusInactive').check();
    await ui.clickAndAwaitReload(fixturePage, form.locator('input[type="submit"][name="subbutton"]'),
      { label: 'save owned provider fixture', timeout: 30000 });
  }
  await withProviderCleanup(async () => {
  await saveFixtureProvider(false);
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'legacy-teleplan-admin' });
  const link = admin.locator(`a.xlink[rel$="${VIEW}"]`).first();
  await admin.locator('a.xlink[rel$="/billing/CA/BC/ViewBillingAccountReports"]').waitFor({ state: 'attached' });
  if (!await link.count()) throw new h.SkipCheck('The legacy Teleplan menu requires NEW_BC_TELEPLAN=no');
  await revealAuditLink(admin, link, 20000);
  await Promise.all([admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(VIEW)), link.click()]);
  const iframe = admin.locator('#dynamic-content iframe#myFrame'); await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The legacy Teleplan form did not open');
  await frame.waitForURL(url => new URL(url).pathname.endsWith(VIEW));
  await h.assertNotErrorPage(frame, 'legacy Teleplan form');
  const select = frame.locator('select[name="providers"]');
  const option = select.locator(`option[value^="${physician.ohipNo},"]`);
  await option.waitFor({ state: 'attached' });
  const selected = await option.getAttribute('value');
  await s.step('opening the legacy generation form writes nothing and a GET generation is refused', async () => {
    h.assert(count() === '0', 'Opening the form created a batch');
    const response = await authenticatedProbe(s, s.config.baseUrl + GENERATE);
    try { h.assert(response.status() === 405, 'GET generation must be refused'); }
    finally { await response.dispose(); }
    h.assert(count() === '0', 'GET generation created a batch');
  });
  async function generate() {
    await select.selectOption(selected);
    await frame.locator('input[name="curUser"]').evaluate(input => { input.value = 'forged-creator'; });
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(GENERATE)),
      Promise.all([admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 }),
        frame.locator('input[type="submit"][value="Create Report"]').click()]),
    ]);
    h.assert(response.status() === 200, 'Legacy generation did not complete successfully');
    await frame.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(frame, 'legacy generation result');
  }
  await s.step('the legacy form commits complete files, authenticated activity, logs and billed status', async () => {
    await generate(); h.assert(count() === '1', 'Exactly one legacy activity was expected');
    const [filename, html, creator, total] = sql.rows(`SELECT ohipfilename,htmlfilename,creator,total FROM billactivity WHERE providerohipno=${q(physician.ohipNo)}`)[0];
    h.assert(creator === s.provider, 'The hidden creator overrode the authenticated login');
    h.assert(total === '12.34', 'Legacy activity total differs from the claim');
    h.assert(fs.readFileSync(path.join(home, filename), 'utf8').includes(claim.billingmasterNo.padStart(7, '0')), 'Legacy output lacks the owned claim');
    h.assert(fs.readFileSync(path.join(home, html), 'utf8').includes(claim.billingmasterNo.padStart(7, '0')), 'Legacy HTML lacks the owned claim');
    h.assert(sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${claim.billingmasterNo}`) === 'B', 'Claim was not marked billed');
    h.assert(sql.value(`SELECT status FROM billing WHERE billing_no=${claim.billingNo}`) === 'B', 'Invoice was not marked billed');
    h.assert(Number(sql.value(`SELECT COUNT(*) FROM log_teleplantx WHERE log_no>${beforeLog}`)) >= 2, 'Legacy logs were not committed');
  });
  await s.step('retrying the same legacy selection creates no duplicate activity or claim output', async () => {
    const logs = sql.value(`SELECT COUNT(*) FROM log_teleplantx WHERE log_no>${beforeLog}`);
    await generate(); h.assert(count() === '1', 'Retry created a duplicate activity');
    h.assert(sql.value(`SELECT COUNT(*) FROM log_teleplantx WHERE log_no>${beforeLog}`) === logs, 'Retry created duplicate claim logs');
  });
  }, () => saveFixtureProvider(true), () => fixturePage.close());
}
if (require.main === module) runWorkflow('billing-bc-legacy-teleplan', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight };
