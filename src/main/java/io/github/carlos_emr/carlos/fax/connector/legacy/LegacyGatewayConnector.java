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
package io.github.carlos_emr.carlos.fax.connector.legacy;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.connector.FaxConnector;
import io.github.carlos_emr.carlos.fax.connector.FaxInboundResult;
import io.github.carlos_emr.carlos.fax.connector.FaxIntegrationType;
import io.github.carlos_emr.carlos.fax.connector.FaxSendResult;
import io.github.carlos_emr.carlos.fax.connector.FaxStatusCheckResult;

import java.util.List;

/**
 * {@link FaxConnector} implementation for the legacy external fax gateway server.
 * <p>
 * This connector is a marker/passthrough that indicates fax accounts using
 * the legacy external CXF REST gateway. The actual send/receive/status logic
 * remains in {@code FaxSender}, {@code FaxImporter}, and {@code FaxStatusUpdater}
 * for backward compatibility. When the integration type is {@link FaxIntegrationType#LEGACY_GATEWAY},
 * the existing code path is used unchanged.
 * <p>
 * This ensures zero disruption to existing deployments using the external
 * fax gateway while allowing new deployments to use SRFax or other connectors.
 *
 * @since 2026-02-09
 */
public class LegacyGatewayConnector implements FaxConnector {

    /** Integration type constant for the legacy external gateway. */
    public static final String INTEGRATION_TYPE = FaxIntegrationType.LEGACY_GATEWAY;

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always; legacy fax sending is handled
     *         directly by {@code FaxSender.sendViaLegacyGateway()}
     */
    @Override
    public FaxSendResult sendFax(FaxConfig faxConfig, FaxJob faxJob) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxSender code path, not the connector interface");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always; legacy inbox polling is handled
     *         directly by {@code FaxImporter.pollViaLegacyGateway()}
     */
    @Override
    public List<FaxInboundResult> pollIncomingFaxes(FaxConfig faxConfig) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxImporter code path, not the connector interface");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always; legacy download is handled
     *         directly by {@code FaxImporter.downloadFax()}
     */
    @Override
    public String downloadFax(FaxConfig faxConfig, String faxReference) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxImporter code path, not the connector interface");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always; legacy deletion is handled
     *         directly by {@code FaxImporter.deleteFax()}
     */
    @Override
    public boolean deleteFax(FaxConfig faxConfig, String faxReference) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxImporter code path, not the connector interface");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always; legacy status checking is handled
     *         directly by {@code FaxStatusUpdater.updateStatusViaLegacyGateway()}
     */
    @Override
    public FaxStatusCheckResult checkFaxStatus(FaxConfig faxConfig, long externalJobId) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxStatusUpdater code path, not the connector interface");
    }

    /**
     * {@inheritDoc}
     * <p>
     * No-op for the legacy gateway. The legacy gateway server manages its own read state.
     */
    @Override
    public void markFaxAsRead(FaxConfig faxConfig, String faxReference) {
        // No-op for legacy gateway
    }

    /** {@inheritDoc} */
    @Override
    public String getIntegrationType() {
        return INTEGRATION_TYPE;
    }
}
