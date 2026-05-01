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
 * Immutable view model for {@code billingONDisplay.jsp} (the
 * "Billing Correction" form). Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnDisplayViewModelAssembler}
 * and exposed as request attribute {@code displayModel}.
 *
 * <p>Captures the request-parameter echoes, the resolved bill-header data,
 * the rejected/explanatory error codes, the dropdown source lists
 * (HCType is a static list rendered by the JSP, while payment types,
 * provider list, clinic numbers, visit-type dropdowns and facility
 * locations are pre-assembled here), and the service-code rows (one per
 * existing item plus a single empty trailing row).</p>
 *
 * @since 2026-04-25
 */
public final class BillingOnDisplayViewModel {

    /** A {@code (label, value)} pair from {@code BillingOnConstants.paymentTypeOptions}. */
    public record PaymentTypeOption(String value, String label) { }

    /** A provider dropdown option ({@code provider_no | last | first}). */
    public record ProviderOption(String providerNo, String lastName, String firstName) { }

    /** A facility/clinic location row ({@code (number, label)}). */
    public record LocationOption(String number, String label) { }

    /** A clinic-number visit-type entry ({@code valueString = "value | string"}). */
    public record ClinicNbrOption(String value, String valueString) { }

    /** A "rejected/explanatory" RA error-code descriptor. */
    public record ErrorCode(String code, String description) { }

    /**
     * One existing-billing-item row pre-resolved into UI-ready strings.
     * The {@code rowIndex} is 1-based to mirror the legacy {@code rowCount}
     * variable; the JSP uses {@code rowIndex - 1} for the form input names.
     */
    public record ServiceItemRow(
            int rowIndex,
            String serviceCode,
            String serviceDesc,
            String billAmount,
            String billingUnit,
            String diagCode,
            boolean settled) { }

    private final boolean billPresent;
    private final boolean rmaEnabled;
    private final String billingNo;

    // Resolved header fields (empty strings when no bill is present)
    private final String updateDate;
    private final String demoNo;
    private final String demoName;
    private final String demoDOB;
    private final String demoSex;
    private final String hcType;
    private final String hcSex;
    private final String hin;
    private final String location;
    private final String billDate;
    private final String provider;
    private final String billType;
    private final String payProgram;
    private final String visitDate;
    private final String visitType;
    private final String mReview;
    private final String comment;
    private final String rDoctor;
    private final String rDoctorOhip;
    private final String diagCode;

    // Pre-assembled lists / dropdowns
    private final List<ErrorCode> errorCodes;
    private final List<PaymentTypeOption> paymentTypes;
    private final List<LocationOption> locations;
    private final List<ProviderOption> providers;
    private final List<ClinicNbrOption> clinicNbrs;
    private final List<ServiceItemRow> serviceRows;
    private final int trailingRowIndex;

    private final Map<String, String> requestParamEchoes;

    private BillingOnDisplayViewModel(Builder b) {
        this.billPresent = b.billPresent;
        this.rmaEnabled = b.rmaEnabled;
        this.billingNo = BillingViewStrings.nullToEmpty(b.billingNo);
        this.updateDate = BillingViewStrings.nullToEmpty(b.updateDate);
        this.demoNo = BillingViewStrings.nullToEmpty(b.demoNo);
        this.demoName = BillingViewStrings.nullToEmpty(b.demoName);
        this.demoDOB = BillingViewStrings.nullToEmpty(b.demoDOB);
        this.demoSex = BillingViewStrings.nullToEmpty(b.demoSex);
        this.hcType = BillingViewStrings.nullToEmpty(b.hcType);
        this.hcSex = BillingViewStrings.nullToEmpty(b.hcSex);
        this.hin = BillingViewStrings.nullToEmpty(b.hin);
        this.location = BillingViewStrings.nullToEmpty(b.location);
        this.billDate = BillingViewStrings.nullToEmpty(b.billDate);
        this.provider = BillingViewStrings.nullToEmpty(b.provider);
        this.billType = BillingViewStrings.nullToEmpty(b.billType);
        this.payProgram = BillingViewStrings.nullToEmpty(b.payProgram);
        this.visitDate = BillingViewStrings.nullToEmpty(b.visitDate);
        this.visitType = BillingViewStrings.nullToEmpty(b.visitType);
        this.mReview = BillingViewStrings.nullToEmpty(b.mReview);
        this.comment = BillingViewStrings.nullToEmpty(b.comment);
        this.rDoctor = BillingViewStrings.nullToEmpty(b.rDoctor);
        this.rDoctorOhip = BillingViewStrings.nullToEmpty(b.rDoctorOhip);
        this.diagCode = BillingViewStrings.nullToEmpty(b.diagCode);

        this.errorCodes = b.errorCodes == null
                ? Collections.emptyList() : List.copyOf(b.errorCodes);
        this.paymentTypes = b.paymentTypes == null
                ? Collections.emptyList() : List.copyOf(b.paymentTypes);
        this.locations = b.locations == null
                ? Collections.emptyList() : List.copyOf(b.locations);
        this.providers = b.providers == null
                ? Collections.emptyList() : List.copyOf(b.providers);
        this.clinicNbrs = b.clinicNbrs == null
                ? Collections.emptyList() : List.copyOf(b.clinicNbrs);
        this.serviceRows = b.serviceRows == null
                ? Collections.emptyList() : List.copyOf(b.serviceRows);
        this.trailingRowIndex = b.trailingRowIndex;

        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isBillPresent() { return billPresent; }
    public boolean isRmaEnabled() { return rmaEnabled; }
    public String getBillingNo() { return billingNo; }
    public String getUpdateDate() { return updateDate; }
    public String getDemoNo() { return demoNo; }
    public String getDemoName() { return demoName; }
    public String getDemoDOB() { return demoDOB; }
    public String getDemoSex() { return demoSex; }
    public String getHCTYPE() { return hcType; }
    public String getHCSex() { return hcSex; }
    public String getHin() { return hin; }
    public String getLocation() { return location; }
    public String getBillDate() { return billDate; }
    public String getProvider() { return provider; }
    public String getBillType() { return billType; }
    public String getPayProgram() { return payProgram; }
    public String getVisitDate() { return visitDate; }
    public String getVisitType() { return visitType; }
    public String getMReview() { return mReview; }
    public String getComment() { return comment; }
    public String getRDoctor() { return rDoctor; }
    public String getRDoctorOhip() { return rDoctorOhip; }
    public String getDiagCode() { return diagCode; }

    public List<ErrorCode> getErrorCodes() { return errorCodes; }
    public List<PaymentTypeOption> getPaymentTypes() { return paymentTypes; }
    public List<LocationOption> getLocations() { return locations; }
    public List<ProviderOption> getProviders() { return providers; }
    public List<ClinicNbrOption> getClinicNbrs() { return clinicNbrs; }
    public List<ServiceItemRow> getServiceRows() { return serviceRows; }
    public int getTrailingRowIndex() { return trailingRowIndex; }
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }

