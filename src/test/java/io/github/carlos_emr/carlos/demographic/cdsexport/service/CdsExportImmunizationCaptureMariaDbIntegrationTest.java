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
                                    CVCMapping.class.getName()));
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
        context.register(
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
    void actualJpaSnapshotOwnsTheDefinitionContextAndRejectsReuseAfterCommit() throws Exception {
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

    @Test
    void
            selectedOwningDaosIncludeEveryVaccineColumnAndChildWhileExcludingScreeningAndForeignValues() {
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
    void unselectedAndForeignChunksOrColumnsCannotBeReadEvenThroughTheSelectedApi() {
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
    void selectedLargeExtensionChunksPreserveUtf8BytesAndNullVersusEmpty() {
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
    void configurationAndAllSelectedValuesShareOneActualSnapshotWithoutEntityHydration()
            throws Exception {
        var sources =
                List.<CdsExportNativeSourceReader>of(
                                new CdsExportCvcImmunizationSourceReader(
                                        context.getBean(CVCImmunizationDaoImpl.class)),
                                new CdsExportCvcMappingSourceReader(
                                        context.getBean(CVCMappingDaoImpl.class)),
                                new CdsExportPreventionConfigurationSourceReader(snapshots),
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
    void unknownInjectionCategoryNeverTurnsIntoAnEmptyOrAutomaticallyIncludedClinicalSelection() {
        sql.update(
                "update preventions set prevention_type='OtherA' where id=? and demographic_no=?",
                first,
                patient);
        read.executeWithoutResult(
                status -> {
                    try {
                        var state = snapshots.prepare(patient, () -> {});
                        assertThat(state.selection().reviews())
                                .containsEntry(
                                        first,
                                        CdsExportPreventionScopePlanner.Review.CATEGORY_NOT_PROVEN);
                        assertThatThrownBy(state::clinicalIds)
                                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    @Test
    void readonlyAndProgramBoundariesRemainMandatoryEvenForEmptySelections() {
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
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                precision.countCdsExportPreventionRows(
                                                        patient, Set.of())))
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
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
