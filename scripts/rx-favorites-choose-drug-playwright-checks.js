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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The Rx favourites page, the drug chooser and the favourite/reprint/CPP mutators behave as
 * PR #3908's review pass left them (adapted from MagentaHealth/Open-O and the CARLOS
 * contributors credited on that PR):
 *
 *   1. The "Edit favourites" side link opens the favourites page. It used to open
 *      rx/updateFavorite, the favourite WRITE action, with no favourite to update; that action
 *      is POST-only now and the link goes through the rx/ViewEditFavorites2 read gate.
 *   2. Choosing a drug (here the custom-drug link on the results page) POSTs to rx/chooseDrug and
 *      lands on the write-script page. It used to be a GET link, staging a card outside CSRFGuard.
 *   3. rx/chooseDrug, the legacy rx/rePrescribe2 reprint path, rx/hideCpp and rx/reorderDrug
 *      refuse GET with 405 rather than mutating the chart.
 *   4. rx/useFavorite stages only the caller's own favourite: another provider's favourite is
 *      403, an unknown id 404, a malformed id 400, and the caller's own favourite is staged.
 *
 * The check owns one synthetic patient, the favourites it inserts, and any drug row it stages
 * (nothing is saved to the chart; the stash lives in the session). It needs no DrugRef lookup.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

/** A provider number no real login owns: the favourites table takes six characters. */
const FOREIGN_PROVIDER = 'FAKEPW';

