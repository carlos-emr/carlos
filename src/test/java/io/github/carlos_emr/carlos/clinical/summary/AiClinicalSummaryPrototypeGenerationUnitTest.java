/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AiClinicalSummaryPrototypeGenerationUnitTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MODEL = "qwen3.5:2b";
    private HttpServer server;
    private final ClinicalSummaryGenerationCache cache = new ClinicalSummaryGenerationCache();
    private ClinicalSummaryArtifact chart;
    private ObjectNode generated;
    private ObjectNode payload;
    private String show = "{}";
    private String digest;
    private String doneReason = "stop";
    private int showStatus = 200;
    private final AtomicInteger generations = new AtomicInteger();
    private MockedStatic<SyntheticSummaryScope> scope;
    private CountDownLatch started;
    private CountDownLatch release;
    private long delayMs;
    private String rawResponse;

    @BeforeEach
    void setUp() throws Exception {
        chart = new SyntheticClinicalSummaryProvider().load(null, ClinicalSummaryRequest.synthetic());
        ObjectNode fixture = MAPPER.valueToTree(chart.getView());
        generated = MAPPER.createObjectNode();
        ArrayNode sections = generated.putArray("sections");
        sections.addObject().put("id", "clinical_overview").put("title", "Clinical overview")
                .putArray("claim_ids").add("claim-1");
        sections.addObject().put("id", "medications_allergies").put("title", "Medications and allergies")
                .putArray("claim_ids").add("claim-2").add("claim-3");
        generated.set("claims", fixture.get("claims"));
        generated.set("coverage", fixture.get("coverage"));
        scope = mockStatic(SyntheticSummaryScope.class);
        scope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/show", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (showStatus == 302) {
                exchange.getResponseHeaders().set("Location", "/api/generate");
            }
            byte[] bytes = show.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(showStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/api/tags", exchange -> {
            ObjectNode response = MAPPER.createObjectNode();
            ArrayNode models = response.putArray("models");
            if (digest != null) models.addObject().put("name", MODEL).put("digest", digest);
            byte[] bytes = MAPPER.writeValueAsBytes(response);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/api/version", exchange -> {
            byte[] bytes = "{\"version\":\"0.33.3\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/api/generate", exchange -> {
            generations.incrementAndGet();
            payload = (ObjectNode) MAPPER.readTree(exchange.getRequestBody());
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (started != null) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            ObjectNode response = MAPPER.createObjectNode().put("model", MODEL).put("done", true)
                    .put("done_reason", doneReason).put("response", generated.toString());
            byte[] bytes = (rawResponse == null ? response.toString() : rawResponse).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (release != null) release.countDown();
        server.stop(0);
        scope.close();
    }

    private ClinicalSummaryGenerationService generator() {
        return new ClinicalSummaryGenerationService(new OllamaClinicalSummaryAgent(server.getAddress().getPort(), MODEL, 2000), cache);
    }

    @Test
    void structuredGenerationPreservesHostOwnedEvidenceAndMetadata() throws Exception {
        ClinicalSummaryArtifact draft = generator().generate(chart);
        for (String key : new String[]{"sources", "fact_ledger", "patient_context", "workflow"}) {
            assertThat(draft.getView().get(key)).isEqualTo(chart.getView().get(key));
        }
        assertThat(draft.getView().get("artifact_id")).isNotEqualTo(chart.getView().get("artifact_id"));
        assertThat(draft.getView().get("model").toString()).contains(MODEL, "unverified");
        assertThat(draft.getView().get("validation").toString()).contains("ai_review_required");
        assertThat(payload.path("stream").asBoolean()).isFalse();
        assertThat(payload.path("think").asBoolean()).isFalse();
        assertThat(payload.path("format").path("type").asText()).isEqualTo("object");
        var sources = MAPPER.valueToTree(chart.getView()).path("sources");
        var coverageSchema = payload.path("format").path("properties").path("coverage");
        // The model reviews only the sources it did not cite, so an all-cited pass returns none.
        assertThat(coverageSchema.path("minItems").asInt()).isZero();
        assertThat(coverageSchema.path("maxItems").asInt()).isEqualTo(sources.size());
        ArrayNode sourceIds = MAPPER.createArrayNode();
        sources.forEach(source -> sourceIds.add(source.get("id").asText()));
        assertThat(coverageSchema.path("items").path("properties").path("source_id").path("enum")).isEqualTo(sourceIds);
        assertThat(payload.path("format").path("properties").path("claims").path("items")
                .path("properties").path("source_ids").path("items").path("enum")).isEqualTo(sourceIds);
        assertThat(MAPPER.readTree(payload.path("prompt").asText()).size()).isEqualTo(1);
        assertThat(draft.isRenderable()).isTrue();
    }

    @Test
    void shouldReuseDraftAcrossServices_whenOllamaDigestAndEvidenceMatch() throws Exception {
        digest = "a".repeat(64);
        var first = generator().generate(chart);
        assertThat(generator().generate(chart).getView()).isEqualTo(first.getView());
        assertThat(generations.get()).isEqualTo(1);
        digest = "b".repeat(64);
        generator().generate(chart);
        assertThat(generations.get()).isEqualTo(2);
    }

    @Test
    void shouldRecheckLocalModel_whenDraftIsCached() throws Exception {
        digest = "a".repeat(64);
        generator().generate(chart);
        show = "{\"remote_model\":\"cloud\"}";
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("unavailable");
        assertThat(generations.get()).isEqualTo(1);
    }

    @Test
    void shouldRegenerateWithoutCache_whenModelDigestIsMissing() throws Exception {
        generator().generate(chart);
        generator().generate(chart);
        assertThat(generations.get()).isEqualTo(2);
    }

    @Test
    void rejectsUnverifiedChartBeforeAnyGeneration() {
        scope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(false);
        assertThatThrownBy(() -> generator().generate(chart)).isInstanceOf(ClinicalSummaryGenerationException.class)
                .hasMessageContaining("No chart data was sent");
        assertThat(generations.get()).isZero();
    }

    @Test
    void rejectsCloudBackedModelBeforeSendingSourceText() {
        show = "{\"remote_model\":\"cloud-model\",\"remote_host\":\"https://example.invalid\"}";
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("configured agent is unavailable");
        assertThat(generations.get()).isZero();
    }

    @Test
    void refusesRedirects() {
        showStatus = 302;
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("unavailable");
        assertThat(generations.get()).isZero();
    }

    @Test
    void rejectsIncompleteModelOutput() {
        doneReason = "length";
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("model reached its output limit")
                .hasMessageNotContaining("unavailable");
    }

    @Test
    void rejectsMissingCitationWithoutExposingModelText() {
        ((ObjectNode) generated.get("claims").get(0)).put("text", "sensitive diagnostic model output")
                .putArray("source_ids").add("wrong-patient-note");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation")
                .hasMessageNotContaining("sensitive diagnostic");
    }

    @Test
    void mergesDuplicateClaimsWithTheirCitationsInsteadOfRejectingTheDraft() throws Exception {
        ObjectNode duplicate = ((ObjectNode) generated.get("claims").get(0)).deepCopy();
        duplicate.put("id", "claim-duplicate");
        ((ArrayNode) generated.get("claims")).add(duplicate);
        ((ArrayNode) generated.get("sections").get(0).get("claim_ids")).add("claim-duplicate");
        int before = generated.get("claims").size();
        ClinicalSummaryArtifact draft = generator().generate(chart);
        assertThat(draft.getClaimsById()).hasSize(before - 1).doesNotContainKey("claim-duplicate");
    }

    @Test
    void rejectsMetadataClaimsAndDropsAnUngroundedStatement() throws Exception {
        for (String metadata : new String[]{"Source note ID: imported-note-1", "Demographic number: 3003",
                "Subject: GP contact for discharge planning", "Type: Medicine Inpatients"}) {
            ((ObjectNode) generated.get("claims").get(0)).put("text", metadata);
            assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
        }

        // One statement sharing no word with its cited notes is dropped and named, not the whole draft.
        String dropped = generated.get("claims").get(0).get("id").asText();
        ((ObjectNode) generated.get("claims").get(0)).put("text", "Migraine with photophobia is worsening.");
        ClinicalSummaryArtifact draft = generator().generate(chart);
        assertThat(draft.getClaimsById()).doesNotContainKey(dropped);
        assertThat(draft.getView().get("validation").toString())
                .contains("statements_settled_by_host", "Migraine with photophobia is worsening.");
    }

    @Test
    void acceptsSmokingInflectionsWithoutChangingTheClaimOrAcceptingUnrelatedText() {
        ObjectNode output = MAPPER.createObjectNode();
        ObjectNode claim = output.putArray("claims").addObject().put("id", "c1").put("text", "Patient does not smoke.");
        claim.putArray("source_ids").add("note-1");
        output.putArray("sections").addObject().put("id", "clinical_overview").put("title", "Clinical overview")
                .putArray("claim_ids").add("c1");
        output.putArray("coverage").addObject().put("source_id", "note-1").put("status", "cited")
                .put("reason", "Smoking history reviewed");
        ArrayNode sources = MAPPER.createArrayNode();
        sources.addObject().put("id", "note-1").put("title", "Encounter").put("date", "2026-01-07")
                .put("text", "No smoking.");
        assertThatCode(() -> ClinicalSummaryGenerationService.validateGenerated(output, sources, false))
                .doesNotThrowAnyException();
        assertThat(claim.path("text").asText()).isEqualTo("Patient does not smoke.");
        claim.put("text", "Patient has diabetes.");
        assertThatThrownBy(() -> ClinicalSummaryGenerationService.validateGenerated(output, sources, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsQuotedClinicalTextButRejectsLineBreaks() throws Exception {
        ObjectNode claim = (ObjectNode) generated.get("claims").get(0);
        String original = claim.get("text").asText();
        claim.put("text", "Reported as \"" + original + "\"");
        assertThat(generator().generate(chart).getClaimsById().get(claim.get("id").asText()).get("text"))
                .isEqualTo(claim.get("text").asText());
        for (String newline : new String[]{"\n", "\r"}) {
            claim.put("text", original + newline + "Additional recorded context.");
            assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
        }
    }

    @Test
    void foldsAnUncontrolledSectionIntoTheOverviewAndRejectsRepeatedCoverageReasons() throws Exception {
        ((ObjectNode) generated.get("sections").get(0)).put("id", "patient_identity")
                .put("title", "Patient Identity");
        ClinicalSummaryArtifact folded = generator().generate(chart);
        assertThat(folded.getView().get("sections").toString()).contains("clinical_overview").doesNotContain("patient_identity");
        assertThat(folded.getView().get("validation").toString()).contains("Section patient_identity is not one of the five");

        ((ObjectNode) generated.get("sections").get(0)).put("id", "clinical_overview")
                .put("title", "Clinical overview");
        String repeated = generated.get("coverage").get(0).get("reason").asText();
        ((ObjectNode) generated.get("coverage").get(1)).put("reason", repeated);
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
    }

    @Test
    void rejectsModelReplacementOfHostFields() {
        generated.putObject("patient_context").put("label", "Replacement patient");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
    }

    @Test
    void rejectsEmptyDraft() {
        generated.putArray("claims");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
    }

    @Test
    void timesOutWithoutDisplayingModelText() {
        delayMs = 300;
        assertThatThrownBy(() -> new ClinicalSummaryGenerationService(new OllamaClinicalSummaryAgent(server.getAddress().getPort(), MODEL, 50))
                .generate(chart)).hasMessageContaining("timed out");
    }

    @Test
    void rejectsOversizedResponse() {
        rawResponse = "x".repeat(4 * 1024 * 1024 + 1);
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("unreadable");
    }

    @Test
    void rejectsDuplicateJsonKeys() {
        rawResponse = "{\"done\":true,\"done\":false}";
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("unreadable");
    }

    @Test
    void allowsOnlyLocalModelTagsAndValidPorts() {
        assertThatThrownBy(() -> new OllamaClinicalSummaryAgent(11434, "qwen3.5:cloud", 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OllamaClinicalSummaryAgent(0, MODEL, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeoutConfigurationIsBoundedWithoutIntegerOverflow() {
        assertThat(ClinicalSummaryAgentProtocol.timeoutMillis("1800")).isEqualTo(1800000);
        for (String invalid : new String[]{"0", "-1", "1801", "2147483647", "invalid"}) {
            assertThatThrownBy(() -> ClinicalSummaryAgentProtocol.timeoutMillis(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsConcurrentGenerationAndReleasesCapacity() throws Exception {
        started = new CountDownLatch(1);
        release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> {
                try (MockedStatic<SyntheticSummaryScope> threadScope = mockStatic(SyntheticSummaryScope.class)) {
                    threadScope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(true);
                    return generator().generate(chart);
                }
            });
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("already generating");
            } finally {
                release.countDown();
            }
            assertThat(first.get(3, TimeUnit.SECONDS).isRenderable()).isTrue();
        }
        assertThat(generator().generate(chart).isRenderable()).isTrue();
    }

    @Test
    void runtimeAndOfflinePromptContractsRemainAligned() throws Exception {
        try (var prompt = getClass().getResourceAsStream("/clinical/summary/generation-prompt.txt");
                var schema = getClass().getResourceAsStream("/clinical/summary/generation-schema.json")) {
            assertThat(new String(prompt.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(Files.readString(Path.of("tools/ai-clinical-summary-draft/prompt.txt")));
            assertThat(MAPPER.readTree(schema))
                    .isEqualTo(MAPPER.readTree(Files.readString(Path.of("tools/ai-clinical-summary-draft/output-schema.json"))));
        }
    }
}
