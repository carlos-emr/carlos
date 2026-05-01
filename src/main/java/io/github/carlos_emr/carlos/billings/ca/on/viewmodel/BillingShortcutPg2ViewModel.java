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
import java.util.Map;

/**
 * Immutable view model for {@code billingShortcutPg2.jsp}, the fast-track
 * billing-confirmation page that displays computed totals and persists
 * the bill on submit.
 *
 * <p>Captures the patient header (demo first/last/sex/DOB/HIN/hcType),
 * provider OHIP / RMA numbers, referral doctor, validation messages
 * (error / warning), the pre-rendered calculation table HTML, the
 * computed total, and the post-save navigation directive.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.service.BillingShortcutPg2Service#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.BillingShortcutPg2Save2Action})
 * and exposed to the JSP as request attribute {@code shortcutPg2Model}.</p>
 *
 * <p>Eliminates the 6 inline {@code SpringUtils.getBean} lookups the JSP
 * used to perform across its scriptlet body (BillingDao, BillingDetailDao,
 * ProviderDao, DemographicDao, BillingServiceDao, BillingPercLimitDao).</p>
 *
 * @since 2026-04-26
 */
public final class BillingShortcutPg2ViewModel {

    /**
     * Post-save navigation directive. The JSP previously emitted a
     * {@code <script>self.close()</script>} or {@code window.location=...}
     * inline depending on the submit button. The action layer now decides
     * the navigation; the JSP renders the matching client-side hook.
     */
    public enum PostSaveAction {
        /** No navigation — initial page render or "Back to Edit" routed away. */
        NONE,
        /** Save complete — close the popup ({@code self.close()}). */
        CLOSE_WINDOW,
        /** "Save and Back" / "Next" — redirect to billingShortcutPg1View. */
        REDIRECT_TO_PG1
    }

    private final String demoFirst;
    private final String demoLast;
    private final String demoSex;
    private final String demoDobYy;
    private final String demoDobMm;
    private final String demoDobDd;
    private final String demoHin;
    private final String demoHcType;

    private final String referralDoctor;
    private final String referralDoctorOhip;

    private final String providerOhipNo;
    private final String providerRmaNo;

    private final String errorMsg;
    private final String warningMsg;
    private final boolean errorFlagged;

    /** Pre-rendered calculation-table HTML rows (the {@code msg} variable
     *  in the legacy JSP). The table assembly mixes per-line-item rows,
     *  percentage-base lines, adjust-to-min/max rows, and the running total
     *  — building this incrementally in the view-layer is fragile, so we
     *  hand the JSP a pre-rendered string. */
    private final String calculationHtml;

    private final String totalAmount;

    /** Decision the JSP makes after the bill is saved: stay on the form,
     *  close the popup, or redirect to billingShortcutPg1View. Builder
     *  enforces the {@link PostSaveAction#REDIRECT_TO_PG1} ↔ non-empty
     *  {@link #redirectUrl} invariant so the JSP can rely on the URL
     *  whenever it sees the redirect action. */
    private final PostSaveAction postSaveAction;
    /** Target URL paired with {@link PostSaveAction#REDIRECT_TO_PG1}.
     *  Built and URL-encoded by
     *  {@code BillingShortcutPg2Service.buildPg1RedirectUrl}; empty for
     *  every other {@code postSaveAction} value. */
    private final String redirectUrl;
    private final String displaySex;

