/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Issue #5152 / PR #5153: the measurement-entry popup (Measurements.jsp) converts a waist typed in
// inches to cm on double click and fills the waist-to-height ratio (WHtR) from WAIS and HT. The
// first version read an undeclared `w` and threw a ReferenceError on every blur, wrote "NaN" for a
// value such as "80 cm", left a ratio behind after its inputs were cleared, and looked only for
// row-WHtR although Add Measurement Type stores the code upper-cased (row-WHTR). This runs the
// page's own closing script block against a stub DOM and pins those behaviours. The browser-level
// proof is the `measurement-whtr` Playwright check.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// MEASUREMENTS_JSP only points the test at another checked-in revision of the same page, to show
// which cases an older version fails.
const JSP = process.env.MEASUREMENTS_JSP || path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/encounter/oscarMeasurements/Measurements.jsp');
const source = fs.readFileSync(JSP, 'utf8');

// The code Add Measurement Type stores for a clinic's WHtR type: it upper-cases every code.
const RATIO = 'WHTR';

/** The text strictly between the nearest `openTag` before `index` and the first `closeTag` after it. */
function enclosingBlock(text, index, openTag, closeTag) {
  const open = text.lastIndexOf(openTag, index);
  const close = text.indexOf(closeTag, index);
  assert.ok(open >= 0 && close > index && !text.slice(open, index).includes(closeTag),
    'the definition is not inside one block');
  return text.slice(open + openTag.length, close);
}

/** The page's closing script block: the one that defines calcWHtR. */
function calculationScript() {
  const definition = source.indexOf('function calcWHtR(');
  assert.ok(definition >= 0, 'Measurements.jsp does not define calcWHtR');
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Extracts executable test code only from the fixed, checked-in JSP above; no external data or HTML response is accepted or rendered.
  const script = enclosingBlock(source, definition, '<script>', '</script>');
  assert.ok(!/<%|\$\{/.test(script),
    'the calculation script must stay free of JSP scriptlets and EL, or this test can no longer run it as written');
  return script;
}

function input(name) {
  const listeners = {};
  return {
    name,
    value: '',
    title: '',
    style: { backgroundColor: '' },
    dataset: {},
    addEventListener(type, fn) {
      (listeners[type] = listeners[type] || []).push(fn);
    },
    fire(type) {
      for (const fn of listeners[type] || []) fn.call(this, { type, target: this });
    },
    listenerCount(type) {
      return (listeners[type] || []).length;
    },
  };
}

/** A rendered measurement row: type, instruction, value, date and comment cells, as the JSP emits. */
function row(type) {
  const value = input(`inputValue-${type}`);
  const cells = [{}, {}, { querySelector: (selector) => (selector === 'input' ? value : null) }, {}, {}];
  return { id: `row-${type}`, value, querySelectorAll: (selector) => (selector === 'td' ? cells : []) };
}

/**
 * Loads the page script for a group with the given measurement type codes and fires
 * DOMContentLoaded. `confirmAnswer` answers every conversion prompt; the prompts are returned.
 */
function renderGroup(types, { confirmAnswer = true } = {}) {
  const rows = types.map(row);
  const loaded = [];
  const prompts = [];
  const document = {
    getElementById(id) {
      return rows.find((candidate) => candidate.id === id) || null;
    },
    // The one selector the page builds: a row id matched with the ASCII case-insensitive flag.
    querySelector(selector) {
      const match = /^tr\[id="([^"]+)" i\]$/.exec(selector);
      assert.ok(match, `unexpected selector ${selector}`);
      return rows.find((candidate) => candidate.id.toLowerCase() === match[1].toLowerCase()) || null;
    },
    addEventListener(type, fn) {
      if (type === 'DOMContentLoaded') loaded.push(fn);
    },
    querySelectorAll() {
      return [];
    },
  };
  const confirm = (message) => {
    prompts.push(String(message));
    return confirmAnswer;
  };
  const context = vm.createContext({ document, confirm });
  vm.runInContext(calculationScript(), context);
  for (const fn of loaded) fn();
  const value = (type) => rows.find((candidate) => candidate.id === `row-${type}`).value;
  return { value, prompts };
}

function enter(field, text) {
  field.value = text;
  field.fire('blur');
}

function type(field, text) {
  field.value = text;
  field.fire('input');
}

test('a waist and a height in cm fill WHtR to two decimals without a script error', () => {
  const page = renderGroup(['HT', 'WAIS', RATIO]);
  enter(page.value('HT'), '177.8');
  assert.doesNotThrow(() => enter(page.value('WAIS'), '86.4'),
    'blurring the waist threw; the calculation must not reference an undeclared variable');
  assert.equal(page.value(RATIO).value, '0.49');
  assert.equal(page.value(RATIO).style.backgroundColor, '#d9e6f2', 'a calculated WHtR is marked like BMI');
});

test('the WHtR row is found whatever case the clinic type code has', () => {
  for (const code of ['WHTR', 'WHtR', 'whtr']) {
    const page = renderGroup(['HT', 'WAIS', code]);
    enter(page.value('HT'), '170');
    enter(page.value('WAIS'), '85');
    assert.equal(page.value(code).value, '0.50', `a WHtR type coded ${code} was not filled`);
  }
});

test('leaving the height field recalculates the ratio from the current waist', () => {
  const page = renderGroup(['HT', 'WAIS', RATIO]);
  enter(page.value('WAIS'), '90');
  assert.equal(page.value(RATIO).value, '', 'no ratio before there is a height');
  enter(page.value('HT'), '180');
  assert.equal(page.value(RATIO).value, '0.50');
  enter(page.value('HT'), '150');
  assert.equal(page.value(RATIO).value, '0.60');
});

