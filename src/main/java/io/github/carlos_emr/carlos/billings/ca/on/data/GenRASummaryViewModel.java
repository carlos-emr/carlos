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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code genRASummary.jsp} and
 * {@code genRASummaryDetail.jsp}, the OHIP RA payment-summary pages.
 *
 * <p>Captures the per-bill-detail report rows (each classified into
 * Hospital / Local-clinic / Other-clinic categories), the provider
 * dropdown options, and the per-category cumulative totals.</p>
 *
 * <p>Both Summary and SummaryDetail JSPs share this model — they
 * differ only in cosmetic rendering. Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.GenRASummaryDataAssembler#assemble}
 * and exposed to the JSPs as request attribute {@code raSummaryModel}.</p>
 *
 * <p>Eliminates the 5 inline {@code SpringUtils.getBean} lookups each JSP
 * used to perform across its scriptlet body (RaHeaderDao, RaDetailDao,
 * ProviderDao, BillingDao — twice in different scopes).</p>
 *
 * @since 2026-04-26
 */
public final class GenRASummaryViewModel {

    /**
     * Row classification. The legacy JSP rendered three near-duplicate
     * row blocks based on this; downstream code uses the same value to
     * decide which "Pay" column gets a value vs "N/A".
     */
    public enum Category {
        /** Hospital bills (visit type 02). */
        HOSPITAL,
        /** Local clinic bills (visit type 00 + wasBilledLocal match). */
        LOCAL_CLINIC,
        /** Everything else — other-clinic / out-of-region. */
        OTHER
    }

    private final String raNo;
    private final String selectedProviderOhipNo;
    private final List<ProviderOption> providerOptions;
    private final List<ReportRow> rows;
    private final String invoicedTotal;
    private final String paidTotal;
    private final String clinicPayTotal;
    private final String hospitalPayTotal;
    private final String obTotal;

    public record ProviderOption(String ohipNo, String displayName, boolean selected) {}

    public record ReportRow(String billingNo,
                            String providerName,
                            String demoName,
                            String demoHin,
                            String serviceDate,
                            String serviceCode,
                            String invoicedAmount,
                            String paidAmount,
                            String obAmount,
                            String errorCode,
                            Category category) {
        /** Per-category render: HOSPITAL category shows N/A in the clinic
         *  column. Hoisted out of the JSP scriptlet body so the view layer
         *  can render the cell value with a single EL binding. */
        public String getClinicCell() {
            return category == Category.LOCAL_CLINIC ? paidAmount : "N/A";
        }
        /** Per-category render: LOCAL_CLINIC category shows N/A in the
         *  hospital column. */
        public String getHospitalCell() {
            return category == Category.HOSPITAL ? paidAmount : "N/A";
        }
    }

    private GenRASummaryViewModel(Builder b) {
        this.raNo = nullToEmpty(b.raNo);
        this.selectedProviderOhipNo = nullToEmpty(b.selectedProviderOhipNo);
        this.providerOptions = b.providerOptions == null
                ? Collections.emptyList() : List.copyOf(b.providerOptions);
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
        this.invoicedTotal = nullToZero(b.invoicedTotal);
        this.paidTotal = nullToZero(b.paidTotal);
        this.clinicPayTotal = nullToZero(b.clinicPayTotal);
        this.hospitalPayTotal = nullToZero(b.hospitalPayTotal);
        this.obTotal = nullToZero(b.obTotal);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String nullToZero(String s) { return s == null ? "0.00" : s; }

    public static Builder builder() { return new Builder(); }

    public String getRaNo() { return raNo; }
    public String getSelectedProviderOhipNo() { return selectedProviderOhipNo; }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }
    public List<ReportRow> getRows() { return rows; }
    public String getInvoicedTotal() { return invoicedTotal; }
    public String getPaidTotal() { return paidTotal; }
    public String getClinicPayTotal() { return clinicPayTotal; }
    public String getHospitalPayTotal() { return hospitalPayTotal; }
    public String getObTotal() { return obTotal; }

    public static final class Builder {
        private String raNo;
        private String selectedProviderOhipNo;
        private List<ProviderOption> providerOptions;
        private List<ReportRow> rows;
        private String invoicedTotal;
        private String paidTotal;
        private String clinicPayTotal;
        private String hospitalPayTotal;
        private String obTotal;

        public Builder raNo(String v) { this.raNo = v; return this; }
        public Builder selectedProviderOhipNo(String v) { this.selectedProviderOhipNo = v; return this; }
        // Defensive copies at the setter — mirrors BillingONFormViewModel pattern.
        public Builder providerOptions(List<ProviderOption> v) { this.providerOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder rows(List<ReportRow> v) { this.rows = v == null ? null : List.copyOf(v); return this; }
        public Builder invoicedTotal(String v) { this.invoicedTotal = v; return this; }
        public Builder paidTotal(String v) { this.paidTotal = v; return this; }
        public Builder clinicPayTotal(String v) { this.clinicPayTotal = v; return this; }
        public Builder hospitalPayTotal(String v) { this.hospitalPayTotal = v; return this; }
        public Builder obTotal(String v) { this.obTotal = v; return this; }

        public GenRASummaryViewModel build() { return new GenRASummaryViewModel(this); }
    }
}
