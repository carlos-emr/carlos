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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the immutability contract of {@link BillingOnConstants} lookup tables.
 *
 * <p>{@code propMonthCode}, {@code propBillingCenter}, and {@code propBillingType}
 * are {@code public static final} and shared by 88+ call sites — any caller
 * that mutates one of them poisons every other reader process-wide. The fields
 * must reject {@code setProperty}, {@code put}, {@code remove}, and
 * {@code clear} after static initialization.</p>
 */
@DisplayName("BillingOnConstants immutability")
@Tag("unit")
@Tag("billing")
class BillingOnConstantsUnitTest {

    @Test
    void shouldExposeMonthCode_whenReadOnly() {
        assertThat(BillingOnConstants.propMonthCode.getProperty("1")).isEqualTo("A");
        assertThat(BillingOnConstants.propMonthCode.getProperty("12")).isEqualTo("L");
    }

    @Test
    void shouldExposeBillingCenter_whenReadOnly() {
        assertThat(BillingOnConstants.propBillingCenter.getProperty("N")).isEqualTo("Toronto");
    }

    @Test
    void shouldExposeBillingType_whenReadOnly() {
        assertThat(BillingOnConstants.propBillingType.getProperty("O")).isEqualTo("Bill OHIP");
    }

    @Test
    void shouldRejectSetProperty_onMonthCode() {
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.setProperty("1", "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetProperty_onBillingCenter() {
        assertThatThrownBy(() -> BillingOnConstants.propBillingCenter.setProperty("N", "Vancouver"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetProperty_onBillingType() {
        assertThatThrownBy(() -> BillingOnConstants.propBillingType.setProperty("O", "X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectClear() {
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectRemove() {
        assertThatThrownBy(() -> BillingOnConstants.propBillingCenter.remove("N"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
