# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import document_facts as facts
import document_distill as distill
import openrouter_agent as agent


class DocumentFactsTest(unittest.TestCase):
    def setUp(self):
        self.config = dict(agent.DEFAULTS, api_key='test-key-never-a-real-secret')
        self.source = ('Presenting Complaint\nPain for 3 days.\n\n'
                       'Medications\nDrug A 10 mg daily.\n\n'
                       'Plan\n- Start drug B 1 mg daily if needed.\n- Review tomorrow.')
        self.draft = {'points': [{'category': 'Summary', 'text': 'Pain for 3 days.', 'evidence_ids': ['1']}]}
        self.calls = []

    def complete_with(self, replies):
        replies = iter(replies)
        def complete(payload):
            self.calls.append(copy.deepcopy(payload))
            return copy.deepcopy(next(replies))
        return complete

    def test_fixed_facts_preserve_dose_status_conditions_and_exact_evidence(self):
        output = facts.run(self.config, self.source, self.complete_with([self.draft, {'issues': []}]))
        self.assertEqual('Summary: Pain for 3 days.', output['overview'])
        self.assertEqual('Medications: Drug A 10 mg daily.', output['points'][1]['text'])
        self.assertEqual('Plan: Start drug B 1 mg daily if needed.\nReview tomorrow.', output['points'][2]['text'])
        self.assertEqual(['Plan\n- Start drug B 1 mg daily if needed.\n- Review tomorrow.'], output['points'][2]['evidence'])
        writer = json.loads(self.calls[0]['messages'][1]['content'])
        self.assertNotIn('Drug A', json.dumps(writer))
        self.assertEqual({'1'}, set(writer['passages']))
        reviewer = json.loads(self.calls[1]['messages'][1]['content'])
        self.assertEqual(3, len(reviewer['passages']))
        self.assertEqual(3, len(reviewer['draft']['points']))

    def test_qualified_negation_is_fixed_and_systems_review_can_be_compacted(self):
        source = 'History\nNo fever with rigors.\n\nSystems Review\nNo dysuria. Reduced urinary output.'
        context = distill.source_context(source)
        fixed = facts.fixed_rows(context)
        self.assertEqual([{'text': 'Context: History\nNo fever with rigors.', 'evidence_ids': ['1']}], fixed)

    def test_summary_must_be_first_and_only_summary_and_use_supplied_context(self):
        for draft in ({'points': []}, {'points': [dict(self.draft['points'][0], category='Social')]},
                      {'points': [dict(self.draft['points'][0], evidence_ids=['2'])]},
                      {'points': self.draft['points'] * 2}):
            with self.subTest(draft=draft), self.assertRaises(ValueError):
                facts.run(self.config, self.source, self.complete_with([draft]))

    def test_unsupported_summary_numbers_cannot_be_approved_by_review(self):
        draft = copy.deepcopy(self.draft)
        draft['points'][0]['text'] = 'Pain for 99 days.'
        with self.assertRaisesRegex(ValueError, 'review did not pass'):
            facts.run(self.config, self.source, self.complete_with([draft, {'issues': []}, draft, {'issues': []}]))
        self.assertEqual(4, len(self.calls))

    def test_missing_context_is_repaired_and_reviewed_again(self):
        wrong = copy.deepcopy(self.draft)
        wrong['points'][0]['text'] = 'Pain.'
        output = facts.run(self.config, self.source, self.complete_with([
            wrong, {'issues': ['Source 1: restore three-day duration.']}, self.draft, {'issues': []}]))
        self.assertEqual('Summary: Pain for 3 days.', output['overview'])
        self.assertEqual(4, len(self.calls))

    def test_all_fixed_source_needs_no_call(self):
        source = 'Allergies\nPenicillin\n\nPlan\nReview tomorrow.'
        output = facts.run(self.config, source, lambda _: self.fail('No call needed'))
        self.assertEqual(2, len(output['points']))
        self.assertEqual('Allergies: Penicillin', output['points'][0]['text'])

    def test_full_paragraph_continuations_are_retained(self):
        source = 'Plan\n' + 'Review tomorrow if symptoms persist. ' * 30
        context = distill.source_context(source)
        rows = facts.fixed_rows(context)
        self.assertGreater(len(rows), 1)
        self.assertEqual(set(context['passages']), {r for p in rows for r in p['evidence_ids']})
        self.assertTrue(all(p['text'].startswith('Plan: ') for p in rows))


if __name__ == '__main__':
    unittest.main()
