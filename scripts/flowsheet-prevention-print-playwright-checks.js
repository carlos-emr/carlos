#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #3884: prevention checkboxes post no printStyle. Preview and print must
// retain all their readings, while unchecked items and measurement filters work.
// Uses an owned synthetic patient; all seeded clinical rows are removed.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, provider } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM preventionsExt WHERE prevention_id IN
      (SELECT id FROM preventions WHERE demographic_no=${patient});
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})`) === '0',
    'Owned prevention and measurement rows were not removed');
  });
  for (const [type, date] of [['Flu', '2025-09-01'], ['Flu', '2026-09-01'], ['Pneumovax', '2026-09-02']]) {
    sql.execute(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,
      prevention_type,deleted,refused,never,lastUpdateDate) VALUES (${patient},NOW(),${h.sqlString(date)},
      ${h.sqlString(provider)},${h.sqlString(type)},'0','0','0',NOW())`);
  }
  const readings = ['6.1', '6.2', '6.3', '6.4'];
  for (const [index, value] of readings.entries()) {
    sql.execute(`INSERT INTO measurements (type,demographicNo,providerNo,dataField,measuringInstruction,
      comments,dateObserved,dateEntered) VALUES ('A1C',${patient},${h.sqlString(provider)},
      ${h.sqlString(value)},'','',${h.sqlString(`2026-09-0${index + 1}`)},NOW())`);
  }

  // The diagnosis-dependent sheet is opened explicitly for this synthetic patient.
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl,
    `/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no=${patient}&template=diab2`);
  await h.assertNotErrorPage(page, 'Diabetes flowsheet');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    page.getByRole('link', { name: /Custom Print/i }).click(),
  ]);
  await h.assertNotErrorPage(page, 'Custom Print');
  const selectionUrl = page.url();
  const section = (label) => page.locator('.preventionSection').filter({
    has: page.locator('.headPrevention span').getByText(label, { exact: true }),
  });
  const flu = () => section('Flu Vaccine');
  const pneumonia = () => section('Pneumococcal vaccine');
  const a1c = () => page.locator('.preventionSection').filter({ hasText: '6.4' });
  const visibleReadings = () => a1c().locator('.preventionProcedure:visible');

  async function select(label) {
    await page.locator(`label[for="printHP${label}"]`).click();
    h.assert(await page.locator(`[id="printHP${label}"]`).isChecked(), `${label} was not selected`);
  }
  async function preview() {
    const responsePromise = page.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/ViewTemplateFlowSheetPrint'));
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.locator('button.preview').click(),
    ]);
    const response = await responsePromise;
    h.assert(response.status() === 200, 'Preview was refused');
    await h.assertNotErrorPage(page, 'flowsheet preview');
    return new URLSearchParams(response.request().postData());
  }
  async function reopen() {
    await page.emulateMedia({ media: 'screen' });
    await page.goto(selectionUrl, { waitUntil: 'networkidle' });
    await h.assertNotErrorPage(page, 'Custom Print selection');
  }

  await s.step('selected prevention without a print style retains its complete history in preview and print', async () => {
    h.assert(await flu().count() === 1 && await pneumonia().count() === 1
      && await flu().isVisible() && await pneumonia().isVisible(),
    'The selection page must list both prevention sections before testing their visibility');
    h.assert(await page.locator('[name="printStyleFlu"]').count() === 0,
      'Precondition: Flu unexpectedly has a print-style control');
    await select('Flu');
    await select('A1C');
    const posted = await preview();
    h.assert(posted.getAll('printHP').includes('Flu') && !posted.has('printStyleFlu'),
      'The browser did not submit the missing-style prevention request');
    h.assert(await flu().isVisible(), 'Selected Flu prevention is missing from preview');
    h.assert(await flu().locator('.preventionProcedure:visible').count() === 2,
      'Preview lost part of the selected prevention history');
    for (const date of ['2025-09-01', '2026-09-01']) {
      h.assert((await flu().innerText()).includes(date), `Preview lost prevention date ${date}`);
    }
    h.assert(!await pneumonia().isVisible(), 'Preview included an unchecked prevention');
    h.assert(posted.get('printStyleA1C') === 'all' && await visibleReadings().count() === 4,
      'Explicit all no longer prints every measurement');
    await page.emulateMedia({ media: 'print' });
    h.assert(await flu().isVisible() && await flu().locator('.preventionProcedure:visible').count() === 2,
      'Print media hides the selected prevention history');
    h.assert(!await pneumonia().isVisible(), 'Print media included an unchecked prevention');
  });

  await s.step('numeric measurement filtering still hides older readings alongside a selected prevention', async () => {
    await reopen();
    await select('Flu');
    await select('A1C');
    await page.locator('[name="printStyleA1C"]').selectOption('num');
    await page.locator('[name="numEleA1C"]').fill('1');
    await preview();
    h.assert(await flu().isVisible(), 'Numeric measurement style dropped the selected prevention');
    h.assert(await visibleReadings().count() < 4 && await visibleReadings().count() > 0,
      'Numeric measurement filtering did not restrict the readings');
    h.assert(await a1c().locator('.preventionProcedure').filter({ hasText: '6.4' }).isVisible(),
      'Numeric filtering lost the latest reading');
    h.assert(!await a1c().locator('.preventionProcedure').filter({ hasText: '6.1' }).isVisible(),
      'Numeric filtering retained the oldest reading');
  });

  await s.step('date-range measurement filtering and unchecked preventions remain respected', async () => {
    await reopen();
    await select('A1C');
    await page.locator('[name="printStyleA1C"]').selectOption('range');
    await page.locator('[name="sDateA1C"]').fill('2026-09-02');
    await page.locator('[name="eDateA1C"]').fill('2026-09-03');
    await preview();
    h.assert(!await flu().isVisible() && !await pneumonia().isVisible(),
      'Preview included an unchecked prevention');
    h.assert(await visibleReadings().count() === 2, 'Date range did not restrict measurements');
    for (const value of readings) {
      const expected = value === '6.2' || value === '6.3';
      h.assert(await a1c().locator('.preventionProcedure').filter({ hasText: value }).isVisible() === expected,
        `Date-range visibility is wrong for ${value}`);
    }
  });
}

if (require.main === module) runWorkflow('flowsheet-prevention-print', workflow);
module.exports = { workflow };
