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

    @Test
    void shouldAcceptEveryApprovedTrigger_whenTargetsCarryMoreThan1024Triggers() throws Exception {
        // 1,025 triggers on a target table, each approved: earlier builds refused past 1,024.
        var definitions = new java.util.ArrayList<String[]>();
        var approved = new java.util.HashMap<String, String>();
        for (int n = 0; n < 1025; n++) {
            String[] trigger = trigger("clinic_audit_" + n, "SET NEW.lastUpdateDate = NOW()");
            definitions.add(trigger);
            approved.put("carlos." + trigger[1], CdsImportTargetSafetyService.triggerDigest(single(trigger)));
        }
        when(triggers.executeQuery()).thenAnswer(call -> rows(definitions));
        when(firstPin.executeQuery(anyString())).thenAnswer(call -> rows(List.of()));
        when(owner.createStatement()).thenReturn(firstPin, account, firstPin, account);
        var approving =
                new CdsImportTargetSafetyService(
                        new CdsImportConfigurationService(new Properties()), Set.of("demographic")) {
                    @Override
                    java.util.Map<String, String> approvals() {
                        return approved;
                    }
                };
        approving.verifyAndPin(owner);
        verify(owner, never()).rollback();
        // One trigger that is not approved still refuses the import.
        approved.remove("carlos.clinic_audit_1024");
        assertThatThrownBy(() -> approving.verifyAndPin(owner))
                .isInstanceOf(CdsImportException.class)
                .hasMessage("IMPORT_TARGET_TRIGGER_APPROVAL_REQUIRED");
    }

    @Test
    void shouldDigestATrigger_whenItsDefinitionIsLongerThan1MiB() throws Exception {
        String body = "BEGIN " + "SET @clinic_audit = 1; ".repeat(60_000) + "END";
        org.assertj.core.api.Assertions.assertThat(body.length()).isGreaterThan(1 << 20);
        org.assertj.core.api.Assertions.assertThat(
                        CdsImportTargetSafetyService.triggerDigest(single(trigger("long", body))))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void shouldReadAnApprovalManifest_whenItIsLargerThan1MiB() throws Exception {
        var manifest = new StringBuilder("{\"format\":1,\"approvedTriggers\":{");
        for (int n = 0; n < 12_000; n++) {
            if (n > 0) manifest.append(',');
            manifest.append("\"carlos.clinic_audit_")
                    .append(n)
                    .append("\":\"")
                    .append(String.format("%064x", n))
                    .append('"');
        }
        // A name longer than the 256 characters earlier builds allowed is read too; it simply
        // approves no trigger that does not have it.
        manifest.append(",\"carlos.").append("x".repeat(300)).append("\":\"").append("f".repeat(64));
        manifest.append("\"}}");
        byte[] bytes = manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(bytes.length).isGreaterThan(1 << 20);
        var approved =
                CdsImportTargetSafetyService.approvedTriggers(
                        new java.io.ByteArrayInputStream(bytes));
        org.assertj.core.api.Assertions.assertThat(approved)
                .hasSize(12_001)
                .containsEntry("carlos.clinic_audit_11999", String.format("%064x", 11_999));
        assertThatThrownBy(
                        () ->
                                CdsImportTargetSafetyService.approvedTriggers(
                                        new java.io.ByteArrayInputStream(
                                                "{\"format\":1,\"approvedTriggers\":{\"a\":\"b\"}}"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8))))
                .isInstanceOf(CdsImportException.class)
                .hasMessage("IMPORT_TRIGGER_MANIFEST_INVALID");
    }

    @Test
    void shouldPinAndVerifyEveryTarget_whenTheCatalogueHasMoreThan256Tables() throws Exception {
        var names = new java.util.TreeSet<String>();
        for (int n = 0; n < 300; n++) names.add(String.format("clinic_table_%03d", n));
        var catalogue = new java.util.ArrayList<String[]>();
        for (String name : names) catalogue.add(new String[] {name, "InnoDB"});
        when(tables.executeQuery()).thenAnswer(call -> rows(catalogue));
        var pins = new java.util.ArrayList<Statement>();
        when(owner.createStatement())
                .thenAnswer(
                        call -> {
                            Statement pin = mock(Statement.class);
                            when(pin.executeQuery(anyString()))
                                    .thenAnswer(
                                            query -> {
                                                String sql = query.getArgument(0);
                                                return sql.equals("SELECT CURRENT_USER()")
                                                        ? rows(
                                                                List.<String[]>of(
                                                                        new String[] {
                                                                            "carlos@localhost"
                                                                        }))
                                                        : rows(List.of());
                                            });
                            pins.add(pin);
                            return pin;
                        });
        new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties()), names)
                .verifyAndPin(owner);
        // 300 tables in pin statements of 16, then the account read.
        org.assertj.core.api.Assertions.assertThat(pins).hasSize(19 + 1);
        verify(owner, never()).rollback();
    }

    /** One TRIGGERS row on the target table: schema, name, table, then its definition. */
    private static String[] trigger(String name, String statement) {
        return new String[] {
            "carlos", name, "demographic", "UPDATE", "BEFORE", statement, "STRICT_ALL_TABLES",
            "root@localhost", "utf8mb4", "utf8mb4_general_ci", "utf8mb4_general_ci", "1"
        };
    }

    /** A result positioned on its only row, as triggerDigest reads it. */
    private static ResultSet single(String[] row) throws SQLException {
        ResultSet rows = rows(List.<String[]>of(row));
        rows.next();
        return rows;
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
