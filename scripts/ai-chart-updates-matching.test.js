/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const matching = require('../src/main/webapp/js/ai-chart-updates-matching');
const match = (draft, chart) => matching.find(matching.prepare(draft), matching.prepare(chart));
for (const [draft, chart] of [
  ['Hypertension', 'HTN'],
  ['Past medical history: HTN', 'Hypertension'],
  // Side markers are exact-only, so the reordered left-knee pair is checked without the side.
  ['OA of the knee', 'Knee osteoarthritis'],
  ['Left knee osteoarthritis', 'left knee  osteoarthritis.'],
  ['Metoprolol → bisoprolol', 'metoprolol  →  bisoprolol'],
  // Only a leading 'Patient has/is/was' drops the word 'patient'.
  ['Patient has HTN', 'Hypertension'], ['The patient is diabetic', 'Diabetic'],
  ['Type two diabetes mellitus', 'T2DM'],
  ['COPD', 'Chronic obstructive pulmonary disease'],
  ['Neurology review in four weeks', 'Follow up with neurology in 4 weeks'],
  ['Arrange physiotherapy review', 'Schedule physio follow-up'],
  ['GP review in 2 weeks', 'General practitioner follow-up in two weeks'],
  ['Hypertension', 'No asthma.\nHypertension.'],
  ['Hypertension', 'No asthma. Hypertension.'],
  ['Hypertension', 'Asthma. Ruled out. Hypertension.'],
  ['Asthma', 'Family history:\nHTN\nAssessment:\nAsthma'],
  ['History: suspected asthma; diagnosis not yet confirmed.', 'HISTORY: SUSPECTED\n ASTHMA; DIAGNOSIS NOT YET CONFIRMED.'],
  ['Pain after surgery', 'pain  after surgery'],
  ['Hypertension', 'Hb 120 g/L. Hypertension.'],
]) test(`finds local duplicate: ${draft} / ${chart}`, () => assert.ok(match(draft, chart)));
for (const [draft, chart] of [
  ['Hypertension', 'No hypertension'], ['Hypertension', 'No\nHTN'],
  ['Asthma', 'Asthma\nruled out'],
  ['Asthma', 'Asthma. Ruled out.'], ['Asthma', 'Asthma.\nRuled out.'],
  ['Asthma', 'Asthma.\nResolved.'],
  ['GP review', 'GP review. Cancelled.'], ['GP review', 'GP review. Completed.'],
  ['GP review', 'GP review. Canceled.'],
  ['Hypertension', 'No history of: Asthma. Hypertension.'],
  ['Hypertension', 'Possible diagnoses: Asthma. Hypertension.'],
  ['Cardiology review', 'If symptoms persist: GP review. Cardiology review.'],
  ['Hypertension', 'Family history: Asthma. Hypertension'],
  ['Hypertension', 'Family history: Asthma.\nHypertension'],
  ['Hypertension', 'Family hx: Asthma.\nHypertension'], ['Hypertension', '2025:\nHTN'], ['Hypertension', 'No history of HTN'],
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
  ['Review in 2 weeks', 'If fever develops. Review in 2 weeks.'],
  ['Hypertension', 'High blood pressure'], ['Multiple sclerosis', 'MS'],
  ['Hypertension', 'No hypertension; asthma'],
  ['Left knee pain and right hip pain', 'Right knee pain and left hip pain'],
  ['Left knee pain, right hip pain', 'Right knee pain, left hip pain'],
  ['Left knee pain / right hip pain', 'Right knee pain / left hip pain'],
  ['Left knee pain right hip fracture', 'Right knee pain left hip fracture'],
  ['Left knee pain left hip fracture', 'Left knee fracture left hip pain'],
  ['Asthma caused cough', 'Cough caused asthma'],
  ['Past pneumonia', 'Pneumonia'], ['Had pneumonia', 'Has pneumonia'],
  ['Acute kidney disease', 'Chronic kidney disease'],
  ['Review after surgery', 'Review before surgery'],
  ['Pain after surgery', 'Surgery after pain'], ['Fall before syncope', 'Syncope before fall'],
  ['Switched metoprolol to bisoprolol', 'Switched bisoprolol to metoprolol'],
  ['Replaced metoprolol with bisoprolol', 'Replaced bisoprolol with metoprolol'],
  ['Swapped ramipril for candesartan', 'Swapped candesartan for ramipril'],
  ['Changed metformin then gliclazide', 'Changed gliclazide then metformin'],
  ['eGFR <60', 'eGFR >60'], ['Review in -1 weeks', 'Review in 1 weeks'],
  ['Measurement 0.5', 'Measurement 5'],
  // Drug-switch direction written with arrows or as stop/start.
  ['Metoprolol → bisoprolol', 'Bisoprolol → metoprolol'], ['Metoprolol -> bisoprolol', 'Bisoprolol -> metoprolol'],
  ['Metoprolol => bisoprolol', 'Bisoprolol => metoprolol'], ['Metoprolol <- bisoprolol', 'Bisoprolol <- metoprolol'],
  ['Stop metoprolol start bisoprolol', 'Stop bisoprolol start metoprolol'],
  ['Hold ramipril resume metformin', 'Hold metformin resume ramipril'],
  // Up and down, as arrows and as words.
  ['↑ TSH', '↓ TSH'], ['TSH ↑', 'TSH ↓'], ['↑ metformin ↓ gliclazide', '↓ metformin ↑ gliclazide'],
  ['Increase metformin decrease gliclazide', 'Decrease metformin increase gliclazide'],
  ['Increased ALT decreased AST', 'Decreased ALT increased AST'],
  ['Metformin up gliclazide down', 'Metformin down gliclazide up'],
  ['High ALT low AST', 'Low ALT high AST'], ['Off metformin on gliclazide', 'On metformin off gliclazide'],
  ['Knee pain worse hip pain better', 'Knee pain better hip pain worse'],
  ['Worsening knee pain improving hip pain', 'Improving knee pain worsening hip pain'],
  ['Prefer ramipril over candesartan', 'Prefer candesartan over ramipril'],
  ['Ramipril over candesartan', 'Candesartan over ramipril'],
  ['Metformin in place of gliclazide', 'Gliclazide in place of metformin'],
  ['Metformin in lieu of gliclazide', 'Gliclazide in lieu of metformin'],
  ['Metformin instead of gliclazide', 'Gliclazide instead of metformin'],
  ['Ramipril for candesartan', 'Candesartan for ramipril'],
  // Who did what to whom.
  ['Partner hit patient', 'Patient hit partner'], ['Patient abused by partner', 'Partner abused by patient'],
  ['Patient was anaemic', 'Anaemic'],
  // Side abbreviations, including one side marker shared by two findings.
  ['L knee pain R hip fracture', 'R knee pain L hip fracture'],
  ['Lt knee pain rt hip fracture', 'Rt knee pain lt hip fracture'],
  ['L. knee pain R. hip fracture', 'R. knee pain L. hip fracture'],
  ['Left knee pain hip fracture', 'Knee pain left hip fracture'],
  ['Knee OA', 'L. Knee OA'], ['Knee OA', 'Lt. Knee OA'], ['Knee OA', 'L.\nKnee OA'],
  // A scope heading after a side abbreviation or an unpunctuated line still blocks what follows.
  ['Diabetes', 'Knee OA L.\nFamily history: HTN.\nDiabetes.'], ['Diabetes', 'Knee OA R.\nFHx: HTN.\nDiabetes.'],
  ['Diabetes', 'Knee OA L.\nFamily history\nHTN.\nDiabetes.'], ['Diabetes', 'Pain R. FHx HTN. Diabetes.'],
  ['Diabetes', 'Knee OA\nFamily history: HTN.\nDiabetes.'], ['Diabetes', 'Knee OA\nFamily history\nHTN.\nDiabetes.'],
  // Fractions and superscript exponents that the term pattern would drop.
  ['Metoprolol ½ tab', 'Metoprolol ¼ tab'], ['Metoprolol ½ tab', 'Metoprolol tab'],
  ['Metoprolol 1 ½ tab', 'Metoprolol 1 tab'], ['Metoprolol 1/2 tab', 'Metoprolol 2 tabs'],
  ['WBC 5 ×10⁹/L', 'WBC 5 ×10⁶/L'], ['Platelets 150 x10⁹', 'Platelets 150 x10⁶'],
  // French order words.
  ['Douleur après chirurgie', 'Chirurgie après douleur'], ['Douleur avant chirurgie', 'Chirurgie avant douleur'],
  ['Metformine puis gliclazide', 'Gliclazide puis metformine'],
  // Signs and comparisons written as symbols.
  ['HIV +ve', 'HIV -ve'], ['eGFR > 60', '60 > eGFR'],
  // Two facts that each carry their own qualifier need four terms, so they stay exact-only.
  ['Metformin 500 gliclazide 80', 'Gliclazide 500 metformin 80'],
  ['Severe asthma mild COPD', 'Mild asthma severe COPD'],
  ['Acute knee pain chronic hip pain', 'Chronic knee pain acute hip pain'],
  ['Diabetes', 'Knee OA\nNo history of\nHTN.\nDiabetes.'],
  ['Diabetes', 'Knee OA\nPossible diagnoses\nHTN.\nDiabetes.'],
  ['Diabetes', 'Knee OA\rFamily history\rHTN.\rDiabetes.'],
  ['Knee OA', 'Bilat. Knee OA'], ['Knee OA', 'Bilat.\nKnee OA'],
  ['Knee OA', 'B/L. Knee OA'], ['Knee OA', 'b/l.\nKnee OA'], ['Knee OA', 'Pain,L. Knee OA'],
  ['HTN', 'Fam hx HTN.'], ['HTN', 'Family medical history\nHTN.'],
  ['Metformin now gliclazide', 'Gliclazide now metformin'],
  ['Metformin superseded gliclazide', 'Gliclazide superseded metformin'],
  ['Metformin pour gliclazide', 'Gliclazide pour metformin'],
  ['Hit nurse', 'Nurse hit'], ['Assaulted grandmother', 'Grandmother assaulted'],
]) test(`keeps distinct: ${draft} / ${chart}`, () => {
  assert.equal(match(draft, chart), null);
  assert.equal(match(chart, draft), null);
});
test('returns the original matching passage, not a rewritten diagnosis', () => {
  const chart = 'No asthma.\nKnee osteoarthritis.\nLeft hip osteoarthritis.\nReview in 4 weeks.';
  assert.equal(match('OA of the knee', chart).passage, 'Knee osteoarthritis.');
  assert.equal(match('Left hip osteoarthritis', chart).passage, 'Left hip osteoarthritis.');
});
test('empty input never matches', () => {
  assert.equal(match('', ''), null);
  assert.equal(match('   ', '\n'), null);
});
