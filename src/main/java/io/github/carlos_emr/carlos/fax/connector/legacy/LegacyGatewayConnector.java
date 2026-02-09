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
import io.github.carlos_emr.carlos.fax.connector.FaxSendResult;
import io.github.carlos_emr.carlos.fax.connector.FaxStatusCheckResult;

import java.util.Collections;
import java.util.List;

/**
 * {@link FaxConnector} implementation for the legacy external fax gateway server.
 * <p>
 * This connector is a marker/passthrough that indicates fax accounts using
 * the legacy external CXF REST gateway. The actual send/receive/status logic
 * remains in {@code FaxSender}, {@code FaxImporter}, and {@code FaxStatusUpdater}
 * for backward compatibility. When the integration type is "LEGACY_GATEWAY",
 * the existing code path is used unchanged.
 * <p>
 * This ensures zero disruption to existing deployments using the external
 * fax gateway while allowing new deployments to use SRFax or other connectors.
 *
 * @since 2026-02-09
 */
public class LegacyGatewayConnector implements FaxConnector {

    public static final String INTEGRATION_TYPE = "LEGACY_GATEWAY";

    @Override
    public FaxSendResult sendFax(FaxConfig faxConfig, FaxJob faxJob) {
        // Legacy path: handled directly by FaxSender's original code
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxSender code path, not the connector interface");
    }

    @Override
    public List<FaxInboundResult> pollIncomingFaxes(FaxConfig faxConfig) {
        // Legacy path: handled directly by FaxImporter's original code
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxImporter code path, not the connector interface");
    }

    @Override
    public String downloadFax(FaxConfig faxConfig, String faxReference) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxImporter code path, not the connector interface");
    }

    @Override
    public boolean deleteFax(FaxConfig faxConfig, String faxReference) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxImporter code path, not the connector interface");
    }

    @Override
    public FaxStatusCheckResult checkFaxStatus(FaxConfig faxConfig, long externalJobId) {
        throw new UnsupportedOperationException(
                "Legacy gateway uses direct FaxStatusUpdater code path, not the connector interface");
    }

    @Override
    public void markFaxAsRead(FaxConfig faxConfig, String faxReference) {
        // No-op for legacy gateway
    }

    @Override
    public String getIntegrationType() {
        return INTEGRATION_TYPE;
    }
}
