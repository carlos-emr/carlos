/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { assertStackedRxPdf } = require('./lib/rx-pdf-layout');
const markers = { patientLastWord: '0000000000', pharmacyFax: '5550100100', drugWord: 'PW' };
const word = (text, top, bottom) => `<word xMin="15" yMin="${top}" xMax="100" yMax="${bottom}">${text}</word>`;
const page = (patientBottom = 180, pharmacyTop = 195, drugTop = 280) => `<page width="297" height="420">${
  word(markers.patientLastWord, patientBottom - 10, patientBottom) + word('ATTENTION:', pharmacyTop, pharmacyTop + 10)
  + word(markers.pharmacyFax, 250, 260) + word('PW', drugTop, drugTop + 10)}</page>`;
test('complete measured blocks in one page pass', () => assert.doesNotThrow(() => assertStackedRxPdf(page(), markers)));
test('the original fixed pharmacy coordinate overlaps a wrapped patient header', () => {
  assert.throws(() => assertStackedRxPdf(page(186, 175), markers), /Pharmacy overlaps the patient header/);
});
test('an unreserved pharmacy block overlaps the drug text', () => {
  assert.throws(() => assertStackedRxPdf(page(180, 195, 255), markers), /Drug text overlaps the pharmacy block/);
});
test('missing geometry and an overflow to another page cannot pass', () => {
  assert.throws(() => assertStackedRxPdf(page().replace('ATTENTION:', 'MISSING'), markers), /pharmacy heading geometry marker/);
  assert.throws(() => assertStackedRxPdf(page() + page(), markers), /must fit one page/);
  assert.throws(() => assertStackedRxPdf(page().replace('yMin="170"', 'yMin="NaN"'), markers), /invalid coordinate/);
});
