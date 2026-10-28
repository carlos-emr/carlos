/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Fails the MariaDB CI lane if either persistence path silently falls back to H2. */
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@Tag("integration")
@Tag("dao")
class MariaDbDaoHarnessIntegrationTest extends CarlosTestBase {
    @Autowired
    private DataSource dataSource;
    @Autowired
    @Qualifier("entityManagerFactory")
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    @Qualifier("sessionFactory")
    private EntityManagerFactory sessionFactory;

    @Test
    void shouldUsePhysicalMariaDbAndSelectedSchema_forBothPersistencePaths() throws Exception {
        String expectedSchema = System.getProperty("carlos.test.schema.expected", "generated");
        assertThat(System.getenv().getOrDefault("CARLOS_TEST_MARIADB_SCHEMA", "generated"))
                .isEqualTo(expectedSchema);
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
            assertThat(connection.getCatalog()).startsWith("carlos_test_5399_dao_");
            if ("flyway".equals(expectedSchema)) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isGreaterThan(0);
                }
            } else {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM measurements")) {
                    assertThat(rows.next()).isTrue();
                }
            }
        }
        for (EntityManagerFactory factory : new EntityManagerFactory[]{entityManagerFactory, sessionFactory}) {
            assertThat(factory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect())
                    .isInstanceOf(OscarMySQL5Dialect.class);
        }
    }
}
