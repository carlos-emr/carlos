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

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for the five ON billing-report JSPF fragments
 * ({@code billingReport_billed.jspf}, {@code billingReport_unsettled.jspf},
 * {@code billingReport_billob.jspf}, {@code billingReport_flu.jspf},
 * {@code billingReport_unbilled.jspf}).
 *
 * <p>The fragments are included from the parent
 * {@code billingReportControl.jsp}. The view model captures the per-fragment
 * row lists plus the totals (billob {@code BigOBTotal}; flu one total and a
 * count) so each JSPF body receives a complete render model.</p>
 *
 * <p>Each fragment uses only the subset of fields its template renders
 * ({@code billedRows}, {@code unsettledRows}, {@code billobRows} +
 * {@code billobTotal}, {@code fluRows} + {@code fluTotal} + {@code fluCount}).
 * The other fields stay as empty
 * lists / "0.00" defaults — assembling all four shapes from one model
 * keeps the parent-jsp include order intact without forcing four separate
 * request-attribute keys.</p>
 *
 * @since 2026-04-26
 */
public final class BillingReportFragmentViewModel {

    /** Row in billingReport_billed.jspf — an open Ontario claim in the date range; billingId is the claim's id. */
    public record BilledRow(
            String apptDate,
            String apptTime,
            String demoName,
            String reasonText,
            String note,
            int billingId,
            String reasonCode,
            String rowBgColor) { }

    /** Row in billingReport_unsettled.jspf — an Ontario claim submitted and not yet settled; billingId is its id. */
    public record UnsettledRow(
            String apptDate,
            String apptTime,
            String demoName,
            String note,
            int billingId,
            String reasonCode,
            String rowBgColor) { }

    /** Row in billingReport_billob.jspf — an Ontario claim with an obstetric service code, and its items' codes. */
    public record BillobRow(
            int billingId,
            String demoName,
            String apptDate,
            List<String> serviceCodes,   // up to 10 entries (legacy param2[0..9])
            String total,                // formatted with decimal point
            String reasonCode,           // claim status (S/B/X/...)
            String rowBgColor) { }

    /** Row in billingReport_flu.jspf — an Ontario claim with a flu-shot service code; billingId is its id. */
    public record FluRow(
            int billingId,
            String demoName,
            String apptDate,             // the claim's service date
            String total,                // the claim's total, with a decimal point
            String reasonCode,           // claim status (S/B/X/O/...)
            String statusLabel,          // the status as the list shows it
            String rowBgColor) { }

    /** One unbill-history row (open appointments awaiting billing). */
    public record UnbilledRow(
            String apptNo,
            String demoNo,
            String demoName,
            String userNo,
            String apptDate,
            String apptTime,
            String reason,
            String rowBgColor,
            /** Pre-built popup-page URL fragment for the "Bill" link. */
            String popupUrl) { }

    private final List<BilledRow> billedRows;
    private final List<UnsettledRow> unsettledRows;
    private final List<BillobRow> billobRows;
    private final String billobTotal;
    private final List<FluRow> fluRows;
    private final String fluTotal;
    private final int fluCount;
    private final int billobUnreadableTotalCount;
    private final List<UnbilledRow> unbilledRows;

    private BillingReportFragmentViewModel(Builder b) {
        // Builder setters already wrap each list in List.copyOf, producing
        // immutable copies. The ctor only has to default null → emptyList;
        // copying again would allocate + iterate every row a second time
        // (real cost on large report tables).
        this.billedRows = b.billedRows == null ? Collections.emptyList() : b.billedRows;
        this.unsettledRows = b.unsettledRows == null ? Collections.emptyList() : b.unsettledRows;
        this.billobRows = b.billobRows == null ? Collections.emptyList() : b.billobRows;
        this.billobTotal = nullToZero(b.billobTotal);
        this.fluRows = b.fluRows == null ? Collections.emptyList() : b.fluRows;
        this.fluTotal = nullToZero(b.fluTotal);
        this.fluCount = b.fluCount;
        this.billobUnreadableTotalCount = b.billobUnreadableTotalCount;
        this.unbilledRows = b.unbilledRows == null ? Collections.emptyList() : b.unbilledRows;
    }

    private static String nullToZero(String s) { return s == null ? "0.00" : s; }

    public static Builder builder() { return new Builder(); }

    public List<BilledRow> getBilledRows() { return billedRows; }
    public List<UnsettledRow> getUnsettledRows() { return unsettledRows; }
    public List<BillobRow> getBillobRows() { return billobRows; }
    public String getBillobTotal() { return billobTotal; }
    public List<FluRow> getFluRows() { return fluRows; }
    public String getFluTotal() { return fluTotal; }
    public int getFluCount() { return fluCount; }
    public int getBillobUnreadableTotalCount() { return billobUnreadableTotalCount; }
    public boolean isBillobTotalPartial() { return billobUnreadableTotalCount > 0; }
    public List<UnbilledRow> getUnbilledRows() { return unbilledRows; }

    public static final class Builder {
        private List<BilledRow> billedRows;
        private List<UnsettledRow> unsettledRows;
        private List<BillobRow> billobRows;
        private String billobTotal;
        private List<FluRow> fluRows;
        private String fluTotal;
        private int fluCount;
        private int billobUnreadableTotalCount;
        private List<UnbilledRow> unbilledRows;

        public Builder billedRows(List<BilledRow> v) { this.billedRows = v == null ? null : List.copyOf(v); return this; }
        public Builder unsettledRows(List<UnsettledRow> v) { this.unsettledRows = v == null ? null : List.copyOf(v); return this; }
        public Builder billobRows(List<BillobRow> v) { this.billobRows = v == null ? null : List.copyOf(v); return this; }
        public Builder billobTotal(String v) { this.billobTotal = v; return this; }
        public Builder fluRows(List<FluRow> v) { this.fluRows = v == null ? null : List.copyOf(v); return this; }
        public Builder fluTotal(String v) { this.fluTotal = v; return this; }
        public Builder fluCount(int v) { this.fluCount = v; return this; }
        public Builder billobUnreadableTotalCount(int v) { this.billobUnreadableTotalCount = v; return this; }
        public Builder unbilledRows(List<UnbilledRow> v) { this.unbilledRows = v == null ? null : List.copyOf(v); return this; }

        public BillingReportFragmentViewModel build() { return new BillingReportFragmentViewModel(this); }
    }
}
