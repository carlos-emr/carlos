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

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;

import java.util.List;

/**
 * Abstraction layer for fax transmission providers.
 * <p>
 * Implementations handle the actual sending, receiving, and status checking
 * of faxes via different backend services (legacy gateway, SRFax, etc.).
 * <p>
 * The existing legacy external gateway and the new SRFax direct API integration
 * both implement this interface, allowing the scheduler to dispatch to the
 * correct provider based on the {@link FaxConfig#getIntegrationType()} setting.
 *
 * @since 2026-02-09
 */
public interface FaxConnector {

    /**
     * Send a fax job to the external fax service.
     *
     * @param faxConfig the fax account configuration
     * @param faxJob the fax job to send (must contain document data and destination)
     * @return FaxSendResult with the external job ID and status
     */
    FaxSendResult sendFax(FaxConfig faxConfig, FaxJob faxJob);

    /**
     * Poll the external fax service for incoming faxes.
     *
     * @param faxConfig the fax account configuration
     * @return list of incoming fax results (may be empty)
     */
    List<FaxInboundResult> pollIncomingFaxes(FaxConfig faxConfig);

    /**
     * Download the content of a specific incoming fax.
     *
     * @param faxConfig the fax account configuration
     * @param faxReference the external reference for the fax to download
     * @return base64-encoded document content, or null on failure
     */
    String downloadFax(FaxConfig faxConfig, String faxReference);

    /**
     * Delete a fax from the remote service after it has been downloaded.
     *
     * @param faxConfig the fax account configuration
     * @param faxReference the external reference for the fax to delete
     * @return true if deletion succeeded
     */
    boolean deleteFax(FaxConfig faxConfig, String faxReference);

    /**
     * Check the delivery status of a previously sent fax.
     *
     * @param faxConfig the fax account configuration
     * @param externalJobId the external job ID returned during send
     * @return FaxStatusCheckResult with updated status information
     */
    FaxStatusCheckResult checkFaxStatus(FaxConfig faxConfig, long externalJobId);

    /**
     * Mark an incoming fax as read/processed on the remote service.
     *
     * @param faxConfig the fax account configuration
     * @param faxReference the external reference for the fax
     */
    void markFaxAsRead(FaxConfig faxConfig, String faxReference);

    /**
     * Get the integration type identifier that this connector handles.
     *
     * @return String integration type constant (e.g., "LEGACY_GATEWAY", "SRFAX")
     */
    String getIntegrationType();
}
