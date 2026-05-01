/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Central date parsing for Ontario billing.
 */
public final class BillingDates {
    private static final DateTimeFormatter SERVICE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter OHIP_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BillingDates() {
    }

    public static String ohipEffectiveDate(String raw, LocalDate fallbackDate) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return fallbackDate.format(SERVICE_DATE);
        }
        return parseOhipDate(raw, false).format(SERVICE_DATE);
    }

    public static String ohipTerminationDate(String raw) {
        if ("99999999".equals(raw)) {
            return "9999-12-31";
        }
        return parseOhipDate(raw, true).format(SERVICE_DATE);
    }

    public static Date serviceDate(String serviceDate) {
        // util.Date (not sql.Date) — see parseIsoDate for rationale.
        return Date.from(LocalDate.parse(serviceDate, SERVICE_DATE)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Parse a {@code yyyy-MM-dd} ISO date strictly. Throws on null, blank, or
     * unparseable input — use this on billing-mutating paths where silently
     * substituting today's date would record audit-incorrect service dates
     * to OHIP.
     *
     * @param raw String the ISO-formatted date
     * @return Date legacy {@link Date} at start-of-day in the system zone
     * @throws IllegalArgumentException when {@code raw} cannot be parsed
     */
    public static Date parseIsoDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("BillingDates.parseIsoDate: date is null or blank");
        }
        try {
            // Return util.Date (not sql.Date) so the result honours the
            // declared Date contract including .toInstant(). java.sql.Date
            // overrides toInstant() to throw, breaking Liskov on the
            // declared return type and forcing every consumer to know
            // which factory produced the value.
            return Date.from(LocalDate.parse(raw.trim(), SERVICE_DATE)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseIsoDate: malformed yyyy-MM-dd date [" + raw + "]");
        }
    }

    /**
     * Parse a {@code yyyy-MM-dd} ISO date that may legitimately be missing.
     * Null/blank input yields {@code null} (the legacy contract treated absent
     * dates as a valid "no value"). Non-blank-but-unparseable input throws —
     * callers in {@code @Transactional} services then roll back rather than
     * persisting a row with a silently-nulled date.
     *
     * @param raw       String the ISO-formatted date, or null/blank for absent
     * @param fieldName String diagnostic name embedded in the throw message
     * @return Date parsed date, or {@code null} when {@code raw} is null/blank
     * @throws IllegalArgumentException when {@code raw} is non-blank and unparseable
     */
    public static Date parseOptionalIsoDate(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            // util.Date (not sql.Date) — see parseIsoDate for rationale.
            return Date.from(LocalDate.parse(raw.trim(), SERVICE_DATE)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseOptionalIsoDate: malformed " + fieldName + " [" + raw + "]");
        }
    }

    /**
     * Parse an {@code HH:mm:ss} ISO time that may legitimately be missing.
     * Mirrors the contract of {@link #parseOptionalIsoDate(String, String)}:
     * null/blank yields {@code null} (legacy "no value" tolerated), non-blank
     * but unparseable throws so the surrounding {@code @Transactional}
     * unit-of-work rolls back instead of persisting a row with a silently
     * nulled time.
     *
     * @param raw       String the {@code HH:mm:ss} time, or null/blank for absent
     * @param fieldName String diagnostic name embedded in the throw message
     * @return Date a {@link java.util.Date} on the JVM-zone epoch day at the
     *         parsed time-of-day, or {@code null} when {@code raw} is null/blank
     * @throws IllegalArgumentException when {@code raw} is non-blank and unparseable
     */
    public static Date parseOptionalIsoTime(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            LocalTime t = LocalTime.parse(raw.trim(), ISO_TIME);
            return Date.from(t.atDate(LocalDate.ofEpochDay(0))
                    .atZone(ZoneId.systemDefault()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseOptionalIsoTime: malformed " + fieldName + " [" + raw + "]");
        }
    }

    public static String toOhipDate(Date date) {
        if (date == null) {
            return "";
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(OHIP_DATE);
    }

    /**
     * Parse an {@code HH:mm:ss} ISO time strictly. Mirrors the contract of
     * {@link #parseIsoDate(String)}: throws on null, blank, or unparseable
     * input. Use this on billing-mutating paths where silently substituting
     * a default time would record audit-incorrect timestamps.
     */
    public static Date parseIsoTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("BillingDates.parseIsoTime: time is null or blank");
        }
        try {
            LocalTime t = LocalTime.parse(raw.trim(), ISO_TIME);
            return Date.from(t.atDate(LocalDate.ofEpochDay(0))
                    .atZone(ZoneId.systemDefault()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseIsoTime: malformed HH:mm:ss time [" + raw + "]");
        }
    }

    /** Format a {@link Date} as {@code yyyy-MM-dd}. Null-safe — returns empty. */
    public static String formatIsoDate(Date date) {
        if (date == null) {
            return "";
        }
        // java.sql.Date.toInstant() throws UnsupportedOperationException;
        // route through getTime() to support both sql.Date (returned by
        // parseIsoDate) and util.Date.
        return java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault()).toLocalDate().format(SERVICE_DATE);
    }

    /** Format a {@link Date} as {@code HH:mm:ss}. Null-safe — returns empty. */
    public static String formatIsoTime(Date date) {
        if (date == null) {
            return "";
        }
        // Route through getTime() — java.sql.Date / java.sql.Time both throw
        // UnsupportedOperationException on toInstant().
        return java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault()).toLocalTime().format(ISO_TIME);
    }

    /** Format a {@link Date} as {@code yyyy-MM-dd HH:mm:ss}. Null-safe — returns empty. */
    public static String formatIsoTimestamp(Date date) {
        if (date == null) {
            return "";
        }
        LocalDateTime ldt = java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        return ldt.format(ISO_TIMESTAMP);
    }

    private static LocalDate parseOhipDate(String raw, boolean normalizeZeroDay) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("\\d{8}")) {
            throw new IllegalArgumentException("Expected OHIP date in yyyyMMdd format: " + raw);
        }

        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(4, 6));
        int day = Integer.parseInt(value.substring(6, 8));
        if (normalizeZeroDay && day == 0) {
            day = 1;
        }
        return LocalDate.of(year, month, day);
    }
}
