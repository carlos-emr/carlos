/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;

/** Bounded model calls over every source, with lossless host assembly rather than a lossy reduce call. */
final class ClinicalSummaryGenerationPipeline {
    // Serialized bytes, including prompt and schema. Leaves room for output in a 16K context.
    static final int REQUEST_BYTES = ClinicalSummaryAgentProtocol.MIN_REQUEST_BYTES;
    private final ClinicalSummaryAgent agent;
    private final ClinicalSummaryGenerationCache cache;
    private final String identity;
    private final int requestBytes;

    ClinicalSummaryGenerationPipeline(ClinicalSummaryAgent agent, ClinicalSummaryGenerationCache cache, String identity) {
        this.agent = agent;
        this.cache = cache;
        this.identity = identity;
        this.requestBytes = agent.requestBytes();
        if (requestBytes < REQUEST_BYTES || requestBytes > ClinicalSummaryAgentProtocol.MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Invalid agent request budget");
        }
    }

    JsonNode generate(ObjectNode snapshot, ObjectNode request) throws IOException {
        ObjectNode clinicalRequest = request.deepCopy();
        ArrayNode clinicalSources = clinicalRequest.putArray("sources");
        List<JsonNode> outputs = new ArrayList<>();
        for (JsonNode source : request.get("sources")) {
            if (isHostIdentity(snapshot, source)) {
                // Identity is constructed by the host, with no clinical content. Keep it in
                // evidence and coverage, but do not ask a model to invent a summary of it.
                ObjectNode identity = JSON.createObjectNode();
                identity.putArray("sections");
                identity.putArray("claims");
                identity.putArray("coverage").addObject().put("source_id", "identity")
                        .put("status", "excluded").put("reason", "Host patient identity; no clinical content sent for generation.");
                outputs.add(identity);
            } else {
                ObjectNode clinicalSource = source.deepCopy();
                String text = source.path("text").asText();
                String boundary = "Source text below is preserved verbatim, including encoding and clinical inconsistencies.\n\n";
                // The service has already checksum-verified every NHS fixture note. Its import
                // preamble is host metadata, not clinical text; original evidence stays intact.
                if (!snapshot.path("patient_context").path("generation_fixture").asText().isBlank()
                        && source.path("id").asText().matches("note-[1-9][0-9]*")
                        && text.startsWith("SYNTHETIC NHS TEST PATIENT - NOT FOR CLINICAL USE\nImported development fixture.")
                        && text.contains(boundary)) {
                    clinicalSource.put("text", text.substring(text.indexOf(boundary) + boundary.length()));
                }
                clinicalSources.add(clinicalSource);
            }
        }
        List<ObjectNode> requests = new ArrayList<>();
        if (!clinicalSources.isEmpty()) partition(clinicalRequest, requests);
        for (ObjectNode part : requests) run(snapshot, part, requests.size() > 1, outputs);
        return outputs.size() == 1 ? outputs.getFirst() : merge(outputs, snapshot.get("sources"));
    }

    private static boolean isHostIdentity(ObjectNode snapshot, JsonNode source) {
        String patientId = snapshot.path("patient_context").path("id").asText();
        return patientId.matches("demographic-[1-9][0-9]*")
                && "identity".equals(source.path("id").asText())
                && patientId.equals(source.path("patient_id").asText())
                && source.path("text").asText().equals("Demographic number: "
                        + patientId.substring("demographic-".length()) + "\nName: "
                        + snapshot.path("patient_context").path("label").asText());
    }

    private void partition(ObjectNode request, List<ObjectNode> requests) throws IOException {
        if (JSON.writeValueAsBytes(request).length <= requestBytes) {
            requests.add(request);
            return;
        }
        if (request.get("sources").size() == 1) {
            for (ObjectNode part : split(request)) partition(part, requests);
            return;
        }
        ObjectNode batch = request.deepCopy();
        ArrayNode sources = batch.putArray("sources");
        String kind = "";
        for (JsonNode source : request.get("sources")) {
            String nextKind = source.get("id").asText().split("-", 2)[0];
            if (!sources.isEmpty() && !kind.equals(nextKind)) {
                requests.add(batch.deepCopy());
                sources.removeAll();
            }
            kind = nextKind;
            sources.add(source.deepCopy());
            if (JSON.writeValueAsBytes(batch).length > requestBytes) {
                sources.remove(sources.size() - 1);
                if (!sources.isEmpty()) requests.add(batch.deepCopy());
                sources.removeAll();
                sources.add(source.deepCopy());
                if (JSON.writeValueAsBytes(batch).length > requestBytes) {
                    partition(batch.deepCopy(), requests);
                    sources.removeAll();
                }
            }
        }
        if (!sources.isEmpty()) requests.add(batch.deepCopy());
        // Stable sequential packing keeps earlier batches reusable when a note is appended.
        requests.forEach(part -> part.put("request_id", UUID.randomUUID().toString()));
    }

