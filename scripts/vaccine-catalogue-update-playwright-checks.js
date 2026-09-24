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
 * Browser check for Administration > Integration > Update National Vaccine
 * Catalogue (NVC), driven the way an administrator uses it (issue #2726).
 *
 * The Canadian Vaccine Catalogue v1 API the Prevention module was built on is
 * retired; its replacement, the PHAC National Vaccine Catalogue v2, is a single
 * FHIR R4 bundle. CARLOS had no way to load it at all. This check pins:
 *
 *   1. The Administration panel link opens the NVC page, which renders the
 *      install status, and a GET to the update route is refused with 405.
 *   2. With VACCINE_CATALOGUE_TRIGGER=true (needs outbound HTTPS to
 *      nvc-cnv.canada.ca, or cvc.url pointing at a mirror): the Update button
 *      downloads and installs the catalogue, the page reports success, and the
 *      database holds a coherent catalogue -- every brand linked to an installed
 *      generic, products and lot numbers with expiry dates, the AnatomicalSite
 *      and RouteOfAdmin lookup lists, and the install bookkeeping.
 *   3. A second update replaces rather than accumulates: catalogue and lookup
 *      list row counts are unchanged.
 *   4. The patient Preventions page switches to the catalogue picker: typing a
 *      real NVC lot number offers that lot, and choosing it opens Add Prevention
 *      for the lot's generic with its brand pre-selected and the lot's expiry
 *      date filled. Nothing is saved to the chart.
 *
 * Runs read-only by default (step 1), so it is safe in the deb-install suite
 * loop (docs/ui-tests/deb-install-validation.md section 6). The trigger rewrites
 * the catalogue tables and is refused against a non-loopback BASE_URL. When the
 * catalogue was not installed before the run, the rows and lookup lists this
 * check created are removed afterwards unless VACCINE_CATALOGUE_KEEP=true.
 *
 * Environment: BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN (RESET_PASSWORD on a
 * fresh deb), MYSQL_HOST/USER/PASSWORD/DATABASE, CHROME_PATH.
 * Optional: VACCINE_CATALOGUE_TRIGGER (default false), VACCINE_CATALOGUE_KEEP
 * (default false), PREVENTION_DEMOGRAPHIC_NO (default 1).
 */
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');

const VIEW_PATH = '/prevention/ViewVaccineCatalogue';
const UPDATE_PATH = '/prevention/UpdateVaccineCatalogue';
const LOOPBACK = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);

function catalogueCounts(sql) {
  const [row] = sql.rows(`SELECT
      (SELECT COUNT(*) FROM CVCImmunization WHERE generic=1),
      (SELECT COUNT(*) FROM CVCImmunization WHERE generic=0),
      (SELECT COUNT(*) FROM CVCMedication),
      (SELECT COUNT(*) FROM CVCMedicationLotNumber),
      (SELECT COUNT(*) FROM CVCMedicationLotNumber WHERE expiryDate IS NOT NULL),
      (SELECT COUNT(*) FROM CVCImmunization b LEFT JOIN CVCImmunization g
         ON g.snomedConceptId=b.parentConceptId AND g.generic=1
         WHERE b.generic=0 AND g.id IS NULL),
      (SELECT COUNT(*) FROM LookupListItem i JOIN LookupList l ON l.id=i.lookupListId
         WHERE l.name IN ('AnatomicalSite','RouteOfAdmin')),
      (SELECT COUNT(*) FROM LookupListItem i JOIN LookupList l ON l.id=i.lookupListId
         WHERE l.name IN ('AnatomicalSite','RouteOfAdmin') AND i.active=1)`);
  const [generics, brands, products, lots, datedLots, orphanBrands, lookupItems, activeLookupItems] = row.map(Number);
  return { generics, brands, products, lots, datedLots, orphanBrands, lookupItems, activeLookupItems };
}

async function openCataloguePage(context, config, recorder) {
  const admin = await context.newPage();
  wireStrictPage(admin, 'admin-panel', recorder);
  await gotoApp(admin, config.baseUrl, '/admin/ViewAdmin');
  await assertNotErrorPage(admin, 'administration panel');
  const link = admin.locator('a', { hasText: /Update National Vaccine Catalogue/i }).first();
  const popup = await clickOpensPopup(admin, link, { recorder, label: 'vaccine-catalogue', timeout: 30000 });
  const popupUrl = new URL(popup.url());
  assert(popupUrl.origin === config.baseUrl.origin
    && popupUrl.pathname === config.baseUrl.pathname.replace(/\/$/, '') + VIEW_PATH,
  'administration link opened an unexpected origin or path');
  await popup.locator('#catalogueStatus').waitFor({ state: 'visible', timeout: 20000 });
  return { admin, popup };
}

