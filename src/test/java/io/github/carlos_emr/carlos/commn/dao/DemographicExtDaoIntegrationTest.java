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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicExtDao} covering demographic
 * extension key-value CRUD, demographic-based lookups, and update operations.
 *
 * <p>Migrated from legacy {@code DemographicExtDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicExtDao
 */
@DisplayName("DemographicExtDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicExtDao demographicExtDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final int DEMO_1 = 70001;
    private static final int DEMO_2 = 70002;

    @BeforeEach
    void createParentDemographics() {
        String sql = "INSERT INTO demographic (demographic_no, first_name, last_name, sex, year_of_birth, month_of_birth, date_of_birth) "
                + "VALUES (?, 'Test', 'Patient', 'M', '1990', '01', '15') ON DUPLICATE KEY UPDATE "
                + "first_name=VALUES(first_name), last_name=VALUES(last_name), sex=VALUES(sex), "
                + "year_of_birth=VALUES(year_of_birth), month_of_birth=VALUES(month_of_birth), date_of_birth=VALUES(date_of_birth)";
        for (int demoNo : new int[]{DEMO_1, DEMO_2}) {
            entityManager.createNativeQuery(sql)
                    .setParameter(1, demoNo)
                    .executeUpdate();
        }
        entityManager.flush();
    }

    private DemographicExt createExt(int demographicNo, String key, String value) {
        DemographicExt ext = new DemographicExt();
        ext.setDemographicNo(demographicNo);
        ext.setProviderNo("999998");
        ext.setKey(key);
        ext.setValue(value);
        demographicExtDao.persist(ext);
        return ext;
    }

    private DemographicExt reportExt(int demographicNo, String key, String value, String timestamp) {
        DemographicExt ext = createExt(demographicNo, key, value);
        entityManager.flush();
        // Historical timestamps and NULLs must be seeded below the entity's current-time hooks.
        if (timestamp == null) {
            entityManager.createNativeQuery("update demographicExt set date_time=NULL where id=?1")
                    .setParameter(1, ext.getId()).executeUpdate();
        } else {
            entityManager.createNativeQuery("update demographicExt set date_time=?1 where id=?2")
                    .setParameter(1, java.sql.Timestamp.valueOf(timestamp))
                    .setParameter(2, ext.getId()).executeUpdate();
        }
        entityManager.clear();
        return ext;
    }

    @Test
    void shouldReturnTheLatestExtension_forEveryPatientInTheReportCohort() {
        String key = "report_cohort";
        reportExt(DEMO_1, key, "first old", "2026-08-01 00:00:00");
        var first = reportExt(DEMO_1, key, "first current", "2026-08-02 00:00:00");
        reportExt(DEMO_2, key, "second old", "2026-08-01 00:00:00");
        reportExt(DEMO_2, key, "second same-time older ID", "2026-08-03 00:00:00");
        var second = reportExt(DEMO_2, key, "second current", "2026-08-03 00:00:00");

        assertThat(demographicExtDao.findReportValues(key, List.of(DEMO_1, DEMO_2), true))
                .containsExactly(new DemographicExtDao.ReportValue(second.getId(), DEMO_2, "second current"),
                        new DemographicExtDao.ReportValue(first.getId(), DEMO_1, "first current"));
    }

    @Test
    void shouldPreferDatedValuesAndStableIds_whenReportHistoryContainsNullTimestamps() {
        String key = "report_null_dates";
        var dated = reportExt(DEMO_1, key, "dated", "2026-08-01 00:00:00");
        reportExt(DEMO_1, key, "undated higher ID", null);
        reportExt(DEMO_2, key, "undated old", null);
        var undated = reportExt(DEMO_2, key, "undated current", null);

        assertThat(demographicExtDao.findReportValues(key, List.of(DEMO_1, DEMO_2), true))
                .containsExactly(new DemographicExtDao.ReportValue(dated.getId(), DEMO_1, "dated"),
                        new DemographicExtDao.ReportValue(undated.getId(), DEMO_2, "undated current"));
    }

    @Test
    void shouldRetainEveryHistoricalValue_whenLatestOnlyIsFalse() {
        String key = "report_full_history";
        var first = reportExt(DEMO_1, key, "first", "2026-08-01 00:00:00");
        var second = reportExt(DEMO_2, key, "second", "2026-08-02 00:00:00");
        var later = reportExt(DEMO_1, key, "later", "2026-08-03 00:00:00");
        assertThat(demographicExtDao.findReportValues(key, List.of(DEMO_1, DEMO_2), false))
                .extracting(DemographicExtDao.ReportValue::id)
                .containsExactly(first.getId(), second.getId(), later.getId());
    }

    @Test
    void shouldExcludeOtherKeysAndPatients_fromTheLatestReportProjection() {
        String key = "report_isolation";
        var own = reportExt(DEMO_1, key, "owned", "2026-08-01 00:00:00");
        reportExt(DEMO_2, key, "other patient", "2026-08-03 00:00:00");
        reportExt(DEMO_1, "other_report_key", "other key", "2026-08-03 00:00:00");
        assertThat(demographicExtDao.findReportValues(key, List.of(DEMO_1), true))
                .containsExactly(new DemographicExtDao.ReportValue(own.getId(), DEMO_1, "owned"));
    }

    @Test
    void shouldReturnNoReportValues_forAnEmptyCohort() {
        assertThat(demographicExtDao.findReportValues("report_empty", List.of(), true)).isEmpty();
        assertThat(demographicExtDao.findReportValues("report_empty", List.of(), false)).isEmpty();
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist demographic extension with generated ID")
        void shouldPersistDemographicExt_whenValidDataProvided() {
            DemographicExt ext = createExt(DEMO_1, "aboriginal", "yes");
            assertThat(ext.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find demographic extension by ID")
        void shouldFindDemographicExt_whenValidIdProvided() {
            DemographicExt saved = createExt(DEMO_1, "ethnicity", "Canadian");
            DemographicExt found = demographicExtDao.getDemographicExt(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getKey()).isEqualTo("ethnicity");
            assertThat(found.getValue()).isEqualTo("Canadian");
        }

        @Test
        @Tag("update")
        @DisplayName("should update demographic extension value")
        void shouldUpdateDemographicExt_whenModified() {
            DemographicExt saved = createExt(DEMO_1, "language", "en");
            saved.setValue("fr");
            demographicExtDao.updateDemographicExt(saved);
            DemographicExt found = demographicExtDao.getDemographicExt(saved.getId());
            assertThat(found.getValue()).isEqualTo("fr");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createExt(DEMO_1, "aboriginal", "yes");
            createExt(DEMO_1, "ethnicity", "Canadian");
            createExt(DEMO_1, "language", "en");
            createExt(DEMO_2, "aboriginal", "no");
            createExt(DEMO_2, "language", "fr");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by demographic number")
        void shouldFindExtensions_byDemographicNo() {
            List<DemographicExt> results = demographicExtDao.getDemographicExtByDemographicNo(DEMO_1);
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find extension by demographic number and key")
        void shouldFindExtension_byDemographicNoAndKey() {
            DemographicExt result = demographicExtDao.getDemographicExt(DEMO_1, "ethnicity");
            assertThat(result).isNotNull();
            assertThat(result.getValue()).isEqualTo("Canadian");
        }

        @Test
        @Tag("query")
        @DisplayName("should find latest extension by demographic number and key")
        void shouldFindLatestExtension_byDemographicNoAndKey() {
            DemographicExt result = demographicExtDao.getLatestDemographicExt(DEMO_1, "language");
            assertThat(result).isNotNull();
            assertThat(result.getValue()).isEqualTo("en");
        }

        @Test
        @Tag("query")
        @DisplayName("should find extensions by key and value")
        void shouldFindExtensions_byKeyAndValue() {
            List<DemographicExt> results = demographicExtDao.getDemographicExtByKeyAndValue("aboriginal", "yes");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDemographicNo()).isEqualTo(DEMO_1);
        }

        @Test
        @Tag("query")
        @DisplayName("should get all values as map for demographic")
        void shouldGetAllValues_asMapForDemographic() {
            HashMap<String, String> map = (HashMap<String, String>) demographicExtDao.getAllValuesForDemo(DEMO_1);
            assertThat(map).containsEntry("aboriginal", "yes");
            assertThat(map).containsEntry("ethnicity", "Canadian");
            assertThat(map).containsEntry("language", "en");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null for non-existent key")
        void shouldReturnNull_whenKeyNotFound() {
            DemographicExt result = demographicExtDao.getDemographicExt(DEMO_1, "nonexistent");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Save shortcut operations")
    class SaveShortcutOperations {

        @Test
        @Tag("create")
        @DisplayName("should save demographic ext via shortcut method")
        void shouldSaveDemographicExt_viaShortcutMethod() {
            demographicExtDao.saveDemographicExt(DEMO_1, "test_key", "test_value");
            DemographicExt found = demographicExtDao.getDemographicExt(DEMO_1, "test_key");
            assertThat(found).isNotNull();
            assertThat(found.getValue()).isEqualTo("test_value");
        }
    }
    @Test
    void shouldSkipCurrentExclusion_forTheSamePatientProviderAndIndicator() {
        java.util.Date since = new java.util.Date(System.currentTimeMillis() - 86400000L);
        assertThat(demographicExtDao.addKeyIfAbsentSince("999998", DEMO_1, "excludeIndicator", "owned", since)).isTrue();
        assertThat(demographicExtDao.addKeyIfAbsentSince("999998", DEMO_1, "excludeIndicator", "owned", since)).isFalse();
        assertThat(demographicExtDao.addKeyIfAbsentSince("999999", DEMO_1, "excludeIndicator", "owned", since)).isTrue();
        assertThat(demographicExtDao.addKeyIfAbsentSince("999998", DEMO_2, "excludeIndicator", "owned", since)).isTrue();
        assertThat(demographicExtDao.addKeyIfAbsentSince("999998", DEMO_1, "excludeIndicator", "different", since)).isTrue();
        assertThat(demographicExtDao.getDemographicExtByKeyAndValue("excludeIndicator", "owned")).hasSize(3);
    }

    @Test
    void shouldKeepHistoricalExclusion_whenAReplacementIsCreated() {
        DemographicExt old = createExt(DEMO_1, "excludeIndicator", "owned");
        entityManager.flush();
        // Entity lifecycle callbacks refresh dateCreated on every entity update.
        // Seed a historical database row directly, as it would exist from a prior year.
        entityManager.createNativeQuery("UPDATE demographicExt SET date_time=?1 WHERE id=?2")
                .setParameter(1, new java.sql.Timestamp(0))
                .setParameter(2, old.getId()).executeUpdate();
        entityManager.clear();
        java.util.Date since = new java.util.Date(System.currentTimeMillis() - 86400000L);
        assertThat(demographicExtDao.addKeyIfAbsentSince("999998", DEMO_1, "excludeIndicator", "owned", since)).isTrue();
        assertThat(demographicExtDao.getDemographicExtByKeyAndValue("excludeIndicator", "owned")).hasSize(2);
    }

    @Test
    void shouldMatchNullProvider_whenAnUnassignedExclusionAlreadyExists() {
        java.util.Date since = new java.util.Date(System.currentTimeMillis() - 86400000L);
        assertThat(demographicExtDao.addKeyIfAbsentSince(null, DEMO_1, "excludeIndicator", "owned", since)).isTrue();
        assertThat(demographicExtDao.addKeyIfAbsentSince(null, DEMO_1, "excludeIndicator", "owned", since)).isFalse();
    }

    @Test
    void shouldSkipMissingPatient_whenAddingACurrentExclusion() {
        assertThat(demographicExtDao.addKeyIfAbsentSince("999998", Integer.MAX_VALUE,
                "excludeIndicator", "owned", new java.util.Date(0))).isFalse();
        assertThat(demographicExtDao.getDemographicExtByKeyAndValue("excludeIndicator", "owned")).isEmpty();
    }

}
