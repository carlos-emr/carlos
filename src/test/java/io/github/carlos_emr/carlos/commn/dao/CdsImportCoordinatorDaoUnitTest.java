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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

/** Acquisition must preserve its original failure and retire untransferred physical connections. */
@Tag("unit")
class CdsImportCoordinatorDaoUnitTest {
    private final OscarTrackingBasicDataSource source = mock(OscarTrackingBasicDataSource.class);
    private final Connection connection = mock(Connection.class);
    private final ResultSet acquisition = mock(ResultSet.class);
    private final CdsImportCoordinatorDao dao = new CdsImportCoordinatorDao(source);

    @BeforeEach void physicalConnectionWithAvailableCoordinator() throws Exception {
        when(source.openUnpooledCdsMaintenanceConnection()).thenReturn(connection);
        PreparedStatement databaseQuery = mock(PreparedStatement.class);
        ResultSet database = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(databaseQuery);
        when(databaseQuery.executeQuery()).thenReturn(database);
        when(database.next()).thenReturn(true, false);
        when(database.getString(1)).thenReturn("cds_control_fixture_test");
        PreparedStatement acquisitionQuery = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?,0)")).thenReturn(acquisitionQuery);
        when(acquisitionQuery.executeQuery()).thenReturn(acquisition);
        when(acquisition.next()).thenReturn(true, false);
        when(acquisition.getLong(1)).thenReturn(1L);
    }

    @Test void successfulAcquisitionTransfersConnectionUntilLeaseRetirement() throws Exception {
        PreparedStatement releaseQuery = mock(PreparedStatement.class);
        ResultSet released = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(releaseQuery);
        when(releaseQuery.executeQuery()).thenReturn(released);
        when(released.next()).thenReturn(true, false);
        when(released.getLong(1)).thenReturn(1L);
        var lease = dao.acquire();
        assertThat(lease).isNotNull();
        verify(connection, never()).close();
        verify(connection).setAutoCommit(true);
        lease.close();
        lease.close();
        verify(connection).close();
        verify(source, never()).getConnection();
    }

    @Test void busyCoordinatorRetiresPhysicalConnectionExactlyOnce() throws Exception {
        when(acquisition.getLong(1)).thenReturn(0L);
        assertThat(dao.acquire()).isNull();
        verify(connection).close();
        verify(source, never()).getConnection();
    }

    @Test void busyConnectionRetirementFailureCannotBecomeAnOrdinaryBusyResult() throws Exception {
        when(acquisition.getLong(1)).thenReturn(0L);
        SQLException cleanup = new SQLException("synthetic cleanup", "08006");
        doThrow(cleanup).when(connection).close();
        assertThat(catchThrowable(dao::acquire)).isSameAs(cleanup);
        verify(connection).close();
    }

    @Test void acquisitionFailureKeepsOriginalSqlFailureAndSuppressedRetirementFailure() throws Exception {
        SQLException primary = new SQLException("synthetic acquisition", "42000");
        SQLException cleanup = new SQLException("synthetic cleanup", "08006");
        when(connection.prepareStatement("SELECT DATABASE()")).thenThrow(primary);
        doThrow(cleanup).when(connection).close();
        assertThat(catchThrowable(dao::acquire)).isSameAs(primary);
        assertThat(primary.getSuppressed()).containsExactly(cleanup);
        verify(connection).close();
    }

    @Test void runtimeAcquisitionFailureKeepsPrimaryEvenWhenRetirementThrowsAnError() throws Exception {
        IllegalStateException primary = new IllegalStateException("synthetic acquisition");
        AssertionError cleanup = new AssertionError("synthetic cleanup");
        when(connection.prepareStatement("SELECT DATABASE()")).thenThrow(primary);
        doThrow(cleanup).when(connection).close();
        assertThat(catchThrowable(dao::acquire)).isSameAs(primary);
        assertThat(primary.getSuppressed()).containsExactly(cleanup);
        verify(connection).close();
    }

    @Test void errorDuringAcquisitionRetiresConnectionWithoutSelfSuppression() throws Exception {
        AssertionError primary = new AssertionError("synthetic acquisition");
        when(connection.prepareStatement("SELECT DATABASE()")).thenThrow(primary);
        doThrow(primary).when(connection).close();
        assertThat(catchThrowable(dao::acquire)).isSameAs(primary);
        assertThat(primary.getSuppressed()).isEmpty();
        verify(connection).close();
    }

    @Test void malformedAcquisitionResultFailsAndRetiresConnection() throws Exception {
        when(acquisition.getLong(1)).thenReturn(2L);
        assertThat(catchThrowable(dao::acquire)).isInstanceOf(SQLException.class)
                .hasMessage("Invalid coordinator acquisition result");
        verify(connection).close();
    }
}
