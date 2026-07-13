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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillingOnRequestParameters}.
 *
 * <p>Regression armor for the prior bug where the review assembler passed
 * {@code "providerNo|ohipNo"} unstripped into {@code ProviderDao.getProvider(...)},
 * silently returning null. The same shape lives in two assemblers; the
 * helper centralizes the parsing so both stay consistent.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("BillingOnRequestParameters.extractProviderNo")
@Tag("unit")
@Tag("billing")
class BillingOnRequestParametersUnitTest {

    @Test
    void shouldStripPipeSuffix_whenXmlProviderHasPickerSelection() {
        assertThat(BillingOnRequestParameters.extractProviderNo("999998|OHIP1234", "999998"))
                .isEqualTo("999998");
    }

    @Test
    void shouldFallBackToProviderView_whenXmlProviderIsNull() {
        assertThat(BillingOnRequestParameters.extractProviderNo(null, "999998"))
                .isEqualTo("999998");
    }

    @Test
    void shouldFallBackToProviderView_whenXmlProviderIsEmpty() {
        assertThat(BillingOnRequestParameters.extractProviderNo("", "999998"))
                .isEqualTo("999998");
    }

    @Test
    void shouldStripPipe_evenInProviderViewFallback() {
        // The single-value providerview historically didn't include the pipe,
        // but if a tampered request sends "999998|x" through it, the helper
        // still strips — better safe than passing garbage to ProviderDao.
        assertThat(BillingOnRequestParameters.extractProviderNo(null, "999998|stuff"))
                .isEqualTo("999998");
    }

    @Test
    void shouldReturnEmptyString_whenBothInputsAreNull() {
        assertThat(BillingOnRequestParameters.extractProviderNo(null, null)).isEqualTo("");
    }

    @Test
    void shouldReturnEmptyString_whenBothInputsAreEmpty() {
        assertThat(BillingOnRequestParameters.extractProviderNo("", "")).isEqualTo("");
    }

    @Test
    void shouldReturnUnchanged_whenNoPipePresent() {
        assertThat(BillingOnRequestParameters.extractProviderNo("999998", null))
                .isEqualTo("999998");
    }

    @Test
    void shouldReturnEmpty_whenXmlProviderIsLeadingPipe() {
        // "|OHIP1" is degenerate but should produce empty rather than NPE.
        assertThat(BillingOnRequestParameters.extractProviderNo("|OHIP1", null))
                .isEqualTo("");
    }

    @Test
    void shouldTrimWhitespace_whenNoPipePresent() {
        assertThat(BillingOnRequestParameters.extractProviderNo("  999998  ", null))
                .isEqualTo("999998");
    }

    @Test
    void shouldTrimBeforeAndAfterPipeExtraction_whenXmlProviderHasWhitespace() {
        assertThat(BillingOnRequestParameters.extractProviderNo(" 999998 | OHIP1234 ", null))
                .isEqualTo("999998");
    }

    @Test
    void shouldTrimBeforeAndAfterPipeExtraction_whenProviderViewFallbackHasWhitespace() {
        assertThat(BillingOnRequestParameters.extractProviderNo(null, " 888888 | stuff "))
                .isEqualTo("888888");
    }
}
