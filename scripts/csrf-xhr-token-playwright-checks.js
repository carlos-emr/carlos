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
 * Browser check that a page which POSTs only through the shared AJAX helper
 * sends its CSRF token -- in the request HEADER -- and is accepted.
 *
 * WHY THIS CHECK EXISTS (issue #3665, finding 10). A static audit of CLAUDE.md's
 * CSRF token bootstrapping rule reported six pages as violations: they POST
 * through share/javascript/carlos-ajax.js and carry neither a real POST form
 * nor the csrf-token.jspf include, so the hidden input the helper reads is
 * absent or empty. The audit's model was that the input is the token's only
 * carrier. It is not. CarlosAjax uses XMLHttpRequest rather than fetch()
 * precisely so that CSRFGuard's own script, which every HTML response loads,
 * injects CSRF-TOKEN and X-Requested-With into every XHR send, and CSRFGuard
 * validates the header before it looks at the body. The bootstrap rule governs
 * fetch() callers, which that script cannot reach.
 *
 * WHAT IS ASSERTED, on each page the audit named that has a document of its own
 * (the fragments it named render into these): the hidden input is read and its
 * emptiness is REPORTED, not asserted, so a page that later grows a real form
 * still passes; then the page's own CarlosAjax.request() is used to POST to a
 * read-only endpoint, the headers the browser actually put on the wire are
 * read back from the request, and the token header, the XHR marker, an HTTP
 * 200 and a JSON body are required. A CSRF rejection is an HTML error page
 * with a 403, so a regression here cannot pass by accident.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: the chart from the Master Record;
 * Pending Docs from the Inbox; Row Display from the chart's Labs menu.
 *
 * READ-ONLY: the probe asks whether document 0 is linked to a patient, which
 * writes nothing and answers {"isLinkedToDemographic":false}.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:csrf-xhr-token-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CSRF_XHR_SEARCH=FAKE-             surname prefix used to reach a patient
 *   CSRF_XHR_DEMOGRAPHIC_NO=2         which patient's chart to open
 *   CSRF_XHR_TIMEOUT_MS=20000         per-step allowance
 *
 * IMPLEMENTS: docs/ui-tests/app-findings-log.md finding 10 (the browser half
 * that scripts/lib/csrf-bootstrap-audit.js says it cannot be).
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart } = require('./echart-navbar-modules-playwright-checks');

/** A read-only POST every logged-in provider may make; answers JSON. */
const PROBE_ENDPOINT = '/documentManager/inboxManage';
const PROBE_BODY = 'method=isDocumentLinkedToDemographic&docId=0';

/**
 * POST through the page's own CarlosAjax and report what the browser sent.
 *
 * The request is matched by endpoint AND body so a page that happens to be
 * making its own inboxManage calls cannot be mistaken for the probe.
 */
async function probeXhrPost(page, label, timeout, contextPath) {
  const hiddenInput = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue().catch(() => null);
  const onTheWire = page.waitForRequest((request) => request.method() === 'POST'
    && request.url().includes(PROBE_ENDPOINT) && (request.postData() || '').includes(PROBE_BODY), { timeout });
  const outcome = await page.evaluate(({ endpoint, body, prefix }) => new Promise((resolve) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code; the arguments are module constants and the validated base URL's path
    if (typeof CarlosAjax === 'undefined' || typeof CarlosAjax.request !== 'function') {
      resolve({ error: 'CarlosAjax is not defined on this page' });
      return;
    }
    CarlosAjax.request(prefix + endpoint, {
      method: 'post',
      parameters: body,
      onComplete(transport) {
        resolve({ status: transport.status, body: String(transport.responseText || '').slice(0, 300) });
      },
    });
  }), { endpoint: PROBE_ENDPOINT, body: PROBE_BODY, prefix: contextPath });
  assert(!outcome.error, `${label}: ${outcome.error}`);

  const request = await onTheWire;
  const headers = await request.allHeaders();
  const response = await request.response();
  const status = response ? response.status() : outcome.status;
  const contentType = response ? (response.headers()['content-type'] || '') : '';
  return {
    hiddenInput, headers, status, contentType, body: outcome.body,
  };
}

function assertTokenCarried(label, result) {
  const token = (result.headers['csrf-token'] || '').trim();
  assert(token,
    `${label}: the POST carried no CSRF-TOKEN header. This page has ${result.hiddenInput === null ? 'no' : 'an empty'} `
    + 'hidden CSRF-TOKEN input, so the header is the only thing that can carry the token, and CSRFGuard\'s XHR '
    + 'interceptor did not add it');
  assert(/^XMLHttpRequest$/i.test(result.headers['x-requested-with'] || ''),
    `${label}: the POST was not marked X-Requested-With: XMLHttpRequest, so CSRFGuard would not validate it by header`);
  assert(result.status === 200,
    `${label}: the POST answered HTTP ${result.status}; a CSRF rejection is a 403 HTML error page`);
  // The action writes the JSON bytes without declaring a content type, so the
  // body is what is judged: it must parse, and carry the key the action sets.
  // A CSRF rejection is an HTML error page, which fails both.
  let parsed = null;
  try {
    parsed = JSON.parse(result.body);
  } catch (error) {
    parsed = null;
  }
  assert(parsed && Object.prototype.hasOwnProperty.call(parsed, 'isLinkedToDemographic'),
    `${label}: the POST answered ${JSON.stringify(result.contentType)} ${JSON.stringify(result.body.slice(0, 80))}, `
    + 'not the JSON the action writes -- an HTML error page in its place is the symptom finding 10 predicted');
  const carrier = result.hiddenInput === null ? 'no hidden input' : result.hiddenInput.trim() ? 'a populated hidden input' : 'an empty hidden input';
  console.log(`  ${label}: ${carrier} on the page; the POST carried the CSRF-TOKEN header and answered 200 JSON`);
}

