/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.*;

/** Host-owned safety and artifact assembly. Every adapter passes through the same checks. */
public final class ClinicalSummaryGenerationService {
    public static final String ENABLED_PROPERTY = "clinical.ai_summary_generation.enabled";
    private static final Semaphore CAPACITY = new Semaphore(1);
    private final ClinicalSummaryAgent agent;

    public ClinicalSummaryGenerationService() { this(ClinicalSummaryAgents.configured()); }

    public ClinicalSummaryGenerationService(ClinicalSummaryAgent agent) {
        this.agent = Objects.requireNonNull(agent);
    }

    public ClinicalSummaryArtifact generate(ClinicalSummaryArtifact chart) throws ClinicalSummaryGenerationException {
        if (!SyntheticSummaryScope.isEligible(chart)) {
            throw new ClinicalSummaryGenerationException("Generation is limited to complete, unmodified NHS synthetic fixtures. No chart data was sent.");
        }
        if (!CAPACITY.tryAcquire()) {
            throw new ClinicalSummaryGenerationException("An agent is already generating a draft. Try again when it finishes.");
        }
        try {
            ObjectNode snapshot = JSON.valueToTree(chart.getView());
            ObjectNode request = JSON.createObjectNode().put("contract_version", VERSION)
                    .put("request_id", UUID.randomUUID().toString()).put("workflow", "patient-overview")
                    .put("data_classification", "verified-synthetic")
                    .put("instructions", resource("generation-prompt.txt"));
            request.set("sources", snapshot.get("sources").deepCopy());
            request.set("output_schema", JSON.readTree(resource("generation-schema.json")));
            if (JSON.writeValueAsBytes(request).length > MAX_REQUEST_BYTES) {
                throw new ClinicalSummaryGenerationException("The source bundle exceeds this prototype's context limit. No chart data was sent.");
            }
            String name = agent.displayName();
            if (name == null || name.isBlank() || name.length() > 224 || name.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid agent name");
            }
            // Adapters receive an isolated data copy, never the chart artifact or CARLOS session.
            JsonNode generated = agent.generate(request.deepCopy());
            if (JSON.writeValueAsBytes(generated).length > MAX_RESPONSE_BYTES) {
                throw new IllegalArgumentException("Oversized agent output");
            }
            exactFields(generated, Set.of("sections", "claims", "coverage"));
            validateRows(generated, "sections", Set.of("id", "title", "claim_ids"), 20);
            validateRows(generated, "claims", Set.of("id", "text", "source_ids"), 100);
            validateRows(generated, "coverage", Set.of("source_id", "status", "reason"), 60);
            if (generated.get("claims").isEmpty()) throw new IllegalArgumentException("Empty agent draft");
            for (String key : Set.of("sections", "claims", "coverage")) {
                snapshot.set(key, generated.get(key).deepCopy());
            }
            snapshot.put("artifact_id", "ai-" + UUID.randomUUID()).put("generated_at", Instant.now().toString())
                    .put("model", name + " (unverified draft)");
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
            throw new ClinicalSummaryGenerationException("Agent generation timed out. The chart extract is unchanged.");
        } catch (IOException unavailable) {
            throw new ClinicalSummaryGenerationException("The configured agent is unavailable or returned an unreadable or incomplete response. The chart extract is unchanged.");
        } catch (RuntimeException invalid) {
            // Third-party adapter exceptions may contain source text; never expose their messages or causes.
            throw new ClinicalSummaryGenerationException("The agent draft failed validation or generation and was not displayed. The original chart evidence is unchanged.");
        } finally {
            CAPACITY.release();
        }
    }

    private static void validateRows(JsonNode generated, String name, Set<String> fields, int max) {
        JsonNode rows = generated.path(name);
        if (!rows.isArray() || rows.size() > max) throw new IllegalArgumentException("Invalid agent collection");
        rows.forEach(row -> exactFields(row, fields));
    }
}
