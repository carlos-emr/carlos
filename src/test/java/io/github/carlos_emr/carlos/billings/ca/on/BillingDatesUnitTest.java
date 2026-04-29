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
}
