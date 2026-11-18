/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.appt.ApptStatusData;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.providers.gate.ProviderAddStatusValidator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Applies provider-schedule appointment status transitions as one locked,
 * transactional unit of work.
 *
 * @since 2026-07-29
 */
@Service
public class AppointmentStatusTransitionService {

    private static final Logger LOGGER = MiscUtils.getLogger();

    private final OscarAppointmentDao appointmentDao;
    private final AppointmentArchiveDao appointmentArchiveDao;
    private final EventService eventService;

    public AppointmentStatusTransitionService(
            OscarAppointmentDao appointmentDao,
            AppointmentArchiveDao appointmentArchiveDao,
            EventService eventService) {
        this.appointmentDao = appointmentDao;
        this.appointmentArchiveDao = appointmentArchiveDao;
        this.eventService = eventService;
    }

    /**
     * Locks and re-reads the appointment, validates the submitted transition
     * against that locked state, then archives and updates it in one transaction.
     * The status-change event is published only after the transaction commits.
     *
     * @param appointmentNo appointment primary key
     * @param submittedProviderNo provider rendered into the schedule link
     * @param submittedCurrentStatus status rendered into the schedule link
     * @param requestedStatus requested next status
     * @param updatedBy authenticated provider applying the transition
     * @return authoritative provider and updated status
     * @throws AppointmentStatusTransitionException when a precondition fails
     */
    @Transactional
    public TransitionResult transition(
            int appointmentNo,
            String submittedProviderNo,
            String submittedCurrentStatus,
            String requestedStatus,
            String updatedBy) {
        return transition(appointmentNo, submittedProviderNo, submittedCurrentStatus, requestedStatus,
                updatedBy, null);
    }

    /**
     * As {@link #transition(int, String, String, String, String)}, and audits the committed
     * change as {@code update}/{@code appointment} for the appointment and its patient. With an
     * {@code actor} the audit row also carries the client address and the security id; the row
     * names the new status and never the appointment's reason or notes.
     *
     * @param actor the signed-in user the request came from, or {@code null} when none is at hand
     * @since 2026-11-12
     */
    @Transactional
    public TransitionResult transition(
            int appointmentNo,
            String submittedProviderNo,
            String submittedCurrentStatus,
            String requestedStatus,
            String updatedBy,
            LoggedInInfo actor) {
        Appointment appointment = appointmentDao.findForUpdate(appointmentNo);
        if (appointment == null) {
            throw failure(
                    AppointmentStatusTransitionException.Reason.APPOINTMENT_NOT_FOUND,
                    "Appointment " + appointmentNo + " does not exist");
        }

        if (!ProviderAddStatusValidator.matchesCurrentStatus(
                appointment.getStatus(), submittedCurrentStatus)) {
            throw failure(
                    AppointmentStatusTransitionException.Reason.STALE_STATUS,
                    "Appointment " + appointmentNo + " status changed before this transition");
        }

        ApptStatusData statusData = new ApptStatusData();
        statusData.setApptStatus(appointment.getStatus());
        String calculatedNextStatus = statusData.getNextStatus();
        if (!ProviderAddStatusValidator.matchesCalculatedNextStatus(
                calculatedNextStatus, requestedStatus)) {
            throw failure(
                    AppointmentStatusTransitionException.Reason.INVALID_TRANSITION,
                    "Appointment " + appointmentNo + " transition is not allowed");
        }

        String authoritativeProviderNo = appointment.getProviderNo();
        if (authoritativeProviderNo == null
                || !authoritativeProviderNo.equals(submittedProviderNo)) {
            throw failure(
                    AppointmentStatusTransitionException.Reason.PROVIDER_MISMATCH,
                    "Appointment " + appointmentNo + " provider does not match");
        }

        appointmentArchiveDao.archiveAppointment(appointment);
        appointment.setStatus(requestedStatus);
        appointment.setLastUpdateUser(updatedBy);
        appointmentDao.merge(appointment);

        int demographicNo = appointment.getDemographicNo();
        publishAfterCommit(appointmentNo, authoritativeProviderNo, requestedStatus,
                () -> audit(actor, updatedBy, appointmentNo, demographicNo, requestedStatus));
        return new TransitionResult(authoritativeProviderNo, requestedStatus);
    }

    /**
     * Queues the audit row of a committed status change. Best effort, like the event: the status is
     * already stored, so neither an audit failure nor a missing actor may undo or fail the request.
     */
    private static void audit(LoggedInInfo actor, String updatedBy, int appointmentNo, int demographicNo,
                              String appointmentStatus) {
        String patient = demographicNo > 0 ? String.valueOf(demographicNo) : null;
        String data = "status=" + appointmentStatus;
        try {
            if (actor != null) {
                LogAction.addLog(actor, LogConst.UPDATE, LogConst.CON_APPT, String.valueOf(appointmentNo), patient, data);
            } else {
                LogAction.addLog(updatedBy, LogConst.UPDATE, LogConst.CON_APPT, String.valueOf(appointmentNo), null,
                        patient, data);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Unable to queue the audit row of a committed appointment status change ({})",
                    e.getClass().getSimpleName());
        }
    }

    private void publishAfterCommit(
            int appointmentNo,
            String providerNo,
            String appointmentStatus,
            Runnable audit) {
        Runnable publish = () -> {
            // After the commit, never before: a rolled-back transition leaves no audit row
            // claiming a change, and the row is written outside the finished transaction.
            audit.run();
            try {
                eventService.appointmentStatusChanged(
                        this,
                        String.valueOf(appointmentNo),
                        providerNo,
                        appointmentStatus);
            } catch (RuntimeException e) {
                LOGGER.error(
                        "Unable to publish committed appointment status change for appointment {}",
                        appointmentNo,
                        e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish.run();
                        }
                    });
        } else {
            publish.run();
        }
    }

    private static AppointmentStatusTransitionException failure(
            AppointmentStatusTransitionException.Reason reason,
            String message) {
        return new AppointmentStatusTransitionException(reason, message);
    }

    public record TransitionResult(String providerNo, String appointmentStatus) {
    }
}
