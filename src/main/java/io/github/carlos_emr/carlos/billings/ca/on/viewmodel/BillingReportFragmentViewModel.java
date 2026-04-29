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
 * Immutable view model for the four ON billing-report JSPF fragments
 * ({@code billingReport_billed.jspf}, {@code billingReport_unsettled.jspf},
 * {@code billingReport_billob.jspf}, {@code billingReport_flu.jspf}).
 *
 * <p>The fragments are included from the parent
 * {@code billingReportControl.jsp}; they previously read parent-scope locals
 * ({@code billingDao}, {@code billingDetailDao}, {@code curYear}, etc.) and
 * iterated DAO results inline. The view model captures the per-fragment row
 * lists plus the totals (billob {@code BigOBTotal}; flu {@code Total1} +
 * {@code Total2}) so each JSPF body becomes pure EL.</p>
 *
 * <p>Each fragment uses only the subset of fields its template renders
 * ({@code billedRows}, {@code unsettledRows}, {@code billobRows} +
 * {@code billobTotal}, {@code fluClinicRows} + {@code fluWalkinRows} +
 * {@code fluTotal1} + {@code fluTotal2}). The other fields stay as empty
 * lists / "0.00" defaults — assembling all four shapes from one model
 * keeps the parent-jsp include order intact without forcing four separate
 * request-attribute keys.</p>
 *
 * @since 2026-04-26
 */
public final class BillingReportFragmentViewModel {

    /** Row in billingReport_billed.jspf — a Billing entry filtered by date range. */
    public record BilledRow(
            String apptDate,
            String apptTime,
            String demoName,
            String reasonText,
            String note,
            int billingId,
            String reasonCode,
            String rowBgColor) { }

    /** Row in billingReport_unsettled.jspf — a Billing entry filtered to unsettled status. */
    public record UnsettledRow(
            String apptDate,
            String apptTime,
            String demoName,
            String note,
            int billingId,
            String reasonCode,
            String rowBgColor) { }

    /** Row in billingReport_billob.jspf — search_billob row joined with service codes. */
    public record BillobRow(
            int billingId,
            String demoName,
            String apptDate,
            List<String> serviceCodes,   // up to 10 entries (legacy param2[0..9])
            String total,                // formatted with decimal point
            String reasonCode,           // bill status (S/B/X/...)
            String rowBgColor) { }

    /** Row in billingReport_flu.jspf for the clinic-count side (specialty != "flu"). */
    public record FluClinicRow(
            int billingId,
            String demoName,
            String apptDate,
            String total,
            String reasonCode,
            String reasonLabel,        // "Bad Debt" / "Submitted to OHIP" / "Settled" / "" if anchor
            boolean reasonIsAnchor,    // true when the JSP should render the -B anchor instead of the label
            String rowBgColor) { }

    /** Row in billingReport_flu.jspf for the walk-in side (specialty == "flu"). */
    public record FluWalkinRow(
            int billingId,
            String demoName,
            String apptDate,
            String total,
            String reasonCode,
            String reasonLabel,
            boolean reasonIsAnchor,
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
    private final List<FluClinicRow> fluClinicRows;
    private final List<FluWalkinRow> fluWalkinRows;
    private final String fluTotal1; // walk-in total (specialty == "flu")
    private final String fluTotal2; // clinic total (specialty != "flu")
    private final int fluClinicCount;
    private final int fluWalkinCount;
    private final List<UnbilledRow> unbilledRows;

    private BillingReportFragmentViewModel(Builder b) {
        this.billedRows = b.billedRows == null
                ? Collections.emptyList() : List.copyOf(b.billedRows);
        this.unsettledRows = b.unsettledRows == null
                ? Collections.emptyList() : List.copyOf(b.unsettledRows);
        this.billobRows = b.billobRows == null
                ? Collections.emptyList() : List.copyOf(b.billobRows);
        this.billobTotal = nullToZero(b.billobTotal);
        this.fluClinicRows = b.fluClinicRows == null
                ? Collections.emptyList() : List.copyOf(b.fluClinicRows);
        this.fluWalkinRows = b.fluWalkinRows == null
                ? Collections.emptyList() : List.copyOf(b.fluWalkinRows);
        this.fluTotal1 = nullToZero(b.fluTotal1);
        this.fluTotal2 = nullToZero(b.fluTotal2);
        this.fluClinicCount = b.fluClinicCount;
        this.fluWalkinCount = b.fluWalkinCount;
        this.unbilledRows = b.unbilledRows == null
                ? Collections.emptyList() : List.copyOf(b.unbilledRows);
    }

    private static String nullToZero(String s) { return s == null ? "0.00" : s; }

    public static Builder builder() { return new Builder(); }

    public List<BilledRow> getBilledRows() { return billedRows; }
    public List<UnsettledRow> getUnsettledRows() { return unsettledRows; }
    public List<BillobRow> getBillobRows() { return billobRows; }
    public String getBillobTotal() { return billobTotal; }
    public List<FluClinicRow> getFluClinicRows() { return fluClinicRows; }
    public List<FluWalkinRow> getFluWalkinRows() { return fluWalkinRows; }
    public String getFluTotal1() { return fluTotal1; }
    public String getFluTotal2() { return fluTotal2; }
    public int getFluClinicCount() { return fluClinicCount; }
    public int getFluWalkinCount() { return fluWalkinCount; }
    public List<UnbilledRow> getUnbilledRows() { return unbilledRows; }

    public static final class Builder {
        private List<BilledRow> billedRows;
        private List<UnsettledRow> unsettledRows;
        private List<BillobRow> billobRows;
        private String billobTotal;
        private List<FluClinicRow> fluClinicRows;
        private List<FluWalkinRow> fluWalkinRows;
        private String fluTotal1;
        private String fluTotal2;
        private int fluClinicCount;
        private int fluWalkinCount;
        private List<UnbilledRow> unbilledRows;

        public Builder billedRows(List<BilledRow> v) { this.billedRows = v == null ? null : List.copyOf(v); return this; }
        public Builder unsettledRows(List<UnsettledRow> v) { this.unsettledRows = v == null ? null : List.copyOf(v); return this; }
        public Builder billobRows(List<BillobRow> v) { this.billobRows = v == null ? null : List.copyOf(v); return this; }
        public Builder billobTotal(String v) { this.billobTotal = v; return this; }
        public Builder fluClinicRows(List<FluClinicRow> v) { this.fluClinicRows = v == null ? null : List.copyOf(v); return this; }
        public Builder fluWalkinRows(List<FluWalkinRow> v) { this.fluWalkinRows = v == null ? null : List.copyOf(v); return this; }
        public Builder fluTotal1(String v) { this.fluTotal1 = v; return this; }
        public Builder fluTotal2(String v) { this.fluTotal2 = v; return this; }
        public Builder fluClinicCount(int v) { this.fluClinicCount = v; return this; }
        public Builder fluWalkinCount(int v) { this.fluWalkinCount = v; return this; }
        public Builder unbilledRows(List<UnbilledRow> v) { this.unbilledRows = v == null ? null : List.copyOf(v); return this; }

        public BillingReportFragmentViewModel build() { return new BillingReportFragmentViewModel(this); }
    }
}
