/**
 * Source tests for the prevention edit form's Date box (WEB-INF/jsp/prevention/AddPreventionData.jsp).
 *
 * A prevention can be dated to the year or the year and month only (a CDS import stores a
 * partial_date row). A date input cannot hold "2019": it opens empty and, being required, refuses
 * the save. The page therefore edits such a date in a text box that takes yyyy, yyyy-mm or
 * yyyy-mm-dd, the forms PreventionData.setPreventionDate saves. Run with:
 *   node --test scripts/prevention-partial-date-input.test.js
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const JSP = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'prevention', 'AddPreventionData.jsp'),
  'utf8',
);

/** Every <input ... name="prevDate" ...> tag, up to the "></div>" that closes it (its attributes hold JSP "%>"). */
const inputs = [...JSP.matchAll(/<input type="(?:(?!<input )[\s\S])*?name="prevDate"[\s\S]*?><\/div>/g)].map((m) => m[0]);

// The partial-date box's pattern attribute, as the JSP must spell it, and the same text as the regex a
// browser makes of it (anchored, v flag). The test pins the JSP to the text and checks dates with the
// literal, so no regex is built from file content.
const PATTERN = String.raw`\d{4}(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\d|3[01]))?)?`;
const PATTERN_AS_BROWSER = /^(?:\d{4}(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\d|3[01]))?)?)$/v;

// The scriptlet's test for a partial stored date: the Java literal, and the same regex (String.matches
// is anchored) as a literal.
const PARTIAL_JAVA_LITERAL = String.raw`"\\d{4}(-\\d{2})?"`;
const PARTIAL = /^(?:\d{4}(-\d{2})?)$/;

test('shouldSwitchEveryPreventionDateBoxToText_forAPartialDate', () => {
  assert.equal(inputs.length, 4, 'the page has four prevention layouts, each with a Date box');
  for (const tag of inputs) {
    assert.match(tag, /^<input type="<%= partialPrevDate \? "text" : "date" %>"/);
    assert.match(tag, /required<% if \(partialPrevDate\) \{ %> pattern="[^"]+" title="yyyy \| yyyy-mm \| yyyy-mm-dd"<% \} %>><\/div>$/);
    assert.equal(/pattern="([^"]+)"/.exec(tag)[1], PATTERN, 'every box carries the same pattern text');
  }
  assert.equal(PATTERN_AS_BROWSER.source, `^(?:${PATTERN})$`, 'the literal checked below is the pattern text');
});

test('shouldAcceptTheDatesThePreventionSaveTakes_inThePartialDateBox', () => {
  const pattern = PATTERN_AS_BROWSER;
  for (const ok of ['2019', '2019-05', '2019-12', '2019-05-14', '2019-01-31', '2020-02-29']) {
    assert.ok(pattern.test(ok), `${ok} should be accepted`);
  }
  for (const bad of ['19', '2019-5', '2019-13', '2019-00', '2019-05-1', '2019-05-32', '2019-05-00', '2019/05', '2019-05-14 10:00', ' 2019']) {
    assert.ok(!pattern.test(bad), `${bad} should be refused`);
  }
});

test('shouldTreatOnlyAYearOrAYearAndMonth_asAPartialDate', () => {
  assert.ok(JSP.includes(`partialPrevDate = prevDate != null && prevDate.matches(${PARTIAL_JAVA_LITERAL});`),
    'the page decides partialPrevDate from the stored date with that literal');
  // The Java literal's "\\" is one backslash in the regex.
  assert.equal(`^(?:${PARTIAL_JAVA_LITERAL.slice(1, -1).replaceAll('\\\\', '\\')})$`, PARTIAL.source);
  for (const yes of ['2019', '2019-05']) assert.ok(PARTIAL.test(yes), `${yes} is partial`);
  for (const no of ['2019-05-14', '2019-05-14 10:00', '', '201']) assert.ok(!PARTIAL.test(no), `${no} is not partial`);
});

/** The page's own source for `function name(...) { ... }`, up to its matching closing brace. */
function pageFunction(name) {
  const start = JSP.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `the page defines ${name}`);
  let depth = 0;
  for (let i = JSP.indexOf('{', start); i < JSP.length; i++) {
    if (JSP[i] === '{') depth++;
    if (JSP[i] === '}' && --depth === 0) return JSP.slice(start, i + 1);
  }
  throw new Error(`${name} is not closed`);
}

/** Runs the page's validatePreventionDate on a stub Date box; returns its answer and what it did to the box. */
function validate(value, type = 'text') {
  const listeners = [];
  const box = {
    value, type, title: 'yyyy | yyyy-mm | yyyy-mm-dd', validity: '', reported: 0,
    setCustomValidity(message) { this.validity = message; },
    reportValidity() { this.reported++; },
    addEventListener(event, handler) { listeners.push({ event, handler }); },
  };
  const context = { document: { getElementById: (id) => (id === 'prevDate' ? box : null) } };
  vm.createContext(context);
  vm.runInContext(`${pageFunction('isCalendarDate')}\n${pageFunction('validatePreventionDate')}\nthis.result = validatePreventionDate();`, context);
  const edit = () => listeners.filter((l) => l.event === 'input').forEach((l) => l.handler());
  return { result: context.result, box, edit };
}

test('shouldRefuseADayTheCalendarDoesNotHave_beforeTheEditIsPosted', () => {
  for (const bad of ['2019-02-29', '2019-02-31', '2019-04-31', '1900-02-29', '2019-06-31']) {
    const { result, box, edit } = validate(bad);
    assert.equal(result, false, `${bad} should be refused`);
    assert.equal(box.validity, box.title, `${bad} gets the box's own format hint`);
    assert.equal(box.reported, 1, `${bad}: the message is shown`);
    edit();
    assert.equal(box.validity, '', `${bad}: the next edit clears the message, so a corrected date can be submitted`);
  }
});

test('shouldAcceptRealDaysAndPartialDates_inThePartialDateBox', () => {
  for (const ok of ['2019', '2019-05', '2019-05-14', '2019-01-31', '2020-02-29', '2000-02-29', '0050-01-01']) {
    const { result, box } = validate(ok);
    assert.equal(result, true, `${ok} should be accepted`);
    assert.equal(box.validity, '', `${ok} leaves no message`);
    assert.equal(box.reported, 0, `${ok} shows nothing`);
  }
});

test('shouldLeaveADateInputToTheBrowser_whenThePreventionHasAFullDate', () => {
  // A full stored date keeps the browser's date input, which cannot hold an impossible day.
  const { result, box } = validate('2019-05-14', 'date');
  assert.equal(result, true);
  assert.equal(box.reported, 0);
});

