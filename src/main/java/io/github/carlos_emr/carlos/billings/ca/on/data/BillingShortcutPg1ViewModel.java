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
import java.util.Properties;
import java.util.ArrayList;
/**
 * Immutable view model for {@code billingShortcutPg1.jsp} (the fast-track
 * hospital-billing entry, page 1).
 *
 * <p>Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingShortcutPg1DataAssembler}
 * via
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingShortcutPg12Action}
 * and exposed to the JSP as request attribute {@code shortcutPg1Model}. Captures
 * the demographic + provider lookups, the billing-history vectors, the
 * service-code grid, and the validation messages that previously lived in the
 * 350-line top scriptlet of the legacy JSP.</p>
 *
 * <p>ArrayList / Properties types are kept (rather than a more idiomatic record /
 * Map shape) because the existing JSP scriptlet body iterates them as
 * {@link ArrayList} and reads via {@code Properties.getProperty}; preserving the
 * shape lets the bridge keep the rendering-side scriptlets intact while still
 * pulling the data from a single tested source.</p>
 *
 * @since 2026-04-24
 */
public final class BillingShortcutPg1ViewModel {

    // ---- Primary composed records ----
    private final BillingDemographicSummary demographic;
    private final BillingReferralDoctor referral;
    private final BillingValidationMessages messages;
    /** Multisite slice — Shortcut populates only the rmaEnabled flag and the
     *  clinicNbrs list (no multisite picker). */
    private final BillingMultisiteContext multisite;

    // Identity / context
    private final String userProviderNo;
    private final String providerView;
    private final String demoNo;
    private final String demoName;
    private final String apptNo;
    private final String apptProviderNo;
    private final String apptDate;
    private final String startTime;
    private final String ctlBillForm;
    private final String clinicNo;

    // Demographic-adjacent fields not subsumed by BillingDemographicSummary
    private final String assignedProviderNo;

    // Defaulted view config
    private final String visitType;
    private final String clinicView;
    private final String visitDate;
    private final String dxCode;

    // Pre-rendered banner (msg seed)
    private final String msg;

    // Vectors / maps preserved for downstream JSP rendering
    private final List<Properties> billingHistory;
    private final List<Properties> billingHistoryDetails;
    private final List<Properties> providers;
    private final List<Properties> clinicLocations;
    private final List<Properties> serviceCodeCol1;
    private final List<Properties> serviceCodeCol2;
    private final List<Properties> serviceCodeCol3;
    private final String headerTitle1;
    private final String headerTitle2;
    private final String headerTitle3;
    private final Map<String, String> propPremium;

    // Round-15: data formerly loaded inline by billingShortcutPg1.jsp via
    // SpringUtils.getBean (CtlBillingServiceDao, DiagnosticCodeDao,
    // ClinicNbrDao, ProviderDao for comments XML). Pre-loaded here so the
    // JSP can iterate via EL/JSTL without any DAO lookups in body code.
    // (rmaEnabled + clinicNbrs are now under {@link #multisite}.)
    private final String selectedXmlPSli;
    private final List<ServiceTypeEntry> serviceTypes;
    private final List<DxCodeEntry> dxCodes;

    // Round-16: drain residual scriptlets — pre-resolved values the JSP
    // previously computed inline in scriptlet code.
    private final Map<String, String> requestParamEchoes;
    private final String currentFormName;
    private final String assgProviderDisplay;
    private final boolean newOnBilling;
    private final String admissionDate;

    public record ServiceTypeEntry(String code, String name) { }
    public record DxCodeEntry(String code, String description) { }

