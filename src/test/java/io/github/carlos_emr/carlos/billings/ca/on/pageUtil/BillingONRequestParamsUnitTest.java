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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillingONRequestParams}.
 *
 * <p>Regression armor for the prior bug where the review assembler passed
 * {@code "providerNo|ohipNo"} unstripped into {@code ProviderDao.getProvider(...)},
 * silently returning null. The same shape lives in two assemblers; the
 * helper centralizes the parsing so both stay consistent.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("BillingONRequestParams.extractProviderNo")
@Tag("unit")
@Tag("billing")
class BillingONRequestParamsUnitTest {

    @Test
    void shouldStripPipeSuffix_whenXmlProviderHasPickerSelection() {
        assertThat(BillingONRequestParams.extractProviderNo("999998|OHIP1234", "999998"))
                .isEqualTo("999998");
    }

    @Test
    void shouldFallBackToProviderView_whenXmlProviderIsNull() {
        assertThat(BillingONRequestParams.extractProviderNo(null, "999998"))
                .isEqualTo("999998");
    }

    @Test
    void shouldFallBackToProviderView_whenXmlProviderIsEmpty() {
        assertThat(BillingONRequestParams.extractProviderNo("", "999998"))
                .isEqualTo("999998");
    }

    @Test
    void shouldStripPipe_evenInProviderViewFallback() {
        // The single-value providerview historically didn't include the pipe,
        // but if a tampered request sends "999998|x" through it, the helper
        // still strips — better safe than passing garbage to ProviderDao.
        assertThat(BillingONRequestParams.extractProviderNo(null, "999998|stuff"))
                .isEqualTo("999998");
    }

    @Test
    void shouldReturnEmptyString_whenBothInputsAreNull() {
        assertThat(BillingONRequestParams.extractProviderNo(null, null)).isEqualTo("");
    }

    @Test
    void shouldReturnEmptyString_whenBothInputsAreEmpty() {
        assertThat(BillingONRequestParams.extractProviderNo("", "")).isEqualTo("");
    }

    @Test
    void shouldReturnUnchanged_whenNoPipePresent() {
        assertThat(BillingONRequestParams.extractProviderNo("999998", null))
                .isEqualTo("999998");
    }

    @Test
    void shouldReturnEmpty_whenXmlProviderIsLeadingPipe() {
        // "|OHIP1" is degenerate but should produce empty rather than NPE.
        assertThat(BillingONRequestParams.extractProviderNo("|OHIP1", null))
                .isEqualTo("");
    }
}
