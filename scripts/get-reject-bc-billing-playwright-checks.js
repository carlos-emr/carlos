#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, BC billing (issue #4439, task 3). Every BC billing write must refuse a GET or HEAD before it
 * changes anything: the session cookie is SameSite=Lax and CSRFGuard checks only POSTs, so a write that runs on a GET
 * is a one-click CSRF (a link in a message, an <img> in an eForm). #4439 found these routes checking only _billing w
 * (D12) and Generate Teleplan File writing a batch on the menu's GET (D3).
 *
 * The writes: Quick Billing Save, the day sheet's unbill, the bill delete, Receive Payment, the WCB form's Save and
 * Close, the WCB correction's Submit, Manage Billing Codes' Add Code and fee save, Manage Private Bill's Delete, the
 * association editor's Save and the list's Remove, Manage Referral Doctors' Add, and BC Billing Preferences' Save
 * (lib/bc-billing-mutators.js names each one's fields, rows and targets). Generate Teleplan File's GET naming a
 * physician and Settle Paid Claims' GET and HEAD are sent by billing-bc-teleplan-file and billing-bc-settle-paid-claims,
 * which own the clinic-wide state those two would change.
 *
 * User path: the writes are sent as a signed-in clinician (a throwaway login holding the test login's roles, so
 * BC Billing Preferences' Save, which always saves the signed-in provider's own, would land on the throwaway) from
 * the browser context, as a link or image would send them: GET, then HEAD.
 * Asserted (LAST step, all routes together): each GET and HEAD is answered 405 by CARLOS itself (not 403: the actions
 * check the privilege before the method, so a 403 would be the throwaway's role, not a GET guard) and changes
 * none of the rows its write would (the owned bill, bill line, appointment status and payments; the owned WCB forms;
 * the owned fee codes; the owned association; referral doctors named by the marker; the throwaway's preferences).
 * Before, the first step proves the throwaway can open BC billing (the Bill Status of the owned bill), so a refusal is
 * not just a login without billing rights.
 *
 * Fixtures: lib/bc-billing-mutators.js (owned bill linked to an owned billed appointment, a fee code, two diagnostic
 * codes and an association, all marker-described) and a throwaway login (lib/throwaway-login-fixture.js); cleanup
 * removes each and asserts it is gone. BC installs only.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createLedger } = require('./lib/get-reject-probe');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const M = require('./lib/bc-billing-mutators');

const NAME = 'get-reject-bc-billing';

async function workflow(s) {
  const { sql, marker, config } = s;
  const targets = M.seedBcBillingTargets(s);
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  M.registerPreferencesCleanup(s, fixture.providerNo);
  const ledger = createLedger(NAME);

  let clinician;
  await s.step('a throwaway clinician login holding the test login\'s roles signs in and opens the owned bill', async () => {
    const context = await h.newContext(s.context.browser(), config);
    context.setDefaultTimeout(20000);
    s.cleanup(() => context.close().catch(() => {}));
    await h.login(context, { ...config, testUser: fixture.username }, h.createRecorder());
    const view = await context.request.get(new URL(`billing/CA/BC/billingView?billing_no=${targets.bill.billingNo}&receipt=no`,
      `${config.baseUrl.href.replace(/\/$/, '')}/`).toString(), { maxRedirects: 0, failOnStatusCode: false });
    h.assert(view.status() === 200 && (await view.text()).includes(marker),
      `The throwaway login could not open the owned bill (HTTP ${view.status()}), so it is not a clinician with billing rights`);
    clinician = { ...s, context };
  });

  for (const write of M.bcBillingWrites(s, targets, fixture.providerNo)) {
    await s.step(`${write.label} sent as a GET and a HEAD is recorded`, async () => {
      await ledger.probe(clinician, { label: write.label, path: M.appPath(s, write.route), params: write.params, snapshot: write.snapshot,
        expect405: true });
    });
  }

  await s.step('every BC billing write refused GET and HEAD and changed nothing', async () => {
    const count = ledger.assertAllRefused();
    console.log(`  probe ${NAME}: ${count} BC billing writes refused GET and HEAD`);
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow };
