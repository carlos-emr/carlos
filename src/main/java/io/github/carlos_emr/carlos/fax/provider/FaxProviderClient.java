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
 * @since 2026-02-11
 */
public interface FaxProviderClient {

    /**
     * Gets the provider type implemented by this client.
     *
     * @return FaxConfig.ProviderType the provider type enumeration value
     */
    FaxConfig.ProviderType getProviderType();

    /**
     * Sends an outbound fax.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param faxJob FaxJob logical fax job to send with destination and metadata
     * @param filePath Path resolved path to document payload on local filesystem
     * @return FaxJob updated fax job with provider response status fields
     * @throws FaxProviderException when send operation fails
     */
    FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException;

    /**
     * Lists inbound fax headers available for download from the provider.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @return List&lt;FaxJob&gt; list of inbound fax metadata available for download
     * @throws FaxProviderException when listing operation fails
     */
    List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException;

    /**
     * Downloads a specific inbound fax document from the provider.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param fax FaxJob fax metadata identifying which document to download
     * @return FaxJob fax job with downloaded document content populated
     * @throws FaxProviderException when download operation fails
     */
    FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException;

    /**
     * Deletes a remote inbound fax after successful local persistence.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param fax FaxJob fax metadata identifying which document to delete
     * @throws FaxProviderException when delete operation fails
     */
    void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException;

    /**
     * Gets current delivery status for an outbound fax.
     *
     * @param faxConfig FaxConfig provider configuration containing credentials and endpoint
     * @param faxJob FaxJob fax job with jobId to check status for
     * @return FaxJob updated fax job with current status information
     * @throws FaxProviderException when status check operation fails
     */
    FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException;
}
