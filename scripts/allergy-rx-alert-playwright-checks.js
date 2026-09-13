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
 * Browser regression check for the two halves of the allergy workflow that an
 * alpha tester reported broken on 2026.08: recording a drug allergy, and being
 * warned about it when prescribing.
 *
 *   1. RECORDING. Searching the allergy picker on /rx/showAllergy and clicking a
 *      result did nothing, with "Uncaught ReferenceError: submitAddReaction is
 *      not defined" and "can't access property 'indexOf', param is undefined" in
 *      the console. ShowAllergies2.jsp AJAX-loads ChooseAllergy2.jsp and lifts
 *      only its #searchResultsContainer out of the response, so that page's
 *      script block -- which defines submitAddReaction -- never arrives, while
 *      the parent's delegated handler parsed a query string off an href that is
 *      now "javascript:void(0)". Neither defect is visible server-side: the
 *      search POST is a clean 200 and the page looks fine until a result is
 *      clicked, which is how it shipped.
 *
 *   2. WARNING. Prescribing Amoxil to a patient recorded as allergic to
 *      PENICILLINS produced no alert. DrugRef's get_allergy_warnings had no
 *      branch for allergy typeCode 0 -- the free-text category CARLOS writes for
 *      every "Custom Allergy" and the one the demonstration dataset seeds its
 *      penicillin allergies with -- so those allergies were silently never
 *      checked. This is the dangerous failure direction: no alert, no error, and
 *      an empty {"results":[]} that is indistinguishable from "no allergy".
 *
 * The check runs in three parts and every assertion is positive and specific.
 * Asserting merely that no console error appears would pass against a page whose
 * search returns nothing, and asserting only that the allergy JSON is well-formed
 * would pass against the empty result that IS the second bug.
 *
 *   Part 1 records an allergen picked out of the search results (a TYPED allergy)
 *          and requires the reaction dialogue to render and the allergy to persist.
 *   Part 2 records a second allergen through "Custom Allergy" (a FREE-TEXT,
 *          typeCode 0 allergy) with its own reaction text as a marker.
 *   Part 3 prescribes a drug in the free-text allergen's class and requires both
 *          the probe JSON to carry that marker and the alert table to be visible.
 *   Part 4 prescribes a drug in the TYPED allergen's class -- the scenario as the
 *          tester reported it -- and requires that marker to come back too.
 *
 * Each recording writes its own reaction text and every assertion keys on that
 * marker rather than the allergen name: the demonstration dataset already seeds
 * PENICILLINS allergies, so a name match would be satisfied by a pre-existing row
 * even when nothing this run did actually saved or alerted.
 *
 * The two allergens are deliberately unrelated classes -- penicillins for part 1,
 * macrolides for part 2 -- so the part 3 drug cannot be matched by the typed
 * allergy. Without that separation a regression in the free-text branch would be
 * masked by the typed allergy part 1 just added, and this check would pass
 * against the exact build the tester reported.
 *
 * The alert path runs through DrugRef, which is a second webapp in the same
 * Tomcat. A failure here can therefore mean the DrugRef pin is older than the
 * free-text allergy fix (debian/drugref.pin), not that CARLOS regressed.
 *
 * RUN THROUGH :443, on a loopback BASE_URL. Certificate verification is relaxed
 * only for loopback, where the packaged install serves its own self-signed cert;
 * a non-local target opted in with ALLOW_NON_LOCAL_BASE_URL must present a
 * trusted certificate, because this check submits credentials to it. Warns on
 * stdout when BASE_URL is not HTTPS: against bare Tomcat the nginx and
 * ModSecurity/CRS hop does not exist, and this check posts free clinical text
 * (the reaction description) on two different forms -- exactly the shape that
 * CRS has blocked before on other surfaces.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN
 * Optional: ALLERGY_DEMOGRAPHIC_NO (default 2), ALLERGY_SEARCH_TERM (default
 *   "amoxicillin"), ALLERGY_ALLERGEN (default "AMOXICILLIN"),
 *   ALLERGY_CUSTOM_ALLERGEN (default "CLARITHROMYCIN"),
 *   ALLERGY_EXPECT_UNCHECKED=true with an unknown custom allergen to require
 *   an explicit unresolved-check JSON result and visible Not checked notice,
 *   ALLERGY_OTHER_DEMOGRAPHIC_NO (default 1, or 2 when testing patient 1) for
 *   a read-only second prescribing tab that must not change the first tab's check,
 *   ALLERGY_DRUG_TERM (default
 *   "biaxin", a macrolide -- the free-text allergen's class),
 *   ALLERGY_TYPED_DRUG_TERM (default "amoxil", a penicillin -- the typed
 *   allergen's class), CHROME_PATH, ALLERGY_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { randomUUID } = require('node:crypto');
const { createGracefulSignalCancellation } = require('./graceful-signal-cancellation');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
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
  screenshotDir: process.env.ALLERGY_SCREENSHOT_DIR || '/tmp',
};
const demographicNo = process.env.ALLERGY_DEMOGRAPHIC_NO || '2';
const otherDemographicNo = process.env.ALLERGY_OTHER_DEMOGRAPHIC_NO || (demographicNo === '1' ? '2' : '1');
// Use a typed name present in both the demo and current DPD reference. Legacy
// AHFS parent names such as PENICILLINS can disappear after a DPD refresh;
// those need an explicit unresolved-check notice, not a fabricated match.
const allergySearchTerm = process.env.ALLERGY_SEARCH_TERM || 'amoxicillin';
const allergenName = process.env.ALLERGY_ALLERGEN || 'AMOXICILLIN';

// Part 2 deliberately uses an unrelated drug from part 1, recorded as a
// free-text custom allergy, and prescribes a brand of that drug. CLARITHROMYCIN
// remains in both the demo reference and the current DPD extract; MACROLIDES
// disappears from the newer extract and belongs in the unresolved-check case. Keeping the two
// classes disjoint is what makes the alert assertion specific: a penicillin
// allergy cannot warn on a macrolide, so the only warning clarithromycin can
// produce here is the free-text one, and the assertion cannot be satisfied by
// the typed allergy part 1 just added.
const customAllergen = process.env.ALLERGY_CUSTOM_ALLERGEN || 'CLARITHROMYCIN';
const expectUnchecked = process.env.ALLERGY_EXPECT_UNCHECKED === 'true';
const drugTerm = process.env.ALLERGY_DRUG_TERM || 'biaxin';
// The originally reported scenario: a penicillin prescribed to a penicillin-allergic
// patient. Part 4 drives it against the TYPED allergy so both recording paths are
// shown to alert, not just the free-text one.
const typedDrugTerm = process.env.ALLERGY_TYPED_DRUG_TERM || 'amoxil';
// A per-run marker prevents a prior interrupted run from satisfying this run's persistence and
// alert assertions. The finally block deactivates only rows carrying these exact markers.
const runMarker = randomUUID();
const typedReaction = `Rash typed check ${runMarker}`;
const freeTextReaction = `Rash free-text check ${runMarker}`;
const attemptedReactionMarkers = [];

assert(/^\d+$/.test(demographicNo), `ALLERGY_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
assert(/^\d+$/.test(otherDemographicNo) && Number(otherDemographicNo) !== Number(demographicNo),
  'ALLERGY_OTHER_DEMOGRAPHIC_NO must identify a different numeric patient');
// The drug picker debounces and only fires at minLength 3; a shorter term never
// reaches the server and every assertion below would be vacuous.
assert(drugTerm.length >= 3, `ALLERGY_DRUG_TERM must be at least 3 characters, got "${drugTerm}"`);
assert(typedDrugTerm.length >= 3, `ALLERGY_TYPED_DRUG_TERM must be at least 3 characters, got "${typedDrugTerm}"`);
// The custom-allergy box is maxlength 16, and the name has to be one the drug
// reference knows for the free-text branch to have anything to resolve.
assert(customAllergen.length <= 16, `ALLERGY_CUSTOM_ALLERGEN must be at most 16 characters, got "${customAllergen}"`);

async function deactivateCreatedAllergies(context) {
  const cleanupPage = await context.newPage();
  try {
    await gotoApp(cleanupPage, config.baseUrl, `/rx/showAllergy?demographicNo=${demographicNo}`);
    await cleanupPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(cleanupPage, 'allergy cleanup profile');
    await cleanupPage.locator('form#searchAllergy2').waitFor({ state: 'attached', timeout: 20000 });
    await cleanupPage.locator('table.allergy_table').waitFor({ state: 'attached', timeout: 20000 });

    for (const marker of attemptedReactionMarkers) {
      // Archive every active exact match. There should normally be one, but if a retry produced a
      // duplicate then abandoning cleanup on count != 1 would leave both test rows behind.
      while (true) {
        const allergyRows = cleanupPage.locator("tr[id^='allergy_']");
        let matchingActiveIndex = -1;
        for (let index = 0; index < await allergyRows.count(); index += 1) {
          const row = allergyRows.nth(index);
          const reaction = ((await row.locator('td').nth(8).innerText()) || '').trim();
          if (reaction === marker && await row.locator('a.deleteAllergyLink').count() === 1) {
            matchingActiveIndex = index;
            break;
          }
        }
        if (matchingActiveIndex < 0) {
          break;
        }
        const matchingRow = allergyRows.nth(matchingActiveIndex);
        const matchingRowId = await matchingRow.getAttribute('id');
        assert(/^allergy_\d+$/.test(matchingRowId || ''), 'Created allergy row has no numeric record id');
        const inactivateLink = matchingRow.locator('a.deleteAllergyLink');
        const expectedDialog = cleanupPage.waitForEvent('dialog', { timeout: 30000 }).then(async (dialog) => {
          if (dialog.type() !== 'confirm' || dialog.message() !== 'Inactivate this Allergy?') {
            await dialog.dismiss().catch(() => {});
            throw new Error(`Allergy cleanup opened an unexpected ${dialog.type()} dialog`);
          }
          await dialog.accept();
        });
        const [deleteResponse] = await Promise.all([
          cleanupPage.waitForResponse(
            (response) => response.url().includes('/rx/deleteAllergy2')
              && response.request().method() === 'POST',
            { timeout: 30000 },
          ),
          expectedDialog,
          cleanupPage.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
          inactivateLink.click(),
        ]);
        assert(deleteResponse.status() === 200,
          `Inactivating created allergy returned HTTP ${deleteResponse.status()} instead of 200`);
        await cleanupPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
        await assertNotErrorPage(cleanupPage, 'allergy cleanup profile after inactivation');
        await cleanupPage.locator('form#searchAllergy2').waitFor({ state: 'attached', timeout: 20000 });
        await cleanupPage.locator('table.allergy_table').waitFor({ state: 'attached', timeout: 20000 });
        const reloadedRow = cleanupPage.locator(`#${matchingRowId}`);
        if (await reloadedRow.count() === 1) {
          const reloadedReaction = ((await reloadedRow.locator('td').nth(8).innerText()) || '').trim();
          assert(reloadedReaction !== marker || await reloadedRow.locator('a.deleteAllergyLink').count() === 0,
            `Created allergy ${marker} remained active after cleanup`);
        }
      }
    }
  } finally {
    await cleanupPage.close();
  }
}

