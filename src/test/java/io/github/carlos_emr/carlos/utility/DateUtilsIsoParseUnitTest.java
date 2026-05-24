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
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the ISO <em>parse</em> helpers of {@link DateUtils} (the highest-traffic date
 * entry point: JS datepickers, REST payloads, lab/HRM flows). These helpers were migrated from a
 * per-call {@code new SimpleDateFormat(...)} to {@link CachedDateFormats#parse}, so each test pins
 * byte-for-byte equivalence with {@code new SimpleDateFormat(<same pattern>).parse(...)} — a
 * regression guard so a future change cannot silently reroute a parse to a different formatter.
 *
 * @since 2026-05-23
 */
@Tag("unit")
@DisplayName("DateUtils ISO parse helpers")
class DateUtilsIsoParseUnitTest {

    // Patterns are pulled from the same source the production helpers use, so the test tracks the
    // pattern rather than restating a literal that could drift.
    private static final String ISO_DATE_PATTERN = DateFormatUtils.ISO_DATE_FORMAT.getPattern();
    private static final String ISO_DATETIME_PATTERN = DateFormatUtils.ISO_DATETIME_FORMAT.getPattern();
    private static final String JS_NO_T_NO_SECONDS_PATTERN = "yyyy-MM-dd HH:mm";

    @Test
    @DisplayName("should parse an ISO date exactly like new SimpleDateFormat(pattern)")
    void shouldParseIsoDate_likeSimpleDateFormat() throws Exception {
        String input = "2026-05-23";
        Date expected = new SimpleDateFormat(ISO_DATE_PATTERN).parse(input);
        assertThat(DateUtils.parseIsoDate(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should parse an ISO date-time exactly like new SimpleDateFormat(pattern)")
    void shouldParseIsoDateTime_likeSimpleDateFormat() throws Exception {
        String input = "2026-05-23T14:30:45";
        Date expected = new SimpleDateFormat(ISO_DATETIME_PATTERN).parse(input);
        assertThat(DateUtils.parseIsoDateTime(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should parse a JS date-time (no T, no seconds) exactly like new SimpleDateFormat(pattern)")
    void shouldParseJsIsoDateTimeNoTNoSeconds_likeSimpleDateFormat() throws Exception {
        String input = "2026-05-23 14:30";
        Date expected = new SimpleDateFormat(JS_NO_T_NO_SECONDS_PATTERN).parse(input);
        assertThat(DateUtils.parseJsIsoDateTimeNoTNoSeconds(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should trim surrounding whitespace before parsing an ISO date")
    void shouldTrimWhitespace_beforeParsingIsoDate() throws Exception {
        Date expected = new SimpleDateFormat(ISO_DATE_PATTERN).parse("2026-05-23");
        assertThat(DateUtils.parseIsoDate("  2026-05-23  ")).isEqualTo(expected);
    }

    @Test
    @DisplayName("should return null for a null ISO date")
    void shouldReturnNull_forNullIsoDate() throws Exception {
        assertThat(DateUtils.parseIsoDate(null)).isNull();
    }

    @Test
    @DisplayName("should return null for a blank ISO date")
    void shouldReturnNull_forBlankIsoDate() throws Exception {
        assertThat(DateUtils.parseIsoDate("   ")).isNull();
    }

    @Test
    @DisplayName("should return null for a null ISO date-time")
    void shouldReturnNull_forNullIsoDateTime() throws Exception {
        assertThat(DateUtils.parseIsoDateTime(null)).isNull();
    }

    @Test
    @DisplayName("should return null for a null JS date-time")
    void shouldReturnNull_forNullJsIsoDateTime() throws Exception {
        assertThat(DateUtils.parseJsIsoDateTimeNoTNoSeconds(null)).isNull();
    }

    @Test
    @DisplayName("should parse an ISO date into a Calendar matching parseIsoDate")
    void shouldParseIsoDateAsCalendar_matchingParseIsoDate() throws Exception {
        String input = "2026-05-23";
        GregorianCalendar calendar = DateUtils.parseIsoDateAsCalendar(input);
        assertThat(calendar).isNotNull();
        assertThat(calendar.getTime()).isEqualTo(DateUtils.parseIsoDate(input));
    }

    @Test
    @DisplayName("should return null for a null ISO date when building a Calendar")
    void shouldReturnNull_forNullIsoDateAsCalendar() throws Exception {
        assertThat(DateUtils.parseIsoDateAsCalendar(null)).isNull();
    }
}
