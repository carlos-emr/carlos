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
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
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

    /**
     * The Inbox Hub's Acknowledged toggle lists what was acknowledged today, so the list is
     * bounded by WHEN the routing row was acknowledged. Bounding it by the lab's own date hid a
     * lab acknowledged today whose result was older (#2515); no bound at all listed every lab the
     * provider had ever acknowledged, a page at a time, until the clinician pressed Stop. The
     * toggle combines with the patient, unmatched and name filters, so every query shape takes it.
     */
    @Nested
    @DisplayName("Acknowledged-since bound used by the Inbox Hub toolbar")
    @Tag("filter")
    class AcknowledgedSinceBound {

        private static final String PROVIDER = "778801";
        private final Date startOfToday = Timestamp.valueOf("2026-10-15 00:00:00");

        private int routeAcknowledgedLab(int labNo, String acknowledgedAt, Integer demographicNo) {
            Hl7TextInfo info = createHl7TextInfo("ACK-SINCE-" + labNo, "Acksince", "Fixture", "");
            info.setLabNumber(labNo);
            // Old results both: only the acknowledgement time tells the two labs apart.
            info.setObrDate("2024-01-05 10:00:00");
            hibernateTemplate.flush();
            ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
            routing.setLabNo(labNo);
            routing.setLabType("HL7");
            routing.setProviderNo(PROVIDER);
            routing.setStatus("A");
            routing.setTimestamp(Timestamp.valueOf(acknowledgedAt));
            entityManager.persist(routing);
            if (demographicNo != null) {
                entityManager.persist(new PatientLabRouting(labNo, "HL7", demographicNo));
            }
            entityManager.flush();
            return labNo;
        }

        private Integer createPatient() {
            Demographic demo = new Demographic();
            demo.setFirstName("Acksince");
            demo.setLastName("Patient");
            demo.setSex("F");
            demo.setProviderNo(PROVIDER);
            demo.setPatientStatus("AC");
            demo.setPatientStatusDate(new Date());
            demo.setDateJoined(new Date());
            hibernateTemplate.save(demo);
            hibernateTemplate.flush();
            return demo.getDemographicNo();
        }

        /**
         * Shapes: the provider's whole list; one patient; a name search answered by the labs' own
         * names (unlinked labs) or by the linked patient's name (only the demographic subqueries
         * can answer that); unmatched labs. The bound is inclusive: a lab acknowledged exactly at
         * the start of the day is listed, one acknowledged a second earlier is not.
         */
        @ParameterizedTest(name = "{0}, mixLabsAndDocs={1}")
        @CsvSource({"provider,true", "provider,false", "patient,true", "patient,false",
                "nameSearch,true", "nameSearch,false", "patientNameSearch,true", "patientNameSearch,false",
                "unmatched,true", "unmatched,false"})
        @DisplayName("should list only labs acknowledged since the bound, whatever their result date")
        void shouldListOnlyLabsAcknowledgedSinceBound_inEveryInboxQueryShape(String shape, boolean mixLabsAndDocs) {
            Integer patient = "patient".equals(shape) || "patientNameSearch".equals(shape) ? createPatient() : null;
            int acknowledgedThisMorning = routeAcknowledgedLab(9836001, "2026-10-15 09:12:00", patient);
            int acknowledgedAtMidnight = routeAcknowledgedLab(9836002, "2026-10-15 00:00:00", patient);
            int acknowledgedLastNight = routeAcknowledgedLab(9836003, "2026-10-14 23:59:59", patient);
            int acknowledgedLastYear = routeAcknowledgedLab(9836004, "2025-01-10 16:40:00", patient);
            String demographicNo = "patient".equals(shape) ? String.valueOf(patient) : "unmatched".equals(shape) ? "0" : null;
            String lastName = "nameSearch".equals(shape) ? "Fixture" : "patientNameSearch".equals(shape) ? "Patient" : "";
            boolean nameSearch = !lastName.isEmpty();

            List<Object[]> unbounded = hl7TextInfoDao.findLabAndDocsViaMagic(PROVIDER, demographicNo, "", lastName, "",
                    "A", false, 0, 100, mixLabsAndDocs, null, true, nameSearch, null, null, null);
            List<Object[]> bounded = hl7TextInfoDao.findLabAndDocsViaMagic(PROVIDER, demographicNo, "", lastName, "",
                    "A", false, 0, 100, mixLabsAndDocs, null, true, nameSearch, null, null, startOfToday);

            assertThat(unbounded).as("every lab is acknowledged, so an unbounded search lists them all")
                    .extracting(row -> ((Number) row[1]).intValue())
                    .containsExactlyInAnyOrder(acknowledgedThisMorning, acknowledgedAtMidnight,
                            acknowledgedLastNight, acknowledgedLastYear);
            assertThat(bounded).extracting(row -> ((Number) row[1]).intValue())
                    .containsExactlyInAnyOrder(acknowledgedThisMorning, acknowledgedAtMidnight);
        }
    }

    /**
     * A lab routed to two providers (a lab forwarding rule adds the second routing row) has one
     * routing row per provider. Only the provider filter narrowed that to one row per lab, so an
     * Any provider search listed the lab once per provider in every query shape but the mixed
     * provider-wide list, which grouped by lab (#5016).
     *
     * <p>The mixed provider-wide list and both unmatched lists are not run here: they order by the
     * routing row's id, and H2 refuses a non-grouped column that differs inside a group, which two
     * routing rows always do; MariaDB takes either row. {@link Hl7TextInfoDaoImplOneRowPerLabUnitTest}
     * pins their grouping on the statement.</p>
     */
    @Nested
    @DisplayName("Any provider search on a lab routed to two providers")
    @Tag("search")
    class LabRoutedToTwoProviders {

        private static final String FIRST_PROVIDER = "778811";
        private static final String SECOND_PROVIDER = "778812";

        private void routeLab(int labNo, String firstStatus, String secondStatus, Integer demographicNo) {
            Hl7TextInfo info = createHl7TextInfo("TWO-ROUTES-" + labNo, "Tworoutes", "Fixture", "");
            info.setLabNumber(labNo);
            info.setObrDate("2026-10-01 10:00:00");
            hibernateTemplate.flush();
            for (String[] route : new String[][] {{FIRST_PROVIDER, firstStatus}, {SECOND_PROVIDER, secondStatus}}) {
                ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
                routing.setLabNo(labNo);
                routing.setLabType("HL7");
                routing.setProviderNo(route[0]);
                routing.setStatus(route[1]);
                entityManager.persist(routing);
            }
            if (demographicNo != null) {
                entityManager.persist(new PatientLabRouting(labNo, "HL7", demographicNo));
            }
            entityManager.flush();
        }

        private Integer createPatient() {
            Demographic demo = new Demographic();
            demo.setFirstName("Tworoutes");
            demo.setLastName("Patient");
            demo.setSex("F");
            demo.setProviderNo(FIRST_PROVIDER);
            demo.setPatientStatus("AC");
            demo.setPatientStatusDate(new Date());
            demo.setDateJoined(new Date());
            hibernateTemplate.save(demo);
            hibernateTemplate.flush();
            return demo.getDemographicNo();
        }

        /**
         * Shapes as in {@link AcknowledgedSinceBound}: the labs-only whole list; one patient; a name
         * search answered by the lab's own names or by the linked patient's name. Status "N" is
         * the New list (both routing rows New) and "" is All.
         */
        @ParameterizedTest(name = "{0}, mixLabsAndDocs={1}, status=\"{2}\"")
        @CsvSource({"provider,false,N", "patient,true,N", "patient,false,N",
                "nameSearch,true,N", "nameSearch,false,N", "patientNameSearch,true,N", "patientNameSearch,false,N",
                "provider,false,''", "patient,true,''", "patient,false,''",
                "nameSearch,true,''", "nameSearch,false,''", "patientNameSearch,true,''", "patientNameSearch,false,''"})
        @DisplayName("should list the lab once when no provider is searched")
        void shouldListLabOnce_whenAnyProviderIsSearched(String shape, boolean mixLabsAndDocs, String status) {
            Integer patient = "patient".equals(shape) || "patientNameSearch".equals(shape) ? createPatient() : null;
            routeLab(9837001, "N", "N", patient);
            String demographicNo = "patient".equals(shape) ? String.valueOf(patient) : null;
            String lastName = "nameSearch".equals(shape) ? "Fixture" : "patientNameSearch".equals(shape) ? "Patient" : "";

            List<Object[]> rows = hl7TextInfoDao.findLabAndDocsViaMagic("", demographicNo, "", lastName, "",
                    status, false, 0, 100, mixLabsAndDocs, null, false, !lastName.isEmpty(), null, null, null);

            // Any provider is the whole clinic, so other labs could be listed too; only this one counts.
            List<Object[]> lab = rows.stream().filter(row -> ((Number) row[1]).intValue() == 9837001).toList();
            assertThat(lab).as("the lab is listed once, not once per routing row").hasSize(1);
            assertThat(lab.get(0)[14]).isEqualTo("N");
        }

        @ParameterizedTest(name = "mixLabsAndDocs={0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("should show the searched provider's own status for a lab routed to two providers")
        void shouldShowSearchedProvidersStatus_whenOneProviderIsSearched(boolean mixLabsAndDocs) {
            routeLab(9837002, "A", "N", null);

            List<Object[]> first = hl7TextInfoDao.findLabAndDocsViaMagic(FIRST_PROVIDER, null, "", "", "",
                    "", false, 0, 100, mixLabsAndDocs, null, true, false, null, null, null);
            List<Object[]> second = hl7TextInfoDao.findLabAndDocsViaMagic(SECOND_PROVIDER, null, "", "", "",
                    "", false, 0, 100, mixLabsAndDocs, null, true, false, null, null, null);

            assertThat(first).extracting(row -> ((Number) row[1]).intValue()).containsExactly(9837002);
            assertThat(first.get(0)[14]).isEqualTo("A");
            assertThat(second).extracting(row -> ((Number) row[1]).intValue()).containsExactly(9837002);
            assertThat(second.get(0)[14]).isEqualTo("N");
        }
    }

    @ParameterizedTest
    @CsvSource({"serviceObservation,true", "serviceObservation,false", "receivedCreated,true", "receivedCreated,false"})
    void shouldIncludeWholeEndDay_whenInboxHasObservationAndReceivedTimestamps(String mode, boolean mixed) {
        entityManager.createQuery("delete from SystemPreferences p where p.name = :name")
                .setParameter("name", "inboxDateSearchType").executeUpdate();
        entityManager.persist(new SystemPreferences("inboxDateSearchType", mode));
        List<Integer> expected = new ArrayList<>();
        List<String> timestamps = List.of("2026-03-02 23:59:59", "2026-03-03", "2026-03-03 00:00:00",
                "2026-03-05 14:30:00", "2026-03-05 23:59:59", "2026-03-06", "2026-03-06 00:00:00");
        for (int i = 0; i < timestamps.size(); i++) {
            String timestamp = timestamps.get(i);
            // Offset receipt dates so selecting the wrong date column changes the returned lab IDs.
            int receivedIndex = (i + 3) % timestamps.size();
            String receivedTimestamp = timestamps.get(receivedIndex);
            LocalDateTime received = LocalDateTime.parse((receivedTimestamp.length() == 10
                    ? receivedTimestamp + " 00:00:00" : receivedTimestamp).replace(' ', 'T'));
            Hl7TextMessage message = new Hl7TextMessage();
            ReflectionTestUtils.setField(message, "created", Timestamp.valueOf(received));
            entityManager.persist(message);
            entityManager.flush();
            int labNo = message.getId();
            Hl7TextInfo info = createHl7TextInfo("DATE-BOUNDARY-" + labNo, "Boundary", "Endday", "");
            info.setLabNumber(labNo);
            info.setObrDate(timestamp);
            hibernateTemplate.flush();
            ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
            routing.setLabNo(labNo);
            routing.setLabType("HL7");
            routing.setProviderNo("999998");
            routing.setStatus("N");
            entityManager.persist(routing);
            int filteredIndex = "serviceObservation".equals(mode) ? i : receivedIndex;
            if (filteredIndex >= 1 && filteredIndex <= 4) expected.add(labNo);
        }
        entityManager.flush();
        var rows = hl7TextInfoDao.findLabAndDocsViaMagic("999998", null, "Boundary", "Endday", "",
                "N", false, 0, 100, mixed, null, true, true,
                java.sql.Date.valueOf("2026-03-03"), java.sql.Date.valueOf("2026-03-05"));
        assertThat(rows).extracting(row -> ((Number) row[1]).intValue()).containsExactlyInAnyOrderElementsOf(expected);
    }

}
