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
package io.github.carlos_emr.carlos.schedule.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Provider availability view request values")
class ScheduleFlipViewRequestUnitTest {
    private static final Date TODAY = new Date(0L);

    @Test
    @DisplayName("should prefer the requested provider, then the preference, then the logged-in provider")
    void shouldResolveProvider_inFallbackOrder() {
        assertThat(ScheduleFlipViewRequest.resolveProviderNo("101", "202", "303")).contains("101");
        assertThat(ScheduleFlipViewRequest.resolveProviderNo(null, "202", "303")).contains("202");
        assertThat(ScheduleFlipViewRequest.resolveProviderNo(null, null, "303")).contains("303");
    }

    @Test
    @DisplayName("should treat a blank provider like a missing one and fall back")
    void shouldFallBack_whenProviderIsBlank() {
        assertThat(ScheduleFlipViewRequest.resolveProviderNo("", " ", "303")).contains("303");
    }

    @Test
    @DisplayName("should resolve nothing when no provider is known")
    void shouldResolveNothing_whenNoProviderIsKnown() {
        assertThat(ScheduleFlipViewRequest.resolveProviderNo(null, null, null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10 OR 1=1", "<script>", "10'", "a/b"})
    @DisplayName("should refuse a requested provider with unexpected characters rather than fall back")
    void shouldRefuseProvider_whenItHasUnexpectedCharacters(String requested) {
        assertThat(ScheduleFlipViewRequest.resolveProviderNo(requested, "202", "303")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"2026-09-05, 2026-09-05", "2026-9-5, 2026-09-05", "2024-02-29, 2024-02-29"})
    @DisplayName("should accept padded and unpadded dates")
    void shouldParseStartDate_whenDateIsReal(String requested, String expected) {
        Date parsed = ScheduleFlipViewRequest.resolveStartDate(requested, TODAY);

        assertThat(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(parsed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"today", "", "   "})
    @DisplayName("should use today when no date is requested")
    void shouldUseToday_whenNoDateIsRequested(String requested) {
        assertThat(ScheduleFlipViewRequest.resolveStartDate(requested, TODAY)).isSameAs(TODAY);
        assertThat(ScheduleFlipViewRequest.resolveStartDate(null, TODAY)).isSameAs(TODAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2026-00-10", "2025-02-29", "20260905", "2026-09-05x",
            "2026/09/05", "tomorrow", "2026-09-05' OR '1'='1"})
    @DisplayName("should refuse a date that is not a real calendar date in the expected form")
    void shouldRefuseStartDate_whenItIsNotARealDate(String requested) {
        assertThatThrownBy(() -> ScheduleFlipViewRequest.resolveStartDate(requested, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"15, 15", "5, 5", "0, 15", "-10, 15"})
    @DisplayName("should fall back to 15-minute slots when the preference holds no usable length")
    void shouldUseDefaultSlotLength_whenPreferenceIsNotPositive(int everyMin, int expected) {
        assertThat(ScheduleFlipViewRequest.slotMinutes(everyMin)).isEqualTo(expected);
    }
}
