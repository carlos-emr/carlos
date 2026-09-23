# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import document_distill as distill
import document_fidelity as fidelity
import openrouter_agent as agent


class DocumentDistillTest(unittest.TestCase):
    def setUp(self):
        self.source = 'Impression\nPossible infection.\n\nPlan\nStart drug A 1 mg daily if needed.'
        self.config = dict(agent.DEFAULTS, api_key='test-key-never-a-real-secret', provider='parasail')
        self.draft = {'points': [
            {'text': 'Possible infection.', 'evidence_ids': ['1']},
            {'text': 'Plan: start drug A 1 mg daily if needed.', 'evidence_ids': ['2']}]}
        self.calls = []

    def complete_with(self, replies):
        replies = iter(replies)
        def complete(payload):
            self.calls.append(copy.deepcopy(payload))
            return copy.deepcopy(next(replies))
        return complete

    def test_approved_draft_uses_exact_evidence_and_reuses_first_point_as_overview(self):
        result = distill.run(self.config, self.source,
                             self.complete_with([self.draft, {'issues': []}]))
        self.assertEqual(2, len(self.calls))
        self.assertEqual(result['points'][0]['text'], result['overview'])
        self.assertEqual(['Plan\nStart drug A 1 mg daily if needed.'], result['points'][1]['evidence'])
        review = json.loads(self.calls[1]['messages'][1]['content'])
        self.assertEqual(fidelity.prepare(self.source, compact=False)['passages'], review['passages'])
        self.assertEqual(self.draft, review['draft'])
        for payload in self.calls:
            self.assertEqual(['parasail'], payload['provider']['only'])
            self.assertFalse(payload['provider']['allow_fallbacks'])
            self.assertTrue(payload['provider']['zdr'])
            self.assertEqual('deny', payload['provider']['data_collection'])
            self.assertEqual({'enabled': False}, payload['reasoning'])

    def test_status_error_requires_repair_and_another_review(self):
        wrong = copy.deepcopy(self.draft)
        wrong['points'][1]['text'] = 'Started drug A 1 mg daily.'
        trace = []
        result = distill.run(self.config, self.source, self.complete_with([
            wrong, {'issues': ['Source 2: treatment is planned and conditional; restore both.']},
            self.draft, {'issues': []}]), trace)
        self.assertEqual(4, len(self.calls))
        self.assertIn('if needed', result['points'][1]['text'])
        self.assertIn('repair_issues', json.loads(self.calls[2]['messages'][1]['content']))
        self.assertEqual(2, len(trace))
        self.assertFalse(trace[-1]['issues'])

    def test_unresolved_review_releases_no_output(self):
        with self.assertRaisesRegex(ValueError, 'review did not pass'):
            distill.run(self.config, self.source, self.complete_with([
                self.draft, {'issues': ['Missing relevant source fact.']},
                self.draft, {'issues': ['Still missing relevant source fact.']}]))
        self.assertEqual(4, len(self.calls))

    def test_auditor_cannot_override_missing_citations_or_unsupported_numbers(self):
        wrong = copy.deepcopy(self.draft)
        wrong['points'][1] = {'text': 'Possible infection for 99 days.', 'evidence_ids': ['1']}
        with self.assertRaisesRegex(ValueError, 'review did not pass'):
            distill.run(self.config, self.source, self.complete_with([
                wrong, {'issues': []}, wrong, {'issues': []}]))
        review = json.loads(self.calls[1]['messages'][1]['content'])
        self.assertEqual(2, len(review['host_issues']))

    def test_malformed_or_forged_draft_fails_before_review(self):
        for draft in ({'points': []}, {'points': [{'text': 'Invented', 'evidence_ids': ['999']}]},
                      dict(self.draft, overview='Invented overview')):
            self.calls = []
            with self.subTest(draft=draft), self.assertRaises(ValueError):
                distill.run(self.config, self.source, self.complete_with([draft]))
            self.assertEqual(1, len(self.calls))

    def test_malformed_review_cannot_be_treated_as_approval(self):
        for audit in ({}, {'issues': 'none'}, {'issues': [None]}, {'issues': ['']},
                      {'issues': [], 'approved': True}):
            with self.subTest(audit=audit), self.assertRaises(ValueError):
                distill.run(self.config, self.source, self.complete_with([self.draft, audit]))

    def test_every_payload_is_budgeted_before_transport(self):
        self.config['request_bytes'] = 50
        with self.assertRaisesRegex(ValueError, 'completion request exceeds'):
            distill.run(self.config, self.source, self.complete_with([]))
        self.assertFalse(self.calls)
        self.config['request_bytes'] = 10000
        huge = dict(self.draft, extra='x' * 20000)
        with self.assertRaisesRegex(ValueError, 'completion request exceeds'):
            distill.payload(self.config, distill.REVIEW_PROMPT, huge, distill.AUDIT_SCHEMA)


