# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
import unittest
from unittest.mock import patch

import test_chart_updates
import chart_updates as updates


class ChartCoverageTest(unittest.TestCase):
    def setUp(self):
        fixture = test_chart_updates.ChartUpdatesTest()
        fixture.setUp()
        self.config = fixture.config
        self.request = fixture.request

    def run_source(self, source, complete):
        self.request['sources'][0]['text'] = source
        return updates.run(self.config, self.request, [('TEST', '', source)], complete)['output']

    def test_second_pass_recovers_fact_after_empty_first_selection(self):
        calls = []
        def complete(payload):
            content = json.loads(payload['messages'][1]['content'])
            calls.append(content)
            if 'existing_candidates' in content:
                self.assertEqual([], content['existing_candidates'])
                return {'proposals': [{'destination': 'Concerns', 'start_id': 1, 'end_id': 1}]}
            if 'segments' in content:
                return {'proposals': []}
            return {'decisions': [{'id': '1', 'keep': True, 'reason': 'Explicit finding'}]}
        output = self.run_source('Pulse 80.', complete)
        self.assertEqual('Pulse 80.', output['proposals'][0]['evidence'])
        self.assertEqual(3, len(calls))
        self.assertEqual([{'start': 0, 'end': 9}], output['coverage']['sections'])

    def test_all_focus_batches_receive_whole_source_and_two_passes(self):
        source = '\n\n'.join('Finding ' + str(i) + ': ' + 'x' * 900 + '.' for i in range(20))
        calls = []
        def complete(payload):
            content = json.loads(payload['messages'][1]['content'])
            self.assertEqual(source, content['context_before'] + ''.join(content['segments'].values()) + content['context_after'])
            calls.append(content)
            return {'proposals': []}
        output = self.run_source(source, complete)
        self.assertGreater(len(calls), 2)
        self.assertEqual(0, len(calls) % 2)
        for first, second in zip(calls[::2], calls[1::2]):
            self.assertEqual(first['focus_sections'], second['focus_sections'])
            self.assertNotIn('existing_candidates', first)
            self.assertIn('existing_candidates', second)
        focused = [s for call in calls[::2] for s in call['focus_sections']]
        self.assertEqual(updates.source_sections(updates.source_segments(source)), focused)
        updates.validate_coverage(output['coverage'], source)

    def test_reviewer_rejection_retains_exact_evidence_and_explanation(self):
        responses = iter([{'proposals': [{'destination': 'Tickler', 'start_id': 1, 'end_id': 1}]},
                          {'proposals': []}, {'decisions': [{'id': '1', 'keep': False, 'reason': 'Completed later'}]}])
        source = 'Review in two weeks.\n\nLater: review completed.'
        output = self.run_source(source, lambda _: next(responses))
        self.assertEqual([], output['proposals'])
        self.assertEqual([{'evidence': 'Review in two weeks.', 'reason': 'Completed later'}], output['coverage']['rejected'])

    def test_invalid_second_pass_fails_without_returning_initial_suggestions(self):
        responses = iter([{'proposals': [{'destination': 'Concerns', 'start_id': 1, 'end_id': 1}]}, {'proposals': 'broken'}])
        with self.assertRaisesRegex(ValueError, 'Invalid proposal'):
            self.run_source('Pulse 80.', lambda _: next(responses))

    def test_section_and_batch_limits_are_checked_before_network(self):
        for limit in ('MAX_SECTIONS', 'MAX_SELECTION_BATCHES'):
            with self.subTest(limit=limit), patch.object(updates, limit, 0), self.assertRaisesRegex(ValueError, 'budget'):
                self.run_source('Pulse 80.', lambda _: self.fail('Unexpected model call'))

    def test_focus_rejects_unrelated_and_malformed_ranges(self):
        lines = updates.source_segments('One.\nTwo.\nThree.')
        for start, end in [(3, 3), (True, 1), (0, 1), (1, 4), (2, 1)]:
            with self.subTest(start=start, end=end), self.assertRaises(ValueError):
                updates.focused_ranges({'proposals': [{'destination': 'Concerns', 'start_id': start, 'end_id': end}]},
                                       lines, [{'start_id': 1, 'end_id': 1}])

    def test_partition_preserves_unicode_crlf_and_unrecognized_sections(self):
        source = 'Novel heading:\r\n\U0001f600 fact.\r\n\r\nAnother heading:\r\nOther fact.'
        lines = updates.source_segments(source)
        coverage = updates.coverage_output(lines, updates.source_sections(lines), [])
        updates.validate_coverage(coverage, source)
        encoded = source.encode('utf-16-le')
        self.assertEqual(source, ''.join(encoded[row['start']*2:row['end']*2].decode('utf-16-le')
                                         for row in coverage['sections']))

    def test_invalid_coverage_cannot_be_served_from_cache(self):
        source = '\U0001f600 fact.'
        valid = {'version': 1, 'sections': [{'start': 0, 'end': 8}], 'rejected': []}
        updates.validate_output({'proposals': [], 'coverage': valid}, source)
        for change in ({'version': True}, {'sections': []}, {'sections': [{'start': 1, 'end': 8}]},
                       {'sections': [{'start': 0, 'end': 1}, {'start': 1, 'end': 8}]},
                       {'sections': [{'start': 0, 'end': 7}]}, {'complete': True},
                       {'rejected': [{'evidence': 'Invented.', 'reason': 'Not useful'}]}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                updates.validate_output({'proposals': [], 'coverage': dict(valid, **change)}, source)

    def test_explicit_tickler_selection_survives_duplicate_history_across_passes(self):
        evidence = 'Arrange outpatient review in two weeks.'
        rows = [{'kind': 'history', 'evidence': evidence}]
        updates.merge_candidates(rows, [{'kind': 'tickler', 'evidence': evidence}])
        self.assertEqual([{'kind': 'tickler', 'evidence': evidence}], rows)

    def test_omission_request_references_candidates_without_duplicating_long_quotes(self):
        source = '\n\n'.join(f'Finding {i}: ' + 'text ' * 55 + '.' for i in range(100))
        lines = updates.source_segments(source)
        sections = updates.source_sections(lines)
        candidates = [{'kind': 'history', 'destination': 'Concerns', 'evidence': line.strip()}
                      for line in lines.values() if line.strip()]
        config = dict(self.config, request_bytes=50000)
        payload = updates.selection_payload(config, lines, sections[:20], candidates[:20])
        self.assertLessEqual(len(json.dumps(payload).encode()), config['request_bytes'])
        content = json.loads(payload['messages'][1]['content'])
        self.assertEqual(source, content['context_before'] + ''.join(content['segments'].values()) + content['context_after'])
        self.assertTrue(all('evidence' not in row for row in content['existing_candidates']))
        for row, candidate in zip(content['existing_candidates'], candidates):
            quote = ''.join(lines[n] for n in range(row['start_id'], row['end_id'] + 1))
            self.assertIn(candidate['evidence'], quote)

    def test_candidate_reference_prefers_repeated_occurrence_in_current_focus(self):
        lines = updates.source_segments('Same fact.\n\nOther fact.\n\nSame fact.')
        result = updates.candidate_references(lines, [{'start_id': 5, 'end_id': 5}],
                                             [{'kind': 'history', 'evidence': 'Same fact.'}])
        self.assertEqual(5, result[0]['start_id'])
        self.assertEqual(5, result[0]['end_id'])

    def test_selection_schema_requires_intersection_with_focus(self):
        lines = updates.source_segments('Fact.\n' * 100)
        payload = updates.selection_payload(self.config, lines, [{'start_id': 40, 'end_id': 60}])
        fields = payload['response_format']['json_schema']['schema']['properties']['proposals']['items']['properties']
        self.assertEqual({'type': 'integer', 'minimum': 20, 'maximum': 60}, fields['start_id'])
        self.assertEqual({'type': 'integer', 'minimum': 40, 'maximum': 80}, fields['end_id'])

    def test_ambiguous_quote_edges_preserve_source_context_for_java_validation(self):
        cases = [
            ('Assessment at 21:30. Patient remains confused.', 'Patient remains confused.'),
            ('GCS was 14/15. Mild disorientation noted.', 'Mild disorientation noted.'),
            ('On Review\nNo new symptoms.', 'No new symptoms.'),
            ('On Examination\nAlert but disoriented.', 'Alert but disoriented.'),
            ('Clinician Leading Ward Round\nDr. Example\n\nFinding.', 'Dr. Example\n\nFinding.'),
            ('OT assessment shows limited dexterity (OT)\nTherapist Example',
             'OT assessment shows limited dexterity (OT)'),
            ('1. Hygroma\n-Confirmed on CT\n-No midline shift\n-Mild headache',
             '-No midline shift\n-Mild headache'),
        ]
        for source, excerpt in cases:
            with self.subTest(source=source):
                self.assertEqual(source, updates.preserve_passage_boundaries(excerpt, source, source.index(excerpt)))

    def test_clear_sentence_and_paragraph_edges_remain_independent(self):
        for source, excerpt in [('No asthma. Hypertension.', 'Hypertension.'),
                                ('Earlier paragraph.\n\nNo new symptoms.', 'No new symptoms.'),
                                ('Finding without punctuation\n\nNext paragraph.', 'Finding without punctuation')]:
            self.assertEqual(excerpt, updates.preserve_passage_boundaries(excerpt, source, source.index(excerpt)))

    def test_tickler_expansion_retains_valid_offsets_with_prefix_suffix_and_repetition(self):
        for prefix in ('', 'Background\n'):
            paragraph = prefix + 'Arrange outpatient review\nBring discharge summary.'
            source = paragraph + '\n\n' + paragraph
            lines = updates.source_segments(source)
            selected = [n for n, line in lines.items() if line.startswith('Arrange')][-1]
            output = updates.resolve_ranges({'proposals': [{'destination': 'Tickler', 'start_id': selected,
                                                            'end_id': selected}]}, lines, source)
            self.assertEqual([paragraph], [row['evidence'] for row in output['proposals']])

    def test_ambiguous_initial_does_not_detach_followup_from_prescription(self):
        source = 'Prescribe drug A. Advised to follow up with GP in 7 days.'
        output = updates.resolve_ranges({'proposals': [{'destination': 'Tickler', 'start_id': 2, 'end_id': 2}]},
                                        updates.source_segments(source), source)
        self.assertEqual([], output['proposals'])

    def test_boundary_expansion_keeps_patient_and_family_sections_separate(self):
        source = 'Family History:\nFather has asthma.\nOn Examination\nChest clear.'
        lines = updates.source_segments(source)
        output = updates.resolve_ranges({'proposals': [{'destination': 'Concerns', 'start_id': 4, 'end_id': 4}]}, lines, source)
        self.assertEqual(['On Examination\nChest clear.'], [row['evidence'] for row in output['proposals']])
        source = 'Mother had asthma\nChest clear.'
        output = updates.resolve_ranges({'proposals': [{'destination': 'Concerns', 'start_id': 2, 'end_id': 2}]},
                                        updates.source_segments(source), source)
        self.assertEqual([], output['proposals'])

    def test_expanded_heading_retains_adjacent_note_date(self):
        source = '=== Source note 7 | 2026-01-03 ===\nClinician Leading Ward Round\nDr. Example\n\nFinding.'
        excerpt = 'Dr. Example\n\nFinding.'
        self.assertEqual(source, updates.preserve_passage_boundaries(excerpt, source, source.index(excerpt)))

    def test_suffix_expansion_stops_before_later_family_heading(self):
        source = 'GP follow-up\nBring summary\nFamily History:\nAsthma.'
        result = updates.resolve_ranges({'proposals': [{'destination': 'Tickler', 'start_id': 1, 'end_id': 1}]},
                                        updates.source_segments(source), source)
        self.assertEqual(['GP follow-up\nBring summary'], [r['evidence'] for r in result['proposals']])

    def test_candidate_limit_never_silently_truncates(self):
        rows = [{'kind': 'history', 'evidence': f'Fact {i}.'} for i in range(updates.MAX_PROPOSALS)]
        with self.assertRaisesRegex(ValueError, 'Combined proposal count'):
            updates.merge_candidates(copy.deepcopy(rows), [{'kind': 'history', 'evidence': 'One more.'}])


if __name__ == '__main__':
    unittest.main()