    private BillingShortcutPg1ViewModel(Builder b) {
        // Composed-record resolution. Shortcut doesn't carry a hin-version
        // field, so demoVer always comes through as "".
        this.demographic = (b.demographic != null)
                ? b.demographic
                : new BillingDemographicSummary(
                        b.demoFirst, b.demoLast, b.demoHin, "",
                        b.demoSex, b.demoHcType, b.demoDob,
                        b.demoDobYy, b.demoDobMm, b.demoDobDd);
        this.referral = (b.referral != null)
                ? b.referral
                : new BillingReferralDoctor(
                        b.referralDoctorName, b.referralDoctorOhip, "");
        this.messages = (b.messages != null)
                ? b.messages
                : new BillingValidationMessages(b.errorFlag, b.errorMessage, b.warningMessage);
        // Shortcut's multisite slice is rmaEnabled + clinicNbrs +
        // selectedClinicNbrPrefix only. Other slices stay empty.
        this.multisite = (b.multisite != null)
                ? b.multisite
                : new BillingMultisiteContext(
                        false,
                        Collections.emptyList(),
                        "", "", "",
                        Collections.emptyMap(),
                        b.rmaEnabled,
                        b.clinicNbrs,
                        b.selectedClinicNbrPrefix);
        this.userProviderNo = nullToEmpty(b.userProviderNo);
        this.providerView = nullToEmpty(b.providerView);
        this.demoNo = nullToEmpty(b.demoNo);
        this.demoName = nullToEmpty(b.demoName);
        this.apptNo = nullToEmpty(b.apptNo);
        this.apptProviderNo = nullToEmpty(b.apptProviderNo);
        this.apptDate = nullToEmpty(b.apptDate);
        this.startTime = nullToEmpty(b.startTime);
        this.ctlBillForm = nullToEmpty(b.ctlBillForm);
        this.clinicNo = nullToEmpty(b.clinicNo);
        this.assignedProviderNo = nullToEmpty(b.assignedProviderNo);
        this.visitType = nullToEmpty(b.visitType);
        this.clinicView = nullToEmpty(b.clinicView);
        this.visitDate = nullToEmpty(b.visitDate);
        this.dxCode = nullToEmpty(b.dxCode);
        this.msg = nullToEmpty(b.msg);
        this.billingHistory = b.billingHistory == null ? Collections.emptyList() : List.copyOf(b.billingHistory);
        this.billingHistoryDetails = b.billingHistoryDetails == null ? Collections.emptyList() : List.copyOf(b.billingHistoryDetails);
        this.providers = b.providers == null ? Collections.emptyList() : List.copyOf(b.providers);
        this.clinicLocations = b.clinicLocations == null ? Collections.emptyList() : List.copyOf(b.clinicLocations);
        this.serviceCodeCol1 = b.serviceCodeCol1 == null ? Collections.emptyList() : List.copyOf(b.serviceCodeCol1);
        this.serviceCodeCol2 = b.serviceCodeCol2 == null ? Collections.emptyList() : List.copyOf(b.serviceCodeCol2);
        this.serviceCodeCol3 = b.serviceCodeCol3 == null ? Collections.emptyList() : List.copyOf(b.serviceCodeCol3);
        this.headerTitle1 = nullToEmpty(b.headerTitle1);
        this.headerTitle2 = nullToEmpty(b.headerTitle2);
        this.headerTitle3 = nullToEmpty(b.headerTitle3);
        this.propPremium = b.propPremium == null ? Collections.emptyMap() : Map.copyOf(b.propPremium);
        this.selectedXmlPSli = nullToEmpty(b.selectedXmlPSli);
        this.serviceTypes = b.serviceTypes == null ? Collections.emptyList() : List.copyOf(b.serviceTypes);
        this.dxCodes = b.dxCodes == null ? Collections.emptyList() : List.copyOf(b.dxCodes);
        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
        this.currentFormName = nullToEmpty(b.currentFormName);
        this.assgProviderDisplay = nullToEmpty(b.assgProviderDisplay);
        this.newOnBilling = b.newOnBilling;
        this.admissionDate = nullToEmpty(b.admissionDate);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Aggregated demographic snapshot — primary internal storage. */
    public BillingDemographicSummary getDemographic() { return demographic; }
    /** Aggregated referral-doctor record — primary internal storage. */
    public BillingReferralDoctor getReferral() { return referral; }
    /** Aggregated validation banner state — primary internal storage. */
    public BillingValidationMessages getMessagesAggregate() { return messages; }
    /** Aggregated multisite slice (rmaEnabled + clinicNbrs only). */
    public BillingMultisiteContext getMultisite() { return multisite; }

    public String getUserProviderNo() { return userProviderNo; }
    public String getProviderView() { return providerView; }
    public String getDemoNo() { return demoNo; }
    public String getDemoName() { return demoName; }
    public String getApptNo() { return apptNo; }
    public String getApptProviderNo() { return apptProviderNo; }
    public String getApptDate() { return apptDate; }
    public String getStartTime() { return startTime; }
    public String getCtlBillForm() { return ctlBillForm; }
    public String getClinicNo() { return clinicNo; }
    public String getDemoFirst() { return demographic.firstName(); }
    public String getDemoLast() { return demographic.lastName(); }
    public String getDemoSex() { return demographic.sex(); }
    public String getDemoHin() { return demographic.hin(); }
    public String getDemoDob() { return demographic.dob(); }
    public String getDemoDobYy() { return demographic.dobYy(); }
    public String getDemoDobMm() { return demographic.dobMm(); }
    public String getDemoDobDd() { return demographic.dobDd(); }
    public String getDemoHcType() { return demographic.hcType(); }
    /** Alias of {@link #getDemographic()} (legacy). */
    public BillingDemographicSummary getDemographicSummary() { return demographic; }
    public String getAssignedProviderNo() { return assignedProviderNo; }
    public String getReferralDoctorName() { return referral.name(); }
    public String getReferralDoctorOhip() { return referral.ohip(); }
    /** Alias of {@link #getReferral()} (legacy). */
    public BillingReferralDoctor getReferralDoctorRecord() { return referral; }
    public String getVisitType() { return visitType; }
    public String getClinicView() { return clinicView; }
    public String getVisitDate() { return visitDate; }
    public String getDxCode() { return dxCode; }
    public String getErrorFlag() { return messages.errorFlag(); }
    public String getErrorMessage() { return messages.errorMessage(); }
    public String getWarningMessage() { return messages.warningMessage(); }
    /** Alias of {@link #getMessagesAggregate()} (legacy). */
    public BillingValidationMessages getValidationMessages() { return messages; }
    public String getMsg() { return msg; }
    public List<Properties> getBillingHistory() { return billingHistory; }
    public List<Properties> getBillingHistoryDetails() { return billingHistoryDetails; }
    public List<Properties> getProviders() { return providers; }
    public List<Properties> getClinicLocations() { return clinicLocations; }
    public List<Properties> getServiceCodeCol1() { return serviceCodeCol1; }
    public List<Properties> getServiceCodeCol2() { return serviceCodeCol2; }
    public List<Properties> getServiceCodeCol3() { return serviceCodeCol3; }
    public String getHeaderTitle1() { return headerTitle1; }
    public String getHeaderTitle2() { return headerTitle2; }
    public String getHeaderTitle3() { return headerTitle3; }
    public Map<String, String> getPropPremium() { return propPremium; }

    public boolean isRmaEnabled() { return multisite.rmaEnabled(); }
    public List<BillingMultisiteContext.ClinicNbrEntry> getClinicNbrs() { return multisite.clinicNbrs(); }
    public String getSelectedClinicNbrPrefix() { return multisite.selectedClinicNbrPrefix(); }
    public String getSelectedXmlPSli() { return selectedXmlPSli; }
    public List<ServiceTypeEntry> getServiceTypes() { return serviceTypes; }
    public List<DxCodeEntry> getDxCodes() { return dxCodes; }
    /**
     * Pre-resolved request-param echoes for hidden-input value attributes —
     * billDate, serviceDateN, serviceUnitN, dxCode, referralCode,
     * referralDocName, rulePerc, xml_billtype, xml_visittype, xml_location,
     * xml_vdate, unit_xml_&lt;code&gt;, code_xml_&lt;code&gt;, etc. Carried
     * through the model so the JSP never references {@code ${param.X}}
     * directly (the encoder-validator hook treats raw param refs as XSS even
     * inside {@code <c:out>}).
     */
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }
    /**
     * Display name of the currently-selected billing form (truncated to 30
     * chars). Pre-resolved by the assembler so the JSP can render it as
     * EL without scriptlet-iterating {@link #getServiceTypes()} a second
     * time to locate the matching label.
     */
    public String getCurrentFormName() { return currentFormName; }
    /** Pre-resolved providerBean session-map lookup for the assigned billing physician. */
    public String getAssgProviderDisplay() { return assgProviderDisplay; }
    /** {@code isNewONbilling} property — drives the legacy/new history-table branch. */
    public boolean isNewOnBilling() { return newOnBilling; }
    /**
     * Pre-computed admission date for the {@code xml_vdate} input — empty
     * unless {@link #getVisitType()} starts with {@code "02"} (hospital) or
     * {@code "04"} (nursing home), in which case it equals
     * {@link #getVisitDate()}. Mirrors the legacy JSP scriptlet logic.
     */
    public String getAdmissionDate() { return admissionDate; }

