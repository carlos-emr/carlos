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

    def test_a_value_that_ends_a_sentence_is_kept_and_a_decimal_is_never_cut_short(self):
        found = host_checks.observation_sets("Observations: HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98. Patient alert.")
        self.assertEqual("HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98", found[0]["text"])
        self.assertEqual([("hr", "88"), ("rr", "16"), ("temp", "36.8")],
                         host_checks.observation_sets("HR 88. RR 16. Temp 36.8.")[0]["measurements"])

    def test_a_rate_written_per_minute_is_recognised_in_notes_and_in_claims(self):
        # Observed live: "RR 16/min" was not recognised, so the host restored a set the draft had reported.
        found = host_checks.observation_sets("Obs: HR 82 bpm, BP 128/78 mmHg, RR 16/min, Temp 36.8, SpO2 97% on air.")
        self.assertEqual([("hr", "82"), ("bp", "128/78"), ("rr", "16"), ("temp", "36.8"), ("spo2", "97")],
                         found[0]["measurements"])
        claim = "Observations on 08/01/26 showed HR 82 bpm, BP 128/78 mmHg, RR 16/min, Temp 36.8°C, and SpO2 97% on air."
        self.assertTrue(host_checks.reports(claim, found[0]["measurements"]))

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


CLASSES = {"tinzaparin": "B01AB10", "enoxaparin": "B01AB05", "paracetamol": "N02BE01", "codeine": "R05DA04"}
TINZ = "Post-op plan: Tinzaparin 4,500 units SC once daily for thromboprophylaxis. Paracetamol 1g QDS PRN."
ENOX = "Ward round. Plan: start enoxaparin 40 mg SC once daily for thromboprophylaxis; codeine 30 mg PRN."


class MedicationConflictsTest(unittest.TestCase):
    def sources(self, later=ENOX):
        return [source(TINZ, "note-7"), source(later, "note-9", "2026-01-06")]

    def test_two_ordered_drugs_of_one_class_with_no_recorded_stop_are_a_conflict(self):
        self.assertEqual([{"group": "B01AB", "drugs": {"tinzaparin": ["note-7"], "enoxaparin": ["note-9"]}}],
                         host_checks.medication_conflicts(self.sources(), CLASSES))

    def test_the_word_postoperative_is_not_a_recorded_stop(self):
        # Observed on NHSSYN002: "as per postop instructions" beside the drug name hid the real conflict.
        later = ENOX + " Ensure enoxaparin administration as per postoperative instructions."
        self.assertEqual(1, len(host_checks.medication_conflicts(self.sources(later), CLASSES)))

    def test_a_recorded_stop_or_switch_a_mere_mention_and_different_classes_are_not_conflicts(self):
        for later in ("Tinzaparin stopped. Start enoxaparin 40 mg SC once daily.",
                      "Switched from tinzaparin to enoxaparin 40 mg SC once daily.",
                      "Patient asked whether enoxaparin would be needed; not prescribed.",
                      "Plan: codeine 30 mg PRN for breakthrough pain."):
            with self.subTest(later=later):
                self.assertEqual([], host_checks.medication_conflicts(self.sources(later), CLASSES))
        self.assertEqual([], host_checks.medication_conflicts(self.sources(), None))

    def test_a_draft_that_lists_both_drugs_as_routine_gets_a_host_statement_and_never_a_claimed_switch(self):
        output = draft(("Tinzaparin 4,500 units SC once daily was prescribed.", ["note-7"]),
                       ("Enoxaparin 40 mg SC once daily was started.", ["note-9"]))
        restored, added = host_checks.note_medication_conflicts(output, self.sources(), CLASSES)
        self.assertEqual(1, added)
        claim = restored["claims"][-1]
        self.assertEqual("host-med-1", claim["id"])
        self.assertEqual(["note-7", "note-9"], claim["source_ids"])
        self.assertEqual("Medication records conflict, as found by the host: tinzaparin (05/01/26) and enoxaparin "
                         "(06/01/26) belong to the same drug class (ATC B01AB) and no note records either being "
                         "stopped, so the record does not show which is intended.", claim["text"])
        self.assertNotRegex(claim["text"], r"(?i)(switch|chang|convert|transition)\w*\s+(to\s+)?enoxaparin")
        self.assertEqual(["host-med-1"], next(row["claim_ids"] for row in restored["sections"]
                                              if row["id"] == "medications_allergies"))
        self.assertEqual((restored, 0), host_checks.note_medication_conflicts(restored, self.sources(), CLASSES))

    def test_the_conflict_statement_uses_the_date_format_the_draft_already_uses(self):
        output = draft(("On 2026-01-05 tinzaparin 4,500 units SC once daily was prescribed.", ["note-7"]))
        restored, _added = host_checks.note_medication_conflicts(output, self.sources(), CLASSES)
        self.assertIn("tinzaparin (2026-01-05) and enoxaparin (2026-01-06)", restored["claims"][-1]["text"])

    def test_a_draft_that_already_reports_the_conflict_is_left_alone(self):
        output = draft(("Medication records conflict: tinzaparin and enoxaparin were both prescribed with no "
                        "documented discontinuation.", ["note-7", "note-9"]))
        self.assertEqual((output, 0), host_checks.note_medication_conflicts(output, self.sources(), CLASSES))

    def test_a_claimed_switch_that_no_note_records_is_reported(self):
        output = draft(("The thromboprophylaxis regimen was changed to enoxaparin 40 mg once daily.", ["note-9"]))
        self.assertEqual([{"claim_id": "c1", "drugs": ["enoxaparin", "tinzaparin"], "source_ids": ["note-9"]}],
                         host_checks.undocumented_changes(output, self.sources(), CLASSES))
        documented = self.sources("Switched from tinzaparin to enoxaparin 40 mg SC once daily.")
        self.assertEqual([], host_checks.undocumented_changes(output, documented, CLASSES))


