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
package io.github.carlos_emr.carlos.fax.provider;

import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;

/**
 * Abstraction for fax service-provider transports.
 *
 * <p>The core fax pipeline should not directly know protocol details for any specific provider.
 * Implementations of this interface encapsulate provider API behavior (middleware relay, SRFax,
 * and future providers) behind a stable contract.</p>
 *
 * <p><strong>Implementation contract:</strong> All implementations MUST call
 * {@link #requireMatchingProviderType(FaxConfig)} as the first line of every public method
 * to enforce provider type safety at runtime.</p>
 *
 * @since 2026-02-11
 */
public interface FaxProviderClient {

    /**
     * Gets the provider type implemented by this client.
     *
     * @return FaxConfig.ProviderType the provider type enumeration value
     * @since 2026-02-11
     */
    FaxConfig.ProviderType getProviderType();

    /**
     * Sends an outbound fax.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param faxJob FaxJob logical fax job with destination and metadata. Note: implementations may null-out
     *        the document field after transmission to free memory. Callers must not rely on the document
     *        field after calling sendFax
     * @param filePath Path resolved path to document payload on local filesystem
     * @return FaxJob new fax job containing provider response fields (jobId, status, statusString)
     * @throws FaxProviderException when send operation fails
     * @since 2026-02-11
     */
    FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException;

    /**
     * Lists inbound fax headers available for download from the provider.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @return List&lt;FaxJob&gt; list of inbound fax metadata available for download
     * @throws FaxProviderException when listing operation fails
     * @since 2026-02-11
     */
    List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException;

    /**
     * Downloads a specific inbound fax document from the provider.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param fax FaxJob fax metadata identifying which document to download
     * @return FaxJob fax job with downloaded document content populated
     * @throws FaxProviderException when download operation fails
     * @since 2026-02-11
     */
    FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException;

    /**
     * Marks a remote inbound fax as read/processed after successful local import.
     *
     * <p>This is the second phase of the three-phase import strategy:
     * (1) download and save to local incoming directory,
     * (2) mark as read on provider after local file is safe,
     * (3) import from incoming directory into EMR document system.
     * This prevents fax loss if import fails after download.</p>
     *
     * <p>Default implementation is a no-op. Providers that use read/unread semantics
     * for duplicate prevention (e.g., SRFax) must override this method.</p>
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param fax FaxJob fax metadata identifying which fax to mark as read
     * @throws FaxProviderException when the mark-as-read operation fails
     * @since 2026-02-11
     */
    default void markFaxAsRead(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        // No-op by default. Providers using read/unread semantics override this.
    }

    /**
     * Acknowledges or deletes a remote inbound fax after successful local persistence.
     *
     * <p>Behavior is provider-specific: middleware deletes the fax from the relay server,
     * while SRFax is a no-op (SRFax duplicate prevention uses read/unread semantics via
     * {@link #markFaxAsRead(FaxConfig, FaxJob)}).</p>
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param fax FaxJob fax metadata identifying which document to acknowledge or delete
     * @throws FaxProviderException when the operation fails
     * @since 2026-02-11
     */
    void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException;

    /**
     * Gets current delivery status for an outbound fax.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param faxJob FaxJob fax job with jobId to check status for
     * @return FaxJob updated fax job with current status information
     * @throws FaxProviderException when status check operation fails
     * @since 2026-02-11
     */
    FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException;

    /**
     * Validates that the given fax configuration matches this client's provider type.
     *
     * @param faxConfig FaxConfig to validate
     * @throws IllegalArgumentException if the provider type does not match
     * @since 2026-02-11
     */
    default void requireMatchingProviderType(FaxConfig faxConfig) {
        FaxConfig.ProviderType expected = getProviderType();
        if (faxConfig.getProviderType() != expected) {
            throw new IllegalArgumentException(
                    expected + " client requires " + expected + " provider type, but got: " + faxConfig.getProviderType());
        }
    }
}
