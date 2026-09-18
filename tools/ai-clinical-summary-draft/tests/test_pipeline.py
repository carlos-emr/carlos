# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import pipeline
import run
from score_full_record import score


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.case = json.loads((ROOT.parent / "ai-clinical-summary-eval/cases/full-record.json").read_text())
        self.bundle = self.case["bundle"]
        self.prompt = (ROOT / "prompt.txt").read_text()
        self.schema = json.loads((ROOT / "output-schema.json").read_text())

    def test_coverage_and_citations_are_bounded_to_each_pass_without_mutating_schema(self):
        original = copy.deepcopy(self.schema)
        for count in (1, 75):
            sources = [{"id": f"note-{i}"} for i in range(count)]
            schema = pipeline.ollama_schema(self.schema, sources)
            coverage = schema["properties"]["coverage"]
            self.assertEqual(count, coverage["minItems"])
            self.assertEqual(count, coverage["maxItems"])
            expected = [source["id"] for source in sources]
            self.assertEqual(expected, coverage["items"]["properties"]["source_id"]["enum"])
            citations = schema["properties"]["claims"]["items"]["properties"]["source_ids"]
            self.assertEqual(expected, citations["items"]["enum"])
            self.assertEqual(count, citations["maxItems"])
        self.assertEqual(original, self.schema)

    def test_full_record_fixture_exceeds_old_limit_and_requires_every_fact(self):
        self.assertEqual(self.bundle, json.loads((ROOT / "full-record-input.json").read_text()))
        claims = [{"id": row["id"], "text": row["text"], "source_ids": row["source_ids"]}
                  for row in self.bundle["fact_ledger"]]
        generated = {"claims": claims, "sections": [{"id": "clinical_overview", "title": "Clinical overview",
                                                     "claim_ids": [claim["id"] for claim in claims]}],
                     "coverage": [{"source_id": source["id"], "status": "cited", "reason": source["id"]}
                                  for source in self.bundle["sources"]]}
        artifact = run.build_artifact(self.bundle, generated, "golden fixture", "golden", "2026-09-14T00:00:00Z")
        self.assertGreater(len(claims), 20)
        result = score(artifact, self.case)
        self.assertTrue(result["metrics"]["full_record_pass"], result)
        artifact["claims"].pop(2)  # One noncritical historical fact must still fail completeness.
        artifact["sections"][0]["claim_ids"].pop(2)
        self.assertFalse(score(artifact, self.case)["metrics"]["full_record_pass"])

    def test_all_characters_and_final_negation_survive_long_source_partitioning(self):
        source = copy.deepcopy(self.bundle["sources"][0])
        source["text"] = "\n".join(f"Observation {i}: no recurrent dizziness." for i in range(2000)) + "\nNo penicillin allergy."
        parts = pipeline.plan([source], self.prompt, self.schema)
        covered = set()
        for part in parts:
            text = part[0]["text"]
            start = source["text"].index(text)
            covered.update(range(start, start + len(text)))
        self.assertEqual(len(source["text"]), len(covered))
        self.assertTrue(parts[-1][0]["text"].endswith("No penicillin allergy."))

    def test_adding_a_later_note_preserves_earlier_batches(self):
        sources = [dict(self.bundle["sources"][0], id=f"note-{i}", text=f"Observation {i}: " + "Clinical detail. " * 40)
                   for i in range(60)]
        before = pipeline.plan(sources, self.prompt, self.schema)
        after = pipeline.plan(sources + [dict(sources[-1], id="note-60")], self.prompt, self.schema)
        self.assertEqual(before[:-1], after[:len(before)-1])

    def test_configurable_context_preserves_all_sources_at_both_budgets(self):
        sources = [dict(self.bundle['sources'][0], id=f'note-{i}', text=f'Finding {i}: ' + 'Detail. ' * 100)
                   for i in range(80)]
        for budget in (10000, 50000):
            batches = pipeline.plan(sources, self.prompt, self.schema, budget)
            self.assertEqual(sources, [source for batch in batches for source in batch])
            self.assertGreater(len(batches), 1)
        self.assertGreater(len(pipeline.plan(sources, self.prompt, self.schema, 10000)),
                           len(pipeline.plan(sources, self.prompt, self.schema, 50000)))

    def test_numeric_punctuation_is_never_erased_when_merging_claims(self):
        sources = [dict(self.bundle["sources"][0], id=f"source-{i}") for i in range(2)]
        outputs = [{"sections": [{"id": "clinical_overview", "title": "Clinical overview", "claim_ids": ["c"]}],
                    "claims": [{"id": "c", "text": text, "source_ids": [sources[i]["id"]]}],
                    "coverage": [{"source_id": sources[i]["id"], "status": "cited", "reason": "Recorded score"}]}
                   for i, text in enumerate(["Recorded score: 2/5.", "Recorded score: 2.5."])]
        self.assertEqual(2, len(pipeline.merge(outputs, sources)["claims"]))

    def test_exact_duplicate_keeps_all_citations_and_prefers_specific_section(self):
        sources = [dict(self.bundle['sources'][0], id=f'source-{i}') for i in range(2)]
        outputs = [{'sections': [{'id': section, 'title': title, 'claim_ids': ['c']}],
                    'claims': [{'id': 'c', 'text': 'No known allergies.', 'source_ids': [sources[i]['id']]}],
                    'coverage': [{'source_id': sources[i]['id'], 'status': 'cited', 'reason': 'Allergy history'}]}
                   for i, (section, title) in enumerate([('clinical_overview', 'Clinical overview'),
                                                         ('medications_allergies', 'Medications and allergies')])]
        output = pipeline.merge(outputs, sources)
        self.assertEqual(1, len(output['claims']))
        self.assertEqual(['source-0', 'source-1'], output['claims'][0]['source_ids'])
        self.assertEqual(['medications_allergies'], [section['id'] for section in output['sections']])


if __name__ == "__main__":
    unittest.main()
