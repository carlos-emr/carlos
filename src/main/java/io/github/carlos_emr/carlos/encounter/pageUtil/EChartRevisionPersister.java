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

import io.github.carlos_emr.carlos.appt.ApptStatusData;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.EChart;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Date;
import java.util.Objects;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes one save of the legacy encounter editor ({@code /encounter/SaveEncounter2}) as a new
 * {@code eChart} revision of the submitting chart's patient (issue #4673).
 *
 * <p>The editor used to take the patient and the revision it was opened from out of the login-wide
 * {@code EctSessionBean} and the session's {@code eChartID}, which follow whichever chart was
 * entered last in any window, so one window's text could be appended to another patient's chart.
 * Every identity now comes from the submitting request and is checked here, in one transaction,
 * after the patient's row is locked:</p>
 * <ul>
 *   <li>the revision the editor was opened from must be the patient's own and still the newest
 *       (otherwise another save has landed since, and appending would drop its changes);</li>
 *   <li>an appointment the editor names must belong to the patient. One that no longer exists is
 *       treated as no appointment, as the chart's navbar does; one that now belongs to another
 *       patient refuses the save.</li>
 * </ul>
 * <p>A refusal writes nothing. On Sign or Verify the appointment's status is derived from the
 * status it has now, not the one it had when the editor opened, and its old values are archived
 * only after the revision is written, so a failure leaves the appointment unchanged too. A visit
 * with no status is saved against but not marked.</p>
 *
 * <p>The patient lock serializes legacy saves of one patient; it is a scalar locking read, because
 * loading {@code Demographic} runs legacy property setters that can mark null fields dirty.</p>
 *
 * @since 2026-10-16
 */
public class EChartRevisionPersister {

    /** What a save does to the appointment the editor was opened from. */
    public enum AppointmentChange { NONE, SIGN, VERIFY }

    /** How a save ended. Only {@link #SAVED} wrote anything. */
    public enum Outcome {
        SAVED,
        PATIENT_NOT_FOUND,
        /** The base revision does not exist or belongs to another patient. */
        FOREIGN_REVISION,
        /** Another revision was saved after the editor's base, or the editor had no base. */
        STALE_REVISION,
        /** The appointment now belongs to another patient. */
        FOREIGN_APPOINTMENT
    }

    /**
     * @param outcome            how the save ended
     * @param revision           the written revision, or {@code null} unless saved
     * @param appointmentUpdated whether an appointment of the patient was signed or verified
     * @param appointmentNo      the patient's existing appointment the save was bound to, or
     *                           {@code null} when it named none or that appointment no longer exists
     * @param appointmentDate    that appointment's date, or {@code null} with no appointment
     */
    public record Result(Outcome outcome, EChart revision, boolean appointmentUpdated, Integer appointmentNo,
                         Date appointmentDate) { }

    private final PlatformTransactionManager transactions;
    private final EntityManagerFactory entityManagers;
    private final EChartDao charts;
    private final OscarAppointmentDao appointments;
    private final AppointmentArchiveDao archives;

    public EChartRevisionPersister(PlatformTransactionManager transactions, EntityManagerFactory entityManagers,
                                   EChartDao charts, OscarAppointmentDao appointments, AppointmentArchiveDao archives) {
        this.transactions = transactions;
        this.entityManagers = entityManagers;
        this.charts = charts;
        this.appointments = appointments;
        this.archives = archives;
    }

    /**
     * Appends {@code revision} as the patient's newest revision when the editor's base is still
     * the newest, and signs or verifies the patient's appointment as requested.
     *
     * @param patient       the submitting chart's patient
     * @param baseRevision  the revision the editor was opened from, or {@code null} when the
     *                      patient had none
     * @param appointmentNo the appointment the editor was opened from, or {@code null}
     * @param change        what to do to that appointment
     * @param revision      the unsaved revision; its patient is set here
     * @param provider      provider recorded as the appointment's last updater
     * @return the outcome; nothing was written unless it is {@link Outcome#SAVED}
     */
    public Result save(int patient, Integer baseRevision, Integer appointmentNo, AppointmentChange change,
                       EChart revision, String provider) {
        return new TransactionTemplate(transactions).execute(status -> {
            if (!lockPatient(patient)) {
                return refused(status, Outcome.PATIENT_NOT_FOUND);
            }
            Outcome refusal = revisionRefusal(patient, baseRevision);
            if (refusal != null) {
                return refused(status, refusal);
            }
            Appointment appointment = appointmentNo == null ? null : appointments.findForUpdate(appointmentNo);
            if (appointment != null && appointment.getDemographicNo() != patient) {
                return refused(status, Outcome.FOREIGN_APPOINTMENT);
            }

            revision.setDemographicNo(patient);
            charts.persist(revision);

            boolean marked = change != AppointmentChange.NONE && hasStatus(appointment);
            if (marked) {
                mark(appointment, change, provider);
            }
            return new Result(Outcome.SAVED, revision, marked, appointment == null ? null : appointment.getId(),
                    appointment == null ? null : appointment.getAppointmentDate());
        });
    }

    /** Locks the patient's row for this transaction; false when the patient no longer exists. */
    private boolean lockPatient(int patient) {
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagers);
        if (em == null) throw new IllegalStateException("Encounter save transaction has no entity manager");
        return !em.createNativeQuery("SELECT demographic_no FROM demographic WHERE demographic_no = :patient FOR UPDATE")
                .setParameter("patient", patient).getResultList().isEmpty();
    }

    /** Why the editor's base revision cannot be appended to, or null when it is the patient's newest. */
    private Outcome revisionRefusal(int patient, Integer baseRevision) {
        if (baseRevision != null) {
            EChart base = charts.find(baseRevision);
            if (base == null || base.getDemographicNo() != patient) {
                return Outcome.FOREIGN_REVISION;
            }
        }
        return Objects.equals(charts.getMaxIdForDemographic(patient), baseRevision) ? null : Outcome.STALE_REVISION;
    }

    /**
     * Whether the visit can be marked: the next status is derived from the current one, and a visit
     * without one is left as it is (deriving from "" throws, from null writes ""), as the editor did
     * before #4673.
     */
    private static boolean hasStatus(Appointment appointment) {
        return appointment != null && appointment.getStatus() != null && !appointment.getStatus().isBlank();
    }

    /** Archives the appointment's current values, then signs or verifies it from its current status. */
    private void mark(Appointment appointment, AppointmentChange change, String provider) {
        ApptStatusData statuses = new ApptStatusData();
        statuses.setApptStatus(appointment.getStatus());
        String next = change == AppointmentChange.SIGN ? statuses.signStatus() : statuses.verifyStatus();
        archives.archiveAppointment(appointment);
        appointment.setStatus(next);
        appointment.setLastUpdateUser(provider);
        appointments.merge(appointment);
    }

    private static Result refused(TransactionStatus status, Outcome outcome) {
        status.setRollbackOnly();
        return new Result(outcome, null, false, null, null);
    }
}
