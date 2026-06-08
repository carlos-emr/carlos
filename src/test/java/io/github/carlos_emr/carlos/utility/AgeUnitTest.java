/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Age} value object.
 *
 * @since 2026-03-31
 */
@DisplayName("Age Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class AgeUnitTest {

    @Test
    @DisplayName("should store days, months, and years")
    void shouldStoreDaysMonthsYears() {
        Age age = new Age(15, 6, 30);
        assertThat(age.getDays()).isEqualTo(15);
        assertThat(age.getMonths()).isEqualTo(6);
        assertThat(age.getYears()).isEqualTo(30);
    }

    @Test
    @DisplayName("should handle zero values for newborn")
    void shouldHandleZeroValues() {
        Age age = new Age(0, 0, 0);
        assertThat(age.getDays()).isZero();
        assertThat(age.getMonths()).isZero();
        assertThat(age.getYears()).isZero();
    }

    @Test
    @DisplayName("should produce readable toString")
    void shouldProduceReadableToString() {
        Age age = new Age(5, 3, 25);
        String str = age.toString();
        assertThat(str).isNotNull().isNotEmpty();
    }
}