    private void run(ObjectNode snapshot, ObjectNode request, boolean cachePart, List<JsonNode> outputs) throws IOException {
        ObjectNode part = snapshot.deepCopy();
        part.set("sources", request.get("sources").deepCopy());
        for (String field : List.of("claims", "sections", "coverage", "fact_ledger", "validation")) part.putArray(field);
        // Each part depends only on its own evidence and patient scope. An unrelated chart edit
        // invalidates the final-result cache, but need not regenerate unchanged source portions.
        String key = cachePart && cache != null && identity != null ? ClinicalSummaryGenerationCache.key(part, request, identity) : null;
        verifyRevision();
        if (key != null) {
            ClinicalSummaryArtifact hit = cache.get(key);
            if (hit != null) {
                outputs.add(output(JSON.valueToTree(hit.getView())));
                return;
            }
        }
        JsonNode generated;
        try {
            generated = agent.generate(request.deepCopy());
        } catch (ClinicalSummaryOutputLimitException exhausted) {
            // Never accept a truncated JSON response. Retry smaller input portions, or fail the
            // whole draft if a minimal portion still cannot be completed.
            for (ObjectNode smaller : split(request)) run(snapshot, smaller, true, outputs);
            return;
        }
        generated = completeCoverage(generated, part.get("sources"));
        ClinicalSummaryGenerationService.validateGenerated(generated, part.get("sources"), true);
        for (String field : List.of("sections", "claims", "coverage")) part.set(field, generated.get(field).deepCopy());
        ClinicalSummaryArtifact validated = new ClinicalSummaryArtifact(part);
        if (!validated.isRenderable()) throw new IllegalArgumentException("Invalid source portion");
        verifyRevision();
        if (key != null) cache.put(key, validated);
        outputs.add(generated);
    }

    private void verifyRevision() throws IOException {
        if (identity != null && !identity.equals(agent.cacheIdentity())) {
            throw new IOException("Model revision changed during generation");
        }
    }

    private static ObjectNode output(ObjectNode artifact) {
        ObjectNode result = JSON.createObjectNode();
        for (String field : List.of("sections", "claims", "coverage")) result.set(field, artifact.get(field));
        return result;
    }

