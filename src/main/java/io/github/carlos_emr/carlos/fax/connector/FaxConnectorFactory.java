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
import io.github.carlos_emr.carlos.fax.connector.legacy.LegacyGatewayConnector;
import io.github.carlos_emr.carlos.fax.connector.srfax.SRFaxConnector;

/**
 * Factory that returns the appropriate {@link FaxConnector} implementation
 * based on a {@link FaxConfig}'s integration type.
 * <p>
 * If the integration type is null, empty, or unrecognized, defaults to
 * {@link LegacyGatewayConnector} for backward compatibility.
 *
 * @since 2026-02-09
 */
public final class FaxConnectorFactory {

    /** Singleton instance of the legacy gateway connector (default). */
    private static final LegacyGatewayConnector LEGACY_CONNECTOR = new LegacyGatewayConnector();
    /** Singleton instance of the SRFax direct API connector. */
    private static final SRFaxConnector SRFAX_CONNECTOR = new SRFaxConnector();

    /** Private constructor prevents instantiation of this utility class. */
    private FaxConnectorFactory() {
    }

    /**
     * Get the connector for the given fax configuration's integration type.
     *
     * @param faxConfig the fax account configuration
     * @return FaxConnector the appropriate connector implementation
     */
    public static FaxConnector getConnector(FaxConfig faxConfig) {
        if (faxConfig == null) {
            return LEGACY_CONNECTOR;
        }
        String type = faxConfig.getIntegrationType();
        return getConnectorByType(type);
    }

    /**
     * Get the connector for the given integration type string.
     *
     * @param integrationType String the integration type constant
     * @return FaxConnector the appropriate connector implementation
     */
    public static FaxConnector getConnectorByType(String integrationType) {
        if (integrationType == null || integrationType.isEmpty()) {
            return LEGACY_CONNECTOR;
        }

        switch (integrationType.toUpperCase()) {
            case SRFaxConnector.INTEGRATION_TYPE:
                return SRFAX_CONNECTOR;
            case LegacyGatewayConnector.INTEGRATION_TYPE:
            default:
                return LEGACY_CONNECTOR;
        }
    }

    /**
     * Check whether the given fax config uses the legacy gateway connector.
     *
     * @param faxConfig the fax account configuration
     * @return true if this config uses the legacy external gateway
     */
    public static boolean isLegacyGateway(FaxConfig faxConfig) {
        return getConnector(faxConfig) instanceof LegacyGatewayConnector;
    }

    /**
     * Check whether the given fax config uses a direct API connector (non-legacy).
     *
     * @param faxConfig the fax account configuration
     * @return true if this config uses a direct API connector like SRFax
     */
    public static boolean isDirectApiConnector(FaxConfig faxConfig) {
        return !isLegacyGateway(faxConfig);
    }
}
