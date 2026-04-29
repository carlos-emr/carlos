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
import java.util.Map;
import java.util.Set;

/**
 * Immutable view model for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Assembled by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONFormViewModelAssembler}
 * via {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingON2Action}
 * and exposed to the JSP as request attribute {@code formModel}. Carries the
 * provider/demographic context, dx/service-code grids, billing history, and
 * validation banner state the form needs to render — replacing ~24 DAO
 * lookups that previously ran inline in the JSP and pushed the rendered
 * response past the 1 MB page buffer.</p>
 *
 * <p>Internally the cross-cutting clusters are stored as composed records so
 * the page model is a small set of structured slices rather than a flat
 * compatibility bag. Flat getters remain as one-line delegations for older
 * Java callers while {@code billingON.jsp} consumes the composed records
 * directly.</p>
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
     * JSP used to iterate {@code BillingONClaimLoader.getBillingHist}.
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
     * {@link io.github.carlos_emr.carlos.billings.ca.on.service.BillingSiteIdService}.
     */
    public record LegacySiteOption(String name, boolean suggested) { }

    // ---- Primary composed records ----
    private final BillingDemographicSummary demographic;
    private final BillingReferralDoctor referral;
    private final BillingValidationMessages messages;
    private final BillingMultisiteContext multisite;
    private final BillingServiceCodeGrid serviceGrid;
    private final BillingBillFormSelector billForm;
    private final BillingONFormRequestContext requestContext;
    private final BillingONPatientPresentation patient;
    private final BillingONProviderPresentation providerPanel;
    private final BillingONVisitPresentation visit;
    private final BillingONLookupPresentation lookupData;
    private final BillingONDisplayPresentation display;
    private final BillingONReferralDefaults referralDefaults;

    private BillingONFormViewModel(Builder b) {
        // Resolve composed records first — either the composed-setter wins or
        // we synthesize from the legacy flat-field accumulators.
        this.demographic = (b.demographic != null)
                ? b.demographic
                : new BillingDemographicSummary(
                        b.demoFirst, b.demoLast, b.demoHin, b.demoVer,
                        b.demoSex, b.demoHcType, b.demoDob,
                        b.demoDobYear, b.demoDobMonth, b.demoDobDay);
        this.referral = (b.referral != null)
                ? b.referral
                : new BillingReferralDoctor(
                        b.referralDoctor, b.referralDoctorOhip, b.referralSpecialty);
        this.messages = (b.messages != null)
                ? b.messages
                : new BillingValidationMessages(b.errorFlag, b.errorMsg, b.warningMsg);
        this.multisite = (b.multisite != null)
                ? b.multisite
                : new BillingMultisiteContext(
                        b.multisiteEnabled,
                        b.multisiteSites,
                        b.defaultSelectedSite,
                        b.defaultXmlProvider,
                        b.selectedXmlProvider,
                        b.multisiteProviderHtml,
                        b.rmaEnabled,
                        b.clinicNbrs,
                        b.selectedClinicNbrPrefix);
        this.serviceGrid = (b.serviceGrid != null)
                ? b.serviceGrid
                : new BillingServiceCodeGrid(
                        b.listServiceType,
                        b.billingServiceCodesMap,
                        b.titleMap,
                        b.premiumCodes,
                        b.dxCodesByServiceType);
        this.billForm = (b.billForm != null)
                ? b.billForm
                : new BillingBillFormSelector(
                        b.defaultBillFormName,
                        b.defaultBillType,
                        b.defaultServiceType,
                        b.billingForms,
                        b.selectedBillType);
        this.requestContext = (b.requestContext != null)
                ? b.requestContext
                : new BillingONFormRequestContext(
                        b.userNo, b.demographicNo, b.appointmentNo,
                        b.providerNo, b.apptProviderNo, b.demoName, b.today,
                        b.billReferenceDate, b.mReview, b.ctlBillForm,
                        b.curBillForm, b.demoNameUrlEncoded, b.requestParamEchoes);
        this.patient = (b.patient != null)
                ? b.patient
                : new BillingONPatientPresentation(
                        b.demoDobInvalid, b.familyDoctor, b.rosterStatus,
                        b.age, b.patientDx, b.patientDxAddCode,
                        b.patientDxMatchCode, b.billingRecommendations,
                        b.billingHistory, b.billingHistoryRows);
        this.providerPanel = (b.providerPanel != null)
                ? b.providerPanel
                : new BillingONProviderPresentation(
                        b.providerView, b.assgProviderNo,
                        b.assgProviderDisplay, b.providers);
        this.visit = (b.visit != null)
                ? b.visit
                : new BillingONVisitPresentation(
                        b.clinicView, b.clinicNo, b.visitType,
                        b.singleClickEnabled, b.hospitalBilling, b.dxCode,
                        b.xmlVisitType, b.xmlLocation, b.visitDate,
                        b.defaultLocation, b.admissionDate, b.defaultXmlVdate,
                        b.dxCodeDefault, b.serviceDateDefault);
        this.lookupData = (b.lookupData != null)
                ? b.lookupData
                : new BillingONLookupPresentation(
                        b.billingFavourites, b.billingFavouriteOptions,
                        b.facilityNumOptions, b.legacySiteContextEnabled,
                        b.legacySiteOptions);
        this.display = (b.display != null)
                ? b.display
                : new BillingONDisplayPresentation(
                        b.displayMessage, b.primaryCareIncentive, b.defaultView);
        this.referralDefaults = (b.referralDefaults != null)
                ? b.referralDefaults
                : new BillingONReferralDefaults(
                        b.referralCheckedDefault,
                        b.referralNameDefault,
                        b.referralNoDefault);
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

    // ---- composed-record getters (preferred) ----

    /** Aggregated demographic snapshot — primary internal storage. */
    public BillingDemographicSummary getDemographic() { return demographic; }

    /** Aggregated referral-doctor record — primary internal storage. */
    public BillingReferralDoctor getReferral() { return referral; }

    /** Aggregated validation banner state — primary internal storage. */
    public BillingValidationMessages getMessages() { return messages; }

    /** Aggregated multisite + RMA / clinic-nbr context — primary internal storage. */
    public BillingMultisiteContext getMultisite() { return multisite; }

    /** Aggregated service-code grid — primary internal storage. */
    public BillingServiceCodeGrid getServiceGrid() { return serviceGrid; }

    /** Aggregated bill-form selector — primary internal storage. */
    public BillingBillFormSelector getBillForm() { return billForm; }

    /** Aggregated identity/request context — primary internal storage. */
    public BillingONFormRequestContext getRequestContext() { return requestContext; }

    /** Aggregated patient presentation state — primary internal storage. */
    public BillingONPatientPresentation getPatient() { return patient; }

    /** Aggregated provider picker/display state — primary internal storage. */
    public BillingONProviderPresentation getProviderPanel() { return providerPanel; }

    /** Aggregated visit/default-input state — primary internal storage. */
    public BillingONVisitPresentation getVisit() { return visit; }

    /** Aggregated lookup/dropdown state — primary internal storage. */
    public BillingONLookupPresentation getLookupData() { return lookupData; }

    /** Aggregated pre-rendered display state — primary internal storage. */
    public BillingONDisplayPresentation getDisplay() { return display; }

    /** Aggregated referral input defaults — primary internal storage. */
    public BillingONReferralDefaults getReferralDefaults() { return referralDefaults; }

    // ---- legacy aggregator aliases (retain for back-compat with earlier tests) ----

    /** Alias of {@link #getDemographic()} (legacy). */
    public BillingDemographicSummary getDemographicSummary() { return demographic; }

    /** Alias of {@link #getReferral()} (legacy). */
    public BillingReferralDoctor getReferralDoctorRecord() { return referral; }

    /** Alias of {@link #getMessages()} (legacy). */
    public BillingValidationMessages getValidationMessages() { return messages; }

    // ---- Identity / context ----

    public String getUserNo() { return requestContext.userNo(); }
    public String getDemographicNo() { return requestContext.demographicNo(); }
    public String getAppointmentNo() { return requestContext.appointmentNo(); }
    public String getProviderNo() { return requestContext.providerNo(); }
    public String getApptProviderNo() { return requestContext.apptProviderNo(); }
    public String getProviderView() { return providerPanel.providerView(); }
    public String getDemoName() { return requestContext.demoName(); }
    public String getToday() { return requestContext.today(); }
    public String getBillReferenceDate() { return requestContext.billReferenceDate(); }
    public String getMReview() { return requestContext.mReview(); }
    public String getCtlBillForm() { return requestContext.ctlBillForm(); }
    public String getCurBillForm() { return requestContext.curBillForm(); }

    // ---- Demographic flat getters (delegate to composed record) ----

    public String getDemoLast() { return demographic.lastName(); }
    public String getDemoFirst() { return demographic.firstName(); }
    public String getDemoHin() { return demographic.hin(); }
    public String getDemoVer() { return demographic.ver(); }
    public String getDemoDob() { return demographic.dob(); }
    public String getDemoDobYear() { return demographic.dobYy(); }
    public String getDemoDobMonth() { return demographic.dobMm(); }
    public String getDemoDobDay() { return demographic.dobDd(); }
    public boolean isDemoDobInvalid() { return patient.demoDobInvalid(); }
    public String getDemoHcType() { return demographic.hcType(); }
    public String getDemoSex() { return demographic.sex(); }
    public String getFamilyDoctor() { return patient.familyDoctor(); }
    public String getRosterStatus() { return patient.rosterStatus(); }
    public String getAssgProviderNo() { return providerPanel.assgProviderNo(); }
    public int getAge() { return patient.age(); }

    // ---- Referral flat getters (delegate) ----

    public String getReferralDoctor() { return referral.name(); }
    public String getReferralDoctorOhip() { return referral.ohip(); }
    public String getReferralSpecialty() { return referral.specialty(); }

    public List<String> getPatientDx() { return patient.patientDx(); }
    public String getPatientDxAddCode() { return patient.patientDxAddCode(); }
    public String getPatientDxMatchCode() { return patient.patientDxMatchCode(); }

    // ---- Multisite flat getters (delegate) ----

    public boolean isMultisiteEnabled() { return multisite.enabled(); }
    public List<BillingMultisiteContext.MultisiteSite> getMultisiteSites() { return multisite.sites(); }
    public String getDefaultSelectedSite() { return multisite.defaultSelectedSite(); }
    public String getDefaultXmlProvider() { return multisite.defaultXmlProvider(); }
    public boolean isRmaEnabled() { return multisite.rmaEnabled(); }
    public List<BillingMultisiteContext.ClinicNbrEntry> getClinicNbrs() { return multisite.clinicNbrs(); }
    public String getSelectedClinicNbrPrefix() { return multisite.selectedClinicNbrPrefix(); }
    public String getSelectedXmlProvider() { return multisite.selectedXmlProvider(); }
    public Map<String, String> getMultisiteProviderHtml() { return multisite.multisiteProviderHtml(); }

    public String getBillingRecommendations() { return patient.billingRecommendations(); }
    public List<BillingHistoryEntry> getBillingHistory() { return patient.billingHistory(); }

    // ---- Validation messages flat getters (delegate) ----

    public String getWarningMsg() { return messages.warningMessage(); }
    public String getErrorMsg() { return messages.errorMessage(); }
    public String getErrorFlag() { return messages.errorFlag(); }

    public String getClinicView() { return visit.clinicView(); }
    public String getClinicNo() { return visit.clinicNo(); }
    public String getVisitType() { return visit.visitType(); }
    public boolean isSingleClickEnabled() { return visit.singleClickEnabled(); }
    public boolean isHospitalBilling() { return visit.hospitalBilling(); }
    public List<ProviderOption> getProviders() { return providerPanel.providers(); }

    // ---- Bill-form flat getters (delegate) ----

    public String getDefaultServiceType() { return billForm.defaultServiceType(); }
    public String getDefaultBillFormName() { return billForm.defaultFormName(); }
    public String getDefaultBillType() { return billForm.defaultBillType(); }
    public List<BillingFormMenuEntry> getBillingForms() { return billForm.forms(); }
    public String getSelectedBillType() { return billForm.selectedBillType(); }

    public String getDxCode() { return visit.dxCode(); }
    public String getXmlVisitType() { return visit.xmlVisitType(); }
    public String getXmlLocation() { return visit.xmlLocation(); }
    public String getVisitDate() { return visit.visitDate(); }

    // ---- Service-grid flat getters (delegate) ----

    public Map<String, List<ServiceCodeEntry>> getBillingServiceCodesMap() { return serviceGrid.codesByServiceType(); }
    public List<String> getListServiceType() { return serviceGrid.serviceTypes(); }
    public Map<String, String> getTitleMap() { return serviceGrid.titlesByServiceType(); }
    public Set<String> getPremiumCodes() { return serviceGrid.premiumCodes(); }
    public Map<String, List<DxCodeEntry>> getDxCodesByServiceType() { return serviceGrid.dxCodesByServiceType(); }

    public List<String> getBillingFavourites() { return lookupData.billingFavourites(); }
    public List<BillingHistoryRow> getBillingHistoryRows() { return patient.billingHistoryRows(); }
    public List<FacilityNumOption> getFacilityNumOptions() { return lookupData.facilityNumOptions(); }
    public String getDefaultLocation() { return visit.defaultLocation(); }
    public List<BillingFavouriteOption> getBillingFavouriteOptions() { return lookupData.billingFavouriteOptions(); }
    public boolean isLegacySiteContextEnabled() { return lookupData.legacySiteContextEnabled(); }
    public List<LegacySiteOption> getLegacySiteOptions() { return lookupData.legacySiteOptions(); }
    public String getAdmissionDate() { return visit.admissionDate(); }
    public String getDefaultXmlVdate() { return visit.defaultXmlVdate(); }
    public String getAssgProviderDisplay() { return providerPanel.assgProviderDisplay(); }
    public String getReferralCheckedDefault() { return referralDefaults.checkedDefault(); }
    public String getReferralNameDefault() { return referralDefaults.nameDefault(); }
    public String getReferralNoDefault() { return referralDefaults.noDefault(); }
    public String getDxCodeDefault() { return visit.dxCodeDefault(); }
    public String getServiceDateDefault() { return visit.serviceDateDefault(); }
    public String getDisplayMessage() { return display.displayMessage(); }
    public String getPrimaryCareIncentive() { return display.primaryCareIncentive(); }
    public String getDefaultView() { return display.defaultView(); }
    public String getDemoNameUrlEncoded() { return requestContext.demoNameUrlEncoded(); }
    public Map<String, String> getRequestParamEchoes() { return requestContext.requestParamEchoes(); }

    public static final class Builder {
        // ---- Composed-record setters (preferred) ----
        private BillingDemographicSummary demographic;
        private BillingReferralDoctor referral;
        private BillingValidationMessages messages;
        private BillingMultisiteContext multisite;
        private BillingServiceCodeGrid serviceGrid;
        private BillingBillFormSelector billForm;
        private BillingONFormRequestContext requestContext;
        private BillingONPatientPresentation patient;
        private BillingONProviderPresentation providerPanel;
        private BillingONVisitPresentation visit;
        private BillingONLookupPresentation lookupData;
        private BillingONDisplayPresentation display;
        private BillingONReferralDefaults referralDefaults;

        // ---- Legacy flat-field accumulators ----
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
        private Map<String, String> requestParamEchoes;

        // Multisite + RMA flat accumulators
        private boolean multisiteEnabled;
        private List<BillingMultisiteContext.MultisiteSite> multisiteSites;
        private String defaultSelectedSite;
        private String defaultXmlProvider;
        private boolean rmaEnabled;
        private List<BillingMultisiteContext.ClinicNbrEntry> clinicNbrs;
        private String selectedClinicNbrPrefix;
        private String selectedXmlProvider;
        private Map<String, String> multisiteProviderHtml;

        // ---- Composed-record setters ----
        public Builder demographic(BillingDemographicSummary v) { this.demographic = v; return this; }
        public Builder referral(BillingReferralDoctor v) { this.referral = v; return this; }
        public Builder messages(BillingValidationMessages v) { this.messages = v; return this; }
        public Builder multisite(BillingMultisiteContext v) { this.multisite = v; return this; }
        public Builder serviceGrid(BillingServiceCodeGrid v) { this.serviceGrid = v; return this; }
        public Builder billForm(BillingBillFormSelector v) { this.billForm = v; return this; }
        public Builder requestContext(BillingONFormRequestContext v) { this.requestContext = v; return this; }
        public Builder patient(BillingONPatientPresentation v) { this.patient = v; return this; }
        public Builder providerPanel(BillingONProviderPresentation v) { this.providerPanel = v; return this; }
        public Builder visit(BillingONVisitPresentation v) { this.visit = v; return this; }
        public Builder lookupData(BillingONLookupPresentation v) { this.lookupData = v; return this; }
        public Builder display(BillingONDisplayPresentation v) { this.display = v; return this; }
        public Builder referralDefaults(BillingONReferralDefaults v) { this.referralDefaults = v; return this; }

        // ---- Legacy flat setters (back-compat — accumulate for build()) ----

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

        public Builder multisiteEnabled(boolean v) { this.multisiteEnabled = v; return this; }
        public Builder multisiteSites(List<BillingMultisiteContext.MultisiteSite> v) {
            this.multisiteSites = v == null ? null : List.copyOf(v); return this;
        }
        public Builder defaultSelectedSite(String v) { this.defaultSelectedSite = v; return this; }
        public Builder defaultXmlProvider(String v) { this.defaultXmlProvider = v; return this; }
        public Builder rmaEnabled(boolean v) { this.rmaEnabled = v; return this; }
        public Builder clinicNbrs(List<BillingMultisiteContext.ClinicNbrEntry> v) {
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
        public List<BillingMultisiteContext.MultisiteSite> peekMultisiteSites() {
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
