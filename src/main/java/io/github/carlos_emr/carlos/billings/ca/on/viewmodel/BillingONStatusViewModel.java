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

/**
 * Immutable view model for {@code billingONStatus.jsp}.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingONStatus2Action}
 * and exposed as request attribute {@code statusModel}. Captures the request
 * parameter echoes + default-value resolution that previously lived in the top
 * scriptlet of the JSP, together with all rendering data (provider list,
 * multisite site/provider HTML, billing forms, visit locations, rejected-bill
 * rows, and aggregated bill rows with computed totals).</p>
 *
 * @since 2026-04-25
 */
public final class BillingONStatusViewModel {

    /** Default bill types when none are selected (matches legacy scriptlet). */
    public static final List<String> DEFAULT_BILL_TYPES = List.of(
            "HCP", "WCB", "RMB", "NOT", "PAT", "OCF", "ODS", "CPP", "STD", "IFH");

    /** A {@code provider_no|last|first|ohip_no}-derived dropdown row. */
    public record ProviderOption(String providerNo, String lastName, String firstName, String ohipNo) { }

    /** Visit-location dropdown entry. */
    public record VisitLocationOption(String code, String label) { }

    /** Billing form dropdown entry (label/value from {@code BillingStatusLoader}). */
    public record BillingFormOption(String label, String value) { }

    /** Rejected-bill row (statusType "_") rendered as one {@code <tr>}. */
    public record RejectedBillRow(
            String id,
            String hin,
            String ver,
            String dob,
            String billingNo,
            String refNo,
            String facility,
            String admittedDate,
            String claimError,
            String code,
            String formattedFee,
            String unit,
            String codeDate,
            String dx,
            String exp,
            String codeError,
            String reportName,
            boolean checked,
            String rowClass) { }

    /** Standard bill row with all derived fields pre-computed. */
    public record BillRow(
            String id,
            String billingDate,
            String demographicNo,
            String demographicName,
            String facilityNum,
            String status,
            String settleDate,
            String code,
            String billed,
            String amountPaid,
            String adjustment,
            String recId,
            String payProgram,
            String invoiceNo,
            String errorCode,
            String cash,
            String debit,
            int qty,
            String providerName,
            String clinic,
            String clinicBgColor,
            String clinicShortName,
            boolean newInvoice,
            boolean thirdParty,
            String rowClass) { }

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
    private final String endDateMinus30;
    private final String endDateMinus60;
    private final String endDateMinus90;

    // Render-only derived data (assembler-populated)
    private final List<ProviderOption> providers;
    /** Aggregated multisite slice — Status populates sites + providerHtml only;
     *  other fields default to empty. The legacy {@code multisites} flag stays
     *  on the top-level fields for back-compat. */
    private final BillingMultisiteContext multisite;
    private final Map<String, String> siteBgColor;
    private final Map<String, String> siteShortName;
    private final List<BillingFormOption> billingForms;
    private final List<VisitLocationOption> visitLocations;
    private final List<RejectedBillRow> rejectedBillRows;
    private final List<BillRow> billRows;
    private final int patientCount;
    private final String totalBilled;
    private final String totalPaid;
    private final String totalAdjustments;
    private final String totalCash;
    private final String totalDebit;
    private final Map<String, String> requestParamEchoes;

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
        this.endDateMinus30 = nullToEmpty(b.endDateMinus30);
        this.endDateMinus60 = nullToEmpty(b.endDateMinus60);
        this.endDateMinus90 = nullToEmpty(b.endDateMinus90);

        this.providers = b.providers == null
                ? Collections.emptyList() : List.copyOf(b.providers);
        // Status's multisite scope is multisites flag + sites + providerHtml only.
        this.multisite = (b.multisite != null)
                ? b.multisite
                : new BillingMultisiteContext(
                        b.multisites,
                        b.multisiteSites,
                        "", "", "",
                        b.multisiteProviderHtml,
                        false,
                        Collections.emptyList(),
                        "");
        this.siteBgColor = b.siteBgColor == null
                ? Collections.emptyMap() : Map.copyOf(b.siteBgColor);
        this.siteShortName = b.siteShortName == null
                ? Collections.emptyMap() : Map.copyOf(b.siteShortName);
        this.billingForms = b.billingForms == null
                ? Collections.emptyList() : List.copyOf(b.billingForms);
        this.visitLocations = b.visitLocations == null
                ? Collections.emptyList() : List.copyOf(b.visitLocations);
        this.rejectedBillRows = b.rejectedBillRows == null
                ? Collections.emptyList() : List.copyOf(b.rejectedBillRows);
        this.billRows = b.billRows == null
                ? Collections.emptyList() : List.copyOf(b.billRows);
        this.patientCount = b.patientCount;
        this.totalBilled = nullToEmpty(b.totalBilled);
        this.totalPaid = nullToEmpty(b.totalPaid);
        this.totalAdjustments = nullToEmpty(b.totalAdjustments);
        this.totalCash = nullToEmpty(b.totalCash);
        this.totalDebit = nullToEmpty(b.totalDebit);
        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
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
    public String getEndDateMinus30() { return endDateMinus30; }
    public String getEndDateMinus60() { return endDateMinus60; }
    public String getEndDateMinus90() { return endDateMinus90; }

