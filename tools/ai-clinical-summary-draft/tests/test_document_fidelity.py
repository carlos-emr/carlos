# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import document_fidelity as fidelity
import document_summary as document


class DocumentFidelityTest(unittest.TestCase):
    def render(self, source, refs=None):
        context = fidelity.prepare(source)
        result = fidelity.resolve({'selected_ids': refs or ['1']}, context)
        document.validate_output(result, source)
        for point in result['points']:
            self.assertEqual(point['evidence'][0].replace('\r\n', '\n').replace('\r', '\n').strip(), point['text'])
            self.assertIn(point['evidence'][0], source)
        return result, context

    def test_plans_pending_results_and_allergies_survive_model_omission(self):
        source = ('Presenting Complaint\nAcute confusion\n\nAllergies\nPenicillin\n\n'
                  'Investigations\nAwaiting urine culture.\n\nImpression\nLikely infection.\n\n'
                  'Plan\nStart antibiotics. Commence fluids. Liaise with geriatrics.')
        result, _ = self.render(source)
        text = '\n'.join(p['text'] for p in result['points'])
        for phrase in ('Penicillin', 'Awaiting urine culture', 'Start antibiotics', 'Commence fluids'):
            self.assertIn(phrase, text)
        self.assertEqual('Impression\nLikely infection.\n\nPlan\nStart antibiotics. Commence fluids. '
                         'Liaise with geriatrics.', result['overview'])
        self.assertNotIn('started', result['overview'])
        self.assertNotIn('transfer', result['overview'])
        self.assertNotIn('elderly', result['overview'])

    def test_continuation_chunks_of_required_sections_cannot_be_omitted(self):
        plan = 'Plan\n' + 'Continue current care. ' * 90 + 'Repeat lactate in four hours.'
        result, context = self.render('Presenting Complaint\nSepsis\n\n' + plan)
        excerpts = [p['evidence'][0] for p in result['points']][1:]
        self.assertGreater(len(excerpts), 2)
        self.assertEqual(plan, ''.join(excerpts))
        self.assertEqual(list(context['passages'])[1:], context['mandatory_ids'])

    def test_selecting_a_continuation_retains_the_entire_source_paragraph(self):
        source = 'History\n' + 'Symptoms assessed. ' * 60 + 'No chest pain or vomiting.'
        result, context = self.render(source, ['2'])
        self.assertGreater(len(context['passages']), 1)
        self.assertEqual(source, ''.join(point['evidence'][0] for point in result['points']))

    def test_inline_allergy_and_pending_negation_keep_full_context(self):
        source = ('Clinical details:\n- Allergies: nuts\n- Dose: 1 mg\n\n'
                  'Test Results\nNone pending.\n\nPlan\nPossible discharge after review.')
        result, _ = self.render(source)
        text = '\n'.join(p['text'] for p in result['points'])
        self.assertIn('Allergies: nuts', text)
        self.assertIn('None pending.', text)
        self.assertIn('Possible discharge after review.', text)

    def test_referral_status_is_retained_alongside_a_plan(self):
        source = ('Impression\nPneumonia\n\nReferral\nAccepted by Respiratory. Transferred to Ward B.\n\n'
                  'Plan\nAdmit for monitoring.')
        result, _ = self.render(source)
        self.assertIn('Referral\nAccepted by Respiratory. Transferred to Ward B.',
                      [point['text'] for point in result['points']])
        self.assertIn('Plan\nAdmit for monitoring.', result['overview'])

    def test_contradictory_selected_excerpts_and_encoding_are_not_rewritten(self):
        source = 'History\nMild nausea.\n\nReview\nNo nausea.\n\nObservations\nTemp 36.7Â°C'
        result, _ = self.render(source, ['1', '2', '3'])
        self.assertEqual(['History\nMild nausea.', 'Review\nNo nausea.', 'Observations\nTemp 36.7Â°C'],
                         [p['text'] for p in result['points']])

    def test_generated_text_invalid_ids_and_duplicate_ids_are_rejected(self):
        context = fidelity.prepare('Diagnosis\nMigraine')
        for refs in (None, '1', [], [1], [True], [{}], ['unknown'], ['1', '1'], ['1'] * 51):
            with self.subTest(refs=refs), self.assertRaises(ValueError):
                fidelity.resolve({'selected_ids': refs}, context)
        with self.assertRaises(ValueError):
            fidelity.resolve({'selected_ids': ['1'], 'overview': 'Invented claim'}, context)

    def test_rendering_restores_document_order_and_keeps_original_crlf_evidence(self):
        source = 'History\r\nHeadache\r\n\r\nPlan\r\nReview tomorrow.'
        result, _ = self.render(source, ['2', '1'])
        self.assertEqual('History\nHeadache', result['points'][0]['text'])
        self.assertEqual('History\r\nHeadache', result['points'][0]['evidence'][0])

    def test_lone_carriage_return_cannot_join_separate_values(self):
        source = 'Dose\n1\r2 mg'
        result, _ = self.render(source)
        self.assertEqual('Dose\n1\n2 mg', result['points'][0]['text'])
        self.assertNotIn('12 mg', result['points'][0]['text'])

    def test_similar_but_different_source_values_are_not_silently_deduplicated(self):
        source = 'Dose\n1-2 tablets\n\nDose\n1/2 tablets'
        output = fidelity.resolve({'selected_ids': ['1', '2']}, fidelity.prepare(source))
        self.assertEqual(2, len(output['points']))
        # The existing host rejects normalized duplicates; never hide that by dropping a dose.
        with self.assertRaises(ValueError):
            document.validate_output(output, source)

    def test_combined_mandatory_and_selected_points_are_bounded(self):
        source = '\n\n'.join(f'Plan\nAction {i}' for i in range(51))
        with self.assertRaises(ValueError):
            fidelity.resolve({'selected_ids': ['1']}, fidelity.prepare(source))


