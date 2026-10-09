/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const claims = require('./lib/form-claims');
const check = require('./form-rourke2020-growth-playwright-checks');

const ROOT = path.join(__dirname, '..');
const read = relative => fs.readFileSync(path.join(ROOT, relative), 'utf8');

/*
 * The tables and helpers of form-rourke2020-growth. The browser flow is proved live; what can drift without a browser is
 * pinned here: the time zone the measurement-date step relies on, the typed data and the floors the graph steps count against.
 */

const MARKER = 'FAKE-PW0123456789abcdef';
const TODAY = { y: 2026, m: 10, d: 9 };
const DOB = check.day(TODAY, -check.INFANT_DAYS);

test('shouldPickAZoneWhoseDateDiffersFromUtc_atEveryHourOfTheDay', () => {
  for (let hour = 0; hour < 24; hour++) {
    for (const minute of [0, 30, 59]) {
      const now = new Date(Date.UTC(2026, 9, 9, hour, minute, 0));
      const zone = check.zoneWhereTodayDiffersFromUtc(now);
      assert.notEqual(check.localDate(zone, now), now.toISOString().slice(0, 10), `at ${hour}:${minute} UTC the zone ${zone} has the UTC date`);
    }
  }
});

test('shouldMoveACalendarDay_acrossMonthYearAndLeapDay', () => {
  assert.deepEqual(check.day({ y: 2026, m: 1, d: 1 }, -1), { y: 2025, m: 12, d: 31 });
  assert.deepEqual(check.day({ y: 2024, m: 2, d: 28 }, 1), { y: 2024, m: 2, d: 29 });
  assert.deepEqual(check.day({ y: 2026, m: 10, d: 9 }, 0), { y: 2026, m: 10, d: 9 });
  assert.equal(check.dmy({ y: 2025, m: 3, d: 4 }), '04/03/2025');
  assert.equal(check.iso({ y: 2025, m: 3, d: 4 }), '2025-03-04');
});

test('shouldCountTheWordNullOnItsOwn_butNotInsideAnotherWord', () => {
  assert.equal(check.nullWords('DATE null / 20\nnull\nannulled nullable 52.1'), 2);
  assert.equal(check.nullWords('FAKE-PW1 concern 1w\n04/04/2025'), 0);
  assert.equal(check.nullWords('a/null/b'), 1);
});

test('shouldTypeEveryRourkeFieldOnce_withAMarkerWhereTheVisitPrints', () => {
  const fields = check.rourkeFields(MARKER, DOB);
  const names = fields.map(field => field.name);
  assert.equal(new Set(names).size, names.length, 'a field is typed twice');
  for (const field of fields.filter(item => item.kind === 'text' && item.value.includes(MARKER))) assert.ok(field.value.length <= 40, `${field.name} is longer than the text boxes accept`);
  assert.ok(fields.every(field => field.kind === 'radio' || typeof field.value === 'string' || field.kind === 'measure'), 'a value is missing');
});

test('shouldGiveEveryMeasurementADateAfterBirth_andExactlyOneTheDefault', () => {
  const measures = check.rourkeFields(MARKER, DOB).filter(field => field.kind === 'measure');
  assert.equal(measures.filter(field => !field.observed).length, 1, 'exactly one import leaves the dialog date alone (the measuredate concern)');
  const dobKey = check.iso(DOB);
  for (const field of measures.filter(item => item.observed)) {
    assert.ok(check.iso(field.observed) >= dobKey, `${field.name} is observed before the birth`);
    assert.ok(['WT', 'HT'].includes(field.type), `${field.name} has a type the dialog does not know`);
  }
});

test('shouldTypeADateForEveryVisitThatHasAMeasurement_soSaveAcceptsTheForm', () => {
  const fields = check.rourkeFields(MARKER, DOB);
  const measured = fields.filter(field => /^p\d_(ht|wt|hc)\d+[wm]$/.test(field.name)).map(field => field.name.replace(/^(p\d)_(ht|wt|hc)/, '$1_date'));
  const dated = new Set(fields.filter(field => field.kind === 'date').map(field => field.name));
  for (const date of measured) assert.ok(dated.has(date), `${date} is not typed but its visit has a measurement (the form's own checkMeasures refuses it)`);
  for (const field of fields.filter(item => item.kind === 'date')) assert.match(field.value, /^\d{2}\/\d{2}\/\d{4}$/);
});

