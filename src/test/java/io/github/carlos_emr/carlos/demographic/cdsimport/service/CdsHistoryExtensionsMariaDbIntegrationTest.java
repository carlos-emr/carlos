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
import io.github.carlos_emr.carlos.commn.model.CdsImportDependency;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** Real ledger dependencies, parent/child receipts and native extension storage. All writes roll back. */
@Tag("integration")
@Tag("cds-import")
class CdsHistoryExtensionsMariaDbIntegrationTest {
    @TempDir Path directory;

    @Test
    void shouldRestoreEveryDuplicateExtensionRow_withIndependentVerifiedReceipts() throws Exception {
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
                    .addAnnotatedClass(CaseManagementNoteExt.class).addAnnotatedClass(Provider.class)
                    .addAnnotatedClass(CdsImportUnit.class).addAnnotatedClass(CdsImportDependency.class);
            config.getProperties().put("hibernate.connection.datasource", dataSource);
            config.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
            config.setProperty("hibernate.hbm2ddl.auto", "none");
            try (var factory = config.buildSessionFactory(); var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                try { roundTrip(session); }
                finally { transaction.rollback(); }
            }
        }
    }

    private void roundTrip(Session session) throws Exception {
        var catalogue = new IssueDAOImpl();
        var patientIssues = new CaseManagementIssueDAOImpl();
        var notes = new CaseManagementNoteDAOImpl();
        var extensions = new CaseManagementNoteExtDAOImpl();
        for (Object dao : List.of(catalogue, patientIssues, notes, extensions))
            ReflectionTestUtils.setField(dao, "entityManager", session);
        if (catalogue.findCandidatesByCode("FamHistory").isEmpty()) {
            var issue = new Issue();
            issue.setCode("FamHistory"); issue.setDescription("Family history fixture");
            issue.setRole("all"); issue.setType("system");
            catalogue.saveIssue(issue);
        }
        int patient = patient(session);
        var rows = new ArrayList<Map<String, String>>();
        rows.add(row("Start Date", "YYYY", "2001-07-23"));
        rows.add(row("Resolution Date", "YYYY-MM", "2005-03-16"));
        rows.add(row("Procedure Date", null, "2000-01-02"));
        rows.add(row("Age at Onset", "childhood", null));
        rows.add(row("Exposure Details", "Exact  exposure\t", null));
        rows.add(row("Problem Status", "Resolved", null));
        for (int i = 0; i < 300; i++) rows.add(row("Treatment", i % 2 == 0 ? "First" : "Second", null));
        byte[] document = CdsExportNativeHistoryFixture.extensionDocument(CdsCategory.FAMILY_HISTORY, "FamHistory", rows);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(Files.write(directory.resolve("patient.xml"), document),
                directory.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        var record = records.stream().filter(r -> r.section().equals("FamilyHistory")).findFirst().orElseThrow();
        var fields = new CdsFieldParser();
        var children = new ArrayList<CdsRecordDto>();
        new CdsHistoryExtensionChildren(fields).forEach(record, CdsInputLimitsDto.defaults(), children::add);
        assertThat(children).hasSize(rows.size());
        var prepared = new HashMap<String, Object>();
        var preparation = mock(CdsNativePreparationService.class);
        doAnswer(call -> { prepared.put(call.getArgument(2), call.getArgument(5)); return null; }).when(preparation)
                .store(anyString(), anyLong(), anyString(), anyString(), any(), any());
        when(preparation.load(anyString(), anyLong(), anyString(), anyString(), any(), any()))
                .thenAnswer(call -> prepared.get(call.getArgument(2)));
        var configuration = new CdsImportConfigurationService(new Properties());
        var writer = new CdsHistoryPersister(fields, new CdsClinicalNormalizationService(fields), configuration,
                mock(CdsProviderResolver.class), preparation, notes, patientIssues, catalogue, extensions);
        var command = new CdsImportCommand(directory, "cpp-round-trip", "999998", null,
                null, "synthetic-fixture", "omd-5.2", false, false, false, 0, null, false);
        String run = UUID.randomUUID().toString();
        String parentKey = CdsImportKeys.hash(run, "parent");
        String patientKey = CdsImportKeys.hash(run, "patient");
        writer.prepare(run, 1, parentKey, record, command);
        var parentRows = writer.write(run, 1, parentKey, patient, record, command, ignored -> {});
        session.flush();
        var receipts = new CdsTargetReceiptService(CdsNativeBaselineTestKeys.keys());
        ReflectionTestUtils.setField(receipts, "entityManager", session);
        var parentReceipt = receipts.capture(parentRows);
        seedRun(session, run, patientKey, patient);
        var parent = unit(run, patientKey, parentKey, record.section(), record.ordinal(), record.effectiveSha256());
        parent.setState("COMMITTED");
        parent.setTargetReferences(parentReceipt.targetReferences());
        parent.setTargetDigest(parentReceipt.targetDigest());
        parent.setNativeBaseline(parentReceipt.nativeBaseline());
        session.persist(parent);
        var childReceipts = new ArrayList<CdsImportReceiptDto>();
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        for (var child : children) {
            String key = CdsImportKeys.hash(run, child.sourceXPath());
            var childUnit = unit(run, patientKey, key, record.section() + "_EXTENSION", child.ordinal(), child.effectiveSha256());
            session.persist(childUnit);
            var dependency = new CdsImportDependency();
            dependency.setId(CdsImportKeys.hash(run, key, parentKey));
            dependency.setRunId(run); dependency.setUnitKey(key); dependency.setDependsOnUnitKey(parentKey);
            session.persist(dependency);
            session.flush();
            writer.validateExtension(child, command);
            writer.prepareExtension(run, 1, key, child, command);
            var written = writer.writeExtension(run, 1, key, patient, child, command, session, receipts, notices::add);
            assertThat(written).hasSize(1).containsKey("casemgmt_note_ext");
            session.flush();
            childReceipts.add(receipts.capture(written));
        }
        assertThat(notices).isEmpty();
        assertThat(receipts.verify(parentReceipt)).isTrue();
        for (var receipt : childReceipts) assertThat(receipts.verify(receipt)).isTrue();
        session.clear();
        var actual = session.doReturningWork(connection -> {
            var result = new ArrayList<Map<String, String>>();
            try (var query = connection.prepareStatement("select key_val,`value`,date_value from casemgmt_note_ext where note_id=? order by id")) {
                query.setLong(1, Long.parseLong(parentRows.get("casemgmt_note")));
                try (var found = query.executeQuery()) {
                    while (found.next()) if (!found.getString(1).startsWith("cdsDate"))
                        result.add(row(found.getString(1), found.getString(2), found.getString(3)));
                }
            }
            return result;
        });
        assertThat(actual).containsExactlyElementsOf(rows);
        assertThat(CdsExportNativeHistoryFixture.extensionDocument(CdsCategory.FAMILY_HISTORY, "FamHistory", actual))
                .isEqualTo(document);
    }

    private static Map<String, String> row(String key, String value, String date) {
        var row = new LinkedHashMap<String, String>();
        row.put("key_val", key); row.put("value", value); row.put("date_value", date);
        return row;
    }

    private static CdsImportUnit unit(String run, String patient, String key, String category, long ordinal, String digest) {
        var unit = new CdsImportUnit();
        unit.setId(CdsImportKeys.hash(run, key)); unit.setRunId(run); unit.setPatientKey(patient);
        unit.setUnitKey(key); unit.setLineageKey(CdsImportKeys.hash(run, "lineage"));
        unit.setCategory(category); unit.setOrdinal(ordinal); unit.setSourceDigest(digest);
        unit.setSpoolLocation("/synthetic-fixture/unit"); unit.setState("EXPECTED");
        return unit;
    }

    private static int patient(Session session) {
        session.createNativeMutationQuery("INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,lastUpdateDate) "
                + "VALUES(?1,'CDS','1971','03','04','F','AC','999998',NOW())")
                .setParameter(1, "FAKE-CDS-CPP-" + UUID.randomUUID()).executeUpdate();
        return ((Number) session.createNativeQuery("SELECT LAST_INSERT_ID()", Long.class).getSingleResult()).intValue();
    }

    private static void seedRun(Session session, String run, String patientKey, int patient) {
        session.createNativeMutationQuery("INSERT INTO cds_import_run (id,submission_key,dry_run,report_state,cleanup_state,"
                + "review_version,discovery_complete,discovered_files,patient_xml_files,source_bytes,operator,provider_no,"
                + "source_namespace,profile,source_digest,options_digest,ruleset_digest,snapshot_location,state,"
                + "expected_patients,expected_units,owner_epoch,cancel_requested,report_generation,created_at,updated_at) "
                + "VALUES (?1,?1,0,'PENDING','PENDING',0,1,1,1,1,'synthetic','999998','fixture','omd-5.2',"
                + "?2,?2,?2,'/synthetic-fixture/job','RUNNING',1,307,1,0,0,NOW(6),NOW(6))")
                .setParameter(1, run).setParameter(2, CdsImportKeys.hash(run)).executeUpdate();
        session.createNativeMutationQuery("INSERT INTO cds_import_patient (id,run_id,patient_key,source_patient_id,copy_key,"
                + "lineage_key,source_digest,target_demographic_no,state,held,expected_units) "
                + "VALUES (?1,?2,?3,'SYNTHETIC-1',?4,?4,?4,?5,'IMPORTING',1,307)")
                .setParameter(1, CdsImportKeys.hash(run, "patient-row")).setParameter(2, run).setParameter(3, patientKey)
                .setParameter(4, CdsImportKeys.hash(run, "lineage")).setParameter(5, patient).executeUpdate();
    }
}
