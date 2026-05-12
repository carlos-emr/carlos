/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

package io.github.carlos_emr.carlos.billings.ca.on.dto;

import java.math.BigDecimal;

/**
 * Mutable carrier for one remittance-advice detail row.
 *
 * <p>The import path still loads RA files into this bean before persisting and
 * summarizing them, so the property names mirror the historic table/report
 * terminology instead of a newer domain model. Amount fields are stored as
 * parsed {@link BigDecimal} values so malformed RA money fails at the import
 * boundary while the legacy string getters still return normalized decimals.</p>
 */
public class BillingRaDetailDto {
    private String radetail_no;
    private String raheader_no;
    private String providerohip_no;
    private String billing_no;
    private String service_code;
    private String service_count;
    private String hin;
    private BigDecimal amountclaim;
    private BigDecimal amountpay;
    private String service_date;
    private String error_code;
    private String billtype;
    private String claim_no = "";

    public String getClaim_no() {
        return this.claim_no;
    }

    public void setClaim_no(String claim_no) {
        this.claim_no = claim_no;
    }

    public String getAmountclaim() {
        return BillingDtoMoney.format(amountclaim);
    }

    public void setAmountclaim(String amountclaim) {
        this.amountclaim = BillingDtoMoney.parseSignedDecimal(amountclaim, "amountclaim");
    }

    public BigDecimal getAmountclaimAmount() {
        return amountclaim;
    }

    public void setAmountclaimAmount(BigDecimal amountclaim) {
        this.amountclaim = amountclaim;
    }

    public String getAmountpay() {
        return BillingDtoMoney.format(amountpay);
    }

    public void setAmountpay(String amountpay) {
        this.amountpay = BillingDtoMoney.parseSignedDecimal(amountpay, "amountpay");
    }

    public BigDecimal getAmountpayAmount() {
        return amountpay;
    }

    public void setAmountpayAmount(BigDecimal amountpay) {
        this.amountpay = amountpay;
    }

    public String getBilling_no() {
        return billing_no;
    }

    public void setBilling_no(String billing_no) {
        this.billing_no = billing_no;
    }

    public String getBilltype() {
        return billtype;
    }

    public void setBilltype(String billtype) {
        this.billtype = billtype;
    }

    public String getError_code() {
        return error_code;
    }

    public void setError_code(String error_code) {
        this.error_code = error_code;
    }

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getProviderohip_no() {
        return providerohip_no;
    }

    public void setProviderohip_no(String providerohip_no) {
        this.providerohip_no = providerohip_no;
    }

    public String getRadetail_no() {
        return radetail_no;
    }

    public void setRadetail_no(String radetail_no) {
        this.radetail_no = radetail_no;
    }

    public String getRaheader_no() {
        return raheader_no;
    }

    public void setRaheader_no(String raheader_no) {
        this.raheader_no = raheader_no;
    }

    public String getService_code() {
        return service_code;
    }

    public void setService_code(String service_code) {
        this.service_code = service_code;
    }

    public String getService_count() {
        return service_count;
    }

    public void setService_count(String service_count) {
        this.service_count = service_count;
    }

    public String getService_date() {
        return service_date;
    }

    public void setService_date(String service_date) {
        this.service_date = service_date;
    }
}
