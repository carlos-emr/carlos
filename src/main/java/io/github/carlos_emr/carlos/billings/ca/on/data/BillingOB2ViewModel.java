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

/**
 * Immutable view model for {@code billingOB2.jsp}, the read-only OB
 * billing-history popup reachable via {@code billing/CA/BC/billingView}'s
 * {@code "ON"} result chain.
 *
 * <p>Captures the patient demographic block, billing-record fields,
 * the providers attached to the bill (billing / appointment / assistant /
 * creator), the single-line-item billing detail, and the diagnostic-code
 * description.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOB2DataAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingOB2View2Action})
 * and exposed to the JSP as request attribute {@code ob2Model}.</p>
 *
 * <p>Eliminates the 6 inline {@code SpringUtils.getBean} lookups the JSP
 * used to perform across its scriptlet body (DiagnosticCodeDao,
 * ClinicLocationDao, BillingDao, DemographicDao, ProviderDao,
 * BillingDetailDao).</p>
 *
 * @since 2026-04-26
 */
public final class BillingOB2ViewModel {

    private final boolean billLoaded;
    private final String demoNo;
    private final String demoName;
    private final String demoSex;
    private final String demoAddress;
    private final String demoCity;
    private final String demoProvince;
    private final String demoPostal;
    private final String demoDob;
    private final String hin;

    private final String billType;
    private final String billDate;
    private final String visitType;
    private final String visitDate;
    private final String billLocation;
    private final String billTotal;
    private final String updateDate;

    private final String providerFirst;
    private final String providerLast;
    private final String apptProviderFirst;
    private final String apptProviderLast;
    private final String asstProviderFirst;
    private final String asstProviderLast;
    private final String creatorFirst;
    private final String creatorLast;

    private final boolean billDetailLoaded;
    private final String serviceCode;
    private final String serviceDesc;
    private final String billUnit;
    private final String billAmount;

    private final String diagCode;
    private final String diagDesc;