async function recordAllergy(page, marker, cancellation) {
  await cancellation.run(async () => {
    attemptedReactionMarkers.push(marker);
    // Wait for every initiated operation, even if one fails. In particular, a
    // signal must not start cleanup while the form submission is still pending.
    const results = await Promise.allSettled([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
      page.locator('#RxAddAllergyForm input[value="Add Allergy"]').click(),
    ]);
    const failure = results.find((result) => result.status === 'rejected');
    if (failure) throw failure.reason;
    const response = results[0].value;
    assert(response && response.ok(), `Adding allergy returned HTTP ${response?.status()}`);
  });
}

(async () => {
  const recorder = createRecorder();
  const cancellation = createGracefulSignalCancellation();
  let browser;
  let context;
  try {
    browser = await chromium.launch({
      ...getLaunchOptions(config.chromePath),
      // Keep Chromium available to finally; Playwright otherwise closes it on
      // these signals before the script can archive its generated allergies.
      handleSIGINT: false,
      handleSIGTERM: false,
    });
    cancellation.throwIfCancelled();
    if (config.baseUrl.protocol !== 'https:') {
      console.log(
        '[warn] BASE_URL is not HTTPS, so this run does NOT go through nginx and '
        + 'ModSecurity/CRS. The allergy reaction text this check posts is free clinical '
        + 'prose, which is the shape CRS has blocked on other surfaces; that class of '
        + 'failure cannot occur on this invocation.',
      );
    }

    // Certificate verification is only relaxed for loopback, where the packaged
    // install serves its own self-signed cert. A non-local target opted in via
    // ALLOW_NON_LOCAL_BASE_URL must still prove its certificate, because this
    // script submits clinician credentials to it.
    const loopback = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);
    const host = config.baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase();
    context = await browser.newContext({
      ignoreHTTPSErrors: loopback.has(host),
      viewport: { width: 1440, height: 1000 },
    });
    context.setDefaultTimeout(30000);
    cancellation.throwIfCancelled();
    const landing = await login(context, config, recorder);
    await landing.close();
    cancellation.throwIfCancelled();

    // ---------------------------------------------------------------- part 1
    const allergyPage = await context.newPage();
    let customConfirmMode = null;
    const customConfirmMessage = `Adding custom allergy: ${customAllergen.toUpperCase()}`;
    wirePage(allergyPage, 'allergy', recorder, async (dialog, entry) => {
      if (customConfirmMode && dialog.type() === 'confirm' && dialog.message() === customConfirmMessage) {
        const mode = customConfirmMode;
        customConfirmMode = null;
        if (mode === 'accept') {
          await dialog.accept();
        } else {
          await dialog.dismiss();
        }
        return;
      }
      recorder.dialogs.push(entry);
      await dialog.dismiss().catch(() => {});
    });
    await gotoApp(allergyPage, config.baseUrl, `/rx/showAllergy?demographicNo=${demographicNo}`);
    await allergyPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(allergyPage, 'allergy profile');

    await allergyPage.locator('#searchString').fill(allergySearchTerm);
    const [searchResponse] = await Promise.all([
      allergyPage.waitForResponse(
        (r) => r.url().includes('/rx/searchAllergy') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      allergyPage.locator('#searchStringButton').click(),
    ]);
    assert(searchResponse.status() < 400, `Allergy search POST returned HTTP ${searchResponse.status()}`);

    // Both layouts ChooseAllergy2.jsp can render put the drugref id/type/description on
    // the result anchor itself; only the hierarchical one wraps them in a _content div.
    // Selecting on data-id covers allergies.flat_results=true as well, and skips the
    // section toggles, which carry no data-id.
    const results = allergyPage.locator('#searchResultsContainer a[data-id]');
    await results.first().waitFor({ state: 'visible', timeout: 20000 });
    assert(
      (await results.count()) > 0,
      `Allergy search for "${allergySearchTerm}" rendered no clickable results, so the click that `
        + 'this check exists to exercise never happens.',
    );

    // Require the configured pairing. A fallback allergen cannot establish
    // whether this run's prescribed drug triggered the intended typed allergy.
    const resultNames = (await results.allTextContents()).map((text) => text.trim());
    const exactIndex = resultNames.indexOf(allergenName);
    assert(exactIndex >= 0, `Allergy search did not return the configured allergen ${allergenName}`);
    const matchedRequestedAllergen = true;
    const chosen = results.nth(exactIndex);
    const chosenName = ((await chosen.textContent()) || '').trim();
    assert(chosenName.length > 0, 'Search result anchor has no text');

    cancellation.throwIfCancelled();
    const [reactionResponse] = await Promise.all([
      allergyPage.waitForResponse(
        (r) => r.url().includes('/rx/addReaction') && r.request().method() === 'POST',
        { timeout: 30000 },
      ),
      chosen.click(),
    ]);
    assert(
      reactionResponse.status() < 400,
      `Clicking allergy result "${chosenName}" got HTTP ${reactionResponse.status()} from addReaction2`,
    );

    // The dialogue arriving is the real assertion: before the fix the click threw
    // before any request was made, so nothing rendered here at all.
    const reactionForm = allergyPage.locator('#RxAddAllergyForm');
    await reactionForm.waitFor({ state: 'visible', timeout: 20000 });
    await allergyPage.locator('#reactionDescription').fill(typedReaction);
    await recordAllergy(allergyPage, typedReaction, cancellation);
    await allergyPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(allergyPage, 'allergy profile after add');

    // Assert on the reaction text this run wrote, not the allergen name: the demonstration
    // dataset already seeds PENICILLINS allergies, so a name match would be satisfied by a
    // pre-existing row even if this recording never saved.
    const listText = (await allergyPage.locator('body').innerText());
    assert(
      listText.includes(typedReaction),
      `The allergy recorded from the search results did not persist: the list does not carry this `
        + `run's reaction marker. The reaction dialogue rendered, but "Add Allergy" did not save.`,
    );
    await screenshot(allergyPage, config.screenshotDir, 'allergy-recorded');

    // ------------------------------------------------- part 2, the free-text allergy
    // "Custom Allergy" is how a clinician records an allergen they did not pick out
    // of the reference. It posts ID=0&type=0, the category DrugRef used to skip.
    cancellation.throwIfCancelled();
    await allergyPage.locator('#searchString').fill(customAllergen);
    customConfirmMode = 'dismiss';
    const cancelledRequest = allergyPage.waitForRequest(
      (request) => request.url().includes('/rx/addReaction') && request.method() === 'POST',
      { timeout: 1000 },
    ).then(() => true, () => false);
    await allergyPage.locator('input[value="Custom Allergy"]').click();
    assert(customConfirmMode === null, 'The Custom Allergy control did not open its confirmation');
    assert(!(await cancelledRequest), 'Cancelling Custom Allergy still submitted addReaction2');

    cancellation.throwIfCancelled();
    customConfirmMode = 'accept';
    const [customReactionResponse] = await Promise.all([
      allergyPage.waitForResponse(
        (r) => r.url().includes('/rx/addReaction') && r.request().method() === 'POST',
        { timeout: 30000 },
      ),
      allergyPage.locator('input[value="Custom Allergy"]').click(),
    ]);
    assert(customConfirmMode === null, 'The Custom Allergy confirmation was not accepted');
    assert(
      customReactionResponse.status() < 400,
      `Adding custom allergy "${customAllergen}" got HTTP ${customReactionResponse.status()}`,
    );

    const customForm = allergyPage.locator('#RxAddAllergyForm');
    await customForm.waitFor({ state: 'visible', timeout: 20000 });
    await allergyPage.locator('#reactionDescription').fill(freeTextReaction);
    // A free-text allergy renders the non-drug selector, and the form's own
    // doSubmit() refuses to submit while it is unset.
    await allergyPage.locator('#nonDrug').selectOption('off');
    await recordAllergy(allergyPage, freeTextReaction, cancellation);
    await allergyPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(allergyPage, 'allergy profile after custom add');

    const listWithCustom = (await allergyPage.locator('body').innerText());
    assert(
      listWithCustom.includes(freeTextReaction),
      `The custom allergy "${customAllergen}" did not persist: the list does not carry this run's `
        + 'free-text reaction marker.',
    );
    await allergyPage.close();

    // ---------------------------------------------------------------- part 3
    cancellation.throwIfCancelled();
    const rxPage = await context.newPage();
    wirePage(rxPage, 'rx-alert', recorder);
    await gotoApp(rxPage, config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
    await rxPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(rxPage, 'rx module');

    const searchBox = rxPage.locator('#searchString');
    await searchBox.waitFor({ state: 'visible', timeout: 20000 });
    // One key at a time: the picker debounces at 400ms and a fill() can skip the
    // request entirely.
    await Promise.all([
      rxPage.waitForResponse(
        (r) => r.url().includes('/rx/searchDrug') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      searchBox.pressSequentially(drugTerm, { delay: 120 }),
    ]);

    const drugItems = rxPage.locator('ul.ui-autocomplete li');
    await drugItems.first().waitFor({ state: 'visible', timeout: 20000 });

    // The allergy probe is a POST to /rx/showAllergy?method=allergyData fired by
    // prescribe.jsp as the drug lands in the stash. Wait on the response so the
    // assertion below is not racing the fetch.
    cancellation.throwIfCancelled();
    const [allergyDataResponse] = await Promise.all([
      rxPage.waitForResponse(
        (r) => r.url().includes('/rx/showAllergy') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      drugItems.first().click(),
    ]);
    assert(
      allergyDataResponse.status() < 400,
      `The allergy probe returned HTTP ${allergyDataResponse.status()}`,
    );

    const payload = await allergyDataResponse.json();
    assert(Array.isArray(payload.results), 'The allergy probe JSON has no results array');
    if (expectUnchecked) {
      assert(payload.checkComplete === false && payload.checkFailed !== true,
        'unresolved allergy must be an incomplete reference check, not a transport failure');
      assert(Array.isArray(payload.unchecked) && payload.unchecked.some((item) => item.reaction === freeTextReaction),
        'unresolved free-text allergy disappeared from the response');
      assert(!payload.results.some((item) => item.reaction === freeTextReaction),
        'unresolved allergy was incorrectly reported as a confirmed match');
    } else {
      assert(payload.results.some((item) => item.reaction === freeTextReaction),
        `Prescribing "${drugTerm}" did not return a confirmed match for this run's free-text ${customAllergen} allergy`);
    }

    const alertTable = rxPage.locator("table[id^='alleg_tbl_']").first();
    await alertTable.waitFor({ state: 'visible', timeout: 20000 });
    await alertTable.getByText(freeTextReaction, {exact:false}).waitFor({state:'visible'});
    const alertText = (await alertTable.innerText()).trim();
    if (expectUnchecked) assert(alertText.includes('Not checked:'), 'unresolved allergy is not visibly distinguished from a confirmed match');
    assert(
      /allergy/i.test(alertText) && alertText.toUpperCase().includes(customAllergen.toUpperCase()),
      `The allergy alert row is visible but does not name ${customAllergen}. Its text is the `
        + "patient's own recorded reaction, so it is not reproduced here; see the screenshot.",
    );

    // The prescribing session is shared across tabs. A later patient's window
    // must not redirect an already-open page's allergy checks to that patient.
    const originalCheck = new URLSearchParams(allergyDataResponse.request().postData() || '');
    assert(originalCheck.get('demographicNo') === demographicNo, 'the allergy request is not bound to its page patient');
    const otherRxPage = await context.newPage();
    wirePage(otherRxPage, 'rx-other-patient', recorder);
    await gotoApp(otherRxPage, config.baseUrl, `/rx/choosePatient?demographicNo=${otherDemographicNo}`);
    await otherRxPage.locator('#searchString').waitFor({state: 'visible', timeout: 20000});
    await assertNotErrorPage(otherRxPage, 'second patient prescribing tab');
    const repeatedCheck = rxPage.waitForResponse((response) => response.url().includes('/rx/showAllergy')
      && response.request().method() === 'POST', {timeout: 60000});
    await rxPage.evaluate(({id, atc}) => window.checkAllergy(id, atc),
      {id: originalCheck.get('id'), atc: originalCheck.get('atcCode')});
    const repeatedResponse = await repeatedCheck;
    assert(repeatedResponse.ok(), 'the original patient allergy recheck failed after opening another patient');
    const repeatedPayload = await repeatedResponse.json();
    const expectedResults = expectUnchecked ? repeatedPayload.unchecked : repeatedPayload.results;
    assert(Array.isArray(expectedResults) && expectedResults.some((item) => item.reaction === freeTextReaction),
      'opening another patient changed the original tab allergy result');
    assert(repeatedPayload.checkFailed !== true, 'the cross-tab allergy recheck was incomplete due to a service failure');
    await alertTable.getByText(freeTextReaction, {exact:false}).waitFor({state:'visible'});
    if (expectUnchecked) assert((await alertTable.innerText()).includes('Not checked:'),
      'the cross-tab unresolved allergy lost its visible Not checked notice');
    await otherRxPage.close();
    await screenshot(rxPage, config.screenshotDir, 'allergy-rx-alert');
    await rxPage.close();

    // ---------------------------------------------------------------- part 4
    // The literally reported scenario: a penicillin prescribed to a patient carrying
    // the penicillin allergy recorded in part 1.
    cancellation.throwIfCancelled();
    {
      const typedRxPage = await context.newPage();
      wirePage(typedRxPage, 'rx-alert-typed', recorder);
      await gotoApp(typedRxPage, config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
      await typedRxPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await assertNotErrorPage(typedRxPage, 'rx module (typed allergen)');

      const typedSearchBox = typedRxPage.locator('#searchString');
      await typedSearchBox.waitFor({ state: 'visible', timeout: 20000 });
      await Promise.all([
        typedRxPage.waitForResponse(
          (r) => r.url().includes('/rx/searchDrug') && r.request().method() === 'POST',
          { timeout: 60000 },
        ),
        typedSearchBox.pressSequentially(typedDrugTerm, { delay: 120 }),
      ]);

      const typedDrugItems = typedRxPage.locator('ul.ui-autocomplete li');
      await typedDrugItems.first().waitFor({ state: 'visible', timeout: 20000 });

      cancellation.throwIfCancelled();
      const [typedAllergyResponse] = await Promise.all([
        typedRxPage.waitForResponse(
          (r) => r.url().includes('/rx/showAllergy') && r.request().method() === 'POST',
          { timeout: 60000 },
        ),
        typedDrugItems.first().click(),
      ]);
      assert(
        typedAllergyResponse.status() < 400,
        `The allergy probe returned HTTP ${typedAllergyResponse.status()} for the typed allergen`,
      );

      const typedPayload = await typedAllergyResponse.json();
      assert(Array.isArray(typedPayload.results), 'The typed allergy probe JSON has no results array');
      assert(
        typedPayload.results.some((r) => String(r.reaction || '').includes(typedReaction)),
        `Prescribing "${typedDrugTerm}" to a patient allergic to ${chosenName} did not warn about the `
          + `allergy recorded from the search results (${typedPayload.results.length} warning(s) `
          + 'returned; contents withheld because they carry the patient\'s own reactions).',
      );

      const typedAlertTable = typedRxPage.locator("table[id^='alleg_tbl_']").first();
      await typedAlertTable.waitFor({ state: 'visible', timeout: 20000 });
      await screenshot(typedRxPage, config.screenshotDir, 'allergy-rx-alert-typed');
      await typedRxPage.close();
    }

    cancellation.throwIfCancelled();
    assertNoPageErrors(recorder);
    assert(recorder.dialogs.length === 0,
      `The browser opened ${recorder.dialogs.length} unexpected dialog(s)`);
    console.log('allergy-rx-alert checks passed');
    console.log(`  recorded from search: ${chosenName}`);
    console.log(`  recorded as free text: ${customAllergen}`);
    console.log(`  ${drugTerm} ${expectUnchecked ? 'displayed Not checked for' : 'warned on'} the free-text allergen`);
    console.log('  opening another patient preserved the original tab allergy check');
    if (matchedRequestedAllergen) {
      console.log(`  ${typedDrugTerm} warned on the allergen recorded from the search results`);
    }
  } catch (error) {
    if (cancellation.isCancellation(error)) {
      console.error(error.message);
    } else {
      console.error('allergy-rx-alert checks FAILED:', error.message);
      // Deliberately narrower than buildFailureDetails(): this check drives a real patient's
      // allergy list, and the recorded reactions that come back in the probe JSON are the
      // patient's own clinical text. Report the diagnostics that identify the defect --
      // failed requests, console errors, dialogs -- and leave response bodies out.
      console.error(JSON.stringify({
        badResponses: recorder.badResponses,
        consoleIssues: recorder.consoleIssues,
        pageErrors: recorder.pageErrors,
        dialogs: recorder.dialogs,
      }, null, 2));
      process.exitCode = 1;
    }
  } finally {
    try {
      if (context && attemptedReactionMarkers.length > 0) {
        try {
          await deactivateCreatedAllergies(context);
        } catch (cleanupError) {
          console.error('allergy-rx-alert cleanup FAILED:', cleanupError.message);
          process.exitCode = 1;
        }
      }
      try {
        if (context) await context.close();
      } finally {
        if (browser) await browser.close();
      }
    } finally {
      if (cancellation.exitCode) process.exitCode = cancellation.exitCode;
      cancellation.dispose();
    }
  }
})();
