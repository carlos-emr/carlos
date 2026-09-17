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
 * Browser regression check for the consultation form opened by a provider who
 * has NO stored signature stamp: the form must fall back to the manual signature
 * pad, and it must do so with a clean console.
 *
 * WHY THIS CHECK EXISTS (issue #3665, finding 6; alpha-11 observation 6). The
 * form asks /provider/providerSignatureImage for the selected provider's stamp
 * whenever it cannot tell from the page whether one is stored. That request
 * used to answer 404 for a provider who signs by hand, which logged a console
 * error on every open, and the whole browser suite had to baseline the request
 * to keep running -- so it was blind to every other 4xx that URL might answer.
 * The contract is now that absence is a normal state: the action answers 204,
 * the <img> fires onerror, and the page shows the signature pad.
 *
 * WHAT IS ASSERTED. (1) The form did ask for the stamp. (2) The answer was 204,
 * not 404 and not 200 -- a 200 would mean the provider has a stamp and this
 * check is looking at the wrong scenario, so it tries the other providers the
 * form offers and skips if every one of them has a stamp. (3) The pad is shown
 * and the stamp box hidden, with newSignature=true so a submit would not persist
 * a stamp that does not exist. (4) Nothing else the browser reported, with NO
 * console baseline at all: the point is that the request no longer needs one.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart, the Consultations module's "+" control.
 *
 * READ-ONLY: it opens the form and reads it. Nothing is typed and nothing is
 * submitted, so no consultation request and no signature is written.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:consultation-signature-fallback-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CONSULT_FALLBACK_SEARCH=FAKE-           surname prefix used to reach a patient
 *   CONSULT_FALLBACK_DEMOGRAPHIC_NO=2       which patient's chart to open
 *   CONSULT_FALLBACK_TIMEOUT_MS=20000       per-step allowance
 *
 * IMPLEMENTS: docs/ui-tests/app-findings-log.md finding 6 (the live half).
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart } = require('./echart-navbar-modules-playwright-checks');

/** The Consultations module's "+" control in the chart navbar. */
const NEW_CONSULTATION_LINK = 'a[onclick*="ViewConsultationFormRequest"]';
const STAMP_REQUEST = /\/provider\/providerSignatureImage(\?|$)/;
/** How many other providers to try before concluding every one has a stamp. */
const MAX_PROVIDERS_TO_TRY = 6;

/** Every stamp request the form has made so far, in order. */
function stampRequests(recorder, label) {
  return recorder.requestLog.filter((entry) => entry.label === label && STAMP_REQUEST.test(entry.url));
}

/** Switch the form to another provider and wait for the stamp request that triggers. */
async function switchProviderAndProbe(formPage, recorder, label, value, timeout) {
  const before = stampRequests(recorder, label).length;
  await formPage.locator('#providerNoSelect').selectOption(value);
  await formPage.waitForResponse((response) => STAMP_REQUEST.test(response.url()), { timeout }).catch(() => null);
  const after = stampRequests(recorder, label);
  return after.length > before ? after[after.length - 1] : null;
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.CONSULT_FALLBACK_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.CONSULT_FALLBACK_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.CONSULT_FALLBACK_TIMEOUT_MS || '20000');
  const label = 'consultation-form';

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChart(context, masterPage, recorder, timeout);

    // The navbar is injected after the chart loads, so wait for the control
    // rather than counting it on a page that has not finished assembling.
    const link = chartPage.locator(NEW_CONSULTATION_LINK).first();
    await link.waitFor({ state: 'attached', timeout }).catch(() => {});
    assert(await link.count() > 0,
      'The chart\'s Consultations module offers no "+" control, so a clinician cannot start a consultation request from the chart');
    // NO BASELINE for the form. The suite-wide console baseline is what used to
    // hide this defect; this check must fail on the very request it tolerated.
    const formPage = await clickOpensPopup(chartPage, link, {
      context, label, recorder, timeout, baseline: [],
    });
    try {
      await formPage.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout });
      await formPage.waitForLoadState('networkidle', { timeout }).catch(() => {});

      let probes = stampRequests(recorder, label);
      if (probes.length === 0) {
        // The page only asks when it cannot tell from its own markup. Changing the
        // provider always asks, so re-select the current one to force the question.
        const current = await formPage.locator('#providerNoSelect').inputValue().catch(() => '');
        assert(current, 'the form never asked for a signature stamp and offers no provider control to make it ask');
        await switchProviderAndProbe(formPage, recorder, label, current, timeout);
        probes = stampRequests(recorder, label);
      }
      assert(probes.length > 0, 'the consultation form never requested /provider/providerSignatureImage, so this check cannot observe the fallback');

      let probe = probes[probes.length - 1];
      let providerNo = await formPage.locator('#signatureProviderNo').inputValue().catch(() => '(unknown)');
      if (probe.status === 200) {
        // This provider signs with a stored stamp. The fallback needs one who does
        // not, so walk the other providers the form offers until one answers 204.
        const values = await formPage.locator('#providerNoSelect option').evaluateAll(
          (options) => options.map((option) => option.value).filter((value) => /^\d+$/.test(value)),
        );
        for (const value of values.slice(0, MAX_PROVIDERS_TO_TRY)) {
          const next = await switchProviderAndProbe(formPage, recorder, label, value, timeout);
          if (next && next.status !== 200) {
            probe = next;
            providerNo = value;
            break;
          }
        }
        if (probe.status === 200) {
          throw new SkipCheck('every provider the consultation form offers has a stored signature stamp; this check needs '
            + 'one who signs by hand (leave consult_sig_<providerNo>.png absent for one provider)');
        }
      }

      assert(probe.status === 204,
        `provider ${providerNo} has no stored stamp and the form's stamp request answered HTTP ${probe.status}; `
        + 'the contract is 204 (absence is a normal state), and a 404 here is the console error finding 6 recorded');

      // The fallback itself: the pad is offered, the stamp box is hidden, and the
      // form knows the signature is not a stored one.
      const newSignature = await formPage.locator('#newSignature').inputValue();
      assert(newSignature === 'true',
        `after a 204 the form should mark the signature as not stored (newSignature=true); it holds ${JSON.stringify(newSignature)}`);
      assert(await formPage.locator('#signatureFrame').isVisible(),
        'after a 204 the manual signature pad (#signatureFrame) should be shown, and it is hidden');
      assert(!(await formPage.locator('#signatureShow').isVisible()),
        'after a 204 the stored-stamp box (#signatureShow) should be hidden, and it is still shown');

      assertStrictPage(recorder, [label]);
      console.log(`  provider ${providerNo} without a stamp: stamp request answered 204, signature pad shown, console clean`);
      return { providerNo, status: probe.status };
    } finally {
      await formPage.close().catch(() => {});
    }
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'consultation-signature-fallback', run: main });
}

module.exports = { MAX_PROVIDERS_TO_TRY, NEW_CONSULTATION_LINK, STAMP_REQUEST, main, stampRequests };
