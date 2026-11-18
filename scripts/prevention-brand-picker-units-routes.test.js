/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { brandsByUnitAndRoute, showsCatalogueValue } = require('./prevention-brand-picker-units-routes-playwright-checks');

test('one brand is sampled per distinct unit and route, skipping brands that name neither', () => {
  const brands = [
    { value: 'A', units: 'mL', route: 'IM' }, { value: 'B', units: 'mL', route: 'IM' },
    { value: 'C', units: '', route: '' }, { value: 'D', units: 'VIAL', route: 'IM' }, { value: 'E', route: 'SQ' },
  ];
  assert.deepEqual(brandsByUnitAndRoute(brands).map(brand => brand.value), ['A', 'D', 'E']);
});

test('the form shows a catalogue value when it matches in any case or is the same route\'s code', () => {
  assert.ok(showsCatalogueValue('unit', 'VIAL', 'vial'));
  assert.ok(showsCatalogueValue('route', 'SQ', 'SC'));
  assert.ok(showsCatalogueValue('route', 'Intranasal', 'IN'));
  assert.ok(showsCatalogueValue('route', 'IM', 'IM'));
});

test('a blank box or another value is never the catalogue value', () => {
  assert.equal(showsCatalogueValue('unit', 'DOSE', 'mL'), false);
  assert.equal(showsCatalogueValue('unit', 'VIAL', ''), false);
  assert.equal(showsCatalogueValue('route', 'IV', ''), false);
  assert.equal(showsCatalogueValue('route', 'SQ', 'IM'), false);
  assert.equal(showsCatalogueValue('unit', 'SQ', 'SC'), false, 'route codes are not unit codes');
});
