/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.DateSearchType;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDaoImpl;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS;
import java.sql.DriverManager;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves the inbox date mode through the real {@link SystemPreferencesDaoImpl} on an H2 copy of
 * the {@code SystemPreferences} table, so the preference key, the stored column and the mapping
 * are exercised together (issue #5772).
 * @since 2026-11-19
 */
@Tag("integration")
@Tag("lab")
@DisplayName("InboxDateSearchTypeResolver against stored system preferences")
class InboxDateSearchTypeResolverIntegrationTest {
    private SessionFactory factory;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:inboxdates-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE";
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            // Mirrors V1__baseline_schema.sql: no unique key on name.
            statement.execute("CREATE TABLE SystemPreferences (id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(40), `value` VARCHAR(255), updateDate DATETIME)");
        }
        factory = new Configuration().addAnnotatedClass(SystemPreferences.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (factory != null) {
            factory.close();
        }
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private void store(String value) {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            dao(em).upsertPreference(LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType, value);
            em.getTransaction().commit();
        }
    }

    private DateSearchType resolve() {
        try (var em = factory.createEntityManager()) {
            return new InboxDateSearchTypeResolver(dao(em)).resolve();
        }
    }

    private static SystemPreferencesDaoImpl dao(jakarta.persistence.EntityManager em) {
        SystemPreferencesDaoImpl dao = new SystemPreferencesDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        return dao;
    }

    @Test
    void shouldSelectObservationDates_whenThePreferenceWasNeverSaved() {
        assertThat(resolve()).isEqualTo(DateSearchType.SERVICE_OBSERVATION);
    }

    @Test
    void shouldFollowTheStoredPreference_whenItChanges() {
        store("receivedCreated");
        assertThat(resolve()).isEqualTo(DateSearchType.RECEIVED_CREATED);

        store("serviceObservation");
        assertThat(resolve()).isEqualTo(DateSearchType.SERVICE_OBSERVATION);

        store("");
        assertThat(resolve()).isEqualTo(DateSearchType.SERVICE_OBSERVATION);
    }
}