async function runUpdate(popup, label) {
  const button = popup.locator('#catalogueUpdateButton');
  await button.waitFor({ state: 'visible', timeout: 20000 });
  // The install downloads ~9 MB and rewrites ~5,000 rows; allow generous time.
  await Promise.all([
    popup.waitForURL((url) => url.pathname.endsWith(VIEW_PATH) && url.searchParams.has('result'), { timeout: 300000 }),
    button.click(),
  ]);
  await assertNotErrorPage(popup, `${label} result page`);
  const result = await popup.locator('#catalogueResult').getAttribute('data-result');
  assert(result === 'updated', `${label}: the page reported "${result}" instead of a successful update`
    + ' (check the Tomcat log; "unavailable" means the NVC endpoint could not be reached)');
}

async function main() {
  const config = readConfig();
  const trigger = process.env.VACCINE_CATALOGUE_TRIGGER === 'true';
  const keep = process.env.VACCINE_CATALOGUE_KEEP === 'true';
  const demographicNo = process.env.PREVENTION_DEMOGRAPHIC_NO || '1';
  assert(/^\d+$/.test(demographicNo), 'PREVENTION_DEMOGRAPHIC_NO must be numeric');
  if (trigger) {
    const host = config.baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase();
    assert(LOOPBACK.has(host), `VACCINE_CATALOGUE_TRIGGER=true rewrites the vaccine catalogue and is refused `
      + `against the non-loopback target ${host}. Run it against a disposable local install.`);
  }
  if (!config.mysql.password) {
    throw new SkipCheck('MYSQL_PASSWORD is not set; this check asserts the installed catalogue rows');
  }

  const sql = createSqlRunner(config.mysql);
  const installedBefore = sql.value(`SELECT COUNT(*) FROM property WHERE name='cvc.updated'`) !== '0';
  const listsBefore = sql.rows(`SELECT name FROM LookupList WHERE name IN ('AnatomicalSite','RouteOfAdmin')`)
    .map((row) => row[0]);
  const recorder = createRecorder();
  let browser;
  let installedByThisRun = false;
  const summary = {};
  try {
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const schedule = await login(context, config, recorder);

    // Step 1: the page is reachable from the Administration panel and read-only on GET.
    let { admin, popup } = await openCataloguePage(context, config, recorder);
    const statusText = await popup.locator('#catalogueStatus').innerText();
    assert(/Last updated/i.test(statusText) && /Source/i.test(statusText), 'catalogue status table is incomplete');
    const refused = await context.request.get(`${config.baseUrl.href.replace(/\/$/, '')}${UPDATE_PATH}`,
      { maxRedirects: 0, failOnStatusCode: false });
    assert(refused.status() === 405, `GET ${UPDATE_PATH} answered HTTP ${refused.status()}, expected 405`);
    await refused.dispose();
    summary.readOnly = 'PASS';

    if (!trigger) {
      await popup.close();
      await admin.close();
      assertStrictPage(recorder);
      return { ...summary, trigger: 'skipped (set VACCINE_CATALOGUE_TRIGGER=true to install)' };
    }

    // Step 2: install and verify what landed in the database.
    await runUpdate(popup, 'first update');
    installedByThisRun = !installedBefore;
    const first = catalogueCounts(sql);
    assert(first.generics >= 50, `only ${first.generics} generic vaccines were installed`);
    assert(first.brands >= 50, `only ${first.brands} brand-name vaccines were installed`);
    assert(first.orphanBrands === 0, `${first.orphanBrands} brand-name vaccines point at no installed generic`);
    assert(first.products === first.brands, `${first.products} products for ${first.brands} brands`);
    assert(first.lots >= 500, `only ${first.lots} lot numbers were installed`);
    assert(first.datedLots / first.lots > 0.9, `${first.lots - first.datedLots} of ${first.lots} lots have no expiry date`);
    assert(first.activeLookupItems > 0, 'no AnatomicalSite/RouteOfAdmin lookup items were installed');
    assert(sql.value(`SELECT COUNT(*) FROM property WHERE name IN ('cvc.updated','cvc.version','cvc.firstdate')`) === '3',
      'install bookkeeping (cvc.updated / cvc.version / cvc.firstdate) is incomplete');
    const shownGenerics = (await popup.locator('#catalogueGenericCount').innerText()).trim();
    assert(shownGenerics === String(first.generics), `page shows ${shownGenerics} generics, database has ${first.generics}`);
    const shownVersion = (await popup.locator('#catalogueVersion').innerText()).trim();
    assert(shownVersion.length > 0, 'page shows no NVC version after the install');
    Object.assign(summary, { version: shownVersion, ...first });

    // Step 3: a second update replaces the catalogue instead of adding to it.
    await runUpdate(popup, 'second update');
    const second = catalogueCounts(sql);
    for (const key of ['generics', 'brands', 'products', 'lots', 'lookupItems']) {
      assert(second[key] === first[key], `second update changed ${key} from ${first[key]} to ${second[key]}`);
    }
    summary.idempotent = 'PASS';
    await popup.close();
    await admin.close();

    // Step 4: the Preventions page now offers the catalogue picker, and a real lot
    // number opens Add Prevention for its generic with the brand and expiry filled.
    const [lotRow] = sql.rows(`SELECT l.lotNumber, DATE_FORMAT(l.expiryDate,'%Y-%m-%d'), b.snomedConceptId, g.picklistName
        FROM CVCMedicationLotNumber l
        JOIN CVCMedication m ON m.id=l.cvcMedicationId
        JOIN CVCImmunization b ON b.snomedConceptId=m.snomedCode AND b.generic=0
        JOIN CVCImmunization g ON g.snomedConceptId=b.parentConceptId AND g.generic=1
        WHERE l.expiryDate IS NOT NULL AND CHAR_LENGTH(l.lotNumber) >= 5 AND l.lotNumber REGEXP '^[A-Za-z0-9]+$'
          AND (SELECT COUNT(*) FROM CVCMedicationLotNumber d WHERE d.lotNumber=l.lotNumber)=1
        ORDER BY l.expiryDate DESC LIMIT 1`);
    assert(lotRow, 'no uniquely numbered, dated lot is available to exercise the picker');
    const [lotNumber, expiry, brandConcept, genericName] = lotRow;

    const prevention = await context.newPage();
    wireStrictPage(prevention, 'preventions', recorder);
    await gotoApp(prevention, config.baseUrl, `/prevention/ViewPreventionIndex?demographic_no=${demographicNo}`);
    await assertNotErrorPage(prevention, 'patient Preventions page');
    const picker = prevention.locator('#lotNumberToAdd2');
    await picker.waitFor({ state: 'visible', timeout: 20000 });
    await picker.pressSequentially(lotNumber, { delay: 40 });
    const choice = prevention.locator('#lotNumberToAdd2_choices .ac-item').first();
    await choice.waitFor({ state: 'visible', timeout: 20000 });
    const addPopup = await clickOpensPopup(prevention, choice, { recorder, label: 'add-prevention', timeout: 30000 });
    await addPopup.waitForLoadState('domcontentloaded');
    await assertNotErrorPage(addPopup, 'Add Prevention (from NVC lot)');
    const addUrl = new URL(addPopup.url());
    assert(addUrl.searchParams.get('lotNumber') === lotNumber, 'the picker did not carry the chosen lot number');
    const bodyText = await addPopup.locator('body').innerText();
    assert(bodyText.includes(genericName), `Add Prevention did not open for the lot's generic "${genericName}"`);
    const brandSelect = addPopup.locator('#cvcName');
    await brandSelect.waitFor({ state: 'attached', timeout: 20000 });
    assert(await brandSelect.inputValue() === brandConcept, 'the lot\'s brand was not pre-selected');
    await addPopup.waitForFunction(
      (want) => (document.getElementById('expiryDate') || {}).value === want, expiry, { timeout: 20000 });
    summary.lotPicker = 'PASS';
    await addPopup.close();
    await prevention.close();
    await schedule.close();

    assertStrictPage(recorder);
    return summary;
  } finally {
    try {
      if (browser) await browser.close();
    } finally {
      try {
        if (installedByThisRun && !keep) {
          // Put a disposable install back the way it was found: no catalogue, and no
          // lookup lists this run created. Pre-existing lists and installs are kept.
          const created = ['AnatomicalSite', 'RouteOfAdmin'].filter((name) => !listsBefore.includes(name));
          const createdList = created.map((name) => sqlString(name)).join(',');
          sql.execute(`DELETE FROM CVCMedicationLotNumber; DELETE FROM CVCMedicationGTIN;
            DELETE FROM CVCMedication; DELETE FROM CVCImmunization;
            DELETE FROM property WHERE name IN ('cvc.updated','cvc.version','cvc.firstdate')
              AND (provider_no IS NULL OR provider_no='');
            ${created.length ? `DELETE i FROM LookupListItem i JOIN LookupList l ON l.id=i.lookupListId WHERE l.name IN (${createdList});
            DELETE FROM LookupList WHERE name IN (${createdList});` : ''}`);
        }
      } finally {
        sql.dispose();
      }
    }
  }
}

if (require.main === module) runCheck({ name: 'vaccine-catalogue-update', run: main });
module.exports = { main };
