/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class DocumentSummaryProviderSchemaUnitTest {
    private static final String SOURCE = "Review rash on 2026-11-16. Keep dressing dry.";
    private static final String EVIDENCE = "/properties/points/items/properties/evidence";

    @Test
    void shouldOmitOnlyWireUniqueness_andPreserveCanonicalRequest() throws Exception {
        var agent = new ProjectingAgent(output(false));
        new DocumentSummaryService(agent).summarize(SOURCE);

        JsonNode canonical = agent.request.get("output_schema");
        assertThat(canonical).isEqualTo(JSON.readTree(resource("document-summary-schema.json")));
        assertThat(canonical.at(EVIDENCE).path("uniqueItems").booleanValue()).isTrue();
        assertThat(agent.wire.at(EVIDENCE).has("uniqueItems")).isFalse();
        ObjectNode restored = agent.wire.deepCopy();
        ((ObjectNode) restored.at(EVIDENCE)).put("uniqueItems", true);
        assertThat(restored).isEqualTo(canonical);
        assertThat(agent.request).isEqualTo(agent.beforeProjection);
    }

    @Test
    void shouldRejectDuplicateEvidence_afterProviderGrammarProjection() {
        var agent = new ProjectingAgent(output(true));
        assertThatThrownBy(() -> new DocumentSummaryService(agent).summarize(SOURCE))
                .isInstanceOf(ClinicalSummaryGenerationException.class)
                .hasMessageContaining("failed validation");
        assertThat(agent.wire.at(EVIDENCE).has("uniqueItems")).isFalse();
        assertThat(agent.request.get("output_schema").at(EVIDENCE).path("uniqueItems").booleanValue()).isTrue();
    }

    @Test
    void shouldAcceptDistinctVerbatimEvidence_afterProviderGrammarProjection() throws Exception {
        var agent = new ProjectingAgent(output(false));
        assertThat(new DocumentSummaryService(agent).summarize(SOURCE).points()).hasSize(1);
        assertThat(agent.calls).isEqualTo(1);
    }

    @Test
    void shouldMatchSharedHostedWireSchemaFixture() throws Exception {
        var agent = new ProjectingAgent(output(false));
        new DocumentSummaryService(agent).summarize(SOURCE);
        assertThat(agent.wire).isEqualTo(JSON.readTree(resource("document-summary-provider-wire-schema.json")));
    }

    @Test
    void shouldPreserveOtherWorkflowKeywords_withoutMutation() throws Exception {
        ObjectNode request = JSON.createObjectNode().put("workflow", "chart-update-proposals");
        request.set("output_schema", JSON.readTree(resource("document-summary-schema.json")));
        JsonNode before = request.deepCopy();
        ObjectNode wire = providerWireSchema(request);
        assertThat(wire).isEqualTo(request.get("output_schema"));
        assertThat(wire.at(EVIDENCE).path("uniqueItems").booleanValue()).isTrue();
        assertThat(request).isEqualTo(before);
    }

    @Test
    void shouldRejectUnexpectedCanonicalDocumentSchema_beforeProviderCall() throws Exception {
        for (Boolean value : new Boolean[]{false, null}) {
            ObjectNode request = JSON.createObjectNode().put("workflow", "single-document-summary");
            ObjectNode schema = (ObjectNode) JSON.readTree(resource("document-summary-schema.json"));
            ObjectNode evidence = (ObjectNode) schema.at(EVIDENCE);
            if (value == null) evidence.remove("uniqueItems"); else evidence.put("uniqueItems", value);
            request.set("output_schema", schema);
            assertThatThrownBy(() -> providerWireSchema(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Expected canonical document evidence uniqueness");
        }
    }

    private static ObjectNode output(boolean duplicate) {
        ObjectNode result = JSON.createObjectNode().put("overview", "Review rash and keep dressing dry.");
        var evidence = result.putArray("points").addObject().put("text", "Review rash and keep dressing dry.")
                .putArray("evidence").add("Review rash on 2026-11-16.");
        evidence.add(duplicate ? "Review rash on 2026-11-16." : "Keep dressing dry.");
        return result;
    }

    private static final class ProjectingAgent implements ClinicalSummaryAgent {
        private final JsonNode output;
        private JsonNode request;
        private JsonNode beforeProjection;
        private ObjectNode wire;
        private int calls;

        private ProjectingAgent(JsonNode output) { this.output = output; }
        public String displayName() { return "synthetic grammar boundary"; }
        public JsonNode generate(JsonNode request) throws IOException {
            this.request = request;
            beforeProjection = request.deepCopy();
            wire = providerWireSchema(request);
            calls++;
            return output.deepCopy();
        }
    }
}