    private BillingOB2ViewModel(Builder b) {
        this.billLoaded = b.billLoaded;
        this.demoNo = nullToEmpty(b.demoNo);
        this.demoName = nullToEmpty(b.demoName);
        this.demoSex = nullToEmpty(b.demoSex);
        this.demoAddress = nullToEmpty(b.demoAddress);
        this.demoCity = nullToEmpty(b.demoCity);
        this.demoProvince = nullToEmpty(b.demoProvince);
        this.demoPostal = nullToEmpty(b.demoPostal);
        this.demoDob = nullToEmpty(b.demoDob);
        this.hin = nullToEmpty(b.hin);
        this.billType = nullToEmpty(b.billType);
        this.billDate = nullToEmpty(b.billDate);
        this.visitType = nullToEmpty(b.visitType);
        this.visitDate = nullToEmpty(b.visitDate);
        this.billLocation = nullToEmpty(b.billLocation);
        this.billTotal = nullToEmpty(b.billTotal);
        this.updateDate = nullToEmpty(b.updateDate);
        // Defaults match the legacy JSP locals: "Not" / "Available" appear in
        // the rendered "First Last" cells when no provider record is loaded.
        this.providerFirst = nullToEmpty(b.providerFirst);
        this.providerLast = nullToEmpty(b.providerLast);
        this.apptProviderFirst = b.apptProviderFirst == null ? "Not" : b.apptProviderFirst;
        this.apptProviderLast = b.apptProviderLast == null ? "Available" : b.apptProviderLast;
        this.asstProviderFirst = b.asstProviderFirst == null ? "Not" : b.asstProviderFirst;
        this.asstProviderLast = b.asstProviderLast == null ? "Available" : b.asstProviderLast;
        this.creatorFirst = b.creatorFirst == null ? "Not" : b.creatorFirst;
        this.creatorLast = b.creatorLast == null ? "Available" : b.creatorLast;
        this.billDetailLoaded = b.billDetailLoaded;
        this.serviceCode = nullToEmpty(b.serviceCode);
        this.serviceDesc = nullToEmpty(b.serviceDesc);
        this.billUnit = nullToEmpty(b.billUnit);
        this.billAmount = nullToEmpty(b.billAmount);
        this.diagCode = nullToEmpty(b.diagCode);
        this.diagDesc = nullToEmpty(b.diagDesc);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public boolean isBillLoaded() { return billLoaded; }
    public String getDemoNo() { return demoNo; }
    public String getDemoName() { return demoName; }
    public String getDemoSex() { return demoSex; }
    public String getDemoAddress() { return demoAddress; }
    public String getDemoCity() { return demoCity; }
    public String getDemoProvince() { return demoProvince; }
    public String getDemoPostal() { return demoPostal; }
    public String getDemoDob() { return demoDob; }
    public String getHin() { return hin; }
    public String getBillType() { return billType; }
    public String getBillDate() { return billDate; }
    public String getVisitType() { return visitType; }
    public String getVisitDate() { return visitDate; }
    public String getBillLocation() { return billLocation; }
    public String getBillTotal() { return billTotal; }
    public String getUpdateDate() { return updateDate; }
    public String getProviderFirst() { return providerFirst; }
    public String getProviderLast() { return providerLast; }
    public String getApptProviderFirst() { return apptProviderFirst; }
    public String getApptProviderLast() { return apptProviderLast; }
    public String getAsstProviderFirst() { return asstProviderFirst; }
    public String getAsstProviderLast() { return asstProviderLast; }
    public String getCreatorFirst() { return creatorFirst; }
    public String getCreatorLast() { return creatorLast; }
    public boolean isBillDetailLoaded() { return billDetailLoaded; }
    public String getServiceCode() { return serviceCode; }
    public String getServiceDesc() { return serviceDesc; }
    public String getBillUnit() { return billUnit; }
    public String getBillAmount() { return billAmount; }
    public String getDiagCode() { return diagCode; }
    public String getDiagDesc() { return diagDesc; }

    public static final class Builder {
        private boolean billLoaded;
        private String demoNo;
        private String demoName;
        private String demoSex;
        private String demoAddress;
        private String demoCity;
        private String demoProvince;
        private String demoPostal;
        private String demoDob;
        private String hin;
        private String billType;
        private String billDate;
        private String visitType;
        private String visitDate;
        private String billLocation;
        private String billTotal;
        private String updateDate;
        private String providerFirst;
        private String providerLast;
        private String apptProviderFirst;
        private String apptProviderLast;
        private String asstProviderFirst;
        private String asstProviderLast;
        private String creatorFirst;
        private String creatorLast;
        private boolean billDetailLoaded;
        private String serviceCode;
        private String serviceDesc;
        private String billUnit;
        private String billAmount;
        private String diagCode;
        private String diagDesc;

        public Builder billLoaded(boolean v) { this.billLoaded = v; return this; }
        public Builder demoNo(String v) { this.demoNo = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder demoAddress(String v) { this.demoAddress = v; return this; }
        public Builder demoCity(String v) { this.demoCity = v; return this; }
        public Builder demoProvince(String v) { this.demoProvince = v; return this; }
        public Builder demoPostal(String v) { this.demoPostal = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder hin(String v) { this.hin = v; return this; }
        public Builder billType(String v) { this.billType = v; return this; }
        public Builder billDate(String v) { this.billDate = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder billLocation(String v) { this.billLocation = v; return this; }
        public Builder billTotal(String v) { this.billTotal = v; return this; }
        public Builder updateDate(String v) { this.updateDate = v; return this; }
        public Builder providerFirst(String v) { this.providerFirst = v; return this; }
        public Builder providerLast(String v) { this.providerLast = v; return this; }
        public Builder apptProviderFirst(String v) { this.apptProviderFirst = v; return this; }
        public Builder apptProviderLast(String v) { this.apptProviderLast = v; return this; }
        public Builder asstProviderFirst(String v) { this.asstProviderFirst = v; return this; }
        public Builder asstProviderLast(String v) { this.asstProviderLast = v; return this; }
        public Builder creatorFirst(String v) { this.creatorFirst = v; return this; }
        public Builder creatorLast(String v) { this.creatorLast = v; return this; }
        public Builder billDetailLoaded(boolean v) { this.billDetailLoaded = v; return this; }
        public Builder serviceCode(String v) { this.serviceCode = v; return this; }
        public Builder serviceDesc(String v) { this.serviceDesc = v; return this; }
        public Builder billUnit(String v) { this.billUnit = v; return this; }
        public Builder billAmount(String v) { this.billAmount = v; return this; }
        public Builder diagCode(String v) { this.diagCode = v; return this; }
        public Builder diagDesc(String v) { this.diagDesc = v; return this; }

        public BillingOB2ViewModel build() { return new BillingOB2ViewModel(this); }
    }
}
