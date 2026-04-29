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
 * Immutable view model for {@code billingOHIPsimulation.jsp}, the
 * OHIP-extract simulation admin form.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOHIPSimulationViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOHIPsimulation2Action})
 * and exposed to the JSP as request attribute {@code simulationModel}.</p>
 *
 * <p>The simulation HTML preview is built when
 * {@code submit=Create Report} is posted; otherwise the field is empty
 * and the JSP renders only the form.</p>
 *
 * @since 2026-04-25
 */
public final class BillingOHIPSimulationViewModel {

    /** A single provider dropdown row: {@code provider_no | last | first}. */
    public record ProviderOption(String providerNo, String lastName, String firstName) { }

    private final boolean multisites;
    private final String billCenter;
    private final String healthOffice;
    private final String monthCode;
    private final String nowDate;
    private final String userNo;
    private final String providerView;
    private final String startDate;
    private final String endDate;
    private final boolean summaryView;
    private final List<ProviderOption> providers;
    /**
     * Trusted-HTML preview block rendered without escape on the JSP — the
     * OHIP file preview formatted as a {@code <pre>}-style code dump. Built
     * ONLY by {@code BillingOHIPSimulationViewModelAssembler} from compile-
     * time constants and server-side state ({@code BillingONCHeader1}
     * properties, {@code BillingONItem} fields, the legacy
     * {@code dbObj.getHtmlValue()} output). No raw request parameter reaches
     * this field. Never set this field directly from a request parameter
     * without going through the assembler's encoding layer.
     */
    private final String previewHtml;

    private BillingOHIPSimulationViewModel(Builder b) {
        this.multisites = b.multisites;
        this.billCenter = nullToEmpty(b.billCenter);
        this.healthOffice = nullToEmpty(b.healthOffice);
        this.monthCode = nullToEmpty(b.monthCode);
        this.nowDate = nullToEmpty(b.nowDate);
        this.userNo = nullToEmpty(b.userNo);
        this.providerView = nullToEmpty(b.providerView);
        this.startDate = nullToEmpty(b.startDate);
        this.endDate = nullToEmpty(b.endDate);
        this.summaryView = b.summaryView;
        this.providers = b.providers == null
                ? Collections.emptyList() : List.copyOf(b.providers);
        this.previewHtml = nullToEmpty(b.previewHtml);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public boolean isMultisites() { return multisites; }
    public String getBillCenter() { return billCenter; }
    public String getHealthOffice() { return healthOffice; }
    public String getMonthCode() { return monthCode; }
    public String getNowDate() { return nowDate; }
    public String getUserNo() { return userNo; }
    public String getProviderView() { return providerView; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public boolean isSummaryView() { return summaryView; }
    public List<ProviderOption> getProviders() { return providers; }
    public String getPreviewHtml() { return previewHtml; }

    public static final class Builder {
        private boolean multisites;
        private String billCenter;
        private String healthOffice;
        private String monthCode;
        private String nowDate;
        private String userNo;
        private String providerView;
        private String startDate;
        private String endDate;
        private boolean summaryView;
        private List<ProviderOption> providers;
        private String previewHtml;

        public Builder multisites(boolean v) { this.multisites = v; return this; }
        public Builder billCenter(String v) { this.billCenter = v; return this; }
        public Builder healthOffice(String v) { this.healthOffice = v; return this; }
        public Builder monthCode(String v) { this.monthCode = v; return this; }
        public Builder nowDate(String v) { this.nowDate = v; return this; }
        public Builder userNo(String v) { this.userNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder startDate(String v) { this.startDate = v; return this; }
        public Builder endDate(String v) { this.endDate = v; return this; }
        public Builder summaryView(boolean v) { this.summaryView = v; return this; }
        public Builder providers(List<ProviderOption> v) { this.providers = v; return this; }
        public Builder previewHtml(String v) { this.previewHtml = v; return this; }

        public BillingOHIPSimulationViewModel build() {
            return new BillingOHIPSimulationViewModel(this);
        }
    }
}
