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
 * Immutable view model for {@code billing/CA/ON/billingEditWithApptNo.jsp},
 * the auto-submitting bridge form fired from
 * {@code provider/appointmentprovideradminday.jsp} when the operator clicks
 * "edit billing" on an appointment row. The JSP renders ~25 hidden inputs
 * out of the assembled state, plus a per-{@link io.github.carlos_emr.carlos.commn.model.BillingONItem}
 * service-code/unit pair derived from the bill's active line items.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingEditWithApptNoDataAssembler}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingEditWithApptNo2Action})
 * and exposed as request attribute {@code editApptModel}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingEditWithApptNoViewModel {

    /**
     * {@code true} when the loaded bill's {@code status} starts with "B"
     * (billed). The legacy JSP rendered an "Sorry, cannot delete billed
     * items" page in that case instead of the auto-submit form.
     */
    private final boolean billedItemBlocked;

    // Pass-through request parameters (echoed into hidden form fields)
    private final String apptProviderNo;
    private final String providerView;
    private final String appointmentDate;
    private final String demographicName;
    private final String appointmentNo;
    private final String demographicNo;
    private final String startTime;
    private final String billForm;

    // Fields derived from the loaded BillingClaimHeader1Data + BillingItemData
    private final String billNo;
    private final String status;
    private final String visitDate;
    private final String visitType;
    private final String location;
    private final String billingDate;
    private final String clinicNo;
    private final String asstProviderNo;
    private final String assgProviderNo;
    private final String mReview;
    private final String xmlProvider;
    private final String referralCode;
    private final String site;
    private final String xmlBilltype;
    private final String demoHin;
    private final String demoVer;
    private final String demoHcType;
    private final String demoDob;
    private final String demoName;
    private final String serviceDate;
    private final String serviceCode;
    private final String dxCode;
    private final String dxCode1;
    private final String dxCode2;
    private final String curBillForm;

    private final List<HiddenServiceField> serviceFields;
    private final int servicesCheckedNum;

    private BillingEditWithApptNoViewModel(Builder b) {
        this.billedItemBlocked = b.billedItemBlocked;
        this.apptProviderNo = nullToEmpty(b.apptProviderNo);
        this.providerView = nullToEmpty(b.providerView);
        this.appointmentDate = nullToEmpty(b.appointmentDate);
        this.demographicName = nullToEmpty(b.demographicName);
        this.appointmentNo = nullToEmpty(b.appointmentNo);
        this.demographicNo = nullToEmpty(b.demographicNo);
        this.startTime = nullToEmpty(b.startTime);
        this.billForm = nullToEmpty(b.billForm);
        this.billNo = nullToEmpty(b.billNo);
        this.status = nullToEmpty(b.status);
        this.visitDate = nullToEmpty(b.visitDate);
        this.visitType = nullToEmpty(b.visitType);
        this.location = nullToEmpty(b.location);
        this.billingDate = nullToEmpty(b.billingDate);
        this.clinicNo = nullToEmpty(b.clinicNo);
        this.asstProviderNo = nullToEmpty(b.asstProviderNo);
        this.assgProviderNo = nullToEmpty(b.assgProviderNo);
        this.mReview = nullToEmpty(b.mReview);
        this.xmlProvider = nullToEmpty(b.xmlProvider);
        this.referralCode = nullToEmpty(b.referralCode);
        this.site = nullToEmpty(b.site);
        this.xmlBilltype = nullToEmpty(b.xmlBilltype);
        this.demoHin = nullToEmpty(b.demoHin);
        this.demoVer = nullToEmpty(b.demoVer);
        this.demoHcType = nullToEmpty(b.demoHcType);
        this.demoDob = nullToEmpty(b.demoDob);
        this.demoName = nullToEmpty(b.demoName);
        this.serviceDate = nullToEmpty(b.serviceDate);
        this.serviceCode = nullToEmpty(b.serviceCode);
        this.dxCode = nullToEmpty(b.dxCode);
        this.dxCode1 = nullToEmpty(b.dxCode1);
        this.dxCode2 = nullToEmpty(b.dxCode2);
        this.curBillForm = nullToEmpty(b.curBillForm);
        this.serviceFields = b.serviceFields == null
                ? Collections.<HiddenServiceField>emptyList()
                : Collections.unmodifiableList(b.serviceFields);
        this.servicesCheckedNum = b.servicesCheckedNum;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public boolean isBilledItemBlocked() { return billedItemBlocked; }
    public String getApptProviderNo() { return apptProviderNo; }
    public String getProviderView() { return providerView; }
    public String getAppointmentDate() { return appointmentDate; }
    public String getDemographicName() { return demographicName; }
    public String getAppointmentNo() { return appointmentNo; }
    public String getDemographicNo() { return demographicNo; }
    public String getStartTime() { return startTime; }
    public String getBillForm() { return billForm; }
    public String getBillNo() { return billNo; }
    public String getStatus() { return status; }
    public String getVisitDate() { return visitDate; }
    public String getVisitType() { return visitType; }
    public String getLocation() { return location; }
    public String getBillingDate() { return billingDate; }
    public String getClinicNo() { return clinicNo; }
    public String getAsstProviderNo() { return asstProviderNo; }
    public String getAssgProviderNo() { return assgProviderNo; }
    public String getMReview() { return mReview; }
    public String getXmlProvider() { return xmlProvider; }
    public String getReferralCode() { return referralCode; }
    public String getSite() { return site; }
    public String getXmlBilltype() { return xmlBilltype; }
    public String getDemoHin() { return demoHin; }
    public String getDemoVer() { return demoVer; }
    public String getDemoHcType() { return demoHcType; }
    public String getDemoDob() { return demoDob; }
    public String getDemoName() { return demoName; }
    public String getServiceDate() { return serviceDate; }
    public String getServiceCode() { return serviceCode; }
    public String getDxCode() { return dxCode; }
    public String getDxCode1() { return dxCode1; }
    public String getDxCode2() { return dxCode2; }
    public String getCurBillForm() { return curBillForm; }
    public List<HiddenServiceField> getServiceFields() { return serviceFields; }
    public int getServicesCheckedNum() { return servicesCheckedNum; }

    /**
     * One hidden-service-field record. Mirrors the legacy if/else inside the
     * BillingONItem loop: when the count is "1" the JSP emits a single
     * {@code xml_<serviceCode>=checked} hidden input (and increments
     * servicesCheckedNum); otherwise it emits a {@code serviceCode<i>} +
     * {@code serviceUnit<i>} pair.
     */
    public static final class HiddenServiceField {
        /** {@code true} for the "checked" variant; {@code false} for the code/unit variant. */
        private final boolean checkedVariant;
        private final String name;
        private final String value;
        private final String unitName;
        private final String unitValue;

        public static HiddenServiceField checked(String xmlServiceCode) {
            return new HiddenServiceField(true, xmlServiceCode, "checked", null, null);
        }

        public static HiddenServiceField pair(String codeName, String codeValue, String unitName, String unitValue) {
            return new HiddenServiceField(false, codeName, codeValue, unitName, unitValue);
        }

        private HiddenServiceField(boolean checkedVariant, String name, String value, String unitName, String unitValue) {
            this.checkedVariant = checkedVariant;
            this.name = nullToEmpty(name);
            this.value = nullToEmpty(value);
            this.unitName = nullToEmpty(unitName);
            this.unitValue = nullToEmpty(unitValue);
        }

        public boolean isCheckedVariant() { return checkedVariant; }
        public String getName() { return name; }
        public String getValue() { return value; }
        public String getUnitName() { return unitName; }
        public String getUnitValue() { return unitValue; }
    }

    public static final class Builder {
        private boolean billedItemBlocked;
        private String apptProviderNo;
        private String providerView;
        private String appointmentDate;
        private String demographicName;
        private String appointmentNo;
        private String demographicNo;
        private String startTime;
        private String billForm;
        private String billNo;
        private String status;
        private String visitDate;
        private String visitType;
        private String location;
        private String billingDate;
        private String clinicNo;
        private String asstProviderNo;
        private String assgProviderNo;
        private String mReview;
        private String xmlProvider;
        private String referralCode;
        private String site;
        private String xmlBilltype;
        private String demoHin;
        private String demoVer;
        private String demoHcType;
        private String demoDob;
        private String demoName;
        private String serviceDate;
        private String serviceCode;
        private String dxCode;
        private String dxCode1;
        private String dxCode2;
        private String curBillForm;
        private List<HiddenServiceField> serviceFields;
        private int servicesCheckedNum;

        public Builder billedItemBlocked(boolean v) { this.billedItemBlocked = v; return this; }
        public Builder apptProviderNo(String v) { this.apptProviderNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder appointmentDate(String v) { this.appointmentDate = v; return this; }
        public Builder demographicName(String v) { this.demographicName = v; return this; }
        public Builder appointmentNo(String v) { this.appointmentNo = v; return this; }
        public Builder demographicNo(String v) { this.demographicNo = v; return this; }
        public Builder startTime(String v) { this.startTime = v; return this; }
        public Builder billForm(String v) { this.billForm = v; return this; }
        public Builder billNo(String v) { this.billNo = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder billingDate(String v) { this.billingDate = v; return this; }
        public Builder clinicNo(String v) { this.clinicNo = v; return this; }
        public Builder asstProviderNo(String v) { this.asstProviderNo = v; return this; }
        public Builder assgProviderNo(String v) { this.assgProviderNo = v; return this; }
        public Builder mReview(String v) { this.mReview = v; return this; }
        public Builder xmlProvider(String v) { this.xmlProvider = v; return this; }
        public Builder referralCode(String v) { this.referralCode = v; return this; }
        public Builder site(String v) { this.site = v; return this; }
        public Builder xmlBilltype(String v) { this.xmlBilltype = v; return this; }
        public Builder demoHin(String v) { this.demoHin = v; return this; }
        public Builder demoVer(String v) { this.demoVer = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder serviceDate(String v) { this.serviceDate = v; return this; }
        public Builder serviceCode(String v) { this.serviceCode = v; return this; }
        public Builder dxCode(String v) { this.dxCode = v; return this; }
        public Builder dxCode1(String v) { this.dxCode1 = v; return this; }
        public Builder dxCode2(String v) { this.dxCode2 = v; return this; }
        public Builder curBillForm(String v) { this.curBillForm = v; return this; }
        public Builder serviceFields(List<HiddenServiceField> v) { this.serviceFields = v; return this; }
        public Builder servicesCheckedNum(int v) { this.servicesCheckedNum = v; return this; }

        public BillingEditWithApptNoViewModel build() { return new BillingEditWithApptNoViewModel(this); }
    }
}