    /** Pre-resolved request-param echoes — the hidden-input loop in the JSP
     *  iterates {@code request.getParameterNames()} and emits one
     *  {@code <input type="hidden">} per param. The assembler captures every
     *  request parameter into this map so the JSP renders a single
     *  {@code <c:forEach>} over a known map. */
    private final Map<String, String> requestParamEchoes;
    /** Pre-rendered billDate column HTML — the legacy JSP joined the
     *  newline-split {@code billDate} param with {@code <br>}. The HTML
     *  fragment is encoded by the assembler before joining; the JSP outputs
     *  it untouched. */
    private final String billDateHtml;
    /** Resolved billing-physician label — the legacy JSP read this from a
     *  session-scoped {@code providerBean} keyed by {@code xml_provider}. */
    private final String billingProviderLabel;
    /** Resolved assigned-physician label — keyed by {@code assgProvider_no}. */
    private final String assignedProviderLabel;
    /** Visit-type label (text after the {@code |} delimiter in
     *  {@code xml_visittype}). Empty when no pipe present. */
    private final String visitTypeLabel;
    /** Billing-type label (text after the {@code |} in {@code xml_billtype}). */
    private final String billTypeLabel;
    /** Visit-location label (text after the {@code |} in {@code xml_location}). */
    private final String visitLocationLabel;
    /** SLI code (text after the {@code |} in {@code xml_slicode}). */
    private final String sliCode;
    /** When true, render "Not Applicable" for SLI; otherwise render
     *  {@link #sliCode}. The legacy logic: if the resolved sliCode starts
     *  with the configured {@code clinic_no} property, the field is N/A. */
    private final boolean sliNotApplicable;
    /** Admission date echo (the {@code xml_vdate} param). */
    private final String admissionDate;
    /** {@code demographic_name} request-param echo (rendered in the patient
     *  header). */
    private final String demographicName;
    /** {@code dxCode} request-param echo. */
    private final String dxCode;
    /** {@code rulePerc} request-param echo. */
    private final String rulePerc;
    /** {@code referralDocName} request-param echo (referral-doctor display). */
    private final String referralDocName;
    /** {@code referralCode} request-param echo (referral-doctor #). */
    private final String referralCodeParam;

    private BillingShortcutPg2ViewModel(Builder b) {
        this.demoFirst = BillingViewStrings.nullToEmpty(b.demoFirst);
        this.demoLast = BillingViewStrings.nullToEmpty(b.demoLast);
        this.demoSex = BillingViewStrings.nullToEmpty(b.demoSex);
        this.demoDobYy = BillingViewStrings.nullToEmpty(b.demoDobYy);
        this.demoDobMm = BillingViewStrings.nullToEmpty(b.demoDobMm);
        this.demoDobDd = BillingViewStrings.nullToEmpty(b.demoDobDd);
        this.demoHin = BillingViewStrings.nullToEmpty(b.demoHin);
        this.demoHcType = BillingViewStrings.nullToEmpty(b.demoHcType);
        this.referralDoctor = BillingViewStrings.nullToEmpty(b.referralDoctor);
        this.referralDoctorOhip = BillingViewStrings.nullToEmpty(b.referralDoctorOhip);
        this.providerOhipNo = BillingViewStrings.nullToEmpty(b.providerOhipNo);
        this.providerRmaNo = BillingViewStrings.nullToEmpty(b.providerRmaNo);
        this.errorMsg = BillingViewStrings.nullToEmpty(b.errorMsg);
        this.warningMsg = BillingViewStrings.nullToEmpty(b.warningMsg);
        this.errorFlagged = b.errorFlagged;
        this.calculationHtml = BillingViewStrings.nullToEmpty(b.calculationHtml);
        this.totalAmount = BillingViewStrings.nullToEmpty(b.totalAmount);
        this.postSaveAction = b.postSaveAction == null ? PostSaveAction.NONE : b.postSaveAction;
        this.redirectUrl = BillingViewStrings.nullToEmpty(b.redirectUrl);
        requirePairCoherence(this.postSaveAction, this.redirectUrl);
        this.displaySex = BillingViewStrings.nullToEmpty(b.displaySex);
        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
        this.billDateHtml = BillingViewStrings.nullToEmpty(b.billDateHtml);
        this.billingProviderLabel = BillingViewStrings.nullToEmpty(b.billingProviderLabel);
        this.assignedProviderLabel = BillingViewStrings.nullToEmpty(b.assignedProviderLabel);
        this.visitTypeLabel = BillingViewStrings.nullToEmpty(b.visitTypeLabel);
        this.billTypeLabel = BillingViewStrings.nullToEmpty(b.billTypeLabel);
        this.visitLocationLabel = BillingViewStrings.nullToEmpty(b.visitLocationLabel);
        this.sliCode = BillingViewStrings.nullToEmpty(b.sliCode);
        this.sliNotApplicable = b.sliNotApplicable;
        this.admissionDate = BillingViewStrings.nullToEmpty(b.admissionDate);
        this.demographicName = BillingViewStrings.nullToEmpty(b.demographicName);
        this.dxCode = BillingViewStrings.nullToEmpty(b.dxCode);
        this.rulePerc = BillingViewStrings.nullToEmpty(b.rulePerc);
        this.referralDocName = BillingViewStrings.nullToEmpty(b.referralDocName);
        this.referralCodeParam = BillingViewStrings.nullToEmpty(b.referralCodeParam);
    }

