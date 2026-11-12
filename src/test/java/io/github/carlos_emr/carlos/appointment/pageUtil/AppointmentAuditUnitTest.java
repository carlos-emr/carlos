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
package io.github.carlos_emr.carlos.appointment.pageUtil;

import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The audit rows of the appointment popup's writes (Add, Update, Cancel, Delete, Cut). Each write
 * leaves one row that names the appointment ({@code contentId}), its patient
 * ({@code demographic_no}) and, through the {@link LoggedInInfo} it is written with, the provider
 * and the client address. No row carries the reason, notes or the patient's name.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("appointment")
@Tag("security")
@DisplayName("Appointment popup audit rows")
class AppointmentAuditUnitTest extends CarlosUnitTestBase {

    private static final String REASON = "FAKE-REASON chest pain follow-up";
    private static final String NOTES = "FAKE-NOTES bring previous imaging";

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final AppointmentArchiveDao archives = mock(AppointmentArchiveDao.class);
    private final ScheduleManager schedule = mock(ScheduleManager.class);
    private final EventService events = mock(EventService.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<OtherIdManager> otherIds;
    private MockedStatic<WaitingList> waitingLists;

    @BeforeEach
    void setUpActions() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(OscarAppointmentDao.class, appointments);
        registerMock(AppointmentArchiveDao.class, archives);
        registerMock(ScheduleManager.class, schedule);
        registerMock(WaitingListDao.class, mock(WaitingListDao.class));
        registerMock(EventService.class, events);
        registerMock(PlatformTransactionManager.class,
                new AppointmentAddRecord2ActionSubmissionUnitTest.RecordingTransactionManager());
        // OtherIdManager resolves its DAO when the class is first loaded.
        registerMock(OtherIdDAO.class, mock(OtherIdDAO.class));

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        response = new MockHttpServletResponse();
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        when(info.getLoggedInProviderNo()).thenReturn("999998");

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenAnswer(inv -> request);
        servlet.when(ServletActionContext::getResponse).thenAnswer(inv -> response);
        otherIds = mockStatic(OtherIdManager.class);
        WaitingList noWaitingList = mock(WaitingList.class);
        when(noWaitingList.getFound()).thenReturn(false);
        waitingLists = mockStatic(WaitingList.class);
        waitingLists.when(WaitingList::getInstance).thenReturn(noWaitingList);

        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), anyString(), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDownActions() {
        for (MockedStatic<?> mocked : new MockedStatic<?>[]{waitingLists, otherIds, servlet}) {
            if (mocked != null) mocked.close();
        }
    }

    private Appointment stored(int id, int demographicNo, String status) {
        Appointment appt = new Appointment();
        appt.setId(id);
        appt.setDemographicNo(demographicNo);
        appt.setProviderNo("999998");
        appt.setStatus(status);
        appt.setName("FAKE-PATIENT,Audit");
        appt.setReason(REASON);
        appt.setNotes(NOTES);
        appt.setLastUpdateUser("999998");
        when(appointments.find(id)).thenReturn(appt);
        // The edit, Delete and Cut buttons go through the manager, which locks the row, applies the
        // edit or archives and removes it, and hands back the row it wrote.
        when(schedule.updateAppointment(eq(info), eq(id), any(), any())).thenAnswer(call -> {
            call.<java.util.function.Consumer<Appointment>>getArgument(3).accept(appt);
            return appt;
        });
        when(schedule.removeAppointment(eq(info), eq(id), any())).thenReturn(appt);
        return appt;
    }

    private void fillEditForm(String demographicNo, String status) {
        request.setParameter("appointment_no", "77");
        request.setParameter("demographic_no", demographicNo);
        request.setParameter("provider_no", "999998");
        request.setParameter("appointment_date", "2027-11-20");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:14");
        request.setParameter("keyword", "FAKE-PATIENT,Audit");
        request.setParameter("reason", REASON + " edited");
        request.setParameter("notes", NOTES);
        request.setParameter("status", status);
    }

