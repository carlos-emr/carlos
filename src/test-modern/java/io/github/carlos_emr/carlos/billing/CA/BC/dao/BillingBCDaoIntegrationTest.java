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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.commn.dao.BillingBCDao;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingBCDao}.
 * <p>
 * Note: BillingBCDao uses native SQL queries against raw tables
 * (ctl_billingservice, billingservice, billinglocation, billingvisit, wcb_side).
 * Tests insert data via native SQL to verify query filtering behavior.
 * </p>
 *
 * @since 2026-03-07
 */
@DisplayName("BillingBCDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillingBCDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingBCDao dao;

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    @Nested
    @DisplayName("findBillingServices (3-param)")
    class FindBillingServices3Param {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching services exist")
        void shouldReturnEmptyList_whenNoMatchingServicesExist() {
            List<Object[]> result = dao.findBillingServices("NONEXISTENT", "NOGROUP", "NOTYPE");
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return matching services filtered by region, group, and type")
        void shouldReturnServices_whenMatchingDataExists() {
            entityManager.createNativeQuery("INSERT INTO billingservice (service_code, description, `value`, percentage, region, billingservice_date, termination_date) VALUES ('SVC01', 'Test Service', '100.00', '100', 'BC', '2025-01-01', '2099-12-31')").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO ctl_billingservice (service_code, service_group, servicetype, status, service_order) VALUES ('SVC01', 'GRP1', 'TYP1', 'A', 1)").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO billingservice (service_code, description, `value`, percentage, region, billingservice_date, termination_date) VALUES ('SVC02', 'Other Service', '50.00', '100', 'ON', '2025-01-01', '2099-12-31')").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO ctl_billingservice (service_code, service_group, servicetype, status, service_order) VALUES ('SVC02', 'GRP1', 'TYP1', 'A', 2)").executeUpdate();
            entityManager.flush();

            List<Object[]> result = dao.findBillingServices("BC", "GRP1", "TYP1");
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isEqualTo("SVC01");
            assertThat(result.get(0)[1]).isEqualTo("Test Service");
        }
    }

    @Nested
    @DisplayName("findBillingServicesByType")
    class FindBillingServicesByType {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching service type exists")
        void shouldReturnEmptyList_whenNoMatchingTypeExists() {
            List<Object[]> result = dao.findBillingServicesByType("NONEXISTENT");
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return services filtered by service type")
        void shouldReturnServices_whenMatchingTypeExists() {
            entityManager.createNativeQuery("INSERT INTO ctl_billingservice (service_code, service_group, servicetype, status, service_order) VALUES ('A001', 'G1', 'TESTTYPE', 'A', 1)").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO ctl_billingservice (service_code, service_group, servicetype, status, service_order) VALUES ('A002', 'G1', 'OTHER', 'A', 2)").executeUpdate();
            entityManager.flush();

            List<Object[]> result = dao.findBillingServicesByType("TESTTYPE");
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isEqualTo("A001");
            assertThat(result.get(0)[2]).isEqualTo("G1");
        }
    }

    @Nested
    @DisplayName("findBillingServices (4-param with reference date)")
    class FindBillingServices4Param {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching services exist for date")
        void shouldReturnEmptyList_whenNoServicesMatchDate() {
            List<Object[]> result = dao.findBillingServices("XX", "YY", "ZZ", "2025-01-01");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findBillingLocations")
    class FindBillingLocations {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no locations exist for region")
        void shouldReturnEmptyList_whenNoLocationsExist() {
            List<Object[]> result = dao.findBillingLocations("NONEXISTENT");
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return locations filtered by region")
        void shouldReturnLocations_whenMatchingRegionExists() {
            entityManager.createNativeQuery("INSERT INTO billinglocation (billinglocation, billinglocation_desc, region) VALUES ('LOC1', 'Test Location', 'BC')").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO billinglocation (billinglocation, billinglocation_desc, region) VALUES ('LOC2', 'Ontario Location', 'ON')").executeUpdate();
            entityManager.flush();

            List<Object[]> result = dao.findBillingLocations("BC");
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isEqualTo("LOC1");
            assertThat(result.get(0)[1]).isEqualTo("Test Location");
        }
    }

    @Nested
    @DisplayName("findBillingVisits")
    class FindBillingVisits {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no visits exist for region")
        void shouldReturnEmptyList_whenNoVisitsExist() {
            List<Object[]> result = dao.findBillingVisits("NONEXISTENT");
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return visits filtered by region")
        void shouldReturnVisits_whenMatchingRegionExists() {
            entityManager.createNativeQuery("INSERT INTO billingvisit (visittype, visit_desc, region) VALUES ('V1', 'Office Visit', 'BC')").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO billingvisit (visittype, visit_desc, region) VALUES ('V2', 'Hospital Visit', 'ON')").executeUpdate();
            entityManager.flush();

            List<Object[]> result = dao.findBillingVisits("BC");
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isEqualTo("V1");
            assertThat(result.get(0)[1]).isEqualTo("Office Visit");
        }
    }

    @Nested
    @DisplayName("findInjuryLocations")
    class FindInjuryLocations {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no injury locations exist")
        void shouldReturnEmptyList_whenNoInjuryLocationsExist() {
            List<Object[]> result = dao.findInjuryLocations();
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return all injury locations from wcb_side table")
        void shouldReturnAllInjuryLocations_whenDataExists() {
            entityManager.createNativeQuery("INSERT INTO wcb_side (sidetype, sidedesc) VALUES ('L', 'Left')").executeUpdate();
            entityManager.createNativeQuery("INSERT INTO wcb_side (sidetype, sidedesc) VALUES ('R', 'Right')").executeUpdate();
            entityManager.flush();

            List<Object[]> result = dao.findInjuryLocations();
            assertThat(result).hasSize(2);
            assertThat(result).extracting(row -> row[0])
                    .containsExactlyInAnyOrder("L", "R");
            assertThat(result).extracting(row -> row[1])
                    .containsExactlyInAnyOrder("Left", "Right");
        }
    }
}
