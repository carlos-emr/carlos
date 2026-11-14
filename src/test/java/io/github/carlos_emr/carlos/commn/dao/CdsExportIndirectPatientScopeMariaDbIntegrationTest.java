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

    /** Whether one fixture row holds the patient; other rows of the shared database are ignored. */
    private boolean blocked(String table, String key, int id, int patient) throws Exception {
        return matches(
                table,
                key,
                id,
                CdsExportIndirectPatientScopeReadSupport.predicate(table, profile),
                patient);
    }

    private boolean unlinked(String table, String key, int id) throws Exception {
        return matches(
                table,
                key,
                id,
                CdsExportIndirectPatientScopeReadSupport.unlinked(table, profile),
                null);
    }

    private boolean unattributable(String table, String key, int id) throws Exception {
        return matches(
                table,
                key,
                id,
                CdsExportIndirectPatientScopeReadSupport.unattributable(table, profile),
                null);
    }

    private boolean matches(String table, String key, int id, String predicate, Integer patient)
            throws Exception {
        String sql = "SELECT 1 FROM `" + table + "` d WHERE d.`" + key + "`=? AND " + predicate;
        // A table with several reviewed chains names the patient once per chain.
        int occurrences = sql.split(":patient", -1).length - 1;
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            s.setInt(1, id);
            for (int i = 0; i < occurrences; i++) s.setInt(i + 2, patient);
            s.setMaxRows(1);
            s.setQueryTimeout(20);
            try (var rows = s.executeQuery()) {
                return rows.next();
            }
        }
    }

    private int document() throws Exception {
        return insert(
                "INSERT INTO"
                    + " document(doctype,docdesc,docxml,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram)"
                    + " VALUES('scope','synthetic','','scope-unused.txt','999998','999998','A','text/plain',0,1,0)");
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
    void shouldPrepareTheActualInstalledLabFixture_onTheStrictPhysicalSchemaBeforePackaging(
            @org.junit.jupiter.api.io.TempDir Path compiled) throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        String classpath =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        try (var diagnostics = new java.io.ByteArrayOutputStream()) {
            int result =
                    compiler.run(
                            null,
                            diagnostics,
                            diagnostics,
                            "--release",
                            "25",
                            "-encoding",
                            "UTF-8",
                            "-proc:none",
                            "-classpath",
                            classpath,
                            "-d",
                            compiled.toString(),
                            Path.of("scripts/ci/CdsExportInstalledFixture.java")
                                    .toAbsolutePath()
                                    .toString());
            assertThat(result)
                    .withFailMessage("The actual installed fixture helper must compile")
                    .isZero();
        }
        try (var loader =
                new java.net.URLClassLoader(
                        new java.net.URL[] {compiled.toUri().toURL()},
                        getClass().getClassLoader())) {
            var type =
                    loader.loadClass("io.github.carlos_emr.carlos.login.CdsExportInstalledFixture");
            var grant = type.getDeclaredMethod("nativeReadGrant", Connection.class);
            grant.setAccessible(true);
            grant.invoke(null, db);
            try (var check = db.createStatement();
                    var granted =
                            check.executeQuery(
                                    "SELECT COUNT(*) FROM secObjPrivilege WHERE"
                                            + " objectName='_lab_sensitive' AND privilege='r' AND"
                                            + " roleUserGroup=provider_no")) {
                assertThat(granted.next()).isTrue();
                assertThat(granted.getInt(1)).isEqualTo(1);
            }
            var prescriptions =
                    type.getDeclaredMethod("nativeMedications", Connection.class, int.class);
            prescriptions.setAccessible(true);
            try {
                prescriptions.invoke(null, db, selected);
            } catch (java.lang.reflect.InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception cause) throw cause;
                throw failure;
            }
            var prepare = type.getDeclaredMethod("nativeLaboratory", Connection.class, int.class);
            prepare.setAccessible(true);
            try {
                prepare.invoke(null, db, selected);
            } catch (java.lang.reflect.InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception cause) throw cause;
                throw failure;
            }
        }
        try (var statement =
                db.prepareStatement(
                        "SELECT COUNT(*) FROM patientLabRouting WHERE demographic_no=? AND"
                                + " lab_type='HL7'")) {
            statement.setInt(1, selected);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
        assertThat(CdsExportSourceCatalogue.current().tables().get("fileUploadCheck"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.OPERATIONAL);
    }

    @Test
    void shouldKeepLegacyLabTypesDistinct_onActualMariaDbAndCountAnUnmatchedProviderRoute()
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
        int route =
                insert(
                        "INSERT INTO"
                            + " providerLabRouting(lab_no,lab_type,provider_no,status,timestamp)"
                            + " VALUES(?,'HL7','999998','N',NOW())",
                        lab);
        assertThat(blocked("providerLabRouting", "id", route, selected)).isFalse();
        assertThat(blocked("providerLabRouting", "id", route, other)).isTrue();
        // The MDS lab with the same number is another report: the HL7 lab is now unmatched.
        update("DELETE FROM patientLabRouting WHERE id=?", patientRoute);
        assertThat(blocked("providerLabRouting", "id", route, selected)).isFalse();
        assertThat(blocked("providerLabRouting", "id", route, other)).isFalse();
        assertThat(unlinked("providerLabRouting", "id", route)).isTrue();
    }

    @Test
    void shouldCountUnmatchedInboxLabs_onActualMariaDbAndHoldMalformedRoutes() throws Exception {
        int lab = 1_910_000_000 + selected;
        int route =
                insert(
                        "INSERT INTO"
                            + " providerLabRouting(lab_no,lab_type,provider_no,status,timestamp)"
                            + " VALUES(?,'HL7','0','N',NOW())",
                        lab);
        assertThat(unlinked("providerLabRouting", "id", route)).isTrue();
        assertThat(blocked("providerLabRouting", "id", route, selected)).isFalse();
        // Unlinked from a chart: PatientLabRoutingDao.UNMATCHED.
        int patientRoute =
                insert(
                        "INSERT INTO"
                            + " patientLabRouting(lab_no,lab_type,demographic_no,created,dateModified)"
                            + " VALUES(?,'HL7',0,NOW(),NOW())",
                        lab);
        assertThat(unlinked("providerLabRouting", "id", route)).isTrue();
        // The same type under the column collation but not byte for byte: never unlinked.
        update(
                "UPDATE patientLabRouting SET lab_type='hl7',demographic_no=? WHERE id=?",
                selected,
                patientRoute);
        assertThat(unlinked("providerLabRouting", "id", route)).isFalse();
        assertThat(unattributable("providerLabRouting", "id", route)).isTrue();
        assertThat(blocked("providerLabRouting", "id", route, other)).isTrue();
        // A link to a chart that does not exist could be anyone's.
        update(
                "UPDATE patientLabRouting SET lab_type='HL7',demographic_no=? WHERE id=?",
                Integer.MAX_VALUE - 7,
                patientRoute);
        assertThat(unlinked("providerLabRouting", "id", route)).isFalse();
        assertThat(blocked("providerLabRouting", "id", route, other)).isTrue();
    }

    @Test
    void shouldCountIncomingDocumentRoutesAndReviewers_onActualMariaDbAsUnlinked() throws Exception {
        int document = document();
        update(
                "INSERT INTO ctl_document(module,module_id,document_no,status)"
                        + " VALUES('demographic',-1,?,'A')",
                document);
        int route =
                insert(
                        "INSERT INTO"
                            + " providerLabRouting(lab_no,lab_type,provider_no,status,timestamp)"
                            + " VALUES(?,'DOC','0','N',NOW())",
                        document);
        int extra =
                insert(
                        "INSERT INTO DocumentExtraReviewer(documentNo,reviewerProviderNo,reviewDateTime)"
                                + " VALUES(?,'999998',NOW())",
                        document);
        int review =
                insert(
                        "INSERT INTO document_review(document_no,provider_no,date_reviewed)"
                                + " VALUES(?,'999998',NOW())",
                        document);
        assertThat(unlinked("providerLabRouting", "id", route)).isTrue();
        assertThat(unlinked("DocumentExtraReviewer", "id", extra)).isTrue();
        assertThat(unlinked("document_review", "id", review)).isTrue();
        assertThat(blocked("providerLabRouting", "id", route, selected)).isFalse();
        assertThat(blocked("document_review", "id", review, selected)).isFalse();
        // Filed to a chart, the same rows belong to that chart only.
        update(
                "UPDATE ctl_document SET module_id=? WHERE document_no=?", other, document);
        assertThat(unlinked("providerLabRouting", "id", route)).isFalse();
        assertThat(blocked("providerLabRouting", "id", route, other)).isTrue();
        assertThat(blocked("providerLabRouting", "id", route, selected)).isFalse();
        assertThat(blocked("DocumentExtraReviewer", "id", extra, other)).isTrue();
        assertThat(blocked("DocumentExtraReviewer", "id", extra, selected)).isFalse();
    }

    @Test
    void shouldCountAnHrmReportMatchedToNoPatient_onActualMariaDbAsUnlinked() throws Exception {
        int report =
                insert(
                        "INSERT INTO HRMDocument(reportType,reportStatus,timeReceived)"
                                + " VALUES('synthetic','S',NOW())");
        int recipient =
                insert(
                        "INSERT INTO HRMDocumentToProvider(hrmDocumentId,providerNo,signedOff,viewed)"
                                + " VALUES(?,'999998',0,0)",
                        Integer.toString(report));
        assertThat(unlinked("HRMDocument", "id", report)).isTrue();
        assertThat(unlinked("HRMDocumentToProvider", "id", recipient)).isTrue();
        assertThat(blocked("HRMDocumentToProvider", "id", recipient, selected)).isFalse();
        insert(
                "INSERT INTO HRMDocumentToDemographic(demographicNo,hrmDocumentId,timeAssigned)"
                        + " VALUES(?,?,NOW())",
                Integer.toString(other),
                Integer.toString(report));
        assertThat(unlinked("HRMDocumentToProvider", "id", recipient)).isFalse();
        assertThat(blocked("HRMDocumentToProvider", "id", recipient, other)).isTrue();
        assertThat(blocked("HRMDocumentToProvider", "id", recipient, selected)).isFalse();
    }
}
