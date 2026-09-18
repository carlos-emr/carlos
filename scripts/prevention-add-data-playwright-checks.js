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
 * Browser regression check for the Add Prevention popup.
 *
 * AddPreventionData.jsp runs `disableifchecked(document.getElementById('neverWarn'),
 * 'nextDate')` from body onload. The "never warn" checkbox and the "next date" field are
 * rendered only for prevention types that schedule a follow-up, so for every other type
 * that call passed null and the page threw "Cannot read properties of null (reading
 * 'checked')" before the user touched anything (#3732).
 *
 * The check loads one prevention type that renders neither control and one that renders
 * both, so it pins the fix without hiding a regression in the case that always worked:
 * a guard that simply returned early for everything would still pass the first page but
 * fail the assertion that the second page's field really is wired up.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:prevention-add-data-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   PREVENTION_DEMOGRAPHIC_NO=1
 *   PREVENTION_WITHOUT_NEXTDATE=Flu
 *   PREVENTION_WITH_NEXTDATE=HPV
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  validateBaseUrl,
  wirePage,
  } = require('./lib/playwright-harness');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const demographicNo = process.env.PREVENTION_DEMOGRAPHIC_NO || '1';
const typeWithoutNextDate = process.env.PREVENTION_WITHOUT_NEXTDATE || 'Flu';
const typeWithNextDate = process.env.PREVENTION_WITH_NEXTDATE || 'HPV';

const failures = [];

async function inspectPreventionPage(context, recorder, prevention) {
  const page = await context.newPage();
  wirePage(page, prevention, recorder);

  const query = new URLSearchParams({ demographic_no: demographicNo, prevention });
  const response = await gotoApp(
    page, config.baseUrl, `/prevention/ViewAddPreventionData?${query.toString()}`, 'networkidle',
  );
  if (!response || response.status() !== 200) {
    failures.push(`${prevention}: page returned ${response ? response.status() : 'no response'}`);
    await page.close();
    return null;
  }

  const state = await page.evaluate(() => {
    const result = {
      neverWarnPresent: !!document.getElementById('neverWarn'),
      nextDatePresent: !!document.getElementById('nextDate'),
      reRunError: null,
      // null when the page renders no never-warn control; otherwise whether ticking the box
      // really disables the next-date field, and clearing it re-enables it.
      togglesNextDate: null,
    };

    // Re-run the exact call body onload makes; it must not throw either way.
    try {
      // eslint-disable-next-line no-undef
      disableifchecked(document.getElementById('neverWarn'), 'nextDate');
    } catch (error) {
      result.reRunError = error.message;
      return result;
    }

    // Guarding the null case must not have cost the behaviour it guards. Without this, an
    // unconditional no-op would satisfy every other assertion here while silently breaking
    // the workflow: "never remind" would stop disabling the date field.
    const neverWarn = document.getElementById('neverWarn');
    const nextDate = document.getElementById('nextDate');
    if (neverWarn && nextDate) {
      const originalChecked = neverWarn.checked;
      try {
        neverWarn.checked = true;
        // eslint-disable-next-line no-undef
        disableifchecked(neverWarn, 'nextDate');
        const disabledWhenChecked = nextDate.disabled === true;

        neverWarn.checked = false;
        // eslint-disable-next-line no-undef
        disableifchecked(neverWarn, 'nextDate');
        const enabledWhenCleared = nextDate.disabled === false;

        result.togglesNextDate = disabledWhenChecked && enabledWhenCleared;
      } catch (error) {
        result.reRunError = error.message;
      } finally {
        neverWarn.checked = originalChecked;
      }
    }

    return result;
  });

  for (const entry of recorder.pageErrors.filter((e) => e.label === prevention)) {
    failures.push(`${prevention}: uncaught page error — ${entry.text}`);
  }
  if (state.reRunError) {
    failures.push(`${prevention}: disableifchecked threw — ${state.reRunError}`);
  }
  await page.close();
  return state;
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    const without = await inspectPreventionPage(context, recorder, typeWithoutNextDate);
    const withNextDate = await inspectPreventionPage(context, recorder, typeWithNextDate);

    // Guard the fixtures: if the chosen types ever stop representing the two shapes, the check
    // would pass without exercising the bug.
    if (without && without.neverWarnPresent) {
      failures.push(`${typeWithoutNextDate} now renders a neverWarn control; pick another PREVENTION_WITHOUT_NEXTDATE so the null case is still covered`);
    }
    if (withNextDate && !withNextDate.nextDatePresent) {
      failures.push(`${typeWithNextDate} no longer renders a nextDate field; pick another PREVENTION_WITH_NEXTDATE so the wired-up case is still covered`);
    }
    if (withNextDate && withNextDate.togglesNextDate === false) {
      failures.push(`${typeWithNextDate}: ticking neverWarn no longer disables nextDate — the null guard broke the behaviour it was meant to protect`);
    }
    if (withNextDate && withNextDate.nextDatePresent && withNextDate.togglesNextDate === null) {
      failures.push(`${typeWithNextDate}: could not exercise the neverWarn toggle, so a no-op disableifchecked would go undetected`);
    }

    await context.close();

    assert(failures.length === 0, `add prevention popup checks failed:\n  - ${failures.join('\n  - ')}`);
    console.log(`PASS add prevention popup loads clean for ${typeWithoutNextDate} (no next-date controls) and ${typeWithNextDate} (with them)`);
  } catch (error) {
    console.error('FAIL add prevention data Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
