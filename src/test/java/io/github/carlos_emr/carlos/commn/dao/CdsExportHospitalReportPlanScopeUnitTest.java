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

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The HRM capture plans read exactly the rows the reviewed inbox scope calls linked to the
 * patient ({@code CdsExportIndirectPatientScopeReadSupport.owned}): the captured rows and the rows
 * the catalogue no longer holds a chart for are the same rows, in every pinned profile.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportHospitalReportPlanScopeUnitTest {
    private static final Map<String, CdsExportPatientTableReadSupport.Plan> PLANS =
            Map.of(
                    "HRMDocument", CdsExportPatientTableReadSupport.Plan.HRM_REPORTS,
                    "HRMDocumentComment", CdsExportPatientTableReadSupport.Plan.HRM_REPORT_COMMENTS,
                    "HRMDocumentSubClass",
                            CdsExportPatientTableReadSupport.Plan.HRM_REPORT_SUBCLASSES,
                    "HRMDocumentToProvider", CdsExportPatientTableReadSupport.Plan.HRM_REPORT_ROUTES);

    private Connection db;

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:hrm_plans_" + UUID.randomUUID() + ";MODE=MySQL");
        db = source.getConnection();
        for (String sql :
                List.of(
                        "create table demographic(demographic_no int primary key)",
                        "create table HRMDocument(id int primary key)",
                        "create table HRMDocumentToDemographic(id int primary key,hrmDocumentId"
                            + " varchar(20),demographicNo varchar(20),cds_patient_key decimal(20,0)"
                            + " AS (CAST(demographicNo AS DECIMAL(20,0))))",
                        "create table HRMDocumentComment(id int primary key,hrmDocumentId int)",
                        "create table HRMDocumentSubClass(id int primary key,hrmDocumentId int)",
                        "create table HRMDocumentToProvider(id int primary key,hrmDocumentId"
                                + " varchar(20))",
                        "insert into demographic values(1),(2)",
                        // Reports 10 (patient 1), 11 (both), 12 (patient 2), 13 (unmatched).
                        "insert into HRMDocument values(10),(11),(12),(13)",
                        "insert into HRMDocumentToDemographic(id,hrmDocumentId,demographicNo)"
                            + " values(1,'10','1'),(2,'11','1'),(3,'11','2'),(4,'12','2'),"
                            + "(5,'99','1'),(6,'012','2')",
                        "insert into HRMDocumentComment values(20,10),(21,12),(22,13),(23,99)",
                        "insert into HRMDocumentSubClass values(30,11),(31,13)",
                        "insert into HRMDocumentToProvider values(40,'10'),(41,'11'),(42,'13'),"
                                + "(43,'012'),(44,'99')")) {
            try (var s = db.createStatement()) {
                s.execute(sql);
            }
        }
    }

    @AfterEach
    void close() throws Exception {
        if (db != null) db.close();
    }

    private TreeSet<Integer> ids(String table, String predicate, int patient) throws Exception {
        String sql = "select d.id from `" + table + "` d where " + predicate;
        int occurrences = sql.split(":patient", -1).length - 1;
        var result = new TreeSet<Integer>();
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            for (int i = 1; i <= occurrences; i++) s.setInt(i, patient);
            try (var rows = s.executeQuery()) {
                while (rows.next()) result.add(rows.getInt(1));
            }
        }
        return result;
    }

    @Test
    void shouldReadExactlyTheRowsTheInboxScopeLinksToThePatient_inEveryProfile() throws Exception {
        for (var profile : CdsExportSourceCatalogue.current().profiles())
            for (var entry : PLANS.entrySet())
                for (int patient : List.of(1, 2)) {
                    var owned =
                            ids(
                                    entry.getKey(),
                                    CdsExportIndirectPatientScopeReadSupport.owned(
                                            entry.getKey(), profile),
                                    patient);
                    var planned =
                            ids(entry.getKey(), entry.getValue().patientPredicate(), patient);
                    assertThat(planned)
                            .as(profile.name() + " " + entry.getKey() + " " + patient)
                            .isEqualTo(owned);
                }
        assertThat(ids("HRMDocument", PLANS.get("HRMDocument").patientPredicate(), 1))
                .containsExactly(10, 11);
        assertThat(ids("HRMDocument", PLANS.get("HRMDocument").patientPredicate(), 2))
                .containsExactly(11, 12);
        assertThat(
                        ids(
                                "HRMDocumentToProvider",
                                PLANS.get("HRMDocumentToProvider").patientPredicate(),
                                2))
                .containsExactly(41, 43);
        // The stored message files are read for exactly the reports read.
        assertThat(CdsExportPatientTableReadSupport.Plan.HRM_REPORT_FILES.patientPredicate())
                .isEqualTo(PLANS.get("HRMDocument").patientPredicate());
    }
}
