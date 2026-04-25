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
        this.statusType = b.statusType;
        this.providerNo = b.providerNo;
        this.providerOhipNo = b.providerOhipNo;
        this.startDate = b.startDate;
        this.endDate = b.endDate;
        this.demoNo = b.demoNo;
        this.serviceCode = b.serviceCode;
        this.raCode = b.raCode;
        this.claimNo = b.claimNo;
        this.dx = b.dx;
        this.visitType = b.visitType;
        this.filename = b.filename;
        this.selectedSite = b.selectedSite;
        this.billingForm = b.billingForm;
        this.visitLocation = b.visitLocation;
        this.sortName = b.sortName;
        this.sortOrder = b.sortOrder;
        this.paymentStartDate = b.paymentStartDate;
        this.paymentEndDate = b.paymentEndDate;
    }

    public static Builder builder() {
        return new Builder();
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
        // variant routes through Arrays.asList(...) (which produces a fixed-
        // size view) and then List.copyOf to get a true unmodifiable copy.
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
