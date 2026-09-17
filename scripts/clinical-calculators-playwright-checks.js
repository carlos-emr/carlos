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
 * INVALID INPUT IS ASSERTED TOO, since issue #3665 findings 8 and 9 were fixed.
 * Both calculators used to answer confidently on an age they could not use: a
 * blank box coerced to 0 and chose the youngest band, and a typo compared false
 * at every rung and fell through to the OLDEST band. Each now refuses with a
 * message in the prediction box and computes nothing, and INVALID_AGE_CASES
 * pins exactly that -- for the fracture calculator AND the coronary one, whose
 * fault was the mirror image (a stale band from the previous patient). A valid
 * age is entered again afterwards to prove a refusal leaves nothing stuck.
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
  assert, assertNotErrorPage, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
  wireStrictPage,
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
 * Ages BOTH calculators must refuse (issue #3665, findings 8 and 9). The first
 * two are the filed defects; the rest are the neighbouring shapes a guard that
 * only checked "is it a number" would still let through. `refused` is the text
 * the prediction box must show instead of a number; the pages print the
 * message the i18n bundle holds, so only its stable fragment is matched.
 */
const INVALID_AGE_CASES = [
  { why: 'blank age (finding 8: was read as 0 and answered for the youngest band)', age: '' },
  { why: 'non-numeric age (finding 9: fell through to the OLDEST band)', age: 'abc' },
  { why: 'digits with a trailing letter', age: '54a' },
  { why: 'decimal age', age: '54.5' },
  { why: 'negative age', age: '-5' },
  { why: 'implausibly old', age: '121' },
];
/* One below the fracture table's first row, which the page labels 50. */
const FRACTURE_BELOW_TABLE_AGE = '49';
const REFUSED_AGE_TEXT = /whole number from \d+ to \d+/;
/* The band highlight the fracture page paints on a computed answer. */
const FRACTURE_HIGHLIGHT = 'rgb(204, 204, 255)';

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
  // The chart offers a text link; older layouts also offered an icon. It lives
  // in casemgmt/navigation.jsp, which the chart fetches AFTER its own load
  // (casemgmt/ViewNavigation), so it has to be waited for, not merely counted:
  // counting it on a freshly opened chart found nothing on the packaged install.
  const icon = chartPage.locator('a[onclick*="ViewCalculators"]').first();
  await icon.waitFor({ state: 'attached', timeout }).catch(() => {});
  assert(await icon.count() > 0,
    'The chart header offers no calculators control, so a clinician cannot reach the calculators from a chart at all');
  const index = await clickOpensPopup(chartPage, icon, {
    context, label: 'calculators-index', recorder, timeout,
  });

  const link = index.locator('a').filter({ hasText: linkText }).first();
  assert(await link.count() > 0, `The calculators index offers no "${linkText}" link`);
  // popperup() opens the calculator and then close()s the index window itself.
  // The index can therefore vanish while Playwright is still inside the click
  // (it reports "Target page, context or browser has been closed"), which is
  // the index behaving as designed, not a failed click. So the popup is awaited
  // independently of the click's own outcome, and only a missing popup fails.
  const label = `calculator:${linkText}`;
  const popup = context.waitForEvent('page', { timeout });
  await link.click({ timeout, noWaitAfter: true }).catch((error) => {
    if (!/closed/i.test(String(error.message))) {
      throw error;
    }
  });
  let calculator;
  try {
    calculator = await popup;
  } catch (error) {
    throw new Error(`${label}: clicking the index link opened no calculator within ${timeout}ms`);
  }
  wireStrictPage(calculator, label, recorder);
  try {
    await calculator.waitForLoadState('domcontentloaded', { timeout });
    await calculator.waitForLoadState('networkidle', { timeout }).catch(() => {});
    await assertNotErrorPage(calculator, label);
  } catch (error) {
    await calculator.close().catch(() => {});
    throw error;
  }
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

/** Compute one fracture scenario and assert the page prints the table's own number. */
async function assertFractureScenario(page, form, scenario, timeout) {
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
}

/** How many risk-table cells the fracture page has painted as the chosen band. */
async function highlightedFractureCells(page) {
  return page.evaluate((highlight) => Array.from(document.querySelectorAll('td[id^="cell["]'))
    .filter((cell) => cell.style.background === highlight).length, FRACTURE_HIGHLIGHT);
}

/**
 * Enter an age the calculator cannot use and prove it refused: the message,
 * no probability, no band highlight (fracture page only) -- and that a valid
 * age entered next still computes, so the refusal leaves no state behind.
 */
async function assertAgeRefused(page, form, scenario, timeout, options = {}) {
  await form.locator('input[name="age"]').fill(scenario.age);
  await form.locator('input[type="button"]').first().click({ timeout });
  const text = await page.locator('textarea[name="prediction"]').inputValue();
  assert(REFUSED_AGE_TEXT.test(text),
    `${scenario.why}: age ${JSON.stringify(scenario.age)} should be refused with a message saying which `
    + `whole-number range the table answers for; the prediction box reads ${JSON.stringify(text.trim())}`);
  assert(!/Probability:\s*[\d.]+\s*%|Point Count:\s*-?\d/.test(text),
    `${scenario.why}: age ${JSON.stringify(scenario.age)} was refused AND a number was printed; a clinician `
    + 'would read the number');
  if (options.checkHighlight) {
    const painted = await highlightedFractureCells(page);
    assert(painted === 0,
      `${scenario.why}: age ${JSON.stringify(scenario.age)} was refused but ${painted} risk-table cell(s) are `
      + 'still highlighted as the chosen band');
  }
}

async function checkFractureRisk(context, chartPage, recorder, timeout) {
  const page = await openCalculator(context, chartPage, 'Osteoporotic Fracture', recorder, timeout);
  try {
    const form = page.locator('form[name="calCorArDi"]');
    const results = [];
    for (const scenario of FRACTURE_CASES) {
      await assertFractureScenario(page, form, scenario, timeout);
      results.push(scenario.why);
    }

    // Findings 8 and 9: every refusal, then a real answer again. The table's own
    // floor is refused too, because the page's first row is 50 and a 49-year-old
    // reading the 50-year-old's figure is the same class of wrong number.
    const refused = [];
    const invalid = [...INVALID_AGE_CASES, { why: 'below the table (first row is 50)', age: FRACTURE_BELOW_TABLE_AGE }];
    for (const scenario of invalid) {
      await assertAgeRefused(page, form, scenario, timeout, { checkHighlight: true });
      refused.push(scenario.why);
    }
    await assertFractureScenario(page, form, FRACTURE_CASES[0], timeout);
    assert(await highlightedFractureCells(page) > 0,
      'after the refusals a valid age computed a number but painted no band; the refusal path left the page half-reset');
    return { computed: results, refused };
  } finally {
    await page.close().catch(() => {});
  }
}

/** Read the point total out of the coronary calculator's prediction box. */
function parsePointCount(text) {
  const found = text.match(/Total Point Count:\s*(-?\d+)/);
  return found ? found[1] : '';
}

/**
 * The coronary calculator's fault was the mirror image of the fracture one: its
 * band index was a page-level variable, so an unusable age kept the PREVIOUS
 * patient's band while the age factor fell back to 0. So this computes a real
 * answer first, refuses each bad age, and then proves the same real answer comes
 * back unchanged and a different age gives a different total.
 */
async function checkCoronaryRisk(context, chartPage, recorder, timeout) {
  const page = await openCalculator(context, chartPage, 'Coronary Artery', recorder, timeout);
  try {
    const form = page.locator('form[name="calCorArDi"]');
    const prediction = page.locator('textarea[name="prediction"]');
    const calculate = form.locator('input[type="button"]').first();
    const total = async (age) => {
      await form.locator('input[name="age"]').fill(age);
      await calculate.click({ timeout });
      const points = parsePointCount(await prediction.inputValue());
      assert(points !== '', `a ${age}-year-old should get a Total Point Count; the box reads `
        + `${JSON.stringify((await prediction.inputValue()).trim())}`);
      return points;
    };
    await form.locator('input[name="sex"][value="F"]').check();
    const fortyFive = await total('45');
    const refused = [];
    for (const scenario of INVALID_AGE_CASES) {
      await assertAgeRefused(page, form, scenario, timeout);
      refused.push(scenario.why);
    }
    assert(await total('45') === fortyFive,
      'the same 45-year-old should score the same total after the refusals; the refusal path changed the answer');
    assert(await total('65') !== fortyFive,
      'a 65-year-old should not score the same total as a 45-year-old; the age band is not being applied');
    return { computed: ['45', '65'], refused };
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
    const coronary = await checkCoronaryRisk(context, chartPage, recorder, timeout);
    const arithmetic = await checkArithmetic(context, chartPage, recorder, timeout);

    // A floor, not a formality. Everything above is inside a loop over a table;
    // an empty table would leave every assertion unexecuted and the check would
    // report success having computed nothing. That failure has come up three
    // times in this suite already, so each family states how many it must run.
    assert(fracture.computed.length >= 5,
      `Only ${fracture.computed.length} fracture-risk scenario(s) ran; the table holds more, so the loop did not execute`);
    assert(fracture.refused.length >= 6 && coronary.refused.length >= 5,
      `Only ${fracture.refused.length} fracture and ${coronary.refused.length} coronary refusal(s) ran; `
      + 'the table holds more, so the loop did not execute');
    assert(arithmetic.length >= 4,
      `Only ${arithmetic.length} arithmetic sequence(s) ran; the table holds more, so the loop did not execute`);

    assertStrictPage(recorder);
    console.log(`  verified ${fracture.computed.length} fracture-risk scenario(s), refused ${fracture.refused.length} `
      + `fracture and ${coronary.refused.length} coronary age(s), and ${arithmetic.length} arithmetic sequence(s)`);
    return { fracture, coronary, arithmetic };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'clinical-calculators', run: main });
}

module.exports = {
  ARITHMETIC_CASES, FRACTURE_BELOW_TABLE_AGE, FRACTURE_CASES, INVALID_AGE_CASES, REFUSED_AGE_TEXT, T_SCORES,
  main, openCalculator, parsePointCount, parsePrediction,
};