    public static final class Builder {
        private boolean billPresent;
        private boolean rmaEnabled;
        private String billingNo;
        private String updateDate;
        private String demoNo;
        private String demoName;
        private String demoDOB;
        private String demoSex;
        private String hcType;
        private String hcSex;
        private String hin;
        private String location;
        private String billDate;
        private String provider;
        private String billType;
        private String payProgram;
        private String visitDate;
        private String visitType;
        private String mReview;
        private String comment;
        private String rDoctor;
        private String rDoctorOhip;
        private String diagCode;
        private List<ErrorCode> errorCodes;
        private List<PaymentTypeOption> paymentTypes;
        private List<LocationOption> locations;
        private List<ProviderOption> providers;
        private List<ClinicNbrOption> clinicNbrs;
        private List<ServiceItemRow> serviceRows;
        private int trailingRowIndex;
        private Map<String, String> requestParamEchoes;

        public Builder billPresent(boolean v) { this.billPresent = v; return this; }
        public Builder rmaEnabled(boolean v) { this.rmaEnabled = v; return this; }
        public Builder billingNo(String v) { this.billingNo = v; return this; }
        public Builder updateDate(String v) { this.updateDate = v; return this; }
        public Builder demoNo(String v) { this.demoNo = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder demoDOB(String v) { this.demoDOB = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder hcType(String v) { this.hcType = v; return this; }
        public Builder hcSex(String v) { this.hcSex = v; return this; }
        public Builder hin(String v) { this.hin = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder billDate(String v) { this.billDate = v; return this; }
        public Builder provider(String v) { this.provider = v; return this; }
        public Builder billType(String v) { this.billType = v; return this; }
        public Builder payProgram(String v) { this.payProgram = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder mReview(String v) { this.mReview = v; return this; }
        public Builder comment(String v) { this.comment = v; return this; }
        public Builder rDoctor(String v) { this.rDoctor = v; return this; }
        public Builder rDoctorOhip(String v) { this.rDoctorOhip = v; return this; }
        public Builder diagCode(String v) { this.diagCode = v; return this; }
        public Builder errorCodes(List<ErrorCode> v) {
            this.errorCodes = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder paymentTypes(List<PaymentTypeOption> v) {
            this.paymentTypes = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder locations(List<LocationOption> v) {
            this.locations = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder providers(List<ProviderOption> v) {
            this.providers = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder clinicNbrs(List<ClinicNbrOption> v) {
            this.clinicNbrs = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder serviceRows(List<ServiceItemRow> v) {
            this.serviceRows = v == null ? null : List.copyOf(v);
            return this;
        }
        public Builder trailingRowIndex(int v) { this.trailingRowIndex = v; return this; }
        public Builder requestParamEchoes(Map<String, String> v) {
            this.requestParamEchoes = v == null ? null : Map.copyOf(v);
            return this;
        }

        public BillingOnDisplayViewModel build() {
            return new BillingOnDisplayViewModel(this);
        }
    }
}
