#!/usr/bin/env node
/*
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
 * Browser check for the Consultations list Consultant and Provider (MRP) filters
 * (issue #3976).
 *
 * WHAT IT DRIVES, THE WAY A REFERRAL CLERK DOES. Login, the schedule banner's
 * Consultations link (encounter/IncomingConsultation, the entry point a user
 * actually reaches, which must populate the Provider dropdown too), then:
 *
 *   a. the Provider dropdown lists MRPs on first load;
 *   b. typing two letters of a consultant seen in the list opens the type-ahead
 *      (debounced, served as JSON by encounter/consultation/searchConsultants,
 *      at most 20 suggestions), and picking one fills the hidden consultantId;
 *   c. Search applies it: every row is that consultant, the badge names them,
 *      the box keeps the label and the filter restarts at offset 0;
 *   d. a sort header keeps the consultant filter; paging (limit=1) keeps it too;
 *   e. text typed after a pick, or typed without picking, is cleared on submit
 *      and never becomes a filter;
 *   f. picking a Provider filters every row to that MRP, and it combines with the
 *      consultant filter;
 *   g. crafted parameters are ignored on the server, not rendered as filters: a
 *      non-numeric or unknown consultantId, and a filterProviderNo the dropdown
 *      does not offer;
 *   h. the search endpoint answers application/json, returns [] for one letter,
 *      and matches "%" literally.
 *
 * Read-only against the demo dataset: it needs consultation requests that name a
 * specialist, which the packaged and devcontainer demo data both carry. It skips
 * (exit 2) if the first page of the list has no such row.
 *
 * Environment (common contract in lib/playwright-harness.js readConfig()):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH, SCREENSHOT_DIR
 *
 * Defaults are for the local devcontainer:
 *   npm run test:consultation-list-filters-playwright
 */

const {
  SkipCheck, appUrl, assert, assertNotErrorPage, assertStrictPage, createRecorder, gotoApp,
  launchBrowser, login, newContext, readConfig, runCheck, screenshot, wireStrictPage,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');

const TIMEOUT = 30000;
// Column order in ViewConsultationRequests.jsp: Status, Urgency, Team, Patient,
// Provider (MRP), Service, Consultant, ...
const PROVIDER_COLUMN = 4;
const CONSULTANT_COLUMN = 6;

async function columnTexts(page, column) {
  return page.locator('table.consult-table tbody tr').evaluateAll((rows, index) => rows.map((row) => {
    const cell = row.querySelectorAll('td')[index];
    if (!cell) return '';
    // The Consultant cell may carry an Ocean badge after the name; take the name only.
    const clone = cell.cloneNode(true);
    clone.querySelectorAll('.badge').forEach((badge) => badge.remove());
    return clone.textContent.replace(/\s+/g, ' ').trim();
  }), column);
}

async function submitFilters(page) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
    page.locator('#consultationFilterForm input[type="submit"]').click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await assertNotErrorPage(page, 'consultation list after filter submit');
}

function listParam(page, name) {
  return new URL(page.url()).searchParams.get(name);
}

async function typeAndWaitForSuggestions(page, text) {
  const search = page.locator('#consultantSearch');
  await search.fill('');
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/encounter/consultation/searchConsultants'), { timeout: TIMEOUT }),
    search.pressSequentially(text, { delay: 40 }),
  ]);
  assert(response.status() === 200, `consultant search answered HTTP ${response.status()}`);
  assert(/^application\/json/.test(response.headers()['content-type'] || ''),
    `consultant search answered ${response.headers()['content-type']}, not JSON`);
  const items = await response.json();
  await page.locator('#consultantSuggestions').waitFor({ state: 'visible', timeout: TIMEOUT });
  return items;
}

