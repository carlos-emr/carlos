/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Versioned rendering contract. Checks provenance structure, not clinical truth of generated prose. */
public final class ClinicalSummaryArtifact {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonNode artifact;

    public ClinicalSummaryArtifact(JsonNode input) {
        require(input != null && input.isObject(), "Artifact must be an object");
        artifact = input.deepCopy();
        validate();
    }

    public Map<String, Object> getView() {
        return MAPPER.convertValue(artifact, new TypeReference<Map<String, Object>>() { });
    }

    public Map<String, Map<String, Object>> getClaimsById() {
        Map<String, Map<String, Object>> claims = new LinkedHashMap<>();
        for (JsonNode claim : artifact.get("claims")) {
            claims.put(claim.get("id").asText(), MAPPER.convertValue(claim,
                    new TypeReference<Map<String, Object>>() { }));
        }
        return claims;
    }

    public boolean isRenderable() {
        for (JsonNode finding : artifact.get("validation")) {
            if ("error".equals(finding.get("severity").asText())) {
                return false;
            }
        }
        return true;
    }

    private void validate() {
        require(artifact.path("schema_version").isIntegralNumber()
                && artifact.path("schema_version").canConvertToInt()
                && artifact.path("schema_version").asInt() == 1, "Unsupported schema version");
        for (String key : new String[]{"artifact_id", "generated_at", "model", "workflow"}) {
            text(artifact, key);
        }
        try {
            java.time.Instant.parse(text(artifact, "generated_at"));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid generation timestamp", e);
        }
        JsonNode patient = artifact.path("patient_context");
        require(patient.path("synthetic").isBoolean(), "Explicit synthetic data flag required");
        String patientId = text(patient, "id");
        text(patient, "label");
        Map<String, JsonNode> sources = index("sources");
        Map<String, JsonNode> claims = index("claims");
        Map<String, JsonNode> facts = index("fact_ledger");
        Map<String, JsonNode> sections = index("sections");
        require(!sources.isEmpty(), "Sources must not be empty");
        for (JsonNode source : sources.values()) {
            require(patientId.equals(text(source, "patient_id")), "Source belongs to another patient");
            text(source, "title");
            text(source, "date");
            text(source, "text");
        }
        Set<String> cited = new HashSet<>();
        for (JsonNode claim : claims.values()) {
            text(claim, "text");
            cited.addAll(references(claim, "source_ids", sources.keySet(), true));
        }
        for (JsonNode fact : facts.values()) {
            text(fact, "text");
            text(fact, "category");
            references(fact, "source_ids", sources.keySet(), true);
        }
        Set<String> placedClaims = new HashSet<>();
        for (JsonNode section : sections.values()) {
            text(section, "title");
            for (String id : references(section, "claim_ids", claims.keySet(), false)) {
                require(placedClaims.add(id), "Claim appears in multiple sections");
            }
        }
        require(placedClaims.equals(claims.keySet()), "Every claim must appear in a section");
        Set<String> covered = new HashSet<>();
        for (JsonNode entry : array(artifact, "coverage")) {
            String sourceId = text(entry, "source_id");
            require(sources.containsKey(sourceId) && covered.add(sourceId), "Invalid or duplicate coverage source");
            String status = text(entry, "status");
            require(Set.of("cited", "reviewed_not_cited", "excluded").contains(status), "Invalid coverage status");
            require(cited.contains(sourceId) == "cited".equals(status), "Coverage disagrees with citations");
            text(entry, "reason");
        }
        require(covered.equals(sources.keySet()), "Coverage must account for every source");
        for (JsonNode finding : array(artifact, "validation")) {
            require(Set.of("pass", "warning", "error").contains(text(finding, "severity")), "Invalid severity");
            text(finding, "code");
            text(finding, "message");
            references(finding, "source_ids", sources.keySet(), false);
        }
    }

    private Map<String, JsonNode> index(String key) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode item : array(artifact, key)) {
            String id = text(item, "id");
            require(id.matches("[A-Za-z0-9_-]{1,80}"), "Invalid identifier");
            require(result.put(id, item) == null, "Duplicate identifier");
        }
        return result;
    }

    private static Set<String> references(JsonNode item, String key, Set<String> known, boolean nonempty) {
        Set<String> result = new HashSet<>();
        JsonNode values = array(item, key);
        require(!nonempty || !values.isEmpty(), "Missing citation");
        for (JsonNode value : values) {
            require(value.isTextual() && known.contains(value.asText()) && result.add(value.asText()),
                    "Unknown or duplicate reference");
        }
        return result;
    }

    private static JsonNode array(JsonNode item, String key) {
        JsonNode value = item.path(key);
        require(value.isArray(), "Expected array: " + key);
        return value;
    }

    private static String text(JsonNode item, String key) {
        JsonNode value = item.path(key);
        require(value.isTextual() && !value.asText().isBlank(), "Expected text: " + key);
        return value.asText();
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
