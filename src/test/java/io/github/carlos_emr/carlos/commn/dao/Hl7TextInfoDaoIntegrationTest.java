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
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link Hl7TextInfoDao} covering lab result metadata
 * queries, accession number lookups, and status-based filtering.
 *
 * <p>Migrated from legacy {@code Hl7TextInfoDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage for lab result lifecycle queries.</p>
 *
 * @since 2026-03-07
 * @see Hl7TextInfoDao
 */
@DisplayName("Hl7TextInfoDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class Hl7TextInfoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7TextInfoDao hl7TextInfoDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static int labCounter = 5000;

    /**
     * Creates an Hl7TextInfo record using Hibernate Session directly because the
     * entity uses {@code @GeneratedValue(IDENTITY)} with primitive {@code int} ID
     * which does not correctly set the generated value in H2 with JPA persist.
     */
    private Hl7TextInfo createHl7TextInfo(String accessionNo,
                                           String firstName, String lastName, String healthNo) {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setAccessionNumber(accessionNo);
        info.setFirstName(firstName);
        info.setLastName(lastName);
        info.setHealthNumber(healthNo);
        info.setObrDate(new Date().toString());
        info.setReportStatus("F");
        info.setFinalResultCount(0);
        hibernateTemplate.save(info);
        hibernateTemplate.flush();
        return info;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist HL7 text info record")
        void shouldPersistHl7TextInfo_whenValidDataProvided() {
            Hl7TextInfo info = createHl7TextInfo("ACC001", "John", "Smith", "1234567890");
            assertThat(info).isNotNull();
            assertThat(info.getAccessionNumber()).isEqualTo("ACC001");
            assertThat(info.getFirstName()).isEqualTo("John");
            assertThat(info.getLastName()).isEqualTo("Smith");
        }

        @Test
        @Tag("read")
        @DisplayName("should find HL7 text info by ID")
        void shouldFindHl7TextInfo_whenValidIdProvided() {
            Hl7TextInfo saved = createHl7TextInfo("ACC002", "Jane", "Doe", "9876543210");
            // The entity uses primitive int for ID with @GeneratedValue(IDENTITY).
            // Hibernate may not update the primitive field, so query by accession number instead.
            @SuppressWarnings("unchecked")
            List<Hl7TextInfo> results = (List<Hl7TextInfo>) (List<?>) hibernateTemplate.find(
                    "FROM Hl7TextInfo h WHERE h.accessionNumber = ?1", "ACC002");
            assertThat(results).isNotEmpty();
            Hl7TextInfo found = results.get(0);
            assertThat(found).isNotNull();
            assertThat(found.getAccessionNumber()).isEqualTo("ACC002");
            assertThat(found.getFirstName()).isEqualTo("Jane");
        }
    }

    @Nested
    @DisplayName("Lab result queries")
    class LabResultQueries {

        @BeforeEach
        void setUp() {
            createHl7TextInfo("ACC-A001", "Patient", "Alpha", "HIN001");
            createHl7TextInfo("ACC-A002", "Patient", "Beta", "HIN002");
            createHl7TextInfo("ACC-B001", "Patient", "Gamma", "HIN003");
        }

        @Test
        @Tag("query")
        @DisplayName("should count all HL7 text info records")
        void shouldCountAllRecords() {
            long count = hl7TextInfoDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("read")
        @DisplayName("should find all records")
        void shouldFindAllRecords() {
            List<Hl7TextInfo> all = hl7TextInfoDao.findAll(0, 100);
            assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"N", "A", "F"})
    void shouldApplyPatientAndStatusFilters_whenLabsAndDocumentsAreMixed(String status) {
        var info = createHl7TextInfo("PLR-FILTER-" + status, "QueryFixture", "Filter", "9090909090");
        info.setLabNumber(9834001);
        info.setObrDate("2026-09-27 10:00:00");
        hibernateTemplate.flush();
        var routing = new io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel();
        routing.setLabNo(9834001);
        routing.setLabType("HL7");
        routing.setProviderNo("999998");
        routing.setStatus(status);
        entityManager.persist(routing);
        entityManager.flush();
        var results = hl7TextInfoDao.findLabAndDocsViaMagic("999998", null, "QueryFixture", "Filter", "",
                status, false, 0, 100, true, null, true, true, null, null);
        assertThat(results).hasSize(1);
        assertThat(((Number) results.get(0)[1]).intValue()).isEqualTo(9834001);
        assertThat(results.get(0)[14]).isEqualTo(status);
    }


    /**
     * The Inbox patient search matched a lab through {@code d.hin LIKE :patientHealthNumber}, which
     * a NULL HIN never satisfies, so a patient without a health card number (uninsured, a
     * newborn, out of province, an imported record) disappeared from every name search.
     */
    @Nested
    @DisplayName("Inbox patient search with a missing health card number")
    @Tag("search")
    class PatientSearchWithoutHealthNumber {

        private static final String PROVIDER = "999998";

        private Integer createDemographic(String lastName, String hin) {
            Demographic demo = new Demographic();
            demo.setFirstName("Nohin");
            demo.setLastName(lastName);
            demo.setHin(hin);
            demo.setSex("F");
            demo.setProviderNo(PROVIDER);
            demo.setPatientStatus("AC");
            demo.setPatientStatusDate(new Date());
            demo.setDateJoined(new Date());
            hibernateTemplate.save(demo);
            hibernateTemplate.flush();
            return demo.getDemographicNo();
        }

        private void routeLab(int labNo, String healthNo, Integer demographicNo) {
            Hl7TextInfo info = createHl7TextInfo("NULLHIN-" + labNo, "Nohin", "Fixture", healthNo);
            info.setLabNumber(labNo);
            info.setObrDate("2026-09-30 10:00:00");
            hibernateTemplate.flush();
            ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
            routing.setLabNo(labNo);
            routing.setLabType("HL7");
            routing.setProviderNo(PROVIDER);
            routing.setStatus("N");
            entityManager.persist(routing);
            if (demographicNo != null) {
                entityManager.persist(new PatientLabRouting(labNo, "HL7", demographicNo));
            }
            entityManager.flush();
        }

        private List<Object[]> searchByName(String lastName, String healthNumber, boolean mixLabsAndDocs) {
            return hl7TextInfoDao.findLabAndDocsViaMagic(PROVIDER, null, "", lastName, healthNumber,
                    "N", false, 0, 100, mixLabsAndDocs, null, true, true, null, null);
        }

        @ParameterizedTest(name = "mixLabsAndDocs={0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("should find a matched lab by name when the patient has no HIN")
        void shouldFindMatchedLab_whenPatientHinIsNull(boolean mixLabsAndDocs) {
            Integer demographicNo = createDemographic("Nullhinmatched", null);
            routeLab(9835001, "9090909090", demographicNo);

            List<Object[]> results = searchByName("Nullhinmatched", "", mixLabsAndDocs);

            assertThat(results).extracting(row -> ((Number) row[1]).intValue()).containsExactly(9835001);
        }

        @ParameterizedTest(name = "mixLabsAndDocs={0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("should find an unmatched lab by name when the lab carries no health number")
        void shouldFindUnmatchedLab_whenLabHealthNumberIsNull(boolean mixLabsAndDocs) {
            routeLab(9835002, null, null);

            List<Object[]> results = searchByName("Fixture", "", mixLabsAndDocs);

            assertThat(results).extracting(row -> ((Number) row[1]).intValue()).contains(9835002);
        }

        @ParameterizedTest(name = "mixLabsAndDocs={0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("should not match a patient without a HIN when a health number is searched")
        void shouldExcludeNullHinPatient_whenHealthNumberIsSearched(boolean mixLabsAndDocs) {
            Integer demographicNo = createDemographic("Nullhinsearched", null);
            routeLab(9835003, null, demographicNo);

            assertThat(searchByName("Nullhinsearched", "12345", mixLabsAndDocs)).isEmpty();
        }
    }

    @Test
    void shouldFindLabWithoutHealthNumber_whenNameSearchLeavesHinBlank() {
        var info = createHl7TextInfo("PLR-NO-HIN", "QueryFixture", "NoHin", null);
        info.setLabNumber(9834002);
        info.setObrDate("2026-09-27 10:00:00");
        hibernateTemplate.flush();
        var routing = new io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel();
        routing.setLabNo(9834002);
        routing.setLabType("HL7");
        routing.setProviderNo("999998");
        routing.setStatus("N");
        entityManager.persist(routing);
        entityManager.flush();

        var matches = hl7TextInfoDao.findLabAndDocsViaMagic("999998", null, "QueryFixture", "NoHin", "",
                "N", false, 0, 100, true, null, true, true, null, null);
        assertThat(matches).hasSize(1);
        assertThat(((Number) matches.get(0)[1]).intValue()).isEqualTo(9834002);
        assertThat(hl7TextInfoDao.findLabAndDocsViaMagic("999998", null, "QueryFixture", "NoHin", "other-hin",
                "N", false, 0, 100, true, null, true, true, null, null)).isEmpty();
    }

}
