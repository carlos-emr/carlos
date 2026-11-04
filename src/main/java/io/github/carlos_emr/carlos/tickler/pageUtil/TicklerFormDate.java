/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;

/** Strict parser for the ISO date submitted by both tickler forms. */
final class TicklerFormDate {
    private TicklerFormDate() {
    }

    /**
     * Parses a complete calendar date at local midnight without rolling invalid dates forward.
     *
     * @param value the form's yyyy-MM-dd value
     * @return the selected service date
     * @throws IllegalArgumentException if the date is missing or invalid
     */
    static Date parse(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("Invalid service date");
        }
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() == 0) {
                throw new IllegalArgumentException("Invalid service year");
            }
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid service date", e);
        }
    }
}
