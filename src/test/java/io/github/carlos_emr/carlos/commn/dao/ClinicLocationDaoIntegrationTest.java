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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ClinicLocationDao}.
 *
 * <p>Migrated from legacy {@code ClinicLocationDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ClinicLocationDao
 */
@DisplayName("ClinicLocation Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class ClinicLocationDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ClinicLocationDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist clinic location with generated ID")
        void shouldPersistClinicLocation_whenValidDataProvided() throws Exception {
            ClinicLocation entity = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find clinic locations by clinic number")
        void shouldFindClinicLocations_byClinicNo() throws Exception {
            int clinicNo1 = 101;
            int clinicNo2 = 202;

            ClinicLocation clinicLocation1 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation1);
            clinicLocation1.setClinicNo(clinicNo1);
            dao.persist(clinicLocation1);

            ClinicLocation clinicLocation2 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation2);
            clinicLocation2.setClinicNo(clinicNo2);
            dao.persist(clinicLocation2);

            ClinicLocation clinicLocation3 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation3);
            clinicLocation3.setClinicNo(clinicNo1);
            dao.persist(clinicLocation3);

            List<ClinicLocation> expectedResult = Arrays.asList(clinicLocation1, clinicLocation3);
            List<ClinicLocation> result = dao.findByClinicNo(clinicNo1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should search visit location name by clinic location number")
        void shouldSearchVisitLocation_byClinicLocationNo() throws Exception {
            String clinicNo1 = "101";
            String clinicNo2 = "202";
            String clinicNo3 = "303";

            String clinicLocationName1 = "alpha";
            String clinicLocationName2 = "bravo";
            String clinicLocationName3 = "charlie";

            ClinicLocation clinicLocation1 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation1);
            clinicLocation1.setClinicLocationNo(clinicNo1);
            clinicLocation1.setClinicLocationName(clinicLocationName1);
            dao.persist(clinicLocation1);

            ClinicLocation clinicLocation2 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation2);
            clinicLocation2.setClinicLocationNo(clinicNo2);
            clinicLocation2.setClinicLocationName(clinicLocationName2);
            dao.persist(clinicLocation2);

            ClinicLocation clinicLocation3 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation3);
            clinicLocation3.setClinicLocationNo(clinicNo3);
            clinicLocation3.setClinicLocationName(clinicLocationName3);
            dao.persist(clinicLocation3);

            String result = dao.searchVisitLocation(clinicNo2);

            assertThat(result).isEqualTo(clinicLocationName2);
        }

        @Test
        @Tag("query")
        @DisplayName("should search bill location by clinic no and clinic location no")
        void shouldSearchBillLocation_byClinicNoAndClinicLocationNo() throws Exception {
            int clinicNo1 = 101;
            int clinicNo2 = 202;
            int clinicNo3 = 303;

            String clinicLocationNo1 = "111";
            String clinicLocationNo2 = "222";
            String clinicLocationNo3 = "333";

            ClinicLocation clinicLocation1 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation1);
            clinicLocation1.setClinicNo(clinicNo1);
            clinicLocation1.setClinicLocationNo(clinicLocationNo1);
            dao.persist(clinicLocation1);

            ClinicLocation clinicLocation2 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation2);
            clinicLocation2.setClinicNo(clinicNo2);
            clinicLocation2.setClinicLocationNo(clinicLocationNo2);
            dao.persist(clinicLocation2);

            ClinicLocation clinicLocation3 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation3);
            clinicLocation3.setClinicNo(clinicNo3);
            clinicLocation3.setClinicLocationNo(clinicLocationNo3);
            dao.persist(clinicLocation3);

            ClinicLocation result = dao.searchBillLocation(clinicNo2, clinicLocationNo2);

            assertThat(result).isEqualTo(clinicLocation2);
        }
    }

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @Tag("delete")
        @DisplayName("should remove clinic locations by clinic location number")
        void shouldRemoveClinicLocations_byClinicLocationNo() throws Exception {
            String clinicLocationNo1 = "111";
            String clinicLocationNo2 = "222";
            String clinicLocationNo3 = "333";

            ClinicLocation clinicLocation1 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation1);
            clinicLocation1.setClinicLocationNo(clinicLocationNo1);
            dao.persist(clinicLocation1);

            ClinicLocation clinicLocation2 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation2);
            clinicLocation2.setClinicLocationNo(clinicLocationNo2);
            dao.persist(clinicLocation2);

            ClinicLocation clinicLocation3 = new ClinicLocation();
            EntityDataGenerator.generateTestDataForModelClass(clinicLocation3);
            clinicLocation3.setClinicLocationNo(clinicLocationNo3);
            dao.persist(clinicLocation3);

            dao.removeByClinicLocationNo(clinicLocationNo2);

            List<ClinicLocation> expectedResult = Arrays.asList(clinicLocation1, clinicLocation3);
            List<ClinicLocation> result = dao.findAll();

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