    /**
     * Pair invariant: REDIRECT_TO_PG1 requires a non-empty URL, every
     * other action requires an empty URL. Failing fast at build-time
     * forces the assembler to keep the two fields coherent — without it,
     * the JSP would have to invent fallback logic for incoherent pairs
     * (e.g., "redirect with no URL" = "fall through to close window?").
     *
     * <p>An IllegalStateException here is a programmer error: the only
     * caller is {@code BillingShortcutPg2Service.assemble}, which sets
     * the redirectUrl iff it sets postSaveAction=REDIRECT_TO_PG1. We
     * deliberately do NOT add a Struts {@code <global-exception-mapping>}
     * for this path — a user-friendly banner would mask the assembler bug.
     * The default 500 + server log is the correct surface.</p>
     */
    private static void requirePairCoherence(PostSaveAction action, String url) {
        boolean isRedirect = action == PostSaveAction.REDIRECT_TO_PG1;
        boolean hasUrl = !url.isEmpty();
        if (isRedirect && !hasUrl) {
            throw new IllegalStateException(
                    "BillingShortcutPg2ViewModel: REDIRECT_TO_PG1 requires a non-empty redirectUrl");
        }
        if (!isRedirect && hasUrl) {
            throw new IllegalStateException(
                    "BillingShortcutPg2ViewModel: redirectUrl must be empty unless postSaveAction is REDIRECT_TO_PG1");
        }
    }

    public static Builder builder() { return new Builder(); }

    public String getDemoFirst() { return demoFirst; }
    public String getDemoLast() { return demoLast; }
    public String getDemoSex() { return demoSex; }
    public String getDemoDobYy() { return demoDobYy; }
    public String getDemoDobMm() { return demoDobMm; }
    public String getDemoDobDd() { return demoDobDd; }
    public String getDemoHin() { return demoHin; }
    public String getDemoHcType() { return demoHcType; }
    public String getReferralDoctor() { return referralDoctor; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getProviderOhipNo() { return providerOhipNo; }
    public String getProviderRmaNo() { return providerRmaNo; }
    public String getErrorMsg() { return errorMsg; }
    public String getWarningMsg() { return warningMsg; }
    public boolean isErrorFlagged() { return errorFlagged; }
    public String getCalculationHtml() { return calculationHtml; }
    public String getTotalAmount() { return totalAmount; }
    public PostSaveAction getPostSaveAction() { return postSaveAction; }
    public String getRedirectUrl() { return redirectUrl; }
    public String getDisplaySex() { return displaySex; }
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }
    public String getBillDateHtml() { return billDateHtml; }
    public String getBillingProviderLabel() { return billingProviderLabel; }
    public String getAssignedProviderLabel() { return assignedProviderLabel; }
    public String getVisitTypeLabel() { return visitTypeLabel; }
    public String getBillTypeLabel() { return billTypeLabel; }
    public String getVisitLocationLabel() { return visitLocationLabel; }
    public String getSliCode() { return sliCode; }
    public boolean isSliNotApplicable() { return sliNotApplicable; }
    public String getAdmissionDate() { return admissionDate; }
    public String getDemographicName() { return demographicName; }
    public String getDxCode() { return dxCode; }
    public String getRulePerc() { return rulePerc; }
    public String getReferralDocName() { return referralDocName; }
    public String getReferralCodeParam() { return referralCodeParam; }

