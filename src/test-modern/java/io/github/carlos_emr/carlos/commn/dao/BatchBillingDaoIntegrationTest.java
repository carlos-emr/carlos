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
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BatchBillingDAO} covering
 * find(int, String), findByProvider(String), findByProvider(String, String),
 * findByServiceCode, findAll, and findDistinctServiceCodes.
 *
 * <p>Migrated from legacy {@code BatchBillingDAOTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see BatchBillingDAO
 */
@DisplayName("BatchBillingDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BatchBillingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BatchBillingDAO dao;

    private final Timestamp currentTimestamp = new Timestamp(Calendar.getInstance().getTime().getTime());

    private BatchBilling createBatchBilling(String serviceCode, String providerNo, int demoNo) {
        BatchBilling bb = new BatchBilling();
        EntityDataGenerator.generateTestDataForModelClass(bb);
        bb.setCreateDate(currentTimestamp);
        bb.setServiceCode(serviceCode);
        bb.setBillingProviderNo(providerNo);
        bb.setDemographicNo(demoNo);
        dao.persist(bb);
        return bb;
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find batch billings by demographic number and service code")
        void shouldFindBatchBillings_byDemoNoAndServiceCode() {
            BatchBilling bb1 = createBatchBilling("100", "1", 1);
            createBatchBilling("200", "1", 2);
            BatchBilling bb3 = createBatchBilling("100", "1", 1);

            List<BatchBilling> result = dao.find(1, "100");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(bb1);
            assertThat(result.get(1)).isEqualTo(bb3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find batch billings by provider number")
        void shouldFindBatchBillings_byProviderNo() {
            BatchBilling bb1 = createBatchBilling("100", "1", 1);
            createBatchBilling("200", "2", 2);
            BatchBilling bb3 = createBatchBilling("300", "1", 3);

            List<BatchBilling> result = dao.findByProvider("1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(bb1);
            assertThat(result.get(1)).isEqualTo(bb3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find batch billings by provider number and service code")
        void shouldFindBatchBillings_byProviderNoAndServiceCode() {
            BatchBilling bb1 = createBatchBilling("101", "1", 1);
            createBatchBilling("181", "2", 2);
            BatchBilling bb3 = createBatchBilling("101", "1", 3);

            List<BatchBilling> result = dao.findByProvider("1", "101");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(bb1);
            assertThat(result.get(1)).isEqualTo(bb3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find batch billings by service code")
        void shouldFindBatchBillings_byServiceCode() {
            BatchBilling bb1 = createBatchBilling("101", "1", 1);
            createBatchBilling("181", "2", 2);

            List<BatchBilling> result = dao.findByServiceCode("101");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(bb1);
        }

        @Test
        @Tag("read")
        @DisplayName("should find all batch billings")
        void shouldReturnAllBatchBillings_whenFindAllCalled() {
            BatchBilling bb1 = createBatchBilling("101", "1", 1);
            BatchBilling bb2 = createBatchBilling("181", "2", 2);
            BatchBilling bb3 = createBatchBilling("181", "3", 3);

            List<BatchBilling> result = dao.findAll();

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(bb1);
            assertThat(result.get(1)).isEqualTo(bb2);
            assertThat(result.get(2)).isEqualTo(bb3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find distinct service codes")
        void shouldReturnDistinctServiceCodes() {
            createBatchBilling("101", "1", 1);
            createBatchBilling("101", "2", 2);
            createBatchBilling("181", "3", 3);

            List<String> result = dao.findDistinctServiceCodes();

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly("101", "181");
        }
    }
}
