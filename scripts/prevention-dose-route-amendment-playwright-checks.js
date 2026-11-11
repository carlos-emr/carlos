#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 7: changing a recorded vaccination's prefilled dose, dose unit and route by hand.
 * prevention-brand-picker checks the picker's prefill and the first save; nothing changed those values afterwards.
 * User path: E-Chart ▸ Preventions ▸ "Pick vaccine brand/generic" ▸ ADACEL (Tdap) ▸ Completed, date, lot, site,
 * comment ▸ Save; the record ▸ Dose 1, unit vial, Route Subcutaneous ▸ Save; the record (reopened); Enable Print ▸
 * Print; LAST, the record ▸ unit left blank, Dose 0.5 ▸ Save; the record ▸ Save unchanged.
 * Asserts: the picker prefills ADACEL's catalogue dose, unit, route, DIN and manufacturer, and the save stores them
 * with the lot, site and comment. The amendment stores "1 vial" and SC, archives the original still holding "0.5 mL"
 * and IM, and keeps the vaccine name, DIN, manufacturer, lot, site and comment. The reopened form shows 1, vial and
 * Subcutaneous, its summary says "Route: SC" and "Dose: 1 vial", and the printout says "Dose Administered: 1 vial"
 * with the vaccine and lot (the printout has no route line: PreventionPrintLoader.Entry carries none). LAST, a dose
 * saved with no unit is stored as entered, reopens with that dose in the Dose box, and is still stored after an
 * unchanged save; that last assertion has a step of its own (finding 322, issue #5431).
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup. SKIP when the
 * install replaces the brand picker with the CVC catalogue (cvc.url set), or Poppler pdftotext is missing.
 */
const h = require('./lib/playwright-harness');
const { requirePoppler } = require('./lib/export-content-helpers');
const {
  enterLot, openPreventionIndex, ownPatientPreventions, preventionForms, printPreventions, printedIncludes,
} = require('./lib/prevention-coverage');
const { runWorkflow } = require('./lib/workflow-session');

const TYPE = 'Tdap';
const BRAND = 'ADACEL';
const GIVEN_ON = '2026-05-06';
const SITE = 'Deltoid Lt';

async function workflow(s) {
  const { context, config, marker } = s;
  ownPatientPreventions(s);
  const index = await openPreventionIndex(s);
  const picker = index.locator('#immunization');
  if (await picker.count() === 0) {
    throw new h.SkipCheck('This install offers the CVC catalogue (cvc.url) instead of the brand picker this check drives');
  }
  const brands = await (await context.request.get(h.appUrl(config.baseUrl, '/prevention/vaccine-brands.json'))).json();
  const brand = brands.find(entry => entry.name === TYPE && entry.value === BRAND);
  h.assert(brand && brand.dose === '0.5' && brand.units === 'mL' && brand.route === 'IM',
    'vaccine-brands.json no longer lists ADACEL as a 0.5 mL intramuscular Tdap');
  const forms = preventionForms(s, index, TYPE);
  const lot = `PWLOT${marker.slice(-8)}`;
  const comment = `${marker} dose and route`;
  const kept = { name: BRAND, din: brand.din, manufacture: brand.manufacture, lot, location: SITE, comments: comment };
  const changedDetails = (id) => Object.entries(kept).filter(([key, value]) => forms.ext(id, key) !== value).map(([key]) => key);
  let id;
  let editor;

  await s.step('the brand picker records ADACEL with its catalogue dose, unit and route', async () => {
    await picker.click();
    await picker.pressSequentially(BRAND, { delay: 30 });
    const choice = index.locator('#immunization_choices .ac-item', { hasText: BRAND }).filter({ hasNotText: 'POLIO' }).first();
    await choice.waitFor({ state: 'visible' });
    editor = await s.popup(index, choice, 'prevention-editor');
    await editor.waitForURL(/\/prevention\/ViewAddPreventionData\?/, { waitUntil: 'domcontentloaded' });
    h.assert(await editor.locator('[name="prevention"]').inputValue() === TYPE, `The picker opened a form for another type than ${TYPE}`);
    const prefill = {
      name: await editor.locator('#name').inputValue(),
      din: await editor.locator('#din').inputValue(),
      dose: await editor.locator('#dose').inputValue(),
      unit: await editor.locator('select[name="doseUnit"]').inputValue(),
      route: await editor.locator('#route').inputValue(),
      manufacture: await editor.locator('#manufacture').inputValue(),
    };
    const expected = { name: BRAND, din: brand.din, dose: '0.5', unit: 'mL', route: 'IM', manufacture: brand.manufacture };
    h.assert(JSON.stringify(prefill) === JSON.stringify(expected), `The picker prefilled ${JSON.stringify(prefill)}`);
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill(GIVEN_ON);
    await enterLot(editor, lot);
    await editor.locator('#location').selectOption(SITE);
    await editor.locator('[name="comments"]').fill(comment);
    id = await forms.saveNew(editor);
    h.assert(forms.ext(id, 'dose') === '0.5 mL' && forms.ext(id, 'route') === 'IM',
      `The prefilled dose and route were stored as ${forms.ext(id, 'dose')} / ${forms.ext(id, 'route')}`);
    h.assert(!changedDetails(id).length, `The first save did not store: ${changedDetails(id).join(', ')}`);
  });

  await s.step('changing the dose, unit and route stores them, archives the original and keeps the vaccine details', async () => {
    editor = await forms.open(id);
    await editor.locator('#dose').fill('1');
    await editor.locator('select[name="doseUnit"]').selectOption('vial');
    await editor.locator('#route').selectOption('SC');
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    h.assert(forms.ext(id, 'dose') === '1 vial', `The amended dose was stored as ${forms.ext(id, 'dose')}`);
    h.assert(forms.ext(id, 'route') === 'SC', `The amended route was stored as ${forms.ext(id, 'route')}`);
    h.assert(!changedDetails(id).length, `The amendment changed: ${changedDetails(id).join(', ')}`);
    h.assert(forms.ext(previous, 'dose') === '0.5 mL' && forms.ext(previous, 'route') === 'IM',
      'The archived original no longer holds the prefilled dose and route');
  });

  await s.step('the reopened form, its summary and the printout show the amended dose and route', async () => {
    editor = await forms.open(id);
    h.assert(await editor.locator('#dose').inputValue() === '1', `The reopened dose is ${await editor.locator('#dose').inputValue()}`);
    h.assert(await editor.locator('select[name="doseUnit"]').inputValue() === 'vial', 'The reopened unit is not vial');
    h.assert(await editor.locator('#route').inputValue() === 'SC', 'The reopened route is not Subcutaneous');
    const summary = await editor.locator('#summary').inputValue();
    for (const line of ['Route: SC', 'Dose: 1 vial', `Lot: ${lot}`, `Name: ${BRAND}`]) {
      h.assert(summary.includes(line), `The reopened summary lacks "${line}"`);
    }
    await editor.close();
    const printed = await printPreventions(s, index);
    for (const phrase of ['Dose Administered: 1 vial', `Name of Vaccine: ${BRAND}`, `Lot#: ${lot}`, GIVEN_ON]) {
      h.assert(printedIncludes(printed, phrase), `The printout lacks "${phrase}"`);
    }
    h.assert(!printedIncludes(printed, 'Dose Administered: 0.5 mL'), 'The printout shows the archived dose');
  });

  // The known defect (finding 322) is asserted in a step of its own, after the steps that act on the page, so a
  // failure while saving or reopening is reported under those steps and never mistaken for the known defect.
  await s.step('a dose saved with no unit is stored as entered', async () => {
    editor = await forms.open(id);
    await editor.locator('#dose').fill('0.5');
    await editor.locator('select[name="doseUnit"]').selectOption('');
    id = await forms.saveAmendment(editor, id);
    h.assert(forms.ext(id, 'dose') === '0.5', `A dose with no unit was stored as ${forms.ext(id, 'dose')}`);
  });

  let reopened;
  let resaved;
  await s.step('the record with a unitless dose reopens and is saved again unchanged', async () => {
    editor = await forms.open(id);
    reopened = await editor.locator('#dose').inputValue();
    id = await forms.saveAmendment(editor, id);
    resaved = forms.ext(id, 'dose');
  });

  await s.step('a dose saved with no unit reopens with that dose and survives an unchanged save', async () => {
    h.assert(reopened === '0.5' && resaved === '0.5',
      `A dose stored as "0.5" with no unit reopened as "${reopened}", and an unchanged save stored "${resaved}"`);
  });
}

if (require.main === module) {
  runWorkflow('prevention-dose-route-amendment', workflow, { preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow };
