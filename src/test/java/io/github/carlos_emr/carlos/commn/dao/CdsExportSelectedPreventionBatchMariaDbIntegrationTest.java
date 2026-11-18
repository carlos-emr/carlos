/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.commons.dbcp2.BasicDataSource;
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
 * A chart with more selected preventions than one statement names (8,192) on a real MariaDB: the
 * former 10,000-row refusal is gone, every selected row and every row that belongs to one is
 * counted and read exactly once, in key order, and rows of preventions that were not selected, or
 * of another patient, are never read. Works in an owned copy of the two tables' structure.
 */
@Tag("integration")
@Tag("cds-export")
@Isolated("Owned temporary database of 15,000 synthetic prevention rows")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportSelectedPreventionBatchMariaDbIntegrationTest {
    private static final int PATIENT = 27, OTHER = 28, ROWS = 15_000;
    private BasicDataSource admin, source;
    private JdbcTemplate sql;
    private LocalContainerEntityManagerFactoryBean factory;
    private TransactionTemplate snapshot;
    private EntityManager em;
    private String owned;
    private boolean created;

    @BeforeEach
    void createOwnedChart() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        String url = properties.getProperty("jdbcUrl", "");
        if (!url.matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException(
                    "An owned disposable cds_export_test database is required");
        owned = "cds_prev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        admin = pool(url, properties);
        sql = new JdbcTemplate(admin);
        assertThat(sql.queryForObject("select VERSION()", String.class)).contains("MariaDB");
        sql.execute("create database `" + owned + "`");
        created = true;
        for (String table : Set.of("preventions", "preventionsExt"))
            sql.execute(
                    "create table `" + owned + "`.`" + table + "` like cds_export_test.`" + table + "`");
        // The patient's 15,000 preventions (ids 1 to 15,000) and one of another patient.
        sql.execute(
                "insert into `"
                        + owned
                        + "`.`preventions`(id,demographic_no,creation_date,prevention_date,"
                        + "provider_no,provider_name,prevention_type,deleted,refused,never,"
                        + "lastUpdateDate) select seq,"
                        + PATIENT
                        + ",'2024-02-29 12:34:56','2024-02-29 12:34:56','','',concat('Type',seq),"
                        + "'0','0','0','2024-02-29 12:34:56' from seq_1_to_"
                        + ROWS);
        sql.execute(
                "insert into `"
                        + owned
                        + "`.`preventions`(id,demographic_no,provider_no,prevention_type,"
                        + "lastUpdateDate) values(20000,"
                        + OTHER
                        + ",'','Foreign','2024-02-29 12:34:56')");
        // One extension row for every third prevention, in descending prevention order, so the
        // extension keys do not follow the prevention ids; and one of the other patient's.
        sql.execute(
                "insert into `"
                        + owned
                        + "`.`preventionsExt`(id,prevention_id,keyval,val) select seq,"
                        + (ROWS + 1)
                        + "-seq*3+2,'lot',concat('L',seq) from seq_1_to_"
                        + ROWS / 3);
        sql.execute(
                "insert into `"
                        + owned
                        + "`.`preventionsExt`(id,prevention_id,keyval,val)"
                        + " values(90000,20000,'lot','FOREIGN')");
        source = pool(url.replace("/cds_export_test", "/" + owned), properties);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPersistenceUnitName("entityManagerFactory");
        factory.setDataSource(source);
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

    private static BasicDataSource pool(String url, Properties properties) {
        var pool = new BasicDataSource();
        pool.setUrl(url);
        pool.setUsername(properties.getProperty("jdbcUser"));
        pool.setPassword(properties.getProperty("jdbcPassword"));
        pool.setMaxTotal(2);
        return pool;
    }

    @AfterEach
    void dropOwnedChart() throws Exception {
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

    @Test
    void shouldReadEverySelectedPreventionOnceInKeyOrder_whenTheChartHasMoreThanOneBatch() {
        // Every prevention but each fifth: 12,000 selected, more than the former limit of
        // 10,000 and more than one statement names.
        var selected = new TreeSet<Integer>();
        for (int id = 1; id <= ROWS; id++) if (id % 5 != 0) selected.add(id);
        assertThat(selected).hasSizeGreaterThan(10_000);

        snapshot.executeWithoutResult(
                status -> {
                    assertThat(
                                    CdsExportPatientTableReadSupport.preventionCount(
                                            em, Plan.PREVENTIONS_FULL, PATIENT, selected))
                            .isEqualTo(selected.size());
                    var read = new ArrayList<Integer>();
                    Long after = null;
                    while (true) {
                        var page =
                                CdsExportPatientTableReadSupport.preventionPage(
                                        em, Plan.PREVENTIONS_FULL, PATIENT, selected, after, 32);
                        for (var row : page) {
                            // Strictly ascending across the batches.
                            assertThat(after == null || row.id() > after).isTrue();
                            after = row.id();
                            read.add((int) row.id());
                        }
                        if (page.size() < 32) break;
                    }
                    assertThat(read).containsExactlyElementsOf(selected);
                    // One value of a row named by the second batch.
                    int late = selected.last();
                    assertThat(
                                    new String(
                                            CdsExportPatientTableReadSupport.preventionChunk(
                                                    em,
                                                    Plan.PREVENTIONS_FULL,
                                                    PATIENT,
                                                    selected,
                                                    late,
                                                    "prevention_type",
                                                    0,
                                                    64),
                                            StandardCharsets.UTF_8))
                            .isEqualTo("Type" + late);
                });
    }

    @Test
    void shouldReadOnlyTheRowsOfSelectedPreventions_whenTheirOwnersSpanBatches() {
        var selected = new TreeSet<Integer>();
        for (int id = 1; id <= ROWS; id++) if (id % 5 != 0) selected.add(id);
        // Extension row n belongs to prevention 15,003 - 3n.
        var expected = new TreeSet<Long>();
        for (long row = 1; row <= ROWS / 3; row++)
            if (selected.contains((int) (ROWS + 1 - row * 3 + 2))) expected.add(row);
        assertThat(expected).hasSizeGreaterThan(3_000);

        snapshot.executeWithoutResult(
                status -> {
                    assertThat(
                                    CdsExportPatientTableReadSupport.preventionCount(
                                            em, Plan.PREVENTIONS_EXT, PATIENT, selected))
                            .isEqualTo(expected.size());
                    var read = new ArrayList<Long>();
                    Long after = null;
                    while (true) {
                        var page =
                                CdsExportPatientTableReadSupport.preventionPage(
                                        em, Plan.PREVENTIONS_EXT, PATIENT, selected, after, 32);
                        for (var row : page) {
                            assertThat(after == null || row.id() > after).isTrue();
                            after = row.id();
                            read.add(row.id());
                        }
                        if (page.size() < 32) break;
                    }
                    // Not a row of an unselected prevention, and not the other patient's.
                    assertThat(read).containsExactlyElementsOf(expected);
                });
    }
}
