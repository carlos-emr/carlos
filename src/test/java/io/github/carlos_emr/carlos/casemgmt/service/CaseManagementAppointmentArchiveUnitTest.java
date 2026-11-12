/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementNoteSaveResult;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the real save orchestration and snapshots the appointment at archive time. */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementAppointmentArchiveUnitTest extends CarlosUnitTestBase {
    private CaseManagementManagerImpl manager;
    private OscarAppointmentDao appointments;
    private AppointmentArchiveDao archives;
    private Appointment appointment;
    private CaseManagementNote note;

    @BeforeEach
    void prepare() {
        registerMock(ProgramManager.class, mock(ProgramManager.class));
        registerMock(AdmissionManager.class, mock(AdmissionManager.class));
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        archives = mock(AppointmentArchiveDao.class);
        manager = spy(new CaseManagementManagerImpl());
        ReflectionTestUtils.setField(manager, "appointmentArchiveDao", archives);
        doNothing().when(manager).saveAndUpdateCaseIssues(anyList());
        doNothing().when(manager).deleteTmpSave(any(), any(), any());
        doReturn("doctor").when(manager).getRoleName(any(), any());
        doReturn("signature").when(manager).getSignature(any(), any(), any(), any(), anyInt());
        appointment = new Appointment();
        appointment.setId(42);
        appointment.setDemographicNo(101);
        appointment.setStatus("t");
        appointment.setLastUpdateUser("original-provider");
        when(appointments.findForUpdate(42)).thenReturn(appointment);
        note = new CaseManagementNote();
        note.setDemographic_no("101");
        note.setProviderNo("999998");
        note.setProgram_no("0");
        note.setAppointmentNo(42);
        note.setNote("Assessment");
        note.setSigned(true);
        doAnswer(call -> {
            assertOriginalAppointment();
            CaseManagementNote saved = ((CaseManagementNote) call.getArgument(1)).copyForRevision();
            saved.setId(502L);
            return new CaseManagementNoteSaveResult(saved, "saved");
        }).when(manager).saveNoteRevision(any(), any(), any(), any(), any(), any());
    }

    private void assertOriginalAppointment() {
        assertThat(appointment.getStatus()).isEqualTo("t");
        assertThat(appointment.getLastUpdateUser()).isEqualTo("original-provider");
    }

    private void save(boolean verify) throws Exception {
        manager.saveCaseManagementNote(mock(LoggedInInfo.class), note, List.of(), null, null,
                verify, Locale.ENGLISH, new Date(), "Doctor", "999998", "192.0.2.1", "");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldArchiveTheOriginalStatusAfterSaving_beforeSignOrVerifyMutatesTheAppointment(boolean verify) throws Exception {
        doAnswer(call -> { assertOriginalAppointment(); return null; }).when(archives).archiveAppointment(appointment);
        save(verify);
        assertThat(appointment.getStatus()).isEqualTo(verify ? "tV" : "tS");
        assertThat(appointment.getLastUpdateUser()).isEqualTo("999998");
        var order = inOrder(manager, archives, appointments);
        order.verify(manager).saveNoteRevision(any(), any(), any(), any(), any(), any());
        order.verify(archives).archiveAppointment(appointment);
        order.verify(appointments).merge(appointment);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldLeaveTheManagedAppointmentUntouched_whenTheRevisionIsRejected(boolean verify) {
        doThrow(new StaleNoteRevisionException()).when(manager).saveNoteRevision(any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> save(verify)).isInstanceOf(StaleNoteRevisionException.class);
        assertOriginalAppointment();
        verifyNoInteractions(archives);
        verify(appointments, never()).merge(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldLeaveTheManagedAppointmentUntouched_whenArchivingFails(boolean verify) {
        doThrow(new IllegalStateException("archive failed")).when(archives).archiveAppointment(appointment);
        assertThatThrownBy(() -> save(verify)).isInstanceOf(IllegalStateException.class);
        assertOriginalAppointment();
        verify(appointments, never()).merge(any());
    }

    @Test
    void shouldRefuseAnotherPatientsAppointment_beforeSavingTheRevision() {
        appointment.setDemographicNo(202);
        assertThatThrownBy(() -> save(false)).isInstanceOf(SecurityException.class);
        assertOriginalAppointment();
        verify(manager, never()).saveNoteRevision(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(archives);
    }

    @Test
    void shouldSaveWithoutAppointmentWrites_whenTheChartHasNoAppointment() throws Exception {
        note.setAppointmentNo(0);
        save(false);
        verifyNoInteractions(archives);
        verify(appointments, never()).merge(any());
        verify(appointments, never()).findForUpdate(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPropagateLookupAndLockFailures_beforeSavingAnyClinicalState(boolean lockFailure) {
        RuntimeException failure = lockFailure ? new jakarta.persistence.PessimisticLockException("locked")
                : new IllegalStateException("lookup failed");
        when(appointments.findForUpdate(42)).thenThrow(failure);
        assertThatThrownBy(() -> save(false)).isSameAs(failure);
        assertOriginalAppointment();
        verify(manager, never()).saveAndUpdateCaseIssues(anyList());
        verify(manager, never()).saveNoteRevision(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(archives);
    }

    @Test
    void shouldPropagateLockedRefreshFailure_beforeSavingAnyClinicalState() {
        var failure = new jakarta.persistence.EntityNotFoundException("locked refresh failed");
        when(appointments.findForUpdate(42)).thenThrow(failure);
        assertThatThrownBy(() -> save(false)).isSameAs(failure);
        assertOriginalAppointment();
        verify(manager, never()).saveAndUpdateCaseIssues(anyList());
        verify(manager, never()).saveNoteRevision(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(archives);
    }
}
