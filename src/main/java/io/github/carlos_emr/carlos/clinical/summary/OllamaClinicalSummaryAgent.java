/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Set;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.*;

/** Default single-call agent. Model-specific behavior stays out of the host generation service. */
public final class OllamaClinicalSummaryAgent implements ClinicalSummaryAgent {
    private static final Set<String> MODELS = Set.of("qwen3.5:0.8b", "qwen3.5:2b", "qwen3.5:4b", "qwen3.5:9b");
    private final int port;
    private final String model;
    private final int timeoutMs;

    public OllamaClinicalSummaryAgent(int port, String model, int timeoutMs) {
        validateConnection(port, timeoutMs);
        if (!MODELS.contains(model)) throw new IllegalArgumentException("Invalid local model configuration");
        this.port = port;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String displayName() { return model + " via local Ollama"; }

    @Override
    public JsonNode generate(JsonNode request) throws IOException {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.set("sources", request.get("sources").deepCopy());
        ObjectNode payload = JSON.createObjectNode().put("model", model)
                .put("system", request.get("instructions").asText())
                .put("prompt", JSON.writeValueAsString(bundle)).put("stream", false).put("think", false)
                .put("keep_alive", "5m");
        payload.set("format", request.get("output_schema").deepCopy());
        payload.putObject("options").put("temperature", 0).put("num_ctx", 65536).put("num_predict", 4096);
        byte[] body = JSON.writeValueAsBytes(payload);
        if (body.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Ollama context limit exceeded");
        JsonNode info = post(port, "/api/show", JSON.writeValueAsBytes(JSON.createObjectNode().put("model", model)), timeoutMs);
        if (info == null || !info.isObject() || !info.path("remote_model").asText("").isBlank()
                || !info.path("remote_host").asText("").isBlank()) {
            throw new IOException("Cloud-backed Ollama models are not permitted");
        }
        JsonNode response = post(port, "/api/generate", body, timeoutMs);
        if (response == null || !response.isObject() || !response.path("done").isBoolean()
                || !response.path("done").booleanValue() || !"stop".equals(response.path("done_reason").asText())
                || !model.equals(response.path("model").asText()) || !response.path("response").isTextual()) {
            throw new IOException("Incomplete Ollama response");
        }
        return JSON.readTree(response.get("response").asText());
    }
}
