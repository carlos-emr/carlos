#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Exercise the packaged DrugRef catalogue, formulary, price endpoint and displayed Rx card.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareRx, saveOnly, stageExisting } = require('./lib/rx-ui-workflow');
const { stageFromSearch } = require('./rx-edit-discontinue-playwright-checks');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

const RAMIPRIL_DIN = '02477580';
const LIPITOR_DIN = '02230713';
const BLEXTEN_DIN = '02454130';

async function expectQuote(rx, key, amount) {
  await rx.waitForFunction(({ key, amount }) => document.getElementById(`cost_${key}`)?.textContent.trim() === amount,
    { key, amount });
  if (amount) h.assert(await rx.locator(`#cost_${key}`).isVisible(), 'The price is hidden');
}

function priceResponse(rx, din, key, quantity) {
  return rx.waitForResponse(response => {
    const url = new URL(response.url());
    return response.request().method() === 'GET' && url.pathname.endsWith('/rx/ViewDrugPrice')
      && url.searchParams.get('din') === din && (key == null || url.searchParams.get('randomId') === key)
      && url.searchParams.get('qty') === (quantity ?? '');
  });
}

async function editQuantity(rx, key, din, typed, quantity, amount) {
  const field = rx.locator(`#quantity_${key}`);
  await field.fill(typed);
  const [price, updated] = await Promise.all([
    priceResponse(rx, din, key, quantity),
    rx.waitForResponse(response => {
      const request = response.request();
      const params = new URLSearchParams(request.postData() || '');
      return request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/rx/WriteScript')
        && params.get('parameterValue') === 'updateDrug' && params.get('action') === 'updateQty'
        && params.get('randomId') === key && params.get('quantity') === typed;
    }),
    field.blur(),
  ]);
  h.assert(updated.ok(), 'The draft rejected the quantity');
  h.assert((await updated.json()).priceQuantity === quantity, 'The draft acknowledged the wrong dispense count for pricing');
  h.assert(price.ok(), 'The price request was refused');
  await expectQuote(rx, key, amount);
  h.assert(await field.inputValue() === typed, 'The card lost the typed quantity');
}

async function workflow(s) {
  const fixture = await prepareRx(s);
  const drugCount = () => s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`);
  let rx = await fixture.open();
  let key;

  await s.step('selecting a DrugRef product with a sub-dollar unit price immediately quotes the default quantity', async () => {
    const [response, staged] = await Promise.all([
      priceResponse(rx, RAMIPRIL_DIN, null, '30'),
      stageFromSearch(rx, 'RAMIPRIL', 'AG-RAMIPRIL 5MG'),
    ]);
    key = staged;
    h.assert(response.ok() && new URL(response.url()).searchParams.get('randomId') === key,
      'The initial price did not belong to the new card');
    h.assert(await rx.locator(`#quantity_${key}`).inputValue() === '30', 'The card lost its default quantity');
    await expectQuote(rx, key, '$2.45/30');
  });

  await s.step('manual edits and a directions calculation replace the price on the same card', async () => {
    await editQuantity(rx, key, RAMIPRIL_DIN, '60', '60', '$4.90/60');
    const directions = rx.locator(`#instructions_${key}`);
    await directions.fill('1 cap PO BID x 14 days');
    const [response] = await Promise.all([priceResponse(rx, RAMIPRIL_DIN, key, '28'), directions.blur()]);
    h.assert(response.ok(), 'The calculated quantity price was refused');
    await expectQuote(rx, key, '$2.29/28');
    h.assert(await rx.locator(`#quantity_${key}`).inputValue() === '28', 'Directions did not calculate 28 capsules');
    h.assert(drugCount() === '0', 'Editing the draft saved a prescription');
  });

  await s.step('ReRx quotes the saved quantity when the card opens', async () => {
    await saveOnly(rx);
    const [saved] = s.sql.rows(`SELECT drugid, quantity FROM drugs WHERE demographic_no=${s.patient}`);
    h.assert(saved?.[1] === '28' && drugCount() === '1', 'The calculated quantity was not saved once');
    await rx.close();
    rx = await fixture.open();
    key = await stageExisting(rx, saved[0]);
    h.assert(await rx.locator(`#quantity_${key}`).inputValue() === '28', 'ReRx lost the saved quantity');
    await expectQuote(rx, key, '$2.29/28');
  });

  await s.step('tablet counts produce totals and treatment durations show the unit price', async () => {
    key = await stageFromSearch(rx, 'LIPITOR 20', 'LIPITOR 20MG');
    await expectQuote(rx, key, '$80.22/30');
    await editQuantity(rx, key, LIPITOR_DIN, '60 tabs', '60', '$160.45/60');
    await editQuantity(rx, key, LIPITOR_DIN, '30 days', null, '$2.67 per unit');
    await editQuantity(rx, key, LIPITOR_DIN, '30 tabs', '30', '$80.22/30');
    await editQuantity(rx, key, LIPITOR_DIN, '30.0 tabs', '30', '$80.22/30');
  });

  await s.step('an unpriced DIN and a custom drug show no estimate', async () => {
    const [response, unpriced] = await Promise.all([
      priceResponse(rx, BLEXTEN_DIN, null, '30'),
      stageFromSearch(rx, 'BLEXTEN', 'BLEXTEN 20MG'),
    ]);
    h.assert(response.ok() && (await response.text()).trim() === '', 'An unpriced DIN returned a price fragment');
    await expectQuote(rx, unpriced, '');
    const queries = [];
    rx.on('request', request => {
      const url = new URL(request.url());
      if (url.pathname.endsWith('/rx/ViewDrugPrice')) queries.push(url.searchParams.get('randomId'));
    });
    const custom = await stageCustomDrug(rx, `${s.marker}-price`);
    await rx.waitForLoadState('networkidle');
    await expectQuote(rx, custom, '');
    h.assert(!queries.includes(custom), 'A card without a DIN attempted a price lookup');
    h.assert(drugCount() === '1', 'Pricing created an additional prescription row');
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-quantity-price', workflow, { openPatient: true });
module.exports = { workflow };
