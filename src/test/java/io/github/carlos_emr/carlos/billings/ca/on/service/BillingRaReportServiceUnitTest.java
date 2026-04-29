/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
        Map<String, BigDecimal> totals = new HashMap<>();

        List result = service.getRASummary("1", "123456", Collections.emptyList(), Collections.emptyList(), totals);

        Properties totalRow = (Properties) result.get(1);
        assertThat(totalRow.getProperty("amountsubmit")).isEqualTo("1.01");
        assertThat(totalRow.getProperty("amountpay")).isEqualTo("1.01");
        assertThat(totalRow.getProperty("clinicPay")).isEqualTo("1.01");
        assertThat(totals).containsEntry("xml_total", new BigDecimal("1.01"));
    }
}