test('shouldDeriveTheGraphFloors_fromWhatIsTyped', () => {
  const fields = check.rourkeFields(MARKER, DOB);
  const by = pattern => fields.filter(field => pattern.test(field.name)).map(field => field.name);
  const weights = by(/^(c_birthWeight|p\d_wt\d+[wm])$/);
  const lengths = by(/^(c_length|p\d_ht\d+[wm])$/);
  const heads = by(/^(c_headCirc|p\d_hc\d+[wm])$/);
  const key = name => (name === 'c_birthWeight' || name === 'c_length' ? 'birth' : name.replace(/^(p\d)_(ht|wt)/, '$1_'));
  const pairs = lengths.filter(name => weights.map(key).includes(key(name))).length;
  assert.equal(check.ROURKE_LENGTH_WEIGHT_POINTS, weights.length + lengths.length);
  assert.equal(check.ROURKE_HEAD_POINTS, heads.length + pairs);
});

test('shouldKeepTheSweepAwayFromTheCellsSaveValidates', () => {
  for (const name of ['p1_ht1w', 'p4_bmi48m', 'p3_wt9m', 'p2_hc4m', 'c_length', 'c_headCirc', 'c_birthWeight', 'p1_date1w', 'c_startOfGestation', 'c_pName']) {
    assert.ok(check.MEASURE_OR_DATE.test(name), `${name} would be typed by the sweep`);
  }
  for (const name of ['p2_development2m', 'p3_immunization', 'c_birthRemarks', 'c_famHistory']) assert.ok(!check.MEASURE_OR_DATE.test(name), `${name} is left out of the sweep`);
});

test('shouldTypeTheGrowthFormsRowsInRange_withTheBirthDateOnlyOnTheChartsDobLine', () => {
  const growth = check.growthFields(MARKER, DOB);
  const rows = growth.filter(field => /^date_\d+$/.test(field.name));
  assert.equal(rows.length, check.GROWTH_ROWS);
  const birth = `${DOB.y}/${String(DOB.m).padStart(2, '0')}/${String(DOB.d).padStart(2, '0')}`;
  assert.ok(rows.every(row => row.value !== birth), 'a row dated on the birth date would put the date of birth on the chart by itself');
  const chart = check.chartFields(MARKER, TODAY);
  assert.equal(chart.filter(field => /^age_\d+$/.test(field.name) && Number(field.value) >= 2).length * 2, check.CHART_POINTS);
  for (const field of [...growth, ...chart]) assert.ok(field.value.length <= (field.name.startsWith('comment_') ? 25 : 80), `${field.name} is longer than its box`);
});

test('shouldLabelEveryPinnedPair_asTheRunPrintsIt', () => {
  for (const [claim, label] of Object.entries(check.PINNED)) {
    const [key, concern] = claim.split('.');
    assert.equal(label, check.generatedLabel(key, concern), `${claim} is pinned under another wording than the run prints`);
  }
});

/* ---- the print boxes and the notes, proved against the templates the check reads ---- */

/** The textField of a Jasper template whose expression is $P{name}: its x, y, width and height. */
function boxOf(jrxml, name) {
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- name is a print-parameter name this test passes from its own fixed list
  const element = new RegExp(`<element kind="textField"[^>]*?\\sx="(\\d+)"\\s+(?:positionType="\\w+"\\s+)?y="(\\d+)"[^>]*?width="(\\d+)"\\s+height="(\\d+)"[^>]*>\\s*<expression><!\\[CDATA\\[\\$P\\{${name}\\}\\]\\]>`).exec(jrxml);
  assert.ok(element, `page1.jrxml places no text field for ${name}`);
  return { x: Number(element[1]), y: Number(element[2]), width: Number(element[3]), height: Number(element[4]) };
}

