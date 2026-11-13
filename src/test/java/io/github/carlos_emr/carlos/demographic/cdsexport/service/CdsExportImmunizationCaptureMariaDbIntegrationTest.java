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

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Selected vaccine data, dependencies, and configuration share one actual read-only database snapshot. */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportImmunizationCaptureMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final int
            patient =
                    520_000_000
                            + java.util.concurrent.ThreadLocalRandom.current().nextInt(20_000_000),
            first = -patient;
    private final List<Integer> patients = new ArrayList<>(),
            ids = new ArrayList<>(),
            extIds = new ArrayList<>(),
            precisionIds = new ArrayList<>();
    private PreventionDaoImpl dao;
    private CdsExportPreventionAccessService access;
    private CdsExportPreventionScopeSourceReader reader;
    private PreventionExtDaoImpl extensions;
    private PartialDateDaoImpl precision;
    private CdsExportPreventionSnapshotService snapshots;
    private final String largeText = "Chosen café 😀\r\n".repeat(10000);
    private final String run = UUID.randomUUID().toString();
    private boolean seededImport;

    private java.util.Set<Integer> vaccineIds() {
        return java.util.Set.of(first, first + 2);
    }

    @BeforeEach
    void prepare() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
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
                                    Prevention.class.getName(),
                                    PreventionExt.class.getName(),
                                    PartialDate.class.getName(),
                                    CVCImmunization.class.getName(),
                                    CVCMapping.class.getName(),
                                    CdsImportUnit.class.getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect",
                                    "hibernate.generate_statistics",
                                    "true"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        // The snapshot reads the patient's import receipts (review F10); no key root is needed
        // here because the receipt scenario seals its synthetic witness with test keys.
        context.registerBean(
                "cdsExportProperties", Properties.class, () -> new Properties());
        context.register(
                CdsImportUnitDao.class,
                PreventionDaoImpl.class,
                PreventionExtDaoImpl.class,
                PartialDateDaoImpl.class,
                CVCImmunizationDaoImpl.class,
                CVCMappingDaoImpl.class,
                CdsExportPreventionAccessService.class,
                CdsExportPreventionConfigurationLoader.class,
                CdsExportPreventionSnapshotService.class);
        context.refresh();
        dao = context.getBean(PreventionDaoImpl.class);
        access = context.getBean(CdsExportPreventionAccessService.class);
        reader = new CdsExportPreventionScopeSourceReader(dao, access);
        extensions = context.getBean(PreventionExtDaoImpl.class);
        precision = context.getBean(PartialDateDaoImpl.class);
        snapshots = context.getBean(CdsExportPreventionSnapshotService.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        for (int owner : List.of(patient, patient + 1)) {
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from demographic where demographic_no=?",
                                    Integer.class,
                                    owner))
                    .isZero();
            sql.update(
                    "insert into demographic(demographic_no,first_name,last_name,patient_status,lastUpdateDate) values(?,'Synthetic','PreventionScopeFixture','IN',CURRENT_TIMESTAMP)",
                    owner);
            patients.add(owner);
        }
        for (int i = 0; i < 4; i++) {
            int id = first + i;
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from preventions where id=?",
                                    Integer.class,
                                    id))
                    .isZero();
            sql.update(
                    "insert into preventions(id,demographic_no,creation_date,prevention_date,provider_no,provider_name,prevention_type,deleted,refused,next_date,never,creator,lastUpdateDate,restrictToProgram,programNo,snomedId) values(?,?,'2024-02-29 12:34:56','2024-02-29 12:34:56','','CLINICAL_PROVIDER_VALUE_MUST_NOT_BE_CAPTURED',?,?,0,NULL,0,'',CURRENT_TIMESTAMP,?,NULL,NULL)",
                    id,
                    i == 3 ? patient + 1 : patient,
                    i == 0 ? "Td" : i == 1 ? "PAP" : i == 2 ? "H1N1" : "FOREIGN_TYPE",
                    i == 2 ? 1 : 0,
                    i == 3 ? 1 : null);
            ids.add(id);
        }
        prepareOwnedDependentRows();
    }

    private void prepareOwnedDependentRows() {
        for (int i = 0; i < 6; i++) {
            int id = first - 10 - i;
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from preventionsExt where id=?",
                                    Integer.class,
                                    id))
                    .isZero();
            int parent =
                    i == 0 || i == 1
                            ? first
                            : i == 2 || i == 3 ? first + 2 : i == 4 ? first + 1 : first + 3;
            String key = i < 2 ? "comments" : i == 2 ? "lot" : i == 3 ? "manufacture" : "comments";
            String value =
                    i == 0
                            ? largeText
                            : i == 1
                                    ? "SECOND_DUPLICATE_VACCINE_COMMENT"
                                    : i == 2
                                            ? null
                                            : i == 3 ? "" : "UNSELECTED_SCREENING_OR_FOREIGN_VALUE";
            sql.update(
                    "insert into preventionsExt(id,prevention_id,keyval,val) values(?,?,?,?)",
                    id,
                    parent,
                    key,
                    value);
            extIds.add(id);
        }
        for (int i = 0; i < 5; i++) {
            int id = first - 30 - i;
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from partial_date where id=?",
                                    Integer.class,
                                    id))
                    .isZero();
            int parent =
                    i == 0
                            ? first
                            : i == 1 ? first + 2 : i == 2 ? first + 1 : i == 3 ? first + 3 : first;
            sql.update(
                    "insert into partial_date(id,table_name,table_id,field_name,format) values(?,?,?,?,?)",
                    id,
                    i == 4 ? 1 : 4,
                    parent,
                    i == 1 ? 8 : 7,
                    i == 1 ? "YYYY-MM" : "YYYY");
            precisionIds.add(id);
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (sql != null && seededImport)
                for (String table : List.of("cds_import_unit", "cds_import_patient"))
                    sql.update("delete from `" + table + "` where run_id=?", run);
            if (sql != null && seededImport) sql.update("delete from cds_import_run where id=?", run);
            if (sql != null) {
                for (int id : extIds)
                    sql.update(
                            "delete from preventionsExt where id=? and prevention_id between ? and ?",
                            id,
                            first,
                            first + 3);
                for (int id : precisionIds)
                    sql.update(
                            "delete from partial_date where id=? and table_id between ? and ?",
                            id,
                            first,
                            first + 3);
                for (int id : ids)
                    sql.update(
                            "delete from preventions where id=? and demographic_no in (?,?) and provider_name='CLINICAL_PROVIDER_VALUE_MUST_NOT_BE_CAPTURED'",
                            id,
                            patient,
                            patient + 1);
                for (int owner : patients)
                    sql.update(
                            "delete from demographic where demographic_no=? and first_name='Synthetic' and last_name='PreventionScopeFixture'",
                            owner);
            }
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (source != null) source.close();
            }
        }
    }

    @Test
    void shouldVerifyActualJpaSnapshotOwnsTheDefinitionContextAndRejectsReuse_afterCommit() throws Exception {
        final CdsExportPreventionSnapshotService.State[] state = {null};
        read.executeWithoutResult(
                status -> {
                    try {
                        state[0] = snapshots.prepare(patient, () -> {});
                        assertThat(state[0].clinicalIds()).isEqualTo(vaccineIds());
                        assertThat(snapshots.current(patient)).isSameAs(state[0]);
                        assertThat(state[0].selection().otherPreventions())
                                .containsExactly(first + 1);
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        assertThatThrownBy(() -> state[0].selection())
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_SCOPE_INVALID");
        assertThatThrownBy(() -> snapshots.current(patient))
                .hasMessage("CDS_EXPORT_PREVENTION_SNAPSHOT_REQUIRED");
    }

    // The vaccine selection still excludes the screening row; that row is no longer dropped: the
    // PastHealth screening selection reads it with its children (see the next test).
    @Test
    void
            shouldVerifySelectedOwningDaosIncludeEveryVaccineColumnAndChildWhileExcludingScreeningAndForeignValues_forImmunizationCaptureContract() {
        read.executeWithoutResult(
                status -> {
                    assertThat(dao.countCdsExportPreventionRows(patient, vaccineIds()))
                            .isEqualTo(2);
                    assertThat(extensions.countCdsExportPreventionRows(patient, vaccineIds()))
                            .isEqualTo(4);
                    assertThat(precision.countCdsExportPreventionRows(patient, vaccineIds()))
                            .isEqualTo(2);
                    var parent =
                            dao.findCdsExportPreventionRowsAfter(patient, vaccineIds(), null, 32);
                    assertThat(parent)
                            .hasSize(2)
                            .allSatisfy(row -> assertThat(row.cells()).hasSize(16));
                    var child =
                            extensions.findCdsExportPreventionRowsAfter(
                                    patient, vaccineIds(), null, 32);
                    assertThat(child)
                            .hasSize(4)
                            .allSatisfy(row -> assertThat(row.cells()).hasSize(4));
                    var dates =
                            precision.findCdsExportPreventionRowsAfter(
                                    patient, vaccineIds(), null, 32);
                    assertThat(dates)
                            .hasSize(2)
                            .allSatisfy(row -> assertThat(row.cells()).hasSize(5));
                    var firstPage =
                            dao.findCdsExportPreventionRowsAfter(patient, vaccineIds(), null, 1);
                    assertThat(firstPage.getFirst().id()).isEqualTo(first);
                    assertThat(
                                    dao.findCdsExportPreventionRowsAfter(
                                                    patient,
                                                    vaccineIds(),
                                                    firstPage.getFirst().id(),
                                                    1)
                                            .getFirst()
                                            .id())
                            .isEqualTo(first + 2);
                });
    }

    @Test
    void shouldReadTheScreeningRowAndItsChildren_throughTheSeparatePastHealthSelection() {
        read.executeWithoutResult(
                status -> {
                    try {
                        var state = snapshots.prepare(patient, () -> {});
                        assertThat(state.screeningIds()).containsExactly(first + 1);
                        var parents = new CdsExportScreeningSourceReader(dao, snapshots, access);
                        var children =
                                new CdsExportScreeningExtSourceReader(extensions, snapshots, access);
                        var dates =
                                new CdsExportScreeningPrecisionSourceReader(
                                        precision, snapshots, access);
                        assertThat(parents.table()).isEqualTo("preventions_screening");
                        assertThat(parents.count(patient)).isEqualTo(1);
                        assertThat(children.count(patient)).isEqualTo(1);
                        assertThat(dates.count(patient)).isEqualTo(1);
                        var screening = Set.of(first + 1);
                        assertThat(dao.findCdsExportPreventionRowsAfter(patient, screening, null, 32))
                                .singleElement()
                                .satisfies(row -> assertThat(row.cells()).hasSize(16));
                        assertThat(
                                        extensions.findCdsExportPreventionRowsAfter(
                                                patient, screening, null, 32))
                                .singleElement()
                                .satisfies(row -> assertThat(row.id()).isEqualTo(first - 14));
                        assertThat(
                                        precision.findCdsExportPreventionRowsAfter(
                                                patient, screening, null, 32))
                                .singleElement()
                                .satisfies(row -> assertThat(row.id()).isEqualTo(first - 32));
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    @Test
    void shouldVerifyUnselectedAndForeignChunksOrColumnsCannotBeReadEvenThroughTheSelectedApi_forImmunizationCaptureContract() {
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                extensions.readCdsExportPreventionChunk(
                                                        patient,
                                                        vaccineIds(),
                                                        first - 14,
                                                        "val",
                                                        0,
                                                        100)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                extensions.readCdsExportPreventionChunk(
                                                        patient,
                                                        vaccineIds(),
                                                        first - 15,
                                                        "val",
                                                        0,
                                                        100)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                dao.readCdsExportPreventionChunk(
                                                        patient,
                                                        vaccineIds(),
                                                        first,
                                                        "password",
                                                        0,
                                                        100)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                precision.findCdsExportPreventionRowsAfter(
                                                        patient, vaccineIds(), null, 33)))
                .isInstanceOf(IllegalStateException.class);
        read.executeWithoutResult(
                status -> {
                    assertThat(dao.countCdsExportPreventionRows(patient, Set.of(first + 3)))
                            .isZero();
                    assertThat(extensions.countCdsExportPreventionRows(patient, Set.of())).isZero();
                    assertThat(
                                    precision.findCdsExportPreventionRowsAfter(
                                            patient, Set.of(), null, 32))
                            .isEmpty();
                });
    }

    @Test
    void shouldVerifySelectedLargeExtensionChunksPreserveUtf8BytesAndNullVersusEmpty_forImmunizationCaptureContract() {
        byte[] expected = largeText.getBytes(StandardCharsets.UTF_8);
        read.executeWithoutResult(
                status -> {
                    assertThat(
                                    extensions.readCdsExportPreventionChunk(
                                            patient, vaccineIds(), first - 10, "val", 1, 17))
                            .isEqualTo(Arrays.copyOfRange(expected, 1, 18));
                    var rows =
                            extensions.findCdsExportPreventionRowsAfter(
                                    patient, vaccineIds(), null, 32);
                    assertThat(
                                    rows.stream()
                                            .filter(row -> row.id() == first - 12)
                                            .findFirst()
                                            .orElseThrow()
                                            .cells()
                                            .get("val")
                                            .sqlNull())
                            .isTrue();
                    var empty =
                            rows.stream()
                                    .filter(row -> row.id() == first - 13)
                                    .findFirst()
                                    .orElseThrow()
                                    .cells()
                                    .get("val");
                    assertThat(empty.sqlNull()).isFalse();
                    assertThat(empty.bytes()).isZero();
                });
    }

    @Test
    void shouldVerifyConfigurationAndAllSelectedValuesShareOneActualSnapshot_withoutEntityHydration()
            throws Exception {
        var sources =
                List.<CdsExportNativeSourceReader>of(
                                new CdsExportCvcImmunizationSourceReader(
                                        context.getBean(CVCImmunizationDaoImpl.class)),
                                new CdsExportCvcMappingSourceReader(
                                        context.getBean(CVCMappingDaoImpl.class)),
                                new CdsExportPreventionConfigurationSourceReader(
                                        snapshots, access),
                                reader,
                                new CdsExportImmunizationSourceReader(dao, snapshots, access),
                                new CdsExportImmunizationExtSourceReader(
                                        extensions, snapshots, access),
                                new CdsExportImmunizationPrecisionSourceReader(
                                        precision, snapshots, access))
                        .stream()
                        .sorted(Comparator.comparing(CdsExportNativeSourceReader::table))
                        .toList();
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        var expected = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        var stats =
                context.getBean(jakarta.persistence.EntityManagerFactory.class)
                        .unwrap(org.hibernate.SessionFactory.class)
                        .getStatistics();
        stats.clear();
        read.executeWithoutResult(
                status -> {
                    try {
                        for (var input : sources) {
                            new CdsExportSourceSchemaValidator()
                                    .verify(input.table(), input.schema(), input.columns());
                            expected.put(
                                    input.table(),
                                    new CdsExportNativeCaptureReader.Table(
                                            input.columns(), input.count(patient, () -> {})));
                        }
                        sqlUpdateOutsideSnapshot(
                                "update preventionsExt set val='CHANGED_AFTER_SNAPSHOT' where id=? and prevention_id=?",
                                first - 10,
                                first);
                        new CdsExportNativeCaptureComposer()
                                .compose(
                                        values,
                                        index,
                                        expected.keySet(),
                                        100000,
                                        8L << 20,
                                        () -> {},
                                        writer -> {
                                            for (var input : sources) {
                                                writer.beginTable(
                                                        input.table(),
                                                        input.columns(),
                                                        expected.get(input.table()).rows());
                                                input.capture(patient, writer);
                                                writer.endTable();
                                            }
                                        });
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        var proof =
                new CdsExportNativeCaptureReader()
                        .verify(
                                new ByteArrayInputStream(values.toByteArray()),
                                new ByteArrayInputStream(index.toByteArray()),
                                expected,
                                100000,
                                8L << 20,
                                () -> {});
        assertThat(proof.fields())
                .isEqualTo(
                        expected.values().stream()
                                .mapToLong(table -> table.rows() * table.columns().size())
                                .sum());
        assertThat(expected.get("preventions").rows()).isEqualTo(2);
        assertThat(expected.get("preventionsExt").rows()).isEqualTo(4);
        assertThat(expected.get("prevention_precision").rows()).isEqualTo(2);
        assertThat(values.toString(StandardCharsets.UTF_8))
                .contains(largeText, "SECOND_DUPLICATE_VACCINE_COMMENT", "2024-02-29 12:34:56")
                .doesNotContain(
                        "CHANGED_AFTER_SNAPSHOT",
                        "UNSELECTED_SCREENING_OR_FOREIGN_VALUE",
                        "FOREIGN_TYPE");
        assertThat(stats.getEntityLoadCount()).isZero();
    }

    @Test
    void shouldVerifyUnknownInjectionCategoryNeverTurnsIntoAnEmptyOrAutomaticallyIncludedClinicalSelection_forImmunizationCaptureContract() {
        // A type the installed configuration does not define is held, never silently included.
        sql.update(
                "update preventions set prevention_type='UNCONFIGURED_INJECTION' where id=? and demographic_no=?",
                first,
                patient);
        read.executeWithoutResult(
                status -> {
                    try {
                        var state = snapshots.prepare(patient, () -> {});
                        assertThat(state.selection().reviews())
                                .containsEntry(
                                        first, CdsExportPreventionScopePlanner.Review.UNKNOWN_TYPE);
                        assertThatThrownBy(state::clinicalIds)
                                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    // A30: with the installed configuration a native "Other immunization or injection" (OtherA)
    // is captured as an immunization, with or without an import, and does not hold a
    // PastHealth-only export: it is not a screening.
    @Test
    void shouldCaptureANativeCatchAllInjection_asAnImmunizationWithTheInstalledConfiguration() {
        sql.update(
                "update preventions set prevention_type='OtherA' where id=? and demographic_no=?",
                first,
                patient);
        read.executeWithoutResult(
                status -> {
                    try {
                        var state = snapshots.prepare(patient, () -> {});
                        assertThat(state.selection().reviews()).doesNotContainKey(first);
                        assertThat(state.clinicalIds()).isEqualTo(vaccineIds());
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    // Review finding F10: the importer files a vaccine with an absent or unconfigured type as
    // OtherA. Its applied Immunizations import unit, read from the same snapshot through the
    // owning DAO, proves the row was imported, so capture selects it as a vaccine.
    @Test
    void shouldCaptureAReceiptProvenImportedCatchAllVaccine_fromTheSnapshotsImportUnits()
            throws Exception {
        sql.update(
                "update preventions set prevention_type='OtherA' where id=? and demographic_no=?",
                first,
                patient);
        var keys =
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys
                        .keys();
        seedImport(patient, CdsExportImmunizationImportReceiptFixture.unit(run, patient, first, keys));
        var receipts =
                new CdsExportPreventionSnapshotService(
                        context.getBean(jakarta.persistence.EntityManagerFactory.class),
                        context.getBean(CdsExportPreventionConfigurationLoader.class),
                        context.getBean(CVCImmunizationDaoImpl.class),
                        context.getBean(CVCMappingDaoImpl.class),
                        dao,
                        context.getBean(CdsImportUnitDao.class),
                        keys);
        read.executeWithoutResult(
                status -> {
                    try {
                        var state = receipts.prepare(patient, () -> {}, true);
                        assertThat(state.clinicalIds()).isEqualTo(vaccineIds());
                        assertThat(state.selection().otherPreventions())
                                .containsExactly(first + 1);
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        // Without Immunizations no Immunizations unit is copied; the installed configuration
        // still classifies the catch-all as an immunization, so it neither holds a PastHealth-only
        // export nor becomes one of its screenings (A30).
        read.executeWithoutResult(
                status -> {
                    try {
                        var selection = receipts.prepare(patient, () -> {}, false).selection();
                        assertThat(selection.reviews()).doesNotContainKey(first);
                        assertThat(selection.otherPreventions()).doesNotContain(first);
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    // A foreign receipt still proves nothing (CdsExportImmunizationImportReceiptsUnitTest); with
    // the installed configuration the catch-all no longer needs that proof (A30).
    @Test
    void shouldCaptureTheInstalledCatchAll_whenTheImportReceiptBelongsToAnotherPatientsImport()
            throws Exception {
        sql.update(
                "update preventions set prevention_type='OtherA' where id=? and demographic_no=?",
                first,
                patient);
        var keys =
                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys
                        .keys();
        seedImport(patient + 1, CdsExportImmunizationImportReceiptFixture.unit(run, patient, first, keys));
        var receipts =
                new CdsExportPreventionSnapshotService(
                        context.getBean(jakarta.persistence.EntityManagerFactory.class),
                        context.getBean(CdsExportPreventionConfigurationLoader.class),
                        context.getBean(CVCImmunizationDaoImpl.class),
                        context.getBean(CVCMappingDaoImpl.class),
                        dao,
                        context.getBean(CdsImportUnitDao.class),
                        keys);
        read.executeWithoutResult(
                status -> {
                    try {
                        var state = receipts.prepare(patient, () -> {}, true);
                        assertThat(state.selection().reviews()).doesNotContainKey(first);
                        assertThat(state.clinicalIds()).isEqualTo(vaccineIds());
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    /** One synthetic published import run whose patient targets {@code owner}. */
    private void seedImport(int owner, Map<String, String> unit) {
        seededImport = true;
        var runRow = new LinkedHashMap<String, Object>();
        runRow.putAll(
                Map.of(
                        "id",
                        run,
                        "submission_key",
                        "f10-" + run,
                        "operator",
                        "f10-test",
                        "provider_no",
                        "999998",
                        "profile",
                        "omd-5.2",
                        "source_namespace",
                        "f10-" + run,
                        "source_digest",
                        "a".repeat(64),
                        "options_digest",
                        "b".repeat(64),
                        "ruleset_digest",
                        "c".repeat(64),
                        "snapshot_location",
                        "/synthetic/" + run));
        runRow.putAll(
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
        runRow.putAll(
                Map.of(
                        "expected_patients",
                        1,
                        "expected_units",
                        1,
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
        insert("cds_import_run", runRow);
        var patientRow =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                CdsExportImmunizationImportReceiptFixture.sha("patient-" + run),
                                "run_id",
                                run,
                                "patient_key",
                                CdsExportImmunizationImportReceiptFixture.PATIENT_KEY,
                                "source_patient_id",
                                "SYNTHETIC-F10",
                                "copy_key",
                                "e".repeat(64),
                                "lineage_key",
                                "e".repeat(64),
                                "source_digest",
                                "c".repeat(64),
                                "target_demographic_no",
                                owner,
                                "state",
                                "COMPLETE",
                                "held",
                                0));
        patientRow.put("expected_units", 1);
        insert("cds_import_patient", patientRow);
        var unitRow = new LinkedHashMap<String, Object>();
        unit.forEach(
                (column, value) -> {
                    if (value != null) unitRow.put(column, value);
                });
        unitRow.put("ordinal", Integer.parseInt(unit.get("ordinal")));
        insert("cds_import_unit", unitRow);
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

    @Test
    void shouldVerifyReadonlyAndProgramBoundariesRemainMandatoryEven_forEmptySelections() {
        assertThatThrownBy(() -> dao.countCdsExportPreventionRows(patient, Set.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(
                        () ->
                                write.executeWithoutResult(
                                        status ->
                                                extensions.countCdsExportPreventionRows(
                                                        patient, Set.of())))
                .isInstanceOf(IllegalStateException.class);
        sql.update(
                "update preventions set restrictToProgram=1,programNo=1 where id=? and demographic_no=?",
                first,
                patient);
        // The DAO no longer refuses a chart with a restricted row: the program boundary is the
        // readers' and the selection's, which a capture never prepared does not pass.
        Long none =
                read.execute(status -> precision.countCdsExportPreventionRows(patient, Set.of()));
        assertThat(none).isZero();
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status -> {
                                            try {
                                                snapshots.prepare(patient, () -> {});
                                            } catch (IOException failed) {
                                                throw new UncheckedIOException(failed);
                                            }
                                        }))
                .isInstanceOf(CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
    }

    /** The prevention readers of an Immunizations and PastHealth capture, as registered. */
    private List<CdsExportNativeSourceReader> preventionReaders() {
        return List.<CdsExportNativeSourceReader>of(
                        new CdsExportCvcImmunizationSourceReader(
                                context.getBean(CVCImmunizationDaoImpl.class)),
                        new CdsExportCvcMappingSourceReader(context.getBean(CVCMappingDaoImpl.class)),
                        new CdsExportPreventionConfigurationSourceReader(snapshots, access),
                        reader,
                        new CdsExportImmunizationSourceReader(dao, snapshots, access),
                        new CdsExportImmunizationExtSourceReader(extensions, snapshots, access),
                        new CdsExportImmunizationPrecisionSourceReader(precision, snapshots, access),
                        new CdsExportScreeningSourceReader(dao, snapshots, access),
                        new CdsExportScreeningExtSourceReader(extensions, snapshots, access),
                        new CdsExportScreeningPrecisionSourceReader(precision, snapshots, access))
                .stream()
                .sorted(Comparator.comparing(CdsExportNativeSourceReader::table))
                .toList();
    }

    /** Counts and captures every reader in one snapshot, as the capture service does. */
    private Map<String, Long> captureAll(List<CdsExportNativeSourceReader> readers, OutputStream values) {
        var counts = new TreeMap<String, Long>();
        read.executeWithoutResult(
                status -> {
                    try {
                        for (var input : readers)
                            counts.put(input.table(), input.count(patient, () -> {}));
                        new CdsExportNativeCaptureComposer()
                                .compose(
                                        values,
                                        OutputStream.nullOutputStream(),
                                        counts.keySet(),
                                        100000,
                                        8L << 20,
                                        () -> {},
                                        writer -> {
                                            for (var input : readers) {
                                                writer.beginTable(
                                                        input.table(),
                                                        input.columns(),
                                                        counts.get(input.table()));
                                                input.capture(patient, writer);
                                                writer.endTable();
                                            }
                                        });
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        return counts;
    }

    private List<CdsExportNativeSourceReader> prepared(
            List<CdsExportNativeSourceReader> readers,
            io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl control)
            throws IOException {
        var result = new ArrayList<CdsExportNativeSourceReader>();
        for (var input : readers) {
            var ready = input.forCapture(patient, control);
            assertThat(ready.table()).isEqualTo(input.table());
            assertThat(ready.categories()).isEqualTo(input.categories());
            assertThat(ready.requiredTables()).isEqualTo(input.requiredTables());
            ready.authorizeReadScope(patient, control);
            result.add(ready);
        }
        return result;
    }

    // OPEN-6: a legacy prevention restricted to a program no longer holds the chart. It is read
    // once every exporting identity is known to work in its program, as the chart's document
    // rule says, and the restrictions read before the snapshot are proved inside it.
    @Test
    void shouldCaptureRestrictedPreventions_onlyAfterEveryReaderAuthorizedTheirProgramsLive()
            throws Exception {
        sql.update(
                "update preventions set restrictToProgram=1,programNo=57 where id=? and demographic_no=?",
                first,
                patient);
        sql.update(
                "update preventions set restrictToProgram=NULL,programNo=58 where id=? and demographic_no=?",
                first + 1,
                patient);
        var registered = preventionReaders();
        // Readers never prepared for this chart read nothing restricted.
        assertThatThrownBy(() -> captureAll(registered, OutputStream.nullOutputStream()))
                .isInstanceOf(CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        var asked = new ArrayList<String>();
        var allowed = CdsExportPreventionScopeMariaDbIntegrationTest.programs(true, asked);
        var ready = prepared(registered, allowed);
        assertThat(asked)
                .contains(
                        "Immunizations:57",
                        "Immunizations:58",
                        "PastHealth:57",
                        "PastHealth:58");
        var values = new ByteArrayOutputStream();
        var counts = captureAll(ready, values);
        assertThat(counts)
                .containsEntry("preventions", 2L)
                .containsEntry(CdsExportPreventionSources.SCREENINGS, 1L)
                .containsEntry("preventions_scope", 3L);
        assertThat(values.toString(StandardCharsets.UTF_8)).contains(largeText, "PAP", "Td");
        // A program nobody exporting works in holds the patient before any value is read.
        for (var input : registered) {
            var refused =
                    input.forCapture(
                            patient,
                            CdsExportPreventionScopeMariaDbIntegrationTest.programs(
                                    false, new ArrayList<>()));
            if (input.table().startsWith("CVC")) continue;
            assertThatThrownBy(
                            () ->
                                    refused.authorizeReadScope(
                                            patient,
                                            CdsExportPreventionScopeMariaDbIntegrationTest.programs(
                                                    false, new ArrayList<>())))
                    .as(input.table())
                    .isInstanceOf(CdsExportReviewException.class)
                    .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        }
        // A restriction changed between the preflight and the snapshot is a changed source.
        sql.update(
                "update preventions set programNo=59 where id=? and demographic_no=?",
                first,
                patient);
        assertThatThrownBy(() -> captureAll(ready, OutputStream.nullOutputStream()))
                .isInstanceOf(CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        // Every selected reader proves its own authorized restrictions against the selection's.
        var stale = prepared(registered, allowed);
        sql.update(
                "update preventions set programNo=57 where id=? and demographic_no=?",
                first,
                patient);
        var mixed = new ArrayList<CdsExportNativeSourceReader>();
        var fresh = prepared(registered, allowed);
        for (int i = 0; i < registered.size(); i++)
            mixed.add(
                    registered.get(i).table().equals("preventions") ? stale.get(i) : fresh.get(i));
        assertThatThrownBy(() -> captureAll(mixed, OutputStream.nullOutputStream()))
                .isInstanceOf(CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_SOURCE_CHANGED");
    }

    @Test
    void shouldCaptureAFlagWithoutAProgramOrAnExplicitZeroFlag_asUnrestricted() {
        sql.update(
                "update preventions set restrictToProgram=1,programNo=NULL where id=? and demographic_no=?",
                first,
                patient);
        sql.update(
                "update preventions set restrictToProgram=0,programNo=57 where id=? and demographic_no=?",
                first + 1,
                patient);
        sql.update(
                "update preventions set restrictToProgram=1,programNo=-1 where id=? and demographic_no=?",
                first + 2,
                patient);
        // No program to authorize: the readers never prepared read every row.
        var counts = captureAll(preventionReaders(), OutputStream.nullOutputStream());
        assertThat(counts).containsEntry("preventions", 2L).containsEntry("preventions_scope", 3L);
    }

    private void sqlUpdateOutsideSnapshot(String statement, int id, int parent) {
        try (var connection = source.getConnection();
                var update = connection.prepareStatement(statement)) {
            update.setInt(1, id);
            update.setInt(2, parent);
            assertThat(update.executeUpdate()).isEqualTo(1);
        } catch (java.sql.SQLException failed) {
            throw new IllegalStateException(failed);
        }
    }
}
