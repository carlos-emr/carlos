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

const JSP = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'prevention', 'AddPreventionData.jsp'),
  'utf8',
);

/** Every <input ... name="prevDate" ...> tag, up to the "></div>" that closes it (its attributes hold JSP "%>"). */
const inputs = [...JSP.matchAll(/<input type="(?:(?!<input )[\s\S])*?name="prevDate"[\s\S]*?><\/div>/g)].map((m) => m[0]);

/** The pattern attribute the partial-date box carries, as the browser reads it (anchored, v flag). */
function patternOf(tag) {
  const m = /pattern="([^"]+)"/.exec(tag);
  assert.ok(m, `no pattern on ${tag.slice(0, 80)}`);
  return new RegExp(`^(?:${m[1]})$`, 'v');
}

test('shouldSwitchEveryPreventionDateBoxToText_forAPartialDate', () => {
  assert.equal(inputs.length, 4, 'the page has four prevention layouts, each with a Date box');
  for (const tag of inputs) {
    assert.match(tag, /^<input type="<%= partialPrevDate \? "text" : "date" %>"/);
    assert.match(tag, /required<% if \(partialPrevDate\) \{ %> pattern="[^"]+" title="yyyy \| yyyy-mm \| yyyy-mm-dd"<% \} %>><\/div>$/);
  }
  assert.equal(new Set(inputs.map((tag) => /pattern="([^"]+)"/.exec(tag)[1])).size, 1, 'the four boxes share one pattern');
});

test('shouldAcceptTheDatesThePreventionSaveTakes_inThePartialDateBox', () => {
  const pattern = patternOf(inputs[0]);
  for (const ok of ['2019', '2019-05', '2019-12', '2019-05-14', '2019-01-31', '2020-02-29']) {
    assert.ok(pattern.test(ok), `${ok} should be accepted`);
  }
  for (const bad of ['19', '2019-5', '2019-13', '2019-00', '2019-05-1', '2019-05-32', '2019-05-00', '2019/05', '2019-05-14 10:00', ' 2019']) {
    assert.ok(!pattern.test(bad), `${bad} should be refused`);
  }
});

test('shouldTreatOnlyAYearOrAYearAndMonth_asAPartialDate', () => {
  const m = /partialPrevDate = prevDate != null && prevDate\.matches\("([^"]+)"\);/.exec(JSP);
  assert.ok(m, 'the page decides partialPrevDate from the stored date');
  // Java String.matches is anchored; the literal's "\\" is one backslash in the regex.
  const partial = new RegExp(`^(?:${m[1].replace(/\\\\/g, '\\')})$`);
  for (const yes of ['2019', '2019-05']) assert.ok(partial.test(yes), `${yes} is partial`);
  for (const no of ['2019-05-14', '2019-05-14 10:00', '', '201']) assert.ok(!partial.test(no), `${no} is not partial`);
});
