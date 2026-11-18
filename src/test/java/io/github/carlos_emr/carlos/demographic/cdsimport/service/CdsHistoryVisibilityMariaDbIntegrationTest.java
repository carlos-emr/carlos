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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;

import jakarta.persistence.NamedNativeQueries;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Executes the actual shipped native recent-note query against synthetic rolled-back MariaDB rows. */
@Tag("integration")
class CdsHistoryVisibilityMariaDbIntegrationTest {
    @Test
    void shouldShowUnknownDatedLatestNotes_withoutResurrectingPriorRevisionsOrCrossingPatients()
            throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            properties.load(input);
        }
        try (Connection connection =
                DriverManager.getConnection(
                        properties.getProperty("jdbcUrl"),
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"))) {
            try (var query = connection.createStatement();
                    var rows = query.executeQuery("SELECT DATABASE(),VERSION()")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1))
                        .isIn("cds_full_test", "carlos_cds_test", "cds_ledger_test");
                assertThat(rows.getString(2)).contains("MariaDB");
            }
            connection.setAutoCommit(false);
            try {
                long patient = patient(connection), other = patient(connection);
                String changedToUnknown = UUID.randomUUID().toString();
                note(connection, patient, changedToUnknown, "2025-01-02 10:00:00");
                long unknownLatest = note(connection, patient, changedToUnknown, null);
                long known =
                        note(
                                connection,
                                patient,
                                UUID.randomUUID().toString(),
                                "2025-01-03 11:00:00");
                String changedToOld = UUID.randomUUID().toString();
                note(connection, patient, changedToOld, "2025-01-04 12:00:00");
                note(connection, patient, changedToOld, "1999-01-01 12:00:00");
                note(connection, other, UUID.randomUUID().toString(), null);
                String actual =
                        Arrays.stream(
                                        CaseManagementNote.class
                                                .getAnnotation(NamedNativeQueries.class)
                                                .value())
                                .filter(query -> query.name().equals("mostRecentTime"))
                                .findFirst()
                                .orElseThrow()
                                .query()
                                .replace(":demographicNo", "?")
                                .replace(":staleDate", "?");
                Timestamp firstNativeRevision;
                try (var query =
                        connection.prepareStatement(
                                "SELECT MIN(update_date) FROM casemgmt_note WHERE uuid=?")) {
                    query.setString(1, changedToUnknown);
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        firstNativeRevision = rows.getTimestamp(1);
                    }
                }
                List<Long> ids = new ArrayList<>();
                try (var query = connection.prepareStatement(actual)) {
                    query.setLong(1, patient);
                    query.setTimestamp(2, Timestamp.valueOf("2020-01-01 00:00:00"));
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) {
                            ids.add(rows.getLong("id"));
                            if (rows.getLong("id") == unknownLatest) {
                                assertThat(rows.getTimestamp("observation_date")).isNull();
                                // This actual native reload uses the revision formula, not the
                                // original source creation clock tested in
                                // CdsCoreProjectionUnitTest.
                                assertThat(rows.getTimestamp("create_date"))
                                        .isEqualTo(firstNativeRevision)
                                        .isNotEqualTo(Timestamp.valueOf("1990-03-04 10:20:30"));
                            }
                        }
                    }
                }
                assertThat(ids).containsExactly(known, unknownLatest);
            } finally {
                connection.rollback();
            }
        }
    }

    private static long patient(Connection connection) throws Exception {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,lastUpdateDate)"
                            + " VALUES(?,'CDS','1971','03','04','F','AC','999998',NOW())",
                        Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, "FAKE-CDS-NOTE-" + UUID.randomUUID());
            assertThat(insert.executeUpdate()).isEqualTo(1);
            try (var keys = insert.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private static long note(Connection connection, long patient, String uuid, String observed)
            throws Exception {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " casemgmt_note(demographic_no,uuid,observation_date,update_date,provider_no,signing_provider_no,note,history,locked,archived)"
                            + " VALUES(?,?,?,NOW(),'','','Owned synthetic note','',0,0)",
                        Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, patient);
            insert.setString(2, uuid);
            insert.setTimestamp(3, observed == null ? null : Timestamp.valueOf(observed));
            assertThat(insert.executeUpdate()).isEqualTo(1);
            try (var keys = insert.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }
}
