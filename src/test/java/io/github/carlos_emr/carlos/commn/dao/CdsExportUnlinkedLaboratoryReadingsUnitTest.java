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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The laboratory readings the ReadMe lists for a chart are exactly the HL7 results filed as
 * measurements whose report is not routed to that chart as an HL7 lab, counted by test name and
 * observation date ({@code CdsExportPatientTableReadSupport.UNLINKED_LABORATORY_READINGS}), run on
 * H2. H2 reads {@code CAST(x AS BINARY)} and {@code CAST(x AS CHAR)} as one byte and one
 * character, so the fixture's report numbers differ in their first digit; the exact byte
 * comparison is proven on MariaDB ({@code CdsExportMeasurementReadMariaDbIntegrationTest}).
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportUnlinkedLaboratoryReadingsUnitTest {
    private Connection db;

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:unlinked_readings_" + UUID.randomUUID() + ";MODE=MySQL");
        db = source.getConnection();
        for (String sql :
                List.of(
                        "create table measurements(id int primary key, demographicNo int,"
                                + " dateObserved datetime)",
                        "create table measurementsExt(id int primary key, measurement_id int,"
                                + " keyval varchar(20), val text)",
                        "create table patientLabRouting(id int primary key, demographic_no int,"
                                + " lab_no int, lab_type varchar(10))",
                        // Patient 1 has HL7 report 77 and a legacy MDS route numbered 88;
                        // patient 2 has HL7 report 99.
                        "insert into patientLabRouting values(1,1,77,'HL7'),(2,1,88,'MDS'),"
                                + "(3,2,99,'HL7')",
                        "insert into measurements values"
                                // 10: filed from report 77, still routed: exported with it.
                                + "(10,1,'2020-02-29 08:00:00'),"
                                // 11, 12: report 88 is no HL7 report of the chart: one line.
                                + "(11,1,'2020-02-29 08:00:00'),(12,1,'2020-02-29 17:30:00'),"
                                // 13: report 99 was moved to patient 2.
                                + "(13,1,'2020-03-01 09:00:00'),"
                                // 14: a care reading: no lab_no, never a laboratory reading.
                                + "(14,1,'2020-03-02 09:00:00'),"
                                // 15: unlinked, with no name; 16: unlinked, two names.
                                + "(15,1,null),(16,1,'2020-03-03 10:00:00'),"
                                // 20: patient 2's own reading of report 99.
                                + "(20,2,'2020-03-01 09:00:00')",
                        "insert into measurementsExt values"
                                + "(100,10,'lab_no','77'),(101,10,'name','HbA1c'),"
                                + "(110,11,'lab_no','88'),(111,11,'name','HbA1c'),"
                                + "(120,12,'lab_no','88'),(121,12,'name','HbA1c'),"
                                + "(130,13,'lab_no','99'),(131,13,'name','Glucose'),"
                                + "(140,14,'weight','80'),(141,14,'name','Weight'),"
                                + "(150,15,'lab_no','88'),"
                                + "(161,16,'name','Potassium'),(162,16,'name','K'),"
                                + "(163,16,'lab_no','88'),"
                                + "(200,20,'lab_no','99'),(201,20,'name','Glucose')")) {
            try (var s = db.createStatement()) {
                s.execute(sql);
            }
        }
    }

    @AfterEach
    void close() throws Exception {
        if (db != null) db.close();
    }

    private List<String> readings(int patient) throws Exception {
        String sql = CdsExportPatientTableReadSupport.UNLINKED_LABORATORY_READINGS;
        int occurrences = sql.split(":patient", -1).length - 1;
        var result = new ArrayList<String>();
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            for (int i = 1; i <= occurrences; i++) s.setInt(i, patient);
            try (var rows = s.executeQuery()) {
                while (rows.next())
                    result.add(
                            rows.getString(1)
                                    + " | "
                                    + (rows.getObject(2) == null ? "" : rows.getObject(2).toString())
                                    + " | "
                                    + rows.getLong(3));
            }
        }
        result.sort(null);
        return result;
    }

    @Test
    void shouldCountTheReadingsWhoseReportIsNotRoutedToTheChart_byTestNameAndDate()
            throws Exception {
        assertThat(readings(1))
                .containsExactly(
                        // A reading with no stored date and no name.
                        " |  | 1",
                        "Glucose | 2020-03-01 | 1",
                        "HbA1c | 2020-02-29 | 2",
                        // Two name extensions: the first stored.
                        "Potassium | 2020-03-03 | 1");
        // Patient 2's reading of report 99 is routed to patient 2: exported with the report.
        assertThat(readings(2)).isEmpty();
        assertThat(readings(3)).isEmpty();
    }
}
