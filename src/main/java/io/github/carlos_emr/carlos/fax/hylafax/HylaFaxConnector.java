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
package io.github.carlos_emr.carlos.fax.hylafax;

import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import org.springframework.stereotype.Component;

/**
 * Fax provider client for on-premise HylaFax installations.
 *
 * <p>HylaFax support is intentionally implemented behind the existing provider abstraction so
 * outbound send, inbound polling, and status tracking flow through the same scheduler pipeline
 * as middleware and SRFax providers.</p>
 *
 * @since 2026-05-05
 */
@Component
public class HylaFaxConnector implements FaxProviderClient {

    private static final String MODE_SSH = "SSH";

    private final HylaFaxClientProtocol clientProtocol;
    private final HylaFaxSshClient sshClient;
    private final HylaFaxRecvqMonitor recvqMonitor;

    public HylaFaxConnector() {
        this(new HylaFaxClientProtocol(), new HylaFaxSshClient(), new HylaFaxRecvqMonitor());
    }

    HylaFaxConnector(HylaFaxClientProtocol clientProtocol, HylaFaxSshClient sshClient, HylaFaxRecvqMonitor recvqMonitor) {
        this.clientProtocol = clientProtocol;
        this.sshClient = sshClient;
        this.recvqMonitor = recvqMonitor;
    }

    @Override
    public FaxConfig.ProviderType getProviderType() {
        return FaxConfig.ProviderType.HYLAFAX;
    }

    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateConfig(faxConfig);
        return useSsh(faxConfig) ? sshClient.sendFax(faxConfig, faxJob, filePath)
                : clientProtocol.sendFax(faxConfig, faxJob, filePath);
    }

    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateConfig(faxConfig);
        return useSsh(faxConfig) ? sshClient.listInboundFaxes(faxConfig) : recvqMonitor.listInboundFaxes(faxConfig);
    }

    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateConfig(faxConfig);
        return useSsh(faxConfig) ? sshClient.downloadFax(faxConfig, fax) : recvqMonitor.downloadFax(faxConfig, fax);
    }

    @Override
    public void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateConfig(faxConfig);
        if (useSsh(faxConfig)) {
            sshClient.archiveFax(faxConfig, fax);
        } else {
            recvqMonitor.archiveFax(faxConfig, fax);
        }
    }

    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        requireMatchingProviderType(faxConfig);
        validateConfig(faxConfig);
        return useSsh(faxConfig) ? sshClient.fetchFaxStatus(faxConfig, faxJob)
                : clientProtocol.fetchFaxStatus(faxConfig, faxJob);
    }

    private void validateConfig(FaxConfig faxConfig) throws HylaFaxException {
        if (faxConfig.getHylafaxHost() == null || faxConfig.getHylafaxHost().trim().isEmpty()) {
            throw new HylaFaxException("HylaFax host is required");
        }
        if (faxConfig.getHylafaxPort() == null || faxConfig.getHylafaxPort() < 1 || faxConfig.getHylafaxPort() > 65535) {
            throw new HylaFaxException("HylaFax port must be between 1 and 65535");
        }
        if (faxConfig.getHylafaxUsername() == null || faxConfig.getHylafaxUsername().trim().isEmpty()) {
            throw new HylaFaxException("HylaFax username is required");
        }
    }

    private boolean useSsh(FaxConfig faxConfig) {
        String mode = CarlosProperties.getInstance().getProperty("hylafax.connection.mode");
        if (mode != null && !mode.trim().isEmpty()) {
            return MODE_SSH.equalsIgnoreCase(mode.trim());
        }
        return faxConfig.isHylafaxUseSsh();
    }
}
