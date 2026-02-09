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
package io.github.carlos_emr.carlos.fax.connector.srfax.resultWrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic JSON response wrapper for SRFax API responses that return a single result value.
 *
 * <p>The SRFax API returns JSON responses with "Status" and "Result" fields. This class
 * deserializes those fields into typed Java objects. When the Status field equals "Success",
 * the Result field contains the typed value. On failure, the error field contains the
 * error message from the API response.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * SingleWrapper&lt;String&gt; response = mapper.readValue(jsonString, new TypeReference&lt;SingleWrapper&lt;String&gt;&gt;() {});
 * if (response.isSuccess()) {
 *     String result = response.getResult();
 *     // Process successful result
 * } else {
 *     String error = response.getError();
 *     // Handle error
 * }
 * </pre>
 *
 * @param <T> the type of the result value returned by the SRFax API
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SingleWrapper<T> {

    /**
     * The success status string returned by the SRFax API.
     * Used to determine if an API response indicates successful execution.
     */
    public static final String STATUS_SUCCESS = "Success";

    @JsonProperty("Status")
    private String status;
    @JsonProperty("Result")
    private T result;

    private String error;

    /**
     * Gets the status field from the SRFax API response.
     *
     * @return the status string indicating the API call outcome (typically "Success" or an error status)
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status field from the SRFax API response.
     *
     * @param status the status string indicating the API call outcome
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Checks if the SRFax API response indicates successful execution.
     *
     * @return true if the status field equals "Success", false otherwise
     */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /**
     * Gets the result value from the SRFax API response.
     *
     * <p>This field is populated when the API call is successful (Status = "Success").
     * The actual type depends on the generic type parameter T.</p>
     *
     * @return the typed result value from the SRFax API response, or null if not present
     */
    public T getResult() {
        return result;
    }

    /**
     * Sets the result value from the SRFax API response.
     *
     * @param result the typed result value returned by the SRFax API
     */
    public void setResult(T result) {
        this.result = result;
    }

    /**
     * Gets the error message from the SRFax API response.
     *
     * <p>This field is populated when the API call fails (Status != "Success").
     * Contains the error message or error code from the SRFax API.</p>
     *
     * @return the error message from the SRFax API response, or null if no error
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message from the SRFax API response.
     *
     * @param error the error message returned by the SRFax API on failure
     */
    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "status:" + status + ", error:" + error + ", result:" + result;
    }
}