/**
 * The chart's note-lock release is the one send on these pages that the header
 * cannot rescue: onClosing() in js/newCaseManagementView.js.jsp reads
 * CarlosAjax.getCsrfToken() and posts it with navigator.sendBeacon(), which
 * CSRFGuard's script does not intercept. The hidden input is its only carrier,
 * and the static audit accepts the chart's <form action="" method="post"> as
 * what populates it; this is where that is proved on the running page.
 */
function assertHiddenInputPopulated(label, result) {
  assert(result.hiddenInput !== null,
    `${label}: the page has no hidden CSRF-TOKEN input, and its pagehide beacon has no other way to carry the token`);
  assert(result.hiddenInput.trim(),
    `${label}: the hidden CSRF-TOKEN input is empty, so the pagehide beacon would post an empty token and the note lock `
    + 'would never be released');
}

/** Pending Docs, from the Inbox the schedule links to. */
async function openPendingDocuments(context, schedulePage, recorder, timeout) {
  const { page: inboxPage } = await clickOpensPopupOrNavigates(schedulePage, schedulePage.locator('#inboxLink').first(), {
    context, label: 'inbox', recorder, timeout,
  });
  const link = inboxPage.locator('a').filter({ hasText: /^\s*Pending Docs\s*$/i }).first();
  await link.waitFor({ state: 'attached', timeout }).catch(() => {});
  assert(await link.count() > 0, 'The Inbox offers no "Pending Docs" link, so the documents-in-queues page cannot be reached');
  return clickOpensPopup(inboxPage, link, {
    context, label: 'pending-documents', recorder, timeout, baseline: [],
  });
}

/** Row Display, from the chart's Labs menu (the "+" reveals the menu on hover). */
async function openLabRowDisplay(context, chartPage, recorder, timeout) {
  // The navbar (and its menus) are injected after the chart loads.
  const item = chartPage.locator('#menu2 a').filter({ hasText: /^\s*Row Display\s*$/i }).first();
  await item.waitFor({ state: 'attached', timeout }).catch(() => {});
  assert(await item.count() > 0, 'The chart\'s Labs menu offers no "Row Display" item, so the cumulative lab values page cannot be reached');
  if (!(await item.isVisible())) {
    await chartPage.locator('#menuTitle2 a').first().hover({ timeout });
    await item.waitFor({ state: 'visible', timeout });
  }
  return clickOpensPopup(chartPage, item, {
    context, label: 'lab-row-display', recorder, timeout, baseline: [],
  });
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.CSRF_XHR_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.CSRF_XHR_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.CSRF_XHR_TIMEOUT_MS || '20000');
  // The probe posts to the configured deployment, not to whatever the open
  // page's first path segment happens to be. Trailing slashes come off in a
  // loop rather than with `/\/+$/`, the pattern CodeQL flags as polynomial on
  // a value that arrives from the environment.
  let contextPath = config.baseUrl.pathname;
  while (contextPath.endsWith('/')) {
    contextPath = contextPath.slice(0, -1);
  }

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const results = {};
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);

    const pendingPage = await openPendingDocuments(context, schedulePage, recorder, timeout);
    try {
      results['pending-documents'] = await probeXhrPost(pendingPage, 'pending-documents', timeout, contextPath);
      assertTokenCarried('pending-documents', results['pending-documents']);
    } finally {
      await pendingPage.close().catch(() => {});
    }

    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChart(context, masterPage, recorder, timeout);
    results.echart = await probeXhrPost(chartPage, 'echart', timeout, contextPath);
    assertTokenCarried('echart', results.echart);
    assertHiddenInputPopulated('echart', results.echart);

    const labPage = await openLabRowDisplay(context, chartPage, recorder, timeout);
    try {
      results['lab-row-display'] = await probeXhrPost(labPage, 'lab-row-display', timeout, contextPath);
      assertTokenCarried('lab-row-display', results['lab-row-display']);
    } finally {
      await labPage.close().catch(() => {});
    }

    assertStrictPage(recorder, ['pending-documents', 'lab-row-display']);
    const probed = Object.keys(results);
    assert(probed.length >= 3, `only ${probed.length} page(s) were probed; the loop did not execute`);
    return { probed };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'csrf-xhr-token', run: main });
}

module.exports = {
  PROBE_BODY, PROBE_ENDPOINT, assertHiddenInputPopulated, assertTokenCarried, main, openLabRowDisplay,
  openPendingDocuments, probeXhrPost,
};
