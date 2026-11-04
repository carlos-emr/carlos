/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.test.base;

import java.sql.DriverManager;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Owns one randomly named test schema; never adopts or drops an existing database. */
public final class IsolatedJpaTestDatabase implements AutoCloseable {
    private final String database;
    private final String server;
    private final Properties credentials = new Properties();
    private final DriverManagerDataSource dataSource;
    private final boolean migrated;
    private boolean closed;

    /** Creates the ordinary H2 or explicitly requested physical MariaDB test database. */
    public IsolatedJpaTestDatabase(String label) throws Exception {
        this(label, System.getenv());
    }

    /** Accepts an explicit environment so destructive-boundary guards can be tested without connecting. */
    public IsolatedJpaTestDatabase(String label, Map<String, String> environment) throws Exception {
        // Prefix + label + separator + UUID must fit MariaDB's 64-character identifier.
        if (!label.matches("[a-z0-9_]{1,19}")) {
            throw new IllegalArgumentException("Invalid test database label");
        }
        boolean nativeMode = "true".equals(environment.get("CARLOS_TEST_MARIADB_DAO"));
        server = environment.get("CARLOS_TEST_MARIADB_URL");
        if (!nativeMode && server != null) {
            throw new IllegalArgumentException("A MariaDB URL requires the explicit native-test opt-in");
        }
        String mode = environment.getOrDefault("CARLOS_TEST_MARIADB_SCHEMA", "generated");
        String province = environment.getOrDefault("CARLOS_TEST_MARIADB_PROVINCE", "on");
        if (!java.util.List.of("generated", "flyway").contains(mode)
                || !java.util.List.of("on", "bc").contains(province)) {
            throw new IllegalArgumentException("Unknown native test schema or province");
        }
        if (nativeMode && (server == null || !server.matches("jdbc:mysql://[^/@?#;\\s]+/"))) {
            throw new IllegalArgumentException("Native tests require a server URL without a database or query");
        }
        migrated = nativeMode && "flyway".equals(mode);
        database = "carlos_test_" + label + "_" + UUID.randomUUID().toString().replace("-", "");
        if (!nativeMode) {
            dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + database
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
            return;
        }
        credentials.setProperty("user", environment.getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        credentials.setProperty("password", environment.getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        try (var connection = DriverManager.getConnection(server, credentials);
             var statement = connection.createStatement()) {
            if (!connection.getMetaData().getDatabaseProductVersion().contains("MariaDB")) {
                throw new IllegalArgumentException("Native evidence requires physical MariaDB");
            }
            statement.executeUpdate("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
        dataSource = new DriverManagerDataSource(server + database
                + "?connectionCollation=utf8mb4_general_ci&zeroDateTimeBehavior=CONVERT_TO_NULL"
                + "&jdbcCompliantTruncation=false&useInformationSchema=false",
                credentials.getProperty("user"), credentials.getProperty("password"));
        Properties settings = new Properties();
        settings.setProperty("sessionVariables", "sql_mode='',innodb_strict_mode=0");
        dataSource.setConnectionProperties(settings);
        try {
            if (migrated) {
                Flyway.configure().dataSource(dataSource)
                        .initSql("SET SESSION sql_mode='', collation_connection='utf8mb4_general_ci', innodb_strict_mode=0")
                        .locations("classpath:db/migration/common", "classpath:db/migration/" + province)
                        .failOnMissingLocations(true).ignoreMigrationPatterns(new String[0]).load().migrate();
            }
        } catch (Exception failure) {
            try { close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Map<String, Object> jpaProperties() {
        return Map.of("hibernate.hbm2ddl.auto", migrated ? "none" : "create-drop",
                "hibernate.show_sql", "false", "hibernate.dialect", server == null
                        ? "org.hibernate.dialect.H2Dialect"
                        : "io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect");
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        if (server == null) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        } else {
            try (var connection = DriverManager.getConnection(server, credentials);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE IF EXISTS " + database);
            }
        }
        closed = true;
    }
}
