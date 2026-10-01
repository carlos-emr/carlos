/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const matching = require('../src/main/webapp/js/ai-chart-updates-matching');
const match = (draft, chart) => matching.find(matching.prepare(draft), matching.prepare(chart));
for (const [draft, chart] of [
  ['Hypertension', 'HTN'],
  ['Past medical history: HTN', 'Hypertension'],
  ['OA of the left knee', 'Left knee osteoarthritis'],
  ['Type two diabetes mellitus', 'T2DM'],
  ['COPD', 'Chronic obstructive pulmonary disease'],
  ['Neurology review in four weeks', 'Follow up with neurology in 4 weeks'],
  ['Arrange physiotherapy review', 'Schedule physio follow-up'],
  ['GP review in 2 weeks', 'General practitioner follow-up in two weeks'],
  ['Hypertension', 'No asthma.\nHypertension.'],
  ['Asthma', 'Family history:\nHTN\nAssessment:\nAsthma'],
  ['History: suspected asthma; diagnosis not yet confirmed.', 'HISTORY: SUSPECTED\n ASTHMA; DIAGNOSIS NOT YET CONFIRMED.'],
]) test(`finds local duplicate: ${draft} / ${chart}`, () => assert.ok(match(draft, chart)));
for (const [draft, chart] of [
  ['Hypertension', 'No hypertension'], ['Hypertension', 'No\nHTN'],
  ['Asthma', 'Asthma\nruled out'], ['Hypertension', '2025:\nHTN'], ['Hypertension', 'No history of HTN'],
  ['Hypertension', 'Family history:\nHTN'], ['Hypertension', 'Family history\nHTN'],
  ['Hypertension', 'Father has HTN'], ['Hypertension', 'Possible HTN'],
  ['Hypertension', 'HTN?'], ['Asthma', 'Asthma resolved'],
  ['Left knee OA', 'Right knee osteoarthritis'], ['Knee OA', 'Left knee osteoarthritis'],
  ['Left knee OA', 'Bilateral knee osteoarthritis'],
  ['Type 1 diabetes', 'Type 2 diabetes'], ['Diabetes', 'T2DM'],
  ['Neurology review in 2 weeks', 'Neurology review in 4 weeks'],
  ['Neurology review in 2 weeks', 'Neurology review in 2 months'],
  ['Review on 2026-10-01', 'Review on 2026-10-02'],
  ['Review on 2026-01-10', 'Review on 2026-10-01'],
  ['Review in 2 weeks', 'Review in 2 weeks after surgery'],
  ['Review in 2 weeks', 'Review in 2 weeks if symptoms persist'],
  ['Review in 2 weeks', 'If symptoms persist:\nReview in 2 weeks'],
  ['Hypertension', 'High blood pressure'], ['Multiple sclerosis', 'MS'],
  ['Hypertension', 'No hypertension; asthma'],
  ['Left knee pain and right hip pain', 'Right knee pain and left hip pain'],
  ['Asthma caused cough', 'Cough caused asthma'],
  ['Past pneumonia', 'Pneumonia'], ['Had pneumonia', 'Has pneumonia'],
  ['Acute kidney disease', 'Chronic kidney disease'],
  ['Review after surgery', 'Review before surgery'],
  ['eGFR <60', 'eGFR >60'], ['Review in -1 weeks', 'Review in 1 weeks'],
  ['Measurement 0.5', 'Measurement 5'],
]) test(`keeps distinct: ${draft} / ${chart}`, () => assert.equal(match(draft, chart), null));
test('returns the original matching passage, not a rewritten diagnosis', () => {
  const chart = 'No asthma.\nLeft knee osteoarthritis.\nReview in 4 weeks.';
  assert.equal(match('OA of the left knee', chart).passage, 'Left knee osteoarthritis.');
});
test('empty input never matches', () => {
  assert.equal(match('', ''), null);
  assert.equal(match('   ', '\n'), null);
});
