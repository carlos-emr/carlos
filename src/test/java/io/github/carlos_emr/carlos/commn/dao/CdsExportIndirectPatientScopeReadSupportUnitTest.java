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
                        "create table HRMDocument(id int primary key)",
                        "create table HRMDocumentToDemographic(id int primary key,hrmDocumentId"
                            + " varchar(20),demographicNo varchar(20))",
                        "create table HRMDocumentComment(id int primary key,hrmDocumentId int)",
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
        try (var s = db.prepareStatement(sql.replace(":patient", "?"))) {
            s.setInt(1, patient);
            try (var rows = s.executeQuery()) {
                return rows.next();
            }
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
        execute("insert into HRMDocumentToDemographic values(11,'00010','00002')");
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
        execute("delete from patientLabRouting where lab_type='HL7'");
        assertThat(blocked("providerLabRouting", 1)).isTrue();
    }

    @Test
    void
            shouldValidateEveryReviewedJoinAgainstBothPinnedPhysicalProfiles_withoutChangingItsClinicalDisposition() {
        var catalogue = CdsExportSourceCatalogue.current();
        for (var candidate : catalogue.profiles())
            for (String table : CdsExportIndirectPatientScopeReadSupport.tables()) {
                assertThat(catalogue.tables().get(table))
                        .isEqualTo(CdsExportSourceCatalogue.Disposition.REQUIRE_EMPTY);
                assertThat(CdsExportIndirectPatientScopeReadSupport.predicate(table, candidate))
                        .contains(":patient", "not exists", "ownedPatient");
            }
        assertThatThrownBy(
                        () ->
                                CdsExportIndirectPatientScopeReadSupport.predicate(
                                        "unreviewed_table", profile))
                .hasMessage("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
    }
}
