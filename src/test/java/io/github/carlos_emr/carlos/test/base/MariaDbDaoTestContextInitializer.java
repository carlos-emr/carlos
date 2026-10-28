/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.test.base;

import io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Opt-in physical MariaDB variant of the existing Spring/JPA DAO test harness. */
public class MariaDbDaoTestContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!"true".equals(System.getenv("CARLOS_TEST_MARIADB_DAO"))) return;
        String server = System.getenv("CARLOS_TEST_MARIADB_URL");
        if (server == null || !server.startsWith("jdbc:mysql:") || !server.endsWith("/") || server.contains("?")) {
            throw new IllegalArgumentException("MariaDB DAO tests require a server JDBC URL ending in / without a database");
        }
        String schemaMode = System.getenv().getOrDefault("CARLOS_TEST_MARIADB_SCHEMA", "generated");
        if (!List.of("generated", "flyway").contains(schemaMode)) {
            throw new IllegalArgumentException("MariaDB test schema must be generated or flyway");
        }
        boolean migrated = "flyway".equals(schemaMode);
        String database = "carlos_test_5399_dao_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = server + database + "?connectionCollation=utf8mb4_general_ci"
                + "&zeroDateTimeBehavior=CONVERT_TO_NULL&jdbcCompliantTruncation=false&useInformationSchema=false";
        Properties credentials = new Properties();
        credentials.setProperty("user", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        credentials.setProperty("password", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        var beans = (DefaultListableBeanFactory) context.getBeanFactory();
        DisposableBean cleanup = () -> execute(server, credentials, "DROP DATABASE IF EXISTS " + database);
        try {
            try (var connection = DriverManager.getConnection(server, credentials)) {
                if (!connection.getMetaData().getDatabaseProductVersion().contains("MariaDB")) {
                    throw new IllegalArgumentException("MariaDB DAO tests require a physical MariaDB server");
                }
            }
            execute(server, credentials, "CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            // Register first so cleanup runs after the pools and persistence factories close,
            // including a failed context refresh. No pre-existing database is opened or dropped.
            beans.registerSingleton("mariaDbDaoTestDatabase", cleanup);
            beans.registerDisposableBean("mariaDbDaoTestDatabase", cleanup);
            if (migrated) Flyway.configure().dataSource(jdbcUrl, credentials.getProperty("user"),
                            credentials.getProperty("password"))
                    .initSql("SET SESSION sql_mode='', collation_connection='utf8mb4_general_ci'")
                    .locations("classpath:db/migration/common", "classpath:db/migration/on")
                    .failOnMissingLocations(true).ignoreMigrationPatterns(new String[0]).load().migrate();
        } catch (Exception failure) {
            try {
                cleanup.destroy();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Could not provision the isolated MariaDB DAO test schema", failure);
        }
        context.addBeanFactoryPostProcessor(factory -> {
            factory.getBeanDefinition("dataSource").getPropertyValues()
                    .add("driverClassName", "com.mysql.cj.jdbc.Driver")
                    .add("url", jdbcUrl)
                    .add("username", credentials.getProperty("user"))
                    .add("password", credentials.getProperty("password"))
                    // Match the packaged server's configured SQL mode for this compatibility lane.
                    .add("connectionInitSqls", List.of(migrated ? "SET SESSION sql_mode=''"
                            : "SET SESSION sql_mode='NO_AUTO_VALUE_ON_ZERO'"));
            factory.getBeanDefinition("databaseInitializer").getPropertyValues().add("enabled", !migrated);
            if (!migrated) {
                var fixtures = new ResourceDatabasePopulator(new ClassPathResource("test-lookup-tables.sql"));
                // H2 CHAR values are case-sensitive. Match the installed appointment-code
                // collation instead of inheriting MariaDB's case-insensitive text default.
                fixtures.addScript(new ByteArrayResource(("ALTER TABLE scheduletemplatecode MODIFY COLUMN code "
                        + "CHAR(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;").getBytes(StandardCharsets.UTF_8)));
                factory.getBeanDefinition("databaseInitializer").getPropertyValues().add("databasePopulator", fixtures);
            }
            BeanDefinition entityManager = factory.getBeanDefinition("entityManagerFactory");
            var adapter = new HibernateJpaVendorAdapter();
            adapter.setDatabase(Database.MYSQL);
            adapter.setGenerateDdl(false);
            entityManager.getPropertyValues().add("jpaVendorAdapter", adapter);
            configureHibernate(entityManager, "jpaProperties", migrated ? "none" : "create");
            configureHibernate(factory.getBeanDefinition("sessionFactory"), "hibernateProperties", "none");
        });
    }

    private static void configureHibernate(BeanDefinition definition, String property, String schemaAction) {
        Properties declared = (Properties) definition.getPropertyValues().getPropertyValue(property).getValue();
        Properties settings = new Properties();
        declared.forEach((key, value) -> settings.setProperty(text(key), text(value)));
        settings.setProperty("hibernate.dialect", OscarMySQL5Dialect.class.getName());
        settings.setProperty("hibernate.hbm2ddl.auto", schemaAction);
        settings.setProperty("hibernate.show_sql", "false");
        settings.setProperty("hibernate.max_fetch_depth", "3");
        definition.getPropertyValues().add(property, settings);
    }

    private static String text(Object value) {
        return value instanceof TypedStringValue typed ? typed.getValue() : value.toString();
    }

    private static void execute(String server, Properties credentials, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(server, credentials);
             var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
