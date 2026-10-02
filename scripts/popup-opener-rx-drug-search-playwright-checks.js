#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx "Search" popup -> window.opener.setSearchedDrug (risk sweep "lost popup openers";
 * coverage plan §2.6 prescriptions, opener contracts).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Rx (rx/choosePatient) ▸ type a drug
 * name ▸ Search button (popupRxSearchWindow -> rx/searchDrug?rx2=true, ChooseDrug.jsp)
 * ▸ click the product. ChooseDrug.jsp's setDrugRx2() calls
 * window.opener.setSearchedDrug(), which stages the product on the Rx pad through
 * rx/WriteScript (parameterValue=createNewRx), then closes the popup.
 * Asserts the popup lists the product, the pick closes it, the opener POSTs
 * createNewRx for that product and renders exactly one new card naming it. Staging is
 * session-only (no drugs row until Save); the check asserts no drugs row appears and
 * leaves nothing behind beyond the owned FAKE- patient runWorkflow removes.
 * RX_EDIT_DRUG_TERM / RX_EDIT_DRUG_NAME select the product (default LIPITOR 20 / 20MG).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { documentChain, pickInPopup, lostOpenerMessage } = require('./lib/popup-opener-helpers');

const DRUG_TERM = process.env.RX_EDIT_DRUG_TERM || 'LIPITOR 20';
const DRUG_NAME = process.env.RX_EDIT_DRUG_NAME || 'LIPITOR 20MG';

async function workflow(s) {
  const { sql, patient } = s;
  const chain = documentChain(s.context);
  let rx;
  let chooser;

  await s.step('the Rx Search button opens the drug chooser listing the typed product', async () => {
    rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
    h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle');
    await rx.locator('#searchString').fill(DRUG_TERM);
    // The autocomplete would also open on typing; the Search button is the full-search popup.
    await rx.keyboard.press('Escape');
    chooser = await s.popup(rx, rx.locator('#searchDrugsButtonSet input[name="search"]'), 'rx-drug-chooser');
    await chooser.locator('a[onclick^="setDrugRx2("]').filter({ hasText: DRUG_NAME }).first()
      .waitFor({ state: 'visible', timeout: 60000 });
  });

  // Last: the opener callback.
  await s.step('picking the product closes the chooser and stages exactly one card for it on the Rx pad', async () => {
    const before = await rx.locator('[id^="drugName_"]').count();
    const staged = rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/WriteScript') && r.request().method() === 'POST'
      && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx', { timeout: 20000 })
      .catch(() => null);
    const closed = await pickInPopup(chooser, chooser.locator('a[onclick^="setDrugRx2("]').filter({ hasText: DRUG_NAME }).first());
    const response = await staged;
    h.assert(response, closed ? 'The chooser closed but the Rx pad never staged the product (no createNewRx POST)'
      : await lostOpenerMessage('Rx drug chooser (rx/searchDrug)', chooser, chain));
    h.assert(response.ok(), `Staging the picked product answered HTTP ${response.status()}`);
    h.assert(closed, 'The product was staged but the chooser did not close');
    await rx.locator('[id^="drugName_"]').nth(before).waitFor({ state: 'attached' });
    h.assert(await rx.locator('[id^="drugName_"]').count() === before + 1, 'The pick did not stage exactly one card');
    h.assert((await rx.locator('[id^="drugName_"]').nth(before).inputValue()).includes(DRUG_NAME),
      'The staged card does not name the picked product');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0',
      'Staging a product wrote a drugs row before Save');
  });
}

if (require.main === module) runWorkflow('popup-opener-rx-drug-search', workflow, { openPatient: true });
module.exports = { workflow };
