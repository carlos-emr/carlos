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
 * Immutable view model for {@code billingON3rdInv.jsp}, the third-party
 * invoice print/preview page.
 *
 * <p>Captures the invoice header (clinic / site logo, billing date, due
 * date), patient context (demographic, HIN, sex, DOB), provider, the
 * line-item table with service descriptions resolved, and the running
 * payment / discount / refund / balance totals.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONThirdPartyInvoiceViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingON3rdInv2Action})
 * and exposed to the JSP as request attribute {@code invoiceModel}.</p>
 *
 * <p>Eliminates the 9 inline {@code SpringUtils.getBean} lookups the JSP
 * used to perform across its scriptlet body (BillingONCHeader1Dao,
 * ClinicDAO, BillingONExtDao, BillingONPaymentDao, DemographicDao,
 * ProviderDao, BillingONInvoiceTotalsCalculator, SiteDao, BillingServiceDao).</p>
 *
 * @since 2026-04-26
 */
public final class BillingONThirdPartyInvoiceViewModel {

    private final String invoiceNoStr;
    private final boolean invoiceLoaded;

    // Clinic / site header
    private final boolean multisiteEnabled;
    private final boolean siteLogoAvailable;
    private final Integer siteLogoId;
    private final String siteName;
    private final String siteAddress;
    private final String siteCity;
    private final String siteProvince;
    private final String sitePostal;
    private final String sitePhone;
    private final boolean clinicLogoImgExists;
    private final String clinicName;
    private final String clinicAddress;
    private final String clinicCity;
    private final String clinicProvince;
    private final String clinicPostal;
    private final String clinicPhone;

    // Header
    private final String billingDateStr;
    private final boolean dueDateEnabled;
    private final String dueDateStr;
    private final String printDate;

    // Bill-to / remit-to (pre-rendered text blocks — the legacy JSP
    // wrote these into a <pre> that preserves embedded newlines)
    private final String billTo;
    private final String remitTo;

    // Patient / provider
    private final String patientName;
    private final String patientDemoNo;
    private final String patientGender;
    private final String patientDob;
    private final String patientHin;
    private final String invoiceComment;
    private final String providerFormattedName;
    private final String payeeName;
    private final String invoiceRefNum;

    // Line items
    private final List<InvoiceItem> invoiceItems;

    // Totals
    private final String totalAmount;
    private final String paymentAmount;
    private final String discountAmount;
    private final String creditAmount;
    private final String refundAmount;
    private final String balanceAmount;
    private final String paymentMethodLabel;

    public record InvoiceItem(String itemId, String description, String serviceCode,
                              String quantity, String dx, String fee) {}

