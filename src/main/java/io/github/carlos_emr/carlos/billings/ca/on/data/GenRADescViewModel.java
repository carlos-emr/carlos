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

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code genRADesc.jsp}, the Ontario MOH RA
 * (Remittance Advice) reconciliation report.
 *
 * <p>Captures the parsed RA-file totals (cheque amount, local clinic,
 * other clinic, OB total, colposcopy total), the pre-rendered balance-
 * forward + transaction HTML blocks (assembled from the RA file headers),
 * any RA message text, and the per-practitioner premium rows (each with
 * its OHIP-mapped provider dropdown options).</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.GenRADescDataAssembler#assemble}
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
public final class GenRADescViewModel {

    private final String raNo;
    private final String chequeTotal;
    private final String localTotal;
    private final String otherTotal;
    private final String obTotal;
    private final String coTotal;
    /** Pre-rendered balance-forward HTML row block. */
    private final String balanceForwardHtml;
    /** Pre-rendered accounting-transaction HTML row block. */
    private final String transactionHtml;
    /** Concatenated RA message text (RA file H8 header lines). */
    private final String messageTxt;
    private final List<PremiumRow> premiumRows;

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

    private GenRADescViewModel(Builder b) {
        this.raNo = nullToEmpty(b.raNo);
        this.chequeTotal = nullToEmpty(b.chequeTotal);
        this.localTotal = nullToEmpty(b.localTotal);
        this.otherTotal = nullToEmpty(b.otherTotal);
        this.obTotal = nullToEmpty(b.obTotal);
        this.coTotal = nullToEmpty(b.coTotal);
        this.balanceForwardHtml = nullToEmpty(b.balanceForwardHtml);
        this.transactionHtml = nullToEmpty(b.transactionHtml);
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
    public String getBalanceForwardHtml() { return balanceForwardHtml; }
    public String getTransactionHtml() { return transactionHtml; }
    public String getMessageTxt() { return messageTxt; }
    public List<PremiumRow> getPremiumRows() { return premiumRows; }

    public static final class Builder {
        private String raNo;
        private String chequeTotal;
        private String localTotal;
        private String otherTotal;
        private String obTotal;
        private String coTotal;
        private String balanceForwardHtml;
        private String transactionHtml;
        private String messageTxt;
        private List<PremiumRow> premiumRows;

        public Builder raNo(String v) { this.raNo = v; return this; }
        public Builder chequeTotal(String v) { this.chequeTotal = v; return this; }
        public Builder localTotal(String v) { this.localTotal = v; return this; }
        public Builder otherTotal(String v) { this.otherTotal = v; return this; }
        public Builder obTotal(String v) { this.obTotal = v; return this; }
        public Builder coTotal(String v) { this.coTotal = v; return this; }
        public Builder balanceForwardHtml(String v) { this.balanceForwardHtml = v; return this; }
        public Builder transactionHtml(String v) { this.transactionHtml = v; return this; }
        public Builder messageTxt(String v) { this.messageTxt = v; return this; }
        // Defensive copy at the setter — mirrors BillingONFormViewModel pattern.
        public Builder premiumRows(List<PremiumRow> v) { this.premiumRows = v == null ? null : List.copyOf(v); return this; }

        public GenRADescViewModel build() { return new GenRADescViewModel(this); }
    }
}
