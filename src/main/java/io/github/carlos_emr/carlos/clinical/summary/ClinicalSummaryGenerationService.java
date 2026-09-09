/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.*;

/** Host-owned safety and artifact assembly. Every adapter passes through the same checks. */
public final class ClinicalSummaryGenerationService {
    public static final String ENABLED_PROPERTY = "clinical.ai_summary_generation.enabled";
    private static final Semaphore CAPACITY = new Semaphore(1);
    private static final int MAX_CLAIMS = 20;
    private static final int MAX_CLAIM_LENGTH = 240;
    private static final int MAX_COVERAGE_REASON_LENGTH = 160;
    private static final Map<String, String> SECTION_TITLES = Map.of(
            "clinical_overview", "Clinical overview",
            "active_problems", "Active problems",
            "medications_allergies", "Medications and allergies",
            "results_observations", "Results and observations",
            "plan_follow_up", "Plan and follow-up");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SOURCE_METADATA = Pattern.compile(
            "(?i)(?:\\b(source (?:note|admission|patient) id|demographic (?:number|id)|"
                    + "recorded gender identity|synthetic nhs test patient|imported development fixture|"
                    + "silver data|nmc number|gmc number|fixture revision)\\b|\\b(?:subject|type)\\s*:)");
    private static final Set<String> COMMON_WORDS = Set.of(
            "about", "after", "also", "and", "are", "been", "being", "clinical", "current", "for",
            "from", "had", "has", "have", "into", "more", "new", "noted", "patient", "recorded",
            "report", "reported", "source", "that", "the", "their", "there", "this", "was", "were",
            "with", "without");
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
            validateRows(generated, "sections", Set.of("id", "title", "claim_ids"), SECTION_TITLES.size());
            validateRows(generated, "claims", Set.of("id", "text", "source_ids"), MAX_CLAIMS);
            validateRows(generated, "coverage", Set.of("source_id", "status", "reason"), 60);
            if (generated.get("claims").isEmpty()) throw new IllegalArgumentException("Empty agent draft");
            validateReadableDraft(generated, snapshot.get("sources"));
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

    private static void validateReadableDraft(JsonNode generated, JsonNode sources) {
        Set<String> sectionIds = new HashSet<>();
        for (JsonNode section : generated.get("sections")) {
            String id = boundedText(section, "id", 80);
            String expectedTitle = SECTION_TITLES.get(id);
            if (expectedTitle == null || !expectedTitle.equals(boundedText(section, "title", 80))
                    || !sectionIds.add(id) || !section.path("claim_ids").isArray()
                    || section.get("claim_ids").isEmpty()) {
                throw new IllegalArgumentException("Invalid clinical section");
            }
        }
        if (sectionIds.isEmpty()) throw new IllegalArgumentException("Missing clinical sections");

        Map<String, Set<String>> sourceWords = new HashMap<>();
        for (JsonNode source : sources) {
            sourceWords.put(source.path("id").asText(), words(source.path("title").asText() + " "
                    + source.path("date").asText() + " " + source.path("text").asText()));
        }
        Set<String> uniqueClaims = new HashSet<>();
        for (JsonNode claim : generated.get("claims")) {
            String claimText = boundedText(claim, "text", MAX_CLAIM_LENGTH);
            if (hasLineBreak(claimText) || SOURCE_METADATA.matcher(claimText).find()
                    || !uniqueClaims.add(normalize(claimText))) {
                throw new IllegalArgumentException("Unreadable or duplicate clinical claim");
            }
            JsonNode references = claim.path("source_ids");
            if (!references.isArray() || references.isEmpty() || references.size() > 8) {
                throw new IllegalArgumentException("Invalid claim citations");
            }
            Set<String> citedWords = new HashSet<>();
            for (JsonNode reference : references) {
                Set<String> words = sourceWords.get(reference.asText());
                if (words == null) throw new IllegalArgumentException("Unknown claim citation");
                citedWords.addAll(words);
            }
            Set<String> claimWords = words(claimText);
            claimWords.removeAll(COMMON_WORDS);
            boolean supported = claimWords.stream().anyMatch(citedWords::contains);
            if (claimWords.isEmpty() || !supported) {
                throw new IllegalArgumentException("Claim lacks lexical support in cited sources");
            }
        }

        Set<String> uniqueReasons = new HashSet<>();
        for (JsonNode entry : generated.get("coverage")) {
            String reason = boundedText(entry, "reason", MAX_COVERAGE_REASON_LENGTH);
            if (hasLineBreak(reason) || !uniqueReasons.add(normalize(reason))) {
                throw new IllegalArgumentException("Unreadable or duplicate coverage reason");
            }
        }
    }

    private static String boundedText(JsonNode item, String field, int maxLength) {
        JsonNode value = item.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            throw new IllegalArgumentException("Invalid agent text");
        }
        return value.asText().strip();
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static Set<String> words(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = WORD.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 3) result.add(word);
        }
        return result;
    }
}