    private static List<ObjectNode> split(ObjectNode request) throws IOException {
        ArrayNode sources = (ArrayNode) request.get("sources");
        ObjectNode left = request.deepCopy().put("request_id", UUID.randomUUID().toString());
        ObjectNode right = request.deepCopy().put("request_id", UUID.randomUUID().toString());
        ArrayNode first = left.putArray("sources");
        ArrayNode second = right.putArray("sources");
        if (sources.size() > 1) {
            for (int i = 0; i < sources.size(); i++) (i < sources.size() / 2 ? first : second).add(sources.get(i).deepCopy());
        } else {
            ObjectNode source = (ObjectNode) sources.get(0);
            String text = source.path("text").asText();
            if (text.length() < 1024) throw new ClinicalSummaryOutputLimitException();
            int middle = text.length() / 2;
            // Prefer a paragraph boundary, then a sentence boundary. Preserve surrounding context
            // on both sides; never drop text, split a surrogate pair, or discard a long final tail.
            int boundary = text.lastIndexOf('\n', middle);
            if (boundary < middle / 2) boundary = text.lastIndexOf(". ", middle);
            if (boundary >= middle / 2) middle = boundary + 1;
            int end = Math.min(text.length(), middle + 128);
            int start = Math.max(0, middle - 128);
            if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) end++;
            if (start > 0 && Character.isLowSurrogate(text.charAt(start))) start--;
            first.add(source.deepCopy().put("text", text.substring(0, end)));
            second.add(source.deepCopy().put("text", text.substring(start)));
        }
        return List.of(left, right);
    }

    static final String UNEXPLAINED = "no statement cites this note and the model gave no reason; recorded by the host.";

    /** Sources the agent neither cited nor reviewed, as recorded by {@link #completeCoverage}. */
    static List<String> unexplainedSources(JsonNode generated) {
        List<String> result = new ArrayList<>();
        for (JsonNode entry : generated.path("coverage")) {
            if (entry.path("reason").asText().endsWith(UNEXPLAINED)) result.add(entry.path("source_id").asText());
        }
        return result;
    }

    /**
     * Records each cited source on the agent's behalf; the agent reviews only sources it did not cite.
     * A source the agent neither cited nor reviewed is recorded as exactly that. It is never described
     * as reviewed by the agent, and it does not fail the draft: a short draft from a weaker stack shows
     * its gaps instead of showing nothing. Malformed output is returned unchanged for validation to reject.
     */
    static JsonNode completeCoverage(JsonNode generated, JsonNode sources) {
        if (generated == null || !generated.isObject() || !generated.path("claims").isArray()
                || !generated.path("coverage").isArray()) {
            return generated;
        }
        ObjectNode result = generated.deepCopy();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode claim : result.get("claims")) {
            Set<String> references = new LinkedHashSet<>();
            claim.path("source_ids").forEach(reference -> references.add(reference.asText()));
            references.forEach(reference -> counts.merge(reference, 1, Integer::sum));
        }
        ArrayNode coverage = (ArrayNode) result.get("coverage");
        // A host record of an unexplained note is stale once a later host statement cites that note.
        for (int i = coverage.size() - 1; i >= 0; i--) {
            JsonNode entry = coverage.get(i);
            if (counts.containsKey(entry.path("source_id").asText()) && entry.path("reason").asText().endsWith(UNEXPLAINED)) {
                coverage.remove(i);
            }
        }
        Set<String> reviewed = new LinkedHashSet<>();
        coverage.forEach(entry -> reviewed.add(entry.path("source_id").asText()));
        for (JsonNode entry : coverage) {
            // Citations are a host-known fact; the agent's reason is kept.
            if (entry.isObject() && counts.containsKey(entry.path("source_id").asText())) {
                ((ObjectNode) entry).put("status", "cited");
            }
        }
        for (JsonNode source : sources) {
            String id = source.path("id").asText();
            Integer count = counts.get(id);
            if (count != null && !reviewed.contains(id)) {
                coverage.addObject().put("source_id", id).put("status", "cited")
                        .put("reason", id + ": cited by " + count + (count == 1 ? " statement" : " statements")
                                + " in this draft; recorded by the host.");
            }
            if (count == null && !reviewed.contains(id)) {
                coverage.addObject().put("source_id", id).put("status", "reviewed_not_cited")
                        .put("reason", id + ": " + UNEXPLAINED);
            }
        }
        return result;
    }

    private static ObjectNode merge(List<JsonNode> outputs, JsonNode sources) {
        ObjectNode result = JSON.createObjectNode();
        ArrayNode sections = result.putArray("sections");
        ArrayNode claims = result.putArray("claims");
        ArrayNode coverage = result.putArray("coverage");
        Map<String, ObjectNode> unique = new LinkedHashMap<>();
        Map<String, ArrayNode> members = new LinkedHashMap<>();
        Map<String, List<JsonNode>> reviews = new LinkedHashMap<>();
        for (JsonNode part : outputs) {
            Map<String, JsonNode> claimSections = new LinkedHashMap<>();
            for (JsonNode section : part.get("sections")) {
                for (JsonNode id : section.get("claim_ids")) claimSections.put(id.asText(), section);
            }
            for (JsonNode claim : part.get("claims")) {
                String normalized = claim.get("text").asText().strip();
                ObjectNode existing = unique.get(normalized);
                if (existing == null) {
                    existing = claim.deepCopy();
                    existing.put("id", "claim-" + (unique.size() + 1));
                    unique.put(normalized, existing);
                    claims.add(existing);
                    JsonNode section = claimSections.get(claim.get("id").asText());
                    ArrayNode ids = members.computeIfAbsent(section.get("id").asText(), id ->
                            sections.addObject().put("id", id).put("title", section.get("title").asText()).putArray("claim_ids"));
                    ids.add(existing.get("id").asText());
                } else {
                    Set<String> ids = new LinkedHashSet<>();
                    existing.get("source_ids").forEach(id -> ids.add(id.asText()));
                    claim.get("source_ids").forEach(id -> ids.add(id.asText()));
                    ArrayNode citations = existing.putArray("source_ids");
                    ids.forEach(citations::add);
                }
            }
            for (JsonNode entry : part.get("coverage")) {
                reviews.computeIfAbsent(entry.get("source_id").asText(), id -> new ArrayList<>()).add(entry);
            }
        }
        Set<String> cited = new LinkedHashSet<>();
        claims.forEach(claim -> claim.get("source_ids").forEach(id -> cited.add(id.asText())));
        for (JsonNode source : sources) {
            String id = source.get("id").asText();
            List<JsonNode> entries = reviews.get(id);
            if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("Unprocessed source");
            boolean excluded = entries.stream().allMatch(entry -> "excluded".equals(entry.get("status").asText()));
            Set<String> reasons = new LinkedHashSet<>();
            entries.forEach(entry -> reasons.add(entry.get("status").asText() + ": " + entry.get("reason").asText()));
            coverage.addObject().put("source_id", id)
                    .put("status", cited.contains(id) ? "cited" : excluded ? "excluded" : "reviewed_not_cited")
                    .put("reason", id + " — all " + entries.size() + " portions processed. " + String.join("; ", reasons));
        }
        return result;
    }
}
