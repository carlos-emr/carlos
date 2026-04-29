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
 * Immutable view model for {@code billingONPayment.jsp} (the
 * "Payment Received Report" with three sub-reports: RA billing,
 * premium payments, and third-party billing).
 *
 * <p>Captures the form state, the selected provider, the per-row
 * structured records for each sub-report, and the cumulative totals.
 * Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONPaymentViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingONPayment2Action})
 * and exposed to the JSP as request attribute {@code paymentModel}.</p>
 *
 * <p>Eliminates the 9 inline {@code SpringUtils.getBean} lookups the JSP
 * used to perform across its scriptlet body (ProviderDao, BillingONPremiumDao,
 * RaDetailDao, BillingONCHeader1Dao, BillingONPaymentDao,
 * BillingOnItemPaymentDao, BillingONExtDao, DemographicDao, BillingONInvoiceTotalsCalculator).</p>
 *
 * @since 2026-04-26
 */
public final class BillingONPaymentViewModel {

    private final List<ProviderEntry> providerOptions;
    private final boolean allProvidersOption;
    private final String selectedProviderNo;
    private final String startDateStr;
    private final String endDateStr;
    private final String today;
    private final String errorMsg;
    private final boolean reportRendered;
    private final boolean isThisProviderOnly;

    private final List<RaReportRow> raRows;
    private final int raItemCount;
    private final String raFeeTotal;
    private final String raClaimTotal;
    private final String raPaidTotal;
    private final String raAdjTotal;

    private final List<PremiumRow> premiumRows;
    private final int premiumItemCount;
    private final String premiumTotal;

    private final List<ThirdPartyBillRow> thirdPartyRows;
    private final int thirdPartyItemCount;
    private final String thirdPartyBilledTotal;
    private final String thirdPartyPaidTotal;
    private final String thirdPartyRefundedTotal;

    private final String finalTotal;

    public record ProviderEntry(String providerNo, String displayName) {}

    /** One row in the RA Billing Report table. {@code firstRowForBill}
     *  controls whether the billing-no and demographic-name cells render
     *  the popup link (only on the first row of each bill); subsequent
     *  rows for the same bill render an empty cell instead. */
    public record RaReportRow(
            String billingNo, String billStatus, String serviceDate,
            String demographicNo, String demographicName,
            String dxCode, String serviceCode, String serviceCount,
            String fee, String claim, String paid, String adjustment,
            String payProgram, String claimNo, String errorCode,
            boolean firstRowForBill,
            String rowColor) {}

    public record PremiumRow(String providerName, String payDate, String amountPaid, String rowColor) {}

    /** One bill in the third-party report. Contains the per-item rows
     *  (each rendered as its own {@code <tr>} with {@code colspan} games
     *  in the JSP), the per-payment rows (also distinct {@code <tr>}s),
     *  and the trailing outstanding-balance row state. */
    public record ThirdPartyBillRow(
            String billingNo, String billingDate, String demographicNo, String demographicName,
            String rowColor,
            List<ThirdPartyItemRow> items,
            String totalBilled,
            List<ThirdPartyPaymentRow> payments,
            boolean hasOutstanding,
            String outstandingAmt,
            boolean outstandingBold) {}

    public record ThirdPartyItemRow(String dxCode, String serviceCode, String serviceCount,
                                     String amtBilled, String amtPaid, String amtRefund) {}

    public record ThirdPartyPaymentRow(String paymentAmt, String refundAmt, String paymentDate) {}

