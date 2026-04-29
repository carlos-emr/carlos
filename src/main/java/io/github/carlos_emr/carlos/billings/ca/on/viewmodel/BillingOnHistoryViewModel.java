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
 * Immutable view model for {@code billingONHistory.jsp}, the patient
 * billing-history popup with DataTables-powered listing.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistoryViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnHistory2Action})
 * and exposed to the JSP as request attribute {@code historyModel}.</p>
 *
 * <p>Captures the patient display block (resolved server-side to keep PHI
 * out of the URL), the billing-history rows, and the {@code warnOnDeleteBill}
 * flag the legacy JSP read directly from {@code CarlosProperties}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingOnHistoryViewModel {

    /**
     * One pre-resolved billing-history row. The legacy JSP rendered
     * conditional unbill links and a balance column; both are pre-computed
     * here so the JSP body becomes pure presentation.
     */
    public record HistoryRow(
            String invoiceId,
            String providerLastFirst,
            String billingDate,
            String billType,
            String serviceCode,
            String dx,
            String balance,
            boolean balanceShown,
            String total,
            String status,
            boolean unbillLinkShown,
            boolean canEdit) { }

    private final String demographicNo;
    private final String patientDisplayName;
    private final boolean warnOnDeleteBill;
    private final List<HistoryRow> rows;

    private BillingOnHistoryViewModel(Builder b) {
        this.demographicNo = nullToEmpty(b.demographicNo);
        this.patientDisplayName = nullToEmpty(b.patientDisplayName);
        this.warnOnDeleteBill = b.warnOnDeleteBill;
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public String getDemographicNo() { return demographicNo; }
    public String getPatientDisplayName() { return patientDisplayName; }
    public boolean isWarnOnDeleteBill() { return warnOnDeleteBill; }
    public List<HistoryRow> getRows() { return rows; }

    public static final class Builder {
        private String demographicNo;
        private String patientDisplayName;
        private boolean warnOnDeleteBill;
        private List<HistoryRow> rows;

        public Builder demographicNo(String v) { this.demographicNo = v; return this; }
        public Builder patientDisplayName(String v) { this.patientDisplayName = v; return this; }
        public Builder warnOnDeleteBill(boolean v) { this.warnOnDeleteBill = v; return this; }
        public Builder rows(List<HistoryRow> v) { this.rows = v; return this; }

        public BillingOnHistoryViewModel build() { return new BillingOnHistoryViewModel(this); }
    }
}
