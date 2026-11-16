/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The capture transaction's schema pass on a real MariaDB: one complete read of
 * {@code information_schema} must serve every table exactly the structure its own read returns,
 * cost a fixed number of statements however many tables are verified, and a table altered before
 * its first read in the transaction must refuse the capture. Works in an owned clone of two empty
 * tables; it never changes the source database.
 */
@Tag("integration")
@Tag("cds-export")
@Isolated("Owned temporary database, database account and table alterations")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportSchemaPassMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    private BasicDataSource admin, source;
    private JdbcTemplate sql;
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate capture;
    private EntityManager em;
    private CdsExportSourceCatalogueDao catalogue;
    private String url, clone, user, password;
    private boolean databaseCreated, userCreated;

    @BeforeEach
    void createOwnedClone() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        url = properties.getProperty("jdbcUrl", "");
        if (!url.matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException(
                    "An owned disposable cds_export_test database is required");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        clone = "cds_schemapass_" + suffix;
        user = "cdsp_" + suffix;
        password = UUID.randomUUID().toString();
        admin =
                pool(
                        url,
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(admin);
        assertThat(sql.queryForObject("select VERSION()", String.class)).contains("MariaDB");
        sql.execute("create database `" + clone + "`");
        databaseCreated = true;
        for (String table : Set.of("allergies", "drugs"))
            sql.execute(
                    "create table `" + clone + "`.`" + table + "` like cds_export_test.`" + table + "`");
        open(properties.getProperty("jdbcUser"), properties.getProperty("jdbcPassword"));
    }

    /** The database every installed table of the reviewed profile is read from, never changed. */
    private static final String INSTALLED = "cds_export_test";

    private String account, secret;

    /** One physical connection, so the session's own statement counter sees every statement. */
    private void open(String account, String secret) {
        open(clone, account, secret);
    }

    private void open(String database, String account, String secret) {
        this.account = account;
        this.secret = secret;
        if (context != null) context.close();
        if (source != null)
            try {
                source.close();
            } catch (SQLException ignored) {
                // The pool is discarded either way.
            }
        source = pool(url.replace("/cds_export_test", "/" + database), account, secret);
        source.setMaxTotal(1);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        DataSource bound = source;
        context.registerBean("dataSource", DataSource.class, () -> bound);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setDataSource(bound);
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(PersistenceManagedTypes.of());
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
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.register(CdsExportSourceCatalogueDao.class);
        context.refresh();
        capture = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        capture.setReadOnly(true);
        capture.setTimeout(30);
        em =
                SharedEntityManagerCreator.createSharedEntityManager(
                        context.getBean(EntityManagerFactory.class));
        catalogue = context.getBean(CdsExportSourceCatalogueDao.class);
    }

    @AfterEach
    void dropOwnedClone() throws Exception {
        try {
            if (context != null) context.close();
            if (source != null) source.close();
        } finally {
            try {
                if (userCreated) sql.execute("drop user '" + user + "'@'%'");
            } finally {
                try {
                    if (databaseCreated) sql.execute("drop database `" + clone + "`");
                } finally {
                    admin.close();
                }
            }
        }
    }

    private static BasicDataSource pool(String url, String user, String password) {
        var pool = new BasicDataSource();
        pool.setUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaxTotal(2);
        return pool;
    }

    /** Statements this session has sent so far, as the server counts them. */
    private long questions() {
        Object[] row =
                (Object[])
                        em.createNativeQuery(
                                        "select VARIABLE_VALUE,1 from"
                                                + " information_schema.SESSION_STATUS where"
                                                + " VARIABLE_NAME='QUESTIONS'",
                                        Object[].class)
                                .getSingleResult();
        return Long.parseLong(row[0].toString());
    }

    /** Statements {@code work} sends, not counting the two counter reads around it. */
    private <T> long statements(Supplier<T> work, T[] result) {
        long before = questions();
        result[0] = work.get();
        return questions() - before - 1;
    }

    @Test
    @DisplayName("should serve each table the structure its own read returns from one complete read")
    void shouldServeEachTableItsOwnStructure_fromOneCompleteReadPerTransaction() {
        var alone = new CdsExportTableSchemaDto[2];
        var served = new CdsExportTableSchemaDto[2];
        var probes = new HashSet<String>();
        long[] cost = new long[6];
        capture.executeWithoutResult(
                status -> {
                    var one = new CdsExportTableSchemaDto[1];
                    cost[0] =
                            statements(
                                    () -> CdsExportPatientTableReadSupport.schema(em, Plan.ALLERGIES),
                                    one);
                    alone[0] = one[0];
                    alone[1] = CdsExportPatientTableReadSupport.schema(em, Plan.DRUGS);
                });
        for (int patient = 0; patient < 2; patient++) {
            int index = patient;
            capture.executeWithoutResult(
                    status -> {
                        var none = new Object[1];
                        cost[1 + 2 * index] =
                                statements(
                                        () -> {
                                            catalogue.openSchemaPass(probes);
                                            return null;
                                        },
                                        none);
                        assertThat(CdsExportPhysicalSchemaReadSupport.current()).isNotNull();
                        var both = new Object[1];
                        cost[2 + 2 * index] =
                                statements(
                                        () -> {
                                            served[0] =
                                                    CdsExportPatientTableReadSupport.schema(
                                                            em, Plan.ALLERGIES);
                                            served[1] =
                                                    CdsExportPatientTableReadSupport.schema(
                                                            em, Plan.DRUGS);
                                            return null;
                                        },
                                        both);
                        assertThat(served).containsExactly(alone);
                        // Nothing changed, so the second complete read agrees with the first.
                        assertThat(
                                        CdsExportPhysicalSchemaReadSupport.changed(
                                                CdsExportPhysicalSchemaReadSupport.current(),
                                                CdsExportPhysicalSchemaReadSupport.read(em, null)))
                                .isEmpty();
                    });
            // The pass belongs to its transaction and is gone with it.
            assertThat(CdsExportPhysicalSchemaReadSupport.current()).isNull();
        }
        assertThat(served[0].columns()).isNotEmpty();
        assertThat(probes).containsExactlyInAnyOrder("allergies", "drugs");
        // One table read on its own: two probes and three catalogue reads.
        assertThat(cost[0]).isEqualTo(5);
        // A pass: six statements whatever the number of tables (two privilege reads for an
        // account whose SELECT right is global, the case mode, tables, columns and keys) ...
        assertThat(cost[1]).isEqualTo(6);
        assertThat(cost[3]).isEqualTo(6);
        // ... then one visibility probe per table the first time the attempt meets it ...
        assertThat(cost[2]).isEqualTo(2);
        // ... and nothing at all for the same tables of the next patient.
        assertThat(cost[4]).isZero();
    }

    @Test
    @DisplayName("should refuse the capture when a table was altered before its first read in the transaction")
    void shouldRefuseTheCapture_whenATableWasAlteredBeforeItsFirstReadInTheTransaction() {
        // An earlier patient of the same attempt probed the table, so this pass takes no lock
        // on it before reading its rows: the one window the second read has to close.
        var probes = new HashSet<>(Set.of("allergies", "drugs"));
        try (var log = LogCapture.forLogger(CdsExportSourceCatalogueDao.class)) {
            assertThatThrownBy(
                            () ->
                                    capture.executeWithoutResult(
                                            status -> {
                                                catalogue.openSchemaPass(probes);
                                                var verified =
                                                        CdsExportPatientTableReadSupport.schema(
                                                                em, Plan.ALLERGIES);
                                                CdsExportPatientTableReadSupport.schema(
                                                        em, Plan.DRUGS);
                                                sql.execute(
                                                        "alter table `"
                                                                + clone
                                                                + "`.allergies add column"
                                                                + " future_clinical_field"
                                                                + " varchar(20) null");
                                                // The rows are now read under a structure nobody
                                                // verified.
                                                em.createNativeQuery(
                                                                "select count(*),1 from allergies",
                                                                Object[].class)
                                                        .getSingleResult();
                                                assertThat(verified.columns())
                                                        .noneMatch(
                                                                column ->
                                                                        column.name()
                                                                                .equals(
                                                                                        "future_clinical_field"));
                                                catalogue.verifySchemaPass();
                                            }))
                    .isInstanceOfSatisfying(
                            CdsExportReviewException.class,
                            refused ->
                                    assertThat(refused.reason())
                                            .isEqualTo(
                                                    CdsExportReviewException.Reason
                                                            .CLINIC_SOURCE_UNREVIEWED));
            // Only the altered table is named; the unchanged one is not.
            assertThat(log.messages())
                    .anyMatch(
                            message ->
                                    message.contains(
                                            "CDS_EXPORT_SOURCE_SCHEMA_CHANGED_DURING_CAPTURE"
                                                    + " tables=[allergies]"));
        }
    }

    @Test
    @DisplayName("should keep a table that was read unalterable until the capture transaction ends")
    void shouldKeepAReadTableUnalterable_untilTheCaptureTransactionEnds() {
        capture.executeWithoutResult(
                status -> {
                    catalogue.openSchemaPass(new HashSet<>(Set.of("allergies", "drugs")));
                    em.createNativeQuery("select count(*),1 from drugs", Object[].class)
                            .getSingleResult();
                    // The read holds the table's metadata lock for the rest of the transaction,
                    // which is why an unchanged second read proves the rows' structure.
                    assertThatThrownBy(
                                    () ->
                                            sql.execute(
                                                    "set statement lock_wait_timeout=1 for alter"
                                                            + " table `"
                                                            + clone
                                                            + "`.drugs add column"
                                                            + " future_clinical_field int null"))
                            .hasRootCauseInstanceOf(SQLException.class)
                            .rootCause()
                            .satisfies(
                                    cause ->
                                            assertThat(((SQLException) cause).getErrorCode())
                                                    .isEqualTo(1205));
                    assertThat(
                                    CdsExportPhysicalSchemaReadSupport.changed(
                                            CdsExportPhysicalSchemaReadSupport.current(),
                                            CdsExportPhysicalSchemaReadSupport.read(em, null)))
                            .isEmpty();
                });
    }

    @Test
    @DisplayName("should verify the whole reviewed profile from one read and probe each table once per attempt")
    void shouldVerifyTheWholeReviewedProfile_fromOneReadForEachPatientOfAnAttempt()
            throws Exception {
        // The installed schema itself, read only: every table of the pinned profile is there.
        open(INSTALLED, account, secret);
        var probes = new HashSet<String>();
        long[] cost = new long[2];
        for (int patient = 0; patient < 2; patient++) {
            int index = patient;
            // The legacy form table may be non-transactional; the capture pins it, as here.
            try (var pinned =
                    io.github.carlos_emr.carlos.demographic.cdsexport.support
                            .CdsExportSourceReadLock.acquireUnpooled(
                            java.sql.DriverManager.getConnection(
                                    source.getUrl(), account, secret))) {
                capture.executeWithoutResult(
                        status -> {
                            var none = new Object[1];
                            cost[index] =
                                    statements(
                                            () -> {
                                                catalogue.openSchemaPass(probes);
                                                assertThat(
                                                                CdsExportPhysicalSchemaReadSupport
                                                                        .current())
                                                        .isNotNull();
                                                var schema = catalogue.readSourceSchema();
                                                try {
                                                    new io.github.carlos_emr.carlos.demographic
                                                                    .cdsexport.service
                                                                    .CdsExportSourceSchemaValidator()
                                                            .verify(
                                                                    schema,
                                                                    Set.of("catalogueSha256"));
                                                } catch (java.io.IOException refused) {
                                                    throw new java.io.UncheckedIOException(
                                                            refused);
                                                }
                                                for (Plan plan :
                                                        java.util.List.of(
                                                                Plan.ALLERGIES,
                                                                Plan.DRUGS,
                                                                Plan.CASE_NOTES,
                                                                Plan.CASE_ISSUES))
                                                    assertThat(
                                                                    CdsExportPatientTableReadSupport
                                                                            .schema(em, plan)
                                                                            .columns())
                                                            .isNotEmpty();
                                                catalogue.requireUnclassifiedSourcesEmpty(
                                                        1, () -> {});
                                                // Nothing changed: the second read agrees and the
                                                // whole profile is compared again.
                                                catalogue.verifySchemaPass();
                                                return null;
                                            },
                                            none);
                        });
            }
        }
        var profileTables =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue
                        .current()
                        .profiles()
                        .stream()
                        .mapToInt(profile -> profile.tables().size())
                        .min()
                        .orElseThrow();
        // The first patient of an attempt probes each table of the profile once ...
        assertThat(probes.size()).isGreaterThanOrEqualTo(profileTables);
        assertThat(cost[0]).isGreaterThan(profileTables);
        // ... and every later patient verifies the same profile and tables with two complete
        // reads and the few presence checks of the catalogue, however many tables there are.
        assertThat(cost[1]).isLessThan(40);
        assertThat(cost[0] - cost[1]).isEqualTo(probes.size());
    }

    @Test
    @DisplayName("should open no pass for an account without SELECT on the whole schema")
    void shouldOpenNoPass_forAnAccountWithoutSelectOnTheWholeSchema() {
        sql.execute("create user '" + user + "'@'%' identified by '" + password + "'");
        userCreated = true;
        // Every table, but one at a time: such an account could be shown only some columns of a
        // later table, so each table keeps its own visibility probe and catalogue read.
        for (String table : Set.of("allergies", "drugs"))
            sql.execute(
                    "grant select on `" + clone + "`.`" + table + "` to '" + user + "'@'%'");
        open(user, password);
        long[] cost = new long[1];
        capture.executeWithoutResult(
                status -> {
                    catalogue.openSchemaPass(new HashSet<>());
                    assertThat(CdsExportPhysicalSchemaReadSupport.current()).isNull();
                    var one = new CdsExportTableSchemaDto[1];
                    cost[0] =
                            statements(
                                    () -> CdsExportPatientTableReadSupport.schema(em, Plan.ALLERGIES),
                                    one);
                    assertThat(one[0].columns()).isNotEmpty();
                });

        assertThat(cost[0]).isEqualTo(5);
    }
}
