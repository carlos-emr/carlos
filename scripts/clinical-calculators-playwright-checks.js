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
 * Browser regression check for the chart's clinical calculators: do they compute
 * the right answer?
 *
 * WHY THIS ONE MATTERS MORE THAN IT LOOKS. Every other check in this suite asks
 * whether a page rendered, a row was written, or a popup opened. This one asks
 * whether a number a clinician reads off the screen is correct. The osteoporotic
 * fracture calculator turns an age, a sex and a bone-density T-score into a
 * ten-year fracture probability, and that number is what a prescribing decision
 * is made on. A wrong answer here does not look like a bug: the page renders, no
 * console error appears, the probability is plausible, and it is wrong.
 *
 * The whole calculation is in the page's own JavaScript -- there is no server
 * round trip and no Java to unit-test -- so a browser check is the ONLY place
 * this can be asserted at all.
 *
 * THE ASSERTIONS ARE EXACT, AND THEY INCLUDE A BAND BOUNDARY. The risk table is
 * eight age bands; the boundary cases (54 vs 55) are the ones an off-by-one in
 * the band ladder would move, and an off-by-one there reads as a perfectly
 * ordinary number for a patient one year older. Every expected value below was
 * transcribed from OsteoporoticFracture.jsp's own table, and
 * scripts/clinical-calculators.test.js pins each one back to the source line so
 * the expectations cannot drift away from the page they describe.
 *
 * THE SIMPLE CALCULATOR is checked too, for a duller reason: it is pure
 * click-driven state (AddDigit / Op / Eq over module-level variables) and it is
 * the kind of code that breaks silently when a shared script changes underneath
 * it.
 *
 * NOT ASSERTED HERE, deliberately: what these calculators do with an age that is
 * blank or not a number. Both answer confidently rather than refusing, and the
 * non-numeric case lands on the OLDEST band -- see findings 8 and 9 in
 * docs/ui-tests/app-findings-log.md. The suite's rule is report, don't encode: an
 * assertion pinning that behaviour as expected would make it permanent. When it
 * is fixed, the assertion belongs here.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart, the calculator icon in the chart header, then the calculator.
 *
 * READ-ONLY: it types into calculator forms that persist nothing. No patient
 * record is written, and "paste to the chart" is never clicked.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:clinical-calculators-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CALCULATOR_SEARCH=FAKE-          surname prefix used to reach a patient
 *   CALCULATOR_DEMOGRAPHIC_NO=2      which patient's chart to open
 *   CALCULATOR_TIMEOUT_MS=20000      per-step allowance
 *
 * IMPLEMENTS: coverage plan section 2.5, `calculators`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md).
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart } = require('./echart-navbar-modules-playwright-checks');

/*
 * The T-score control is a <select> whose option VALUES are 1..5 and whose
 * LABELS are the clinical T-scores. They are not the same thing and the page
 * indexes its table by the value, so both are recorded: the check selects by
 * label (what a clinician picks) and the test pins the pairing.
 */
const T_SCORES = [
  { label: '1', value: '1' },
  { label: '0', value: '2' },
  { label: '-1', value: '3' },
  { label: '-2', value: '4' },
  { label: '< -2.5', value: '5' },
];

/*
 * Transcribed from OsteoporoticFracture.jsp's osteFactorFemale / osteFactorMale
 * tables. `probability` is the ten-year probability for this age/sex/T-score;
 * `overall` is the band's overall average, which the page prints beneath it.
 *
 * The 54 / 55 pair is the point of the list: they are one year apart and two
 * different bands, so an off-by-one in the "age <= 54" ladder shows up here and
 * nowhere else.
 */
const FRACTURE_CASES = [
  {
    why: 'female, first band', sex: 'F', age: '52', tScore: '1', probability: '2.4', overall: '6.0',
  },
  {
    why: 'female, band boundary (54 is still the first band)', sex: 'F', age: '54', tScore: '1', probability: '2.4', overall: '6.0',
  },
  {
    why: 'female, band boundary (55 is the second band)', sex: 'F', age: '55', tScore: '1', probability: '2.6', overall: '7.8',
  },
  {
    why: 'female, mid table, osteopenic', sex: 'F', age: '67', tScore: '-2', probability: '15.6', overall: '14.3',
  },
  {
    why: 'male, mid table, osteopenic', sex: 'M', age: '72', tScore: '-2', probability: '10.9', overall: '7.6',
  },
  {
    why: 'male, oldest band, osteoporotic', sex: 'M', age: '90', tScore: '< -2.5', probability: '21.4', overall: '13.1',
  },
  {
    why: 'female, normal T-score in a high-risk band', sex: 'F', age: '60', tScore: '0', probability: '5.1', overall: '10.6',
  },
];

/*
 * Keystroke sequences for the simple calculator, as button labels. Each entry is
 * exactly what a user would press, and `display` is what the box must then read.
 */
const ARITHMETIC_CASES = [
  { keys: ['7', '+', '8', '='], display: '15' },
  { keys: ['1', '2', '-', '5', '='], display: '7' },
  { keys: ['6', '*', '5', '='], display: '30' },
  { keys: ['9', '/', '4', '='], display: '2.25' },
  // Clear must actually clear: a stale operand carried into the next sum is the
  // failure mode of a calculator built on module-level variables.
  { keys: ['9', '+', '9', 'C', '3', '+', '4', '='], display: '7' },
];

