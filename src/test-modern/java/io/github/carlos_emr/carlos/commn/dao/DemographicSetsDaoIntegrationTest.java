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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.DemographicSets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicSetsDao} covering persist,
 * findBySetName, findBySetNames, findBySetNameAndEligibility,
 * findSetNamesByDemographicNo, and findSetNames.
 *
 * <p>Migrated from legacy {@code DemographicSetsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicSetsDao
 */
@DisplayName("DemographicSetsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicSetsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicSetsDao dao;

    private DemographicSets createSet(String name, String archive) throws Exception {
        DemographicSets entity = new DemographicSets();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setName(name);
        entity.setArchive(archive);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist set with generated ID")
    void shouldPersistSet_whenValidDataProvided() throws Exception {
        DemographicSets entity = new DemographicSets();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("findBySetName")
    class FindBySetName {

        @Test
        @Tag("query")
        @DisplayName("should return non-archived sets matching name")
        void shouldReturnNonArchivedSets_forMatchingName() throws Exception {
            createSet("a", "0");
            createSet("a", "0");

            List<DemographicSets> result = dao.findBySetName("a");

            assertThat(result).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should exclude archived sets from results")
        void shouldExcludeArchivedSets_fromResults() throws Exception {
            createSet("a", "0");
            createSet("a", "1");

            List<DemographicSets> result = dao.findBySetName("a");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findBySetNames")
    class FindBySetNames {

        @Test
        @Tag("query")
        @DisplayName("should return non-archived sets matching any of the provided names")
        void shouldReturnNonArchivedSets_forMultipleNames() throws Exception {
            createSet("a", "0");
            createSet("b", "0");

            List<String> names = Arrays.asList("a", "b");
            List<DemographicSets> result = dao.findBySetNames(names);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findBySetNameAndEligibility")
    class FindBySetNameAndEligibility {

        @Test
        @Tag("query")
        @DisplayName("should return sets matching name and eligibility")
        void shouldReturnSets_forMatchingNameAndEligibility() throws Exception {
            DemographicSets entity1 = new DemographicSets();
            EntityDataGenerator.generateTestDataForModelClass(entity1);
            entity1.setName("a");
            entity1.setEligibility("0");
            dao.persist(entity1);

            DemographicSets entity2 = new DemographicSets();
            EntityDataGenerator.generateTestDataForModelClass(entity2);
            entity2.setName("a");
            entity2.setEligibility("1");
            dao.persist(entity2);

            hibernateTemplate.flush();

            assertThat(dao.findBySetNameAndEligibility("a", "0")).hasSize(1);
            assertThat(dao.findBySetNameAndEligibility("a", "1")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findSetNamesByDemographicNo")
    class FindSetNamesByDemographicNo {

        @Test
        @Tag("query")
        @DisplayName("should return distinct set names for demographic with archived status")
        void shouldReturnDistinctSetNames_forDemographic() throws Exception {
            DemographicSets entity1 = new DemographicSets();
            EntityDataGenerator.generateTestDataForModelClass(entity1);
            entity1.setName("a");
            entity1.setDemographicNo(1);
            entity1.setArchive("1");
            dao.persist(entity1);

            DemographicSets entity2 = new DemographicSets();
            EntityDataGenerator.generateTestDataForModelClass(entity2);
            entity2.setName("b");
            entity2.setDemographicNo(1);
            entity2.setArchive("1");
            dao.persist(entity2);

            hibernateTemplate.flush();

            List<String> names = dao.findSetNamesByDemographicNo(1);

            assertThat(names).hasSize(2);
            assertThat(names).contains("a", "b");
        }
    }

    @Nested
    @DisplayName("findSetNames")
    class FindSetNames {

        @Test
        @Tag("query")
        @DisplayName("should return distinct set names across all sets")
        void shouldReturnDistinctSetNames_acrossAllSets() throws Exception {
            createSet("a", "0");
            createSet("b", "0");
            // duplicate name - should not increase distinct count
            createSet("b", "0");

            List<String> names = dao.findSetNames();

            assertThat(names).hasSize(2);
            assertThat(names).contains("a", "b");
        }
    }
}
