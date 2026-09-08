/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.CarlosProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Bounded local-only inference. No subprocesses, proxies, redirects, persistence or cloud models. */
public final class LocalClinicalSummaryGenerator {
    public static final String ENABLED_PROPERTY = "clinical.ai_summary_generation.enabled";
    private static final String PREFIX = "clinical.ai_summary_generation.ollama.";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Semaphore CAPACITY = new Semaphore(1);
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 60000;
    private static final Set<String> MODELS = Set.of("qwen3.5:0.8b", "qwen3.5:2b", "qwen3.5:4b", "qwen3.5:9b");
    private final int port;
    private final String model;
    private final int timeoutMs;

    public LocalClinicalSummaryGenerator() {
        this(Integer.parseInt(CarlosProperties.getInstance().getProperty(PREFIX + "port", "11434")),
                CarlosProperties.getInstance().getProperty(PREFIX + "model", "qwen3.5:2b"),
                timeoutMillis(CarlosProperties.getInstance().getProperty(PREFIX + "timeoutSeconds", "600")));
    }

    LocalClinicalSummaryGenerator(int port, String model, int timeoutMs) {
        if (port < 1 || port > 65535 || !MODELS.contains(model) || timeoutMs < 1 || timeoutMs > 1800000) {
            throw new IllegalArgumentException("Invalid local inference configuration");
        }
        this.port = port;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    static int timeoutMillis(String configured) {
        int seconds = Integer.parseInt(configured);
        if (seconds < 1 || seconds > 1800) {
            throw new IllegalArgumentException("Local inference timeout must be between 1 and 1800 seconds");
        }
        return seconds * 1000;
    }

    public ClinicalSummaryArtifact generate(ClinicalSummaryArtifact chart) throws ClinicalSummaryGenerationException {
        if (!SyntheticSummaryScope.isEligible(chart)) {
            throw new ClinicalSummaryGenerationException("Generation is limited to complete, unmodified NHS synthetic fixtures. No chart data was sent.");
        }
        if (!CAPACITY.tryAcquire()) {
            throw new ClinicalSummaryGenerationException("The local model is already generating a draft. Try again when it finishes.");
        }
        try {
            ObjectNode snapshot = MAPPER.valueToTree(chart.getView());
            ObjectNode payload = MAPPER.createObjectNode();
            ObjectNode bundle = MAPPER.createObjectNode();
            bundle.set("sources", snapshot.get("sources").deepCopy());
            // Send only verified source text; metadata and the ledger stay host-owned.
            JsonNode schema = MAPPER.readTree(resource("generation-schema.json"));
            payload.put("model", model).put("system", resource("generation-prompt.txt"))
                    .put("prompt", MAPPER.writeValueAsString(bundle)).put("stream", false).put("think", false)
                    .put("keep_alive", "5m");
            payload.set("format", schema);
            payload.putObject("options").put("temperature", 0).put("num_ctx", 65536).put("num_predict", 4096);
            byte[] body = MAPPER.writeValueAsBytes(payload);
            // UTF-8 bytes are a conservative token bound, leaving space for output and the model template.
            if (body.length > MAX_REQUEST_BYTES) {
                throw new ClinicalSummaryGenerationException("The source bundle exceeds this prototype's context limit. No chart data was sent.");
            }
            JsonNode info = request("show", MAPPER.writeValueAsBytes(MAPPER.createObjectNode().put("model", model)));
            if (info == null || !info.isObject() || !info.path("remote_model").asText("").isBlank()
                    || !info.path("remote_host").asText("").isBlank()) {
                throw new ClinicalSummaryGenerationException("Cloud-backed models are not permitted. No chart data was sent.");
            }
            JsonNode response = request("generate", body);
            if (response == null || !response.isObject() || !response.path("done").isBoolean() || !response.path("done").booleanValue()
                    || !"stop".equals(response.path("done_reason").asText())
                    || !model.equals(response.path("model").asText())
                    || !response.path("response").isTextual()) {
                throw new ClinicalSummaryGenerationException("The model did not finish a complete draft. The chart extract is unchanged.");
            }
            JsonNode generated = MAPPER.readTree(response.get("response").asText());
            exactFields(generated, Set.of("sections", "claims", "coverage"));
            validateRows(generated, "sections", Set.of("id", "title", "claim_ids"), 20);
            validateRows(generated, "claims", Set.of("id", "text", "source_ids"), 100);
            validateRows(generated, "coverage", Set.of("source_id", "status", "reason"), 60);
            if (generated.get("claims").isEmpty()) {
                throw new IllegalArgumentException("Empty model draft");
            }
            for (String key : Set.of("sections", "claims", "coverage")) {
                snapshot.set(key, generated.get(key).deepCopy());
            }
            snapshot.put("artifact_id", "ai-" + UUID.randomUUID()).put("generated_at", Instant.now().toString())
                    .put("model", model + " via local Ollama (unverified draft)");
            ArrayNode findings = (ArrayNode) snapshot.get("validation");
            for (JsonNode finding : findings) {
                if ("partial_chart".equals(finding.path("code").asText())) {
                    ((ObjectNode) finding).put("message", "AI draft covers only the included source snapshot. Labs, documents, forms and other chart sections are not included. Missing information is not a negative clinical finding.");
                }
            }
            findings.addObject().put("severity", "warning").put("code", "ai_review_required")
                    .put("message", "Unverified AI draft for synthetic testing only. Reference checks do not establish support, clinical accuracy or completeness. Nothing has been saved to the chart.")
                    .putArray("source_ids");
            return new ClinicalSummaryArtifact(snapshot);
        } catch (SocketTimeoutException timeout) {
            throw new ClinicalSummaryGenerationException("Local generation timed out. The chart extract is unchanged.");
        } catch (IOException unavailable) {
            throw new ClinicalSummaryGenerationException("Local Ollama or the configured model is unavailable, or returned an unreadable response. The chart extract is unchanged.");
        } catch (IllegalArgumentException invalid) {
            throw new ClinicalSummaryGenerationException("The generated draft failed validation and was not displayed. The original chart evidence is unchanged.");
        } finally {
            CAPACITY.release();
        }
    }

    private JsonNode request(String endpoint, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/api/" + endpoint)
                .toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (var output = connection.getOutputStream()) {
                output.write(body);
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Local inference request rejected");
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("Local inference response too large");
                }
                return MAPPER.readTree(bytes);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = LocalClinicalSummaryGenerator.class.getResourceAsStream("/clinical/summary/" + name)) {
            if (input == null) {
                throw new IOException("Missing inference resource");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void exactFields(JsonNode object, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        if (object == null || !object.isObject()) {
            throw new IllegalArgumentException("Expected model object");
        }
        object.fieldNames().forEachRemaining(actual::add);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Unexpected model fields");
        }
    }

    private static void validateRows(JsonNode generated, String name, Set<String> fields, int max) {
        JsonNode rows = generated.path(name);
        if (!rows.isArray() || rows.size() > max) {
            throw new IllegalArgumentException("Invalid model collection");
        }
        rows.forEach(row -> exactFields(row, fields));
    }
}
