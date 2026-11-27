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

/**
 * One small further model call to rewrite only the statements the host faulted.
 *
 * <p>The host decides what is faulted and whether a rewrite is accepted; the agent only writes. A
 * rewrite is kept only if the host can no longer fault it; it keeps its citations and takes the ID
 * prefix {@code repaired-}, so the draft records how it was made. A restatement the agent omits
 * from its answer is dropped. Nothing is rewritten silently.
 *
 * @since 2026-09-22
 */
final class ClinicalSummaryRepair {
    static final int CONTRACT_VERSION = 2;
    static final String PREFIX = "repaired-";
    static final String INSTRUCTIONS =
            "You are correcting individual statements of a clinical summary. For each statement you receive its "
            + "text, the IDs of the notes it cites, the text of those notes, and the problems the host found. Return "
            + "each statement rewritten so that every problem is gone, using only facts the cited notes record, in "
            + "one paragraph with no line breaks. Refer to staff and the patient by role only, never by name, and "
            + "never include NHS numbers, dates of birth or ages. Write dates only as the cited notes carry them. "
            + "Never state that a medication was switched, changed or replaced unless a note records it. If a "
            + "statement only restates another and adds nothing, omit it from your answer. Return JSON with exactly "
            + "statements, each with id and text.";

    private ClinicalSummaryRepair() { }

    /** Statements the host can fault, each with plain reasons the agent can act on. */
    static Map<String, List<String>> flagged(JsonNode generated, JsonNode sources, String patientLabel,
                                             Map<String, String> classes) {
        Map<String, List<String>> reasons = new LinkedHashMap<>();
        for (ClinicalSummaryHostChecks.DateFinding finding : ClinicalSummaryHostChecks.dateFindings(generated, sources)) {
            reasons.computeIfAbsent(finding.claimId(), id -> new ArrayList<>()).add("asserts the date " + finding.asserted()
                    + ", which its cited notes do not carry; remove that date, or use only a date they carry: "
                    + String.join(", ", finding.allowed()));
        }
        for (ClinicalSummaryHostChecks.NameFinding finding : ClinicalSummaryHostChecks.nameFindings(generated, sources, patientLabel)) {
            reasons.computeIfAbsent(finding.claimId(), id -> new ArrayList<>()).add("names a person or gives a patient identifier ("
                    + String.join(", ", finding.terms()) + "); refer to people by role only and omit identifiers");
        }
        for (ClinicalSummaryHostChecks.ChangeFinding finding : ClinicalSummaryHostChecks.undocumentedChanges(generated, sources, classes)) {
            reasons.computeIfAbsent(finding.claimId(), id -> new ArrayList<>()).add("describes a switch or change between "
                    + String.join(" and ", finding.drugs()) + " that no note records; state only what each note records");
        }
        for (ClinicalSummaryHostChecks.Restatement pair : ClinicalSummaryHostChecks.nearDuplicates(generated)) {
            reasons.computeIfAbsent(pair.shorter(), id -> new ArrayList<>()).add("restates statement " + pair.longer()
                    + ", which is in another section; return it only if it adds a distinct fact, otherwise omit it");
        }
        reasons.keySet().removeIf(id -> id.startsWith("host-"));
        return reasons;
    }

    /** The agent request: only the faulted statements, their problems, and the notes they cite. */
    static ObjectNode request(JsonNode generated, JsonNode sources, Map<String, List<String>> flagged) {
        ObjectNode request = JSON.createObjectNode().put("contract_version", CONTRACT_VERSION)
                .put("request_id", UUID.randomUUID().toString()).put("workflow", "patient-overview")
                .put("data_classification", "verified-synthetic").put("instructions", INSTRUCTIONS);
        ArrayNode statements = request.putArray("statements");
        Set<String> cited = new LinkedHashSet<>();
        for (JsonNode claim : generated.get("claims")) {
            List<String> problems = flagged.get(claim.path("id").asText());
            if (problems == null) continue;
            ObjectNode statement = statements.addObject().put("id", claim.path("id").asText()).put("text", claim.path("text").asText());
            statement.set("source_ids", claim.get("source_ids").deepCopy());
            ArrayNode list = statement.putArray("problems");
            problems.forEach(list::add);
            claim.path("source_ids").forEach(id -> cited.add(id.asText()));
        }
        ArrayNode requestSources = request.putArray("sources");
        for (JsonNode source : sources) if (cited.contains(source.path("id").asText())) requestSources.add(source.deepCopy());
        return request;
    }

