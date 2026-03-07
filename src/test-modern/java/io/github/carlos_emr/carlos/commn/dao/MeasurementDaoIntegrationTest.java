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

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Provider;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MeasurementDao} query methods.
 *
 * <p>Validates JPQL queries for clinical measurement data against an H2 in-memory
 * database. Covers find-by-demographic, find-by-type, date range queries, and
 * aggregate/Object[] returning methods critical for Hibernate 6 migration.</p>
 *
 * <p>Note: Measurement entities are immutable after creation (@PreUpdate throws
 * UnsupportedOperationException). Tests create new records rather than updating.</p>
 *
 * @since 2026-03-05
 * @see MeasurementDao
 * @see MeasurementDaoImpl
 */
@DisplayName("MeasurementDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("measurement")
@Transactional
public class MeasurementDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementDao measurementDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final int DEMO_NO = 100;
    private static final int DEMO_NO_2 = 101;

    private Date today;
    private Date yesterday;
    private Date tomorrow;
    private Date lastWeek;
    private Date nextWeek;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 10, 0, 0);
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

    private Measurement createMeasurement(int demoNo, String type, String dataField, Date dateObserved) {
        Measurement m = new Measurement();
        m.setDemographicId(demoNo);
        m.setType(type);
        m.setDataField(dataField);
        m.setDateObserved(dateObserved);
        m.setProviderNo(PROVIDER_NO);
        m.setMeasuringInstruction("");
        m.setComments("");
        return m;
    }

    private Measurement createAndPersist(int demoNo, String type, String dataField, Date dateObserved) {
        Measurement m = createMeasurement(demoNo, type, dataField, dateObserved);
        entityManager.persist(m);
        entityManager.flush();
        return m;
    }

    private Measurement createAndPersistWithInstruction(int demoNo, String type, String dataField,
                                                         Date dateObserved, String instruction) {
        Measurement m = createMeasurement(demoNo, type, dataField, dateObserved);
        m.setMeasuringInstruction(instruction);
        entityManager.persist(m);
        entityManager.flush();
        return m;
    }

    private Measurement createAndPersistWithApptNo(int demoNo, String type, String dataField,
                                                    Date dateObserved, int appointmentNo) {
        Measurement m = createMeasurement(demoNo, type, dataField, dateObserved);
        m.setAppointmentNo(appointmentNo);
        entityManager.persist(m);
        entityManager.flush();
        return m;
    }

    // ========================================================================
    // findByDemographicNo
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicNo")
    @Tag("read")
    class FindByDemographicNo {

        @Test
        @DisplayName("should return all measurements for demographic")
        void shouldReturnAllMeasurements_whenDemographicHasMeasurements() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "WT", "75", today);

            // When
            List<Measurement> result = measurementDao.findByDemographicNo(DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for demographic with no measurements")
        void shouldReturnEmptyList_whenNoMeasurementsExist() {
            // When
            List<Measurement> result = measurementDao.findByDemographicNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicNoAndType
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicNoAndType")
    @Tag("read")
    class FindByDemographicNoAndType {

        @Test
        @DisplayName("should return measurements filtered by type")
        void shouldReturnMeasurements_whenFilteredByType() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "WT", "75", today);

            // When
            List<Measurement> result = measurementDao.findByDemographicNoAndType(DEMO_NO, "BP");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo("BP");
        }

        @Test
        @DisplayName("should return empty list for non-existent type")
        void shouldReturnEmptyList_whenTypeDoesNotExist() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByDemographicNoAndType(DEMO_NO, "NONEXIST");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findLatestByDemographicNoAndType
    // ========================================================================

    @Nested
    @DisplayName("findLatestByDemographicNoAndType")
    @Tag("read")
    class FindLatestByDemographicNoAndType {

        @Test
        @DisplayName("should return most recent measurement of type")
        void shouldReturnLatest_whenMultipleMeasurementsExist() {
            // Given
            createAndPersist(DEMO_NO, "BP", "110/70", yesterday);
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            Measurement result = measurementDao.findLatestByDemographicNoAndType(DEMO_NO, "BP");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDataField()).isEqualTo("120/80");
        }

        @Test
        @DisplayName("should return null when no measurement of type exists")
        void shouldReturnNull_whenNoMeasurementOfType() {
            // When
            Measurement result = measurementDao.findLatestByDemographicNoAndType(DEMO_NO, "NONEXIST");

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
        @DisplayName("should return measurements linked to appointment")
        void shouldReturnMeasurements_whenLinkedToAppointment() {
            // Given
            createAndPersistWithApptNo(DEMO_NO, "BP", "120/80", today, 5001);
            createAndPersistWithApptNo(DEMO_NO, "WT", "75", today, 5001);

            // When
            List<Measurement> result = measurementDao.findByAppointmentNo(5001);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for appointment with no measurements")
        void shouldReturnEmptyList_whenNoMeasurementsForAppointment() {
            // When
            List<Measurement> result = measurementDao.findByAppointmentNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicIdObservedDate
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdObservedDate")
    @Tag("read")
    class FindByDemographicIdObservedDate {

        @Test
        @DisplayName("should return measurements in date range")
        void shouldReturnMeasurements_whenInDateRange() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "BP", "110/70", lastWeek);

            // When
            List<Measurement> result = measurementDao.findByDemographicIdObservedDate(
                    DEMO_NO, yesterday, tomorrow);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no measurements in range")
        void shouldReturnEmptyList_whenNoMeasurementsInRange() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", lastWeek);

            // When
            List<Measurement> result = measurementDao.findByDemographicIdObservedDate(
                    DEMO_NO, yesterday, tomorrow);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getMeasurements (HashMap return - critical for migration)
    // ========================================================================

    @Nested
    @DisplayName("getMeasurements")
    @Tag("read")
    class GetMeasurements {

        @Test
        @DisplayName("should return map keyed by measurement type")
        void shouldReturnMap_keyedByType() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "WT", "75", today);

            // When
            HashMap<String, Measurement> result = measurementDao.getMeasurements(
                    DEMO_NO, new String[]{"BP", "WT"});

            // Then
            assertThat(result).containsKeys("BP", "WT");
            assertThat(result.get("BP").getDataField()).isEqualTo("120/80");
        }

        @Test
        @DisplayName("should return empty map when no matching types")
        void shouldReturnEmptyMap_whenNoMatchingTypes() {
            // When
            HashMap<String, Measurement> result = measurementDao.getMeasurements(
                    DEMO_NO, new String[]{"NONEXIST"});

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return latest measurement per type when multiple exist")
        void shouldReturnLatest_whenMultipleMeasurementsPerType() {
            // Given
            createAndPersist(DEMO_NO, "BP", "110/70", yesterday);
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            HashMap<String, Measurement> result = measurementDao.getMeasurements(
                    DEMO_NO, new String[]{"BP"});

            // Then
            assertThat(result).containsKey("BP");
            assertThat(result.get("BP").getDataField()).isEqualTo("120/80");
        }
    }

    // ========================================================================
    // findByCreateDate (date range - migration sensitive)
    // ========================================================================

    @Nested
    @DisplayName("findByCreateDate")
    @Tag("read")
    class FindByCreateDate {

        @Test
        @DisplayName("should return measurements created after date with limit")
        void shouldReturnMeasurements_whenCreatedAfterDate() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByCreateDate(lastWeek, 10);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo("BP");
            assertThat(result.get(0).getDataField()).isEqualTo("120/80");
        }

        @Test
        @DisplayName("should return empty list when none created after date")
        void shouldReturnEmptyList_whenNoneCreatedAfterDate() {
            // When
            List<Measurement> result = measurementDao.findByCreateDate(nextWeek, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByIdTypeAndInstruction
    // ========================================================================

    @Nested
    @DisplayName("findByIdTypeAndInstruction")
    @Tag("read")
    class FindByIdTypeAndInstruction {

        @Test
        @DisplayName("should return measurements matching type and instruction")
        void shouldReturnMeasurements_whenMatchingTypeAndInstruction() {
            // Given
            createAndPersistWithInstruction(DEMO_NO, "BP", "120/80", today, "Sitting");
            createAndPersistWithInstruction(DEMO_NO, "BP", "130/85", today, "Standing");

            // When
            List<Measurement> result = measurementDao.findByIdTypeAndInstruction(DEMO_NO, "BP", "Sitting");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDataField()).isEqualTo("120/80");
        }
    }

    // ========================================================================
    // findByValue
    // ========================================================================

    @Nested
    @DisplayName("findByValue")
    @Tag("read")
    class FindByValue {

        @Test
        @DisplayName("should return measurements matching key-value pair")
        void shouldReturnMeasurements_whenKeyValueMatches() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByValue("BP", "120/80");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getDataField()).isEqualTo("120/80");
        }
    }

    // ========================================================================
    // findLastEntered
    // ========================================================================

    @Nested
    @DisplayName("findLastEntered")
    @Tag("read")
    class FindLastEntered {

        @Test
        @DisplayName("should return most recently entered measurement for demo and type")
        void shouldReturnLastEntered_forDemoAndType() {
            // Given
            createAndPersist(DEMO_NO, "BP", "110/70", yesterday);
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            Measurement result = measurementDao.findLastEntered(DEMO_NO, "BP");

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when no measurements for type")
        void shouldReturnNull_whenNoMeasurementsForType() {
            // When
            Measurement result = measurementDao.findLastEntered(DEMO_NO, "NONEXIST");

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // findByType (multiple overloads)
    // ========================================================================

    @Nested
    @DisplayName("findByType")
    @Tag("read")
    class FindByType {

        @Test
        @DisplayName("should return measurements by demographic and type")
        void shouldReturnMeasurements_byDemographicAndType() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "WT", "75", today);

            // When
            List<Measurement> result = measurementDao.findByType(DEMO_NO, "BP");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo("BP");
        }

        @Test
        @DisplayName("should return measurements by demographic and type after date")
        void shouldReturnMeasurements_byDemographicTypeAndDate() {
            // Given
            createAndPersist(DEMO_NO, "BP", "110/70", lastWeek);
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByType(DEMO_NO, "BP", yesterday);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return measurements by type only (all demographics)")
        void shouldReturnMeasurements_byTypeOnly() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO_2, "BP", "130/85", today);

            // When
            List<Measurement> result = measurementDao.findByType("BP");

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findByTypeBefore
    // ========================================================================

    @Nested
    @DisplayName("findByTypeBefore")
    @Tag("read")
    class FindByTypeBefore {

        @Test
        @DisplayName("should return measurements before date")
        void shouldReturnMeasurements_beforeDate() {
            // Given
            createAndPersist(DEMO_NO, "BP", "110/70", lastWeek);
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByTypeBefore(DEMO_NO, "BP", yesterday);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findMeasurementByTypeAndDate
    // ========================================================================

    @Nested
    @DisplayName("findMeasurementByTypeAndDate")
    @Tag("read")
    class FindMeasurementByTypeAndDate {

        @Test
        @DisplayName("should return measurements by type within date range")
        void shouldReturnMeasurements_byTypeAndDateRange() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "BP", "110/70", lastWeek);

            // When
            List<Measurement> result = measurementDao.findMeasurementByTypeAndDate(
                    DEMO_NO, "BP", yesterday, tomorrow);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findMeasurementsAndProviders (Object[] return - migration critical)
    // ========================================================================

    @Nested
    @DisplayName("findMeasurementsAndProviders")
    @Tag("read")
    class FindMeasurementsAndProviders {

        @Test
        @DisplayName("should return measurement-provider pairs by ID")
        void shouldReturnPairs_byMeasurementId() {
            // Given
            Provider provider = new Provider();
            provider.setProviderNo(PROVIDER_NO);
            provider.setFirstName("John");
            provider.setLastName("Smith");
            provider.setProviderType("doctor");
            provider.setSex("M");
            provider.setSpecialty("");
            provider.setStatus("1");
            hibernateTemplate.save(provider);
            hibernateTemplate.flush();

            Measurement m = createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Object[]> result = measurementDao.findMeasurementsAndProviders(m.getId());

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row.length).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list for non-existent measurement ID")
        void shouldReturnEmptyList_forNonExistentId() {
            // When
            List<Object[]> result = measurementDao.findMeasurementsAndProviders(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getAppointmentNosByDemographicNoAndType (Set return)
    // ========================================================================

    @Nested
    @DisplayName("getAppointmentNosByDemographicNoAndType")
    @Tag("read")
    class GetAppointmentNosByDemographicNoAndType {

        @Test
        @DisplayName("should return appointment numbers for measurements of type in date range")
        void shouldReturnApptNos_forTypeInDateRange() {
            // Given
            createAndPersistWithApptNo(DEMO_NO, "BP", "120/80", today, 5001);
            createAndPersistWithApptNo(DEMO_NO, "BP", "130/85", today, 5002);

            // When
            Set<Integer> result = measurementDao.getAppointmentNosByDemographicNoAndType(
                    DEMO_NO, "BP", yesterday, tomorrow);

            // Then
            assertThat(result).contains(5001, 5002);
        }
    }

    // ========================================================================
    // findByDemographicIdUpdatedAfterDate (integration sync)
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdUpdatedAfterDate")
    @Tag("read")
    class FindByDemographicIdUpdatedAfterDate {

        @Test
        @DisplayName("should return measurements updated after date")
        void shouldReturnMeasurements_updatedAfterDate() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByDemographicIdUpdatedAfterDate(DEMO_NO, lastWeek);

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should return empty list when none updated after date")
        void shouldReturnEmptyList_whenNoneUpdatedAfterDate() {
            // When
            List<Measurement> result = measurementDao.findByDemographicIdUpdatedAfterDate(DEMO_NO, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicNoTypeAndDate
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicNoTypeAndDate")
    @Tag("read")
    class FindByDemographicNoTypeAndDate {

        @Test
        @DisplayName("should return measurement matching demo, type, and observation date")
        void shouldReturnMeasurement_whenExactMatch() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            Measurement result = measurementDao.findByDemographicNoTypeAndDate(DEMO_NO, "BP", today);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDataField()).isEqualTo("120/80");
        }
    }

    // ========================================================================
    // getDatesForMeasurements
    // ========================================================================

    @Nested
    @DisplayName("getDatesForMeasurements")
    @Tag("read")
    class GetDatesForMeasurements {

        @Test
        @DisplayName("should return distinct dates for measurement types")
        void shouldReturnDistinctDates_forMeasurementTypes() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO, "BP", "110/70", yesterday);

            // When
            List<Date> result = measurementDao.getDatesForMeasurements(
                    DEMO_NO, new String[]{"BP"});

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findDemographicIdsUpdatedAfterDate (integrator support)
    // ========================================================================

    @Nested
    @DisplayName("findDemographicIdsUpdatedAfterDate")
    @Tag("read")
    class FindDemographicIdsUpdatedAfterDate {

        @Test
        @DisplayName("should return demographic IDs with measurements updated after date")
        void shouldReturnDemoIds_whenUpdatedAfterDate() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO_2, "WT", "75", today);

            // When
            List<Integer> result = measurementDao.findDemographicIdsUpdatedAfterDate(lastWeek);

            // Then
            assertThat(result).contains(DEMO_NO, DEMO_NO_2);
        }
    }

    // ========================================================================
    // findDemographicIdsByType
    // ========================================================================

    @Nested
    @DisplayName("findDemographicIdsByType")
    @Tag("read")
    class FindDemographicIdsByType {

        @Test
        @DisplayName("should return demographic IDs having measurements of given types")
        void shouldReturnDemoIds_forGivenTypes() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);
            createAndPersist(DEMO_NO_2, "WT", "75", today);

            // When
            List<Integer> result = measurementDao.findDemographicIdsByType(
                    java.util.Arrays.asList("BP"));

            // Then
            assertThat(result).contains(DEMO_NO);
            assertThat(result).doesNotContain(DEMO_NO_2);
        }
    }

    // ========================================================================
    // findByProviderDemographicLastUpdateDate
    // ========================================================================

    @Nested
    @DisplayName("findByProviderDemographicLastUpdateDate")
    @Tag("read")
    class FindByProviderDemographicLastUpdateDate {

        @Test
        @DisplayName("should return measurements for provider and demographic updated after date")
        void shouldReturnMeasurements_forProviderDemoAndDate() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByProviderDemographicLastUpdateDate(
                    PROVIDER_NO, DEMO_NO, lastWeek, 10);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByDemoNoTypeDateAndMeasuringInstruction
    // ========================================================================

    @Nested
    @DisplayName("findByDemoNoTypeDateAndMeasuringInstruction")
    @Tag("read")
    class FindByDemoNoTypeDateAndMeasuringInstruction {

        @Test
        @DisplayName("should return measurements matching all criteria")
        void shouldReturnMeasurements_whenAllCriteriaMatch() {
            // Given
            createAndPersistWithInstruction(DEMO_NO, "BP", "120/80", today, "Sitting");

            // When
            List<Measurement> result = measurementDao.findByDemoNoTypeDateAndMeasuringInstruction(
                    DEMO_NO, yesterday, tomorrow, "BP", "Sitting");

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findByAppointmentNoAndType
    // ========================================================================

    @Nested
    @DisplayName("findByAppointmentNoAndType")
    @Tag("read")
    class FindByAppointmentNoAndType {

        @Test
        @DisplayName("should return measurements for appointment and type")
        void shouldReturnMeasurements_forAppointmentAndType() {
            // Given
            createAndPersistWithApptNo(DEMO_NO, "BP", "120/80", today, 5001);
            createAndPersistWithApptNo(DEMO_NO, "WT", "75", today, 5001);

            // When
            List<Measurement> result = measurementDao.findByAppointmentNoAndType(5001, "BP");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo("BP");
        }
    }

    // ========================================================================
    // findLatestByAppointmentNoAndType
    // ========================================================================

    @Nested
    @DisplayName("findLatestByAppointmentNoAndType")
    @Tag("read")
    class FindLatestByAppointmentNoAndType {

        @Test
        @DisplayName("should return latest measurement for appointment and type")
        void shouldReturnLatest_forAppointmentAndType() {
            // Given
            createAndPersistWithApptNo(DEMO_NO, "BP", "120/80", today, 5001);

            // When
            Measurement result = measurementDao.findLatestByAppointmentNoAndType(5001, "BP");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDataField()).isEqualTo("120/80");
        }

        @Test
        @DisplayName("should return null when no matching measurement")
        void shouldReturnNull_whenNoMatch() {
            // When
            Measurement result = measurementDao.findLatestByAppointmentNoAndType(99999, "BP");

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // findByDemographicId (AbstractDao inherited)
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicId")
    @Tag("read")
    class FindByDemographicId {

        @Test
        @DisplayName("should return measurements for demographic")
        void shouldReturnMeasurements_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", today);

            // When
            List<Measurement> result = measurementDao.findByDemographicId(DEMO_NO);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // getMeasurementsPriorToDate
    // ========================================================================

    @Nested
    @DisplayName("getMeasurementsPriorToDate")
    @Tag("read")
    class GetMeasurementsPriorToDate {

        @Test
        @DisplayName("should return latest measurements before date keyed by type")
        void shouldReturnLatestBeforeDate_keyedByType() {
            // Given
            createAndPersist(DEMO_NO, "BP", "120/80", yesterday);
            createAndPersist(DEMO_NO, "WT", "75", yesterday);

            // When
            HashMap<String, Measurement> result = measurementDao.getMeasurementsPriorToDate(DEMO_NO, today);

            // Then
            assertThat(result).containsKeys("BP", "WT");
        }
    }
}
