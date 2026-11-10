/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

/**
 * Validates and normalizes consultation warning periods stored as calendar months.
 * Blank values retain the established one-month default.
 * @since 2026-11-09
 */
public final class ConsultationWarningPeriod {
    private ConsultationWarningPeriod() { }

    /**
     * Checks a submitted period without changing it.
     * @param value blank/null for the default, or a positive integer number of months
     * @return whether the value is blank or fits a positive Java integer after trimming
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = value.trim();
        if (!normalized.matches("\\d{1,10}")) return false;
        try {
            return Integer.parseInt(normalized) > 0;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    /**
     * Validates before any part of a submitted preference set is persisted.
     * @param value submitted calendar-month period, including blank/null for the default
     * @throws IllegalArgumentException if a nonblank value is not a positive integer
     */
    public static void validate(String value) {
        if (!isValid(value)) throw new IllegalArgumentException("Invalid consultation warning period in months");
    }

    /**
     * Produces the canonical storage value, removing whitespace and leading zeroes.
     * @param value submitted calendar-month period
     * @return an empty string for blank/null, otherwise a positive decimal integer
     * @throws IllegalArgumentException if the nonblank value is invalid
     */
    public static String normalized(String value) {
        validate(value);
        return value == null || value.isBlank() ? "" : Integer.toString(Integer.parseInt(value.trim()));
    }

    /**
     * Reads historical values without preventing the clinical panel from loading.
     * @param value stored calendar-month period
     * @return the positive period, or one month for blank/null or malformed values
     */
    public static int monthsOrDefault(String value) {
        if (value == null || value.isBlank()) return 1;
        if (!isValid(value)) {
            MiscUtils.getLogger().warn("Invalid stored consultation warning period; using one month");
            return 1;
        }
        return Integer.parseInt(value.trim());
    }
}
