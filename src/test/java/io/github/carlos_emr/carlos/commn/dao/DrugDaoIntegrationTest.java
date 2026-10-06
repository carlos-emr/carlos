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

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DrugDao} query methods.
 *
 * <p>Validates JPQL queries for prescription drug data against H2. Covers
 * demographic-based lookups, ATC code queries, archived filtering, and
 * date-based searches critical for Hibernate 6 migration.</p>
 *
 * @since 2026-03-05
 * @see DrugDao
 * @see DrugDaoImpl
 */
@DisplayName("DrugDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("drug")
@Transactional
public class DrugDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DrugDao drugDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final int DEMO_NO = 100;
    private static final int DEMO_NO_2 = 101;

    private Date today;
    private Date yesterday;
    private Date lastWeek;
    private Date nextWeek;
    private Date tomorrow;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -1);
        yesterday = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        tomorrow = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, -7);
        lastWeek = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 7);
        nextWeek = cal.getTime();
    }

    private Drug createDrug(int demoNo, String brandName, String atc, boolean archived) {
        Drug drug = new Drug();
        drug.setDemographicId(demoNo);
        drug.setProviderNo(PROVIDER_NO);
        drug.setBrandName(brandName);
        drug.setAtc(atc);
        drug.setArchived(archived);
        drug.setRxDate(today);
        drug.setEndDate(nextWeek);
        drug.setWrittenDate(today);
        drug.setSpecial("1 tab PO daily");
        drug.setCustomName("");
        drug.setGenericName("");
        drug.setRegionalIdentifier("");
        drug.setGcnSeqNo("0");
        drug.setFreqCode("OD");
        drug.setDuration("30");
        drug.setDurUnit("D");
        drug.setQuantity("30");
        drug.setUnitName("tab");
        drug.setNoSubs(false);
        drug.setPrn(false);
        drug.setCreateDate(today);
        return drug;
    }

    private Drug createAndPersist(int demoNo, String brandName, String atc, boolean archived) {
        Drug drug = createDrug(demoNo, brandName, atc, archived);
        entityManager.persist(drug);
        entityManager.flush();
        return drug;
    }

    @Test
    void shouldPreserveFirstDiscontinuation_andRefuseAnotherPatient() {
        Drug drug = createAndPersist(DEMO_NO, "Owned discontinuation", "", false);
        assertThat(drugDao.discontinueIfActive(drug.getId(), DEMO_NO_2, today, "foreign")).isFalse();
        assertThat(drugDao.discontinueIfActive(drug.getId(), DEMO_NO, today, " doseChange ")).isTrue();
        assertThat(drugDao.discontinueIfActive(drug.getId(), DEMO_NO, tomorrow, "allergy")).isFalse();
        entityManager.refresh(drug);
        assertThat(drug.isArchived()).isTrue();
        assertThat(drug.getArchivedReason()).isEqualTo("doseChange");
        assertThat(drug.getArchivedDate()).isEqualTo(today);
        assertThat(drug.getLastUpdateDate()).isEqualTo(today);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldAllowOnlyOneDiscontinuation_whenTransactionsOverlap() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        Integer id = transaction.execute(status -> createAndPersist(DEMO_NO, "Concurrent discontinuation", "", false).getId());
        var firstSaved = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> transaction.execute(status -> {
                boolean changed = drugDao.discontinueIfActive(id, DEMO_NO, today, "doseChange");
                firstSaved.countDown();
                try {
                    if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Transaction wait timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return changed;
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> transaction.execute(status -> {
                secondStarted.countDown();
                return drugDao.discontinueIfActive(id, DEMO_NO, tomorrow, "allergy");
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
            transaction.executeWithoutResult(status -> {
                Drug saved = entityManager.find(Drug.class, id);
                assertThat(saved.getArchivedReason()).isEqualTo("doseChange");
                assertThat(saved.getArchivedDate()).isEqualTo(today);
            });
        } finally {
            releaseFirst.countDown();
            workers.shutdown();
            if (!workers.awaitTermination(15, TimeUnit.SECONDS)) workers.shutdownNow();
            transaction.executeWithoutResult(status -> entityManager.remove(entityManager.find(Drug.class, id)));
        }
    }

    @Test
    @DisplayName("should require both prescription and drug ownership when loading a patient's script")
    void shouldScopeScriptLookup_toPersistedPatientOwnership() {
        Prescription script = new Prescription();
        script.setDemographicId(DEMO_NO);
        script.setProviderNo(PROVIDER_NO);
        script.setDatePrescribed(today);
        script.setDatePrinted(today);
        entityManager.persist(script);
        entityManager.flush();

        Drug owned = createDrug(DEMO_NO, "Owned medication", "", false);
        owned.setScriptNo(script.getId());
        entityManager.persist(owned);
        // Inconsistent historical data must not leak another patient's drug through this script.
        Drug foreign = createDrug(DEMO_NO_2, "Foreign medication", "", false);
        foreign.setScriptNo(script.getId());
        entityManager.persist(foreign);
        entityManager.flush();
        entityManager.clear();

        List<Object[]> ownedRows = drugDao.findDrugsAndPrescriptionsByScriptNumber(script.getId(), DEMO_NO);

        assertThat(ownedRows).hasSize(1);
        assertThat(((Drug) ownedRows.getFirst()[0]).getId()).isEqualTo(owned.getId());
        assertThat(((Prescription) ownedRows.getFirst()[1]).getDemographicId()).isEqualTo(DEMO_NO);
        // The foreign drug's own demographic cannot read a prescription belonging to DEMO_NO.
        assertThat(drugDao.findDrugsAndPrescriptionsByScriptNumber(script.getId(), DEMO_NO_2)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "UPDATE drugs SET takemin=NULL WHERE drugid=:id",
            "UPDATE drugs SET takemax=NULL WHERE drugid=:id",
            "UPDATE drugs SET custom_instructions=NULL WHERE drugid=:id",
            "UPDATE drugs SET hide_cpp=NULL WHERE drugid=:id",
            "UPDATE drugs SET start_date_unknown=NULL WHERE drugid=:id"})
    @DisplayName("should load schema-legal null prescription fields using existing primitive defaults")
    void shouldLoadDefaultValues_whenLegacyDrugFieldIsNull(String query) {
        Drug stored = createAndPersist(DEMO_NO, "Legacy synthetic drug", "TEST", false);
        // Statements are fixed literals above; the fixture identifier remains bound.
        entityManager.createNativeQuery(query)
                .setParameter("id", stored.getId()).executeUpdate();
        entityManager.clear();
        Drug loaded = drugDao.find(stored.getId());
        assertThat(loaded.getTakeMin()).isZero();
        assertThat(loaded.getTakeMax()).isZero();
        assertThat(loaded.isCustomInstructions()).isFalse();
        assertThat(loaded.getHideFromCpp()).isFalse();
        assertThat(loaded.getStartDateUnknown()).isFalse();
        assertThat(loaded.getSpecial()).isEqualTo("1 tab PO daily");
        loaded.setTakeMin(0.5f);
        loaded.setTakeMax(2.5f);
        loaded.setCustomInstructions(true);
        loaded.setHideFromCpp(true);
        loaded.setStartDateUnknown(true);
        entityManager.flush();
        entityManager.clear();
        Drug reloaded = drugDao.find(stored.getId());
        assertThat(reloaded.getTakeMin()).isEqualTo(0.5f);
        assertThat(reloaded.getTakeMax()).isEqualTo(2.5f);
        assertThat(reloaded.isCustomInstructions()).isTrue();
        assertThat(reloaded.getHideFromCpp()).isTrue();
        assertThat(reloaded.getStartDateUnknown()).isTrue();
        assertThat(reloaded.getSpecial()).isEqualTo("1 tab PO daily");
    }

    // ========================================================================
    // findByDemographicId
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicId")
    @Tag("read")
    class FindByDemographicId {

        @Test
        @DisplayName("should return all drugs for demographic")
        void shouldReturnAllDrugs_whenDemographicHasDrugs() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);
            createAndPersist(DEMO_NO, "Metformin", "A10BA02", false);

            // When
            List<Drug> result = drugDao.findByDemographicId(DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no drugs for demographic")
        void shouldReturnEmptyList_whenNoDrugs() {
            // When
            List<Drug> result = drugDao.findByDemographicId(99999);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should filter by archived status")
        void shouldFilterByArchived_whenBooleanProvided() {
            // Given
            Drug active = createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);
            Drug archived = createAndPersist(DEMO_NO, "OldDrug", "X99XX99", true);

            // When
            List<Drug> activeResult = drugDao.findByDemographicId(DEMO_NO, false);
            List<Drug> archivedResult = drugDao.findByDemographicId(DEMO_NO, true);

            // Then
            assertThat(activeResult).extracting(Drug::getId).contains(active.getId());
            assertThat(activeResult).extracting(Drug::getId).doesNotContain(archived.getId());
            assertThat(archivedResult).extracting(Drug::getId).contains(archived.getId());
        }
    }

    // ========================================================================
    // findByAtc
    // ========================================================================

    @Nested
    @DisplayName("findByAtc")
    @Tag("read")
    class FindByAtc {

        @Test
        @DisplayName("should return drugs matching ATC code")
        void shouldReturnDrugs_whenAtcMatches() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);
            createAndPersist(DEMO_NO_2, "Aspirin 325mg", "B01AC06", false);

            // When
            List<Drug> result = drugDao.findByAtc("B01AC06");

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result).allSatisfy(d -> assertThat(d.getAtc()).isEqualTo("B01AC06"));
        }

        @Test
        @DisplayName("should return drugs matching any ATC in list")
        void shouldReturnDrugs_whenAnyAtcInListMatches() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);
            createAndPersist(DEMO_NO, "Metformin", "A10BA02", false);

            // When
            List<Drug> result = drugDao.findByAtc(Arrays.asList("B01AC06", "A10BA02"));

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list for non-existent ATC")
        void shouldReturnEmptyList_whenAtcDoesNotExist() {
            // When
            List<Drug> result = drugDao.findByAtc("Z99ZZ99");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicIdOrderByPosition
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdOrderByPosition")
    @Tag("read")
    class FindByDemographicIdOrderByPosition {

        @Test
        @DisplayName("should return drugs ordered by position")
        void shouldReturnDrugsOrdered_byPosition() {
            // Given
            Drug first = createDrug(DEMO_NO, "Aspirin", "B01AC06", false);
            first.setPosition(1);
            entityManager.persist(first);

            Drug second = createDrug(DEMO_NO, "Metformin", "A10BA02", false);
            second.setPosition(2);
            entityManager.persist(second);
            entityManager.flush();

            // When
            List<Drug> result = drugDao.findByDemographicIdOrderByPosition(DEMO_NO, false);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findByDemographicIdSimilarDrugOrderByDate
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdSimilarDrugOrderByDate")
    @Tag("read")
    class FindByDemographicIdSimilarDrug {

        @Test
        @DisplayName("should find drugs with same regional identifier")
        void shouldFindDrugs_withSameRegionalIdentifier() {
            // Given
            Drug drug = createDrug(DEMO_NO, "Aspirin", "B01AC06", false);
            drug.setRegionalIdentifier("00123456");
            entityManager.persist(drug);
            entityManager.flush();

            // When
            List<Drug> result = drugDao.findByDemographicIdSimilarDrugOrderByDate(
                    DEMO_NO, "00123456", "");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRegionalIdentifier()).isEqualTo("00123456");
            assertThat(result.get(0).getBrandName()).isEqualTo("Aspirin");
        }
    }

    // ========================================================================
    // findByDemographicIdUpdatedAfterDate
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdUpdatedAfterDate")
    @Tag("read")
    class FindByDemographicIdUpdatedAfterDate {

        @Test
        @DisplayName("should return drugs updated after date")
        void shouldReturnDrugs_whenUpdatedAfterDate() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);

            // When
            List<Drug> result = drugDao.findByDemographicIdUpdatedAfterDate(DEMO_NO, lastWeek);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBrandName()).isEqualTo("Aspirin");
            assertThat(result.get(0).getDemographicId()).isEqualTo(DEMO_NO);
        }

        @Test
        @DisplayName("should return empty list when none updated after date")
        void shouldReturnEmptyList_whenNoneUpdatedAfterDate() {
            // When
            List<Drug> result = drugDao.findByDemographicIdUpdatedAfterDate(DEMO_NO, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicIdAndAtc
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdAndAtc")
    @Tag("read")
    class FindByDemographicIdAndAtc {

        @Test
        @DisplayName("should return drugs matching demographic and ATC")
        void shouldReturnDrugs_whenDemoAndAtcMatch() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);
            createAndPersist(DEMO_NO, "Metformin", "A10BA02", false);

            // When
            List<Drug> result = drugDao.findByDemographicIdAndAtc(DEMO_NO, "B01AC06");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAtc()).isEqualTo("B01AC06");
        }
    }

    // ========================================================================
    // findLongTermDrugsByDemographic
    // ========================================================================

    @Nested
    @DisplayName("findLongTermDrugsByDemographic")
    @Tag("read")
    class FindLongTermDrugsByDemographic {

        @Test
        @DisplayName("should return only long-term drugs")
        void shouldReturnOnlyLongTermDrugs() {
            // Given
            Drug longTerm = createDrug(DEMO_NO, "Metformin", "A10BA02", false);
            longTerm.setLongTerm(true);
            entityManager.persist(longTerm);

            Drug shortTerm = createDrug(DEMO_NO, "Amoxicillin", "J01CA04", false);
            shortTerm.setLongTerm(false);
            entityManager.persist(shortTerm);
            entityManager.flush();

            // When
            List<Drug> result = drugDao.findLongTermDrugsByDemographic(DEMO_NO);

            // Then
            assertThat(result).extracting(Drug::getId).contains(longTerm.getId());
            assertThat(result).extracting(Drug::getId).doesNotContain(shortTerm.getId());
        }
    }

    // ========================================================================
    // getMaxPosition
    // ========================================================================

    @Nested
    @DisplayName("getMaxPosition")
    @Tag("read")
    class GetMaxPosition {

        @Test
        @DisplayName("should return highest position value for demographic")
        void shouldReturnMaxPosition_forDemographic() {
            // Given
            Drug d1 = createDrug(DEMO_NO, "Aspirin", "B01AC06", false);
            d1.setPosition(5);
            entityManager.persist(d1);

            Drug d2 = createDrug(DEMO_NO, "Metformin", "A10BA02", false);
            d2.setPosition(10);
            entityManager.persist(d2);
            entityManager.flush();

            // When
            int maxPos = drugDao.getMaxPosition(DEMO_NO);

            // Then
            assertThat(maxPos).isGreaterThanOrEqualTo(10);
        }
    }

    // ========================================================================
    // addNewDrug
    // ========================================================================

    @Nested
    @DisplayName("addNewDrug")
    @Tag("create")
    class AddNewDrug {

        @Test
        @DisplayName("should persist new drug and return true")
        void shouldPersistNewDrug_andReturnTrue() {
            // Given
            Drug drug = createDrug(DEMO_NO, "NewDrug", "C03AA01", false);

            // When
            boolean result = drugDao.addNewDrug(drug);

            // Then
            assertThat(result).isTrue();
            assertThat(drug.getId()).isPositive();
        }
    }

    // ========================================================================
    // getNumberOfDemographicsWithRxForProvider (COUNT - migration critical)
    // ========================================================================

    @Nested
    @DisplayName("getNumberOfDemographicsWithRxForProvider")
    @Tag("aggregate")
    class GetNumberOfDemographicsWithRxForProvider {

        @Test
        @DisplayName("should return count of demographics with prescriptions")
        void shouldReturnCount_ofDemographicsWithRx() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);
            createAndPersist(DEMO_NO_2, "Metformin", "A10BA02", false);

            // When
            int count = drugDao.getNumberOfDemographicsWithRxForProvider(
                    PROVIDER_NO, lastWeek, nextWeek, true);

            // Then
            assertThat(count).isGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findByScriptNo
    // ========================================================================

    @Nested
    @DisplayName("findByScriptNo")
    @Tag("read")
    class FindByScriptNo {

        @Test
        @DisplayName("should return drugs matching script number")
        void shouldReturnDrugs_whenScriptNoMatches() {
            // Given
            Drug drug = createDrug(DEMO_NO, "Aspirin", "B01AC06", false);
            drug.setScriptNo(12345);
            entityManager.persist(drug);
            entityManager.flush();

            // When
            List<Drug> result = drugDao.findByScriptNo(12345, false);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getScriptNo()).isEqualTo(12345);
            assertThat(result.get(0).getBrandName()).isEqualTo("Aspirin");
        }
    }

    // ========================================================================
    // findDemographicIdsUpdatedAfterDate (integrator sync)
    // ========================================================================

    @Nested
    @DisplayName("findDemographicIdsUpdatedAfterDate")
    @Tag("read")
    class FindDemographicIdsUpdatedAfterDate {

        @Test
        @DisplayName("should return demographic IDs with drugs updated after date")
        void shouldReturnDemoIds_whenDrugsUpdatedAfterDate() {
            // Given
            createAndPersist(DEMO_NO, "Aspirin", "B01AC06", false);

            // When
            List<Integer> result = drugDao.findDemographicIdsUpdatedAfterDate(lastWeek);

            // Then
            assertThat(result).contains(DEMO_NO);
        }
    }
}
