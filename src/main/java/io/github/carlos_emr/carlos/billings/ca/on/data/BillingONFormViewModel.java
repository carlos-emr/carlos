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
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONFormDataAssembler}
 * via {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingON2Action}
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

    /**
     * One row in the recent-billing history table at the bottom of the form.
     * Replaces the inline {@code aL.get(i)/aL.get(i+1)} cast pairs the legacy
     * JSP used to iterate {@code JdbcBillingReviewImpl.getBillingHist}.
     */
    public record BillingHistoryRow(
            String id,
            String billingDate,
            String serviceDate,
            String serviceCode,
            String dx,
            String updateDate) { }

    /** One entry in the visit-location dropdown ({@code xml_location}). */
    public record FacilityNumOption(String code, String label) { }

    /** One entry in the {@code cutlist} (favourite super-codes) dropdown. */
    public record BillingFavouriteOption(String text, String value) { }

    /**
     * One entry in the legacy non-multisite "site" dropdown rendered when
     * {@code scheduleSiteID} is set in the props file. Resolved via
     * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingSiteIdPrep}.
     */
    public record LegacySiteOption(String name, boolean suggested) { }

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

    // Round-15: site context for billingON.jsp's multisite + RMA blocks.
    // Replaces the inline SiteDao / OscarAppointmentDao / ClinicNbrDao /
    // ProviderDao calls the JSP previously made.
    private final boolean multisiteEnabled;
    private final List<MultisiteSite> multisiteSites;
    private final String defaultSelectedSite;
    private final String defaultXmlProvider;
    private final boolean rmaEnabled;
    private final List<ClinicNbrEntry> clinicNbrs;
    private final String selectedClinicNbrPrefix;

    /** A multisite site entry (name, bg color, providers attached). */
    public record MultisiteSite(String name, String bgColor, List<MultisiteProvider> providers) { }
    /** A provider attached to a multisite site (for the per-site picker). */
    public record MultisiteProvider(String providerNo, String ohipNo, String lastName, String firstName) { }
    /** Clinic-number entry for the xml_visittype dropdown when rma_enabled=true. */
    public record ClinicNbrEntry(String nbrValue, String displayLabel) { }

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

    // Round-A6: residual body-DAO data — pulled from the assembler so the JSP
    // body no longer reaches into JdbcBillingPageUtil / JdbcBillingReviewImpl /
    // BillingSiteIdPrep / DemographicData / JdbcApptImpl directly.
    /** All recent-history rows the bottom table renders (was {@code aL} in JSP). */
    private final List<BillingHistoryRow> billingHistoryRows;
    /** Visit-location dropdown options (was {@code tdbObj.getFacilty_num()}). */
    private final List<FacilityNumOption> facilityNumOptions;
    /** Resolved default "strLocation" for the location dropdown's selected state. */
    private final String defaultLocation;
    /** Cut-list super-code options (was {@code tdbObj.getBillingFavouriteList()}). */
    private final List<BillingFavouriteOption> billingFavouriteOptions;
    /** Legacy non-multisite site dropdown context (was {@code BillingSiteIdPrep}). */
    private final boolean legacySiteContextEnabled;
    private final List<LegacySiteOption> legacySiteOptions;
    /** Admission-date pre-fill (was {@code DemographicData.getDemographicDateJoined}). */
    private final String admissionDate;
    /** Pre-resolved xml_vdate input value: param &gt; admissionDate &gt; "". */
    private final String defaultXmlVdate;
    /** Pre-resolved xml_billtype selection (request param &gt; rosterStatus
     *  override of {@code defaultBillType}). Lets the JSP iterate the static
     *  bill-type list with {@code fn:startsWith(formModel.selectedBillType, ...)}
     *  instead of computing the local {@code srtBillType} inline. */
    private final String selectedBillType;
    /** Display name of the assigned billing physician (was {@code providerBean.getProperty}).
     *  Truncated to 14 chars when the full name exceeds 15 to keep the legacy
     *  rendering width contract. */
    private final String assgProviderDisplay;
    /** Default referral-doctor checkbox state ("checked" or ""). */
    private final String referralCheckedDefault;
    /** Default referral-doctor name input value. */
    private final String referralNameDefault;
    /** Default referral-doctor OHIP-no input value. */
    private final String referralNoDefault;
    /** Default dx code for the dxCode input (request param &gt; preference &gt; history). */
    private final String dxCodeDefault;
    /** Default service date for the input above the dx panel. */
    private final String serviceDateDefault;
    /** Pre-rendered "msg" string (msg seed + errorMsg + warningMsg + DOB-invalid
     *  warning) the legacy JSP built inline at lines 273-280. */
    private final String displayMessage;
    /** {@code primary_care_incentive} property value (trimmed). Used by the
     *  {@code onChangePrivate} JS to route to the configured PRI form. */
    private final String primaryCareIncentive;
    /** {@code default_view} property value (trimmed). Used by the same JS to
     *  route back to the default form when leaving the PRI variant. */
    private final String defaultView;
    /** URL-encoded demographic name for href interpolation in click handlers. */
    private final String demoNameUrlEncoded;
    /** Pre-resolved value for the multisite xml_provider input on first render
     *  ({@code request.getParameter("xml_provider") || defaultXmlProvider}). */
    private final String selectedXmlProvider;
    /** Pre-rendered provider-pickers HTML keyed by site name. The legacy JSP
     *  built the same string inline via SiteDao + Provider iteration; the
     *  assembler now produces it once so the {@code _providers} JS array can
     *  be a {@code c:forEach}-emitted assignment. */
    private final Map<String, String> multisiteProviderHtml;
    /** Pre-resolved request-param echoes for hidden-input value attributes —
     *  appointment_date / start_time / asstProvider_no / apptProvider_no /
     *  billNo_old / billStatus_old / dxCode1 / dxCode2 / serviceCodeN /
     *  serviceUnitN / serviceAtN. Carried through the model so the JSP never
     *  references {@code ${param.X}} directly (the encoder-validator hook
     *  treats raw param refs as XSS even inside {@code <c:out>}). */
    private final Map<String, String> requestParamEchoes;

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
        this.multisiteEnabled = b.multisiteEnabled;
        this.multisiteSites = b.multisiteSites == null ? Collections.emptyList() : List.copyOf(b.multisiteSites);
        this.defaultSelectedSite = nullToEmpty(b.defaultSelectedSite);
        this.defaultXmlProvider = nullToEmpty(b.defaultXmlProvider);
        this.rmaEnabled = b.rmaEnabled;
        this.clinicNbrs = b.clinicNbrs == null ? Collections.emptyList() : List.copyOf(b.clinicNbrs);
        this.selectedClinicNbrPrefix = nullToEmpty(b.selectedClinicNbrPrefix);
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
        this.billingHistoryRows = b.billingHistoryRows == null
                ? Collections.emptyList() : List.copyOf(b.billingHistoryRows);
        this.facilityNumOptions = b.facilityNumOptions == null
                ? Collections.emptyList() : List.copyOf(b.facilityNumOptions);
        this.defaultLocation = nullToEmpty(b.defaultLocation);
        this.billingFavouriteOptions = b.billingFavouriteOptions == null
                ? Collections.emptyList() : List.copyOf(b.billingFavouriteOptions);
        this.legacySiteContextEnabled = b.legacySiteContextEnabled;
        this.legacySiteOptions = b.legacySiteOptions == null
                ? Collections.emptyList() : List.copyOf(b.legacySiteOptions);
        this.admissionDate = nullToEmpty(b.admissionDate);
        this.defaultXmlVdate = nullToEmpty(b.defaultXmlVdate);
        this.selectedBillType = nullToEmpty(b.selectedBillType);
        this.assgProviderDisplay = nullToEmpty(b.assgProviderDisplay);
        this.referralCheckedDefault = nullToEmpty(b.referralCheckedDefault);
        this.referralNameDefault = nullToEmpty(b.referralNameDefault);
        this.referralNoDefault = nullToEmpty(b.referralNoDefault);
        this.dxCodeDefault = nullToEmpty(b.dxCodeDefault);
        this.serviceDateDefault = nullToEmpty(b.serviceDateDefault);
        this.displayMessage = nullToEmpty(b.displayMessage);
        this.primaryCareIncentive = nullToEmpty(b.primaryCareIncentive);
        this.defaultView = nullToEmpty(b.defaultView);
        this.demoNameUrlEncoded = nullToEmpty(b.demoNameUrlEncoded);
        this.selectedXmlProvider = nullToEmpty(b.selectedXmlProvider);
        this.multisiteProviderHtml = b.multisiteProviderHtml == null
                ? Collections.emptyMap() : Map.copyOf(b.multisiteProviderHtml);
        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
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
    public boolean isMultisiteEnabled() { return multisiteEnabled; }
    public List<MultisiteSite> getMultisiteSites() { return multisiteSites; }
    public String getDefaultSelectedSite() { return defaultSelectedSite; }
    public String getDefaultXmlProvider() { return defaultXmlProvider; }
    public boolean isRmaEnabled() { return rmaEnabled; }
    public List<ClinicNbrEntry> getClinicNbrs() { return clinicNbrs; }
    public String getSelectedClinicNbrPrefix() { return selectedClinicNbrPrefix; }
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
    public List<BillingHistoryRow> getBillingHistoryRows() { return billingHistoryRows; }
    public List<FacilityNumOption> getFacilityNumOptions() { return facilityNumOptions; }
    public String getDefaultLocation() { return defaultLocation; }
    public List<BillingFavouriteOption> getBillingFavouriteOptions() { return billingFavouriteOptions; }
    public boolean isLegacySiteContextEnabled() { return legacySiteContextEnabled; }
    public List<LegacySiteOption> getLegacySiteOptions() { return legacySiteOptions; }
    public String getAdmissionDate() { return admissionDate; }
    public String getDefaultXmlVdate() { return defaultXmlVdate; }
    public String getSelectedBillType() { return selectedBillType; }
    public String getAssgProviderDisplay() { return assgProviderDisplay; }
    public String getReferralCheckedDefault() { return referralCheckedDefault; }
    public String getReferralNameDefault() { return referralNameDefault; }
    public String getReferralNoDefault() { return referralNoDefault; }
    public String getDxCodeDefault() { return dxCodeDefault; }
    public String getServiceDateDefault() { return serviceDateDefault; }
    public String getDisplayMessage() { return displayMessage; }
    public String getPrimaryCareIncentive() { return primaryCareIncentive; }
    public String getDefaultView() { return defaultView; }
    public String getDemoNameUrlEncoded() { return demoNameUrlEncoded; }
    public String getSelectedXmlProvider() { return selectedXmlProvider; }
    public Map<String, String> getMultisiteProviderHtml() { return multisiteProviderHtml; }
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }
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
        private List<BillingHistoryRow> billingHistoryRows;
        private List<FacilityNumOption> facilityNumOptions;
        private String defaultLocation;
        private List<BillingFavouriteOption> billingFavouriteOptions;
        private boolean legacySiteContextEnabled;
        private List<LegacySiteOption> legacySiteOptions;
        private String admissionDate;
        private String defaultXmlVdate;
        private String selectedBillType;
        private String assgProviderDisplay;
        private String referralCheckedDefault;
        private String referralNameDefault;
        private String referralNoDefault;
        private String dxCodeDefault;
        private String serviceDateDefault;
        private String displayMessage;
        private String primaryCareIncentive;
        private String defaultView;
        private String demoNameUrlEncoded;
        private String selectedXmlProvider;
        private Map<String, String> multisiteProviderHtml;
        private Map<String, String> requestParamEchoes;
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

        private boolean multisiteEnabled;
        private List<MultisiteSite> multisiteSites;
        private String defaultSelectedSite;
        private String defaultXmlProvider;
        private boolean rmaEnabled;
        private List<ClinicNbrEntry> clinicNbrs;
        private String selectedClinicNbrPrefix;
        public Builder multisiteEnabled(boolean v) { this.multisiteEnabled = v; return this; }
        public Builder multisiteSites(List<MultisiteSite> v) {
            this.multisiteSites = v == null ? null : List.copyOf(v); return this;
        }
        public Builder defaultSelectedSite(String v) { this.defaultSelectedSite = v; return this; }
        public Builder defaultXmlProvider(String v) { this.defaultXmlProvider = v; return this; }
        public Builder rmaEnabled(boolean v) { this.rmaEnabled = v; return this; }
        public Builder clinicNbrs(List<ClinicNbrEntry> v) {
            this.clinicNbrs = v == null ? null : List.copyOf(v); return this;
        }
        public Builder selectedClinicNbrPrefix(String v) { this.selectedClinicNbrPrefix = v; return this; }
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
        public Builder billingHistoryRows(List<BillingHistoryRow> v) { this.billingHistoryRows = v == null ? null : List.copyOf(v); return this; }
        public Builder facilityNumOptions(List<FacilityNumOption> v) { this.facilityNumOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder defaultLocation(String v) { this.defaultLocation = v; return this; }
        public Builder billingFavouriteOptions(List<BillingFavouriteOption> v) { this.billingFavouriteOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder legacySiteContextEnabled(boolean v) { this.legacySiteContextEnabled = v; return this; }
        public Builder legacySiteOptions(List<LegacySiteOption> v) { this.legacySiteOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder admissionDate(String v) { this.admissionDate = v; return this; }
        public Builder defaultXmlVdate(String v) { this.defaultXmlVdate = v; return this; }
        public Builder selectedBillType(String v) { this.selectedBillType = v; return this; }
        public Builder assgProviderDisplay(String v) { this.assgProviderDisplay = v; return this; }
        public Builder referralCheckedDefault(String v) { this.referralCheckedDefault = v; return this; }
        public Builder referralNameDefault(String v) { this.referralNameDefault = v; return this; }
        public Builder referralNoDefault(String v) { this.referralNoDefault = v; return this; }
        public Builder dxCodeDefault(String v) { this.dxCodeDefault = v; return this; }
        public Builder serviceDateDefault(String v) { this.serviceDateDefault = v; return this; }
        public Builder displayMessage(String v) { this.displayMessage = v; return this; }
        public Builder primaryCareIncentive(String v) { this.primaryCareIncentive = v; return this; }
        public Builder defaultView(String v) { this.defaultView = v; return this; }
        public Builder demoNameUrlEncoded(String v) { this.demoNameUrlEncoded = v; return this; }
        public Builder selectedXmlProvider(String v) { this.selectedXmlProvider = v; return this; }
        public Builder multisiteProviderHtml(Map<String, String> v) { this.multisiteProviderHtml = v == null ? null : Map.copyOf(v); return this; }
        public Builder requestParamEchoes(Map<String, String> v) { this.requestParamEchoes = v == null ? null : Map.copyOf(v); return this; }
        public Builder requestEchoes(Map<String, String> v) { this.requestEchoes = v == null ? null : Map.copyOf(v); return this; }

        public BillingONFormViewModel build() {
            return new BillingONFormViewModel(this);
        }

        // Package-private peek accessors so the assembler can read fields it
        // (or a composer) wrote earlier in the build pipeline without
        // duplicating local state. Used to resolve dependent precomputed
        // fields such as displayMessage, selectedBillType, dxCodeDefault, and
        // referral defaults that depend on values produced by upstream
        // composers (DemographicLoader, BillFormResolver).
        public String peekDefaultBillType() { return defaultBillType; }
        public String peekDefaultServiceType() { return defaultServiceType; }
        public String peekReferralDoctor() { return referralDoctor; }
        public String peekReferralDoctorOhip() { return referralDoctorOhip; }
        public String peekDxCode() { return dxCode; }
        public String peekErrorMsg() { return errorMsg; }
        public String peekWarningMsg() { return warningMsg; }
        public boolean peekDemoDobInvalid() { return demoDobInvalid; }
        public String peekDefaultXmlProvider() { return defaultXmlProvider; }
        public List<MultisiteSite> peekMultisiteSites() {
            return multisiteSites == null ? Collections.emptyList() : multisiteSites;
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
