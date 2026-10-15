/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

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

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        session.setAttribute("userrole", "doctor");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        global = new EctSessionBean();
        global.demographicNo = "202";
        global.appointmentNo = "9002";
        global.reason = "Other chart";
        session.setAttribute("EctSessionBean", global);
        session.setAttribute("eChartID", "202");
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
            assertThat(a.loaded).isNotSameAs(b.loaded).isNotSameAs(again.loaded).isNotSameAs(global);
            assertThat(a.loaded.demographicNo).isEqualTo("101");
            assertThat(beans.constructed()).hasSize(3);
            beans.constructed().forEach(bean -> verify(bean).setUpEncounterPage(login));
            verify(security, times(2)).hasPrivilege(login, "_eChart", "r", "101");
            verify(security).hasPrivilege(login, "_eChart", "r", "202");
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

    private void assertGlobalUnchanged() {
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo("202");
        assertThat(global.appointmentNo).isEqualTo("9002");
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
