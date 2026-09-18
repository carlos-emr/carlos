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
 * Browser regression check for the Add Prevention popup (#3732).
 *
 * AddPreventionData.jsp runs `disableifchecked(document.getElementById('neverWarn'),
 * 'nextDate')` from body onload, unconditionally. The never-warn checkbox and the
 * next-date field live inside the `prevHash != null` branch, so on a page where the
 * requested prevention type does not resolve, the JSP renders "prevention not found"
 * and that onload call receives null: the page threw "Cannot read properties of null
 * (reading 'checked')" before the user touched anything.
 *
 * WHICH PAGES HAVE NO CONTROLS. Only that not-found page. PreventionDisplayConfig keys
 * its map by the item's `name`, and every configured type renders the same form, so
 * "a prevention type that schedules no follow-up" is not a shape that exists — an
 * earlier revision of this check assumed it did and used `Flu`, which is a
 * displayName whose `name` is `Inf`, so it was silently exercising the not-found
 * branch while claiming otherwise. The unresolved case is therefore asserted as what
 * it is, and the check fails if the key ever starts resolving.
 *
 * The resolving case is driven through the checkbox's own event path with
 * check()/uncheck(), not by assigning `.checked` and calling the handler: a regression
 * in the markup wiring has to fail this check, and it would not if the handler were
 * invoked directly. `showHideNextDate` is exercised on both pages, because its null
 * guards are part of the same fix and nothing else would catch them regressing.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:prevention-add-data-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   PREVENTION_DEMOGRAPHIC_NO=1
 *   PREVENTION_UNRESOLVED=Flu       (a displayName, deliberately not a configured `name`)
 *   PREVENTION_RESOLVED=HPV         (a configured `name` in PreventionItems.xml)
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const {
  assert,
  buildFailureDetails,
  createRecorder,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  wirePage,
} = require('./lib/playwright-harness');

const config = readConfig();
const demographicNo = process.env.PREVENTION_DEMOGRAPHIC_NO || '1';
const unresolvedType = process.env.PREVENTION_UNRESOLVED || 'Flu';
const resolvedType = process.env.PREVENTION_RESOLVED || 'HPV';

const failures = [];

async function openPreventionPage(context, recorder, prevention) {
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
  return page;
}

/** Re-runs, in the page, the two calls whose null paths this fix added. Neither may throw. */
async function reRunOnloadHandlers(page, prevention) {
  const error = await page.evaluate(() => {
    try {
      // eslint-disable-next-line no-undef
      disableifchecked(document.getElementById('neverWarn'), 'nextDate');
      // eslint-disable-next-line no-undef
      showHideNextDate('nextDateDiv', 'nextDate', 'neverWarn');
      // Twice: the second call takes the other branch, which clears the field and the box.
      // eslint-disable-next-line no-undef
      showHideNextDate('nextDateDiv', 'nextDate', 'neverWarn');
      return null;
    } catch (thrown) {
      return thrown.message;
    }
  });
  if (error) {
    failures.push(`${prevention}: an onload handler threw — ${error}`);
  }
}

function recordPageErrors(recorder, prevention) {
  for (const entry of recorder.pageErrors.filter((e) => e.label === prevention)) {
    failures.push(`${prevention}: uncaught page error — ${entry.text}`);
  }
}

/*
 * The not-found page: no form, no controls, and the onload call receives null. This is
 * the page #3732 was reported on.
 */
async function checkUnresolvedType(context, recorder) {
  const page = await openPreventionPage(context, recorder, unresolvedType);
  if (!page) {
    return;
  }
  const controls = await page.locator('#neverWarn, #nextDate').count();
  if (controls !== 0) {
    failures.push(`${unresolvedType} now resolves to a configured prevention type and renders its controls; `
      + 'pick a PREVENTION_UNRESOLVED that is not a name in PreventionItems.xml so the null case is still covered');
  }
  if (await page.locator('form[action$="/prevention/AddPrevention"]').count() !== 0) {
    failures.push(`${unresolvedType} rendered the prevention form; it is meant to be the unresolved page`);
  }
  await reRunOnloadHandlers(page, unresolvedType);
  recordPageErrors(recorder, unresolvedType);
  await page.close();
}

/*
 * A configured type: the controls are there and must still work. Guarding the null case
 * must not have cost the behaviour being guarded — a `disableifchecked` that returned
 * early for everything would satisfy the page above and silently break this.
 */
async function checkResolvedType(context, recorder) {
  const page = await openPreventionPage(context, recorder, resolvedType);
  if (!page) {
    return;
  }
  const disclosure = page.locator('a[onclick*="showHideNextDate"]').first();
  const nextDateDiv = page.locator('#nextDateDiv');
  const neverWarn = page.locator('#neverWarn');
  const nextDate = page.locator('#nextDate');
  if (await disclosure.count() === 0 || await neverWarn.count() === 0 || await nextDate.count() === 0) {
    failures.push(`${resolvedType} no longer renders the next-date disclosure and its controls; `
      + 'pick a PREVENTION_RESOLVED that does, so the wired-up case is still covered');
    await page.close();
    return;
  }

  // Everything below goes through the controls the user actually operates, so a broken
  // onclick or onchange attribute fails here. Invoking the handlers directly would not.
  if (await nextDateDiv.isVisible()) {
    failures.push(`${resolvedType}: the next-date section starts expanded; it is meant to start hidden`);
  }
  await disclosure.click();
  if (!await nextDateDiv.isVisible()) {
    failures.push(`${resolvedType}: clicking the next-date legend did not reveal the section`);
    await page.close();
    return;
  }

  await neverWarn.check();
  if (!await nextDate.isDisabled()) {
    failures.push(`${resolvedType}: ticking neverWarn no longer disables nextDate — `
      + 'the null guard broke the behaviour it was meant to protect');
  }
  await neverWarn.uncheck();
  if (await nextDate.isDisabled()) {
    failures.push(`${resolvedType}: clearing neverWarn no longer re-enables nextDate`);
  }

  // Collapsing again is the handler's other branch: it hides the section, clears the date
  // and clears the checkbox. Those are the three lookups the null guards wrap.
  await nextDate.fill('2026-12-01');
  await neverWarn.check();
  await disclosure.click();
  if (await nextDateDiv.isVisible()) {
    failures.push(`${resolvedType}: clicking the legend again did not collapse the section`);
  }
  if (await nextDate.inputValue() !== '') {
    failures.push(`${resolvedType}: collapsing the section left a next date behind`);
  }
  if (await neverWarn.isChecked()) {
    failures.push(`${resolvedType}: collapsing the section left neverWarn ticked`);
  }

  recordPageErrors(recorder, resolvedType);
  await page.close();
}

(async () => {
  const recorder = createRecorder();
  let browser;
  try {
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    await checkUnresolvedType(context, recorder);
    await checkResolvedType(context, recorder);
    await context.close();

    assert(failures.length === 0, `add prevention popup checks failed:\n  - ${failures.join('\n  - ')}`);
    console.log(`PASS add prevention popup loads clean for ${unresolvedType} (unresolved, no controls) `
      + `and ${resolvedType} (configured, controls wired)`);
  } catch (error) {
    console.error('FAIL add prevention data Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
})();
