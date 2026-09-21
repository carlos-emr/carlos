/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
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

    @Test
    void leavesMalformedOutputForValidationToReject() {
        ObjectNode malformed = JSON.createObjectNode().put("claims", "not an array");
        assertThat(ClinicalSummaryHostChecks.restoreObservations(malformed, sources("note-9", "2026-01-05", BLOCK)))
                .isEqualTo(malformed);
        assertThat(ClinicalSummaryHostChecks.dateFindings(malformed, sources("note-9", "2026-01-05", BLOCK)))
                .isEqualTo(List.of());
    }
}