    /**
     * ArrayList view of {@link #getBillingHistory()} for legacy JSP scriptlets that
     * iterate via {@link ArrayList}. Returns a defensive copy.
     */
    public ArrayList<Properties> getBillingHistoryVec() { return new ArrayList<>(billingHistory); }

    /** ArrayList view of {@link #getBillingHistoryDetails()} for legacy JSP scriptlets. */
    public ArrayList<Properties> getBillingHistoryDetailsVec() { return new ArrayList<>(billingHistoryDetails); }

    /** ArrayList view of {@link #getProviders()} for legacy JSP scriptlets. */
    public ArrayList<Properties> getProvidersVec() { return new ArrayList<>(providers); }

    /** ArrayList view of {@link #getClinicLocations()} for legacy JSP scriptlets. */
    public ArrayList<Properties> getClinicLocationsVec() { return new ArrayList<>(clinicLocations); }

    /** ArrayList view of {@link #getServiceCodeCol1()} for legacy JSP scriptlets. */
    public ArrayList<Properties> getServiceCodeCol1Vec() { return new ArrayList<>(serviceCodeCol1); }

    /** ArrayList view of {@link #getServiceCodeCol2()} for legacy JSP scriptlets. */
    public ArrayList<Properties> getServiceCodeCol2Vec() { return new ArrayList<>(serviceCodeCol2); }

