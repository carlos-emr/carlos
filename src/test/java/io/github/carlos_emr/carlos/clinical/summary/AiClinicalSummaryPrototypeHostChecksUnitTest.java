/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;

/** Mirrors tools/ai-clinical-summary-draft/tests/test_host_checks.py so both hosts hold the same cases. */
@Tag("unit")
class AiClinicalSummaryPrototypeHostChecksUnitTest {
    private static final String BLOCK = "Post-operative review of the knee replacement.\n\nObservations\nHR 2\nBP 124/78\n"
            + "RR 1\nTemp 36.8\nSpO2 98\n\nOn Examination\nPatient alert and oriented. Plan: review tomorrow.";

    private static ArrayNode sources(String... idDateText) {
        ArrayNode sources = JSON.createArrayNode();
        for (int i = 0; i < idDateText.length; i += 3) {
            sources.addObject().put("id", idDateText[i]).put("patient_id", "demographic-3002")
                    .put("title", "Signed encounter note (" + idDateText[i] + ")")
                    .put("date", idDateText[i + 1]).put("text", idDateText[i + 2]);
        }
        return sources;
    }

    private static ObjectNode draft(String text, String... citedSourceIds) {
        ObjectNode output = JSON.createObjectNode();
        ArrayNode citations = output.putArray("claims").addObject().put("id", "c1").put("text", text).putArray("source_ids");
        for (String id : citedSourceIds) citations.add(id);
        output.putArray("sections").addObject().put("id", "plan_follow_up").put("title", "Plan and follow-up")
                .putArray("claim_ids").add("c1");
        output.putArray("coverage");
        return output;
    }

