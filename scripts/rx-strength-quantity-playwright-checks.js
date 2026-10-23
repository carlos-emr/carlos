#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #3876: Schedule > patient master record > E-Chart > Rx > Custom Drug >
 * Instructions > Save. Verify the parser's AJAX response, displayed quantity,
 * and persisted prescription. A custom drug has no product strength to divide
 * by, so a strength defaults to one unit per dose and a strength range keeps
 * its proportions; an explicit count, volume or gram/IU per-dose amount keeps
 * its value, a vehicle volume is not a dose, and a concentration alone
 * calculates nothing and leaves the card's quantity as it was. The quantity
 * update after a 1000-unit dose derives the duration, a spaced range, written
 * daily counts, the n/7 shorthand, a trailing ", take with food", a second
 * day word before the duration and a "then" taper are read; "grams" is the
 * same unit as "g", "international units" the same as "IU", and a method word
 * after a product name ("Amoxicillin 250 mg/5 mL: take 7.5 mL") opens the dose.
 * A product count in parentheses needs no form word, a date after the frequency
 * ("until 11/12", "starting 3/15") is neither a duration nor a dose, an amount
 * of time ("30 min before meals") is skipped for the dose written after it, and
 * a later taper segment keeps the first segment's method ("Apply ... then 2 g
 * daily") (PR #5077 review).
 * Uses an owned synthetic patient and removes its prescription rows after
 * the browser closes; do not run concurrently with other checks.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageCustomDrugSettled, saveOnly, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

/** Compares a parsed dose with the expected value, allowing for the float the server sends for a fraction. */
function sameDose(parsed, expected) {
  return Math.abs(Number(parsed) - expected) < 1e-6;
}

/** True for the page's POST to the given Rx route whose form body carries the given action. */
function isRxActionPost(response, route, action) {
  const request = response.request();
  return request.method() === 'POST' && h.pathOnly(response.url()).endsWith(route)
    && new URLSearchParams(request.postData() || '').get('action') === action;
}

