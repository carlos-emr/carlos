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
 * that the quantity is kept on the card. Repeating the edit must send the new quantity as well.
 * The real endpoint handles the custom drug's unavailable price; native-helper Node regressions also
 * verify that returned prices replace earlier amounts and an unavailable price clears stale content.
 * Fixtures: the owned synthetic patient and one session-only custom drug card named with the run marker.
 * Implements gap-encounter "drug price on the prescription card" (rx/ViewDrugPrice had no check).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

async function workflow(s) {
  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
  let key;

  await s.step('Custom Drug stages a card with a Qty/Mitte box', async () => {
    key = await stageCustomDrug(rx, `${s.marker}-price`);
    h.assert(await rx.locator(`#quantity_${key}`).count() === 1, 'The staged card has no Qty/Mitte box');
  });

  for (const quantity of ['30', '60']) {
    await s.step(`leaving the Qty box requests price for ${quantity} without a script error`, async () => {
      await rx.locator(`#quantity_${key}`).fill(quantity);
      const responsePromise = rx.waitForResponse(response => {
        const url = new URL(response.url());
        return url.pathname.endsWith('/rx/ViewDrugPrice') && url.searchParams.get('randomId') === key
          && url.searchParams.get('qty') === quantity;
      }, { timeout: 10000 });
      const [response] = await Promise.all([responsePromise, rx.locator(`#quantity_${key}`).blur()]);
      h.assert(response.request().method() === 'GET', 'The price lookup did not use GET');
      h.assert(response.status() === 200, 'The price request was refused');
      h.assert(await response.finished() === null, 'The price response did not finish');
      h.assert(await rx.locator(`#quantity_${key}`).inputValue() === quantity, 'The card lost the typed quantity');
    });
  }
}

if (require.main === module) runWorkflow('gap-encounter-rx-quantity-price', workflow, { openPatient: true });
module.exports = { workflow };