class BriefDocumentTest(unittest.TestCase):
    def test_selected_bullet_keeps_heading_and_conditional_continuation(self):
        source = ('On Examination\r\n- Chest clear.\r\n- Abdomen tender; review\r\n'
                  '  if symptoms persist.\r\n\r\nPlan\r\nObserve overnight.')
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['2']}, context)
        document.validate_output(output, source)
        self.assertEqual('On Examination\n- Abdomen tender; review\n  if symptoms persist.',
                         output['points'][0]['text'])
        self.assertEqual(['On Examination\r\n', '- Abdomen tender; review\r\n  if symptoms persist.'],
                         output['points'][0]['evidence'])
        self.assertNotIn('Chest clear', str(output))

    def test_pending_bullet_is_required_but_routine_result_is_selectable(self):
        source = ('Investigations\n- ECG: Normal sinus rhythm.\n- Culture: Awaiting result.\n\n'
                  'Impression\nPossible infection.\n\nPlan\nReview when culture available.')
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['3']}, context)
        document.validate_output(output, source)
        self.assertNotIn('Normal sinus', str(output))
        self.assertIn('Culture: Awaiting result.', str(output))
        self.assertIn('Possible infection.', output['overview'])

    def test_nested_bullets_keep_their_parent_qualifier(self):
        source = 'On Examination\n- No evidence of:\n  - pneumonia\n  - oedema\n- Abdomen tender.'
        context = fidelity.prepare(source)
        self.assertEqual(2, len(context['passages']))
        output = fidelity.resolve({'selected_ids': ['1']}, context)
        self.assertEqual('On Examination\n- No evidence of:\n  - pneumonia\n  - oedema',
                         output['points'][0]['text'])

    def test_qualifiers_unknown_headings_and_dependent_lists_remain_whole(self):
        for source in ('On Examination\nNo evidence of:\n- pneumonia\n- oedema',
                       'Investigations\n- Culture negative.\n- However, repeat is pending.',
                       'Unrecognized heading\n- First item\n- Second item',
                       'Plan\n- Possible discharge\n- If review is reassuring.'):
            with self.subTest(source=source):
                context = fidelity.prepare(source)
                self.assertEqual([source], list(context['passages'].values()))

    def test_long_bullet_keeps_all_continuations_and_exact_evidence(self):
        bullet = '- Symptoms noted. ' + 'More history recorded. ' * 90 + 'No vomiting.'
        source = 'History of Presenting Complaint\n' + bullet + '\n- No trauma.'
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['2']}, context)
        document.validate_output(output, source)
        self.assertEqual(bullet + '\n', ''.join(p['evidence'][1] for p in output['points']))
        for point in output['points']:
            self.assertEqual(''.join(point['evidence']).strip(), point['text'])
            self.assertTrue(all(excerpt in source for excerpt in point['evidence']))

    def test_medications_allergies_and_disposition_cannot_be_shortened(self):
        source = ('Medications\n- Drug A 1 mg daily\n- Drug B 2 mg if needed\n\n'
                  'Allergies\n- Nuts\n- Penicillin\n\nReferral\nAccepted; awaiting bed.\n\n'
                  'Plan\nTransfer if bed available.')
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['4']}, context)
        document.validate_output(output, source)
        self.assertEqual(4, len(output['points']))
        self.assertIn('Accepted; awaiting bed.', str(output))
        self.assertIn('Transfer if bed available.', output['overview'])

    def test_observations_and_consciousness_scores_survive_selection_omission(self):
        source = ('On Examination\n- Alert but confused.\n- GCS 13/15 (E4, V4, M5). No focal deficit.\n\n'
                  'Observations\nHR 112\nBP 96/60\n\nImpression\nDelirium.')
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['4']}, context)
        document.validate_output(output, source)
        self.assertIn('GCS 13/15 (E4, V4, M5). No focal deficit.', str(output))
        self.assertIn('BP 96/60', str(output))

    def test_selected_bullets_share_heading_with_separate_verbatim_evidence(self):
        source = 'Investigations\n- First finding.\n- Routine result.\n- Third finding.'
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['1', '3']}, context)
        document.validate_output(output, source)
        self.assertEqual('Investigations\n- First finding.\n- Third finding.', output['points'][0]['text'])
        self.assertEqual(['Investigations\n', '- First finding.\n', '- Third finding.'],
                         output['points'][0]['evidence'])
        self.assertNotIn('Routine result', str(output))
        self.assertEqual(output, fidelity.resolve({'selected_ids': ['1', '3']}, context))

    def test_equal_headings_in_different_paragraphs_do_not_merge(self):
        source = ('Investigations\n- First finding.\n- Other finding.\n\n'
                  'Investigations\n- Later finding.\n- Final finding.')
        context = fidelity.prepare(source)
        output = fidelity.resolve({'selected_ids': ['1', '3']}, context)
        self.assertEqual(2, len(output['points']))

    def test_completed_treatment_under_investigations_is_retained(self):
        source = ('Investigations\nPatient received 1L IV fluids in ED.\n\n'
                  'Plan\nContinue fluids and reassess.')
        output = fidelity.resolve({'selected_ids': ['2']}, fidelity.prepare(source))
        document.validate_output(output, source)
        self.assertIn('Patient received 1L IV fluids in ED.', str(output))

    def test_observations_inside_an_examination_bullet_are_retained(self):
        source = 'On Examination\n- Appears comfortable.\n- No rash.\nBP: 110/70\nHR: 88\n\nPlan\nReview.'
        output = fidelity.resolve({'selected_ids': ['3']}, fidelity.prepare(source))
        document.validate_output(output, source)
        self.assertIn('BP: 110/70', str(output))

    def test_flat_identity_block_retains_clinical_fields_without_identity(self):
        source = ('Patient Name: Test Person\n- Patient ID: synthetic-id\n- Gender: Female\n'
                  '- Allergies: Penicillin\n- Current Medications: Drug A 1 mg daily')
        output = fidelity.resolve({'selected_ids': ['4']}, fidelity.prepare(source))
        document.validate_output(output, source)
        self.assertEqual(2, len(output['points']))
        self.assertNotIn('Test Person', str(output))
        self.assertIn('Penicillin', str(output))
        self.assertIn('Drug A 1 mg daily', str(output))
        wrapped = source + '\n  if symptoms recur'
        self.assertEqual([wrapped], list(fidelity.prepare(wrapped)['passages'].values()))

    def test_follow_up_survives_a_misspelled_section_heading(self):
        source = ('Next Tseps:\n- Admission tomorrow.\n- Follow-up FBC on 04/01/26 before surgery.\n\n'
                  'Impression\nAnaemia.')
        output = fidelity.resolve({'selected_ids': ['2']}, fidelity.prepare(source))
        document.validate_output(output, source)
        self.assertIn('Next Tseps:', str(output))
        self.assertIn('Follow-up FBC on 04/01/26 before surgery.', str(output))

    def test_simple_negation_contrast_keeps_both_source_accounts(self):
        source = ('Presenting Complaint\nHeadache with mild nausea.\n\n'
                  'Systems Review\nNo nausea or vomiting since onset.\n\nImpression\nHeadache.')
        output = fidelity.resolve({'selected_ids': ['3']}, fidelity.prepare(source))
        document.validate_output(output, source)
        self.assertIn('mild nausea', str(output))
        self.assertIn('No nausea or vomiting', str(output))

    def test_historical_mode_retains_whole_results_section(self):
        source = 'Investigations\n- ECG normal.\n- X-ray normal.\n\nImpression\nChest pain.'
        context = fidelity.prepare(source, compact=False)
        output = fidelity.resolve({'selected_ids': ['2']}, context)
        self.assertIn('ECG normal.', str(output))
        self.assertEqual(2, len(context['passages']))


if __name__ == '__main__':
    unittest.main()
