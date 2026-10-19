#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): the chart's "General Conversions" calculator -- the unit converter a
 * clinician reaches for to turn a patient's weight in pounds into kilograms before dosing.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ header "Calculators" ▸ General
 * Conversions (opens encounter/calculators/ViewGeneralCalculators in its own window) ▸ type a value in
 * any unit box ▸ Calculate; Calibrate / reset; the Fahrenheit and Celsius boxes; About and License.
 * clinical-calculators covers the fracture, coronary and simple calculators; this converter has no
 * other coverage, and like them it is pure browser JavaScript, so a wrong factor does not look like a
 * failure -- it prints a plausible, wrong, number.
 *
 * Asserts, against values worked out from the exact definitions (1 lb = 0.45359237 kg, 1 in =
 * 0.0254 m, 1 US gal = 3.785411784 L, 1 imperial gal = 4.54609 L, troy lb = 0.3732417216 kg):
 * every distance, weight and volume box after one entry (a 154 lb adult reads 69.8532 kg), that
 * typing in another box makes THAT box the source, that Calibrate resets a form to its base unit,
 * that junk input is refused (validity message, no stale numbers) and that Fahrenheit and Celsius
 * convert both ways (98.6 F = 37 C, 100 C = 212 F, -40 is the same in both). Then About and License
 * (encounter/ViewAbout, encounter/ViewLicense) each open a populated window.
 *
 * Fixtures: the owned FAKE-PW patient (runWorkflow); nothing is saved. Coverage plan §2.5 calculators.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {openCalculator} = require('./clinical-calculators-playwright-checks');

/** [form index, typed box, value, expected {boxN: text}] */
const CONVERSIONS = [
  {why: 'distance from 1 metre', form: 0, box: 'val1', value: '1',
    expect: {val2: '39.37008', val3: '3.28084', val4: '1.093613', val5: '0.0006213712', val6: '0.0005399568'}},
  {why: 'distance from 12 inches is 1 foot', form: 0, box: 'val2', value: '12',
    expect: {val1: '0.3048', val3: '1', val4: '0.3333333', val5: '0.0001893939'}},
  {why: 'weight from 70 kg', form: 1, box: 'val1', value: '70',
    expect: {val2: '2469.18', val3: '154.324', val4: '187.546', val5: '11.0231', val6: '0.0771618', val7: '0.0688945'}},
  {why: 'weight from 154 pounds (a common dosing conversion)', form: 1, box: 'val3', value: '154',
    expect: {val1: '69.8532', val2: '2464', val4: '187.153', val5: '11', val6: '0.077', val7: '0.06875'}},
  {why: 'volume from 1 litre', form: 2, box: 'val1', value: '1',
    expect: {val2: '33.814', val3: '1.05669', val4: '0.264172', val5: '0.219969'}},
  {why: 'volume from 1 US gallon', form: 2, box: 'val4', value: '1',
    expect: {val1: '3.78541', val2: '128', val3: '4', val5: '0.832674'}},
];

async function workflow(s) {
  const chart = await s.chart();
  const page = await openCalculator(s.context, chart, 'General Conversions', s.recorder, 20000);
  const form = index => page.locator('form').nth(index);
  const read = async (index, name) => form(index).locator(`input[name="${name}"]`).inputValue();

  await s.step('every distance, weight and volume box shows the converted value after one entry', async () => {
    for (const c of CONVERSIONS) {
      await form(c.form).locator('input[type="button"]').first().click(); // Calibrate: start from the base unit
      await form(c.form).locator(`input[name="${c.box}"]`).fill(c.value);
      await form(c.form).locator('input[type="button"]').last().click(); // Calculate
      for (const [box, wanted] of Object.entries(c.expect)) {
        const shown = await read(c.form, box);
        h.assert(shown === wanted, `${c.why}: ${box} reads ${JSON.stringify(shown)}, expected ${wanted}`);
      }
    }
  });

  await s.step('typing in another box makes that box the source, and Calibrate resets to the base unit', async () => {
    await form(1).locator('input[name="val1"]').fill('70');
    await form(1).locator('input[type="button"]').last().click();
    h.assert(await read(1, 'val3') === '154.324', 'The weight form did not convert 70 kg');
    await form(1).locator('input[name="val3"]').fill('1');
    h.assert(await read(1, 'val1') === '', 'Typing in the pounds box did not clear the stale kilogram value');
    await form(1).locator('input[type="button"]').last().click();
    h.assert(await read(1, 'val1') === '0.453592', `1 lb should read 0.453592 kg, not ${JSON.stringify(await read(1, 'val1'))}`);
    await form(1).locator('input[type="button"]').first().click();
    h.assert(await read(1, 'val1') === '1' && await read(1, 'val3') === '2.20462', 'Calibrate did not reset the weight form to 1 kg');
  });

  await s.step('non-numeric input is refused with a validity message and no stale numbers', async () => {
    const f = form(0);
    await f.locator('input[type="button"]').first().click();
    await f.locator('input[name="val2"]').fill('abc');
    await f.locator('input[type="button"]').last().click();
    const message = await f.locator('input[name="val2"]').evaluate(el => el.validationMessage);
    h.assert(message !== '', 'Entering "abc" raised no validity message');
    h.assert(await read(0, 'val1') === '', 'A non-numeric entry still printed a metre value');
  });

  await s.step('Fahrenheit and Celsius convert both ways', async () => {
    const t = page.locator('form').nth(3);
    const f = t.locator('input[name="F"]');
    const c = t.locator('input[name="C"]');
    h.assert(await f.inputValue() === '32' && await c.inputValue() === '0', 'The temperature boxes do not start at 32 F = 0 C');
    await f.fill('98.6');
    await f.dispatchEvent('change');
    h.assert(await c.inputValue() === '37', `98.6 F should read 37 C, not ${await c.inputValue()}`);
    await c.fill('100');
    await c.dispatchEvent('change');
    h.assert(await f.inputValue() === '212', `100 C should read 212 F, not ${await f.inputValue()}`);
    await c.fill('-40');
    await c.dispatchEvent('change');
    h.assert(await f.inputValue() === '-40', `-40 C should read -40 F, not ${await f.inputValue()}`);
    await c.fill('37');
    await c.dispatchEvent('change');
    h.assert(await f.inputValue() === '98.6', `37 C should read 98.6 F, not ${await f.inputValue()}`);
  });

  await s.step('About and License open populated windows from the calculator', async () => {
    for (const label of ['About', 'License']) {
      const popup = await s.popup(page, page.locator('a').filter({hasText: new RegExp(`^${label}$`, 'i')}).first(), `calculator-${label}`);
      await popup.waitForLoadState('domcontentloaded');
      const text = (await popup.locator('body').innerText()).trim();
      h.assert(text.length > 20, `The ${label} window is empty`);
      await popup.close();
    }
  });
}

if (require.main === module) runWorkflow('gap-clinical-calculators-conversions', workflow, {openPatient: true});
module.exports = {workflow, CONVERSIONS};
