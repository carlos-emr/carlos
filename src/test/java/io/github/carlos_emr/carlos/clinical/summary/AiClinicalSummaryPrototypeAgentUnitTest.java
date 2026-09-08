/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AiClinicalSummaryPrototypeAgentUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ClinicalSummaryArtifact chart;
    private ObjectNode output;
    private JsonNode request;
    private HttpServer server;
    private MockedStatic<SyntheticSummaryScope> scope;
    private Consumer<ObjectNode> alterResponse = response -> { };
    private int status = 200;

    @BeforeEach
    void setUp() throws Exception {
        chart = new SyntheticClinicalSummaryProvider().load(null, ClinicalSummaryRequest.synthetic());
        JsonNode fixture = JSON.valueToTree(chart.getView());
        output = JSON.createObjectNode();
        for (String key : Set.of("sections", "claims", "coverage")) output.set(key, fixture.get(key));
        scope = mockStatic(SyntheticSummaryScope.class);
        scope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/clinical-summary", exchange -> {
            request = JSON.readTree(exchange.getRequestBody());
            ObjectNode response = JSON.createObjectNode().put("contract_version", 1)
                    .put("request_id", request.path("request_id").asText()).put("status", "completed");
            response.set("output", output);
            alterResponse.accept(response);
            byte[] bytes = JSON.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Location", "/must-not-follow");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        scope.close();
    }

    private ClinicalSummaryGenerationService service() {
        return new ClinicalSummaryGenerationService(new HttpClinicalSummaryAgent(
                server.getAddress().getPort(), "/v1/clinical-summary", "Test agent", 2000));
    }

    @Test
    void httpAgentReceivesVersionedSnapshotAndHostRetainsAuthority() throws Exception {
        var draft = service().generate(chart);
        Set<String> fields = new HashSet<>();
        request.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("contract_version", "request_id", "workflow",
                "data_classification", "instructions", "sources", "output_schema");
        assertThat(request.path("contract_version").asInt()).isEqualTo(1);
        assertThat(request.path("request_id").asText()).matches("[a-f0-9-]{36}");
        assertThat(request.path("workflow").asText()).isEqualTo("patient-overview");
        assertThat(request.path("data_classification").asText()).isEqualTo("verified-synthetic");
        assertThat(request.path("instructions").asText()).isNotBlank();
        assertThat(request.path("output_schema").path("type").asText()).isEqualTo("object");
        assertThat(request.path("sources")).isEqualTo(JSON.valueToTree(chart.getView().get("sources")));
        for (String key : Set.of("sources", "fact_ledger", "patient_context", "workflow")) {
            assertThat(draft.getView().get(key)).isEqualTo(chart.getView().get(key));
        }
        assertThat(draft.getView().get("model")).isEqualTo("Test agent via agent API v1 (unverified draft)");
        assertThat(draft.isRenderable()).isTrue();
    }

    @Test
    void rejectsWrongRequestVersionAndNoncompletedStatus() {
        for (Consumer<ObjectNode> mutation : java.util.List.<Consumer<ObjectNode>>of(
                r -> r.put("request_id", "another-request"),
                r -> r.put("contract_version", 2),
                r -> r.put("contract_version", "1"),
                r -> r.put("status", "queued"),
                r -> r.put("status", "failed"))) {
            alterResponse = mutation;
            assertThatThrownBy(() -> service().generate(chart)).isInstanceOf(ClinicalSummaryGenerationException.class)
                    .hasMessageContaining("incomplete response");
        }
    }

    @Test
    void rejectsAgentSuppliedHostFieldsAndInvalidCitations() {
        alterResponse = r -> r.put("model", "Untrusted provenance");
        assertThatThrownBy(() -> service().generate(chart)).hasMessageContaining("failed validation");
        alterResponse = r -> r.putNull("output");
        assertThatThrownBy(() -> service().generate(chart)).hasMessageContaining("failed validation");
        alterResponse = r -> { };
        output.putObject("sources");
        assertThatThrownBy(() -> service().generate(chart)).hasMessageContaining("failed validation");
        output.remove("sources");
        ((ObjectNode) output.path("claims").get(0)).putArray("source_ids").add("foreign-note");
        assertThatThrownBy(() -> service().generate(chart)).hasMessageContaining("failed validation");
    }

    @Test
    void refusesHttpRedirectsAndErrorBodies() {
        for (int code : new int[]{302, 401, 500}) {
            status = code;
            assertThatThrownBy(() -> service().generate(chart)).hasMessageContaining("unavailable");
        }
    }

    @Test
    void customAgentReceivesAnIsolatedCopy() throws Exception {
        JsonNode original = JSON.valueToTree(chart.getView());
        ClinicalSummaryAgent custom = new ClinicalSummaryAgent() {
            public String displayName() { return "Custom Java agent"; }
            public JsonNode generate(JsonNode input) {
                ((ObjectNode) input.path("sources").get(0)).put("text", "Replacement evidence");
                ((ObjectNode) input).putObject("patient_context").put("id", "another-patient");
                return output;
            }
        };
        var draft = new ClinicalSummaryGenerationService(custom).generate(chart);
        output.removeAll();
        assertThat(JSON.<JsonNode>valueToTree(chart.getView())).isEqualTo(original);
        assertThat(JSON.<JsonNode>valueToTree(draft.getView().get("sources"))).isEqualTo(original.get("sources"));
        assertThat(draft.isRenderable()).isTrue();
    }

    @Test
    void eligibilityPrecedesEveryAgentCall() {
        ClinicalSummaryAgent custom = mock(ClinicalSummaryAgent.class);
        scope.when(() -> SyntheticSummaryScope.isEligible(chart)).thenReturn(false);
        assertThatThrownBy(() -> new ClinicalSummaryGenerationService(custom).generate(chart))
                .hasMessageContaining("No chart data was sent");
        verifyNoInteractions(custom);
    }

    @Test
    void sanitizesThirdPartyExceptionsAndReleasesCapacity() throws Exception {
        ClinicalSummaryAgent custom = mock(ClinicalSummaryAgent.class);
        when(custom.displayName()).thenReturn("Custom agent");
        when(custom.generate(any())).thenThrow(new IOException("sensitive source text"))
                .thenThrow(new IllegalStateException("sensitive source text"));
        var service = new ClinicalSummaryGenerationService(custom);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.generate(chart)).isInstanceOf(ClinicalSummaryGenerationException.class)
                    .hasMessageNotContaining("sensitive source text").hasNoCause();
        }
        assertThat(service().generate(chart).isRenderable()).isTrue();
    }

    @Test
    void operatorCanSwapAdaptersWithoutChangingTheHost() {
        Properties properties = new Properties();
        assertThat(ClinicalSummaryAgents.configured(properties)).isInstanceOf(OllamaClinicalSummaryAgent.class);
        properties.setProperty("clinical.ai_summary_generation.agent", "http");
        properties.setProperty("clinical.ai_summary_generation.http.name", "My agent");
        assertThat(ClinicalSummaryAgents.configured(properties)).isInstanceOf(HttpClinicalSummaryAgent.class);
        assertThat(ClinicalSummaryAgents.configured(properties).displayName()).contains("My agent");
        properties.setProperty("clinical.ai_summary_generation.agent", "unsupported");
        assertThatThrownBy(() -> ClinicalSummaryAgents.configured(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsafeGatewayConfiguration() {
        for (String path : new String[]{"https://example.com", "//host:80/path", "/v1?token=value", "/../path"}) {
            assertThatThrownBy(() -> new HttpClinicalSummaryAgent(11435, path, "Agent", 2000))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new HttpClinicalSummaryAgent(65536, "/v1", "Agent", 2000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpClinicalSummaryAgent(11435, "/v1", "Agent\n", 2000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
