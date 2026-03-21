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

import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialists;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialistsPK;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ServiceSpecialistsDao} covering persist,
 * findByServiceId, and findSpecialists.
 *
 * <p>Note: {@code findSpecialists} joins with ProfessionalSpecialist, so
 * it requires ProfessionalSpecialist records. These tests verify the
 * findByServiceId method which does not require cross-entity joins.</p>
 *
 * <p>Migrated from legacy {@code ServiceSpecialistsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ServiceSpecialistsDao
 */
@DisplayName("ServiceSpecialists Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ServiceSpecialistsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ServiceSpecialistsDao dao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a ProfessionalSpecialist record with the given ID to satisfy FK constraints.
     */
    private void ensureSpecialistExists(int specId) {
        String sql = "MERGE INTO professionalSpecialists (specId, fName, lName, deleted) KEY(specId) VALUES (?, 'Test', 'Specialist', 0)";
        entityManager.createNativeQuery(sql)
                .setParameter(1, specId)
                .executeUpdate();
        entityManager.flush();
    }

    private ServiceSpecialists createServiceSpecialist(int serviceId, int specId) {
        ensureSpecialistExists(specId);
        ServiceSpecialists entity = new ServiceSpecialists();
        entity.setId(new ServiceSpecialistsPK(serviceId, specId));
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist service specialists with composite key")
        void shouldPersistServiceSpecialists_whenCompositeKeyProvided() {
            ServiceSpecialists entity = createServiceSpecialist(1, 100);

            assertThat(entity.getId()).isNotNull();
            assertThat(entity.getId().getServiceId()).isEqualTo(1);
            assertThat(entity.getId().getSpecId()).isEqualTo(100);
        }

        @Test
        @Tag("read")
        @DisplayName("should find service specialists by composite key")
        void shouldFindServiceSpecialists_whenCompositeKeyProvided() {
            ServiceSpecialistsPK key = new ServiceSpecialistsPK(2, 200);
            createServiceSpecialist(2, 200);

            ServiceSpecialists found = dao.find(key);

            assertThat(found).isNotNull();
            assertThat(found.getId().getServiceId()).isEqualTo(2);
            assertThat(found.getId().getSpecId()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("findByServiceId")
    class FindByServiceId {

        @Test
        @Tag("query")
        @DisplayName("should return all specialists for matching service ID")
        void shouldReturnAllSpecialists_whenServiceIdMatches() {
            createServiceSpecialist(10, 301);
            createServiceSpecialist(10, 302);
            createServiceSpecialist(10, 303);
            createServiceSpecialist(20, 304);

            List<ServiceSpecialists> results = dao.findByServiceId(10);

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(s -> s.getId().getServiceId().equals(10));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no specialists for service ID")
        void shouldReturnEmptyList_whenNoSpecialistsForServiceId() {
            List<ServiceSpecialists> results = dao.findByServiceId(99999);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findSpecialists")
    class FindSpecialists {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no ProfessionalSpecialist records match")
        void shouldReturnEmptyList_whenNoProfessionalSpecialistRecordsMatch() {
            // Create service specialist mappings without corresponding ProfessionalSpecialist records
            createServiceSpecialist(30, 901);
            createServiceSpecialist(30, 902);

            List<Object[]> results = dao.findSpecialists(30);

            // Join with ProfessionalSpecialist will yield no results since no specialist records exist
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent service ID")
        void shouldReturnEmptyList_forNonExistentServiceId() {
            List<Object[]> results = dao.findSpecialists(99999);

            assertThat(results).isEmpty();
        }
    }
}
