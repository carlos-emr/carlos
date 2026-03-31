/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DateRange} immutable date range value object.
 *
 * @since 2026-03-31
 */
@DisplayName("DateRange Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class DateRangeUnitTest {

    private Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create range with valid from and to dates")
        void shouldCreateRange_withValidDates() {
            DateRange range = new DateRange(date(2026, 1, 1), date(2026, 12, 31));
            assertThat(range.getFrom()).isNotNull();
            assertThat(range.getTo()).isNotNull();
        }

        @Test
        @DisplayName("should allow single-day range where from equals to")
        void shouldAllowSingleDayRange() {
            Date sameDay = date(2026, 3, 31);
            DateRange range = new DateRange(sameDay, sameDay);
            assertThat(range.getFrom()).isEqualTo(range.getTo());
        }

        @Test
        @DisplayName("should throw when from is after to")
        void shouldThrow_whenFromAfterTo() {
            assertThatThrownBy(() -> new DateRange(date(2026, 12, 31), date(2026, 1, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("From date must preceed to date");
        }

        @Test
        @DisplayName("should allow null from (open-ended start)")
        void shouldAllowNullFrom() {
            DateRange range = new DateRange(null, date(2026, 12, 31));
            assertThat(range.getFrom()).isNull();
            assertThat(range.getTo()).isNotNull();
        }

        @Test
        @DisplayName("should allow null to (open-ended end)")
        void shouldAllowNullTo() {
            DateRange range = new DateRange(date(2026, 1, 1), null);
            assertThat(range.getFrom()).isNotNull();
            assertThat(range.getTo()).isNull();
        }
    }

    @Nested
    @DisplayName("isInRange")
    class IsInRange {

        @Test
        @DisplayName("should return true for date inside range")
        void shouldReturnTrue_forDateInsideRange() {
            DateRange range = new DateRange(date(2026, 1, 1), date(2026, 12, 31));
            assertThat(range.isInRange(date(2026, 6, 15))).isTrue();
        }

        @Test
        @DisplayName("should return false for date before range")
        void shouldReturnFalse_forDateBeforeRange() {
            DateRange range = new DateRange(date(2026, 3, 1), date(2026, 12, 31));
            assertThat(range.isInRange(date(2025, 12, 31))).isFalse();
        }

        @Test
        @DisplayName("should return false for date after range")
        void shouldReturnFalse_forDateAfterRange() {
            DateRange range = new DateRange(date(2026, 1, 1), date(2026, 6, 30));
            assertThat(range.isInRange(date(2027, 1, 1))).isFalse();
        }

        @Test
        @DisplayName("should return true for any date when open-ended start")
        void shouldReturnTrue_whenOpenEndedStart() {
            DateRange range = new DateRange(null, date(2026, 12, 31));
            assertThat(range.isInRange(date(2020, 1, 1))).isTrue();
        }

        @Test
        @DisplayName("should return true for any date when open-ended end")
        void shouldReturnTrue_whenOpenEndedEnd() {
            DateRange range = new DateRange(date(2026, 1, 1), null);
            assertThat(range.isInRange(date(2030, 12, 31))).isTrue();
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal for same dates")
        void shouldBeEqual_forSameDates() {
            DateRange r1 = new DateRange(date(2026, 1, 1), date(2026, 12, 31));
            DateRange r2 = new DateRange(date(2026, 1, 1), date(2026, 12, 31));
            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different dates")
        void shouldNotBeEqual_forDifferentDates() {
            DateRange r1 = new DateRange(date(2026, 1, 1), date(2026, 6, 30));
            DateRange r2 = new DateRange(date(2026, 1, 1), date(2026, 12, 31));
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqual_toNull() {
            DateRange r = new DateRange(date(2026, 1, 1), date(2026, 12, 31));
            assertThat(r).isNotEqualTo(null);
        }
    }
}
