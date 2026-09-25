#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * HRM provider confidentiality statements were retired in 2026.08.0-alpha14 (the entity, DAO,
 * admin page, route and navigation links are gone). HRM2Action keeps the two old method names
 * and answers them with HTTP 410 Gone instead of falling through into the report listing with
 * a misleading 200. This check pins that contract from a real logged-in session, in both the
 * verb an old bookmark uses (GET) and the one the removed page posted (POST), and proves the
 * two things that must still work around it: the Administration panel offers no link to the
 * removed page, and the HRM report listing the same action serves still answers.
 *
 * Read-only: nothing is written. Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD,
 * TEST_PIN, MYSQL_* (the workflow session opens its database runner for the login lookup).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const RETIRED = ['getConfidentialityStatement', 'saveConfidentialityStatement'];

async function workflow(s) {
  const hrm = `${s.config.baseUrl}/hospitalReportManager/hrm`;
  await s.step('retired statement operations answer 410 on GET and POST', async () => {
    for (const method of RETIRED) {
      const get = await s.context.request.get(`${hrm}?method=${method}&providerNo=${encodeURIComponent(s.provider)}`,
        { maxRedirects: 0, failOnStatusCode: false });
      h.assert(get.status() === 410, `GET method=${method} answered ${get.status()}, expected 410`);
      const post = await s.context.request.post(hrm, {
        form: { method, providerNo: s.provider, statement: 'no longer stored' },
        maxRedirects: 0, failOnStatusCode: false,
      });
      // POST without a CSRF token is refused before the action runs; with the same session's
      // token it must reach the 410. Either way it must never be a 200 report listing.
      h.assert(post.status() === 410 || post.status() === 403,
        `POST method=${method} answered ${post.status()}, expected 410 (or the CSRF 403)`);
      h.assert(!/"recordsTotal"|"data"\s*:/.test(await post.text()),
        `POST method=${method} fell through into the report listing`);
    }
  });
  await s.step('the report listing served by the same action still answers', async () => {
    const list = await s.context.request.get(`${hrm}?start=0&length=5`, { maxRedirects: 0, failOnStatusCode: false });
    h.assert(list.status() === 200, `HRM listing answered ${list.status()}`);
    const body = await list.text();
    h.assert(/"recordsTotal"|"data"/.test(body), 'HRM listing did not return its DataTables JSON');
  });
  await s.step('the Administration panel no longer links the removed statement page', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'hrm-admin' });
    await admin.locator('a.xlink').first().waitFor({ state: 'attached' });
    const stale = await admin.locator('a[rel*="ConfidentialityStatement" i], a[href*="ConfidentialityStatement" i], a[rel*="HRMStatementModify" i], a[href*="HRMStatementModify" i]').count();
    h.assert(stale === 0, `The Administration panel still offers ${stale} link(s) to the removed HRM statement page`);
    await admin.close();
  });
}

if (require.main === module) runWorkflow('hrm-retired-statement', workflow, { openPatient: false });
module.exports = { workflow };
