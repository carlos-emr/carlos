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

/**
 * Immutable view model for {@code billingONStatus.jsp}.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.ViewBillingONStatus2Action}
 * and exposed as request attribute {@code statusModel}. Captures the request
 * parameter echoes + default-value resolution that previously lived in the top
 * scriptlet of the JSP.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONStatusViewModel {

    /** Default bill types when none are selected (matches legacy scriptlet). */
    public static final List<String> DEFAULT_BILL_TYPES = List.of(
            "HCP", "WCB", "RMB", "NOT", "PAT", "OCF", "ODS", "CPP", "STD", "IFH");

    private final boolean teamBillingOnly;
    private final boolean siteAccessPrivacy;
    private final boolean multisites;
    private final boolean hideName;

    private final boolean search;
    private final List<String> billTypes;
    private final String statusType;
    private final String providerNo;
    private final String providerOhipNo;
    private final String startDate;
    private final String endDate;
    private final String demoNo;
    private final String serviceCode;
    private final String raCode;
    private final String claimNo;
    private final String dx;
    private final String visitType;
    private final String filename;
    private final String selectedSite;
    private final String billingForm;
    private final String visitLocation;
    private final String sortName;
    private final String sortOrder;
    private final String paymentStartDate;
    private final String paymentEndDate;

    private BillingONStatusViewModel(Builder b) {
        this.teamBillingOnly = b.teamBillingOnly;
        this.siteAccessPrivacy = b.siteAccessPrivacy;
        this.multisites = b.multisites;
        this.hideName = b.hideName;
        this.search = b.search;
        this.billTypes = b.billTypes == null ? Collections.emptyList() : List.copyOf(b.billTypes);
        this.statusType = nullToEmpty(b.statusType);
        this.providerNo = nullToEmpty(b.providerNo);
        this.providerOhipNo = nullToEmpty(b.providerOhipNo);
        this.startDate = nullToEmpty(b.startDate);
        this.endDate = nullToEmpty(b.endDate);
        this.demoNo = nullToEmpty(b.demoNo);
        this.serviceCode = nullToEmpty(b.serviceCode);
        this.raCode = nullToEmpty(b.raCode);
        this.claimNo = nullToEmpty(b.claimNo);
        this.dx = nullToEmpty(b.dx);
        this.visitType = nullToEmpty(b.visitType);
        this.filename = nullToEmpty(b.filename);
        this.selectedSite = nullToEmpty(b.selectedSite);
        this.billingForm = nullToEmpty(b.billingForm);
        this.visitLocation = nullToEmpty(b.visitLocation);
        this.sortName = nullToEmpty(b.sortName);
        this.sortOrder = nullToEmpty(b.sortOrder);
        this.paymentStartDate = nullToEmpty(b.paymentStartDate);
        this.paymentEndDate = nullToEmpty(b.paymentEndDate);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Coalesce null Strings to empty so EL doesn't render literal "null". */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public boolean isTeamBillingOnly() { return teamBillingOnly; }
    public boolean isSiteAccessPrivacy() { return siteAccessPrivacy; }
    public boolean isMultisites() { return multisites; }
    public boolean isHideName() { return hideName; }
    public boolean isSearch() { return search; }
    public List<String> getBillTypes() { return billTypes; }
    public String getStatusType() { return statusType; }
    public String getProviderNo() { return providerNo; }
    public String getProviderOhipNo() { return providerOhipNo; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getDemoNo() { return demoNo; }
    public String getServiceCode() { return serviceCode; }
    public String getRaCode() { return raCode; }
    public String getClaimNo() { return claimNo; }
    public String getDx() { return dx; }
    public String getVisitType() { return visitType; }
    public String getFilename() { return filename; }
    public String getSelectedSite() { return selectedSite; }
    public String getBillingForm() { return billingForm; }
    public String getVisitLocation() { return visitLocation; }
    public String getSortName() { return sortName; }
    public String getSortOrder() { return sortOrder; }
    public String getPaymentStartDate() { return paymentStartDate; }
    public String getPaymentEndDate() { return paymentEndDate; }

    public static final class Builder {
        private boolean teamBillingOnly;
        private boolean siteAccessPrivacy;
        private boolean multisites;
        private boolean hideName;
        private boolean search;
        private List<String> billTypes;
        private String statusType;
        private String providerNo;
        private String providerOhipNo;
        private String startDate;
        private String endDate;
        private String demoNo;
        private String serviceCode;
        private String raCode;
        private String claimNo;
        private String dx;
        private String visitType;
        private String filename;
        private String selectedSite;
        private String billingForm;
        private String visitLocation;
        private String sortName;
        private String sortOrder;
        private String paymentStartDate;
        private String paymentEndDate;

        public Builder teamBillingOnly(boolean v) { this.teamBillingOnly = v; return this; }
        public Builder siteAccessPrivacy(boolean v) { this.siteAccessPrivacy = v; return this; }
        public Builder multisites(boolean v) { this.multisites = v; return this; }
        public Builder hideName(boolean v) { this.hideName = v; return this; }
        public Builder search(boolean v) { this.search = v; return this; }
        // Defensive copy at the setter so callers retaining the original
        // mutable List/array can't influence builder state. The String[]
        // variant uses List.of(...) which is varargs over the array — it
        // rejects null elements (NPE), but billType param values from
        // request.getParameterValues never return nulls in practice. If a
        // future caller needs null-tolerance, switch the array overload to
        // Arrays.stream(v).filter(Objects::nonNull).toList().
        public Builder billTypes(List<String> v) { this.billTypes = v == null ? null : List.copyOf(v); return this; }
        public Builder billTypes(String[] v) { this.billTypes = v == null ? null : List.of(v); return this; }
        public Builder statusType(String v) { this.statusType = v; return this; }
        public Builder providerNo(String v) { this.providerNo = v; return this; }
        public Builder providerOhipNo(String v) { this.providerOhipNo = v; return this; }
        public Builder startDate(String v) { this.startDate = v; return this; }
        public Builder endDate(String v) { this.endDate = v; return this; }
        public Builder demoNo(String v) { this.demoNo = v; return this; }
        public Builder serviceCode(String v) { this.serviceCode = v; return this; }
        public Builder raCode(String v) { this.raCode = v; return this; }
        public Builder claimNo(String v) { this.claimNo = v; return this; }
        public Builder dx(String v) { this.dx = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder filename(String v) { this.filename = v; return this; }
        public Builder selectedSite(String v) { this.selectedSite = v; return this; }
        public Builder billingForm(String v) { this.billingForm = v; return this; }
        public Builder visitLocation(String v) { this.visitLocation = v; return this; }
        public Builder sortName(String v) { this.sortName = v; return this; }
        public Builder sortOrder(String v) { this.sortOrder = v; return this; }
        public Builder paymentStartDate(String v) { this.paymentStartDate = v; return this; }
        public Builder paymentEndDate(String v) { this.paymentEndDate = v; return this; }

        public BillingONStatusViewModel build() {
            return new BillingONStatusViewModel(this);
        }
    }
}
