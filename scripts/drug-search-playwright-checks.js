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
 * Browser regression check for the Rx drug picker, driven the way a prescriber
 * uses it: log in, open the patient's Rx module, type into the search box, and
 * wait for the autocomplete list.
 *
 * Nothing in the suite covered this surface, which is how a tester reached
 * 2026.08.0-alpha9 reporting that drug search "posts but nothing returns", with
 * a 502 from nginx and a permanent "Drugref database is unavailable. Contact
 * support." banner beside the picker. Three things had to be true at once for
 * that to be invisible, and this check pins all three:
 *
 *   1. The search POST must not 502/500. DrugRef runs as a second webapp inside
 *      the SAME Tomcat JVM, so nginx can only answer 502 if that JVM stopped
 *      responding -- an OOM exit under -XX:+ExitOnOutOfMemoryError, or a
 *      keepalive socket Tomcat closed first. Either way it is a real outage,
 *      not a slow query (a slow one is a 504 at proxy_read_timeout 300s).
 *   2. Results must actually arrive. The picker's $.ajax had no error callback,
 *      so any non-JSON reply was a silent no-op and an empty list was
 *      indistinguishable from a failure.
 *   3. The DrugRef status banner must stay clear. TopLinks2.jspf renders
 *      "Drugref database is unavailable" from a status probe that used to
 *      require _rx WRITE and mis-tested a null lastUpdate, so it could show for
 *      a perfectly healthy DrugRef.
 *
 * RUN THROUGH :443. The 502 in the report came from nginx; against bare Tomcat
 * that hop does not exist and assertion 1 covers nothing. Warns on stdout when
 * BASE_URL is not HTTPS.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN
 * Optional: PRESCRIPTION_DEMOGRAPHIC_NO (default 1), DRUG_SEARCH_TERM
 *   (default "amox" -- a common ingredient the seeded DrugRef answers),
 *   CHROME_PATH, DRUG_SEARCH_SCREENSHOT_DIR (default /tmp).
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
  screenshotDir: process.env.DRUG_SEARCH_SCREENSHOT_DIR || '/tmp',
};
const demographicNo = process.env.PRESCRIPTION_DEMOGRAPHIC_NO || '1';
const searchTerm = process.env.DRUG_SEARCH_TERM || 'amox';
assert(/^\d+$/.test(demographicNo), `PRESCRIPTION_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
// minLength on the picker is 3; a shorter term never fires a request at all and
// the check would pass while testing nothing.
assert(searchTerm.length >= 3, `DRUG_SEARCH_TERM must be at least 3 characters, got "${searchTerm}"`);

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    if (config.baseUrl.protocol !== 'https:') {
      console.log(
        '[warn] BASE_URL is not HTTPS, so this run does NOT go through nginx; the 502 this '
        + 'check exists to catch cannot occur on this invocation.',
      );
    }

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const landing = await login(context, config, recorder);
    await landing.close();

    const rxPage = await context.newPage();
    wirePage(rxPage, 'drug-search', recorder);
    await gotoApp(rxPage, config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
    await rxPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(rxPage, 'rx module');

    // The DrugRef status probe fires on DOMContentLoaded, so it has already
    // run by now. Its banner must be clear before the search is judged: a
    // "Contact support." here means the probe itself failed (a non-JSON reply,
    // or the _rx write gate rejecting a read-only prescriber), which is a
    // different defect from the search failing.
    // Assert the panel EXISTS before reading it. Swallowing the lookup with
    // .catch(() => '') makes "" pass the !/unavailable/ test, so a regression
    // that stops rendering #statusDisplay entirely -- or renames it -- would
    // satisfy this check vacuously, which is exactly the shape of hollow
    // assertion that let the original six defects ship green.
    await rxPage.locator('#statusDisplay').waitFor({ state: 'attached', timeout: 20000 });
    const statusBefore = (await rxPage.locator('#statusDisplay').innerText()).trim();
    assert(
      !/unavailable/i.test(statusBefore),
      `The DrugRef status banner already reads "${statusBefore}" before any search was made. `
        + 'The status probe (rx/updateDrugrefDB, method=verify) is failing or being rejected.',
    );

    const searchBox = rxPage.locator('#searchString');
    await searchBox.waitFor({ state: 'visible', timeout: 20000 });

    // Type it like a prescriber, one key at a time: the widget debounces at
    // 400ms and only fires at minLength 3, so a fill() can skip the request
    // entirely and the assertions below would pass against a picker that never
    // called the server.
    const [searchResponse] = await Promise.all([
      rxPage.waitForResponse(
        (r) => r.url().includes('/rx/searchDrug') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      searchBox.pressSequentially(searchTerm, { delay: 120 }),
    ]);

    const status = searchResponse.status();
    assert(
      status !== 502,
      'Drug search returned 502 from nginx. DrugRef shares the Tomcat JVM, so this means that '
        + 'JVM stopped answering: check journalctl -u carlos-emr for OutOfMemoryError and '
        + '/var/lib/carlos-emr/heapdumps, and /var/log/nginx/error.log for "Connection refused" '
        + 'or "reset by peer" (a slow query would be 504, not 502).',
    );
    assert(status < 400, `Drug search POST returned HTTP ${status}`);

    const contentType = (searchResponse.headers()['content-type'] || '').toLowerCase();
    assert(
      contentType.includes('json'),
      `Drug search answered "${contentType}", not JSON. The picker parses it as JSON, so anything `
        + 'else is discarded silently and the list simply never opens.',
    );

    const payload = await searchResponse.json();
    assert(Array.isArray(payload.results), 'Drug search JSON has no results array');
    assert(
      payload.results.length > 0,
      `Drug search for "${searchTerm}" returned zero results. Either DrugRef has no data loaded `
        + '(carlos-ctl check probes a live lookup) or the query is not reaching it.',
    );

    // The list must actually render, not just the response arrive.
    await rxPage.locator('ul.ui-autocomplete li').first().waitFor({ state: 'visible', timeout: 20000 });

    // And the failure banner must still be clear afterwards: the new error
    // handler writes into #statusDisplay, so a regression that fails the search
    // shows up here even if the assertions above were somehow satisfied.
    const statusAfter = (await rxPage.locator('#statusDisplay').innerText()).trim();
    assert(
      !/unavailable/i.test(statusAfter),
      `After a successful search the DrugRef banner reads "${statusAfter}".`,
    );

    // The DrugRef name/version/date spans live INSIDE #statusDisplay. The error
    // handler used to replace that container's innerHTML, deleting all three
    // permanently, so assert they survive: a regression that reintroduces the
    // destructive write leaves the banner text clear and would pass the two
    // assertions above.
    for (const id of ['drugDatabase', 'drugDatabaseVersion', 'dbDateTime']) {
      assert(
        (await rxPage.locator(`#statusDisplay #${id}`).count()) === 1,
        `#${id} is gone from the DrugRef status panel after a search. Something replaced `
          + "#statusDisplay's innerHTML instead of writing to a child node.",
      );
    }

    await screenshot(rxPage, config.screenshotDir, 'drug-search-results');
    await rxPage.close();

    assertNoPageErrors(recorder);

    await context.close();

    console.log(
      `PASS drug search: "${searchTerm}" returned ${payload.results.length} result(s) over `
      + `HTTP ${status}, list rendered, DrugRef banner clear`,
    );
  } catch (error) {
    console.error('FAIL drug search Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
