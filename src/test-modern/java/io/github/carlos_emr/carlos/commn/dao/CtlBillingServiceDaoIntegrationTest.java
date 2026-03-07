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

import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CtlBillingServiceDao} covering
 * getAllServiceTypes, findByServiceGroupAndServiceType, findUniqueServiceTypesByCode,
 * findServiceTypes, findServiceCodesByType, and findServiceTypesByStatus.
 *
 * <p>Migrated from legacy {@code CtlBillingServiceDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CtlBillingServiceDao
 */
@DisplayName("CtlBillingServiceDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class CtlBillingServiceDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlBillingServiceDao dao;

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should return all service types")
        void shouldReturnAllServiceTypes() {
            List<Object[]> serviceTypes = dao.getAllServiceTypes();
            assertThat(serviceTypes).isNotNull();
            assertThat(serviceTypes).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find by service group and service type")
        void shouldFindServices_byServiceGroupAndServiceType() {
            List<CtlBillingService> services = dao.findByServiceGroupAndServiceType("Group2", null);
            assertThat(services).isNotEmpty();

            services = dao.findByServiceGroupAndServiceType("Group1", "MFP");
            assertThat(services).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find unique service types by code")
        void shouldReturnUniqueServiceTypes_byCode() {
            List<Object[]> result = dao.findUniqueServiceTypesByCode("CODE");
            assertThat(result).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find service types")
        void shouldReturnServiceTypes() {
            List<Object[]> result = dao.findServiceTypes();
            assertThat(result).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find service codes by type")
        void shouldReturnServiceCodes_byType() {
            List<Object> result = dao.findServiceCodesByType("SRV_TY");
            assertThat(result).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find service types by status")
        void shouldReturnServiceTypes_byStatus() {
            List<Object[]> result = dao.findServiceTypesByStatus("A");
            assertThat(result).isNotNull();
        }
    }
}