    @Test
    void reportsADateNoCitedNoteCarriesWithWhatTheNotesDoCarry() {
        var findings = ClinicalSummaryHostChecks.dateFindings(
                draft("On 12/01/26 the knee replacement was reviewed.", "note-9"), sources("note-9", "2026-01-05", BLOCK));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).claimId()).isEqualTo("c1");
        assertThat(findings.get(0).asserted()).isEqualTo("12/01/26");
        assertThat(findings.get(0).allowed()).containsExactly("05/01/26", "06/01/26");
        assertThat(findings.get(0).sourceIds()).containsExactly("note-9");
    }

    @Test
    void acceptsTheNoteDateADateInItsTextAStatedTomorrowAndAnyOneCitedNote() {
        String text = BLOCK + " Pre-operative bloods were taken on 20/12/25.";
        assertThat(ClinicalSummaryHostChecks.dateFindings(
                draft("Reviewed on 2026-01-05; bloods from 20/12/25 noted; review planned for 06/01/26.", "note-9"),
                sources("note-9", "2026-01-05", text))).isEmpty();
        assertThat(ClinicalSummaryHostChecks.dateFindings(
                draft("Reviewed on 05/01/26 and again on 09/01/26.", "note-9", "note-12"),
                sources("note-9", "2026-01-05", BLOCK, "note-12", "2026-01-09 08:30:00", "Physiotherapy review."))).isEmpty();
    }

    @Test
    void doesNotReadASlashSeparatedNoteIdListOrAnImpossibleDateAsADate() {
        assertThat(ClinicalSummaryHostChecks.claimDates("note-10/12/13/14/15/18/20 do not specify the dose")).isEmpty();
        assertThat(ClinicalSummaryHostChecks.claimDates("Ratio 14/15/18 was recorded.")).isEmpty();
        assertThat(ClinicalSummaryHostChecks.claimDates("Given 05/01/26-06/01/26 and 2026-01-07."))
                .containsExactlyInAnyOrder("05/01/26", "06/01/26", "07/01/26");
    }

    @Test
    void findsAHeadingBlockAndAnInlineSentenceAsOneObservationSetEach() {
        assertThat(ClinicalSummaryHostChecks.observationSets(BLOCK)).extracting(ClinicalSummaryHostChecks.ObservationSet::text)
                .containsExactly("HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98");
        assertThat(ClinicalSummaryHostChecks.observationSets(
                "Nil sig PMHx. Obs: HR 102, Temp 38.2Â°C, BP 128/78, SpO2 98% RA. Airway: Mallampati I."))
                .extracting(ClinicalSummaryHostChecks.ObservationSet::text)
                .containsExactly("HR 102; Temp 38.2; BP 128/78; SpO2 98");
    }

    @Test
    void keepsAValueThatEndsASentenceAndNeverCutsADecimalShort() {
        assertThat(ClinicalSummaryHostChecks.observationSets(
                "Observations: HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98. Patient alert."))
                .extracting(ClinicalSummaryHostChecks.ObservationSet::text)
                .containsExactly("HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98");
        assertThat(ClinicalSummaryHostChecks.observationSets("HR 88. RR 16. Temp 36.8."))
                .extracting(ClinicalSummaryHostChecks.ObservationSet::text).containsExactly("HR 88; RR 16; Temp 36.8");
    }

    @Test
    void recognisesARateWrittenPerMinuteInNotesAndInClaims() {
        ArrayNode sources = sources("note-15", "2026-01-08",
                "Obs: HR 82 bpm, BP 128/78 mmHg, RR 16/min, Temp 36.8, SpO2 97% on air.");
        assertThat(ClinicalSummaryHostChecks.observationSets(sources.get(0).get("text").asText()))
                .extracting(ClinicalSummaryHostChecks.ObservationSet::text).containsExactly("HR 82; BP 128/78; RR 16; Temp 36.8; SpO2 97");
        ObjectNode reported = draft("Observations on 08/01/26 showed HR 82 bpm, BP 128/78 mmHg, RR 16/min, "
                + "Temp 36.8\u00b0C, and SpO2 97% on air.", "note-15");
        assertThat(ClinicalSummaryHostChecks.restoreObservations(reported, sources)).isEqualTo(reported);
    }

    @Test
    void ignoresFewerThanThreeKindsAndMeasurementsFarApart() {
        assertThat(ClinicalSummaryHostChecks.observationSets("BP 120/80 today, HR 70.")).isEmpty();
        String far = "HR 70 on arrival. " + "Unrelated narrative. ".repeat(8) + "BP 120/80 later. "
                + "More narrative. ".repeat(8) + "RR 16.";
        assertThat(ClinicalSummaryHostChecks.observationSets(far)).isEmpty();
    }

    @Test
    void addsADroppedSetBackVerbatimAsAHostRecordedStatement() {
        ObjectNode output = draft("The knee replacement was reviewed and the patient was alert.", "note-9");
        ObjectNode original = output.deepCopy();
        ArrayNode sources = sources("note-9", "2026-01-05", BLOCK);
        ObjectNode restored = ClinicalSummaryHostChecks.restoreObservations(output, sources);
        assertThat(output).isEqualTo(original);
        JsonNode claim = restored.get("claims").get(1);
        assertThat(claim.get("id").asText()).isEqualTo("host-obs-1");
        assertThat(claim.get("text").asText()).isEqualTo("Observations recorded on 05/01/26, restored verbatim by the host "
                + "because the draft omitted them: HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98.");
        assertThat(claim.get("source_ids")).containsExactly(JSON.getNodeFactory().textNode("note-9"));
        JsonNode results = restored.get("sections").get(1);
        assertThat(results.get("id").asText()).isEqualTo("results_observations");
        assertThat(results.get("title").asText()).isEqualTo("Results and observations");
        assertThat(results.get("claim_ids")).containsExactly(JSON.getNodeFactory().textNode("host-obs-1"));
        assertThat(ClinicalSummaryHostChecks.restoreObservations(restored, sources)).isEqualTo(restored);
        assertThat(ClinicalSummaryHostChecks.dateFindings(restored, sources)).isEmpty();
    }

    @Test
    void writesTheRestoredStatementInTheDateFormatTheDraftAlreadyUses() {
        ObjectNode restored = ClinicalSummaryHostChecks.restoreObservations(
                draft("On 2026-01-05 the knee replacement was reviewed.", "note-9"), sources("note-9", "2026-01-05", BLOCK));
        assertThat(restored.get("claims").get(1).get("text").asText()).startsWith("Observations recorded on 2026-01-05, restored");
    }

    @Test
    void leavesASetTheDraftReportsInItsOwnWordsAndRestoresAPartialOrMisattributedOne() {
        ArrayNode sources = sources("note-9", "2026-01-05", BLOCK);
        ObjectNode own = draft("On 05/01/26 observations were heart rate 2 bpm, blood pressure 124/78 mmHg, respiratory "
                + "rate 1 br/min, temperature 36.8°C, and SpO2 98%.", "note-9");
        assertThat(ClinicalSummaryHostChecks.restoreObservations(own, sources)).isEqualTo(own);
        ObjectNode partial = draft("Observations were blood pressure 124/78 mmHg, temperature 36.8 and SpO2 98%.", "note-9");
        assertThat(ClinicalSummaryHostChecks.restoreObservations(partial, sources).get("claims")).hasSize(2);
        ArrayNode two = sources("note-9", "2026-01-05", BLOCK,
                "note-1", "2026-01-05", "Pre-operative clinic. HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98.");
        ObjectNode elsewhere = ClinicalSummaryHostChecks.restoreObservations(
                draft("HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98.", "note-1"), two);
        assertThat(elsewhere.get("claims")).hasSize(2);
        assertThat(elsewhere.get("claims").get(1).get("source_ids")).containsExactly(JSON.getNodeFactory().textNode("note-9"));
    }

    @Test
    void sharesOneStatementBetweenIdenticalSetsInTwoNotes() {
        ObjectNode restored = ClinicalSummaryHostChecks.restoreObservations(
                draft("The patient was reviewed twice.", "note-9", "note-10"),
                sources("note-9", "2026-01-05", BLOCK, "note-10", "2026-01-05", BLOCK));
        assertThat(restored.get("claims")).hasSize(2);
        assertThat(restored.get("claims").get(1).get("source_ids"))
                .containsExactly(JSON.getNodeFactory().textNode("note-9"), JSON.getNodeFactory().textNode("note-10"));
    }

    private static final String NOTE = "Pt: Andrew Michael Edwards, 19M. NHS number 928876615. DOB: 18/03/04.\n"
            + "Reviewed by Nurse Saoirse Keogh at 10:00 with Dr Cai Hopkins.\nSaoirse Keogh \nNMC 1234\nPlan: mobilise.";
    private static final Map<String, String> CLASSES = Map.of("tinzaparin", "B01AB10", "enoxaparin", "B01AB05",
            "paracetamol", "N02BE01", "codeine", "R05DA04");
    private static final String TINZ = "Post-op plan: Tinzaparin 4,500 units SC once daily for thromboprophylaxis. Paracetamol 1g QDS PRN.";
    private static final String ENOX = "Ward round. Plan: start enoxaparin 40 mg SC once daily for thromboprophylaxis; codeine 30 mg PRN.";

    private static ObjectNode twoClaims(String first, String firstSource, String second, String secondSource,
                                        String firstSection, String secondSection) {
        ObjectNode output = draft(first, firstSource);
        ((ArrayNode) output.get("claims")).addObject().put("id", "c2").put("text", second).putArray("source_ids").add(secondSource);
        ArrayNode sections = output.putArray("sections");
        sections.addObject().put("id", firstSection).put("title", firstSection).putArray("claim_ids").add("c1");
        sections.addObject().put("id", secondSection).put("title", secondSection).putArray("claim_ids").add("c2");
        return output;
    }

    @Test
    void staffNamesComeFromTitlesInTheNotesAndIdentityFromTheNotesAndLabel() {
        ArrayNode sources = sources("note-9", "2026-01-05", NOTE);
        assertThat(ClinicalSummaryHostChecks.staffNames(sources)).containsExactlyInAnyOrder("Saoirse Keogh", "Cai Hopkins");
        assertThat(ClinicalSummaryHostChecks.identityTerms(sources, "FAKE-NHS Edwards, Andrew Michael"))
                .containsExactlyInAnyOrder("928876615", "18/03/04", "19-year-old", "19 year old",
                        "Andrew Michael Edwards", "Edwards, Andrew Michael");
    }

    @Test
    void aClaimNamingStaffOrThePatientIsReportedAndARoleOnlyClaimIsNot() {
        ObjectNode output = twoClaims("Nurse Saoirse Keogh reviewed the patient, a 19-year-old.", "note-9",
                "The nurse reviewed the patient and planned mobilisation.", "note-9", "plan_follow_up", "plan_follow_up");
        var findings = ClinicalSummaryHostChecks.nameFindings(output, sources("note-9", "2026-01-05", NOTE), "FAKE-NHS Edwards, Andrew Michael");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).claimId()).isEqualTo("c1");
        assertThat(findings.get(0).terms()).containsExactly("19-year-old", "Saoirse Keogh");
    }

    @Test
    void identicalStatementsAreMergedWithTheirCitationsInsteadOfFailingTheDraft() {
        ObjectNode output = twoClaims("HR 2 was recorded.", "note-9", "HR 2 was recorded.", "note-10",
                "results_observations", "plan_follow_up");
        ObjectNode merged = ClinicalSummaryHostChecks.mergeIdenticalClaims(output);
        assertThat(merged.get("claims")).hasSize(1);
        assertThat(merged.get("claims").get(0).get("source_ids").toString()).isEqualTo("[\"note-9\",\"note-10\"]");
        assertThat(merged.get("sections")).hasSize(1);
        assertThat(ClinicalSummaryHostChecks.mergeIdenticalClaims(merged)).isEqualTo(merged);
    }

    @Test
    void aNearDuplicateAcrossSectionsIsReportedButTheSameTemplateOnTwoDaysIsNot() {
        ObjectNode restated = twoClaims("On 05/01/26 nimodipine 60 mg four-hourly was started for RCVS with dietary advice.", "note-9",
                "Nimodipine 60 mg four-hourly was started for RCVS on the ward.", "note-9", "medications_allergies", "active_problems");
        assertThat(ClinicalSummaryHostChecks.nearDuplicates(restated)).extracting(ClinicalSummaryHostChecks.Restatement::shorter)
                .containsExactly("c2");
        ObjectNode twoDays = twoClaims("Pre-operative vital signs on 20/12/25 were heart rate 72 bpm, blood pressure 124/78 mmHg, "
                + "respiratory rate 16 br/min, temperature 36.8 and SpO2 98%.", "note-1",
                "Observations on 2026-01-05 were heart rate 2 bpm, blood pressure 124/78 mmHg, respiratory rate 1 br/min, "
                + "temperature 36.8 and SpO2 98%.", "note-9", "results_observations", "clinical_overview");
        assertThat(ClinicalSummaryHostChecks.nearDuplicates(twoDays)).isEmpty();
    }

    @Test
    void twoOrderedDrugsOfOneClassWithNoRecordedStopAreAConflictAndGetAHostStatement() {
        ArrayNode sources = sources("note-7", "2026-01-05", TINZ, "note-9", "2026-01-06", ENOX);
        var conflicts = ClinicalSummaryHostChecks.medicationConflicts(sources, CLASSES);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).group()).isEqualTo("B01AB");
        assertThat(conflicts.get(0).drugs().keySet()).containsExactly("tinzaparin", "enoxaparin");
        ObjectNode output = twoClaims("Tinzaparin 4,500 units SC once daily was prescribed.", "note-7",
                "Enoxaparin 40 mg SC once daily was started.", "note-9", "medications_allergies", "plan_follow_up");
        ObjectNode noted = ClinicalSummaryHostChecks.noteMedicationConflicts(output, sources, CLASSES);
        JsonNode claim = noted.get("claims").get(2);
        assertThat(claim.get("id").asText()).isEqualTo("host-med-1");
        assertThat(claim.get("text").asText()).isEqualTo("Medication records conflict, as found by the host: tinzaparin (05/01/26) "
                + "and enoxaparin (06/01/26) belong to the same drug class (ATC B01AB) and no note records either being stopped, "
                + "so the record does not show which is intended.");
        assertThat(noted.get("sections").get(0).get("claim_ids").toString()).isEqualTo("[\"c1\",\"host-med-1\"]");
        assertThat(ClinicalSummaryHostChecks.noteMedicationConflicts(noted, sources, CLASSES)).isEqualTo(noted);
        assertThat(ClinicalSummaryHostChecks.noteMedicationConflicts(output, sources, null)).isEqualTo(output);
    }

    @Test
    void aRecordedStopOrSwitchAMereMentionAndTheWordPostoperativeAreHandled() {
        for (String later : List.of("Tinzaparin stopped. Start enoxaparin 40 mg SC once daily.",
                "Switched from tinzaparin to enoxaparin 40 mg SC once daily.",
                "Patient asked whether enoxaparin would be needed; not prescribed.")) {
            assertThat(ClinicalSummaryHostChecks.medicationConflicts(
                    sources("note-7", "2026-01-05", TINZ, "note-9", "2026-01-06", later), CLASSES)).as(later).isEmpty();
        }
        assertThat(ClinicalSummaryHostChecks.medicationConflicts(sources("note-7", "2026-01-05", TINZ, "note-9", "2026-01-06",
                ENOX + " Ensure enoxaparin administration as per postoperative instructions."), CLASSES)).hasSize(1);
    }

    @Test
    void aClaimedSwitchThatNoNoteRecordsIsReported() {
        ArrayNode sources = sources("note-7", "2026-01-05", TINZ, "note-9", "2026-01-06", ENOX);
        ObjectNode output = draft("The thromboprophylaxis regimen was changed to enoxaparin 40 mg once daily.", "note-9");
        var findings = ClinicalSummaryHostChecks.undocumentedChanges(output, sources, CLASSES);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).claimId()).isEqualTo("c1");
        assertThat(findings.get(0).drugs()).containsExactly("enoxaparin", "tinzaparin");
        ArrayNode documented = sources("note-7", "2026-01-05", TINZ, "note-9", "2026-01-06",
                "Switched from tinzaparin to enoxaparin 40 mg SC once daily.");
        assertThat(ClinicalSummaryHostChecks.undocumentedChanges(output, documented, CLASSES)).isEmpty();
    }

    @Test
    void leavesMalformedOutputForValidationToReject() {
        ObjectNode malformed = JSON.createObjectNode().put("claims", "not an array");
        assertThat(ClinicalSummaryHostChecks.restoreObservations(malformed, sources("note-9", "2026-01-05", BLOCK)))
                .isEqualTo(malformed);
        assertThat(ClinicalSummaryHostChecks.dateFindings(malformed, sources("note-9", "2026-01-05", BLOCK)))
                .isEqualTo(List.of());
    }
}
