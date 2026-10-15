/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');

/** Check trusted pdftotext -bbox output without exposing PDF content in diagnostics. */
function assertStackedRxPdf(xml, { patientLastWord, pharmacyFax, drugWord }) {
  const pages = Array.from(xml.matchAll(/<page\b[^>]*>([\s\S]*?)<\/page>/g));
  assert.equal(pages.length, 1, 'The single-drug narrow-paper fixture must fit one page');
  const words = Array.from(pages[0][1].matchAll(/<word\b([^>]*)>([^<]*)<\/word>/g), match => {
    const coordinate = pattern => {
      const value = match[1].match(pattern);
      assert(value && value[1].trim(), 'PDF word is missing a coordinate');
      const number = Number(value[1]);
      assert(Number.isFinite(number), 'PDF word has an invalid coordinate');
      return number;
    };
    return { text: match[2], top: coordinate(/\byMin="([^"]+)"/), bottom: coordinate(/\byMax="([^"]+)"/) };
  });
  const uniqueWord = (text, label) => {
    const found = words.filter(word => word.text === text);
    assert.equal(found.length, 1, `Missing or ambiguous ${label} geometry marker`);
    return found[0];
  };
  const patient = uniqueWord(patientLastWord, 'patient');
  const pharmacyStart = uniqueWord('ATTENTION:', 'pharmacy heading');
  const pharmacyEnd = uniqueWord(pharmacyFax, 'pharmacy footer');
  const drug = uniqueWord(drugWord, 'drug');
  assert(pharmacyStart.top > patient.bottom, 'Pharmacy overlaps the patient header');
  assert(pharmacyEnd.bottom >= pharmacyStart.bottom, 'Pharmacy block ordering is invalid');
  assert(drug.top > pharmacyEnd.bottom, 'Drug text overlaps the pharmacy block');
}
module.exports = { assertStackedRxPdf };
