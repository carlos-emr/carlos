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

import io.github.carlos_emr.carlos.commn.dao.EncounterWindowDao;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.commn.model.EncounterWindow;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.AppointmentChange;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.Outcome;
import io.github.carlos_emr.carlos.encounter.pageUtil.EChartRevisionPersister.Result;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The legacy encounter editor's save ({@code /encounter/SaveEncounter2}) binds the patient, the
 * revision and the appointment to the submitting chart window (issue #4673). It used to take the
 * patient, the entry metadata and the revision from the login-wide {@code EctSessionBean} and the
 * session's {@code eChartID}, both rewritten whenever another chart was entered, so saving A's
 * editor after opening B appended A's text to B's chart and signed B's appointment.
 *
 * <p>Each refusal is checked to happen before any write: the revision persister and the layout
 * DAO are never touched and the shared bean keeps its contents. The database behaviour of an
 * accepted save is covered by {@link EChartRevisionPersisterIntegrationTest}.</p>
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("encounter")
@DisplayName("Legacy encounter save patient isolation")
class EctSaveEncounterPatientIsolationUnitTest {
    private static final String A = "900001";
    private static final String B = "900002";

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final EChartRevisionPersister persister = mock(EChartRevisionPersister.class);
    private final EncounterWindowDao windows = mock(EncounterWindowDao.class);
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private EctSessionBean other;

    @BeforeEach
    void openAThenEnterB() {
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(login), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        MockHttpSession session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        session.setAttribute("userfirstname", "Casey");
        session.setAttribute("userlastname", "Clinician");
        request = new MockHttpServletRequest();
        request.setSession(session);
        response = new MockHttpServletResponse();

        // A's legacy editor was opened first; B was entered afterwards, so the login-wide bean is B's.
        EctSessionBean aEntry = entry(A, "111111", "777");
        EncounterPatientContext.rememberEncounter(request, aEntry);
        other = entry(B, "222222", "888");
        other.eChartId = "5002";
        other.encounter = "B's encounter";
        other.status = "t";
        session.setAttribute("EctSessionBean", other);
        session.setAttribute("eChartID", "5002");
        request.removeAttribute("EctSessionBean");

        request.setMethod("POST");
        request.setParameter("demographicNo", A);
        request.setParameter("eChartId", "5001");
        request.setParameter("appointment_no", "777");
        request.setParameter("btnPressed", "Sign,Save and Exit");
        request.setParameter("shTextarea", "A social");
        request.setParameter("fhTextarea", "A family");
        request.setParameter("mhTextarea", "A medical");
        request.setParameter("ocTextarea", "A concerns");
        request.setParameter("reTextarea", "A reminders");
        request.setParameter("enTextarea", "A's encounter");
        request.setParameter("subject", "A reason");
        request.setParameter("rowOneSize", "60");
        request.setParameter("rowTwoSize", "60");
        request.setParameter("rowThreeSize", "378");
        request.setParameter("presBoxSize", "30");
    }

    @Test
    @DisplayName("should reject GET before any write")
    void shouldRejectGet_beforeAnyWrite() throws Exception {
        request.setMethod("GET");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertRefusedWithoutWrites();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"missing", "malformed", "zero", "conflicting aliases", "conflicting repeats"})
    @DisplayName("should reject a missing, invalid or conflicting patient before any write")
    void shouldRejectPatient_whenMissingInvalidOrConflicting(String variant) throws Exception {
        request.removeParameter("demographicNo");
        switch (variant) {
            case "malformed" -> request.setParameter("demographicNo", "A");
            case "zero" -> request.setParameter("demographicNo", "0");
            case "conflicting aliases" -> {
                request.setParameter("demographicNo", A);
                request.setParameter("demographic_no", B);
            }
            case "conflicting repeats" -> request.setParameter("demographicNo", A, B);
            default -> {
                // "missing": the request names no patient at all.
            }
        }

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should refuse a patient the user may not write before any write")
    void shouldRefuse_whenPatientWriteIsDenied() {
        when(security.hasPrivilege(login, "_demographic", "w", A)).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should refuse a patient whose record is restricted from the user before any write")
    void shouldRefuse_whenPatientRecordIsRestricted() {
        when(security.isAllowedAccessToPatientRecord(login, Integer.parseInt(A))).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should refuse a chart write the editor would not offer the user before any write")
    void shouldRefuseSave_whenChartWriteIsDenied() {
        // The editor shows Save and the Sign buttons only with _eChart write.
        when(security.hasPrivilege(login, "_eChart", "w", A)).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");

        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should refuse Verify and Sign without the verify privilege before any write")
    void shouldRefuseVerify_whenVerifyPrivilegeIsDenied() {
        request.setParameter("btnPressed", "Verify and Sign");
        when(security.hasPrivilege(login, "_eChart.verifyButton", "w", A)).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart.verifyButton)");

        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should still let Exit store the layout without chart write")
    void shouldExit_whenChartWriteIsDenied() throws Exception {
        request.setParameter("btnPressed", "Exit");
        when(security.hasPrivilege(login, "_eChart", "w", A)).thenReturn(false);

        assertThat(execute()).isEqualTo("close");

        verifyNoInteractions(persister);
        verify(windows).remove("111111");
    }

    @ParameterizedTest(name = "btnPressed={0}")
    @ValueSource(strings = {"", "Delete", "Sign"})
    @DisplayName("should reject an unknown button before any write")
    void shouldRejectButton_whenUnknown(String button) throws Exception {
        request.setParameter("btnPressed", button);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should reject a request without a button before any write")
    void shouldRejectButton_whenMissing() throws Exception {
        request.removeParameter("btnPressed");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertRefusedWithoutWrites();
    }

    @ParameterizedTest(name = "{0}={1}")
    @org.junit.jupiter.params.provider.CsvSource({"eChartId,latest", "eChartId,-4", "appointment_no,7e2", "appointment_no,99999999999"})
    @DisplayName("should reject a malformed revision or appointment before any write")
    void shouldRejectIdentifier_whenMalformed(String parameter, String value) throws Exception {
        request.setParameter(parameter, value);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertRefusedWithoutWrites();
    }

    @Test
    @DisplayName("should save A's text into A's chart from A's revision and appointment after B was entered")
    void shouldSaveSubmittingPatient_whenAnotherChartWasEnteredLast() throws Exception {
        EChart saved = savedRevision(A, 5003);
        when(persister.save(eq(Integer.parseInt(A)), eq(5001), eq(777), eq(AppointmentChange.SIGN), any(), eq("111111")))
                .thenReturn(new Result(Outcome.SAVED, saved, true, 777, null));

        assertThat(execute()).isEqualTo("success");

        ArgumentCaptor<EChart> row = ArgumentCaptor.forClass(EChart.class);
        verify(persister).save(eq(Integer.parseInt(A)), eq(5001), eq(777), eq(AppointmentChange.SIGN), row.capture(), eq("111111"));
        assertThat(row.getValue().getProviderNo()).isEqualTo("111111");
        assertThat(row.getValue().getSocialHistory()).isEqualTo("A social");
        assertThat(row.getValue().getFamilyHistory()).isEqualTo("A family");
        assertThat(row.getValue().getMedicalHistory()).isEqualTo("A medical");
        assertThat(row.getValue().getOngoingConcerns()).isEqualTo("A concerns");
        assertThat(row.getValue().getReminders()).isEqualTo("A reminders");
        assertThat(row.getValue().getSubject()).isEqualTo("A reason");
        // The signature names the user recorded for A's own entry, never B's.
        assertThat(row.getValue().getEncounter()).startsWith("A's encounter\n[").endsWith("A user]");
        assertThat(request.getAttribute("refreshOpener")).isEqualTo(Boolean.TRUE);
        ArgumentCaptor<EncounterWindow> layout = ArgumentCaptor.forClass(EncounterWindow.class);
        verify(windows).persist(layout.capture());
        assertThat(layout.getValue().getProviderNo()).isEqualTo("111111");
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should not ask the schedule to refresh when no visit status changed")
    void shouldNotRefreshOpener_whenNoAppointmentWasMarked() throws Exception {
        request.setParameter("btnPressed", "Verify and Sign");
        when(persister.save(eq(Integer.parseInt(A)), eq(5001), eq(777), eq(AppointmentChange.VERIFY), any(), eq("111111")))
                .thenReturn(new Result(Outcome.SAVED, savedRevision(A, 5003), false, null, null));

        assertThat(execute()).isEqualTo("success");

        assertThat(request.getAttribute("refreshOpener")).isEqualTo(Boolean.FALSE);
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should treat an empty revision as an editor opened on a chart with no revision")
    void shouldPassNoBase_whenRevisionIsEmpty() throws Exception {
        request.setParameter("eChartId", "");
        request.setParameter("appointment_no", "null");
        when(persister.save(eq(Integer.parseInt(A)), isNull(), isNull(), eq(AppointmentChange.SIGN), any(), eq("111111")))
                .thenReturn(new Result(Outcome.SAVED, savedRevision(A, 5003), false, null, null));

        assertThat(execute()).isEqualTo("success");

        verify(persister).save(eq(Integer.parseInt(A)), isNull(), isNull(), eq(AppointmentChange.SIGN), any(), eq("111111"));
    }

    @Test
    @DisplayName("should show the unsaved text and answer 409 when the revision is stale")
    void shouldShowConflictPage_whenRevisionIsStale() throws Exception {
        when(persister.save(anyInt(), any(), any(), any(), any(), anyString()))
                .thenReturn(new Result(Outcome.STALE_REVISION, null, false, null, null));

        assertThat(execute()).isEqualTo("concurrencyError");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat((String) request.getAttribute("unsavedEncounter")).startsWith("A's encounter");
        verifyNoInteractions(windows);
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should treat an editor without a revision field as stale, never as the latest")
    void shouldShowConflictPage_whenRevisionFieldIsAbsent() throws Exception {
        request.removeParameter("eChartId");

        assertThat(execute()).isEqualTo("concurrencyError");

        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(persister, windows);
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should show the unsaved text when the appointment now belongs to another patient")
    void shouldShowConflictPage_whenAppointmentBelongsToAnotherPatient() throws Exception {
        when(persister.save(anyInt(), any(), any(), any(), any(), anyString()))
                .thenReturn(new Result(Outcome.FOREIGN_APPOINTMENT, null, false, null, null));

        assertThat(execute()).isEqualTo("concurrencyError");

        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(windows);
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should reject another patient's revision as a bad request")
    void shouldReject_whenRevisionBelongsToAnotherPatient() throws Exception {
        when(persister.save(anyInt(), any(), any(), any(), any(), anyString()))
                .thenReturn(new Result(Outcome.FOREIGN_REVISION, null, false, null, null));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(windows);
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should answer 404 when the patient no longer exists")
    void shouldAnswerNotFound_whenPatientIsMissing() throws Exception {
        when(persister.save(anyInt(), any(), any(), any(), any(), anyString()))
                .thenReturn(new Result(Outcome.PATIENT_NOT_FOUND, null, false, null, null));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(windows);
    }

    @Test
    @DisplayName("should keep the legacy AJAX precondition answer for a stale revision")
    void shouldAnswerPreconditionFailed_whenAjaxRevisionIsStale() throws Exception {
        request.setParameter("submitMethod", "ajax");
        when(persister.save(anyInt(), any(), any(), any(), any(), anyString()))
                .thenReturn(new Result(Outcome.STALE_REVISION, null, false, null, null));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(412);
        verifyNoInteractions(windows);
    }

    @Test
    @DisplayName("should close without saving and keep the layout of A's entry provider on Exit")
    void shouldCloseWithoutSaving_whenExitIsPressed() throws Exception {
        request.setParameter("btnPressed", "Exit");
        request.removeParameter("eChartId");

        assertThat(execute()).isEqualTo("close");

        verifyNoInteractions(persister);
        verify(windows).remove("111111");
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should fall back to the logged-in provider when this login has not entered the chart")
    void shouldUseLoggedInProvider_whenPatientsEntryIsNotRemembered() throws Exception {
        EncounterPatientContext.forgetEncounter(request, A);
        when(persister.save(eq(Integer.parseInt(A)), eq(5001), eq(777), eq(AppointmentChange.SIGN), any(), eq("999998")))
                .thenReturn(new Result(Outcome.SAVED, savedRevision(A, 5003), true, 777, null));

        assertThat(execute()).isEqualTo("success");

        ArgumentCaptor<EChart> row = ArgumentCaptor.forClass(EChart.class);
        verify(persister).save(eq(Integer.parseInt(A)), eq(5001), eq(777), eq(AppointmentChange.SIGN), row.capture(), eq("999998"));
        assertThat(row.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(row.getValue().getEncounter()).endsWith("Casey Clinician]");
        assertSharedBeanUnchanged();
    }

    @Test
    @DisplayName("should keep the editor's own visit after a save when the patient was entered again from another")
    void shouldKeepSubmittedVisit_whenPatientWasEnteredAgainForAnotherVisit() {
        // A's remembered entry is the latest of any of A's windows: here one opened from visit 779.
        EctSessionBean latest = entry(A, "111111", "779");
        latest.reason = "Another visit";
        latest.appointmentDate = "2026-10-20";
        Date visitDay = new GregorianCalendar(2026, Calendar.OCTOBER, 16).getTime();

        EctSaveEncounter2Action.keepSubmittedVisit(latest,
                new Result(Outcome.SAVED, savedRevision(A, 5003), false, 777, visitDay), "A reason");

        assertThat(latest.appointmentNo).isEqualTo("777");
        assertThat(latest.appointmentDate).isEqualTo("2026-10-16");
        assertThat(latest.reason).isEqualTo("A reason");
    }

    @Test
    @DisplayName("should keep no visit, dated today, after a save from an editor that had none")
    void shouldKeepNoVisit_whenEditorHadNone() {
        // A's remembered entry is another window's visit of A, on another day.
        EctSessionBean latest = entry(A, "111111", "779");
        latest.appointmentDate = "2026-10-20";
        String before = java.time.LocalDate.now().toString();

        EctSaveEncounter2Action.keepSubmittedVisit(latest,
                new Result(Outcome.SAVED, savedRevision(A, 5003), false, null, null), "");

        assertThat(latest.appointmentNo).isNull();
        // Today, as a chart opened without an appointment is dated: never the other visit's day,
        // which Sign, Save and Bill would put on this bill.
        assertThat(latest.appointmentDate).isIn(before, java.time.LocalDate.now().toString());
        assertThat(latest.reason).isEmpty();
    }

    /**
     * Sign, Save and Bill hands the bill form the save's own appointment, or "0" for none: the
     * form's physician and form switches send it back to the bill setup, which takes digits only,
     * so a missing appointment would have refused the switch with 400.
     */
    @Test
    @DisplayName("should hand the bill form the save's appointment, or 0 for none")
    void shouldHandTheBillDigits_whenTheSaveHasNoAppointment() {
        assertThat(EctSaveEncounter2Action.billAppointmentNo(
                new Result(Outcome.SAVED, savedRevision(A, 5003), false, null, null))).isEqualTo("0");
        assertThat(EctSaveEncounter2Action.billAppointmentNo(
                new Result(Outcome.SAVED, savedRevision(A, 5003), true, 777, null))).isEqualTo("777");
    }

    private String execute() throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class);
             var _ = mockStatic(LogAction.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new EctSaveEncounter2Action(security, persister, windows).execute();
        }
    }

    private void assertRefusedWithoutWrites() {
        verifyNoInteractions(persister, windows);
        assertSharedBeanUnchanged();
    }

    private void assertSharedBeanUnchanged() {
        assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(other);
        assertThat(other.demographicNo).isEqualTo(B);
        assertThat(other.eChartId).isEqualTo("5002");
        assertThat(other.encounter).isEqualTo("B's encounter");
        assertThat(other.socialHistory).isNull();
        assertThat(other.eChartTimeStamp).isNull();
        assertThat(request.getSession().getAttribute("eChartID")).isEqualTo("5002");
    }

    private static EctSessionBean entry(String patient, String provider, String appointment) {
        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = patient;
        bean.providerNo = provider;
        bean.curProviderNo = provider;
        bean.userName = patient.equals(A) ? "A user" : "B user";
        bean.appointmentNo = appointment;
        bean.appointmentDate = "2026-10-16";
        bean.reason = patient.equals(A) ? "A reason" : "B reason";
        return bean;
    }

    private static EChart savedRevision(String patient, int id) {
        EChart chart = new EChart();
        chart.setId(id);
        chart.setDemographicNo(Integer.parseInt(patient));
        return chart;
    }
}
