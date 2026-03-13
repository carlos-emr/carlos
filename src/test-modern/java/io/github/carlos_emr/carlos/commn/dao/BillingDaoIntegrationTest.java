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

import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingDao} query methods.
 *
 * <p>Validates JPQL queries for billing data. Covers active billing lookups,
 * type-based filtering, appointment-based queries, date range searches,
 * and aggregation queries. Critical for Hibernate 6 migration due to
 * dynamic query construction and Object[] return types.</p>
 *
 * <p>The {@code findByManyThings} method uses native SQL and is documented
 * but not tested here due to MySQL-specific syntax.</p>
 *
 * @since 2026-03-04
 * @see BillingDao
 * @see BillingDaoImpl
 */
@DisplayName("BillingDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("billingDaoImpl")
    private BillingDao billingDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final int DEMO_NO = 100;

    private Date today;
    private Date yesterday;
    private Date lastWeek;
    private Date nextWeek;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -1);
        yesterday = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, -7);
        lastWeek = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 7);
        nextWeek = cal.getTime();
    }

    private Billing createBilling(int demoNo, String providerNo, String status, Date billingDate) {
        Billing b = new Billing();
        b.setDemographicNo(demoNo);
        b.setProviderNo(providerNo);
        b.setProviderOhipNo(providerNo);
        b.setApptProviderNo(providerNo);
        b.setStatus(status);
        b.setBillingDate(billingDate);
        b.setBillingTime(billingDate);
        b.setUpdateDate(billingDate);
        b.setUpdateTime(billingDate);
        b.setVisitDate(billingDate);
        b.setDemographicName("Test Patient");
        b.setHin("1234567890");
        b.setTotal("50.00");
        b.setCreator("testuser");
        b.setBillingtype("MSP");
        b.setVisitType("00");
        b.setAppointmentNo(0);
        return b;
    }

    private Billing createAndPersist(int demoNo, String providerNo, String status, Date billingDate) {
        Billing b = createBilling(demoNo, providerNo, status, billingDate);
        entityManager.persist(b);
        entityManager.flush();
        return b;
    }

    // ========================================================================
    // findActive
    // ========================================================================

    @Nested
    @DisplayName("findActive")
    @Tag("read")
    class FindActive {

        @Test
        @DisplayName("should return active billing by ID")
        void shouldReturnActiveBilling_byId() {
            // Given
            Billing b = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Billing> result = billingDao.findActive(b.getId());

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isNotEqualTo("D");
        }

        @Test
        @DisplayName("should exclude deleted billing")
        void shouldExcludeDeleted_billing() {
            // Given
            Billing b = createAndPersist(DEMO_NO, PROVIDER_NO, "D", today);

            // When
            List<Billing> result = billingDao.findActive(b.getId());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for non-existent ID")
        void shouldReturnEmpty_forNonExistentId() {
            // When
            List<Billing> result = billingDao.findActive(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByBillingType
    // ========================================================================

    @Nested
    @DisplayName("findByBillingType")
    @Tag("read")
    class FindByBillingType {

        @Test
        @DisplayName("should return billings by type")
        void shouldReturnBillings_byType() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Billing> result = billingDao.findByBillingType("MSP");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(b ->
                    assertThat(b.getBillingtype()).isEqualTo("MSP"));
        }

        @Test
        @DisplayName("should return empty for non-existent type")
        void shouldReturnEmpty_forNonExistentType() {
            // When
            List<Billing> result = billingDao.findByBillingType("ZZZ");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByAppointmentNo
    // ========================================================================

    @Nested
    @DisplayName("findByAppointmentNo")
    @Tag("read")
    class FindByAppointmentNo {

        @Test
        @DisplayName("should return billings for appointment number")
        void shouldReturnBillings_forAppointmentNo() {
            // Given
            Billing b = createBilling(DEMO_NO, PROVIDER_NO, "O", today);
            b.setAppointmentNo(5001);
            entityManager.persist(b);
            entityManager.flush();

            // When
            List<Billing> result = billingDao.findByAppointmentNo(5001);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAppointmentNo()).isEqualTo(5001);
            assertThat(result.get(0).getDemographicNo()).isEqualTo(DEMO_NO);
        }

        @Test
        @DisplayName("should return empty for non-existent appointment")
        void shouldReturnEmpty_forNonExistentAppointment() {
            // When
            List<Billing> result = billingDao.findByAppointmentNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findSet
    // ========================================================================

    @Nested
    @DisplayName("findSet")
    @Tag("read")
    class FindSet {

        @Test
        @DisplayName("should return billings by ID list")
        void shouldReturnBillings_byIdList() {
            // Given
            Billing b1 = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            Billing b2 = createAndPersist(DEMO_NO + 1, PROVIDER_NO, "S", yesterday);

            // When
            List<Billing> result = billingDao.findSet(
                    Arrays.asList(String.valueOf(b1.getId()), String.valueOf(b2.getId())));

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for empty ID list")
        void shouldReturnEmpty_forEmptyIdList() {
            // When
            List<Billing> result = billingDao.findSet(Arrays.asList());

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findBillings (demoNo, serviceCodes)
    // ========================================================================

    @Nested
    @DisplayName("findBillings by demoNo and serviceCodes")
    @Tag("read")
    class FindBillingsByDemoAndServiceCodes {

        @Test
        @DisplayName("should return empty when no matching service codes")
        void shouldReturnEmpty_whenNoMatchingServiceCodes() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Object[]> result = billingDao.findBillings(DEMO_NO, Arrays.asList("ZZZ"));

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findBillings (demoNo, statusType, providerNo, startDate, endDate)
    // ========================================================================

    @Nested
    @DisplayName("findBillings by multiple criteria")
    @Tag("search")
    class FindBillingsByMultipleCriteria {

        @Test
        @DisplayName("should find billings by status and date range")
        void shouldFindBillings_byStatusAndDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Billing> result = billingDao.findBillings(
                    DEMO_NO, "O", PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo("O");
            assertThat(result.get(0).getDemographicNo()).isEqualTo(DEMO_NO);
            assertThat(result.get(0).getProviderNo()).isEqualTo(PROVIDER_NO);
        }

        @Test
        @DisplayName("should return empty when no match")
        void shouldReturnEmpty_whenNoMatch() {
            // When
            List<Billing> result = billingDao.findBillings(
                    99999, "O", PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByProviderStatusAndDates
    // ========================================================================

    @Nested
    @DisplayName("findByProviderStatusAndDates")
    @Tag("search")
    class FindByProviderStatusAndDates {

        @Test
        @DisplayName("should find billings by provider, statuses, and date range")
        void shouldFindBillings_byProviderStatusesAndDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            DateRange range = new DateRange(yesterday, nextWeek);
            List<Billing> result = billingDao.findByProviderStatusAndDates(
                    PROVIDER_NO, Arrays.asList("O", "S"), range);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(result.get(0).getStatus()).isIn("O", "S");
        }
    }

    // ========================================================================
    // search_billing_no_by_appt
    // ========================================================================

    @Nested
    @DisplayName("search_billing_no_by_appt")
    @Tag("read")
    class SearchBillingNoByAppt {

        @Test
        @DisplayName("should return billing number for appointment")
        void shouldReturnBillingNo_forAppointment() {
            // Given
            Billing b = createBilling(DEMO_NO, PROVIDER_NO, "O", today);
            b.setAppointmentNo(5001);
            entityManager.persist(b);
            entityManager.flush();

            // When
            Integer result = billingDao.search_billing_no_by_appt(DEMO_NO, 5001);

            // Then
            assertThat(result).isEqualTo(b.getId());
        }

        @Test
        @DisplayName("should return null when no matching billing")
        void shouldReturnNull_whenNoMatchingBilling() {
            // When
            Integer result = billingDao.search_billing_no_by_appt(99999, 99999);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // search_billing_no
    // ========================================================================

    @Nested
    @DisplayName("search_billing_no")
    @Tag("read")
    class SearchBillingNo {

        @Test
        @DisplayName("should return latest billing number for demographic")
        void shouldReturnLatestBillingNo_forDemographic() {
            // Given
            Billing b = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            Integer result = billingDao.search_billing_no(DEMO_NO);

            // Then
            assertThat(result).isEqualTo(b.getId());
        }

        @Test
        @DisplayName("should return null for demographic with no billings")
        void shouldReturnNull_forDemoWithNoBillings() {
            // When
            Integer result = billingDao.search_billing_no(99999);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // search_unsettled_history_daterange
    // ========================================================================

    @Nested
    @DisplayName("search_unsettled_history_daterange")
    @Tag("search")
    class SearchUnsettledHistoryDaterange {

        @Test
        @DisplayName("should return unsettled billings in date range")
        void shouldReturnUnsettledBillings_inDateRange() {
            // Given — status 'B' is the "unsettled/billed" status this query filters on
            createAndPersist(DEMO_NO, PROVIDER_NO, "B", today);

            // When
            List<Billing> result = billingDao.search_unsettled_history_daterange(
                    PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(result.get(0).getDemographicNo()).isEqualTo(DEMO_NO);
        }
    }

    // ========================================================================
    // search_bill_history_daterange
    // ========================================================================

    @Nested
    @DisplayName("search_bill_history_daterange")
    @Tag("search")
    class SearchBillHistoryDaterange {

        @Test
        @DisplayName("should return billing history in date range")
        void shouldReturnBillingHistory_inDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Billing> result = billingDao.search_bill_history_daterange(
                    PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(result.get(0).getStatus()).isEqualTo("O");
        }
    }

    // ========================================================================
    // findActiveBillingsByDemoNo
    // ========================================================================

    @Nested
    @DisplayName("findActiveBillingsByDemoNo")
    @Tag("read")
    class FindActiveBillingsByDemoNo {

        @Test
        @DisplayName("should return active billings with limit")
        void shouldReturnActiveBillings_withLimit() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", yesterday);

            // When
            List<Billing> result = billingDao.findActiveBillingsByDemoNo(DEMO_NO, 1);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findBillingsByDemoNoServiceCodeAndDate
    // ========================================================================

    @Nested
    @DisplayName("findBillingsByDemoNoServiceCodeAndDate")
    @Tag("search")
    class FindBillingsByDemoNoServiceCodeAndDate {

        @Test
        @DisplayName("should return empty when no matching service codes")
        void shouldReturnEmpty_whenNoMatchingServiceCodes() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Billing> result = billingDao.findBillingsByDemoNoServiceCodeAndDate(
                    DEMO_NO, today, Arrays.asList("ZZZ"));

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // countBillings (aggregate)
    // ========================================================================

    @Nested
    @DisplayName("countBillings")
    @Tag("aggregate")
    class CountBillings {

        @Test
        @DisplayName("should return zero when no matching billings")
        void shouldReturnZero_whenNoMatchingBillings() {
            // When
            Integer result = billingDao.countBillings("ZZZ", "nobody", yesterday, nextWeek);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(0);
        }
    }

    // ========================================================================
    // countBillingVisitsByProvider (Object[] return)
    // ========================================================================

    @Nested
    @DisplayName("countBillingVisitsByProvider")
    @Tag("aggregate")
    class CountBillingVisitsByProvider {

        @Test
        @DisplayName("should return visit counts grouped by visit type")
        void shouldReturnCounts_groupedByVisitType() {
            // Given — query requires appointmentNo <> '0' and filters by apptProviderNo
            Billing b = createBilling(DEMO_NO, PROVIDER_NO, "O", today);
            b.setAppointmentNo(1);
            entityManager.persist(b);
            entityManager.flush();

            // When
            List<Object[]> result = billingDao.countBillingVisitsByProvider(
                    PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
        }
    }

    // ========================================================================
    // countBillingVisitsByCreator (Object[] return)
    // ========================================================================

    @Nested
    @DisplayName("countBillingVisitsByCreator")
    @Tag("aggregate")
    class CountBillingVisitsByCreator {

        @Test
        @DisplayName("should return visit counts grouped by visit type for creator")
        void shouldReturnCounts_groupedByVisitTypeForCreator() {
            // Given — query requires appointmentNo <> '0' and filters by creator
            Billing b = createBilling(DEMO_NO, PROVIDER_NO, "O", today);
            b.setCreator(PROVIDER_NO);
            b.setAppointmentNo(1);
            entityManager.persist(b);
            entityManager.flush();

            // When
            List<Object[]> result = billingDao.countBillingVisitsByCreator(
                    PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).hasSize(1);
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            // row[0] = visitType (String), row[1] = count (Long)
            assertThat(row[0]).isEqualTo("00");
            assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
        }
    }

    // ========================================================================
    // findByProviderStatusForTeleplanFileWriter
    // ========================================================================

    @Nested
    @DisplayName("findByProviderStatusForTeleplanFileWriter")
    @Tag("read")
    class FindByProviderStatusForTeleplanFileWriter {

        @Test
        @DisplayName("should return empty for non-existent HIN")
        void shouldReturnEmpty_forNonExistentHin() {
            // When
            List<Billing> result = billingDao.findByProviderStatusForTeleplanFileWriter("NONEXISTENT");

            // Then
            assertThat(result).isEmpty();
        }
    }
}
