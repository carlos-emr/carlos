# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import openrouter_agent as agent
import render_document_comparison as render


class DocumentComparisonRenderTest(unittest.TestCase):
    def test_report_escapes_generated_text_and_source_with_no_external_assets(self):
        source = agent.committed_notes()[0][0][2]
        excerpt = source[:200]
        point = {'text': 'Summary: ' + excerpt + '<script>alert(1)</script>', 'evidence': [excerpt]}
        report = {'cases': [{'case': 'test', 'text': source}], 'runs': [
            {'case': 'test', 'mode': 'facts', 'accepted': True, 'seconds': 1.2, 'calls': [],
             'output': {'overview': point['text'], 'points': [point]}}]}
        html = render.render(report, {'test': ['<img src=x onerror=alert(1)>']})
        self.assertIn('&lt;script&gt;', html)
        self.assertIn('&lt;img', html)
        self.assertNotIn('<script>', html)
        self.assertNotIn('<img ', html)
        self.assertNotIn('https://', html)
        self.assertIn('Full synthetic source', html)

    def test_non_corpus_source_cannot_be_rendered_as_synthetic(self):
        with self.assertRaisesRegex(ValueError, 'complete committed synthetic'):
            render.render({'cases': [{'case': 'test', 'text': 'Unverified private text'}], 'runs': []})


if __name__ == '__main__':
    unittest.main()
