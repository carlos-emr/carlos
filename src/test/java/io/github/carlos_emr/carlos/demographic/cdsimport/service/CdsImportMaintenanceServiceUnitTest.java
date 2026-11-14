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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportMaintenanceServiceUnitTest {
    @Test
    void shouldRefuseImportAcquisition_whenMaintenanceWasNotExplicitlyActivated() {
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.clinicalZone", java.time.ZoneId.systemDefault().getId());
        var pool = mock(OscarTrackingBasicDataSource.class);
        var service =
                new CdsImportMaintenanceService(pool, new CdsImportConfigurationService(policy));
        assertThatThrownBy(service::acquire)
                .isInstanceOf(CdsImportException.class)
                .hasMessage("MAINTENANCE_MODE_NOT_ACTIVATED");
        verifyNoInteractions(pool);
    }

    @Test
    void shouldRefuseImportAcquisition_whenPhysicalGuardIsNotInstalled() {
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.clinicalZone", java.time.ZoneId.systemDefault().getId());
        policy.setProperty("cds.import.maintenanceMode", "true");
        var pool = mock(OscarTrackingBasicDataSource.class);
        var service =
                new CdsImportMaintenanceService(pool, new CdsImportConfigurationService(policy));
        assertThatThrownBy(service::acquire)
                .isInstanceOf(CdsImportException.class)
                .hasMessage("MAINTENANCE_GUARD_NOT_READY");
    }

    @Test
    void shouldVerifyTheOwner_whenIsUsedLockAnswersAfterFiveSeconds() throws Exception {
        // A busy server that takes five seconds to answer the owner's lock probe: the probe is a
        // wait for the database (first deadline 30 s), not a 3-second failure of the import.
        var deadlines = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var connection =
                ownerConnection(
                        deadlines,
                        call -> {
                            Thread.sleep(5000);
                            return rows(42L);
                        });
        var owner = service(connection).acquire();
        long started = System.nanoTime();
        owner.heartbeat();
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
                .isGreaterThanOrEqualTo(java.time.Duration.ofSeconds(5));
        assertThat(deadlines).containsExactly(30);
    }

    @Test
    void shouldSendTheLockProbeAgain_whenTheServerEndsItAtItsDeadline() throws Exception {
        var deadlines = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var sent = new java.util.concurrent.atomic.AtomicInteger();
        var connection =
                ownerConnection(
                        deadlines,
                        call -> {
                            if (sent.incrementAndGet() == 1)
                                throw new java.sql.SQLTimeoutException(
                                        "synthetic lock probe deadline");
                            return rows(42L);
                        });
        var owner = service(connection).acquire();
        owner.heartbeat();
        assertThat(deadlines).containsExactly(30, 60);
        // Another failure still fences the owner at once.
        var lost = ownerConnection(new java.util.concurrent.CopyOnWriteArrayList<>(), call -> {
            throw new java.sql.SQLException("synthetic lost connection", "08006");
        });
        var fenced = service(lost).acquire();
        assertThatThrownBy(fenced::heartbeat)
                .isInstanceOf(CdsImportException.class)
                .hasMessage("MAINTENANCE_OWNER_FENCED");
    }

    private CdsImportMaintenanceService service(java.sql.Connection connection) throws Exception {
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.clinicalZone", java.time.ZoneId.systemDefault().getId());
        policy.setProperty("cds.import.maintenanceMode", "true");
        var pool = mock(OscarTrackingBasicDataSource.class);
        when(pool.getCdsAdmissionState())
                .thenReturn(
                        new io.github.carlos_emr.carlos.demographic.cdsimport.support
                                .CdsJdbcAdmissionState("cds_fixture"));
        when(pool.openUnpooledCdsMaintenanceConnection()).thenReturn(connection);
        return new CdsImportMaintenanceService(pool, new CdsImportConfigurationService(policy));
    }

    /**
     * The owner's dedicated connection: every acquisition statement answers at once; the owner's
     * IS_USED_LOCK probe answers as {@code probe} says, and each deadline it is sent with is kept.
     */
    private static java.sql.Connection ownerConnection(
            java.util.List<Integer> deadlines,
            org.mockito.stubbing.Answer<java.sql.ResultSet> probe)
            throws Exception {
        var connection = mock(java.sql.Connection.class);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            var statement = mock(java.sql.PreparedStatement.class);
                            if (sql.equals("SELECT IS_USED_LOCK(?)")) {
                                org.mockito.Mockito.doAnswer(
                                                set -> {
                                                    deadlines.add(set.getArgument(0));
                                                    return null;
                                                })
                                        .when(statement)
                                        .setQueryTimeout(org.mockito.ArgumentMatchers.anyInt());
                                when(statement.executeQuery()).thenAnswer(probe);
                            } else if (sql.startsWith("UPDATE")) {
                                when(statement.executeUpdate()).thenReturn(1);
                            } else if (sql.contains("wait_timeout")) {
                                when(statement.executeQuery()).thenAnswer(read -> rows(31_536_000L));
                            } else if (sql.contains("GET_LOCK")) {
                                when(statement.executeQuery()).thenAnswer(read -> rows(1L));
                            } else if (sql.contains("CONNECTION_ID")) {
                                when(statement.executeQuery()).thenAnswer(read -> rows(42L));
                            } else {
                                when(statement.executeQuery()).thenAnswer(read -> rows(7L));
                            }
                            return statement;
                        });
        return connection;
    }

    /** One row with one number. */
    private static java.sql.ResultSet rows(long value) throws Exception {
        var rows = mock(java.sql.ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong(1)).thenReturn(value);
        return rows;
    }
}
