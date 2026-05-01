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


/**
 * Immutable view model for {@code billingONReview.jsp}.
 *
 * <p>Captures demographic + provider lookups, the chosen diagnostic code, the
 * pre-rendered service-code / percent-code rows, the validation messages, and
 * all derived flags / request-param echoes the JSP needs. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnReviewViewModelAssembler}
 * and exposed to the JSP as request attribute {@code reviewModel}. Replaces
 * the legacy multi-block scriptlet that previously ran inline in the JSP.</p>
 *
 * @since 2026-04-24
 *        2026-04-25 (full body-scriptlet drain)
 */
public final class BillingOnReviewViewModel {

    // ---- Primary composed records ----
    private final BillingDemographicSummary demographic;
    private final BillingReferralDoctor referral;
    private final BillingValidationMessages messages;
    private final BillingMultisiteContext multisite;

    private final String patientAddress;
    private final String assignedProviderNo;

    private final String providerOhip;
    private final String providerRma;
    private final String providerView;

    private final String dxCode;
    private final String dxDesc;

    private final java.util.List<io.github.carlos_emr.carlos.billings.ca.on.validator.BillingOnReviewValidator.Message> validationMessages;
    private final boolean codeValid;
    /** True when at least one numeric input on this review (a code total or a
     *  GST percent) failed strict parsing and the assembler had to substitute
     *  zero. The Review JSP must banner-warn the provider so they know the
     *  GST/Total figures are understated and submission should be gated. */
    private final boolean totalsParseFailed;

    private final java.util.Map<String, ProviderName> providerNameLookup;

    private final java.util.Map<String, String> requestParamEchoes;
    private final String demoName;
    private final String wrongMessage;
    private final java.util.List<ReviewAlert> reviewAlerts;
    private final String demoSexLabel;
    private final String demoHeaderLine;
    private final boolean mReview;
    private final java.util.List<String> serviceDateLines;
    private final String billingPhysicianLabel;
    private final String mrpLabel;
    private final String visitTypeLabel;
    private final String billTypeLabel;
    private final String billType;
    private final String locationLabel;
    private final String sliCodeLabel;
    private final String admissionDate;
    private final String siteName;
    private final java.util.List<ServiceCodeRow> serviceCodeRows;
    private final java.util.List<PercCodeRow> percCodeRows;
    private final java.util.Map<String, String> codeDescriptions;
    private final String gstTotal;
    private final String gstBilledTotal;
    private final String gstPercent;
    private final boolean percRendered;
    private final boolean dupServiceCode;
    private final int totalItem;
    private final java.util.List<PercJsBinding> percJsHandlers;
    private final boolean publicPayer;
    private final boolean privatePayer;
    private final String billingNotes;
    private final String clinicAddress;
    private final String payeeProviderNo;
    private final String payeeName;
    private final String payeeFromConfig;
    private final boolean payeeFromConfigSet;
    private final java.util.List<PaymentType> paymentTypes;
    private final java.util.List<ParamPair> allRequestParams;
    private final String loggedInUserNo;

    /** Provider name pair surfaced for the payee lookup. */
    public record ProviderName(String lastName, String firstName) { }

    /** A single service-code row in the calculation table. */
    public record ServiceCodeRow(
            int rowIndex,
            int n,
            String codeName,
            String codeUnit,
            String codeFee,
            String codeTotal,
            String codeAt,
            String codeDescription,
            String warning,
            boolean gstApplied,
            boolean codeValid) { }

    /** A single percent-code row in the calculation table. */
    public record PercCodeRow(
            int rowIndex,
            String codeName,
            String codeUnit,
            String percFee,
            String codeMin,
            String codeMax,
            java.util.List<PercSegment> segments) { }

    /** One percent-code segment (checkbox value within a {@link PercCodeRow}). */
    public record PercSegment(String percTotal, String factor) { }

    /** JS handler descriptor for the onCheckMaster() per-percent loop. */
    public record PercJsBinding(String iCheckNo, String min, String max) { }

    /** Payment-type radio option. */
    public record PaymentType(String id, String label) { }

    /** Generic (name,value) pair used for the dump-all-params hidden inputs. */
    public record ParamPair(String name, String value) { }

