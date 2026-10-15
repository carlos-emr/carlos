/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.services.security.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("encounter")
class EctDisplayRequestIsolationUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockHttpSession session;
    private EctSessionBean global;
    private DemographicManager demographics;
    private EncounterTemplateDao templates;
    private MeasurementGroupStyleDao groups;
    private EChartDao charts;
    private OscarAppointmentDao appointments;
    private MessageTblDao messages;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        demographics = createAndRegisterMock(DemographicManager.class);
        templates = createAndRegisterMock(EncounterTemplateDao.class);
        groups = createAndRegisterMock(MeasurementGroupStyleDao.class);
        charts = createAndRegisterMock(EChartDao.class);
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        messages = createAndRegisterMock(MessageTblDao.class);
        login = mock(LoggedInInfo.class);
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        when(login.getLoggedInProvider()).thenReturn(provider);
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        session.setAttribute("userrole", "doctor");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        global = new EctSessionBean();
        global.demographicNo = "202";
        global.appointmentNo = "9002";
        global.curProviderNo = "B22222";
        global.reason = "Other chart";
        session.setAttribute("EctSessionBean", global);
        session.setAttribute("eChartID", "202");
        when(appointments.find(Integer.valueOf(9001))).thenReturn(appointment(101, "A11111"));
        when(appointments.find(Integer.valueOf(9002))).thenReturn(appointment(202, "B22222"));
    }

    @Test
    void shouldKeepInterleavedPanelRequestsAndAppointmentsIndependent() throws Exception {
        when(security.hasPrivilege(eq(login), eq("_eChart"), eq("r"), anyString())).thenReturn(true);
        try (var beans = mockConstruction(EctSessionBean.class);
             var moduleSecurity = mockConstruction(SecurityManager.class,
                     (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            MockHttpServletRequest first = request("101", "9001");
            RecordingDisplay a = execute(first, new MockHttpServletResponse());
            MockHttpServletRequest second = request("202", "9002");
            RecordingDisplay b = execute(second, new MockHttpServletResponse());
            MockHttpServletRequest refresh = request("101", "9001");
            RecordingDisplay again = execute(refresh, new MockHttpServletResponse());

            assertThat(a.loaded).isSameAs(first.getAttribute("EctSessionBean"));
            assertThat(a.loaded.demographicNo).isEqualTo("101");
            assertThat(a.loaded.appointmentNo).isEqualTo("9001");
            assertThat(b.loaded.demographicNo).isEqualTo("202");
            assertThat(again.loaded.demographicNo).isEqualTo("101");
            assertThat(again.loaded.appointmentNo).isEqualTo("9001");
            assertThat(a.loaded.curProviderNo).isEqualTo("A11111");
            assertThat(b.loaded.curProviderNo).isEqualTo("B22222");
            assertThat(again.loaded.curProviderNo).isEqualTo("A11111");
            assertThat(a.loaded).isNotSameAs(b.loaded).isNotSameAs(again.loaded).isNotSameAs(global);
            assertThat(a.loaded.demographicNo).isEqualTo("101");
            assertThat(beans.constructed()).hasSize(3);
            beans.constructed().forEach(bean -> verify(bean, never()).setUpEncounterPage(any(LoggedInInfo.class)));
            verify(security, times(2)).hasPrivilege(login, "_eChart", "r", "101");
            verify(security).hasPrivilege(login, "_eChart", "r", "202");
            verify(appointments, times(2)).find(Integer.valueOf(9001));
            verify(appointments).find(Integer.valueOf(9002));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldBuildARealLightweightRequestBeanWithoutInitializingClinicalOrChartData() throws Exception {
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        MockHttpServletRequest request = request("101", "0");
        request.setParameter("encType", "telephone");
        request.setParameter("reason", "Patient A visit");
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded).isSameAs(request.getAttribute("EctSessionBean")).isNotSameAs(global);
            assertThat(action.loaded.demographicNo).isEqualTo("101");
            assertThat(action.loaded.appointmentNo).isEqualTo("0");
            assertThat(action.loaded.encType).isEqualTo("telephone");
            assertThat(action.loaded.reason).isEqualTo("Patient A visit");
            assertThat(action.loaded.providerNo).isEqualTo("999998");
            verifyNoInteractions(demographics, templates, groups, charts, appointments, messages);
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldValidateEachOwnedVisitAcrossInterleavedRefreshesDespiteTheLastOpenedChart() throws Exception {
        remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        when(security.hasPrivilege(eq(login), eq("_eChart"), eq("r"), anyString())).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            EctSessionBean first = null;
            for (String patient : new String[]{"101", "202", "101"}) {
                MockHttpServletRequest request = request(patient, patient.equals("101") ? "9001" : "9002");
                request.removeParameter("curProviderNo");
                RecordingDisplay action = execute(request, new MockHttpServletResponse());
                assertThat(action.result).isEqualTo("success");
                assertThat(action.loaded.curProviderNo).isEqualTo(patient.equals("101") ? "A11111" : "B22222");
                assertThat(action.loaded.demographicNo).isEqualTo(patient);
                if (first == null) first = action.loaded;
                else assertThat(action.loaded).isNotSameAs(first);
            }
            assertThat(first.curProviderNo).isEqualTo("A11111");
            assertSavedProvidersUnchanged();
            verify(appointments, times(2)).find(Integer.valueOf(9001));
            verify(appointments).find(Integer.valueOf(9002));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldPreferExplicitValidProviderOverCacheWhenOwnedAppointmentHasNoProvider() throws Exception {
        remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        when(appointments.find(Integer.valueOf(9001))).thenReturn(appointment(101, null));
        MockHttpServletRequest request = request("101", "9001");
        request.setParameter("curProviderNo", "C33333");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded.curProviderNo).isEqualTo("C33333");
            assertSavedProvidersUnchanged();
            verify(appointments).find(Integer.valueOf(9001));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldPreferActualOwnedAppointmentProviderOverStaleCacheAndExplicitProviderWithOnlyOneLookup() throws Exception {
        remember("101", "9001", "S55555");
        remember("202", "9002", "B22222");
        MockHttpServletRequest request = request("101", "9001");
        request.setParameter("curProviderNo", "C33333");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded.curProviderNo).isEqualTo("A11111");
            assertThat(action.loaded.demographicNo).isEqualTo("101");
            verify(appointments).find(Integer.valueOf(9001));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            assertThat(EncounterPatientContext.findEncounter(request, "101").curProviderNo).isEqualTo("S55555");
            assertThat(EncounterPatientContext.findEncounter(request, "202").curProviderNo).isEqualTo("B22222");
            assertGlobalUnchanged();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad!"})
    void shouldUseOnlyTheMatchingPatientsCachedProviderAfterValidatingAppointmentOwnership(String actualProvider) throws Exception {
        remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        when(appointments.find(Integer.valueOf(9001))).thenReturn(appointment(101, actualProvider));
        MockHttpServletRequest request = request("101", "9001");
        request.removeParameter("curProviderNo");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded.curProviderNo).isEqualTo("A11111");
            verify(appointments).find(Integer.valueOf(9001));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            assertSavedProvidersUnchanged();
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldNotReuseCachedProviderForADifferentOwnedVisitWithoutAProvider() throws Exception {
        remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        when(appointments.find(Integer.valueOf(9003))).thenReturn(appointment(101, null));
        MockHttpServletRequest request = request("101", "9003");
        request.removeParameter("curProviderNo");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded.curProviderNo).isEqualTo("999998");
            verify(appointments).find(Integer.valueOf(9003));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            assertSavedProvidersUnchanged();
            assertGlobalUnchanged();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldUseTheOwnedAppointmentProviderWhenCacheIsMissingOrNamesADifferentVisit(boolean cached) throws Exception {
        if (cached) remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        when(appointments.find(Integer.valueOf(9003))).thenReturn(appointment(101, "D44444"));
        MockHttpServletRequest request = request("101", "9003");
        request.removeParameter("curProviderNo");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded.curProviderNo).isEqualTo("D44444");
            assertThat(action.loaded.appointmentNo).isEqualTo("9003");
            verify(appointments).find(Integer.valueOf(9003));
            verifyNoMoreInteractions(appointments);
            verifyNoInteractions(demographics, templates, groups, charts, messages);
            if (cached) assertSavedProvidersUnchanged();
            else assertThat(EncounterPatientContext.findEncounter(request, "101")).isNull();
            assertGlobalUnchanged();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shouldRejectMissingOrForeignAppointmentsDespiteMatchingCacheOrExplicitProvider(boolean foreign, boolean explicit) throws Exception {
        remember("101", "9003", "A11111");
        remember("202", "9002", "B22222");
        if (foreign) when(appointments.find(Integer.valueOf(9003))).thenReturn(appointment(202, "B22222"));
        MockHttpServletRequest request = request("101", "9003");
        if (explicit) request.setParameter("curProviderNo", "C33333");
        else request.removeParameter("curProviderNo");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingDisplay action = execute(request, response);
        assertThat(action.result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(action.loaded).isNull();
        assertThat(request.getAttribute("EctSessionBean")).isNull();
        verify(appointments).find(Integer.valueOf(9003));
        verifyNoMoreInteractions(appointments);
        verifyNoInteractions(demographics, templates, groups, charts, messages);
        assertThat(EncounterPatientContext.findEncounter(request, "101").appointmentNo).isEqualTo("9003");
        assertThat(EncounterPatientContext.findEncounter(request, "101").curProviderNo).isEqualTo("A11111");
        assertThat(EncounterPatientContext.findEncounter(request, "202").curProviderNo).isEqualTo("B22222");
        assertGlobalUnchanged();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0"})
    void shouldUseLoggedInProviderWhenNoPositiveAppointmentWasRequested(String appointmentNo) throws Exception {
        remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        MockHttpServletRequest request = request("101", appointmentNo);
        request.removeParameter("curProviderNo");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        try (var moduleSecurity = mockConstruction(SecurityManager.class,
                (mock, context) -> when(mock.hasReadAccess(anyString(), anyString())).thenReturn(true))) {
            RecordingDisplay action = execute(request, new MockHttpServletResponse());
            assertThat(action.result).isEqualTo("success");
            assertThat(action.loaded.curProviderNo).isEqualTo("999998");
            verifyNoInteractions(demographics, templates, groups, charts, appointments, messages);
            assertSavedProvidersUnchanged();
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldRejectBlankAppointmentBeforeAnyAppointmentOrClinicalLookupOrPanelLoad() throws Exception {
        remember("101", "9001", "A11111");
        remember("202", "9002", "B22222");
        MockHttpServletRequest request = request("101", "");
        when(security.hasPrivilege(login, "_eChart", "r", "101")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var moduleSecurity = mockConstruction(SecurityManager.class)) {
            RecordingDisplay action = execute(request, response);
            assertThat(action.result).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(action.loaded).isNull();
            assertThat(request.getAttribute("EctSessionBean")).isNull();
            assertThat(moduleSecurity.constructed()).isEmpty();
            verifyNoInteractions(demographics, templates, groups, charts, appointments, messages);
            assertSavedProvidersUnchanged();
            assertGlobalUnchanged();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "conflictingAliases", "conflictingDuplicates", "overflow", "zero"})
    void shouldReturn400BeforeConstructingOrLoadingChartData(String invalid) throws Exception {
        MockHttpServletRequest request = request(null, "9001");
        if (invalid.equals("conflictingAliases")) {
            request.setParameter("demographicNo", "101");
            request.setParameter("demographic_no", "202");
        } else if (invalid.equals("conflictingDuplicates")) {
            request.setParameter("demographicNo", "101", "202");
        } else if (invalid.equals("overflow")) {
            request.setParameter("demographicNo", "2147483648");
        } else if (invalid.equals("zero")) {
            request.setParameter("demographicNo", "0");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var beans = mockConstruction(EctSessionBean.class)) {
            RecordingDisplay action = execute(request, response);
            assertThat(action.result).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(action.loaded).isNull();
            assertThat(beans.constructed()).isEmpty();
            verifyNoInteractions(security);
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldAuthorizeRequestedPatientBeforeConstructingOrLoadingChartData() {
        MockHttpServletRequest request = request("101", "9001");
        when(security.hasPrivilege(login, "_eChart", "r", "202")).thenReturn(true);
        try (var beans = mockConstruction(EctSessionBean.class)) {
            assertThatThrownBy(() -> execute(request, new MockHttpServletResponse()))
                    .isInstanceOf(SecurityException.class);
            verify(security).hasPrivilege(login, "_eChart", "r", "101");
            assertThat(beans.constructed()).isEmpty();
            assertGlobalUnchanged();
        }
    }

    private MockHttpServletRequest request(String patient, String appointment) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/encounter/displayMeasurements");
        request.setSession(session);
        request.setParameter("cmd", "measurements");
        request.setParameter("curProviderNo", "999998");
        request.setParameter("appointmentNo", appointment);
        if (patient != null) request.setParameter("demographicNo", patient);
        return request;
    }

    private RecordingDisplay execute(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            RecordingDisplay action = new RecordingDisplay();
            action.result = action.execute();
            return action;
        }
    }

    private void remember(String patient, String appointment, String provider) {
        EctSessionBean entry = new EctSessionBean();
        entry.demographicNo = patient;
        entry.appointmentNo = appointment;
        entry.curProviderNo = provider;
        EncounterPatientContext.rememberEncounter(request(patient, appointment), entry);
    }

    private Appointment appointment(int patient, String provider) {
        Appointment appointment = new Appointment();
        appointment.setDemographicNo(patient);
        appointment.setProviderNo(provider);
        return appointment;
    }

    private void assertSavedProvidersUnchanged() {
        MockHttpServletRequest request = request("101", "9001");
        EctSessionBean a = EncounterPatientContext.findEncounter(request, "101");
        EctSessionBean b = EncounterPatientContext.findEncounter(request, "202");
        assertThat(a.curProviderNo).isEqualTo("A11111");
        assertThat(a.appointmentNo).isEqualTo("9001");
        assertThat(b.curProviderNo).isEqualTo("B22222");
        assertThat(b.appointmentNo).isEqualTo("9002");
    }

    private void assertGlobalUnchanged() {
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo("202");
        assertThat(global.appointmentNo).isEqualTo("9002");
        assertThat(global.curProviderNo).isEqualTo("B22222");
        assertThat(global.reason).isEqualTo("Other chart");
        assertThat(session.getAttribute("eChartID")).isEqualTo("202");
    }

    private static final class RecordingDisplay extends EctDisplayAction {
        EctSessionBean loaded;
        String result;

        @Override public String getCmd() { return "measurements"; }
        @Override public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO dao) {
            loaded = bean;
            return true;
        }
    }
}