    private BillingONThirdPartyInvoiceViewModel(Builder b) {
        this.invoiceNoStr = nullToEmpty(b.invoiceNoStr);
        this.invoiceLoaded = b.invoiceLoaded;
        this.multisiteEnabled = b.multisiteEnabled;
        this.siteLogoAvailable = b.siteLogoAvailable;
        this.siteLogoId = b.siteLogoId;
        this.siteName = nullToEmpty(b.siteName);
        this.siteAddress = nullToEmpty(b.siteAddress);
        this.siteCity = nullToEmpty(b.siteCity);
        this.siteProvince = nullToEmpty(b.siteProvince);
        this.sitePostal = nullToEmpty(b.sitePostal);
        this.sitePhone = nullToEmpty(b.sitePhone);
        this.clinicLogoImgExists = b.clinicLogoImgExists;
        this.clinicName = nullToEmpty(b.clinicName);
        this.clinicAddress = nullToEmpty(b.clinicAddress);
        this.clinicCity = nullToEmpty(b.clinicCity);
        this.clinicProvince = nullToEmpty(b.clinicProvince);
        this.clinicPostal = nullToEmpty(b.clinicPostal);
        this.clinicPhone = nullToEmpty(b.clinicPhone);
        this.billingDateStr = nullToEmpty(b.billingDateStr);
        this.dueDateEnabled = b.dueDateEnabled;
        this.dueDateStr = nullToEmpty(b.dueDateStr);
        this.printDate = nullToEmpty(b.printDate);
        this.billTo = nullToEmpty(b.billTo);
        this.remitTo = nullToEmpty(b.remitTo);
        this.patientName = nullToEmpty(b.patientName);
        this.patientDemoNo = nullToEmpty(b.patientDemoNo);
        this.patientGender = nullToEmpty(b.patientGender);
        this.patientDob = nullToEmpty(b.patientDob);
        this.patientHin = nullToEmpty(b.patientHin);
        this.invoiceComment = nullToEmpty(b.invoiceComment);
        this.providerFormattedName = nullToEmpty(b.providerFormattedName);
        this.payeeName = nullToEmpty(b.payeeName);
        this.invoiceRefNum = nullToEmpty(b.invoiceRefNum);
        this.invoiceItems = b.invoiceItems == null
                ? Collections.emptyList() : List.copyOf(b.invoiceItems);
        this.totalAmount = nullToZero(b.totalAmount);
        this.paymentAmount = nullToZero(b.paymentAmount);
        this.discountAmount = nullToZero(b.discountAmount);
        this.creditAmount = nullToZero(b.creditAmount);
        this.refundAmount = nullToZero(b.refundAmount);
        this.balanceAmount = nullToZero(b.balanceAmount);
        this.paymentMethodLabel = nullToEmpty(b.paymentMethodLabel);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String nullToZero(String s) { return s == null ? "0.00" : s; }

    public static Builder builder() { return new Builder(); }

    public String getInvoiceNoStr() { return invoiceNoStr; }
    public boolean isInvoiceLoaded() { return invoiceLoaded; }
    public boolean isMultisiteEnabled() { return multisiteEnabled; }
    public boolean isSiteLogoAvailable() { return siteLogoAvailable; }
    public Integer getSiteLogoId() { return siteLogoId; }
    public String getSiteName() { return siteName; }
    public String getSiteAddress() { return siteAddress; }
    public String getSiteCity() { return siteCity; }
    public String getSiteProvince() { return siteProvince; }
    public String getSitePostal() { return sitePostal; }
    public String getSitePhone() { return sitePhone; }
    public boolean isClinicLogoImgExists() { return clinicLogoImgExists; }
    public String getClinicName() { return clinicName; }
    public String getClinicAddress() { return clinicAddress; }
    public String getClinicCity() { return clinicCity; }
    public String getClinicProvince() { return clinicProvince; }
    public String getClinicPostal() { return clinicPostal; }
    public String getClinicPhone() { return clinicPhone; }
    public String getBillingDateStr() { return billingDateStr; }
    public boolean isDueDateEnabled() { return dueDateEnabled; }
    public String getDueDateStr() { return dueDateStr; }
    public String getPrintDate() { return printDate; }
    public String getBillTo() { return billTo; }
    public String getRemitTo() { return remitTo; }
    public String getPatientName() { return patientName; }
    public String getPatientDemoNo() { return patientDemoNo; }
    public String getPatientGender() { return patientGender; }
    public String getPatientDob() { return patientDob; }
    public String getPatientHin() { return patientHin; }
    public String getInvoiceComment() { return invoiceComment; }
    public String getProviderFormattedName() { return providerFormattedName; }
    public String getPayeeName() { return payeeName; }
    public String getInvoiceRefNum() { return invoiceRefNum; }
    public List<InvoiceItem> getInvoiceItems() { return invoiceItems; }
    public String getTotalAmount() { return totalAmount; }
    public String getPaymentAmount() { return paymentAmount; }
    public String getDiscountAmount() { return discountAmount; }
    public String getCreditAmount() { return creditAmount; }
    public String getRefundAmount() { return refundAmount; }
    public String getBalanceAmount() { return balanceAmount; }
    public String getPaymentMethodLabel() { return paymentMethodLabel; }

    public static final class Builder {
        private String invoiceNoStr;
        private boolean invoiceLoaded;
        private boolean multisiteEnabled;
        private boolean siteLogoAvailable;
        private Integer siteLogoId;
        private String siteName;
        private String siteAddress;
        private String siteCity;
        private String siteProvince;
        private String sitePostal;
        private String sitePhone;
        private boolean clinicLogoImgExists;
        private String clinicName;
        private String clinicAddress;
        private String clinicCity;
        private String clinicProvince;
        private String clinicPostal;
        private String clinicPhone;
        private String billingDateStr;
        private boolean dueDateEnabled;
        private String dueDateStr;
        private String printDate;
        private String billTo;
        private String remitTo;
        private String patientName;
        private String patientDemoNo;
        private String patientGender;
        private String patientDob;
        private String patientHin;
        private String invoiceComment;
        private String providerFormattedName;
        private String payeeName;
        private String invoiceRefNum;
        private List<InvoiceItem> invoiceItems;
        private String totalAmount;
        private String paymentAmount;
        private String discountAmount;
        private String creditAmount;
        private String refundAmount;
        private String balanceAmount;
        private String paymentMethodLabel;

        public Builder invoiceNoStr(String v) { this.invoiceNoStr = v; return this; }
        public Builder invoiceLoaded(boolean v) { this.invoiceLoaded = v; return this; }
        public Builder multisiteEnabled(boolean v) { this.multisiteEnabled = v; return this; }
        public Builder siteLogoAvailable(boolean v) { this.siteLogoAvailable = v; return this; }
        public Builder siteLogoId(Integer v) { this.siteLogoId = v; return this; }
        public Builder siteName(String v) { this.siteName = v; return this; }
        public Builder siteAddress(String v) { this.siteAddress = v; return this; }
        public Builder siteCity(String v) { this.siteCity = v; return this; }
        public Builder siteProvince(String v) { this.siteProvince = v; return this; }
        public Builder sitePostal(String v) { this.sitePostal = v; return this; }
        public Builder sitePhone(String v) { this.sitePhone = v; return this; }
        public Builder clinicLogoImgExists(boolean v) { this.clinicLogoImgExists = v; return this; }
        public Builder clinicName(String v) { this.clinicName = v; return this; }
        public Builder clinicAddress(String v) { this.clinicAddress = v; return this; }
        public Builder clinicCity(String v) { this.clinicCity = v; return this; }
        public Builder clinicProvince(String v) { this.clinicProvince = v; return this; }
        public Builder clinicPostal(String v) { this.clinicPostal = v; return this; }
        public Builder clinicPhone(String v) { this.clinicPhone = v; return this; }
        public Builder billingDateStr(String v) { this.billingDateStr = v; return this; }
        public Builder dueDateEnabled(boolean v) { this.dueDateEnabled = v; return this; }
        public Builder dueDateStr(String v) { this.dueDateStr = v; return this; }
        public Builder printDate(String v) { this.printDate = v; return this; }
        public Builder billTo(String v) { this.billTo = v; return this; }
        public Builder remitTo(String v) { this.remitTo = v; return this; }
        public Builder patientName(String v) { this.patientName = v; return this; }
        public Builder patientDemoNo(String v) { this.patientDemoNo = v; return this; }
        public Builder patientGender(String v) { this.patientGender = v; return this; }
        public Builder patientDob(String v) { this.patientDob = v; return this; }
        public Builder patientHin(String v) { this.patientHin = v; return this; }
        public Builder invoiceComment(String v) { this.invoiceComment = v; return this; }
        public Builder providerFormattedName(String v) { this.providerFormattedName = v; return this; }
        public Builder payeeName(String v) { this.payeeName = v; return this; }
        public Builder invoiceRefNum(String v) { this.invoiceRefNum = v; return this; }
        // Defensive copy at the setter — mirrors BillingONFormViewModel pattern.
        public Builder invoiceItems(List<InvoiceItem> v) { this.invoiceItems = v == null ? null : List.copyOf(v); return this; }
        public Builder totalAmount(String v) { this.totalAmount = v; return this; }
        public Builder paymentAmount(String v) { this.paymentAmount = v; return this; }
        public Builder discountAmount(String v) { this.discountAmount = v; return this; }
        public Builder creditAmount(String v) { this.creditAmount = v; return this; }
        public Builder refundAmount(String v) { this.refundAmount = v; return this; }
        public Builder balanceAmount(String v) { this.balanceAmount = v; return this; }
        public Builder paymentMethodLabel(String v) { this.paymentMethodLabel = v; return this; }

        public BillingONThirdPartyInvoiceViewModel build() { return new BillingONThirdPartyInvoiceViewModel(this); }
    }
}
