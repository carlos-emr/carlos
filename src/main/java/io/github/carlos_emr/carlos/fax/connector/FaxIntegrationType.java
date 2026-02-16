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

/**
 * Constants for fax integration types used in {@link io.github.carlos_emr.carlos.commn.model.FaxConfig}.
 * <p>
 * These constants define the valid values for the {@code integrationType} field and are used
 * by {@link FaxConnectorFactory} to determine which {@link FaxConnector} implementation to use
 * for fax send, receive, and status operations.
 * <p>
 * Integration types:
 * <ul>
 *   <li><b>LEGACY_GATEWAY</b> - External CXF REST gateway server (backward compatible)</li>
 *   <li><b>SRFAX</b> - Direct SRFax cloud API integration</li>
 * </ul>
 * An empty or null {@code integrationType} in {@code FaxConfig} defaults to {@code LEGACY_GATEWAY}.
 *
 * @see io.github.carlos_emr.carlos.commn.model.FaxConfig
 * @see FaxConnectorFactory
 * @since 2026-02-15
 */
public final class FaxIntegrationType {

    /** Integration type for the legacy external fax gateway server. */
    public static final String LEGACY_GATEWAY = "LEGACY_GATEWAY";

    /** Integration type for SRFax direct API integration. */
    public static final String SRFAX = "SRFAX";

    /**
     * Private constructor to prevent instantiation of this constants class.
     */
    private FaxIntegrationType() {
        throw new AssertionError("FaxIntegrationType is a constants class and should not be instantiated");
    }
}
