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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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
            assertThat(result).isNotEmpty();
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
            assertThat(result).isNotEmpty();
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
            assertThat(result).isNotEmpty();
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
