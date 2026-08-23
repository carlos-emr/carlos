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
package io.github.carlos_emr.carlos.providers.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Provider add status validator")
@Tag("unit")
@Tag("provider")
class ProviderAddStatusValidatorUnitTest {

    @Test
    @DisplayName("should build appointment status with valid fragments")
    void shouldBuildAppointmentStatus_withValidFragments() {
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", "C")).isEqualTo("C");
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("C", "S")).isEqualTo("CS");
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", "t")).isEqualTo("t");
    }

    @Test
    @DisplayName("should reject appointment status with missing fragment")
    void shouldRejectAppointmentStatus_withMissingFragment() {
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus(null, "C")).isNull();
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", null)).isNull();
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", "")).isNull();
    }

    @Test
    @DisplayName("should reject appointment status with invalid characters")
    void shouldRejectAppointmentStatus_withInvalidCharacters() {
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", "C&x=1")).isNull();
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("1", "C")).isNull();
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", " C")).isNull();
    }

    @Test
    @DisplayName("should reject appointment status with too many characters")
    void shouldRejectAppointmentStatus_withTooManyCharacters() {
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("CS", "V")).isNull();
        assertThat(ProviderAddStatusValidator.buildValidatedAppointmentStatus("", "CSV")).isNull();
    }

    @Test
    @DisplayName("should match current status when submitted value equals persisted value")
    void shouldMatchCurrentStatus_whenSubmittedValueEqualsPersistedValue() {
        assertThat(ProviderAddStatusValidator.matchesCurrentStatus("T", "T")).isTrue();
        assertThat(ProviderAddStatusValidator.matchesCurrentStatus("TS", "TS")).isTrue();
    }

    @Test
    @DisplayName("should reject current status when submitted value is stale or invalid")
    void shouldRejectCurrentStatus_whenSubmittedValueIsStaleOrInvalid() {
        assertThat(ProviderAddStatusValidator.matchesCurrentStatus("H", "T")).isFalse();
        assertThat(ProviderAddStatusValidator.matchesCurrentStatus("H", null)).isFalse();
        assertThat(ProviderAddStatusValidator.matchesCurrentStatus("H", "H&status=C")).isFalse();
    }

    @Test
    @DisplayName("should match requested status when server calculates same next status")
    void shouldMatchRequestedStatus_whenServerCalculatesSameNextStatus() {
        assertThat(ProviderAddStatusValidator.matchesCalculatedNextStatus("H", "H")).isTrue();
        assertThat(ProviderAddStatusValidator.matchesCalculatedNextStatus("TS", "TS")).isTrue();
    }

    @Test
    @DisplayName("should reject requested status when server calculates different next status")
    void shouldRejectRequestedStatus_whenServerCalculatesDifferentNextStatus() {
        assertThat(ProviderAddStatusValidator.matchesCalculatedNextStatus("H", "C")).isFalse();
        assertThat(ProviderAddStatusValidator.matchesCalculatedNextStatus(null, "H")).isFalse();
        assertThat(ProviderAddStatusValidator.matchesCalculatedNextStatus("H", "H&status=C")).isFalse();
    }
}
