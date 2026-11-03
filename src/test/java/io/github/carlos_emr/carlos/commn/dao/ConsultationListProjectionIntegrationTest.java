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

import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.consultation.dto.*;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;

/** Executes list projections against Hibernate and checks SQL count independently of page size. */
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
class ConsultationListProjectionIntegrationTest extends CarlosTestBase {
    @Autowired private ConsultationRequestDao requests;
    @Autowired private ProfessionalSpecialistDao specialists;
    @Autowired private ConsultationRequestExtDao extensions;
    @Autowired private FaxClientLogDao faxLogs;
    @PersistenceContext private EntityManager entityManager;

    private ConsultationRequest request(int patient, String team, String status, int day) {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(patient);
        request.setStatus(status);
        request.setSendTo(team);
        request.setReferralDate(java.sql.Date.valueOf("2097-03-" + day));
        request.setLastUpdateDate(new Date());
        requests.persist(request);
        return request;
    }

    private ConsultationListFilterDto filter(String team, String sort, String desc, int offset, int limit) {
        return new ConsultationListFilterDto(team, true, null, null, sort, desc, null, offset, limit, null, null);
    }

    private <T> T oneStatement(Supplier<T> read) {
        return statements(1, read);
    }

    private <T> T statements(int expected, Supplier<T> read) {
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            T value = read.get();
            assertThat(stats.getPrepareStatementCount()).isEqualTo(expected);
            assertThat(stats.getEntityLoadCount()).isZero();
            return value;
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void shouldProjectOrphansInOneStatement_forManyConsultations() {
        for (int i = 0; i < 25; i++) request(60001, "projection", "1", 10 + i % 10);
        List<ConsultationListRow> rows = oneStatement(() -> requests.getConsultationListRows(filter("projection", null, null, 0, 50)));
        assertThat(rows).hasSize(25).allSatisfy(row -> {
            assertThat(row.demographicId()).isEqualTo(60001);
            assertThat(row.mrpNo()).isNull();
            assertThat(row.specialistId()).isNull();
            assertThat(row.serviceDescription()).isNull();
        });
        // The production DAO also checks merged IDs once, independently of the number of consult rows.
        assertThat(statements(2, () -> requests.getConsultationListRows(60001))).hasSize(25);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "invalid"})
    void shouldMatchEntityQueryOrderAndPaging_forEverySortToken(String sort) {
        request(60001, "sort", "2", 10);
        request(60001, "sort", "1", 11);
        request(60001, "sort", "4", 12);
        for (String desc : new String[] {null, "1"}) {
            ConsultationListFilterDto filter = filter("sort", sort, desc, 1, 2);
            List<Integer> expected = requests.getConsults(filter).stream().map(ConsultationRequest::getId).toList();
            assertThat(requests.getConsultationListRows(filter)).extracting(ConsultationListRow::id).containsExactlyElementsOf(expected);
        }
    }

    @Test
    void shouldHonorDatesStatusAndVisibility_forScalarRows() {
        ConsultationRequest active = request(60001, "filter", "1", 11);
        request(60001, "filter", "4", 11);
        request(60001, "other", "1", 11);
        ConsultationListFilterDto filter = new ConsultationListFilterDto("filter", false,
                java.sql.Date.valueOf("2097-03-11"), java.sql.Date.valueOf("2097-03-11"), null, null, null,
                0, 50, null, null);
        assertThat(requests.getConsultationListRows(filter)).extracting(ConsultationListRow::id).containsExactly(active.getId());
        ConsultationListFilterDto denied = new ConsultationListFilterDto("filter", true, null, null, null, null,
                null, 0, 50, null, null, Set.of(), null);
        assertThat(requests.getConsultationListRows(denied)).isEmpty();
        ConsultationListFilterDto wrongSite = new ConsultationListFilterDto("filter", true, null, null, null, null,
                null, 0, 50, null, null, null, Set.of("other-site"));
        assertThat(requests.getConsultationListRows(wrongSite)).isEmpty();
    }

