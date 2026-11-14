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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The target-safety catalogue reads run on the maintenance owner's connection and wait for the
 * database: a metadata pin the server ends at its deadline is sent again in the same transaction,
 * keeping the pins already taken, instead of failing the import at a fixed ten seconds.
 */
@Tag("unit")
class CdsImportTargetSafetyServiceUnitTest {
    private final Connection owner = mock(Connection.class);
    private final PreparedStatement tables = mock(PreparedStatement.class);
    private final PreparedStatement privileges = mock(PreparedStatement.class);
    private final PreparedStatement triggers = mock(PreparedStatement.class);
    private final Statement firstPin = mock(Statement.class);
    private final Statement secondPin = mock(Statement.class);
    private final Statement account = mock(Statement.class);
    private CdsImportTargetSafetyService safety;

    @BeforeEach
    void catalogue() throws Exception {
        safety =
                new CdsImportTargetSafetyService(
                        new CdsImportConfigurationService(new Properties()), Set.of("demographic"));
        when(owner.prepareStatement(anyString()))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            if (sql.contains("information_schema.TABLES ")) return tables;
                            if (sql.contains("USER_PRIVILEGES")) return privileges;
                            if (sql.contains("information_schema.TRIGGERS")) return triggers;
                            throw new AssertionError("Unexpected catalogue statement");
                        });
        when(tables.executeQuery())
                .thenAnswer(call -> rows(List.<String[]>of(new String[] {"demographic", "InnoDB"})));
        when(privileges.executeQuery()).thenAnswer(call -> rows(List.<String[]>of(new String[] {"1"})));
        when(triggers.executeQuery()).thenAnswer(call -> rows(List.of()));
        when(owner.createStatement()).thenReturn(firstPin, secondPin, account);
        when(account.executeQuery("SELECT CURRENT_USER()"))
                .thenAnswer(call -> rows(List.<String[]>of(new String[] {"carlos@localhost"})));
    }

    @Test
    void shouldPinTheTargetsAgain_whenTheServerEndsAPinAtItsDeadline() throws Exception {
        when(firstPin.executeQuery(anyString()))
                .thenThrow(new SQLTimeoutException("synthetic metadata lock deadline"));
        when(secondPin.executeQuery(anyString())).thenAnswer(call -> rows(List.of()));
        safety.verifyAndPin(owner);
        var order = inOrder(firstPin, secondPin);
        order.verify(firstPin).setQueryTimeout(30);
        order.verify(firstPin).executeQuery("SELECT 1 FROM `demographic` AS `cds_pin_0` LIMIT 0");
        order.verify(secondPin).setQueryTimeout(60);
        order.verify(secondPin).executeQuery("SELECT 1 FROM `demographic` AS `cds_pin_0` LIMIT 0");
        // The pins stay held: nothing is rolled back, and nothing is committed.
        verify(owner, never()).rollback();
        verify(owner, never()).commit();
    }

    @Test
    void shouldRefuseTheCatalogue_whenAPinFailsForAnotherReason() throws Exception {
        AtomicInteger sent = new AtomicInteger();
        when(firstPin.executeQuery(anyString()))
                .thenAnswer(
                        call -> {
                            sent.incrementAndGet();
                            throw new SQLException("synthetic lock wait timeout", "HY000", 1205);
                        });
        assertThatThrownBy(() -> safety.verifyAndPin(owner))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        refused ->
                                org.assertj.core.api.Assertions.assertThat(refused.getCode())
                                        .isEqualTo("IMPORT_TARGET_CATALOGUE_UNAVAILABLE"));
        org.assertj.core.api.Assertions.assertThat(sent).hasValue(1);
        verify(owner).rollback();
    }

    /** A forward-only result whose columns are read as strings. */
    private static ResultSet rows(List<String[]> values) throws SQLException {
        ResultSet rows = mock(ResultSet.class);
        AtomicInteger at = new AtomicInteger(-1);
        when(rows.next()).thenAnswer(call -> at.incrementAndGet() < values.size());
        when(rows.getString(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> values.get(at.get())[(int) call.getArgument(0) - 1]);
        return rows;
    }
}
