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
import io.github.carlos_emr.carlos.commn.model.Prescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PrescriptionDao} covering demographic-based
 * lookups, date-range queries, and script update operations.
 *
 * <p>Migrated from legacy {@code PrescriptionDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PrescriptionDao
 */
@DisplayName("PrescriptionDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("prescription")
@Transactional
public class PrescriptionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PrescriptionDao prescriptionDao;

    private static final int DEMO_NO = 40001;
    private static final int DEMO_NO_2 = 40002;

    private Prescription createPrescription(int demographicNo) {
        Prescription rx = new Prescription();
        rx.setDemographicId(demographicNo);
        rx.setProviderNo("999998");
        rx.setDatePrescribed(new Date());
        rx.setLastUpdateDate(new Date());
        prescriptionDao.persist(rx);
        return rx;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist prescription with generated ID")
        void shouldPersistPrescription_whenValidDataProvided() {
            Prescription rx = createPrescription(DEMO_NO);
            assertThat(rx.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find prescription by ID")
        void shouldFindPrescription_whenValidIdProvided() {
            Prescription saved = createPrescription(DEMO_NO);
            Prescription found = prescriptionDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getDemographicId()).isEqualTo(DEMO_NO);
        }
    }

    @Nested
    @DisplayName("findByDemographicId")
    class FindByDemographicId {

        @BeforeEach
        void setUp() {
            createPrescription(DEMO_NO);
            createPrescription(DEMO_NO);
            createPrescription(DEMO_NO_2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return prescriptions for specific demographic")
        void shouldReturnPrescriptions_forSpecificDemographic() {
            List<Prescription> results = prescriptionDao.findByDemographicId(DEMO_NO);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(rx -> rx.getDemographicId() == DEMO_NO);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for demographic with no prescriptions")
        void shouldReturnEmpty_forDemographicWithNoPrescriptions() {
            List<Prescription> results = prescriptionDao.findByDemographicId(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUpdateDate (date-based queries)")
    class FindByUpdateDate {

        @Test
        @Tag("query")
        @DisplayName("should find prescriptions updated after yesterday")
        void shouldFindPrescriptions_updatedAfterYesterday() {
            createPrescription(DEMO_NO);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            List<Prescription> results = prescriptionDao.findByUpdateDate(cal.getTime(), 99);
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty for future date")
        void shouldReturnEmpty_forFutureDate() {
            createPrescription(DEMO_NO);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            List<Prescription> results = prescriptionDao.findByUpdateDate(cal.getTime(), 99);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should respect items-to-return limit")
        void shouldRespectLimit_forUpdateDateQuery() {
            for (int i = 0; i < 5; i++) {
                createPrescription(DEMO_NO);
            }
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            List<Prescription> results = prescriptionDao.findByUpdateDate(cal.getTime(), 2);
            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByDemographicIdUpdatedAfterDate")
    class FindByDemographicIdUpdatedAfterDate {

        @Test
        @Tag("query")
        @DisplayName("should find prescriptions for demographic updated after date")
        void shouldFindPrescriptions_forDemographicUpdatedAfterDate() {
            createPrescription(DEMO_NO);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);

            List<Prescription> results = prescriptionDao.findByDemographicIdUpdatedAfterDate(
                DEMO_NO, cal.getTime());
            assertThat(results).isNotEmpty();
            assertThat(results).allMatch(rx -> rx.getDemographicId() == DEMO_NO);
        }

        @Test
        @Tag("query")
        @DisplayName("should not return prescriptions from other demographics")
        void shouldNotReturnPrescriptions_fromOtherDemographics() {
            createPrescription(DEMO_NO);
            createPrescription(DEMO_NO_2);
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_YEAR, -1);

            List<Prescription> results = prescriptionDao.findByDemographicIdUpdatedAfterDate(
                DEMO_NO, cal.getTime());
            assertThat(results).allMatch(rx -> rx.getDemographicId() == DEMO_NO);
        }
    }
}
