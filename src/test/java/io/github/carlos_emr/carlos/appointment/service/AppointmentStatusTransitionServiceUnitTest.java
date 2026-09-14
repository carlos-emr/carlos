/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppointmentStatusTransitionService")
@Tag("unit")
@Tag("appointment")
class AppointmentStatusTransitionServiceUnitTest {

    private OscarAppointmentDao appointmentDao;
    private AppointmentArchiveDao appointmentArchiveDao;
    private EventService eventService;
    private AppointmentStatusTransitionService service;

    @BeforeEach
    void setUp() {
        appointmentDao = mock(OscarAppointmentDao.class);
        appointmentArchiveDao = mock(AppointmentArchiveDao.class);
        eventService = mock(EventService.class);
        service = new AppointmentStatusTransitionService(
                appointmentDao, appointmentArchiveDao, eventService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldLockArchiveAndUpdateInOrder_whenTransitionIsValid() {
        Appointment appointment = appointment("T", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);
        TransactionSynchronizationManager.initSynchronization();

        AppointmentStatusTransitionService.TransitionResult result =
                service.transition(42, "7", "T", "H", "9");

        assertThat(result.providerNo()).isEqualTo("7");
        assertThat(result.appointmentStatus()).isEqualTo("H");
        assertThat(appointment.getStatus()).isEqualTo("H");
        assertThat(appointment.getLastUpdateUser()).isEqualTo("9");

        InOrder mutationOrder = inOrder(appointmentDao, appointmentArchiveDao);
        mutationOrder.verify(appointmentDao).findForUpdate(42);
        mutationOrder.verify(appointmentArchiveDao).archiveAppointment(appointment);
        mutationOrder.verify(appointmentDao).merge(appointment);
    }

    @Test
    void shouldRejectSecondConcurrentTransition_whenLockedStatusIsAlreadyChanged() {
        Appointment appointment = appointment("H", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);

        assertThatThrownBy(() -> service.transition(42, "7", "T", "H", "9"))
                .isInstanceOf(AppointmentStatusTransitionException.class)
                .extracting("reason")
                .isEqualTo(AppointmentStatusTransitionException.Reason.STALE_STATUS);

        verify(appointmentArchiveDao, never()).archiveAppointment(appointment);
        verify(appointmentDao, never()).merge(appointment);
        verify(eventService, never()).appointmentStatusChanged(
                service, "42", "7", "H");
    }

    @Test
    void shouldRejectTransitionBeforeMutation_whenRequestedStatusIsNotNext() {
        Appointment appointment = appointment("T", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);

        assertThatThrownBy(() -> service.transition(42, "7", "T", "C", "9"))
                .isInstanceOf(AppointmentStatusTransitionException.class)
                .extracting("reason")
                .isEqualTo(AppointmentStatusTransitionException.Reason.INVALID_TRANSITION);

        verify(appointmentArchiveDao, never()).archiveAppointment(appointment);
        verify(appointmentDao, never()).merge(appointment);
    }

    @Test
    void shouldRejectTransitionBeforeMutation_whenProviderDoesNotMatchLockedAppointment() {
        Appointment appointment = appointment("T", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);

        assertThatThrownBy(() -> service.transition(42, "8", "T", "H", "9"))
                .isInstanceOf(AppointmentStatusTransitionException.class)
                .extracting("reason")
                .isEqualTo(AppointmentStatusTransitionException.Reason.PROVIDER_MISMATCH);

        verify(appointmentArchiveDao, never()).archiveAppointment(appointment);
        verify(appointmentDao, never()).merge(appointment);
    }

    @Test
    void shouldPublishEventOnlyAfterCommit_whenTransitionSucceeds() {
        Appointment appointment = appointment("T", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);
        TransactionSynchronizationManager.initSynchronization();

        service.transition(42, "7", "T", "H", "9");

        verify(eventService, never()).appointmentStatusChanged(
                service, "42", "7", "H");
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.get(0).afterCommit();

        verify(eventService).appointmentStatusChanged(service, "42", "7", "H");
    }

    @Test
    void shouldNotPublishEvent_whenTransactionRollsBack() {
        Appointment appointment = appointment("T", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);
        TransactionSynchronizationManager.initSynchronization();

        service.transition(42, "7", "T", "H", "9");
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(eventService, never()).appointmentStatusChanged(
                service, "42", "7", "H");
    }

    @Test
    void shouldAbortBeforeChangingAppointment_whenArchiveFails() {
        Appointment appointment = appointment("T", "7");
        when(appointmentDao.findForUpdate(42)).thenReturn(appointment);
        doThrow(new IllegalStateException("archive failed"))
                .when(appointmentArchiveDao).archiveAppointment(appointment);

        assertThatThrownBy(() -> service.transition(42, "7", "T", "H", "9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("archive failed");

        assertThat(appointment.getStatus()).isEqualTo("T");
        verify(appointmentDao, never()).merge(appointment);
        verify(eventService, never()).appointmentStatusChanged(
                service, "42", "7", "H");
    }

    @Test
    void shouldDeclareTransactionBoundary_onStatusTransition() throws Exception {
        Method transition = AppointmentStatusTransitionService.class.getMethod(
                "transition",
                int.class,
                String.class,
                String.class,
                String.class,
                String.class);

        assertThat(transition.getAnnotation(Transactional.class)).isNotNull();
    }

    private static Appointment appointment(String status, String providerNo) {
        Appointment appointment = new Appointment();
        appointment.setId(42);
        appointment.setStatus(status);
        appointment.setProviderNo(providerNo);
        return appointment;
    }
}