/** Verifies parsed and displayed dose quantities before Save, then checks the persisted original prescription. */
async function workflow(s) {
  s.cleanup(() => clearOwnedPrescriptionRows(s.sql, s.patient, s.marker));
  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-strength-quantity');
  // The first case edits the card at once; a late staging response would otherwise replace it.
  const key = await stageCustomDrugSettled(rx, `${s.marker}-strength`);

  // The dose text used to render 1000 as "1,000", which made the quantity update throw; the
  // AJAX path swallowed it and the card never derived a duration from the typed quantity.
  await s.step('updating the quantity after a 1000-unit dose derives a 30-day duration', async () => {
    await rx.locator(`#instructions_${key}`).fill('Take 1000 units po daily');
    await Promise.all([
      rx.waitForResponse(r => isRxActionPost(r, '/rx/UpdateScript', 'parseInstructions')),
      rx.locator(`label[for="jsonDxSearch_${key}"]`).click(),
    ]);
    const quantity = rx.locator(`#quantity_${key}`);
    await quantity.fill('30000');
    const [response] = await Promise.all([
      rx.waitForResponse(r => isRxActionPost(r, '/rx/WriteScript', 'updateQty')),
      quantity.blur(),
    ]);
    h.assert(response.status() === 200, `Quantity update answered HTTP ${response.status()}`);
    const updated = await response.json();
    h.assert(sameDose(updated.takeMax, 1000) && String(updated.duration) === '30' && updated.durationUnit === 'D'
      && String(updated.calQuantity) === '30000', `Incorrect derived duration: ${JSON.stringify(updated)}`);
    await rx.waitForFunction(({ key, text }) => document.getElementById(`duration_${key}`).textContent === text,
      { key, text: '30' });
  });

  // [instruction, takeMin, takeMax, calQuantity, frequency = 'TID', duration = '7']
  // A '0' calQuantity means the page must keep the quantity the card already shows.
  const cases = [
    ['1000 mg po tid x 7 days', 1, 1, '21'],
    ['Take 5mg po tid x 7 days', 1, 1, '21'],
    ['Take 5 mL po tid x 7 days', 5, 5, '105'],
    ['Take 5mL po tid x 7 days', 5, 5, '105'],
    ['Take 5-10mL po tid x 7 days', 5, 10, '210'],
    ['20 units po tid x 7 days', 20, 20, '420'],
    ['Apply 2g BID x 7 days', 2, 2, '28', 'BID'],
    ['Rub well in 2 g BID x 7 days', 2, 2, '28', 'BID'],
    ['Apply 1/2g BID x 7 days', 0.5, 0.5, '7', 'BID'],
    ['2g TOP. BID x 7 days', 2, 2, '28', 'BID'],
    ['20 IU SC BID x 7 days', 20, 20, '280', 'BID'],
    ['10-20IU IM BID x 7 days', 10, 20, '280', 'BID'],
    ['20 mEq IV daily x 3 days', 20, 20, '60', 'OD', '3'],
    ['20 IU per dose PO BID x 7 days', 20, 20, '280', 'BID'],
    ['20IU/dose SL BID x 7 days', 20, 20, '280', 'BID'],
    ['20 IU qhs x 7 days', 20, 20, '140', 'QHS'],
    ['20IU IV qhs x 7 days', 20, 20, '140', 'QHS'],
    ['Take 17 g per dose PO daily x 7 days', 17, 17, '119', 'OD'],
    ['Take 17g/dose PO daily x 7 days', 17, 17, '119', 'OD'],
    ['500 mg per dose PO BID x 7 days', 1, 1, '14', 'BID'],
    ['100IU/mL SC BID x 7 days', 0, 0, '0', 'BID'],
    ['100IU/5mL IM BID x 7 days', 0, 0, '0', 'BID'],
    ['100IU per mL SC BID x 7 days', 0, 0, '0', 'BID'],
    ['100units/mL SC BID x 7 days', 0, 0, '0', 'BID'],
    ['250mg/5mL PO TID x 7 days', 0, 0, '0'],
    ['Take 250 mg per 5 mL TID x 7 days', 0, 0, '0'],
    ['Mix medication in 100 mL BID x 7 days', 0, 0, '0', 'BID'],
    ['100 mg/5mL PO 10mL TID x 7 days', 10, 10, '210'],
    ['1000 mg PO 2 tablets TID x 7 days', 2, 2, '42'],
    ['250mg/5mL PO 5-10 mL TID x 7 days', 5, 10, '210'],
    ['100units/mL SC 0.5mL BID x 7 days', 0.5, 0.5, '7', 'BID'],
    ['20units SC BID x 7 days', 20, 20, '280', 'BID'],
    ['Take 0.5mL po OD x 7 days', 0.5, 0.5, '4', 'OD'],
    ['0.5IU SC OD x 7 days', 0.5, 0.5, '4', 'OD'],
    ['5/6 tab PO daily x 6 days', 5 / 6, 5 / 6, '5', 'OD', '6'],
    ['5/3 tab PO TID x 1 days', 5 / 3, 5 / 3, '5', 'TID', '1'],
    ['500-1000 mg po tid x 7 days', 1, 2, '42'],
    ['1000 mg (2 tabs) po tid x 7 days', 2, 2, '42'],
    ['Take 1 1/2 TID x 10 days', 1.5, 1.5, '45', 'TID', '10'],
    ['1 g IV in 100 mL Q8H x 7 days', 1, 1, '21', 'Q8H'],
    ['Take 2 tablets (1000 mg) po tid x 7 days', 2, 2, '42'],
    ['1000 mg po 1-2 tid x 7 days', 1, 2, '42'],
    ['Take 1 - 2 tabs po bid x 7 days', 1, 2, '28', 'BID'],
    ['Take 1 tab 3 times daily x 7 days', 1, 1, '21'],
    ['Take 1 tab 2x daily x 7 days', 1, 1, '14', 'BID'],
    ['Take 1 tab PO BID x 5/7', 1, 1, '10', 'BID', '5'],
    ['1 tab po bid x 7 days, take with food', 1, 1, '14', 'BID'],
    ['Take 1 tab po on alternate days x 10 days with food', 1, 1, '0', '', '10'],
    ['Take 2 tabs BID x 5 days then 1 tab BID x 5 days', 2, 2, '30', 'BID', '10'],
    ['Take 2 tabs po bid x 3 days then 1 tab po bid x 3 days then stop', 2, 2, '18', 'BID', '6'],
    ['Take 2 grams po tid x 7 days', 1, 1, '21'],
    ['Apply 2 grams BID x 7 days', 2, 2, '28', 'BID'],
    ['Amoxicillin 250 mg/5 mL: take 7.5 mL po tid x 10 days', 7.5, 7.5, '225', 'TID', '10'],
    ['1000 international units po daily x 30 days', 1, 1, '30', 'OD', '30'],
    ['1000 mg (2 x 500 mg) po tid x 7 days', 2, 2, '42'],
    ['1000 mg po starting 3/15 bid x 30 days', 1, 1, '60', 'BID', '30'],
    ['Take 1 tab po daily until 11/12', 1, 1, '0', 'OD', '0'],
    ['Take 1 1/2 hr before meals 1 tab po bid x 7 days', 1, 1, '14', 'BID'],
    ['Take 30 min before meals 1 tab po bid x 7 days', 1, 1, '14', 'BID'],
    ['Apply 2 g bid x 2 weeks then 2 g daily x 2 weeks', 2, 2, '84', 'BID', '28'],
    ['1000 mg po tid x 7 days', 1, 1, '21'],
  ];
  let displayed = null;
  for (const [instruction, minimum, maximum, quantity, frequency = 'TID', duration = '7'] of cases) {
    const expected = quantity === '0' ? displayed : quantity;
    const label = quantity === '0'
      ? `parsing ${instruction} calculates nothing and keeps quantity ${displayed}`
      : `parsing ${instruction} calculates ${quantity} units`;
    await s.step(label, async () => {
      await rx.locator(`#instructions_${key}`).fill(instruction);
      const [response] = await Promise.all([
        rx.waitForResponse(r => isRxActionPost(r, '/rx/UpdateScript', 'parseInstructions')),
        rx.locator(`label[for="jsonDxSearch_${key}"]`).click(),
      ]);
      h.assert(response.status() === 200, `Instruction parse answered HTTP ${response.status()}`);
      const parsed = await response.json();
      h.assert(sameDose(parsed.takeMin, minimum) && sameDose(parsed.takeMax, maximum)
        && String(parsed.frequency).toUpperCase() === frequency && String(parsed.duration) === duration
        && parsed.durationUnit === 'D' && String(parsed.calQuantity) === quantity,
      `Incorrect parsed dose/quantity: ${JSON.stringify(parsed)}`);
      h.assert(expected !== null, 'The first case must calculate a quantity so later cases can check preservation');
      // The page's handler always rewrites the card's minimum dose; once it has, the quantity
      // field holds the calculated value for a nonzero result and is untouched for zero.
      await rx.waitForFunction(({ key, text }) => document.getElementById(`minimum_${key}`).textContent === text,
        { key, text: String(parsed.takeMin) });
      h.assert(await rx.locator(`#quantity_${key}`).inputValue() === expected,
        `Displayed quantity is not ${expected}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`) === '0',
        'Editing instructions saved a prescription before Save');
    });
    displayed = expected;
  }

  await s.step('Save stores one unit per dose and quantity 21 for the strength instruction', async () => {
    await saveOnly(rx);
    await rx.locator(`#set_${key}`).waitFor({ state: 'detached' });
    const rows = s.sql.rows(`SELECT takemin,takemax,freqcode,duration,durunit,quantity,special
      FROM drugs WHERE demographic_no=${s.patient} AND customName=${h.sqlString(`${s.marker}-strength`)}`);
    h.assert(rows.length === 1, 'Save did not create exactly one owned prescription');
    const [minimum, maximum, frequency, duration, unit, quantity, special] = rows[0];
    h.assert(Number(minimum) === 1 && Number(maximum) === 1 && frequency.toUpperCase() === 'TID'
      && duration === '7' && unit === 'D' && quantity === '21'
      && special.includes('1000 mg po tid x 7 days'), 'Saved dose, instructions, or quantity is incorrect');
  });
}

if (require.main === module) runWorkflow('rx-strength-quantity', workflow);
module.exports = { workflow };