    private BillingONPaymentViewModel(Builder b) {
        this.providerOptions = b.providerOptions == null
                ? Collections.emptyList() : List.copyOf(b.providerOptions);
        this.allProvidersOption = b.allProvidersOption;
        this.selectedProviderNo = nullToEmpty(b.selectedProviderNo);
        this.startDateStr = nullToEmpty(b.startDateStr);
        this.endDateStr = nullToEmpty(b.endDateStr);
        this.today = nullToEmpty(b.today);
        this.errorMsg = nullToEmpty(b.errorMsg);
        this.reportRendered = b.reportRendered;
        this.isThisProviderOnly = b.isThisProviderOnly;
        this.raRows = b.raRows == null ? Collections.emptyList() : List.copyOf(b.raRows);
        this.raItemCount = b.raItemCount;
        this.raFeeTotal = nullToZero(b.raFeeTotal);
        this.raClaimTotal = nullToZero(b.raClaimTotal);
        this.raPaidTotal = nullToZero(b.raPaidTotal);
        this.raAdjTotal = nullToZero(b.raAdjTotal);
        this.premiumRows = b.premiumRows == null ? Collections.emptyList() : List.copyOf(b.premiumRows);
        this.premiumItemCount = b.premiumItemCount;
        this.premiumTotal = nullToZero(b.premiumTotal);
        this.thirdPartyRows = b.thirdPartyRows == null ? Collections.emptyList() : List.copyOf(b.thirdPartyRows);
        this.thirdPartyItemCount = b.thirdPartyItemCount;
        this.thirdPartyBilledTotal = nullToZero(b.thirdPartyBilledTotal);
        this.thirdPartyPaidTotal = nullToZero(b.thirdPartyPaidTotal);
        this.thirdPartyRefundedTotal = nullToZero(b.thirdPartyRefundedTotal);
        this.finalTotal = nullToZero(b.finalTotal);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String nullToZero(String s) { return s == null ? "0.00" : s; }

    public static Builder builder() { return new Builder(); }

    public List<ProviderEntry> getProviderOptions() { return providerOptions; }
    public boolean isAllProvidersOption() { return allProvidersOption; }
    public String getSelectedProviderNo() { return selectedProviderNo; }
    public String getStartDateStr() { return startDateStr; }
    public String getEndDateStr() { return endDateStr; }
    public String getToday() { return today; }
    public String getErrorMsg() { return errorMsg; }
    public boolean isReportRendered() { return reportRendered; }
    public boolean isThisProviderOnly() { return isThisProviderOnly; }
    public List<RaReportRow> getRaRows() { return raRows; }
    public int getRaItemCount() { return raItemCount; }
    public String getRaFeeTotal() { return raFeeTotal; }
    public String getRaClaimTotal() { return raClaimTotal; }
    public String getRaPaidTotal() { return raPaidTotal; }
    public String getRaAdjTotal() { return raAdjTotal; }
    public List<PremiumRow> getPremiumRows() { return premiumRows; }
    public int getPremiumItemCount() { return premiumItemCount; }
    public String getPremiumTotal() { return premiumTotal; }
    public List<ThirdPartyBillRow> getThirdPartyRows() { return thirdPartyRows; }
    public int getThirdPartyItemCount() { return thirdPartyItemCount; }
    public String getThirdPartyBilledTotal() { return thirdPartyBilledTotal; }
    public String getThirdPartyPaidTotal() { return thirdPartyPaidTotal; }
    public String getThirdPartyRefundedTotal() { return thirdPartyRefundedTotal; }
    public String getFinalTotal() { return finalTotal; }

    public static final class Builder {
        private List<ProviderEntry> providerOptions;
        private boolean allProvidersOption;
        private String selectedProviderNo;
        private String startDateStr;
        private String endDateStr;
        private String today;
        private String errorMsg;
        private boolean reportRendered;
        private boolean isThisProviderOnly;
        private List<RaReportRow> raRows;
        private int raItemCount;
        private String raFeeTotal;
        private String raClaimTotal;
        private String raPaidTotal;
        private String raAdjTotal;
        private List<PremiumRow> premiumRows;
        private int premiumItemCount;
        private String premiumTotal;
        private List<ThirdPartyBillRow> thirdPartyRows;
        private int thirdPartyItemCount;
        private String thirdPartyBilledTotal;
        private String thirdPartyPaidTotal;
        private String thirdPartyRefundedTotal;
        private String finalTotal;

        // Defensive copies at the setter — mirrors the pattern used by
        // BillingONFormViewModel / BillingONCorrectionViewModel so callers
        // retaining the original mutable list can't influence builder state.
        public Builder providerOptions(List<ProviderEntry> v) { this.providerOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder allProvidersOption(boolean v) { this.allProvidersOption = v; return this; }
        public Builder selectedProviderNo(String v) { this.selectedProviderNo = v; return this; }
        public Builder startDateStr(String v) { this.startDateStr = v; return this; }
        public Builder endDateStr(String v) { this.endDateStr = v; return this; }
        public Builder today(String v) { this.today = v; return this; }
        public Builder errorMsg(String v) { this.errorMsg = v; return this; }
        public Builder reportRendered(boolean v) { this.reportRendered = v; return this; }
        public Builder isThisProviderOnly(boolean v) { this.isThisProviderOnly = v; return this; }
        public Builder raRows(List<RaReportRow> v) { this.raRows = v == null ? null : List.copyOf(v); return this; }
        public Builder raItemCount(int v) { this.raItemCount = v; return this; }
        public Builder raFeeTotal(String v) { this.raFeeTotal = v; return this; }
        public Builder raClaimTotal(String v) { this.raClaimTotal = v; return this; }
        public Builder raPaidTotal(String v) { this.raPaidTotal = v; return this; }
        public Builder raAdjTotal(String v) { this.raAdjTotal = v; return this; }
        public Builder premiumRows(List<PremiumRow> v) { this.premiumRows = v == null ? null : List.copyOf(v); return this; }
        public Builder premiumItemCount(int v) { this.premiumItemCount = v; return this; }
        public Builder premiumTotal(String v) { this.premiumTotal = v; return this; }
        public Builder thirdPartyRows(List<ThirdPartyBillRow> v) { this.thirdPartyRows = v == null ? null : List.copyOf(v); return this; }
        public Builder thirdPartyItemCount(int v) { this.thirdPartyItemCount = v; return this; }
        public Builder thirdPartyBilledTotal(String v) { this.thirdPartyBilledTotal = v; return this; }
        public Builder thirdPartyPaidTotal(String v) { this.thirdPartyPaidTotal = v; return this; }
        public Builder thirdPartyRefundedTotal(String v) { this.thirdPartyRefundedTotal = v; return this; }
        public Builder finalTotal(String v) { this.finalTotal = v; return this; }

        public BillingONPaymentViewModel build() { return new BillingONPaymentViewModel(this); }
    }
}