/** Open the calculators index from the chart header, then one calculator. */
async function openCalculator(context, chartPage, linkText, recorder, timeout) {
  // The chart offers a text link; older layouts also offered an icon.
  const icon = chartPage.locator('a[onclick*="ViewCalculators"]').first();
  assert(await icon.count() > 0,
    'The chart header offers no calculators control, so a clinician cannot reach the calculators from a chart at all');
  const index = await clickOpensPopup(chartPage, icon, {
    context, label: 'calculators-index', recorder, timeout,
  });

  const link = index.locator('a').filter({ hasText: linkText }).first();
  assert(await link.count() > 0, `The calculators index offers no "${linkText}" link`);
  const calculator = await clickOpensPopup(index, link, {
    context, label: `calculator:${linkText}`, recorder, timeout,
  });
  // popperup() calls close() on the index after opening the calculator, so the
  // index window is already gone; there is nothing to close here.
  return calculator;
}

/** Read the probability and the band average out of the prediction textarea. */
function parsePrediction(text) {
  const probability = text.match(/10 Year Probability:\s*([\d.]+)\s*%/i);
  const overall = text.match(/Overall average Probability:\s*([\d.]+)\s*%/i);
  return {
    probability: probability ? probability[1] : '',
    overall: overall ? overall[1] : '',
  };
}

async function checkFractureRisk(context, chartPage, recorder, timeout) {
  const page = await openCalculator(context, chartPage, 'Osteoporotic Fracture', recorder, timeout);
  try {
    const form = page.locator('form[name="calCorArDi"]');
    const results = [];
    for (const scenario of FRACTURE_CASES) {
      await form.locator(`input[name="sex"][value="${scenario.sex}"]`).check();
      await form.locator('input[name="age"]').fill(scenario.age);
      // By LABEL, because the clinician picks a T-score, not an index. The page
      // reads the option's value and uses it to index the risk table, so this
      // also proves the label/value pairing has not drifted.
      await form.locator('select[name="tScore"]').selectOption({ label: scenario.tScore });
      await form.locator('input[type="button"]').first().click({ timeout });

      const text = await page.locator('textarea[name="prediction"]').inputValue();
      const shown = parsePrediction(text);
      assert(shown.probability === scenario.probability,
        `${scenario.why}: a ${scenario.age}-year-old ${scenario.sex} with a T-score of ${scenario.tScore} `
        + `should read a ten-year fracture probability of ${scenario.probability}%, and the page says `
        + `${shown.probability || '(nothing)'}%. This number is what a treatment decision is made on.`);
      assert(shown.overall === scenario.overall,
        `${scenario.why}: the band's overall average should be ${scenario.overall}%, the page says `
        + `${shown.overall || '(nothing)'}%`);
      results.push(scenario.why);
    }
    return results;
  } finally {
    await page.close().catch(() => {});
  }
}

async function checkArithmetic(context, chartPage, recorder, timeout) {
  const page = await openCalculator(context, chartPage, 'Simple Calculator', recorder, timeout);
  try {
    const form = page.locator('form[name="rcform"]');
    const display = form.locator('input[name="display"]');
    const results = [];
    for (const scenario of ARITHMETIC_CASES) {
      // Start from a known state: these handlers keep their operands in
      // module-level variables that outlive any one sum.
      await form.locator('input[value="C"]').click({ timeout });
      for (const key of scenario.keys) {
        await form.locator(`input[value="${key}"]`).first().click({ timeout });
      }
      const shown = (await display.inputValue()).trim();
      assert(shown === scenario.display,
        `Pressing ${scenario.keys.join(' ')} should leave ${scenario.display} in the display, not ${JSON.stringify(shown)}`);
      results.push(scenario.keys.join(''));
    }
    return results;
  } finally {
    await page.close().catch(() => {});
  }
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.CALCULATOR_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.CALCULATOR_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.CALCULATOR_TIMEOUT_MS || '20000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChart(context, masterPage, recorder, timeout);

    const fracture = await checkFractureRisk(context, chartPage, recorder, timeout);
    const arithmetic = await checkArithmetic(context, chartPage, recorder, timeout);

    // A floor, not a formality. Everything above is inside a loop over a table;
    // an empty table would leave every assertion unexecuted and the check would
    // report success having computed nothing. That failure has come up three
    // times in this suite already, so each family states how many it must run.
    assert(fracture.length >= 5,
      `Only ${fracture.length} fracture-risk scenario(s) ran; the table holds more, so the loop did not execute`);
    assert(arithmetic.length >= 4,
      `Only ${arithmetic.length} arithmetic sequence(s) ran; the table holds more, so the loop did not execute`);

    assertStrictPage(recorder);
    console.log(`  verified ${fracture.length} fracture-risk scenario(s) and ${arithmetic.length} arithmetic sequence(s)`);
    return { fracture, arithmetic };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'clinical-calculators', run: main });
}

module.exports = {
  ARITHMETIC_CASES, FRACTURE_CASES, T_SCORES, main, openCalculator, parsePrediction,
};