NOTE = ("Pt: Andrew Michael Edwards, 19M. NHS number 928876615. DOB: 18/03/04.\n"
        "Reviewed by Nurse Saoirse Keogh at 10:00 with Dr Cai Hopkins.\nSaoirse Keogh \nNMC 1234\nPlan: mobilise.")


class NameFindingsTest(unittest.TestCase):
    def test_staff_names_come_from_titles_in_the_notes_and_identity_from_the_notes_and_label(self):
        self.assertEqual({"Saoirse Keogh", "Cai Hopkins"}, host_checks.staff_names([source(NOTE)]))
        self.assertEqual({"928876615", "18/03/04", "19-year-old", "19 year old", "Andrew Michael Edwards",
                          "Edwards, Andrew Michael"},
                         host_checks.identity_terms([source(NOTE)], "FAKE-NHS Edwards, Andrew Michael"))

    def test_a_claim_naming_staff_or_the_patient_is_reported_and_a_role_only_claim_is_not(self):
        output = draft(("Nurse Saoirse Keogh reviewed the patient, a 19-year-old.", ["note-9"]),
                       ("The nurse reviewed the patient and planned mobilisation.", ["note-9"]))
        self.assertEqual([{"claim_id": "c1", "terms": ["19-year-old", "Saoirse Keogh"]}],
                         host_checks.name_findings(output, [source(NOTE)], "FAKE-NHS Edwards, Andrew Michael"))


class DuplicateClaimsTest(unittest.TestCase):
    def test_identical_statements_are_merged_with_their_citations_instead_of_failing_the_draft(self):
        output = draft(("HR 2 was recorded.", ["note-9"]), ("HR 2 was recorded.", ["note-10"]),
                       ("Other.", ["note-9"]))
        output["sections"] = [{"id": "results_observations", "title": "Results and observations", "claim_ids": ["c1"]},
                              {"id": "plan_follow_up", "title": "Plan and follow-up", "claim_ids": ["c2", "c3"]}]
        merged, count = host_checks.merge_identical_claims(output)
        self.assertEqual(1, count)
        self.assertEqual([{"id": "c1", "text": "HR 2 was recorded.", "source_ids": ["note-9", "note-10"]},
                          {"id": "c3", "text": "Other.", "source_ids": ["note-9"]}], merged["claims"])
        self.assertEqual([["c1"], ["c3"]], [row["claim_ids"] for row in merged["sections"]])
        self.assertEqual((merged, 0), host_checks.merge_identical_claims(merged))

    def test_a_statement_wholly_contained_in_another_section_s_statement_is_folded_into_it(self):
        output = draft(("On 05/01/26 nimodipine 60 mg was started for RCVS.", ["note-9"]),
                       ("Nimodipine 60 mg was started.", ["note-10"]))
        output["sections"] = [{"id": "medications_allergies", "title": "Medications and allergies", "claim_ids": ["c1"]},
                              {"id": "active_problems", "title": "Active problems", "claim_ids": ["c2"]}]
        merged, count = host_checks.merge_contained_claims(output)
        self.assertEqual(1, count)
        self.assertEqual(["c1"], [claim["id"] for claim in merged["claims"]])
        self.assertEqual(["note-9", "note-10"], merged["claims"][0]["source_ids"])
        self.assertEqual([["c1"]], [row["claim_ids"] for row in merged["sections"]])

    def test_the_same_template_of_words_on_two_days_with_different_values_is_not_a_duplicate(self):
        output = draft(("Pre-operative vital signs on 20/12/25 were heart rate 72 bpm, blood pressure 124/78 mmHg, "
                        "respiratory rate 16 br/min, temperature 36.8 and SpO2 98%.", ["note-1"]),
                       ("Observations on 05/01/26 were heart rate 2 bpm, blood pressure 124/78 mmHg, respiratory "
                        "rate 1 br/min, temperature 36.8 and SpO2 98%.", ["note-9"]))
        output["sections"] = [{"id": "results_observations", "title": "Results and observations", "claim_ids": ["c1"]},
                              {"id": "clinical_overview", "title": "Clinical overview", "claim_ids": ["c2"]}]
        self.assertEqual([], host_checks.near_duplicates(output))

    def test_a_near_duplicate_across_sections_is_reported_but_not_removed(self):
        output = draft(("On 05/01/26 nimodipine 60 mg four-hourly was started for RCVS with dietary advice.", ["note-9"]),
                       ("Nimodipine 60 mg four-hourly was started for RCVS on the ward.", ["note-9"]))
        output["sections"] = [{"id": "medications_allergies", "title": "Medications and allergies", "claim_ids": ["c1"]},
                              {"id": "active_problems", "title": "Active problems", "claim_ids": ["c2"]}]
        self.assertEqual([("c1", "c2")], [(a, b) for a, b, _j, _c in host_checks.near_duplicates(output)])
        self.assertEqual((output, 0), host_checks.merge_contained_claims(output))


