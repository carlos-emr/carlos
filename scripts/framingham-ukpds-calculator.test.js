/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Unit tests for the Framingham/UKPDS risk calculator's input guard and for the
 * retired static copies of the chart calculators (issue #3665).
 *
 * The calculator is static HTML + js/js.js with no server round trip, so its
 * guard is exercised here against the page's own script with a minimal fake
 * DOM; scripts/clinical-calculators-playwright-checks.js drives the same pages
 * in a browser. Findings 8 and 9 fixed the age ladders of the other two risk
 * calculators; this one had the same fault in every box: a blank or mistyped
 * value became NaN, every "<=" compared false and the page highlighted its
 * HIGHEST-risk band.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEBAPP = path.join(__dirname, '..', 'src', 'main', 'webapp');
const RISKCALC = path.join(WEBAPP, 'encounter', 'calculators', 'riskcalc');
const CALCULATORS_DIR = path.join(WEBAPP, 'encounter', 'calculators');

// js.js expects the shared age parser as a page global, exactly as the HTML loads it.
global.CarlosCalculatorAge = require('../src/main/webapp/share/javascript/clinicalCalculatorAge');
const calc = require('../src/main/webapp/encounter/calculators/riskcalc/js/js.js');

const FRAMINGHAM_CELL = /^bp\d+c\d+$/;
const UKPDS_CELL = /^UKPDS_bp\d+c\d+h\d+$/;

/** A fake element: enough of the DOM for js.js's reads and writes. */
function element(id) {
  const attributes = {};
  let text = '';
  return {
    id,
    value: '',
    checked: false,
    style: {},
    children: [],
    get textContent() { return text; },
    set textContent(next) { text = String(next); this.children = []; },
    get innerHTML() { return text; },
    set innerHTML(next) { text = String(next); this.children = []; },
    setAttribute(name, value) { attributes[name] = String(value); },
    removeAttribute(name) { delete attributes[name]; },
    getAttribute(name) { return name in attributes ? attributes[name] : null; },
    hasAttribute(name) { return name in attributes; },
    appendChild(child) { this.children.push(child); },
    listeners: {},
    addEventListener(type, listener) { this.listeners[type] = listener; },
  };
}

/** A page holding both result tables' cells, so a test can see what was written. */
function fakePage(values, search = '') {
  const elements = new Map();
  const get = (id) => {
    if (!elements.has(id)) elements.set(id, element(id));
    return elements.get(id);
  };
  for (let b = 2; b <= 8; ++b) {
    get(`bp${b}`);
    for (let c = 1; c <= 5; ++c) get(`bp${b}c${c}`);
  }
  for (let c = 1; c <= 5; ++c) get(`c${c}`);
  for (let b = 0; b <= 3; ++b) {
    get(`UKPDS_bp${b}`);
    for (let c = 1; c <= 3; ++c) {
      get(`UKPDS_bp${b}c${c}`);
      for (let h = 1; h <= 4; ++h) get(`UKPDS_bp${b}c${c}h${h}`);
    }
  }
  for (let h = 1; h <= 4; ++h) get(`UKPDS_h${h}`);
  for (const [id, value] of Object.entries(values)) get(id).value = value;
  get('cMale').checked = true;
  get('cNonSmoker').checked = true;
  get('cWhite').checked = true;
  get('cAtrialNo').checked = true;
  const other = get('otherCalculator');
  other.setAttribute('href', 'diabetic.html');
  global.document = {
    getElementById: get,
    querySelectorAll: () => [...elements.values()].filter((el) => /^(?:bp|c\d|UKPDS_)/.test(el.id)),
    createElement: () => element(''),
  };
  global.window = { location: { search } };
  return { get, figures: (pattern) => [...elements.values()].filter((el) => pattern.test(el.id)) };
}

const FRAMINGHAM_VALID = { cAge: '55', cSystolic: '140', cCholesterol: '5', cHDL: '1' };
const UKPDS_VALID = { ...FRAMINGHAM_VALID, cDuration: '5', cALC: '6' };

function refusal(page) {
  const out = page.get('theScript');
  return out.children.map((child) => child.textContent).join('');
}

function assertRefused(page, pattern, field, why) {
  const message = refusal(page);
  assert.match(message, /Nothing has been calculated\./, `${why}: no refusal was shown`);
  assert.match(message, pattern, `${why}: the refusal does not name the box to correct (${message})`);
  assert.equal(page.get(field).getAttribute('aria-invalid'), 'true', `${why}: ${field} is not marked invalid`);
  for (const cell of [...page.figures(FRAMINGHAM_CELL), ...page.figures(UKPDS_CELL)]) {
    assert.equal(cell.textContent, '', `${why}: ${cell.id} still shows ${cell.textContent} under a refusal`);
    assert.notEqual(cell.style.fontWeight, 'bold', `${why}: ${cell.id} is still highlighted under a refusal`);
  }
}

test('parseRiskNumber accepts plain numbers inside the range and refuses everything else', () => {
  const systolic = calc.RISK_INPUTS.cSystolic;
  assert.equal(calc.parseRiskNumber('140', systolic), 140);
  assert.equal(calc.parseRiskNumber(' 149.5 ', systolic), 149.5);
  assert.equal(calc.parseRiskNumber('60', systolic), 60, 'the lower bound is inclusive');
  assert.equal(calc.parseRiskNumber('300', systolic), 300, 'the upper bound is inclusive');
  assert.equal(calc.parseRiskNumber('.9', calc.RISK_INPUTS.cHDL), 0.9);
  assert.equal(calc.parseRiskNumber('1.0345', calc.RISK_INPUTS.cHDL), 1.0345, 'any number of decimals is accepted');
  assert.equal(calc.parseRiskNumber('24', calc.RISK_INPUTS.cCholesterol), 24, 'familial hypercholesterolaemia values are accepted');
  for (const rejected of ['', '   ', '.', 'abc', '140mmHg', '1,2', '-5', '+140', '1e2', '59', '301', 'NaN',
    'Infinity', undefined, null]) {
    assert.equal(calc.parseRiskNumber(rejected, systolic), null, `${JSON.stringify(rejected)} must be refused`);
  }
  // HDL is the divisor of the lipid ratio: zero used to divide to Infinity and
  // land in the highest cholesterol column.
  assert.equal(calc.parseRiskNumber('0', calc.RISK_INPUTS.cHDL), null);
});

test('the age is whole-number only and keeps the range the page used to clamp to', () => {
  const age = calc.RISK_INPUTS.cAge;
  assert.deepEqual([age.min, age.max], [30, 75]);
  assert.equal(calc.parseRiskNumber('30', age), 30);
  assert.equal(calc.parseRiskNumber('75', age), 75);
  for (const rejected of ['', 'abc', '54.5', '29', '76', '-40']) {
    assert.equal(calc.parseRiskNumber(rejected, age), null, `age ${JSON.stringify(rejected)} must be refused`);
  }
  // The refusal text is the shape the other calculators' checks already match.
  assert.match(calc.riskInputMessage(age, ''), /whole number from 30 to 75 years/);
  assert.match(calc.riskInputMessage(age, 'abc'), /whole number from 30 to 75 years/);
  assert.match(calc.riskInputMessage(age, '82'), /does not apply to a patient aged 82\./);
  assert.doesNotMatch(calc.riskInputMessage(age, '82'), /Enter/, 'a real age outside the table is not a typo to correct');
});

test('a valid Framingham entry computes and highlights the matching cell', () => {
  const page = fakePage(FRAMINGHAM_VALID);
  calc.UpdateNonDiabetic();
  const figures = page.figures(FRAMINGHAM_CELL);
  assert.equal(figures.length, 35);
  for (const cell of figures) {
    assert.match(cell.textContent, /^≥?\d+%$/, `${cell.id} reads ${cell.textContent}`);
  }
  // Systolic 140 is the "140" row (bp6) and 5 / 1 is the "5" column (c3).
  const bold = figures.filter((cell) => cell.style.fontWeight === 'bold').map((cell) => cell.id);
  assert.deepEqual(bold, ['bp6c3']);
  assert.equal(refusal(page), '');
  assert.match(page.get('theScript').innerHTML, /risk/);
});

for (const [field, value, pattern, why] of [
  ['cAge', '', /age as a whole number from 30 to 75/, 'blank age'],
  ['cAge', 'abc', /age as a whole number from 30 to 75/, 'non-numeric age'],
  ['cAge', '80', /covers ages 30 to 75 years; it does not apply to a patient aged 80\./,
    'age above the range (was clamped to 75)'],
  ['cAge', '25', /does not apply to a patient aged 25\./, 'age below the range (was clamped to 30)'],
  ['cSystolic', '', /systolic blood pressure as a number from 60 to 300 mmHg/,
    'blank systolic (was the >=160 row)'],
  ['cCholesterol', '1,2', /total cholesterol as a number from 1 to 25 mmol\/L/, 'comma decimal (was read as 1)'],
  ['cHDL', '0', /HDL cholesterol as a number from 0.1 to 5 mmol\/L/, 'zero HDL (divided by zero)'],
  ['cHDL', 'x', /HDL cholesterol/, 'non-numeric HDL (was the highest column)'],
]) {
  test(`Framingham refuses ${why} and clears the previous answer`, () => {
    const page = fakePage(FRAMINGHAM_VALID);
    calc.UpdateNonDiabetic();
    assert.ok(page.figures(FRAMINGHAM_CELL).every((cell) => cell.textContent !== ''), 'precondition: a table');
    page.get(field).value = value;
    calc.UpdateNonDiabetic();
    assertRefused(page, pattern, field, why);
    assert.equal(page.get('cAge').value, field === 'cAge' ? value : '55',
      'the typed age must be left as typed, never rewritten to a clamped one');

    // Correcting the box computes again, and the invalid mark goes away.
    page.get(field).value = FRAMINGHAM_VALID[field];
    calc.UpdateNonDiabetic();
    assert.equal(refusal(page), '');
    assert.equal(page.get(field).getAttribute('aria-invalid'), null);
    assert.ok(page.figures(FRAMINGHAM_CELL).every((cell) => /%$/.test(cell.textContent)));
  });
}

test('a valid UKPDS entry computes, including a decimal systolic between 149 and 150', () => {
  const page = fakePage({ ...UKPDS_VALID, cSystolic: '149.5' });
  // The ladder ended "else if (systolic >= 150)", so 149.5 matched no band and
  // the page threw before writing the advice.
  assert.doesNotThrow(() => calc.UpdateDiabetic());
  const figures = page.figures(UKPDS_CELL);
  assert.equal(figures.length, 48);
  for (const cell of figures) {
    assert.match(cell.textContent, /^\d+%$/, `${cell.id} reads ${cell.textContent}`);
  }
  const bold = figures.filter((cell) => cell.style.fontWeight === 'bold').map((cell) => cell.id);
  assert.deepEqual(bold, ['UKPDS_bp2c2h2']);
  assert.equal(refusal(page), '');
});

for (const [field, value, pattern, why] of [
  ['cSystolic', '', /systolic blood pressure/, 'blank systolic (threw and left the previous answer on screen)'],
  ['cALC', '', /A1C as a number from 3 to 20 %/, 'blank A1C'],
  ['cDuration', 'five', /duration of diabetes as a number from 0 to 54 years/, 'non-numeric duration'],
  ['cDuration', '55', /duration of diabetes as a number from 0 to 54 years \(it must be less than the patient's age\)/,
    'a duration as long as the patient has lived'],
]) {
  test(`UKPDS refuses ${why} and clears the previous answer`, () => {
    const page = fakePage(UKPDS_VALID);
    calc.UpdateDiabetic();
    assert.ok(page.figures(UKPDS_CELL).every((cell) => cell.textContent !== ''), 'precondition: a table');
    page.get(field).value = value;
    assert.doesNotThrow(() => calc.UpdateDiabetic());
    assertRefused(page, pattern, field, why);
  });
}

test('the chart prefill sets sex and age and carries the patient to the other calculator', () => {
  const page = fakePage(FRAMINGHAM_VALID, '?sex=F&age=62');
  calc.PrefillFromChart();
  assert.equal(page.get('cFemale').checked, true);
  assert.equal(page.get('cAge').value, '62');
  // The switch link carries what is in the form when clicked, not the chart's original values.
  assert.equal(calc.OtherCalculatorHref('diabetic.html'), 'diabetic.html?sex=F&age=62');
  const other = page.get('otherCalculator');
  other.listeners.click();
  assert.equal(other.href, 'diabetic.html?sex=F&age=62', 'clicking the switch link must rewrite it from the form');
  page.get('cAge').value = '64';
  page.get('cFemale').checked = false;
  page.get('cMale').checked = true;
  assert.equal(calc.OtherCalculatorHref('diabetic.html?sex=F&age=62'), 'diabetic.html?sex=M&age=64');
  // Values are URL-encoded, so typed text cannot add parameters.
  page.get('cAge').value = '6&x=1';
  assert.equal(calc.OtherCalculatorHref('diabetic.html'), 'diabetic.html?sex=M&age=6%26x%3D1');
});

test('an age the chart supplies outside the range is refused by name, not answered for the default', () => {
  const page = fakePage(FRAMINGHAM_VALID, '?sex=M&age=82');
  calc.PrefillFromChart();
  calc.UpdateNonDiabetic();
  assert.equal(page.get('cAge').value, '82');
  assertRefused(page, /covers ages 30 to 75 years; it does not apply to a patient aged 82\./, 'cAge', 'chart age 82');
});

test('without a chart query the page keeps its defaults', () => {
  const page = fakePage(FRAMINGHAM_VALID, '');
  calc.PrefillFromChart();
  assert.equal(page.get('cAge').value, '55');
  assert.equal(page.get('cMale').checked, true);
  assert.equal(page.get('otherCalculator').getAttribute('href'), 'diabetic.html');
});

test('both pages load the shared age parser before js.js and prefill before computing', () => {
  for (const [file, update, other] of [['index.html', 'UpdateNonDiabetic', 'diabetic.html'],
    ['diabetic.html', 'UpdateDiabetic', 'index.html']]) {
    const html = fs.readFileSync(path.join(RISKCALC, file), 'utf8');
    const parser = html.indexOf('src="../../../share/javascript/clinicalCalculatorAge.js"');
    const script = html.indexOf('src="js/js.js"');
    assert.ok(parser > 0 && script > parser, `${file} must load clinicalCalculatorAge.js before js.js`);
    assert.match(html, new RegExp(`<body onload="PrefillFromChart\\(\\); ${update}\\(\\)">`));
    assert.match(html, new RegExp(`<a id="otherCalculator" href="${other.replace('.', '\\.')}">`));
  }
  // The shared parser resolves from the page's own directory to the webapp root.
  assert.ok(fs.existsSync(path.join(RISKCALC, '../../../share/javascript/clinicalCalculatorAge.js')));
});

test('the chart calculators index passes the patient sex and age to this calculator', () => {
  const index = fs.readFileSync(path.join(WEBAPP, 'WEB-INF', 'jsp', 'encounter', 'calculators.jsp'), 'utf8');
  assert.match(index, /riskcalc\/index\.html\?sex=\$\{sexEncoded\}&age=\$\{ageEncoded\}/);
});

/*
 * The retired static copies. OsteoporoticFracture.htm and
 * CoronaryArteryDiseaseRiskPrediction.html were public duplicates of the
 * maintained WEB-INF pages and still carried the unguarded age ladder findings
 * 8 and 9 fixed there; each is now only a redirect, like GeneralCalculators.htm.
 */
const LEGACY = [
  ['OsteoporoticFracture.htm', 'ViewOsteoporoticFracture'],
  ['CoronaryArteryDiseaseRiskPrediction.html', 'ViewCoronaryArteryDiseaseRiskPrediction'],
  ['SimpleCalculator.htm', 'ViewSimpleCalculator'],
  ['GeneralCalculators.htm', 'ViewGeneralCalculators'],
];

for (const [file, route] of LEGACY) {
  test(`${file} is a redirect to ${route} with no calculator code left in it`, () => {
    const source = fs.readFileSync(path.join(CALCULATORS_DIR, file), 'utf8');
    assert.doesNotMatch(source, /age\s*<=|calculate\(|<form/i, `${file} still carries calculator code`);
    assert.match(source, /Copyright \(c\) 2001-2002\. Department of Family Medicine, McMaster University/,
      `${file} must keep its upstream copyright notice`);
    const struts = fs.readFileSync(path.join(WEBAPP, 'WEB-INF', 'classes', 'struts-clinical.xml'), 'utf8');
    assert.ok(struts.includes(`<action name="encounter/calculators/${route}"`), `${route} is not a mapped action`);
  });
}

for (const [file, route] of LEGACY.slice(0, 3)) {
  for (const [search, hash] of [['', ''], ['?sex=F&age=67', ''], ['?sex=M&age=72', '#top']]) {
    test(`${file} forwards ${search || '(no query)'}${hash} to the maintained page`, () => {
      const source = fs.readFileSync(path.join(CALCULATORS_DIR, file), 'utf8');
      const start = source.indexOf('<script>');
      const end = source.indexOf('</script>', start);
      assert.ok(start >= 0 && end > start);
      const link = { href: route };
      let destination;
      // Executes only the checked-in fixture above in a mock VM; no external input or HTML output.
      // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag
      vm.runInNewContext(source.slice(start + '<script>'.length, end), {
        document: { getElementById: () => link },
        window: { location: { search, hash, replace: (value) => { destination = value; } } },
      });
      assert.equal(destination, `${route}${search}${hash}`);
      assert.equal(link.href, destination);
    });
  }
}

/*
 * The browser check's expectations, replayed against the page's own script, so
 * clinical-calculators-playwright-checks.js cannot assert a cell or a refusal
 * the page does not produce.
 */
test('the browser check\'s valid entries highlight the cells it expects', () => {
  const {
    FRAMINGHAM_VALID: framingham, FRAMINGHAM_VALID_CELL, UKPDS_VALID: ukpds, UKPDS_VALID_CELL,
  } = require('./clinical-calculators-playwright-checks');
  let page = fakePage(framingham);
  calc.UpdateNonDiabetic();
  assert.deepEqual(page.figures(FRAMINGHAM_CELL).filter((c) => c.style.fontWeight === 'bold').map((c) => c.id),
    [FRAMINGHAM_VALID_CELL]);
  page = fakePage(ukpds);
  calc.UpdateDiabetic();
  assert.deepEqual(page.figures(UKPDS_CELL).filter((c) => c.style.fontWeight === 'bold').map((c) => c.id),
    [UKPDS_VALID_CELL]);
});

test('every refusal the browser check asserts is one the page makes, with the message it matches', () => {
  const {
    FRAMINGHAM_REFUSALS, FRAMINGHAM_VALID: framingham, UKPDS_REFUSALS, UKPDS_VALID: ukpds,
  } = require('./clinical-calculators-playwright-checks');
  assert.ok(FRAMINGHAM_REFUSALS.some((s) => s.field === 'cSystolic' && s.value === ''),
    'the blank systolic (highest row) case must stay in the browser check');
  assert.ok(FRAMINGHAM_REFUSALS.some((s) => s.field === 'cHDL' && s.value === '0'),
    'the zero-HDL (divide by zero) case must stay in the browser check');
  for (const [refusals, valid, update] of [[FRAMINGHAM_REFUSALS, framingham, calc.UpdateNonDiabetic],
    [UKPDS_REFUSALS, ukpds, calc.UpdateDiabetic]]) {
    for (const scenario of refusals) {
      const page = fakePage({ ...valid, [scenario.field]: scenario.value });
      update();
      assertRefused(page, scenario.says, scenario.field, scenario.why);
    }
  }
});