test('shouldMeasureThePrintBoxesOnThePageTheTemplateDeclares', () => {
  const page1 = read('src/main/resources/oscar/form/rourke2020/page1.jrxml');
  const page = /<jasperReport[^>]*pageWidth="(\d+)"\s+pageHeight="(\d+)"/.exec(page1);
  assert.deepEqual({ width: Number(page[1]), height: Number(page[2]) }, check.PRINT_PAGE);
  assert.deepEqual(check.PRINT_BOXES.male, boxOf(page1, 'c_male'));
  assert.deepEqual(check.PRINT_BOXES.female, boxOf(page1, 'c_female'));
  assert.deepEqual(check.PRINT_BOXES.gestationalAge, boxOf(page1, 'c_gestationalAge'));
});

test('shouldCallTheNotesTheTemplateDeclaresAndNeverPlaces_onPageIIOfTheForm', () => {
  const page2 = read('src/main/resources/oscar/form/rourke2020/page2.jrxml');
  const jsp = read('src/main/webapp/WEB-INF/jsp/form/formRourke2020p2.jsp');
  const fields = check.rourkeFields(MARKER, DOB);
  assert.equal(check.ROURKE_NOTES.length, 7);
  for (const name of check.ROURKE_NOTES) {
    assert.match(page2, new RegExp(`<parameter name="${name}"`), `${name} is not declared in page2.jrxml`);
    assert.ok(!page2.includes(`$P{${name}}`), `${name} is placed in page2.jrxml, so it is no longer a note the print drops`);
    assert.match(jsp, new RegExp(`<textarea[^>]*name="${name}"`), `formRourke2020p2.jsp has no note box ${name}`);
    const field = fields.find(item => item.name === name);
    assert.ok(field, `${name} is not typed`);
    assert.equal(field.page, 1, `${name} is on page II of the form`);
  }
});

test('shouldHaveAStepLabelForEveryConcern_andPinTheTitlesOnTheirOwnPairs', () => {
  for (const form of check.claimForms) {
    for (const concern of form.concerns) assert.match(check.generatedLabel(form.key, concern), /^[A-Z][^:]*: \S/, `${form.key}.${concern} has no label`);
  }
  assert.equal(check.stepLabel('rourke2020', 'graphtitle'), check.PINNED['rourke2020.graphtitle']);
  assert.equal(check.stepLabel('growth036', 'title'), check.PINNED['growth036.title']);
  assert.ok(check.claimForms.find(form => form.key === 'rourke2020').concerns.includes('graphtitle'));
  assert.ok(check.claimForms.find(form => form.key === 'growth036').concerns.includes('title'));
});

/* ---- what each pinned judgment reads, on synthetic page I words ---- */

const word = (text, x, y, width = 9, height = 17) => ({ text, xMin: x, yMin: y, xMax: x + width, yMax: y + height });
const BOX = check.PRINT_BOXES;

test('shouldMarkTheOwnSexOnly_whereTheTemplatePlacesTheBoxes', () => {
  // As the application prints a girl today: x in both boxes (finding 246). Other x words on the page do not count.
  const girlToday = [word('x', BOX.male.x, 160.8), word('x', BOX.female.x, 160.8), word('x', 400, 900)];
  assert.deepEqual(check.sexMarks(girlToday), { male: 1, female: 1 });
  assert.deepEqual(check.sexMarks([word('X', BOX.female.x, 160.8), word('x', 400, 900)]), { male: 0, female: 1 });
  assert.deepEqual(check.sexMarks([word('x', BOX.male.x + 3, 161)]), { male: 1, female: 0 });
  assert.deepEqual(check.sexMarks([word('xx', BOX.female.x, 161)]), { male: 0, female: 0 }, 'a word that is not a lone mark is not a mark');
  assert.deepEqual(check.sexMarks([]), { male: 0, female: 0 });
});

