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
import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LabPatientPhysicianInfoDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code LabPatientPhysicianInfoDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see LabPatientPhysicianInfoDao
 */
@DisplayName("LabPatientPhysicianInfo Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class LabPatientPhysicianInfoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LabPatientPhysicianInfoDao labPatientPhysicianInfoDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist labpatientphysicianinfo with generated ID")
        void shouldPersistLabPatientPhysicianInfo_whenValidDataProvided() throws Exception {
            LabPatientPhysicianInfo entity = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            labPatientPhysicianInfoDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find labpatientphysicianinfo by ID")
        void shouldFindLabPatientPhysicianInfo_whenValidIdProvided() throws Exception {
            LabPatientPhysicianInfo saved = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            labPatientPhysicianInfoDao.persist(saved);
            LabPatientPhysicianInfo found = labPatientPhysicianInfoDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all labpatientphysicianinfo records")
        void shouldCountAllLabPatientPhysicianInfos() throws Exception {
            LabPatientPhysicianInfo entity = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            labPatientPhysicianInfoDao.persist(entity);
            long count = labPatientPhysicianInfoDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }

    /**
     * {@code findByPatientName} backs the legacy CML/MDS Inbox search. A patient without a health
     * card number (NULL HIN) must still be found when the HIN search field is blank, and must not
     * match a non-blank HIN search.
     */
    @Nested
    @DisplayName("Patient search without a health card number")
    class PatientSearchWithoutHealthNumber {

        private static final String PROVIDER = "999998";
        private static final String LAB_TYPE = "CML";

        private int routeLabWithoutHin(String lastName) throws Exception {
            LabPatientPhysicianInfo info = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(info);
            info.setPatientLastName(lastName);
            info.setPatientFirstName("Fixture");
            info.setPatientHin(null);
            labPatientPhysicianInfoDao.persist(info);

            ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
            routing.setLabNo(info.getId());
            routing.setLabType(LAB_TYPE);
            routing.setProviderNo(PROVIDER);
            routing.setStatus("N");
            entityManager.persist(routing);
            entityManager.flush();
            return info.getId();
        }

        @Test
        @Tag("search")
        @DisplayName("should find a lab by name when the patient has no HIN and the HIN field is blank")
        void shouldFindLab_whenPatientHinIsNull() throws Exception {
            int labNo = routeLabWithoutHin("Nullhincml");

            List<Object[]> results = labPatientPhysicianInfoDao.findByPatientName("N", LAB_TYPE, PROVIDER,
                    "Nullhincml", "", "");

            assertThat(results).extracting(row -> ((LabPatientPhysicianInfo) row[0]).getId()).containsExactly(labNo);
        }

        @Test
        @Tag("search")
        @DisplayName("should not match a patient without a HIN when a health number is searched")
        void shouldExcludeNullHinPatient_whenHealthNumberIsSearched() throws Exception {
            routeLabWithoutHin("Nullhincmlsearched");

            assertThat(labPatientPhysicianInfoDao.findByPatientName("N", LAB_TYPE, PROVIDER,
                    "Nullhincmlsearched", "", "12345")).isEmpty();
        }
    }
}