class ToleratedStructureTest(unittest.TestCase):
    def test_a_duplicate_claim_id_is_renumbered_and_its_section_membership_kept(self):
        output = {"claims": [{"id": "c5", "text": "The knee replacement was reviewed.", "source_ids": ["note-9"]},
                             {"id": "c5", "text": "The patient was alert and oriented.", "source_ids": ["note-9"]},
                             {"id": "c6", "text": "Review was planned for tomorrow.", "source_ids": ["note-9"]}],
                  "sections": [{"id": "plan_follow_up", "title": "Plan and follow-up", "claim_ids": ["c5", "c6"]}],
                  "coverage": []}
        fixed, notes = host_checks.tolerate_structure(output, [source()])
        self.assertEqual(["c5", "c5-2", "c6"], [claim["id"] for claim in fixed["claims"]])
        self.assertEqual(["c5", "c5-2", "c6"], fixed["sections"][0]["claim_ids"])
        self.assertEqual(["Statement c5 shared its ID with another; the second is now c5-2."], notes)
        validate_generated(dict(fixed, coverage=[{"source_id": "note-9", "status": "cited", "reason": "r"}]), [source()])

    def test_a_section_outside_the_fixed_five_or_with_a_wrong_title_is_folded_into_the_overview(self):
        output = {"claims": [{"id": "c1", "text": "A.", "source_ids": ["note-9"]},
                             {"id": "c2", "text": "B.", "source_ids": ["note-9"]}],
                  "sections": [{"id": "social_history", "title": "Social history", "claim_ids": ["c1"]},
                               {"id": "plan_follow_up", "title": "Plan", "claim_ids": ["c2", "c2"]}],
                  "coverage": []}
        fixed, notes = host_checks.tolerate_structure(output, [source()])
        self.assertEqual([{"id": "plan_follow_up", "title": "Plan and follow-up", "claim_ids": ["c2"]},
                          {"id": "clinical_overview", "title": "Clinical overview", "claim_ids": ["c1"]}],
                         fixed["sections"])
        self.assertEqual(["Section social_history is not one of the five clinical sections; its statements are "
                          "under Clinical overview."], notes)

    def test_one_statement_with_no_word_from_its_cited_notes_is_dropped_and_named(self):
        output = draft(("The knee replacement was reviewed.", ["note-9"]),
                       ("Zebras migrate seasonally across savannah.", ["note-9"]))
        fixed, notes = host_checks.tolerate_structure(output, [source()])
        self.assertEqual(["c1"], [claim["id"] for claim in fixed["claims"]])
        self.assertEqual(["Statement c2 shared no word with the notes it cited and was dropped: "
                          "\"Zebras migrate seasonally across savannah.\""], notes)
        self.assertEqual((fixed, []), host_checks.tolerate_structure(fixed, [source()]))


if __name__ == "__main__":
    unittest.main()
