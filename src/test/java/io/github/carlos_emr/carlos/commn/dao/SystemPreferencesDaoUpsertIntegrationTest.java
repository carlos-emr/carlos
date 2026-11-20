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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS;

import jakarta.persistence.EntityManager;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * H2 tests for {@link SystemPreferencesDao#upsertPreference}: it inserts a row only when none
 * exists, updates in place otherwise, and brings pre-existing duplicate rows to the same value
 * (the {@code name} column has no unique key).
 *
 * @since 2026-10-01
 */
@Tag("integration")
@Tag("dao")
@Tag("update")
@DisplayName("SystemPreferencesDao upsertPreference")
class SystemPreferencesDaoUpsertIntegrationTest {

    private static final LAB_DISPLAY_PREFERENCE_KEYS KEY = LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_max_size;

    private SessionFactory factory;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:sysprefs-" + UUID.randomUUID()
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
                .setProperty("hibernate.show_sql", "false")
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

    private static SystemPreferencesDaoImpl dao(EntityManager em) {
        SystemPreferencesDaoImpl dao = new SystemPreferencesDaoImpl();
        dao.entityManager = em;
        return dao;
    }

    private void upsert(String value) {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            dao(em).upsertPreference(KEY, value);
            em.getTransaction().commit();
        }
    }

    private List<Object[]> rows() {
        try (var em = factory.createEntityManager()) {
            return em.createQuery("SELECT sp.value, sp.updateDate FROM SystemPreferences sp WHERE sp.name = ?1 "
                    + "ORDER BY sp.id", Object[].class).setParameter(1, KEY.name()).getResultList();
        }
    }

    @Test
    @DisplayName("should insert one row on the first save and update it afterwards")
    void shouldInsertOnceThenUpdate_whenSavedRepeatedly() {
        upsert("1048576");
        assertThat(rows()).hasSize(1);

        upsert("2097152");
        upsert("2097152");

        List<Object[]> rows = rows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("2097152");
        assertThat(rows.get(0)[1]).isNotNull();
    }

    @Test
    @DisplayName("should bring every pre-existing duplicate row to the saved value without adding one")
    void shouldUpdateEveryDuplicate_whenRowsAlreadyDuplicated() throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO SystemPreferences (name, `value`, updateDate) VALUES "
                    + "('lab_pdf_max_size', '5', NULL), ('lab_pdf_max_size', '7', NULL), "
                    + "('lab_pdf_inline_preview', 'true', NULL)");
        }

        upsert("3145728");

        List<Object[]> rows = rows();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row[0]).isEqualTo("3145728");
            assertThat(row[1]).isNotNull();
        });
        try (var em = factory.createEntityManager()) {
            assertThat(dao(em).findPreferenceByName(LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview).getValue())
                    .as("other preference rows are untouched")
                    .isEqualTo("true");
        }
    }
}
