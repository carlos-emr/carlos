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
import java.util.Set;

/**
 * Immutable view model for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Assembled by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONFormDataAssembler}
 * via {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.ViewBillingON2Action}
 * and exposed to the JSP as request attribute {@code formModel}. Carries the
 * provider/demographic context, dx/service-code grids, billing history, and
 * validation banner state the form needs to render — replacing ~24 DAO
 * lookups that previously ran inline in the JSP and pushed the rendered
 * response past the 1 MB page buffer.</p>
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

    /** One entry in the service-code grid (3 columns x N service types). */
    public record ServiceCodeEntry(
            String serviceCode,
            String serviceDesc,
            String serviceDisp,
            String servicePercentage,
            String serviceType,
            String serviceTypeName,
            String displayStyle,
            boolean sliFlag) { }

    /** One billing form entry for the Layer1 menu and the _billingForms JS array. */
    public record BillingFormMenuEntry(
            String code,
            String name,
            String billType) { }

    /** One dx-code panel entry per service type. */
    public record DxCodeEntry(
            String serviceType,
            String diagnosticCode,
            String description) { }

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
    /** True when the stored DOB was non-empty but unparseable as YYYYMMDD.
     *  The assembler sets this so the JSP can warn the operator that
     *  visit-type defaults / premium codes are computed off bad input. */
    private final boolean demoDobInvalid;
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

    // Service-code grid data (3 columns x N service types)
    private final Map<String, List<ServiceCodeEntry>> billingServiceCodesMap;
    private final List<String> listServiceType;
    private final Map<String, String> titleMap;
    private final Set<String> premiumCodes;
    private final String defaultBillFormName;
    private final String defaultBillType;

    // Billing-form menu (Layer1 + the _billingForms JS autocomplete array)
    private final List<BillingFormMenuEntry> billingForms;
    // Dx codes grouped by service type for the Layer2 search panels
    private final Map<String, List<DxCodeEntry>> dxCodesByServiceType;
    // Favourite combo list used by the cutlist dropdown (flat code/name pairs)
    private final List<String> billingFavourites;

    // Future expansion (not yet populated)
    private final Map<String, String> requestEchoes;

    private BillingONFormViewModel(Builder b) {
        this.userNo = nullToEmpty(b.userNo);
        this.demographicNo = nullToEmpty(b.demographicNo);
        this.appointmentNo = nullToEmpty(b.appointmentNo);
        this.providerNo = nullToEmpty(b.providerNo);
        this.apptProviderNo = nullToEmpty(b.apptProviderNo);
        this.providerView = nullToEmpty(b.providerView);
        this.demoName = nullToEmpty(b.demoName);
        this.today = nullToEmpty(b.today);
        this.billReferenceDate = nullToEmpty(b.billReferenceDate);
        this.mReview = nullToEmpty(b.mReview);
        this.ctlBillForm = nullToEmpty(b.ctlBillForm);
        this.curBillForm = nullToEmpty(b.curBillForm);
        this.demoLast = nullToEmpty(b.demoLast);
        this.demoFirst = nullToEmpty(b.demoFirst);
        this.demoHin = nullToEmpty(b.demoHin);
        this.demoVer = nullToEmpty(b.demoVer);
        this.demoDob = nullToEmpty(b.demoDob);
        this.demoDobYear = nullToEmpty(b.demoDobYear);
        this.demoDobMonth = nullToEmpty(b.demoDobMonth);
        this.demoDobDay = nullToEmpty(b.demoDobDay);
        this.demoDobInvalid = b.demoDobInvalid;
        this.demoHcType = nullToEmpty(b.demoHcType);
        this.demoSex = nullToEmpty(b.demoSex);
        this.familyDoctor = nullToEmpty(b.familyDoctor);
        this.rosterStatus = nullToEmpty(b.rosterStatus);
        this.assgProviderNo = nullToEmpty(b.assgProviderNo);
        this.age = b.age;
        this.referralDoctor = nullToEmpty(b.referralDoctor);
        this.referralDoctorOhip = nullToEmpty(b.referralDoctorOhip);
        this.referralSpecialty = nullToEmpty(b.referralSpecialty);
        this.patientDx = b.patientDx == null ? Collections.emptyList() : List.copyOf(b.patientDx);
        this.patientDxAddCode = nullToEmpty(b.patientDxAddCode);
        this.patientDxMatchCode = nullToEmpty(b.patientDxMatchCode);
        this.billingRecommendations = nullToEmpty(b.billingRecommendations);
        this.billingHistory = b.billingHistory == null ? Collections.emptyList() : List.copyOf(b.billingHistory);
        this.warningMsg = nullToEmpty(b.warningMsg);
        this.errorMsg = nullToEmpty(b.errorMsg);
        this.errorFlag = nullToEmpty(b.errorFlag);
        this.clinicView = nullToEmpty(b.clinicView);
        this.clinicNo = nullToEmpty(b.clinicNo);
        this.visitType = nullToEmpty(b.visitType);
        this.singleClickEnabled = b.singleClickEnabled;
        this.hospitalBilling = b.hospitalBilling;
        this.providers = b.providers == null ? Collections.emptyList() : List.copyOf(b.providers);
        this.defaultServiceType = nullToEmpty(b.defaultServiceType);
        this.dxCode = nullToEmpty(b.dxCode);
        this.xmlVisitType = nullToEmpty(b.xmlVisitType);
        this.xmlLocation = nullToEmpty(b.xmlLocation);
        this.visitDate = nullToEmpty(b.visitDate);
        // Deep-copy: Map.copyOf is shallow, so without per-entry copying the
        // nested lists could still be mutated after build. Force the inner lists
        // immutable too so the view-model's immutability contract holds.
        this.billingServiceCodesMap = b.billingServiceCodesMap == null
                ? Collections.emptyMap()
                : copyOfNestedListMap(b.billingServiceCodesMap);
        this.listServiceType = b.listServiceType == null
                ? Collections.emptyList() : List.copyOf(b.listServiceType);
        this.titleMap = b.titleMap == null ? Collections.emptyMap() : Map.copyOf(b.titleMap);
        this.premiumCodes = b.premiumCodes == null ? Collections.emptySet() : Set.copyOf(b.premiumCodes);
        this.defaultBillFormName = nullToEmpty(b.defaultBillFormName);
        this.defaultBillType = nullToEmpty(b.defaultBillType);
        this.billingForms = b.billingForms == null ? Collections.emptyList() : List.copyOf(b.billingForms);
        this.dxCodesByServiceType = b.dxCodesByServiceType == null
                ? Collections.emptyMap()
                : copyOfNestedListMap(b.dxCodesByServiceType);
        this.billingFavourites = b.billingFavourites == null
                ? Collections.emptyList() : List.copyOf(b.billingFavourites);
        this.requestEchoes = b.requestEchoes == null ? Collections.emptyMap() : Map.copyOf(b.requestEchoes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Coalesces null Strings to empty so the JSP doesn't render the literal
     * 4-character word {@code "null"} when a builder field is unset. Mirrors
     * the {@code nullToEmpty} pattern already used by
     * {@link BillingONCorrectionViewModel} and {@link BillingShortcutPg1ViewModel}.
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
    /** Aggregated view of the demographic snapshot as a structured record. */
    public BillingDemographicSummary getDemographicSummary() {
        return new BillingDemographicSummary(demoFirst, demoLast, demoHin, demoVer,
                demoSex, demoHcType, demoDob, demoDobYear, demoDobMonth, demoDobDay);
    }
    public boolean isDemoDobInvalid() { return demoDobInvalid; }
    public String getDemoHcType() { return demoHcType; }
    public String getDemoSex() { return demoSex; }
    public String getFamilyDoctor() { return familyDoctor; }
    public String getRosterStatus() { return rosterStatus; }
    public String getAssgProviderNo() { return assgProviderNo; }
    public int getAge() { return age; }
    public String getReferralDoctor() { return referralDoctor; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getReferralSpecialty() { return referralSpecialty; }
    /** Aggregated referral-doctor view as a structured record. */
    public BillingReferralDoctor getReferralDoctorRecord() {
        return new BillingReferralDoctor(referralDoctor, referralDoctorOhip, referralSpecialty);
    }
    public List<String> getPatientDx() { return patientDx; }
    public String getPatientDxAddCode() { return patientDxAddCode; }
    public String getPatientDxMatchCode() { return patientDxMatchCode; }
    public String getBillingRecommendations() { return billingRecommendations; }
    public List<BillingHistoryEntry> getBillingHistory() { return billingHistory; }
    public String getWarningMsg() { return warningMsg; }
    public String getErrorMsg() { return errorMsg; }
    public String getErrorFlag() { return errorFlag; }
    /** Aggregated view of the (errorFlag, errorMsg, warningMsg) triple. */
    public BillingValidationMessages getValidationMessages() {
        return new BillingValidationMessages(errorFlag, errorMsg, warningMsg);
    }
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
    public Map<String, List<ServiceCodeEntry>> getBillingServiceCodesMap() { return billingServiceCodesMap; }
    public List<String> getListServiceType() { return listServiceType; }
    public Map<String, String> getTitleMap() { return titleMap; }
    public Set<String> getPremiumCodes() { return premiumCodes; }
    public String getDefaultBillFormName() { return defaultBillFormName; }
    public String getDefaultBillType() { return defaultBillType; }
    public List<BillingFormMenuEntry> getBillingForms() { return billingForms; }
    public Map<String, List<DxCodeEntry>> getDxCodesByServiceType() { return dxCodesByServiceType; }
    public List<String> getBillingFavourites() { return billingFavourites; }
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
        private boolean demoDobInvalid;
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
        private Map<String, List<ServiceCodeEntry>> billingServiceCodesMap;
        private List<String> listServiceType;
        private Map<String, String> titleMap;
        private Set<String> premiumCodes;
        private String defaultBillFormName;
        private String defaultBillType;
        private List<BillingFormMenuEntry> billingForms;
        private Map<String, List<DxCodeEntry>> dxCodesByServiceType;
        private List<String> billingFavourites;
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
        public Builder demoDobInvalid(boolean v) { this.demoDobInvalid = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder familyDoctor(String v) { this.familyDoctor = v; return this; }
        public Builder rosterStatus(String v) { this.rosterStatus = v; return this; }
        public Builder assgProviderNo(String v) { this.assgProviderNo = v; return this; }
        public Builder age(int v) { this.age = v; return this; }
        public Builder referralDoctor(String v) { this.referralDoctor = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder referralSpecialty(String v) { this.referralSpecialty = v; return this; }
        // Collection setters store an unmodifiable copy at the call site so that
        // callers retaining the original mutable List/Map/Set can't influence
        // builder state between setter invocation and build(). The constructor
        // performs an additional copy, but defense-in-depth here closes the
        // CodeQL "exposing internal representation" finding.
        public Builder patientDx(List<String> v) { this.patientDx = v == null ? null : List.copyOf(v); return this; }
        public Builder patientDxAddCode(String v) { this.patientDxAddCode = v; return this; }
        public Builder patientDxMatchCode(String v) { this.patientDxMatchCode = v; return this; }
        public Builder billingRecommendations(String v) { this.billingRecommendations = v; return this; }
        public Builder billingHistory(List<BillingHistoryEntry> v) { this.billingHistory = v == null ? null : List.copyOf(v); return this; }
        public Builder warningMsg(String v) { this.warningMsg = v; return this; }
        public Builder errorMsg(String v) { this.errorMsg = v; return this; }
        public Builder errorFlag(String v) { this.errorFlag = v; return this; }
        public Builder clinicView(String v) { this.clinicView = v; return this; }
        public Builder clinicNo(String v) { this.clinicNo = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder singleClickEnabled(boolean v) { this.singleClickEnabled = v; return this; }
        public Builder hospitalBilling(boolean v) { this.hospitalBilling = v; return this; }
        public Builder providers(List<ProviderOption> v) { this.providers = v == null ? null : List.copyOf(v); return this; }
        public Builder defaultServiceType(String v) { this.defaultServiceType = v; return this; }
        public Builder dxCode(String v) { this.dxCode = v; return this; }
        public Builder xmlVisitType(String v) { this.xmlVisitType = v; return this; }
        public Builder xmlLocation(String v) { this.xmlLocation = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder billingServiceCodesMap(Map<String, List<ServiceCodeEntry>> v) { this.billingServiceCodesMap = v == null ? null : copyOfNestedListMap(v); return this; }
        public Builder listServiceType(List<String> v) { this.listServiceType = v == null ? null : List.copyOf(v); return this; }
        public Builder titleMap(Map<String, String> v) { this.titleMap = v == null ? null : Map.copyOf(v); return this; }
        public Builder premiumCodes(Set<String> v) { this.premiumCodes = v == null ? null : Set.copyOf(v); return this; }
        public Builder defaultBillFormName(String v) { this.defaultBillFormName = v; return this; }
        public Builder defaultBillType(String v) { this.defaultBillType = v; return this; }
        public Builder billingForms(List<BillingFormMenuEntry> v) { this.billingForms = v == null ? null : List.copyOf(v); return this; }
        public Builder dxCodesByServiceType(Map<String, List<DxCodeEntry>> v) { this.dxCodesByServiceType = v == null ? null : copyOfNestedListMap(v); return this; }
        public Builder billingFavourites(List<String> v) { this.billingFavourites = v == null ? null : List.copyOf(v); return this; }
        public Builder requestEchoes(Map<String, String> v) { this.requestEchoes = v == null ? null : Map.copyOf(v); return this; }

        public BillingONFormViewModel build() {
            return new BillingONFormViewModel(this);
        }
    }

    /**
     * Builds an immutable copy of a {@code Map<String, List<T>>} where the
     * outer map and each inner list are wrapped in {@link Map#copyOf} /
     * {@link List#copyOf}. {@link Map#copyOf} alone would only freeze the outer
     * map, leaving the inner lists mutable and breaking the view-model
     * immutability contract.
     */
    private static <T> Map<String, List<T>> copyOfNestedListMap(Map<String, List<T>> source) {
        java.util.LinkedHashMap<String, List<T>> tmp = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<T>> e : source.entrySet()) {
            tmp.put(e.getKey(), e.getValue() == null
                    ? Collections.emptyList()
                    : List.copyOf(e.getValue()));
        }
        return Map.copyOf(tmp);
    }
}
