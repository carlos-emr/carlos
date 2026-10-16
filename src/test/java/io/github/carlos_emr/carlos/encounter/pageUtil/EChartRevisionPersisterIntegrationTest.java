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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.AppointmentChange;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.Outcome;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.Result;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Actual eChart, appointment and archive rows for the legacy encounter editor's save (issue #4673):
 * a revision is appended only for the submitting chart's patient, only on top of the revision that
 * editor was opened from, and only with an appointment of that patient. Every refusal writes
 * nothing, and the other patient's rows are compared before and after each operation.
 *
 * <p>Transactions commit for real (no test transaction) so the patient row lock and the
 * concurrent-save case are exercised; each test removes exactly the rows it created.</p>
 *
 * @since 2026-10-16
 */
@Tag("integration")
@Tag("encounter")
@DisplayName("Legacy encounter save: revision and appointment ownership")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EChartRevisionPersisterIntegrationTest extends CarlosTestBase {
    private static final String PROVIDER = "999998";

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private EChartDao charts;
    @Autowired
    private OscarAppointmentDao appointments;
    @Autowired
    private AppointmentArchiveDao archives;

    private TransactionTemplate tx;
    private EChartRevisionPersister persister;
    private final List<Integer> patients = new ArrayList<>();
    private final List<Integer> visits = new ArrayList<>();
    private int a;
    private int b;
    private int aBase;
    private int bBase;
    private int aVisit;
    private int bVisit;

    @BeforeEach
    void seedTwoPatientsWithChartsAndVisits() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            a = patient("Legacy A");
            b = patient("Legacy B");
            aBase = chart(a, "A history");
            bBase = chart(b, "B history");
            aVisit = appointment(a, "t");
            bVisit = appointment(b, "t");
        });
        persister = new EChartRevisionPersister(transactionManager, entityManagerFactory, charts, appointments, archives);
    }

    @AfterEach
    void removeOwnedRows() {
        tx.executeWithoutResult(status -> {
            if (!visits.isEmpty()) {
                entityManager.createQuery("delete from AppointmentArchive x where x.appointmentNo in :ids")
                        .setParameter("ids", visits).executeUpdate();
                entityManager.createQuery("delete from Appointment x where x.id in :ids")
                        .setParameter("ids", visits).executeUpdate();
            }
            if (!patients.isEmpty()) {
                entityManager.createQuery("delete from EChart x where x.demographicNo in :ids")
                        .setParameter("ids", patients).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no IN (:ids)")
                        .setParameter("ids", patients).executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("should append the revision when the editor's base is still the patient's latest")
    void shouldAppendRevision_whenBaseIsPatientsLatest() {
        List<String> bBefore = state(b);

        Result result = persister.save(a, aBase, null, AppointmentChange.NONE, revision("A edited"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
        assertThat(encounters(a)).containsExactly("A history", "A edited");
        assertThat(result.revision().getId()).isEqualTo(charts.getMaxIdForDemographic(a));
        assertThat(result.revision().getDemographicNo()).isEqualTo(a);
        assertThat(state(b)).isEqualTo(bBefore);
    }

    @Test
    @DisplayName("should refuse a base revision that another save has since superseded")
    void shouldRefuseStaleBase_whenAnotherRevisionWasSavedSince() {
        tx.executeWithoutResult(status -> chart(a, "Saved by another window"));
        List<String> aBefore = state(a);

        Result result = persister.save(a, aBase, null, AppointmentChange.NONE, revision("Older copy"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.STALE_REVISION);
        assertThat(state(a)).isEqualTo(aBefore);
    }

    @Test
    @DisplayName("should refuse another patient's revision as the base, writing neither chart")
    void shouldRefuseBase_whenItIsAnotherPatientsRevision() {
        List<String> aBefore = state(a);
        List<String> bBefore = state(b);

        Result result = persister.save(a, bBase, null, AppointmentChange.NONE, revision("Cross-patient"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.FOREIGN_REVISION);
        assertThat(state(a)).isEqualTo(aBefore);
        assertThat(state(b)).isEqualTo(bBefore);
    }

    @Test
    @DisplayName("should refuse a save without a base when the patient already has a revision")
    void shouldRefuseMissingBase_whenPatientAlreadyHasRevisions() {
        List<String> aBefore = state(a);

        Result result = persister.save(a, null, null, AppointmentChange.NONE, revision("Blind overwrite"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.STALE_REVISION);
        assertThat(state(a)).isEqualTo(aBefore);
    }

    @Test
    @DisplayName("should save a patient's first revision when the editor was opened on an empty chart")
    void shouldSaveFirstRevision_whenPatientHadNone() {
        int c = tx.execute(status -> patient("Legacy C"));

        Result result = persister.save(c, null, null, AppointmentChange.NONE, revision("C first"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
        assertThat(encounters(c)).containsExactly("C first");
    }

    @Test
    @DisplayName("should refuse another patient's appointment without writing the chart or the appointment")
    void shouldRefuseAppointment_whenItBelongsToAnotherPatient() {
        List<String> aBefore = state(a);
        List<String> bBefore = state(b);

        Result result = persister.save(a, aBase, bVisit, AppointmentChange.SIGN, revision("Signed"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.FOREIGN_APPOINTMENT);
        assertThat(state(a)).isEqualTo(aBefore);
        assertThat(state(b)).isEqualTo(bBefore);
    }

    @ParameterizedTest(name = "{0} turns H into {1}")
    @CsvSource({"SIGN,HS", "VERIFY,HV"})
    @DisplayName("should mark the patient's own appointment from its current status and archive the old one")
    void shouldMarkOwnAppointment_fromItsCurrentStatus(AppointmentChange change, String expected) {
        // Reception marked the patient Here after the editor was opened from a To Do visit.
        tx.executeWithoutResult(status -> entityManager.createQuery(
                        "update Appointment x set x.status = 'H', x.lastUpdateUser = 'reception' where x.id = :id")
                .setParameter("id", aVisit).executeUpdate());
        List<String> bBefore = state(b);

        Result result = persister.save(a, aBase, aVisit, change, revision("Marked"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
        assertThat(result.appointmentUpdated()).isTrue();
        assertThat(result.appointmentNo()).isEqualTo(aVisit);
        Appointment visit = visit(aVisit);
        assertThat(visit.getStatus()).isEqualTo(expected);
        assertThat(visit.getLastUpdateUser()).isEqualTo(PROVIDER);
        assertThat(archived(aVisit)).singleElement().satisfies(old -> {
            assertThat(old.getStatus()).isEqualTo("H");
            assertThat(old.getLastUpdateUser()).isEqualTo("reception");
        });
        assertThat(encounters(a)).containsExactly("A history", "Marked");
        assertThat(state(b)).isEqualTo(bBefore);
    }

    @ParameterizedTest(name = "status [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("should save the chart and leave a visit without a status as it is when signing")
    void shouldLeaveVisitWithoutStatus_whenSigning(String blank) {
        // A sign derives the next status from the current one; there is none to derive it from.
        int unmarked = tx.execute(status -> appointment(a, blank));
        List<String> bBefore = state(b);

        Result result = persister.save(a, aBase, unmarked, AppointmentChange.SIGN, revision("Signed"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
        assertThat(result.appointmentUpdated()).isFalse();
        assertThat(result.appointmentNo()).isEqualTo(unmarked);
        assertThat(visit(unmarked).getStatus()).isEqualTo(blank);
        assertThat(visit(unmarked).getLastUpdateUser()).isEqualTo("previous");
        assertThat(archived(unmarked)).isEmpty();
        assertThat(encounters(a)).containsExactly("A history", "Signed");
        assertThat(state(b)).isEqualTo(bBefore);
    }

    @Test
    @DisplayName("should report the date of the patient's own visit the save was bound to")
    void shouldReportVisitDate_whenSaveIsBoundToOwnVisit() {
        Result result = persister.save(a, aBase, aVisit, AppointmentChange.NONE, revision("Dated"), PROVIDER);

        assertThat(result.appointmentNo()).isEqualTo(aVisit);
        assertThat(result.appointmentDate()).isEqualTo(visit(aVisit).getAppointmentDate());
    }

    @Test
    @DisplayName("should leave the appointment untouched when the save neither signs nor verifies")
    void shouldLeaveAppointment_whenSaveDoesNotMarkIt() {
        Result result = persister.save(a, aBase, aVisit, AppointmentChange.NONE, revision("Plain save"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
        assertThat(result.appointmentUpdated()).isFalse();
        assertThat(result.appointmentNo()).isEqualTo(aVisit);
        assertThat(visit(aVisit).getStatus()).isEqualTo("t");
        assertThat(archived(aVisit)).isEmpty();
    }

    @Test
    @DisplayName("should save the chart without an appointment when the visit no longer exists")
    void shouldSaveWithoutAppointment_whenVisitWasDeleted() {
        int deleted = tx.execute(status -> appointment(a, "t"));
        tx.executeWithoutResult(status -> entityManager.createQuery("delete from Appointment x where x.id = :id")
                .setParameter("id", deleted).executeUpdate());

        Result result = persister.save(a, aBase, deleted, AppointmentChange.SIGN, revision("Signed late"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED);
        assertThat(result.appointmentUpdated()).isFalse();
        assertThat(result.appointmentNo()).isNull();
        assertThat(encounters(a)).containsExactly("A history", "Signed late");
        assertThat(archived(deleted)).isEmpty();
    }

    @Test
    @DisplayName("should report a missing patient without writing a revision")
    void shouldReportMissingPatient_withoutWriting() {
        int missing = tx.execute(status -> {
            int gone = patient("Legacy gone");
            entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no = :id")
                    .setParameter("id", gone).executeUpdate();
            return gone;
        });

        Result result = persister.save(missing, null, null, AppointmentChange.NONE, revision("Orphan"), PROVIDER);

        assertThat(result.outcome()).isEqualTo(Outcome.PATIENT_NOT_FOUND);
        assertThat(encounters(missing)).isEmpty();
    }

    @Test
    @DisplayName("should roll the revision back when the appointment write fails")
    void shouldRollBackRevision_whenAppointmentWriteFails() {
        AppointmentArchiveDao failing = mock(AppointmentArchiveDao.class, delegatesTo(archives));
        doAnswer(call -> {
            archives.archiveAppointment(call.getArgument(0));
            entityManager.flush();
            throw new IllegalStateException("archive write failed");
        }).when(failing).archiveAppointment(any());
        EChartRevisionPersister broken = new EChartRevisionPersister(transactionManager, entityManagerFactory,
                charts, appointments, failing);
        List<String> aBefore = state(a);
        EChart lost = revision("Lost");

        assertThatThrownBy(() -> broken.save(a, aBase, aVisit, AppointmentChange.SIGN, lost, PROVIDER))
                .isInstanceOf(IllegalStateException.class);

        assertThat(state(a)).isEqualTo(aBefore);
        assertThat(visit(aVisit).getStatus()).isEqualTo("t");
        assertThat(archived(aVisit)).isEmpty();
    }

    @Test
    @DisplayName("should save once when two windows save from the same base at the same time")
    void shouldSaveOnce_whenTwoSavesStartFromTheSameBase() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Outcome> save = () -> {
            start.await(10, TimeUnit.SECONDS);
            return persister.save(a, aBase, null, AppointmentChange.NONE, revision("Concurrent"), PROVIDER).outcome();
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(save);
            var second = executor.submit(save);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(Outcome.SAVED, Outcome.STALE_REVISION);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(encounters(a)).containsExactly("A history", "Concurrent");
    }

    private int patient(String name) {
        Demographic patient = new Demographic();
        patient.setFirstName(name);
        patient.setLastName("Isolation4673");
        patient.setSex("U");
        patient.setProviderNo(PROVIDER);
        entityManager.persist(patient);
        entityManager.flush();
        patients.add(patient.getDemographicNo());
        return patient.getDemographicNo();
    }

    private int chart(int patient, String encounter) {
        EChart chart = revision(encounter);
        chart.setDemographicNo(patient);
        entityManager.persist(chart);
        entityManager.flush();
        return chart.getId();
    }

    private int appointment(int patient, String status) {
        Appointment visit = new Appointment();
        visit.setDemographicNo(patient);
        visit.setProviderNo(PROVIDER);
        visit.setAppointmentDate(new Date());
        visit.setStartTime(new Date());
        visit.setEndTime(new Date());
        visit.setName("Isolation4673");
        visit.setStatus(status);
        visit.setLastUpdateUser("previous");
        entityManager.persist(visit);
        entityManager.flush();
        visits.add(visit.getId());
        return visit.getId();
    }

    private static EChart revision(String encounter) {
        EChart chart = new EChart();
        chart.setTimestamp(new Date());
        chart.setProviderNo(PROVIDER);
        chart.setSubject("");
        chart.setSocialHistory("social");
        chart.setFamilyHistory("family");
        chart.setMedicalHistory("medical");
        chart.setOngoingConcerns("concerns");
        chart.setReminders("reminders");
        chart.setEncounter(encounter);
        return chart;
    }

    private List<String> encounters(int patient) {
        return tx.execute(status -> entityManager.createQuery(
                        "select x.encounter from EChart x where x.demographicNo = :id order by x.id", String.class)
                .setParameter("id", patient).getResultList());
    }

    /** Every eChart, appointment and archive column of one patient, for before/after comparison. */
    private List<String> state(int patient) {
        return tx.execute(status -> {
            List<String> rows = new ArrayList<>();
            for (EChart chart : entityManager.createQuery(
                    "from EChart x where x.demographicNo = :id order by x.id", EChart.class)
                    .setParameter("id", patient).getResultList()) {
                rows.add("chart:" + chart.getId() + "|" + chart.getProviderNo() + "|" + chart.getSubject() + "|"
                        + chart.getEncounter() + "|" + chart.getSocialHistory() + "|" + chart.getTimestamp().getTime());
            }
            for (Appointment visit : entityManager.createQuery(
                    "from Appointment x where x.demographicNo = :id order by x.id", Appointment.class)
                    .setParameter("id", patient).getResultList()) {
                rows.add("visit:" + visit.getId() + "|" + visit.getStatus() + "|" + visit.getLastUpdateUser());
                for (AppointmentArchive old : archivedIn(visit.getId())) {
                    rows.add("archive:" + old.getId() + "|" + old.getStatus());
                }
            }
            return rows;
        });
    }

    private Appointment visit(int id) {
        return tx.execute(status -> entityManager.find(Appointment.class, id));
    }

    private List<AppointmentArchive> archived(int appointmentNo) {
        return tx.execute(status -> archivedIn(appointmentNo));
    }

    private List<AppointmentArchive> archivedIn(int appointmentNo) {
        return entityManager.createQuery("from AppointmentArchive x where x.appointmentNo = :id order by x.id",
                AppointmentArchive.class).setParameter("id", appointmentNo).getResultList();
    }
}
