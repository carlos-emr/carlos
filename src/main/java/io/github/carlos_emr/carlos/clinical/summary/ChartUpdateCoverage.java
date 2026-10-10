/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Processing audit only. Quotation coverage cannot establish clinical completeness. */
public final class ChartUpdateCoverage implements Serializable {
    private record Range(int start, int end) implements Serializable {}
    public record Rejected(String evidence, String reason) implements Serializable {
        public String getEvidence() { return evidence; }
        public String getReason() { return reason; }
    }
    private final List<Range> ranges;
    private final List<Rejected> rejected;

    private ChartUpdateCoverage(List<Range> ranges, List<Rejected> rejected) {
        this.ranges = List.copyOf(ranges);
        this.rejected = List.copyOf(rejected);
    }

    public List<Rejected> getRejected() { return rejected; }

    /** Require a complete, ordered UTF-16 partition; legacy results have no audit. */
    public static ChartUpdateCoverage parse(JsonNode node, String source) {
        if (node == null) return null;
        ClinicalSummaryAgentProtocol.exactFields(node, Set.of("version", "sections", "rejected"));
        if (!node.path("version").isIntegralNumber() || !node.path("version").canConvertToInt() || node.path("version").asInt() != 1) invalid();
        JsonNode sections = node.path("sections");
        if (!sections.isArray() || sections.isEmpty() || sections.size() > 256) invalid();
        List<Range> ranges = new ArrayList<>();
        int end = 0;
        for (JsonNode section : sections) {
            ClinicalSummaryAgentProtocol.exactFields(section, Set.of("start", "end"));
            if (!section.path("start").isIntegralNumber() || !section.path("start").canConvertToInt()
                    || !section.path("end").isIntegralNumber() || !section.path("end").canConvertToInt()) invalid();
            int next = section.path("end").asInt();
            if (section.path("start").asInt() != end || next <= end || next > source.length()
                    || (next < source.length() && Character.isHighSurrogate(source.charAt(next - 1))
                        && Character.isLowSurrogate(source.charAt(next)))) invalid();
            ranges.add(new Range(end, next));
            end = next;
        }
        if (end != source.length()) invalid();
        JsonNode omitted = node.path("rejected");
        if (!omitted.isArray() || omitted.size() > ChartUpdateProposals.MAX_PROPOSALS) invalid();
        List<Rejected> rejected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : omitted) {
            ClinicalSummaryAgentProtocol.exactFields(row, Set.of("evidence", "reason"));
            String evidence = row.path("evidence").asText(), reason = row.path("reason").asText();
            if (!row.path("evidence").isTextual() || evidence.isBlank() || evidence.length() > 2000
                    || !source.contains(evidence) || !seen.add(evidence) || !row.path("reason").isTextual()
                    || reason.isBlank() || reason.codePointCount(0, reason.length()) > 300) invalid();
            rejected.add(new Rejected(evidence, reason));
        }
        return new ChartUpdateCoverage(ranges, rejected);
    }

    private static void invalid() { throw new IllegalArgumentException("Invalid coverage audit"); }

    /** Derive all counts, links and gaps locally, never trust model completeness claims. */
    public List<Map<String, Object>> sections(String source, List<ChartUpdateProposals.Proposal> proposals) {
        boolean[] quoted = new boolean[source.length()];
        List<List<Range>> occurrences = new ArrayList<>();
        for (var proposal : proposals) {
            List<Range> found = new ArrayList<>();
            String evidence = proposal.evidence();
            for (int at = source.indexOf(evidence); at >= 0; at = source.indexOf(evidence, at + evidence.length())) {
                int end = at + evidence.length();
                found.add(new Range(at, end));
            }
            // A quotation alone has no occurrence provenance. Identical text elsewhere
            // may belong to a negation, relative or different date. Keep ambiguous text
            // visible for manual review instead of claiming every occurrence is covered.
            if (found.size() == 1 && source.indexOf(evidence, found.getFirst().start() + 1) < 0) {
                java.util.Arrays.fill(quoted, found.getFirst().start(), found.getFirst().end(), true);
            }
            occurrences.add(found);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Range range : ranges) {
            List<Map<String, Object>> links = new ArrayList<>();
            for (int i = 0; i < proposals.size(); i++) {
                if (occurrences.get(i).stream().anyMatch(found -> found.start() < range.end() && found.end() > range.start())) {
                    var proposal = proposals.get(i);
                    links.add(Map.of("key", proposal.key(), "number", i + 1, "nativeRecord", "review".equals(proposal.kind()),
                            "destination", proposal.destination()));
                }
            }
            List<String> gaps = new ArrayList<>();
            for (int at = range.start(); at < range.end();) {
                if (quoted[at]) { at++; continue; }
                int start = at;
                while (at < range.end() && !quoted[at]) at++;
                String text = source.substring(start, at);
                if (!text.isBlank()) gaps.add(text);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("text", source.substring(range.start(), range.end()));
            row.put("links", links);
            row.put("gaps", gaps);
            result.add(row);
        }
        return List.copyOf(result);
    }
}
