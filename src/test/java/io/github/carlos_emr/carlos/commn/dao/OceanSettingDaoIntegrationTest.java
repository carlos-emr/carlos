// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.OceanSetting;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@DisplayName("Ocean singleton settings persistence")
class OceanSettingDaoIntegrationTest {
    private SessionFactory factory;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:ocean-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url);
             var reader = Files.newBufferedReader(Path.of("database/mysql/migration/common/V1.0.30__add_ocean_setting.sql"))) {
            RunScript.execute(connection, reader);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS SystemPreferences (id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "name VARCHAR(40), `value` VARCHAR(255), updateDate DATETIME)");
            }
        }
        factory = new Configuration().addAnnotatedClass(OceanSetting.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (factory != null) factory.close();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private OceanSettingDaoImpl dao(EntityManager em) {
        OceanSettingDaoImpl dao = new OceanSettingDaoImpl();
        dao.entityManager = em;
        return dao;
    }

    @Test
    void shouldPersistUpdateAndClear_withActorAndTimestamp() throws Exception {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var dao = dao(em);
            assertThat(dao.getSettings()).isNull();
            var first = dao.saveSettings("first synthetic blob", "1001");
            assertThat(first.getId()).isEqualTo(1);
            assertThat(first.getSettings()).isEqualTo("first synthetic blob");
            assertThat(first.getLastUpdateUser()).isEqualTo("1001");
            assertThat(first.getLastUpdateDate()).isNotNull();
            var updated = dao.saveSettings("second synthetic blob", "1002");
            assertThat(updated.getSettings()).isEqualTo("second synthetic blob");
            assertThat(updated.getLastUpdateUser()).isEqualTo("1002");
            assertThat(dao.saveSettings(null, "1003").getSettings()).isNull();
            em.getTransaction().commit();
        }
        // Recovery may replay a DDL migration: keep the audited singleton intact.
        try (var connection = DriverManager.getConnection(url);
             var reader = Files.newBufferedReader(Path.of("database/mysql/migration/common/V1.0.30__add_ocean_setting.sql"))) {
            RunScript.execute(connection, reader);
        }
        try (var em = factory.createEntityManager()) {
            var stored = dao(em).getSettings();
            assertThat(stored.getSettings()).isNull();
            assertThat(stored.getLastUpdateUser()).isEqualTo("1003");
        }
    }

    @Test
    void shouldKeepOneRow_whenFirstSavesRace() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> saveConcurrently("first", ready, start));
            var second = pool.submit(() -> saveConcurrently("second", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("second");
        } finally {
            start.countDown();
        }
        try (var em = factory.createEntityManager()) {
            assertThat(em.createQuery("select count(s) from OceanSetting s", Long.class).getSingleResult()).isEqualTo(1);
            var stored = dao(em).getSettings();
            assertThat(stored.getSettings()).isIn("first", "second");
            assertThat(stored.getLastUpdateUser()).isEqualTo(stored.getSettings());
        }
    }

    private String saveConcurrently(String value, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for peer");
            var saved = dao(em).saveSettings(value, value);
            em.getTransaction().commit();
            return saved.getSettings();
        }
    }

    @Test
    void shouldRejectOtherKeysAndMissingAuditFields_atDatabaseBoundary() throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("INSERT INTO OceanSetting VALUES (2, 'synthetic', '1001', CURRENT_TIMESTAMP)"))
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("CHK_OCEAN_SETTING_SINGLETON");
            assertThatThrownBy(() -> statement.execute("INSERT INTO OceanSetting(id,settings) VALUES (1,'synthetic')"))
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("NULL not allowed");
        }
    }

    @Test
    void shouldSerializeFirstDisplaySaves_withoutReplacingExistingSettings() throws Exception {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            dao(em).saveSettings("keep synthetic credentials", "initial-admin");
            em.getTransaction().commit();
        }
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> saveDisplayConcurrently(true, ready, start));
            var second = pool.submit(() -> saveDisplayConcurrently(false, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
        }
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            dao(em).saveDisplayPreference(false, "admin");
            dao(em).saveDisplayPreference(false, "admin");
            assertThat(((Number) em.createNativeQuery("SELECT COUNT(*) FROM SystemPreferences WHERE name='echart_show_ocean'")
                    .getSingleResult()).longValue()).isEqualTo(1);
            assertThat(em.createNativeQuery("SELECT `value` FROM SystemPreferences WHERE name='echart_show_ocean'")
                    .getSingleResult()).isEqualTo("false");
            assertThat(dao(em).getSettings().getSettings()).isEqualTo("keep synthetic credentials");
            assertThat(dao(em).getSettings().getLastUpdateUser()).isEqualTo("initial-admin");
            em.getTransaction().commit();
        }
    }

    private boolean saveDisplayConcurrently(boolean value, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for peer");
            dao(em).saveDisplayPreference(value, "admin");
            em.getTransaction().commit();
            return value;
        }
    }

    @Test
    void shouldExcludeCredentialBlob_whenEntityIsLogged() {
        var setting = new OceanSetting();
        setting.setSettings("synthetic-secret");
        assertThat(setting.toString()).doesNotContain("synthetic-secret");
    }
}