    /** Plain-text billing review alert rendered by the JSP. */
    public record ReviewAlert(String level, String message) {
        public String getLevel() { return level; }
        public String getMessage() { return message; }
    }

    private BillingOnReviewViewModel(Builder b) {
        this.demographic = (b.demographic != null)
                ? b.demographic
                : new BillingDemographicSummary(
                        b.demoFirst, b.demoLast, b.demoHin, b.demoVer,
                        b.demoSex, b.demoHcType, b.demoDob,
                        b.demoDobYy, b.demoDobMm, b.demoDobDd);
        this.referral = (b.referral != null)
                ? b.referral
                : new BillingReferralDoctor(
                        b.referralDoctorName, b.referralDoctorOhip, "");
        this.messages = (b.messages != null)
                ? b.messages
                : new BillingValidationMessages(b.errorFlag, b.errorMessage, b.warningMessage);
        // Review only carries the multisitesEnabled flag — sites & providerHtml
        // are not populated by this assembler today, so the composed record is
        // synthesized with empty collections for those slices.
        this.multisite = (b.multisite != null)
                ? b.multisite
                : new BillingMultisiteContext(
                        b.multisitesEnabled,
                        java.util.Collections.emptyList(),
                        "", "", "",
                        java.util.Collections.emptyMap(),
                        false,
                        java.util.Collections.emptyList(),
                        "");
        this.patientAddress = BillingViewStrings.nullToEmpty(b.patientAddress);
        this.assignedProviderNo = BillingViewStrings.nullToEmpty(b.assignedProviderNo);
        this.providerOhip = BillingViewStrings.nullToEmpty(b.providerOhip);
        this.providerRma = BillingViewStrings.nullToEmpty(b.providerRma);
        this.providerView = BillingViewStrings.nullToEmpty(b.providerView);
        this.dxCode = BillingViewStrings.nullToEmpty(b.dxCode);
        this.dxDesc = BillingViewStrings.nullToEmpty(b.dxDesc);
        this.validationMessages = b.validationMessages == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.validationMessages);
        this.codeValid = b.codeValid;
        this.totalsParseFailed = b.totalsParseFailed;
        this.providerNameLookup = b.providerNameLookup == null
                ? java.util.Collections.emptyMap()
                : java.util.Map.copyOf(b.providerNameLookup);

