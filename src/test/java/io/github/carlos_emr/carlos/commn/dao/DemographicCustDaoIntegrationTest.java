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
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for {@link DemographicCustDao} covering patient
 * customization data CRUD, midwife/resident/nurse assignment lookups.
 *
 * <p>Migrated from legacy {@code DemographicCustDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicCustDao
 */
@DisplayName("DemographicCustDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicCustDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicCustDao demographicCustDao;

    private DemographicCust createCust(int demographicNo, String nurse, String resident, String midwife) {
        DemographicCust cust = new DemographicCust();
        cust.setId(demographicNo);
        cust.setNurse(nurse);
        cust.setResident(resident);
        cust.setMidwife(midwife);
        demographicCustDao.persist(cust);
        return cust;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist demographic customization")
        void shouldPersistDemographicCust_whenValidDataProvided() {
            DemographicCust cust = createCust(80001, "NurseA", "ResidentB", "MidwifeC");
            DemographicCust found = demographicCustDao.find(80001);
            assertThat(found).isNotNull();
            assertThat(found.getNurse()).isEqualTo("NurseA");
            assertThat(found.getResident()).isEqualTo("ResidentB");
            assertThat(found.getMidwife()).isEqualTo("MidwifeC");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for non-existent demographic")
        void shouldReturnNull_whenDemographicNotFound() {
            DemographicCust found = demographicCustDao.find(99999);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Provider assignment queries")
    class ProviderAssignmentQueries {

        @BeforeEach
        void setUp() {
            createCust(80001, "NurseA", "ResidentX", "MidwifeP");
            createCust(80002, "NurseB", "ResidentX", "MidwifeQ");
            createCust(80003, "NurseA", "ResidentY", "MidwifeP");
        }

        @Test
        @Tag("query")
        @DisplayName("should find demographics by midwife assignment")
        void shouldFindDemographics_byMidwifeAssignment() {
            List<DemographicCust> results = demographicCustDao.findMultipleMidwife(
                    Arrays.asList(80001, 80002, 80003), "MidwifeP");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find demographics by resident assignment")
        void shouldFindDemographics_byResidentAssignment() {
            List<DemographicCust> results = demographicCustDao.findMultipleResident(
                    Arrays.asList(80001, 80002, 80003), "ResidentX");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find demographics by nurse assignment")
        void shouldFindDemographics_byNurseAssignment() {
            List<DemographicCust> results = demographicCustDao.findMultipleNurse(
                    Arrays.asList(80001, 80002, 80003), "NurseA");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-matching assignment")
        void shouldReturnEmptyList_whenNoMatchingAssignment() {
            List<DemographicCust> results = demographicCustDao.findMultipleMidwife(
                    Arrays.asList(80001, 80002), "NonExistentMidwife");
            assertThat(results).isEmpty();
        }
    }
}
