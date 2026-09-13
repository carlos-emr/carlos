/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  ARITHMETIC_CASES, FRACTURE_CASES, T_SCORES, parsePrediction,
} = require('./clinical-calculators-playwright-checks');

const SOURCE = fs.readFileSync(path.join(__dirname, 'clinical-calculators-playwright-checks.js'), 'utf8');
const CALC_DIR = path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'encounter', 'calculators',
);
const FRACTURE_JSP = fs.readFileSync(path.join(CALC_DIR, 'OsteoporoticFracture.jsp'), 'utf8');
const SIMPLE_JSP = fs.readFileSync(path.join(CALC_DIR, 'SimpleCalculator.jsp'), 'utf8');
const FINDINGS = fs.readFileSync(
  path.join(__dirname, '..', 'docs', 'ui-tests', 'app-findings-log.md'), 'utf8',
);

/*
 * These expectations are the check. If one drifts from the page's own risk
 * table, the browser check keeps passing while asserting a probability the
 * product does not produce -- which is worse than no check at all, because the
 * number is what a prescribing decision is made on. So every expected value is
 * pinned back here to the source line it was transcribed from.
 */

/**
 * The page's table, read out of the JSP: osteFactor<Sex>[band][index] = value.
 *
 * A literal pattern rather than one built from the sex: the repo's Semgrep rules
 * flag new RegExp(...) on principle, and nothing here needs one.
 */
const TABLE_ENTRY = /osteFactor(Female|Male)\[(\d+)\]\[(\d+)\]\s*=\s*"?([\d.]+)"?\s*;/g;

function riskTable(sex) {
  const table = {};
  for (const found of FRACTURE_JSP.matchAll(TABLE_ENTRY)) {
    if (found[1] === sex) {
      table[`${found[2]}:${found[3]}`] = found[4];
    }
  }
  return table;
}

/** The page's own age ladder, so the band a scenario lands in is not guessed. */
function bandFor(age) {
  const value = Number(age);
  if (value <= 54) return 1;
  if (value <= 59) return 2;
  if (value <= 64) return 3;
  if (value <= 69) return 4;
  if (value <= 74) return 5;
  if (value <= 79) return 6;
  if (value <= 84) return 7;
  return 8;
}

test('the risk table was actually found in the page, so the pinning is not vacuous', () => {
  for (const sex of ['Female', 'Male']) {
    const table = riskTable(sex);
    assert.equal(Object.keys(table).length, 48,
      `osteFactor${sex} should hold 8 bands x 6 columns; found ${Object.keys(table).length}`);
  }
});

test('every expected probability is the value the page would look up', () => {
  for (const scenario of FRACTURE_CASES) {
    const table = riskTable(scenario.sex === 'F' ? 'Female' : 'Male');
    const tScore = T_SCORES.find((candidate) => candidate.label === scenario.tScore);
    assert.ok(tScore, `${scenario.tScore} is not a T-score the control offers`);
    const band = bandFor(scenario.age);
    assert.equal(scenario.probability, table[`${band}:${tScore.value}`],
      `${scenario.why}: expected ${scenario.probability} but the page's table holds `
      + `${table[`${band}:${tScore.value}`]} at band ${band}, column ${tScore.value}`);
    assert.equal(scenario.overall, table[`${band}:0`],
      `${scenario.why}: the band average does not match column 0 of band ${band}`);
  }
});

test('the T-score labels and values match the control the page renders', () => {
  // The clinician picks a T-score; the page indexes its table by the option's
  // VALUE. If those two ever drift, every probability shown is off by a column.
  const options = [...FRACTURE_JSP.matchAll(/<option value="(\d)">([^<]+)<\/option>/g)]
    .map((found) => ({ value: found[1], label: found[2].replace(/&lt;/g, '<').trim() }));
  assert.deepEqual(options, T_SCORES);
});

test('the age-band boundary is covered from both sides', () => {
  // An off-by-one in the "age <= 54" ladder reads as an ordinary number for a
  // patient one year older, so it is invisible to any single-age check.
  const ages = FRACTURE_CASES.map((scenario) => Number(scenario.age));
  assert.ok(ages.includes(54) && ages.includes(55),
    'the 54/55 pair must stay in the list; it is the only case that catches a shifted age band');
  const fiftyFour = FRACTURE_CASES.find((scenario) => scenario.age === '54');
  const fiftyFive = FRACTURE_CASES.find((scenario) => scenario.age === '55');
  assert.notEqual(fiftyFour.probability, fiftyFive.probability,
    'the boundary cases must straddle two different bands, or they prove nothing');
});

