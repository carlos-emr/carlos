/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler;

/**
 * The order of the versions of one CML report.
 *
 * <p>Lab filing keeps the OSCAR contract: the newest version of a report replaces the measurements
 * of the versions before it. This class only decides which version is newest, because the stored
 * final-result count and report date cannot: a correction (status C) used to count as zero final
 * results and so sorted before the report it corrects (#5819), and a version delivered late could
 * displace a newer one (#5820). The order is total and never refuses; when the lab's own fields do
 * not separate two versions, the one received later is the newer, as it always was.
 *
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

    /**
     * Parses an HL7 timestamp (date, optional time, optional fraction and offset) strictly.
     *
     * @throws java.time.DateTimeException when the value is not a real calendar time
     * @throws IllegalArgumentException when the value is missing or overlong
     */
    public static Instant timestamp(String value) {
        if (value == null || value.length() > 40) throw new IllegalArgumentException("Missing CML timestamp");
        var parsed = TIMESTAMP.parse(value);
        LocalDateTime local = LocalDateTime.from(parsed);
        var offset = parsed.query(TemporalQueries.offset());
        return offset == null ? local.atZone(ZoneId.systemDefault()).toInstant() : local.toInstant(offset);
    }

    /** A time the lab did not send, or sent unusably, sorts before every real one. */
    private static Instant timeOrEarliest(String value) {
        try {
            return timestamp(value);
        } catch (java.time.DateTimeException | IllegalArgumentException unusable) {
            return Instant.MIN;
        }
    }

    /** Final, corrected and deleted results outrank preliminary ones; anything else ranks last. */
    private static int stage(String status) {
        return switch (Objects.toString(status, "")) {
            case "F", "f", "Final", "C", "c", "D", "d" -> 2;
            case "P", "p", "I", "i", "Pending", "R", "r", "S", "s" -> 1;
            default -> 0;
        };
    }

    private static int correction(String status) {
        return switch (Objects.toString(status, "")) {
            case "D", "d" -> 2;
            case "C", "c" -> 1;
            default -> 0;
        };
    }

    /**
     * Orders the versions of one CML report, oldest first, newest last.
     *
     * <p>Each report is ranked by its most advanced result: stage, comparable OBR-22 revision
     * times, correction, then final-result count. Within a stage, revision times are compared
     * only when every version has one. Pairwise omission of a missing time would make the sort
     * non-transitive. Collection dates never stand in for revision times. Reports still tied
     * keep the order they were received in (their ids).
     *
     * @param reports the reports of the chain by id, each already parsed
     * @return every id of {@code reports}, oldest version first
     */
    public static List<Integer> order(Map<Integer, CMLHandler> reports) {
        record Revision(int report, int stage, Instant time, int correction, int finalResults) { }
        List<Revision> revisions = new ArrayList<>();
        for (var entry : reports.entrySet()) {
            CMLHandler handler = entry.getValue();
            int stage = 0;
            int correction = 0;
            Instant time = Instant.MIN;
            boolean dated = true;
            for (int i = 0; i < handler.getOBRCount(); i++) {
                for (int j = 0; j < handler.getOBXCount(i); j++) {
                    String status = handler.getOBXResultStatus(i, j);
                    int nextStage = stage(status);
                    if (nextStage == 0) continue;
                    Instant nextTime = timeOrEarliest(handler.getRevisionTimestamp(i, j));
                    int nextCorrection = correction(status);
                    if (nextStage > stage) {
                        stage = nextStage;
                        time = nextTime;
                        correction = nextCorrection;
                        dated = !nextTime.equals(Instant.MIN);
                    } else if (nextStage == stage) {
                        if (nextTime.isAfter(time)) time = nextTime;
                        correction = Math.max(correction, nextCorrection);
                        dated &= !nextTime.equals(Instant.MIN);
                    }
                }
            }
            revisions.add(new Revision(entry.getKey(), stage, dated ? time : Instant.MIN,
                    correction, handler.getOBXFinalResultCount()));
        }
        Map<Integer, Boolean> comparableTimes = new java.util.HashMap<>();
        for (Revision revision : revisions) {
            comparableTimes.merge(revision.stage(), !revision.time().equals(Instant.MIN), (left, right) -> left && right);
        }
        revisions.sort(Comparator.comparingInt(Revision::stage)
                .thenComparing(revision -> comparableTimes.get(revision.stage()) ? revision.time() : Instant.MIN)
                .thenComparingInt(Revision::correction).thenComparingInt(Revision::finalResults)
                .thenComparingInt(Revision::report));
        return revisions.stream().map(Revision::report).toList();
    }
}
