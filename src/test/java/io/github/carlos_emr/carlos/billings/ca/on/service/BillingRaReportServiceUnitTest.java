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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingRaReportService} RA report filtering and presentation shaping. */
@DisplayName("BillingRaReportService")
@Tag("unit")
@Tag("billing")
class BillingRaReportServiceUnitTest {

    @Test
    void shouldCalculateRASummaryTotalsWithoutBinaryFloatingPointRounding() {
        BillingOnRaService remittanceAdviceService = mock(BillingOnRaService.class);
        Properties row = new Properties();
        row.setProperty("servicedate", "20260428");
        row.setProperty("explain", "");
        row.setProperty("amountsubmit", "1.005");
        row.setProperty("amountpay", "1.005");
        row.setProperty("location", "00");
        row.setProperty("localServiceDate", "2026-04-28");
        row.setProperty("demo_hin", "1234567890");
        row.setProperty("account", "42");
        when(remittanceAdviceService.getRASummary("1", "123456")).thenReturn(List.of(row));

        BillingRaReportService service = new BillingRaReportService(remittanceAdviceService);
        Map<String, Object> totals = new HashMap<>();

        List result = service.getRASummary("1", "123456", Collections.emptyList(), Collections.emptyList(), totals);

        Properties totalRow = (Properties) result.get(1);
        assertThat(totalRow.getProperty("amountsubmit")).isEqualTo("1.01");
        assertThat(totalRow.getProperty("amountpay")).isEqualTo("1.01");
        assertThat(totalRow.getProperty("clinicPay")).isEqualTo("1.01");
        assertThat(totals).containsEntry("xml_total", new BigDecimal("1.01"));
    }

    @Test
    void shouldBumpPartialCountAndSkipMarker_whenLoadFailureMarkerAppears() {
        // Round-6 contract: BillingOnRaService appends a marker Properties
        // row when its outer catch fires mid-iteration (DAO outage, parse
        // throw). The consumer must (a) skip the marker so it doesn't
        // render as a real row, and (b) bump xml_partial_count so the
        // persister/view-model can refuse to overwrite the snapshot.
        BillingOnRaService remittanceAdviceService = mock(BillingOnRaService.class);

        Properties realRow = new Properties();
        realRow.setProperty("servicedate", "20260428");
        realRow.setProperty("explain", "");
        realRow.setProperty("amountsubmit", "10.00");
        realRow.setProperty("amountpay", "10.00");
        realRow.setProperty("location", "00");
        realRow.setProperty("localServiceDate", "2026-04-28");
        realRow.setProperty("demo_hin", "1234567890");
        realRow.setProperty("account", "42");

        Properties marker = new Properties();
        marker.setProperty(BillingOnRaService.LOAD_FAILURE_MARKER, "true");

        when(remittanceAdviceService.getRASummary("1", "123456"))
                .thenReturn(List.of(realRow, marker));

        BillingRaReportService service = new BillingRaReportService(remittanceAdviceService);
        Map<String, Object> totals = new HashMap<>();

        List result = service.getRASummary("1", "123456",
                Collections.emptyList(), Collections.emptyList(), totals);

        // result is [realRow, totalsRow] — the marker must NOT appear as
        // a rendered row.
        assertThat(result).hasSize(2);
        Properties firstRendered = (Properties) result.get(0);
        assertThat(firstRendered.getProperty(BillingOnRaService.LOAD_FAILURE_MARKER))
                .as("marker row must be filtered out, not rendered")
                .isNull();

        // Partial count exposed for the persister/view-model gate.
        assertThat(totals).containsEntry("xml_partial_count", 1);
    }

    @Test
    void shouldBumpPartialCount_whenRowFlagsAmountUnreadable() {
        // Sibling contract: per-row amountUnreadable flag (set by the
        // upstream parser when a money string couldn't be coerced) also
        // bumps xml_partial_count and zero-coalesces the row out of the
        // running totals so the totals don't silently understate.
        BillingOnRaService remittanceAdviceService = mock(BillingOnRaService.class);

        Properties unreadable = new Properties();
        unreadable.setProperty("servicedate", "20260428");
        unreadable.setProperty("explain", "");
        unreadable.setProperty("amountsubmit", "0.00");
        unreadable.setProperty("amountpay", "0.00");
        unreadable.setProperty("amountUnreadable", "true");
        unreadable.setProperty("location", "00");
        unreadable.setProperty("localServiceDate", "2026-04-28");
        unreadable.setProperty("demo_hin", "1234567890");
        unreadable.setProperty("account", "42");

        when(remittanceAdviceService.getRASummary("1", "123456"))
                .thenReturn(List.of(unreadable));

        BillingRaReportService service = new BillingRaReportService(remittanceAdviceService);
        Map<String, Object> totals = new HashMap<>();

        service.getRASummary("1", "123456",
                Collections.emptyList(), Collections.emptyList(), totals);

        assertThat(totals).containsEntry("xml_partial_count", 1);
        // amountpay zero-coalesced, so xml_total stays at 0.
        assertThat(totals).containsEntry("xml_total", new BigDecimal("0.00"));
    }
}
