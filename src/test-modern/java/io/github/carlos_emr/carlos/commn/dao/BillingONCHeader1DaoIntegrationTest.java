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

import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingONCHeader1Dao} query methods.
 *
 * <p>Validates JPQL/HQL queries and native SQL for Ontario billing header data.
 * Covers invoice lookups, date range queries, billing count aggregation, and
 * cross-entity joins with {@link BillingONItem}. Critical for Hibernate 6
 * migration due to native SQL usage and aggregate return types.</p>
 *
 * <p>Methods requiring {@code BillingONPayment}, {@code RaDetail},
 * {@code BillingService}, or {@code GstControl} entities are documented
 * but not tested here due to test infrastructure limitations.</p>
 *
 * @since 2026-03-04
 * @see BillingONCHeader1Dao
 * @see BillingONCHeader1DaoImpl
 */
@DisplayName("BillingONCHeader1Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingONCHeader1DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONCHeader1Dao billingONCHeader1Dao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final int DEMO_NO = 100;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

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

    private BillingONCHeader1 createHeader(int demoNo, String providerNo, String status, Date billingDate) {
        BillingONCHeader1 h = new BillingONCHeader1();
        h.setHeaderId(0);
        h.setDemographicNo(demoNo);
        h.setProviderNo(providerNo);
        h.setStatus(status);
        h.setBillingDate(billingDate);
        h.setBillingTime(billingDate);
        h.setPayProgram("HCP");
        h.setVisitType("00");
        h.setTotal(new BigDecimal("50.00"));
        h.setPaid(new BigDecimal("0.00"));
        h.setApptProviderNo(providerNo);
        h.setCreator("testuser");
        h.setAppointmentNo(0);
        h.setFaciltyNum("0001");
        return h;
    }

    private BillingONCHeader1 createAndPersist(int demoNo, String providerNo, String status, Date billingDate) {
        BillingONCHeader1 h = createHeader(demoNo, providerNo, status, billingDate);
        entityManager.persist(h);
        entityManager.flush();
        return h;
    }

    private BillingONItem createItem(Integer ch1Id, String serviceCode, String dx, Date serviceDate) {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(ch1Id);
        item.setServiceCode(serviceCode);
        item.setFee("50.00");
        item.setServiceCount("1");
        item.setServiceDate(serviceDate);
        item.setDx(dx);
        item.setDx1("");
        item.setDx2("");
        item.setStatus("O");
        return item;
    }

    private BillingONItem createAndPersistItem(Integer ch1Id, String serviceCode, String dx, Date serviceDate) {
        BillingONItem item = createItem(ch1Id, serviceCode, dx, serviceDate);
        entityManager.persist(item);
        entityManager.flush();
        return item;
    }

    // ========================================================================
    // getBillCheader1ByDemographicNo
    // ========================================================================

    @Nested
    @DisplayName("getBillCheader1ByDemographicNo")
    @Tag("read")
    class GetBillCheader1ByDemographicNo {

        @Test
        @DisplayName("should return billing headers for demographic")
        void shouldReturnHeaders_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", yesterday);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getBillCheader1ByDemographicNo(DEMO_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should exclude deleted billing headers")
        void shouldExcludeDeleted_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "D", yesterday);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getBillCheader1ByDemographicNo(DEMO_NO);

            // Then
            assertThat(result).allSatisfy(h ->
                    assertThat(h.getStatus()).isNotEqualTo("D"));
        }

        @Test
        @DisplayName("should return empty list for demographic with no billings")
        void shouldReturnEmpty_whenNoBillings() {
            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getBillCheader1ByDemographicNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getNumberOfDemographicsWithInvoicesForProvider (native SQL)
    // ========================================================================

    @Nested
    @DisplayName("getNumberOfDemographicsWithInvoicesForProvider")
    @Tag("aggregate")
    class GetNumberOfDemographicsWithInvoicesForProvider {

        @Test
        @DisplayName("should return distinct count of demographics with invoices")
        void shouldReturnDistinctCount_forProvider() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", today);
            createAndPersist(DEMO_NO + 1, PROVIDER_NO, "O", today);

            // When
            int result = billingONCHeader1Dao.getNumberOfDemographicsWithInvoicesForProvider(
                    PROVIDER_NO, yesterday, nextWeek, true);

            // Then
            assertThat(result).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return non-distinct count when distinct is false")
        void shouldReturnNonDistinctCount_whenDistinctFalse() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", today);

            // When
            int result = billingONCHeader1Dao.getNumberOfDemographicsWithInvoicesForProvider(
                    PROVIDER_NO, yesterday, nextWeek, false);

            // Then
            assertThat(result).isGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // billedBetweenTheseDays
    // ========================================================================

    @Nested
    @DisplayName("billedBetweenTheseDays")
    @Tag("read")
    class BilledBetweenTheseDays {

        @Test
        @DisplayName("should return true when billed in date range")
        void shouldReturnTrue_whenBilledInRange() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            boolean result = billingONCHeader1Dao.billedBetweenTheseDays("A001", DEMO_NO, yesterday, nextWeek);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when not billed in date range")
        void shouldReturnFalse_whenNotBilledInRange() {
            // When
            boolean result = billingONCHeader1Dao.billedBetweenTheseDays("A001", 99999, yesterday, nextWeek);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should exclude deleted headers from billing check")
        void shouldExcludeDeleted_fromBillingCheck() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "D", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            boolean result = billingONCHeader1Dao.billedBetweenTheseDays("A001", DEMO_NO, yesterday, nextWeek);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ========================================================================
    // getDaysSinceBilled
    // ========================================================================

    @Nested
    @DisplayName("getDaysSinceBilled")
    @Tag("read")
    class GetDaysSinceBilled {

        @Test
        @DisplayName("should return -1 when never billed")
        void shouldReturnNegativeOne_whenNeverBilled() {
            // When
            int result = billingONCHeader1Dao.getDaysSinceBilled("A001", 99999);

            // Then
            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("should return non-negative when billed")
        void shouldReturnNonNegative_whenBilled() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            int result = billingONCHeader1Dao.getDaysSinceBilled("A001", DEMO_NO);

            // Then
            assertThat(result).isGreaterThanOrEqualTo(0);
        }
    }

    // ========================================================================
    // getDaysSincePaid
    // ========================================================================

    @Nested
    @DisplayName("getDaysSincePaid")
    @Tag("read")
    class GetDaysSincePaid {

        @Test
        @DisplayName("should return -1 when never paid")
        void shouldReturnNegativeOne_whenNeverPaid() {
            // When
            int result = billingONCHeader1Dao.getDaysSincePaid("A001", 99999);

            // Then
            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("should return non-negative when paid")
        void shouldReturnNonNegative_whenPaid() {
            // Given - status 'S' = settled/paid
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "S", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            int result = billingONCHeader1Dao.getDaysSincePaid("A001", DEMO_NO);

            // Then
            assertThat(result).isGreaterThanOrEqualTo(0);
        }
    }

    // ========================================================================
    // getInvoices
    // ========================================================================

    @Nested
    @DisplayName("getInvoices")
    @Tag("read")
    class GetInvoices {

        @Test
        @DisplayName("should return invoices for demographic with limit")
        void shouldReturnInvoices_withLimit() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", yesterday);
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", lastWeek);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getInvoices(DEMO_NO, 2);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return all invoices for demographic without limit")
        void shouldReturnAllInvoices_withoutLimit() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", yesterday);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getInvoices(DEMO_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should exclude deleted invoices")
        void shouldExcludeDeleted_fromInvoices() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "D", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getInvoices(DEMO_NO);

            // Then
            assertThat(result).allSatisfy(h ->
                    assertThat(h.getStatus()).isNotEqualTo("D"));
        }
    }

    // ========================================================================
    // getInvoicesByIds
    // ========================================================================

    @Nested
    @DisplayName("getInvoicesByIds")
    @Tag("read")
    class GetInvoicesByIds {

        @Test
        @DisplayName("should return invoices by IDs")
        void shouldReturnInvoices_byIds() {
            // Given
            BillingONCHeader1 h1 = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            BillingONCHeader1 h2 = createAndPersist(DEMO_NO, PROVIDER_NO, "S", yesterday);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getInvoicesByIds(
                    Arrays.asList(h1.getId(), h2.getId()));

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for empty ID list")
        void shouldReturnEmpty_forEmptyIds() {
            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getInvoicesByIds(
                    Arrays.asList());

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getInvoicesMeta
    // ========================================================================

    @Nested
    @DisplayName("getInvoicesMeta")
    @Tag("read")
    class GetInvoicesMeta {

        @Test
        @DisplayName("should return invoice metadata as maps")
        void shouldReturnMetadata_asMaps() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Map<String, Object>> result = billingONCHeader1Dao.getInvoicesMeta(DEMO_NO);

            // Then
            assertThat(result).isNotEmpty();
            Map<String, Object> meta = result.get(0);
            assertThat(meta).containsKey("id");
            assertThat(meta).containsKey("billingDate");
            assertThat(meta).containsKey("billing_time");
            assertThat(meta).containsKey("provider_no");
        }
    }

    // ========================================================================
    // findBillingONItemByServiceCode
    // ========================================================================

    @Nested
    @DisplayName("findBillingONItemByServiceCode")
    @Tag("read")
    class FindBillingONItemByServiceCode {

        @Test
        @DisplayName("should return item by service code")
        void shouldReturnItem_byServiceCode() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            BillingONItem result = billingONCHeader1Dao.findBillingONItemByServiceCode(h, "A001");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getServiceCode()).isEqualTo("A001");
        }

        @Test
        @DisplayName("should return null when service code not found")
        void shouldReturnNull_whenServiceCodeNotFound() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            BillingONItem result = billingONCHeader1Dao.findBillingONItemByServiceCode(h, "ZZZZ");

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // getLastOHIPBillingDateForServiceCode
    // ========================================================================

    @Nested
    @DisplayName("getLastOHIPBillingDateForServiceCode")
    @Tag("read")
    class GetLastOHIPBillingDateForServiceCode {

        @Test
        @DisplayName("should return last OHIP billing header for service code")
        void shouldReturnLastBilling_forServiceCode() {
            // Given - HCP pay program, status 'O' (open) matches query
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            BillingONCHeader1 result = billingONCHeader1Dao.getLastOHIPBillingDateForServiceCode(DEMO_NO, "A001");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(h.getId());
            assertThat(result.getDemographicNo()).isEqualTo(DEMO_NO);
            assertThat(result.getPayProgram()).isEqualTo("HCP");
        }

        @Test
        @DisplayName("should return null when no matching billing exists")
        void shouldReturnNull_whenNoMatchingBilling() {
            // When
            BillingONCHeader1 result = billingONCHeader1Dao.getLastOHIPBillingDateForServiceCode(99999, "ZZZZ");

            // Then
            assertThat(result).isNull();
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
            BillingONCHeader1 h = createHeader(DEMO_NO, PROVIDER_NO, "O", today);
            h.setAppointmentNo(5001);
            entityManager.persist(h);
            entityManager.flush();

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByAppointmentNo(5001);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getAppointmentNo()).isEqualTo(5001);
        }

        @Test
        @DisplayName("should return empty list for non-existent appointment")
        void shouldReturnEmpty_forNonExistentAppointment() {
            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByAppointmentNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // countBillingVisitsByProvider (aggregate - Object[] return)
    // ========================================================================

    @Nested
    @DisplayName("countBillingVisitsByProvider")
    @Tag("aggregate")
    class CountBillingVisitsByProvider {

        @Test
        @DisplayName("should return visit counts grouped by visit type")
        void shouldReturnCounts_groupedByVisitType() {
            // Given
            BillingONCHeader1 h = createHeader(DEMO_NO, PROVIDER_NO, "O", today);
            h.setAppointmentNo(1);
            entityManager.persist(h);
            entityManager.flush();

            // When
            List<Object[]> result = billingONCHeader1Dao.countBillingVisitsByProvider(
                    PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(String.class);
            assertThat(row[1]).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("should return empty list when no matching billings")
        void shouldReturnEmpty_whenNoMatchingBillings() {
            // When
            List<Object[]> result = billingONCHeader1Dao.countBillingVisitsByProvider(
                    "NONE", yesterday, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // countBillingVisitsByCreator (aggregate - Object[] return)
    // ========================================================================

    @Nested
    @DisplayName("countBillingVisitsByCreator")
    @Tag("aggregate")
    class CountBillingVisitsByCreator {

        @Test
        @DisplayName("should return visit counts grouped by visit type for creator")
        void shouldReturnCounts_groupedByVisitTypeForCreator() {
            // Given
            BillingONCHeader1 h = createHeader(DEMO_NO, PROVIDER_NO, "O", today);
            h.setAppointmentNo(1);
            h.setCreator(PROVIDER_NO);
            entityManager.persist(h);
            entityManager.flush();

            // When
            List<Object[]> result = billingONCHeader1Dao.countBillingVisitsByCreator(
                    PROVIDER_NO, yesterday, nextWeek);

            // Then
            assertThat(result).hasSize(1);
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isEqualTo("00");
            assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
        }
    }

    // ========================================================================
    // count_larrykain_clinic (Long return type)
    // ========================================================================

    @Nested
    @DisplayName("count_larrykain_clinic")
    @Tag("aggregate")
    class CountLarrykainClinic {

        @Test
        @DisplayName("should return count of clinic visit billings")
        void shouldReturnCount_ofClinicVisitBillings() {
            // Given - visitType '00' = clinic
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<Long> result = billingONCHeader1Dao.count_larrykain_clinic(
                    "0001", yesterday, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0)).isInstanceOf(Long.class);
        }
    }

    // ========================================================================
    // count_larrykain_hospital
    // ========================================================================

    @Nested
    @DisplayName("count_larrykain_hospital")
    @Tag("aggregate")
    class CountLarrykainHospital {

        @Test
        @DisplayName("should return count of hospital visit billings")
        void shouldReturnCount_ofHospitalVisitBillings() {
            // Given - visitType != '00' for hospital
            BillingONCHeader1 h = createHeader(DEMO_NO, PROVIDER_NO, "O", today);
            h.setVisitType("01");
            h.setFaciltyNum("F001");
            entityManager.persist(h);
            entityManager.flush();

            // When
            List<Long> result = billingONCHeader1Dao.count_larrykain_hospital(
                    "F001", "F002", "F003", "F004", yesterday, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0)).isInstanceOf(Long.class);
        }
    }

    // ========================================================================
    // count_larrykain_other
    // ========================================================================

    @Nested
    @DisplayName("count_larrykain_other")
    @Tag("aggregate")
    class CountLarrykainOther {

        @Test
        @DisplayName("should return count of other visit billings")
        void shouldReturnCount_ofOtherVisitBillings() {
            // Given - visitType != '00', facility not matching any of the 5
            BillingONCHeader1 h = createHeader(DEMO_NO, PROVIDER_NO, "O", today);
            h.setVisitType("02");
            h.setFaciltyNum("XXXX");
            entityManager.persist(h);
            entityManager.flush();

            // When
            List<Long> result = billingONCHeader1Dao.count_larrykain_other(
                    "F001", "F002", "F003", "F004", "F005", yesterday, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0)).isInstanceOf(Long.class);
        }
    }

    // ========================================================================
    // findBillingsByManyThings
    // ========================================================================

    @Nested
    @DisplayName("findBillingsByManyThings")
    @Tag("search")
    class FindBillingsByManyThings {

        @Test
        @DisplayName("should find billings by status only")
        void shouldFindBillings_byStatusOnly() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findBillingsByManyThings(
                    "O", null, null, null, null);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result).allSatisfy(h ->
                    assertThat(h.getStatus()).isEqualTo("O"));
        }

        @Test
        @DisplayName("should filter by provider and date range")
        void shouldFilter_byProviderAndDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findBillingsByManyThings(
                    "O", PROVIDER_NO, yesterday, nextWeek, null);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result).allSatisfy(h -> {
                assertThat(h.getStatus()).isEqualTo("O");
                assertThat(h.getProviderNo()).isEqualTo(PROVIDER_NO);
            });
        }

        @Test
        @DisplayName("should filter by demographic number")
        void shouldFilter_byDemographicNo() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findBillingsByManyThings(
                    "O", null, null, null, DEMO_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result).allSatisfy(h -> {
                assertThat(h.getStatus()).isEqualTo("O");
                assertThat(h.getDemographicNo()).isEqualTo(DEMO_NO);
            });
        }
    }

    // ========================================================================
    // findByProviderStatusAndDateRange
    // ========================================================================

    @Nested
    @DisplayName("findByProviderStatusAndDateRange")
    @Tag("search")
    class FindByProviderStatusAndDateRange {

        @Test
        @DisplayName("should find billings by provider, status, and date range")
        void shouldFindBillings_byProviderStatusAndDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            DateRange range = new DateRange(yesterday, nextWeek);
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByProviderStatusAndDateRange(
                    PROVIDER_NO, Arrays.asList("O", "S"), range);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(result.get(0).getStatus()).isIn("O", "S");
        }

        @Test
        @DisplayName("should return empty when status doesn't match")
        void shouldReturnEmpty_whenStatusDoesntMatch() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            DateRange range = new DateRange(yesterday, nextWeek);
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByProviderStatusAndDateRange(
                    PROVIDER_NO, Arrays.asList("X"), range);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemoNo (paginated)
    // ========================================================================

    @Nested
    @DisplayName("findByDemoNo")
    @Tag("read")
    class FindByDemoNo {

        @Test
        @DisplayName("should return paginated billings for demographic")
        void shouldReturnPaginated_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersist(DEMO_NO, PROVIDER_NO, "S", yesterday);
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", lastWeek);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByDemoNo(DEMO_NO, 0, 2);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should exclude deleted billings")
        void shouldExcludeDeleted_fromPaginated() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "D", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByDemoNo(DEMO_NO, 0, 10);

            // Then
            assertThat(result).allSatisfy(h ->
                    assertThat(h.getStatus()).isNotEqualTo("D"));
        }
    }

    // ========================================================================
    // findByDemoNoAndDates (paginated with DateRange)
    // ========================================================================

    @Nested
    @DisplayName("findByDemoNoAndDates")
    @Tag("search")
    class FindByDemoNoAndDates {

        @Test
        @DisplayName("should return billings within date range")
        void shouldReturnBillings_withinDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            DateRange range = new DateRange(yesterday, nextWeek);
            List<BillingONCHeader1> result = billingONCHeader1Dao.findByDemoNoAndDates(
                    DEMO_NO, range, 0, 10);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDemographicNo()).isEqualTo(DEMO_NO);
        }
    }

    // ========================================================================
    // getBillingItemByDxCode
    // ========================================================================

    @Nested
    @DisplayName("getBillingItemByDxCode")
    @Tag("read")
    class GetBillingItemByDxCode {

        @Test
        @DisplayName("should return billing headers with matching dx code")
        void shouldReturnHeaders_withMatchingDxCode() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersistItem(h.getId(), "A001", "250", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getBillingItemByDxCode(DEMO_NO, "250");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(h.getId());
            assertThat(result.get(0).getDemographicNo()).isEqualTo(DEMO_NO);
        }

        @Test
        @DisplayName("should return empty when dx code not found")
        void shouldReturnEmpty_whenDxCodeNotFound() {
            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.getBillingItemByDxCode(99999, "ZZZ");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findBillingsByDemoNoCh1HeaderServiceCodeAndDate
    // ========================================================================

    @Nested
    @DisplayName("findBillingsByDemoNoCh1HeaderServiceCodeAndDate")
    @Tag("search")
    class FindBillingsByDemoNoCh1HeaderServiceCodeAndDate {

        @Test
        @DisplayName("should find billings by demo, service codes, and date range")
        void shouldFindBillings_byDemoServiceCodesAndDateRange() {
            // Given
            BillingONCHeader1 h = createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);
            createAndPersistItem(h.getId(), "A001", "001", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao
                    .findBillingsByDemoNoCh1HeaderServiceCodeAndDate(
                            DEMO_NO, Arrays.asList("A001"), yesterday, nextWeek);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(h.getId());
            assertThat(result.get(0).getDemographicNo()).isEqualTo(DEMO_NO);
        }
    }

    // ========================================================================
    // findAllByPayProgram (paginated)
    // ========================================================================

    @Nested
    @DisplayName("findAllByPayProgram")
    @Tag("read")
    class FindAllByPayProgram {

        @Test
        @DisplayName("should return billings by pay program with pagination")
        void shouldReturnBillings_byPayProgramWithPagination() {
            // Given
            createAndPersist(DEMO_NO, PROVIDER_NO, "O", today);

            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findAllByPayProgram("HCP", 0, 10);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(h ->
                    assertThat(h.getPayProgram()).isEqualTo("HCP"));
        }

        @Test
        @DisplayName("should return empty for non-existent pay program")
        void shouldReturnEmpty_forNonExistentPayProgram() {
            // When
            List<BillingONCHeader1> result = billingONCHeader1Dao.findAllByPayProgram("ZZZ", 0, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // createBills (list persist)
    // ========================================================================

    @Nested
    @DisplayName("createBills (list)")
    @Tag("create")
    class CreateBillsList {

        @Test
        @DisplayName("should persist list of billing headers")
        void shouldPersistList_ofBillingHeaders() {
            // Given
            BillingONCHeader1 h1 = createHeader(DEMO_NO, PROVIDER_NO, "O", today);
            BillingONCHeader1 h2 = createHeader(DEMO_NO + 1, PROVIDER_NO, "O", today);

            // When
            billingONCHeader1Dao.createBills(Arrays.asList(h1, h2));
            entityManager.flush();

            // Then
            assertThat(h1.getId()).isPositive();
            assertThat(h2.getId()).isNotNull();
        }
    }
}