test('both sexes and both ends of the table are exercised', () => {
  const sexes = new Set(FRACTURE_CASES.map((scenario) => scenario.sex));
  assert.deepEqual([...sexes].sort(), ['F', 'M']);
  const bands = new Set(FRACTURE_CASES.map((scenario) => bandFor(scenario.age)));
  assert.ok(bands.has(1) && bands.has(8), 'the first and last age bands must both be covered');
});

test('the prediction parser reads the page\'s actual output format', () => {
  assert.match(FRACTURE_JSP, /msg10YearProb"\/>:\s*"\s*\+ retval \+ " %/);
  assert.match(FRACTURE_JSP, /msgOverall"\/>:\s*"\s*\+ total \+ " %/);
  const shown = parsePrediction(
    '*****\n* 10 Year Probability:         2.4 %  \n* Overall average Probability: 6.0 %  \n*****\n',
  );
  assert.deepEqual(shown, { probability: '2.4', overall: '6.0' });
  // A prediction that did not render must read as empty, never as a number.
  assert.deepEqual(parsePrediction(''), { probability: '', overall: '' });
});

test('every arithmetic key is a button the calculator actually has', () => {
  const buttons = new Set([...SIMPLE_JSP.matchAll(/VALUE="([^"]+)"\s*$/gm)].map((found) => found[1]));
  for (const scenario of ARITHMETIC_CASES) {
    for (const key of scenario.keys) {
      assert.ok(SIMPLE_JSP.includes(`VALUE="${key}"`), `the calculator has no "${key}" button`);
    }
  }
  assert.ok(buttons.size > 0);
});

test('the arithmetic expectations match what the page computes', () => {
  // Replays the page's own AddDigit / Op / Eq over the same module-level
  // variables, so an expectation cannot be wishful.
  for (const scenario of ARITHMETIC_CASES) {
    let display = '0';
    let x = 0;
    let y = 0;
    let op = '';
    let lastop = '';
    let clearflag = true;
    for (const key of scenario.keys) {
      if (/^\d$/.test(key)) {
        if (clearflag) display = '';
        display += key;
        lastop = '';
        clearflag = false;
      } else if (key === 'C') {
        x = 0; y = 0; op = ''; display = '0'; lastop = ''; clearflag = true;
      } else if (key === '=') {
        if (lastop === '') { y = parseFloat(display); } else { x = parseFloat(display); op = lastop; }
        if (op === '+') display = String(x + y);
        if (op === '-') display = String(x - y);
        if (op === '*') display = String(x * y);
        if (op === '/') display = String(x / y);
        lastop = op; op = ''; clearflag = true;
      } else {
        x = parseFloat(display);
        op = key;
        lastop = '';
        clearflag = true;
      }
    }
    assert.equal(display, scenario.display,
      `${scenario.keys.join(' ')} computes ${display}, but the check expects ${scenario.display}`);
  }
});

test('a clear-mid-sum case is kept, because the operands outlive the sum', () => {
  const hasClear = ARITHMETIC_CASES.some((scenario) => scenario.keys.includes('C') && scenario.keys.length > 4);
  assert.ok(hasClear,
    'this calculator keeps x, y and op in module-level variables; without a clear-mid-sum case '
    + 'a stale operand carried into the next sum would go unnoticed');
});

test('the check asserts only valid input, and says where the rest is recorded', () => {
  // Report, don't encode: pinning the blank/non-numeric age behaviour as
  // expected would make it permanent.
  assert.match(SOURCE, /findings 8 and 9 in/);
  assert.match(FINDINGS, /A blank age is treated as 50/);
  assert.match(FINDINGS, /A non-numeric age selects the OLDEST band/);
  for (const scenario of FRACTURE_CASES) {
    assert.match(scenario.age, /^\d+$/, 'every asserted scenario must use a valid age');
  }
});

test('the calculators are reached by clicking, never by their URL', () => {
  assert.match(SOURCE, /a\[title="calculators"\]/);
  assert.ok(!/page\.goto\(/.test(SOURCE),
    'entering by address would skip the chart header opener this check exists to exercise');
  assert.ok(!/ViewOsteoporoticFracture|ViewSimpleCalculator/.test(SOURCE),
    'the check must name the link a clinician clicks, not the route behind it');
});
