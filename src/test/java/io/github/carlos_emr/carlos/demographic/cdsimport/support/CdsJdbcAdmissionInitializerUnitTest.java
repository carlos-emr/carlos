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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Feature-off startup preserves pool behavior while still refusing retained clinical holds. */
@Tag("unit")
class CdsJdbcAdmissionInitializerUnitTest {
    @Test
    void shouldReturnNativePoolConnections_whenImportAndGuardAreDisabled() throws Exception {
        try (var dataSource = dataSource()) {
            bootState(dataSource, false);
            new CdsJdbcAdmissionInitializer(dataSource, false, "off").initialize();
            try (Connection connection = dataSource.getConnection()) {
                assertThat(Proxy.isProxyClass(connection.getClass())).isFalse();
                assertThat(Proxy.isProxyClass(connection.getMetaData().getConnection().getClass())).isFalse();
                assertThat(dataSource.getCdsAdmissionState()).isNull();
            }
        } finally {
            OscarTrackingBasicDataSource.releaseThreadConnections();
        }
    }

    @Test
    void shouldKeepExistingPoolLeases_whenImportAndGuardAreDisabled() throws Exception {
        try (var dataSource = dataSource(); Connection existing = dataSource.getConnection()) {
            bootState(dataSource, false);
            assertThatCode(() -> new CdsJdbcAdmissionInitializer(dataSource, false, "off").initialize())
                    .doesNotThrowAnyException();
            assertThat(existing.isValid(1)).isTrue();
        } finally {
            OscarTrackingBasicDataSource.releaseThreadConnections();
        }
    }

    @Test
    void shouldProtectMetadata_whenImportsAreEnabledBeforeMaintenanceActivation() throws Exception {
        try (var dataSource = dataSource()) {
            bootState(dataSource, false);
            new CdsJdbcAdmissionInitializer(dataSource, false, true, "off").initialize();
            try (Connection connection = dataSource.getConnection()) {
                assertThat(Proxy.isProxyClass(connection.getMetaData().getConnection().getClass())).isTrue();
            }
        } finally {
            OscarTrackingBasicDataSource.releaseThreadConnections();
        }
    }

    @Test
    void shouldRetainStartupHoldCheck_whenImportAndGuardAreDisabled() throws Exception {
        try (var dataSource = dataSource()) {
            bootState(dataSource, true);
            assertThatThrownBy(() -> new CdsJdbcAdmissionInitializer(dataSource, false, "off").initialize())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maintenance/holds");
        }
    }

    private static OscarTrackingBasicDataSource dataSource() {
        var dataSource = spy(new OscarTrackingBasicDataSource());
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:cds-off-" + java.util.UUID.randomUUID());
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void bootState(OscarTrackingBasicDataSource dataSource, boolean held) throws Exception {
        Connection boot = mock(Connection.class);
        PreparedStatement engine = mock(PreparedStatement.class), gate = mock(PreparedStatement.class);
        ResultSet engineRows = mock(ResultSet.class), gateRows = mock(ResultSet.class);
        doReturn(boot).when(dataSource).openUnpooledCdsMaintenanceConnection();
        when(boot.prepareStatement(anyString())).thenReturn(engine, gate);
        when(boot.getCatalog()).thenReturn("cds_off");
        when(engine.executeQuery()).thenReturn(engineRows);
        when(engineRows.next()).thenReturn(true);
        when(engineRows.getString(1)).thenReturn("InnoDB");
        when(gate.executeQuery()).thenReturn(gateRows);
        when(gateRows.next()).thenReturn(true);
        when(gateRows.getBoolean(1)).thenReturn(held);
    }
}
