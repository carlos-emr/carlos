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
import java.util.Map;

/**
 * Immutable view model for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Assembled by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONFormDataAssembler}
 * via {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONView2Action}
 * and exposed to the JSP as request attribute {@code model}. Fields are added
 * incrementally as their corresponding scriptlet blocks are migrated out of the
 * JSP. The form previously built this data inline via ~24 DAO lookups, which
 * pushed the rendered response past the 1 MB page buffer.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONFormViewModel {

    /** Billing history entry extracted from the top scriptlet block. */
    public record BillingHistoryEntry(
            String visitDate,
            String visitType,
            String clinicRefCode,
            String diagnosticCode) { }

    /** Provider option rendered in the billing form's provider picker. */
    public record ProviderOption(
            String lastName,
            String firstName,
            String proOhip) { }

    // Identity / context
    private final String userNo;
    private final String demographicNo;
    private final String appointmentNo;
    private final String providerNo;
    private final String apptProviderNo;
    private final String providerView;
    private final String demoName;
    private final String today;

    // Request echoes for form state
    private final String billReferenceDate;
    private final String mReview;
    private final String ctlBillForm;
    private final String curBillForm;

    // Demographic
    private final String demoLast;
    private final String demoFirst;
    private final String demoHin;
    private final String demoVer;
    private final String demoDob;
    private final String demoDobYear;
    private final String demoDobMonth;
    private final String demoDobDay;
    private final String demoHcType;
    private final String demoSex;
    private final String familyDoctor;
    private final String rosterStatus;
    private final String assgProviderNo;
    private final int age;

    // Referral
    private final String referralDoctor;
    private final String referralDoctorOhip;
    private final String referralSpecialty;

    // Patient diagnoses
    private final List<String> patientDx;
    private final String patientDxAddCode;
    private final String patientDxMatchCode;

    // Billing recommendations (pre-rendered HTML snippet)
    private final String billingRecommendations;

    // Billing history
    private final List<BillingHistoryEntry> billingHistory;

    // Validation messages
    private final String warningMsg;
    private final String errorMsg;
    private final String errorFlag;

    // Config
    private final String clinicView;
    private final String clinicNo;
    private final String visitType;
    private final boolean singleClickEnabled;
    private final boolean hospitalBilling;

    // Provider list for the form's provider picker
    private final List<ProviderOption> providers;

    // Billing-form selection (resolved after roster / preference / group / properties fallback)
    private final String defaultServiceType;

    // Default dx / visit type / location / visit date used to pre-populate the form
    private final String dxCode;
    private final String xmlVisitType;
    private final String xmlLocation;
    private final String visitDate;

    // Future expansion (not yet populated)
    private final Map<String, String> requestEchoes;

    private BillingONFormViewModel(Builder b) {
        this.userNo = b.userNo;
        this.demographicNo = b.demographicNo;
        this.appointmentNo = b.appointmentNo;
        this.providerNo = b.providerNo;
        this.apptProviderNo = b.apptProviderNo;
        this.providerView = b.providerView;
        this.demoName = b.demoName;
        this.today = b.today;
        this.billReferenceDate = b.billReferenceDate;
        this.mReview = b.mReview;
        this.ctlBillForm = b.ctlBillForm;
        this.curBillForm = b.curBillForm;
        this.demoLast = b.demoLast;
        this.demoFirst = b.demoFirst;
        this.demoHin = b.demoHin;
        this.demoVer = b.demoVer;
        this.demoDob = b.demoDob;
        this.demoDobYear = b.demoDobYear;
        this.demoDobMonth = b.demoDobMonth;
        this.demoDobDay = b.demoDobDay;
        this.demoHcType = b.demoHcType;
        this.demoSex = b.demoSex;
        this.familyDoctor = b.familyDoctor;
        this.rosterStatus = b.rosterStatus;
        this.assgProviderNo = b.assgProviderNo;
        this.age = b.age;
        this.referralDoctor = b.referralDoctor;
        this.referralDoctorOhip = b.referralDoctorOhip;
        this.referralSpecialty = b.referralSpecialty;
        this.patientDx = b.patientDx == null ? Collections.emptyList() : List.copyOf(b.patientDx);
        this.patientDxAddCode = b.patientDxAddCode;
        this.patientDxMatchCode = b.patientDxMatchCode;
        this.billingRecommendations = b.billingRecommendations;
        this.billingHistory = b.billingHistory == null ? Collections.emptyList() : List.copyOf(b.billingHistory);
        this.warningMsg = b.warningMsg;
        this.errorMsg = b.errorMsg;
        this.errorFlag = b.errorFlag;
        this.clinicView = b.clinicView;
        this.clinicNo = b.clinicNo;
        this.visitType = b.visitType;
        this.singleClickEnabled = b.singleClickEnabled;
        this.hospitalBilling = b.hospitalBilling;
        this.providers = b.providers == null ? Collections.emptyList() : List.copyOf(b.providers);
        this.defaultServiceType = b.defaultServiceType;
        this.dxCode = b.dxCode;
        this.xmlVisitType = b.xmlVisitType;
        this.xmlLocation = b.xmlLocation;
        this.visitDate = b.visitDate;
        this.requestEchoes = b.requestEchoes == null ? Collections.emptyMap() : Map.copyOf(b.requestEchoes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserNo() { return userNo; }
    public String getDemographicNo() { return demographicNo; }
    public String getAppointmentNo() { return appointmentNo; }
    public String getProviderNo() { return providerNo; }
    public String getApptProviderNo() { return apptProviderNo; }
    public String getProviderView() { return providerView; }
    public String getDemoName() { return demoName; }
    public String getToday() { return today; }
    public String getBillReferenceDate() { return billReferenceDate; }
    public String getMReview() { return mReview; }
    public String getCtlBillForm() { return ctlBillForm; }
    public String getCurBillForm() { return curBillForm; }
    public String getDemoLast() { return demoLast; }
    public String getDemoFirst() { return demoFirst; }
    public String getDemoHin() { return demoHin; }
    public String getDemoVer() { return demoVer; }
    public String getDemoDob() { return demoDob; }
    public String getDemoDobYear() { return demoDobYear; }
    public String getDemoDobMonth() { return demoDobMonth; }
    public String getDemoDobDay() { return demoDobDay; }
    public String getDemoHcType() { return demoHcType; }
    public String getDemoSex() { return demoSex; }
    public String getFamilyDoctor() { return familyDoctor; }
    public String getRosterStatus() { return rosterStatus; }
    public String getAssgProviderNo() { return assgProviderNo; }
    public int getAge() { return age; }
    public String getReferralDoctor() { return referralDoctor; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getReferralSpecialty() { return referralSpecialty; }
    public List<String> getPatientDx() { return patientDx; }
    public String getPatientDxAddCode() { return patientDxAddCode; }
    public String getPatientDxMatchCode() { return patientDxMatchCode; }
    public String getBillingRecommendations() { return billingRecommendations; }
    public List<BillingHistoryEntry> getBillingHistory() { return billingHistory; }
    public String getWarningMsg() { return warningMsg; }
    public String getErrorMsg() { return errorMsg; }
    public String getErrorFlag() { return errorFlag; }
    public String getClinicView() { return clinicView; }
    public String getClinicNo() { return clinicNo; }
    public String getVisitType() { return visitType; }
    public boolean isSingleClickEnabled() { return singleClickEnabled; }
    public boolean isHospitalBilling() { return hospitalBilling; }
    public List<ProviderOption> getProviders() { return providers; }
    public String getDefaultServiceType() { return defaultServiceType; }
    public String getDxCode() { return dxCode; }
    public String getXmlVisitType() { return xmlVisitType; }
    public String getXmlLocation() { return xmlLocation; }
    public String getVisitDate() { return visitDate; }
    public Map<String, String> getRequestEchoes() { return requestEchoes; }

    public static final class Builder {
        private String userNo;
        private String demographicNo;
        private String appointmentNo;
        private String providerNo;
        private String apptProviderNo;
        private String providerView;
        private String demoName;
        private String today;
        private String billReferenceDate;
        private String mReview;
        private String ctlBillForm;
        private String curBillForm;
        private String demoLast;
        private String demoFirst;
        private String demoHin;
        private String demoVer;
        private String demoDob;
        private String demoDobYear;
        private String demoDobMonth;
        private String demoDobDay;
        private String demoHcType;
        private String demoSex;
        private String familyDoctor;
        private String rosterStatus;
        private String assgProviderNo;
        private int age;
        private String referralDoctor;
        private String referralDoctorOhip;
        private String referralSpecialty;
        private List<String> patientDx;
        private String patientDxAddCode;
        private String patientDxMatchCode;
        private String billingRecommendations;
        private List<BillingHistoryEntry> billingHistory;
        private String warningMsg;
        private String errorMsg;
        private String errorFlag;
        private String clinicView;
        private String clinicNo;
        private String visitType;
        private boolean singleClickEnabled;
        private boolean hospitalBilling;
        private List<ProviderOption> providers;
        private String defaultServiceType;
        private String dxCode;
        private String xmlVisitType;
        private String xmlLocation;
        private String visitDate;
        private Map<String, String> requestEchoes;

        public Builder userNo(String v) { this.userNo = v; return this; }
        public Builder demographicNo(String v) { this.demographicNo = v; return this; }
        public Builder appointmentNo(String v) { this.appointmentNo = v; return this; }
        public Builder providerNo(String v) { this.providerNo = v; return this; }
        public Builder apptProviderNo(String v) { this.apptProviderNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder today(String v) { this.today = v; return this; }
        public Builder billReferenceDate(String v) { this.billReferenceDate = v; return this; }
        public Builder mReview(String v) { this.mReview = v; return this; }
        public Builder ctlBillForm(String v) { this.ctlBillForm = v; return this; }
        public Builder curBillForm(String v) { this.curBillForm = v; return this; }
        public Builder demoLast(String v) { this.demoLast = v; return this; }
        public Builder demoFirst(String v) { this.demoFirst = v; return this; }
        public Builder demoHin(String v) { this.demoHin = v; return this; }
        public Builder demoVer(String v) { this.demoVer = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoDobYear(String v) { this.demoDobYear = v; return this; }
        public Builder demoDobMonth(String v) { this.demoDobMonth = v; return this; }
        public Builder demoDobDay(String v) { this.demoDobDay = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder familyDoctor(String v) { this.familyDoctor = v; return this; }
        public Builder rosterStatus(String v) { this.rosterStatus = v; return this; }
        public Builder assgProviderNo(String v) { this.assgProviderNo = v; return this; }
        public Builder age(int v) { this.age = v; return this; }
        public Builder referralDoctor(String v) { this.referralDoctor = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder referralSpecialty(String v) { this.referralSpecialty = v; return this; }
        public Builder patientDx(List<String> v) { this.patientDx = v; return this; }
        public Builder patientDxAddCode(String v) { this.patientDxAddCode = v; return this; }
        public Builder patientDxMatchCode(String v) { this.patientDxMatchCode = v; return this; }
        public Builder billingRecommendations(String v) { this.billingRecommendations = v; return this; }
        public Builder billingHistory(List<BillingHistoryEntry> v) { this.billingHistory = v; return this; }
        public Builder warningMsg(String v) { this.warningMsg = v; return this; }
        public Builder errorMsg(String v) { this.errorMsg = v; return this; }
        public Builder errorFlag(String v) { this.errorFlag = v; return this; }
        public Builder clinicView(String v) { this.clinicView = v; return this; }
        public Builder clinicNo(String v) { this.clinicNo = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder singleClickEnabled(boolean v) { this.singleClickEnabled = v; return this; }
        public Builder hospitalBilling(boolean v) { this.hospitalBilling = v; return this; }
        public Builder providers(List<ProviderOption> v) { this.providers = v; return this; }
        public Builder defaultServiceType(String v) { this.defaultServiceType = v; return this; }
        public Builder dxCode(String v) { this.dxCode = v; return this; }
        public Builder xmlVisitType(String v) { this.xmlVisitType = v; return this; }
        public Builder xmlLocation(String v) { this.xmlLocation = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder requestEchoes(Map<String, String> v) { this.requestEchoes = v; return this; }

        public BillingONFormViewModel build() {
            return new BillingONFormViewModel(this);
        }
    }
}