    public String getCombinedMsgs() { return errorMsg + warningMsg; }

    public static final class Builder {
        private String demoFirst;
        private String demoLast;
        private String demoSex;
        private String demoDobYy;
        private String demoDobMm;
        private String demoDobDd;
        private String demoHin;
        private String demoHcType;
        private String referralDoctor;
        private String referralDoctorOhip;
        private String providerOhipNo;
        private String providerRmaNo;
        private String errorMsg;
        private String warningMsg;
        private boolean errorFlagged;
        private String calculationHtml;
        private String totalAmount;
        private PostSaveAction postSaveAction;
        private String redirectUrl;
        private String displaySex;
        private Map<String, String> requestParamEchoes;
        private String billDateHtml;
        private String billingProviderLabel;
        private String assignedProviderLabel;
        private String visitTypeLabel;
        private String billTypeLabel;
        private String visitLocationLabel;
        private String sliCode;
        private boolean sliNotApplicable;
        private String admissionDate;
        private String demographicName;
        private String dxCode;
        private String rulePerc;
        private String referralDocName;
        private String referralCodeParam;

        public Builder demoFirst(String v) { this.demoFirst = v; return this; }
        public Builder demoLast(String v) { this.demoLast = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder demoDobYy(String v) { this.demoDobYy = v; return this; }
        public Builder demoDobMm(String v) { this.demoDobMm = v; return this; }
        public Builder demoDobDd(String v) { this.demoDobDd = v; return this; }
        public Builder demoHin(String v) { this.demoHin = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder referralDoctor(String v) { this.referralDoctor = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder providerOhipNo(String v) { this.providerOhipNo = v; return this; }
        public Builder providerRmaNo(String v) { this.providerRmaNo = v; return this; }
        public Builder errorMsg(String v) { this.errorMsg = v; return this; }
        public Builder warningMsg(String v) { this.warningMsg = v; return this; }
        public Builder errorFlagged(boolean v) { this.errorFlagged = v; return this; }
        public Builder calculationHtml(String v) { this.calculationHtml = v; return this; }
        public Builder totalAmount(String v) { this.totalAmount = v; return this; }
        public Builder postSaveAction(PostSaveAction v) { this.postSaveAction = v; return this; }
        public Builder redirectUrl(String v) { this.redirectUrl = v; return this; }
        public Builder displaySex(String v) { this.displaySex = v; return this; }
        public Builder requestParamEchoes(Map<String, String> v) { this.requestParamEchoes = v == null ? null : Map.copyOf(v); return this; }
        public Builder billDateHtml(String v) { this.billDateHtml = v; return this; }
        public Builder billingProviderLabel(String v) { this.billingProviderLabel = v; return this; }
        public Builder assignedProviderLabel(String v) { this.assignedProviderLabel = v; return this; }
        public Builder visitTypeLabel(String v) { this.visitTypeLabel = v; return this; }
        public Builder billTypeLabel(String v) { this.billTypeLabel = v; return this; }
        public Builder visitLocationLabel(String v) { this.visitLocationLabel = v; return this; }
        public Builder sliCode(String v) { this.sliCode = v; return this; }
        public Builder sliNotApplicable(boolean v) { this.sliNotApplicable = v; return this; }
        public Builder admissionDate(String v) { this.admissionDate = v; return this; }
        public Builder demographicName(String v) { this.demographicName = v; return this; }
        public Builder dxCode(String v) { this.dxCode = v; return this; }
        public Builder rulePerc(String v) { this.rulePerc = v; return this; }
        public Builder referralDocName(String v) { this.referralDocName = v; return this; }
        public Builder referralCodeParam(String v) { this.referralCodeParam = v; return this; }

        public BillingShortcutPg2ViewModel build() {
            return new BillingShortcutPg2ViewModel(this);
        }
    }
}
