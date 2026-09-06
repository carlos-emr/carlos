#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for Administration > Update Drugref, driven the way
 * an operator uses it: log in, open the Administration panel, click the
 * "Update Drugref" link, read the status panel, and (optionally) click the
 * button and follow the run to its end.
 *
 * Why this exists. A tester on 2026.08.0-alpha11 reported that "the feature to
 * trigger drugref to update the db from CARLOS is not working". What actually
 * happened: the click DID reach DrugRef, which dropped every drug table, then
 * died on a Hibernate 7 HQL rejection with its in-progress flag still set. The
 * page said "Update has started" and never looked again, the status probe said
 * "updating" forever, and drug search answered "None found" until the seed was
 * reloaded by hand. Nothing in the suite drove this page. This check pins:
 *
 *   1. The page reaches DrugRef: the verify probe answers a real date/version.
 *   2. The status relay (method=status) answers JSON with a `state`, so the
 *      page can tell "running" from "died". UNAVAILABLE is accepted only when
 *      DRUGREF_UPDATE_REQUIRE_STATUS is not "true" (a DrugRef build older than
 *      getUpdateStatus); the packaged deployment must answer a real state.
 *   3. With DRUGREF_UPDATE_TRIGGER=true: the click gets "running" (or "already
 *      running"), the page follows the run, the run ends SUCCEEDED, the date in
 *      the panel moves forward, and a live drug search still answers. This
 *      rebuilds the DrugRef database from DPD_BASE_URL and takes 15-60 minutes
 *      against Health Canada; point DPD_BASE_URL at a local mirror in CI.
 *
 * Runs read-only by default (steps 1 and 2 only), so it is safe in the
 * deb-install suite loop (docs/ui-tests/deb-install-validation.md §6).
 *
 * Requires the deb-install env contract: BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN.
 * Optional: DRUGREF_UPDATE_TRIGGER (default false), DRUGREF_UPDATE_TIMEOUT_SEC
 *   (default 3600), DRUGREF_UPDATE_REQUIRE_STATUS (default false), CHROME_PATH,
 *   DRUGREF_UPDATE_SCREENSHOT_DIR (default /tmp), DRUG_SEARCH_TERM (default "amox").
 */

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.DRUGREF_UPDATE_SCREENSHOT_DIR || '/tmp',
};
const trigger = process.env.DRUGREF_UPDATE_TRIGGER === 'true';
// The trigger REBUILDS the target's drug database, so it is refused against anything but a
// loopback target. DRUGREF_UPDATE_TRIGGER=true alone is an easy thing to leave exported in
// a shell that is later pointed at a real installation with ALLOW_NON_LOCAL_BASE_URL.
if (trigger) {
  const host = config.baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase();
  assert(
    new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']).has(host),
    `DRUGREF_UPDATE_TRIGGER=true rebuilds the DrugRef database and is refused against the `
      + `non-loopback target ${host}. Run it against a disposable local install.`,
  );
}
const requireStatus = process.env.DRUGREF_UPDATE_REQUIRE_STATUS === 'true';
const timeoutSec = Number(process.env.DRUGREF_UPDATE_TIMEOUT_SEC || '3600');
const searchTerm = process.env.DRUG_SEARCH_TERM || 'amox';
assert(Number.isFinite(timeoutSec) && timeoutSec > 0, `DRUGREF_UPDATE_TIMEOUT_SEC must be a positive number, got ${process.env.DRUGREF_UPDATE_TIMEOUT_SEC}`);

// Context-relative: BASE_URL carries the webapp context (/carlos), and the page's own
// calls are made against it.
const ENDPOINT = `${config.baseUrl.pathname.replace(/\/$/, '')}/rx/updateDrugrefDB`;

function postedMethod(response) {
  const body = response.request().postData() || '';
  const match = /(?:^|&)method=([^&]*)/.exec(body);
  return match ? decodeURIComponent(match[1]) : '';
}

