# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import unittest
import uuid
import threading
from http.server import HTTPServer
from urllib.request import Request, ProxyHandler, build_opener
from urllib.error import HTTPError
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import chart_updates as updates
import openrouter_agent as agent
import pipeline


class ChartUpdatesTest(unittest.TestCase):
    def setUp(self):
        self.source = 'History: suspected asthma.\nPlan: review in two weeks if symptoms persist.'
        self.notes = [('TEST', '2026-09-28', self.source)]
        self.config = dict(agent.DEFAULTS)
        self.request = {'contract_version': 1, 'request_id': str(uuid.uuid4()),
                        'workflow': 'chart-update-proposals', 'data_classification': 'clinical-document',
                        'instructions': updates.PROMPT, 'output_schema': updates.SCHEMA,
                        'sources': [{'id': 'document', 'title': 'Document', 'text': self.source}]}

    def test_preserves_uncertainty_condition_and_timing_exactly(self):
        output = {'proposals': [{'kind': 'history', 'evidence': self.source.split('\n')[0]},
                                {'kind': 'tickler', 'evidence': self.source.split('\n')[1]}]}
        requests = []
        def complete(payload):
            requests.append(payload)
            return ({'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1},
                                   {'kind': 'tickler', 'start_id': 2, 'end_id': 2}]}
                    if len(requests) <= 2 else {'decisions': [{'id': i, 'keep': True, 'reason': 'Eligible'} for i in ['1', '2']]})
        response = updates.run(self.config, self.request, self.notes, complete)
        self.assertEqual(output['proposals'], response['output']['proposals'])
        self.assertEqual(self.request['request_id'], response['request_id'])
        self.assertEqual({'1': self.source.splitlines(keepends=True)[0],
                          '2': self.source.splitlines(keepends=True)[1]},
                         json.loads(requests[0]['messages'][1]['content'])['segments'])
        self.assertEqual(3, len(requests))
        self.assertFalse(requests[0]['provider']['allow_fallbacks'])
        self.assertTrue(requests[0]['provider']['zdr'])

    def test_copies_multiline_evidence_with_source_typos_and_crlf(self):
        source = 'History\r\n- Suspected ashtma\r\n  if investigations confirm.\r\n\r\nOther context'
        raw = {'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 3}]}
        result = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual(source.split('\r\n\r\n')[0], result['proposals'][0]['evidence'])

    def test_rejects_single_item_mixed_medication_and_investigation_plans(self):
        for source in ('Start drug X and arrange GP review.', 'Review in four weeks; order histology.',
                       '- Increase medication and follow up in clinic.'):
            self.assertEqual([], updates.followup_items(source))

    def test_excludes_mixed_action_from_multibullet_followups(self):
        source = 'Plan\n- Increase medication and arrange clinic follow-up\n- Arrange GP review'
        self.assertEqual(['- Arrange GP review'], updates.followup_items(source))

    def test_family_scope_ends_at_patient_heading_without_blank_line(self):
        for heading in ('Family History:', 'Family Hx:', 'FH:', 'FHx:', 'F/H:'):
            source = heading + '\n- HTN\nPast Medical History:\n- Asthma'
            parts = updates.source_segments(source)
            result = updates.resolve_ranges({'proposals': [
                {'kind': 'history', 'start_id': 2, 'end_id': 2},
                {'kind': 'history', 'start_id': 4, 'end_id': 4}]}, parts, source)
            self.assertEqual([{'kind': 'history', 'evidence': 'Past Medical History:\n- Asthma'}], result['proposals'])

    def test_rejects_expanded_lists_exceeding_budget_without_partial_output(self):
        source = 'Plan\n' + '\n'.join(f'- Arrange clinic {i} follow-up.' for i in range(updates.MAX_PROPOSALS + 5))
        parts = updates.source_segments(source)
        with self.assertRaisesRegex(ValueError, 'Expanded proposal count'):
            updates.resolve_ranges({'proposals': [
                {'kind': 'tickler', 'start_id': 1, 'end_id': len(parts)}]}, parts, source)

    def test_followup_can_be_selected_without_neighbouring_prescription(self):
        # Use an unambiguous sentence ending; a trailing initial requires its context.
        source = 'Prescribe paracetamol. Advised to follow up with GP in 7 days.\r\n'
        segments = updates.source_segments(source)
        self.assertEqual(source, ''.join(segments.values()))
        output = updates.resolve_ranges({'proposals': [
            {'kind': 'tickler', 'start_id': 2, 'end_id': 2}]}, segments, source)
        self.assertEqual('Advised to follow up with GP in 7 days.', output['proposals'][0]['evidence'])

    def test_segmentation_preserves_every_complete_synthetic_note(self):
        for _, _, source in agent.committed_notes()[0]:
            self.assertEqual(source, ''.join(updates.source_segments(source).values()))

    def test_numbered_plan_does_not_attach_next_marker_to_followup(self):
        source = '1. Discharge today. 2. No new meds. 3. Arrange respiratory OP follow-up. 4. Monitor symptoms.\r\n'
        parts = updates.source_segments(source)
        self.assertEqual(source, ''.join(parts.values()))
        self.assertEqual('3. Arrange respiratory OP follow-up.', parts[3].strip())
        self.assertEqual('4. Monitor symptoms.', parts[4].strip())

    def test_mixed_plan_yields_only_independent_followup_bullets(self):
        source = 'Plan\n- Start inpatient physio\n- Schedule ortho outpatient follow-up in six weeks\n- Send tissue to histology'
        parts = updates.source_segments(source)
        output = updates.resolve_ranges({'proposals': [
            {'kind': 'tickler', 'start_id': 1, 'end_id': len(parts)}]}, parts, source)
        self.assertEqual([{'kind': 'tickler', 'evidence': '- Schedule ortho outpatient follow-up in six weeks'}],
                         output['proposals'])

    def test_qualified_or_dependent_plan_is_not_split_without_context(self):
        for source in ('Plan only if symptoms resolve\n- Stop medication\n- Arrange clinic follow-up',
                       'Plan\n- Await results\n- Then arrange GP review',
                       'Plan\n- If symptoms persist, continue treatment\n- Arrange clinic follow-up'):
            self.assertEqual([], updates.followup_items(source))
        conditional = '- Arrange GP review\n  if symptoms persist'
        self.assertEqual([conditional], updates.followup_items(conditional))

    def test_inline_numbered_plan_does_not_bundle_medication_with_followup(self):
        source = 'Plan\n1. Start drug X 2.5 mg. 2. Arrange outpatient physio follow-up. 3. Advise GP follow-up 6 weeks post-surgery.'
        self.assertEqual(['2. Arrange outpatient physio follow-up.',
                          '3. Advise GP follow-up 6 weeks post-surgery.'], updates.followup_items(source))

    def test_segmentation_keeps_titles_initials_and_decimal_measurements(self):
        source = 'Dr. A. Smith recorded sodium 132.5 mmol/L. Arrange GP review.\n'
        self.assertEqual([ 'Dr. A. Smith recorded sodium 132.5 mmol/L.', ' Arrange GP review.\n'],
                         list(updates.source_segments(source).values()))

    def test_rejects_unknown_reversed_noninteger_and_extra_reference_fields(self):
        for change in ({'start_id': 0}, {'end_id': 99}, {'start_id': 2, 'end_id': 1},
                       {'start_id': True}, {'end_id': '2'}, {'evidence': 'invented'}):
            row = dict(kind='history', start_id=1, end_id=1)
            row.update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                updates.resolve_ranges({'proposals': [row]}, updates.source_segments(self.source), self.source)

    def test_family_section_cannot_become_patient_history_without_heading(self):
        source = 'Family History\n- HTN\n\nPast Medical History\n- Asthma'
        raw = {'proposals': [{'kind': 'history', 'start_id': 2, 'end_id': 2},
                             {'kind': 'history', 'start_id': 4, 'end_id': 5}]}
        result = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual([{'kind': 'history', 'evidence': 'Past Medical History\n- Asthma'}],
                         result['proposals'])

    def test_selected_history_bullets_keep_the_adjacent_historical_heading(self):
        source = 'Past Medical History\n- HTN\n- Mild osteoarthritis'
        raw = {'proposals': [{'kind': 'history', 'start_id': 2, 'end_id': 3}]}
        output = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual(['Past Medical History\n- HTN', '- Mild osteoarthritis'],
                         [row['evidence'] for row in output['proposals']])

    def test_repeated_history_is_deduplicated_without_rewriting_first_quote(self):
        source = 'History: suspected asthma.\n\nHistory:  suspected asthma.'
        raw = {'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1},
                             {'kind': 'history', 'start_id': 3, 'end_id': 3}]}
        result = updates.resolve_ranges(raw, updates.source_segments(source), source)
        self.assertEqual([{'kind': 'history', 'evidence': 'History: suspected asthma.'}],
                         result['proposals'])

    def test_review_can_remove_stale_candidate_and_sees_later_source(self):
        source = self.source + '\nLater: follow-up completed.'
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = source
        replies = [{'proposals': [{'kind': 'tickler', 'start_id': 2, 'end_id': 2}]}, {'proposals': []}, {'decisions': [{'id': '1', 'keep': False, 'reason': 'Completed later'}]}]
        seen = []
        def complete(payload):
            seen.append(payload)
            return replies.pop(0)
        result = updates.run(self.config, request, [('TEST', '', source)], complete)
        self.assertEqual([], result['output']['proposals'])
        self.assertIn('Later: follow-up completed.', seen[2]['messages'][1]['content'])

    def test_review_must_be_valid_before_any_proposals_are_released(self):
        valid = {'id': '1', 'keep': True, 'reason': 'Eligible'}
        for review in ({'decisions': [dict(valid, id='unknown')]}, {'decisions': [valid, valid]},
                       {'decisions': [dict(valid, keep='yes')]}, {'decisions': []},
                       {'decisions': [valid], 'extra': True}):
            replies = [{'proposals': [{'kind': 'history', 'start_id': 1, 'end_id': 1}]}, {'proposals': []}, review]
            with self.subTest(review=review), self.assertRaises(ValueError):
                updates.run(self.config, self.request, self.notes, lambda _: replies.pop(0))

    def test_empty_candidates_need_no_review_call(self):
        with patch('chart_updates.distill.payload', wraps=updates.distill.payload) as payload:
            result = updates.run(self.config, self.request, self.notes, lambda _: {'proposals': []})
            self.assertEqual([], result['output']['proposals'])
            self.assertEqual(2, payload.call_count)

    def test_rejects_disclosure_before_network_call(self):
        for source in ('real patient data', self.source[:20], self.source + '\nextra metadata'):
            with self.subTest(source=source):
                request = copy.deepcopy(self.request)
                request['sources'][0]['text'] = source
                with self.assertRaises(ValueError):
                    updates.run(self.config, request, self.notes, lambda _: self.fail('Unexpected network call'))

    def test_rejects_extra_chart_data_modified_contract_and_oversized_requests(self):
        for change in ({'chart': 'private chart'}, {'instructions': 'different'}, {'output_schema': {}},
                       {'workflow': 'apply'}, {'contract_version': True}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                updates.run(self.config, dict(self.request, **change), self.notes, lambda _: self.fail('Network call'))
        with self.assertRaises(ValueError):
            updates.run(dict(self.config, request_bytes=10), self.request, self.notes, lambda _: self.fail('Network call'))

    def test_rejects_fabricated_evidence_write_fields_and_unapproved_kinds(self):
        for row in ({'kind': 'history', 'evidence': 'Confirmed asthma.'},
                    {'kind': 'prescription', 'evidence': self.source},
                    {'kind': 'tickler', 'evidence': self.source, 'due_date': '2026-10-12'},
                    {'kind': 'history', 'evidence': ''}):
            with self.subTest(row=row), self.assertRaises(ValueError):
                updates.validate_output({'proposals': [row]}, self.source)

    def test_allows_no_proposals_and_rejects_duplicate_passages(self):
        updates.validate_output({'proposals': []}, self.source)
        row = {'kind': 'history', 'evidence': self.source}
        with self.assertRaises(ValueError):
            updates.validate_output({'proposals': [row, dict(row, kind='tickler')]}, self.source)

    def test_bounds_count_and_java_utf16_length(self):
        text = '\U0001f600' * 1001
        with self.assertRaises(ValueError):
            updates.validate_output({'proposals': [{'kind': 'history', 'evidence': text}]}, text)
        with self.assertRaises(ValueError):
            updates.validate_output({'proposals': [{'kind': 'history', 'evidence': self.source}] * 21}, self.source)

    def test_full_chart_destinations_preserve_negatives_and_family_relationships(self):
        source = 'Social History\n- Non-smoker\n\nFamily History\n- Father: MI at 67\n\nAllergies\nNone'
        lines = updates.source_segments(source)
        raw = {'proposals': [
            {'kind': 'history', 'destination': 'SocHistory', 'start_id': 1, 'end_id': 2},
            {'kind': 'history', 'destination': 'FamHistory', 'start_id': 4, 'end_id': 5},
            {'kind': 'review', 'destination': 'Allergies', 'start_id': 7, 'end_id': 8}]}
        output = updates.resolve_ranges(raw, lines, source)
        self.assertEqual(3, len(output['proposals']))
        self.assertEqual({'kind': 'history', 'destination': 'SocHistory', 'evidence': 'Social History\n- Non-smoker'}, output['proposals'][0])
        self.assertEqual('Allergies\nNone', output['proposals'][2]['evidence'])
        self.assertEqual('Family History\n- Father: MI at 67', output['proposals'][1]['evidence'])

    def test_native_information_cannot_be_misrouted_as_a_chart_write(self):
        for kind, destination in [('review', 'Concerns'), ('history', 'Allergies'),
                                  ('tickler', 'Medications'), ('history', 'arbitrary')]:
            with self.subTest(kind=kind, destination=destination), self.assertRaises(ValueError):
                updates.validate_output({'proposals': [{'kind': kind, 'destination': destination,
                                                        'evidence': self.source}]}, self.source)

    def test_more_than_twenty_distinct_chart_facts_can_be_selected(self):
        source = '\n\n'.join(f'Social fact {i}.' for i in range(30))
        lines = updates.source_segments(source)
        rows = [{'kind': 'history', 'destination': 'SocHistory', 'start_id': i, 'end_id': i}
                for i, line in lines.items() if line.strip()]
        output = updates.resolve_ranges({'proposals': rows}, lines, source)
        self.assertEqual(30, len(output['proposals']))

    def test_social_support_relatives_are_kept_but_family_disease_is_not_patient_disease(self):
        source = 'Social History\n- Lives with daughter\n\nFamily History\n- Father: asthma'
        lines = updates.source_segments(source)
        for destination in ('MedHistory', 'Allergies', 'Medications', 'SocHistory'):
            raw = {'proposals': [{'destination': 'SocHistory', 'start_id': 2, 'end_id': 2},
                                 {'destination': destination, 'start_id': 5, 'end_id': 5}]}
            result = updates.resolve_ranges(raw, lines, source)
            self.assertEqual(['Social History\n- Lives with daughter'], [r['evidence'] for r in result['proposals']])

    def test_flat_lists_split_but_shared_conditions_and_nested_lists_stay_intact(self):
        self.assertEqual(['Social History\n- Retired', '- Lives alone'],
                         updates.independent_items('Social History\n- Retired\n- Lives alone'))
        for source in ('Plan\n- If fever, call GP\n- Rest',
                       'Plan\n- Take medicine\n  - With food\n- Call GP',
                       'Medications before admission\n- Drug A\n- Drug B'):
            self.assertEqual([source], updates.independent_items(source))

    def test_fall_prevention_and_non_medication_care_use_chart_sections(self):
        self.assertEqual('RiskFactors', updates.route_excerpt({'destination': 'Preventions'}, 'Falls prevention advice')['destination'])
        self.assertEqual('Reminders', updates.route_excerpt({'destination': 'Medications'}, 'Arrange home safety review')['destination'])
        self.assertEqual('Medications', updates.route_excerpt({'destination': 'Medications'}, 'Repeat serum Na; Drugname 50mg daily')['destination'])
        self.assertEqual('Medications', updates.route_excerpt({'destination': 'Medications'}, 'Monitor IV medication')['destination'])
        self.assertEqual('Preventions', updates.route_excerpt({'destination': 'Preventions'}, 'Vaccination and falls prevention')['destination'])

    def test_oversized_inventory_fallback_does_not_discard_valid_selected_fact(self):
        source = 'History: asthma.\n\nSocial History\n' + 'long narrative ' * 200 + '\nAllergies\nNone'
        inventory = updates.section_inventory(updates.source_segments(source))
        self.assertEqual(['Allergies'], [r['destination'] for r in inventory])
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = source
        def complete(payload):
            if 'segments' in json.loads(payload['messages'][1]['content']):
                return {'proposals': [{'destination': 'MedHistory', 'start_id': 1, 'end_id': 1}]}
            return {'decisions': [{'id': key, 'keep': True, 'reason': 'Eligible'} for key in
                                  json.loads(payload['messages'][1]['content'])['candidates']]}
        result = updates.run(self.config, request, [('TEST', '2026-09-28', source)], complete)
        self.assertEqual(['History: asthma.', 'Allergies\nNone'], [r['evidence'] for r in result['output']['proposals']])

    def test_adjacent_negation_and_note_date_remain_with_selected_facts(self):
        source = '=== Source note 1 | 2026-01-02 ===\nPatient details.\n\nMedications\n- No regular medications\n- IV paracetamol during ED stay'
        result = updates.resolve_ranges({'proposals': [
            {'destination': 'Demographics', 'start_id': 2, 'end_id': 2},
            {'destination': 'Medications', 'start_id': 6, 'end_id': 6}]}, updates.source_segments(source), source)
        self.assertEqual('=== Source note 1 | 2026-01-02 ===\nPatient details.', result['proposals'][0]['evidence'])
        self.assertEqual('Medications\n- No regular medications\n- IV paracetamol during ED stay', result['proposals'][1]['evidence'])

    def test_followup_after_conditional_discharge_does_not_lose_qualification(self):
        source = 'Plan\n- Discharge if neurologically stable\n- Arrange outpatient follow-up'
        result = updates.resolve_ranges({'proposals': [{'kind': 'tickler', 'start_id': 3, 'end_id': 3}]}, updates.source_segments(source), source)
        self.assertEqual([], result['proposals'])

    def test_punctuated_conditions_remain_attached_before_tickler_filtering(self):
        for punctuation in ('.', '!', '?'):
            source = 'Plan\n- Discharge if neurologically stable' + punctuation + '\n- Arrange outpatient follow-up'
            result = updates.resolve_ranges({'proposals': [{'kind': 'tickler', 'start_id': 3, 'end_id': 3}]}, updates.source_segments(source), source)
            self.assertEqual([], result['proposals'])

    def test_inline_inventory_headings_preserve_patient_and_family_boundaries(self):
        source = 'Family History:\nFather: asthma.\nImpression: Possible pneumonia.\nPlan: Review tomorrow.'
        lines = updates.source_segments(source)
        result = updates.resolve_ranges({'proposals': updates.section_inventory(lines)}, lines, source)
        self.assertEqual([('FamHistory', 'Family History:\nFather: asthma.'), ('Concerns', 'Impression: Possible pneumonia.')],
                         [(row['destination'], row['evidence']) for row in result['proposals']])
        source = 'Assessment: Possible pneumonia.'
        lines = updates.source_segments(source)
        result = updates.resolve_ranges({'proposals': updates.section_inventory(lines)}, lines, source)
        self.assertEqual(source, result['proposals'][0]['evidence'])

    def test_family_scope_resets_at_other_patient_sections(self):
        for heading, destination in [('Risk Factors', 'RiskFactors'), ('Immunizations', 'Preventions'), ('Demographics', 'Demographics')]:
            source = 'Family History:\nFather: asthma.\n' + heading + ':\nPatient fact.'
            lines = updates.source_segments(source)
            result = updates.resolve_ranges({'proposals': [{'destination': destination, 'start_id': 4, 'end_id': 4}]}, lines, source)
            self.assertEqual(destination, result['proposals'][0]['destination'])
            self.assertNotIn('Father', result['proposals'][0]['evidence'])
            inventory = updates.section_inventory(lines)
            self.assertEqual(2, inventory[0]['end_id'])

    def test_new_explicit_sections_survive_empty_model_selection(self):
        for heading, destination in [('Risk Factors', 'RiskFactors'), ('Immunizations', 'Preventions'),
                                     ('Immunisations', 'Preventions'), ('Screening', 'Preventions'),
                                     ('Preventions', 'Preventions'), ('Demographics', 'Demographics')]:
            source = heading + ':\nPatient fact.'
            request = copy.deepcopy(self.request)
            request['sources'][0]['text'] = source
            def complete(payload):
                content = json.loads(payload['messages'][1]['content'])
                if 'segments' in content:
                    return {'proposals': []}
                self.assertEqual(source, content['source'])
                return {'decisions': [{'id': key, 'keep': True, 'reason': 'Eligible'} for key in content['candidates']]}
            result = updates.run(self.config, request, [('TEST', '2026-09-28', source)], complete)
            kind = 'history' if destination == 'RiskFactors' else 'review'
            expected = {'kind': kind, 'destination': destination, 'evidence': source}
            self.assertEqual([expected], result['output']['proposals'])
            self.assertEqual([expected], updates.remove_heading_duplicates([dict(expected, evidence='Patient fact.'), expected]))

    def test_inline_heading_resets_preceding_unrelated_negation(self):
        source = 'No symptoms.\nImpression: Possible pneumonia.'
        result = updates.resolve_ranges({'proposals': [{'destination': 'Concerns', 'start_id': 2, 'end_id': 2}]}, updates.source_segments(source), source)
        self.assertEqual('Impression: Possible pneumonia.', result['proposals'][0]['evidence'])

    def test_legacy_medication_selector_routes_to_dedicated_review(self):
        source = 'Stopped paracetamol.'
        result = updates.resolve_ranges({'proposals': [{'destination': 'OMeds', 'start_id': 1, 'end_id': 1}]}, updates.source_segments(source), source)
        self.assertEqual([{'kind': 'review', 'destination': 'Medications', 'evidence': source}], result['proposals'])

    def test_advertised_schema_destinations_agree_with_host_kinds(self):
        found = set()
        for variant in updates.SCHEMA['properties']['proposals']['items']['anyOf']:
            kind = variant['properties']['kind']['enum'][0]
            destinations = set(variant['properties']['destination']['enum'])
            expected = {'history': {'', *updates.SECTIONS}, 'tickler': {''}, 'review': set(updates.NATIVE)}[kind]
            self.assertEqual(expected, destinations)
            found.update(destinations)
        self.assertEqual({'', *updates.SECTIONS, *updates.NATIVE}, found)

    def test_inventory_stops_at_referral_and_system_review(self):
        source = 'Family History\nNil significant.\nSystems Review\nNo seizures.\nImpression\nSuspected asthma.\nReferral\nDr. Example'
        lines = updates.source_segments(source)
        output = updates.resolve_ranges({'proposals': updates.section_inventory(lines)}, lines, source)
        self.assertEqual(['Family History\nNil significant.', 'Impression\nSuspected asthma.'], [r['evidence'] for r in output['proposals']])

    def test_heading_duplicates_are_removed_without_paraphrase_or_cross_section_matching(self):
        rows = [{'kind': 'history', 'destination': 'Concerns', 'evidence': 'Possible asthma.'},
                {'kind': 'history', 'destination': 'Concerns', 'evidence': 'Impression\nPossible asthma.'},
                {'kind': 'history', 'destination': 'FamHistory', 'evidence': 'Possible asthma.'},
                {'kind': 'history', 'destination': 'Concerns', 'evidence': 'Suspected asthma.'}]
        self.assertEqual(rows[1:], updates.remove_heading_duplicates(rows))

    def test_review_batches_keep_complete_source_and_require_all_decisions(self):
        source = 'Source document. ' * 40
        candidates = {str(i): {'kind': 'history', 'evidence': 'fact ' * 50 + str(i)} for i in range(20)}
        config = dict(self.config, request_bytes=5500)
        batches = updates.review_batches(config, source, candidates)
        self.assertGreater(len(batches), 1)
        seen = {}
        for batch, payload in batches:
            self.assertLessEqual(len(json.dumps(payload).encode()), config['request_bytes'])
            self.assertEqual(source, json.loads(payload['messages'][1]['content'])['source'])
            seen.update(batch)
            with self.assertRaisesRegex(ValueError, 'Incomplete'):
                updates.review_decisions({'decisions': []}, batch)
        self.assertEqual(candidates, seen)
        with self.assertRaisesRegex(ValueError, 'budget'):
            updates.review_batches(dict(config, request_bytes=10), source, candidates)

    def test_gateway_route_rejects_unknown_source_before_transport(self):
        gateway = agent.Gateway(self.config, transport=lambda *_: self.fail('Network call'))
        source = gateway.allowed.notes[0][2]
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = source
        output = {'proposals': []}
        server = HTTPServer(('127.0.0.1', 0), agent.handler_for(gateway))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f'http://127.0.0.1:{server.server_port}{updates.PATH}'
            opener = build_opener(ProxyHandler({}))
            with patch.object(gateway, 'complete', return_value=output) as complete:
                with opener.open(Request(url, json.dumps(request).encode(),
                                         {'Content-Type': 'application/json'}), timeout=5) as response:
                    self.assertEqual(output['proposals'], json.load(response)['output']['proposals'])
                request['sources'][0]['text'] = 'Unapproved clinical text'
                with self.assertRaises(HTTPError) as error:
                    opener.open(Request(url, json.dumps(request).encode()), timeout=5)
                self.assertEqual(400, error.exception.code)
                self.assertEqual(2, complete.call_count)
        finally:
            server.shutdown()
            thread.join()
            server.server_close()

    def test_gateway_handles_output_limit_without_accepting_partial_proposals(self):
        gateway = agent.Gateway(self.config, transport=lambda *_: self.fail('Network call'))
        request = copy.deepcopy(self.request)
        request['sources'][0]['text'] = gateway.allowed.notes[0][2]
        with patch.object(gateway, 'complete', side_effect=pipeline.OutputLimitError()):
            with self.assertRaises(agent.UpstreamError):
                gateway.run_chart_updates(request)


if __name__ == '__main__':
    unittest.main()
