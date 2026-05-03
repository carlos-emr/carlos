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

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingViewStrings;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billing/CA/ON/billingReportControl.jsp}, the
 * parent of the five {@code billingReport_*.jspf} fragments. The parent JSP
 * used to perform inline scriptlet work to: read the report-action /
 * provider-view request parameters, look up the report-provider list via
 * {@link io.github.carlos_emr.carlos.commn.dao.ReportProviderDao}, and compute
 * the calendar-popup year/month defaults used in the From/To date links.
 *
 * <p>This view model captures the parameter echoes, the provider dropdown
 * options, and the calendar-popup defaults. The fragment-specific row data
 * still lives in {@link BillingReportFragmentViewModel}; the fragments
 * lazily resolve their own model from the {@code billingReportFragmentModel}
 * request attribute.</p>
 *
 * @since 2026-04-25
 */
public final class BillingReportControlViewModel {

    /** Provider option for the "Select provider" dropdown. */
    public record ProviderOption(String providerNo, String firstName, String lastName) {}

    private final String reportAction;
    private final String providerView;
    private final String xmlVdate;
    private final String xmlAppointmentDate;
    private final int curYear;
    private final int curMonth;
    private final List<ProviderOption> providerOptions;

    private BillingReportControlViewModel(Builder b) {
        this.reportAction = BillingViewStrings.nullToEmpty(b.reportAction);
        this.providerView = b.providerView == null ? "all" : b.providerView;
        this.xmlVdate = BillingViewStrings.nullToEmpty(b.xmlVdate);
        this.xmlAppointmentDate = BillingViewStrings.nullToEmpty(b.xmlAppointmentDate);
        this.curYear = b.curYear;
        this.curMonth = b.curMonth;
        this.providerOptions = b.providerOptions == null
                ? Collections.emptyList()
                : List.copyOf(b.providerOptions);
    }

    public static Builder builder() { return new Builder(); }

    public String getReportAction() { return reportAction; }
    public String getProviderView() { return providerView; }
    public String getXmlVdate() { return xmlVdate; }
    public String getXmlAppointmentDate() { return xmlAppointmentDate; }
    public int getCurYear() { return curYear; }
    public int getCurMonth() { return curMonth; }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }

    public static final class Builder {
        private String reportAction;
        private String providerView;
        private String xmlVdate;
        private String xmlAppointmentDate;
        private int curYear;
        private int curMonth;
        private List<ProviderOption> providerOptions;

        public Builder reportAction(String v) { this.reportAction = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder xmlVdate(String v) { this.xmlVdate = v; return this; }
        public Builder xmlAppointmentDate(String v) { this.xmlAppointmentDate = v; return this; }
        public Builder curYear(int v) { this.curYear = v; return this; }
        public Builder curMonth(int v) { this.curMonth = v; return this; }
        public Builder providerOptions(List<ProviderOption> v) { this.providerOptions = v; return this; }

        public BillingReportControlViewModel build() {
            return new BillingReportControlViewModel(this);
        }
    }
}
