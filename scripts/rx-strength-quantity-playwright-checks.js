#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #3876: Schedule > patient master record > E-Chart > Rx > Custom Drug >
 * Instructions > Save. Verify the parser's AJAX response, displayed quantity,
 * and persisted prescription. Uses an owned synthetic patient and removes its
 * prescription rows after the browser closes; do not run concurrently with other checks.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageCustomDrug, saveOnly, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

/** Verifies parsed and displayed dose quantities before Save, then checks the persisted original prescription. */
async function workflow(s) {
  s.cleanup(() => clearOwnedPrescriptionRows(s.sql, s.patient, s.marker));
  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-strength-quantity');
  const key = await stageCustomDrug(rx, `${s.marker}-strength`);

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
    ['20 IU qhs x 7 days', 20, 20, '140', 'QHS'],
    ['20IU IV qhs x 7 days', 20, 20, '140', 'QHS'],
    ['100IU/mL SC BID x 7 days', 1, 1, '14', 'BID'],
    ['100IU/5mL IM BID x 7 days', 1, 1, '14', 'BID'],
    ['100IU per mL SC BID x 7 days', 1, 1, '14', 'BID'],
    ['100units/mL SC BID x 7 days', 1, 1, '14', 'BID'],
    ['20units SC BID x 7 days', 20, 20, '280', 'BID'],
    ['Take 0.5mL po OD x 7 days', 0.5, 0.5, '4', 'OD'],
    ['0.5IU SC OD x 7 days', 0.5, 0.5, '4', 'OD'],
    ['500-1000 mg po tid x 7 days', 1, 1, '21'],
    ['Take 2 tablets (1000 mg) po tid x 7 days', 2, 2, '42'],
    ['1000 mg po 1-2 tid x 7 days', 1, 2, '42'],
    ['1000 mg po tid x 7 days', 1, 1, '21'],
  ];
  for (const [instruction, minimum, maximum, quantity, frequency = 'TID'] of cases) {
    await s.step(`parsing ${instruction} calculates ${quantity} units`, async () => {
      await rx.locator(`#instructions_${key}`).fill(instruction);
      const [response] = await Promise.all([
        rx.waitForResponse(r => r.request().method() === 'POST'
          && h.pathOnly(r.url()).endsWith('/rx/UpdateScript')
          && new URLSearchParams(r.request().postData() || '').get('action') === 'parseInstructions'),
        rx.locator(`label[for="jsonDxSearch_${key}"]`).click(),
      ]);
      h.assert(response.status() === 200, `Instruction parse answered HTTP ${response.status()}`);
      const parsed = await response.json();
      h.assert(Number(parsed.takeMin) === minimum && Number(parsed.takeMax) === maximum
        && String(parsed.frequency).toUpperCase() === frequency && String(parsed.duration) === '7' && parsed.durationUnit === 'D'
        && String(parsed.calQuantity) === quantity, `Incorrect parsed dose/quantity: ${JSON.stringify(parsed)}`);
      await rx.waitForFunction(({ key, quantity }) => document.getElementById(`quantity_${key}`).value === quantity,
        { key, quantity });
      h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`) === '0',
        'Editing instructions saved a prescription before Save');
    });
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