    /** No audit call of any overload may carry the appointment's free text or the patient's name. */
    private void assertNoAppointmentTextIsAudited() {
        logActionMock.verify(() -> LogAction.addLog(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                nullable(String.class), org.mockito.ArgumentMatchers.argThat(AppointmentAuditUnitTest::carriesText)), never());
        logActionMock.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class), anyString(), anyString(),
                anyString(), nullable(String.class), org.mockito.ArgumentMatchers.argThat(AppointmentAuditUnitTest::carriesText)), never());
    }

    private static boolean carriesText(String data) {
        return data != null && (data.contains("FAKE-REASON") || data.contains("FAKE-NOTES") || data.contains("FAKE-PATIENT"));
    }

    @Test
    void shouldAuditTheBookingAgainstItsPatient_whenAnAppointmentIsAdded() throws Exception {
        doAnswer(inv -> {
            inv.<Appointment>getArgument(0).setId(501);
            return null;
        }).when(appointments).persist(any(Appointment.class));
        request.setParameter("provider_no", "999998");
        request.setParameter("demographic_no", "42");
        request.setParameter("appointment_date", "2027-11-20");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:14");
        request.setParameter("keyword", "FAKE-PATIENT,Audit");
        request.setParameter("reason", REASON);
        request.setParameter("notes", NOTES);
        request.setParameter("status", "t");
        Demographic patient = new Demographic();
        patient.setLastName("FAKE-PATIENT");
        patient.setFirstName("Audit");

        String result;
        try (MockedConstruction<DemographicMerged> merged = mockConstruction(DemographicMerged.class,
                (mock, context) -> when(mock.getHead("42")).thenReturn("42"));
             MockedConstruction<DemographicData> data = mockConstruction(DemographicData.class,
                     (mock, context) -> when(mock.getDemographic(info, "42")).thenReturn(patient))) {
            result = new AppointmentAddRecord2Action().execute();
        }

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        logActionMock.verify(() -> LogAction.addLog(info, LogConst.ADD, LogConst.CON_APPT, "501", "42", null), times(1));
        assertNoAppointmentTextIsAudited();
    }

    @Test
    void shouldAuditTheBookingWithoutAPatient_whenTheSlotIsBookedForNoPatient() throws Exception {
        doAnswer(inv -> {
            inv.<Appointment>getArgument(0).setId(502);
            return null;
        }).when(appointments).persist(any(Appointment.class));
        request.setParameter("provider_no", "999998");
        request.setParameter("appointment_date", "2027-11-20");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:14");
        request.setParameter("keyword", "FAKE-PATIENT,Meeting");
        request.setParameter("status", "t");

        new AppointmentAddRecord2Action().execute();

        logActionMock.verify(() -> LogAction.addLog(info, LogConst.ADD, LogConst.CON_APPT, "502", null, null), times(1));
    }

    @Test
    void shouldNotAuditABooking_whenNothingWasStored() throws Exception {
        // persist() assigns no id: the booking did not reach the table.
        request.setParameter("provider_no", "999998");
        request.setParameter("appointment_date", "2027-11-20");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:14");
        request.setParameter("keyword", "FAKE-PATIENT,Meeting");

        new AppointmentAddRecord2Action().execute();

        logActionMock.verify(() -> LogAction.addLog(any(LoggedInInfo.class), eq(LogConst.ADD), eq(LogConst.CON_APPT),
                anyString(), nullable(String.class), nullable(String.class)), never());
    }

    @Test
    void shouldAuditTheEditAgainstItsPatient_whenAnAppointmentIsUpdated() throws Exception {
        stored(77, 42, "t");
        fillEditForm("42", "t");

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        logActionMock.verify(() -> LogAction.addLog(info, LogConst.UPDATE, LogConst.CON_APPT, "77", "42", null), times(1));
        assertNoAppointmentTextIsAudited();
    }

    @Test
    void shouldNameTheNewStatus_whenAnEditCancelsTheAppointment() throws Exception {
        stored(77, 42, "t");
        fillEditForm("42", "C");

        new AppointmentUpdateRecord2Action().execute();

        logActionMock.verify(() -> LogAction.addLog(info, LogConst.UPDATE, LogConst.CON_APPT, "77", "42", "status=C"),
                times(1));
        assertNoAppointmentTextIsAudited();
    }

    @Test
    void shouldNameTheNewStatus_whenTheNoShowButtonIsUsed() throws Exception {
        stored(77, 42, "t");
        request.setParameter("appointment_no", "77");
        request.setParameter("buttoncancel", "No Show");

        new AppointmentUpdateRecord2Action().execute();

        logActionMock.verify(() -> LogAction.addLog(info, LogConst.UPDATE, LogConst.CON_APPT, "77", "42", "status=N"),
                times(1));
    }

    @Test
    void shouldNotAuditAnEdit_whenTheAppointmentDoesNotExist() throws Exception {
        request.setParameter("appointment_no", "78");

        new AppointmentUpdateRecord2Action().execute();

        assertThat(response.getStatus()).isEqualTo(404);
        logActionMock.verify(() -> LogAction.addLog(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                nullable(String.class), nullable(String.class)), never());
    }

    @Test
    void shouldAuditTheRemovalAgainstItsPatient_whenAnAppointmentIsDeleted() throws Exception {
        stored(77, 42, "t");
        request.setParameter("appointment_no", "77");

        String result = new AppointmentDeleteRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // The row is written in the request thread, for the appointment the manager archived and removed.
        verify(schedule).removeAppointment(eq(info), eq(77), any());
        logActionMock.verify(() -> LogAction.addLogSynchronous(info, LogConst.DELETE, LogConst.CON_APPT, "77", "42",
                null), times(1));
        assertNoAppointmentTextIsAudited();
    }

    @Test
    void shouldNotAuditARemoval_whenAStaleDeleteIsRefused() throws Exception {
        stored(77, 42, "t");
        request.setParameter("appointment_no", "77");
        when(schedule.removeAppointment(eq(info), eq(77), any()))
                .thenThrow(new AppointmentUpdateException(AppointmentUpdateException.Reason.STALE_VERSION));

        new AppointmentDeleteRecord2Action().execute();

        assertThat(response.getStatus()).isEqualTo(409);
        logActionMock.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class)), never());
    }

    @Test
    void shouldNotAuditACut_whenAStaleCutIsRefused() throws Exception {
        stored(77, 42, "t");
        request.setParameter("appointment_no", "77");
        when(schedule.removeAppointment(eq(info), eq(77), any()))
                .thenThrow(new AppointmentUpdateException(AppointmentUpdateException.Reason.STALE_VERSION));

        new AppointmentCutRecord2Action().execute();

        assertThat(response.getStatus()).isEqualTo(409);
        logActionMock.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class)), never());
    }

    @Test
    void shouldKeepTheCutReceiptAndNameThePatient_whenAnAppointmentIsCut() throws Exception {
        stored(77, 42, "t");
        request.setParameter("appointment_no", "77");

        String result = new AppointmentCutRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // The action name and "id=N" data are the receipt the cut-recovery check reads.
        logActionMock.verify(() -> LogAction.addLogSynchronous(info, "Appointment.cut", LogConst.CON_APPT, "77", "42",
                "id=77"), times(1));
        assertNoAppointmentTextIsAudited();
    }
}
