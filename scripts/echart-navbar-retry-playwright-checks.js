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

/**
 * Remove only failures whose exact Playwright Request identity was recorded by an
 * injected route handler, and drop matching console errors only when unambiguously
 * attributable to those removed failures.
 */
function consumeInjectedFault(recorder, since, injectedRequests) {
  const addedFailures = recorder.requestFailures.splice(since.failures);
  const removedFailures = [];
  const keptFailures = [];
  for (const entry of addedFailures) {
    const req = entry.requestRef?.deref();
    if (req && injectedRequests.has(req)) {
      removedFailures.push(entry);
    } else {
      keptFailures.push(entry);
    }
  }
  recorder.requestFailures.push(...keptFailures);

  const keyFor = (label, url) => `${label ?? ''}\0${url ?? ''}`;
  const removedCounts = new Map();
  for (const failure of removedFailures) {
    const key = keyFor(failure.label, failure.url);
    removedCounts.set(key, (removedCounts.get(key) || 0) + 1);
  }
  const keptKeys = new Set(keptFailures.map((failure) => keyFor(failure.label, failure.url)));

  const addedConsole = recorder.consoleIssues.splice(since.console);
  const consoleMatchesByKey = new Map();
  for (const entry of addedConsole) {
    if (!/^Failed to load resource: net::ERR_(FAILED|ABORTED|TIMED_OUT)/.test(entry.text)) continue;
    const key = keyFor(entry.label, entry.location?.url);
    if (!removedCounts.has(key) || keptKeys.has(key)) continue;
    const bucket = consoleMatchesByKey.get(key) || [];
    bucket.push(entry);
    consoleMatchesByKey.set(key, bucket);
  }
  const removableConsole = new Set();
  for (const [key, entries] of consoleMatchesByKey.entries()) {
    if (entries.length <= removedCounts.get(key)) {
      for (const entry of entries) removableConsole.add(entry);
    }
  }
  recorder.consoleIssues.push(...addedConsole.filter((entry) => !removableConsole.has(entry)));
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
    const exercisedPanels = hasFamilyDoctor ? ['preventions', 'Dx', 'fDoc'] : ['preventions', 'Dx'];
    const capturePanelSnapshots = (panelIds) => chartPage.evaluate((ids) => {
      const snapshots = {};
      for (const id of ids) {
        const el = document.getElementById(id);
        const heading = el?.querySelector('.nav-menu-title h3')?.textContent?.trim() || '';
        const listId = el?.querySelector('ul[id]')?.id || '';
        const reloadUrl = el?.querySelector('input[name="reloadUrl"]')?.value || '';
        const text = (el?.textContent || '').trim();
        snapshots[id] = {
          present: Boolean(el),
          nonEmpty: Boolean(el && (el.childElementCount > 0 || text.length > 0)),
          heading,
          listId,
          reloadUrl,
          linkCount: el ? el.querySelectorAll('a').length : 0,
          text,
        };
      }
      return snapshots;
    }, panelIds);
    const baselinePanels = await capturePanelSnapshots(exercisedPanels);
    assert(exercisedPanels.some((id) => baselinePanels[id].nonEmpty),
      'Expected at least one exercised navbar panel to render baseline content before fault injection');

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
    const recoveredPanels = await capturePanelSnapshots(exercisedPanels);
    for (const id of exercisedPanels) {
      assert(recoveredState[id]?.status === 'loaded' && recoveredState[id]?.recovered === true,
        `Panel ${id} did not report recovered === true after network-error retry`);
      const before = baselinePanels[id];
      const after = recoveredPanels[id];
      assert(after.present === before.present, `Panel ${id} DOM presence changed after retry`);
      assert(!after.text.includes(`${id} Error:`), `Panel ${id} retained error text after retry`);
      if (before.nonEmpty) {
        assert(after.nonEmpty, `Panel ${id} rendered blank after network-error retry`);
        if (before.heading) {
          assert(after.heading === before.heading,
            `Panel ${id} heading mismatch after retry: expected "${before.heading}", got "${after.heading}"`);
        }
        if (before.listId) {
          assert(after.listId === before.listId,
            `Panel ${id} list container mismatch after retry: expected "${before.listId}", got "${after.listId}"`);
        }
        if (before.reloadUrl) {
          assert(after.reloadUrl === before.reloadUrl,
            `Panel ${id} reloadUrl mismatch after retry: expected "${before.reloadUrl}", got "${after.reloadUrl}"`);
        }
        if (before.linkCount > 0) {
          assert(after.linkCount > 0, `Panel ${id} lost its rendered links after network-error retry`);
        }
      }
    }
    assert(recoveredState.leftBusy === 'false' && recoveredState.rightBusy === 'false',
      'Navbar columns stayed busy after display recovery');
    assertStrictPage(recorder, ['login', 'patient-search', 'master-record', 'echart']);
    console.log('  PASS left, doctor, and right display modules each recovered once and restored their rendered fragments after net::ERR_FAILED');

    // 2. Retry exhaustion: two network errors settle to error with no third attempt.
    let ticklerAttempts = 0;
    const injectedTicklerRequests = new Set();
    const failAllTickler = async (route) => {
      ticklerAttempts += 1;
      injectedTicklerRequests.add(route.request());
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
    consumeInjectedFault(recorder, ticklerSince, injectedTicklerRequests);
    console.log('  PASS exhausted display retry fails cleanly after 2 attempts without infinite retry');

    // 3. Non-display CaseManagementView POST never retries on a network error.
    let cppAttempts = 0;
    const injectedCppRequests = new Set();
    const failCpp = async (route) => {
      if (!route.request().url().includes('issue_code=FamHistory')) {
        await route.continue();
        return;
      }
      cppAttempts += 1;
      injectedCppRequests.add(route.request());
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
    consumeInjectedFault(recorder, cppSince, injectedCppRequests);
    console.log('  PASS non-display CaseManagementView POST failed immediately with 1 attempt');

    // 4. Stalled retry expires via Chromium's native XHR timeout (15s) and clears busy state.
    let msgsAttempts = 0;
    const injectedMsgsRequests = new Set();
    let releaseStall;
    const stallGate = new Promise((resolve) => { releaseStall = resolve; });
    const stallRetry = async (route) => {
      msgsAttempts += 1;
      injectedMsgsRequests.add(route.request());
      if (msgsAttempts === 1) {
        await route.abort('failed');
        return;
      }
      await stallGate;
      await route.abort('aborted').catch(() => {});
    };
    const msgsSince = { failures: recorder.requestFailures.length, console: recorder.consoleIssues.length };
    let timeoutProbe = null;
    await chartPage.route('**/encounter/displayMessages?*', stallRetry);
    try {
      await chartPage.evaluate(() => {
        const origOpen = XMLHttpRequest.prototype.open;
        const probe = { origOpen, xhrs: [], timeoutEvents: 0, timedOutAttempts: [] };
        window.__carlosMsgsTimeoutProbe = probe;
        XMLHttpRequest.prototype.open = function (method, url) {
          if (typeof url === 'string' && url.includes('/encounter/displayMessages')) {
            const xhr = this;
            const attemptIndex = probe.xhrs.length;
            probe.xhrs.push(xhr);
            xhr.addEventListener('timeout', () => {
              probe.timeoutEvents += 1;
              probe.timedOutAttempts.push({
                attemptIndex,
                timeoutMs: xhr.timeout,
                statusBeforeTimeout: window.carlosNavbarLoadState?.modules?.msgs?.status || null,
              });
            });
          }
          return origOpen.apply(this, arguments);
        };
      });
      await chartPage.evaluate(() => {
        window.requestNavbarColumn(window.ctx + '/encounter/displayMessages?hC=009999', 'msgs', 'cmd=msgs', 'leftNavBar');
      });
      await chartPage.waitForFunction(() => {
        const state = window.carlosNavbarLoadState;
        return state && state.modules.msgs?.status === 'loading'
          && document.getElementById('leftNavBar')?.getAttribute('aria-busy') === 'true';
      }, undefined, { timeout: 5000 });
      // Require the native timeout event on the replacement XHR before accepting terminal error state.
      await chartPage.waitForFunction(() => {
        const state = window.carlosNavbarLoadState;
        const probe = window.__carlosMsgsTimeoutProbe;
        return state && state.pending === 0 && state.modules.msgs?.status === 'error'
          && probe && probe.xhrs.length === 2
          && probe.timeoutEvents >= 1;
      }, undefined, { timeout: 22000 });
      timeoutProbe = await chartPage.evaluate(() => {
        const probe = window.__carlosMsgsTimeoutProbe;
        return {
          xhrs: probe.xhrs.length,
          configuredTimeouts: probe.xhrs.map((xhr) => xhr.timeout),
          timeoutEvents: probe.timeoutEvents,
          timedOutAttempts: probe.timedOutAttempts,
        };
      });
    } finally {
      await chartPage.evaluate(() => {
        const probe = window.__carlosMsgsTimeoutProbe;
        if (probe && probe.origOpen) {
          XMLHttpRequest.prototype.open = probe.origOpen;
        }
        delete window.__carlosMsgsTimeoutProbe;
      }).catch(() => {});
      releaseStall();
      await chartPage.unroute('**/encounter/displayMessages?*', stallRetry);
    }
    assert(msgsAttempts === 2, `Expected 2 displayMessages attempts (initial + stalled retry), observed ${msgsAttempts}`);
    assert(timeoutProbe && timeoutProbe.xhrs === 2,
      `Expected 2 observed displayMessages XHRs, got ${timeoutProbe?.xhrs}`);
    assert(timeoutProbe.configuredTimeouts[0] === 0 && timeoutProbe.configuredTimeouts[1] === 15000,
      `Unexpected displayMessages XHR timeouts: ${JSON.stringify(timeoutProbe.configuredTimeouts)}`);
    assert(timeoutProbe.timeoutEvents === 1
      && timeoutProbe.timedOutAttempts.length === 1
      && timeoutProbe.timedOutAttempts[0].attemptIndex === 1
      && timeoutProbe.timedOutAttempts[0].timeoutMs === 15000
      && timeoutProbe.timedOutAttempts[0].statusBeforeTimeout === 'loading',
      `Expected native timeout event to transition replacement displayMessages XHR from loading (15000ms), observed ${JSON.stringify(timeoutProbe.timedOutAttempts)}`);
    assert((await chartPage.locator('#msgs').innerText()).includes('msgs Error: 0'),
      'Timed-out displayMessages retry did not display error text');
    assert(await chartPage.locator('#leftNavBar').getAttribute('aria-busy') === 'false',
      'Left navbar stayed busy after stalled retry timed out');
    consumeInjectedFault(recorder, msgsSince, injectedMsgsRequests);
    assertStrictPage(recorder, ['login', 'patient-search', 'master-record', 'echart']);
    console.log('  PASS stalled retry fired native 15s XHR timeout event and cleared column busy state');

    return { recovered: exercisedPanels.length };
  } finally {
    await closeBrowserWithChartCleanup(browser, config.baseUrl);
  }
}

if (require.main === module) {
  runCheck({ name: 'echart-navbar-retry', run: main });
}

module.exports = { main, consumeInjectedFault };
