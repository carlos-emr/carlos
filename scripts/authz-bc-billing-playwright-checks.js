#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Role authorization, BC billing (issue #4439, task 3). A login whose role holds no billing, billing administration
 * or report right is refused every BC billing page and every BC billing write. authz-read-routes.js lists only
 * Ontario billing routes and authz-read-role-matrix runs on Ontario only, so nothing asked this of a BC install.
 *
 * User path: a throwaway login with a front-desk role (chart and demographic read, appointment write: the write-
 * restricted role of authz-write-role-matrix, no _billing, _admin.billing, _admin or _report) signs in through the
 * login form, then requests each BC billing page by its address (GET), and sends each BC billing write
 * (lib/bc-billing-mutators.js) as the POST its page sends, carrying the login's own CSRF token so the refusal is the
 * authorization decision, not CSRFGuard's.
 * Asserted (LAST step, every route together): each page and each write is answered with CARLOS's own refusal (its 403
 * Security Exception page, or the security error redirect), never served; and each write changes none of the rows it
 * would. The first step proves the login reached the schedule and that the test login is served the bill view, so a
 * refusal is the role's, not a broken page.
 *
 * Fixtures: lib/bc-billing-mutators.js (owned bill on an owned billed appointment, a fee code, two diagnostic codes
 * and an association, all marker-described), the restricted role and its login (lib/authz-read-fixture.js);
 * cleanup removes each and asserts it is gone. BC installs only.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, WRITE_RESTRICTED_PRIVILEGES } = require('./lib/authz-read-fixture');
const { probe, classify, signIn, ledger: makeLedger } = require('./lib/authz-read-probe');
const R = require('./lib/mutation-replay');
const M = require('./lib/bc-billing-mutators');

const NAME = 'authz-bc-billing';

/** The BC billing pages a clinic opens (menus, the bill form's popups, the master record). */
function pages(s, targets) {
  const { bill, codes } = targets;
  return [
    `billing/CA/BC/billingView?billing_no=${bill.billingNo}&receipt=no`,
    `billing/CA/BC/billingView?billing_no=${bill.billingNo}&receipt=yes`,
    `billing/CA/BC/viewReceivePaymentAction?billNo=${bill.billingNo}&billingmasterNo=${bill.billingmasterNo}`,
    `billing/CA/BC/ViewWcbForms?demographic_no=${s.patient}`,
    `billing/CA/BC/viewformwcb?demographic_no=${s.patient}`,
    'billing/CA/BC/showServiceCodeAssocs',
    `billing/CA/BC/editServiceCodeAssocAction?svcCode=${codes.fee}`,
    'billing/CA/BC/ViewBillingCodeAdjust',
    `billing/CA/BC/ViewBillingEditCode?codeId=${codes.feeId}`,
    'billing/CA/BC/ViewBillingAddCode?addNew=true',
    'billing/CA/BC/ViewBillingPrivateCodeAdjust',
    'billing/CA/BC/ViewBillingManageReferralDoc',
    'billing/CA/BC/ViewBillingAddReferralDoc',
    'billing/CA/BC/viewBillingPreferencesAction',
    'quickBillingBC',
    'billing/CA/BC/SimulateTeleplanFile',
    'billing/CA/BC/GenerateTeleplanFile',
    'billing/CA/BC/DownloadBilling?filename=H0000000_000000_000',
    'billing/CA/BC/ManageTeleplan',
    'billing/CA/BC/ViewSettleBG',
    'billing/CA/BC/ViewReconcileReports',
    'billing/CA/BC/ViewBillingTA',
    'billing/CA/BC/ViewBillingAccountReports',
    'billing/CA/BC/ViewBillingReportCenter',
  ];
}

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const base = `${config.baseUrl.href.replace(/\/$/, '')}/`;
  const targets = M.seedBcBillingTargets(s);
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const open = makeLedger();
  let restricted;
  let token;
  let login;

  await s.step('a login with a front-desk role and no billing right signs in; the test login is served the bill', async () => {
    const role = fixture.addRole(WRITE_RESTRICTED_PRIVILEGES);
    login = fixture.addLogin(role);
    M.registerPreferencesCleanup(s, login.providerNo);
    restricted = await signIn(s, login);
    s.cleanup(() => restricted.context.close().catch(() => {}));
    h.assert(/\/provider\/providercontrol/.test(h.pathOnly(restricted.page.url())),
      `The restricted login landed on ${h.pathOnly(restricted.page.url())}, not the schedule`);
    token = await R.sessionToken(restricted.page, config.baseUrl);
    const served = await probe(s.context, new URL(pages(s, targets)[0], base).toString(), { needles: [marker] });
    h.assert(classify(served) === 'served' && served.found.length === 1,
      `The test login was not served the owned bill (HTTP ${served.status}, ${served.lead}), so a refusal would prove nothing`);
  });

  await s.step('each BC billing page requested by the restricted login is recorded', async () => {
    for (const route of pages(s, targets)) {
      const result = await probe(restricted.context, new URL(route, base).toString(), { needles: [marker] });
      const verdict = classify(result);
      console.log(`  probe ${NAME}: GET ${route} -> ${result.status} ${verdict}${result.found.length ? ' (shows the owned patient)' : ''}`);
      if (verdict !== 'refused') open.add(`GET ${route.split('?')[0]}`, `${result.status} ${verdict}: ${result.lead}`);
    }
  });

  await s.step('each BC billing write sent by the restricted login with its own CSRF token is recorded', async () => {
    for (const write of M.bcBillingWrites(s, targets, login.providerNo)) {
      const before = write.snapshot();
      const body = new URLSearchParams(write.params);
      body.set('CSRF-TOKEN', token);
      let result;
      try {
        const response = await restricted.context.request.post(new URL(write.route, base).toString(), {
          headers: { 'content-type': 'application/x-www-form-urlencoded', referer: restricted.page.url() },
          data: body.toString(), maxRedirects: 0, failOnStatusCode: false, timeout: 40000,
        });
        const text = await response.text();
        const title = (/<title[^>]*>([\s\S]*?)<\/title>/i.exec(text) || [])[1] || '';
        result = { status: response.status(), location: response.headers().location || '', length: text.length,
          fromApp: Object.prototype.hasOwnProperty.call(response.headers(), 'x-permitted-cross-domain-policies'),
          lead: (title || text.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim().slice(0, 60), found: [] };
      } catch (error) {
        result = { status: 0, lead: String(error.message).slice(0, 60), found: [] };
      }
      const changed = write.snapshot() !== before;
      const verdict = classify(result);
      console.log(`  probe ${NAME}: POST ${write.route} -> ${result.status} ${verdict}${changed ? ', CHANGED the owned rows' : ''}`);
      if (verdict !== 'refused' || changed) {
        open.add(`POST ${write.route}`, `${result.status} ${verdict}: ${result.lead}${changed ? '; changed the owned rows' : ''}`);
      }
    }
  });

  await s.step('every BC billing page and write refused the login without billing rights and changed nothing', async () => {
    open.assertEmpty('BC billing served a login whose role holds no billing right');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