test('shouldReadTheGestationalAgeFromItsOwnBox_notFromTheFirstWeeksOnThePage', () => {
  const inBox = [word('39', BOX.gestationalAge.x, 192, 18, 15), word('weeks', BOX.gestationalAge.x + 24, 192, 45, 15)];
  const elsewhere = [word('12', 700, 600), word('weeks', 740, 600)];
  assert.equal(check.printedGestation([...elsewhere, ...inBox]), '39 weeks');
  assert.equal(check.printedGestation([...inBox].reverse()), '39 weeks', 'left to right whatever the order of the words');
  assert.equal(check.printedGestation(elsewhere), '', 'a blank cell is blank although another cell says weeks');
});

test('shouldJudgeTheMeasurementDate_andNeverBlameTheDialogForARunThatCannotTell', () => {
  const filed = { expected: '2026-10-10', utc: '2026-10-09', offered: '2026-10-09', stored: '2026-10-09', clockMoved: false };
  assert.throws(() => check.judgeMeasureDate(filed, 'Pacific/Kiritimati'), error => !(error instanceof claims.Precondition) && /it uses the UTC date/.test(error.message));
  assert.doesNotThrow(() => check.judgeMeasureDate({ ...filed, offered: '2026-10-10', stored: '2026-10-10' }, 'Pacific/Kiritimati'));
  // The two dates are the same day: nothing can be said about the dialog, in either direction.
  for (const same of [{ ...filed, expected: filed.utc }, { ...filed, expected: filed.utc, stored: filed.utc }]) {
    assert.throws(() => check.judgeMeasureDate(same, 'Pacific/Kiritimati'), error => error instanceof claims.Precondition);
  }
  assert.throws(() => check.judgeMeasureDate({ ...filed, clockMoved: true }, 'Pacific/Kiritimati'), error => error instanceof claims.Precondition);
  // A precondition reads as a failure elsewhere, never under the pinned label.
  const outcome = claims.outcomeOf((() => { try { check.judgeMeasureDate({ ...filed, expected: filed.utc }, 'Pacific/Kiritimati'); } catch (error) { return error; } return null; })(), []);
  assert.equal(claims.claimFailure(outcome, 'pinned').label, 'pinned (precondition)');
});

test('shouldFormatTheUtcDateOfAnInstant', () => {
  assert.equal(check.utcDate(new Date(Date.UTC(2026, 9, 9, 23, 59, 59))), '2026-10-09');
  assert.equal(check.utcDate(new Date(Date.UTC(2026, 9, 10, 0, 0, 0))), '2026-10-10');
});

test('shouldTreatARefusalBeforeTheEndpointAsAPrecondition_andAnErrorPageAsTheDefect', () => {
  // Finding 250 is Print BMI answering HTTP 500. A redirect, 401 or 403 is the session or the CSRF token: not the document, not 250.
  for (const status of [301, 302, 303, 307, 308, 399, 401, 403]) assert.equal(check.refusedBeforeTheEndpoint(status), true, `HTTP ${status}`);
  for (const status of [200, 204, 400, 404, 405, 500, 502, 503]) assert.equal(check.refusedBeforeTheEndpoint(status), false, `HTTP ${status}`);
});

test('shouldDeclareHowEveryPinnedPairTreatsBrowserProblems_andPinEachOnItsAssertion', () => {
  // Every pin of this check is on an assertion: a browser problem that ends the concern must never read as the pinned defect.
  for (const claim of Object.keys(check.PINNED)) {
    const [key, concern] = claim.split('.');
    assert.equal(check.knownProblem(key, concern), claims.ASSERTION_ONLY, `${claim} declares nothing about browser problems`);
  }
  const problems = claims.outcomeOf(null, ['console error: Failed to load resource: the server responded with a status of 404']);
  const pinned = check.PINNED['growthchart.printbmi'];
  assert.equal(claims.claimFailure(problems, pinned, check.knownProblem('growthchart', 'printbmi')).label, `${pinned} (other browser problems)`);
  assert.equal(claims.claimFailure(claims.outcomeOf(new Error('Print BMI answered 500 text/html, not a PDF'), []), pinned, check.knownProblem('growthchart', 'printbmi')).label, pinned,
    'the assertion itself still holds the pin');
});
