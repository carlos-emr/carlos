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
import java.util.Properties;

/**
 * Immutable view model for {@code billing/CA/ON/billingONNewReport.jsp}. Built
 * by {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONNewReportViewModelAssembler}
 * before the JSP renders. Captures the four-mode report data
 * (unbilled / billed / paid / unpaid) plus the dropdown options the JSP
 * uses to present the "select provider" / "select clinic" controls.
 *
 * @since 2026-04-26
 */
public final class BillingONNewReportViewModel {

    /** Provider option for the non-multisite "select provider" dropdown. */
    public record ProviderOption(String providerNo, String lastName, String firstName) {}

    /** A single provider entry inside a clinic in the multisite layout. */
    public record SiteProviderEntry(String providerNo, String displayName) {}

    /** Clinic option for the multisite "select clinic" dropdown. */
    public record SiteOption(String name, String bgColor, List<SiteProviderEntry> providers) {
        public SiteOption {
            providers = copyOfOrEmpty(providers);
        }
    }

    private final String reportAction;
    private final String providerView;
    private final String xmlVdate;
    private final String xmlAppointmentDate;
    private final String selectedSite;
    private final String defaultBillForm;
    private final boolean multisitesEnabled;
    private final List<String> columnHeaders;
    private final List<Properties> rows;
    private final List<String> totalRow;
    private final List<ProviderOption> providerOptions;
    private final List<SiteOption> siteOptions;

    private BillingONNewReportViewModel(Builder b) {
        this.reportAction = b.reportAction;
        this.providerView = b.providerView;
        this.xmlVdate = b.xmlVdate;
        this.xmlAppointmentDate = b.xmlAppointmentDate;
        this.selectedSite = b.selectedSite;
        this.defaultBillForm = b.defaultBillForm;
        this.multisitesEnabled = b.multisitesEnabled;
        this.columnHeaders = copyOfOrEmpty(b.columnHeaders);
        this.rows = copyOfOrEmpty(b.rows);
        this.totalRow = copyOfOrEmpty(b.totalRow);
        this.providerOptions = copyOfOrEmpty(b.providerOptions);
        this.siteOptions = copyOfOrEmpty(b.siteOptions);
    }

    private static <T> List<T> copyOfOrEmpty(List<T> values) {
        return values == null ? Collections.emptyList() : List.copyOf(values);
    }

    public String getReportAction() { return reportAction; }
    public String getProviderView() { return providerView; }
    public String getXmlVdate() { return xmlVdate; }
    public String getXmlAppointmentDate() { return xmlAppointmentDate; }
    public String getSelectedSite() { return selectedSite; }
    public String getDefaultBillForm() { return defaultBillForm; }
    public boolean isMultisitesEnabled() { return multisitesEnabled; }
    public List<String> getColumnHeaders() { return columnHeaders; }
    public List<Properties> getRows() { return rows; }
    public List<String> getTotalRow() { return totalRow; }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }
    public List<SiteOption> getSiteOptions() { return siteOptions; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String reportAction = "";
        private String providerView = "all";
        private String xmlVdate = "";
        private String xmlAppointmentDate = "";
        private String selectedSite = "";
        private String defaultBillForm = "";
        private boolean multisitesEnabled;
        private List<String> columnHeaders = Collections.emptyList();
        private List<Properties> rows = Collections.emptyList();
        private List<String> totalRow = Collections.emptyList();
        private List<ProviderOption> providerOptions = Collections.emptyList();
        private List<SiteOption> siteOptions = Collections.emptyList();

        public Builder reportAction(String v) { this.reportAction = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder xmlVdate(String v) { this.xmlVdate = v; return this; }
        public Builder xmlAppointmentDate(String v) { this.xmlAppointmentDate = v; return this; }
        public Builder selectedSite(String v) { this.selectedSite = v; return this; }
        public Builder defaultBillForm(String v) { this.defaultBillForm = v; return this; }
        public Builder multisitesEnabled(boolean v) { this.multisitesEnabled = v; return this; }
        public Builder columnHeaders(List<String> v) { this.columnHeaders = v; return this; }
        public Builder rows(List<Properties> v) { this.rows = v; return this; }
        public Builder totalRow(List<String> v) { this.totalRow = v; return this; }
        public Builder providerOptions(List<ProviderOption> v) { this.providerOptions = v; return this; }
        public Builder siteOptions(List<SiteOption> v) { this.siteOptions = v; return this; }

        public BillingONNewReportViewModel build() { return new BillingONNewReportViewModel(this); }
    }
}