async function main() {
  const config = readConfig();
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config, { viewport: { width: 1440, height: 1000 } });
    const schedulePage = await login(context, config, recorder);

    // a. Enter through the banner, as a user does.
    const list = await clickOpensPopup(schedulePage,
      schedulePage.locator("a[onclick*='/encounter/IncomingConsultation']").first(),
      { label: 'consultation-list', recorder, timeout: TIMEOUT });
    await assertNotErrorPage(list, 'consultation list');
    const providerOptions = await list.locator('#filterProviderNo option').evaluateAll((opts) => opts
      .map((o) => ({ value: o.value, label: o.textContent.replace(/\s+/g, ' ').trim() })));
    assert(providerOptions.length > 1 && providerOptions[0].value === '',
      `Provider dropdown was not populated on the banner entry point (${providerOptions.length} options)`);
    console.log(`PASS provider dropdown lists ${providerOptions.length - 1} MRPs on first load`);

    // Pick the consultant with the most rows on the first page, so paging has
    // something to page through.
    const consultants = (await columnTexts(list, CONSULTANT_COLUMN)).filter((name) => name && name !== 'N/A');
    if (consultants.length === 0) {
      throw new SkipCheck('the first page of the consultation list names no specialist');
    }
    const counts = new Map();
    consultants.forEach((name) => counts.set(name, (counts.get(name) || 0) + 1));
    const [consultantLabel] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
    const lastName = consultantLabel.split(',')[0].trim();
    assert(lastName.length >= 2, `consultant "${consultantLabel}" has no usable last name`);

    // b. Type-ahead.
    const items = await typeAndWaitForSuggestions(list, lastName.slice(0, Math.min(lastName.length, 4)));
    assert(Array.isArray(items) && items.length > 0 && items.length <= 20,
      `type-ahead returned ${Array.isArray(items) ? items.length : 'non-array'} suggestions`);
    const option = list.locator('#consultantSuggestions li[role="option"]', { hasText: consultantLabel }).first();
    assert(await option.count() === 1, `type-ahead did not offer "${consultantLabel}"`);
    await option.click();
    const consultantId = await list.locator('#consultantId').inputValue();
    assert(/^\d+$/.test(consultantId), `picking a consultant left consultantId "${consultantId}"`);
    assert(await list.locator('#consultantSuggestions').isHidden(), 'suggestion list stayed open after the pick');

    // c. Apply it.
    await submitFilters(list);
    assert(listParam(list, 'consultantId') === consultantId, 'submitted URL lost the consultantId');
    assert(listParam(list, 'offset') === '0', `a new filter did not restart at offset 0 (${listParam(list, 'offset')})`);
    let names = await columnTexts(list, CONSULTANT_COLUMN);
    assert(names.length > 0, 'consultant filter returned no rows for a consultant seen in the list');
    assert(names.every((name) => name === consultantLabel),
      `consultant filter let other consultants through: ${[...new Set(names)].join(' | ')}`);
    assert((await list.locator('#consultantFilterBadge').innerText()).includes(consultantLabel),
      'the consultant filter badge does not name the consultant');
    assert(await list.locator('#consultantSearch').inputValue() === consultantLabel,
      'the consultant box did not keep the selected label');
    await screenshot(list, config.screenshotDir, 'consultation-list-consultant-filter');
    console.log(`PASS consultant filter shows only ${names.length} request(s) to the chosen specialist`);

    // d. Sorting and paging keep the filter.
    await Promise.all([
      list.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
      list.locator('a[onclick*="setOrder(\'7\')"]').click(),
    ]);
    await assertNotErrorPage(list, 'consultation list after sorting');
    assert(listParam(list, 'consultantId') === consultantId, 'sorting dropped the consultant filter');
    names = await columnTexts(list, CONSULTANT_COLUMN);
    assert(names.length > 0 && names.every((name) => name === consultantLabel), 'sorting widened the consultant filter');
    if (counts.get(consultantLabel) > 1) {
      await gotoApp(list, config.baseUrl, `/encounter/ViewConsultation?consultantId=${consultantId}&includeCompleted=on&limit=1&offset=0`);
      await assertNotErrorPage(list, 'consultation list with limit=1');
      await Promise.all([
        list.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
        list.locator('button[onclick="gotoPage(true);"]').click(),
      ]);
      assert(listParam(list, 'consultantId') === consultantId, 'paging dropped the consultant filter');
      assert(listParam(list, 'offset') === '1', `paging did not advance (offset ${listParam(list, 'offset')})`);
      names = await columnTexts(list, CONSULTANT_COLUMN);
      assert(names.length === 1 && names[0] === consultantLabel, 'the second page is not the chosen consultant');
      console.log('PASS sorting and paging keep the consultant filter');
    } else {
      console.log('PASS sorting keeps the consultant filter (one request: paging not exercised)');
    }

    // e. Unpicked text never becomes a filter.
    await typeAndWaitForSuggestions(list, lastName.slice(0, 2));
    assert(await list.locator('#consultantId').inputValue() === '', 'editing the box kept the previous consultantId');
    await list.locator('#consultantSearch').press('Escape');
    await submitFilters(list);
    assert(!listParam(list, 'consultantId'), 'half-typed text was submitted as a consultant filter');
    assert(await list.locator('#consultantSearch').inputValue() === '', 'half-typed text survived the submit');
    assert(await list.locator('#consultantFilterBadge').count() === 0, 'a consultant badge was shown with no filter');
    console.log('PASS text typed but not picked is cleared and not applied');

    // f. Provider (MRP) filter, alone and combined.
    const rowMrps = (await columnTexts(list, PROVIDER_COLUMN)).filter((name) => name && name !== 'N/A');
    const mrp = providerOptions.find((o) => o.value && rowMrps.includes(o.label)) || providerOptions[1];
    await list.locator('#filterProviderNo').selectOption(mrp.value);
    await submitFilters(list);
    assert(listParam(list, 'filterProviderNo') === mrp.value, 'the provider filter was not submitted');
    assert(await list.locator('#filterProviderNo').inputValue() === mrp.value, 'the provider dropdown lost its selection');
    const mrpRows = await columnTexts(list, PROVIDER_COLUMN);
    assert(mrpRows.every((name) => name === mrp.label),
      `provider filter let other MRPs through: ${[...new Set(mrpRows)].join(' | ')}`);
    assert((await list.locator('#providerFilterBadge').innerText()).includes(mrp.label),
      'the provider filter badge does not name the MRP');
    console.log(`PASS provider filter shows only ${mrpRows.length} request(s) for ${mrp.value}`);

    await gotoApp(list, config.baseUrl,
      `/encounter/ViewConsultation?includeCompleted=on&consultantId=${consultantId}&filterProviderNo=${encodeURIComponent(mrp.value)}`);
    await assertNotErrorPage(list, 'consultation list with both filters');
    const both = await list.locator('table.consult-table tbody tr').count();
    names = await columnTexts(list, CONSULTANT_COLUMN);
    const bothMrps = await columnTexts(list, PROVIDER_COLUMN);
    assert(names.every((name) => name === consultantLabel) && bothMrps.every((name) => name === mrp.label),
      'the consultant and provider filters did not combine');
    console.log(`PASS consultant and provider filters combine (${both} row(s))`);

    // g. Crafted parameters are ignored, not applied and not echoed.
    for (const [query, what] of [
      ['consultantId=abc', 'a non-numeric consultantId'],
      ['consultantId=2147483000', 'an unknown consultantId'],
      ['filterProviderNo=ZZ9999', 'a provider the dropdown does not offer'],
    ]) {
      const response = await gotoApp(list, config.baseUrl, `/encounter/ViewConsultation?includeCompleted=on&${query}`);
      assert(response && response.ok(), `${what} answered HTTP ${response && response.status()}`);
      await assertNotErrorPage(list, `consultation list with ${what}`);
      assert(await list.locator('#consultantFilterBadge, #providerFilterBadge').count() === 0,
        `${what} was rendered as an active filter`);
      assert(await list.locator('table.consult-table tbody tr').count() > 0, `${what} emptied the list`);
    }
    console.log('PASS crafted consultant and provider parameters are ignored on the server');

    // h. The endpoint itself.
    const endpoint = (keyword) => list.request.get(appUrl(config.baseUrl,
      `/encounter/consultation/searchConsultants?keyword=${encodeURIComponent(keyword)}`));
    const short = await endpoint('a');
    assert(short.status() === 200 && (await short.text()).trim() === '[]', 'a one-letter keyword was searched');
    const wildcard = await endpoint('%%');
    assert(wildcard.status() === 200, `a "%" keyword answered HTTP ${wildcard.status()}`);
    assert(JSON.stringify(await wildcard.json()) === '[]', '"%" matched specialists as a wildcard');
    console.log('PASS consultant search endpoint: JSON, 2-character minimum, literal wildcards');

    assertStrictPage(recorder, ['consultation-list']);
    await context.close();
    return { consultantId, mrp: mrp.value };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'consultation-list-filters', run: main });
}
