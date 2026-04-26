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
 * Immutable view model for {@code billing/CA/ON/batchBilling.jsp}, the
 * provider/service-code-filtered batch-billing review page.
 *
 * <p>Bundles the provider dropdown, service-code dropdown, clinic-location
 * dropdown, and batch-bill rows the JSP renders, plus the user-no /
 * current-date / current-time hidden form values it forwards on submit.
 * Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BatchBillingDataAssembler}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billing.CA.ON.web.BatchBill2Action})
 * and exposed as request attribute {@code batchModel}.</p>
 *
 * @since 2026-04-25
 */
public final class BatchBillingViewModel {

    private final String userNo;
    private final String nowDate;
    private final String nowTime;
    private final String defaultBillDate;

    private final String providerView;
    private final String serviceCode;
    private final String clinicView;

    private final List<ProviderOption> providers;
    private final List<String> serviceCodes;
    private final List<ClinicOption> clinicLocations;

    private final boolean rowsAvailable;
    private final boolean filterApplied;
    private final List<Row> rows;

    private BatchBillingViewModel(Builder b) {
        this.userNo = nullToEmpty(b.userNo);
        this.nowDate = nullToEmpty(b.nowDate);
        this.nowTime = nullToEmpty(b.nowTime);
        this.defaultBillDate = nullToEmpty(b.defaultBillDate);
        this.providerView = nullToEmpty(b.providerView);
        this.serviceCode = nullToEmpty(b.serviceCode);
        this.clinicView = nullToEmpty(b.clinicView);
        this.providers = b.providers == null ? Collections.<ProviderOption>emptyList()
                : Collections.unmodifiableList(b.providers);
        this.serviceCodes = b.serviceCodes == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(b.serviceCodes);
        this.clinicLocations = b.clinicLocations == null ? Collections.<ClinicOption>emptyList()
                : Collections.unmodifiableList(b.clinicLocations);
        this.rowsAvailable = b.rowsAvailable;
        this.filterApplied = b.filterApplied;
        this.rows = b.rows == null ? Collections.<Row>emptyList()
                : Collections.unmodifiableList(b.rows);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public String getUserNo() { return userNo; }
    public String getNowDate() { return nowDate; }
    public String getNowTime() { return nowTime; }
    public String getDefaultBillDate() { return defaultBillDate; }
    public String getProviderView() { return providerView; }
    public String getServiceCode() { return serviceCode; }
    public String getClinicView() { return clinicView; }
    public List<ProviderOption> getProviders() { return providers; }
    public List<String> getServiceCodes() { return serviceCodes; }
    public List<ClinicOption> getClinicLocations() { return clinicLocations; }
    /** {@code true} when at least one batch-billing row matched the filter. */
    public boolean isRowsAvailable() { return rowsAvailable; }
    /**
     * {@code true} when the user submitted a non-default provider/service-code
     * filter selection. Distinguishes "no rows match" (filterApplied=true,
     * rowsAvailable=false) from "no filter yet" (filterApplied=false). The
     * legacy JSP rendered different hint text in each case.
     */
    public boolean isFilterApplied() { return filterApplied; }
    public List<Row> getRows() { return rows; }

    /** Provider dropdown option. */
    public static final class ProviderOption {
        private final String providerNo;
        private final String firstName;
        private final String lastName;

        public ProviderOption(String providerNo, String firstName, String lastName) {
            this.providerNo = nullToEmpty(providerNo);
            this.firstName = nullToEmpty(firstName);
            this.lastName = nullToEmpty(lastName);
        }

        public String getProviderNo() { return providerNo; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
    }

    /** Clinic-location dropdown option. */
    public static final class ClinicOption {
        private final String code;
        private final String name;

        public ClinicOption(String code, String name) {
            this.code = nullToEmpty(code);
            this.name = nullToEmpty(name);
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /** Per-row projection used by the batch-billing review table. */
    public static final class Row {
        private final String checkboxValue;
        private final String demoName;
        private final String providerName;
        private final String serviceCode;
        private final String billingAmount;
        private final String diagnosticCode;
        private final String lastBilledDate;

        public Row(String checkboxValue, String demoName, String providerName, String serviceCode,
                   String billingAmount, String diagnosticCode, String lastBilledDate) {
            this.checkboxValue = nullToEmpty(checkboxValue);
            this.demoName = nullToEmpty(demoName);
            this.providerName = nullToEmpty(providerName);
            this.serviceCode = nullToEmpty(serviceCode);
            this.billingAmount = nullToEmpty(billingAmount);
            this.diagnosticCode = nullToEmpty(diagnosticCode);
            this.lastBilledDate = nullToEmpty(lastBilledDate);
        }

        public String getCheckboxValue() { return checkboxValue; }
        public String getDemoName() { return demoName; }
        public String getProviderName() { return providerName; }
        public String getServiceCode() { return serviceCode; }
        public String getBillingAmount() { return billingAmount; }
        public String getDiagnosticCode() { return diagnosticCode; }
        public String getLastBilledDate() { return lastBilledDate; }
    }

    public static final class Builder {
        private String userNo;
        private String nowDate;
        private String nowTime;
        private String defaultBillDate;
        private String providerView;
        private String serviceCode;
        private String clinicView;
        private List<ProviderOption> providers;
        private List<String> serviceCodes;
        private List<ClinicOption> clinicLocations;
        private boolean rowsAvailable;
        private boolean filterApplied;
        private List<Row> rows;

        public Builder userNo(String v) { this.userNo = v; return this; }
        public Builder nowDate(String v) { this.nowDate = v; return this; }
        public Builder nowTime(String v) { this.nowTime = v; return this; }
        public Builder defaultBillDate(String v) { this.defaultBillDate = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder serviceCode(String v) { this.serviceCode = v; return this; }
        public Builder clinicView(String v) { this.clinicView = v; return this; }
        public Builder providers(List<ProviderOption> v) { this.providers = v; return this; }
        public Builder serviceCodes(List<String> v) { this.serviceCodes = v; return this; }
        public Builder clinicLocations(List<ClinicOption> v) { this.clinicLocations = v; return this; }
        public Builder rowsAvailable(boolean v) { this.rowsAvailable = v; return this; }
        public Builder filterApplied(boolean v) { this.filterApplied = v; return this; }
        public Builder rows(List<Row> v) { this.rows = v; return this; }

        public BatchBillingViewModel build() { return new BatchBillingViewModel(this); }
    }
}
