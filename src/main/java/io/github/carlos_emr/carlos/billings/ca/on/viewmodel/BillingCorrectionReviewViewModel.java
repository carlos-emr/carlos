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

/**
 * Immutable view model for {@code billing/CA/ON/billingCorrectionReview.jsp},
 * the read-only "Correction Review" preview that confirms a correction's
 * pending changes before they are POSTed to
 * {@code BillingCorrectionSubmit2Action}.
 *
 * <p>This view model captures the projected fields the JSP renders and the
 * typed hidden-field payload posted to the submit action.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCorrectionReviewViewModelAssembler}
 * (invoked from the correction preparation service)
 * and exposed as request attribute {@code reviewModel}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingCorrectionReviewViewModel {

    private final boolean dataLoaded;

    // Patient.
    private final String demoName;
    private final String hin;
    private final String demoSex;
    private final String demoDob;
    private final String demoAddress;
    private final String demoCity;
    private final String demoProvince;
    private final String demoPostal;
    private final String referralDoctor;
    private final String referralDoctorOhip;

    // Additional info.
    private final String hcType;
    private final String manualReviewLabel;
    private final String referralCheckedLabel;
    private final String rosterStatus;

    // Billing info
    private final String billingType;
    private final String billingDate;
    private final String visitLocation;
    private final String billingPhysicianNo;
    private final String visitType;
    private final String visitDate;
    private final String updateDate;
    private final String billingNo;

    // Items + total
    private final List<Item> billingItems;
    private final String diagCode;
    private final String formattedTotal;
    private final String content;
    private final String storedTotal;

    private BillingCorrectionReviewViewModel(Builder b) {
        this.dataLoaded = b.dataLoaded;
        this.demoName = nullToEmpty(b.demoName);
        this.hin = nullToEmpty(b.hin);
        this.demoSex = nullToEmpty(b.demoSex);
        this.demoDob = nullToEmpty(b.demoDob);
        this.demoAddress = nullToEmpty(b.demoAddress);
        this.demoCity = nullToEmpty(b.demoCity);
        this.demoProvince = nullToEmpty(b.demoProvince);
        this.demoPostal = nullToEmpty(b.demoPostal);
        this.referralDoctor = nullToEmpty(b.referralDoctor);
        this.referralDoctorOhip = nullToEmpty(b.referralDoctorOhip);
        this.hcType = nullToEmpty(b.hcType);
        this.manualReviewLabel = nullToEmpty(b.manualReviewLabel);
        this.referralCheckedLabel = nullToEmpty(b.referralCheckedLabel);
        this.rosterStatus = nullToEmpty(b.rosterStatus);
        this.billingType = nullToEmpty(b.billingType);
        this.billingDate = nullToEmpty(b.billingDate);
        this.visitLocation = nullToEmpty(b.visitLocation);
        this.billingPhysicianNo = nullToEmpty(b.billingPhysicianNo);
        this.visitType = nullToEmpty(b.visitType);
        this.visitDate = nullToEmpty(b.visitDate);
        this.updateDate = nullToEmpty(b.updateDate);
        this.billingNo = nullToEmpty(b.billingNo);
        this.billingItems = b.billingItems == null
                ? Collections.<Item>emptyList()
                : Collections.unmodifiableList(b.billingItems);
        this.diagCode = nullToEmpty(b.diagCode);
        this.formattedTotal = nullToEmpty(b.formattedTotal);
        this.content = nullToEmpty(b.content);
        this.storedTotal = nullToEmpty(b.storedTotal);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public boolean isDataLoaded() { return dataLoaded; }
    public String getDemoName() { return demoName; }
    public String getHin() { return hin; }
    public String getDemoSex() { return demoSex; }
    public String getDemoDob() { return demoDob; }
    public String getDemoAddress() { return demoAddress; }
    public String getDemoCity() { return demoCity; }
    public String getDemoProvince() { return demoProvince; }
    public String getDemoPostal() { return demoPostal; }
    public String getReferralDoctor() { return referralDoctor; }
    public String getReferralDoctorOhip() { return referralDoctorOhip; }
    public String getHcType() { return hcType; }
    public String getManualReviewLabel() { return manualReviewLabel; }
    public String getReferralCheckedLabel() { return referralCheckedLabel; }
    public String getRosterStatus() { return rosterStatus; }
    public String getBillingType() { return billingType; }
    public String getBillingDate() { return billingDate; }
    public String getVisitLocation() { return visitLocation; }
    public String getBillingPhysicianNo() { return billingPhysicianNo; }
    public String getVisitType() { return visitType; }
    public String getVisitDate() { return visitDate; }
    public String getUpdateDate() { return updateDate; }
    public String getBillingNo() { return billingNo; }
    public List<Item> getBillingItems() { return billingItems; }
    public String getDiagCode() { return diagCode; }
    public String getFormattedTotal() { return formattedTotal; }
    public String getContent() { return content; }
    public String getStoredTotal() { return storedTotal; }

    /** Per-line-item projection used by the review table. */
    public static final class Item {
        private final String serviceCode;
        private final String description;
        private final String quantity;
        private final String formattedFee;
        private final String storedFee;
        private final String percentage;
        private final String diagCode;

        public Item(String serviceCode, String description, String quantity, String formattedFee, String diagCode) {
            this(serviceCode, description, quantity, formattedFee, "", "", diagCode);
        }

        public Item(String serviceCode, String description, String quantity, String formattedFee,
                    String storedFee, String percentage, String diagCode) {
            this.serviceCode = nullToEmpty(serviceCode);
            this.description = nullToEmpty(description);
            this.quantity = nullToEmpty(quantity);
            this.formattedFee = nullToEmpty(formattedFee);
            this.storedFee = nullToEmpty(storedFee);
            this.percentage = nullToEmpty(percentage);
            this.diagCode = nullToEmpty(diagCode);
        }

        public String getServiceCode() { return serviceCode; }
        public String getDescription() { return description; }
        public String getQuantity() { return quantity; }
        public String getFormattedFee() { return formattedFee; }
        public String getStoredFee() { return storedFee; }
        public String getPercentage() { return percentage; }
        public String getDiagCode() { return diagCode; }
    }

    public static final class Builder {
        private boolean dataLoaded;
        private String demoName;
        private String hin;
        private String demoSex;
        private String demoDob;
        private String demoAddress;
        private String demoCity;
        private String demoProvince;
        private String demoPostal;
        private String referralDoctor;
        private String referralDoctorOhip;
        private String hcType;
        private String manualReviewLabel;
        private String referralCheckedLabel;
        private String rosterStatus;
        private String billingType;
        private String billingDate;
        private String visitLocation;
        private String billingPhysicianNo;
        private String visitType;
        private String visitDate;
        private String updateDate;
        private String billingNo;
        private List<Item> billingItems;
        private String diagCode;
        private String formattedTotal;
        private String content;
        private String storedTotal;

        public Builder dataLoaded(boolean v) { this.dataLoaded = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder hin(String v) { this.hin = v; return this; }
        public Builder demoSex(String v) { this.demoSex = v; return this; }
        public Builder demoDob(String v) { this.demoDob = v; return this; }
        public Builder demoAddress(String v) { this.demoAddress = v; return this; }
        public Builder demoCity(String v) { this.demoCity = v; return this; }
        public Builder demoProvince(String v) { this.demoProvince = v; return this; }
        public Builder demoPostal(String v) { this.demoPostal = v; return this; }
        public Builder referralDoctor(String v) { this.referralDoctor = v; return this; }
        public Builder referralDoctorOhip(String v) { this.referralDoctorOhip = v; return this; }
        public Builder hcType(String v) { this.hcType = v; return this; }
        public Builder manualReviewLabel(String v) { this.manualReviewLabel = v; return this; }
        public Builder referralCheckedLabel(String v) { this.referralCheckedLabel = v; return this; }
        public Builder rosterStatus(String v) { this.rosterStatus = v; return this; }
        public Builder billingType(String v) { this.billingType = v; return this; }
        public Builder billingDate(String v) { this.billingDate = v; return this; }
        public Builder visitLocation(String v) { this.visitLocation = v; return this; }
        public Builder billingPhysicianNo(String v) { this.billingPhysicianNo = v; return this; }
        public Builder visitType(String v) { this.visitType = v; return this; }
        public Builder visitDate(String v) { this.visitDate = v; return this; }
        public Builder updateDate(String v) { this.updateDate = v; return this; }
        public Builder billingNo(String v) { this.billingNo = v; return this; }
        public Builder billingItems(List<Item> v) { this.billingItems = v; return this; }
        public Builder diagCode(String v) { this.diagCode = v; return this; }
        public Builder formattedTotal(String v) { this.formattedTotal = v; return this; }
        public Builder content(String v) { this.content = v; return this; }
        public Builder storedTotal(String v) { this.storedTotal = v; return this; }

        public BillingCorrectionReviewViewModel build() { return new BillingCorrectionReviewViewModel(this); }
    }
}
