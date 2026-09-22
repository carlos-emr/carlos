#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the encounter chart header: is it in the
 * clinician's language, and does it offer ONE way to the calculators?
 *
 * WHY THIS ONE EXISTS. phc007 reported, against a packaged install, that the
 * chart header "remains not i18n", that it "provides TWO links for calculators",
 * and that the note-template search label and placeholder were untranslated.
 * Two separate defects sat behind that:
 *
 *   1. UNTAGGED TEXT. "Template Search", "template name", "Search templates..."
 *      and the copy-to-clipboard confirmation were literals in the markup and in
 *      an inline script, so no translation could reach them.
 *
 *   2. THE WRONG LOCALE. The header is half JSP and half a Java-built HTML
 *      string (Demographic#getStandardIdentificationHtml). The JSP half resolves
 *      <fmt:message> from the browser's Accept-Language; the Java half read
 *      LocaleContextHolder, and CARLOS installs no Spring LocaleResolver on the
 *      Struts/JSP request path, so that is the SERVER's locale. On an
 *      English-defaulted server the two halves disagreed and the header looked
 *      "not i18n" even though the page around it was translated.
 *
 * Defect 2 is why this check walks the chart against the same running server
 * with French, English, and unsupported-language preferences, and asserts
 * the French and English headers differ. A server-locale regression passes every
 * single-language check ever written: one language is always right.
 *
 * WHAT IT ASSERTS, per language:
 *   1. The Java-rendered identity labels (Sex, Age, Next Appt., MRP) are in the
 *      browser's language.
 *   2. The <fmt:message> labels around them are in the SAME language -- the
 *      header is not half translated.
 *   3. The header offers exactly ONE calculators control, and clicking it opens
 *      the calculators.
 *   4. The note-template search legend and its input placeholder are in the
 *      configured bundle (new translations remain marked English placeholders until verified).
 * and across the supported languages:
 *   5. The French header is not the English header. This is the assertion that
 *      fails if the locale is ever taken from the JVM again.
 *
 * ENTERED THE WAY A CLINIC ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart. Every selector used to get there is a non-translated id,
 * href or title -- a check that navigated by visible text could not run in
 * French at all.
 *
 * NO PATIENT DATA IN THE OUTPUT. The assertions read LABELS, never the values
 * beside them, and the diagnostics quote only label text. demographic_no is not
 * printed; the messages say "the selected patient".
 *
 * READ-ONLY: it opens the chart and a popup and closes them. Nothing is typed
 * into the note editor, nothing is saved.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:encounter-header-i18n-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   HEADER_I18N_SEARCH=FAKE-            surname prefix used to reach a patient
 *   HEADER_I18N_DEMOGRAPHIC_NO=2        which patient's chart to open
 *   HEADER_I18N_TIMEOUT_MS=30000        per-step allowance
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/*
 * Supported languages plus an unsupported first preference with a French fallback.
 *
 * Every expectation names the oscarResources key it comes from, so a
 * translation change is a one-line edit here rather than a mystery failure.
 * French is the second language because it is the one CARLOS ships most
 * completely and the one the report came from.
 */
const ENGLISH_LABELS = Object.freeze({
  'patient-pronouns': 'Pronouns', 'patient-sex': 'Sex', 'patient-gender': 'Gender',
  'patient-dob': 'DOB', 'patient-age': 'Age', 'patient-hin': 'HIN',
  'patient-phone': 'Phone', 'patient-cell-phone': 'Cell Phone', 'patient-email': 'Email',
  'patient-next-appointment': 'Next Appt.', 'patient-mrp': 'MRP',
});
const FRENCH_LABELS = Object.freeze({
  ...ENGLISH_LABELS,
  'patient-pronouns': 'Pronoms', 'patient-sex': 'Sexe', 'patient-gender': 'Genre',
  'patient-dob': 'DDN', 'patient-age': 'Âge', 'patient-phone': 'Téléphone',
  'patient-cell-phone': 'Tél. cellulaire', 'patient-email': 'Courriel',
  'patient-next-appointment': 'Prochain rendez-vous',
});

const LANGUAGES = [
  {
    tag: 'en-CA',
    acceptLanguage: 'en-CA,en;q=0.9',
    // demographic.demographicaddrecordhtm.formSex / global.age  (Java-rendered)
    javaLabels: ENGLISH_LABELS,
    // encounter.Index.calculators  (JSP-rendered, same header)
    calculators: 'calculators',
    // encounter.templateSearch.legend / .namePlaceholder
    templateLegend: 'Template Search',
    templatePlaceholder: 'template name',
  },
  {
    tag: 'fr-CA',
    acceptLanguage: 'fr-CA,fr;q=0.9',
    javaLabels: FRENCH_LABELS,
    calculators: 'calculatrices',
    templateLegend: 'Template Search',
    templatePlaceholder: 'template name',
  },
  {
    tag: 'de-AT',
    acceptLanguage: 'de-AT,de;q=0.9',
    javaLabels: ENGLISH_LABELS,
    calculators: 'calculators',
    templateLegend: 'Template Search',
    templatePlaceholder: 'template name',
  },
  {
    tag: 'de-DE',
    acceptLanguage: 'de-DE,fr;q=0.9,en;q=0.8',
    javaLabels: FRENCH_LABELS,
    calculators: 'calculatrices',
    templateLegend: 'Template Search',
    templatePlaceholder: 'template name',
  },
];

/** The identity block the Java half of the header renders. */
const IDENTITY_LABELS = '#header #patient-label .label';
/** Every control in the header that leads to the calculators, whatever its label. */
const CALCULATOR_LINKS = '#header a[onclick*="ViewCalculators"], #header a[href*="ViewCalculators"]';
/** The note-template search box, rendered into the chart by ChartNotes.jsp. */
const TEMPLATE_INPUT = '#enTemplate';

/**
 * Open the chart from the Master Record.
 *
 * By the onclick, NOT by the link's text: the E-Chart link is itself translated
 * (demographic.demographiceditdemographic.btnEChart is "Dossier electronique" in
 * French), so a text matcher would find nothing in the very language this check
 * exists to exercise.
 */
async function openChart(context, masterPage, recorder, timeout) {
  const chartLink = masterPage.locator('a[onclick*="popupEChart"]').first();
  assert(await chartLink.count() > 0,
    'The Master Record offers no E-Chart control, so a clinician cannot open the chart from the patient record');
  const { page } = await clickOpensPopupOrNavigates(masterPage, chartLink, {
    context, label: 'echart', recorder, timeout,
  });
  await page.locator(IDENTITY_LABELS).first().waitFor({ state: 'attached', timeout });
  return page;
}

/**
 * Read the header's labels out of one chart.
 *
 * LABELS ONLY. Each entry is the text of a `.label` div, which is the caption
 * ("Sex", "DOB", "HIN (ON)"); the patient's value is its sibling and is never
 * read, so nothing clinical reaches the artifact of a failing run.
 */
async function readHeader(chartPage, timeout) {
  const identityLabels = (await chartPage.locator(IDENTITY_LABELS).allInnerTexts())
    .map((text) => text.trim())
    .filter(Boolean);

  const calculators = chartPage.locator(CALCULATOR_LINKS);
  const calculatorCount = await calculators.count();
  const calculatorText = calculatorCount > 0
    ? (await calculators.first().innerText()).trim()
    : '';

  // ChartNotes.jsp is fetched AFTER the chart's own load (viewFullChart POSTs to
  // CaseManagementEntry and renders it into #notCPP), so the template search has
  // to be waited for rather than merely counted.
  const templateInput = chartPage.locator(TEMPLATE_INPUT).first();
  await templateInput.waitFor({ state: 'attached', timeout }).catch(() => {});
  assert(await templateInput.count() > 0,
    'The chart rendered no note-template search box, so its label and placeholder cannot be checked');
  const placeholder = (await templateInput.getAttribute('placeholder') || '').trim();

  // The legend is read through the input's own <fieldset> rather than by
  // position, so an added fieldset elsewhere on the chart cannot silently make
  // this check assert against the wrong caption. page.evaluate READS here; the
  // click below is a real click (see lib/playwright-ui.js on that line).
  const legend = await chartPage.evaluate((selector) => {
    const input = document.querySelector(selector);
    const fieldset = input && input.closest('fieldset');
    const element = fieldset && fieldset.querySelector('legend');
    return element ? element.textContent.trim() : '';
  }, TEMPLATE_INPUT);

  return {
    identityLabels, calculatorCount, calculatorText, placeholder, legend,
    identityLabelsById: await chartPage.locator(IDENTITY_LABELS).evaluateAll((labels) =>
      Object.fromEntries(labels.map((label) => [label.parentElement.id, label.textContent.trim()]))),
  };
}

/** Everything one language has to satisfy on its own. */
function assertLanguage(language, header) {
  for (const id of ['patient-sex', 'patient-dob', 'patient-age', 'patient-next-appointment', 'patient-mrp']) {
    assert(Object.hasOwn(header.identityLabelsById, id), `The ${language.tag} chart omitted identity label ${id}`);
  }
  for (const [id, text] of Object.entries(header.identityLabelsById)) {
    const label = id === 'patient-hin' ? text.split(' (')[0] : text;
    assert(label === language.javaLabels[id],
      `The ${language.tag} chart has an unexpected ${id} label: ${JSON.stringify(label)}; `
      + `expected ${JSON.stringify(language.javaLabels[id])}`);
  }

  assert(header.calculatorCount === 1,
    `The ${language.tag} chart header offers ${header.calculatorCount} calculators control(s); it must offer exactly `
    + 'one. Two identically labelled links to the same page is the duplicate phc007 reported.');

  assert(header.calculatorText.toLowerCase() === language.calculators.toLowerCase(),
    `The ${language.tag} calculators control reads ${JSON.stringify(header.calculatorText)}, not `
    + `${JSON.stringify(language.calculators)}: the JSP half of the header is in a different language than the browser `
    + 'asked for');

  assert(header.legend === language.templateLegend,
    `The ${language.tag} note-template search legend reads ${JSON.stringify(header.legend)}, not `
    + `${JSON.stringify(language.templateLegend)}`);

  assert(header.placeholder === language.templatePlaceholder,
    `The ${language.tag} note-template search placeholder reads ${JSON.stringify(header.placeholder)}, not `
    + `${JSON.stringify(language.templatePlaceholder)}`);
}

/** One full walk: log in with this Accept-Language, reach a chart, read the header. */
async function walkInLanguage(browser, config, language, options) {
  const { searchTerm, preferredDemographicNo, timeout } = options;
  const recorder = createRecorder();
  const context = await newContext(browser, config, {
    locale: language.tag,
  });
  try {
    // Chromium's locale emulation overrides extraHTTPHeaders for Accept-Language,
    // reducing a weighted preference list to the single emulated locale. Apply
    // the intended browser preferences at request dispatch so fallback is tested.
    await context.route('**/*', (route) => route.continue({
      headers: { ...route.request().headers(), 'accept-language': language.acceptLanguage },
    }));
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChart(context, masterPage, recorder, timeout);
    const header = await readHeader(chartPage, timeout);

    // The one control must actually work, not merely be the only one present.
    const popup = await clickOpensPopup(chartPage, chartPage.locator(CALCULATOR_LINKS).first(), {
      context, label: `calculators:${language.tag}`, recorder, timeout,
    });
    const popupText = await popup.locator('body').innerText({ timeout }).catch(() => '');
    assert(/calcul|risk|BMI|body mass|masse|Framingham/i.test(popupText),
      `The ${language.tag} calculators control opened something that is not the calculators index`);
    await popup.close().catch(() => {});

    assertStrictPage(recorder);
    return header;
  } finally {
    await context.close().catch(() => {});
  }
}

async function main() {
  const config = readConfig();
  const options = {
    searchTerm: process.env.HEADER_I18N_SEARCH || 'FAKE-',
    preferredDemographicNo: process.env.HEADER_I18N_DEMOGRAPHIC_NO || '2',
    timeout: Number(process.env.HEADER_I18N_TIMEOUT_MS || '30000'),
  };

  const browser = await launchBrowser(config);
  const headers = {};
  try {
    for (const language of LANGUAGES) {
      const header = await walkInLanguage(browser, config, language, options);
      assertLanguage(language, header);
      headers[language.tag] = header;
    }
  } finally {
    await browser.close().catch(() => {});
  }

  // THE ASSERTION THAT CATCHES A SERVER-LOCALE REGRESSION. Each language above
  // passes on its own whenever the server happens to be running in that
  // language; only the DIFFERENCE between two browsers against one server shows
  // that the header follows the browser.
  const english = headers['en-CA'];
  const french = headers['fr-CA'];
  assert(JSON.stringify(english.identityLabels) !== JSON.stringify(french.identityLabels),
    'The French and English charts rendered identical identity labels, so the header is not following the browser '
    + 'locale at all -- it is answering in one fixed language (the server\'s) to every clinician');
  assert(english.calculatorText !== french.calculatorText,
    'The calculators control reads the same in both languages, so the header labels are not being translated');

  return `header locale negotiation verified for ${LANGUAGES.map((language) => language.tag).join(', ')}; `
    + 'one calculators control in each; template search legend and placeholder match configured bundle values';
}

if (require.main === module) {
  runCheck({ name: 'encounter-header-i18n', run: main });
}

module.exports = { CALCULATOR_LINKS, IDENTITY_LABELS, LANGUAGES, TEMPLATE_INPUT, assertLanguage, main };