test('a value with a unit, a feet mark or no finite size never puts NaN or a bogus ratio into WHtR', () => {
  const huge = '9'.repeat(400);
  for (const [waist, height] of [['80 cm', '170'], ['80', '5\'7"'], ['80', '170cm'], ['abc', '170'], ['80', '1,70'],
    [huge, '170'], ['80', huge]]) {
    const page = renderGroup(['HT', 'WAIS', RATIO]);
    enter(page.value('HT'), height);
    enter(page.value('WAIS'), waist);
    assert.equal(page.value(RATIO).value, '', `waist ${waist.slice(0, 12)} and height ${height.slice(0, 12)} must not produce a ratio`);
  }
});

test('a calculated ratio is cleared when its waist is removed or its height becomes zero', () => {
  const page = renderGroup(['HT', 'WAIS', RATIO]);
  enter(page.value('HT'), '170');
  enter(page.value('WAIS'), '85');
  assert.equal(page.value(RATIO).value, '0.50');

  enter(page.value('WAIS'), '');
  assert.equal(page.value(RATIO).value, '', 'Submit would save a ratio for a waist the form no longer holds');
  assert.equal(page.value(RATIO).style.backgroundColor, '', 'a cleared ratio is no longer marked as calculated');

  enter(page.value('WAIS'), '85');
  assert.equal(page.value(RATIO).value, '0.50');
  enter(page.value('HT'), '0');
  assert.equal(page.value(RATIO).value, '', 'a zero height must not leave the earlier ratio behind');
});

test('a ratio the user typed in is kept when the inputs become unusable', () => {
  const page = renderGroup(['HT', 'WAIS', RATIO]);
  enter(page.value('HT'), '170');
  enter(page.value('WAIS'), '85');
  type(page.value(RATIO), '0.52');
  enter(page.value('WAIS'), '');
  assert.equal(page.value(RATIO).value, '0.52', 'only a value this page calculated may be cleared');
});

test('a ratio the user retyped to the calculated value is still the user\'s own', () => {
  const page = renderGroup(['HT', 'WAIS', RATIO]);
  enter(page.value('HT'), '170');
  enter(page.value('WAIS'), '85');
  type(page.value(RATIO), '0.50');
  enter(page.value('WAIS'), '');
  assert.equal(page.value(RATIO).value, '0.50', 'typing must mark the value as entered, whatever it is');
  enter(page.value('WAIS'), '85');
  assert.equal(page.value(RATIO).value, '0.50', 'a later usable waist calculates again');
  enter(page.value('WAIS'), '');
  assert.equal(page.value(RATIO).value, '', 'and that calculated value is cleared again');
});

test('double clicking a waist in inches converts it to cm and recalculates the ratio at once', () => {
  for (const typed of ['34', '34"', ' 34 " ']) {
    const page = renderGroup(['HT', 'WAIS', RATIO]);
    enter(page.value('HT'), '177.8');
    const waist = page.value('WAIS');
    assert.equal(waist.title, 'Double click to convert inches to cm');
    waist.value = typed;
    waist.fire('dblclick');
    assert.deepEqual(page.prompts, ['Are you sure you want to change 34 inch(es) to 86.4cm?'], `prompt for ${typed}`);
    assert.equal(String(waist.value), '86.4', `waist ${typed} was not converted`);
    assert.equal(page.value(RATIO).value, '0.49', 'the ratio must use the converted waist without another blur');
  }
});

test('a declined or impossible waist conversion leaves the typed value as it was', () => {
  const declined = renderGroup(['HT', 'WAIS', RATIO], { confirmAnswer: false });
  declined.value('WAIS').value = '34';
  declined.value('WAIS').fire('dblclick');
  assert.equal(declined.value('WAIS').value, '34');
  assert.equal(declined.prompts.length, 1);

  const page = renderGroup(['HT', 'WAIS', RATIO]);
  for (const typed of ['', 'abc', '0', '-34', '2\'10"', '9'.repeat(400)]) {
    page.value('WAIS').value = typed;
    page.value('WAIS').fire('dblclick');
    assert.equal(page.value('WAIS').value, typed, `"${typed.slice(0, 12)}" must not be converted`);
  }
  assert.deepEqual(page.prompts, [], 'nothing to convert must not ask the user anything');
});

test('converting a height in feet and inches recalculates the ratio at once', () => {
  const page = renderGroup(['HT', 'WAIS', RATIO]);
  enter(page.value('WAIS'), '86.4');
  const height = page.value('HT');
  height.value = '5\'10"';
  height.fire('blur');
  assert.equal(page.value(RATIO).value, '', 'feet and inches are not a height in cm');
  height.fire('dblclick');
  assert.equal(String(height.value), '177.8');
  assert.equal(page.value(RATIO).value, '0.49');
});

test('a group without a WHtR row converts the waist but wires no ratio', () => {
  const page = renderGroup(['HT', 'WT', 'BMI', 'WAIS']);
  const waist = page.value('WAIS');
  assert.equal(waist.listenerCount('blur'), 0, 'there is no WHtR to calculate');
  assert.equal(waist.listenerCount('dblclick'), 1, 'the inch conversion is offered on its own');
  enter(page.value('HT'), '177.8');
  enter(page.value('WT'), '68');
  assert.equal(page.value('BMI').value, '21.5', 'BMI keeps working beside the waist row');
});

test('a WHtR row without a waist row is left for manual entry', () => {
  const page = renderGroup(['HT', RATIO]);
  enter(page.value('HT'), '170');
  assert.equal(page.value('HT').listenerCount('blur'), 0);
  assert.equal(page.value(RATIO).listenerCount('input'), 0);
  assert.equal(page.value(RATIO).value, '');
});
