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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ON billing date parsing")
@Tag("unit")
@Tag("billing")
class BillingDatesUnitTest {

    @Test
    void shouldNormalizeOhipEffectiveDate_withNullFallback() {
        LocalDate fallback = LocalDate.of(2026, 4, 28);

        assertThat(BillingDates.ohipEffectiveDate("20260427", fallback)).isEqualTo("2026-04-27");
        assertThat(BillingDates.ohipEffectiveDate("null", fallback)).isEqualTo("2026-04-28");
    }

    @Test
    void shouldNormalizeOhipTerminationDate_forPastEndOfMonth() {
        assertThat(BillingDates.ohipTerminationDate("99999999")).isEqualTo("9999-12-31");
        assertThat(BillingDates.ohipTerminationDate("20260400")).isEqualTo("2026-04-01");
    }

    @Test
    void shouldRejectMalformedOhipDates_withIllegalArgumentException() {
        assertThatThrownBy(() -> BillingDates.ohipEffectiveDate("202604", LocalDate.of(2026, 4, 28)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldParseIsoDate_whenInputIsValid() {
        // Round-trip via formatIsoDate to assert the contract rather than
        // the underlying Date subclass's toString format.
        assertThat(BillingDates.formatIsoDate(BillingDates.parseIsoDate("2026-04-28")))
                .isEqualTo("2026-04-28");
    }

    @Test
    void shouldReturnUtilDateThatSupportsToInstant_fromParseIsoDate() {
        // Liskov: parseIsoDate returns Date, callers expect .toInstant() to work.
        // java.sql.Date overrides toInstant() to throw — this test catches a
        // regression that swaps back to sql.Date.
        java.util.Date d = BillingDates.parseIsoDate("2026-04-28");
        assertThat(d.toInstant()).isNotNull();
    }

    @Test
    void shouldThrow_whenIsoDateIsNull() {
        assertThatThrownBy(() -> BillingDates.parseIsoDate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldThrow_whenIsoDateIsBlank() {
        assertThatThrownBy(() -> BillingDates.parseIsoDate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldThrow_whenIsoDateIsMalformed() {
        // Used to silently substitute today() — recording an audit-incorrect
        // service date on the OHIP claim. Must surface to the caller.
        assertThatThrownBy(() -> BillingDates.parseIsoDate("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-date");
    }

    @Test
    void shouldThrow_whenIsoDateHasWrongFormat() {
        assertThatThrownBy(() -> BillingDates.parseIsoDate("04/28/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("04/28/2026");
    }

    // ---- parseOptionalIsoDate: lenient on null/blank, strict otherwise ----

    @Test
    void shouldReturnNull_whenOptionalIsoDateIsNull() {
        assertThat(BillingDates.parseOptionalIsoDate(null, "service_date")).isNull();
    }

    @Test
    void shouldReturnNull_whenOptionalIsoDateIsBlank() {
        assertThat(BillingDates.parseOptionalIsoDate("   ", "service_date")).isNull();
    }

    @Test
    void shouldParse_whenOptionalIsoDateIsValid() {
        assertThat(BillingDates.formatIsoDate(
                BillingDates.parseOptionalIsoDate("2026-04-28", "service_date")))
                .isEqualTo("2026-04-28");
    }

    @Test
    void shouldThrowWithFieldName_whenOptionalIsoDateIsMalformed() {
        assertThatThrownBy(() -> BillingDates.parseOptionalIsoDate("not-a-date", "billing_date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billing_date")
                .hasMessageContaining("not-a-date");
    }

    // ---- parseOptionalIsoTime: HH:mm:ss companion to parseOptionalIsoDate ----

    @Test
    void shouldReturnNull_whenOptionalIsoTimeIsNull() {
        assertThat(BillingDates.parseOptionalIsoTime(null, "billing_time")).isNull();
    }

    @Test
    void shouldReturnNull_whenOptionalIsoTimeIsBlank() {
        assertThat(BillingDates.parseOptionalIsoTime("   ", "billing_time")).isNull();
    }

    @Test
    void shouldParse_whenOptionalIsoTimeIsValid() {
        assertThat(BillingDates.parseOptionalIsoTime("12:34:56", "billing_time")).isNotNull();
    }

    @Test
    void shouldThrowWithFieldName_whenOptionalIsoTimeIsMalformed() {
        assertThatThrownBy(() -> BillingDates.parseOptionalIsoTime("99:99:99", "billing_time"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billing_time")
                .hasMessageContaining("99:99:99");
    }

    @Test
    void shouldThrowWithFieldName_whenOptionalIsoTimeHasWrongFormat() {
        assertThatThrownBy(() -> BillingDates.parseOptionalIsoTime("12-34-56", "billing_time"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billing_time");
    }

    // ---- parseIsoTime (strict variant — used by BillingOnCorrectionPersister
    //      where silently substituting a default time would record audit-
    //      incorrect timestamps on OHIP claims) -------------------------------

    @Test
    void shouldParseIsoTime_whenInputIsValid() {
        java.util.Date d = BillingDates.parseIsoTime("12:34:56");
        assertThat(d).isNotNull();
    }

    @Test
    void shouldThrow_whenIsoTimeIsNull() {
        assertThatThrownBy(() -> BillingDates.parseIsoTime(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldThrow_whenIsoTimeIsBlank() {
        assertThatThrownBy(() -> BillingDates.parseIsoTime("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void shouldThrow_whenIsoTimeIsMalformed() {
        assertThatThrownBy(() -> BillingDates.parseIsoTime("99:99:99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99:99:99");
    }

    // ---- formatIso* (null-safe-empty contract) ------------------------------

    @Test
    void shouldReturnEmpty_whenFormatIsoDateGivenNull() {
        assertThat(BillingDates.formatIsoDate(null)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenFormatIsoTimeGivenNull() {
        assertThat(BillingDates.formatIsoTime(null)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenFormatIsoTimestampGivenNull() {
        assertThat(BillingDates.formatIsoTimestamp(null)).isEmpty();
    }

    @Test
    void shouldRoundTrip_parseIsoDate_andFormatIsoDate() {
        java.util.Date d = BillingDates.parseIsoDate("2026-04-30");
        assertThat(BillingDates.formatIsoDate(d)).isEqualTo("2026-04-30");
    }

    @Test
    void shouldRoundTrip_parseIsoTime_andFormatIsoTime() {
        java.util.Date d = BillingDates.parseIsoTime("12:34:56");
        assertThat(BillingDates.formatIsoTime(d)).isEqualTo("12:34:56");
    }

    // ---- BillingONItem.serviceDate is @Temporal(TemporalType.DATE);
    //      Hibernate hydrates it as java.sql.Date, whose toInstant()
    //      throws UnsupportedOperationException. These tests pin the
    //      contract by constructing sql.Date / sql.Time / sql.Timestamp
    //      explicitly.

    @Test
    void shouldFormatSqlDate_withoutThrowing() {
        java.sql.Date sqlDate = java.sql.Date.valueOf("2026-04-30");
        assertThat(BillingDates.formatIsoDate(sqlDate)).isEqualTo("2026-04-30");
    }

    @Test
    void shouldFormatSqlTime_withoutThrowing() {
        java.sql.Time sqlTime = java.sql.Time.valueOf("12:34:56");
        assertThat(BillingDates.formatIsoTime(sqlTime)).isEqualTo("12:34:56");
    }

    @Test
    void shouldFormatSqlTimestamp_withoutThrowing() {
        java.sql.Timestamp sqlTs = java.sql.Timestamp.valueOf("2026-04-30 12:34:56");
        assertThat(BillingDates.formatIsoTimestamp(sqlTs)).isEqualTo("2026-04-30 12:34:56");
    }
}
