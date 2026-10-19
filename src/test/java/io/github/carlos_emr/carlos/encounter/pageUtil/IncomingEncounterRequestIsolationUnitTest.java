/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterWindowDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real root-entry action, with only its clinical loaders isolated from external/database state. */
@Tag("unit")
@Tag("encounter")
@DisplayName("Root encounter requests retain their own patient and appointment")
public class IncomingEncounterRequestIsolationUnitTest extends CarlosUnitTestBase {
    private LoggedInInfo login;
    private MockHttpSession session;
    private CarlosProperties properties;
    private SecurityInfoManager security;
    private CaseManagementManager manager;
    private CaseManagementNoteDAO notes;
    private EChartDao charts;
    private EctSessionBean stale;

    @BeforeEach
    void prepareLoginAndClinicalLoaders() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        manager = createAndRegisterMock(CaseManagementManager.class);
        notes = createAndRegisterMock(CaseManagementNoteDAO.class);
        charts = createAndRegisterMock(EChartDao.class);
        createAndRegisterMock(EncounterWindowDao.class);
        createAndRegisterMock(DrugDao.class);
        login = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        login.setLoggedInProvider(provider);
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        session.setAttribute("userfirstname", "Synthetic");
        session.setAttribute("userlastname", "Provider");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        when(security.hasPrivilege(eq(login), eq("_demographic"), eq("r"), nullable(String.class)))
                .thenReturn(true);
        properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(properties.getProperty("program_domain.show_echart", "false")).thenReturn("true");
        stale = new EctSessionBean();
        stale.demographicNo = "202";
        stale.appointmentNo = "9002";
        stale.patientFirstName = "Other chart";
        session.setAttribute("EctSessionBean", stale);
        session.setAttribute("cur_appointment_no", "9002");
        session.setAttribute("eChartID", "old-global-chart");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetainAsExplicitVisitAndRequestBean_whenBOpensLater(boolean demographicSearch) throws Exception {
        try (var props = mockStatic(CarlosProperties.class);
             var patients = mockStatic(RxPatientData.class);
             var beans = clinicalBeans()) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            MockHttpServletRequest aRequest = request("101", "9001");
            assertThat(execute(aRequest, new MockHttpServletResponse())).isEqualTo("success2");
            EctSessionBean a = (EctSessionBean) aRequest.getAttribute("EctSessionBean");
            assertEncounter(a, "101", "9001");
            EctSessionBean aSnapshot = EncounterPatientContext.findEncounter(aRequest, "101");
            assertEncounter(aSnapshot, "101", "9001");
            verify(a).setUpEncounterPage(login);

            MockHttpServletRequest bRequest = request("202", "9002");
            if (demographicSearch) bRequest.setParameter("demographicSearch", "true");
            assertThat(execute(bRequest, new MockHttpServletResponse())).isEqualTo("success2");
            EctSessionBean b = (EctSessionBean) bRequest.getAttribute("EctSessionBean");
            assertEncounter(b, "202", demographicSearch ? "0" : "9002");
            assertThat(b).isNotSameAs(a).isNotSameAs(stale);
            assertThat(aRequest.getAttribute("EctSessionBean")).isSameAs(a);
            assertEncounter(a, "101", "9001");
            assertEncounter(aSnapshot, "101", "9001");
            assertEncounter(EncounterPatientContext.findEncounter(bRequest, "101"), "101", "9001");
            assertEncounter(EncounterPatientContext.findEncounter(bRequest, "202"), "202", demographicSearch ? "0" : "9002");
            assertThat(aRequest.getAttribute("encounterText")).as("A's already prepared response remains A's")
                    .asString().contains("Synthetic chart 101").doesNotContain("Synthetic chart 202");
            assertThat(stale.demographicNo).isEqualTo("202");
            assertThat(stale.appointmentNo).isEqualTo("9002");
            assertThat(stale.patientFirstName).isEqualTo("Other chart");
            assertThat(session.getAttribute("cur_appointment_no")).isEqualTo("9002");
            verify(charts).findByDemoIdAndSubject(101, "SPLIT CHART");
            verify(charts).findByDemoIdAndSubject(202, "SPLIT CHART");
            patients.verify(() -> RxPatientData.getPatient(login, 101));
            patients.verify(() -> RxPatientData.getPatient(login, 202));
            verify(security).hasPrivilege(login, "_demographic", "r", "101");
            verify(security).hasPrivilege(login, "_demographic", "r", "202");
        }
    }

    @Test
    void shouldLeaveAppointmentAbsent_whenOnlyAnotherChartsGlobalAppointmentExists() throws Exception {
        try (var props = mockStatic(CarlosProperties.class);
             var patients = mockStatic(RxPatientData.class);
             var beans = clinicalBeans()) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            MockHttpServletRequest request = request("101", null);
            assertThat(execute(request, new MockHttpServletResponse())).isEqualTo("success2");
            assertEncounter((EctSessionBean) request.getAttribute("EctSessionBean"), "101", null);
            assertEncounter(EncounterPatientContext.findEncounter(request, "101"), "101", null);
            assertThat(session.getAttribute("cur_appointment_no")).isEqualTo("9002");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "malformed", "zero", "overflow", "conflicting aliases", "conflicting repeats"})
    void shouldRejectInvalidIdentityBeforeLoadingOrPublishingPatientState_whenRequestIsAmbiguous(String scenario) throws Exception {
        MockHttpServletRequest request = request(null, "9001");
        switch (scenario) {
            case "empty" -> request.setParameter("demographicNo", "");
            case "malformed" -> request.setParameter("demographicNo", "101x");
            case "zero" -> request.setParameter("demographicNo", "0");
            case "overflow" -> request.setParameter("demographicNo", "2147483648");
            case "conflicting aliases" -> {
                request.setParameter("demographicNo", "101");
                request.setParameter("demographic_no", "202");
            }
            case "conflicting repeats" -> request.setParameter("demographicNo", "101", "202");
            default -> { }
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var props = mockStatic(CarlosProperties.class);
             var patients = mockStatic(RxPatientData.class);
             var beans = clinicalBeans()) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(execute(request, response)).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(beans.constructed()).isEmpty();
            assertThat(request.getAttribute("EctSessionBean")).isNull();
            assertThat(session.getAttribute("EctSessionBean")).isSameAs(stale);
            assertThat(session.getAttribute("eChartID")).isEqualTo("old-global-chart");
            verify(security).hasPrivilege(login, "_demographic", "r", null);
            verifyNoMoreInteractions(security);
            verifyNoInteractions(manager, notes, charts);
        }
    }

    @Test
    void shouldCanonicalizeEquivalentAliases_whenOpeningTheRootEncounter() throws Exception {
        try (var props = mockStatic(CarlosProperties.class);
             var patients = mockStatic(RxPatientData.class);
             var beans = clinicalBeans()) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            MockHttpServletRequest request = request("00101", "9001");
            request.setParameter("demographic_no", "101", "000101");
            assertThat(execute(request, new MockHttpServletResponse())).isEqualTo("success2");
            assertEncounter((EctSessionBean) request.getAttribute("EctSessionBean"), "101", "9001");
            assertEncounter(EncounterPatientContext.findEncounter(request, "101"), "101", "9001");
            verify(security).hasPrivilege(login, "_demographic", "r", "101");
        }
    }

    @Test
    void shouldDenyPatientSpecificAccess_whenOnlyTheOtherChartIsAuthorized() {
        when(security.hasPrivilege(login, "_demographic", "r", "101")).thenReturn(false);
        MockHttpServletRequest request = request("101", "9001");
        try (var beans = clinicalBeans()) {
            assertThatThrownBy(() -> execute(request, new MockHttpServletResponse()))
                    .isInstanceOf(SecurityException.class);
            verify(security).hasPrivilege(login, "_demographic", "r", "101");
            verifyNoInteractions(manager, notes, charts);
            assertThat(beans.constructed()).isEmpty();
            assertThat(request.getAttribute("EctSessionBean")).isNull();
            assertThat(session.getAttribute("EctSessionBean")).isSameAs(stale);
        }
    }

    @Test
    void shouldRefuseToCopyANote_whenTheAttachIsNotPosted() throws Exception {
        MockHttpServletRequest request = attachRequest("GET", "101", "5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(execute(request, response)).isEqualTo(ActionSupport.NONE);
        }
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(notes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"another client than the chart", "no note", "malformed note"})
    void shouldRefuseToCopyANote_whenTheAttachIsMalformed(String scenario) throws Exception {
        MockHttpServletRequest request = switch (scenario) {
            case "another client than the chart" -> attachRequest("POST", "202", "5");
            case "no note" -> attachRequest("POST", "101", null);
            default -> attachRequest("POST", "101", "5x");
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(execute(request, response)).isEqualTo(ActionSupport.NONE);
        }
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(notes);
    }

    @Test
    void shouldAnswerNotFound_whenTheNoteToAttachDoesNotExist() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(execute(attachRequest("POST", "101", "5"), response)).isEqualTo(ActionSupport.NONE);
        }
        assertThat(response.getStatus()).isEqualTo(404);
        verify(notes, never()).saveNote(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"note read", "note's record", "client note write", "client's record"})
    void shouldRefuseToCopyANote_whenEitherPatientIsNotTheUsers(String denied) throws Exception {
        when(notes.getNote(5L)).thenReturn(sourceNote());
        allowAttach();
        switch (denied) {
            case "note read" -> when(security.hasPrivilege(login, "_casemgmt.notes", "r", "202")).thenReturn(false);
            case "note's record" -> when(security.isAllowedAccessToPatientRecord(login, 202)).thenReturn(false);
            case "client note write" -> when(security.hasPrivilege(login, "_casemgmt.notes", "w", "101")).thenReturn(false);
            default -> when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        }
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThatThrownBy(() -> execute(attachRequest("POST", "101", "5"), new MockHttpServletResponse()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_casemgmt.notes)");
        }
        verify(notes, never()).saveNote(any());
    }

    @Test
    void shouldCopyTheNoteAsANewNoteOfTheClient_whenPostedByAUserWhoMayReadAndWriteBoth() throws Exception {
        CaseManagementNote source = sourceNote();
        when(notes.getNote(5L)).thenReturn(source);
        allowAttach();
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(execute(attachRequest("POST", "101", "5"), response)).isEqualTo(ActionSupport.NONE);
        }
        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        CaseManagementNote copy = saved.getValue();
        assertThat(copy.getId()).isNull();
        assertThat(copy.getUuid()).as("a new note, not a revision of the source").isNull();
        assertThat(copy.getDemographic_no()).isEqualTo("101");
        assertThat(copy.getAppointmentNo()).isZero();
        assertThat(copy.getIssues()).as("no links to the other patient's issues").isEmpty();
        assertThat(copy.getNote()).isEqualTo("FAKE current text");
        assertThat(copy.getHistory()).as("not the source note's earlier text").isEqualTo("FAKE current text");
        assertThat(response.getRedirectedUrl()).isEqualTo("/encounter/IncomingEncounter?demographicNo=101&appointmentNo=0");
    }

    private void allowAttach() {
        when(security.hasPrivilege(eq(login), eq("_casemgmt.notes"), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
    }

    /** Patient 202's note, with an appointment, an issue link and earlier text. */
    private static CaseManagementNote sourceNote() {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(5L);
        note.setUuid("00000000-0000-0000-0000-000000000202");
        note.setDemographic_no("202");
        note.setAppointmentNo(9002);
        note.setNote("FAKE current text");
        note.setHistory("FAKE earlier text\nFAKE current text");
        Set<CaseManagementIssue> issues = new HashSet<>();
        issues.add(new CaseManagementIssue());
        note.setIssues(issues);
        return note;
    }

    /** The PMM attach popup's request: attach note {@code noteId} to {@code client}, opening chart 101. */
    private MockHttpServletRequest attachRequest(String method, String client, String noteId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/encounter/IncomingEncounter");
        request.setSession(session);
        request.setParameter("demographicNo", "101");
        request.setParameter("appointmentNo", "0");
        request.setParameter("PEAttach", "yes");
        request.setParameter("selectId", client);
        if (noteId != null) request.setParameter("noteId", noteId);
        return request;
    }

    private MockedConstruction<EctSessionBean> clinicalBeans() {
        return mockConstruction(EctSessionBean.class, (bean, context) -> {
            when(bean.getDemographicNo()).thenAnswer(call -> bean.demographicNo);
            doAnswer(call -> {
                bean.patientFirstName = "Synthetic " + bean.demographicNo;
                bean.patientLastName = "Patient";
                bean.yearOfBirth = "1980";
                bean.monthOfBirth = "01";
                bean.dateOfBirth = "02";
                bean.eChartId = "chart-" + bean.demographicNo;
                bean.encounter = "Synthetic chart " + bean.demographicNo;
                return null;
            }).when(bean).setUpEncounterPage(login);
        });
    }

    private MockHttpServletRequest request(String patient, String appointment) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/encounter/IncomingEncounter");
        request.setSession(session);
        if (patient != null) request.setParameter("demographicNo", patient);
        if (appointment != null) request.setParameter("appointmentNo", appointment);
        request.setParameter("curProviderNo", "999998");
        request.setParameter("encType", "telephone");
        request.setParameter("appointmentDate", "2026-10-15");
        return request;
    }

    private String execute(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new EctIncomingEncounter2Action().execute();
        }
    }

    private static void assertEncounter(EctSessionBean bean, String patient, String appointment) {
        assertThat(bean).isNotNull();
        assertThat(bean.demographicNo).isEqualTo(patient);
        assertThat(bean.appointmentNo).isEqualTo(appointment);
        assertThat(bean.patientFirstName).isEqualTo("Synthetic " + patient);
        assertThat(bean.providerNo).isEqualTo("999998");
    }
}
