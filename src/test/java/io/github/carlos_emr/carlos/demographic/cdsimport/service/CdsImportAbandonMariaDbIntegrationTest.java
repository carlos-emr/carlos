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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportCheckpointDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDependencyDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFileDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFileReferenceDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportLineageDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportUnitDao;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportRetainedIndexService;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The census lock-out and its supported way out, on a real MariaDB with the real admission
 * guard, maintenance owner, coordinator lock, receipts and ledger: a run that stopped after
 * writing a chart, left INCOMPLETE and PARTIAL_COMMITTED with its chart held and the maintenance
 * window active, and was then cancelled. Its operator's abandon releases the clinic without
 * changing a clinical row; the exporter keeps refusing the chart, and the run refuses a second
 * abandon or a resume. Runs in its own scratch database on the export test server, copied
 * table by table from the disposable export schema and dropped afterwards, so the maintenance
 * window and advisory locks it takes belong to no other test.
 */
@Tag("integration")
@Tag("mariadb")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsImportAbandonMariaDbIntegrationTest {
    private static final CdsImportActorDto OPERATOR =
            new CdsImportActorDto("unix:synthetic-operator", "999998", "999998", 1, null, null);
    private static final List<String> CLINICAL =
            List.of("demographic", "allergies", "partial_date", "log");
    private static final List<Map<String, Object>> RECORDS = new CopyOnWriteArrayList<>();

    private static String serverUrl;
    private static String scratchUrl;
    private static String scratch;
    private static Properties jdbc;
    private static OscarTrackingBasicDataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static CdsImportLedgerService ledger;
    private static CdsImportAbandonService abandon;
    private static CdsImportMaintenanceService maintenance;
    private static CdsTargetReceiptService receipts;
    private static CdsImportResultService results;
    private static TransactionTemplate transaction;

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private record Chart(int demographicNo, int allergyId, CdsImportReceiptDto receipt) {}

    private record Seeded(String run, String patient, String unit, Chart chart) {}

    @BeforeAll
    static void createScratchDatabase() throws Exception {
        jdbc = new Properties();
        try (InputStream input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            jdbc.load(input);
        }
        serverUrl = jdbc.getProperty("jdbcUrl", "");
        assertThat(serverUrl).matches("jdbc:mariadb://[^/]+/cds_export_test(\\?.*)?");
        assertThat(jdbc.getProperty("ownedDisposableDatabase")).isEqualTo("true");
        byte[] random = new byte[6];
        new SecureRandom().nextBytes(random);
        scratch = "cds_abandon_" + HexFormat.of().formatHex(random);
        try (Connection admin = admin();
                Statement sql = admin.createStatement()) {
            sql.execute(
                    "CREATE DATABASE " + scratch + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            List<String> tables = new ArrayList<>(CLINICAL);
            try (ResultSet rows =
                    sql.executeQuery(
                            "SELECT table_name FROM information_schema.tables WHERE"
                                    + " table_schema=DATABASE() AND table_name LIKE"
                                    + " 'cds\\_import\\_%'")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            assertThat(tables).contains("cds_import_run", "cds_import_admission");
            for (String table : tables) {
                assertThat(table).matches("[a-z_]+");
                sql.execute("CREATE TABLE " + scratch + "." + table + " LIKE " + table);
            }
            sql.execute(
                    "INSERT INTO "
                            + scratch
                            + ".cds_import_admission (id,maintenance_active,maintenance_generation,"
                            + "reserved_bytes) VALUES ('global',0,0,0),"
                            + "('clinical-maintenance',0,0,0),('UPLOAD_BUDGET',0,0,0)");
        }
        scratchUrl =
                serverUrl.replaceFirst("/cds_export_test(\\?.*)?$", "/" + scratch)
                        + "?useUnicode=true&characterEncoding=UTF-8"
                        + "&connectionCollation=utf8mb4_unicode_ci";
        dataSource = new OscarTrackingBasicDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        dataSource.setUrl(scratchUrl);
        dataSource.setUsername(jdbc.getProperty("jdbcUser"));
        dataSource.setPassword(jdbc.getProperty("jdbcPassword"));
        dataSource.setMaxTotal(8);
        dataSource.setDefaultAutoCommit(true);
        new CdsJdbcAdmissionInitializer(dataSource, true, "validate").initialize();
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.maintenanceMode", "true");
        policy.setProperty("cds.import.clinicalZone", java.time.ZoneId.systemDefault().getId());
        var configuration =
                new CdsImportConfigurationService(policy) {
                    @Override
                    public io.github.carlos_emr.carlos.demographic.cds.security
                                    .CdsNativeBaselineKeys
                            nativeBaselineKeys() {
                        return CdsNativeBaselineTestKeys.keys();
                    }
                };
        var evidence = mock(CdsImportFailureEvidenceService.class);
        when(evidence.writeAbandonment(anyString(), anyLong(), anyMap()))
                .thenAnswer(
                        invocation -> {
                            RECORDS.add(invocation.getArgument(2));
                            return "/var/lib/carlos-emr/cds-import-staging/synthetic/diagnostics/"
                                    + "abandonment-"
                                    + invocation.getArgument(1)
                                    + ".json";
                        });
        results = mock(CdsImportResultService.class);
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", OscarTrackingBasicDataSource.class, () -> dataSource);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setDataSource(dataSource);
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    List<String> managed = new ArrayList<>();
                    for (String name :
                            List.of(
                                    "Run",
                                    "Patient",
                                    "Unit",
                                    "Dependency",
                                    "Lineage",
                                    "Checkpoint",
                                    "Field",
                                    "Diagnostic",
                                    "File",
                                    "FileReference",
                                    "Event",
                                    "Admission",
                                    "Identity",
                                    "Review"))
                        managed.add("io.github.carlos_emr.carlos.commn.model.CdsImport" + name);
                    managed.add("io.github.carlos_emr.carlos.commn.model.OscarLog");
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(managed.toArray(String[]::new)));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect",
                                    "hibernate.jdbc.time_zone",
                                    "UTC"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean(
                "persistenceAnnotationBeanPostProcessor",
                org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor.class);
        context.register(
                CdsImportIdentityDao.class,
                CdsImportRunDao.class,
                CdsImportPatientDao.class,
                CdsImportUnitDao.class,
                CdsImportDependencyDao.class,
                CdsImportLineageDao.class,
                CdsImportCheckpointDao.class,
                CdsImportDiagnosticDao.class,
                CdsImportAdmissionDao.class,
                CdsImportFieldDao.class,
                CdsImportFileDao.class,
                CdsImportFileReferenceDao.class,
                CdsImportEventDao.class,
                CdsImportLedgerService.class,
                CdsImportAbandonService.class);
        context.registerBean(CdsImportConfigurationService.class, () -> configuration);
        context.registerBean(
                CdsTargetReceiptService.class,
                () -> new CdsTargetReceiptService(CdsNativeBaselineTestKeys.keys()));
        context.registerBean(
                CdsImportMaintenanceService.class,
                () -> new CdsImportMaintenanceService(dataSource, configuration));
        context.registerBean(
                CdsImportCoordinatorService.class,
                () -> new CdsImportCoordinatorService(dataSource));
        context.registerBean(
                CdsImportTargetSafetyService.class,
                () -> new CdsImportTargetSafetyService(configuration, Set.copyOf(CLINICAL)));
        context.registerBean(CdsImportResultService.class, () -> results);
        context.registerBean(CdsImportFailureEvidenceService.class, () -> evidence);
        context.refresh();
        ledger = context.getBean(CdsImportLedgerService.class);
        abandon = context.getBean(CdsImportAbandonService.class);
        maintenance = context.getBean(CdsImportMaintenanceService.class);
        receipts = context.getBean(CdsTargetReceiptService.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void dropScratchDatabase() throws Exception {
        try {
            if (context != null) context.close();
            if (dataSource != null) dataSource.close();
        } finally {
            if (scratch != null)
                try (Connection admin = admin();
                        Statement sql = admin.createStatement()) {
                    sql.execute("DROP DATABASE IF EXISTS " + scratch);
                }
        }
    }

    @Test
    void shouldReleaseTheCensusLockout_whenItsStoppedRunIsAbandonedByItsOperator()
            throws Exception {
        Chart chart = chart("FAKE-ABANDON-ONE");
        Seeded census = census(chart, OPERATOR.operator());
        activateStaleMaintenance();
        assertThat(maintenance.isClinicalAccessBlocked()).isTrue();
        List<String> demographic = row("demographic", "demographic_no", chart.demographicNo());
        List<String> allergy = row("allergies", "allergyid", chart.allergyId());
        List<String> unit = row("cds_import_unit", "unit_key", census.unit());
        List<String> checkpoint = row("cds_import_checkpoint", "id", census.unit());
        long generation = generation();

        var status =
                abandon.abandon(
                        census.run(), OPERATOR, census.run(), "defect stops every resume");

        assertThat(status.state()).isEqualTo("ABANDONED");
        assertThat(maintenance.isClinicalAccessBlocked()).isFalse();
        assertThat(scalar("SELECT maintenance_active FROM cds_import_admission WHERE"
                        + " id='clinical-maintenance'"))
                .isEqualTo("0");
        assertThat(generation()).isGreaterThan(generation);
        assertThat(scalar("SELECT COUNT(*) FROM cds_import_patient WHERE held=TRUE"))
                .isEqualTo("0");
        assertThat(scalar("SELECT owner_run_id FROM cds_import_admission WHERE id='global'"))
                .isNull();
        // No clinical row, unit or checkpoint changed, byte for byte.
        assertThat(row("demographic", "demographic_no", chart.demographicNo()))
                .isEqualTo(demographic);
        assertThat(row("allergies", "allergyid", chart.allergyId())).isEqualTo(allergy);
        assertThat(row("cds_import_unit", "unit_key", census.unit())).isEqualTo(unit);
        assertThat(row("cds_import_checkpoint", "id", census.unit())).isEqualTo(checkpoint);
        assertThat(
                        scalar(
                                "SELECT CONCAT(state,'/',CAST(held AS CHAR),'/',"
                                        + "target_demographic_no) FROM cds_import_patient WHERE"
                                        + " run_id=?",
                                census.run()))
                .isEqualTo("ABANDONED/0/" + chart.demographicNo());
        var summary = ledger.summary(census.run());
        assertThat(summary.get("maintenanceReleaseState")).isEqualTo("RELEASED");
        assertThat(summary.get("maintenanceReleasePending")).isEqualTo(false);
        assertThat(summary.get("cleanComplete")).isEqualTo(false);
        assertThat(summary.get("clinicalComplete")).isEqualTo(false);
        assertThat(events(census.run()))
                .contains("ABANDON_OWNER_ACQUIRED", "PATIENT_ABANDONED", "RUN_ABANDONED");
        assertThat(
                        scalar(
                                "SELECT safe_summary FROM cds_import_diagnostic WHERE run_id=?"
                                        + " AND code='PATIENT_ABANDONED'",
                                census.run()))
                .endsWith("receipts: VERIFIED");
        assertThat(
                        scalar(
                                "SELECT COUNT(*) FROM log WHERE demographic_no=? AND"
                                        + " action='import abandoned' AND content='demographic'",
                                chart.demographicNo()))
                .isEqualTo("1");
        // The stated reason lives only in the protected record, never in a ledger column.
        assertThat(RECORDS)
                .anySatisfy(
                        record -> {
                            assertThat(record.get("runId")).isEqualTo(census.run());
                            assertThat(record.get("reason"))
                                    .isEqualTo("defect stops every resume");
                        });
        assertThat(
                        scalar(
                                "SELECT COUNT(*) FROM cds_import_event WHERE run_id=? AND"
                                        + " observation_context LIKE '%stops every resume%'",
                                census.run()))
                .isEqualTo("0");
        var facts = ledger.abandonment(census.run());
        assertThat(facts)
                .containsEntry("releasedCharts", 1L)
                .containsEntry("verified", 1L)
                .containsEntry("operator", OPERATOR.operator())
                .containsEntry("reasonSha256", CdsImportAbandonService.sha256(
                        "defect stops every resume"));
        assertThat((String) facts.get("protectedRecord")).contains("abandonment-");
        verify(results).generate(eq(census.run()), anyLong(), any());

        // The exporter keeps refusing the released chart by name.
        String exported =
                scalar(
                        "SELECT CONCAT(CAST(held AS CHAR),'/',state) FROM cds_import_patient"
                                + " WHERE run_id=?",
                        census.run());
        assertThatThrownBy(
                        () ->
                                CdsExportRetainedIndexService.requireExportableImportPatient(
                                        exported.split("/")[0], exported.split("/")[1]))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_ABANDONED_IMPORT_REQUIRES_REVIEW");

        // Abandoned is final: a second abandon or a resume is refused, without a new window.
        long released = generation();
        assertThatThrownBy(
                        () -> abandon.abandon(census.run(), OPERATOR, census.run(), "again"))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("IMPORT_ABANDONED"));
        assertThatThrownBy(() -> ledger.requestResume(census.run()))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("IMPORT_ABANDONED"));
        assertThat(generation()).isEqualTo(released);
        assertThat(maintenance.isClinicalAccessBlocked()).isFalse();
        assertThat(ledger.status(census.run()).state()).isEqualTo("ABANDONED");
    }

    @Test
    void shouldRefuseAnotherOperatorsAbandon_withoutTouchingTheRunOrTheWindow() throws Exception {
        Chart chart = chart("FAKE-ABANDON-TWO");
        Seeded census = census(chart, OPERATOR.operator());
        activateStaleMaintenance();
        long generation = generation();
        long epoch = ledger.status(census.run()).ownerEpoch();
        var intruder =
                new CdsImportActorDto("unix:another-operator", "999998", "999998", 2, null, null);
        assertThatThrownBy(
                        () -> abandon.abandon(census.run(), intruder, census.run(), "not mine"))
                .isInstanceOf(SecurityException.class);
        var otherProvider =
                new CdsImportActorDto(OPERATOR.operator(), "999997", "999997", 1, null, null);
        assertThatThrownBy(
                        () ->
                                abandon.abandon(
                                        census.run(), otherProvider, census.run(), "not mine"))
                .isInstanceOf(SecurityException.class);
        assertThat(ledger.status(census.run()).state()).isEqualTo("INCOMPLETE");
        assertThat(ledger.status(census.run()).ownerEpoch()).isEqualTo(epoch);
        assertThat(generation()).isEqualTo(generation);
        assertThat(scalar("SELECT COUNT(*) FROM cds_import_patient WHERE run_id=? AND held=TRUE",
                        census.run()))
                .isEqualTo("1");
        assertThat(maintenance.isClinicalAccessBlocked()).isTrue();

        // A run that was never cancelled is refused the same way, and its owner still can.
        execute("UPDATE cds_import_run SET cancel_requested=FALSE WHERE id=?", census.run());
        assertThatThrownBy(
                        () -> abandon.abandon(census.run(), OPERATOR, census.run(), "not yet"))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        refused ->
                                assertThat(refused.getCode())
                                        .isEqualTo("ABANDON_CANCEL_REQUIRED"));
        assertThat(generation()).isEqualTo(generation);
        execute("UPDATE cds_import_run SET cancel_requested=TRUE WHERE id=?", census.run());
        assertThat(abandon.abandon(census.run(), OPERATOR, census.run(), "owner closes it")
                        .state())
                .isEqualTo("ABANDONED");
        assertThat(maintenance.isClinicalAccessBlocked()).isFalse();
    }

    @Test
    void shouldDeferTheRelease_whileAnotherRunStillHoldsAChart() throws Exception {
        Chart first = chart("FAKE-ABANDON-THREE");
        Chart second = chart("FAKE-ABANDON-FOUR");
        Seeded held = census(first, OPERATOR.operator());
        Seeded other = census(second, OPERATOR.operator());
        activateStaleMaintenance();
        // A row of the second chart changed after its unit committed: the abandon records it.
        execute(
                "UPDATE allergies SET reaction='FAKE changed after import' WHERE allergyid=?",
                second.allergyId());

        assertThat(abandon.abandon(held.run(), OPERATOR, held.run(), "first stopped run").state())
                .isEqualTo("ABANDONED");
        assertThat(ledger.summary(held.run()).get("maintenanceReleaseState"))
                .isEqualTo("RELEASE_DEFERRED");
        assertThat(maintenance.isClinicalAccessBlocked()).isTrue();
        assertThat(scalar("SELECT maintenance_active FROM cds_import_admission WHERE"
                        + " id='clinical-maintenance'"))
                .isEqualTo("1");
        // The deferred run may only retry its release proof; that request is accepted.
        assertThatCode(() -> ledger.requestResume(held.run())).doesNotThrowAnyException();
        assertThat(ledger.status(held.run()).state()).isEqualTo("ABANDONED");

        assertThat(
                        abandon.abandon(other.run(), OPERATOR, other.run(), "second stopped run")
                                .state())
                .isEqualTo("ABANDONED");
        assertThat(ledger.summary(other.run()).get("maintenanceReleaseState"))
                .isEqualTo("RELEASED");
        assertThat(maintenance.isClinicalAccessBlocked()).isFalse();
        assertThat(
                        scalar(
                                "SELECT CONCAT(severity,' ',safe_summary) FROM"
                                        + " cds_import_diagnostic WHERE run_id=? AND"
                                        + " code='PATIENT_ABANDONED'",
                                other.run()))
                .startsWith("ERROR ")
                .endsWith("receipts: CHANGED");
        assertThat(ledger.abandonment(other.run())).containsEntry("changed", 1L);
    }

    private static Chart chart(String lastName) throws Exception {
        int demographicNo, allergyId;
        try (Connection raw = raw()) {
            try (PreparedStatement insert =
                    raw.prepareStatement(
                            "INSERT INTO demographic (last_name, first_name, lastUpdateDate)"
                                    + " VALUES (?, 'SYNTHETIC', NOW())",
                            Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, lastName);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    demographicNo = keys.getInt(1);
                }
            }
            try (PreparedStatement insert =
                    raw.prepareStatement(
                            "INSERT INTO allergies (demographic_no, DESCRIPTION, archived,"
                                    + " position, lastUpdateDate) VALUES (?, 'FAKE synthetic"
                                    + " allergy', 0, 0, NOW())",
                            Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, demographicNo);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    allergyId = keys.getInt(1);
                }
            }
        }
        CdsImportReceiptDto receipt =
                transaction.execute(
                        status ->
                                receipts.capture(
                                        Map.of(
                                                "demographic#0",
                                                Integer.toString(demographicNo),
                                                "allergies#1",
                                                Integer.toString(allergyId))));
        return new Chart(demographicNo, allergyId, receipt);
    }

    /** A run left as the census found it: INCOMPLETE, its chart PARTIAL_COMMITTED and held. */
    private static Seeded census(Chart chart, String operator) throws Exception {
        String run = UUID.randomUUID().toString();
        String patient = hash("patient", run), unit = hash("unit", run);
        String lineage = hash("lineage", run), source = hash("source", run);
        String options = hash("options", run), rules = hash("rules", run);
        try (Connection raw = raw()) {
            execute(
                    raw,
                    "INSERT INTO cds_import_run (id,submission_key,dry_run,report_state,"
                            + "cleanup_state,safe_error_code,review_version,discovery_complete,"
                            + "discovered_files,patient_xml_files,source_bytes,operator,provider_no,"
                            + "source_namespace,profile,source_digest,options_digest,"
                            + "ruleset_digest,snapshot_location,state,manifest_digest,"
                            + "execution_digest,expected_patients,expected_units,owner_epoch,"
                            + "cancel_requested,report_generation,created_at,updated_at,"
                            + "resume_requested) VALUES (?,?,0,'DELIVERED','COMPLETE',"
                            + "'NATIVE_BASELINE_REQUIRES_REVIEW',0,1,1,1,100,?,'999998',"
                            + "'census','omd-5.2',?,?,?,'/nonexistent/job.json','INCOMPLETE',?,?,"
                            + "1,2,6,1,1,NOW(6),NOW(6),0)",
                    run,
                    UUID.randomUUID().toString(),
                    operator,
                    source,
                    options,
                    rules,
                    hash("manifest", run),
                    hash("execution", run));
            execute(
                    raw,
                    "INSERT INTO cds_import_patient (id,run_id,patient_key,source_patient_id,"
                            + "copy_key,lineage_key,source_digest,target_demographic_no,state,"
                            + "held,expected_units,execution_digest) VALUES"
                            + " (?,?,?,'SYNTHETIC-1',?,?,?,?,'PARTIAL_COMMITTED',1,2,?)",
                    hash("patient-row", run),
                    run,
                    patient,
                    hash("copy", run),
                    lineage,
                    source,
                    chart.demographicNo(),
                    hash("patient-execution", run));
            execute(
                    raw,
                    "INSERT INTO cds_import_unit (id,run_id,patient_key,unit_key,lineage_key,"
                            + "category,ordinal,source_digest,spool_location,state,"
                            + "target_references,target_digest,committed_at,native_baseline)"
                            + " VALUES (?,?,?,?,?,'Demographics',0,?,'/nonexistent/unit',"
                            + "'COMMITTED',?,?,NOW(6),?)",
                    hash("unit-row", run),
                    run,
                    patient,
                    unit,
                    lineage,
                    source,
                    chart.receipt().targetReferences(),
                    chart.receipt().targetDigest(),
                    chart.receipt().nativeBaseline());
            execute(
                    raw,
                    "INSERT INTO cds_import_checkpoint (id,lineage_key,source_digest,"
                            + "options_digest,ruleset_digest,target_references,target_digest,"
                            + "first_run_id,committed_at,native_baseline) VALUES"
                            + " (?,?,?,?,?,?,?,?,NOW(6),?)",
                    unit,
                    lineage,
                    source,
                    options,
                    rules,
                    chart.receipt().targetReferences(),
                    chart.receipt().targetDigest(),
                    run,
                    chart.receipt().nativeBaseline());
            execute(
                    raw,
                    "INSERT INTO cds_import_unit (id,run_id,patient_key,unit_key,lineage_key,"
                            + "category,ordinal,source_digest,spool_location,state) VALUES"
                            + " (?,?,?,?,?,'Demographics_FIELDS',1,?,'/nonexistent/fields',"
                            + "'EXPECTED')",
                    hash("fields-row", run),
                    run,
                    patient,
                    hash("fields", run),
                    lineage,
                    hash("fields-source", run));
            execute(
                    raw,
                    "INSERT INTO cds_import_diagnostic (run_id,severity,code,stage,safe_summary,"
                            + "blocking,resolved,created_at) VALUES (?,'ERROR',"
                            + "'NATIVE_BASELINE_REQUIRES_REVIEW','EXECUTION','Import stopped;"
                            + " inspect protected source and unit results',1,0,NOW(6))",
                    run);
        }
        return new Seeded(run, patient, unit, chart);
    }

    /** The window the stopped worker left: active, under an owner that no longer exists. */
    private static void activateStaleMaintenance() throws Exception {
        execute(
                "UPDATE cds_import_admission SET maintenance_active=TRUE,"
                        + " maintenance_generation=maintenance_generation+1,"
                        + " maintenance_owner_token=?, maintenance_connection_id=255 WHERE"
                        + " id='clinical-maintenance'",
                UUID.randomUUID().toString());
    }

    private static long generation() throws Exception {
        return Long.parseLong(
                scalar(
                        "SELECT maintenance_generation FROM cds_import_admission WHERE"
                                + " id='clinical-maintenance'"));
    }

    private static List<String> events(String run) throws Exception {
        List<String> found = new ArrayList<>();
        try (Connection raw = raw();
                PreparedStatement query =
                        raw.prepareStatement(
                                "SELECT event_type FROM cds_import_event WHERE run_id=? ORDER BY"
                                        + " id")) {
            query.setString(1, run);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) found.add(rows.getString(1));
            }
        }
        return found;
    }

    /**
     * Every column of one row as the hex of its exact bytes (its binary form for a binary column,
     * its exact text for any other), for a byte-for-byte comparison.
     */
    private static List<String> row(String table, String key, Object id) throws Exception {
        assertThat(table + key).matches("[a-z_]+");
        try (Connection raw = raw()) {
            List<String> columns = new ArrayList<>();
            try (PreparedStatement query =
                            raw.prepareStatement(
                                    "SELECT column_name FROM information_schema.columns WHERE"
                                            + " table_schema=DATABASE() AND table_name=? ORDER BY"
                                            + " ordinal_position");
                    var bound = bind(query, table);
                    ResultSet rows = bound.executeQuery()) {
                while (rows.next()) columns.add(rows.getString(1));
            }
            StringBuilder select = new StringBuilder("SELECT ");
            for (int i = 0; i < columns.size(); i++) {
                assertThat(columns.get(i)).matches("[A-Za-z0-9_]+");
                if (i > 0) select.append(',');
                select.append("HEX(CAST(`").append(columns.get(i)).append("` AS BINARY))");
            }
            select.append(" FROM ").append(table).append(" WHERE ").append(key).append("=?");
            try (PreparedStatement query = raw.prepareStatement(select.toString())) {
                query.setObject(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    List<String> values = new ArrayList<>();
                    for (int i = 1; i <= columns.size(); i++)
                        values.add(columns.get(i - 1) + "=" + rows.getString(i));
                    assertThat(rows.next()).isFalse();
                    return values;
                }
            }
        }
    }

    private static PreparedStatement bind(PreparedStatement statement, String value)
            throws SQLException {
        statement.setString(1, value);
        return statement;
    }

    private static String scalar(String sql, Object... values) throws Exception {
        try (Connection raw = raw();
                PreparedStatement query = raw.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) query.setObject(i + 1, values[i]);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static void execute(String sql, Object... values) throws Exception {
        try (Connection raw = raw()) {
            execute(raw, sql, values);
        }
    }

    private static void execute(Connection raw, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = raw.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static String hash(String purpose, String run) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (purpose + ":" + run)
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** The scratch database without the admission guard: the stopped worker's own writes. */
    private static Connection raw() throws SQLException {
        return DriverManager.getConnection(
                scratchUrl, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"));
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(
                serverUrl, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"));
    }
}
