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
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
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
 * Integration tests for {@link BillingONItemDao}.
 *
 * <p>Migrated from legacy {@code BillingONItemDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see BillingONItemDao
 */
@DisplayName("BillingONItem Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingONItemDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONItemDao dao;

    @Autowired
    private BillingONCHeader1Dao bONCH1Dao;

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should get billing items by ch1 ID")
        void shouldGetBillingItems_byCh1Id() throws Exception {
            int ch1Id1 = 101, ch1Id2 = 202;

            BillingONItem bONI1 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI1);
            bONI1.setCh1Id(ch1Id2);
            dao.persist(bONI1);

            BillingONItem bONI2 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI2);
            bONI2.setCh1Id(ch1Id1);
            dao.persist(bONI2);

            BillingONItem bONI3 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI3);
            bONI3.setCh1Id(ch1Id2);
            dao.persist(bONI3);

            BillingONItem bONI4 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI4);
            bONI4.setCh1Id(ch1Id1);
            dao.persist(bONI4);

            BillingONItem bONI5 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI5);
            bONI5.setCh1Id(ch1Id1);
            dao.persist(bONI5);

            List<BillingONItem> expectedResult = Arrays.asList(bONI2, bONI4, bONI5);
            List<BillingONItem> result = dao.getBillingItemByCh1Id(ch1Id1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should get active billing items by ch1 ID excluding deleted status")
        void shouldGetActiveBillingItems_byCh1Id() throws Exception {
            int ch1Id1 = 101, ch1Id2 = 202;
            String status1 = "D", status2 = "Active";

            BillingONItem bONI1 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI1);
            bONI1.setCh1Id(ch1Id2);
            bONI1.setStatus(status1);
            dao.persist(bONI1);

            BillingONItem bONI2 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI2);
            bONI2.setCh1Id(ch1Id1);
            bONI2.setStatus(status2);
            dao.persist(bONI2);

            BillingONItem bONI3 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI3);
            bONI3.setCh1Id(ch1Id2);
            bONI3.setStatus(status2);
            dao.persist(bONI3);

            BillingONItem bONI4 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI4);
            bONI4.setCh1Id(ch1Id1);
            bONI4.setStatus(status1);
            dao.persist(bONI4);

            BillingONItem bONI5 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI5);
            bONI5.setCh1Id(ch1Id1);
            bONI5.setStatus(status2);
            dao.persist(bONI5);

            BillingONItem bONI6 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI6);
            bONI6.setCh1Id(ch1Id1);
            bONI6.setStatus(status2);
            dao.persist(bONI6);

            List<BillingONItem> expectedResult = Arrays.asList(bONI2, bONI5, bONI6);
            List<BillingONItem> result = dao.getActiveBillingItemByCh1Id(ch1Id1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should get ch1 headers by demographic number")
        void shouldGetCh1Headers_byDemographicNo() throws Exception {
            Integer demographicNo1 = 101, demographicNo2 = 202;

            BillingONCHeader1 bONCH11 = new BillingONCHeader1();
            EntityDataGenerator.generateTestDataForModelClass(bONCH11);
            bONCH11.setDemographicNo(demographicNo1);
            bONCH1Dao.persist(bONCH11);

            BillingONCHeader1 bONCH12 = new BillingONCHeader1();
            EntityDataGenerator.generateTestDataForModelClass(bONCH12);
            bONCH12.setDemographicNo(demographicNo2);
            bONCH1Dao.persist(bONCH12);

            BillingONCHeader1 bONCH13 = new BillingONCHeader1();
            EntityDataGenerator.generateTestDataForModelClass(bONCH13);
            bONCH13.setDemographicNo(demographicNo1);
            bONCH1Dao.persist(bONCH13);

            BillingONCHeader1 bONCH14 = new BillingONCHeader1();
            EntityDataGenerator.generateTestDataForModelClass(bONCH14);
            bONCH14.setDemographicNo(demographicNo1);
            bONCH1Dao.persist(bONCH14);

            BillingONCHeader1 bONCH15 = new BillingONCHeader1();
            EntityDataGenerator.generateTestDataForModelClass(bONCH15);
            bONCH15.setDemographicNo(demographicNo2);
            bONCH1Dao.persist(bONCH15);

            List<BillingONCHeader1> expectedResult = Arrays.asList(bONCH11, bONCH13, bONCH14);
            List<BillingONCHeader1> result = dao.getCh1ByDemographicNo(demographicNo1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i).getDemographicNo()).isEqualTo(expectedResult.get(i).getDemographicNo());
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should find billing items by ch1 ID with non-deleted and non-settled status")
        void shouldFindBillingItems_byCh1IdWithActiveStatus() throws Exception {
            int ch1Id1 = 101, ch1Id2 = 202;
            String status1 = "D", status2 = "N", status3 = "S";

            BillingONItem bONI1 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI1);
            bONI1.setCh1Id(ch1Id2);
            bONI1.setStatus(status1);
            dao.persist(bONI1);

            BillingONItem bONI2 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI2);
            bONI2.setCh1Id(ch1Id1);
            bONI2.setStatus(status2);
            dao.persist(bONI2);

            BillingONItem bONI3 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI3);
            bONI3.setCh1Id(ch1Id2);
            bONI3.setStatus(status2);
            dao.persist(bONI3);

            BillingONItem bONI4 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI4);
            bONI4.setCh1Id(ch1Id1);
            bONI4.setStatus(status1);
            dao.persist(bONI4);

            BillingONItem bONI5 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI5);
            bONI5.setCh1Id(ch1Id1);
            bONI5.setStatus(status2);
            dao.persist(bONI5);

            BillingONItem bONI6 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI6);
            bONI6.setCh1Id(ch1Id1);
            bONI6.setStatus(status2);
            dao.persist(bONI6);

            BillingONItem bONI7 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI7);
            bONI7.setCh1Id(ch1Id1);
            bONI7.setStatus(status3);
            dao.persist(bONI7);

            BillingONItem bONI8 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI8);
            bONI8.setCh1Id(ch1Id1);
            bONI8.setStatus(status2);
            dao.persist(bONI8);

            BillingONItem bONI9 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI9);
            bONI9.setCh1Id(ch1Id2);
            bONI9.setStatus(status2);
            dao.persist(bONI9);

            List<BillingONItem> expectedResult = Arrays.asList(bONI2, bONI5, bONI6, bONI8);
            List<BillingONItem> result = dao.findByCh1Id(ch1Id1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }

        @Test
        @Tag("query")
        @DisplayName("should find billing items by ch1 ID and status not equal to given status")
        void shouldFindBillingItems_byCh1IdAndStatusNotEqual() throws Exception {
            int ch1Id1 = 101, ch1Id2 = 202;
            String status1 = "D", status2 = "Active";

            BillingONItem bONI1 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI1);
            bONI1.setCh1Id(ch1Id2);
            bONI1.setStatus(status1);
            dao.persist(bONI1);

            BillingONItem bONI2 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI2);
            bONI2.setCh1Id(ch1Id1);
            bONI2.setStatus(status2);
            dao.persist(bONI2);

            BillingONItem bONI3 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI3);
            bONI3.setCh1Id(ch1Id2);
            bONI3.setStatus(status2);
            dao.persist(bONI3);

            BillingONItem bONI4 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI4);
            bONI4.setCh1Id(ch1Id1);
            bONI4.setStatus(status1);
            dao.persist(bONI4);

            BillingONItem bONI5 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI5);
            bONI5.setCh1Id(ch1Id1);
            bONI5.setStatus(status2);
            dao.persist(bONI5);

            BillingONItem bONI6 = new BillingONItem();
            EntityDataGenerator.generateTestDataForModelClass(bONI6);
            bONI6.setCh1Id(ch1Id1);
            bONI6.setStatus(status2);
            dao.persist(bONI6);

            List<BillingONItem> expectedResult = Arrays.asList(bONI2, bONI5, bONI6);
            List<BillingONItem> result = dao.findByCh1IdAndStatusNotEqual(ch1Id1, status1);

            assertThat(result).hasSize(expectedResult.size());
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
