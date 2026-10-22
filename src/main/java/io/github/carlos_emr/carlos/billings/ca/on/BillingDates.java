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
package io.github.carlos_emr.carlos.billings.ca.on;

import io.github.carlos_emr.CarlosProperties;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Date;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Central date parsing and formatting for Ontario billing.
 *
 * <p>Billing date/timestamp helpers return legacy {@link Date} values because
 * the surrounding DAO and model APIs still expose {@code java.util.Date}.
 * Formatting routes through epoch milliseconds rather than {@code toInstant()}
 * so Hibernate-hydrated {@code java.sql.Date}, {@code java.sql.Time}, and
 * {@code java.sql.Timestamp} instances work consistently.</p>
 *
 * <p>The billing zone is configurable through {@code billing_on_timezone} in
 * {@code carlos.properties}. When unset, Ontario billing preserves the legacy
 * server-default timezone behavior. The resolved zone is cached with a single
 * volatile key/value snapshot so concurrent readers cannot observe a stale key
 * paired with a new zone.</p>
 *
 * @since 2026-04-28
 */
public final class BillingDates {
    private static final String BILLING_TIMEZONE_PROPERTY = "billing_on_timezone";
    private static final DateTimeFormatter SERVICE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter OHIP_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FLEXIBLE_SERVICE_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FLEXIBLE_ISO_TIME = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);
    // Cache the lookup key and resolved zone together so callers never see a
    // stale property key paired with a freshly-resolved ZoneId.
    private static volatile ZoneCache cachedBillingZone;

    private BillingDates() {
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static String ohipEffectiveDate(String raw, LocalDate fallbackDate) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return fallbackDate.format(SERVICE_DATE);
        }
        return OhipDateTokens.parse(raw, OhipDateTokens.ZeroDayPolicy.REJECT_ZERO_DAY).format(SERVICE_DATE);
    }

    public static Date serviceDate(String serviceDate) {
        // util.Date (not sql.Date) — see parseIsoDate for rationale.
        return Date.from(LocalDate.parse(serviceDate, SERVICE_DATE)
                .atStartOfDay(billingZone()).toInstant());
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
                    .atStartOfDay(billingZone()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseIsoDate: malformed yyyy-MM-dd date [" + raw + "]", e);
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
                    .atStartOfDay(billingZone()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseOptionalIsoDate: malformed " + fieldName + " [" + raw + "]", e);
        }
    }

    /**
     * Normalize a legacy web-form ISO date to canonical {@code yyyy-MM-dd}.
     * Accepts the historic appointment-calendar shape where month/day may be
     * one digit (for example {@code 2026-4-7}) and returns the strict shape
     * consumed by the persistence layer.
     */
    public static String normalizeIsoDateText(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("BillingDates.normalizeIsoDateText: " + fieldName + " is null or blank");
        }
        try {
            return LocalDate.parse(raw.trim(), FLEXIBLE_SERVICE_DATE).format(SERVICE_DATE);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "BillingDates.normalizeIsoDateText: malformed " + fieldName + " [" + raw + "]", e);
        }
    }

    /**
     * Normalize an optional legacy web-form ISO date. Blank input preserves
     * the legacy absent-field convention; non-blank input is canonicalized.
     */
    public static String normalizeOptionalIsoDateText(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        return normalizeIsoDateText(raw, fieldName);
    }

    /**
     * Normalize an optional legacy form time to {@code HH:mm:ss}. The
     * appointment billing shortcut historically sent {@code 0:00:00}, and
     * other form paths still send {@code HH:mm}; the persistence layer expects
     * canonical seconds-bearing text.
     */
    public static String normalizeOptionalIsoTimeText(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        try {
            return LocalTime.parse(raw.trim(), FLEXIBLE_ISO_TIME).format(ISO_TIME);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "BillingDates.normalizeOptionalIsoTimeText: malformed " + fieldName + " [" + raw + "]", e);
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
                    .atZone(billingZone()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseOptionalIsoTime: malformed " + fieldName + " [" + raw + "]", e);
        }
    }

    public static String toOhipDate(Date date) {
        if (date == null) {
            return "";
        }
        return java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(billingZone()).toLocalDate().format(OHIP_DATE);
    }

    /**
     * Parse an {@code HH:mm:ss} ISO time strictly. Mirrors the contract of
     * {@link #parseIsoDate(String)}: throws on null, blank, or unparseable
     * input. Use this on billing-mutating paths where silently substituting
     * a default time would record audit-incorrect timestamps. The returned
     * legacy {@link Date} uses the JVM-zone epoch day with the parsed
     * time-of-day because callers only persist or format the clock value.
     *
     * @param raw String the {@code HH:mm:ss} time
     * @return Date a {@link java.util.Date} on the JVM-zone epoch day at the
     *         parsed time-of-day
     * @throws IllegalArgumentException when {@code raw} is null, blank, or
     *         unparseable
     */
    public static Date parseIsoTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("BillingDates.parseIsoTime: time is null or blank");
        }
        try {
            LocalTime t = LocalTime.parse(raw.trim(), ISO_TIME);
            return Date.from(t.atDate(LocalDate.ofEpochDay(0))
                    .atZone(billingZone()).toInstant());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "BillingDates.parseIsoTime: malformed HH:mm:ss time [" + raw + "]", e);
        }
    }

    /** Format a {@link Date} as {@code yyyy-MM-dd}. Null-safe — returns empty. */
    public static String formatIsoDate(Date date) {
        if (date == null) {
            return "";
        }
        // java.sql.Date.toInstant() throws UnsupportedOperationException;
        // route through getTime() to support both sql.Date and the util.Date
        // returned by parseIsoDate.
        return java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(billingZone()).toLocalDate().format(SERVICE_DATE);
    }

    /** Format a {@link Date} as {@code HH:mm:ss}. Null-safe — returns empty. */
    public static String formatIsoTime(Date date) {
        if (date == null) {
            return "";
        }
        // Route through getTime() — java.sql.Date / java.sql.Time both throw
        // UnsupportedOperationException on toInstant().
        return java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(billingZone()).toLocalTime().format(ISO_TIME);
    }

    /** Format a {@link Date} as {@code yyyy-MM-dd HH:mm:ss}. Null-safe — returns empty. */
    public static String formatIsoTimestamp(Date date) {
        if (date == null) {
            return "";
        }
        LocalDateTime ldt = java.time.Instant.ofEpochMilli(date.getTime())
                .atZone(billingZone()).toLocalDateTime();
        return ldt.format(ISO_TIMESTAMP);
    }

    private static ZoneId billingZone() {
        Object configuredZone = CarlosProperties.getInstance().get(BILLING_TIMEZONE_PROPERTY);
        String zoneId = configuredZone == null ? "" : configuredZone.toString().trim();
        ZoneId systemDefault = ZoneId.systemDefault();
        String cacheKey = zoneId.isEmpty() ? "system:" + systemDefault.getId() : "configured:" + zoneId;
        ZoneCache cached = cachedBillingZone;
        if (cached != null && cacheKey.equals(cached.key())) {
            return cached.zone();
        }

        ZoneId resolved;
        if (zoneId.isEmpty()) {
            resolved = systemDefault;
        } else {
            try {
                resolved = ZoneId.of(zoneId);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Invalid " + BILLING_TIMEZONE_PROPERTY + " value: " + zoneId, e);
            }
        }
        synchronized (BillingDates.class) {
            cachedBillingZone = new ZoneCache(cacheKey, resolved);
        }
        return resolved;
    }

    private record ZoneCache(String key, ZoneId zone) {
    }
}
