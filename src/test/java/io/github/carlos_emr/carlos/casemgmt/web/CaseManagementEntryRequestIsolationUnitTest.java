/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real edit action and its note creation branches with isolated patient metadata. */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEntryRequestIsolationUnitTest extends CarlosUnitTestBase {
    private MockHttpSession session;
    private CaseManagementManager manager;
    private OscarAppointmentDao appointments;
    private DemographicManager demographics;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private Map<String, Object> forms;
    private CarlosProperties properties;
    private EctSessionBean global;

    @BeforeEach
    void setUp() {
        manager = createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(CasemgmtNoteLockDao.class);
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        demographics = createAndRegisterMock(DemographicManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(login, "_demographic", "r", null)).thenReturn(true);
        for (String patient : List.of("101", "202")) {
            when(security.hasPrivilege(login, "_demographic", "r", patient)).thenReturn(true);
            when(security.hasPrivilege(login, "_casemgmt.issues", "w", patient)).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(login, Integer.valueOf(patient))).thenReturn(true);
        }
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        session.setAttribute("userrole", "doctor");
        session.setAttribute("case_program_id", "7");
        session.setAttribute("casemgmt_oscar_baseurl", "https://clinic.example");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        forms = new HashMap<>();
        properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(properties.getProperty("billregion", "")).thenReturn("ON");
        when(properties.isPropertyActive("encounter.empty_new_note")).thenReturn(true);

        MockHttpServletRequest entry = request("101");
        EncounterPatientContext.rememberEncounter(entry, metadata("101", "Alice", "Adams", "9001", "telephone"));
        global = metadata("202", "Bob", "Brown", "9002", "office");
        EncounterPatientContext.rememberEncounter(entry, global);
        session.setAttribute("EctSessionBean", global);
        session.setAttribute("eChartID", "202");
        registerAppointment(9001, 101, "2026-10-15");
        registerAppointment(9002, 202, "2025-02-20");
        registerAppointment(9003, 101, "2026-10-16");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldDefaultNewNotesAndBillingToEachPatientsOwnEntryAcrossInterleavedRequests(boolean explicitNew) throws Exception {
        List<EditedNote> opened = new ArrayList<>();
        List<String> originalUrls = new ArrayList<>();
        for (String patient : new String[]{"101", "202", "101"}) {
            EditedNote edited = edit(patient, explicitNew, null);
            opened.add(edited);
            originalUrls.add((String) edited.request().getAttribute("billing_url"));
            CaseManagementNote note = edited.note();
            assertThat(note.getDemographic_no()).isEqualTo(patient);
            assertThat(note.getProviderNo()).isEqualTo("999998");
            assertThat(note.getEncounter_type()).isEqualTo(patient.equals("101") ? "telephone" : "office");
            Map<String, String> billing = billingParameters(edited.request());
            assertThat(billing).containsEntry("demographic_no", patient)
                    .containsEntry("demographic_name", patient.equals("101") ? "Adams,Alice" : "Brown,Bob")
                    .containsEntry("appointment_no", patient.equals("101") ? "9001" : "9002")
                    .containsEntry("appointment_date", patient.equals("101") ? "2026-10-15" : "2025-02-20");
            assertThat(session.getAttribute("billing_url")).isNull();
        }
        // Render A after B has opened: each originating request must still own its URL.
        for (int i = 0; i < opened.size(); i++) {
            assertThat(opened.get(i).request().getAttribute("billing_url")).isEqualTo(originalUrls.get(i));
            assertThat(billingParameters(opened.get(i).request()))
                    .containsEntry("demographic_no", i == 1 ? "202" : "101")
                    .containsEntry("demographic_name", i == 1 ? "Brown,Bob" : "Adams,Alice")
                    .containsEntry("appointment_no", i == 1 ? "9002" : "9001");
        }
        assertThat(forms).containsKeys("caseManagementEntryForm101", "caseManagementEntryForm202");
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo("202");
        assertThat(global.encType).isEqualTo("office");
        assertThat(session.getAttribute("eChartID")).isEqualTo("202");
        verifyNoInteractions(demographics);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPreferExplicitEncounterTypeForBothNewNoteBranches(boolean explicitNew) throws Exception {
        CaseManagementNote note = edit("101", explicitNew, "video").note();
        assertThat(note.getEncounter_type()).isEqualTo("video");
        assertThat(EncounterPatientContext.findEncounter(request("101"), "101").encType).isEqualTo("telephone");
        assertThat(EncounterPatientContext.findEncounter(request("202"), "202").encType).isEqualTo("office");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUseRequestedPatientDemographicsAndEmptyNoteTypeWhenNoEntryIsCached(boolean explicitNew) throws Exception {
        Demographic third = new Demographic();
        third.setFirstName("Carol");
        third.setLastName("Clark");
        when(demographics.getDemographic(login, "303")).thenReturn(third);
        EditedNote edited = edit("303", explicitNew, null);
        CaseManagementNote note = edited.note();
        assertThat(note.getDemographic_no()).isEqualTo("303");
        assertThat(note.getEncounter_type()).isEmpty();
        assertThat(billingParameters(edited.request())).containsEntry("demographic_no", "303")
                .containsEntry("demographic_name", "Clark,Carol").containsEntry("appointment_no", "0");
        assertThat(EncounterPatientContext.findEncounter(request("303"), "303")).isNull();
        assertThat(EncounterPatientContext.findEncounter(request("202"), "202").patientFirstName).isEqualTo("Bob");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "9001, NULL, 9001, 2026-10-15",
            "9002, NULL, 0, TODAY",
            "9004, NULL, 0, TODAY",
            "9001, 9003, 9003, 2026-10-16",
            "9002, 9003, 9003, 2026-10-16",
            "9001, 9002, 0, TODAY",
            "9001, 9004, 0, TODAY"
    }, nullValues = "NULL")
    void shouldValidateBillingAppointmentOwnershipAndDateWithoutMutatingCachedMetadata(
            String cachedAppointment, String requestedAppointment, String expectedAppointment, String expectedDate) throws Exception {
        EctSessionBean original = metadata("101", "Alice", "Adams", cachedAppointment, "telephone");
        original.appointmentDate = "1900-01-01";
        EncounterPatientContext.rememberEncounter(request("101"), original);
        String before = java.time.LocalDate.now().toString();
        EditedNote edited = edit("101", true, null, requestedAppointment);
        String after = java.time.LocalDate.now().toString();
        Map<String, String> billing = billingParameters(edited.request());
        assertThat(billing).containsEntry("demographic_no", "101").containsEntry("demographic_name", "Adams,Alice")
                .containsEntry("appointment_no", expectedAppointment);
        if (expectedDate.equals("TODAY")) {
            assertThat(billing.get("appointment_date")).isIn(before, after);
            assertThat(billing.get("appointment_date")).isNotEqualTo("2025-02-20").isNotEqualTo("1900-01-01");
        } else {
            assertThat(billing).containsEntry("appointment_date", expectedDate);
        }
        assertThat(edited.note().getEncounter_type()).isEqualTo("telephone");
        EctSessionBean saved = EncounterPatientContext.findEncounter(edited.request(), "101");
        assertThat(saved.appointmentNo).isEqualTo(cachedAppointment);
        assertThat(saved.appointmentDate).isEqualTo("1900-01-01");
        assertThat(saved.patientFirstName).isEqualTo("Alice");
        assertThat(original.appointmentNo).isEqualTo(cachedAppointment);
        assertThat(original.appointmentDate).isEqualTo("1900-01-01");
        EctSessionBean b = EncounterPatientContext.findEncounter(edited.request(), "202");
        assertThat(b.appointmentNo).isEqualTo("9002");
        assertThat(b.patientFirstName).isEqualTo("Bob");
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "submitChangeDiagnosis"})
    void shouldPrepareRequestOwnedBillingAfterSaveOrDiagnosisDispatchReturnsView(String method) throws Exception {
        List<MockHttpServletRequest> completedRequests = new ArrayList<>();
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            for (String patient : new String[]{"101", "202"}) {
                MockHttpServletRequest request = request(patient);
                request.setParameter("method", method);
                // These handlers populate "from" before returning the full editor view.
                request.setAttribute("from", "casemgmt");
                CaseManagementEntry2Action action = action(request);
                // Persistence and diagnosis changes have separate coverage; execute's lifecycle is real.
                if (method.equals("save")) doReturn("view").when(action).save();
                else doReturn("view").when(action).submitChangeDiagnosis();
                assertThat(request.getAttribute("billing_url")).isNull();
                assertThat(action.execute()).isEqualTo("view");
                if (method.equals("save")) verify(action).save();
                else verify(action).submitChangeDiagnosis();
                verify(action, never()).edit();
                assertThat(billingParameters(request)).containsEntry("demographic_no", patient)
                        .containsEntry("demographic_name", patient.equals("101") ? "Adams,Alice" : "Brown,Bob")
                        .containsEntry("appointment_no", patient.equals("101") ? "9001" : "9002");
                assertThat(session.getAttribute("billing_url")).isNull();
                completedRequests.add(request);
            }
        }
        assertThat(billingParameters(completedRequests.get(0))).containsEntry("demographic_no", "101")
                .containsEntry("demographic_name", "Adams,Alice").containsEntry("appointment_no", "9001");
        verifyNoInteractions(manager);
    }

    @Test
    void shouldBindANewNoteToOnlyItsOwnCachedAppointment() {
        for (String patient : new String[]{"101", "202", "101"}) {
            MockHttpServletRequest request = request(patient);
            CaseManagementNote note = new CaseManagementNote();
            note.setDemographic_no(patient);
            ReflectionTestUtils.invokeMethod(action(request), "bindNoteAppointment", note);
            assertThat(note.getAppointmentNo()).isEqualTo(patient.equals("101") ? 9001 : 9002);
        }
        verify(appointments, times(2)).find(9001);
        verify(appointments).find(9002);
    }

    @Test
    void shouldPreferAnExplicitOwnedAppointmentAndRejectAnExplicitForeignAppointment() {
        MockHttpServletRequest request = request("101");
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no("101");
        request.setParameter("appointmentNo", "9003");
        ReflectionTestUtils.invokeMethod(action(request), "bindNoteAppointment", note);
        assertThat(note.getAppointmentNo()).isEqualTo(9003);
        request.setParameter("appointmentNo", "9002");
        ReflectionTestUtils.invokeMethod(action(request), "bindNoteAppointment", note);
        assertThat(note.getAppointmentNo()).isZero();
        assertThat(EncounterPatientContext.findEncounter(request, "101").appointmentNo).isEqualTo("9001");
    }

    @Test
    void shouldKeepExistingNoteAppointmentWithoutQueryingOrUsingCachedVisit() {
        CaseManagementNote existing = new CaseManagementNote();
        existing.setId(501L);
        existing.setDemographic_no("101");
        existing.setAppointmentNo(42);
        MockHttpServletRequest request = request("101");
        request.setParameter("appointmentNo", "9003");
        ReflectionTestUtils.invokeMethod(action(request), "bindNoteAppointment", existing);
        assertThat(existing.getAppointmentNo()).isEqualTo(42);
        verifyNoInteractions(appointments);
    }

    @Test
    void shouldReleaseNothing_whenTheCleanupNamesNoPatient() {
        session.setAttribute(ChartSessionKeys.lastSavedNoteText("101"), "101's last eChart text");
        session.setAttribute(ChartSessionKeys.draftRestoring("101"), "true");

        // Missing, blank, zero, not a number, too long: no chart to release, and no 500.
        for (String patient : new String[]{null, "", "0", "abc", "99999999999"}) {
            assertThat(action(request(patient)).cleanup()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        }

        assertThat(session.getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("101's last eChart text");
        assertThat(session.getAttribute(ChartSessionKeys.draftRestoring("101"))).isEqualTo("true");
    }

    @Test
    void shouldCleanupOnlyTheClosingPatientsFormAndCachedMetadata_whenOnePatientIsNamed() {
        Object formA = new Object();
        Object formB = new Object();
        session.setAttribute("caseManagementEntryForm101", formA);
        session.setAttribute("caseManagementEntryForm202", formB);
        for (String patient : new String[]{"101", "202"}) {
            session.setAttribute(ChartSessionKeys.lastSavedNoteText(patient), patient + "'s last eChart text");
            session.setAttribute(ChartSessionKeys.draftRestoring(patient), "true");
        }
        MockHttpServletRequest request = request("101");
        assertThat(action(request).cleanup()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(session.getAttribute("caseManagementEntryForm101")).isNull();
        assertThat(session.getAttribute("caseManagementEntryForm202")).isSameAs(formB);
        // The closing chart's saved-note text and restore signal leave the session with it; the
        // other open chart keeps its own.
        assertThat(session.getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isNull();
        assertThat(session.getAttribute(ChartSessionKeys.draftRestoring("101"))).isNull();
        assertThat(session.getAttribute(ChartSessionKeys.lastSavedNoteText("202"))).isEqualTo("202's last eChart text");
        assertThat(session.getAttribute(ChartSessionKeys.draftRestoring("202"))).isEqualTo("true");
        assertThat(EncounterPatientContext.findEncounter(request, "101")).isNull();
        EctSessionBean remaining = EncounterPatientContext.findEncounter(request, "202");
        assertThat(remaining.patientFirstName).isEqualTo("Bob");
        assertThat(remaining.encType).isEqualTo("office");
        assertThat(remaining.appointmentNo).isEqualTo("9002");
    }

    @Test
    void shouldReleaseNeitherPatientsSession_whenCleanupNamesConflictingPatients() {
        Object formA = new Object();
        Object formB = new Object();
        session.setAttribute("caseManagementEntryForm101", formA);
        session.setAttribute("caseManagementEntryForm202", formB);
        MockHttpServletRequest request = request("101");
        request.setParameter("demographic_no", "202");

        assertThat(action(request).cleanup()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(session.getAttribute("caseManagementEntryForm101")).isSameAs(formA);
        assertThat(session.getAttribute("caseManagementEntryForm202")).isSameAs(formB);
        assertThat(EncounterPatientContext.findEncounter(request, "101")).isNotNull();
        assertThat(EncounterPatientContext.findEncounter(request, "202")).isNotNull();
    }

    @Test
    void shouldForgetOnlyItsOwnPatientsEChartText_whenItsChartIsEdited() throws Exception {
        session.setAttribute(ChartSessionKeys.lastSavedNoteText("101"), "A's last eChart text");
        session.setAttribute(ChartSessionKeys.lastSavedNoteText("202"), "B's last eChart text");

        edit("202", true, null);

        assertThat(session.getAttribute(ChartSessionKeys.lastSavedNoteText("202"))).isNull();
        // A's next save still removes A's own previous text from A's eChart record (issue #4673).
        assertThat(session.getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("A's last eChart text");
    }

    @Test
    void shouldHandFiltersOnlyToItsOwnPatientsNoteList_whenItsEditorForwards() throws Exception {
        MockHttpServletRequest request = request("202");
        request.setParameter("note_edit", "new");
        request.setParameter("note_sort", "observation_date_desc");
        request.setParameter("filter_roles", "7");
        request.setParameter("filter_providers", "200");
        request.setParameter("issues", "73");
        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }

        assertThat(session.getAttribute(ChartSessionKeys.noteFilterHandoff("note_sort", "202"))).isEqualTo("observation_date_desc");
        assertThat((String[]) session.getAttribute(ChartSessionKeys.noteFilterHandoff("filter_roles", "202"))).containsExactly("7");
        assertThat((String[]) session.getAttribute(ChartSessionKeys.noteFilterHandoff("filter_provider", "202"))).containsExactly("200");
        assertThat((String[]) session.getAttribute(ChartSessionKeys.noteFilterHandoff("issues", "202"))).containsExactly("73");
        for (String name : new String[]{"note_sort", "filter_roles", "filter_provider", "issues"}) {
            assertThat(session.getAttribute(name)).as("login-wide %s", name).isNull();
            assertThat(session.getAttribute(ChartSessionKeys.noteFilterHandoff(name, "101"))).as("A's %s", name).isNull();
        }
    }

    @Test
    void shouldSignalTheRestoreOnlyForItsOwnPatient_whenADraftIsRestored() throws Exception {
        CaseManagementEntry2Action action = action(request("101"));
        doReturn("view").when(action).edit();

        assertThat(action.restore()).isEqualTo("view");

        assertThat(session.getAttribute(ChartSessionKeys.draftRestoring("101"))).isEqualTo("true");
        assertThat(session.getAttribute(ChartSessionKeys.draftRestoring("202"))).isNull();
        assertThat(session.getAttribute("restoring")).isNull();
    }

    private record EditedNote(CaseManagementNote note, MockHttpServletRequest request) { }

    private EditedNote edit(String patient, boolean explicitNew, String encounterType) throws Exception {
        return edit(patient, explicitNew, encounterType, null);
    }

    private EditedNote edit(String patient, boolean explicitNew, String encounterType, String appointment) throws Exception {
        MockHttpServletRequest request = request(patient);
        if (explicitNew) request.setParameter("note_edit", "new");
        if (encounterType != null) request.setParameter("encType", encounterType);
        if (appointment != null) request.setParameter("appointmentNo", appointment);
        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }
        assertThat(session.getAttribute("billing_url")).isNull();
        return new EditedNote(((CaseManagementEntryFormBean) forms.get("caseManagementEntryForm" + patient)).getCaseNote(), request);
    }

    private CaseManagementEntry2Action action(MockHttpServletRequest request) {
        CaseManagementEntry2Action action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        action.request = request;
        action.response = new MockHttpServletResponse();
        action.caseManagementMgr = manager;
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        action.withSession(forms);
        doReturn("Patient " + request.getParameter("demographicNo")).when(action).getDemoName(anyString());
        doReturn("40").when(action).getDemoAge(anyString());
        doReturn("1986-01-01").when(action).getDemoDOB(anyString());
        return action;
    }

    private MockHttpServletRequest request(String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setContextPath("/carlos");
        request.setSession(session);
        request.setParameter("demographicNo", patient);
        request.setParameter("from", "casemgmt");
        return request;
    }

    private static EctSessionBean metadata(String patient, String first, String last, String appointment, String type) {
        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = patient;
        bean.patientFirstName = first;
        bean.patientLastName = last;
        bean.providerNo = "999998";
        bean.curProviderNo = "999998";
        bean.appointmentNo = appointment;
        bean.appointmentDate = "2026-10-15";
        bean.encType = type;
        return bean;
    }

    private void registerAppointment(int id, int patient, String date) {
        Appointment appointment = new Appointment();
        appointment.setDemographicNo(patient);
        appointment.setAppointmentDate(java.sql.Date.valueOf(date));
        when(appointments.find(id)).thenReturn(appointment);
        when(appointments.find(Integer.valueOf(id))).thenReturn(appointment);
    }

    private Map<String, String> billingParameters(MockHttpServletRequest request) {
        Map<String, String> values = new HashMap<>();
        String url = (String) request.getAttribute("billing_url");
        assertThat(url).isNotNull();
        String query = URI.create(url).getRawQuery();
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }
}
