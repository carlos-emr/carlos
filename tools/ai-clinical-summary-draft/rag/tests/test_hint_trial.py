# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Offline tests for round 2 of the chart-updater + RAG trial (hint_trial.py).

No Ollama, no network, no API key. Run from tools/ai-clinical-summary-draft:
    python3 -m unittest discover -s rag/tests -t .
"""
from pathlib import Path
import sys
import unittest

TOOL = Path(__file__).resolve().parents[2]
for path in (TOOL, TOOL / 'rag'):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

import chart_updates  # noqa: E402
import hint_trial as trial  # noqa: E402
import pipeline  # noqa: E402
import updater_trial as ut  # noqa: E402

NOTE = ("Ward round\nObservations:\nBP 128/74, HR 88\nTemp 37.1\n\nExamination\nChest clear\n\n"
        "Plan:\n- Continue amlodipine 5 mg OD\n- BP check in 2 weeks\nO/E: abdomen soft\n"
        "Allergies: penicillin\nNo new concerns overnight\n- Repeat U&E tomorrow\n")
PASSAGES = [{'date': '2026-01-02', 'heading': 'Medications', 'text': 'Amlodipine 5 mg OD started for hypertension.'},
            {'date': '2026-01-03', 'heading': 'Plan', 'text': 'Repeat U&E in 48 hours.'}]


def answer(status, passage='NONE', quote='NONE', reasoning='Short reasoning.'):
    return f"{reasoning}\nANSWER: {status}\nPASSAGE: {passage}\nQUOTE: {quote}"


def committed():
    import openrouter_agent as agent
    return agent.committed_notes()[0]


class LabelsTest(unittest.TestCase):
    def test_labelled_documents_pass_the_updaters_own_request_contract(self):
        notes = committed()
        for doc in trial.load_labels()['documents']:
            chart_updates.validate_request(ut.build_request(ut.incoming_document(notes, doc)), notes, 50000)

    def test_every_label_cue_is_an_exact_phrase_of_its_document(self):
        notes = committed()
        for doc in trial.load_labels()['documents']:
            body = ut.incoming_document(notes, doc)
            for fact in doc['facts']:
                self.assertIn(fact['status'], fact['accept'], fact['id'])
                for cue in fact['cues']:
                    self.assertIn(cue, body, f"{doc['patient']}:{fact['id']}")

    def test_round_two_uses_new_patients_inside_the_round_three_index(self):
        documents = trial.load_labels()['documents']
        patients = {doc['patient'] for doc in documents}
        round_one = {doc['patient'] for doc in ut.load_labels()['documents']}
        self.assertEqual(len(documents), 5)
        self.assertFalse(patients & (round_one | {'NHSSYN006'}))  # 006 is the unlabelled development patient
        self.assertTrue(all('NHSSYN001' <= p <= 'NHSSYN010' for p in patients))


class HeadingRuleTest(unittest.TestCase):
    def skipped(self, evidence):
        return trial.skipped_by_rule(NOTE, {'evidence': evidence})

    def test_todays_readings_and_examination_are_never_compared(self):
        for evidence in ('BP 128/74, HR 88', 'Temp 37.1', 'Chest clear', 'O/E: abdomen soft'):
            self.assertTrue(self.skipped(evidence), evidence)

    def test_plans_allergies_and_follow_ups_are_compared_even_when_they_mention_a_reading(self):
        for evidence in ('- Continue amlodipine 5 mg OD', '- BP check in 2 weeks', 'Allergies: penicillin',
                         '- Repeat U&E tomorrow'):
            self.assertFalse(self.skipped(evidence), evidence)

    def test_a_status_line_is_not_taken_for_a_heading(self):
        self.assertEqual(trial.is_heading_line('No new concerns overnight'), '')
        self.assertEqual(trial.is_heading_line('BP 128/74'), '')
        self.assertEqual(trial.heading_before(NOTE, '- Repeat U&E tomorrow'), 'Plan')

    def test_evidence_not_in_the_note_has_no_heading(self):
        self.assertEqual(trial.heading_before(NOTE, 'not in the note'), '')


class AnswerTest(unittest.TestCase):
    def test_same_with_an_exact_quote_of_an_offered_passage_is_valid(self):
        parsed = trial.parse_answer(answer('SAME', 'P1', 'Amlodipine 5 mg OD'), PASSAGES)
        self.assertEqual((parsed['status'], parsed['passage'], parsed['valid']), ('already_recorded', 'P1', True))

    def test_only_whitespace_may_differ_and_surrounding_quote_marks_are_ignored(self):
        parsed = trial.parse_answer(answer('DIFFERENT', 'P2', '"Repeat  U&E\nin 48 hours"'), PASSAGES)
        self.assertTrue(parsed['valid'])
        self.assertEqual(parsed['status'], 'conflict')

    def test_a_reworded_quote_an_unoffered_passage_or_a_missing_line_is_invalid(self):
        self.assertFalse(trial.parse_answer(answer('SAME', 'P1', 'Amlodipine 5mg daily'), PASSAGES)['valid'])
        self.assertFalse(trial.parse_answer(answer('SAME', 'P3', 'Amlodipine 5 mg OD'), PASSAGES)['valid'])
        self.assertFalse(trial.parse_answer('I think it is the same.', PASSAGES)['valid'])
        self.assertFalse(trial.parse_answer(answer('MAYBE'), PASSAGES)['valid'])

    def test_unrelated_needs_no_passage_and_the_last_answer_lines_count(self):
        text = 'ANSWER: SAME\nOn reflection it is a new plan.\n' + answer('UNRELATED')
        parsed = trial.parse_answer(text, PASSAGES)
        self.assertEqual((parsed['status'], parsed['valid']), ('new', True))

    def test_markdown_bold_labels_are_read(self):
        parsed = trial.parse_answer('Reasoning.\n**ANSWER:** SAME\n**PASSAGE:** P1\n**QUOTE:** Amlodipine 5 mg OD',
                                    PASSAGES)
        self.assertTrue(parsed['valid'])


class AgreementTest(unittest.TestCase):
    def parsed(self, *texts):
        return [trial.parse_answer(t, PASSAGES) for t in texts]

    def test_three_identical_valid_answers_show_the_hint(self):
        same = answer('SAME', 'P1', 'Amlodipine 5 mg OD')
        self.assertEqual(trial.agreed_hint(self.parsed(same, same, same)), 'already_recorded')

    def test_any_disagreement_invalid_answer_or_missing_answer_shows_nothing(self):
        same = answer('SAME', 'P1', 'Amlodipine 5 mg OD')
        other = answer('SAME', 'P2', 'Repeat U&E in 48 hours')
        self.assertIsNone(trial.agreed_hint(self.parsed(same, same, other)))  # another passage
        self.assertIsNone(trial.agreed_hint(self.parsed(same, same, answer('UNRELATED'))))
        self.assertIsNone(trial.agreed_hint(self.parsed(same, same, 'no answer')))
        self.assertIsNone(trial.agreed_hint(self.parsed(same, same)))

    def test_three_unrelated_answers_show_nothing(self):
        unrelated = answer('UNRELATED')
        self.assertIsNone(trial.agreed_hint(self.parsed(unrelated, unrelated, unrelated)))

    def test_first_answer_only_is_what_a_single_ask_would_show(self):
        conflict = answer('DIFFERENT', 'P1', 'Amlodipine 5 mg OD')
        self.assertEqual(trial.first_answer_hint(self.parsed(conflict, answer('UNRELATED'), 'x')), 'conflict')
        self.assertIsNone(trial.first_answer_hint(self.parsed('x', conflict, conflict)))


class CompletionTest(unittest.TestCase):
    CONFIG = {'model': 'synthetic/model', 'provider': 'synthetic', 'max_tokens': 16384, 'request_bytes': 50000,
              'timeout_seconds': 60}

    def gateway(self, reply):
        class Fake:
            config = self.CONFIG
            def __init__(self):
                self.sent = []
            def transport(self, config, endpoint, payload):
                self.sent.append((endpoint, payload))
                return reply
        return Fake()

    def reply(self, content='text', finish='stop', model='synthetic/model'):
        return {'model': model, 'choices': [{'finish_reason': finish, 'message': {'content': content}}]}

    def test_the_question_is_free_text_with_zero_retention_and_no_fallbacks(self):
        payload = trial.hint_payload(self.CONFIG, {'kind': 'history', 'destination': 'MedHistory',
                                                   'evidence': 'Amlodipine 5 mg OD'}, '2026-01-09', PASSAGES)
        self.assertNotIn('response_format', payload)
        self.assertEqual(payload['provider'], {'only': ['synthetic'], 'allow_fallbacks': False,
                                               'require_parameters': True, 'data_collection': 'deny', 'zdr': True})
        self.assertEqual(payload['temperature'], trial.TEMPERATURE)
        user = payload['messages'][1]['content']
        self.assertIn('P1 (2026-01-02, Medications):', user)
        self.assertIn('Amlodipine 5 mg OD', user)

    def test_a_finished_reply_from_the_configured_model_is_returned_as_text(self):
        self.assertEqual(trial.complete_text(self.gateway(self.reply('ANSWER: SAME')), {}), 'ANSWER: SAME')

    def test_another_model_a_cut_off_reply_or_a_missing_message_is_refused(self):
        with self.assertRaises(ValueError):
            trial.complete_text(self.gateway(self.reply(model='other/model')), {})
        with self.assertRaises(pipeline.OutputLimitError):
            trial.complete_text(self.gateway(self.reply(finish='length')), {})
        with self.assertRaises(ValueError):
            trial.complete_text(self.gateway({'model': 'synthetic/model',
                                              'choices': [{'finish_reason': 'stop', 'message': {}}]}), {})

    def test_the_trial_budget_still_applies_to_hint_calls(self):
        transport = ut.CountingTransport(transport=lambda c, e, p: self.reply(), budget=1)
        gateway = self.gateway(None)
        gateway.transport = transport
        trial.complete_text(gateway, {})
        with self.assertRaises(ValueError):
            trial.complete_text(gateway, {})


class BudgetTest(unittest.TestCase):
    def test_a_card_is_asked_only_when_all_its_asks_fit_the_budget(self):
        class Gate:
            pass
        gate = Gate()
        gate.transport = ut.CountingTransport(transport=lambda c, e, p: {}, budget=5)
        gate.transport.calls = [{}] * 2
        self.assertTrue(trial.budget_allows(gate, 3))
        gate.transport.calls = [{}] * 3
        self.assertFalse(trial.budget_allows(gate, 3))


class ScoreTest(unittest.TestCase):
    LABELS = {'patient': 'NHSSYN099', 'note': 12, 'date': '2026-01-09', 'facts': [
        {'id': 'amlodipine', 'cues': ['amlodipine 5 mg'], 'dest': ['Medications'], 'status': 'already_recorded',
         'accept': ['already_recorded']},
        {'id': 'ramipril-up', 'cues': ['ramipril 10 mg'], 'dest': ['Medications'], 'status': 'conflict',
         'accept': ['conflict', 'new']},
        {'id': 'clinic', 'cues': ['clinic in 6 weeks'], 'dest': ['Tickler'], 'status': 'new', 'accept': ['new']},
        {'id': 'obs', 'cues': ['BP 128/74'], 'dest': ['Concerns'], 'status': 'new', 'accept': ['new'],
         'optional': True},
        {'id': 'allergy', 'cues': ['penicillin'], 'dest': ['Allergies'], 'status': 'already_recorded',
         'accept': ['already_recorded']}]}

    def run_with(self, hints):
        cards = {'1': 'Continue amlodipine 5 mg OD', '2': 'Increase ramipril 10 mg', '3': 'Review in clinic in 6 weeks',
                 '4': 'Extra unlabelled card'}
        return {'patient': 'NHSSYN099', 'candidates': {r: {'kind': 'history', 'destination': 'Medications',
                                                           'evidence': e} for r, e in cards.items()},
                'kept': ['1', '2', '3', '4'], 'skipped_by_rule': [],
                'hints': {ref: {'answers': [{'status': s or 'new', 'passage': None, 'valid': True}] * 3,
                                'shown': s, 'first_only': s} for ref, s in hints.items()}}

    def test_shown_hints_are_scored_per_card_and_per_fact(self):
        result = trial.score_run(self.LABELS, self.run_with(
            {'1': 'already_recorded', '2': 'conflict', '3': 'conflict', '4': 'already_recorded'}))
        self.assertEqual(result['hints_shown'], 4)
        self.assertEqual(result['hints_shown_right'], 2)
        self.assertEqual(result['hints_shown_wrong'], ['3:conflict:clinic'])
        self.assertEqual(result['hints_on_unlabelled_cards'], 1)
        self.assertEqual(result['false_conflicts'], ['clinic'])
        self.assertEqual((result['duplicates'], result['duplicates_flagged']), (1, 1))
        self.assertEqual((result['conflict_facts'], result['conflicts_flagged']), (1, 1))
        self.assertEqual(result['facts_missed'], ['allergy'])  # the optional observation is not a miss

    def test_no_hint_counts_as_new_for_the_fact(self):
        result = trial.score_run(self.LABELS, self.run_with({'1': None, '2': None, '3': None}))
        self.assertEqual(result['facts_right'], 2)  # ramipril accepts new; clinic is new
        self.assertEqual(result['facts_wrong'], ['amlodipine:new'])
        self.assertEqual(result['hints_shown'], 0)


if __name__ == '__main__':
    unittest.main()
