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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AddPrevention2Action#parseDemographicNo(String)}.
 *
 * <p>Covers issue #2757: before the fix, a missing, non-numeric, or too-large
 * {@code demographic_no} reached {@code Integer.parseInt(...)} in {@code validate()}
 * with no guard, threw a {@link NumberFormatException}, and showed up as a 500 error
 * instead of a validation message. The too-large (overflow) case is the one a plain
 * {@code matches("\\d+")} check misses, so it is checked here too.
 *
 * @since 2026-07-31
 */
@Tag("unit")
class AddPrevention2ActionParseDemographicNoUnitTest {

    @Test
    @DisplayName("should return null when demographic_no is null (missing parameter)")
    void shouldReturnNull_whenDemographicNoIsNull() {
        assertThat(AddPrevention2Action.parseDemographicNo(null)).isNull();
    }

    @Test
    @DisplayName("should return null when demographic_no is an empty string")
    void shouldReturnNull_whenDemographicNoIsEmpty() {
        assertThat(AddPrevention2Action.parseDemographicNo("")).isNull();
    }

    @Test
    @DisplayName("should return null when demographic_no is non-numeric")
    void shouldReturnNull_whenDemographicNoIsNonNumeric() {
        assertThat(AddPrevention2Action.parseDemographicNo("abc")).isNull();
    }

    @Test
    @DisplayName("should return null when demographic_no mixes digits and letters")
    void shouldReturnNull_whenDemographicNoMixesDigitsAndLetters() {
        assertThat(AddPrevention2Action.parseDemographicNo("12a")).isNull();
    }

    @Test
    @DisplayName("should return null when demographic_no overflows the int range")
    void shouldReturnNull_whenDemographicNoOverflowsIntRange() {
        // All digits, so it passes the shape check, but exceeds Integer.MAX_VALUE.
        assertThat(AddPrevention2Action.parseDemographicNo("99999999999")).isNull();
    }

    @Test
    @DisplayName("should return null when demographic_no is exactly one past Integer.MAX_VALUE")
    void shouldReturnNull_whenDemographicNoIsOnePastMaxInt() {
        assertThat(AddPrevention2Action.parseDemographicNo("2147483648")).isNull();
    }

    @Test
    @DisplayName("should return parsed identifier when demographic_no is a valid integer")
    void shouldReturnParsedIdentifier_whenDemographicNoIsValid() {
        assertThat(AddPrevention2Action.parseDemographicNo("123")).isEqualTo(123);
    }

    @Test
    @DisplayName("should return parsed identifier at the Integer.MAX_VALUE boundary")
    void shouldReturnParsedIdentifier_atMaxIntBoundary() {
        assertThat(AddPrevention2Action.parseDemographicNo("2147483647")).isEqualTo(Integer.MAX_VALUE);
    }
}
