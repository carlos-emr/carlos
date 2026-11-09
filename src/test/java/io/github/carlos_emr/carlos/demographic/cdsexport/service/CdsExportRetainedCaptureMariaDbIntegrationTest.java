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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto;
import io.github.carlos_emr.carlos.commn.model.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real owning-DAO patient/category snapshots, digest-key paging and independently hashed large values. */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportRetainedCaptureMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final String run = UUID.randomUUID().toString();
    private final int patient =
            500_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(50_000_000);
    private static final Set<String> DEMOGRAPHICS = Set.of("Demographics");

    private String key(int id) {
        return "0".repeat(16) + run.replace("-", "") + String.format("%016x", id);
    }

    private String fileKey(int id) {
        return run.substring(0, 24) + String.format(java.util.Locale.ROOT, "%012x", id);
    }

    @BeforeEach
    void prepareOwnedGraph() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException("Owned CDS database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(properties.getProperty("jdbcUrl"));
        source.setUsername(properties.getProperty("jdbcUser"));
        source.setPassword(properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setDataSource(source);
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    CdsImportField.class.getName(),
                                    CdsImportUnit.class.getName(),
                                    CdsImportPreparation.class.getName(),
                                    CdsImportDependency.class.getName(),
                                    CdsImportPatient.class.getName(),
                                    CdsImportRun.class.getName(),
                                    CdsImportFile.class.getName(),
                                    CdsImportFileReference.class.getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.register(
                CdsImportFieldDao.class,
                CdsImportUnitDao.class,
                CdsImportPreparationDao.class,
                CdsImportDependencyDao.class,
                CdsImportPatientDao.class,
                CdsImportRunDao.class,
                CdsImportFileDao.class,
                CdsImportFileReferenceDao.class);
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        seed();
    }

    @AfterEach
    void cleanOwnedGraph() throws Exception {
        try {
            if (sql != null)
                for (String table :
                        List.of(
                                "cds_import_file_reference",
                                "cds_import_file",
                                "cds_import_field",
                                "cds_import_preparation",
                                "cds_import_dependency",
                                "cds_import_unit",
                                "cds_import_patient")) {
                    sql.update("delete from `" + table + "` where run_id=?", run);
                }
            if (sql != null) sql.update("delete from cds_import_run where id=?", run);
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (source != null) source.close();
            }
        }
    }

    @Test
    void
            shouldCaptureEveryMetadataColumnAndOnlyTheSelectedPatientCategories_andTheirNativeFieldEdges() {
        read.executeWithoutResult(
                status -> {
                    Map<Class<?>, CdsExportPatientTableReadSupport.Plan> plans =
                            Map.of(
                                    CdsImportFieldDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS,
                                    CdsImportUnitDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_UNITS,
                                    CdsImportPreparationDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_PREPARATIONS,
                                    CdsImportDependencyDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_DEPENDENCIES,
                                    CdsImportPatientDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_PATIENTS,
                                    CdsImportRunDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_RUNS,
                                    CdsImportFileDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_FILES,
                                    CdsImportFileReferenceDao.class,
                                    CdsExportPatientTableReadSupport.Plan.IMPORT_FILE_REFERENCES);
                    for (var entry : plans.entrySet()) {
                        var dao = (CdsExportRetainedTableReadDao) context.getBean(entry.getKey());
                        try {
                            new CdsExportSourceSchemaValidator()
                                    .verify(
                                            dao.readCdsExportRetainedSchema(),
                                            Set.copyOf(entry.getValue().columns()));
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    }
                    var fields = context.getBean(CdsImportFieldDao.class);
                    assertThat(fields.countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(34);
                    var first =
                            fields.findCdsExportRetainedRowsAfter(patient, DEMOGRAPHICS, null, 32);
                    var second =
                            fields.findCdsExportRetainedRowsAfter(
                                    patient, DEMOGRAPHICS, first.getLast().id(), 32);
                    assertThat(first).hasSize(32);
                    assertThat(second).hasSize(2);
                    assertThat(first.getFirst().id()).isEqualTo(key(1));
                    assertThat(second.getLast().id()).isEqualTo(key(34));
                    assertThat(first.getFirst().cells().get("normalized_value").sqlNull()).isTrue();
                    assertThat(first.get(1).cells().get("normalized_value").bytes()).isZero();
                    assertThat(
                                    new String(
                                            first.get(2)
                                                    .cells()
                                                    .get("normalized_value")
                                                    .inlineBytes(),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("0012 café 😀 ");
                    var units = context.getBean(CdsImportUnitDao.class);
                    assertThat(units.countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(4);
                    assertThat(
                                    units.findCdsExportRetainedRowsAfter(
                                            patient, DEMOGRAPHICS, null, 32))
                            .hasSize(4);
                    assertThat(
                                    context.getBean(CdsImportDependencyDao.class)
                                            .countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(3);
                    assertThat(
                                    context.getBean(CdsImportPreparationDao.class)
                                            .countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(1);
                    assertThat(
                                    context.getBean(CdsImportPatientDao.class)
                                            .countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(1);
                    var runs = context.getBean(CdsImportRunDao.class);
                    assertThat(runs.countCdsExportRetainedRows(patient, DEMOGRAPHICS)).isEqualTo(1);
                    assertThat(runs.findCdsExportRetainedRowsAfter(patient, DEMOGRAPHICS, null, 32))
                            .extracting(CdsExportRawKeyRowDto::id)
                            .containsExactly(run);
                    assertThat(runs.countCdsExportRetainedRows(patient + 2, DEMOGRAPHICS)).isZero();
                    assertThat(
                                    fields.countCdsExportRetainedRows(
                                            patient, Set.of("AllergiesAndAdverseReactions")))
                            .isEqualTo(1);
                    assertThat(fields.countCdsExportRetainedRows(patient + 1, DEMOGRAPHICS))
                            .isEqualTo(1);
                });
    }

    @Test
    void shouldStreamTheExactOldLargeValueAcrossAConcurrentUpdate_usingTheSamePatientSnapshot() {
        var fields = context.getBean(CdsImportFieldDao.class);
        String expected = "Retained café 😀\r\n ".repeat(10000);
        sql.update("update cds_import_field set original_value=? where id=?", expected, key(1));
        read.executeWithoutResult(
                status -> {
                    var row =
                            fields.findCdsExportRetainedRowsAfter(patient, DEMOGRAPHICS, null, 1)
                                    .getFirst();
                    assertThat(row.cells().get("original_value").inlineBytes()).isNull();
                    try (var writer = source.getConnection();
                            var update =
                                    writer.prepareStatement(
                                            "update cds_import_field set original_value='later edit' where id=? and run_id=?")) {
                        update.setString(1, key(1));
                        update.setString(2, run);
                        assertThat(update.executeUpdate()).isEqualTo(1);
                    } catch (java.sql.SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                    try (var input =
                            new CdsExportRawValueInputStream(
                                    row.cells().get("original_value"),
                                    (offset, count) ->
                                            fields.readCdsExportRetainedChunk(
                                                    patient,
                                                    DEMOGRAPHICS,
                                                    row.id(),
                                                    "original_value",
                                                    offset,
                                                    count))) {
                        assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                                .isEqualTo(expected);
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                });
        assertThat(
                        sql.queryForObject(
                                "select original_value from cds_import_field where id=?",
                                String.class,
                                key(1)))
                .isEqualTo("later edit");
    }

    @Test
    void shouldRefuseForeignPatientUnselectedCategoryAndInvalidScope_beforeReturningAnyPayload() {
        var fields = context.getBean(CdsImportFieldDao.class);
        read.executeWithoutResult(
                status -> {
                    for (String id : List.of(key(100), key(101))) {
                        assertThatThrownBy(
                                        () ->
                                                fields.readCdsExportRetainedChunk(
                                                        patient,
                                                        DEMOGRAPHICS,
                                                        id,
                                                        "original_value",
                                                        0,
                                                        1))
                                .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    }
                    assertThatThrownBy(
                                    () ->
                                            fields.findCdsExportRetainedRowsAfter(
                                                    patient, Set.of("Unknown"), null, 1))
                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    assertThatThrownBy(
                                    () ->
                                            fields.findCdsExportRetainedRowsAfter(
                                                    patient, DEMOGRAPHICS, "1", 1))
                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    assertThatThrownBy(
                                    () ->
                                            fields.readCdsExportRetainedChunk(
                                                    patient,
                                                    DEMOGRAPHICS,
                                                    key(1),
                                                    "not_a_column",
                                                    0,
                                                    1))
                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    status
                            .setRollbackOnly(); // Every deliberately refused DAO operation
                                                // correctly marks this shared transaction for
                                                // rollback.
                });
        assertThatThrownBy(() -> fields.countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void
            shouldRefuseAnUnclassifiedRetainedCategory_insteadOfFilteringItOutOfTheExpectedInventory() {
        field(102, patient + 1, "FutureClinicalSection", 6007, "unclassified foreign source");
        var fields = context.getBean(CdsImportFieldDao.class);
        Long count =
                read.execute(status -> fields.countCdsExportRetainedRows(patient, DEMOGRAPHICS));
        assertThat(count).isEqualTo(34L);
        field(103, patient, "FutureClinicalSection", 6001, "unclassified selected source");
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                fields.countCdsExportRetainedRows(
                                                        patient, DEMOGRAPHICS)))
                .hasMessage("CDS_EXPORT_RETAINED_CATEGORY_REQUIRES_REVIEW");
        sql.update(
                "update cds_import_unit set category='Demographics_FUTURE' where id=?", key(7000));
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                context.getBean(CdsImportUnitDao.class)
                                                        .countCdsExportRetainedRows(
                                                                patient, DEMOGRAPHICS)))
                .hasMessage("CDS_EXPORT_RETAINED_CATEGORY_REQUIRES_REVIEW");
    }

    @Test
    void fileClosureIsPatientAndCategoryScopedEvenWhenOnePayloadIsSharedAcrossChartsAndSections() {
        var files = context.getBean(CdsImportFileDao.class);
        var references = context.getBean(CdsImportFileReferenceDao.class);
        read.executeWithoutResult(
                status -> {
                    assertThat(files.countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(2);
                    var first =
                            files.findCdsExportRetainedRowsAfter(patient, DEMOGRAPHICS, null, 1);
                    var second =
                            files.findCdsExportRetainedRowsAfter(
                                    patient, DEMOGRAPHICS, first.getFirst().id(), 1);
                    assertThat(first)
                            .extracting(CdsExportRawKeyRowDto::id)
                            .containsExactly(fileKey(11000));
                    assertThat(second)
                            .extracting(CdsExportRawKeyRowDto::id)
                            .containsExactly(fileKey(11003));
                    assertThat(references.countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                            .isEqualTo(2);
                    assertThat(
                                    references.findCdsExportRetainedRowsAfter(
                                            patient, DEMOGRAPHICS, null, 32))
                            .extracting(CdsExportRawKeyRowDto::id)
                            .containsExactly(key(12000), key(12003));
                    assertThat(
                                    files.findCdsExportRetainedRowsAfter(
                                            patient,
                                            Set.of("AllergiesAndAdverseReactions"),
                                            null,
                                            32))
                            .extracting(CdsExportRawKeyRowDto::id)
                            .containsExactly(fileKey(11001), fileKey(11003));
                    assertThat(
                                    files.findCdsExportRetainedRowsAfter(
                                            patient + 1, DEMOGRAPHICS, null, 32))
                            .extracting(CdsExportRawKeyRowDto::id)
                            .containsExactly(fileKey(11002), fileKey(11003));
                    assertThat(files.countCdsExportRetainedRows(patient + 2, DEMOGRAPHICS))
                            .isZero();
                    assertThat(references.countCdsExportRetainedRows(patient + 2, DEMOGRAPHICS))
                            .isZero();
                });
    }

    @Test
    void fileChunkAccessCannotCrossPatientCategoryOrAnUnreferencedFileInTheSameRun() {
        var files = context.getBean(CdsImportFileDao.class);
        var references = context.getBean(CdsImportFileReferenceDao.class);
        read.executeWithoutResult(
                status -> {
                    for (String id : List.of(fileKey(11001), fileKey(11002), fileKey(11004)))
                        assertThatThrownBy(
                                        () ->
                                                files.readCdsExportRetainedChunk(
                                                        patient,
                                                        DEMOGRAPHICS,
                                                        id,
                                                        "location",
                                                        0,
                                                        1))
                                .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    assertThatThrownBy(
                                    () ->
                                            references.readCdsExportRetainedChunk(
                                                    patient,
                                                    DEMOGRAPHICS,
                                                    key(12001),
                                                    "file_id",
                                                    0,
                                                    1))
                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    assertThatThrownBy(
                                    () ->
                                            files.readCdsExportRetainedChunk(
                                                    patient,
                                                    Set.of("Unknown"),
                                                    fileKey(11000),
                                                    "location",
                                                    0,
                                                    1))
                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                    status.setRollbackOnly();
                });
    }

    @Test
    void fileMetadataAndChunksShareTheSameSnapshotAcrossAConcurrentUpdate() {
        var files = context.getBean(CdsImportFileDao.class);
        String expected = "/synthetic/" + run + "/file-é😀-0";
        read.executeWithoutResult(
                status -> {
                    var row =
                            files.findCdsExportRetainedRowsAfter(patient, DEMOGRAPHICS, null, 1)
                                    .getFirst();
                    try (var connection = source.getConnection();
                            var update =
                                    connection.prepareStatement(
                                            "update cds_import_file set location='changed file metadata' where id=? and run_id=?")) {
                        update.setString(1, fileKey(11000));
                        update.setString(2, run);
                        assertThat(update.executeUpdate()).isEqualTo(1);
                    } catch (java.sql.SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                    try (var input =
                            new CdsExportRawValueInputStream(
                                    row.cells().get("location"),
                                    (offset, count) ->
                                            files.readCdsExportRetainedChunk(
                                                    patient,
                                                    DEMOGRAPHICS,
                                                    row.id(),
                                                    "location",
                                                    offset,
                                                    count))) {
                        assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                                .isEqualTo(expected);
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                });
        assertThat(
                        sql.queryForObject(
                                "select location from cds_import_file where id=?",
                                String.class,
                                fileKey(11000)))
                .isEqualTo("changed file metadata");
    }

    @Test
    void fileReceiptRecipeMatchesTheHistoricalImporterAndCapturesTheSeparateVerificationEpoch() {
        read.executeWithoutResult(
                status -> {
                    var files = context.getBean(CdsImportFileDao.class);
                    var recipe = files.readCdsExportReceiptColumns();
                    assertThat(recipe).hasSize(8);
                    assertThat(recipe)
                            .extracting(
                                    io.github.carlos_emr.carlos.demographic.cds.contract
                                                    .CdsNativeRowDigest.Column
                                            ::name)
                            .doesNotContain("verified_epoch");
                    assertThat(
                                    files.findCdsExportRetainedRowsAfter(
                                                    patient, DEMOGRAPHICS, null, 1)
                                            .getFirst()
                                            .cells())
                            .containsKey("verified_epoch");
                    assertThat(
                                    context.getBean(CdsImportFileReferenceDao.class)
                                            .readCdsExportReceiptColumns())
                            .hasSize(4);
                });
    }

    @Test
    void fileOwningDaosRequireAnActualReadOnlyTransaction() {
        var files = context.getBean(CdsImportFileDao.class);
        var references = context.getBean(CdsImportFileReferenceDao.class);
        assertThatThrownBy(() -> files.countCdsExportRetainedRows(patient, DEMOGRAPHICS))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> references.readCdsExportReceiptColumns())
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(
                        () ->
                                write.execute(
                                        status ->
                                                files.countCdsExportRetainedRows(
                                                        patient, DEMOGRAPHICS)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void seed() {
        var row = new LinkedHashMap<String, Object>();
        row.putAll(
                Map.of(
                        "id",
                        run,
                        "submission_key",
                        "retained-" + run,
                        "operator",
                        "retained-test",
                        "provider_no",
                        "999998",
                        "profile",
                        "omd-5.2",
                        "source_namespace",
                        "retained-" + run,
                        "source_digest",
                        key(900),
                        "options_digest",
                        key(901),
                        "ruleset_digest",
                        key(902),
                        "snapshot_location",
                        "/synthetic/" + run));
        row.putAll(
                Map.of(
                        "state",
                        "COMPLETE",
                        "report_state",
                        "UNAVAILABLE",
                        "cleanup_state",
                        "RETAINED",
                        "dry_run",
                        0,
                        "review_version",
                        0,
                        "discovery_complete",
                        1,
                        "discovered_files",
                        1,
                        "patient_xml_files",
                        1,
                        "source_bytes",
                        100));
        row.putAll(
                Map.of(
                        "expected_patients",
                        2,
                        "expected_units",
                        8,
                        "owner_epoch",
                        1,
                        "cancel_requested",
                        0,
                        "report_generation",
                        0,
                        "created_at",
                        java.sql.Timestamp.from(java.time.Instant.now()),
                        "updated_at",
                        java.sql.Timestamp.from(java.time.Instant.now())));
        insert("cds_import_run", row);
        for (int n = 0; n < 2; n++) {
            var patientRow =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "id",
                                    key(9000 + n),
                                    "run_id",
                                    run,
                                    "patient_key",
                                    key(8000 + n),
                                    "source_patient_id",
                                    "SYNTHETIC-" + n,
                                    "copy_key",
                                    key(8100 + n),
                                    "lineage_key",
                                    key(8200 + n),
                                    "source_digest",
                                    key(8300 + n),
                                    "target_demographic_no",
                                    patient + n,
                                    "state",
                                    "COMPLETE",
                                    "held",
                                    0));
            patientRow.put("expected_units", 8);
            insert("cds_import_patient", patientRow);
        }
        String[] categories = {
            "Demographics",
            "Demographics_FIELDS",
            "Demographics_CONTACT",
            "Demographics_CARE",
            "AllergiesAndAdverseReactions",
            "AllergiesAndAdverseReactions_FIELDS",
            "Demographics",
            "Demographics_FIELDS"
        };
        for (int n = 0; n < categories.length; n++) {
            int owner = n < 6 ? 0 : 1;
            var unit =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "id",
                                    key(7000 + n),
                                    "run_id",
                                    run,
                                    "patient_key",
                                    key(8000 + owner),
                                    "unit_key",
                                    key(6000 + n),
                                    "lineage_key",
                                    key(8200 + owner),
                                    "category",
                                    categories[n],
                                    "ordinal",
                                    1,
                                    "source_digest",
                                    key(8300 + owner),
                                    "spool_location",
                                    "/synthetic/" + run + "/" + n,
                                    "state",
                                    "COMMITTED"));
            insert("cds_import_unit", unit);
        }
        for (int n = 1; n <= 34; n++)
            field(n, patient, "Demographics", 6001, n == 1 ? null : n == 2 ? "" : "0012 café 😀 ");
        field(100, patient, "AllergiesAndAdverseReactions", 6005, "unselected allergy");
        field(101, patient + 1, "Demographics", 6007, "foreign patient");
        for (int n : new int[] {0, 4, 6}) {
            insert(
                    "cds_import_preparation",
                    Map.of(
                            "id",
                            key(4000 + n),
                            "run_id",
                            run,
                            "unit_key",
                            key(6000 + n),
                            "source_digest",
                            key(8300),
                            "options_digest",
                            key(901),
                            "ruleset_digest",
                            key(902),
                            "payload_type",
                            "synthetic",
                            "payload",
                            "{\"retained\":true}",
                            "digest",
                            key(3000 + n)));
        }
        for (int n : new int[] {1, 2, 3, 5, 7})
            insert(
                    "cds_import_dependency",
                    Map.of(
                            "id",
                            key(2000 + n),
                            "run_id",
                            run,
                            "unit_key",
                            key(6000 + n),
                            "depends_on_unit_key",
                            key(6000 + (n < 4 ? 0 : n == 5 ? 4 : 6))));
        for (int n = 0; n < 5; n++)
            insert(
                    "cds_import_file",
                    Map.of(
                            "id",
                            fileKey(11000 + n),
                            "run_id",
                            run,
                            "location_key",
                            key(13000 + n),
                            "location",
                            "/synthetic/" + run + "/file-é😀-" + n,
                            "digest",
                            key(14000 + n),
                            "byte_length",
                            100,
                            "state",
                            "PUBLISHED",
                            "generation",
                            7,
                            "verified_epoch",
                            13));
        for (int n = 0; n < 6; n++)
            insert(
                    "cds_import_file_reference",
                    Map.of(
                            "id",
                            key(12000 + n),
                            "run_id",
                            run,
                            "unit_key",
                            key(n % 3 == 0 ? 6001 : n % 3 == 1 ? 6005 : 6007),
                            "file_id",
                            fileKey(11000 + (n < 3 ? n : 3))));
    }

    private void field(int id, int owner, String category, int unit, String normalized) {
        var row =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                key(id),
                                "run_id",
                                run,
                                "patient_key",
                                key(owner == patient ? 8000 : 8001),
                                "unit_key",
                                key(unit),
                                "demographic_no",
                                owner,
                                "category",
                                category,
                                "source_namespace",
                                "cds",
                                "source_path",
                                "/PatientRecord/" + category + "/Name[" + id + "]",
                                "occurrence",
                                1,
                                "value_type",
                                "{cds_dt}text"));
        row.put("original_value", "original");
        row.put("normalized_value", normalized);
        row.put("payload_offset", 0);
        row.put("payload_length", 0);
        insert("cds_import_field", row);
    }

    private void insert(String table, Map<String, Object> row) {
        var columns = new ArrayList<>(row.keySet());
        sql.update(
                "insert into `"
                        + table
                        + "` (`"
                        + String.join("`,`", columns)
                        + "`) values ("
                        + String.join(",", Collections.nCopies(columns.size(), "?"))
                        + ")",
                columns.stream().map(row::get).toArray());
    }
}
