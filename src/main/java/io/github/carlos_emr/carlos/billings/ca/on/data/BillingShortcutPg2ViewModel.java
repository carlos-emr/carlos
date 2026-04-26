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
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingShortcutPg2DataAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingShortcutPg2Save2Action})
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

    private final PostSaveAction postSaveAction;
    private final String redirectUrl;
    private final String displaySex;

    private BillingShortcutPg2ViewModel(Builder b) {
        this.demoFirst = nullToEmpty(b.demoFirst);
        this.demoLast = nullToEmpty(b.demoLast);
        this.demoSex = nullToEmpty(b.demoSex);
        this.demoDobYy = nullToEmpty(b.demoDobYy);
        this.demoDobMm = nullToEmpty(b.demoDobMm);
        this.demoDobDd = nullToEmpty(b.demoDobDd);
        this.demoHin = nullToEmpty(b.demoHin);
        this.demoHcType = nullToEmpty(b.demoHcType);
        this.referralDoctor = nullToEmpty(b.referralDoctor);
        this.referralDoctorOhip = nullToEmpty(b.referralDoctorOhip);
        this.providerOhipNo = nullToEmpty(b.providerOhipNo);
        this.providerRmaNo = nullToEmpty(b.providerRmaNo);
        this.errorMsg = nullToEmpty(b.errorMsg);
        this.warningMsg = nullToEmpty(b.warningMsg);
        this.errorFlagged = b.errorFlagged;
        this.calculationHtml = nullToEmpty(b.calculationHtml);
        this.totalAmount = nullToEmpty(b.totalAmount);
        this.postSaveAction = b.postSaveAction == null ? PostSaveAction.NONE : b.postSaveAction;
        this.redirectUrl = nullToEmpty(b.redirectUrl);
        this.displaySex = nullToEmpty(b.displaySex);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

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

        public BillingShortcutPg2ViewModel build() {
            return new BillingShortcutPg2ViewModel(this);
        }
    }
}
