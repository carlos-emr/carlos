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
 * Immutable result data transfer object (DTO) returned when sending a fax through a {@link FaxConnector}.
 *
 * This class encapsulates the result of a fax send operation, including success status, the external
 * job identifier assigned by the fax service provider, the current status of the fax job, and a
 * human-readable status message. The result contains all necessary information to track fax delivery
 * and update the corresponding {@link FaxJob} entity in the CARLOS EMR system.
 *
 * <p><strong>Immutability</strong>: All fields are final and immutable. Once created, a
 * {@code FaxSendResult} instance cannot be modified, making it safe for concurrent access and
 * cacheable.</p>
 *
 * <p><strong>Usage Example</strong>:</p>
 * <pre>
 * FaxConnector connector = new SomeFaxConnector();
 * FaxSendResult result = connector.sendFax(document, recipientNumber);
 * if (result.isSuccess()) {
 *     faxJob.setExternalJobId(result.getExternalJobId());
 *     faxJob.setStatus(result.getStatus());
 *     faxJobDao.update(faxJob);
 * } else {
 *     logger.warn("Fax send failed: " + result.getStatusMessage());
 * }
 * </pre>
 *
 * @see FaxConnector
 * @see FaxJob
 * @since 2026-02-09
 */
public class FaxSendResult {

    private final boolean success;
    private final long externalJobId;
    private final FaxJob.STATUS status;
    private final String statusMessage;

    /**
     * Constructs a new {@code FaxSendResult} with the specified fax send operation details.
     *
     * @param success boolean indicating whether the fax was successfully sent to the fax service provider
     * @param externalJobId long the unique identifier assigned by the external fax service provider
     *        for tracking the fax delivery status
     * @param status FaxJob.STATUS the current status of the fax job (e.g., PENDING, SENT, FAILED, DELIVERED)
     * @param statusMessage String a detailed message describing the result of the fax send operation,
     *        useful for logging and error reporting
     */
    public FaxSendResult(boolean success, long externalJobId, FaxJob.STATUS status, String statusMessage) {
        this.success = success;
        this.externalJobId = externalJobId;
        this.status = status;
        this.statusMessage = statusMessage;
    }

    /**
     * Returns whether the fax was successfully submitted to the fax service provider.
     *
     * @return boolean {@code true} if the fax was successfully submitted for delivery,
     *         {@code false} if the send operation failed at any stage
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the unique identifier assigned by the external fax service provider for this fax job.
     *
     * @return long the external job ID that can be used to track fax delivery status with the fax
     *         service provider. This value should be persisted in the corresponding {@link FaxJob}
     *         entity to maintain a linkage with the external system.
     */
    public long getExternalJobId() {
        return externalJobId;
    }

    /**
     * Returns the current status of the fax job within the CARLOS EMR system.
     *
     * @return FaxJob.STATUS the status code indicating the current state of the fax delivery
     *         (e.g., PENDING for jobs awaiting processing, SENT for successfully transmitted faxes,
     *         FAILED for unsuccessful attempts, DELIVERED for confirmed delivery)
     */
    public FaxJob.STATUS getStatus() {
        return status;
    }

    /**
     * Returns a detailed human-readable message describing the result of the fax send operation.
     *
     * @return String a descriptive message that may contain error details for failed sends,
     *         confirmation details for successful sends, or other relevant operational information
     */
    public String getStatusMessage() {
        return statusMessage;
    }
}
