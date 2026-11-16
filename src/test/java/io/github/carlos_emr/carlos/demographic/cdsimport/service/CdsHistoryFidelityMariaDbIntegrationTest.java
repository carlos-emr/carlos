/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** Actual native note columns through the exporter, XML parser, importer and database. */
@Tag("integration")
@Tag("cds-import")
class CdsHistoryFidelityMariaDbIntegrationTest {
    @TempDir Path directory;

    @Test
    void shouldStoreTheSameEncounterType_afterCarlosExportAndImport() throws Exception {
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
                    var notes = new CaseManagementNoteDAOImpl();
                    var extensions = new CaseManagementNoteExtDAOImpl();
                    ReflectionTestUtils.setField(notes, "entityManager", session);
                    ReflectionTestUtils.setField(extensions, "entityManager", session);
                    var preparation = mock(CdsNativePreparationService.class);
                    var prepared = new AtomicReference<CdsHistoryNativeDto>();
                    doAnswer(call -> { prepared.set(call.getArgument(5)); return null; }).when(preparation)
                            .store(anyString(), anyLong(), anyString(), anyString(), any(), any());
                    when(preparation.load(anyString(), anyLong(), anyString(), anyString(), any(), eq(CdsHistoryNativeDto.class)))
                            .thenAnswer(call -> prepared.get());
                    var providers = mock(CdsProviderResolver.class);
                    when(providers.resolve(any(), any(), any(), any(), anyBoolean())).thenReturn(
                            new CdsProviderMatchDto(null, CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
                    var fields = new CdsFieldParser();
                    var configuration = new CdsImportConfigurationService(new Properties());
                    var writer = new CdsHistoryPersister(fields, new CdsClinicalNormalizationService(fields),
                            configuration, providers, preparation, notes,
                            mock(CaseManagementIssueDAO.class), mock(IssueDAO.class), extensions);
                    var command = new CdsImportCommand(directory, "history-round-trip", "999998", null,
                            null, "synthetic-fixture", "omd-5.2", false, false, false, 0, null, false);
                    for (String encounter : List.of("telephone encounter with client", "Custom  visit\t", "🩺".repeat(100))) {
                        byte[] document = CdsExportNativeHistoryFixture.encounterDocument(encounter);
                        Path root = Files.createTempDirectory(directory, "encounter-");
                        var records = new ArrayList<CdsRecordDto>();
                        new CdsXmlParser().parse(Files.write(root.resolve("patient.xml"), document),
                                root.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
                        var record = records.stream().filter(r -> r.section().equals("ClinicalNotes")).findFirst().orElseThrow();
                        writer.prepare("run", 1, "unit", record, command);
                        var receipt = writer.write("run", 1, "unit", 227, record, command, ignored -> {});
                        session.flush();
                        session.clear();
                        assertThat(session.createNativeQuery("select encounter_type from casemgmt_note where note_id=?1", String.class)
                                .setParameter(1, Long.valueOf(receipt.get("casemgmt_note"))).getSingleResult()).isEqualTo(encounter);
                    }
                } finally { transaction.rollback(); }
            }
        }
    }
}
