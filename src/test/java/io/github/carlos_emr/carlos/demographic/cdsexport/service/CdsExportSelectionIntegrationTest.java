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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dto.CdsExportSelectionRowDto;
import io.github.carlos_emr.carlos.commn.model.CdsExportRun;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Executes actual scalar ORM projections and the read-only snapshot on synthetic owned data. */
@Tag("integration")
@Tag("cds-export")
class CdsExportSelectionIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    static class PagingDao extends DemographicDaoImpl {
        private Runnable afterPage;

        public void afterNextPage(Runnable callback) {
            afterPage = callback;
        }

        @Override
        public List<CdsExportSelectionRowDto> findCdsExportRosterAfter(
                List<String> providers, Integer after, int limit) {
            var result = super.findCdsExportRosterAfter(providers, after, limit);
            Runnable callback = afterPage;
            afterPage = null;
            if (callback != null) callback.run();
            return result;
        }
    }

    @TempDir Path root;
    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private CdsExportSelectionService selections;
    private CdsExportStagingService store;
    private PagingDao dao;
    private final List<Integer> owned = new ArrayList<>();
    private final int first =
            600_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000);
    private final String physician =
            "X" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);

    @BeforeEach
    void openOwnedDatabase() throws Exception {
        Properties properties = new Properties();
        String selected = System.getProperty("cds.export.test.properties");
        boolean nativeDatabase = selected != null && !selected.isBlank();
        if (nativeDatabase) {
            try (var input = Files.newInputStream(Path.of(selected))) {
                properties.load(input);
            }
            if (!properties
                            .getProperty("jdbcUrl", "")
                            .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                    || !"true".equals(properties.getProperty("ownedDisposableDatabase"))) {
                throw new IllegalArgumentException(
                        "A fresh disposable cds_export_test database is required");
            }
        }
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(
                nativeDatabase
                        ? properties.getProperty("jdbcUrl")
                        : "jdbc:h2:mem:selection_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        source.setUsername(nativeDatabase ? properties.getProperty("jdbcUser") : "sa");
        source.setPassword(nativeDatabase ? properties.getProperty("jdbcPassword") : "");
        sql = new JdbcTemplate(source);
        if (nativeDatabase)
            assertThat(sql.queryForObject("SELECT VERSION()", String.class)).contains("MariaDB");
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setDataSource(source);
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    CdsExportRun.class.getName(),
                                    Demographic.class.getName(),
                                    DemographicCust.class.getName(),
                                    io.github.carlos_emr.carlos.commn.model.DemographicExt.class
                                            .getName(),
                                    io.github.carlos_emr.carlos.commn.model.Allergy.class.getName(),
                                    io.github.carlos_emr.carlos.commn.model.PartialDate.class
                                            .getName(),
                                    io.github.carlos_emr.carlos.commn.model.Appointment.class
                                            .getName(),
                                    io.github.carlos_emr.carlos.commn.model.AppointmentArchive.class
                                            .getName(),
                                    Provider.class.getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    nativeDatabase ? "none" : "create-drop",
                                    "hibernate.dialect",
                                    nativeDatabase
                                            ? "org.hibernate.dialect.MariaDBDialect"
                                            : "org.hibernate.dialect.H2Dialect",
                                    "hibernate.jdbc.time_zone",
                                    "UTC",
                                    "hibernate.show_sql",
                                    "false"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.registerBean(
                org.springframework.cache.CacheManager.class,
                org.springframework.cache.support.NoOpCacheManager::new);
        context.registerBean("demographicDao", PagingDao.class, PagingDao::new);
        context.register(
                CdsExportRunDao.class,
                CdsExportSelectionService.class,
                io.github.carlos_emr.carlos.commn.dao.DemographicExtDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.DemographicCustDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.AllergyDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.PartialDateDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.LookupListItemDaoImpl.class,
                io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class,
                io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDaoImpl.class);
        // Sealed native-baseline witnesses need an installation key (IMP-1).
        context.registerBean(
                io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTargetReceiptService.class,
                () ->
                        new io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsTargetReceiptService(
                                io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys.keys()));
        context.refresh();
        selections = context.getBean(CdsExportSelectionService.class);
        dao = context.getBean(PagingDao.class);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        store =
                new CdsExportStagingService(
                        root,
                        UUID.randomUUID().toString(),
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        16L << 20,
                        0,
                        () -> {});
    }

    @AfterEach
    void closeOwnedResources() throws Exception {
        try {
            if (sql != null)
                for (int id : owned)
                    sql.update("delete from demographic where demographic_no=?", id);
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (source != null) source.close();
            }
        }
    }

    @Test
    void shouldIncludeEveryRosterStatus_andNoOtherPhysician() throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "IN");
        insert(first + 2, physician, "DE");
        insert(first + 3, "OTHER", "AC");
        var result = selections.capture(roster(), store, 1000, () -> {});
        assertThat(rows(result))
                .extracting(CdsExportSelectionRowDto::demographicNo)
                .containsExactly(first, first + 1, first + 2);
        assertThat(result.patients()).isEqualTo(3);
        assertThat(result.readCompletedAt()).isAfterOrEqualTo(result.readStartedAt());
        assertThat(
                        sql.queryForObject(
                                "select count(*) from demographic where provider_no=?",
                                Long.class,
                                physician))
                .isEqualTo(3);
    }

    @Test
    void shouldKeepOneMembershipSnapshot_acrossActualKeysetPagesAndAConcurrentInsert()
            throws Exception {
        for (int i = 0; i < 260; i++) insert(first + i, physician, i % 2 == 0 ? "AC" : "DE");
        try (var external = Executors.newSingleThreadExecutor()) {
            dao.afterNextPage(
                    () -> {
                        try {
                            external.submit(() -> insert(first + 500, physician, "IN"))
                                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    });
            var result = selections.capture(roster(), store, 1000, () -> {});
            assertThat(rows(result))
                    .hasSize(260)
                    .extracting(CdsExportSelectionRowDto::demographicNo)
                    .doesNotContain(first + 500);
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from demographic where provider_no=?",
                                    Long.class,
                                    physician))
                    .isEqualTo(261);
        }
    }

    @Test
    void shouldRefuseAMissingExplicitMember_insteadOfPublishingASmallerCohort() throws Exception {
        insert(first, physician, "AC");
        var command = selected(List.of(first, first + 1));
        assertThatThrownBy(() -> selections.capture(command, store, 1000, () -> {}))
                .hasMessage("CDS_EXPORT_SELECTED_PATIENT_MISSING");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldPreserveExactlyAnExplicitSelection_inSortedUniqueOrder() throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "DE");
        insert(first + 2, "OTHER", "IN");
        var result = selections.capture(selected(List.of(first + 2, first)), store, 1000, () -> {});
        assertThat(rows(result))
                .extracting(CdsExportSelectionRowDto::demographicNo)
                .containsExactly(first, first + 2);
    }

    @Test
    void shouldEraseAPartialInventory_whenTheCohortExceedsItsBound() throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "DE");
        assertThatThrownBy(() -> selections.capture(roster(), store, 1, () -> {}))
                .hasMessage("CDS_EXPORT_COHORT_LIMIT_REQUIRES_REVIEW");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldPropagateCancellation_duringDiscoveryWithoutReturningAHandle() throws Exception {
        for (int i = 0; i < 12; i++) insert(first + i, physician, "AC");
        AtomicInteger probes = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                selections.capture(
                                        roster(),
                                        store,
                                        1000,
                                        () -> {
                                            if (probes.incrementAndGet() == 8)
                                                throw new IllegalStateException(
                                                        "CDS_EXPORT_CANCELLED");
                                        }))
                .hasMessage("CDS_EXPORT_CANCELLED");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldEraseACompletedButUnpublishedInventory_whenTheFinalCheckpointRefusesIt()
            throws Exception {
        insert(first, physician, "AC");
        AtomicInteger probes = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                selections.capture(
                                        roster(),
                                        store,
                                        1000,
                                        () -> {
                                            if (probes.incrementAndGet() == 4)
                                                throw new IllegalStateException(
                                                        "CDS_EXPORT_CANCELLED");
                                        }))
                .hasMessage("CDS_EXPORT_CANCELLED");
        try (var files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldCaptureEveryRawDemographicColumn_withoutInventingMissingDatesOrFalseFlags() {
        insert(first, physician, "IN");
        String fullName = "A".repeat(49) + "é";
        sql.update(
                "update demographic set first_name=?,last_name=?,year_of_birth='1952',month_of_birth=NULL,date_of_birth=NULL,"
                        + "consentToUseEmailForCare=NULL,hin='12345 67890',hc_type='ON',email='' where demographic_no=?",
                fullName,
                "Zoë 👩🏽‍⚕️",
                first);
        var captured = sourceRead().execute(status -> dao.findCdsExportRawDemographic(first));
        assertThat(captured.demographicNo()).isEqualTo(first);
        assertThat(captured.fields())
                .hasSize(59)
                .containsEntry("first_name", fullName)
                .containsEntry("last_name", "Zoë 👩🏽‍⚕️")
                .containsEntry("year_of_birth", "1952")
                .containsEntry("month_of_birth", null)
                .containsEntry("date_of_birth", null)
                .containsEntry("consentToUseEmailForCare", null)
                .containsEntry("email", "")
                .containsEntry("patient_status", "IN")
                .containsEntry("hin", "12345 67890")
                .containsEntry("cds_identity_hin", "1234567890")
                .containsEntry("cds_identity_issuer", "ON");
        assertThatThrownBy(() -> captured.fields().put("email", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        sql.update(
                "update demographic set consentToUseEmailForCare=0 where demographic_no=?", first);
        assertThat(sourceRead().execute(status -> dao.findCdsExportRawDemographic(first)).fields())
                .containsEntry("consentToUseEmailForCare", "0");
        assertThat(captured.fields()).containsEntry("consentToUseEmailForCare", null);
        var missing = sourceRead().execute(status -> dao.findCdsExportRawDemographic(first + 1));
        assertThat(missing).isNull();
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldKeepOneRawPatientSnapshot_whenAnotherConnectionChangesItsClinicalScalars()
            throws Exception {
        insert(first, physician, "AC");
        try (var external = Executors.newSingleThreadExecutor()) {
            sourceRead()
                    .execute(
                            status -> {
                                var initial = dao.findCdsExportRawDemographic(first);
                                try {
                                    external.submit(
                                                    () ->
                                                            sql.update(
                                                                    "update demographic set first_name='Changed' where demographic_no=?",
                                                                    first))
                                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
                                } catch (Exception failed) {
                                    throw new IllegalStateException(failed);
                                }
                                assertThat(dao.findCdsExportRawDemographic(first))
                                        .isEqualTo(initial);
                                return null;
                            });
        }
        assertThat(sourceRead().execute(status -> dao.findCdsExportRawDemographic(first)).fields())
                .containsEntry("first_name", "Changed");
    }

    @Test
    void shouldRefuseRawCapture_withoutAnExplicitReadOnlySourceTransaction() {
        assertThatThrownBy(() -> dao.findCdsExportRawDemographic(first))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
        var write =
                new org.springframework.transaction.support.TransactionTemplate(
                        context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> write.execute(status -> dao.findCdsExportRawDemographic(first)))
                .hasMessage("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldVerifyActualPinnedNativeSchema_beforeCapturingEveryDeclaredColumn()
            throws Exception {
        insert(first, physician, "IN");
        var reader = new CdsExportDemographicSourceReader(dao);
        var values = new java.io.ByteArrayOutputStream();
        var index = new java.io.ByteArrayOutputStream();
        sourceRead()
                .execute(
                        status -> {
                            try {
                                var schema = reader.schema();
                                assertThat(
                                                new CdsExportSourceSchemaValidator()
                                                        .verify(schema, reader.columns()))
                                        .matches("[0-9a-f]{64}");
                                assertThat(schema.columns()).hasSize(60);
                                assertThat(reader.count(first)).isEqualTo(1);
                                new CdsExportNativeCaptureComposer()
                                        .compose(
                                                values,
                                                index,
                                                java.util.Set.of("demographic"),
                                                100,
                                                1L << 20,
                                                () -> {},
                                                writer -> {
                                                    writer.beginTable(
                                                            reader.table(), reader.columns(), 1);
                                                    reader.capture(first, writer);
                                                    writer.endTable();
                                                });
                                return null;
                            } catch (java.io.IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        });
        var verified =
                new CdsExportNativeCaptureReader()
                        .verify(
                                new java.io.ByteArrayInputStream(values.toByteArray()),
                                new java.io.ByteArrayInputStream(index.toByteArray()),
                                Map.of(
                                        "demographic",
                                        new CdsExportNativeCaptureReader.Table(
                                                reader.columns(), 1)),
                                100,
                                1L << 20,
                                () -> {});
        assertThat(verified.fields()).isEqualTo(60);
        assertThat(verified.rows()).containsEntry("demographic", 1L);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldCaptureAllAdjunctRowsAcrossKeysetPages_withIndependentSqlHashesAndBoundedLargeText()
            throws Exception {
        insert(first, physician, "AC");
        String large = ("FAKE-CDS é 😀\r\n").repeat(18000);
        try {
            for (int index = 0; index < 34; index++) {
                int id = index == 0 ? -first : first + index;
                sql.update(
                        "insert into demographicExt(id,demographic_no,provider_no,key_val,value,hidden) values(?,?,?,?,?,?)",
                        id,
                        first,
                        physician,
                        "raw_capture_" + index,
                        index == 0 ? large : index == 1 ? null : index == 2 ? "" : "0",
                        index % 2 == 0 ? null : "0");
            }
            sql.update(
                    "insert into demographiccust(demographic_no,content,cust1,cust2,cust3,cust4) values(?,?,NULL,'','0',NULL)",
                    first,
                    large);
            var ext =
                    context.getBean(io.github.carlos_emr.carlos.commn.dao.DemographicExtDao.class);
            var cust =
                    context.getBean(io.github.carlos_emr.carlos.commn.dao.DemographicCustDao.class);
            var readers =
                    List.of(
                            new CdsExportDemographicExtSourceReader(ext),
                            new CdsExportDemographicCustSourceReader(cust));
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    for (var reader : readers)
                                        assertThat(
                                                        new CdsExportSourceSchemaValidator()
                                                                .verify(
                                                                        reader.schema(),
                                                                        reader.columns()))
                                                .matches("[0-9a-f]{64}");
                                    assertThat(ext.countCdsExportRows(first)).isEqualTo(34);
                                    assertThat(cust.countCdsExportRows(first)).isEqualTo(1);
                                    var projected =
                                            cust.findCdsExportDemographicRowsAfter(first, null, 1)
                                                    .getFirst();
                                    assertThat(projected.cells())
                                            .doesNotContainKey("cust3")
                                            .containsKey("content");
                                    assertThat(
                                                    cust.findCdsExportRowsAfter(first, null, 1)
                                                            .getFirst()
                                                            .cells())
                                            .containsKey("cust3");
                                    var page = ext.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(page).hasSize(32);
                                    assertThat(page.getFirst().id()).isEqualTo(-first);
                                    assertThat(page.getFirst().cells().get("value").inlineBytes())
                                            .isNull();
                                    assertThat(page.getFirst().cells().get("value").bytes())
                                            .isEqualTo(
                                                    large.getBytes(StandardCharsets.UTF_8).length);
                                    assertThat(page.getFirst().cells().get("value").sha256())
                                            .isEqualTo(
                                                    java.util.HexFormat.of()
                                                            .formatHex(
                                                                    java.security.MessageDigest
                                                                            .getInstance("SHA-256")
                                                                            .digest(
                                                                                    large.getBytes(
                                                                                            StandardCharsets
                                                                                                    .UTF_8))));
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of(
                                                            "demographicExt", "demographiccust"),
                                                    1000,
                                                    8L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        for (var reader : readers) {
                                                            writer.beginTable(
                                                                    reader.table(),
                                                                    reader.columns(),
                                                                    reader.count(first));
                                                            reader.capture(first, writer);
                                                            writer.endTable();
                                                        }
                                                    });
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var proof =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "demographicExt",
                                                    new CdsExportNativeCaptureReader.Table(
                                                            java.util.Set.copyOf(
                                                                    io.github.carlos_emr.carlos
                                                                            .commn.dao
                                                                            .CdsExportPatientTableReadSupport
                                                                            .Plan.DEMOGRAPHIC_EXT
                                                                            .columns()),
                                                            34),
                                            "demographiccust",
                                                    new CdsExportNativeCaptureReader.Table(
                                                            java.util.Set.copyOf(
                                                                    io.github.carlos_emr.carlos
                                                                            .commn.dao
                                                                            .CdsExportPatientTableReadSupport
                                                                            .Plan.DEMOGRAPHIC_CUST
                                                                            .columns()),
                                                            1)),
                                    1000,
                                    8L << 20,
                                    () -> {});
            assertThat(proof.fields()).isEqualTo(34L * 8 + 6);
            assertThat(values.toString(StandardCharsets.UTF_8)).contains(large);
        } finally {
            sql.update("delete from demographicExt where demographic_no=?", first);
            sql.update("delete from demographiccust where demographic_no=?", first);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldKeepLargeValueRangesInOneSnapshot_whenAnotherConnectionChangesTheirBytes()
            throws Exception {
        insert(first, physician, "AC");
        String original = "FAKE-CDS 😀".repeat(10000);
        try {
            sql.update(
                    "insert into demographiccust(demographic_no,content) values(?,?)",
                    first,
                    original);
            var source =
                    context.getBean(io.github.carlos_emr.carlos.commn.dao.DemographicCustDao.class);
            try (var external = Executors.newSingleThreadExecutor()) {
                sourceRead()
                        .execute(
                                status -> {
                                    var row =
                                            source.findCdsExportRowsAfter(first, null, 1)
                                                    .getFirst();
                                    try {
                                        external.submit(
                                                        () ->
                                                                sql.update(
                                                                        "update demographiccust set content='ChangedAfterHeader' where demographic_no=?",
                                                                        first))
                                                .get(5, java.util.concurrent.TimeUnit.SECONDS);
                                        try (var value =
                                                new CdsExportRawValueInputStream(
                                                        source,
                                                        first,
                                                        first,
                                                        "content",
                                                        row.cells().get("content"))) {
                                            assertThat(
                                                            new String(
                                                                    value.readAllBytes(),
                                                                    StandardCharsets.UTF_8))
                                                    .isEqualTo(original);
                                        }
                                    } catch (Exception failure) {
                                        throw new IllegalStateException(failure);
                                    }
                                    return null;
                                });
            }
            assertThat(
                            sql.queryForObject(
                                    "select content from demographiccust where demographic_no=?",
                                    String.class,
                                    first))
                    .isEqualTo("ChangedAfterHeader");
            assertThatThrownBy(() -> source.readCdsExportValueChunk(first, first, "content", 0, 1))
                    .isInstanceOf(
                            org.springframework.transaction.IllegalTransactionStateException.class);
        } finally {
            sql.update("delete from demographiccust where demographic_no=?", first);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void
            shouldCaptureAllAllergyRowsIncludingArchivedEntries_withoutTrimmingTextOrDefaultingNullableFlags()
                    throws Exception {
        insert(first, physician, "IN");
        String reaction = ("FAKE-CDS é 😀\r\n").repeat(18000);
        try {
            for (int row = 0; row < 34; row++) {
                sql.update(
                        "insert into allergies(allergyid,demographic_no,DESCRIPTION,reaction,archived,position,lastUpdateDate,nonDrug,entry_date,start_date) "
                                + "values(?,?,?,?,?,?,CURRENT_TIMESTAMP,?,'2024-02-29',NULL)",
                        row == 0 ? -first : first + row,
                        first,
                        "FAKE-CDS allergy 😀  ",
                        row == 0 ? reaction : row == 1 ? null : row == 2 ? "" : "0",
                        row % 2,
                        row,
                        row == 0 ? null : 0);
            }
            var allergies = context.getBean(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class);
            var reader = new CdsExportAllergySourceReader(allergies);
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    assertThat(
                                                    new CdsExportSourceSchemaValidator()
                                                            .verify(
                                                                    reader.schema(),
                                                                    reader.columns()))
                                            .matches("[0-9a-f]{64}");
                                    assertThat(allergies.countCdsExportRows(first)).isEqualTo(34);
                                    var page = allergies.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(page).hasSize(32);
                                    assertThat(
                                                    allergies.findCdsExportRowsAfter(
                                                            first, page.getLast().id(), 32))
                                            .hasSize(2);
                                    assertThat(page.getFirst().id()).isEqualTo(-first);
                                    assertThat(
                                                    page.getFirst()
                                                            .cells()
                                                            .get("reaction")
                                                            .inlineBytes())
                                            .isNull();
                                    assertThat(page.getFirst().cells().get("reaction").sha256())
                                            .isEqualTo(
                                                    java.util.HexFormat.of()
                                                            .formatHex(
                                                                    java.security.MessageDigest
                                                                            .getInstance("SHA-256")
                                                                            .digest(
                                                                                    reaction
                                                                                            .getBytes(
                                                                                                    StandardCharsets
                                                                                                            .UTF_8))));
                                    assertThat(page.getFirst().cells().get("nonDrug").sqlNull())
                                            .isTrue();
                                    assertThat(page.get(1).cells().get("nonDrug").inlineBytes())
                                            .isEqualTo("0".getBytes(StandardCharsets.UTF_8));
                                    assertThat(page.get(1).cells().get("archived").inlineBytes())
                                            .isEqualTo("1".getBytes(StandardCharsets.UTF_8));
                                    assertThat(
                                                    page.getFirst()
                                                            .cells()
                                                            .get("DESCRIPTION")
                                                            .inlineBytes())
                                            .isEqualTo(
                                                    "FAKE-CDS allergy 😀  "
                                                            .getBytes(StandardCharsets.UTF_8));
                                    assertThat(
                                                    page.getFirst()
                                                            .cells()
                                                            .get("entry_date")
                                                            .inlineBytes())
                                            .isEqualTo(
                                                    "2024-02-29".getBytes(StandardCharsets.UTF_8));
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of("allergies"),
                                                    1000,
                                                    8L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                reader.count(first));
                                                        reader.capture(first, writer);
                                                        writer.endTable();
                                                    });
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var checked =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "allergies",
                                            new CdsExportNativeCaptureReader.Table(
                                                    reader.columns(), 34)),
                                    1000,
                                    8L << 20,
                                    () -> {});
            assertThat(checked.fields()).isEqualTo(34L * 24);
            assertThat(values.toString(StandardCharsets.UTF_8)).contains(reaction);
        } finally {
            sql.update("delete from allergies where demographic_no=?", first);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void
            shouldCaptureOnlyPatientOwnedAllergyPrecisionRows_andRetainDuplicateAndUnknownFormatsForReview()
                    throws Exception {
        insert(first, physician, "IN");
        insert(first + 1, physician, "AC");
        int allergy = first + 100, otherAllergy = first + 101;
        try {
            sql.update(
                    "insert into allergies(allergyid,demographic_no,DESCRIPTION,archived,position,lastUpdateDate,start_date) "
                            + "values(?,?,'FAKE-CDS precision',1,0,CURRENT_TIMESTAMP,'1965-01-01'),(?,?,'FAKE-CDS other patient',0,0,CURRENT_TIMESTAMP,NULL)",
                    allergy,
                    first,
                    otherAllergy,
                    first + 1);
            sql.update(
                    "insert into partial_date(id,table_name,table_id,field_name,format) values(?,1,?,1,'YYYY'),(?,1,?,2,'YYYY-MM'),"
                            + "(?,1,?,1,NULL),(?,2,?,1,'YYYY'),(?,1,?,1,'YYYY'),(?,1,?,1,'YYYY')",
                    -first,
                    allergy,
                    first + 1,
                    allergy,
                    first + 2,
                    allergy,
                    first + 3,
                    allergy,
                    first + 4,
                    otherAllergy,
                    first + 5,
                    otherAllergy + 1);
            var dates = context.getBean(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class);
            var reader = new CdsExportPartialDateSourceReader(dates);
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    assertThat(
                                                    new CdsExportSourceSchemaValidator()
                                                            .verify(
                                                                    reader.schema(),
                                                                    reader.columns()))
                                            .matches("[0-9a-f]{64}");
                                    assertThat(dates.countCdsExportRows(first)).isEqualTo(3);
                                    var rows = dates.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(rows).hasSize(3);
                                    assertThat(rows.getFirst().cells().get("format").inlineBytes())
                                            .isEqualTo("YYYY".getBytes(StandardCharsets.UTF_8));
                                    assertThat(rows.get(1).cells().get("format").inlineBytes())
                                            .isEqualTo("YYYY-MM".getBytes(StandardCharsets.UTF_8));
                                    assertThat(rows.get(2).cells().get("format").sqlNull())
                                            .isTrue();
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of("partial_date"),
                                                    100,
                                                    1L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                reader.count(first));
                                                        reader.capture(first, writer);
                                                        writer.endTable();
                                                    });
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var checked =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "partial_date",
                                            new CdsExportNativeCaptureReader.Table(
                                                    reader.columns(), 3)),
                                    100,
                                    1L << 20,
                                    () -> {});
            assertThat(checked.fields()).isEqualTo(15);
            assertThatThrownBy(
                            () ->
                                    sourceRead()
                                            .execute(
                                                    status ->
                                                            dates.readCdsExportValueChunk(
                                                                    first, first + 4, "format", 0,
                                                                    4)))
                    .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
        } finally {
            sql.update(
                    "delete from partial_date where id in (?,?,?,?,?,?)",
                    -first,
                    first + 1,
                    first + 2,
                    first + 3,
                    first + 4,
                    first + 5);
            sql.update("delete from allergies where allergyid in (?,?)", allergy, otherAllergy);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldCaptureAllAppointmentScalarsAcrossPages_withoutUiDefaultsOrAnotherPatientsRows()
            throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "IN");
        var ids = new ArrayList<Integer>();
        String note = "FAKE-CDS café 😀\r\n".repeat(10000);
        try {
            for (int i = 0; i < 35; i++) {
                int id = -first + i, owner = i == 34 ? first + 1 : first;
                sql.update(
                        "insert into appointment(appointment_no,demographic_no,provider_no,appointment_date,start_time,end_time,name,notes,status,imported_status,reason,createdatetime) "
                                + "values(?,?,?,'2024-02-29','23:59:30','00:00:30','FAKE-CDS appointment',?,?,?,'Exact reason',NULL)",
                        id,
                        owner,
                        physician,
                        i == 0 ? note : "",
                        i == 0 ? null : i == 1 ? "" : "XX",
                        i == 2 ? "Original imported status" : null);
                ids.add(id);
            }
            var source =
                    context.getBean(
                            io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao.class);
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    new CdsExportSourceSchemaValidator()
                                            .verify(
                                                    source.readCdsExportSourceSchema(),
                                                    java.util.Set.copyOf(
                                                            io.github.carlos_emr.carlos.commn.dao
                                                                    .CdsExportPatientTableReadSupport
                                                                    .Plan.APPOINTMENTS
                                                                    .columns()));
                                    assertThat(source.countCdsExportRows(first)).isEqualTo(34);
                                    var page = source.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(page).hasSize(32);
                                    assertThat(page.getFirst().id()).isEqualTo(-first);
                                    assertThat(page.getFirst().cells().get("status").sqlNull())
                                            .isTrue();
                                    assertThat(page.get(1).cells().get("status").sqlNull())
                                            .isFalse();
                                    assertThat(page.get(1).cells().get("status").bytes()).isZero();
                                    assertThat(
                                                    source.findCdsExportRowsAfter(
                                                            first, page.getLast().id(), 32))
                                            .hasSize(2);
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of("appointment"),
                                                    1000,
                                                    8L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        var reader =
                                                                new CdsExportAppointmentSourceReader(
                                                                        source);
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                34);
                                                        reader.capture(first, writer);
                                                        writer.endTable();
                                                    });
                                    assertThatThrownBy(
                                                    () ->
                                                            source.readCdsExportValueChunk(
                                                                    first,
                                                                    -first + 34,
                                                                    "notes",
                                                                    0,
                                                                    1))
                                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                                    status
                                            .setRollbackOnly(); // Expected scope refusal poisons
                                                                // this read transaction.
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var proof =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "appointment",
                                            new CdsExportNativeCaptureReader.Table(
                                                    java.util.Set.copyOf(
                                                            io.github.carlos_emr.carlos.commn.dao
                                                                    .CdsExportPatientTableReadSupport
                                                                    .Plan.APPOINTMENTS
                                                                    .columns()),
                                                    34)),
                                    1000,
                                    8L << 20,
                                    () -> {});
            assertThat(proof.fields()).isEqualTo(34L * 26);
            assertThat(values.toString(StandardCharsets.UTF_8))
                    .contains(
                            note, "23:59:30", "00:00:30", "2024-02-29", "Original imported status");
        } finally {
            for (int id : ids)
                sql.update(
                        "delete from appointment where appointment_no=? and demographic_no in (?,?)",
                        id,
                        first,
                        first + 1);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldCaptureExactReferencedStatusDefinitions_includingInactiveAndDuplicateCodes()
            throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "IN");
        String code = "Ω";
        String other = "λ";
        assertThat(
                        sql.queryForObject(
                                "select count(*) from appointment_status where status in (?,?)",
                                Long.class,
                                code,
                                other))
                .isZero();
        try {
            sql.update(
                    "insert into appointment_status(id,status,description,active) values(?,?,?,0),(?,?,?,1),(?,?,?,1)",
                    -first,
                    code,
                    "Exact inactive Ω",
                    -first + 1,
                    code,
                    "Conflicting definition",
                    -first + 2,
                    other,
                    "Other patient only");
            sql.update(
                    "insert into appointment(appointment_no,demographic_no,provider_no,appointment_date,start_time,end_time,status) "
                            + "values(?,?,?,'2024-02-29','10:00:00','10:30:00',?),(?,?,?,'2024-02-29','10:00:00','10:30:00',?)",
                    -first,
                    first,
                    physician,
                    code,
                    -first + 1,
                    first + 1,
                    physician,
                    other);
            var source =
                    context.getBean(
                            io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao.class);
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    var reader = new CdsExportAppointmentStatusSourceReader(source);
                                    new CdsExportSourceSchemaValidator()
                                            .verify(reader.schema(), reader.columns());
                                    assertThat(reader.count(first)).isEqualTo(2);
                                    var page = source.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(page).hasSize(2);
                                    assertThat(
                                                    new String(
                                                            page.getFirst()
                                                                    .cells()
                                                                    .get("status")
                                                                    .inlineBytes(),
                                                            StandardCharsets.UTF_8))
                                            .isEqualTo(code);
                                    assertThat(
                                                    new String(
                                                            page.getFirst()
                                                                    .cells()
                                                                    .get("active")
                                                                    .inlineBytes(),
                                                            StandardCharsets.UTF_8))
                                            .isEqualTo("0");
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of("appointment_status"),
                                                    100,
                                                    1L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                2);
                                                        reader.capture(first, writer);
                                                        writer.endTable();
                                                    });
                                    assertThatThrownBy(
                                                    () ->
                                                            source.readCdsExportValueChunk(
                                                                    first,
                                                                    -first + 2,
                                                                    "description",
                                                                    0,
                                                                    1))
                                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                                    status.setRollbackOnly();
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var verified =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "appointment_status",
                                            new CdsExportNativeCaptureReader.Table(
                                                    java.util.Set.copyOf(
                                                            io.github.carlos_emr.carlos.commn.dao
                                                                    .CdsExportPatientTableReadSupport
                                                                    .Plan.APPOINTMENT_STATUSES
                                                                    .columns()),
                                                    2)),
                                    100,
                                    1L << 20,
                                    () -> {});
            assertThat(verified.fields()).isEqualTo(18);
            assertThat(values.toString(StandardCharsets.UTF_8))
                    .contains("Exact inactive Ω", "Conflicting definition")
                    .doesNotContain("Other patient only");
        } finally {
            sql.update(
                    "delete from appointment where appointment_no in (?,?) and demographic_no in (?,?)",
                    -first,
                    -first + 1,
                    first,
                    first + 1);
            sql.update(
                    "delete from appointment_status where id in (?,?,?) and status in (?,?)",
                    -first,
                    -first + 1,
                    -first + 2,
                    code,
                    other);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void shouldCaptureOnlyReferencedReasonListItems_forTheSelectedPatientsAppointments()
            throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "IN");
        int list = -first, other = -first + 1;
        int item = -first, otherList = -first + 1, otherPatient = -first + 2, unused = -first + 3;
        try {
            sql.update(
                    "insert into LookupList(id,name,active,createdBy) values(?,'reasonCode',1,'cds'),"
                            + "(?,'otherList',1,'cds')",
                    list,
                    other);
            sql.update(
                    "insert into LookupListItem(id,lookupListId,value,label,displayOrder,active,createdBy)"
                            + " values(?,?,'FU','Follow-Up Ω',1,0,'cds'),(?,?,'X','Other list',1,1,'cds'),"
                            + "(?,?,'P','Other patient',2,1,'cds'),(?,?,'U','Unreferenced',3,1,'cds')",
                    item,
                    list,
                    otherList,
                    other,
                    otherPatient,
                    list,
                    unused,
                    list);
            sql.update(
                    "insert into appointment(appointment_no,demographic_no,provider_no,appointment_date,start_time,end_time,reasonCode) "
                            + "values(?,?,?,'2024-02-29','10:00:00','10:14:00',?),"
                            + "(?,?,?,'2024-02-29','11:00:00','11:14:00',?),"
                            + "(?,?,?,'2024-02-29','12:00:00','12:14:00',?)",
                    -first,
                    first,
                    physician,
                    item,
                    -first + 1,
                    first,
                    physician,
                    otherList,
                    -first + 2,
                    first + 1,
                    physician,
                    otherPatient);
            var dao =
                    context.getBean(io.github.carlos_emr.carlos.commn.dao.LookupListItemDao.class);
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    var reader = new CdsExportAppointmentReasonCodeSourceReader(dao);
                                    new CdsExportSourceSchemaValidator()
                                            .verify(reader.schema(), reader.columns());
                                    assertThat(reader.count(first)).isEqualTo(1);
                                    var page = dao.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(page).hasSize(1);
                                    assertThat(page.getFirst().id()).isEqualTo(item);
                                    assertThat(
                                                    new String(
                                                            page.getFirst()
                                                                    .cells()
                                                                    .get("label")
                                                                    .inlineBytes(),
                                                            StandardCharsets.UTF_8))
                                            .isEqualTo("Follow-Up Ω");
                                    assertThatThrownBy(
                                                    () ->
                                                            dao.readCdsExportValueChunk(
                                                                    first, unused, "label", 0, 1))
                                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                                    status.setRollbackOnly();
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
        } finally {
            sql.update(
                    "delete from appointment where appointment_no in (?,?,?) and demographic_no in (?,?)",
                    -first,
                    -first + 1,
                    -first + 2,
                    first,
                    first + 1);
            sql.update(
                    "delete from LookupListItem where id in (?,?,?,?) and createdBy='cds'",
                    item,
                    otherList,
                    otherPatient,
                    unused);
            sql.update("delete from LookupList where id in (?,?) and createdBy='cds'", list, other);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void
            shouldCaptureOnlyPublicProviderFields_andOnlyTheSelectedPatientReferencesWithBinaryTextPaging()
                    throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "IN");
        var providers = new ArrayList<String>();
        var appointments = new ArrayList<Integer>();
        String prefix = "P" + Integer.toString(first, 36).substring(0, 2);
        try {
            for (int i = 0; i < 37; i++) {
                String id =
                        prefix
                                + (i == 33
                                        ? "é33"
                                        : String.format(java.util.Locale.ROOT, "%03d", i));
                providers.add(id);
                sql.update(
                        "insert into provider(provider_no,first_name,last_name,ohip_no,comments,billing_no,dob,lastUpdateDate) values(?,?,?,?,?,'999999','1900-01-01',CURRENT_TIMESTAMP)",
                        id,
                        i == 0 ? "Élodie" : "First" + i,
                        "Exact Last",
                        i == 1 ? null : i == 2 ? "" : "001234",
                        "PRIVATE PROVIDER VALUE MUST NOT LEAVE TABLE");
                if (i < 35) {
                    int appointment = -first + i;
                    appointments.add(appointment);
                    sql.update(
                            "insert into appointment(appointment_no,demographic_no,provider_no,appointment_date,start_time,end_time) "
                                    + "values(?,?,?,'2024-02-29','10:00:00','10:30:00')",
                            appointment,
                            i == 34 ? first + 1 : first,
                            id);
                }
            }
            sql.update(
                    "update demographic set provider_no=? where demographic_no=?",
                    providers.get(35),
                    first);
            sql.update(
                    "insert into appointmentArchive(id,demographic_no,provider_no) values(?,?,?)",
                    -first + 100,
                    first,
                    providers.get(36));
            var source =
                    context.getBean(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class);
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            status -> {
                                try {
                                    var base = selected(List.of(first));
                                    var command =
                                            new CdsExportCommand(
                                                    base.submissionKey(),
                                                    base.providerNo(),
                                                    base.facilityId(),
                                                    base.profile(),
                                                    base.selection(),
                                                    base.physicianNos(),
                                                    base.patientNos(),
                                                    java.util.Set.of(
                                                            CdsCategory.DEMOGRAPHICS,
                                                            CdsCategory.APPOINTMENTS),
                                                    base.recipientId(),
                                                    base.auditPlanId());
                                    var reader =
                                            new CdsExportPublicProviderSourceReader(source)
                                                    .forRequest(command);
                                    new CdsExportSourceSchemaValidator()
                                            .verify(reader.schema(), reader.columns());
                                    assertThat(
                                                    source.countCdsExportPublicProviders(
                                                            first,
                                                            java.util.Set.of("Appointments")))
                                            .isEqualTo(35);
                                    assertThat(
                                                    source.countCdsExportPublicProviders(
                                                            first,
                                                            java.util.Set.of("Demographics")))
                                            .isEqualTo(1);
                                    assertThat(
                                                    source.countCdsExportPublicProviders(
                                                            first,
                                                            java.util.Set.of(
                                                                    "AllergiesAndAdverseReactions")))
                                            .isZero();
                                    var page =
                                            source.findCdsExportPublicProvidersAfter(
                                                    first,
                                                    java.util.Set.of("Appointments"),
                                                    null,
                                                    32);
                                    assertThat(page).hasSize(32);
                                    assertThat(page.getFirst().id())
                                            .isEqualTo(providers.getFirst());
                                    assertThat(page.get(1).cells().get("ohip_no").sqlNull())
                                            .isTrue();
                                    assertThat(page.get(2).cells().get("ohip_no").sqlNull())
                                            .isFalse();
                                    assertThat(page.get(2).cells().get("ohip_no").bytes()).isZero();
                                    assertThat(
                                                    source.findCdsExportPublicProvidersAfter(
                                                            first,
                                                            java.util.Set.of("Appointments"),
                                                            CdsExportPublicProviderSourceReader.key(
                                                                    page.getLast().id()),
                                                            32))
                                            .hasSize(3);
                                    assertThat(reader.count(first)).isEqualTo(36);
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of("provider"),
                                                    1000,
                                                    1L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                36);
                                                        reader.capture(first, writer);
                                                        writer.endTable();
                                                    });
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var verified =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "provider",
                                            new CdsExportNativeCaptureReader.Table(
                                                    io.github.carlos_emr.carlos.commn.dto
                                                            .CdsExportProviderRowDto.COLUMNS,
                                                    36)),
                                    1000,
                                    1L << 20,
                                    () -> {});
            assertThat(verified.fields()).isEqualTo(216);
            assertThat(values.toString(StandardCharsets.UTF_8))
                    .contains("Élodie", "001234")
                    .doesNotContain("PRIVATE PROVIDER", "999999", "1900-01-01", providers.get(34));
        } finally {
            sql.update(
                    "delete from appointmentArchive where id=? and demographic_no=?",
                    -first + 100,
                    first);
            for (int id : appointments)
                sql.update(
                        "delete from appointment where appointment_no=? and demographic_no in (?,?)",
                        id,
                        first,
                        first + 1);
            for (String id : providers) sql.update("delete from provider where provider_no=?", id);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void
            shouldVerifyAnExistingImporterReceipt_fromCapturedValuesAndActualDriverMetadataWithoutChangingItsProtocol()
                    throws Exception {
        insert(first, physician, "AC");
        var source = context.getBean(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class);
        var extensions =
                context.getBean(io.github.carlos_emr.carlos.commn.dao.DemographicExtDao.class);
        var receipts =
                context.getBean(
                        io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsTargetReceiptService.class);
        try {
            sql.update(
                    "insert into allergies(allergyid,demographic_no,DESCRIPTION,reaction,lastUpdateDate,archived,position) values(?,?,?,NULL,CURRENT_TIMESTAMP,0,0)",
                    -first,
                    first,
                    "Exact receipt café 😀");
            sql.update(
                    "insert into demographicExt(id,demographic_no,provider_no,key_val,value,hidden) values(?,?,?,'receipt_fixture','Exact receipt extension','0')",
                    -first,
                    first,
                    physician);
            var receipt =
                    sourceRead()
                            .execute(
                                    status -> {
                                        try {
                                            var columns = source.readCdsExportReceiptColumns();
                                            assertThat(columns).hasSize(24);
                                            assertThat(columns.getFirst().name())
                                                    .isEqualTo("allergyid");
                                            var row =
                                                    source.findCdsExportRowsAfter(first, null, 1)
                                                            .getFirst();
                                            var hash =
                                                    io.github.carlos_emr.carlos.demographic.cds
                                                            .contract.CdsNativeRowDigest.hasher(
                                                            columns);
                                            for (var column : columns) {
                                                var cell = row.cells().get(column.name());
                                                hash.append(
                                                        cell.sqlNull(),
                                                        cell.bytes(),
                                                        cell.sha256());
                                            }
                                            var extensionColumns =
                                                    extensions.readCdsExportReceiptColumns();
                                            assertThat(extensionColumns).hasSize(7);
                                            assertThat(extensionColumns)
                                                    .noneMatch(
                                                            column ->
                                                                    column.name()
                                                                            .equals(
                                                                                    "dashboard_single_value_key"));
                                            var extension =
                                                    extensions
                                                            .findCdsExportRowsAfter(first, null, 1)
                                                            .getFirst();
                                            assertThat(extension.cells())
                                                    .hasSize(8)
                                                    .containsKey("dashboard_single_value_key");
                                            var extensionHash =
                                                    io.github.carlos_emr.carlos.demographic.cds
                                                            .contract.CdsNativeRowDigest.hasher(
                                                            extensionColumns);
                                            for (var column : extensionColumns) {
                                                var cell = extension.cells().get(column.name());
                                                extensionHash.append(
                                                        cell.sqlNull(),
                                                        cell.bytes(),
                                                        cell.sha256());
                                            }
                                            String document =
                                                    new ObjectMapper()
                                                            .writeValueAsString(
                                                                    Map.of(
                                                                            "protocol",
                                                                            2,
                                                                            "refs",
                                                                            List.of(
                                                                                    Map.of(
                                                                                            "table",
                                                                                            "allergies",
                                                                                            "id",
                                                                                            Integer
                                                                                                    .toString(
                                                                                                            -first),
                                                                                            "digest",
                                                                                            hash
                                                                                                    .finish()),
                                                                                    Map.of(
                                                                                            "table",
                                                                                            "demographicExt",
                                                                                            "id",
                                                                                            Integer
                                                                                                    .toString(
                                                                                                            -first),
                                                                                            "digest",
                                                                                            extensionHash
                                                                                                    .finish()))));
                                            var proof =
                                                    new io.github.carlos_emr.carlos.demographic
                                                            .cdsimport.dto.CdsImportReceiptDto(
                                                            document,
                                                            java.util.HexFormat.of()
                                                                    .formatHex(
                                                                            io.github.carlos_emr
                                                                                    .carlos.utility
                                                                                    .CdsImportKeys
                                                                                    .digest()
                                                                                    .digest(
                                                                                            document
                                                                                                    .getBytes(
                                                                                                            StandardCharsets
                                                                                                                    .UTF_8))));
                                            assertThat(receipts.verify(proof)).isTrue();
                                            return proof;
                                        } catch (Exception failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    });
            sql.update(
                    "update allergies set reaction='' where allergyid=? and demographic_no=?",
                    -first,
                    first);
            Boolean unchanged = sourceRead().execute(status -> receipts.verify(receipt));
            assertThat(unchanged).isFalse();
            var mapper = new ObjectMapper();
            var old =
                    mapper.readValue(
                            "{\"columns\":[\"id\"],\"rows\":0,\"schemaSha256\":\""
                                    + "a".repeat(64)
                                    + "\"}",
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportCapturedTableDto.class);
            assertThat(old.receiptColumns()).isEmpty();
        } finally {
            sql.update("delete from demographicExt where id=? and demographic_no=?", -first, first);
            sql.update(
                    "delete from allergies where allergyid=? and demographic_no=?", -first, first);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
            named = "cds.export.test.properties",
            matches = ".+")
    void
            shouldCaptureEveryArchiveVersion_withoutCollapsingBookingIdsOrDroppingHistoricalStatusDefinitions()
                    throws Exception {
        insert(first, physician, "AC");
        insert(first + 1, physician, "IN");
        var ids = new ArrayList<Integer>();
        String notes = "FAKE-CDS historical café 😀\r\n".repeat(10000);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from appointment_status where status='β'",
                                Long.class))
                .isZero();
        try {
            sql.update(
                    "insert into appointment_status(id,status,description,active) values(?,'β','Historical definition',0)",
                    -first - 100);
            for (int i = 0; i < 35; i++) {
                int id = -first + i;
                ids.add(id);
                sql.update(
                        "insert into appointmentArchive(id,demographic_no,appointment_no,provider_no,status,notes) values(?,?,?,?,?,?)",
                        id,
                        i == 34 ? first + 1 : first,
                        i / 2,
                        physician,
                        i == 0 ? null : i == 1 ? "" : "β",
                        i == 0 ? notes : "");
            }
            var source =
                    context.getBean(
                            io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao.class);
            var statusSource =
                    context.getBean(
                            io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao.class);
            var values = new java.io.ByteArrayOutputStream();
            var index = new java.io.ByteArrayOutputStream();
            sourceRead()
                    .execute(
                            transaction -> {
                                try {
                                    var reader =
                                            new CdsExportAppointmentArchiveSourceReader(source);
                                    new CdsExportSourceSchemaValidator()
                                            .verify(reader.schema(), reader.columns());
                                    assertThat(reader.count(first)).isEqualTo(34);
                                    var page = source.findCdsExportRowsAfter(first, null, 32);
                                    assertThat(page).hasSize(32);
                                    assertThat(
                                                    source.findCdsExportRowsAfter(
                                                            first, page.getLast().id(), 32))
                                            .hasSize(2);
                                    assertThat(page.getFirst().cells().get("status").sqlNull())
                                            .isTrue();
                                    assertThat(page.get(1).cells().get("status").bytes()).isZero();
                                    assertThat(
                                                    new String(
                                                            page.getFirst()
                                                                    .cells()
                                                                    .get("appointment_no")
                                                                    .inlineBytes(),
                                                            StandardCharsets.UTF_8))
                                            .isEqualTo(
                                                    new String(
                                                            page.get(1)
                                                                    .cells()
                                                                    .get("appointment_no")
                                                                    .inlineBytes(),
                                                            StandardCharsets.UTF_8));
                                    assertThat(statusSource.countCdsExportRows(first)).isEqualTo(1);
                                    assertThat(
                                                    new String(
                                                            statusSource
                                                                    .findCdsExportRowsAfter(
                                                                            first, null, 32)
                                                                    .getFirst()
                                                                    .cells()
                                                                    .get("description")
                                                                    .inlineBytes(),
                                                            StandardCharsets.UTF_8))
                                            .isEqualTo("Historical definition");
                                    new CdsExportNativeCaptureComposer()
                                            .compose(
                                                    values,
                                                    index,
                                                    java.util.Set.of("appointmentArchive"),
                                                    1000,
                                                    8L << 20,
                                                    () -> {},
                                                    writer -> {
                                                        writer.beginTable(
                                                                reader.table(),
                                                                reader.columns(),
                                                                34);
                                                        reader.capture(first, writer);
                                                        writer.endTable();
                                                    });
                                    assertThatThrownBy(
                                                    () ->
                                                            source.readCdsExportValueChunk(
                                                                    first,
                                                                    -first + 34,
                                                                    "notes",
                                                                    0,
                                                                    1))
                                            .hasMessage("CDS_EXPORT_NATIVE_SOURCE_INVALID");
                                    transaction.setRollbackOnly();
                                    return null;
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            var proof =
                    new CdsExportNativeCaptureReader()
                            .verify(
                                    new java.io.ByteArrayInputStream(values.toByteArray()),
                                    new java.io.ByteArrayInputStream(index.toByteArray()),
                                    Map.of(
                                            "appointmentArchive",
                                            new CdsExportNativeCaptureReader.Table(
                                                    java.util.Set.copyOf(
                                                            io.github.carlos_emr.carlos.commn.dao
                                                                    .CdsExportPatientTableReadSupport
                                                                    .Plan.APPOINTMENT_ARCHIVE
                                                                    .columns()),
                                                    34)),
                                    1000,
                                    8L << 20,
                                    () -> {});
            assertThat(proof.fields()).isEqualTo(34L * 26);
            assertThat(values.toString(StandardCharsets.UTF_8)).contains(notes);
        } finally {
            for (int id : ids)
                sql.update(
                        "delete from appointmentArchive where id=? and demographic_no in (?,?)",
                        id,
                        first,
                        first + 1);
            sql.update("delete from appointment_status where id=? and status='β'", -first - 100);
        }
    }

    private org.springframework.transaction.support.TransactionTemplate sourceRead() {
        var read =
                new org.springframework.transaction.support.TransactionTemplate(
                        context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(15);
        return read;
    }

    private CdsExportCommand roster() {
        return command(CdsExportCommand.Selection.PHYSICIAN_ROSTER, List.of(physician), List.of());
    }

    private CdsExportCommand selected(List<Integer> patients) {
        return command(CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), patients);
    }

    private CdsExportCommand command(
            CdsExportCommand.Selection selection, List<String> providers, List<Integer> patients) {
        return new CdsExportCommand(
                "test",
                physician,
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                selection,
                providers,
                patients,
                java.util.EnumSet.allOf(CdsCategory.class),
                "recipient",
                "audit");
    }

    private void insert(int id, String provider, String status) {
        owned.add(id);
        sql.update(
                "insert into demographic(demographic_no,first_name,last_name,sex,pref_name,lastUpdateDate,provider_no,patient_status)"
                        + " values(?, 'FAKE-CDS', 'Selection', 'F', '', CURRENT_TIMESTAMP, ?, ?)",
                id,
                provider,
                status);
    }

    private List<CdsExportSelectionRowDto> rows(CdsExportSelectionDto capture) throws Exception {
        try (var input = store.open(capture.inventory())) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            List<CdsExportSelectionRowDto> rows = new ArrayList<>();
            for (String line : text.split("\\n"))
                if (!line.isEmpty())
                    rows.add(mapper.readValue(line, CdsExportSelectionRowDto.class));
            return rows;
        }
    }
}