async function callStatus(page) {
  // Same call the page makes, through the page's own session and CSRF token.
  return page.evaluate(async (endpoint) => {
    const token = (document.querySelector('input[name="CSRF-TOKEN"]') || {}).value || '';
    const body = new URLSearchParams();
    body.append('method', 'status');
    body.append('CSRF-TOKEN', token);
    const r = await fetch(endpoint, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'CSRF-TOKEN': token,
        'X-Requested-With': 'XMLHttpRequest',
      },
      body: body.toString(),
    });
    return { status: r.status, contentType: r.headers.get('content-type') || '', text: await r.text() };
  }, ENDPOINT);
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    // Certificate validation is bypassed for loopback only. BASE_URL can be pointed at a
    // remote deployment with ALLOW_NON_LOCAL_BASE_URL=true, and this check logs in as an
    // administrator and carries CSRF tokens, so an unvalidated certificate there would be
    // a real exposure rather than the self-signed localhost cert the runbook expects.
    const loopback = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);
    const isLoopback = loopback.has(config.baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase());
    const context = await browser.newContext({ ignoreHTTPSErrors: isLoopback, viewport: { width: 1280, height: 900 } });
    const landing = await login(context, config, recorder);
    await landing.close();

    // Reach the page the way an operator does: the Administration panel's link
    // opens it in a popup window.
    const admin = await context.newPage();
    wirePage(admin, 'admin-panel', recorder);
    await gotoApp(admin, config.baseUrl, '/admin/ViewAdmin');
    await admin.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(admin, 'administration panel');
    const link = admin.locator('a', { hasText: /^\s*Update Drugref\s*$/i }).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    // Registered BEFORE the click: the popup fetches its CSRF token and fires the verify
    // probe from its own DOMContentLoaded, which can complete before a waiter attached to
    // the popup afterwards exists — a 30s timeout on a page that worked.
    const verifyResponsePromise = context.waitForEvent('response', {
      predicate: (r) => r.url().includes(ENDPOINT) && postedMethod(r) === 'verify',
      timeout: 30000,
    });
    const [popup] = await Promise.all([
      context.waitForEvent('page', { timeout: 20000 }),
      link.evaluate((a) => a.click()),
    ]);
    wirePage(popup, 'update-drugref', recorder);
    await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(popup, 'Update Drugref page');
    assert(/\/admin\/ViewUpdateDrugref/.test(popup.url()), `popup opened ${popup.url()}, expected /admin/ViewUpdateDrugref`);

    // Step 1: the verify probe answered and the panel shows a real dataset.
    const verifyResponse = await verifyResponsePromise;
    assert(verifyResponse.status() === 200, `verify probe answered HTTP ${verifyResponse.status()}`);
    const verify = await verifyResponse.json();
    assert(
      verify && verify.lastUpdate && verify.lastUpdate !== 'updating',
      `verify answered ${JSON.stringify(verify)}: DrugRef is unavailable, has no data, or an update is already running`,
    );
    await popup.locator('#statusDisplay').waitFor({ state: 'visible', timeout: 20000 });
    const dateBefore = (await popup.locator('#dbDateTime').innerText()).trim();
    assert(dateBefore === verify.lastUpdate, `panel shows "${dateBefore}" but verify said "${verify.lastUpdate}"`);
    assert((await popup.locator('#drugDatabase').innerText()).trim().length > 0, 'panel shows no database name');
    console.log(`STEP 1 verify: PASS (${verify.drugDatabase} ${verify.version}, last update ${dateBefore})`);

    // Step 2: the status relay answers JSON with a state the page can act on.
    const statusReply = await callStatus(popup);
    assert(statusReply.status === 200, `status relay answered HTTP ${statusReply.status}`);
    assert(/json/i.test(statusReply.contentType), `status relay answered "${statusReply.contentType}", not JSON`);
    let status;
    try {
      status = JSON.parse(statusReply.text);
    } catch (e) {
      assert(false, `status relay body is not JSON: ${statusReply.text.slice(0, 200)}`);
    }
    assert(typeof status.state === 'string' && status.state.length > 0, `status has no state: ${statusReply.text}`);
    if (status.state === 'UNAVAILABLE') {
      assert(!requireStatus, 'status relay answered UNAVAILABLE: DrugRef is down or predates getUpdateStatus');
      console.log('STEP 2 status: PASS with UNAVAILABLE (DrugRef build without getUpdateStatus; set DRUGREF_UPDATE_REQUIRE_STATUS=true to reject)');
    } else {
      assert(['IDLE', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(status.state), `unexpected state ${status.state}`);
      assert(status.state !== 'RUNNING', 'an update is already running; rerun when it has finished');
      console.log(`STEP 2 status: PASS (state ${status.state}${status.message ? ', ' + status.message : ''})`);
    }
    // Load-bearing: #updateButton now renders hidden and is only revealed once a probe
    // reports a definite state, so this asserts the page positively enabled the trigger
    // rather than merely never having hidden it.
    assert(await popup.locator('#updatedb').isVisible(), 'the Update Drugref button is not visible');

    if (!trigger) {
      await screenshot(popup, config.screenshotDir, 'drugref-update-page');
      console.log('STEP 3 trigger: SKIPPED (set DRUGREF_UPDATE_TRIGGER=true to rebuild the DrugRef database)');
    } else {
      // Step 3: click, and follow the run to its end.
      const [startResponse] = await Promise.all([
        popup.waitForResponse((r) => r.url().includes(ENDPOINT) && postedMethod(r) === 'updateDB', { timeout: 60000 }),
        popup.locator('#updatedb').click(),
      ]);
      assert(startResponse.status() === 200, `updateDB answered HTTP ${startResponse.status()}`);
      const start = await startResponse.json();
      assert(
        start.result === 'running' || start.result === 'updating',
        `updateDB answered ${JSON.stringify(start)}: the trigger did not reach DrugRef`,
      );
      await popup.locator('#updateResult').waitFor({ state: 'visible', timeout: 20000 });
      const startText = (await popup.locator('#updateResult').innerText()).trim();
      assert(/started|already running/i.test(startText), `page reported "${startText}" after the click`);
      console.log(`STEP 3a trigger: PASS (${start.result}: "${startText.slice(0, 80)}")`);

      const deadline = Date.now() + timeoutSec * 1000;
      let last = null;
      let sawRunning = false;
      while (Date.now() < deadline) {
        const reply = await callStatus(popup);
        assert(reply.status === 200, `status relay answered HTTP ${reply.status} mid-run`);
        last = JSON.parse(reply.text);
        if (last.state === 'RUNNING') {
          sawRunning = true;
        } else if (last.state === 'UNAVAILABLE') {
          // Older DrugRef: only the verify probe knows. Poll it instead.
          const v = await popup.evaluate(async (endpoint) => {
            const token = (document.querySelector('input[name="CSRF-TOKEN"]') || {}).value || '';
            const body = new URLSearchParams();
            body.append('method', 'verify');
            body.append('CSRF-TOKEN', token);
            const r = await fetch(endpoint, {
              method: 'POST',
              credentials: 'same-origin',
              headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
              body: body.toString(),
            });
            return r.json();
          }, ENDPOINT);
          if (v.lastUpdate !== 'updating') {
            break;
          }
          sawRunning = true;
        } else {
          break;
        }
        await popup.waitForTimeout(15000);
      }
      assert(last && last.state !== 'RUNNING', `update still running after ${timeoutSec}s: ${JSON.stringify(last)}`);
      assert(last.state !== 'FAILED', `update FAILED: ${last.message}`);
      console.log(`STEP 3b run: PASS (${sawRunning ? 'observed RUNNING, then ' : ''}${last.state}${last.message ? ': ' + last.message : ''})`);

      // The page must have followed the run and now show the new date.
      await popup.waitForFunction(
        (before) => {
          const el = document.getElementById('dbDateTime');
          return el && el.textContent.trim() && el.textContent.trim() !== before;
        },
        dateBefore,
        { timeout: 60000 },
      );
      const dateAfter = (await popup.locator('#dbDateTime').innerText()).trim();
      assert(dateAfter > dateBefore, `last-update date did not move forward: "${dateBefore}" -> "${dateAfter}"`);
      const resultText = (await popup.locator('#updateResult').innerText()).trim();
      assert(/completed/i.test(resultText), `page reports "${resultText}" after the run ended`);
      console.log(`STEP 3c page: PASS (panel now shows ${dateAfter}; "${resultText.slice(0, 100)}")`);

      // And prescribing still finds drugs in the rebuilt dataset.
      const rxPage = await context.newPage();
      wirePage(rxPage, 'drug-search', recorder);
      await gotoApp(rxPage, config.baseUrl, '/rx/choosePatient?demographicNo=1');
      await rxPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      const searchBox = rxPage.locator('#searchString');
      await searchBox.waitFor({ state: 'visible', timeout: 20000 });
      const [searchResponse] = await Promise.all([
        rxPage.waitForResponse((r) => r.url().includes('/rx/searchDrug') && r.request().method() === 'POST', { timeout: 60000 }),
        searchBox.pressSequentially(searchTerm, { delay: 120 }),
      ]);
      const payload = await searchResponse.json();
      assert(Array.isArray(payload.results) && payload.results.length > 0, `drug search for "${searchTerm}" found nothing after the update`);
      console.log(`STEP 3d search: PASS ("${searchTerm}" -> ${payload.results.length} result(s))`);
      await rxPage.close();
      await screenshot(popup, config.screenshotDir, 'drugref-update-done');
    }

    await popup.close();
    await admin.close();
    assertNoPageErrors(recorder);
    await context.close();
    console.log('PASS drugref update page');
  } catch (error) {
    console.error('FAIL drugref update Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
