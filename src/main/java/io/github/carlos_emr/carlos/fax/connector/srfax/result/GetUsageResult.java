/**
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * This software was written for
 * CloudPractice Inc.
 * Victoria, British Columbia
 * Canada
 *
 * Ported to CARLOS EMR from JunoEMR (2026).
 */
package io.github.carlos_emr.carlos.fax.connector.srfax.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * JSON model representing a single usage entry from the SRFax Get_Fax_Usage API response.
 *
 * This class deserialization supports the SRFax REST API endpoint that provides
 * usage statistics for a billing period, including the count of faxes sent/received,
 * number of pages processed, and associated billing information.
 *
 * The class uses Jackson annotations to automatically map JSON properties from the
 * SRFax API response to Java fields. Unknown JSON properties are ignored to support
 * API versioning and forward compatibility.
 *
 * Typical usage:
 * <pre>
 *   GetUsageResult usage = mapper.readValue(jsonResponse, GetUsageResult.class);
 *   System.out.println("Faxes sent: " + usage.getNumberOfFaxes());
 *   System.out.println("Pages: " + usage.getNumberOfPages());
 * </pre>
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUsageResult {

    @JsonProperty("UserID")
    private String userId;
    @JsonProperty("Period")
    private String period;
    @JsonProperty("ClientName")
    private String clientName;
    @JsonProperty("SubUserID")
    private Integer subUserId;
    @JsonProperty("BillingNumber")
    private String billingNumber;
    @JsonProperty("NumberOfFaxes")
    private Integer numberOfFaxes;
    @JsonProperty("NumberOfPages")
    private Integer numberOfPages;

    /**
     * Retrieves the SRFax user ID associated with this usage record.
     *
     * @return String the user ID from the SRFax account, or null if not set
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the SRFax user ID for this usage record.
     *
     * @param userId String the user ID from the SRFax account
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Retrieves the billing period for which this usage data applies.
     *
     * The period is typically in the format "YYYY-MM" or "YYYY-MM-DD" depending on
     * the SRFax API response format and billing cycle configuration.
     *
     * @return String the billing period identifier, or null if not set
     */
    public String getPeriod() {
        return period;
    }

    /**
     * Sets the billing period for this usage record.
     *
     * @param period String the billing period identifier (typically "YYYY-MM" format)
     */
    public void setPeriod(String period) {
        this.period = period;
    }

    /**
     * Retrieves the name of the client account associated with this usage record.
     *
     * This typically represents the healthcare provider or clinic name registered
     * with the SRFax service.
     *
     * @return String the client account name, or null if not set
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Sets the client account name for this usage record.
     *
     * @param clientName String the name of the client account (typically provider/clinic name)
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /**
     * Retrieves the sub-user ID associated with this usage record.
     *
     * A sub-user ID is assigned when the SRFax account has multiple user profiles or
     * departments. A null value typically indicates the main account user.
     *
     * @return Integer the sub-user identifier, or null if this is the main account user
     */
    public Integer getSubUserId() {
        return subUserId;
    }

    /**
     * Sets the sub-user ID for this usage record.
     *
     * @param subUserId Integer the sub-user identifier within the SRFax account
     */
    public void setSubUserId(Integer subUserId) {
        this.subUserId = subUserId;
    }

    /**
     * Retrieves the billing number associated with this usage record.
     *
     * The billing number is a unique identifier used by SRFax for billing and
     * invoice reconciliation purposes.
     *
     * @return String the billing number or account reference, or null if not set
     */
    public String getBillingNumber() {
        return billingNumber;
    }

    /**
     * Sets the billing number for this usage record.
     *
     * @param billingNumber String the billing number or account reference from SRFax
     */
    public void setBillingNumber(String billingNumber) {
        this.billingNumber = billingNumber;
    }

    /**
     * Retrieves the total count of faxes sent or received during the billing period.
     *
     * This count includes both incoming and outgoing faxes processed through the
     * SRFax service during the specified billing period.
     *
     * @return Integer the number of faxes processed, or null if not set
     */
    public Integer getNumberOfFaxes() {
        return numberOfFaxes;
    }

    /**
     * Sets the total count of faxes for this usage record.
     *
     * @param numberOfFaxes Integer the number of faxes sent/received during the billing period
     */
    public void setNumberOfFaxes(Integer numberOfFaxes) {
        this.numberOfFaxes = numberOfFaxes;
    }

    /**
     * Retrieves the total count of pages transmitted during the billing period.
     *
     * This represents the cumulative page count across all faxes (inbound and outbound)
     * processed through the SRFax service. Page counts are used in billing calculations
     * as many fax services charge per page transmitted.
     *
     * @return Integer the number of pages transmitted, or null if not set
     */
    public Integer getNumberOfPages() {
        return numberOfPages;
    }

    /**
     * Sets the total count of pages for this usage record.
     *
     * @param numberOfPages Integer the total number of pages transmitted during the billing period
     */
    public void setNumberOfPages(Integer numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }
}