    @Test
    void shouldIncludeMergedPatientConsultations_withProductionDaoSubclass() {
        int parentId = patient();
        int childId = patient();
        int deletedId = patient();
        ConsultationRequest parent = request(parentId, "merged", "1", 10);
        ConsultationRequest child = request(childId, "merged", "1", 11);
        request(deletedId, "merged", "1", 12);
        for (int id : List.of(childId, deletedId)) {
            DemographicMerged merge = new DemographicMerged();
            merge.setDemographicNo(id); merge.setMergedTo(parentId); merge.setDeleted(id == deletedId ? 1 : 0);
            entityManager.persist(merge);
        }
        entityManager.flush(); entityManager.clear();
        var productionDao = new ConsultationRequestMergedDemographicDaoImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(productionDao, "entityManager", entityManager);
        assertThat(statements(3, () -> productionDao.getConsultationListRows(parentId)))
                .extracting(ConsultationListRow::id).containsExactly(parent.getId(), child.getId());
        assertThat(productionDao.getConsultationListRows(60004)).isEmpty();
    }

    private int patient() {
        Demographic patient = new Demographic();
        patient.setSex("M"); patient.setFirstName("Synthetic"); patient.setLastName("Projection");
        entityManager.persist(patient);
        return patient.getDemographicNo();
    }

    @Test
    void shouldKeepOneRowPerConsultation_whenServiceExtensionsAreDuplicated() {
        ConsultationRequest request = request(60001, "duplicate", "1", 10);
        request.setServiceId(0);
        requests.merge(request);
        for (String value : List.of("First", "Second")) {
            ConsultationRequestExt ext = new ConsultationRequestExt();
            ext.setRequestId(request.getId()); ext.setKey("ereferral_service"); ext.setValue(value);
            extensions.persist(ext);
        }
        entityManager.flush(); entityManager.clear();
        assertThat(requests.getConsultationListRows(filter("duplicate", null, null, 0, 50)))
                .extracting(ConsultationListRow::id).containsExactly(request.getId());
    }

    @Test
    void shouldProjectSpecialistDirectoryInOneStatement_withSameFieldsAndOrder() {
        for (int i = 0; i < 20; i++) {
            ProfessionalSpecialist specialist = new ProfessionalSpecialist();
            specialist.setFirstName("First" + i);
            specialist.setLastName("Last" + i);
            specialist.setDeleted(i == 19);
            specialist.setPhoneNumber("5550000");
            specialist.setProfessionalLetters("MD");
            specialists.persist(specialist);
        }
        List<SpecialistListRow> rows = oneStatement(specialists::findListRows);
        assertThat(rows).hasSize(19);
        assertThat(rows).extracting(SpecialistListRow::lastName).isSorted();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.phoneNumber()).isEqualTo("5550000");
            assertThat(row.professionalLetters()).isEqualTo("MD");
        });
        assertThat(rows).extracting(SpecialistListRow::id)
                .containsExactlyElementsOf(specialists.findAll().stream().map(ProfessionalSpecialist::getId).toList());
    }

    @Test
    void shouldLoadExtensionsForRequestedPage_withEmptyAndUnknownIds() {
        for (int id : List.of(7, 8, 9)) {
            ConsultationRequestExt ext = new ConsultationRequestExt();
            ext.setRequestId(id); ext.setKey("ereferral_service"); ext.setValue("Service " + id);
            extensions.persist(ext);
        }
        entityManager.flush(); entityManager.clear();
        assertThat(extensions.getConsultationRequestExts(List.of(7, 8)))
                .extracting(ConsultationRequestExt::getRequestId).containsExactlyInAnyOrder(7, 8);
        assertThat(extensions.getConsultationRequestExts(List.of())).isEmpty();
        assertThat(extensions.getConsultationRequestExts(List.of(99))).isEmpty();
    }

    @Test
    void shouldJoinFaxHistoryInOneStatement_withExistingOrderAndMissingJobsSkipped() {
        for (int i = 0; i < 10; i++) {
            FaxJob job = new FaxJob();
            job.setDestination("5550000"); job.setRecipient("Synthetic specialist"); job.setStatus(FaxJob.STATUS.SENT);
            entityManager.persist(job);
            FaxClientLog log = new FaxClientLog();
            log.setRequestId(42); log.setFaxId(job.getId()); log.setStartTime(new Date(1000L * i));
            faxLogs.persist(log);
        }
        FaxClientLog orphan = new FaxClientLog();
        orphan.setRequestId(42); orphan.setFaxId(999999); faxLogs.persist(orphan);
        List<ConsultationFaxRow> rows = oneStatement(() -> faxLogs.findConsultationFaxRows(42));
        assertThat(rows).hasSize(10);
        assertThat(rows).extracting(ConsultationFaxRow::sent).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(rows).allSatisfy(row -> assertThat(row.status()).isEqualTo(FaxJob.STATUS.SENT));
        assertThat(faxLogs.findConsultationFaxRows(99)).isEmpty();
    }
}
