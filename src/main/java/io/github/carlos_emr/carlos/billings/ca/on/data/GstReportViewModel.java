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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.math.BigDecimal;
import java.util.List;

/**
 * View model for {@code admin/gstreport.jsp}. Carries the formatted
 * row list, totals, provider drop-down options, and request-param echoes
 * so the JSP can render via EL only.
 *
 * @since 2026-04-27
 */
public record GstReportViewModel(
        String today,
        String startDate,
        String endDate,
        String providerView,
        List<ProviderOption> providerOptions,
        List<Row> rows,
        BigDecimal gstTotal,
        BigDecimal earnedTotal,
        BigDecimal billedTotal) {

    public String getToday() { return today; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getProviderView() { return providerView; }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }
    public List<Row> getRows() { return rows; }
    public BigDecimal getGstTotal() { return gstTotal; }
    public BigDecimal getEarnedTotal() { return earnedTotal; }
    public BigDecimal getBilledTotal() { return billedTotal; }

    /**
     * One option in the provider drop-down. {@code value} is the provider
     * number; {@code label} is rendered as "lastName, firstName".
     */
    public record ProviderOption(String value, String lastName, String firstName) {
        public String getValue() { return value; }
        public String getLastName() { return lastName; }
        public String getFirstName() { return firstName; }
    }

    /**
     * One row in the GST-by-service-date table. Filtered to rows where
     * {@code gst > 0} (matches the legacy scriptlet's {@code if (gst.doubleValue() > 0)}).
     */
    public record Row(
            String serviceDate,
            String demographicNo,
            String patientName,
            BigDecimal gstBilled,
            BigDecimal earned,
            BigDecimal billed) {
        public String getServiceDate() { return serviceDate; }
        public String getDemographicNo() { return demographicNo; }
        public String getPatientName() { return patientName; }
        public BigDecimal getGstBilled() { return gstBilled; }
        public BigDecimal getEarned() { return earned; }
        public BigDecimal getBilled() { return billed; }
    }
}
