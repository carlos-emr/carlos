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
import java.sql.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportIndirectPatientScopeReadSupportUnitTest {
    private Connection db;
    private CdsExportSourceCatalogue.Profile profile;

    @BeforeEach
    void setup() throws Exception {
        profile =
                CdsExportSourceCatalogue.current().profiles().stream()
                        .filter(p -> p.name().equals("on"))
                        .findFirst()
                        .orElseThrow();
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:indirect_" + UUID.randomUUID() + ";MODE=MySQL");
        db = source.getConnection();
        for (String sql :
                List.of(
                        "create table demographic(demographic_no int primary key)",
                        "create table CdsClientForm(id int primary key,clientId int)",
                        "create table CdsClientFormData(id int primary key,cdsClientFormId int)",
                        "create table document(document_no int primary key)",
                        "create table ctl_document(document_no int,module varchar(30),module_id"
                                + " int)",
                        "create table DocumentExtraReviewer(id int primary key,documentNo int)",
                        "create table document_review(id int primary key,document_no int)",
                        "create table HRMDocument(id int primary key)",
                        "create table HRMDocumentToDemographic(id int primary key,hrmDocumentId"
                            + " varchar(20),demographicNo varchar(20),cds_patient_key decimal(20,0)"
                            + " AS (CAST(demographicNo AS DECIMAL(20,0))))",
                        "create table HRMDocumentComment(id int primary key,hrmDocumentId int)",
                        "create table HRMDocumentToProvider(id int primary key,hrmDocumentId"
                                + " varchar(20))",
                        "create table providerLabRouting(id int primary key,lab_no int,lab_type"
                                + " char(3))",
                        "create table patientLabRouting(id int primary key,lab_no int,lab_type"
                                + " char(3),demographic_no int)",
                        "insert into demographic values(1),(2)")) execute(sql);
    }

    @AfterEach
    void close() throws Exception {
        if (db != null) db.close();
    }

    private void execute(String sql) throws Exception {
        try (var s = db.createStatement()) {
            s.execute(sql);
        }
    }

    private boolean blocked(String table, int patient) throws Exception {
        String sql =
                "select 1 from `"
                        + table
                        + "` d where "
                        + CdsExportIndirectPatientScopeReadSupport.predicate(table, profile);
        // A table with several reviewed chains names the patient once per chain.
        int occurrences = sql.split(":patient", -1).length - 1;
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            for (int i = 1; i <= occurrences; i++) s.setInt(i, patient);
            try (var rows = s.executeQuery()) {
                return rows.next();
            }
        }
    }

    private long unlinked(String table) throws Exception {
        return count(table, CdsExportIndirectPatientScopeReadSupport.unlinked(table, profile));
    }

    private long unattributable(String table) throws Exception {
        return count(
                table, CdsExportIndirectPatientScopeReadSupport.unattributable(table, profile));
    }

    private long count(String table, String predicate) throws Exception {
        try (var s = db.createStatement();
                var rows =
                        s.executeQuery(
                                "select count(*) from `" + table + "` d where " + predicate)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    @Test
    void shouldIgnoreKnownOtherPatientFormChildren_andBlockSelectedOrOrphanedChildren()
            throws Exception {
        execute("insert into CdsClientForm values(10,2)");
        execute("insert into CdsClientFormData values(11,10)");
        assertThat(blocked("CdsClientFormData", 1)).isFalse();
        assertThat(blocked("CdsClientFormData", 2)).isTrue();
        execute("update CdsClientForm set clientId=999 where id=10");
        assertThat(blocked("CdsClientFormData", 1)).isTrue();
        execute("delete from CdsClientForm");
        assertThat(blocked("CdsClientFormData", 1)).isTrue();
    }

    @Test
    void shouldScopeNoteLinksThroughTheirNote_andCaptureOnlyTheSelectedChartsLinks()
            throws Exception {
        execute("create table casemgmt_note(note_id int primary key,demographic_no int)");
        execute(
                "create table casemgmt_note_link(id int primary key,table_name int,table_id int,"
                        + "note_id int,other_id varchar(25))");
        execute("insert into casemgmt_note values(10,1),(11,2)");
        execute("insert into casemgmt_note_link values(20,5,700,10,null),(21,12,82,11,null)");
        // Mapped now: each chart captures its own notes' links and nothing else.
        assertThat(count("casemgmt_note_link", owned("casemgmt_note_link", 1))).isEqualTo(1);
        assertThat(count("casemgmt_note_link", owned("casemgmt_note_link", 2))).isEqualTo(1);
        assertThat(unattributable("casemgmt_note_link")).isZero();
        // A link whose note is missing, or whose note names no patient, still holds every chart.
        execute("insert into casemgmt_note_link values(22,5,701,99,null)");
        assertThat(unattributable("casemgmt_note_link")).isEqualTo(1);
        execute("delete from casemgmt_note_link where id=22");
        execute("update casemgmt_note set demographic_no=999 where note_id=11");
        assertThat(unattributable("casemgmt_note_link")).isEqualTo(1);
        assertThat(blocked("casemgmt_note_link", 1)).isTrue();
    }

    private String owned(String table, int patient) {
        return CdsExportIndirectPatientScopeReadSupport.owned(table, profile)
                .replace(":patient", Integer.toString(patient));
    }

    @Test
    void shouldResolveDocumentReviewersThroughTheirExactPatientLink_andRefuseUnattributedModules()
            throws Exception {
        execute("insert into document values(10)");
        execute("insert into ctl_document values(10,'demographic',2)");
        execute("insert into DocumentExtraReviewer values(11,10)");
        assertThat(blocked("DocumentExtraReviewer", 1)).isFalse();
        assertThat(blocked("DocumentExtraReviewer", 2)).isTrue();
        execute("update ctl_document set module='provider'");
        assertThat(blocked("DocumentExtraReviewer", 1)).isTrue();
        execute("update ctl_document set module='demographic'");
        execute("delete from document");
        assertThat(blocked("DocumentExtraReviewer", 1)).isTrue();
    }

    @Test
    void shouldIncludeNumericLegacyHrmAliases_andRefuseMissingPatientOwners() throws Exception {
        execute("insert into HRMDocument values(10)");
        execute(
                "insert into HRMDocumentToDemographic(id,hrmDocumentId,demographicNo)"
                    + " values(11,'00010','00002')");
        execute("insert into HRMDocumentComment values(12,10)");
        assertThat(blocked("HRMDocumentComment", 1)).isFalse();
        assertThat(blocked("HRMDocumentComment", 2)).isTrue();
        execute("update HRMDocumentToDemographic set demographicNo='999'");
        assertThat(blocked("HRMDocumentComment", 1)).isTrue();
    }

    @Test
    void shouldKeepLabRoutingTypesDistinct_andNeverBorrowAnUnrelatedNumericLabId()
            throws Exception {
        execute("insert into providerLabRouting values(10,91,'HL7')");
        execute("insert into patientLabRouting values(11,91,'MDS',1),(12,91,'HL7',2)");
        assertThat(blocked("providerLabRouting", 1)).isFalse();
        assertThat(blocked("providerLabRouting", 2)).isTrue();
        assertThat(unlinked("providerLabRouting")).isZero();
        // MDS lab 91 is another report; the HL7 lab 91 is now an unmatched inbox lab.
        execute("delete from patientLabRouting where lab_type='HL7'");
        assertThat(blocked("providerLabRouting", 1)).isFalse();
        assertThat(blocked("providerLabRouting", 2)).isFalse();
        assertThat(unlinked("providerLabRouting")).isEqualTo(1);
        assertThat(unattributable("providerLabRouting")).isZero();
    }

    @Test
    void shouldHoldNoChart_forAnInboxLabMatchedToNoPatient() throws Exception {
        execute("insert into providerLabRouting values(10,91,'HL7'),(11,92,'HL7')");
        // Lab 91 was never matched; lab 92 was unlinked from a chart (PatientLabRoutingDao.UNMATCHED).
        execute("insert into patientLabRouting values(12,92,'HL7',0)");
        for (int patient : List.of(1, 2)) assertThat(blocked("providerLabRouting", patient)).isFalse();
        assertThat(unlinked("providerLabRouting")).isEqualTo(2);
        assertThat(unattributable("providerLabRouting")).isZero();
        execute("update patientLabRouting set demographic_no=2 where id=12");
        assertThat(blocked("providerLabRouting", 2)).isTrue();
        assertThat(blocked("providerLabRouting", 1)).isFalse();
        assertThat(unlinked("providerLabRouting")).isEqualTo(1);
    }

    @Test
    void shouldKeepHoldingEveryChart_whenALabRouteIsMalformed() throws Exception {
        execute("insert into providerLabRouting values(10,91,'HL7'),(11,93,null)");
        // A link to a chart that does not exist, and a route with no type, could be anyone's.
        execute("insert into patientLabRouting values(12,91,'HL7',999)");
        for (int patient : List.of(1, 2)) assertThat(blocked("providerLabRouting", patient)).isTrue();
        assertThat(unlinked("providerLabRouting")).isZero();
        assertThat(unattributable("providerLabRouting")).isEqualTo(2);
    }

    @Test
    void shouldScopeDocumentRoutes_throughTheDocumentChartLink() throws Exception {
        execute("insert into document values(10),(20)");
        // Document 10 is an incoming document not filed yet; document 20 is filed to patient 2.
        execute("insert into ctl_document values(10,'demographic',-1),(20,'demographic',2)");
        execute("insert into providerLabRouting values(11,10,'DOC'),(21,20,'DOC'),(31,30,'DOC')");
        // A stale patientLabRouting DOC row does not move the document to another chart.
        execute("insert into patientLabRouting values(22,20,'DOC',1)");
        assertThat(blocked("providerLabRouting", 1)).isTrue();
        execute("delete from providerLabRouting where id=31");
        assertThat(blocked("providerLabRouting", 1)).isFalse();
        assertThat(blocked("providerLabRouting", 2)).isTrue();
        assertThat(unlinked("providerLabRouting")).isEqualTo(1);
        assertThat(unattributable("providerLabRouting")).isZero();
    }

    @Test
    void shouldHoldNoChart_forReviewersOfAnIncomingDocument() throws Exception {
        execute("insert into document values(10)");
        execute("insert into ctl_document values(10,'demographic',-1)");
        execute("insert into DocumentExtraReviewer values(11,10)");
        execute("insert into document_review values(12,10)");
        for (String table : List.of("DocumentExtraReviewer", "document_review")) {
            assertThat(blocked(table, 1)).isFalse();
            assertThat(unlinked(table)).isEqualTo(1);
        }
        // Another module is not the incoming placeholder: it stays unattributable.
        execute("update ctl_document set module='provider'");
        assertThat(blocked("document_review", 1)).isTrue();
        assertThat(unlinked("document_review")).isZero();
        assertThat(unattributable("document_review")).isEqualTo(1);
    }

    @Test
    void shouldHoldNoChart_forAnHrmReportMatchedToNoPatient() throws Exception {
        execute("insert into HRMDocument values(10)");
        execute("insert into HRMDocumentComment values(12,10),(13,77)");
        execute("insert into HRMDocumentToProvider values(14,'10')");
        assertThat(blocked("HRMDocument", 1)).isFalse();
        assertThat(blocked("HRMDocumentToProvider", 1)).isFalse();
        assertThat(unlinked("HRMDocument")).isEqualTo(1);
        assertThat(unlinked("HRMDocumentToProvider")).isEqualTo(1);
        // A comment of a report that does not exist cannot be called unlinked.
        assertThat(blocked("HRMDocumentComment", 1)).isTrue();
        assertThat(unlinked("HRMDocumentComment")).isEqualTo(1);
        execute("delete from HRMDocumentComment where id=13");
        assertThat(blocked("HRMDocumentComment", 1)).isFalse();
        execute(
                "insert into HRMDocumentToDemographic(id,hrmDocumentId,demographicNo)"
                        + " values(15,'10','2')");
        assertThat(blocked("HRMDocumentToProvider", 2)).isTrue();
        assertThat(blocked("HRMDocumentToProvider", 1)).isFalse();
        assertThat(unlinked("HRMDocument")).isZero();
    }

    private long owned(String table, int patient) throws Exception {
        return owned(CdsExportIndirectPatientScopeReadSupport.owned(table, profile), table, patient);
    }

    private long owned(String predicate, String table, int patient) throws Exception {
        String sql = "select count(*) from `" + table + "` d where " + predicate;
        int occurrences = sql.split(":patient", -1).length - 1;
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            for (int i = 1; i <= occurrences; i++) s.setInt(i, patient);
            try (var rows = s.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private void legacyLaboratoryTables() throws Exception {
        for (String sql :
                List.of(
                        "create table mdsMSH(segmentID int primary key,demographic_no int)",
                        "create table mdsOBX(segmentID int,observationValue varchar(255))",
                        "create table labPatientPhysicianInfo(id int primary key,labReportInfo_id int)",
                        "create table labTestResults(id int primary key,labPatientPhysicianInfo_id int)",
                        "create table labReportInformation(id int primary key)",
                        "create table labRequestReportLink(id int primary key,report_table"
                                + " varchar(60),report_id int)")) execute(sql);
    }

    @Test
    void shouldScopeLegacyLabSegments_throughTheRouteOfExactlyTheirTypeOnly() throws Exception {
        legacyLaboratoryTables();
        execute("insert into mdsMSH values(91,2),(92,1)");
        execute("insert into mdsOBX values(91,'5.1'),(91,'5.1'),(92,'7'),(93,'orphan')");
        // MDS lab 91 is routed to patient 1; its legacy demographic_no copy (2) decides nothing.
        // HL7 lab 92 is another report with the same number, and a type spelled 'mds' is no MDS.
        execute("insert into patientLabRouting values(11,91,'MDS',1),(12,92,'HL7',1),(13,92,'mds',1)");
        assertThat(owned("mdsMSH", 1)).isEqualTo(1);
        assertThat(owned("mdsMSH", 2)).isZero();
        // Both identical keyless segments belong to the report; an orphan segment to no chart.
        assertThat(owned("mdsOBX", 1)).isEqualTo(2);
        assertThat(owned("mdsOBX", 2)).isZero();
        // A segment whose header row is missing still follows its lab number's route.
        execute("delete from mdsMSH where segmentID=91");
        assertThat(owned("mdsOBX", 1)).isEqualTo(2);
    }

    @Test
    void shouldScopeCmlReportsLinesHeadersAndRequisitionLinks_throughTheReportRoute() throws Exception {
        legacyLaboratoryTables();
        execute("insert into labReportInformation values(3),(4)");
        execute("insert into labPatientPhysicianInfo values(80,3),(81,3),(82,4)");
        execute("insert into labTestResults values(800,80),(810,81),(820,82)");
        execute("insert into patientLabRouting values(11,80,'CML',1),(12,81,'CML',2),(13,82,'CML',0),(14,77,'HL7',1)");
        assertThat(owned("labPatientPhysicianInfo", 1)).isEqualTo(1);
        assertThat(owned("labTestResults", 1)).isEqualTo(1);
        assertThat(owned("labTestResults", 2)).isEqualTo(1);
        // One upload header serves the reports of patients 1 and 2; an unmatched report's header
        // belongs to no chart.
        assertThat(owned("labReportInformation", 1)).isEqualTo(1);
        assertThat(owned("labReportInformation", 2)).isEqualTo(1);
        execute(
                "insert into labRequestReportLink values(1,'labPatientPhysicianInfo',80),"
                        + "(2,'hl7TextMessage',77),(3,'hl7TextMessage',80),(4,'unknownTable',80)");
        // The CML link and the HL7 link of patient 1; the HL7 lab 80 is not routed and another
        // table names no chart. (A spelling differing only by case is covered on MariaDB, whose
        // CAST AS BINARY compares every byte; H2's does not.)
        assertThat(owned("labRequestReportLink", 1)).isEqualTo(2);
        assertThat(owned("labRequestReportLink", 2)).isZero();
    }

    @Test
    void shouldScopePathNetSegments_throughPatientAndOrderRowsToTheMessageRoute() throws Exception {
        var bc =
                CdsExportSourceCatalogue.current().profiles().stream()
                        .filter(p -> p.name().equals("bc"))
                        .findFirst()
                        .orElseThrow();
        for (String sql :
                List.of(
                        "create table hl7_message(message_id int primary key)",
                        "create table hl7_pid(pid_id int primary key,message_id int)",
                        "create table hl7_link(pid_id int primary key,demographic_no int)",
                        "create table hl7_obr(obr_id int primary key,pid_id int)",
                        "create table hl7_obx(obx_id int primary key,obr_id int)",
                        "insert into hl7_message values(5),(6)",
                        "insert into hl7_pid values(50,5),(60,6)",
                        // The legacy link copy names patient 2; the route names patient 1.
                        "insert into hl7_link values(50,2),(60,2)",
                        "insert into hl7_obr values(500,50),(600,60)",
                        "insert into hl7_obx values(5000,500),(5001,500),(6000,600),(9000,999)",
                        "insert into patientLabRouting values(11,5,'BCP',1),(12,6,'BCP',0)"))
            execute(sql);
        String obx = CdsExportIndirectPatientScopeReadSupport.owned("hl7_obx", bc);
        assertThat(owned(obx, "hl7_obx", 1)).isEqualTo(2);
        assertThat(owned(obx, "hl7_obx", 2)).isZero();
        assertThat(owned(CdsExportIndirectPatientScopeReadSupport.owned("hl7_link", bc), "hl7_link", 1))
                .isEqualTo(1);
        assertThat(owned(CdsExportIndirectPatientScopeReadSupport.owned("hl7_link", bc), "hl7_link", 2))
                .isZero();
        assertThat(owned(CdsExportIndirectPatientScopeReadSupport.owned("hl7_message", bc), "hl7_message", 1))
                .isEqualTo(1);
    }

    @Test
    void shouldOfferTheUnlinkedRule_onlyForTheReviewedInboxTables() {
        assertThat(CdsExportIndirectPatientScopeReadSupport.unlinkedTables())
                .containsExactlyElementsOf(
                        io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedSourceDto.tables());
        assertThatThrownBy(
                        () ->
                                CdsExportIndirectPatientScopeReadSupport.unlinked(
                                        "CdsClientFormData", profile))
                .hasMessage("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
    }

    @Test
    void
            shouldValidateEveryReviewedJoinAgainstBothPinnedPhysicalProfiles_withoutChangingItsClinicalDisposition() {
        var catalogue = CdsExportSourceCatalogue.current();
        for (var candidate : catalogue.profiles())
            for (String table : CdsExportIndirectPatientScopeReadSupport.tables()) {
                // Data-summary and listed children keep a parent scope too (decisions 6 and 20);
                // a mapped inbox table is captured through its chart, and only its
                // unattributable rows are checked, which hold every chart.
                assertThat(catalogue.tables().get(table))
                        .isIn(
                                CdsExportSourceCatalogue.Disposition.REQUIRE_EMPTY,
                                CdsExportSourceCatalogue.Disposition.MAPPED_SOURCE,
                                CdsExportSourceCatalogue.Disposition.DOCUMENT,
                                CdsExportSourceCatalogue.Disposition.LISTED);
                // The BC antenatal record 2020 pages exist only in the BC profile.
                if (!candidate.tables().containsKey(table)) continue;
                // An orphan or unattributable child still blocks every selection.
                assertThat(CdsExportIndirectPatientScopeReadSupport.predicate(table, candidate))
                        .contains(":patient", "not exists", "ownedPatient");
                assertThat(
                                CdsExportIndirectPatientScopeReadSupport.unattributable(
                                        table, candidate))
                        .contains("not exists", "ownedPatient")
                        .doesNotContain(":patient");
                assertThat(CdsExportIndirectPatientScopeReadSupport.owned(table, candidate))
                        .contains(":patient")
                        .doesNotContain("not exists", "ownedPatient");
            }
        assertThatThrownBy(
                        () ->
                                CdsExportIndirectPatientScopeReadSupport.predicate(
                                        "unreviewed_table", profile))
                .hasMessage("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
    }
}
