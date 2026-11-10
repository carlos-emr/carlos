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

/** Actual metadata-only capture, program checks before type reads, and fresh privacy revocation across a frozen snapshot. */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportPreventionScopeMariaDbIntegrationTest {
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
    private final List<Integer> patients = new ArrayList<>(), ids = new ArrayList<>();
    private PreventionDaoImpl dao;
    private CdsExportPreventionAccessService access;
    private CdsExportPreventionScopeSourceReader reader;

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
                                    Prevention.class.getName(), PreventionExt.class.getName()));
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
        context.register(PreventionDaoImpl.class, CdsExportPreventionAccessService.class);
        context.refresh();
        dao = context.getBean(PreventionDaoImpl.class);
        access = context.getBean(CdsExportPreventionAccessService.class);
        reader = new CdsExportPreventionScopeSourceReader(dao, access);
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
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (sql != null) {
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
    void shouldVerifyPinsFullPhysicalSchemaButAllowsOnlyTheExplicitMetadataAliasProjection_forPreventionScopeContract() {
        read.executeWithoutResult(
                status -> {
                    try {
                        var schema = dao.readCdsExportSourceSchema();
                        assertThat(schema.columns()).hasSize(16);
                        assertThat(reader.columns())
                                .containsExactlyInAnyOrderElementsOf(
                                        CdsExportPreventionScopePlanner.COLUMNS);
                        assertThat(reader.receiptColumns()).hasSize(6);
                        var validator = new CdsExportSourceSchemaValidator();
                        String hash =
                                validator.verify("preventions_scope", schema, reader.columns());
                        assertThat(validator.expectedSha256("preventions_scope", reader.columns()))
                                .isEqualTo(hash);
                        assertThatThrownBy(() -> validator.verify(schema, reader.columns()))
                                .isInstanceOf(IOException.class);
                        assertThatThrownBy(
                                        () ->
                                                validator.verify(
                                                        "demographic", schema, reader.columns()))
                                .isInstanceOf(IOException.class);
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
    }

    @Test
    void shouldVerifyPreservesEveryRootMetadataRecordIncludingDeletedHistory_withoutReadingClinicalCells() {
        var stats =
                context.getBean(jakarta.persistence.EntityManagerFactory.class)
                        .unwrap(org.hibernate.SessionFactory.class)
                        .getStatistics();
        stats.clear();
        read.executeWithoutResult(
                status -> {
                    assertThat(reader.count(patient)).isEqualTo(3);
                    var page = dao.findCdsExportRowsAfter(patient, null, 32);
                    assertThat(page).hasSize(3);
                    assertThat(page)
                            .allSatisfy(
                                    row ->
                                            assertThat(row.cells().keySet())
                                                    .containsExactlyInAnyOrderElementsOf(
                                                            CdsExportPreventionScopePlanner
                                                                    .COLUMNS));
                    assertThat(page.stream().map(row -> row.id()).toList())
                            .containsExactly((long) first, (long) first + 1, (long) first + 2);
                });
        assertThat(stats.getEntityLoadCount()).isZero();
    }

    @Test
    void shouldVerifyActualComposerAndIndependentReaderCarryOnlyTheSixClassificationMetadataFields_forPreventionScopeContract()
            throws Exception {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        read.executeWithoutResult(
                status -> {
                    try {
                        new CdsExportNativeCaptureComposer()
                                .compose(
                                        values,
                                        index,
                                        Set.of("preventions_scope"),
                                        1000,
                                        1L << 20,
                                        () -> {},
                                        writer -> {
                                            writer.beginTable(
                                                    reader.table(),
                                                    reader.columns(),
                                                    reader.count(patient));
                                            reader.capture(patient, writer);
                                            writer.endTable();
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
                                Map.of(
                                        "preventions_scope",
                                        new CdsExportNativeCaptureReader.Table(
                                                reader.columns(), 3)),
                                1000,
                                1L << 20,
                                () -> {});
        assertThat(proof.fields()).isEqualTo(18);
        assertThat(values.toString(StandardCharsets.UTF_8))
                .contains("H1N1", "PAP", "Td")
                .doesNotContain(
                        "2024-02-29",
                        "CLINICAL_PROVIDER_VALUE_MUST_NOT_BE_CAPTURED",
                        "provider_name",
                        "prevention_date",
                        "FOREIGN_TYPE");
    }

    @Test
    void shouldVerifyActiveUnknownAndAmbiguousProgramScopesRefuse_beforeAnyMetadataProjection() {
        for (Integer flag : Arrays.asList(1, 2, null)) {
            sql.update(
                    "update preventions set restrictToProgram=?,programNo=1 where id=? and demographic_no=?",
                    flag,
                    first,
                    patient);
            assertThatThrownBy(() -> access.requireUnrestricted(patient))
                    .isInstanceOf(CdsExportReviewException.class)
                    .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
            assertThatThrownBy(() -> read.executeWithoutResult(status -> reader.count(patient)))
                    .isInstanceOf(CdsExportReviewException.class);
            assertThatThrownBy(
                            () ->
                                    read.executeWithoutResult(
                                            status ->
                                                    dao.findCdsExportRowsAfter(patient, null, 32)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        }
    }

    @Test
    void shouldVerifyAnExplicitFalseFlagKeepsItsMeaningEven_withARecordedProgramAffiliation() {
        sql.update(
                "update preventions set restrictToProgram=0,programNo=123 where id=? and demographic_no=?",
                first,
                patient);
        assertThatCode(() -> access.requireUnrestricted(patient)).doesNotThrowAnyException();
        read.executeWithoutResult(status -> assertThat(reader.count(patient)).isEqualTo(3));
    }

    @Test
    void
            shouldVerifyFreshProgramRevocationIsVisibleEvenWhileAnOlderReadonlySnapshotStillSeesTheOriginalMetadata_forPreventionScopeContract() {
        read.executeWithoutResult(
                status -> {
                    var before = dao.findCdsExportRowsAfter(patient, null, 1).getFirst();
                    try (var separate = source.getConnection();
                            var edit =
                                    separate.prepareStatement(
                                            "update preventions set restrictToProgram=1,programNo=1,prevention_type='CHANGED_AFTER_SNAPSHOT' where id=? and demographic_no=?")) {
                        edit.setInt(1, first);
                        edit.setInt(2, patient);
                        assertThat(edit.executeUpdate()).isEqualTo(1);
                    } catch (java.sql.SQLException failed) {
                        throw new IllegalStateException(failed);
                    }
                    assertThat(dao.findCdsExportRowsAfter(patient, null, 1).getFirst())
                            .isEqualTo(before);
                    assertThatThrownBy(() -> access.requireUnrestricted(patient))
                            .isInstanceOf(CdsExportReviewException.class)
                            .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
                });
    }

    @Test
    void shouldVerifySnapshotAndFreshRestrictionReadsRequireReadonlyTransactionsAndRejectForeignChunks_forPreventionScopeContract() {
        assertThatThrownBy(() -> dao.findCdsExportRowsAfter(patient, null, 32))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                dao.readCdsExportValueChunk(
                                                        patient,
                                                        first + 3,
                                                        "prevention_type",
                                                        0,
                                                        10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                dao.readCdsExportValueChunk(
                                                        patient, first, "provider_name", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }
}
