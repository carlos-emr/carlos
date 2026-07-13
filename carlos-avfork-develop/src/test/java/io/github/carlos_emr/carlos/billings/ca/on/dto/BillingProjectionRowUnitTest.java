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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for constructor-projection DTO null handling. */
@DisplayName("Ontario billing projection rows")
@Tag("unit")
@Tag("billing")
class BillingProjectionRowUnitTest {

    @Test
    void shouldCoalesceNullTextFields_whenBilledReportRowIsProjected() {
        BillingOnNewReportBilledRow row = new BillingOnNewReportBilledRow(
                null, null, null, null, null, null, null, null);

        assertThat(row.id()).isEmpty();
        assertThat(row.billingDate()).isEmpty();
        assertThat(row.demographicName()).isEmpty();
        assertThat(row.clinic()).isEmpty();
    }

    @Test
    void shouldCoalesceNullTextFields_whenPaidBillingRowIsProjected() {
        BillingOnNewReportPaidBillingRow row = new BillingOnNewReportPaidBillingRow(null, null);

        assertThat(row.billingNo()).isEmpty();
        assertThat(row.total()).isEmpty();
    }

    @Test
    void shouldCoalesceNullTextFields_whenUnpaidReportRowIsProjected() {
        BillingOnNewReportUnpaidRow row = new BillingOnNewReportUnpaidRow(
                null, null, null, null, null, null, null, null);

        assertThat(row.billingNo()).isEmpty();
        assertThat(row.demographicName()).isEmpty();
        assertThat(row.providerNo()).isEmpty();
        assertThat(row.total()).isEmpty();
    }

    @Test
    void shouldCoalesceNullTextFields_whenClaimReportRowIsProjected() {
        BillingClaimReportRow row = new BillingClaimReportRow(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(row.id()).isEmpty();
        assertThat(row.payProgram()).isEmpty();
        assertThat(row.providerOhipNo()).isEmpty();
        assertThat(row.billingOnItemId()).isEmpty();
    }
}
