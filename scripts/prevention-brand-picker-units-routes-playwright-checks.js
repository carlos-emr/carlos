#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The brand picker's prefilled dose unit and route, for every unit and route the shipped catalogue uses. Found while
 * covering issue #5279 section 7: prevention-brand-picker checks one brand (Tdap ADACEL, mL and IM), whose values
 * the form happens to offer.
 * User path: E-Chart ▸ Preventions ▸ "Pick vaccine brand/generic" ▸ a brand, once per distinct pair of dose unit and
 * route in prevention/vaccine-brands.json; the Add Prevention popup is read and closed, never saved.
 * The form offers a fixed list of units (mL, mg, g, capsule, vial) and routes (ID, IM, IN, PO, SC) as selects, so a
 * prefilled value is kept only if it is one of those exactly, and a new record's unit starts at mL. The catalogue
 * also uses VIAL, DOSE, SQ, IV, Intranasal and Intradermal. The form must show the catalogue's own unit and route in
 * any letter case, or the form's standard code for the same route (SQ as SC, Intranasal as IN, Intradermal as ID):
 * never a blank box that saves nothing and never another value. Units the catalogue leaves blank are not judged.
 * Every pair is opened and every difference is reported together (finding 323, issue #5432).
 * Fixtures: the runWorkflow FAKE- patient. Nothing is saved. SKIP when the install replaces the brand picker with
 * the CVC catalogue (cvc.url).
 */
const h = require('./lib/playwright-harness');
const { openPreventionIndex } = require('./lib/prevention-coverage');
const { runWorkflow } = require('./lib/workflow-session');

/** The form's code for a route the catalogue spells another way; the same route either way. */
const ROUTE_CODES = Object.freeze({ SQ: 'SC', SUBCUTANEOUS: 'SC', INTRANASAL: 'IN', INTRADERMAL: 'ID', INTRAMUSCULAR: 'IM', ORAL: 'PO' });

/** Whether the form shows the catalogue's value: the same text in any case, or the same route's code. */
function showsCatalogueValue(what, given, shown) {
  const want = given.toUpperCase();
  const got = shown.toUpperCase();
  return got === want || (what === 'route' && got !== '' && got === ROUTE_CODES[want]);
}

/** One brand per distinct (unit, route) pair that names at least one of them, in catalogue order. */
function brandsByUnitAndRoute(brands) {
  const seen = new Map();
  for (const brand of brands) {
    const key = `${brand.units || ''}|${brand.route || ''}`;
    if (key !== '|' && !seen.has(key)) seen.set(key, brand);
  }
  return [...seen.values()];
}

async function workflow(s) {
  const { context, config } = s;
  const index = await openPreventionIndex(s);
  const picker = index.locator('#immunization');
  if (await picker.count() === 0) {
    throw new h.SkipCheck('This install offers the CVC catalogue (cvc.url) instead of the brand picker this check drives');
  }
  const brands = await (await context.request.get(h.appUrl(config.baseUrl, '/prevention/vaccine-brands.json'))).json();
  const samples = brandsByUnitAndRoute(brands);
  h.assert(samples.length > 1, 'vaccine-brands.json lists no units or routes');

  // Opening the forms and judging what they show are separate steps, so a failure of the picker or a popup is
  // reported under the first and never mistaken for the known defect (finding 323) the second asserts.
  const shown = [];
  await s.step('the brand picker opens the Add Prevention form for one brand of every unit and route pair', async () => {
    for (const brand of samples) {
      await picker.fill('');
      await picker.pressSequentially(brand.value.slice(0, 24), { delay: 10 });
      const choice = index.locator('#immunization_choices .ac-item')
        .filter({ has: index.getByText(brand.name, { exact: true }) })
        .filter({ hasText: brand.value }).first();
      await choice.waitFor({ state: 'visible' });
      // Every pick opens the same named window, so each popup is closed before the next pick.
      const editor = await s.popup(index, choice, 'prevention-editor');
      await editor.waitForURL(/\/prevention\/ViewAddPreventionData\?/, { waitUntil: 'domcontentloaded' });
      h.assert(await editor.locator('#name').inputValue() === brand.value, `The picker opened another brand than ${brand.value}`);
      shown.push({
        brand,
        unit: await editor.locator('select[name="doseUnit"]').inputValue(),
        route: await editor.locator('#route').inputValue(),
      });
      await editor.close();
    }
  });

  await s.step('every dose unit and route the catalogue prefills is shown by the Add Prevention form as given', async () => {
    const differences = [];
    for (const { brand, unit, route } of shown) {
      for (const [what, given, value] of [['unit', brand.units, unit], ['route', brand.route, route]]) {
        if (given && !showsCatalogueValue(what, given, value)) {
          differences.push(`${brand.value}: ${what} "${given}" shows as ${value ? `"${value}"` : 'blank'}`);
        }
      }
    }
    h.assert(!differences.length,
      `${differences.length} prefilled value(s) of ${samples.length} brands were dropped or replaced: ${differences.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('prevention-brand-picker-units-routes', workflow);
module.exports = { workflow, brandsByUnitAndRoute, showsCatalogueValue };
