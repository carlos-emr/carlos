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

import java.util.List;

/**
 * Generic JSON response wrapper for SRFax API endpoints that return a list of results.
 *
 * <p>The SRFax API returns responses with two primary fields:
 * <ul>
 *   <li><strong>Status</strong>: A string indicating success or failure (typically "Success" for successful calls)</li>
 *   <li><strong>Result</strong>: A list of typed items (deserialized into the generic type parameter)</li>
 * </ul>
 *
 * <p>When the API call is successful, Status equals {@link #STATUS_SUCCESS} and the Result field contains the requested list of items.
 * On failure, the Status field contains a failure indicator and the error field contains the error message.
 *
 * <p>This class uses Jackson annotations for JSON deserialization, mapping the SRFax JSON response structure to
 * Java fields. Unknown properties in the JSON response are automatically ignored to maintain forward compatibility
 * with API changes.
 *
 * <p><strong>Example Response:</strong>
 * <pre>
 * {
 *   "Status": "Success",
 *   "Result": [
 *     {"id": 1, "name": "Item1"},
 *     {"id": 2, "name": "Item2"}
 *   ]
 * }
 * </pre>
 *
 * @param <T> the type of items in the result list
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListWrapper<T> {

    /**
     * The status value indicating a successful SRFax API response.
     *
     * @see #isSuccess()
     */
    public static final String STATUS_SUCCESS = "Success";

    @JsonProperty("Status")
    private String status;
    @JsonProperty("Result")
    private List<T> result;

    @JsonProperty("Error")
    private String error;

    /**
     * Retrieves the status value from the SRFax API response.
     *
     * @return String the status of the API response, typically "Success" for successful calls or an error indicator for failures
     * @see #STATUS_SUCCESS
     * @see #isSuccess()
     */
    public String getStatus() {
        return status;
    }

    /**
     * Checks whether the SRFax API response indicates success.
     *
     * <p>This method compares the Status field against the success constant to determine if the API call
     * was successful. When true, the Result field is expected to contain the requested list of items.
     * When false, the error field should be checked for the reason of failure.
     *
     * @return boolean true if Status equals {@link #STATUS_SUCCESS}, false otherwise
     * @see #STATUS_SUCCESS
     * @see #getError()
     */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /**
     * Sets the status value from the SRFax API response.
     *
     * <p>This method is typically called by Jackson during JSON deserialization to populate the Status field
     * from the API response.
     *
     * @param status String the status value from the API response (typically "Success" or an error indicator)
     * @see #getStatus()
     * @see #isSuccess()
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Retrieves the list of results from the SRFax API response.
     *
     * <p>This list is populated when the API call is successful (Status equals "Success").
     * The list may be empty even on success, depending on the specific API endpoint and query parameters.
     * When the API call fails, this field should not be relied upon.
     *
     * @return List the list of typed items returned by the API, or null if the API call failed or no results were returned
     * @see #isSuccess()
     */
    public List<T> getResult() {
        return result;
    }

    /**
     * Sets the list of results from the SRFax API response.
     *
     * <p>This method is typically called by Jackson during JSON deserialization to populate the Result field
     * from the API response. The list contains items of the parameterized type.
     *
     * @param result List the list of typed items from the API response
     * @see #getResult()
     * @see #isSuccess()
     */
    public void setResult(List<T> result) {
        this.result = result;
    }

    /**
     * Retrieves the error message from a failed SRFax API response.
     *
     * <p>This field contains diagnostic information when the API call fails (Status does not equal "Success").
     * The error message describes what went wrong with the request, such as invalid parameters or service issues.
     *
     * @return String the error message from the API response, or null if the API call was successful
     * @see #isSuccess()
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message from the SRFax API response.
     *
     * <p>This method is typically called by Jackson during JSON deserialization to populate the error field
     * from the API response when the call fails.
     *
     * @param error String the error message or diagnostic information from the API response
     * @see #getError()
     * @see #isSuccess()
     */
    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "status:" + status + ", error:" + error + ", result:" + result;
    }
}
