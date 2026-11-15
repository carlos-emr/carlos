/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAOImpl;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAOImpl;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsNativeIssuesDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** Actual MariaDB row bytes and ORM insertion across export/import/re-export. All rows roll back. */
@Tag("integration")
@Tag("cds-import")
class CdsNativeIssuesMariaDbIntegrationTest {
    @TempDir Path temporary;

    @Test
    void shouldPreserveEveryStoredMarkerColumn_throughAnActualMariaDbRoundTrip() throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(selector != null && !selector.isBlank(),
                "Explicit isolated MariaDB tier requires protected cds.test.properties");
        var jdbc = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) { jdbc.load(input); }
        assertThat(jdbc.getProperty("jdbcUrl"))
                .matches("jdbc:(mysql|mariadb)://[^/]+/(carlos_cds_test|cds_full_test|cds_ledger_test)(\\?.*)?");
        try (var dataSource = new BasicDataSource()) {
            dataSource.setUrl(jdbc.getProperty("jdbcUrl"));
            dataSource.setUsername(jdbc.getProperty("jdbcUser"));
            dataSource.setPassword(jdbc.getProperty("jdbcPassword"));
            dataSource.setMaxTotal(2);
            var config = new Configuration().addAnnotatedClass(Issue.class)
                    .addAnnotatedClass(CaseManagementIssue.class).addAnnotatedClass(CaseManagementNote.class)
                    .addAnnotatedClass(CaseManagementNoteExt.class).addAnnotatedClass(Provider.class);
            config.getProperties().put("hibernate.connection.datasource", dataSource);
            config.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
            config.setProperty("hibernate.hbm2ddl.auto", "none");
            try (var factory = config.buildSessionFactory(); var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                try {
                    var catalogue = new IssueDAOImpl();
                    var patientIssues = new CaseManagementIssueDAOImpl();
                    ReflectionTestUtils.setField(catalogue, "entityManager", session);
                    ReflectionTestUtils.setField(patientIssues, "entityManager", session);
                    var definition = new Issue();
                    definition.setCode("CDSM" + UUID.randomUUID().toString().substring(0, 8));
                    definition.setDescription("Exact  marker\t");
                    definition.setRole("all");
                    definition.setType("system");
                    definition.setPriority(null);
                    definition.setSortOrderId(0);
                    catalogue.saveIssue(definition);
                    var source = new CaseManagementIssue();
                    source.setDemographic_no(27);
                    source.setIssue_id(definition.getId());
                    source.setType("general  ");
                    source.setAcute(true);
                    source.setCertain(false);
                    source.setMajor(true);
                    source.setResolved(false);
                    source.setProgram_id(null);
                    source.setUpdate_date(Timestamp.valueOf("2026-09-01 12:34:56"));
                    patientIssues.insertCdsMarker(source);
                    session.flush();
                    session.clear();
                    var before = session.doReturningWork(connection -> marker(connection, source.getId()));
                    var entry = session.doReturningWork(connection -> definition(connection, definition.getId()));
                    var document = CdsExportNativeHistoryFixture.markerDocument(before, entry);
                    var records = new ArrayList<CdsRecordDto>();
                    new CdsXmlParser().parse(Files.write(temporary.resolve("patient.xml"), document),
                            temporary.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
                    var record = records.stream().filter(r -> r.section().equals("NewCategory")).findFirst().orElseThrow();
                    var prepared = new AtomicReference<CdsNativeIssuesDto>();
                    var preparation = mock(CdsNativePreparationService.class);
                    doAnswer(call -> { prepared.set(call.getArgument(5)); return null; }).when(preparation)
                            .store(anyString(), anyLong(), anyString(), anyString(), any(), any());
                    when(preparation.load(anyString(), anyLong(), anyString(), anyString(), any(), eq(CdsNativeIssuesDto.class)))
                            .thenAnswer(call -> prepared.get());
                    var writer = new CdsNativeIssuesImportService(new CdsFieldParser(), preparation, patientIssues, catalogue);
                    var command = new CdsImportCommand(temporary, "marker-round-trip", "999998", null,
                            null, "synthetic-fixture", "omd-5.2", false, false, false, 0, null, false);
                    writer.prepare("run", 1, "unit", record, command, null, "patient.xml");
                    var refs = writer.write("run", 1, "unit", 227, record, command);
                    session.flush();
                    session.clear();
                    var after = session.doReturningWork(connection -> marker(connection, Long.parseLong(refs.get("casemgmt_issue"))));
                    var expected = new LinkedHashMap<>(before);
                    expected.put("id", after.get("id"));
                    expected.put("demographic_no", "227");
                    assertThat(after).containsExactlyInAnyOrderEntriesOf(expected);
                    assertThat(after.get("id")).isNotEqualTo(before.get("id"));
                    assertThat(CdsNativeIssuesReader.calendar(after.get("update_date")))
                            .isEqualTo(java.time.LocalDateTime.of(2026, 9, 1, 12, 34, 56));
                    assertThat(CdsExportNativeHistoryFixture.markerDocument(after, entry)).isEqualTo(document);
                } finally { transaction.rollback(); }
            }
        }
    }

    private static Map<String, String> marker(Connection connection, long id) throws java.sql.SQLException {
        try (var query = connection.prepareStatement(
                "SELECT id,demographic_no,issue_id,acute,certain,major,resolved,type,program_id,update_date FROM casemgmt_issue WHERE id=?")) {
            query.setLong(1, id);
            return row(query);
        }
    }

    private static Map<String, String> definition(Connection connection, long id) throws java.sql.SQLException {
        try (var query = connection.prepareStatement(
                "SELECT issue_id,code,description,priority,role,sortOrderId,type,update_date FROM issue WHERE issue_id=?")) {
            query.setLong(1, id);
            return row(query);
        }
    }

    private static Map<String, String> row(java.sql.PreparedStatement query) throws java.sql.SQLException {
        try (var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            var result = new LinkedHashMap<String, String>();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++)
                result.put(rows.getMetaData().getColumnLabel(column), rows.getString(column));
            assertThat(rows.next()).isFalse();
            return result;
        }
    }
}
