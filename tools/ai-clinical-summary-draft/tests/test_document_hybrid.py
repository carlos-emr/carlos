# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import document_hybrid as hybrid
import openrouter_agent as agent


class HybridTest(unittest.TestCase):
    def setUp(self):
        self.source = ('Presenting Complaint\nConfusion for 3 days.\n\n'
            'Systems Review\n- Reduced urinary output.\n- No fever with rigors.\n\n'
            'Medications\n- Drug A 10 mg daily\n\n'
            'Plan\n- Start drug B 1 mg daily if needed.\n- Recheck tomorrow.')
        self.config = dict(agent.DEFAULTS, api_key='test-key-never-a-real-secret')

    def test_ai_only_chooses_emphasis_all_other_facts_are_retained(self):
        calls = []
        def complete(payload):
            calls.append(payload)
            return {'selected_ids': ['1']}
        result = hybrid.run(self.config, self.source, complete)
        self.assertEqual(1, len(calls))
        self.assertEqual(256, calls[0]['max_tokens'])
        self.assertEqual({'opening_facts': {'1': 'Presentation: Confusion for 3 days.'}},
                         json.loads(calls[0]['messages'][1]['content']))
        text = '\n'.join(p['text'] for p in result['points'])
        for phrase in ('Reduced urinary output.', 'No fever with rigors.', 'Drug A 10 mg daily',
                       'Start drug B 1 mg daily if needed.', 'Recheck tomorrow.'):
            self.assertIn(phrase, text)
        self.assertEqual(1, text.count('Confusion for 3 days.'))
        self.assertTrue(all(e in self.source for p in result['points'] for e in p['evidence']))

    def test_empty_selection_does_not_drop_content(self):
        result = hybrid.run(self.config, self.source, lambda _: {'selected_ids': []})
        self.assertIn('Reduced urinary output.', str(result))
        self.assertIn('Confusion for 3 days.', str(result))

    def test_no_opening_candidate_needs_no_api_call(self):
        result = hybrid.run(self.config, 'Plan\nReview tomorrow.', lambda _: self.fail('Unexpected model call'))
        self.assertEqual('Plan: Review tomorrow.', result['overview'])

    def test_unknown_duplicate_and_nonopening_ids_are_rejected(self):
        context = hybrid.prepare(self.source)
        for refs in (['999'], ['1', '1'], [True], ['2']):
            with self.subTest(refs=refs), self.assertRaises(ValueError):
                hybrid.resolve({'selected_ids': refs}, context, self.source)

    def test_numeric_ranges_qualifiers_and_nested_lists_survive(self):
        source = ('History of Presenting Complaint\nSite: Left hip. Onset: 5 years. Severity: 8/10 on a bad day, 5/10 on a good day.\n\n'
                  'On Examination\n- No obvious deformity.\n  - No focal weakness.\n- GCS 13/15.\n\n'
                  'Plan\nReview if needed, then repeat tests.')
        result = hybrid.run(self.config, source, lambda _: self.fail('Unexpected call'))
        text = '\n'.join(p['text'] for p in result['points'])
        for phrase in ('8/10 on a bad day, 5/10 on a good day.', 'No obvious deformity.', 'GCS 13/15.',
                       'Review if needed, then repeat tests.'):
            self.assertIn(phrase, text)

    def test_identical_normal_results_can_share_predicate_without_losing_fields(self):
        source = 'Test Results\n- FBC: Normal\n- LFTs: Normal\n- U&E: Mild hyponatremia (Na 132 mmol/L)'
        result = hybrid.run(self.config, source, lambda _: self.fail('Unexpected call'))
        self.assertIn('FBC, LFTs: Normal', result['overview'])
        self.assertIn('Mild hyponatremia (Na 132 mmol/L)', result['overview'])
        self.assertEqual(3, len(result['points'][0]['evidence']))

    def test_unknown_sections_remain_intact_and_clinical_patient_block_is_not_identity(self):
        source = 'Unexpected heading\nNo pain unless walking.\n\nPatient\nAwaiting culture results, then review.'
        result = hybrid.run(self.config, source, lambda _: self.fail('Unexpected call'))
        text = '\n'.join(p['text'] for p in result['points'])
        self.assertIn(source.split('\n\n')[0], text)
        self.assertIn('Awaiting culture results, then review.', text)

    def test_ambiguous_patient_block_is_not_assumed_to_be_a_name(self):
        source = 'Patient\nSevere Pain\n\nPlan\nReview tomorrow.'
        output = hybrid.run(self.config, source, lambda _: self.fail('Unexpected call'))
        self.assertIn('Severe Pain', str(output))

    def test_recognised_identity_and_signature_are_removed_but_age_preserved(self):
        source = ('Patient: Test Person, 33-year-old female, DOB 21/07/90, NHS No. 123456789.\n\n'
                  'Plan\nReview tomorrow.\n\nDr. Test Person (Registrar)\nGMC number: 1234567')
        result = hybrid.run(self.config, source, lambda _: self.fail('Unexpected call'))
        text = '\n'.join(p['text'] for p in result['points'])
        self.assertIn('33-year-old female', text)
        self.assertNotIn('Test Person', text)
        self.assertNotIn('1234567', text)

    def test_short_values_retain_heading_support(self):
        result = hybrid.run(self.config, 'Medications\nNil\n\nAllergies\nNil', lambda _: self.fail('Unexpected call'))
        self.assertEqual(2, len(result['points']))
        self.assertIn('Medications\n', result['points'][0]['evidence'])

    def test_length_split_presenting_paragraph_cannot_be_promoted_incompletely(self):
        source = 'Presenting Complaint\n' + 'Important history with qualified negatives. ' * 50
        context = hybrid.prepare(source)
        self.assertGreater(len(context['facts']), 1)
        self.assertEqual({}, hybrid.opening_candidates(context))
        result = hybrid.run(self.config, source, lambda _: self.fail('Unexpected call'))
        self.assertEqual(50, str([p['text'] for p in result['points']]).count('Important history'))


if __name__ == '__main__':
    unittest.main()
