# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import host_checks
from validate_artifact import validate_generated

BLOCK = ("Post-operative review of the knee replacement.\n\nObservations\nHR 2\nBP 124/78\nRR 1\nTemp 36.8\nSpO2 98\n\n"
         "On Examination\nPatient alert and oriented. Plan: review tomorrow.")


def source(text=BLOCK, source_id="note-9", date="2026-01-05"):
    return {"id": source_id, "patient_id": "demographic-3002", "title": f"Signed encounter note ({source_id})",
            "date": date, "text": text}


def draft(*claims):
    rows = [{"id": f"c{i}", "text": text, "source_ids": list(ids)} for i, (text, ids) in enumerate(claims, 1)]
    return {"claims": rows, "coverage": [],
            "sections": [{"id": "plan_follow_up", "title": "Plan and follow-up",
                          "claim_ids": [row["id"] for row in rows]}]}


class DateFindingsTest(unittest.TestCase):
    def test_a_date_no_cited_note_carries_is_reported_with_what_the_notes_do_carry(self):
        output = draft(("On 12/01/26 the knee replacement was reviewed.", ["note-9"]))
        self.assertEqual([{"claim_id": "c1", "asserted": "12/01/26", "allowed": ["05/01/26", "06/01/26"],
                           "source_ids": ["note-9"]}],
                         host_checks.date_findings(output, [source()]))

    def test_the_note_date_a_date_in_its_text_and_a_stated_tomorrow_are_supported(self):
        text = BLOCK + " Pre-operative bloods were taken on 20/12/25."
        output = draft(("Reviewed on 2026-01-05; bloods from 20/12/25 noted; review planned for 06/01/26.", ["note-9"]))
        self.assertEqual([], host_checks.date_findings(output, [source(text)]))

    def test_a_date_is_supported_by_any_one_of_the_cited_notes(self):
        output = draft(("Reviewed on 05/01/26 and again on 09/01/26.", ["note-9", "note-12"]))
        sources = [source(), source("Knee replacement physiotherapy review.", "note-12", "2026-01-09")]
        self.assertEqual([], host_checks.date_findings(output, sources))

    def test_a_slash_separated_note_id_list_is_not_a_date(self):
        self.assertEqual(set(), host_checks.claim_dates("note-10/12/13/14/15/18/20 do not specify the dose"))


    def test_the_artifact_carries_a_misdating_as_a_host_owned_validation_warning(self):
        import run
        output = draft(("On 12/01/26 the knee replacement was reviewed.", ["note-9"]))
        output["coverage"] = [{"source_id": "note-9", "status": "cited", "reason": "Reviewed."}]
        bundle = {"patient_context": {"id": "demographic-3002", "label": "Synthetic", "synthetic": True},
                  "sources": [source()], "fact_ledger": []}
        artifact = run.build_artifact(bundle, output, "test-model", "test", "2026-09-21T00:00:00+00:00")
        warning = [row for row in artifact["validation"] if row["code"] == "date_not_in_cited_sources"]
        self.assertEqual([{"severity": "warning", "code": "date_not_in_cited_sources", "source_ids": ["note-9"],
                           "message": "Statement c1 asserts 12/01/26, which none of its cited notes carries "
                                      "(05/01/26, 06/01/26)."}], warning)