    public List<ProviderOption> getProviders() { return providers; }
    /** Aggregated multisite slice — primary internal storage. */
    public BillingMultisiteContext getMultisite() { return multisite; }
    public List<BillingMultisiteContext.MultisiteSite> getMultisiteSites() { return multisite.sites(); }
    public Map<String, String> getMultisiteProviderHtml() { return multisite.multisiteProviderHtml(); }
    public Map<String, String> getSiteBgColor() { return siteBgColor; }
    public Map<String, String> getSiteShortName() { return siteShortName; }
    public List<BillingFormOption> getBillingForms() { return billingForms; }
    public List<VisitLocationOption> getVisitLocations() { return visitLocations; }
    public List<RejectedBillRow> getRejectedBillRows() { return rejectedBillRows; }
    public List<BillRow> getBillRows() { return billRows; }
    public int getPatientCount() { return patientCount; }
    public String getTotalBilled() { return totalBilled; }
    public String getTotalPaid() { return totalPaid; }
    public String getTotalAdjustments() { return totalAdjustments; }
    public String getTotalCash() { return totalCash; }
    public String getTotalDebit() { return totalDebit; }
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }

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
        private String endDateMinus30;
        private String endDateMinus60;
        private String endDateMinus90;

        private List<ProviderOption> providers;
        private BillingMultisiteContext multisite;
        private List<BillingMultisiteContext.MultisiteSite> multisiteSites;
        private Map<String, String> multisiteProviderHtml;
        private Map<String, String> siteBgColor;
        private Map<String, String> siteShortName;
        private List<BillingFormOption> billingForms;
        private List<VisitLocationOption> visitLocations;
        private List<RejectedBillRow> rejectedBillRows;
        private List<BillRow> billRows;
        private int patientCount;
        private String totalBilled;
        private String totalPaid;
        private String totalAdjustments;
        private String totalCash;
        private String totalDebit;
        private Map<String, String> requestParamEchoes;

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
        public Builder endDateMinus30(String v) { this.endDateMinus30 = v; return this; }
        public Builder endDateMinus60(String v) { this.endDateMinus60 = v; return this; }
        public Builder endDateMinus90(String v) { this.endDateMinus90 = v; return this; }

        public Builder providers(List<ProviderOption> v) {
            this.providers = v == null ? null : List.copyOf(v);
            return this;
        }
        /** Composed multisite-context setter (preferred over the slice-by-slice setters). */
        public Builder multisite(BillingMultisiteContext v) { this.multisite = v; return this; }
        public Builder multisiteSites(List<BillingMultisiteContext.MultisiteSite> v) {
            this.multisiteSites = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder multisiteProviderHtml(Map<String, String> v) {
            this.multisiteProviderHtml = v == null ? null : Map.copyOf(v);
            return this;
        }
        public Builder siteBgColor(Map<String, String> v) {
            this.siteBgColor = v == null ? null : Map.copyOf(v);
            return this;
        }
        public Builder siteShortName(Map<String, String> v) {
            this.siteShortName = v == null ? null : Map.copyOf(v);
            return this;
        }
        public Builder billingForms(List<BillingFormOption> v) {
            this.billingForms = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder visitLocations(List<VisitLocationOption> v) {
            this.visitLocations = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder rejectedBillRows(List<RejectedBillRow> v) {
            this.rejectedBillRows = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder billRows(List<BillRow> v) {
            this.billRows = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder patientCount(int v) { this.patientCount = v; return this; }
        public Builder totalBilled(String v) { this.totalBilled = v; return this; }
        public Builder totalPaid(String v) { this.totalPaid = v; return this; }
        public Builder totalAdjustments(String v) { this.totalAdjustments = v; return this; }
        public Builder totalCash(String v) { this.totalCash = v; return this; }
        public Builder totalDebit(String v) { this.totalDebit = v; return this; }
        public Builder requestParamEchoes(Map<String, String> v) {
            this.requestParamEchoes = v == null ? null : Map.copyOf(v);
            return this;
        }

        public BillingONStatusViewModel build() {
            return new BillingONStatusViewModel(this);
        }
    }
}