class BalancedDocumentTest(unittest.TestCase):
    def setUp(self):
        self.config = dict(agent.DEFAULTS, api_key='test-key-never-a-real-secret')
        self.source = ('History\nConfused for three days.\n\nImpression\nPossible infection.\n\n'
                       'Observations\nHR 112\nBP 96/60\n\nPlan\nStart drug A 1 mg daily if needed.')
        self.context = {'points': [{'text': 'Confusion for three days.', 'evidence_ids': ['1']}]}

    def test_protected_facts_are_copied_even_when_generator_returns_no_context(self):
        replies = iter([{'points': []}, {'issues': []}])
        output = distill.run(self.config, self.source, lambda _: next(replies), protect=True)
        self.assertEqual(['Impression\nPossible infection.', 'Observations\nHR 112\nBP 96/60',
                          'Plan\nStart drug A 1 mg daily if needed.'], [p['text'] for p in output['points']])

    def test_generated_text_cannot_replace_a_protected_plan(self):
        draft = {'points': [{'text': 'Started drug A 1 mg daily.', 'evidence_ids': ['4']}]}
        with self.assertRaisesRegex(ValueError, 'cannot replace protected'):
            distill.run(self.config, self.source, lambda _: draft, protect=True)

    def test_full_source_is_available_to_review_and_omission_can_be_repaired(self):
        replies = iter([{'points': []}, {'issues': ['Source 1: include three-day confusion.']},
                        self.context, {'issues': []}])
        calls = []
        def complete(payload):
            calls.append(payload)
            return next(replies)
        output = distill.run(self.config, self.source, complete, protect=True)
        self.assertEqual(4, len(calls))
        self.assertEqual('Confusion for three days.', output['points'][0]['text'])
        review = json.loads(calls[1]['messages'][1]['content'])
        self.assertEqual(4, len(review['passages']))
        self.assertEqual(['2', '3', '4'], review['protected_ids'])
        self.assertEqual(['Plan\nStart drug A 1 mg daily if needed.'], output['points'][-1]['evidence'])

    def test_all_protected_document_needs_no_model_call_and_loses_no_observations(self):
        source = 'Plan\nReview tomorrow.\n\nObservations\nHR 80'
        def unexpected(_):
            self.fail('No generation is needed when every passage is copied in full')
        result = distill.run(self.config, source, unexpected, protect=True)
        self.assertEqual(2, len(result['points']))
        self.assertEqual('Observations\nHR 80', result['points'][1]['text'])

    def test_history_and_systems_review_preserve_ambiguous_terms_and_positive_findings(self):
        source = ('Family History\nMother: AO (early onset).\n\n'
                  'Review of Systems\nNo dysuria. Reduced urinary output reported.\n\n'
                  'Plan\nReview tomorrow.')
        result = distill.run(self.config, source, lambda _: self.fail('All sections should be copied'), protect=True)
        text = '\n'.join(p['text'] for p in result['points'])
        self.assertIn('AO (early onset)', text)
        self.assertNotIn('arthritis', text)
        self.assertIn('Reduced urinary output reported.', text)

    def test_repeated_citations_are_canonicalized_without_changing_text_or_accepting_unknown_ids(self):
        context = fidelity.prepare(self.source, compact=False)
        draft = {'points': [{'text': 'Confused for three days.', 'evidence_ids': ['1', '1']}]}
        result = distill.canonicalize_citations(draft, context)
        self.assertEqual(['1'], result['points'][0]['evidence_ids'])
        self.assertEqual(draft['points'][0]['text'], result['points'][0]['text'])
        self.assertEqual(['1', '1'], draft['points'][0]['evidence_ids'])
        draft['points'][0]['evidence_ids'].append('999')
        with self.assertRaises(ValueError):
            distill.canonicalize_citations(draft, context)

    def test_generator_does_not_receive_protected_body_text_to_accidentally_rewrite(self):
        replies = iter([self.context, {'issues': []}])
        calls = []
        def complete(payload):
            calls.append(payload)
            return next(replies)
        distill.run(self.config, self.source, complete, protect=True)
        generator = json.loads(calls[0]['messages'][1]['content'])
        self.assertEqual({'1': 'History\nConfused for three days.'}, generator['passages'])
        self.assertNotIn('drug A', json.dumps(generator))
        review = json.loads(calls[1]['messages'][1]['content'])
        self.assertIn('drug A', json.dumps(review))

    def test_flat_metadata_block_does_not_force_identity_into_medication_or_allergy_points(self):
        source = ('Patient Name: Test Person\n- NHS Number: 12345678\n- Allergies: Penicillin\n'
                  '- Current Medications: Drug A 1 mg daily')
        context = distill.source_context(source)
        self.assertEqual(4, len(context['passages']))
        self.assertEqual({'3', '4'}, distill.protected_ids(context))
        replies = iter([{'points': []}, {'issues': []}])
        result = distill.run(self.config, source, lambda _: next(replies), protect=True)
        self.assertEqual(2, len(result['points']))
        self.assertNotIn('Test Person', str(result))
        self.assertNotIn('12345678', str(result))
        for point in result['points']:
            self.assertIn(point['evidence'][0], source)

    def test_conditional_or_temporal_negation_is_not_rewritten(self):
        for phrase in ('No vomiting, diarrhoea, fever with rigors, or respiratory distress at home.',
                       'No chest pain with walking.', 'No dizziness since the medication was stopped.'):
            source = 'History\n' + phrase + '\n\nPlan\nReview tomorrow.'
            with self.subTest(phrase=phrase):
                output = distill.run(self.config, source, lambda _: self.fail('Must copy qualified negation'), protect=True)
                self.assertIn(phrase, output['points'][0]['text'])

    def test_unstated_physiological_labels_cannot_be_approved_by_auditor(self):
        draft = {'points': [{'text': 'Hypotension: BP 96/60.', 'evidence_ids': ['3']}]}
        issues = distill.host_issues(draft, fidelity.prepare(self.source, compact=False))
        self.assertTrue(any('clinical label' in issue for issue in issues))


if __name__ == '__main__':
    unittest.main()
