/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.w3c.dom.Element;
import static org.junit.jupiter.api.Assertions.*;

/** Actual shipped datasource/property bindings and real JDBC work in a disposable H2/MariaDB schema. */
@Tag("integration")
@Tag("mariadb-native")
@Tag("report")
@Tag("read")
class ReportConnectionLeaseIntegrationTest {
    private IsolatedJpaTestDatabase database;
    private DriverManagerDataSource direct;
    private boolean mariaDb;

    @BeforeEach
    void prepare() throws Exception {
        database = new IsolatedJpaTestDatabase("report_lease");
        direct = (DriverManagerDataSource) database.dataSource();
        try (var connection = direct.getConnection()) {
            mariaDb = connection.getMetaData().getDatabaseProductName().equals("MariaDB");
            if (!mariaDb) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE ALIAS QUALIFICATION_SLEEP FOR 'java.lang.Thread.sleep(long)'");
                }
            }
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    private Path productionResources() throws Exception {
        var root = Path.of(OscarTrackingBasicDataSource.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "Use the real compiled/installed datasource resource directory");
        return root;
    }

    private BasicDataSource pool(Map<String, String> overrides) throws Exception {
        var defaults = new Properties();
        try (var input = Files.newInputStream(productionResources().resolve("carlos.properties"))) {
            defaults.load(input);
        }
        Map<String, Object> values = new HashMap<>();
        defaults.forEach((key, value) -> values.put(key.toString(), value));
        values.putAll(overrides);
        values.put("db_driver", mariaDb ? "org.mariadb.jdbc.Driver" : "org.h2.Driver");
        values.put("db_uri", direct.getUrl());
        values.put("db_name", "");
        values.put("db_username", direct.getUsername());
        values.put("db_password", direct.getPassword());
        // Three leases reproduce DBCP's near-capacity condition without opening a clinic-size pool.
        values.put("db_max_active", "3");
        values.put("db_max_idle", "3");
        values.put("db_max_wait", "2000");
        var sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("owned-test", values));
        var resolver = new PropertySourcesPropertyResolver(sources);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);
        org.w3c.dom.Document xml;
        try (var input = Files.newInputStream(productionResources().resolve("spring_jpa.xml"))) {
            xml = factory.newDocumentBuilder().parse(input);
        }
        var beans = xml.getElementsByTagNameNS("http://www.springframework.org/schema/beans", "bean");
        Element selected = null;
        for (int i = 0; i < beans.getLength(); i++) {
            var bean = (Element) beans.item(i);
            if (bean.getAttribute("id").equals("dataSource")) {
                selected = bean;
            }
        }
        assertNotNull(selected);
        var pool = (BasicDataSource) Class.forName(selected.getAttribute("class")).getConstructor().newInstance();
        var wrapper = new BeanWrapperImpl(pool);
        var properties = selected.getElementsByTagNameNS("http://www.springframework.org/schema/beans", "property");
        for (int i = 0; i < properties.getLength(); i++) {
            var property = (Element) properties.item(i);
            assertTrue(property.hasAttribute("value"), "Datasource test requires explicit value binding");
            wrapper.setPropertyValue(property.getAttribute("name"),
                    resolver.resolveRequiredPlaceholders(property.getAttribute("value")));
        }
        return pool;
    }

    private void awaitExecuting(long session, Future<?> query) throws Exception {
        String probe = mariaDb
                ? "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID=? AND STATE='User sleep'"
                : "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID=? AND EXECUTING_STATEMENT LIKE '%QUALIFICATION_SLEEP%'";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (var observer = direct.getConnection(); var statement = observer.prepareStatement(probe)) {
            statement.setLong(1, session);
            do {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) == 1) {
                        return;
                    }
                }
                assertFalse(query.isDone(), "The query ended before actual execution was observed");
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
        }
        fail("The actual long-running query was not observed");
    }

    @Test
    void shouldRetainActiveReportConnection_untilItsQueryDeadline() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor(); var pool = pool(Map.of());
                var report = pool.getConnection(); var held = pool.getConnection()) {
            long session;
            try (var statement = report.createStatement();
                    var row = statement.executeQuery(mariaDb ? "SELECT CONNECTION_ID()" : "SELECT SESSION_ID()")) {
                assertTrue(row.next());
                session = row.getLong(1);
            }
            long started = System.nanoTime();
            var query = executor.submit(() -> {
                try (var statement = report.createStatement()) {
                    statement.setQueryTimeout(ReportResultBudget.TIMEOUT_SECONDS);
                    try (var rows = statement.executeQuery(mariaDb
                            ? "SELECT SLEEP(20) AS report_delay" : "CALL QUALIFICATION_SLEEP(20000)")) {
                        return rows.next();
                    }
                }
            });
            awaitExecuting(session, query);
            Thread.sleep(16000);
            boolean reclaimed;
            try (var pressure = pool.getConnection()) {
                assertFalse(pressure.isClosed());
                reclaimed = pool.getNumActive() != 3;
            }
            Throwable failure = null;
            Boolean row = null;
            try {
                row = query.get(25, TimeUnit.SECONDS);
            } catch (ExecutionException error) {
                failure = error.getCause();
            }
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
            Throwable observed = failure;
            Boolean found = row;
            assertAll(
                    () -> assertFalse(reclaimed, "An executing report was reclaimed as an abandoned lease"),
                    () -> assertNull(observed, "The legitimate query failed before its report deadline"),
                    () -> assertEquals(Boolean.TRUE, found),
                    () -> assertTrue(seconds >= 15 && seconds < ReportResultBudget.TIMEOUT_SECONDS,
                            "Query must cross 15 seconds within its 30-second report budget"),
                    () -> assertTrue(pool.getRemoveAbandonedTimeoutDuration().getSeconds()
                            > ReportResultBudget.TIMEOUT_SECONDS_CEILING,
                            "Default reclamation must exceed every supported report deadline"));
        }
    }

    @Test
    void shouldRetireIdleAbandonedLeases_withExplicitShortTimeoutOverride() throws Exception {
        try (var pool = pool(Map.of("db_remove_abandoned_timeout", "1"));
                var idle = pool.getConnection(); var held = pool.getConnection()) {
            assertEquals(3, pool.getMaxTotal());
            assertEquals(2, pool.getNumActive());
            assertEquals(0, pool.getNumIdle());
            Thread.sleep(2100);
            try (var pressure = pool.getConnection()) {
                assertFalse(pressure.isClosed());
                assertEquals(1, pool.getNumActive(), "Both old leases must be retired before this new borrow");
                // H2's JDBC abort is a no-op; actual MariaDB must also close the physical handle.
                if (mariaDb) {
                    assertThrows(SQLException.class, () -> {
                        try (var statement = idle.createStatement()) {
                            statement.executeQuery("SELECT 1");
                        }
                    }, "The retired physical lease should no longer execute queries");
                }
            }
        }
    }

    @Test
    void shouldHonorConfiguredTimeout_withoutDisablingLeakDetection() throws Exception {
        try (var pool = pool(Map.of("db_remove_abandoned_timeout", "17"))) {
            assertEquals(17, pool.getRemoveAbandonedTimeoutDuration().getSeconds());
            assertTrue(pool.getRemoveAbandonedOnBorrow());
            assertTrue(pool.getRemoveAbandonedOnMaintenance());
            assertTrue(pool.getLogAbandoned());
        }
    }
}
