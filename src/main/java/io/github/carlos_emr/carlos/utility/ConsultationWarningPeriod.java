/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

/** The stored consultation warning period is a number of calendar months. */
public final class ConsultationWarningPeriod {
    private ConsultationWarningPeriod() { }

    /** Blank means the established one-month default. */
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

    /** Validate before any part of the submitted preference set is persisted. */
    public static void validate(String value) {
        if (!isValid(value)) throw new IllegalArgumentException("Invalid consultation warning period in months");
    }

    /** Store a canonical value while preserving blank's established default. */
    public static String normalized(String value) {
        validate(value);
        return value == null || value.isBlank() ? "" : Integer.toString(Integer.parseInt(value.trim()));
    }

    /** Historical malformed values must not prevent the clinical panel from loading. */
    public static int monthsOrDefault(String value) {
        if (value == null || value.isBlank()) return 1;
        if (!isValid(value)) {
            MiscUtils.getLogger().warn("Invalid stored consultation warning period; using one month");
            return 1;
        }
        return Integer.parseInt(value.trim());
    }
}
