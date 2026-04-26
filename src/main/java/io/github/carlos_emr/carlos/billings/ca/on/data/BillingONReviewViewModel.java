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
 * <p>Captures demographic + provider lookups, the chosen diagnostic code, the
 * pre-rendered service-code / percent-code rows, the validation messages, and
 * all derived flags / request-param echoes the JSP needs. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONReviewDataAssembler}
 * and exposed to the JSP as request attribute {@code reviewModel}. Replaces
 * the legacy multi-block scriptlet that previously ran inline in the JSP.</p>
 *
 * @since 2026-04-24
 *        2026-04-25 (full body-scriptlet drain)
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

    private final java.util.List<io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewValidator.Message> validationMessages;
    private final boolean codeValid;

    private final java.util.Map<String, ProviderName> providerNameLookup;

    private final java.util.Map<String, String> requestParamEchoes;
    private final String demoName;
    private final String wrongMessage;
    private final String demoSexLabel;
    private final String demoHeaderLine;
    private final boolean multisitesEnabled;
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
    private final java.util.List<PercJsHandler> percJsHandlers;
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
    public record PercJsHandler(String iCheckNo, String min, String max) { }

    /** Payment-type radio option. */
    public record PaymentType(String id, String label) { }

    /** Generic (name,value) pair used for the dump-all-params hidden inputs. */
    public record ParamPair(String name, String value) { }

    private BillingONReviewViewModel(Builder b) {
        this.demoFirst = nullToEmpty(b.demoFirst);
        this.demoLast = nullToEmpty(b.demoLast);
        this.demoHin = nullToEmpty(b.demoHin);
        this.demoVer = nullToEmpty(b.demoVer);
        this.demoSex = nullToEmpty(b.demoSex);
        this.demoHcType = nullToEmpty(b.demoHcType);
        this.demoDob = nullToEmpty(b.demoDob);
        this.demoDobYy = nullToEmpty(b.demoDobYy);
        this.demoDobMm = nullToEmpty(b.demoDobMm);
        this.demoDobDd = nullToEmpty(b.demoDobDd);
        this.patientAddress = nullToEmpty(b.patientAddress);
        this.referralDoctorName = nullToEmpty(b.referralDoctorName);
        this.referralDoctorOhip = nullToEmpty(b.referralDoctorOhip);
        this.assignedProviderNo = nullToEmpty(b.assignedProviderNo);
        this.providerOhip = nullToEmpty(b.providerOhip);
        this.providerRma = nullToEmpty(b.providerRma);
        this.providerView = nullToEmpty(b.providerView);
        this.dxCode = nullToEmpty(b.dxCode);
        this.dxDesc = nullToEmpty(b.dxDesc);
        this.errorFlag = nullToEmpty(b.errorFlag);
        this.errorMessage = nullToEmpty(b.errorMessage);
        this.warningMessage = nullToEmpty(b.warningMessage);
        this.validationMessages = b.validationMessages == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.validationMessages);
        this.codeValid = b.codeValid;
        this.providerNameLookup = b.providerNameLookup == null
                ? java.util.Collections.emptyMap()
                : java.util.Map.copyOf(b.providerNameLookup);

        this.requestParamEchoes = b.requestParamEchoes == null
                ? java.util.Collections.emptyMap()
                : java.util.Map.copyOf(b.requestParamEchoes);
        this.demoName = nullToEmpty(b.demoName);
        this.wrongMessage = nullToEmpty(b.wrongMessage);
        this.demoSexLabel = nullToEmpty(b.demoSexLabel);
        this.demoHeaderLine = nullToEmpty(b.demoHeaderLine);
        this.multisitesEnabled = b.multisitesEnabled;
        this.mReview = b.mReview;
        this.serviceDateLines = b.serviceDateLines == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.serviceDateLines);
        this.billingPhysicianLabel = nullToEmpty(b.billingPhysicianLabel);
        this.mrpLabel = nullToEmpty(b.mrpLabel);
        this.visitTypeLabel = nullToEmpty(b.visitTypeLabel);
        this.billTypeLabel = nullToEmpty(b.billTypeLabel);
        this.billType = nullToEmpty(b.billType);
        this.locationLabel = nullToEmpty(b.locationLabel);
        this.sliCodeLabel = nullToEmpty(b.sliCodeLabel);
        this.admissionDate = nullToEmpty(b.admissionDate);
        this.siteName = nullToEmpty(b.siteName);
        this.serviceCodeRows = b.serviceCodeRows == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.serviceCodeRows);
        this.percCodeRows = b.percCodeRows == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.percCodeRows);
        this.codeDescriptions = b.codeDescriptions == null
                ? java.util.Collections.emptyMap()
                : java.util.Map.copyOf(b.codeDescriptions);
        this.gstTotal = nullToEmpty(b.gstTotal);
        this.gstBilledTotal = nullToEmpty(b.gstBilledTotal);
        this.gstPercent = nullToEmpty(b.gstPercent);
        this.percRendered = b.percRendered;
        this.dupServiceCode = b.dupServiceCode;
        this.totalItem = b.totalItem;
        this.percJsHandlers = b.percJsHandlers == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.percJsHandlers);
        this.publicPayer = b.publicPayer;
        this.privatePayer = b.privatePayer;
        this.billingNotes = nullToEmpty(b.billingNotes);
        this.clinicAddress = nullToEmpty(b.clinicAddress);
        this.payeeProviderNo = nullToEmpty(b.payeeProviderNo);
        this.payeeName = nullToEmpty(b.payeeName);
        this.payeeFromConfig = nullToEmpty(b.payeeFromConfig);
        this.payeeFromConfigSet = b.payeeFromConfigSet;
        this.paymentTypes = b.paymentTypes == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.paymentTypes);
        this.allRequestParams = b.allRequestParams == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(b.allRequestParams);
        this.loggedInUserNo = nullToEmpty(b.loggedInUserNo);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
    public BillingDemographicSummary getDemographicSummary() {
        return new BillingDemographicSummary(demoFirst, demoLast, demoHin, demoVer,
                demoSex, demoHcType, demoDob, demoDobYy, demoDobMm, demoDobDd);
    }
    public String getPatientAddress() { return patientAddress; }
    public String getReferralDoctorName() { return referralDoctorName; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public BillingReferralDoctor getReferralDoctorRecord() {
        return new BillingReferralDoctor(referralDoctorName, referralDoctorOhip, "");
    }
    public String getAssignedProviderNo() { return assignedProviderNo; }
    public String getProviderOhip() { return providerOhip; }
    public String getProviderRma() { return providerRma; }
    public String getProviderView() { return providerView; }
    public String getDxCode() { return dxCode; }
    public String getDxDesc() { return dxDesc; }
    public String getErrorFlag() { return errorFlag; }
    public String getErrorMessage() { return errorMessage; }
    public String getWarningMessage() { return warningMessage; }
    public BillingValidationMessages getValidationMessagesAggregate() {
        return new BillingValidationMessages(errorFlag, errorMessage, warningMessage);
    }
    public java.util.List<io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewValidator.Message> getValidationMessages() {
        return validationMessages;
    }
    public boolean isCodeValid() { return codeValid; }
    public java.util.Map<String, ProviderName> getProviderNameLookup() { return providerNameLookup; }

    public java.util.Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }
    public String getDemoName() { return demoName; }
    public String getWrongMessage() { return wrongMessage; }
    public String getDemoSexLabel() { return demoSexLabel; }
    public String getDemoHeaderLine() { return demoHeaderLine; }
    public boolean isMultisitesEnabled() { return multisitesEnabled; }
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
    public java.util.List<PercJsHandler> getPercJsHandlers() { return percJsHandlers; }
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
        private java.util.List<io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewValidator.Message> validationMessages;
        private boolean codeValid = true;
        private java.util.Map<String, ProviderName> providerNameLookup;

        private java.util.Map<String, String> requestParamEchoes;
        private String demoName = "";
        private String wrongMessage = "";
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
        private java.util.List<PercJsHandler> percJsHandlers;
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

        public Builder validationMessages(java.util.List<io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewValidator.Message> v) {
            this.validationMessages = v == null ? null : java.util.List.copyOf(v); return this;
        }
        public Builder codeValid(boolean v) { this.codeValid = v; return this; }
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
        public Builder percJsHandlers(java.util.List<PercJsHandler> v) { this.percJsHandlers = v == null ? null : java.util.List.copyOf(v); return this; }
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

        public BillingONReviewViewModel build() {
            return new BillingONReviewViewModel(this);
        }
    }
}
