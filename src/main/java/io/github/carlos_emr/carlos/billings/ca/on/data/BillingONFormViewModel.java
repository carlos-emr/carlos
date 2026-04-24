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

/**
 * Immutable view model for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Assembled by {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONView2Action}
 * and exposed to the JSP as request attribute {@code model}. Fields are added
 * incrementally as their corresponding scriptlet blocks are migrated out of the
 * JSP. The form previously built this data inline via ~24 DAO lookups, which
 * pushed the rendered response past the 1 MB page buffer.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONFormViewModel {

    private final String demographicNo;
    private final String appointmentNo;
    private final String providerNo;
    private final String apptProviderNo;
    private final String providerView;
    private final String billReferenceDate;
    private final String demoLast;
    private final String demoFirst;
    private final String demoHin;
    private final String demoVer;
    private final String demoDob;
    private final String demoHcType;
    private final String demoSex;
    private final String familyDoctor;
    private final String rosterStatus;
    private final String referralDoctor;
    private final String referralDoctorOhip;
    private final String warningMsg;
    private final String errorMsg;
    private final String errorFlag;
    private final List<String> patientDx;
    private final Map<String, String> requestEchoes;

    private BillingONFormViewModel(Builder b) {
        this.demographicNo = b.demographicNo;
        this.appointmentNo = b.appointmentNo;
        this.providerNo = b.providerNo;
        this.apptProviderNo = b.apptProviderNo;
        this.providerView = b.providerView;
        this.billReferenceDate = b.billReferenceDate;
        this.demoLast = b.demoLast;
        this.demoFirst = b.demoFirst;
        this.demoHin = b.demoHin;
        this.demoVer = b.demoVer;
        this.demoDob = b.demoDob;
        this.demoHcType = b.demoHcType;
        this.demoSex = b.demoSex;
        this.familyDoctor = b.familyDoctor;
        this.rosterStatus = b.rosterStatus;
        this.referralDoctor = b.referralDoctor;
        this.referralDoctorOhip = b.referralDoctorOhip;
        this.warningMsg = b.warningMsg;
        this.errorMsg = b.errorMsg;
        this.errorFlag = b.errorFlag;
        this.patientDx = b.patientDx == null ? Collections.emptyList() : List.copyOf(b.patientDx);
        this.requestEchoes = b.requestEchoes == null ? Collections.emptyMap() : Map.copyOf(b.requestEchoes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDemographicNo() { return demographicNo; }
    public String getAppointmentNo() { return appointmentNo; }
    public String getProviderNo() { return providerNo; }
    public String getApptProviderNo() { return apptProviderNo; }
    public String getProviderView() { return providerView; }
    public String getBillReferenceDate() { return billReferenceDate; }
    public String getDemoLast() { return demoLast; }
    public String getDemoFirst() { return demoFirst; }
    public String getDemoHin() { return demoHin; }
    public String getDemoVer() { return demoVer; }
    public String getDemoDob() { return demoDob; }
    public String getDemoHcType() { return demoHcType; }
    public String getDemoSex() { return demoSex; }
    public String getFamilyDoctor() { return familyDoctor; }
    public String getRosterStatus() { return rosterStatus; }
    public String getReferralDoctor() { return referralDoctor; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getWarningMsg() { return warningMsg; }
    public String getErrorMsg() { return errorMsg; }
    public String getErrorFlag() { return errorFlag; }
    public List<String> getPatientDx() { return patientDx; }
    public Map<String, String> getRequestEchoes() { return requestEchoes; }

    public static final class Builder {
        private String demographicNo;
        private String appointmentNo;
        private String providerNo;
        private String apptProviderNo;
        private String providerView;
        private String billReferenceDate;
        private String demoLast;
        private String demoFirst;
        private String demoHin;
        private String demoVer;
        private String demoDob;
        private String demoHcType;
        private String demoSex;
        private String familyDoctor;
        private String rosterStatus;
        private String referralDoctor;
        private String referralDoctorOhip;
        private String warningMsg;
        private String errorMsg;
        private String errorFlag;
        private List<String> patientDx;
        private Map<String, String> requestEchoes;

        public Builder demographicNo(String v) { this.demographicNo = v; return this; }
        public Builder appointmentNo(String v) { this.appointmentNo = v; return this; }
        public Builder providerNo(String v) { this.providerNo = v; return this; }
        public Builder apptProviderNo(String v) { this.apptProviderNo = v; return this; }
        public Builder providerView(String v) { this.providerView = v; return this; }
        public Builder billReferenceDate(String v) { this.billReferenceDate = v; return this; }
        public Builder demoLast(String v) { this.demoLast = v; return this; }
        public Builder demoFirst(String v) { this.demoFirst = v; return this; }
        public Builder demoHin(String v) { this.demoHin = v; return this; }
        public Builder demoVer(String v) { this.demoVer = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoHcType(String v) { this.demoHcType = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder familyDoctor(String v) { this.familyDoctor = v; return this; }
        public Builder rosterStatus(String v) { this.rosterStatus = v; return this; }
        public Builder referralDoctor(String v) { this.referralDoctor = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder warningMsg(String v) { this.warningMsg = v; return this; }
        public Builder errorMsg(String v) { this.errorMsg = v; return this; }
        public Builder errorFlag(String v) { this.errorFlag = v; return this; }
        public Builder patientDx(List<String> v) { this.patientDx = v; return this; }
        public Builder requestEchoes(Map<String, String> v) { this.requestEchoes = v; return this; }

        public BillingONFormViewModel build() {
            return new BillingONFormViewModel(this);
        }
    }
}
