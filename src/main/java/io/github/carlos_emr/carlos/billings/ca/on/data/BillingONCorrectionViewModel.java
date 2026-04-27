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
 * Immutable view model for {@code billingONCorrection.jsp}.
 *
 * <p>Captures the user-context lookups (provider record, site/team-access flags,
 * provider-access list, multisite list) that the JSP top scriptlet currently
 * builds inline. Bill-record-specific data (the Ch1 invoice + items the
 * correction page actually edits) still flows through
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingCorrection2Action}'s
 * existing state machine and is not duplicated here.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONCorrectionDataAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingCorrection2Action})
 * and exposed to the JSP as request attribute {@code correctionModel}.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONCorrectionViewModel {

    private final String userProviderNo;
    private final String userFirstName;
    private final String userLastName;

    private final boolean siteAccessPrivacy;
    private final boolean teamAccessPrivacy;
    private final boolean teamBillingOnly;
    private final boolean multisites;

    private final Set<String> providerAccessList;
    private final List<String> mgrSites;

    // Bill-record state, populated when billing_no or claim_no resolves
    // through bCh1Dao.find. {@link #isBillLoaded()} reflects whether the
    // load succeeded; {@link #isBillNoErr()} reflects the user-visible
    // "Invoice number does not exist!" alert. Bill-record fields read from
    // the loaded {@code BillingONCHeader1} + Demographic + ProfessionalSpecialist.
    private final boolean billLoaded;
    private final boolean billNoErr;
    private final boolean multiSiteProvider;
    // True when the demographic-by-id lookup threw — JSP shows a banner so
    // the operator doesn't act on the (empty) patient context as if it were
    // authoritative.
    /** True when the bill row's admission_date column was non-empty but
     *  failed to parse. Sibling of {@link #demoLoadError} /
     *  {@link #raLookupError}; the JSP can render a banner when set. */
    private final boolean visitDateInvalid;
    private final boolean demoLoadError;
    // True when the OHIP RA claim-number lookup threw — JSP shows a hint
    // since the operator's primary correlation key for ministry remittance
    // is silently absent.
    private final boolean raLookupError;
    private final String billingNo;
    private final String claimNo;
    private final String createTimestamp;
    private final String demoNo;
    private final String demoName;
    private final String demoDob;
    private final String demoSex;
    private final String demoRosterStatus;
    private final String hin;
    private final String hcType;
    private final String hcSex;
    private final String billLocationNo;
    private final String billDate;
    private final String billProvider;
    private final String billStatus;
    private final String payProgram;
    private final String billTotal;
    private final String visitDate;
    private final String visitType;
    private final String sliCode;
    private final String referralDoctorOhip;
    private final String referralDoctor;
    private final String manReview;
    private final String comment;
    private final String clinicSite;

    // Render-time fields populated by BillingONCorrectionRenderComposer.
    // These remove the 11 inline SpringUtils.getBean lookups that used to live
    // in the JSP body (BillingServiceDao / BillingONInvoiceTotalsCalculator / BillingONExtDao /
    // BillingONPaymentDao / BillingONEAReportDao / BillingONErrorCodeDao /
    // RaDetailDao / ClinicLocationDao / ClinicNbrDao / SecurityInfoManager).
    private final boolean thirdParty;
    private final String htmlPaid;
    private final String payer;
    private final boolean dueDateAvailable;
    private final String dueDateString;
    private final boolean useDemoContactAvailable;
    private final boolean useDemoContactChecked;
    private final boolean canEditBilling;
    private final String clinicNo;
    private final boolean rmaEnabled;
    private final List<ClinicLocationEntry> clinicLocations;
    private final List<BillingMultisiteContext.ClinicNbrEntry> clinicNbrs;
    private final List<BillItemEntry> billItems;
    private final List<ErrorReportEntry> errorReportEntries;
    // Multisite picker context for the JSP. The legacy scriptlet looped
    // SiteDao + Provider lists inline; the assembler now supplies the
    // pre-resolved {@link BillingMultisiteContext} record carrying the site
    // list and the name->HTML map of <option> fragments mirroring billingON.jsp.
    private final BillingMultisiteContext multisite;
    // Non-multisite provider dropdown — pre-resolved triples of
    // {providerNo, firstName, lastName} from BillingONLookupService so the JSP
    // can render with c:forEach.
    private final List<ProviderOption> providerOptions;
    // BillingDataHlp.vecPaymentType pairs (code + label) so the JSP renders
    // <c:forEach var="pt" items="${correctionModel.paymentTypes}"> rather
    // than reading the static ArrayList via a scriptlet.
    private final List<PaymentTypeEntry> paymentTypes;
    // Echo of select request parameters (e.g. ?adminSubmit, ?admin) so the
    // JSP can branch on them without referencing ${param.X} (the encoder
    // validator treats raw param refs as XSS even inside <c:out>).
    private final Map<String, String> requestParamEchoes;
    // Resolved current site value: ?site param when present, else the bill
    // record's clinicSite. Used for the <option selected> attribute on the
    // multisite site picker.
    private final String currentSite;

    public record ClinicLocationEntry(String no, String name) {}
    public record BillItemEntry(String serviceCode, String serviceDesc,
                                String fee, String dx, String count, String status) {}
    public record ErrorReportEntry(String code, String description) {}
    /** A row in the non-multisite provider dropdown. */
    public record ProviderOption(String providerNo, String firstName, String lastName) {}
    /** Pay-program code + display label for the payProgram dropdown. */
    public record PaymentTypeEntry(String code, String label) {}

    private BillingONCorrectionViewModel(Builder b) {
        this.userProviderNo = nullToEmpty(b.userProviderNo);
        this.userFirstName = nullToEmpty(b.userFirstName);
        this.userLastName = nullToEmpty(b.userLastName);
        this.siteAccessPrivacy = b.siteAccessPrivacy;
        this.teamAccessPrivacy = b.teamAccessPrivacy;
        this.teamBillingOnly = b.teamBillingOnly;
        this.multisites = b.multisites;
        this.providerAccessList = b.providerAccessList == null
                ? Collections.emptySet()
                : Set.copyOf(b.providerAccessList);
        this.mgrSites = b.mgrSites == null
                ? Collections.emptyList()
                : List.copyOf(b.mgrSites);
        this.billLoaded = b.billLoaded;
        this.billNoErr = b.billNoErr;
        this.multiSiteProvider = b.multiSiteProvider;
        this.visitDateInvalid = b.visitDateInvalid;
        this.demoLoadError = b.demoLoadError;
        this.raLookupError = b.raLookupError;
        this.billingNo = nullToEmpty(b.billingNo);
        this.claimNo = nullToEmpty(b.claimNo);
        this.createTimestamp = nullToEmpty(b.createTimestamp);
        this.demoNo = nullToEmpty(b.demoNo);
        this.demoName = nullToEmpty(b.demoName);
        this.demoDob = nullToEmpty(b.demoDob);
        this.demoSex = nullToEmpty(b.demoSex);
        this.demoRosterStatus = nullToEmpty(b.demoRosterStatus);
        this.hin = nullToEmpty(b.hin);
        this.hcType = nullToEmpty(b.hcType);
        this.hcSex = nullToEmpty(b.hcSex);
        this.billLocationNo = nullToEmpty(b.billLocationNo);
        this.billDate = nullToEmpty(b.billDate);
        this.billProvider = nullToEmpty(b.billProvider);
        this.billStatus = nullToEmpty(b.billStatus);
        this.payProgram = nullToEmpty(b.payProgram);
        this.billTotal = nullToEmpty(b.billTotal);
        this.visitDate = nullToEmpty(b.visitDate);
        this.visitType = nullToEmpty(b.visitType);
        this.sliCode = nullToEmpty(b.sliCode);
        this.referralDoctorOhip = nullToEmpty(b.referralDoctorOhip);
        this.referralDoctor = nullToEmpty(b.referralDoctor);
        this.manReview = nullToEmpty(b.manReview);
        this.comment = nullToEmpty(b.comment);
        this.clinicSite = nullToEmpty(b.clinicSite);
        this.thirdParty = b.thirdParty;
        this.htmlPaid = nullToEmpty(b.htmlPaid);
        this.payer = nullToEmpty(b.payer);
        this.dueDateAvailable = b.dueDateAvailable;
        this.dueDateString = nullToEmpty(b.dueDateString);
        this.useDemoContactAvailable = b.useDemoContactAvailable;
        this.useDemoContactChecked = b.useDemoContactChecked;
        this.canEditBilling = b.canEditBilling;
        this.clinicNo = nullToEmpty(b.clinicNo);
        this.rmaEnabled = b.rmaEnabled;
        this.clinicLocations = b.clinicLocations == null
                ? Collections.emptyList() : List.copyOf(b.clinicLocations);
        this.clinicNbrs = b.clinicNbrs == null
                ? Collections.emptyList() : List.copyOf(b.clinicNbrs);
        this.billItems = b.billItems == null
                ? Collections.emptyList() : List.copyOf(b.billItems);
        this.errorReportEntries = b.errorReportEntries == null
                ? Collections.emptyList() : List.copyOf(b.errorReportEntries);
        // Correction's multisite scope is sites + multisiteProviderHtml only.
        // Other slices are populated as empty defaults.
        this.multisite = (b.multisite != null)
                ? b.multisite
                : new BillingMultisiteContext(
                        b.multisites,
                        b.multisiteSites,
                        "", "", "",
                        b.multisiteProviderHtml,
                        b.rmaEnabled,
                        Collections.emptyList(),
                        "");
        this.providerOptions = b.providerOptions == null
                ? Collections.emptyList() : List.copyOf(b.providerOptions);
        this.paymentTypes = b.paymentTypes == null
                ? Collections.emptyList() : List.copyOf(b.paymentTypes);
        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
        this.currentSite = nullToEmpty(b.currentSite);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserProviderNo() { return userProviderNo; }
    public String getUserFirstName() { return userFirstName; }
    public String getUserLastName() { return userLastName; }
    public boolean isSiteAccessPrivacy() { return siteAccessPrivacy; }
    public boolean isTeamAccessPrivacy() { return teamAccessPrivacy; }
    public boolean isTeamBillingOnly() { return teamBillingOnly; }
    public boolean isMultisites() { return multisites; }
    public Set<String> getProviderAccessList() { return providerAccessList; }
    public List<String> getMgrSites() { return mgrSites; }

    public boolean isBillLoaded() { return billLoaded; }
    public boolean isBillNoErr() { return billNoErr; }
    public boolean isMultiSiteProvider() { return multiSiteProvider; }
    public boolean isVisitDateInvalid() { return visitDateInvalid; }
    public boolean isDemoLoadError() { return demoLoadError; }
    public boolean isRaLookupError() { return raLookupError; }
    public String getBillingNo() { return billingNo; }
    public String getClaimNo() { return claimNo; }
    public String getCreateTimestamp() { return createTimestamp; }
    public String getDemoNo() { return demoNo; }
    public String getDemoName() { return demoName; }
    public String getDemoDob() { return demoDob; }
    public String getDemoSex() { return demoSex; }
    public String getDemoRosterStatus() { return demoRosterStatus; }
    public String getHin() { return hin; }
    public String getHcType() { return hcType; }
    public String getHcSex() { return hcSex; }
    public String getBillLocationNo() { return billLocationNo; }
    public String getBillDate() { return billDate; }
    public String getBillProvider() { return billProvider; }
    public String getBillStatus() { return billStatus; }
    public String getPayProgram() { return payProgram; }
    public String getBillTotal() { return billTotal; }
    public String getVisitDate() { return visitDate; }
    public String getVisitType() { return visitType; }
    public String getSliCode() { return sliCode; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getReferralDoctor() { return referralDoctor; }
    public String getManReview() { return manReview; }
    public String getComment() { return comment; }
    public String getClinicSite() { return clinicSite; }

    public boolean isThirdParty() { return thirdParty; }
    public String getHtmlPaid() { return htmlPaid; }
    public String getPayer() { return payer; }
    public boolean isDueDateAvailable() { return dueDateAvailable; }
    public String getDueDateString() { return dueDateString; }
    public boolean isUseDemoContactAvailable() { return useDemoContactAvailable; }
    public boolean isUseDemoContactChecked() { return useDemoContactChecked; }
    public boolean isCanEditBilling() { return canEditBilling; }
    public String getClinicNo() { return clinicNo; }
    public boolean isRmaEnabled() { return rmaEnabled; }
    public List<ClinicLocationEntry> getClinicLocations() { return clinicLocations; }
    public List<BillingMultisiteContext.ClinicNbrEntry> getClinicNbrs() { return clinicNbrs; }
    public List<BillItemEntry> getBillItems() { return billItems; }
    public List<ErrorReportEntry> getErrorReportEntries() { return errorReportEntries; }
    /** Aggregated multisite context — primary internal storage. */
    public BillingMultisiteContext getMultisite() { return multisite; }
    public List<BillingMultisiteContext.MultisiteSite> getMultisiteSites() { return multisite.sites(); }
    public Map<String, String> getMultisiteProviderHtml() { return multisite.multisiteProviderHtml(); }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }
    public List<PaymentTypeEntry> getPaymentTypes() { return paymentTypes; }
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }
    public String getCurrentSite() { return currentSite; }

    public static final class Builder {
        private String userProviderNo;
        private String userFirstName;
        private String userLastName;
        private boolean siteAccessPrivacy;
        private boolean teamAccessPrivacy;
        private boolean teamBillingOnly;
        private boolean multisites;
        private Set<String> providerAccessList;
        private List<String> mgrSites;
        private boolean billLoaded;
        private boolean billNoErr;
        // Fail-closed default. The assembler opts in to true via
        // multiSiteProvider(true) once the bill loads and access checks pass.
        // The JSP only consumes the value when correctionModel.isBillLoaded()
        // is true, so the empty-model fallback path renders harmlessly with
        // the false default — and any future code path that constructs an
        // empty model can't accidentally expose other-site bills.
        private boolean multiSiteProvider = false;
        private boolean visitDateInvalid;
        private boolean demoLoadError;
        private boolean raLookupError;
        private String billingNo;
        private String claimNo;
        private String createTimestamp;
        private String demoNo;
        private String demoName;
        private String demoDob;
        private String demoSex;
        private String demoRosterStatus;
        private String hin;
        private String hcType;
        private String hcSex;
        private String billLocationNo;
        private String billDate;
        private String billProvider;
        private String billStatus;
        private String payProgram;
        private String billTotal;
        private String visitDate;
        private String visitType;
        private String sliCode;
        private String referralDoctorOhip;
        private String referralDoctor;
        private String manReview;
        private String comment;
        private String clinicSite;
        private boolean thirdParty;
        private String htmlPaid;
        private String payer;
        private boolean dueDateAvailable;
        private String dueDateString;
        private boolean useDemoContactAvailable;
        private boolean useDemoContactChecked;
        private boolean canEditBilling;
        private String clinicNo;
        private boolean rmaEnabled;
        private List<ClinicLocationEntry> clinicLocations;
        private List<BillingMultisiteContext.ClinicNbrEntry> clinicNbrs;
        private List<BillItemEntry> billItems;
        private List<ErrorReportEntry> errorReportEntries;
        private BillingMultisiteContext multisite;
        private List<BillingMultisiteContext.MultisiteSite> multisiteSites;
        private Map<String, String> multisiteProviderHtml;
        private List<ProviderOption> providerOptions;
        private List<PaymentTypeEntry> paymentTypes;
        private Map<String, String> requestParamEchoes;
        private String currentSite;

        public Builder userProviderNo(String v) { this.userProviderNo = v; return this; }
        public Builder userFirstName(String v) { this.userFirstName = v; return this; }
        public Builder userLastName(String v) { this.userLastName = v; return this; }
        public Builder siteAccessPrivacy(boolean v) { this.siteAccessPrivacy = v; return this; }
        public Builder teamAccessPrivacy(boolean v) { this.teamAccessPrivacy = v; return this; }
        public Builder teamBillingOnly(boolean v) { this.teamBillingOnly = v; return this; }
        public Builder multisites(boolean v) { this.multisites = v; return this; }
        // Defensive copy at the setter so callers retaining the original
        // mutable Set/List can't influence builder state between setter and
        // build(). Matches BillingONFormViewModel's pattern; closes the
        // CodeQL "exposing internal representation" finding consistently
        // across the view-model family.
        public Builder providerAccessList(Set<String> v) { this.providerAccessList = v == null ? null : Set.copyOf(v); return this; }
        public Builder mgrSites(List<String> v) { this.mgrSites = v == null ? null : List.copyOf(v); return this; }
        public Builder billLoaded(boolean v) { this.billLoaded = v; return this; }
        public Builder billNoErr(boolean v) { this.billNoErr = v; return this; }
        public Builder multiSiteProvider(boolean v) { this.multiSiteProvider = v; return this; }
        public Builder visitDateInvalid(boolean v) { this.visitDateInvalid = v; return this; }
        public Builder demoLoadError(boolean v) { this.demoLoadError = v; return this; }
        public Builder raLookupError(boolean v) { this.raLookupError = v; return this; }
        public Builder billingNo(String v) { this.billingNo = v; return this; }
        public Builder claimNo(String v) { this.claimNo = v; return this; }
        public Builder createTimestamp(String v) { this.createTimestamp = v; return this; }
        public Builder demoNo(String v) { this.demoNo = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder demoRosterStatus(String v) { this.demoRosterStatus = v; return this; }
        public Builder hin(String v) { this.hin = v; return this; }
        public Builder hcType(String v) { this.hcType = v; return this; }
        public Builder hcSex(String v) { this.hcSex = v; return this; }
        public Builder billLocationNo(String v) { this.billLocationNo = v; return this; }
        public Builder billDate(String v) { this.billDate = v; return this; }
        public Builder billProvider(String v) { this.billProvider = v; return this; }
        public Builder billStatus(String v) { this.billStatus = v; return this; }
        public Builder payProgram(String v) { this.payProgram = v; return this; }
        public Builder billTotal(String v) { this.billTotal = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder sliCode(String v) { this.sliCode = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder referralDoctor(String v) { this.referralDoctor = v; return this; }
        public Builder manReview(String v) { this.manReview = v; return this; }
        public Builder comment(String v) { this.comment = v; return this; }
        public Builder clinicSite(String v) { this.clinicSite = v; return this; }
        public Builder thirdParty(boolean v) { this.thirdParty = v; return this; }
        public Builder htmlPaid(String v) { this.htmlPaid = v; return this; }
        public Builder payer(String v) { this.payer = v; return this; }
        public Builder dueDateAvailable(boolean v) { this.dueDateAvailable = v; return this; }
        public Builder dueDateString(String v) { this.dueDateString = v; return this; }
        public Builder useDemoContactAvailable(boolean v) { this.useDemoContactAvailable = v; return this; }
        public Builder useDemoContactChecked(boolean v) { this.useDemoContactChecked = v; return this; }
        public Builder canEditBilling(boolean v) { this.canEditBilling = v; return this; }
        public Builder clinicNo(String v) { this.clinicNo = v; return this; }
        public Builder rmaEnabled(boolean v) { this.rmaEnabled = v; return this; }
        // Defensive copy at the setter so callers retaining the original
        // mutable List can't influence builder state between setter and build().
        public Builder clinicLocations(List<ClinicLocationEntry> v) { this.clinicLocations = v == null ? null : List.copyOf(v); return this; }
        public Builder clinicNbrs(List<BillingMultisiteContext.ClinicNbrEntry> v) { this.clinicNbrs = v == null ? null : List.copyOf(v); return this; }
        public Builder billItems(List<BillItemEntry> v) { this.billItems = v == null ? null : List.copyOf(v); return this; }
        public Builder errorReportEntries(List<ErrorReportEntry> v) { this.errorReportEntries = v == null ? null : List.copyOf(v); return this; }
        /** Composed multisite-context setter (preferred over the slice-by-slice setters). */
        public Builder multisite(BillingMultisiteContext v) { this.multisite = v; return this; }
        public Builder multisiteSites(List<BillingMultisiteContext.MultisiteSite> v) { this.multisiteSites = v == null ? null : List.copyOf(v); return this; }
        public Builder multisiteProviderHtml(Map<String, String> v) { this.multisiteProviderHtml = v == null ? null : Map.copyOf(v); return this; }
        public Builder providerOptions(List<ProviderOption> v) { this.providerOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder paymentTypes(List<PaymentTypeEntry> v) { this.paymentTypes = v == null ? null : List.copyOf(v); return this; }
        public Builder requestParamEchoes(Map<String, String> v) { this.requestParamEchoes = v == null ? null : Map.copyOf(v); return this; }
        public Builder currentSite(String v) { this.currentSite = v; return this; }

        public BillingONCorrectionViewModel build() {
            return new BillingONCorrectionViewModel(this);
        }
    }
}
