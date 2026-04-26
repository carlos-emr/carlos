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
 * Immutable view model for {@code billing/CA/ON/inr/reportINR.jsp}, the
 * INR Batch Billing report.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingInrReportDataAssembler}
 * and exposed as request attribute {@code reportInrModel}. Carries the
 * provider dropdown, clinic-location dropdown, and current-billing rows the
 * legacy JSP previously assembled inline via raw {@code BillingInrDao} +
 * {@code ProviderDao} + {@code ClinicLocationDao} scriptlets — including the
 * dead {@code ResultSet rsclinic} placeholder.</p>
 *
 * @since 2026-04-26
 */
public final class BillingInrReportViewModel {

    /** Provider dropdown entry — {@code provider_no}-keyed with display name. */
    public record ProviderOption(String providerNo, String firstName, String lastName) { }

    /** Clinic-location dropdown entry — {@code clinic_location_no}-keyed. */
    public record ClinicLocationOption(String code, String name) { }

    /**
     * One bill row in the report table. {@code lastBillDateLabel} is
     * pre-resolved to either the truncated {@code yyyy-MM-dd} of the
     * billing date when the bill is "active" (status code {@code "A"}) or
     * the literal string {@code "Not Available"} otherwise — replacing the
     * inline {@code <%if(billstatus.compareTo("A") == 0){%>...<%}else{%>...} }
     * scriptlet pair that appeared twice in the legacy JSP.
     */
    public record BillRow(
            String billingInrNo,
            String demoNo,
            String demoName,
            String demoNameUrlEncoded,
            String providerName,
            String providerNameUrlEncoded,
            String serviceCode,
            String billingAmount,
            String diagnosticCode,
            String lastBillDateLabel) { }

    private final String userNo;
    private final String providerView;
    private final String clinicView;
    private final String clinicNo;
    private final String inrBillingActionUrl;
    private final String nowDate;
    private final String nowTime;
    private final int curYear;
    private final int curMonth;
    private final int curDay;
    private final String defaultServiceDate;
    private final List<ProviderOption> providers;
    private final List<ClinicLocationOption> clinicLocations;
    private final List<BillRow> billRows;

    private BillingInrReportViewModel(Builder b) {
        this.userNo = nullToEmpty(b.userNo);
        this.providerView = nullToEmpty(b.providerView);
        this.clinicView = nullToEmpty(b.clinicView);
        this.clinicNo = nullToEmpty(b.clinicNo);
        this.inrBillingActionUrl = nullToEmpty(b.inrBillingActionUrl);
        this.nowDate = nullToEmpty(b.nowDate);
        this.nowTime = nullToEmpty(b.nowTime);
        this.curYear = b.curYear;
        this.curMonth = b.curMonth;
        this.curDay = b.curDay;
        this.defaultServiceDate = nullToEmpty(b.defaultServiceDate);
        this.providers = b.providers == null ? Collections.emptyList() : List.copyOf(b.providers);
        this.clinicLocations = b.clinicLocations == null
                ? Collections.emptyList() : List.copyOf(b.clinicLocations);
        this.billRows = b.billRows == null ? Collections.emptyList() : List.copyOf(b.billRows);
    }

    public static Builder builder() { return new Builder(); }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public String getUserNo() { return userNo; }
    public String getProviderView() { return providerView; }
    public String getClinicView() { return clinicView; }
    public String getClinicNo() { return clinicNo; }
    public String getInrBillingActionUrl() { return inrBillingActionUrl; }
    public String getNowDate() { return nowDate; }
    public String getNowTime() { return nowTime; }
    public int getCurYear() { return curYear; }
    public int getCurMonth() { return curMonth; }
    public int getCurDay() { return curDay; }
    public String getDefaultServiceDate() { return defaultServiceDate; }
    public List<ProviderOption> getProviders() { return providers; }
    public List<ClinicLocationOption> getClinicLocations() { return clinicLocations; }
    public List<BillRow> getBillRows() { return billRows; }
    public int getRowCount() { return billRows.size(); }
    public boolean isAllProvidersSelected() { return "all".equals(providerView); }

    public static final class Builder {
        private String userNo;
        private String providerView;
        private String clinicView;
        private String clinicNo;
        private String inrBillingActionUrl;
        private String nowDate;
        private String nowTime;
        private int curYear;
        private int curMonth;
        private int curDay;
        private String defaultServiceDate;
        private List<ProviderOption> providers;
        private List<ClinicLocationOption> clinicLocations;
        private List<BillRow> billRows;

        public Builder userNo(String v) { this.userNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder clinicView(String v) { this.clinicView = v; return this; }
        public Builder clinicNo(String v) { this.clinicNo = v; return this; }
        public Builder inrBillingActionUrl(String v) { this.inrBillingActionUrl = v; return this; }
        public Builder nowDate(String v) { this.nowDate = v; return this; }
        public Builder nowTime(String v) { this.nowTime = v; return this; }
        public Builder curYear(int v) { this.curYear = v; return this; }
        public Builder curMonth(int v) { this.curMonth = v; return this; }
        public Builder curDay(int v) { this.curDay = v; return this; }
        public Builder defaultServiceDate(String v) { this.defaultServiceDate = v; return this; }
        public Builder providers(List<ProviderOption> v) {
            this.providers = v == null ? null : List.copyOf(v); return this;
        }
        public Builder clinicLocations(List<ClinicLocationOption> v) {
            this.clinicLocations = v == null ? null : List.copyOf(v); return this;
        }
        public Builder billRows(List<BillRow> v) {
            this.billRows = v == null ? null : List.copyOf(v); return this;
        }

        public BillingInrReportViewModel build() {
            return new BillingInrReportViewModel(this);
        }
    }
}
