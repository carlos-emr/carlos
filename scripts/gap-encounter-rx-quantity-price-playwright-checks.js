#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx card: entering a quantity asks CARLOS for the drug price.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ Prescriptions "+" (the Rx page) ▸ Custom Drug ▸ the
 * card's Qty/Mitte box ▸ leave the box (its blur handler calls updateQty and getCost, which GETs
 * rx/ViewDrugPrice and fills the card's cost line).
 * Asserts that typing a quantity and leaving the box raises no script error, sends a GET to
 * rx/ViewDrugPrice carrying the quantity and the card's random id and is answered without error, and
 * that the quantity is kept on the card. It fails today at the first of those: getCost builds its Ajax
 * request with Insertion.Bottom, a Prototype global the page no longer loads (SearchDrug3.jsp), so every
 * quantity entry throws "Insertion is not defined" and no price request is ever sent; the route
 * rx/ViewDrugPrice is therefore unreachable. The strict recorder reports the error; nothing is consumed.
 * Fixtures: the owned synthetic patient and one session-only custom drug card named with the run marker.
 * Implements gap-encounter "drug price on the prescription card" (rx/ViewDrugPrice had no check).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

async function workflow(s) {
  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
  const priceRequests = [];
  rx.on('request', request => {
    if (/\/rx\/ViewDrugPrice/.test(request.url())) priceRequests.push(request);
  });
  let key;

  await s.step('Custom Drug stages a card with a Qty/Mitte box', async () => {
    key = await stageCustomDrug(rx, `${s.marker}-price`);
    h.assert(await rx.locator(`#quantity_${key}`).count() === 1, 'The staged card has no Qty/Mitte box');
  });

  await s.step('typing a quantity and leaving the box requests the price without a script error', async () => {
    await rx.locator(`#quantity_${key}`).fill('30');
    await rx.locator(`#quantity_${key}`).blur();
    const deadline = Date.now() + 8000;
    while (priceRequests.length === 0 && Date.now() < deadline) await rx.waitForTimeout(150);
    // The strict recorder reports "Insertion is not defined" when this step ends; the assertions below
    // state the correct behaviour for the case where it does not.
    h.assert(priceRequests.length >= 1, 'Leaving the Qty box sent no rx/ViewDrugPrice request');
    const request = priceRequests[0];
    const url = new URL(request.url());
    h.assert(request.method() === 'GET' && url.searchParams.get('qty') === '30' && url.searchParams.get('randomId') === key,
      'The price request does not carry the quantity and the card id');
    // Bounded: a request that never completes would otherwise hang until the suite kills the child before cleanup runs.
    const response = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Timed out waiting for rx/ViewDrugPrice response')), 30000);
      request.response().then(
        value => { clearTimeout(timer); resolve(value); },
        error => { clearTimeout(timer); reject(error); },
      );
    });
    h.assert(response && response.status() < 400, 'The price request was refused');
    h.assert(await rx.locator(`#quantity_${key}`).inputValue() === '30', 'The card lost the typed quantity');
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-quantity-price', workflow, { openPatient: true });
module.exports = { workflow };
