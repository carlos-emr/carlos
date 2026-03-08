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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CtlBillingServiceDao} covering query operations
 * with meaningful assertions on filtering and grouping behavior.
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

    private CtlBillingService createService(String serviceType, String serviceTypeName,
                                             String serviceCode, String serviceGroup,
                                             String serviceGroupName, String status) {
        CtlBillingService svc = new CtlBillingService();
        svc.setServiceType(serviceType);
        svc.setServiceTypeName(serviceTypeName);
        svc.setServiceCode(serviceCode);
        svc.setServiceGroup(serviceGroup);
        svc.setServiceGroupName(serviceGroupName);
        svc.setStatus(status);
        svc.setServiceOrder(0);
        dao.persist(svc);
        return svc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist and find billing service")
        void shouldPersistAndFind_whenValidDataProvided() {
            CtlBillingService svc = createService("MFP", "Med Fee Prac", "A001", "GRP1", "Group One", "A");

            hibernateTemplate.flush();
            CtlBillingService found = hibernateTemplate.get(CtlBillingService.class, svc.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(svc.getId());
            assertThat(found.getServiceType()).isEqualTo("MFP");
            assertThat(found.getServiceCode()).isEqualTo("A001");
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove billing service by ID")
        void shouldRemoveService_whenValidIdProvided() {
            CtlBillingService svc = createService("RMV", "Remove Test", "R001", "GRP1", "Group One", "A");
            Integer id = svc.getId();

            dao.remove(id);
            hibernateTemplate.flush();
            hibernateTemplate.clear();

            CtlBillingService found = hibernateTemplate.get(CtlBillingService.class, id);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createService("MFP", "Med Fee Prac", "A001", "GRP1", "Group One", "A");
            createService("MFP", "Med Fee Prac", "A002", "GRP1", "Group One", "A");
            createService("LAB", "Laboratory", "B001", "GRP2", "Group Two", "A");
            createService("RAD", "Radiology", "C001", "GRP1", "Group One", "D");
        }

        @Test
        @Tag("query")
        @DisplayName("should return non-null and non-empty unique service types with default status")
        void shouldReturnNonEmptyUniqueServiceTypes_withDefaultStatus() {
            List<Object[]> serviceTypes = dao.getUniqueServiceTypes();

            assertThat(serviceTypes).isNotNull();
            assertThat(serviceTypes).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return all service types grouped by type")
        void shouldReturnAllServiceTypes() {
            List<Object[]> serviceTypes = dao.getAllServiceTypes();

            assertThat(serviceTypes).isNotEmpty();
            // Each result is [serviceTypeName, serviceType]
            assertThat(serviceTypes).allSatisfy(row -> {
                assertThat(row).hasSize(2);
                assertThat(row[0]).isNotNull();
                assertThat(row[1]).isNotNull();
            });
            // Should have at least 3 distinct types: MFP, LAB, RAD
            assertThat(serviceTypes.size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find by service group with null service type")
        void shouldFindByServiceGroup_whenServiceTypeIsNull() {
            List<CtlBillingService> services = dao.findByServiceGroupAndServiceType("GRP1", null);

            // GRP1 has 3 entries (A001, A002 active; C001 deleted)
            assertThat(services).hasSize(3);
            assertThat(services).extracting(CtlBillingService::getServiceGroup)
                    .containsOnly("GRP1");
        }

        @Test
        @Tag("query")
        @DisplayName("should find by service group and service type")
        void shouldFindByServiceGroupAndServiceType_whenBothSpecified() {
            List<CtlBillingService> services = dao.findByServiceGroupAndServiceType("GRP1", "MFP");

            assertThat(services).hasSize(2);
            assertThat(services).extracting(CtlBillingService::getServiceType)
                    .containsOnly("MFP");
            assertThat(services).extracting(CtlBillingService::getServiceGroup)
                    .containsOnly("GRP1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent service group")
        void shouldReturnEmptyList_whenServiceGroupDoesNotExist() {
            List<CtlBillingService> services = dao.findByServiceGroupAndServiceType("NOGROUP", null);

            assertThat(services).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find unique service types by service code")
        void shouldReturnUniqueServiceTypes_byCode() {
            List<Object[]> result = dao.findUniqueServiceTypesByCode("A001");

            assertThat(result).hasSize(1);
            // [serviceTypeName, serviceType]
            assertThat(result.get(0)[0]).isEqualTo("Med Fee Prac");
            assertThat(result.get(0)[1]).isEqualTo("MFP");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent service code")
        void shouldReturnEmptyList_forNonExistentServiceCode() {
            List<Object[]> result = dao.findUniqueServiceTypesByCode("ZZZZ");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find service types excluding deleted")
        void shouldReturnServiceTypes_excludingDeleted() {
            List<Object[]> result = dao.findServiceTypes();

            // Should return non-deleted types with non-null, non-empty serviceType
            assertThat(result).isNotEmpty();
            // RAD with status "D" should be excluded
            List<String> serviceTypes = result.stream()
                    .map(row -> (String) row[0])
                    .toList();
            assertThat(serviceTypes).doesNotContain("RAD");
            assertThat(serviceTypes).contains("MFP", "LAB");
        }

        @Test
        @Tag("query")
        @DisplayName("should find service codes by service type")
        void shouldReturnServiceCodes_byType() {
            List<Object> result = dao.findServiceCodesByType("MFP");

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder("A001", "A002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for service codes of deleted type")
        void shouldReturnEmptyList_forDeletedServiceType() {
            // RAD has status "D", findServiceCodesByType filters status <> 'D'
            List<Object> result = dao.findServiceCodesByType("RAD");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find service types by status")
        void shouldReturnServiceTypes_byStatus() {
            List<Object[]> result = dao.findServiceTypesByStatus("A");

            assertThat(result).isNotEmpty();
            // Should contain MFP and LAB (active), not RAD (deleted)
            List<String> types = result.stream()
                    .map(row -> (String) row[1])
                    .toList();
            assertThat(types).contains("MFP", "LAB");
            assertThat(types).doesNotContain("RAD");
        }

        @Test
        @Tag("query")
        @DisplayName("should find deleted service types by status D")
        void shouldReturnDeletedServiceTypes_byStatusD() {
            List<Object[]> result = dao.findServiceTypesByStatus("D");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)[1]).isEqualTo("RAD");
        }

        @Test
        @Tag("query")
        @DisplayName("should find all services")
        void shouldReturnAllServices() {
            List<CtlBillingService> all = dao.findAll();

            assertThat(all).hasSize(4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find active services by service type ID")
        void shouldFindByServiceTypeId_onlyActiveStatus() {
            List<CtlBillingService> results = dao.findByServiceTypeId("MFP");

            assertThat(results).hasSize(2);
            assertThat(results).extracting(CtlBillingService::getStatus)
                    .containsOnly("A");
        }

        @Test
        @Tag("query")
        @DisplayName("should not find deleted services by service type ID")
        void shouldNotFindDeletedServices_byServiceTypeId() {
            List<CtlBillingService> results = dao.findByServiceTypeId("RAD");

            // RAD exists but with status "D", findByServiceTypeId filters status='A'
            assertThat(results).isEmpty();
        }
    }
}
