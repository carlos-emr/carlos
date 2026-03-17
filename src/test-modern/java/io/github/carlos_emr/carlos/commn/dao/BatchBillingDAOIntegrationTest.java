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
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
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
 * Integration tests for {@link BatchBillingDAO} covering find by demographic/service code,
 * find by provider, find all, and distinct service codes.
 *
 * <p>Migrated from legacy {@code BatchBillingDAOTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see BatchBillingDAO
 */
@DisplayName("BatchBillingDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BatchBillingDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private BatchBillingDAO dao;

    private final Timestamp currentTimestamp = new Timestamp(Calendar.getInstance().getTime().getTime());

    private BatchBilling createBatchBilling(int demoNo, String serviceCode, String providerNo) throws Exception {
        BatchBilling bb = new BatchBilling();
        EntityDataGenerator.generateTestDataForModelClass(bb);
        bb.setCreateDate(currentTimestamp);
        bb.setDemographicNo(demoNo);
        bb.setServiceCode(serviceCode);
        bb.setBillingProviderNo(providerNo);
        dao.persist(bb);
        return bb;
    }

    @Nested
    @DisplayName("find(demographicNo, serviceCode)")
    class FindByDemographicAndServiceCode {

        @Test
        @Tag("query")
        @DisplayName("should return matching records by demographic and service code")
        void shouldReturnMatchingRecords_byDemographicAndServiceCode() throws Exception {
            createBatchBilling(1, "100", "p1");
            createBatchBilling(2, "200", "p1");
            createBatchBilling(1, "100", "p2");

            List<BatchBilling> result = dao.find(1, "100");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(bb -> bb.getDemographicNo() == 1 && "100".equals(bb.getServiceCode()));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no match found")
        void shouldReturnEmpty_whenNoMatchFound() throws Exception {
            createBatchBilling(1, "100", "p1");

            List<BatchBilling> result = dao.find(999, "999");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProvider(providerNo)")
    class FindByProvider {

        @Test
        @Tag("query")
        @DisplayName("should return records matching provider number")
        void shouldReturnRecords_byProviderNumber() throws Exception {
            createBatchBilling(1, "100", "1");
            createBatchBilling(2, "200", "2");
            createBatchBilling(3, "300", "1");

            List<BatchBilling> result = dao.findByProvider("1");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(bb -> "1".equals(bb.getBillingProviderNo()));
        }
    }

    @Nested
    @DisplayName("findByProvider(providerNo, serviceCode)")
    class FindByProviderAndServiceCode {

        @Test
        @Tag("query")
        @DisplayName("should return records matching provider and service code")
        void shouldReturnRecords_byProviderAndServiceCode() throws Exception {
            createBatchBilling(1, "101", "1");
            createBatchBilling(2, "181", "2");
            createBatchBilling(3, "101", "1");

            List<BatchBilling> result = dao.findByProvider("1", "101");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(bb ->
                    "1".equals(bb.getBillingProviderNo()) && "101".equals(bb.getServiceCode()));
        }
    }

    @Nested
    @DisplayName("findByServiceCode")
    class FindByServiceCode {

        @Test
        @Tag("query")
        @DisplayName("should return records matching service code")
        void shouldReturnRecords_byServiceCode() throws Exception {
            BatchBilling bb1 = createBatchBilling(1, "101", "p1");
            createBatchBilling(2, "181", "p2");

            List<BatchBilling> result = dao.findByServiceCode("101");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(bb1);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all batch billing records")
        void shouldReturnAllRecords_afterPersist() throws Exception {
            createBatchBilling(1, "101", "p1");
            createBatchBilling(2, "181", "p2");
            createBatchBilling(3, "181", "p3");

            List<BatchBilling> result = dao.findAll();

            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("findDistinctServiceCodes")
    class FindDistinctServiceCodes {

        @Test
        @Tag("query")
        @DisplayName("should return distinct service codes")
        void shouldReturnDistinctServiceCodes_whenDuplicatesExist() throws Exception {
            createBatchBilling(1, "101", "p1");
            createBatchBilling(2, "101", "p2");
            createBatchBilling(3, "181", "p3");

            List<String> result = dao.findDistinctServiceCodes();

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder("101", "181");
        }
    }
}
