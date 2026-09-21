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

    def test_committed_prompt_leaves_room_for_clinical_text_at_the_minimum_budget(self):
        """The prompt shares the 10,000-byte request budget with source text, so its size is a hard limit.

        pipeline.split refuses to divide a source under 1024 characters, so a note that is too big to
        fit alongside the prompt but too small to split cannot be planned at all. That surfaces as a
        confusing "minimal source portion could not be completed" rather than as a prompt-size problem.
        The worst real case is NHSSYN003 note-12 at 985 characters, which capped the prompt near 7.3 KB
        when the budget was 10,000 and forced the budget up rather than the prompt down.
        Plan every committed fixture at the minimum budget so prompt growth fails loudly here instead.
        """
        import openrouter_agent
        notes = openrouter_agent.SyntheticNotes().notes
        fixtures = sorted({name for name, _date, _body in notes})
        self.assertEqual(50, len(fixtures))
        for fixture in fixtures:
            sources = []
            for date, body in [(date, body) for name, date, body in notes if name == fixture]:
                source_id = f"note-{len(sources) + 1}"
                sources.append({"id": source_id, "patient_id": "demographic-3001",
                                "title": f"Signed encounter note ({source_id})",
                                "date": date, "text": body})
            with self.subTest(fixture=fixture):
                self.assertTrue(pipeline.plan(sources, self.prompt, self.schema, pipeline.REQUEST_BYTES))

    def test_coverage_and_citations_are_bounded_to_each_pass_without_mutating_schema(self):
        original = copy.deepcopy(self.schema)
        for count in (1, 75):
            sources = [{"id": f"note-{i}"} for i in range(count)]
            schema = pipeline.ollama_schema(self.schema, sources)
            coverage = schema["properties"]["coverage"]
            # The model reviews only sources it did not cite, so an all-cited pass returns none.
            self.assertEqual(0, coverage["minItems"])
            self.assertEqual(count, coverage["maxItems"])
            self.assertEqual(["reviewed_not_cited", "excluded"],
                             coverage["items"]["properties"]["status"]["enum"])
            expected = [source["id"] for source in sources]
            self.assertEqual(expected, coverage["items"]["properties"]["source_id"]["enum"])
            citations = schema["properties"]["claims"]["items"]["properties"]["source_ids"]
            self.assertEqual(expected, citations["items"]["enum"])
            self.assertEqual(count, citations["maxItems"])
        self.assertEqual(original, self.schema)

    def test_host_records_cited_sources_and_keeps_the_models_uncited_reviews(self):
        sources = [{"id": "note-1"}, {"id": "note-2"}, {"id": "admin-1"}]
        skipped = {"source_id": "admin-1", "status": "excluded", "reason": "Administrative task text."}
        output = {"claims": [{"id": "c1", "text": "A.", "source_ids": ["note-1", "note-2"]},
                             {"id": "c2", "text": "B.", "source_ids": ["note-1"]}],
                  "sections": [], "coverage": [skipped]}
        original = copy.deepcopy(output)
        completed = pipeline.complete_coverage(output, sources)
        self.assertEqual(original, output)
        self.assertEqual([skipped,
                          {"source_id": "note-1", "status": "cited",
                           "reason": "note-1: cited by 2 statements in this draft; recorded by the host."},
                          {"source_id": "note-2", "status": "cited",
                           "reason": "note-2: cited by 1 statement in this draft; recorded by the host."}],
                         completed["coverage"])
        self.assertEqual(completed, pipeline.complete_coverage(completed, sources))

    def test_a_models_own_review_is_kept_and_an_unexplained_uncited_source_is_recorded_as_such(self):
        sources = [{"id": "note-1"}, {"id": "note-2"}]
        own = {"source_id": "note-1", "status": "cited", "reason": "Admission history."}
        output = {"claims": [{"id": "c1", "text": "A.", "source_ids": ["note-1"]}], "sections": [], "coverage": [own]}
        # note-2 is uncited and unexplained. The host says exactly that rather than failing the draft
        # or pretending the model reviewed it.
        unexplained = {"source_id": "note-2", "status": "reviewed_not_cited",
                       "reason": "note-2: no statement cites this note and the model gave no reason; "
                                 "recorded by the host."}
        self.assertEqual([own, unexplained], pipeline.complete_coverage(output, sources)["coverage"])
        self.assertEqual(["note-2"], pipeline.unexplained_sources(pipeline.complete_coverage(output, sources)))
        # The schema no longer offers "cited", so a model reviewing a cited source can only mislabel it.
        output["coverage"] = [dict(own, status="reviewed_not_cited")]
        self.assertEqual([own, unexplained], pipeline.complete_coverage(output, sources)["coverage"])

    def test_a_note_first_recorded_as_unexplained_becomes_cited_once_a_host_statement_cites_it(self):
        sources = [{"id": "note-1"}, {"id": "note-2"}]
        output = {"claims": [{"id": "c1", "text": "A.", "source_ids": ["note-1"]}], "sections": [], "coverage": []}
        first = pipeline.complete_coverage(output, sources)
        self.assertEqual(["note-2"], pipeline.unexplained_sources(first))
        first["claims"].append({"id": "host-obs-1", "text": "Restored.", "source_ids": ["note-2"]})
        second = pipeline.complete_coverage(first, sources)
        self.assertEqual([], pipeline.unexplained_sources(second))
        self.assertEqual({"source_id": "note-2", "status": "cited",
                          "reason": "note-2: cited by 1 statement in this draft; recorded by the host."},
                         second["coverage"][-1])

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
        # The committed prompt needs more than the protocol's 10,000 floor; compare the real
        # default budget against a large one rather than a literal that no longer applies.
        for budget in (pipeline.REQUEST_BYTES, 50000):
            batches = pipeline.plan(sources, self.prompt, self.schema, budget)
            self.assertEqual(sources, [source for batch in batches for source in batch])
            self.assertGreater(len(batches), 1)
        self.assertGreater(len(pipeline.plan(sources, self.prompt, self.schema, pipeline.REQUEST_BYTES)),
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
