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
package io.github.carlos_emr.carlos.billing.CA.dao;

import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillActivityDao}.
 *
 * <p>Migrated from legacy {@code BillActivityDaoTest} (JUnit 4 / DaoTestFixtures).
 * Replicates exact legacy test coverage: persist entity, query by month code
 * and group number, and query by date range with ordering verification.</p>
 *
 * @since 2026-03-07
 * @see BillActivityDao
 */
@DisplayName("BillActivityDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillActivityDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillActivityDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        BillActivity entity = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    /**
     * Replicates legacy testFindCurrentByMonthCodeAndGroupNo.
     * Tests filtering by monthCode, groupNo, updateDateTime, and status "A",
     * and verifies ordering by batchCount ascending.
     */
    @Test
    @Tag("read")
    @Tag("query")
    @DisplayName("should return matching activities ordered by batch count when queried by month code and group number")
    void shouldReturnMatchingActivities_whenQueriedByMonthCodeAndGroupNo() throws Exception {
        String monthCode = "A";
        String groupNo = "101";
        Date updateDateTime = new Date(dfm.parse("20080101").getTime());

        // billActivity1: matches all criteria, batchCount=10
        BillActivity billActivity1 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity1);
        billActivity1.setUpdateDateTime(new Date(dfm.parse("20090101").getTime()));
        billActivity1.setMonthCode(monthCode);
        billActivity1.setGroupNo(groupNo);
        billActivity1.setStatus("A");
        billActivity1.setBatchCount(10);

        // billActivity2: wrong monthCode; should not be selected
        BillActivity billActivity2 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity2);
        billActivity2.setUpdateDateTime(new Date(dfm.parse("20090101").getTime()));
        billActivity2.setMonthCode("B");
        billActivity2.setGroupNo(groupNo);
        billActivity2.setStatus("A");

        // billActivity3: wrong group number; should not be selected
        BillActivity billActivity3 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity3);
        billActivity3.setUpdateDateTime(new Date(dfm.parse("20090101").getTime()));
        billActivity3.setMonthCode(monthCode);
        billActivity3.setGroupNo("102");
        billActivity3.setStatus("A");

        // billActivity4: update time older than specified; should not be selected
        BillActivity billActivity4 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity4);
        billActivity4.setUpdateDateTime(new Date(dfm.parse("20070101").getTime()));
        billActivity4.setMonthCode(monthCode);
        billActivity4.setGroupNo(groupNo);
        billActivity4.setStatus("A");

        // billActivity5: inactive; should not be selected
        BillActivity billActivity5 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity5);
        billActivity5.setUpdateDateTime(new Date(dfm.parse("20090101").getTime()));
        billActivity5.setMonthCode(monthCode);
        billActivity5.setGroupNo(groupNo);
        billActivity5.setStatus("D");

        // billActivity6: matches all criteria, batchCount=6
        BillActivity billActivity6 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity6);
        billActivity6.setUpdateDateTime(new Date(dfm.parse("20090101").getTime()));
        billActivity6.setMonthCode(monthCode);
        billActivity6.setGroupNo(groupNo);
        billActivity6.setStatus("A");
        billActivity6.setBatchCount(6);

        dao.persist(billActivity1);
        dao.persist(billActivity2);
        dao.persist(billActivity3);
        dao.persist(billActivity4);
        dao.persist(billActivity5);
        dao.persist(billActivity6);

        List<BillActivity> result = dao.findCurrentByMonthCodeAndGroupNo(monthCode, groupNo, updateDateTime);
        List<BillActivity> expectedResult = Arrays.asList(billActivity6, billActivity1);

        assertThat(result).hasSameSizeAs(expectedResult);
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    /**
     * Replicates legacy testFindCurrentByDateRange.
     * Tests filtering by date range and status "A", and verifies ordering
     * by updateDateTime descending.
     */
    @Test
    @Tag("read")
    @Tag("query")
    @DisplayName("should return matching activities ordered by date when queried by date range")
    void shouldReturnMatchingActivities_whenQueriedByDateRange() throws Exception {
        Date startDate = new Date(dfm.parse("20090101").getTime());
        Date endDate = new Date(dfm.parse("20090301").getTime());

        BillActivity billActivity1 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity1);
        billActivity1.setUpdateDateTime(new Date(dfm.parse("20090101").getTime()));
        billActivity1.setMonthCode("A");
        billActivity1.setGroupNo("101");
        billActivity1.setStatus("A");

        BillActivity billActivity2 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity2);
        billActivity2.setUpdateDateTime(new Date(dfm.parse("20090201").getTime()));
        billActivity2.setStatus("A");

        BillActivity billActivity3 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity3);
        billActivity3.setUpdateDateTime(new Date(dfm.parse("20090301").getTime()));
        billActivity3.setStatus("A");

        // billActivity4: date before range; should not be selected
        BillActivity billActivity4 = new BillActivity();
        EntityDataGenerator.generateTestDataForModelClass(billActivity4);
        billActivity4.setUpdateDateTime(new Date(dfm.parse("20081231").getTime()));
        billActivity4.setStatus("A");

        dao.persist(billActivity1);
        dao.persist(billActivity2);
        dao.persist(billActivity3);
        dao.persist(billActivity4);

        List<BillActivity> result = dao.findCurrentByDateRange(startDate, endDate);
        List<BillActivity> expectedResult = Arrays.asList(billActivity3, billActivity2, billActivity1);

        assertThat(result).hasSameSizeAs(expectedResult);
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
