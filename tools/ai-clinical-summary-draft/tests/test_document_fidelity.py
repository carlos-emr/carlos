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


if __name__ == '__main__':
    unittest.main()
