/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { LANGUAGES, installInitialHeaderObserver, assertInitialHeader, readChartRequestLanguage } =
  require('./encounter-header-i18n-playwright-checks');

function observe() {
  const state = { labels: [], calculators: [], header: null, lang: 'fr-CA' };
  const window = {};
  let callback;
  let disconnected = false;
  class MutationObserver {
    constructor(fn) { callback = fn; }
    observe() {}
    disconnect() { disconnected = true; }
  }
  const document = {
    documentElement: { getAttribute: () => state.lang },
    getElementById: () => state.header,
    querySelectorAll: selector => selector.includes('.label') ? state.labels : state.calculators,
  };
  vm.runInNewContext(`(${installInitialHeaderObserver.toString()})()`, { document, window, MutationObserver });
  return { state, window, mutate() { if (!disconnected) callback(); } };
}

function completeHeader(s, language = 'fr') {
  s.state.lang = language;
  s.state.header = { getAttribute: () => language };
  s.state.labels = [{ parentElement: { id: 'patient-sex' }, textContent: language === 'fr' ? 'Sexe' : 'Sex' }];
  s.state.calculators = [{ textContent: language === 'fr' ? 'calculatrices' : 'calculators' }];
  s.mutate();
}

test('shouldWaitForACompleteHeader_whenParserDeliversPartialMarkup', () => {
  const s = observe();
  assert.equal(s.window.__encounterInitialHeader, undefined);
  s.state.header = { getAttribute: () => 'fr-CA' };
  s.state.labels = [{ parentElement: { id: 'patient-sex' }, textContent: 'Sexe' }];
  s.state.calculators = [{ textContent: '' }];
  s.mutate();
  assert.equal(s.window.__encounterInitialHeader, undefined);
  completeHeader(s);
  assert.equal(s.window.__encounterInitialHeader.identityLabelsById['patient-sex'], 'Sexe');
});

test('shouldRetainEnglishFirstRender_whenLaterScriptsTranslateTheHeader', () => {
  const s = observe();
  completeHeader(s, 'en');
  const first = s.window.__encounterInitialHeader;
  completeHeader(s, 'fr');
  assert.equal(first.identityLabelsById['patient-sex'], 'Sex');
  assert.throws(() => assertInitialHeader(LANGUAGES[0], first, {
    identityLabelsById: { 'patient-sex': 'Sexe' }, calculatorCount: 1, calculatorText: 'calculatrices',
  }), /changed after its initial render/);
});

test('shouldAcceptFrenchFromFirstRender_whenHeaderRemainsStable', () => {
  const s = observe();
  completeHeader(s);
  const first = s.window.__encounterInitialHeader;
  assert.doesNotThrow(() => assertInitialHeader(LANGUAGES[0], first, first));
  assert.throws(() => assertInitialHeader(LANGUAGES[0], null, first), /not observed/);
  first.lang.header = 'en';
  assert.throws(() => assertInitialHeader(LANGUAGES[0], first, first), /negotiated/);
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
