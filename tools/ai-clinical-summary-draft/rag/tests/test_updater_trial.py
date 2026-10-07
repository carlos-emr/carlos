# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Offline tests for the chart-updater + RAG trial. No Ollama, no network, no API key.

Run from tools/ai-clinical-summary-draft:
    python3 -m unittest discover -s rag/tests -t .
"""
import json
from pathlib import Path
import sqlite3
import sys
import unittest

TOOL = Path(__file__).resolve().parents[2]
for path in (TOOL, TOOL / 'rag'):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

import chart_updates  # noqa: E402
import rag_trial as rag  # noqa: E402
import updater_trial as trial  # noqa: E402
import openrouter_agent as agent  # noqa: E402


def committed():
    return agent.committed_notes()[0]


class InputsTest(unittest.TestCase):
    def test_labelled_documents_pass_the_updaters_own_request_contract(self):
        notes = committed()
        for doc in trial.load_labels()['documents']:
            request = trial.build_request(trial.incoming_document(notes, doc))
            chart_updates.validate_request(request, notes, 50000)  # raises on anything but a complete note

    def test_every_label_cue_is_an_exact_phrase_of_its_document(self):
        notes = committed()
        for doc in trial.load_labels()['documents']:
            body = trial.incoming_document(notes, doc)
            for fact in doc['facts']:
                for cue in fact['cues']:
                    self.assertIn(cue, body, f"{doc['patient']} {fact['id']}")

    def test_a_partial_note_is_refused(self):
        notes = committed()
        doc = trial.load_labels()['documents'][0]
        body = trial.incoming_document(notes, doc)
        with self.assertRaises(ValueError):
            chart_updates.validate_request(trial.build_request(body[:200]), notes, 50000)


class RetrievalTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute('CREATE TABLE chunks(id INTEGER PRIMARY KEY, patient_id TEXT, note_id TEXT, date TEXT, '
                        'heading TEXT, start INTEGER, end INTEGER, text TEXT, embed_text TEXT)')
        self.db.execute('CREATE TABLE vectors(chunk_id INTEGER PRIMARY KEY, embedding BLOB)')
        rows = [(1, 'NHSSYN001', 'NHSSYN001-n001', '2026-01-07', 'Plan', 'early'),
                (2, 'NHSSYN001', 'NHSSYN001-n016', '2026-01-11', 'Plan', 'just before'),
                (3, 'NHSSYN001', 'NHSSYN001-n017', '2026-01-12', 'Plan', 'the incoming note itself'),
                (4, 'NHSSYN001', 'NHSSYN001-n018', '2026-01-12', 'Plan', 'later'),
                (5, 'NHSSYN002', 'NHSSYN002-n001', '2025-12-20', 'Plan', 'another patient')]
        for cid, patient, note, date, heading, text in rows:
            self.db.execute('INSERT INTO chunks VALUES (?,?,?,?,?,0,0,?,?)', (cid, patient, note, date, heading, text, text))
            self.db.execute('INSERT INTO vectors VALUES (?,?)', (cid, rag.pack([1.0, float(cid)])))

    def test_existing_chart_is_only_this_patients_earlier_notes(self):
        chunks = trial.earlier_chunks(self.db, 'NHSSYN001', 17)
        self.assertEqual([c['id'] for c in chunks], [1, 2])

    def test_top_passages_rank_by_cosine_then_id(self):
        chunks = trial.earlier_chunks(self.db, 'NHSSYN001', 17)
        self.assertEqual([c['id'] for c in trial.top_passages(chunks, [0.0, 1.0], k=1)], [2])
        self.assertEqual(len(trial.top_passages(chunks, [1.0, 0.0], k=5)), 2)


class DevDocumentTest(unittest.TestCase):
    def test_a_development_note_is_any_committed_synthetic_note_and_nothing_else(self):
        notes = committed()
        doc = trial.dev_document(notes, 'NHSSYN006:10')
        self.assertEqual((doc['patient'], doc['note']), ('NHSSYN006', 10))
        trial.incoming_document(notes, doc)
        for bad in ('NHSSYN006:999', 'PATIENT1:1', 'NHSSYN006:x'):
            with self.assertRaises(ValueError):
                trial.dev_document(notes, bad)


class OutgoingGuardTest(unittest.TestCase):
    def test_only_an_exact_slice_of_that_patients_committed_note_may_leave(self):
        notes = committed()
        patient, date, body = next(n for n in notes if n[0] == 'NHSSYN001')
        ok = {'note_id': 'NHSSYN001-n001', 'date': date, 'text': body[:80]}
        trial.check_outgoing_passage(notes, 'NHSSYN001', ok)
        with self.assertRaises(ValueError):
            trial.check_outgoing_passage(notes, 'NHSSYN001', dict(ok, text=body[:80] + ' invented'))
        with self.assertRaises(ValueError):
            trial.check_outgoing_passage(notes, 'NHSSYN002', ok)
        with self.assertRaises(ValueError):
            trial.check_outgoing_passage(notes, 'NHSSYN001', dict(ok, date='2020-01-01'))


class ChartReviewTest(unittest.TestCase):
    config = {'model': 'test/model', 'provider': 'test', 'max_tokens': 8192, 'temperature': 0,
              'reasoning_tokens': 0, 'request_bytes': 50000}

    def setUp(self):
        self.candidates = {'1': {'kind': 'review', 'destination': 'Medications', 'evidence': 'Nimodipine 30 mg PO QDS'},
                           '2': {'kind': 'history', 'destination': 'Concerns', 'evidence': 'HR: 84'}}
        chunk = {'id': 7, 'date': '2026-01-10', 'heading': 'Plan', 'text': 'Maintain nimodipine 30 mg every 4 hours'}
        self.passages = {'1': [chunk], '2': []}

    def test_each_candidate_lists_only_its_own_passages(self):
        for variant in ('v1', 'v2'):
            payload, listed = trial.chart_review_payload(self.config, 'source', self.candidates, self.passages, variant)
            self.assertEqual(listed, {'1': ['C7'], '2': []})
            item = payload['response_format']['json_schema']['schema']['properties']['decisions']['items']['properties']
            self.assertEqual(item['chart_ref']['enum'], ['', 'C7'])
            self.assertEqual(payload['provider']['data_collection'], 'deny')
            self.assertIn(trial.PROMPTS[variant], payload['messages'][0]['content'])

    def test_v2_puts_the_passage_text_inside_its_candidate(self):
        payload, _listed = trial.chart_review_payload(self.config, 'source', self.candidates, self.passages, 'v2')
        content = json.loads(payload['messages'][1]['content'])
        self.assertNotIn('chart_passages', content)
        self.assertEqual(content['candidates']['1']['chart_passages'],
                         [{'id': 'C7', 'date': '2026-01-10', 'section': 'Plan',
                           'text': 'Maintain nimodipine 30 mg every 4 hours'}])
        self.assertEqual(content['candidates']['2']['chart_passages'], [])

    def test_valid_decisions_are_kept_with_their_chart_status(self):
        review = {'decisions': [
            {'id': '1', 'keep': True, 'reason': 'frequency differs', 'chart_status': 'conflict', 'chart_ref': 'C7'},
            {'id': '2', 'keep': True, 'reason': 'today', 'chart_status': 'new', 'chart_ref': ''}]}
        decisions = trial.chart_review_decisions(review, self.candidates, {'1': ['C7'], '2': []})
        self.assertEqual(decisions['1']['chart_status'], 'conflict')
        self.assertTrue(decisions['1']['ref_offered'])

    def test_a_new_fact_may_not_cite_a_passage_and_every_candidate_needs_a_decision(self):
        bad = {'decisions': [
            {'id': '1', 'keep': True, 'reason': 'x', 'chart_status': 'new', 'chart_ref': 'C7'},
            {'id': '2', 'keep': True, 'reason': 'x', 'chart_status': 'new', 'chart_ref': ''}]}
        with self.assertRaises(ValueError):
            trial.chart_review_decisions(bad, self.candidates, {'1': ['C7'], '2': []})
        with self.assertRaises(ValueError):
            trial.chart_review_decisions({'decisions': bad['decisions'][1:]}, self.candidates, {'1': ['C7'], '2': []})

    def test_a_citation_of_another_candidates_passage_is_marked_not_offered(self):
        review = {'decisions': [
            {'id': '1', 'keep': True, 'reason': 'x', 'chart_status': 'already_recorded', 'chart_ref': 'C7'},
            {'id': '2', 'keep': True, 'reason': 'x', 'chart_status': 'already_recorded', 'chart_ref': 'C7'}]}
        decisions = trial.chart_review_decisions(review, self.candidates, {'1': ['C7'], '2': []})
        self.assertFalse(decisions['2']['ref_offered'])


class ChartQuoteTest(unittest.TestCase):
    texts = {'C7': 'Plan - Maintain nimodipine 30 mg every 4 hours'}

    def setUp(self):
        self.candidates = {'1': {'kind': 'review', 'destination': 'Medications', 'evidence': 'Nimodipine 30 mg PO QDS'},
                           '2': {'kind': 'history', 'destination': 'Concerns', 'evidence': 'HR: 84'}}

    def review(self, quote, status='conflict', ref='C7'):
        return {'decisions': [
            {'id': '1', 'keep': True, 'reason': 'x', 'chart_status': status, 'chart_ref': ref, 'chart_quote': quote},
            {'id': '2', 'keep': True, 'reason': 'x', 'chart_status': 'new', 'chart_ref': '', 'chart_quote': ''}]}

    def test_v3_schema_requires_a_bounded_quote(self):
        config = ChartReviewTest.config
        payload, _ = trial.chart_review_payload(config, 'source', self.candidates, {'1': [
            {'id': 7, 'date': '2026-01-10', 'heading': 'Plan', 'text': self.texts['C7']}]}, 'v3')
        item = payload['response_format']['json_schema']['schema']['properties']['decisions']['items']
        self.assertIn('chart_quote', item['required'])
        self.assertEqual(item['properties']['chart_quote']['maxLength'], 200)

    def test_an_exact_quote_of_the_cited_passage_is_shown(self):
        decisions = trial.chart_review_decisions(self.review('nimodipine 30 mg every 4 hours'), self.candidates,
                                                 {'1': ['C7'], '2': []}, self.texts)
        self.assertTrue(decisions['1']['quote_exact'])
        self.assertEqual(trial.shown_status(decisions['1']), 'conflict')
        self.assertTrue(decisions['2']['quote_exact'])

    def test_a_reworded_or_unoffered_quote_drops_the_hint(self):
        listed = {'1': ['C7'], '2': []}
        reworded = trial.chart_review_decisions(self.review('nimodipine 30mg q4h'), self.candidates, listed, self.texts)
        self.assertFalse(reworded['1']['quote_exact'])
        self.assertEqual(trial.shown_status(reworded['1']), 'new')
        unoffered = trial.chart_review_decisions(self.review('nimodipine 30 mg every 4 hours'), self.candidates,
                                                 {'1': [], '2': ['C7']}, self.texts)
        self.assertFalse(unoffered['1']['quote_exact'])

    def test_only_whitespace_may_differ_in_a_quote(self):
        self.assertTrue(trial.quote_matches('Plan -  Maintain\nnimodipine', self.texts['C7']))
        for bad in ('plan - maintain nimodipine', 'Plan - Maintain nimodipne', 'Pl', '   '):
            self.assertFalse(trial.quote_matches(bad, self.texts['C7']), bad)

    def test_a_new_fact_may_not_quote_and_the_quote_field_is_required(self):
        bad = self.review('', status='new', ref='')
        bad['decisions'][1]['chart_quote'] = 'HR'
        with self.assertRaises(ValueError):
            trial.chart_review_decisions(bad, self.candidates, {'1': ['C7'], '2': []}, self.texts)
        missing = self.review('nimodipine 30 mg every 4 hours')
        del missing['decisions'][0]['chart_quote']
        with self.assertRaises(ValueError):
            trial.chart_review_decisions(missing, self.candidates, {'1': ['C7'], '2': []}, self.texts)


class TransportTest(unittest.TestCase):
    def test_budget_is_enforced_and_usage_recorded(self):
        def fake(config, endpoint, payload):
            self.assertEqual(payload['usage'], {'include': True})
            return {'usage': {'prompt_tokens': 10, 'completion_tokens': 5, 'cost': 0.001}}
        transport = trial.CountingTransport(fake, budget=2)
        transport({}, 'chat/completions', {'model': 'x'})
        transport({}, 'chat/completions', {'model': 'x'})
        with self.assertRaises(ValueError):
            transport({}, 'chat/completions', {'model': 'x'})
        self.assertEqual(transport.summary(), {'calls': 2, 'prompt_tokens': 20, 'completion_tokens': 10, 'cost_usd': 0.002})

    def test_a_failed_attempt_still_counts_against_the_budget(self):
        def failing(config, endpoint, payload):
            raise agent.UpstreamError('API key rejected (HTTP 401)')
        transport = trial.CountingTransport(failing, budget=1)
        with self.assertRaises(agent.UpstreamError):
            transport({}, 'chat/completions', {'model': 'x'})
        with self.assertRaises(ValueError):
            transport({}, 'chat/completions', {'model': 'x'})
        self.assertEqual(transport.summary()['calls'], 1)
        self.assertIsNone(transport.summary()['cost_usd'])

    def test_only_completions_are_allowed(self):
        with self.assertRaises(ValueError):
            trial.CountingTransport(lambda *a: {}, budget=5)({}, 'key')


class ScoreTest(unittest.TestCase):
    labels = {'patient': 'NHSSYN001', 'facts': [
        {'id': 'dup', 'cues': ['CRP 12'], 'dest': ['Concerns'], 'status': 'already_recorded', 'accept': ['already_recorded']},
        {'id': 'new', 'cues': ['HR 84'], 'dest': ['Concerns'], 'status': 'new', 'accept': ['new']},
        {'id': 'conf', 'cues': ['QDS'], 'dest': ['Medications'], 'status': 'conflict', 'accept': ['conflict']},
        {'id': 'gone', 'cues': ['never proposed'], 'dest': ['Concerns'], 'status': 'new', 'accept': ['new']},
        {'id': 'opt', 'cues': ['optional thing'], 'dest': ['Concerns'], 'status': 'new', 'accept': ['new'], 'optional': True}]}
    arms = {'candidates': {
        '1': {'kind': 'history', 'destination': 'Concerns', 'evidence': 'CRP 12 mg/L'},
        '2': {'kind': 'history', 'destination': 'MedHistory', 'evidence': 'HR 84'},
        '3': {'kind': 'review', 'destination': 'Medications', 'evidence': 'Nimodipine 30 mg QDS'},
        '4': {'kind': 'history', 'destination': 'Concerns', 'evidence': 'Dr Someone (SpR)'}},
        'without': {'1': {'keep': True}, '2': {'keep': True}, '3': {'keep': True}, '4': {'keep': False}},
        'with': {'1': {'keep': True, 'chart_status': 'already_recorded', 'chart_ref': 'C1', 'ref_offered': True},
                 '2': {'keep': True, 'chart_status': 'already_recorded', 'chart_ref': 'C2', 'ref_offered': True},
                 '3': {'keep': True, 'chart_status': 'conflict', 'chart_ref': 'C3', 'ref_offered': True},
                 '4': {'keep': True, 'chart_status': 'new', 'chart_ref': '', 'ref_offered': False}}}

    def test_without_arm_counts_duplicates_misses_and_wrong_sections(self):
        result = trial.score_arm(self.labels, self.arms, 'without')
        self.assertEqual(result['missed'], ['gone'])
        self.assertEqual(result['duplicates_suggested'], 1)
        self.assertEqual(result['duplicates_unflagged'], 1)
        self.assertEqual(result['wrong_section'], ['new:MedHistory'])
        self.assertEqual(result['hints_correct'], 0)

    def test_a_hint_with_an_inexact_quote_counts_as_not_shown(self):
        arms = json.loads(json.dumps(self.arms))
        arms['with']['3'].update(chart_quote='QDS', quote_exact=False)
        result = trial.score_arm(self.labels, arms, 'with')
        self.assertEqual(result['conflicts_flagged'], 0)
        self.assertEqual(result['hints_wrong'], ['new:already_recorded', 'conf:new'])
        self.assertEqual(result['quotes_not_exact'], 1)
        self.assertEqual(result['hints_correct_before_quote_check'], 2)

    def test_with_arm_scores_hints_including_a_false_already_recorded(self):
        result = trial.score_arm(self.labels, self.arms, 'with')
        self.assertEqual(result['hints_correct'], 2)
        self.assertEqual(result['hints_wrong'], ['new:already_recorded'])
        self.assertEqual(result['false_already_recorded'], 1)
        self.assertEqual(result['duplicates_unflagged'], 0)
        self.assertEqual(result['conflicts_flagged'], 1)
        self.assertEqual(result['unlabelled_kept'], 1)
        self.assertEqual(result['citations_not_offered'], 0)
        self.assertEqual(result['false_conflicts'], [])
        self.assertEqual(result['keep_changed_vs_without'], ['4'])

    def test_totals_sum_numbers_and_count_lists_across_patients(self):
        other = dict(self.labels, patient='NHSSYN002')
        result = trial.score({'documents': [self.labels, other]},
                             [dict(self.arms, patient='NHSSYN001'), dict(self.arms, patient='NHSSYN002')])
        self.assertEqual(result['total']['with']['hints_correct'], 4)
        self.assertEqual(result['total']['without']['missed'], 2)
        self.assertEqual(result['total']['with']['facts_covered'], 6)

    def test_a_conflict_on_a_fact_that_cannot_conflict_is_a_false_conflict(self):
        arms = json.loads(json.dumps(self.arms))
        arms['with']['2'].update(chart_status='conflict')
        self.assertEqual(trial.score_arm(self.labels, arms, 'with')['false_conflicts'], ['new'])


if __name__ == '__main__':
    unittest.main()
