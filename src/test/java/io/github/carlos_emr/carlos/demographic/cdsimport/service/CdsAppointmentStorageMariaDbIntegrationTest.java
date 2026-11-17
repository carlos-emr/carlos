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

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

/** Real MariaDB storage/archive proof. Synthetic rows stay uncommitted and are always rolled back. */
@Tag("integration")
class CdsAppointmentStorageMariaDbIntegrationTest {
    @Test
    void shouldPreserveCompleteUnicodeSourceNotesAndStatus_throughNativeArchiveStorage()
            throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        Properties configuration = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            configuration.load(input);
        }
        try (Connection connection =
                DriverManager.getConnection(
                        configuration.getProperty("jdbcUrl"),
                        configuration.getProperty("jdbcUser"),
                        configuration.getProperty("jdbcPassword"))) {
            try (var query = connection.createStatement();
                    var rows = query.executeQuery("SELECT DATABASE()")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).matches("[A-Za-z0-9_]*(test|fixture)[A-Za-z0-9_]*");
            }
            try (var query = connection.createStatement();
                    var rows =
                            query.executeQuery(
                                    "SELECT table_name,data_type FROM information_schema.columns"
                                            + " WHERE table_schema=DATABASE() AND table_name IN"
                                            + " ('appointment','appointmentArchive') AND"
                                            + " column_name='notes' ORDER BY table_name")) {
                int count = 0;
                while (rows.next()) {
                    assertThat(rows.getString(2)).isIn("mediumtext", "longtext");
                    count++;
                }
                assertThat(count).isEqualTo(2);
            }
            connection.setAutoCommit(false);
            try {
                String notes = "😀".repeat(32768),
                        status = "S".repeat(250),
                        name = "CDS-OWNED-" + UUID.randomUUID();
                Appointment original = new Appointment();
                original.setNotes(notes);
                original.setImportedStatus(status);
                AppointmentArchive copy = new AppointmentArchive();
                org.springframework.beans.BeanUtils.copyProperties(
                        original, copy, new String[] {"id"});
                assertThat(copy.getNotes()).isEqualTo(notes);
                assertThat(copy.getImportedStatus()).isEqualTo(status);
                long id;
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " appointment(provider_no,appointment_date,start_time,end_time,name,demographic_no,notes,reason,imported_status)"
                                    + " VALUES('000000','2099-01-02','10:00:00','10:30:00',?,0,?,'owned"
                                    + " reason',?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, name);
                    insert.setString(2, notes);
                    insert.setString(3, status);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    try (var keys = insert.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        id = keys.getLong(1);
                    }
                }
                try (var archive =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " appointmentArchive(appointment_no,provider_no,appointment_date,start_time,end_time,name,demographic_no,notes,reason,imported_status)"
                                    + " SELECT"
                                    + " appointment_no,provider_no,appointment_date,start_time,end_time,name,demographic_no,notes,reason,imported_status"
                                    + " FROM appointment WHERE appointment_no=? AND name=?")) {
                    archive.setLong(1, id);
                    archive.setString(2, name);
                    assertThat(archive.executeUpdate()).isEqualTo(1);
                }
                try (var update =
                        connection.prepareStatement(
                                "UPDATE appointment SET reason='owned unrelated edit' WHERE"
                                        + " appointment_no=? AND name=?")) {
                    update.setLong(1, id);
                    update.setString(2, name);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                String expected =
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(notes.getBytes(StandardCharsets.UTF_8)));
                for (String table : new String[] {"appointment", "appointmentArchive"}) {
                    try (var query =
                            connection.prepareStatement(
                                    "SELECT"
                                        + " CHAR_LENGTH(notes),OCTET_LENGTH(notes),SHA2(notes,256),CHAR_LENGTH(imported_status)"
                                        + " FROM "
                                            + table
                                            + " WHERE appointment_no=? AND name=?")) {
                        query.setLong(1, id);
                        query.setString(2, name);
                        try (var rows = query.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getLong(1)).isEqualTo(32768);
                            assertThat(rows.getLong(2)).isEqualTo(131072);
                            assertThat(rows.getString(3)).isEqualTo(expected);
                            assertThat(rows.getInt(4)).isEqualTo(250);
                            assertThat(rows.next()).isFalse();
                        }
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
