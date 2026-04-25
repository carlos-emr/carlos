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
 * Immutable view model for {@code billingONReview.jsp}.
 *
 * <p>Captures demographic + provider lookups, the chosen diagnostic code, and
 * the validation messages produced from those lookups. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewDataAssembler}
 * and exposed to the JSP as request attribute {@code reviewModel}. Fields are
 * added incrementally as additional scriptlet blocks migrate out of the JSP;
 * for now this captures the demographic-driven prep that previously happened
 * inline in the top scriptlet (lines 199-270 of the legacy JSP).</p>
 *
 * @since 2026-04-24
 */
public final class BillingONReviewViewModel {

    private final String demoFirst;
    private final String demoLast;
    private final String demoHin;
    private final String demoVer;
    private final String demoSex;
    private final String demoHcType;
    private final String demoDob;
    private final String demoDobYy;
    private final String demoDobMm;
    private final String demoDobDd;
    private final String patientAddress;
    private final String referralDoctorName;
    private final String referralDoctorOhip;
    private final String assignedProviderNo;

    private final String providerOhip;
    private final String providerRma;
    private final String providerView;

    private final String dxCode;
    private final String dxDesc;

    private final String errorFlag;
    private final String errorMessage;
    private final String warningMessage;

    private BillingONReviewViewModel(Builder b) {
        this.demoFirst = b.demoFirst;
        this.demoLast = b.demoLast;
        this.demoHin = b.demoHin;
        this.demoVer = b.demoVer;
        this.demoSex = b.demoSex;
        this.demoHcType = b.demoHcType;
        this.demoDob = b.demoDob;
        this.demoDobYy = b.demoDobYy;
        this.demoDobMm = b.demoDobMm;
        this.demoDobDd = b.demoDobDd;
        this.patientAddress = b.patientAddress;
        this.referralDoctorName = b.referralDoctorName;
        this.referralDoctorOhip = b.referralDoctorOhip;
        this.assignedProviderNo = b.assignedProviderNo;
        this.providerOhip = b.providerOhip;
        this.providerRma = b.providerRma;
        this.providerView = b.providerView;
        this.dxCode = b.dxCode;
        this.dxDesc = b.dxDesc;
        this.errorFlag = b.errorFlag;
        this.errorMessage = b.errorMessage;
        this.warningMessage = b.warningMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDemoFirst() { return demoFirst; }
    public String getDemoLast() { return demoLast; }
    public String getDemoHin() { return demoHin; }
    public String getDemoVer() { return demoVer; }
    public String getDemoSex() { return demoSex; }
    public String getDemoHcType() { return demoHcType; }
    public String getDemoDob() { return demoDob; }
    public String getDemoDobYy() { return demoDobYy; }
    public String getDemoDobMm() { return demoDobMm; }
    public String getDemoDobDd() { return demoDobDd; }
    public String getPatientAddress() { return patientAddress; }
    public String getReferralDoctorName() { return referralDoctorName; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getAssignedProviderNo() { return assignedProviderNo; }
    public String getProviderOhip() { return providerOhip; }
    public String getProviderRma() { return providerRma; }
    public String getProviderView() { return providerView; }
    public String getDxCode() { return dxCode; }
    public String getDxDesc() { return dxDesc; }
    public String getErrorFlag() { return errorFlag; }
    public String getErrorMessage() { return errorMessage; }
    public String getWarningMessage() { return warningMessage; }

    public static final class Builder {
        private String demoFirst = "";
        private String demoLast = "";
        private String demoHin = "";
        private String demoVer = "";
        private String demoSex = "";
        private String demoHcType = "";
        private String demoDob = "";
        private String demoDobYy = "";
        private String demoDobMm = "";
        private String demoDobDd = "";
        private String patientAddress = "";
        private String referralDoctorName = "";
        private String referralDoctorOhip = "";
        private String assignedProviderNo = "";
        private String providerOhip = "";
        private String providerRma = "";
        private String providerView = "";
        private String dxCode = "";
        private String dxDesc = "";
        private String errorFlag = "";
        private String errorMessage = "";
        private String warningMessage = "";

        public Builder demoFirst(String v) { this.demoFirst = v; return this; }
        public Builder demoLast(String v) { this.demoLast = v; return this; }
        public Builder demoHin(String v) { this.demoHin = v; return this; }
        public Builder demoVer(String v) { this.demoVer = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoDobYy(String v) { this.demoDobYy = v; return this; }
        public Builder demoDobMm(String v) { this.demoDobMm = v; return this; }
        public Builder demoDobDd(String v) { this.demoDobDd = v; return this; }
        public Builder patientAddress(String v) { this.patientAddress = v; return this; }
        public Builder referralDoctorName(String v) { this.referralDoctorName = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder assignedProviderNo(String v) { this.assignedProviderNo = v; return this; }
        public Builder providerOhip(String v) { this.providerOhip = v; return this; }
        public Builder providerRma(String v) { this.providerRma = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder dxCode(String v) { this.dxCode = v; return this; }
        public Builder dxDesc(String v) { this.dxDesc = v; return this; }
        public Builder errorFlag(String v) { this.errorFlag = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public Builder warningMessage(String v) { this.warningMessage = v; return this; }

        public BillingONReviewViewModel build() {
            return new BillingONReviewViewModel(this);
        }
    }
}
