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
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for {@link BillingClaimReportFilter} date invariants. */
@DisplayName("Ontario claim report filter")
@Tag("unit")
@Tag("billing")
class BillingClaimReportFilterUnitTest {

    @Test
    void shouldKeepOptionalDates_whenDatesAreBlank() {
        BillingClaimReportFilter filter = new BillingClaimReportFilter(
                "HCP", "O", "999", "", " ", "100", "A007", "401", "00");

        assertThat(filter.startDate()).isEmpty();
        assertThat(filter.endDate()).isEqualTo(" ");
    }

    @Test
    void shouldThrowValidation_whenEndDatePrecedesStartDate() {
        assertThatThrownBy(() -> new BillingClaimReportFilter(
                "HCP", "O", "999", "2026-04-30", "2026-04-01", "100", "A007", "401", "00"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("endDate must be on or after startDate");
    }

    @Test
    void shouldSanitizeRawDate_whenDateIsMalformed() {
        assertThatThrownBy(() -> new BillingClaimReportFilter(
                "HCP", "O", "999", "2026-04-01\r\nINJECT", "2026-04-30", "100", "A007", "401", "00"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("2026-04-01INJECT")
                .hasMessageNotContaining("\r")
                .hasMessageNotContaining("\n");
    }
}
