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
import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;
import io.github.carlos_emr.carlos.commn.model.LabReportInformation;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LabPatientPhysicianInfoDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code LabPatientPhysicianInfoDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see LabPatientPhysicianInfoDao
 */
@DisplayName("LabPatientPhysicianInfo Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class LabPatientPhysicianInfoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LabPatientPhysicianInfoDao labPatientPhysicianInfoDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist labpatientphysicianinfo with generated ID")
        void shouldPersistLabPatientPhysicianInfo_whenValidDataProvided() throws Exception {
            LabPatientPhysicianInfo entity = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            labPatientPhysicianInfoDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find labpatientphysicianinfo by ID")
        void shouldFindLabPatientPhysicianInfo_whenValidIdProvided() throws Exception {
            LabPatientPhysicianInfo saved = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            labPatientPhysicianInfoDao.persist(saved);
            LabPatientPhysicianInfo found = labPatientPhysicianInfoDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all labpatientphysicianinfo records")
        void shouldCountAllLabPatientPhysicianInfos() throws Exception {
            LabPatientPhysicianInfo entity = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            labPatientPhysicianInfoDao.persist(entity);
            long count = labPatientPhysicianInfoDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }

    /**
     * {@code findByPatientName} backs the legacy CML/MDS Inbox search. A patient without a health
     * card number (NULL HIN) must still be found when the HIN search field is blank, and must not
     * match a non-blank HIN search.
     */
    @Nested
    @DisplayName("Patient search without a health card number")
    class PatientSearchWithoutHealthNumber {

        private static final String PROVIDER = "999998";
        private static final String LAB_TYPE = "CML";

        private int routeLabWithoutHin(String lastName) throws Exception {
            return routeLab(lastName, null);
        }

        private int routeLab(String lastName, String hin) throws Exception {
            LabPatientPhysicianInfo info = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(info);
            info.setPatientLastName(lastName);
            info.setPatientFirstName("Fixture");
            info.setPatientHin(hin);
            labPatientPhysicianInfoDao.persist(info);

            ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
            routing.setLabNo(info.getId());
            routing.setLabType(LAB_TYPE);
            routing.setProviderNo(PROVIDER);
            routing.setStatus("N");
            entityManager.persist(routing);
            entityManager.flush();
            return info.getId();
        }

        @Test
        @Tag("search")
        @DisplayName("should find a lab by name when the patient has no HIN and the HIN field is blank")
        void shouldFindLab_whenPatientHinIsNull() throws Exception {
            int labNo = routeLabWithoutHin("Nullhincml");

            List<Object[]> results = labPatientPhysicianInfoDao.findByPatientName("N", LAB_TYPE, PROVIDER,
                    "Nullhincml", "", "");

            assertThat(results).extracting(row -> ((LabPatientPhysicianInfo) row[0]).getId()).containsExactly(labNo);
        }

        @Test
        @Tag("search")
        @DisplayName("should find a lab by name when the patient has a HIN and the HIN field is blank")
        void shouldFindLab_whenHinFieldIsBlank() throws Exception {
            int labNo = routeLab("Withhincml", "9876543210");

            List<Object[]> results = labPatientPhysicianInfoDao.findByPatientName("N", LAB_TYPE, PROVIDER,
                    "Withhincml", "", "");

            assertThat(results).extracting(row -> ((LabPatientPhysicianInfo) row[0]).getId()).containsExactly(labNo);
        }

        @Test
        @Tag("search")
        @DisplayName("should find a lab when its exact HIN is searched")
        void shouldFindLab_whenExactHinIsSearched() throws Exception {
            int labNo = routeLab("Exacthincml", "9876543211");

            List<Object[]> results = labPatientPhysicianInfoDao.findByPatientName("N", LAB_TYPE, PROVIDER,
                    "Exacthincml", "", "9876543211");

            assertThat(results).extracting(row -> ((LabPatientPhysicianInfo) row[0]).getId()).containsExactly(labNo);
        }

        @Test
        @Tag("search")
        @DisplayName("should not match a patient without a HIN when a health number is searched")
        void shouldExcludeNullHinPatient_whenHealthNumberIsSearched() throws Exception {
            routeLabWithoutHin("Nullhincmlsearched");

            assertThat(labPatientPhysicianInfoDao.findByPatientName("N", LAB_TYPE, PROVIDER,
                    "Nullhincmlsearched", "", "12345")).isEmpty();
        }
    }

    /**
     * {@code findLabServiceDatesByLabId} backs the CML report-version chain (issue #4568). Both date
     * columns are the {@code service_date varchar(10)} property, so they come back as {@code String}s that
     * the chain code must parse; it once cast them to {@code Date}. A unit test that mocks this DAO proves
     * nothing about what the query returns, so the row types and order are pinned here against a real query.
     */
    @Nested
    @DisplayName("Version chain lookup")
    class VersionChainLookup {

        private static final String ACCESSION = "ACC-4568-A";

        /** Persists one report: its print header (which orders the chain) and its patient/physician row. */
        private int version(String accession, String serviceDate, String printDate, String printTime) throws Exception {
            LabReportInformation header = new LabReportInformation();
            header.setLocationId("70");
            header.setPrintDate(printDate);
            header.setPrintTime(printTime);
            header.setTotalBType("1");
            header.setTotalCType("1");
            header.setTotalDType("0");
            entityManager.persist(header);
            entityManager.flush();

            LabPatientPhysicianInfo info = new LabPatientPhysicianInfo();
            EntityDataGenerator.generateTestDataForModelClass(info);
            info.setLabReportInfoId(header.getId());
            info.setAccessionNum(accession);
            info.setServiceDate(serviceDate);
            labPatientPhysicianInfoDao.persist(info);
            entityManager.flush();
            return info.getId();
        }

        @Test
        @Tag("query")
        @DisplayName("should return every version of the accession with its service date as the stored string")
        void shouldReturnServiceDatesAsStrings_forEveryVersionOfTheAccession() throws Exception {
            int preliminary = version(ACCESSION, "20260829", "20260829", "08:00");
            int finalReport = version(ACCESSION, "20260901", "20260901", "09:30");

            List<Object[]> rows = labPatientPhysicianInfoDao.findLabServiceDatesByLabId(finalReport);

            assertThat(rows).hasSize(2);
            for (Object[] row : rows) {
                assertThat(row[0]).isInstanceOf(Integer.class);
                assertThat(row[1]).isInstanceOf(String.class);
                assertThat(row[2]).isInstanceOf(String.class);
                // The third column is always the requested report's own service date.
                assertThat(row[2]).isEqualTo("20260901");
            }
            assertThat(rows).extracting(row -> row[0], row -> row[1])
                    .containsExactly(tuple(preliminary, "20260829"), tuple(finalReport, "20260901"));
        }

        @Test
        @Tag("query")
        @DisplayName("should order the versions by print date and time, not by insertion order")
        void shouldOrderVersionsByPrintDateAndTime_notByInsertion() throws Exception {
            // Inserted newest first so that neither the id nor the insertion order matches the print order.
            int corrected = version(ACCESSION, "20260901", "20260901", "14:15");
            int finalReport = version(ACCESSION, "20260901", "20260901", "09:30");
            int preliminary = version(ACCESSION, "20260829", "20260829", "08:00");

            List<Object[]> rows = labPatientPhysicianInfoDao.findLabServiceDatesByLabId(corrected);

            assertThat(rows).extracting(row -> row[0]).containsExactly(preliminary, finalReport, corrected);
        }

        @Test
        @Tag("query")
        @DisplayName("should leave out reports of other accession numbers")
        void shouldExcludeOtherAccessions_fromTheVersionLookup() throws Exception {
            int requested = version(ACCESSION, "20260901", "20260901", "09:30");
            version("ACC-4568-OTHER", "20260901", "20260901", "09:30");

            List<Object[]> rows = labPatientPhysicianInfoDao.findLabServiceDatesByLabId(requested);

            assertThat(rows).extracting(row -> row[0]).containsExactly(requested);
        }

        @ParameterizedTest(name = "accession number [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        @Tag("query")
        @DisplayName("should find no versions for a report without an accession number")
        void shouldFindNoVersions_whenTheReportHasNoAccessionNumber(String blankAccession) throws Exception {
            // A blank accession equals every other blank one; treating it as a key would chain unrelated reports.
            // (NULL is never equal to NULL in SQL, so that case holds with or without the guard; the empty and
            // whitespace values are what the guard is for.)
            int requested = version(blankAccession, "20260901", "20260901", "09:30");
            version(blankAccession, "20260901", "20260901", "10:30");

            assertThat(labPatientPhysicianInfoDao.findLabServiceDatesByLabId(requested)).isEmpty();
        }
    }
}