        this.requestParamEchoes = b.requestParamEchoes == null
                ? java.util.Collections.emptyMap()
                : java.util.Map.copyOf(b.requestParamEchoes);
        this.demoName = BillingViewStrings.nullToEmpty(b.demoName);
        this.wrongMessage = BillingViewStrings.nullToEmpty(b.wrongMessage);
        this.reviewAlerts = b.reviewAlerts == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.reviewAlerts);
        this.demoSexLabel = BillingViewStrings.nullToEmpty(b.demoSexLabel);
        this.demoHeaderLine = BillingViewStrings.nullToEmpty(b.demoHeaderLine);
        this.mReview = b.mReview;
        this.serviceDateLines = b.serviceDateLines == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.serviceDateLines);
        this.billingPhysicianLabel = BillingViewStrings.nullToEmpty(b.billingPhysicianLabel);
        this.mrpLabel = BillingViewStrings.nullToEmpty(b.mrpLabel);
        this.visitTypeLabel = BillingViewStrings.nullToEmpty(b.visitTypeLabel);
        this.billTypeLabel = BillingViewStrings.nullToEmpty(b.billTypeLabel);
        this.billType = BillingViewStrings.nullToEmpty(b.billType);
        this.locationLabel = BillingViewStrings.nullToEmpty(b.locationLabel);
        this.sliCodeLabel = BillingViewStrings.nullToEmpty(b.sliCodeLabel);
        this.admissionDate = BillingViewStrings.nullToEmpty(b.admissionDate);
        this.siteName = BillingViewStrings.nullToEmpty(b.siteName);
        this.serviceCodeRows = b.serviceCodeRows == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.serviceCodeRows);
        this.percCodeRows = b.percCodeRows == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.percCodeRows);
        this.codeDescriptions = b.codeDescriptions == null
                ? java.util.Collections.emptyMap()
                : java.util.Map.copyOf(b.codeDescriptions);
        this.gstTotal = BillingViewStrings.nullToEmpty(b.gstTotal);
        this.gstBilledTotal = BillingViewStrings.nullToEmpty(b.gstBilledTotal);
        this.gstPercent = BillingViewStrings.nullToEmpty(b.gstPercent);
        this.percRendered = b.percRendered;
        this.dupServiceCode = b.dupServiceCode;
        this.totalItem = b.totalItem;
        this.percJsHandlers = b.percJsHandlers == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.percJsHandlers);
        this.publicPayer = b.publicPayer;
        this.privatePayer = b.privatePayer;
        this.billingNotes = BillingViewStrings.nullToEmpty(b.billingNotes);
        this.clinicAddress = BillingViewStrings.nullToEmpty(b.clinicAddress);
        this.payeeProviderNo = BillingViewStrings.nullToEmpty(b.payeeProviderNo);
        this.payeeName = BillingViewStrings.nullToEmpty(b.payeeName);
        this.payeeFromConfig = BillingViewStrings.nullToEmpty(b.payeeFromConfig);
        this.payeeFromConfigSet = b.payeeFromConfigSet;
        this.paymentTypes = b.paymentTypes == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.paymentTypes);
        this.allRequestParams = b.allRequestParams == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.allRequestParams);
        this.loggedInUserNo = BillingViewStrings.nullToEmpty(b.loggedInUserNo);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Aggregated demographic snapshot — primary internal storage. */
    public BillingDemographicSummary getDemographic() { return demographic; }
    /** Aggregated referral-doctor record — primary internal storage. */
    public BillingReferralDoctor getReferral() { return referral; }
    /** Aggregated validation banner state — primary internal storage. */
    public BillingValidationMessages getMessages() { return messages; }
    /** Aggregated multisite context — primary internal storage. */
    public BillingMultisiteContext getMultisite() { return multisite; }

    public String getDemoFirst() { return demographic.firstName(); }
    public String getDemoLast() { return demographic.lastName(); }
    public String getDemoHin() { return demographic.hin(); }
    public String getDemoVer() { return demographic.ver(); }
    public String getDemoSex() { return demographic.sex(); }
    public String getDemoHcType() { return demographic.hcType(); }
    public String getDemoDob() { return demographic.dob(); }
    public String getDemoDobYy() { return demographic.dobYy(); }
    public String getDemoDobMm() { return demographic.dobMm(); }
    public String getDemoDobDd() { return demographic.dobDd(); }
    /** Alias of {@link #getDemographic()} (legacy). */
    public BillingDemographicSummary getDemographicSummary() { return demographic; }
    public String getPatientAddress() { return patientAddress; }
    public String getReferralDoctorName() { return referral.name(); }
    public String getReferralDoctorOhip() { return referral.ohip(); }
    /** Alias of {@link #getReferral()} (legacy). */
    public BillingReferralDoctor getReferralDoctorRecord() { return referral; }
    public String getAssignedProviderNo() { return assignedProviderNo; }
    public String getProviderOhip() { return providerOhip; }
    public String getProviderRma() { return providerRma; }
    public String getProviderView() { return providerView; }
    public String getDxCode() { return dxCode; }
    public String getDxDesc() { return dxDesc; }
    public String getErrorFlag() { return messages.errorFlag(); }
    public String getErrorMessage() { return messages.errorMessage(); }
    public String getWarningMessage() { return messages.warningMessage(); }
    /** Alias of {@link #getMessages()} (legacy). */
    public BillingValidationMessages getValidationMessagesAggregate() { return messages; }
    public java.util.List<io.github.carlos_emr.carlos.billings.ca.on.validator.BillingOnReviewValidator.Message> getValidationMessages() {
        return validationMessages;
    }
    public boolean isCodeValid() { return codeValid; }
    /**
     * @return {@code true} when one or more numeric inputs failed to parse and
     *         the assembler substituted zero. JSP must banner-warn and gate
     *         submission; GST and total figures cannot be trusted.
     */
    public boolean isTotalsParseFailed() { return totalsParseFailed; }
    public java.util.Map<String, ProviderName> getProviderNameLookup() { return providerNameLookup; }

    public java.util.Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }
    public String getDemoName() { return demoName; }
    public String getWrongMessage() { return wrongMessage; }
    public java.util.List<ReviewAlert> getReviewAlerts() { return reviewAlerts; }
    public String getDemoSexLabel() { return demoSexLabel; }
    public String getDemoHeaderLine() { return demoHeaderLine; }
    public boolean isMultisitesEnabled() { return multisite.enabled(); }
    public boolean isMReview() { return mReview; }
    public java.util.List<String> getServiceDateLines() { return serviceDateLines; }
    public String getBillingPhysicianLabel() { return billingPhysicianLabel; }
    public String getMrpLabel() { return mrpLabel; }
    public String getVisitTypeLabel() { return visitTypeLabel; }
    public String getBillTypeLabel() { return billTypeLabel; }
    public String getBillType() { return billType; }
    public String getLocationLabel() { return locationLabel; }
    public String getSliCodeLabel() { return sliCodeLabel; }
    public String getAdmissionDate() { return admissionDate; }
    public String getSiteName() { return siteName; }
    public java.util.List<ServiceCodeRow> getServiceCodeRows() { return serviceCodeRows; }
    public java.util.List<PercCodeRow> getPercCodeRows() { return percCodeRows; }
    public java.util.Map<String, String> getCodeDescriptions() { return codeDescriptions; }
    public String getGstTotal() { return gstTotal; }
    public String getGstBilledTotal() { return gstBilledTotal; }
    public String getGstPercent() { return gstPercent; }
    public boolean isPercRendered() { return percRendered; }
    public boolean isDupServiceCode() { return dupServiceCode; }
    public int getTotalItem() { return totalItem; }
    public java.util.List<PercJsBinding> getPercJsHandlers() { return percJsHandlers; }
    public boolean isPublicPayer() { return publicPayer; }
    public boolean isPrivatePayer() { return privatePayer; }
    public String getBillingNotes() { return billingNotes; }
    public String getClinicAddress() { return clinicAddress; }
    public String getPayeeProviderNo() { return payeeProviderNo; }
    public String getPayeeName() { return payeeName; }
    public String getPayeeFromConfig() { return payeeFromConfig; }
    public boolean isPayeeFromConfigSet() { return payeeFromConfigSet; }
    public java.util.List<PaymentType> getPaymentTypes() { return paymentTypes; }
    public java.util.List<ParamPair> getAllRequestParams() { return allRequestParams; }
    public String getLoggedInUserNo() { return loggedInUserNo; }

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
        private java.util.List<io.github.carlos_emr.carlos.billings.ca.on.validator.BillingOnReviewValidator.Message> validationMessages;
        private boolean codeValid = true;
        private boolean totalsParseFailed = false;
        private java.util.Map<String, ProviderName> providerNameLookup;

        private java.util.Map<String, String> requestParamEchoes;
        private String demoName = "";
        private String wrongMessage = "";
        private java.util.List<ReviewAlert> reviewAlerts;
        private String demoSexLabel = "";
        private String demoHeaderLine = "";
        private boolean multisitesEnabled = false;
        private boolean mReview = false;
        private java.util.List<String> serviceDateLines;
        private String billingPhysicianLabel = "";
        private String mrpLabel = "";
        private String visitTypeLabel = "";
        private String billTypeLabel = "";
        private String billType = "";
        private String locationLabel = "";
        private String sliCodeLabel = "";
        private String admissionDate = "";
        private String siteName = "";
        private java.util.List<ServiceCodeRow> serviceCodeRows;
        private java.util.List<PercCodeRow> percCodeRows;
        private java.util.Map<String, String> codeDescriptions;
        private String gstTotal = "0";
        private String gstBilledTotal = "0";
        private String gstPercent = "";
        private boolean percRendered = false;
        private boolean dupServiceCode = false;
        private int totalItem = 0;
        private java.util.List<PercJsBinding> percJsHandlers;
        private boolean publicPayer = false;
        private boolean privatePayer = false;
        private String billingNotes = "";
        private String clinicAddress = "";
        private String payeeProviderNo = "";
        private String payeeName = "";
        private String payeeFromConfig = "";
        private boolean payeeFromConfigSet = false;
        private java.util.List<PaymentType> paymentTypes;
        private java.util.List<ParamPair> allRequestParams;
        private String loggedInUserNo = "";

        public Builder validationMessages(java.util.List<io.github.carlos_emr.carlos.billings.ca.on.validator.BillingOnReviewValidator.Message> v) {
            this.validationMessages = v == null ? null : java.util.List.copyOf(v); return this;
        }
        public Builder codeValid(boolean v) { this.codeValid = v; return this; }
        public Builder totalsParseFailed(boolean v) { this.totalsParseFailed = v; return this; }
        public Builder providerNameLookup(java.util.Map<String, ProviderName> v) {
            this.providerNameLookup = v == null ? null : java.util.Map.copyOf(v); return this;
        }

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

        public Builder requestParamEchoes(java.util.Map<String, String> v) { this.requestParamEchoes = v == null ? null : java.util.Map.copyOf(v); return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder wrongMessage(String v) { this.wrongMessage = v; return this; }
        public Builder reviewAlerts(java.util.List<ReviewAlert> v) { this.reviewAlerts = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder demoSexLabel(String v) { this.demoSexLabel = v; return this; }
        public Builder demoHeaderLine(String v) { this.demoHeaderLine = v; return this; }
        public Builder multisitesEnabled(boolean v) { this.multisitesEnabled = v; return this; }
        public Builder mReview(boolean v) { this.mReview = v; return this; }
        public Builder serviceDateLines(java.util.List<String> v) { this.serviceDateLines = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder billingPhysicianLabel(String v) { this.billingPhysicianLabel = v; return this; }
        public Builder mrpLabel(String v) { this.mrpLabel = v; return this; }
        public Builder visitTypeLabel(String v) { this.visitTypeLabel = v; return this; }
        public Builder billTypeLabel(String v) { this.billTypeLabel = v; return this; }
        public Builder billType(String v) { this.billType = v; return this; }
        public Builder locationLabel(String v) { this.locationLabel = v; return this; }
        public Builder sliCodeLabel(String v) { this.sliCodeLabel = v; return this; }
        public Builder admissionDate(String v) { this.admissionDate = v; return this; }
        public Builder siteName(String v) { this.siteName = v; return this; }
        public Builder serviceCodeRows(java.util.List<ServiceCodeRow> v) { this.serviceCodeRows = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder percCodeRows(java.util.List<PercCodeRow> v) { this.percCodeRows = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder codeDescriptions(java.util.Map<String, String> v) { this.codeDescriptions = v == null ? null : java.util.Map.copyOf(v); return this; }
        public Builder gstTotal(String v) { this.gstTotal = v; return this; }
        public Builder gstBilledTotal(String v) { this.gstBilledTotal = v; return this; }
        public Builder gstPercent(String v) { this.gstPercent = v; return this; }
        public Builder percRendered(boolean v) { this.percRendered = v; return this; }
        public Builder dupServiceCode(boolean v) { this.dupServiceCode = v; return this; }
        public Builder totalItem(int v) { this.totalItem = v; return this; }
        public Builder percJsHandlers(java.util.List<PercJsBinding> v) { this.percJsHandlers = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder publicPayer(boolean v) { this.publicPayer = v; return this; }
        public Builder privatePayer(boolean v) { this.privatePayer = v; return this; }
        public Builder billingNotes(String v) { this.billingNotes = v; return this; }
        public Builder clinicAddress(String v) { this.clinicAddress = v; return this; }
        public Builder payeeProviderNo(String v) { this.payeeProviderNo = v; return this; }
        public Builder payeeName(String v) { this.payeeName = v; return this; }
        public Builder payeeFromConfig(String v) { this.payeeFromConfig = v; return this; }
        public Builder payeeFromConfigSet(boolean v) { this.payeeFromConfigSet = v; return this; }
        public Builder paymentTypes(java.util.List<PaymentType> v) { this.paymentTypes = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder allRequestParams(java.util.List<ParamPair> v) { this.allRequestParams = v == null ? null : java.util.List.copyOf(v); return this; }
        public Builder loggedInUserNo(String v) { this.loggedInUserNo = v; return this; }

        public BillingOnReviewViewModel build() {
            return new BillingOnReviewViewModel(this);
        }
    }
}
