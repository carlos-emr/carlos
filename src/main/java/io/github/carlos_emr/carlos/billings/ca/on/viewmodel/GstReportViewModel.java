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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public GstReportViewModel {
        providerOptions = providerOptions == null ? List.of() : List.copyOf(providerOptions);
        rows = rows == null ? List.of() : List.copyOf(rows);
        gstTotal = money(gstTotal);
        earnedTotal = money(earnedTotal);
        billedTotal = money(billedTotal);
    }

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
        public Row {
            serviceDate = serviceDate == null ? "" : serviceDate;
            demographicNo = demographicNo == null ? "" : demographicNo;
            patientName = patientName == null ? "" : patientName;
            gstBilled = money(gstBilled);
            earned = money(earned);
            billed = money(billed);
        }

        public String getServiceDate() { return serviceDate; }
        public String getDemographicNo() { return demographicNo; }
        public String getPatientName() { return patientName; }
        public BigDecimal getGstBilled() { return gstBilled; }
        public BigDecimal getEarned() { return earned; }
        public BigDecimal getBilled() { return billed; }
    }

    private static BigDecimal money(BigDecimal value) {
        // Normalize every monetary cell to scale 2 here so the JSP and total
        // assertions do not each need to encode their own null/rounding rules.
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
