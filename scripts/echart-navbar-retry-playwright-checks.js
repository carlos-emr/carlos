#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Live browser regression check for eChart navbar network-error recovery (#6383):
 *   1. Aborts the first attempt of left, optional doctor, and right read-only display
 *      modules with net::ERR_FAILED and verifies each retries once with valid CSRF/context,
 *      renders links, marks module.recovered === true, and clears aria-busy.
 *   2. Aborts both attempts of displayTickler and verifies retry exhaustion settles to error
 *      without scheduling a third attempt.
 *   3. Aborts a non-display CaseManagementView POST and verifies it never retries.
 *   4. Aborts the first displayMessages attempt and stalls its replacement so real Chromium
 *      fires the configured 15s XHR timeout, surfacing the error and clearing busy state.
 *
 * READ-ONLY: writes no clinical rows; releases chart locks on exit.
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { closeBrowserWithChartCleanup } = require('./lib/chart-lock-cleanup');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { watchNavbarRecovery } = require('./patient-photo-upload-playwright-checks');

/** Remove only deliberately injected fault entries for one endpoint from the strict recorder. */
function consumeInjectedFault(recorder, since, pathSuffix) {
  const matchesPath = (url) => {
    try { return new URL(url).pathname.endsWith(pathSuffix); }
    catch { return false; }
  };
  const addedFailures = recorder.requestFailures.splice(since.failures);
  recorder.requestFailures.push(...addedFailures.filter((entry) => !matchesPath(entry.url)));
  const addedConsole = recorder.consoleIssues.splice(since.console);
  recorder.consoleIssues.push(...addedConsole.filter((entry) => !(
    matchesPath(entry.location?.url) && /^Failed to load resource: net::ERR_(FAILED|ABORTED|TIMED_OUT)/.test(entry.text)
  )));
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.ECHART_NAV_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.ECHART_NAV_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.ECHART_NAV_TIMEOUT_MS || '20000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChart(context, masterPage, recorder, timeout);
    await waitForNavbars(chartPage, timeout);
    assertStrictPage(recorder, ['login', 'patient-search', 'master-record', 'echart']);

    const waitForReloadedNavbars = watchNavbarRecovery(chartPage, recorder);
    const hasFamilyDoctor = await chartPage.evaluate(() => Boolean(window.carlosNavbarLoadState?.modules?.fDoc));

    // 1. Single-retry recovery across left, optional doctor, and right display modules.
    const counts = { prevention: 0, familyDoctor: 0, disease: 0 };
    const failOnce = (key) => async (route) => {
      counts[key] += 1;
      if (counts[key] === 1) {
        await route.abort('failed');
        return;
      }
      await route.continue();
    };
    const prevHandler = failOnce('prevention');
    const docHandler = failOnce('familyDoctor');
    const dxHandler = failOnce('disease');
    await chartPage.route('**/encounter/displayPrevention?*', prevHandler);
    await chartPage.route('**/encounter/displayFamilyDoctor?*', docHandler);
    await chartPage.route('**/encounter/displayDisease?*', dxHandler);
    try {
      const since = recorder.requestFailures.length;
      await chartPage.reload({ waitUntil: 'load', timeout });
      await waitForReloadedNavbars(since);
    } finally {
      await chartPage.unroute('**/encounter/displayPrevention?*', prevHandler);
      await chartPage.unroute('**/encounter/displayFamilyDoctor?*', docHandler);
      await chartPage.unroute('**/encounter/displayDisease?*', dxHandler);
    }

    assert(counts.prevention === 2, `Expected 2 displayPrevention attempts, observed ${counts.prevention}`);
    assert(counts.disease === 2, `Expected 2 displayDisease attempts, observed ${counts.disease}`);
    if (hasFamilyDoctor) {
      assert(counts.familyDoctor === 2, `Expected 2 displayFamilyDoctor attempts, observed ${counts.familyDoctor}`);
    }
    const recoveredState = await chartPage.evaluate(() => ({
      preventions: window.carlosNavbarLoadState.modules.preventions,
      Dx: window.carlosNavbarLoadState.modules.Dx,
      fDoc: window.carlosNavbarLoadState.modules.fDoc || null,
      leftBusy: document.getElementById('leftNavBar')?.getAttribute('aria-busy'),
      rightBusy: document.getElementById('rightNavBar')?.getAttribute('aria-busy'),
    }));
    assert(recoveredState.preventions?.status === 'loaded' && recoveredState.preventions?.recovered === true,
      'Preventions did not report recovered === true after network-error retry');
    assert(recoveredState.Dx?.status === 'loaded' && recoveredState.Dx?.recovered === true,
      'Disease Registry (right navbar) did not report recovered === true after network-error retry');
    if (hasFamilyDoctor) {
      assert(recoveredState.fDoc?.status === 'loaded' && recoveredState.fDoc?.recovered === true,
        'Family Doctor optional panel did not report recovered === true after network-error retry');
    }
    assert(recoveredState.leftBusy === 'false' && recoveredState.rightBusy === 'false',
      'Navbar columns stayed busy after display recovery');
    assertStrictPage(recorder, ['login', 'patient-search', 'master-record', 'echart']);
    console.log('  PASS left, doctor, and right display modules each recovered once after net::ERR_FAILED');

    // 2. Retry exhaustion: two network errors settle to error with no third attempt.
    let ticklerAttempts = 0;
    const failAllTickler = async (route) => {
      ticklerAttempts += 1;
      await route.abort('failed');
    };
    const ticklerSince = { failures: recorder.requestFailures.length, console: recorder.consoleIssues.length };
    await chartPage.route('**/encounter/displayTickler?*', failAllTickler);
    try {
      await chartPage.evaluate(() => {
        window.requestNavbarColumn(window.ctx + '/encounter/displayTickler?hC=FF6600', 'tickler', 'cmd=tickler', 'leftNavBar');
      });
      await chartPage.waitForFunction(() => {
        const state = window.carlosNavbarLoadState;
        return state && state.pending === 0 && state.modules.tickler?.status === 'error';
      }, undefined, { timeout });
      await new Promise((resolve) => setTimeout(resolve, 250));
    } finally {
      await chartPage.unroute('**/encounter/displayTickler?*', failAllTickler);
    }
    assert(ticklerAttempts === 2, `Expected exactly 2 displayTickler attempts on double failure, observed ${ticklerAttempts}`);
    assert((await chartPage.locator('#tickler').innerText()).includes('tickler Error: 0'),
      'Exhausted displayTickler retry did not display error text');
    assert(await chartPage.locator('#leftNavBar').getAttribute('aria-busy') === 'false',
      'Left navbar stayed busy after displayTickler retry exhaustion');
    consumeInjectedFault(recorder, ticklerSince, '/encounter/displayTickler');
    console.log('  PASS exhausted display retry fails cleanly after 2 attempts without infinite retry');

    // 3. Non-display CaseManagementView POST never retries on a network error.
    let cppAttempts = 0;
    const failCpp = async (route) => {
      cppAttempts += 1;
      await route.abort('failed');
    };
    const cppSince = { failures: recorder.requestFailures.length, console: recorder.consoleIssues.length };
    await chartPage.route('**/CaseManagementView*', failCpp);
    try {
      await chartPage.evaluate(() => {
        window.requestNavbarColumn(
          window.ctx + '/CaseManagementView?hc=009999&method=listNotes&issue_code=FamHistory',
          'FamHistory',
          'cmd=FamHistory',
          'rightNavBar',
        );
      });
      await chartPage.waitForFunction(() => {
        const state = window.carlosNavbarLoadState;
        return state && state.pending === 0 && state.modules.FamHistory?.status === 'error';
      }, undefined, { timeout });
      await new Promise((resolve) => setTimeout(resolve, 250));
    } finally {
      await chartPage.unroute('**/CaseManagementView*', failCpp);
    }
    assert(cppAttempts === 1, `Non-display CaseManagementView POST retried (${cppAttempts} attempts)`);
    assert((await chartPage.locator('#FamHistory').innerText()).includes('FamHistory Error: 0'),
      'Failed CaseManagementView POST did not display error text');
    assert(await chartPage.locator('#rightNavBar').getAttribute('aria-busy') === 'false',
      'Right navbar stayed busy after failed CaseManagementView POST');
    consumeInjectedFault(recorder, cppSince, '/CaseManagementView');
    console.log('  PASS non-display CaseManagementView POST failed immediately with 1 attempt');

    // 4. Stalled retry expires via Chromium's native XHR timeout (15s) and clears busy state.
    let msgsAttempts = 0;
    let releaseStall;
    const stallGate = new Promise((resolve) => { releaseStall = resolve; });
    const stallRetry = async (route) => {
      msgsAttempts += 1;
      if (msgsAttempts === 1) {
        await route.abort('failed');
        return;
      }
      await stallGate;
      await route.abort('aborted').catch(() => {});
    };
    const msgsSince = { failures: recorder.requestFailures.length, console: recorder.consoleIssues.length };
    await chartPage.route('**/encounter/displayMessages?*', stallRetry);
    try {
      await chartPage.evaluate(() => {
        window.requestNavbarColumn(window.ctx + '/encounter/displayMessages?hC=009999', 'msgs', 'cmd=msgs', 'leftNavBar');
      });
      await chartPage.waitForFunction(() => {
        const state = window.carlosNavbarLoadState;
        return state && state.modules.msgs?.status === 'loading'
          && document.getElementById('leftNavBar')?.getAttribute('aria-busy') === 'true';
      }, undefined, { timeout: 5000 });
      // Wait for real Chromium's 15s xhr.timeout to fire and transition msgs to error.
      await chartPage.waitForFunction(() => {
        const state = window.carlosNavbarLoadState;
        return state && state.pending === 0 && state.modules.msgs?.status === 'error';
      }, undefined, { timeout: 22000 });
    } finally {
      releaseStall();
      await chartPage.unroute('**/encounter/displayMessages?*', stallRetry);
    }
    assert(msgsAttempts === 2, `Expected 2 displayMessages attempts (initial + stalled retry), observed ${msgsAttempts}`);
    assert((await chartPage.locator('#msgs').innerText()).includes('msgs Error: 0'),
      'Timed-out displayMessages retry did not display error text');
    assert(await chartPage.locator('#leftNavBar').getAttribute('aria-busy') === 'false',
      'Left navbar stayed busy after stalled retry timed out');
    consumeInjectedFault(recorder, msgsSince, '/encounter/displayMessages');
    assertStrictPage(recorder, ['login', 'patient-search', 'master-record', 'echart']);
    console.log('  PASS stalled retry expired via real XHR timeout and cleared column busy state');

    return { recovered: hasFamilyDoctor ? 3 : 2 };
  } finally {
    await closeBrowserWithChartCleanup(browser, config.baseUrl);
  }
}

if (require.main === module) {
  runCheck({ name: 'echart-navbar-retry', run: main });
}

module.exports = { main };
