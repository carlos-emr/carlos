/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { LANGUAGES, parseInitialHeader, readInitialChartHeader, assertInitialHeader, readChartRequestLanguage } =
  require('./encounter-header-i18n-playwright-checks');

function snapshot(language = 'fr', { empty = false } = {}) {
  const document = {
    documentElement: { getAttribute: () => language },
    getElementById: () => empty ? null : { getAttribute: () => language },
    querySelectorAll: selector => selector.includes('.label')
      ? [{ parentElement: { id: 'patient-sex' }, textContent: language === 'fr' ? 'Sexe' : 'Sex' }]
      : [{ textContent: language === 'fr' ? 'calculatrices' : 'calculators' }],
  };
  class DOMParser {
    parseFromString(html, type) { assert.equal(type, 'text/html'); return document; }
  }
  return vm.runInNewContext(`(${parseInitialHeader.toString()})('<html>')`, { DOMParser });
}

test('shouldRejectMissingHeader_whenInitialResponseIsNotAChart', () => {
  assert.equal(snapshot('fr', { empty: true }), null);
  assert.throws(() => assertInitialHeader(LANGUAGES[0], null, snapshot()), /missing/);
});

test('shouldDetectEnglishInitialResponse_whenLaterScriptsTranslateTheHeader', () => {
  const initial = snapshot('en');
  assert.throws(() => assertInitialHeader(LANGUAGES[0], initial, snapshot('fr')), /changed after its initial render/);
});

test('shouldAcceptFrenchInitialResponse_whenHeaderRemainsStable', () => {
  const initial = snapshot();
  assert.doesNotThrow(() => assertInitialHeader(LANGUAGES[0], initial, initial));
  initial.lang.header = 'en';
  assert.throws(() => assertInitialHeader(LANGUAGES[0], initial, initial), /negotiated/);
});

test('shouldReadTheOriginalNavigationResponse_withoutReissuingTheRequest', async () => {
  const frame = {};
  const html = '<html lang="fr"><script>throw new Error("must remain inert")</script></html>';
  const page = { mainFrame: () => frame, evaluate: async (parser, received) => {
    assert.equal(parser, parseInitialHeader);
    assert.equal(received, html);
    return 'snapshot';
  } };
  const request = { frame: () => frame, response: async () => ({ status: () => 200,
    headers: () => ({ 'content-type': 'text/html;charset=UTF-8' }), text: async () => html }) };
  assert.equal(await readInitialChartHeader([request], page), 'snapshot');
  await assert.rejects(() => readInitialChartHeader([request, request], page), /exactly one/);
  request.response = async () => ({ status: () => 302 });
  await assert.rejects(() => readInitialChartHeader([request], page), /successful HTML/);
});

test('shouldReadOnlyChartLanguage_whenOtherPagesAlsoNavigate', async () => {
  const frame = {};
  const page = { mainFrame: () => frame };
  const chart = { frame: () => frame, allHeaders: async () => ({ 'accept-language': 'fr-CA,fr;q=0.9', cookie: 'secret' }) };
  assert.equal(await readChartRequestLanguage([{ frame: () => ({}) }, chart], page), 'fr-CA,fr;q=0.9');
  await assert.rejects(() => readChartRequestLanguage([chart, chart], page), /exactly one/);
});

test('shouldRunNativeFrenchFirst_withoutAHeaderRewrite', () => {
  assert.equal(LANGUAGES[0].nativeLanguage, true);
  assert.equal(LANGUAGES[0].tag, 'fr-CA');
  assert.equal(LANGUAGES[0].acceptLanguage, undefined);
  assert.ok(LANGUAGES.some(language => language.acceptLanguage === 'de-DE,fr;q=0.9,en;q=0.8'));
});
