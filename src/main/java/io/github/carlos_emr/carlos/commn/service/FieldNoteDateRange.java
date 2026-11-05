/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Strict, inclusive calendar dates converted to a database query interval.
 *
 * @param start inclusive start instant
 * @param endExclusive start of the day following the requested last day
 * @since 2026-10-02
 */
public record FieldNoteDateRange(Instant start, Instant endExclusive) {
    /**
     * Parses ISO calendar dates in the server's default time zone.
     *
     * @param startText first included date, in ISO yyyy-MM-dd format
     * @param endText last included date, in ISO yyyy-MM-dd format
     * @return inclusive start and exclusive end instants, including the entire last day
     * @throws IllegalArgumentException for missing, malformed, impossible, or reversed dates
     */
    public static FieldNoteDateRange parse(String startText, String endText) {
        if (startText == null || endText == null) {
            throw new IllegalArgumentException("Missing report dates");
        }
        try {
            LocalDate start = LocalDate.parse(startText);
            LocalDate end = LocalDate.parse(endText);
            if (start.isAfter(end)) throw new IllegalArgumentException("Reversed report dates");
            ZoneId zone = ZoneId.systemDefault();
            return new FieldNoteDateRange(start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid report dates", ex);
        }
    }

    /** @return a new mutable Date representing the inclusive query start */
    public Date startDate() {
        return Date.from(start);
    }

    /** @return a new mutable Date representing the exclusive query end */
    public Date endExclusiveDate() {
        return Date.from(endExclusive);
    }
}
