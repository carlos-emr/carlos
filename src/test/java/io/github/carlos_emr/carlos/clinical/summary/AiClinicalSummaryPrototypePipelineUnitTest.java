/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AiClinicalSummaryPrototypePipelineUnitTest {
    private final List<JsonNode> requests = new ArrayList<>();
    private final ClinicalSummaryGenerationCache cache = new ClinicalSummaryGenerationCache();
    private int failOnCall;
    private int outputLimitBytes;
    private int requestBytes = ClinicalSummaryGenerationPipeline.REQUEST_BYTES;
    private boolean reviewsOnlyUncited;
    private boolean citesOnlyFirstSource;
    private boolean dropsObservations;
    private boolean misdates;
    private boolean namesStaff;
    private JsonNode repairRequest;
    private String repairReply;
    private java.util.Map<String, String> classes;
    private final ClinicalSummaryAgent agent = new ClinicalSummaryAgent() {
        public String displayName() { return "Test full-record agent"; }
        public String cacheIdentity() { return "fixed-test-revision"; }
        public int requestBytes() { return requestBytes; }
        public JsonNode generate(JsonNode request) throws IOException {
            requests.add(request.deepCopy());
            assertThat(JSON.writeValueAsBytes(request).length).isLessThanOrEqualTo(requestBytes);
            if (requests.size() == failOnCall) throw new IOException("Test failure");
            if (outputLimitBytes > 0 && JSON.writeValueAsBytes(request.get("sources")).length > outputLimitBytes) {
                throw new ClinicalSummaryOutputLimitException();
            }
            ObjectNode output = JSON.createObjectNode();
            ArrayNode ids = output.putArray("sections").addObject().put("id", "clinical_overview")
                    .put("title", "Clinical overview").putArray("claim_ids");
            ArrayNode claims = output.putArray("claims");
            ArrayNode coverage = output.putArray("coverage");
            for (JsonNode source : request.get("sources")) {
                String id = source.get("id").asText();
                if (citesOnlyFirstSource && !claims.isEmpty()) continue;
                String text = source.get("text").asText().replace('\n', ' ');
                if (dropsObservations) text = text.substring(0, text.indexOf(" Observations:"));
                if (misdates) text = text + " Reviewed again on 19/03/26.";
                if (namesStaff) text = "Nurse Ada Example recorded: " + text;
                claims.addObject().put("id", "claim-" + id).put("text", text).putArray("source_ids").add(id);
                ids.add("claim-" + id);
                if (reviewsOnlyUncited) continue;
                coverage.addObject().put("source_id", id).put("status", "cited").put("reason", "Recorded findings from " + id);
            }
            return output;
        }
        public JsonNode repair(JsonNode request) throws IOException {
            repairRequest = request.deepCopy();
            if (repairReply == null) return null;
            return JSON.readTree(repairReply);
        }
    };

    private ObjectNode chart(int count) {
        ObjectNode chart = JSON.createObjectNode().put("schema_version", 1).put("artifact_id", "full-record")
                .put("generated_at", "2026-09-14T00:00:00Z").put("model", "test extract").put("workflow", "patient-overview");
        chart.putObject("patient_context").put("id", "patient-test").put("label", "Synthetic full record").put("synthetic", true);
        ArrayNode sources = chart.putArray("sources");
        ArrayNode coverage = chart.putArray("coverage");
        chart.putArray("claims");
        chart.putArray("sections");
        chart.putArray("fact_ledger");
        chart.putArray("validation");
        for (int i = 1; i <= count; i++) {
            String id = "source-" + i;
            sources.addObject().put("id", id).put("patient_id", "patient-test").put("title", "Encounter " + i)
                    .put("date", "2026-09-14").put("text", "Recorded finding " + i + ": " + "Clinical qualifiers preserved. ".repeat(12));
            coverage.addObject().put("source_id", id).put("status", "reviewed_not_cited").put("reason", "Original evidence " + id);
        }
        return chart;
    }

    private ClinicalSummaryArtifact generate(ObjectNode node) throws Exception {
        var chart = new ClinicalSummaryArtifact(node);
        try (MockedStatic<SyntheticSummaryScope> scope = mockStatic(SyntheticSummaryScope.class)) {
            scope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(true);
            return new ClinicalSummaryGenerationService(agent, cache, () -> classes).generate(chart);
        }
    }

    @Test
    void largerAgentContextKeepsRelatedNotesTogetherWithoutDroppingSources() throws Exception {
        ObjectNode input = chart(60);
        requestBytes = 50000;
        var result = generate(input);
        assertThat(requests).hasSize(1);
        assertThat(result.getClaimsById()).hasSize(60);
        assertThat(result.getView().get("sources")).isEqualTo(new ClinicalSummaryArtifact(input).getView().get("sources"));
        requests.clear();
        requestBytes = ClinicalSummaryGenerationPipeline.REQUEST_BYTES;
        generate(input);
        assertThat(requests).hasSizeGreaterThan(1);
    }

    @Test
    void keepsHostIdentityInEvidenceWithoutGeneratingAnIdentityOnlyBatch() throws Exception {
        ObjectNode input = chart(20);
        ((ObjectNode) input.get("patient_context")).put("id", "demographic-3001");
        input.get("sources").forEach(source -> ((ObjectNode) source).put("patient_id", "demographic-3001"));
        ((ArrayNode) input.get("sources")).insert(0, JSON.createObjectNode().put("id", "identity")
                .put("patient_id", "demographic-3001").put("title", "Patient identity (identity)")
                .put("date", "Current record").put("text", "Demographic number: 3001\nName: Synthetic full record"));
        ((ArrayNode) input.get("coverage")).addObject().put("source_id", "identity")
                .put("status", "reviewed_not_cited").put("reason", "Patient identity only");

        var result = generate(input);

        assertThat(result.getView().get("sources")).isEqualTo(new ClinicalSummaryArtifact(input).getView().get("sources"));
        assertThat(result.getClaimsById()).hasSize(20);
        assertThat(requests).allSatisfy(request -> assertThat(request.get("sources"))
                .noneSatisfy(source -> assertThat(source.path("id").asText()).isEqualTo("identity")));
        JsonNode coverage = JSON.valueToTree(result.getView().get("coverage"));
        assertThat(coverage.get(0).path("source_id").asText()).isEqualTo("identity");
        assertThat(coverage.get(0).path("status").asText()).isEqualTo("excluded");

        requests.clear();
        ((ObjectNode) input.get("sources").get(0)).put("text", "Clinical finding: no fever.");
        generate(input);
        assertThat(requests).anySatisfy(request -> assertThat(request.get("sources"))
                .anySatisfy(source -> assertThat(source.path("text").asText()).contains("no fever")));
    }

    @Test
    void sendsClinicalBodyOfVerifiedFixtureWhileKeepingOriginalEvidence() throws Exception {
        ObjectNode input = chart(1);
        ((ObjectNode) input.get("patient_context")).put("generation_fixture", "NHSSYN001");
        ObjectNode source = (ObjectNode) input.get("sources").get(0);
        source.put("id", "note-1");
        ((ObjectNode) input.get("coverage").get(0)).put("source_id", "note-1");
        String body = "Follow-up arranged in four weeks. No changes to medications.\nFinal recorded qualifier retained.";
        String full = "SYNTHETIC NHS TEST PATIENT - NOT FOR CLINICAL USE\nImported development fixture.\n"
                + "Source text below is preserved verbatim, including encoding and clinical inconsistencies.\n\n" + body;
        source.put("text", full);

        var result = generate(input);

        assertThat(requests.getFirst().get("sources").get(0).get("text").asText()).isEqualTo(body);
        assertThat(JSON.valueToTree(result.getView()).path("sources").get(0).path("text").asText()).isEqualTo(full);
        requests.clear();
        ((ObjectNode) input.get("patient_context")).remove("generation_fixture");
        assertThatThrownBy(() -> generate(input)).hasMessageContaining("failed validation");
        assertThat(requests.getFirst().get("sources").get(0).get("text").asText()).isEqualTo(full);
    }

    @Test
    void processesEverySourceBeyondOldCountAndCharacterCaps() throws Exception {
        ObjectNode input = chart(75);
        var result = generate(input);
        assertThat(result.getClaimsById()).hasSize(75);
        assertThat(result.getView().get("sources")).isEqualTo(new ClinicalSummaryArtifact(input).getView().get("sources"));
        assertThat(requests).hasSizeGreaterThan(1);
        assertThat(result.getClaimsById().values()).allSatisfy(claim -> assertThat(claim.get("text").toString().length()).isGreaterThan(240));
        int firstRun = requests.size();
        generate(input);
        assertThat(requests).hasSize(firstRun);
        ((ObjectNode) input.get("sources").get(74)).put("text", "Changed recorded finding with the final clinical qualifier intact.");
        generate(input);
        assertThat(requests.size() - firstRun).isBetween(1, firstRun - 1);
    }

    @Test
    void preservesLongSourceBeginningMiddleAndTailAcrossPortions() throws Exception {
        ObjectNode input = chart(1);
        String text = "Beginning finding.\n" + java.util.stream.IntStream.range(0, 1800).mapToObj(i -> "Observation " + i + ": clinical context and negation remain recorded.\n").collect(java.util.stream.Collectors.joining()) + "Final finding: no penicillin allergy.";
        ((ObjectNode) input.get("sources").get(0)).put("text", text);
        var result = generate(input);
        List<String> portions = requests.stream().map(request -> request.get("sources").get(0).get("text").asText()).toList();
        boolean[] covered = new boolean[text.length()];
        for (String portion : portions) {
            int start = text.indexOf(portion);
            assertThat(start).isGreaterThanOrEqualTo(0);
            java.util.Arrays.fill(covered, start, start + portion.length(), true);
        }
        for (boolean present : covered) assertThat(present).isTrue();
        assertThat(portions.getFirst()).startsWith("Beginning finding.");
        assertThat(portions.getLast()).endsWith("Final finding: no penicillin allergy.");
        assertThat(requests.stream().mapToInt(request -> request.get("sources").get(0).get("text").asText().length()).sum())
                .isGreaterThanOrEqualTo(text.length());
        assertThat(result.getView().get("sources").toString()).contains(text);
    }

    @Test
    void rejectsWholeDraftOnLaterFailureAndReusesValidatedEarlierPortionsOnRetry() throws Exception {
        ObjectNode input = chart(30);
        failOnCall = 2;
        assertThatThrownBy(() -> generate(input)).hasMessageContaining("unavailable");
        JsonNode first = requests.getFirst().get("sources");
        failOnCall = 0;
        requests.clear();
        assertThat(generate(input).getClaimsById()).hasSize(30);
        assertThat(requests).noneSatisfy(request -> assertThat(request.get("sources")).isEqualTo(first));
    }

    @Test
    void hostRecordsCitedSourcesWhenTheAgentReviewsOnlyUncitedOnes() throws Exception {
        reviewsOnlyUncited = true;
        var result = generate(chart(3));
        assertThat(result.getClaimsById()).hasSize(3);
        assertThat(result.getView().get("coverage").toString())
                .contains("source-1", "source-2", "source-3", "cited by 1 statement in this draft; recorded by the host")
                .doesNotContain("reviewed_not_cited");
    }

    @Test
    void aMislabelledReviewOfACitedSourceTakesTheHostKnownStatusAndKeepsItsReason() throws Exception {
        ObjectNode output = JSON.createObjectNode();
        output.putArray("claims").addObject().put("id", "c1").put("text", "A.").putArray("source_ids").add("source-1");
        output.putArray("sections");
        output.putArray("coverage").addObject().put("source_id", "source-1").put("status", "reviewed_not_cited")
                .put("reason", "Admission history.");
        JsonNode completed = ClinicalSummaryGenerationPipeline.completeCoverage(output, chart(1).get("sources"));
        assertThat(completed.get("coverage")).hasSize(1);
        assertThat(completed.get("coverage").get(0).get("status").asText()).isEqualTo("cited");
        assertThat(completed.get("coverage").get(0).get("reason").asText()).isEqualTo("Admission history.");
        assertThat(output.get("coverage").get(0).get("status").asText()).isEqualTo("reviewed_not_cited");
    }

    @Test
    void recordsAnUncitedSourceTheAgentLeftUnexplainedInsteadOfFailingTheDraft() throws Exception {
        reviewsOnlyUncited = true;
        citesOnlyFirstSource = true;
        var result = generate(chart(2));
        // The host says exactly what happened; it never describes the note as reviewed by the agent.
        assertThat(result.getView().get("coverage").toString())
                .contains("source-2: no statement cites this note and the model gave no reason; recorded by the host.");
        assertThat(result.getView().get("validation").toString())
                .contains("sources_not_cited_without_reason", "source-2");
    }

    @Test
    void replacesAStaleUnexplainedRecordOnceAHostStatementCitesThatNote() {
        ObjectNode output = JSON.createObjectNode();
        output.putArray("claims").addObject().put("id", "c1").put("text", "A.").putArray("source_ids").add("source-1");
        output.putArray("sections");
        output.putArray("coverage");
        JsonNode sources = chart(2).get("sources");
        ObjectNode first = (ObjectNode) ClinicalSummaryGenerationPipeline.completeCoverage(output, sources);
        assertThat(ClinicalSummaryGenerationPipeline.unexplainedSources(first)).containsExactly("source-2");
        ((ArrayNode) first.get("claims")).addObject().put("id", "host-obs-1").put("text", "Restored.")
                .putArray("source_ids").add("source-2");
        JsonNode second = ClinicalSummaryGenerationPipeline.completeCoverage(first, sources);
        assertThat(ClinicalSummaryGenerationPipeline.unexplainedSources(second)).isEmpty();
        assertThat(second.get("coverage").toString()).contains("source-2: cited by 1 statement in this draft");
    }

    @Test
    void restoresADroppedObservationSetAndFlagsAMisdatedStatementInTheGeneratedArtifact() throws Exception {
        ObjectNode input = chart(1);
        ((ObjectNode) input.get("sources").get(0)).put("date", "2026-01-05")
                .put("text", "Recorded finding on 12/01/27 was stable. Observations: HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98.");
        dropsObservations = true;
        var result = generate(input);
        assertThat(result.getView().get("claims").toString())
                .contains("restored verbatim by the host because the draft omitted them: HR 2; BP 124/78; RR 1; Temp 36.8; SpO2 98.");
        assertThat(result.getView().get("validation").toString()).doesNotContain("date_not_in_cited_sources");
        misdates = true;
        // A changed note is a new cache key, so the agent runs again.
        ((ObjectNode) input.get("sources").get(0)).put("text", "Recorded finding on 12/01/27 was unchanged. "
                + "Observations: HR 2, BP 124/78, RR 1, Temp 36.8, SpO2 98.");
        assertThat(generate(input).getView().get("validation").toString())
                .contains("date_not_in_cited_sources", "asserts 19/03/26");
    }

    @Test
    void aFaultedStatementIsRepairedByTheAgentOrKeptWithItsWarning() throws Exception {
        ObjectNode input = chart(1);
        ((ObjectNode) input.get("sources").get(0)).put("text", "Reviewed by Nurse Ada Example. Recorded finding one was stable.");
        namesStaff = true;
        repairReply = "{\"statements\":[{\"id\":\"claim-source-1\",\"text\":\"The nurse recorded: Reviewed by the nurse. Recorded finding one was stable.\"}]}";
        var result = generate(input);
        assertThat(repairRequest.get("contract_version").asInt()).isEqualTo(2);
        assertThat(repairRequest.get("statements").get(0).get("problems").get(0).asText()).contains("names a person");
        assertThat(repairRequest.get("sources")).hasSize(1);
        assertThat(result.getClaimsById().keySet()).containsExactly("repaired-claim-source-1");
        assertThat(result.getView().get("validation").toString()).contains("statements_repaired", "1 statement was rewritten")
                .doesNotContain("statement_names_person_or_identifier");
        // A rewrite the host can still fault is not accepted; the warning stays.
        repairReply = "{\"statements\":[{\"id\":\"claim-source-1\",\"text\":\"Nurse Ada Example still recorded finding one.\"}]}";
        ((ObjectNode) input.get("sources").get(0)).put("text", "Reviewed by Nurse Ada Example. Recorded finding one was unchanged.");
        var kept = generate(input);
        assertThat(kept.getClaimsById().keySet()).containsExactly("claim-source-1");
        assertThat(kept.getView().get("validation").toString()).contains("statement_names_person_or_identifier", "Ada Example")
                .doesNotContain("statements_repaired");
        // An agent without the operation leaves the draft as it was.
        repairReply = null;
        ((ObjectNode) input.get("sources").get(0)).put("text", "Reviewed by Nurse Ada Example. Recorded finding one was steady.");
        assertThat(generate(input).getClaimsById().keySet()).containsExactly("claim-source-1");
    }

    @Test
    void anUnreportedSameClassConflictGetsAHostStatementWhenTheSiteSuppliesTheClassTable() throws Exception {
        ObjectNode input = chart(2);
        ((ObjectNode) input.get("sources").get(0)).put("date", "2026-01-05").put("text", "Post-op plan: Tinzaparin 4,500 units SC once daily for thromboprophylaxis.");
        ((ObjectNode) input.get("sources").get(1)).put("date", "2026-01-06").put("text", "Ward round plan: start enoxaparin 40 mg SC once daily for thromboprophylaxis.");
        assertThat(generate(input).getClaimsById().keySet()).doesNotContain("host-med-1");
        classes = java.util.Map.of("tinzaparin", "B01AB10", "enoxaparin", "B01AB05");
        ((ObjectNode) input.get("sources").get(1)).put("text", "Ward round plan: start enoxaparin 40 mg SC once daily for thromboprophylaxis. ");
        var result = generate(input);
        assertThat(String.valueOf(result.getClaimsById().get("host-med-1").get("text"))).startsWith("Medication records conflict, as found by the host: tinzaparin (05/01/26) and enoxaparin (06/01/26)");
        assertThat(String.valueOf(result.getClaimsById().get("host-med-1").get("source_ids"))).isEqualTo("[source-1, source-2]");
    }

    @Test
    void retriesTokenExhaustionWithSmallerPortionsAndNeverUsesPartialOutput() throws Exception {
        outputLimitBytes = 2200;
        assertThat(generate(chart(12)).getClaimsById()).hasSize(12);
        assertThat(requests).hasSizeGreaterThan(4);
    }

    @Test
    void mergesRepeatedClaimsWithAllSupportingCitationsBeyondEight() throws Exception {
        ObjectNode input = chart(30);
        for (JsonNode source : input.get("sources")) ((ObjectNode) source).put("text", "Aspirin was discontinued on 2026-09-14.");
        // Force each source into its own pass so the per-pass duplicate check remains meaningful.
        outputLimitBytes = 250;
        var result = generate(input);
        assertThat(result.getClaimsById()).hasSize(1);
        assertThat((List<?>) result.getClaimsById().values().iterator().next().get("source_ids")).hasSize(30);
    }

    @Test
    void neverMergesDifferentNumericPunctuation() throws Exception {
        ObjectNode input = chart(2);
        ((ObjectNode) input.get("sources").get(0)).put("text", "Recorded score: 2/5.");
        ((ObjectNode) input.get("sources").get(1)).put("text", "Recorded score: 2.5.");
        outputLimitBytes = 250;
        assertThat(generate(input).getClaimsById()).hasSize(2);
    }

    @Test
    void acceptsReadableExpansionOfRecordedClinicalAbbreviations() throws Exception {
        ObjectNode input = chart(1);
        ((ObjectNode) input.get("sources").get(0)).put("text", "HR 76.");
        ObjectNode generated = JSON.createObjectNode();
        generated.putArray("sections").addObject().put("id", "results_observations").put("title", "Results and observations")
                .putArray("claim_ids").add("c1");
        generated.putArray("claims").addObject().put("id", "c1").put("text", "Heart rate was 76 beats per minute.")
                .putArray("source_ids").add("source-1");
        generated.putArray("coverage").addObject().put("source_id", "source-1").put("status", "cited").put("reason", "Recorded vital sign.");
        assertThatCode(() -> ClinicalSummaryGenerationService.validateGenerated(generated, input.get("sources"), false)).doesNotThrowAnyException();
    }
}
