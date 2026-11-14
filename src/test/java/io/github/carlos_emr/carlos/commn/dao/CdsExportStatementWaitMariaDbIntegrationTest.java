/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A statement's deadline on a real MariaDB: the server ends the statement, the connection and its
 * read-only snapshot stay as they were, the statement sent again reads the same snapshot, and the
 * transaction still commits. Works in an owned temporary database; it never touches another.
 */
@Tag("integration")
@Tag("cds-export")
@Isolated("Owned temporary database and deliberately slow statements")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportStatementWaitMariaDbIntegrationTest {
    private BasicDataSource admin, source;
    private JdbcTemplate sql;
    private LocalContainerEntityManagerFactoryBean factory;
    private TransactionTemplate snapshot;
    private EntityManager em;
    private String url, owned, account, secret;
    private boolean created;

    @BeforeEach
    void createOwnedDatabase() throws Exception {
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
        account = properties.getProperty("jdbcUser");
        secret = properties.getProperty("jdbcPassword");
        owned = "cds_wait_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        admin = pool(url, 2);
        sql = new JdbcTemplate(admin);
        assertThat(sql.queryForObject("select VERSION()", String.class)).contains("MariaDB");
        sql.execute("create database `" + owned + "`");
        created = true;
        sql.execute("create table `" + owned + "`.`chart` (id int primary key) engine=InnoDB");
        sql.execute("insert into `" + owned + "`.`chart` values (1),(2),(3)");
        sql.execute(
                "create table `"
                        + owned
                        + "`.`"
                        + CdsExportSourceReadLock.TABLE
                        + "` (id int primary key) engine=Aria");
    }

    private BasicDataSource pool(String jdbcUrl, int size) {
        var pool = new BasicDataSource();
        pool.setUrl(jdbcUrl);
        pool.setUsername(account);
        pool.setPassword(secret);
        pool.setMaxTotal(size);
        return pool;
    }

    /** One physical connection with the given socket timeout, behind a real entity manager. */
    private void open(int socketTimeoutMillis) {
        String ownedUrl = url.replace("/cds_export_test", "/" + owned);
        source =
                pool(
                        ownedUrl
                                + (ownedUrl.contains("?") ? "&" : "?")
                                + "socketTimeout="
                                + socketTimeoutMillis,
                        1);
        DataSource bound = source;
        factory = new LocalContainerEntityManagerFactoryBean();
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
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        snapshot = new TransactionTemplate(new JpaTransactionManager(emf));
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
    }

    @AfterEach
    void dropOwnedDatabase() throws Exception {
        try {
            if (factory != null) factory.destroy();
            if (source != null) source.close();
        } finally {
            try {
                if (created) sql.execute("drop database `" + owned + "`");
            } finally {
                admin.close();
            }
        }
    }

    private long rows() {
        Object[] row =
                (Object[])
                        CdsExportStatementWaitReadSupport.waiting(
                                        em,
                                        em.createNativeQuery(
                                                "select count(*),1 from `chart`", Object[].class))
                                .getSingleResult();
        return ((Number) row[0]).longValue();
    }

    @Test
    void shouldReadTheSameSnapshotAndCommit_whenAStatementIsEndedAtItsDeadlineAndSentAgain() {
        open(60_000);
        var hints = new ArrayList<Long>();
        Long seen =
                snapshot.execute(
                        status -> {
                            // The snapshot is taken by its first read.
                            assertThat(rows()).isEqualTo(3);
                            // Another session commits a row the snapshot must never see.
                            sql.execute("insert into `" + owned + "`.`chart` values (4)");
                            long connection = connectionId();
                            // 2.5 s of work under a first wait of 1 s: ended at 1 s, ended again
                            // at 2 s, answered within the third wait of 4 s.
                            long started = System.nanoTime();
                            Object[] row =
                                    (Object[])
                                            CdsExportStatementWaitReadSupport.waiting(
                                                            em,
                                                            em.createNativeQuery(
                                                                    "select (select count(*) from"
                                                                        + " `chart`),sleep(2.5)",
                                                                    Object[].class),
                                                            1)
                                                    .getSingleResult();
                            hints.add((System.nanoTime() - started) / 1_000_000L);
                            // The same connection and the same snapshot, before and after.
                            assertThat(connectionId()).isEqualTo(connection);
                            assertThat(rows()).isEqualTo(3);
                            return ((Number) row[0]).longValue();
                        });
        // The statement that was sent again read the snapshot, not the committed fourth row,
        // and the transaction committed: nothing marked it for rollback.
        assertThat(seen).isEqualTo(3);
        // 1 s and 2 s of ended waits, then 2.5 s of work.
        assertThat(hints.getFirst()).isBetween(5_000L, 20_000L);
        Long afterwards = snapshot.execute(status -> rows());
        assertThat(afterwards).isEqualTo(4);
    }

    private long connectionId() {
        Object[] row =
                (Object[])
                        em.createNativeQuery("select connection_id(),1", Object[].class)
                                .getSingleResult();
        return ((Number) row[0]).longValue();
    }

    @Test
    void shouldOutlastAShorterSocketTimeout_andPutItBackBeforeTheConnectionReturnsToItsPool() {
        // The pool's one connection gives up on a silent socket after 1.5 s.
        open(1_500);
        List<Integer> during = new ArrayList<>();
        snapshot.executeWithoutResult(
                status -> {
                    // 3 s without a byte from the server: longer than the socket timeout, and
                    // answered, because the wait raised it for this transaction.
                    Object[] row =
                            (Object[])
                                    CdsExportStatementWaitReadSupport.waiting(
                                                    em,
                                                    em.createNativeQuery(
                                                            "select sleep(3),1", Object[].class),
                                                    10)
                                            .getSingleResult();
                    assertThat(((Number) row[0]).intValue()).isZero();
                    em.unwrap(Session.class)
                            .doWork(connection -> during.add(connection.getNetworkTimeout()));
                });
        assertThat(during).containsExactly(40_000);
        // The same physical connection, back in its pool with the socket timeout it had.
        List<Integer> after = new ArrayList<>();
        snapshot.executeWithoutResult(
                status ->
                        em.unwrap(Session.class)
                                .doWork(connection -> after.add(connection.getNetworkTimeout())));
        assertThat(after).containsExactly(1_500);
    }

    /** A new read-committed, read-only transaction for each sending of a unit, as callers use. */
    private TransactionTemplate units() {
        var units = new TransactionTemplate(snapshot.getTransactionManager());
        units.setReadOnly(true);
        units.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        units.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return units;
    }

    /** The pooled connection's socket timeout and own statement deadline, read in a transaction. */
    private List<Object> pooled() {
        List<Object> found = new ArrayList<>();
        snapshot.executeWithoutResult(
                status ->
                        em.unwrap(Session.class)
                                .doWork(
                                        connection -> {
                                            found.add(connection.getNetworkTimeout());
                                            try (var statement = connection.createStatement();
                                                    var rows =
                                                            statement.executeQuery(
                                                                    "select"
                                                                        + " @@SESSION.max_statement_time")) {
                                                rows.next();
                                                found.add(rows.getDouble(1));
                                            }
                                        }));
        return found;
    }

    @Test
    void shouldReadASharedUnitAgainWhole_whenTheServerEndsAStatementTheSharedCodeSwallowed() {
        // The pool's one connection gives up on a silent socket after 1.5 s.
        open(1_500);
        var sendings = new int[1];
        long started = System.nanoTime();
        String answer =
                CdsExportStatementWaitReadSupport.transaction(
                        units(),
                        1,
                        status -> {
                            sendings[0]++;
                            CdsExportStatementWaitReadSupport.arm();
                            try {
                                // A plain statement, as code shared with the rest of CARLOS sends
                                // it: no deadline of its own, 2.5 s of work.
                                Object[] row =
                                        (Object[])
                                                em.createNativeQuery(
                                                                "select sleep(2.5),1",
                                                                Object[].class)
                                                        .getSingleResult();
                                return ((Number) row[0]).intValue() == 0 ? "granted" : "cut";
                            } catch (RuntimeException failed) {
                                // What the shared privilege check makes of a failed read.
                                return "refused";
                            }
                        });
        // Ended at 1 s and at 2 s and answered within 4 s, never cut off by the 1.5 s socket
        // timeout: the refusals were the deadline's, and the third reading is the answer.
        assertThat(answer).isEqualTo("granted");
        assertThat(sendings[0]).isEqualTo(3);
        assertThat((System.nanoTime() - started) / 1_000_000L).isBetween(5_000L, 30_000L);
        // The same physical connection, back in its pool as it was: its own socket timeout and
        // no deadline left behind.
        assertThat(pooled()).containsExactly(1_500, 0.0d);
    }

    @Test
    void shouldPassASharedReadsOwnFailureOn_withoutReadingTheUnitAgain() {
        open(1_500);
        var sendings = new int[1];
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.transaction(
                                        units(),
                                        1,
                                        status -> {
                                            sendings[0]++;
                                            CdsExportStatementWaitReadSupport.arm();
                                            return em.createNativeQuery(
                                                            "select missing from `chart`")
                                                    .getResultList();
                                        }))
                .isInstanceOf(RuntimeException.class);
        assertThat(sendings[0]).isEqualTo(1);
        assertThat(pooled()).containsExactly(1_500, 0.0d);
    }

    @Test
    void shouldWaitForAWriterOfTheLegacyTable_andPinItOnceTheWriterIsDone() throws Exception {
        String ownedUrl = url.replace("/cds_export_test", "/" + owned);
        try (var writer = DriverManager.getConnection(ownedUrl, account, secret);
                var statement = writer.createStatement()) {
            statement.execute("lock tables `" + CdsExportSourceReadLock.TABLE + "` write");
            var releaser =
                    new Thread(
                            () -> {
                                try {
                                    Thread.sleep(1_500);
                                    statement.execute("unlock tables");
                                } catch (Exception failed) {
                                    throw new IllegalStateException(failed);
                                }
                            });
            releaser.start();
            long started = System.nanoTime();
            try (var guard =
                    CdsExportSourceReadLock.guard(
                            () -> DriverManager.getConnection(ownedUrl, account, secret))) {
                guard.pin();
                long waited = (System.nanoTime() - started) / 1_000_000L;
                // The pin waited for the writer and holds the table now.
                assertThat(waited).isGreaterThanOrEqualTo(1_000L);
                guard.verify();
                guard.release();
            }
            releaser.join();
        }
    }
}
