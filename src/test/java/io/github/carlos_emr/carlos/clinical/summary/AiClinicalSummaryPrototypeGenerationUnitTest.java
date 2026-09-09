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
    private ClinicalSummaryArtifact chart;
    private ObjectNode generated;
    private ObjectNode payload;
    private String show = "{}";
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
        return new ClinicalSummaryGenerationService(new OllamaClinicalSummaryAgent(server.getAddress().getPort(), MODEL, 2000));
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
        assertThat(MAPPER.readTree(payload.path("prompt").asText()).size()).isEqualTo(1);
        assertThat(draft.isRenderable()).isTrue();
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
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("incomplete response");
    }

    @Test
    void rejectsMissingCitationWithoutExposingModelText() {
        ((ObjectNode) generated.get("claims").get(0)).put("text", "sensitive diagnostic model output")
                .putArray("source_ids").add("wrong-patient-note");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation")
                .hasMessageNotContaining("sensitive diagnostic");
    }

    @Test
    void rejectsDuplicateClaimsEvenWhenIdsAreUnique() {
        ObjectNode duplicate = ((ObjectNode) generated.get("claims").get(0)).deepCopy();
        duplicate.put("id", "claim-duplicate");
        ((ArrayNode) generated.get("claims")).add(duplicate);
        ((ArrayNode) generated.get("sections").get(0).get("claim_ids")).add("claim-duplicate");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
    }

    @Test
    void rejectsMetadataClaimsAndUngroundedCitations() {
        for (String metadata : new String[]{"Source note ID: imported-note-1", "Demographic number: 3003",
                "Subject: GP contact for discharge planning", "Type: Medicine Inpatients"}) {
            ((ObjectNode) generated.get("claims").get(0)).put("text", metadata);
            assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
        }

        ((ObjectNode) generated.get("claims").get(0)).put("text", "Migraine with photophobia is worsening.");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");
    }

    @Test
    void rejectsUncontrolledSectionsAndRepeatedCoverageReasons() {
        ((ObjectNode) generated.get("sections").get(0)).put("id", "patient_identity")
                .put("title", "Patient Identity");
        assertThatThrownBy(() -> generator().generate(chart)).hasMessageContaining("failed validation");

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
    void rejectsOversizedContextBeforeGeneration() {
        ObjectNode input = MAPPER.valueToTree(chart.getView());
        ((ObjectNode) input.get("sources").get(0)).put("text", "x".repeat(60000));
        ClinicalSummaryArtifact oversized = new ClinicalSummaryArtifact(input);
        scope.when(() -> SyntheticSummaryScope.isEligible(oversized)).thenReturn(true);
        assertThatThrownBy(() -> generator().generate(oversized)).hasMessageContaining("context limit");
        assertThat(generations.get()).isZero();
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
