/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const check = require('./form-rourke2020-growth-playwright-checks');

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
  for (const name of ['p1_pNutrition1w', 'p3_immunization', 'c_birthRemarks', 'c_famHistory']) assert.ok(!check.MEASURE_OR_DATE.test(name), `${name} is left out of the sweep`);
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