class ObservationSetsTest(unittest.TestCase):
    def test_a_heading_block_with_one_measurement_per_line_is_one_set(self):
        self.assertEqual([[("hr", "2"), ("bp", "124/78"), ("rr", "1"), ("temp", "36.8"), ("spo2", "98")]],
                         [entry["measurements"] for entry in host_checks.observation_sets(BLOCK)])

    def test_measurements_written_inline_in_a_sentence_are_one_set(self):
        text = "Nil sig PMHx. Obs: HR 102, Temp 38.2Â°C, BP 128/78, SpO2 98% RA. Airway: Mallampati I."
        found = host_checks.observation_sets(text)
        self.assertEqual([[("hr", "102"), ("temp", "38.2"), ("bp", "128/78"), ("spo2", "98")]],
                         [entry["measurements"] for entry in found])
        self.assertEqual("HR 102; Temp 38.2; BP 128/78; SpO2 98", found[0]["text"])

    def test_fewer_than_three_kinds_or_measurements_far_apart_are_not_a_set(self):
        self.assertEqual([], host_checks.observation_sets("BP 120/80 today, HR 70."))
        far = "HR 70 on arrival. " + "Unrelated narrative. " * 8 + "BP 120/80 later. " + "More narrative. " * 8 + "RR 16."
        self.assertEqual([], host_checks.observation_sets(far))


class RestoreObservationsTest(unittest.TestCase):
    def test_a_dropped_set_is_added_back_verbatim_as_a_host_recorded_statement(self):
        output = draft(("The knee replacement was reviewed and the patient was alert.", ["note-9"]))
        original = copy.deepcopy(output)
        restored, added = host_checks.restore_observations(output, [source()])
        self.assertEqual(original, output)
        self.assertEqual(1, added)
        claim = restored["claims"][-1]
        self.assertEqual({"id": "host-obs-1", "source_ids": ["note-9"],
                          "text": "Observations recorded on 05/01/26, restored verbatim by the host because the "
                                  "draft omitted them: HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98."}, claim)
        results = [section for section in restored["sections"] if section["id"] == "results_observations"]
        self.assertEqual([{"id": "results_observations", "title": "Results and observations",
                           "claim_ids": ["host-obs-1"]}], results)
        validate_generated(dict(restored, coverage=[{"source_id": "note-9", "status": "cited", "reason": "r"}]),
                           [source()])
        self.assertEqual((restored, 0), host_checks.restore_observations(restored, [source()]))
        self.assertEqual([], host_checks.date_findings(restored, [source()]))

    def test_the_restored_statement_uses_the_date_format_the_draft_already_uses(self):
        # Replaying saved drafts, a dd/mm/yy host date inside an ISO draft failed the mixed-format check.
        output = draft(("On 2026-01-05 the knee replacement was reviewed.", ["note-9"]))
        restored, _added = host_checks.restore_observations(output, [source()])
        self.assertTrue(restored["claims"][-1]["text"].startswith("Observations recorded on 2026-01-05, restored"))
        self.assertEqual([], host_checks.date_findings(restored, [source()]))

    def test_a_set_the_draft_reports_in_its_own_words_is_left_alone(self):
        output = draft(("On 05/01/26 observations were heart rate 2 bpm, blood pressure 124/78 mmHg, respiratory "
                        "rate 1 br/min, temperature 36.8°C, and SpO2 98%.", ["note-9"]))
        self.assertEqual((output, 0), host_checks.restore_observations(output, [source()]))

    def test_a_set_with_one_value_missing_or_reported_only_under_another_note_is_restored(self):
        partial = draft(("Observations were blood pressure 124/78 mmHg, temperature 36.8 and SpO2 98%.", ["note-9"]))
        self.assertEqual(1, host_checks.restore_observations(partial, [source()])[1])
        elsewhere = draft(("HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98.", ["note-1"]))
        sources = [source(), source("Pre-operative clinic. HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98.", "note-1")]
        restored, added = host_checks.restore_observations(elsewhere, sources)
        self.assertEqual(1, added)
        self.assertEqual(["note-9"], restored["claims"][-1]["source_ids"])

    def test_identical_sets_in_two_notes_share_one_statement_citing_both(self):
        output = draft(("The patient was reviewed twice.", ["note-9", "note-10"]))
        restored, added = host_checks.restore_observations(output, [source(), source(BLOCK, "note-10")])
        self.assertEqual(1, added)
        self.assertEqual(["note-9", "note-10"], restored["claims"][-1]["source_ids"])


if __name__ == "__main__":
    unittest.main()
