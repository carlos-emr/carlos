/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 */
package io.github.carlos_emr.carlos.fax.connector;

import io.github.carlos_emr.carlos.commn.model.FaxJob;

/**
 * Immutable Data Transfer Object (DTO) representing the result of a fax delivery status check.
 *
 * <p>This DTO is returned by a {@link FaxConnector} when checking the delivery status of a previously
 * sent fax. It contains three pieces of information: a success flag indicating whether the status check
 * itself was successful, the current {@link FaxJob.STATUS} of the fax delivery, and a human-readable
 * status message providing additional context.</p>
 *
 * <p>The {@code success} flag indicates whether the FaxConnector was able to successfully query the
 * status of the fax delivery. A value of {@code true} means the status information was retrieved without
 * errors; {@code false} indicates the status check operation itself failed.</p>
 *
 * <p>The {@code status} field contains the current delivery status of the fax as a {@link FaxJob.STATUS}
 * enum value, such as PENDING, SENT, FAILED, DELIVERED, etc.</p>
 *
 * <p>The {@code statusMessage} provides a human-readable explanation of the status or error condition,
 * useful for logging, debugging, and displaying to end users.</p>
 *
 * @since 2026-02-09
 */
public class FaxStatusCheckResult {

    private final boolean success;
    private final FaxJob.STATUS status;
    private final String statusMessage;

    /**
     * Constructs a FaxStatusCheckResult with the specified status information.
     *
     * @param success boolean indicating whether the status check operation was successful. {@code true}
     *                means the FaxConnector successfully retrieved status information; {@code false} means
     *                the status check operation itself failed
     * @param status  {@link FaxJob.STATUS} enum representing the current delivery status of the fax
     *                (e.g., PENDING, SENT, FAILED, DELIVERED). May be {@code null} if the status check failed
     * @param statusMessage String containing a human-readable status message or error description.
     *                      Provides additional context about the delivery status or the reason for failure.
     *                      May be {@code null} if no message is available
     */
    public FaxStatusCheckResult(boolean success, FaxJob.STATUS status, String statusMessage) {
        this.success = success;
        this.status = status;
        this.statusMessage = statusMessage;
    }

    /**
     * Gets the success status of the fax status check operation.
     *
     * @return {@code true} if the FaxConnector successfully retrieved the fax delivery status;
     *         {@code false} if the status check operation itself failed
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the current delivery status of the fax.
     *
     * @return {@link FaxJob.STATUS} enum value representing the fax delivery status (e.g., PENDING, SENT,
     *         FAILED, DELIVERED). May be {@code null} if the status check operation failed
     */
    public FaxJob.STATUS getStatus() {
        return status;
    }

    /**
     * Gets the human-readable status message describing the fax delivery status or any error condition.
     *
     * @return String containing a descriptive message about the fax status or failure reason.
     *         May be {@code null} if no message is available
     */
    public String getStatusMessage() {
        return statusMessage;
    }
}
