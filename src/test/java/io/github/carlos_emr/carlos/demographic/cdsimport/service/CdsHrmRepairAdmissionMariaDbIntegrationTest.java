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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMFixMissingReportHelper;
import io.github.carlos_emr.carlos.hospitalReportManager.HrmReportRepairDeferredException;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real helper, DAO scalar SQL, Spring/JPA transactions and cross-node MariaDB admission locks. */
@Tag("integration")
@Tag("mariadb")
@Isolated("Physical database-wide CDS maintenance state")
@EnabledIfSystemProperty(named = "cds.test.properties", matches = ".+")
class CdsHrmRepairAdmissionMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions { }

    /** Only descriptor pagination is synthetic; the repair's fresh scalar DAO queries execute SQL. */
    public static class DescriptorPage extends HRMDocumentDao {
        private final HRMDocument descriptor;
        private boolean empty;
        DescriptorPage(HRMDocument descriptor) { this.descriptor = descriptor; }
        @Override public List<HRMDocument> findAll(int offset, int limit) {
            return empty || offset != 0 ? List.of() : List.of(descriptor);
        }
    }

    @TempDir Path directory;
    private OscarTrackingBasicDataSource ordinary;
    private OscarTrackingBasicDataSource importing;
    private CdsImportMaintenanceService maintenance;
    private CdsImportTargetSafetyService safety;
    private AnnotationConfigApplicationContext context;
    private ExecutorService workers;
    private final List<CdsImportMaintenanceService.Owner> owners = new CopyOnWriteArrayList<>();
    private final CountDownLatch releaseCopy = new CountDownLatch(1);
    private DescriptorPage descriptors;
    private Path downloads;
    private Path documents;
    private Path source;
    private String filename;
    private int documentId;
    private boolean ownsFixture;
    private final AtomicInteger copies = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        Properties jdbc = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {
            jdbc.load(input);
        }
        assertThat(jdbc.getProperty("ownedDisposableDatabase")).isEqualTo("true");
        assertThat(jdbc.getProperty("jdbcUrl", "")).matches("jdbc:(mysql|mariadb)://[^/]+/carlos_cds_test(?:\\?.*)?");
        ordinary = dataSource(jdbc);
        importing = dataSource(jdbc);
        try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection(); var sql = raw.createStatement()) {
            try (var rows = sql.executeQuery("SELECT VERSION()")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).contains("MariaDB");
            }
            try (var rows = sql.executeQuery("SELECT maintenance_active FROM cds_import_admission WHERE id='clinical-maintenance'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM cds_import_patient WHERE held=TRUE")) {
                rows.next();
                assertThat(rows.getLong(1)).isZero();
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM HRMDocument")) {
                rows.next();
                assertThat(rows.getLong(1)).isZero();
            }
        }
        assertThat(propertyCount()).isZero();
        ownsFixture = true;
        new CdsJdbcAdmissionInitializer(ordinary, true, "validate").initialize();
        new CdsJdbcAdmissionInitializer(importing, true, "validate").initialize();
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.maintenanceMode", "true");
        policy.setProperty("cds.import.drainTimeoutSeconds", "3");
        var configuration = new CdsImportConfigurationService(policy);
        maintenance = new CdsImportMaintenanceService(importing, configuration);
        safety = new CdsImportTargetSafetyService(configuration);
        downloads = Files.createDirectories(directory.resolve("downloads"));
        documents = Files.createDirectories(directory.resolve("documents"));
        filename = "synthetic-hrm-" + UUID.randomUUID() + ".xml";
        source = Files.createDirectories(downloads.resolve("20261027/decrypted")).resolve(filename);
        Files.writeString(source, "<synthetic>complete report</synthetic>");
        try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection();
                var insert = raw.prepareStatement("INSERT INTO HRMDocument(reportFile) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, filename);
            assertThat(insert.executeUpdate()).isEqualTo(1);
            try (var keys = insert.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                documentId = keys.getInt(1);
            }
        }
        HRMDocument descriptor = new HRMDocument();
        ReflectionTestUtils.setField(descriptor, "id", documentId);
        descriptor.setReportFile(filename);
        descriptors = new DescriptorPage(descriptor);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean("dataSource", DataSource.class, () -> ordinary);
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setPersistenceUnitName("entityManagerFactory");
            factory.setDataSource(ordinary);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(Property.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.dialect", "org.hibernate.dialect.MariaDBDialect"));
            return factory;
        });
        context.registerBean("transactionManager", PlatformTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean("persistenceAnnotationBeanPostProcessor", PersistenceAnnotationBeanPostProcessor.class);
        context.registerBean(HRMDocumentDao.class, () -> descriptors);
        context.register(PropertyDaoImpl.class);
        context.refresh();
        assertThat(markerCount()).isZero();
        workers = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        releaseCopy.countDown();
        if (workers != null) {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        for (var owner : owners) owner.close();
        if (ordinary != null && ownsFixture) {
            try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection()) {
                try (var delete = raw.prepareStatement("DELETE FROM HRMDocument WHERE id=?")) {
                    delete.setInt(1, documentId);
                    delete.executeUpdate();
                }
                try (var delete = raw.prepareStatement("DELETE FROM property WHERE name=?")) {
                    delete.setString(1, HRMFixMissingReportHelper.SCRIPT_PROPERTY);
                    delete.executeUpdate();
                }
                // Only a freshly owned disposable schema is allowed; leave no test-held window.
                try (var sql = raw.createStatement()) {
                    sql.executeUpdate("UPDATE cds_import_admission SET maintenance_active=FALSE,maintenance_owner_token=NULL,maintenance_connection_id=NULL WHERE id='clinical-maintenance'");
                }
            }
        }
        if (context != null) context.close();
        if (ordinary != null) ordinary.close();
        if (importing != null) importing.close();
    }

    @Test
    void shouldDrainAnAlreadyAdmittedCopy_beforeAnotherNodeActivatesMaintenance() throws Exception {
        CountDownLatch copying = new CountDownLatch(1);
        var helper = helper((file, target) -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            copies.incrementAndGet();
            copying.countDown();
            try {
                if (!releaseCopy.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic copy release timeout");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            Files.copy(file.toPath(), target.toPath().resolve(file.getName()));
        });
        var fixing = workers.submit(helper::fixIt);
        assertThat(copying.await(5, TimeUnit.SECONDS)).isTrue();
        var entering = workers.submit(this::acquire);
        awaitAdvisoryOwner();
        assertThatThrownBy(() -> entering.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(Files.exists(documents.resolve(filename))).isFalse();
        releaseCopy.countDown();
        var owner = entering.get(5, TimeUnit.SECONDS);
        safety.verify(owner);
        assertThatThrownBy(() -> fixing.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(HrmReportRepairDeferredException.class);
        assertThat(Files.readString(documents.resolve(filename))).isEqualTo(Files.readString(source));
        assertThat(copies).hasValue(1);
        assertThat(markerCount()).isZero();
        owner.allowReopen();
    }

    @Test
    void shouldRetainOrdinaryAdmission_whenDbcpActuallyReclaimsTheJdbcIdleCopyTransaction() throws Exception {
        CountDownLatch copying=new CountDownLatch(1);
        AtomicLong pooledId=new AtomicLong();
        var helper=helper((file, target) -> {
            pooledId.set(new org.springframework.jdbc.core.JdbcTemplate(ordinary).queryForObject("SELECT CONNECTION_ID()", Long.class));
            copying.countDown();
            try {
                if (!releaseCopy.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic copy release timeout");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            copies.incrementAndGet();
            Files.copy(file.toPath(), target.toPath().resolve(file.getName()));
        });
        var fixing=workers.submit(helper::fixIt);
        assertThat(copying.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ordinary.getRemoveAbandonedOnBorrow()).isTrue();
        // Only the file operation is paused. Two pressure borrows satisfy DBCP's
        // active>maxTotal-3 / idle<2 reclamation condition after its actual timeout.
        Thread.sleep(2200);
        try (Connection firstPressure=ordinary.getConnection(); Connection secondPressure=ordinary.getConnection()) {
            assertThat(firstPressure.isClosed()).isFalse();
            assertThat(secondPressure.isClosed()).isFalse();
            assertThat(ordinary.getNumActive()).isEqualTo(2);
            try (Connection raw=ordinary.openUnpooledCdsMaintenanceConnection();
                    var query=raw.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID=?")) {
                query.setLong(1, pooledId.get());
                try (var rows=query.executeQuery()) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
            }
            var entering=workers.submit(this::acquire);
            awaitAdvisoryOwner();
            assertThatThrownBy(() -> entering.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(Files.exists(documents.resolve(filename))).isFalse();
            releaseCopy.countDown();
            assertThatThrownBy(() -> fixing.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(HrmReportRepairDeferredException.class);
            var owner=entering.get(5, TimeUnit.SECONDS);
            safety.verify(owner);
            assertThat(Files.readString(documents.resolve(filename))).isEqualTo(Files.readString(source));
            assertThat(copies).hasValue(1);
            assertThat(markerCount()).isZero();
            owner.allowReopen();
        }
    }

    @Test
    void shouldRefuseAFileCopyAndMarker_whenMaintenanceIsAlreadyActive() throws Exception {
        var owner = acquire();
        safety.verify(owner);
        HRMFixMissingReportHelper helper = helper(this::copy);
        assertThatThrownBy(helper::fixIt).isInstanceOf(HrmReportRepairDeferredException.class);
        assertThat(copies).hasValue(0);
        assertThat(Files.exists(documents.resolve(filename))).isFalse();
        assertThat(markerCount()).isZero();
        owner.allowReopen();
    }

    @Test
    void shouldRefuseTheFinalMarkerForAnEmptyClinicalTable_whenMaintenanceIsActive() throws Exception {
        descriptors.empty = true;
        try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection(); var sql = raw.prepareStatement("DELETE FROM HRMDocument WHERE id=?")) {
            sql.setInt(1, documentId);
            assertThat(sql.executeUpdate()).isEqualTo(1);
        }
        var owner = acquire();
        safety.verify(owner);
        HRMFixMissingReportHelper helper = helper(this::copy);
        assertThatThrownBy(helper::fixIt).isInstanceOf(HrmReportRepairDeferredException.class);
        assertThat(copies).hasValue(0);
        assertThat(markerCount()).isZero();
        owner.allowReopen();
    }

    @Test
    void shouldReleaseTheSharedLeaseAndKeepTheMarkerAbsent_whenTheCopyFails() throws Exception {
        var helper = helper((file, target) -> { copies.incrementAndGet(); throw new IllegalStateException("Synthetic copy failure"); });
        assertThatThrownBy(helper::fixIt).isInstanceOf(IllegalStateException.class).hasMessage("Synthetic copy failure");
        assertThat(copies).hasValue(1);
        assertThat(markerCount()).isZero();
        var owner = workers.submit(this::acquire).get(5, TimeUnit.SECONDS);
        safety.verify(owner);
        owner.allowReopen();
    }

    @Test
    void shouldNeverPublishAPartialCopyOrMarkComplete_whenLegacyCopierFailsWithIo() throws Exception {
        helper((file, target) -> {
            assertThat(Files.getPosixFilePermissions(target.toPath()))
                    .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            Files.writeString(target.toPath().resolve(file.getName()), "partial");
            throw new java.io.IOException("Synthetic partial copy");
        }).fixIt();
        assertThat(Files.exists(documents.resolve(filename))).isFalse();
        assertThat(markerCount()).isZero();
        try (var paths=Files.list(documents)) { assertThat(paths.toList()).isEmpty(); }
        // The error is retryable without a destructive flag reset or manual file removal.
        helper(this::copy).fixIt();
        assertThat(Files.readString(documents.resolve(filename))).isEqualTo(Files.readString(source));
        assertThat(markerCount()).isEqualTo(1);
    }

    @Test
    void shouldPublishAndMarkCompleteForAnExistingNestedTargetDirectory() throws Exception {
        String relative="nested/"+filename;
        Files.createDirectory(documents.resolve("nested"));
        try (Connection raw=ordinary.openUnpooledCdsMaintenanceConnection();
                var update=raw.prepareStatement("UPDATE HRMDocument SET reportFile=? WHERE id=?")) {
            update.setString(1, relative); update.setInt(2, documentId); assertThat(update.executeUpdate()).isEqualTo(1);
        }
        descriptors.descriptor.setReportFile(relative);
        helper(this::copy).fixIt();
        assertThat(Files.readString(documents.resolve(relative))).isEqualTo(Files.readString(source));
        assertThat(Files.exists(documents.resolve(filename))).isFalse();
        assertThat(markerCount()).isEqualTo(1);
    }

    @Test
    void shouldNeverOverwriteATargetThatAppearsDuringPrivateCopy() throws Exception {
        helper((file, target) -> {
            Files.copy(file.toPath(), target.toPath().resolve(file.getName()));
            Files.writeString(documents.resolve(filename), "independent target");
        }).fixIt();
        assertThat(Files.readString(documents.resolve(filename))).isEqualTo("independent target");
        assertThat(markerCount()).isZero();
        try (var paths=Files.list(documents)) { assertThat(paths.toList()).containsExactly(documents.resolve(filename)); }
    }

    @Test
    void shouldRefuseAnAmbientTransaction_beforeFilesystemOrAdmissionWork() {
        var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var helper=helper(this::copy);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> helper.fixIt()))
                .isInstanceOf(HrmReportRepairDeferredException.class).hasMessage("HRM_REPAIR_AMBIENT_TRANSACTION");
        assertThat(copies).hasValue(0);
        assertThat(Files.exists(documents.resolve(filename))).isFalse();
    }

    @Test
    void shouldRefuseAChangedReportIdentity_beforeCopyOrCompletionMarker() throws Exception {
        try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection(); var sql = raw.prepareStatement("UPDATE HRMDocument SET reportFile=? WHERE id=?")) {
            sql.setString(1, "changed-" + filename);
            sql.setInt(2, documentId);
            assertThat(sql.executeUpdate()).isEqualTo(1);
        }
        HRMFixMissingReportHelper helper = helper(this::copy);
        assertThatThrownBy(helper::fixIt).isInstanceOf(HrmReportRepairDeferredException.class).hasMessage("HRM_REPAIR_SOURCE_CHANGED");
        assertThat(copies).hasValue(0);
        assertThat(markerCount()).isZero();
        assertThat(Files.exists(documents.resolve(filename))).isFalse();
    }

    @Test
    void shouldCopyTheCompleteReportAndCommitTheMarker_whenAdmissionStaysOpen() throws Exception {
        helper(this::copy).fixIt();
        assertThat(copies).hasValue(1);
        assertThat(Files.readString(documents.resolve(filename))).isEqualTo(Files.readString(source));
        assertThat(markerCount()).isEqualTo(1);
        var owner = workers.submit(this::acquire).get(5, TimeUnit.SECONDS);
        safety.verify(owner);
        owner.allowReopen();
    }

    private void copy(java.io.File sourceFile, java.io.File targetDirectory) throws java.io.IOException {
        copies.incrementAndGet();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        Files.copy(sourceFile.toPath(), targetDirectory.toPath().resolve(sourceFile.getName()));
    }

    private HRMFixMissingReportHelper helper(HRMFixMissingReportHelper.ReportCopier copier) {
        return new HRMFixMissingReportHelper(downloads.toString(), documents.toString(),
                context.getBean(HRMDocumentDao.class), context.getBean(PropertyDao.class),
                context.getBean(PlatformTransactionManager.class), copier);
    }

    private CdsImportMaintenanceService.Owner acquire() {
        var owner = maintenance.acquire();
        owners.add(owner);
        return owner;
    }

    private long markerCount() throws Exception {
        try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection(); var query = raw.prepareStatement("SELECT COUNT(*) FROM property WHERE name=? AND value='1'")) {
            query.setString(1, HRMFixMissingReportHelper.SCRIPT_PROPERTY);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private long propertyCount() throws Exception {
        try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection(); var query = raw.prepareStatement("SELECT COUNT(*) FROM property WHERE name=?")) {
            query.setString(1, HRMFixMissingReportHelper.SCRIPT_PROPERTY);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private void awaitAdvisoryOwner() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (Connection raw = ordinary.openUnpooledCdsMaintenanceConnection(); var query = raw.prepareStatement("SELECT IS_USED_LOCK(?)")) {
                query.setString(1, importing.getCdsAdmissionState().lockName());
                try (var rows = query.executeQuery()) { rows.next(); if (rows.getObject(1) != null) return; }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Competing node did not request the physical maintenance lease");
    }

    private static OscarTrackingBasicDataSource dataSource(Properties jdbc) {
        var source = new OscarTrackingBasicDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(jdbc.getProperty("jdbcUrl").replace("jdbc:mariadb:", "jdbc:mysql:"));
        source.setUsername(jdbc.getProperty("jdbcUser"));
        source.setPassword(jdbc.getProperty("jdbcPassword"));
        source.setMaxTotal(4);
        source.setRemoveAbandonedOnBorrow(true);
        source.setRemoveAbandonedTimeout(Duration.ofSeconds(1));
        source.setLogAbandoned(false);
        return source;
    }
}
