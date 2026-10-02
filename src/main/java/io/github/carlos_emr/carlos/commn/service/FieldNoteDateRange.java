/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/** Strict, inclusive calendar dates converted to an exclusive database query interval. */
public record FieldNoteDateRange(Instant start, Instant endExclusive) {
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

    public Date startDate() {
        return Date.from(start);
    }

    public Date endExclusiveDate() {
        return Date.from(endExclusive);
    }
}