    /** ArrayList view of {@link #getServiceCodeCol3()} for legacy JSP scriptlets. */
    public ArrayList<Properties> getServiceCodeCol3Vec() { return new ArrayList<>(serviceCodeCol3); }

    /**
     * Properties view of {@link #getPropPremium()} for legacy JSP scriptlets that
     * call {@code Properties.getProperty(key)}. Returns a defensive copy.
     */
    public Properties getPropPremiumProps() {
        Properties p = new Properties();
        p.putAll(propPremium);
        return p;
    }

    public static final class Builder {
        // ---- Composed-record setters (preferred) ----
        private BillingDemographicSummary demographic;
        private BillingReferralDoctor referral;
        private BillingValidationMessages messages;
        private BillingMultisiteContext multisite;

        public Builder demographic(BillingDemographicSummary v) { this.demographic = v; return this; }
        public Builder referral(BillingReferralDoctor v) { this.referral = v; return this; }
        public Builder messages(BillingValidationMessages v) { this.messages = v; return this; }
        public Builder multisite(BillingMultisiteContext v) { this.multisite = v; return this; }

        // ---- Legacy flat-field accumulators ----
        private String userProviderNo;
        private String providerView;
        private String demoNo;
        private String demoName;
        private String apptNo;
        private String apptProviderNo;
        private String apptDate;
        private String startTime;
        private String ctlBillForm;
        private String clinicNo;
        private String demoFirst;
        private String demoLast;
        private String demoSex;
        private String demoHin;
        private String demoDob;
        private String demoDobYy;
        private String demoDobMm;
        private String demoDobDd;
        private String demoHcType;
        private String assignedProviderNo;
        private String referralDoctorName;
        private String referralDoctorOhip;
        private String visitType;
        private String clinicView;
        private String visitDate;
        private String dxCode;
        private String errorFlag;
        private String errorMessage;
        private String warningMessage;
        private String msg;
        private List<Properties> billingHistory;
        private List<Properties> billingHistoryDetails;
        private List<Properties> providers;
        private List<Properties> clinicLocations;
        private List<Properties> serviceCodeCol1;
        private List<Properties> serviceCodeCol2;
        private List<Properties> serviceCodeCol3;
        private String headerTitle1;
        private String headerTitle2;
        private String headerTitle3;
        private Map<String, String> propPremium;

