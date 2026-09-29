/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  ARITHMETIC_CASES, CORONARY_OUTSIDE_TABLE_AGES, FRACTURE_BELOW_TABLE_AGE, FRACTURE_CASES, INVALID_AGE_CASES,
  REFUSED_AGE_TEXT, T_SCORES, parsePointCount, parsePrediction,
} = require('./clinical-calculators-playwright-checks');
const { parseAge } = require('../src/main/webapp/share/javascript/clinicalCalculatorAge');

const SOURCE = fs.readFileSync(path.join(__dirname, 'clinical-calculators-playwright-checks.js'), 'utf8');
const CALC_DIR = path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'encounter', 'calculators',
);
const FRACTURE_JSP = fs.readFileSync(path.join(CALC_DIR, 'OsteoporoticFracture.jsp'), 'utf8');
const SIMPLE_JSP = fs.readFileSync(path.join(CALC_DIR, 'SimpleCalculator.jsp'), 'utf8');
const CORONARY_JSP = fs.readFileSync(path.join(CALC_DIR, 'CoronaryArteryDiseaseRiskPrediction.jsp'), 'utf8');
const BUNDLE_DIR = path.join(__dirname, '..', 'src', 'main', 'resources');
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
  // BOUND TO TYPE=button. The old pattern took any VALUE= that ended a line,
  // which captured the DISPLAY field -- SimpleCalculator.jsp:269 is
  // `<INPUT TYPE="text" VALUE="0"` -- and the membership test was a bare
  // SIMPLE_JSP.includes(), which any element's VALUE could satisfy. A scenario
  // pressing "0", or a button renamed to something the display happens to
  // carry, would have passed with no such button on the page.
  //
  // TYPE is unquoted on the buttons and quoted on the text field, so the
  // pattern accepts either spelling rather than assuming the current one.
  const buttons = new Set(
    [...SIMPLE_JSP.matchAll(/<INPUT\s+TYPE=["']?button["']?\s+VALUE="([^"]+)"/gi)].map((found) => found[1]),
  );
  assert.ok(buttons.size > 10, `only ${buttons.size} buttons parsed; the pattern is probably not matching`);
  // The display field's value must NOT be in there, which is the bug itself.
  assert.ok(!buttons.has('0') || SIMPLE_JSP.includes('TYPE=button VALUE="0"'),
    'the display field was parsed as a button');
  for (const scenario of ARITHMETIC_CASES) {
    for (const key of scenario.keys) {
      assert.ok(buttons.has(key), `the calculator has no "${key}" button`);
    }
  }
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

test('every computed scenario uses a valid age, and every refused one does not', () => {
  for (const scenario of FRACTURE_CASES) {
    assert.match(scenario.age, /^\d+$/, 'every computed scenario must use a valid age');
    assert.notEqual(parseAge(scenario.age, 50, 120), null, `${scenario.why}: the page would refuse ${scenario.age}`);
  }
  // The two filed shapes must stay in the list: they are the defects.
  assert.ok(INVALID_AGE_CASES.some((scenario) => scenario.age === ''), 'finding 8 (blank age) must be asserted');
  assert.ok(INVALID_AGE_CASES.some((scenario) => /^[a-z]+$/.test(scenario.age)),
    'finding 9 (non-numeric age) must be asserted');
  for (const scenario of INVALID_AGE_CASES) {
    assert.equal(parseAge(scenario.age, 50, 120), null, `${scenario.why}: the fracture page would accept ${scenario.age}`);
    assert.equal(parseAge(scenario.age, 20, 79), null, `${scenario.why}: the coronary page would accept ${scenario.age}`);
  }
  assert.equal(parseAge(FRACTURE_BELOW_TABLE_AGE, 50, 120), null);
  assert.equal(parseAge(FRACTURE_BELOW_TABLE_AGE, 20, 79), 49, 'the coronary tables answer for a 49-year-old');
  for (const outside of CORONARY_OUTSIDE_TABLE_AGES) {
    assert.equal(parseAge(outside, 20, 79), null, `the coronary tables have no band for a ${outside}-year-old`);
  }
  // The two pages' bounds differ, and the cases must show it: an 80-year-old
  // is inside the fracture table (whose last row is open-ended) and outside
  // the coronary one.
  assert.ok(CORONARY_OUTSIDE_TABLE_AGES.includes('80'));
  assert.equal(parseAge('80', 50, 120), 80);
});

test('the shared age parser refuses exactly what the pages must refuse', () => {
  // The same code the JSPs run, exercised directly: a whole number inside the
  // table's range and nothing else. Whitespace is trimmed because a pasted age
  // arrives with it; everything that changes the number is refused, never
  // truncated to something plausible.
  assert.equal(parseAge('54', 50, 120), 54);
  assert.equal(parseAge(' 60 ', 50, 120), 60);
  assert.equal(parseAge('50', 50, 120), 50, 'the lower bound is inclusive');
  assert.equal(parseAge('120', 50, 120), 120, 'the upper bound is inclusive');
  for (const rejected of ['', '   ', 'abc', '54a', '54.5', '-5', '+54', '49', '121', '0', undefined, null]) {
    assert.equal(parseAge(rejected, 50, 120), null, `${JSON.stringify(rejected)} must be refused`);
  }
  assert.equal(parseAge(0, 20, 79), null, 'zero is below every table');
  assert.equal(parseAge(72, 20, 79), 72, 'a number is accepted as its own text');
});

test('both pages guard the age with the shared parser and their own table bounds', () => {
  // The guard lives in the pages' own calculate(); if either stops calling it,
  // the browser check still fails, but this says which page and why first.
  for (const [name, jsp, min, max] of [['OsteoporoticFracture', FRACTURE_JSP, 50, 120], ['CoronaryArteryDiseaseRiskPrediction', CORONARY_JSP, 20, 79]]) {
    assert.match(jsp, /share\/javascript\/clinicalCalculatorAge\.js/, `${name} must load the shared parser`);
    assert.match(jsp, /CarlosCalculatorAge\.parseAge\(document\.calCorArDi\.age\.value, AGE_MIN, AGE_MAX\)/,
      `${name} must parse the age box through the shared parser`);
    assert.match(jsp, new RegExp(`var AGE_MIN = ${min};`), `${name}'s first table row is ${min}`);
    assert.match(jsp, new RegExp(`var AGE_MAX = ${max};`), `${name}'s last table band ends at ${max}`);
    assert.match(jsp, new RegExp(`<fmt:message key="encounter\\.calculators\\.${name}\\.msgInvalidAge" var="msgInvalidAge"\\/>`),
      `${name} must print its refusal from the i18n bundle`);
    // The message is spliced into a JavaScript string, so it must be encoded for
    // that context -- and the page must actually declare the taglibs it uses:
    // the coronary page never had, so <fmt:message> rendered literally inside
    // the string and the whole script block failed to parse on a real install.
    assert.match(jsp, /prediction\.value = "\$\{carlos:forJavaScript\(msgInvalidAge\)\}";/,
      `${name} must JavaScript-encode the refusal message`);
    assert.match(jsp, /<%@ taglib uri="jakarta\.tags\.fmt" prefix="fmt" %>/, `${name} must declare the fmt taglib`);
    assert.match(jsp, /<%@ taglib uri="carlos" prefix="carlos" %>/, `${name} must declare the carlos taglib`);
    assert.match(jsp, /<fmt:setBundle basename="oscarResources"\/>/, `${name} must set the bundle`);
  }
  // The coronary band index must be local to calculate(): the page-level copy
  // is what carried the previous patient's band into a NaN age (finding 9).
  assert.ok(!/^\s*var ageGroup = 0;\s*$/m.test(CORONARY_JSP.slice(0, CORONARY_JSP.indexOf('function calculate()'))),
    'CoronaryArteryDiseaseRiskPrediction.jsp declares ageGroup at page level again');
  assert.match(CORONARY_JSP, /function calculate\(\) \{[\s\S]{0,1200}var ageGroup = 0;/,
    'ageGroup must be declared inside calculate()');
});

test('the refusal message exists in every bundle and matches what the check looks for', () => {
  // The browser check matches the message by its "whole number from N to M"
  // fragment, so the bundles must keep saying that; and a missing key would
  // render as "???key???", which the regex would then reject as a refusal.
  const bundles = fs.readdirSync(BUNDLE_DIR).filter((name) => /^oscarResources_.*\.properties$/.test(name));
  assert.ok(bundles.length >= 5, `expected the five locale bundles, found ${bundles.length}`);
  for (const bundle of bundles) {
    const text = fs.readFileSync(path.join(BUNDLE_DIR, bundle), 'utf8');
    for (const [key, min, max] of [['OsteoporoticFracture', 50, 120], ['CoronaryArteryDiseaseRiskPrediction', 20, 79]]) {
      const line = text.split('\n').find((candidate) => candidate.startsWith(`encounter.calculators.${key}.msgInvalidAge=`));
      assert.ok(line, `${bundle} lacks encounter.calculators.${key}.msgInvalidAge`);
      const message = line.slice(line.indexOf('=') + 1);
      assert.match(message, REFUSED_AGE_TEXT, `${bundle}: the ${key} message no longer says which range is accepted`);
      assert.ok(message.includes(`from ${min} to ${max}`), `${bundle}: the ${key} message must state its own table's range`);
    }
  }
});

test('the findings log records 8 and 9 as fixed, with the fix named', () => {
  assert.match(FINDINGS, /A blank age is treated as 50/);
  assert.match(FINDINGS, /A non-numeric age selects the OLDEST band/);
  const rows = FINDINGS.split('\n').filter((line) => /^\|\s*(8|9)\s*\|/.test(line));
  assert.equal(rows.length, 2, 'findings 8 and 9 must both still be recorded');
  for (const row of rows) {
    assert.match(row, /`fixed`\s*\|\s*$/, `finding row is not marked fixed: ${row.slice(0, 60)}`);
    assert.match(row, /clinicalCalculatorAge\.js/, 'the row must name the fix so a reader can re-check it');
  }
});

test('the coronary point-count parser reads the page\'s actual output format', () => {
  assert.match(CORONARY_JSP, /Total Point Count:\s*"\s*\+ Total \+/);
  assert.equal(parsePointCount('*****\n* Total Point Count:  12        \n* 10-year Risk: LOW - 2%\n'), '12');
  assert.equal(parsePointCount('* Total Point Count: -3\n'), '-3');
  assert.equal(parsePointCount(''), '');
  // A refusal must never parse as a score.
  assert.equal(parsePointCount("Enter the patient's age as a whole number from 20 to 79 before calculating."), '');
});

test('the calculators are reached by clicking, never by their URL', () => {
  assert.ok(SOURCE.includes('a[onclick*="ViewCalculators"]'));
  const navigation = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/navigation.jsp'), 'utf8');
  assert.match(navigation, /ViewCalculators/);
  // The DEFAULT chart layout (newEncounterLayout) renders newEncounterHeader.jsp,
  // not navigation.jsp; it had no calculators control at all until #3665, so
  // the opener above found nothing on a packaged install. Both layouts must
  // keep the control, and the header's must carry the onclick the opener looks
  // for. The header briefly carried TWO such controls (phc007 reported the pair);
  // it now carries exactly one, and it is the demo= form, which resolves sex and
  // age from the record instead of putting them in the URL.
  const header = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp'), 'utf8');
  const headerMarkup = header.replace(/<%--[\s\S]*?--%>/g, '');
  const openers = headerMarkup.match(/on[Cc]lick="[^"]*\/encounter\/ViewCalculators[^"]*"/g) || [];
  assert.equal(openers.length, 1,
    `the default chart header must offer exactly one calculators control, found ${openers.length}`);
  assert.match(openers[0], /window\.open\(/,
    'the calculators control must open a popup, which is what the opener above clicks');
  assert.match(openers[0], /ViewCalculators\?demo=/,
    'the calculators control must resolve the patient from the record, not from sex/age in the URL');
  assert.doesNotMatch(openers[0], /(?:[?&]|&amp;)(?:sex|age)=/,
    'the calculator opener must not append patient sex or age to the record reference');
  assert.ok(!/page\.goto\(/.test(SOURCE),
    'entering by address would skip the chart header opener this check exists to exercise');
  assert.ok(!/ViewOsteoporoticFracture|ViewSimpleCalculator/.test(SOURCE),
    'the check must name the link a clinician clicks, not the route behind it');
});

test('legacy chart calculator launchers also use record references without sex or age', () => {
  for (const relative of ['casemgmt/navigation.jsp', 'encounter/includes/encounter-header-bar.jspf']) {
    const markup = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp', relative), 'utf8');
    const launches = markup.split('\n').filter((line) => line.includes('/encounter/ViewCalculators?'));
    assert.equal(launches.length, 1, `${relative} must retain one calculator launcher`);
    assert.match(launches[0], /ViewCalculators\?demo=/);
    assert.doesNotMatch(launches[0], /(?:[?&]|&amp;)(?:sex|age)=/);
  }
});

test('the run refuses to report success having computed nothing', () => {
  // Every assertion in this check lives inside a loop over a table. An empty
  // table would leave them all unexecuted, and the check would pass. The floors
  // have to stay below the table sizes, or they fail on a correct run.
  assert.match(SOURCE, /fracture\.computed\.length >= 5/);
  assert.match(SOURCE, /fracture\.refused\.length >= 6 && coronary\.refused\.length >= 7/);
  assert.match(SOURCE, /arithmetic\.length >= 4/);
  assert.ok(FRACTURE_CASES.length >= 5, 'the floor must be reachable');
  // The fracture page refuses INVALID_AGE_CASES plus the below-table age.
  assert.ok(INVALID_AGE_CASES.length + 1 >= 6 && INVALID_AGE_CASES.length >= 5, 'the floor must be reachable');
  assert.ok(ARITHMETIC_CASES.length >= 4, 'the floor must be reachable');
});
