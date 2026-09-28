/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;

/** The model selects source passages; the host owns identity, comparison, review and writes. */
public final class ChartUpdateProposals {
    public static final String ENABLED = "clinical.ai_chart_updates.enabled";
    private static final Semaphore CAPACITY = new Semaphore(1);
    private final ClinicalSummaryAgent agent;

    public record Proposal(String kind, String evidence) implements Serializable {
        public String getKind() { return kind; }
        public String getEvidence() { return evidence; }
        public String key() { return hash(kind + "\n" + evidence); }
    }

    public ChartUpdateProposals() { this(ClinicalSummaryAgents.configuredChartUpdates()); }
    public ChartUpdateProposals(ClinicalSummaryAgent agent) { this.agent = agent; }
    public String displayName() { return agent.displayName(); }

    public List<Proposal> generate(String source) throws ClinicalSummaryGenerationException {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Readable source required");
        if (!CAPACITY.tryAcquire()) throw new ClinicalSummaryGenerationException("Another proposal request is running. Try again shortly.");
        try {
            ObjectNode request = JSON.createObjectNode().put("contract_version", 1)
                    .put("request_id", UUID.randomUUID().toString()).put("workflow", "chart-update-proposals")
                    .put("data_classification", "clinical-document")
                    .put("instructions", ClinicalSummaryAgentProtocol.resource("chart-update-prompt.txt"));
            request.putArray("sources").addObject().put("id", "document").put("title", "Document").put("text", source);
            request.set("output_schema", JSON.readTree(ClinicalSummaryAgentProtocol.resource("chart-update-schema.json")));
            if (JSON.writeValueAsBytes(request).length > agent.requestBytes()) {
                throw new ClinicalSummaryGenerationException("This document exceeds the configured request budget. No partial proposals were generated.");
            }
            return validate(agent.generate(request), source);
        } catch (IOException | RuntimeException invalid) {
            throw new ClinicalSummaryGenerationException("Proposals could not be generated or failed source validation. Nothing was saved.");
        } finally {
            CAPACITY.release();
        }
    }

    public static List<Proposal> validate(JsonNode output, String source) {
        ClinicalSummaryAgentProtocol.exactFields(output, Set.of("proposals"));
        JsonNode rows = output.path("proposals");
        if (!rows.isArray() || rows.size() > 20) throw new IllegalArgumentException("Invalid proposals");
        List<Proposal> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            ClinicalSummaryAgentProtocol.exactFields(row, Set.of("kind", "evidence"));
            if (!row.path("kind").isTextual() || !Set.of("tickler", "history").contains(row.get("kind").asText())) {
                throw new IllegalArgumentException("Unsupported proposal kind");
            }
            JsonNode excerpt = row.path("evidence");
            if (!excerpt.isTextual() || excerpt.asText().isBlank() || excerpt.asText().length() > 2000
                    || !source.contains(excerpt.asText()) || !seen.add(excerpt.asText())) {
                throw new IllegalArgumentException("Invalid or duplicate source passage");
            }
            result.add(new Proposal(row.get("kind").asText(), excerpt.asText()));
        }
        return List.copyOf(result);
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