        public Builder userProviderNo(String v) { this.userProviderNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder demoNo(String v) { this.demoNo = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder apptNo(String v) { this.apptNo = v; return this; }
        public Builder apptProviderNo(String v) { this.apptProviderNo = v; return this; }
        public Builder apptDate(String v) { this.apptDate = v; return this; }
        public Builder startTime(String v) { this.startTime = v; return this; }
        public Builder ctlBillForm(String v) { this.ctlBillForm = v; return this; }
        public Builder clinicNo(String v) { this.clinicNo = v; return this; }
        public Builder demoFirst(String v) { this.demoFirst = v; return this; }
        public Builder demoLast(String v) { this.demoLast = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder demoHin(String v) { this.demoHin = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoDobYy(String v) { this.demoDobYy = v; return this; }
        public Builder demoDobMm(String v) { this.demoDobMm = v; return this; }
        public Builder demoDobDd(String v) { this.demoDobDd = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder assignedProviderNo(String v) { this.assignedProviderNo = v; return this; }
        public Builder referralDoctorName(String v) { this.referralDoctorName = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder clinicView(String v) { this.clinicView = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder dxCode(String v) { this.dxCode = v; return this; }
        public Builder errorFlag(String v) { this.errorFlag = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public Builder warningMessage(String v) { this.warningMessage = v; return this; }
        public Builder msg(String v) { this.msg = v; return this; }
        public Builder billingHistory(List<Properties> v) { this.billingHistory = v; return this; }
        public Builder billingHistoryDetails(List<Properties> v) { this.billingHistoryDetails = v; return this; }
        public Builder providers(List<Properties> v) { this.providers = v; return this; }
        public Builder clinicLocations(List<Properties> v) { this.clinicLocations = v; return this; }
        public Builder serviceCodeCol1(List<Properties> v) { this.serviceCodeCol1 = v; return this; }
        public Builder serviceCodeCol2(List<Properties> v) { this.serviceCodeCol2 = v; return this; }
        public Builder serviceCodeCol3(List<Properties> v) { this.serviceCodeCol3 = v; return this; }
        public Builder headerTitle1(String v) { this.headerTitle1 = v; return this; }
        public Builder headerTitle2(String v) { this.headerTitle2 = v; return this; }
        public Builder headerTitle3(String v) { this.headerTitle3 = v; return this; }
        public Builder propPremium(Map<String, String> v) { this.propPremium = v; return this; }

        private boolean rmaEnabled;
        private List<BillingMultisiteContext.ClinicNbrEntry> clinicNbrs;
        private String selectedClinicNbrPrefix;
        private String selectedXmlPSli;
        private List<ServiceTypeEntry> serviceTypes;
        private List<DxCodeEntry> dxCodes;

        public Builder rmaEnabled(boolean v) { this.rmaEnabled = v; return this; }
        public Builder clinicNbrs(List<BillingMultisiteContext.ClinicNbrEntry> v) {
            this.clinicNbrs = v == null ? null : List.copyOf(v); return this;
        }
        public Builder selectedClinicNbrPrefix(String v) { this.selectedClinicNbrPrefix = v; return this; }
        public Builder selectedXmlPSli(String v) { this.selectedXmlPSli = v; return this; }
        public Builder serviceTypes(List<ServiceTypeEntry> v) {
            this.serviceTypes = v == null ? null : List.copyOf(v); return this;
        }
        public Builder dxCodes(List<DxCodeEntry> v) {
            this.dxCodes = v == null ? null : List.copyOf(v); return this;
        }

        // Round-16: drain residual scriptlets.
        private Map<String, String> requestParamEchoes;
        private String currentFormName;
        private String assgProviderDisplay;
        private boolean newOnBilling;
        private String admissionDate;

        public Builder requestParamEchoes(Map<String, String> v) {
            this.requestParamEchoes = v == null ? null : Map.copyOf(v); return this;
        }
        public Builder currentFormName(String v) { this.currentFormName = v; return this; }
        public Builder assgProviderDisplay(String v) { this.assgProviderDisplay = v; return this; }
        public Builder newOnBilling(boolean v) { this.newOnBilling = v; return this; }
        public Builder admissionDate(String v) { this.admissionDate = v; return this; }

        public BillingShortcutPg1ViewModel build() {
            return new BillingShortcutPg1ViewModel(this);
        }
    }
}
