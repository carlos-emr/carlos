/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import java.util.function.Function;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler;
import io.github.carlos_emr.carlos.utility.ContentDigests;

/** CML observation revisions. Omission never implies deletion; only an explicit D withdraws a result.
 * @since 2026-11-09
 */
public final class CmlVersionPolicy {
    private CmlVersionPolicy() { }
    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendValue(ChronoField.MONTH_OF_YEAR, 2).appendValue(ChronoField.DAY_OF_MONTH, 2)
            .optionalStart().appendValue(ChronoField.HOUR_OF_DAY, 2)
            .optionalStart().appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart().appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .optionalEnd().optionalEnd().optionalEnd()
            .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0).parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0).parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    public record Observation(int report, int obr, int obx, String key, Instant revision, int stage,
            boolean withdrawn, int correction, String signature) { }

    public static String pack(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null) out.append("-1:");
            else out.append(value.length()).append(':').append(value);
        }
        return out.toString();
    }

    public static Instant timestamp(String value) {
        if (value == null || value.length() > 40) throw new IllegalArgumentException("Missing CML clinical revision time");
        var parsed = TIMESTAMP.parse(value);
        LocalDateTime local = LocalDateTime.from(parsed);
        var offset = parsed.query(TemporalQueries.offset());
        return offset == null ? local.atZone(ZoneId.systemDefault()).toInstant() : local.toInstant(offset);
    }

    private static Instant clinicalTime(String value) {
        try { return timestamp(value); }
        catch (java.time.DateTimeException | IllegalArgumentException invalid) {
            throw new AmbiguousRevision("Missing or unusable CML clinical revision time requires review");
        }
    }

    private static String normalizedStatus(String status) {
        return switch (Objects.toString(status, "")) {
            case "F", "f", "Final" -> "F";
            case "C", "c" -> "C";
            case "D", "d" -> "D";
            case "P", "p", "I", "i", "Pending", "R", "r", "S", "s" -> "P";
            case "DNS" -> "DNS";
            default -> "UNKNOWN";
        };
    }

    private static int stage(String status) {
        return switch (status) { case "F", "C", "D" -> 2; case "P" -> 1; default -> 0; };
    }

    public static boolean hasUnsupportedStatus(CMLHandler handler) {
        for (int i = 0; i < handler.getOBRCount(); i++) {
            for (int j = 0; j < handler.getOBXCount(i); j++) {
                if ("UNKNOWN".equals(normalizedStatus(handler.getOBXResultStatus(i, j)))
                        && !Objects.toString(handler.getOBXResult(i, j), "").isEmpty()) return true;
            }
        }
        return false;
    }

    public static List<Observation> observations(int report, CMLHandler handler) {
        List<Observation> result = new ArrayList<>();
        for (int i = 0; i < handler.getOBRCount(); i++) {
            for (int j = 0; j < handler.getOBXCount(i); j++) {
                String status = normalizedStatus(handler.getOBXResultStatus(i, j));
                int stage = stage(status);
                if (stage == 0) continue;
                if (!"D".equals(status) && Objects.toString(handler.getOBXResult(i, j), "").isEmpty()) continue;
                String key = handler.getProjectionKey(i, j);
                StringBuilder comments = new StringBuilder();
                for (int n = 0; n < handler.getOBXCommentCount(i, j); n++) comments.append(pack(handler.getOBXComment(i, j, n)));
                String signature = ContentDigests.sha256Hex(pack(key, handler.getOBXResult(i, j),
                        handler.getOBXUnits(i, j), handler.getOBXReferenceRange(i, j),
                        handler.getOBXAbnormalFlag(i, j), handler.getTimeStamp(i, j), status, comments.toString(),
                        Boolean.toString(handler.isTestResultBlocked(i, j))));
                result.add(new Observation(report, i, j, key, clinicalTime(handler.getRevisionTimestamp(i, j)),
                        stage, "D".equals(status), "D".equals(status) ? 2 : "C".equals(status) ? 1 : 0, signature));
            }
        }
        return result;
    }

    /** Latest explicit terminal revision per observation; preliminary updates cannot demote a final result. */
    public static Map<String, Observation> winners(List<Observation> observations) {
        Map<String, Observation> winners = new LinkedHashMap<>();
        Set<String> withinReport = new HashSet<>();
        // Compare the maximal revisions first. An obsolete conflict must not
        // prevent a newer explicit correction from resolving the observation.
        List<Observation> ordered = new ArrayList<>(observations);
        ordered.sort((left, right) -> rank(right, left));
        for (Observation observation : ordered) {
            if (!withinReport.add(pack(Integer.toString(observation.report()), observation.key()))) {
                throw new AmbiguousRevision("Repeated CML observation identity requires review");
            }
            winners.merge(observation.key(), observation, CmlVersionPolicy::newer);
        }
        return winners;
    }

    private static int rank(Observation left, Observation right) {
        int order = Integer.compare(left.stage(), right.stage());
        if (order == 0) order = left.revision().compareTo(right.revision());
        return order == 0 ? Integer.compare(left.correction(), right.correction()) : order;
    }

    private static Observation newer(Observation left, Observation right) {
        int order = rank(left, right);
        if (order != 0) return order > 0 ? left : right;
        if (!left.signature().equals(right.signature())) {
            throw new AmbiguousRevision("Conflicting CML revisions at the same clinical time require review");
        }
        // Identical clinical content is interchangeable; this tie never chooses between different results.
        return left.report() < right.report() ? left : right;
    }

    /** A partial report cannot silently mark omitted observations reviewed. */
    public static List<Integer> coveredOlderReports(int selected, List<Integer> older,
            Function<Integer, MessageHandler> handlers) {
        if (older.isEmpty()) return older;
        MessageHandler handler = handlers.apply(selected);
        if (handler == null) throw new IllegalStateException("Selected source is unreadable");
        if (!(handler instanceof CMLHandler cml)) return older;
        Map<String, Observation> reviewed = winners(observations(selected, cml));
        List<Integer> covered = new ArrayList<>();
        for (int report : older) {
            if (!(handlers.apply(report) instanceof CMLHandler previous)) {
                throw new AmbiguousRevision("CML source membership requires review");
            }
            List<Observation> prior = observations(report, previous);
            boolean complete = !prior.isEmpty() && prior.stream().allMatch(row -> {
                Observation replacement = reviewed.get(row.key());
                if (replacement == null) return false;
                int comparison = rank(replacement, row);
                return comparison > 0 || comparison == 0 && replacement.signature().equals(row.signature());
            });
            if (complete) covered.add(report);
        }
        return covered;
    }

    public static List<Integer> order(Map<Integer, CMLHandler> reports) {
        record Revision(int report, int stage, Instant time, int correction) { }
        List<Revision> revisions = new ArrayList<>();
        List<Observation> candidates = new ArrayList<>();
        for (var entry : reports.entrySet()) {
            CMLHandler handler = entry.getValue();
            if (reports.size() > 1) {
                if (hasUnsupportedStatus(handler)) throw new AmbiguousRevision("Unsupported CML observation status requires review");
                candidates.addAll(observations(entry.getKey(), handler));
            }
            int stage = 0, correction = 0;
            Instant time = Instant.MIN;
            for (int i = 0; i < handler.getOBRCount(); i++) {
                for (int j = 0; j < handler.getOBXCount(i); j++) {
                    String status = normalizedStatus(handler.getOBXResultStatus(i, j));
                    int nextStage = stage(status);
                    if (nextStage == 0) continue;
                    Instant nextTime = clinicalTime(handler.getRevisionTimestamp(i, j));
                    int nextCorrection = "D".equals(status) ? 2 : "C".equals(status) ? 1 : 0;
                    if (nextStage > stage || nextStage == stage && (nextTime.isAfter(time)
                            || nextTime.equals(time) && nextCorrection > correction)) {
                        stage = nextStage; time = nextTime; correction = nextCorrection;
                    }
                }
            }
            revisions.add(new Revision(entry.getKey(), stage, time, correction));
        }
        // A report-level tie-break must never make conflicting observations appear
        // superseded to automatic filing or current-report consumers.
        winners(candidates);
        revisions.sort(Comparator.comparingInt(Revision::stage).thenComparing(Revision::time)
                .thenComparingInt(Revision::correction).thenComparingInt(Revision::report));
        return revisions.stream().map(Revision::report).toList();
    }
    public static final class AmbiguousRevision extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public AmbiguousRevision(String message) { super(message); }
    }

}
