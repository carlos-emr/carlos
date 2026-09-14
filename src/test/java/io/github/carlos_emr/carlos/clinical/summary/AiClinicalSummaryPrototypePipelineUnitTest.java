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
    private final ClinicalSummaryAgent agent = new ClinicalSummaryAgent() {
        public String displayName() { return "Test full-record agent"; }
        public String cacheIdentity() { return "fixed-test-revision"; }
        public JsonNode generate(JsonNode request) throws IOException {
            requests.add(request.deepCopy());
            assertThat(JSON.writeValueAsBytes(request).length).isLessThanOrEqualTo(ClinicalSummaryGenerationPipeline.REQUEST_BYTES);
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
                String text = source.get("text").asText().replace('\n', ' ');
                claims.addObject().put("id", "claim-" + id).put("text", text).putArray("source_ids").add(id);
                ids.add("claim-" + id);
                coverage.addObject().put("source_id", id).put("status", "cited").put("reason", "Recorded findings from " + id);
            }
            return output;
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
            return new ClinicalSummaryGenerationService(agent, cache).generate(chart);
        }
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
