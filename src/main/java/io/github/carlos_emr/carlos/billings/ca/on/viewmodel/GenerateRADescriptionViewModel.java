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
 * Immutable view model for {@code genRADesc.jsp}, the Ontario MOH RA
 * (Remittance Advice) reconciliation report.
 *
 * <p>Captures the parsed RA-file totals (cheque amount, local clinic,
 * other clinic, OB total, colposcopy total), structured balance-forward
 * and transaction rows, any RA message text, and the per-practitioner
 * premium rows (each with its OHIP-mapped provider dropdown options).</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.GenerateRADescriptionViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewGenRADesc2Action})
 * and exposed to the JSP as request attribute {@code raDescModel}.</p>
 *
 * <p>Eliminates the 3 inline {@code SpringUtils.getBean} lookups the JSP
 * used to perform across its scriptlet body (RaHeaderDao, BillingONPremiumDao,
 * ProviderDao) plus the RA-file parsing and the per-RA-header DB merge that
 * lived inline.</p>
 *
 * @since 2026-04-26
 */
public final class GenerateRADescriptionViewModel {

    private final String raNo;
    private final String chequeTotal;
    private final String localTotal;
    private final String otherTotal;
    private final String obTotal;
    private final String coTotal;
    private final BalanceForwardRow balanceForwardRow;
    private final List<TransactionRow> transactionRows;
    /** Concatenated RA message text (RA file H8 header lines). */
    private final String messageTxt;
    private final List<PremiumRow> premiumRows;

    public record BalanceForwardRow(String claimsAdjustment,
                                    String advances,
                                    String reductions,
                                    String deductions) {
        public BalanceForwardRow {
            claimsAdjustment = nullToEmpty(claimsAdjustment);
            advances = nullToEmpty(advances);
            reductions = nullToEmpty(reductions);
            deductions = nullToEmpty(deductions);
        }

        static BalanceForwardRow zero() {
            return new BalanceForwardRow("0.000", "0.000", "0.000", "0.000");
        }
    }

    public record TransactionRow(String transaction,
                                 String transactionDate,
                                 String chequeIssued,
                                 String amount,
                                 String message) {
        public TransactionRow {
            transaction = nullToEmpty(transaction);
            transactionDate = nullToEmpty(transactionDate);
            chequeIssued = nullToEmpty(chequeIssued);
            amount = nullToEmpty(amount);
            message = nullToEmpty(message);
        }
    }

    /**
     * One row in the per-practitioner premium table. Carries the OHIP-mapped
     * provider dropdown options (each with a "selected" flag) and the
     * checkbox state.
     */
    public record PremiumRow(int premiumId,
                             String providerOhipNo,
                             String amountPay,
                             String payDateStr,
                             boolean checked,
                             List<ProviderOption> providerOptions) {
        public PremiumRow {
            providerOptions = providerOptions == null
                    ? List.of() : List.copyOf(providerOptions);
        }
    }

    public record ProviderOption(String providerNo, String formattedName, boolean selected) {}

    private GenerateRADescriptionViewModel(Builder b) {
        this.raNo = nullToEmpty(b.raNo);
        this.chequeTotal = nullToEmpty(b.chequeTotal);
        this.localTotal = nullToEmpty(b.localTotal);
        this.otherTotal = nullToEmpty(b.otherTotal);
        this.obTotal = nullToEmpty(b.obTotal);
        this.coTotal = nullToEmpty(b.coTotal);
        this.balanceForwardRow = b.balanceForwardRow == null
                ? BalanceForwardRow.zero() : b.balanceForwardRow;
        this.transactionRows = b.transactionRows == null
                ? Collections.emptyList() : List.copyOf(b.transactionRows);
        this.messageTxt = nullToEmpty(b.messageTxt);
        this.premiumRows = b.premiumRows == null
                ? Collections.emptyList() : List.copyOf(b.premiumRows);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public String getRaNo() { return raNo; }
    public String getChequeTotal() { return chequeTotal; }
    public String getLocalTotal() { return localTotal; }
    public String getOtherTotal() { return otherTotal; }
    public String getObTotal() { return obTotal; }
    public String getCoTotal() { return coTotal; }
    public BalanceForwardRow getBalanceForwardRow() { return balanceForwardRow; }
    public List<TransactionRow> getTransactionRows() { return transactionRows; }
    public String getMessageTxt() { return messageTxt; }
    public List<PremiumRow> getPremiumRows() { return premiumRows; }

    public static final class Builder {
        private String raNo;
        private String chequeTotal;
        private String localTotal;
        private String otherTotal;
        private String obTotal;
        private String coTotal;
        private BalanceForwardRow balanceForwardRow;
        private List<TransactionRow> transactionRows;
        private String messageTxt;
        private List<PremiumRow> premiumRows;

        public Builder raNo(String v) { this.raNo = v; return this; }
        public Builder chequeTotal(String v) { this.chequeTotal = v; return this; }
        public Builder localTotal(String v) { this.localTotal = v; return this; }
        public Builder otherTotal(String v) { this.otherTotal = v; return this; }
        public Builder obTotal(String v) { this.obTotal = v; return this; }
        public Builder coTotal(String v) { this.coTotal = v; return this; }
        public Builder balanceForwardRow(BalanceForwardRow v) { this.balanceForwardRow = v; return this; }
        public Builder transactionRows(List<TransactionRow> v) { this.transactionRows = v == null ? null : List.copyOf(v); return this; }
        public Builder messageTxt(String v) { this.messageTxt = v; return this; }
        // Defensive copy at the setter — mirrors BillingONFormViewModel pattern.
        public Builder premiumRows(List<PremiumRow> v) { this.premiumRows = v == null ? null : List.copyOf(v); return this; }

        public GenerateRADescriptionViewModel build() { return new GenerateRADescriptionViewModel(this); }
    }
}
