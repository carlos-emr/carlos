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
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Immutable view model for {@code billing/CA/ON/onGenRASummary.jsp}. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.OnGenRASummaryDataAssembler}
 * after the action runs the RA-header audit merge. Holds the dropdown options,
 * the per-row summary data, and the running totals the JSP renders.
 *
 * @since 2026-04-26
 */
public final class OnGenRASummaryViewModel {

    /** Provider option for the "Filter by provider" dropdown. */
    public record ProviderOption(String ohipNo, String lastName, String firstName) {}

    private final String raNo;
    private final String selectedProviderOhip;
    private final List<ProviderOption> providerOptions;
    private final List<Properties> summaryRows;
    private final BigDecimal localTotal;
    private final BigDecimal payTotal;
    private final BigDecimal otherTotal;
    private final BigDecimal obTotal;
    private final BigDecimal coTotal;
    private final boolean multisitesEnabled;

    private OnGenRASummaryViewModel(Builder b) {
        this.raNo = b.raNo;
        this.selectedProviderOhip = b.selectedProviderOhip;
        this.providerOptions = List.copyOf(b.providerOptions);
        this.summaryRows = List.copyOf(b.summaryRows);
        this.localTotal = b.localTotal;
        this.payTotal = b.payTotal;
        this.otherTotal = b.otherTotal;
        this.obTotal = b.obTotal;
        this.coTotal = b.coTotal;
        this.multisitesEnabled = b.multisitesEnabled;
    }

    public String getRaNo() { return raNo; }
    public String getSelectedProviderOhip() { return selectedProviderOhip; }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }
    public List<Properties> getSummaryRows() { return summaryRows; }
    public BigDecimal getLocalTotal() { return localTotal; }
    public BigDecimal getPayTotal() { return payTotal; }
    public BigDecimal getOtherTotal() { return otherTotal; }
    public BigDecimal getObTotal() { return obTotal; }
    public BigDecimal getCoTotal() { return coTotal; }
    public boolean isMultisitesEnabled() { return multisitesEnabled; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String raNo = "";
        private String selectedProviderOhip = "all";
        private List<ProviderOption> providerOptions = Collections.emptyList();
        private List<Properties> summaryRows = Collections.emptyList();
        private BigDecimal localTotal = BigDecimal.ZERO;
        private BigDecimal payTotal = BigDecimal.ZERO;
        private BigDecimal otherTotal = BigDecimal.ZERO;
        private BigDecimal obTotal = BigDecimal.ZERO;
        private BigDecimal coTotal = BigDecimal.ZERO;
        private boolean multisitesEnabled;

        public Builder raNo(String v) { this.raNo = v; return this; }
        public Builder selectedProviderOhip(String v) { this.selectedProviderOhip = v; return this; }
        public Builder providerOptions(List<ProviderOption> v) { this.providerOptions = v; return this; }
        public Builder summaryRows(List<Properties> v) { this.summaryRows = v; return this; }
        public Builder localTotal(BigDecimal v) { this.localTotal = v; return this; }
        public Builder payTotal(BigDecimal v) { this.payTotal = v; return this; }
        public Builder otherTotal(BigDecimal v) { this.otherTotal = v; return this; }
        public Builder obTotal(BigDecimal v) { this.obTotal = v; return this; }
        public Builder coTotal(BigDecimal v) { this.coTotal = v; return this; }
        public Builder multisitesEnabled(boolean v) { this.multisitesEnabled = v; return this; }

        public OnGenRASummaryViewModel build() { return new OnGenRASummaryViewModel(this); }
    }
}