    /**
     * Applies an agent's answer. {@code sources} is the host's evidence for the checks and
     * {@code agentSources} the same notes as the agent may see them. Returns the draft unchanged
     * when the agent does not support repair,
     * fails, or answers unusably; a rewrite the host can still fault is not accepted.
     */
    static JsonNode apply(ClinicalSummaryAgent agent, JsonNode generated, JsonNode sources, JsonNode agentSources,
                          String patientLabel, Map<String, String> classes) {
        Map<String, List<String>> flagged = flagged(generated, sources, patientLabel, classes);
        if (flagged.isEmpty()) return generated;
        JsonNode reply;
        try {
            // The agent receives the notes as generation sent them, never the host's evidence copy.
            reply = agent.repair(request(generated, agentSources, flagged));
        } catch (IOException | RuntimeException failed) {
            return generated;  // The faulted statements stay as they were, with their warnings.
        }
        if (reply == null || !reply.path("statements").isArray()) return generated;
        Map<String, String> replacement = new LinkedHashMap<>();
        for (JsonNode statement : reply.get("statements")) {
            String id = statement.path("id").asText();
            String text = statement.path("text").asText().strip();
            if (flagged.containsKey(id) && !text.isEmpty() && !text.contains("\n") && !text.contains("\r")) replacement.put(id, text);
        }
        ObjectNode repaired = generated.deepCopy();
        Map<String, String> original = new LinkedHashMap<>();
        for (JsonNode claim : repaired.get("claims")) {
            String id = claim.path("id").asText();
            original.put(id, claim.path("text").asText());
            if (replacement.containsKey(id)) ((ObjectNode) claim).put("text", replacement.get(id));
        }
        Map<String, List<String>> still = flagged(repaired, sources, patientLabel, classes);
        Set<String> accepted = new LinkedHashSet<>();
        for (JsonNode claim : repaired.get("claims")) {
            String id = claim.path("id").asText();
            if (!replacement.containsKey(id)) continue;
            if (still.containsKey(id) || replacement.get(id).equals(original.get(id))) {
                ((ObjectNode) claim).put("text", original.get(id));
            } else {
                accepted.add(id);
            }
        }
        Set<String> omitted = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : flagged.entrySet()) {
            if (!replacement.containsKey(entry.getKey())
                    && entry.getValue().stream().anyMatch(reason -> reason.startsWith("restates statement"))) {
                omitted.add(entry.getKey());
            }
        }
        ClinicalSummaryHostChecks.dropClaims(repaired, omitted);
        if (accepted.isEmpty() && omitted.isEmpty()) return generated;
        for (JsonNode claim : repaired.get("claims")) {
            if (accepted.contains(claim.path("id").asText())) ((ObjectNode) claim).put("id", PREFIX + claim.path("id").asText());
        }
        for (JsonNode section : repaired.get("sections")) {
            ArrayNode ids = (ArrayNode) section.get("claim_ids");
            for (int i = 0; i < ids.size(); i++) if (accepted.contains(ids.get(i).asText())) ids.set(i, PREFIX + ids.get(i).asText());
        }
        return repaired;
    }

    static int repairedCount(JsonNode generated) {
        int count = 0;
        for (JsonNode claim : generated.path("claims")) if (claim.path("id").asText().startsWith(PREFIX)) count++;
        return count;
    }
}
