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
import io.github.carlos_emr.carlos.commn.model.Allergy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AllergyDao} covering CRUD, demographic-based
 * queries, active/archived filtering, and date-based lookups.
 *
 * <p>Migrated from legacy {@code AllergyDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see AllergyDao
 */
@DisplayName("AllergyDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("allergy")
@Transactional
public class AllergyDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private AllergyDao allergyDao;

    private static final int DEMO_1 = 10001;
    private static final int DEMO_2 = 10002;
    private static final int DEMO_3 = 10003;

    private Allergy createAllergy(int demographicNo, boolean archived) {
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(demographicNo);
        allergy.setDescription("Test Allergy " + System.nanoTime());
        allergy.setArchived(archived);
        allergy.setEntryDate(new Date());
        allergy.setTypeCode(0);
        allergy.setSeverityOfReaction("1");
        allergy.setOnsetOfReaction("1");
        allergy.setReaction("Test Reaction");
        allergy.setStartDate(new Date());
        allergy.setLastUpdateDate(new Date());
        allergyDao.persist(allergy);
        return allergy;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist allergy with generated ID")
        void shouldPersistAllergy_whenValidDataProvided() {
            Allergy allergy = createAllergy(DEMO_1, false);
            assertThat(allergy.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find allergy by ID")
        void shouldFindAllergy_whenValidIdProvided() {
            Allergy saved = createAllergy(DEMO_1, false);
            Allergy found = allergyDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getDemographicNo()).isEqualTo(DEMO_1);
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove allergy by ID")
        void shouldRemoveAllergy_whenValidIdProvided() {
            Allergy saved = createAllergy(DEMO_1, false);
            Integer id = saved.getId();
            allergyDao.remove(id);
            hibernateTemplate.flush();
            Allergy found = allergyDao.find(id);
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should count all allergies")
        void shouldCountAllAllergies_afterPersist() {
            createAllergy(DEMO_1, false);
            createAllergy(DEMO_2, false);
            long count = allergyDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findAllergies (by demographic)")
    class FindAllergiesByDemographic {

        @BeforeEach
        void setUp() {
            createAllergy(DEMO_1, false);
            createAllergy(DEMO_1, false);
            createAllergy(DEMO_2, false);
        }

        @Test
        @Tag("query")
        @DisplayName("should return allergies for specific demographic")
        void shouldReturnAllergies_forSpecificDemographic() {
            List<Allergy> results = allergyDao.findAllergies(DEMO_1);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(a -> a.getDemographicNo() == DEMO_1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return single allergy for demographic with one record")
        void shouldReturnSingleAllergy_forDemographicWithOneRecord() {
            List<Allergy> results = allergyDao.findAllergies(DEMO_2);
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for demographic with no allergies")
        void shouldReturnEmptyList_forDemographicWithNoAllergies() {
            List<Allergy> results = allergyDao.findAllergies(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveAllergies (non-archived)")
    class FindActiveAllergies {

        @BeforeEach
        void setUp() {
            createAllergy(DEMO_3, false);
            createAllergy(DEMO_3, false);
            createAllergy(DEMO_3, true);
        }

        @Test
        @Tag("filter")
        @DisplayName("should return only non-archived allergies")
        void shouldReturnOnlyNonArchivedAllergies_forDemographic() {
            List<Allergy> results = allergyDao.findActiveAllergies(DEMO_3);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(a -> !a.getArchived());
        }

        @Test
        @Tag("filter")
        @DisplayName("should return empty when all allergies are archived")
        void shouldReturnEmpty_whenAllAllergiesAreArchived() {
            int demoArchived = 10099;
            createAllergy(demoArchived, true);
            createAllergy(demoArchived, true);
            List<Allergy> results = allergyDao.findActiveAllergies(demoArchived);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUpdateDate (date-range queries)")
    class FindByUpdateDate {

        @Test
        @Tag("query")
        @DisplayName("should find allergies updated after yesterday")
        void shouldFindAllergies_updatedAfterYesterday() {
            createAllergy(DEMO_1, false);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            List<Allergy> results = allergyDao.findByUpdateDate(cal.getTime(), 99);
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when searching for future updates")
        void shouldReturnEmpty_whenSearchingForFutureUpdates() {
            createAllergy(DEMO_1, false);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            List<Allergy> results = allergyDao.findByUpdateDate(cal.getTime(), 99);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should respect items-to-return limit")
        void shouldRespectLimit_whenMultipleResultsExist() {
            for (int i = 0; i < 5; i++) {
                createAllergy(DEMO_1, false);
            }
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            List<Allergy> results = allergyDao.findByUpdateDate(cal.getTime(), 2);
            assertThat(results).hasSize(2);
        }
    }
}
