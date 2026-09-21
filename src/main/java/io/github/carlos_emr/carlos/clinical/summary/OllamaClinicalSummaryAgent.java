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
    public String cacheIdentity() throws IOException {
        verifyLocalModel();
        // Digest identifies weights/template/parameters even when an operator reuses a model tag.
        // Missing metadata disables caching rather than trusting the mutable tag.
        JsonNode tags;
        JsonNode version;
        try {
            tags = get(port, "/api/tags", Math.min(timeoutMs, 3000));
            version = get(port, "/api/version", Math.min(timeoutMs, 3000));
        } catch (IOException unavailable) {
            return null;
        }
        if (tags == null || !tags.path("models").isArray() || version == null
                || !version.path("version").isTextual() || version.path("version").asText().isBlank()) return null;
        for (JsonNode tag : tags.get("models")) {
            if (model.equals(tag.path("name").asText()) && tag.path("digest").isTextual()
                    && tag.path("digest").asText().matches("(?:sha256:)?[a-f0-9]{64}")) {
                return "ollama:" + port + ":" + model + ":" + tag.get("digest").asText()
                        + ":" + version.get("version").asText() + ":no-think:temp0:ctx16384:predict4096";
            }
        }
        return null;
    }

    private void verifyLocalModel() throws IOException {
        JsonNode info = post(port, "/api/show", JSON.writeValueAsBytes(JSON.createObjectNode().put("model", model)),
                Math.min(timeoutMs, 3000));
        if (info == null || !info.isObject() || !info.path("remote_model").asText("").isBlank()
                || !info.path("remote_host").asText("").isBlank()) {
            throw new IOException("Cloud-backed Ollama models are not permitted");
        }
    }

    @Override
    public JsonNode generate(JsonNode request) throws IOException {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.set("sources", request.get("sources").deepCopy());
        ObjectNode payload = JSON.createObjectNode().put("model", model)
                .put("system", request.get("instructions").asText())
                .put("prompt", JSON.writeValueAsString(bundle)).put("stream", false).put("think", false)
                .put("keep_alive", "5m");
        ObjectNode schema = request.get("output_schema").deepCopy();
        ObjectNode coverage = (ObjectNode) schema.path("properties").path("coverage");
        // The model reviews only the sources it did not cite; the host records the cited ones.
        coverage.put("minItems", 0).put("maxItems", request.get("sources").size());
        var coverageIds = ((ObjectNode) coverage.path("items").path("properties").path("source_id")).putArray("enum");
        ObjectNode citations = (ObjectNode) schema.path("properties").path("claims").path("items").path("properties").path("source_ids");
        citations.put("maxItems", request.get("sources").size());
        var citationIds = ((ObjectNode) citations.path("items")).putArray("enum");
        request.get("sources").forEach(source -> {
            coverageIds.add(source.get("id").asText());
            citationIds.add(source.get("id").asText());
        });
        payload.set("format", schema);
        payload.putObject("options").put("temperature", 0).put("num_ctx", 16384).put("num_predict", 4096);
        byte[] body = JSON.writeValueAsBytes(payload);
        if (body.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Ollama context limit exceeded");
        verifyLocalModel();
        JsonNode response = post(port, "/api/generate", body, timeoutMs);
        if (response != null && model.equals(response.path("model").asText())
                && response.path("done").asBoolean() && "length".equals(response.path("done_reason").asText())) {
            throw new ClinicalSummaryOutputLimitException();
        }
        if (response == null || !response.isObject() || !response.path("done").isBoolean()
                || !response.path("done").booleanValue() || !"stop".equals(response.path("done_reason").asText())
                || !model.equals(response.path("model").asText()) || !response.path("response").isTextual()) {
            throw new IOException("Incomplete Ollama response");
        }
        return JSON.readTree(response.get("response").asText());
    }
}
