/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

@Tag("integration")
@Tag("cds-export")
@Isolated("Rollback-only metadata fixtures in the owned export database")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportIndirectPatientScopeMariaDbIntegrationTest {
    private Connection db;
    private CdsExportSourceCatalogue.Profile profile;
    private int selected, other;

    @BeforeEach
    void setup() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        String url = properties.getProperty("jdbcUrl", "");
        if (!url.matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException(
                    "Owned disposable cds_export_test database required");
        db =
                DriverManager.getConnection(
                        url,
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"));
        db.setAutoCommit(false);
        profile =
                CdsExportSourceCatalogue.current().profiles().stream()
                        .filter(p -> p.name().equals("on"))
                        .findFirst()
                        .orElseThrow();
        selected = patient();
        other = patient();
    }

    @AfterEach
    void close() throws Exception {
        if (db != null)
            try {
                db.rollback();
            } finally {
                db.close();
            }
    }

    private int patient() throws Exception {
        return insert(
                "INSERT INTO"
                    + " demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,lastUpdateDate)"
                    + " VALUES('SYNTHETIC"
                    + " Scope','Fixture','2000','01','02','F','AC','999998',NOW())");
    }

    private int insert(String sql, Object... values) throws Exception {
        try (var s = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            assertThat(s.executeUpdate()).isEqualTo(1);
            try (var keys = s.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getInt(1);
            }
        }
    }

    private void update(String sql, Object... values) throws Exception {
        try (var s = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            assertThat(s.executeUpdate()).isEqualTo(1);
        }
    }

    private boolean blocked(String table, int patient) throws Exception {
        String sql =
                "SELECT 1 FROM `"
                        + table
                        + "` d WHERE "
                        + CdsExportIndirectPatientScopeReadSupport.predicate(table, profile);
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            s.setInt(1, patient);
            s.setMaxRows(1);
            s.setQueryTimeout(20);
            try (var rows = s.executeQuery()) {
                return rows.next();
            }
        }
    }

    @Test
    void shouldResolveFormChildOwnership_onActualMariaDbAndKeepOrphansBlocking() throws Exception {
        int parent =
                insert(
                        "INSERT INTO"
                            + " CdsClientForm(clientId,cdsFormVersion,facilityId,providerNo,created,signed)"
                            + " VALUES(?,'scope-test',0,'999998',NOW(),0)",
                        other);
        insert(
                "INSERT INTO CdsClientFormData(cdsClientFormId,question,answer)"
                        + " VALUES(?,'synthetic','value')",
                parent);
        assertThat(blocked("CdsClientFormData", selected)).isFalse();
        assertThat(blocked("CdsClientFormData", other)).isTrue();
        update("UPDATE CdsClientForm SET clientId=? WHERE id=?", selected, parent);
        assertThat(blocked("CdsClientFormData", selected)).isTrue();
        update("DELETE FROM CdsClientForm WHERE id=?", parent);
        assertThat(blocked("CdsClientFormData", selected)).isTrue();
    }

    @Test
    void shouldResolveTheWholeDocumentLink_onActualMariaDbWithoutTreatingProviderModulesAsPatients()
            throws Exception {
        int document =
                insert(
                        "INSERT INTO"
                            + " document(doctype,docdesc,docxml,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram)"
                            + " VALUES('scope','synthetic','','scope-unused.txt','999998','999998','A','text/plain',0,1,0)");
        update(
                "INSERT INTO ctl_document(module,module_id,document_no,status)"
                        + " VALUES('demographic',?,?,'A')",
                other,
                document);
        insert(
                "INSERT INTO DocumentExtraReviewer(documentNo,reviewerProviderNo,reviewDateTime)"
                        + " VALUES(?,'999998',NOW())",
                document);
        assertThat(blocked("DocumentExtraReviewer", selected)).isFalse();
        assertThat(blocked("DocumentExtraReviewer", other)).isTrue();
        update("UPDATE ctl_document SET module='provider' WHERE document_no=?", document);
        assertThat(blocked("DocumentExtraReviewer", selected)).isTrue();
        update("DELETE FROM document WHERE document_no=?", document);
        assertThat(blocked("DocumentExtraReviewer", selected)).isTrue();
    }

    @Test
    void shouldKeepLegacyLabTypesDistinct_onActualMariaDbAndRefuseAnOrphanProviderRoute()
            throws Exception {
        int lab = 1_900_000_000 + selected;
        int patientRoute =
                insert(
                        "INSERT INTO"
                            + " patientLabRouting(lab_no,lab_type,demographic_no,created,dateModified)"
                            + " VALUES(?,'HL7',?,NOW(),NOW())",
                        lab,
                        other);
        insert(
                "INSERT INTO patientLabRouting(lab_no,lab_type,demographic_no,created,dateModified)"
                        + " VALUES(?,'MDS',?,NOW(),NOW())",
                lab,
                selected);
        insert(
                "INSERT INTO providerLabRouting(lab_no,lab_type,provider_no,status,timestamp)"
                        + " VALUES(?,'HL7','999998','N',NOW())",
                lab);
        assertThat(blocked("providerLabRouting", selected)).isFalse();
        assertThat(blocked("providerLabRouting", other)).isTrue();
        update("DELETE FROM patientLabRouting WHERE id=?", patientRoute);
        assertThat(blocked("providerLabRouting", selected)).isTrue();
    }
}
