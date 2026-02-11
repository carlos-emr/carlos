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
 */
public interface FaxProviderClient {

    /**
     * @return provider type implemented by this client.
     */
    FaxConfig.ProviderType getProviderType();

    /**
     * Sends an outbound fax.
     *
     * @param faxConfig provider configuration
     * @param faxJob logical fax job to send
     * @param filePath resolved path to document payload
     * @return updated fax job with provider response status fields
     * @throws FaxProviderException when send operation fails
     */
    FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException;

    /**
     * Lists inbound fax headers available for download.
     */
    List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException;

    /**
     * Downloads a specific inbound fax document.
     */
    FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException;

    /**
     * Deletes a remote inbound fax after successful local persistence.
     */
    void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException;

    /**
     * Gets current status for an outbound fax.
     */
    FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException;
}
