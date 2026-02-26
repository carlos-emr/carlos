/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr;

/**
 * Data bean representing billing transaction information in the system.
 * 
 * <p>This bean encapsulates all billing-related data including:</p>
 * <ul>
 *   <li>Billing identifiers (billing number, appointment number)</li>
 *   <li>Provider and demographic information</li>
 *   <li>Health insurance numbers and provider identifiers</li>
 *   <li>Billing dates, times, and status</li>
 *   <li>Visit information and billing content</li>
 * </ul>
 * 
 * <p>This is a serializable JavaBean used for transferring billing data
 * between application layers.</p>
 */
public class BillingDataBean extends java.lang.Object implements java.io.Serializable {

    /** Billing transaction number */
    protected String billing_no = "";
    /** Clinic identifier */
    protected String clinic_no = "";
    /** Demographic (patient) identifier */
    protected String demographic_no = "";
    /** Associated appointment number */
    protected String appointment_no = "";
    /** Provider identifier */
    protected String provider_no = "";
    /** Organization specialty code */
    protected String organization_spec_code = "";
    /** Demographic (patient) name */
    protected String demographic_name = "";
    /** Health insurance number */
    protected String hin = "";
    /** Last update date */
    protected String update_date = "";
    /** Last update time */
    protected String update_time = "";
    /** Billing transaction date */
    protected String billing_date = "";
    /** Billing transaction time */
    protected String billing_time = "";
    /** Clinic reference code */
    protected String clinic_ref_code = "";
    /** Billing content/details */
    protected String content = "";
    /** Total billing amount */
    protected String total = "";
    /** Billing status */
    protected String status = "";
    /** Patient date of birth */
    protected String dob = "";
    /** Visit date */
    protected String visitdate = "";
    /** Visit type */
    protected String visittype = "";
    /** Provider OHIP number */
    protected String provider_ohip_no = "";
    /** Provider RMA number */
    protected String provider_rma_no = "";


    /**
     * Sets the billing transaction number.
     *
     * @param value the new billing number
     */
    public void setBilling_no(String value) {
        billing_no = value;
    }

    /**
     * Gets the billing transaction number.
     *
     * @return the billing number
     */
    public String getBilling_no() {
        return billing_no;
    }

    public void setClinic_no(String value) {
        clinic_no = value;
    }

    public String getClinic_no() {
        return clinic_no;
    }

    public void setDemographic_no(String value) {
        demographic_no = value;
    }

    public String getDemographic_no() {
        return demographic_no;
    }

    public void setAppointment_no(String value) {
        appointment_no = value;
    }

    public String getAppointment_no() {
        return appointment_no;
    }

    public void setProviderNo(String value) {
        provider_no = value;
    }

    public String getProviderNo() {
        return provider_no;
    }

    public void setOrganization_spec_code(String value) {
        organization_spec_code = value;
    }

    public String getOrganization_spec_code() {
        return organization_spec_code;
    }

    public void setDemographic_name(String value) {
        demographic_name = value;
    }

    public String getDemographic_name() {
        return demographic_name;
    }

    public void setHin(String value) {
        hin = value;
    }

    public String getHin() {
        return hin;
    }

    public void setUpdate_date(String value) {
        update_date = value;
    }

    public String getUpdate_date() {
        return update_date;
    }

    public void setUpdate_time(String value) {
        update_time = value;
    }

    public String getUpdate_time() {
        return update_time;
    }

    public void setBilling_date(String value) {
        billing_date = value;
    }

    public String getBilling_date() {
        return billing_date;
    }

    public void setBilling_time(String value) {
        billing_time = value;
    }

    public String getBilling_time() {
        return billing_time;
    }

    public void setClinic_ref_code(String value) {
        clinic_ref_code = value;
    }

    public String getClinic_ref_code() {
        return clinic_ref_code;
    }

    public void setContent(String value) {
        content = value;
    }

    public String getContent() {
        return content;
    }


    public void setTotal(String value) {
        total = value;
    }

    public String getTotal() {
        return total;
    }

    public void setStatus(String value) {
        status = value;
    }

    public String getStatus() {
        return status;
    }

    public void setDob(String value) {
        dob = value;
    }

    public String getDob() {
        return dob;
    }

    public void setVisitdate(String value) {
        visitdate = value;
    }

    public String getVisitdate() {
        return visitdate;
    }

    public void setVisittype(String value) {
        visittype = value;
    }

    public String getVisittype() {
        return visittype;
    }

    public void setProvider_ohip_no(String value) {
        provider_ohip_no = value;
    }

    public String getProvider_ohip_no() {
        return provider_ohip_no;
    }

    public void setProvider_rma_no(String value) {
        provider_rma_no = value;
    }

    public String getProvider_rma_no() {
        return provider_rma_no;
    }

}