async function openRx(session, demographicNo) {
  const page = await session.context.newPage();
  await h.gotoApp(page, session.config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await h.assertNotErrorPage(page, 'Rx page');
  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  return page;
}

function insertFavorite(sql, providerNo, name) {
  const id = sql.value(`INSERT INTO favorites (provider_no, favoritename, customName, GCN_SEQNO, takemin,
    takemax, freqcode, duration, durunit, quantity, \`repeat\`, nosubs, prn, special)
    VALUES (${h.sqlString(providerNo)}, ${h.sqlString(name)}, ${h.sqlString(name)}, 0, 1, 1, 'OID', '30',
    'D', '30', 0, 0, 0, ''); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), `the favourite ${name} was not created`);
  return id;
}

/**
 * POST rx/useFavorite the way SearchDrug3's useFav2() does: the page's CSRF token in the
 * CSRF-TOKEN header, the AJAX marker, and the window's patient (CarlosAjax adds it to every Rx
 * request; staging is refused for a request that names no patient). Sent through the context's
 * request API, which shares the session cookie, so the refusals this check expects (403/404/400)
 * are not logged as resource failures on the strict page.
 */
async function useFavorite(session, page, demographicNo, favoriteId) {
  const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue().catch(() => '');
  h.assert(token, 'the Rx page has no CSRF token to send');
  const response = await session.context.request.post(`${String(session.config.baseUrl).replace(/\/$/, '')}/rx/useFavorite`, {
    headers: { 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
    form: { parameterValue: 'useFav2', favoriteId, randomId: '424242', demographicNo: String(demographicNo) },
    maxRedirects: 0,
    timeout: 20000,
  });
  const text = await response.text().catch(() => '');
  return { status: response.status(), body: text.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 400) };
}

async function workflow(session) {
  const { sql, patient, provider, marker, config } = session;
  session.cleanup(() => sql.execute(`DELETE FROM favorites WHERE favoritename LIKE ${h.sqlString(`${marker}%`)}`));
  session.cleanup(() => sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}
    AND customName LIKE ${h.sqlString(`${marker}%`)}`));

  await session.step('the Edit favourites side link opens the favourites page', async () => {
    const rx = await openRx(session, patient);
    const link = rx.locator('a[href*="/rx/ViewEditFavorites2"]').first();
    await link.waitFor({ state: 'visible', timeout: 20000 });
    h.assert(new URL(await link.getAttribute('href'), config.baseUrl).searchParams.get('demographicNo') === patient,
      'the Edit favourites link does not carry the open patient');
    await Promise.all([
      rx.waitForURL(/\/rx\/ViewEditFavorites2\?/, { timeout: 30000 }),
      link.click(),
    ]);
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(rx, 'Edit favourites page');
    await rx.locator('form[name="DispForm"]').waitFor({ state: 'attached', timeout: 20000 });
    await rx.locator('form[action$="/rx/deleteFavorite2"] input[name="favoriteId"]')
      .waitFor({ state: 'attached', timeout: 20000 });
    await rx.close();
  });

  await session.step('choosing a custom drug POSTs to rx/chooseDrug and opens the write-script page', async () => {
    const page = await session.context.newPage();
    await h.gotoApp(page, config.baseUrl, `/rx/searchDrug?demographicNo=${patient}`
      + `&searchString=${encodeURIComponent(`${marker}-nosuchdrug`)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'drug search results');
    const form = page.locator('form#chooseDrugForm[method="post"][action$="/rx/chooseDrug"]');
    await form.waitFor({ state: 'attached', timeout: 20000 });
    h.assert(await form.locator('input[name="demographicNo"]').inputValue() === patient,
      'the chooser form does not name the open patient');
    h.assert((await page.locator('a[href*="/rx/chooseDrug?"]').count()) === 0,
      'the results page still links to rx/chooseDrug with GET');
    const chosen = page.waitForResponse((response) => /\/rx\/chooseDrug(\?|$)/.test(response.url()), { timeout: 30000 });
    // The custom-drug link confirms first; accept it like the prescriber does. The link is a
    // javascript: URL, so its confirm opens after click() resolves: keep the expected-dialog
    // handler in place until the chooser has answered, or the strict page dismisses it.
    let response;
    await h.withExpectedDialogs(page, async () => {
      await page.locator('a[href="javascript:customWarning();"]').click();
      response = await chosen;
    }, { accept: true });
    h.assert(response.request().method() === 'POST', `rx/chooseDrug was requested with ${response.request().method()}`);
    h.assert(response.status() < 400, `rx/chooseDrug answered HTTP ${response.status()}`);
    // The chosen drug is a staged card and the write-script page ("Step 3") opens for it, with its
    // form bound: the page's scripts named a form that does not exist and threw on every field
    // (a page error fails this strict page). It used to bounce back to the Rx page with nothing
    // staged, because DrugRef refused the blank id before the custom-drug fallback ran.
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'write-script page');
    await page.locator('form#frm textarea[name="customName"]').waitFor({ state: 'attached', timeout: 20000 });
    h.assert(await page.evaluate(() => frm === document.forms.frm && typeof frm.quantity === 'object'),
      'the write-script page did not bind its form');
    await page.close();
    // The stash belongs to the session: the Rx page for the patient shows the staged card.
    const rx = await openRx(session, patient);
    await rx.locator("[id^='drugName_']").first().waitFor({ state: 'attached', timeout: 20000 });
    await rx.close();
  });

  await session.step('chooseDrug, legacy reprint, hideCpp and reorderDrug refuse GET', async () => {
    const base = String(config.baseUrl).replace(/\/$/, '');
    const probes = [
      `/rx/chooseDrug?demographicNo=${patient}&BN=&drugId=`,
      `/rx/rePrescribe2?demographicNo=${patient}&drugList=1`,
      '/rx/hideCpp?prescriptId=1&value=true',
      '/rx/reorderDrug?prescriptId=1&position=1',
    ];
    const failures = [];
    for (const probe of probes) {
      const response = await session.context.request.fetch(`${base}${probe}`, { method: 'GET', maxRedirects: 0, timeout: 15000 });
      if (response.status() !== 405) failures.push(`${probe} answered HTTP ${response.status()}, not 405`);
    }
    h.assert(failures.length === 0, `mutator GET probe(s) not refused:\n    - ${failures.join('\n    - ')}`);
  });

  await session.step('useFavorite stages only the caller\'s own favourite', async () => {
    const own = insertFavorite(sql, provider, `${marker}-own`);
    const foreign = insertFavorite(sql, FOREIGN_PROVIDER, `${marker}-foreign`);
    const rx = await openRx(session, patient);

    const refused = await useFavorite(session, rx, patient, foreign);
    h.assert(refused.status === 403, `another provider's favourite answered HTTP ${refused.status}: ${refused.body}`);
    const missing = await useFavorite(session, rx, patient, '999999999');
    h.assert(missing.status === 404, `an unknown favourite answered HTTP ${missing.status}: ${missing.body}`);
    const malformed = await useFavorite(session, rx, patient, 'abc');
    h.assert(malformed.status === 400, `a malformed favourite id answered HTTP ${malformed.status}: ${malformed.body}`);

    const staged = await useFavorite(session, rx, patient, own);
    h.assert(staged.status === 200, `the caller's own favourite answered HTTP ${staged.status}: ${staged.body}`);
    // The stash belongs to the session, so a fresh Rx page for the patient shows the card under
    // the key the page asked for, named after the favourite.
    const again = await openRx(session, patient);
    const card = again.locator('#drugName_424242');
    await card.waitFor({ state: 'attached', timeout: 20000 });
    h.assert((await card.inputValue()).includes(`${marker}-own`),
      'the staged card is not the caller\'s favourite');
    await again.close();
    await rx.close();
  });
}

if (require.main === module) runWorkflow('rx-favorites-choose-drug', workflow);
module.exports = { workflow };
