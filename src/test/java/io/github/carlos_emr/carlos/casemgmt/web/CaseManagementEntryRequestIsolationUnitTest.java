/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real edit action and its note creation branches with isolated patient metadata. */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEntryRequestIsolationUnitTest extends CarlosUnitTestBase {
    private MockHttpSession session;
    private CaseManagementManager manager;
    private CasemgmtNoteLockDao locks;
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
        locks = createAndRegisterMock(CasemgmtNoteLockDao.class);
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        demographics = createAndRegisterMock(DemographicManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(login, "_demographic", "r", null)).thenReturn(true);
        for (String patient : List.of("101", "202")) {
            when(security.hasPrivilege(login, "_demographic", "r", patient)).thenReturn(true);
            when(security.hasPrivilege(login, "_casemgmt.issues", "w", patient)).thenReturn(true);
            when(security.hasPrivilege(login, "_eChart", "w", patient)).thenReturn(true);
            when(security.hasPrivilege(login, "_casemgmt.notes", "w", patient)).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(login, Integer.valueOf(patient))).thenReturn(true);
        }
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        session.setAttribute("userrole", "doctor");
        session.setAttribute("case_program_id", "7");
        session.setAttribute("casemgmt_oscar_baseurl", "https://clinic.example");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        EctProgramManager defaults = mock(EctProgramManager.class);
        when(defaults.getProgramBeans("999998", null)).thenReturn(List.of(new LabelValueBean("Default", "7")));
        when(defaults.getDefaultProgramId("999998")).thenReturn(7);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean("ectProgramManager")).thenReturn(defaults);
        session.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
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
                ChartProgramContext.remember(request(patient), "7");
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
        if (method.equals("save")) {
            verify(manager).hasAccessRight("note-read-only", "access", "999998", "101", "7");
            verify(manager).hasAccessRight("note-read-only", "access", "999998", "202", "7");
            verifyNoMoreInteractions(manager);
        } else {
            verifyNoInteractions(manager);
        }
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

    @Test
    void shouldRetainEachProgramsUnsavedForm_whenTheSamePatientOpensInTwoPrograms() throws Exception {
        MockHttpServletRequest first = request("101");
        io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.remember(first, "11");
        first.setParameter("note_edit", "new");
        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(first).edit()).isEqualTo("view");
        }
        String firstKey = io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(first, "101");
        CaseManagementEntryFormBean firstForm = (CaseManagementEntryFormBean) forms.get(firstKey);
        firstForm.getCaseNote().setNote("Unsaved first-program draft");

        MockHttpServletRequest second = request("101");
        io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.remember(second, "22");
        second.setParameter("note_edit", "new");
        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(second).edit()).isEqualTo("view");
        }
        String secondKey = io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(second, "101");
        CaseManagementEntryFormBean secondForm = (CaseManagementEntryFormBean) forms.get(secondKey);
        secondForm.getCaseNote().setNote("Unsaved second-program draft");

        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(firstForm).isNotSameAs(secondForm);
        assertThat(firstForm.getCaseNote().getNote()).isEqualTo("Unsaved first-program draft");
        assertThat(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.resolve(first)).isEqualTo("11");
        assertThat(secondForm.getCaseNote().getNote()).isEqualTo("Unsaved second-program draft");
        assertThat(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.resolve(second)).isEqualTo("22");
        assertThat(forms.get(firstKey)).isSameAs(firstForm);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRestorePopulatedChartDraftsWithoutLosingTheirDataOrMixingWindowState_afterSessionSerialization(
            boolean samePatient) throws Exception {
        var linkDao = createAndRegisterMock(CaseManagementNoteLinkDAO.class);
        MockHttpServletRequest firstRequest = request("101");
        MockHttpServletRequest secondRequest = request(samePatient ? "101" : "202");
        ChartProgramContext.remember(firstRequest, "11");
        ChartProgramContext.remember(secondRequest, "22");
        String firstKey = ChartProgramContext.formKey(firstRequest, "101");
        String secondKey = ChartProgramContext.formKey(secondRequest, samePatient ? "101" : "202");
        CaseManagementEntryFormBean first = populatedSessionForm("101", "11", 501L, linkDao);
        CaseManagementEntryFormBean second = populatedSessionForm(samePatient ? "101" : "202", "22", 602L, linkDao);
        Map<String, CaseManagementEntryFormBean> windows = new LinkedHashMap<>();
        windows.put(firstKey, first);
        windows.put(secondKey, second);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(windows);
        }

        Map<?, ?> restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Map<?, ?>) input.readObject();
        }

        assertThat(restored.keySet()).isEqualTo(windows.keySet());
        CaseManagementEntryFormBean restoredFirst = (CaseManagementEntryFormBean) restored.get(firstKey);
        CaseManagementEntryFormBean restoredSecond = (CaseManagementEntryFormBean) restored.get(secondKey);
        assertRestoredSessionForm(restoredFirst, first);
        assertRestoredSessionForm(restoredSecond, second);
        assertThat(restoredFirst).isNotSameAs(restoredSecond);
        restoredFirst.getCaseNote().setNote("Changed only in the restored first window");
        restoredFirst.getCpp().setReminders("Changed first-window reminder");
        restoredFirst.getGroupNoteClientIds()[0] = "303";
        restoredFirst.getIssueCheckList().getFirst().getIssueDisplay().certain = "uncertain";
        assertThat(restoredSecond).usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes("(^|.*\\.)caseManagementNoteLinkDao$").isEqualTo(second);
        assertThat(first.getCaseNote().getNote()).contains("11").contains("\r\n").endsWith("  ");
        assertThat(first.getCpp().getReminders()).isEqualTo("Reminder 11");
        assertThat(first.getGroupNoteClientIds()).containsExactly("101", "202");
        verify(linkDao).getLastLinkByNote(501L);
        verify(linkDao).getLastLinkByNote(602L);
        verifyNoMoreInteractions(linkDao);
    }

    private CaseManagementEntryFormBean populatedSessionForm(String patient, String program, long noteId,
                                                              CaseManagementNoteLinkDAO linkDao) {
        String raw = "  FAKE unsaved " + patient + "/" + program + "\r\n<literal>& Ω  ";
        CaseManagementNote note = new CaseManagementNote();
        note.setId(noteId);
        note.setUuid("session-" + patient + "-" + program);
        note.setDemographic_no(patient);
        note.setProviderNo("999998");
        note.setProgram_no(program);
        note.setNote(raw);
        note.setHistory("Saved history " + program);
        note.setObservation_date(new Date(1_700_000_000_000L + noteId));
        note.setUpdate_date(new Date(1_700_000_001_000L + noteId));
        note.setCreate_date(new Date(1_699_999_000_000L + noteId));
        note.setRevision("2");
        note.setReporter_caisi_role("701");
        note.setReporter_program_team("73");
        note.setBilling_code("A001");
        note.setIncludeissue(false);
        note.setPosition(3);
        note.setEncounter_type("telephone");
        note.setAppointmentNo((int) noteId + 9000);
        note.setHourOfEncounterTime(2);
        note.setMinuteOfEncounterTime(17);
        note.setHourOfEncTransportationTime(null);
        note.setMinuteOfEncTransportationTime(29);
        Provider provider = new Provider("999998", "Session", "doctor", "F", "GP", "Fixture");
        note.setProvider(provider);
        note.setEditors(new ArrayList<>(List.of(provider)));
        Issue catalog = new Issue();
        catalog.setId(noteId + 100);
        catalog.setCode("SESSION" + program);
        catalog.setDescription("Existing issue " + program);
        catalog.setRole("doctor");
        CaseManagementIssue existing = new CaseManagementIssue();
        existing.setId(noteId + 200);
        existing.setDemographic_no(Integer.valueOf(patient));
        existing.setIssue_id(catalog.getId());
        existing.setIssue(catalog);
        existing.setProgram_id(Integer.valueOf(program));
        existing.setCertain(true);
        existing.setMajor(true);
        note.getIssues().add(existing);
        existing.getNotes().add(note);
        CheckBoxBean selected = new CheckBoxBean();
        selected.setIssue(existing);
        selected.setChecked("on");
        selected.setUsed(true);
        var display = selected.getIssueDisplay();
        display.codeType = "system";
        display.code = catalog.getCode();
        display.description = catalog.getDescription();
        display.location = "local";
        display.acute = "chronic";
        display.certain = "certain";
        display.major = "major";
        display.resolved = "unresolved";
        display.role = "doctor";
        display.priority = "high";
        display.sortOrderId = 3;
        display.setWriteAccess(false);
        Issue pendingCatalog = new Issue();
        pendingCatalog.setId(noteId + 300);
        pendingCatalog.setCode("PENDING" + program);
        pendingCatalog.setDescription("New issue " + program);
        CheckIssueBoxBean pending = new CheckIssueBoxBean();
        pending.setIssue(pendingCatalog);
        pending.setChecked(true);
        pending.setCommunity(true);
        CaseManagementNoteExt extension = new CaseManagementNoteExt();
        extension.setId(noteId + 400);
        extension.setNoteId(noteId);
        extension.setKeyVal(CaseManagementNoteExt.TREATMENT);
        extension.setValue("Treatment <literal> " + program);
        extension.setDateValue(new Date(1_700_000_000_000L));
        note.getExtend().add(extension);
        CaseManagementNoteLink link = new CaseManagementNoteLink(CaseManagementNoteLink.DOCUMENT, noteId + 500, noteId);
        link.setId(noteId + 600);
        link.setOtherId("Document " + program);
        when(linkDao.getLastLinkByNote(noteId)).thenReturn(link);
        assertThat(note.isDocumentNote()).isTrue(); // Populate the real nontransient link cache before passivation.
        CaseManagementCPP cpp = new CaseManagementCPP();
        cpp.setId(noteId + 700);
        cpp.setDemographic_no(patient);
        cpp.setProviderNo("999998");
        cpp.setSocialHistory("Social " + program);
        cpp.setFamilyHistory("Family " + program);
        cpp.setMedicalHistory("Medical " + program);
        cpp.setOngoingConcerns("Concern " + program);
        cpp.setReminders("Reminder " + program);
        cpp.setRiskFactors("Risk " + program);
        cpp.setOtherSupportSystems("Support " + program);
        cpp.setPrimaryPhysician("Physician " + program);
        cpp.setPrimaryCounsellor("Counsellor " + program);
        cpp.setPastMedications("Medication " + program);
        cpp.setOtherFileNumber("File " + program);
        cpp.setUpdate_date(new Date(1_700_000_000_000L));
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        form.setCaseNote(note);
        form.setCaseNote_note(raw);
        form.setCaseNote_history(note.getHistory());
        form.setCpp(cpp);
        form.setIssueCheckList(new ArrayList<>(List.of(selected)));
        form.setNewIssueCheckList(new CheckIssueBoxBean[]{pending});
        form.setNewIssueList(new ArrayList<>(List.of(pendingCatalog)));
        form.setDemoNo(patient);
        form.setDemographicNo(patient);
        form.setDemoName("FAKE patient " + patient);
        form.setProviderNo("999998");
        form.setProgramNo(program);
        form.setNoteId(Long.toString(noteId));
        form.setObservation_date("14-Oct-2026 10:00");
        form.setAppointmentNo(Integer.toString(note.getAppointmentNo()));
        form.setAppointmentDate("2026-10-14");
        form.setStart_time("10:00");
        form.setApptProvider("999998");
        form.setProviderview("999998");
        form.setBillRegion("ON");
        form.setGroupNote(true);
        form.setGroupNoteClientIds(new String[]{"101", "202"});
        form.setGroupNoteTotalAnonymous(3);
        form.setHourOfEncounterTime(2);
        form.setMinuteOfEncounterTime(17);
        form.setHourOfEncTransportationTime(null);
        form.setMinuteOfEncTransportationTime(29);
        form.setSign("persist");
        form.setIncludeIssue("on");
        form.setMethod("save");
        form.setChain("list");
        form.setShowList("all");
        form.setSearString("Issue search " + program);
        form.setDeleteId("1");
        form.setLineId("0");
        return form;
    }

    private void assertRestoredSessionForm(CaseManagementEntryFormBean restored, CaseManagementEntryFormBean original) {
        assertThat(restored).isNotSameAs(original).usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes("(^|.*\\.)caseManagementNoteLinkDao$").isEqualTo(original);
        CaseManagementNote note = restored.getCaseNote();
        CaseManagementIssue issue = restored.getIssueCheckList().getFirst().getIssue();
        assertThat(note.getIssues()).singleElement().isSameAs(issue);
        assertThat(issue.getNotes()).singleElement().isSameAs(note);
        assertThat(note.getEditors()).singleElement().isSameAs(note.getProvider());
        assertThat(restored.getNewIssueList()).singleElement().isSameAs(restored.getNewIssueCheckList()[0].getIssue());
        assertThat(note.getExtend()).singleElement().satisfies(extension -> {
            assertThat(extension.getNoteId()).isEqualTo(note.getId());
            assertThat(extension.getValue()).contains(restored.getProgramNo());
        });
        assertThat(note.isDocumentNote()).isTrue(); // A restored cache must not call the transient DAO again.
        assertThat(ReflectionTestUtils.getField(note, "caseManagementNoteLinkDao")).isNull();
        assertThat(restored.getHourOfEncTransportationTime()).isNull();
        assertThat(note.getHourOfEncTransportationTime()).isNull();
    }

    private EditedNote edit(String patient, boolean explicitNew, String encounterType) throws Exception {
        return edit(patient, explicitNew, encounterType, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldSelectTheCorrectUnsignedNoteWithoutRestoringUncapturedDrafts_whenTheEditorHasNoExplicitNote(
            boolean captured) throws Exception {
        MockHttpServletRequest request = request("101");
        String expectedProgram = captured ? "22" : "11";
        if (captured) ChartProgramContext.remember(request, "22");
        String formKey = ChartProgramContext.formKey(request, "101");
        request.setParameter("programId", "7");
        request.setParameter("providerNo", "888888");
        EctProgramManager programs = mock(EctProgramManager.class);
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of(
                new LabelValueBean("Authenticated default", "11"), new LabelValueBean("Captured program", "22")));
        when(programs.getDefaultProgramId("999998")).thenReturn(11);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean("ectProgramManager")).thenReturn(programs);
        session.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        CaseManagementNote original = new CaseManagementNote();
        original.setId(501L);
        original.setDemographic_no("101");
        original.setProgram_no(expectedProgram);
        original.setNote("Existing unsigned clinical note");
        original.setHistory("Existing clinical history");
        when(manager.getLastSaved(expectedProgram, "101", "999998")).thenReturn(original);
        when(manager.isClientInProgramDomain("999998", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "r", "101")).thenReturn(true);
        when(manager.filterNotes(login, "999998", List.of(original), expectedProgram)).thenReturn(List.of(original));

        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }

        verify(manager).getLastSaved(expectedProgram, "101", "999998");
        assertThat(ChartProgramContext.formKey(request, "101")).isEqualTo(formKey);
        CaseManagementEntryFormBean rendered = (CaseManagementEntryFormBean) forms.get(formKey);
        assertThat(rendered.getCaseNote().getId()).isEqualTo(501L);
        assertThat(rendered.getCaseNote().getProgram_no()).isEqualTo(expectedProgram);
        assertThat(request.getAttribute("noteId")).isEqualTo("501");
        if (captured) {
            verifyNoInteractions(programs);
        } else {
            assertThat(ChartProgramContext.resolve(request)).isNull();
            verify(manager).restoreTmpSave("999998", "101", null);
            verify(manager, never()).restoreTmpSave("999998", "101", "11");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void shouldPublishFinalEditorNoteIdentity_whenSelectingUnsignedFallbackOrReplacingALockedNote(
            boolean explicit, boolean locked) throws Exception {
        MockHttpServletRequest request = request("101");
        io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.remember(request, "11");
        if (explicit) request.setParameter("noteId", "501");
        CaseManagementNote original = new CaseManagementNote();
        original.setId(501L);
        original.setDemographic_no("101");
        original.setProgram_no("11");
        original.setNote("Existing unsigned clinical note");
        original.setHistory("Existing history");
        when(manager.getNote("501")).thenReturn(original);
        when(manager.getLastSaved("11", "101", "999998")).thenReturn(original);
        when(manager.isClientInProgramDomain("999998", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "r", "101")).thenReturn(true);
        when(manager.filterNotes(login, "999998", List.of(original), "11")).thenReturn(List.of(original));
        if (locked) {
            io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock otherLock =
                    new io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock();
            otherLock.setId(55L);
            otherLock.setNoteId(501L);
            otherLock.setProviderNo("888888");
            otherLock.setSessionId("another-session");
            when(locks.findByNoteDemo(101, 501L)).thenReturn(otherLock);
        }
        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }
        CaseManagementEntryFormBean rendered = (CaseManagementEntryFormBean) forms.get(
                io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(request, "101"));
        String expected = locked ? "0" : "501";
        assertThat(request.getAttribute("noteId")).isEqualTo(expected);
        assertThat(rendered.getCaseNote().getId()).isEqualTo(locked ? null : 501L);
        if (!explicit) assertThat(request.getParameter("noteId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "missing", "module", "hidden"})
    void shouldRefuseExplicitNoteBeforeInitializingItsHistory_whenItIsOutsideTheChartReadBoundary(String boundary) throws Exception {
        MockHttpServletRequest request = request("101");
        request.setParameter("noteId", "501");
        request.setParameter("forceNote", "true");
        io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.remember(request, "11");
        CaseManagementNote original = new CaseManagementNote();
        original.setId(501L);
        original.setDemographic_no("foreign".equals(boundary) ? "202" : "101");
        original.setProgram_no("22");
        original.setNote("Original clinical text");
        when(manager.getNote("501")).thenReturn("missing".equals(boundary) ? null : original);
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "r", "101")).thenReturn(!"module".equals(boundary));
        when(manager.isClientInProgramDomain("999998", "101")).thenReturn(true);
        when(manager.filterNotes(login, "999998", List.of(original), "11"))
                .thenReturn("hidden".equals(boundary) ? List.of() : List.of(original));
        CaseManagementEntry2Action entry = action(request);

        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(entry.edit()).isEqualTo("none");
        }

        assertThat(entry.response.getStatus()).isEqualTo("missing".equals(boundary) ? 404 : 403);
        assertThat(original.getHistory()).isNull();
        assertThat(original.getNote()).isEqualTo("Original clinical text");
        verify(manager, never()).saveNoteSimple(any());
        verify(manager, never()).addNewNoteLink(anyLong());
        assertThat(forms).isEmpty();
    }

    @Test
    void shouldRetainExplicitCrossProgramEditing_whenTheSourceNoteIsVisibleAndOwnedByThisPatient() throws Exception {
        MockHttpServletRequest request = request("101");
        request.setParameter("noteId", "501");
        request.setParameter("forceNote", "true");
        io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.remember(request, "11");
        CaseManagementNote original = new CaseManagementNote();
        original.setId(501L);
        original.setDemographic_no("101");
        original.setProgram_no("22");
        original.setHistory("Existing clinical history");
        original.setNote("Existing clinical text");
        when(manager.getNote("501")).thenReturn(original);
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "r", "101")).thenReturn(true);
        when(manager.isClientInProgramDomain("999998", "101")).thenReturn(true);
        when(manager.filterNotes(login, "999998", List.of(original), "11")).thenReturn(List.of(original));

        try (var props = mockStatic(CarlosProperties.class);
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }

        CaseManagementEntryFormBean form = (CaseManagementEntryFormBean) forms.get(
                io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(request, "101"));
        assertThat(form.getCaseNote().getId()).isEqualTo(501L);
        assertThat(form.getCaseNote().getProgram_no()).isEqualTo("22");
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
