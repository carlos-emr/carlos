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
    public static final int MAX_PROPOSALS = 200;
    private static final Semaphore CAPACITY = new Semaphore(1);
    private final ClinicalSummaryAgent agent;

    public record Proposal(String kind, String evidence, String destination) implements Serializable {
        public Proposal(String kind, String evidence) { this(kind, evidence, ""); }
        public String getKind() { return kind; }
        public String getEvidence() { return evidence; }
        public String getDestination() { return destination; }
        public String key() { return hash(kind + "\n" + evidence); }
    }

    public ChartUpdateProposals() { this(ClinicalSummaryAgents.configuredChartUpdates()); }
    public ChartUpdateProposals(ClinicalSummaryAgent agent) { this.agent = agent; }
    public String displayName() { return agent.displayName(); }

    public record Report(List<Proposal> proposals, ChartUpdateCoverage coverage) {}

    public List<Proposal> generate(String source) throws ClinicalSummaryGenerationException {
        return generateReport(source).proposals();
    }

    public Report generateReport(String source) throws ClinicalSummaryGenerationException {
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
            JsonNode output = agent.generate(request);
            // A direct model cannot attest that additional passes ran. Only the configured
            // orchestration service may supply an audit; legacy/direct results stay usable.
            if (!agent.providesChartCoverageAudit() && output instanceof ObjectNode object && output.has("coverage")) {
                output = object.deepCopy().without("coverage");
            }
            return validateReport(output, source);
        } catch (ClinicalSummaryOutputLimitException truncated) {
            // Truncated output is never validated; say why so the clinician can retry with a shorter document.
            throw new ClinicalSummaryGenerationException("The model could not finish this document within its output limit. No partial proposals were generated.");
        } catch (IOException | RuntimeException invalid) {
            throw new ClinicalSummaryGenerationException("Proposals could not be generated or failed source validation. Nothing was saved.");
        } finally {
            CAPACITY.release();
        }
    }

    public static List<Proposal> validate(JsonNode output, String source) {
        return validateReport(output, source).proposals();
    }

    private static Report validateReport(JsonNode output, String source) {
        ClinicalSummaryAgentProtocol.exactFields(output, output != null && output.has("coverage")
                ? Set.of("proposals", "coverage") : Set.of("proposals"));
        ChartUpdateCoverage coverage = ChartUpdateCoverage.parse(output.get("coverage"), source);
        JsonNode rows = output.path("proposals");
        if (!rows.isArray() || rows.size() > MAX_PROPOSALS) throw new IllegalArgumentException("Invalid proposals");
        List<Proposal> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        var boundaries = new PassageBoundaries(source);
        for (JsonNode row : rows) {
            ClinicalSummaryAgentProtocol.exactFields(row, row.has("destination")
                    ? Set.of("kind", "evidence", "destination") : Set.of("kind", "evidence"));
            if (!row.path("kind").isTextual() || !Set.of("tickler", "history", "review").contains(row.get("kind").asText())) {
                throw new IllegalArgumentException("Unsupported proposal kind");
            }
            JsonNode excerpt = row.path("evidence");
            if (!excerpt.isTextual() || excerpt.asText().isBlank() || excerpt.asText().length() > 2000
                    || !boundaries.contains(excerpt.asText()) || !seen.add(excerpt.asText())) {
                throw new IllegalArgumentException("Invalid or duplicate source passage");
            }
            String kind = row.get("kind").asText();
            String destination = row.has("destination") ? row.path("destination").asText() : "";
            if ((row.has("destination") && !row.path("destination").isTextual())
                    || ("review".equals(kind) && !ChartUpdateSections.NATIVE.contains(destination))
                    || ("history".equals(kind) && !destination.isEmpty() && !ChartUpdateSections.CODES.contains(destination))
                    || ("tickler".equals(kind) && !destination.isEmpty())) {
                throw new IllegalArgumentException("Unsupported chart destination");
            }
            result.add(new Proposal(kind, excerpt.asText(), destination));
        }
        return new Report(List.copyOf(result), coverage);
    }

    /** Require sentence/line boundaries so an agent cannot quote only "asthma" from "No asthma". */
    static boolean completePassage(String source, String evidence) {
        return new PassageBoundaries(source).contains(evidence);
    }

    /** Index whitespace and line boundaries once; repeated quotations never copy/rescan the whole source. */
    private static final class PassageBoundaries {
        private static final java.util.regex.Pattern LIST_PREFIX = java.util.regex.Pattern.compile("[ \\t]*(?:[-*•]|[0-9]+[.)])[ \\t]+");
        private static final java.util.regex.Pattern NEXT_LIST = java.util.regex.Pattern.compile("(?:[-*•][ \\t]*|[0-9]+[.)][ \\t]+)");
        private static final java.util.regex.Pattern HEADING = java.util.regex.Pattern.compile("[A-Z][A-Za-z /-]{0,60}:");
        private static final java.util.regex.Pattern PLAIN_HEADING = java.util.regex.Pattern.compile("(?i)(?:impression|presenting complaint|diagnosis|diagnoses|issues|plan)");
        private static final java.util.regex.Pattern END_ABBREVIATION = java.util.regex.Pattern.compile("\\b(?:Dr|Mr|Mrs|Ms|Prof|St)\\.(?:\\s+[A-Z]\\.)*$");
        private static final java.util.regex.Pattern ABBREVIATION = java.util.regex.Pattern.compile("\\b(?:Dr|Mr|Mrs|Ms|Prof|St|[A-Z]|[0-9]+)\\.$");
        private static final java.util.regex.Pattern PREFIX_QUALIFIER = java.util.regex.Pattern.compile("(?i)\\b(?:no|not|denies|without|if|unless|pending)\\b");
        private static final java.util.regex.Pattern SUFFIX_QUALIFIER = java.util.regex.Pattern.compile(
                "(?i)(?:[-*•][ \\t]*|[0-9]+[.)][ \\t]+)?(?:ruled out|not confirmed|resolved|cancelled|if\\b|unless\\b|when\\b)");
        private final String source;
        private final int[] lineStart, clauseStart, previousText, nextText;

        PassageBoundaries(String source) {
            this.source = source;
            int size = source.length();
            lineStart = new int[size + 1];
            clauseStart = new int[size + 1];
            previousText = new int[size + 1];
            nextText = new int[size + 1];
            previousText[0] = -1;
            for (int i = 0; i < size; i++) {
                char c = source.charAt(i);
                lineStart[i + 1] = c == '\n' ? i + 1 : lineStart[i];
                clauseStart[i + 1] = c == '\n' || ".!?".indexOf(c) >= 0 ? i + 1 : clauseStart[i];
                previousText[i + 1] = Character.isWhitespace(c) ? previousText[i] : i;
            }
            nextText[size] = size;
            for (int i = size - 1; i >= 0; i--) {
                nextText[i] = Character.isWhitespace(source.charAt(i)) ? nextText[i + 1] : i;
            }
        }

        boolean contains(String evidence) {
            if (evidence == null || evidence.isBlank()) return false;
            for (int start = source.indexOf(evidence); start >= 0; start = source.indexOf(evidence, start + 1)) {
                int end = start + evidence.length();
                int previous = previousText[start];
                boolean blankPrefix = previous < lineStart[start];
                boolean begins = previous < 0 || blankPrefix
                        || (start - lineStart[start] <= 32
                            && LIST_PREFIX.matcher(source).region(lineStart[start], start).matches());
                if (!begins && previous >= lineStart[start] && ".!?".indexOf(source.charAt(previous)) >= 0
                        && previous + 1 < start && horizontalEnd(previous + 1) == start) {
                    begins = !ABBREVIATION.matcher(source).region(Math.max(lineStart[start], previous - 32), previous + 1).find();
                }
                if (!begins) continue;
                if (previous >= 0 && blankPrefix) {
                    int previousLine = nextText[lineStart[previous]];
                    int gapLines = newlines(previous + 1, start);
                    boolean heading = previous + 1 - previousLine <= 64
                            && PLAIN_HEADING.matcher(source).region(previousLine, previous + 1).matches();
                    if (".!?:".indexOf(source.charAt(previous)) < 0 && !heading && gapLines < 2
                            && !LIST_PREFIX.matcher(evidence).lookingAt()) continue;
                    if (gapLines == 1 && PREFIX_QUALIFIER.matcher(source)
                            .region(clauseStart[previous + 1], previous + 1).find()) continue;
                }
                int following = nextText[end];
                int afterHorizontal = horizontalEnd(end);
                int afterCr = afterHorizontal < source.length() && source.charAt(afterHorizontal) == '\r'
                        ? afterHorizontal + 1 : afterHorizontal;
                boolean newlineAfter = afterCr < source.length() && source.charAt(afterCr) == '\n';
                char last = evidence.charAt(evidence.length() - 1);
                boolean ends = following == source.length() || newlineAfter
                        || (".!?".indexOf(last) >= 0 && afterHorizontal > end);
                if (newlineAfter && following < source.length() && ".!?:".indexOf(last) < 0
                        && newlines(end, following) < 2
                        && !NEXT_LIST.matcher(source).region(following, source.length()).lookingAt()
                        && !HEADING.matcher(source).region(following, source.length()).lookingAt()) ends = false;
                if (following < source.length() && END_ABBREVIATION.matcher(evidence).find()) ends = false;
                if (SUFFIX_QUALIFIER.matcher(source).region(following, source.length()).lookingAt()) ends = false;
                if (ends) return true;
            }
            return false;
        }

        private int horizontalEnd(int from) {
            int end = from;
            while (end < source.length() && (source.charAt(end) == ' ' || source.charAt(end) == '\t')) end++;
            return end;
        }
        private int newlines(int from, int to) {
            int count = 0;
            for (int i = from; i < to && count < 2; i++) if (source.charAt(i) == '\n') count++;
            return count;
        }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
