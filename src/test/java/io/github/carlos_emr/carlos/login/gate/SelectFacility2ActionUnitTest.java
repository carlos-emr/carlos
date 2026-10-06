/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login.gate;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.login.Login2Action;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the authenticated, CSRF-protected facility-selection action.
 */
@Tag("unit")
@Tag("security")
@DisplayName("SelectFacility2Action")
class SelectFacility2ActionUnitTest extends CarlosUnitTestBase {
    private ProviderDao providerDao;
    private FacilityDao facilityDao;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContextMock;

    @BeforeEach
    void setUp() {
        providerDao = mock(ProviderDao.class);
        facilityDao = mock(FacilityDao.class);
        request = new MockHttpServletRequest("POST", "/select_facility");
        response = new MockHttpServletResponse();
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/select_facility");
        request.getSession(true).setAttribute("user", "999998");
        request.getSession(false).setAttribute(SessionConstants.LOGGED_IN_PROVIDER, new Provider());
        request.getSession(false).setAttribute(SessionConstants.LOGGED_IN_SECURITY, new Security());

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should render selector on authenticated GET")
    void shouldRenderSelector_onAuthenticatedGet() throws Exception {
        request.setMethod("GET");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should reject GET when selected facility is present")
    void shouldRejectGet_whenSelectedFacilityIsPresent() throws Exception {
        request.setMethod("GET");
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(providerDao, facilityDao);
    }

    @Test
    @DisplayName("should reject unsupported method")
    void shouldRejectUnsupportedMethod_whenNotGetHeadOrPost() throws Exception {
        request.setMethod("DELETE");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }

    @Test
    @DisplayName("should redirect unauthenticated POST to logout page")
    void shouldRedirectUnauthenticatedPost_toLogoutPage() throws Exception {
        request.getSession(false).removeAttribute("user");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(providerDao, facilityDao);
    }

    @Test
    @DisplayName("should return to selector when selected facility is missing")
    void shouldReturnToSelector_whenSelectedFacilityIsMissing() throws Exception {
        request.addParameter("nextPage", "provider");
        request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/select_facility");
        assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION))
                .isEqualTo(Boolean.TRUE);
        verifyNoInteractions(providerDao, facilityDao);
    }

    @Test
    @DisplayName("should return to selector when selected facility is malformed")
    void shouldReturnToSelector_whenSelectedFacilityIsMalformed() throws Exception {
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "abc");
        request.addParameter("nextPage", "provider");
        request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/select_facility");
        assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION))
                .isEqualTo(Boolean.TRUE);
        verifyNoInteractions(providerDao, facilityDao);
    }

    @Test
    @DisplayName("should set current facility and return next result")
    void shouldSetCurrentFacilityAndReturnNextResult_whenSelectionIsAuthorized() throws Exception {
        Facility facility = new Facility();
        facility.setId(10);
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "provider");
        request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10, 11));
        when(facilityDao.find(10)).thenReturn(facility);

        String result = action().execute();

        assertThat(result).isEqualTo("provider");
        assertThat(request.getSession(false).getAttribute(SessionConstants.CURRENT_FACILITY))
                .isSameAs(facility);
        assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION)).isNull();
        assertThat(LoggedInInfo.getLoggedInInfoFromSession(request.getSession(false))).isNotNull();
        logActionMock.verify(() -> LogAction.addLog("999998", "log in", "login",
                "facilityId=10", request.getRemoteAddr()));
    }

    @Test
    @DisplayName("should sanitize remote address when writing login audit log")
    void shouldSanitizeRemoteAddress_whenWritingLoginAuditLog() throws Exception {
        Facility facility = new Facility();
        facility.setId(10);
        request.setRemoteAddr("127.0.0.1\r\nforged-login");
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "provider");
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10));
        when(facilityDao.find(10)).thenReturn(facility);

        String result = action().execute();

        assertThat(result).isEqualTo("provider");
        logActionMock.verify(() -> LogAction.addLog("999998", "log in", "login",
                "facilityId=10", "127.0.0.1\\r\\nforged-login"));
    }

    @Test
    @DisplayName("should default to provider when next page is blank")
    void shouldDefaultToProvider_whenNextPageIsBlank() throws Exception {
        Facility facility = new Facility();
        facility.setId(10);
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "");
        request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10, 11));
        when(facilityDao.find(10)).thenReturn(facility);

        String result = action().execute();

        assertThat(result).isEqualTo("provider");
        assertThat(request.getSession(false).getAttribute(SessionConstants.CURRENT_FACILITY))
                .isSameAs(facility);
        assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION)).isNull();
    }

    @Test
    @DisplayName("should reject unauthorized facility")
    void shouldRejectUnauthorizedFacility_whenProviderIsNotAllowed() throws Exception {
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "99");
        request.addParameter("nextPage", "provider");
        session.setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10, 11));

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        assertThat(session.isInvalid()).isTrue();
        verify(facilityDao, never()).find(99);
    }

    @Test
    @DisplayName("should reject missing facility record")
    void shouldRejectMissingFacilityRecord_whenAuthorizedFacilityNoLongerExists() throws Exception {
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "provider");
        session.setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10, 11));
        when(facilityDao.find(10)).thenReturn(null);

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("should return to selector before mutation when next page is invalid")
    void shouldReturnToSelectorBeforeMutation_whenNextPageIsInvalid() throws Exception {
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "https://evil.example");
        request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/select_facility");
        assertThat(request.getSession(false).getAttribute(SessionConstants.CURRENT_FACILITY)).isNull();
        assertThat(request.getSession(false).getAttribute(SessionConstants.PENDING_FACILITY_SELECTION))
                .isEqualTo(Boolean.TRUE);
        verifyNoInteractions(providerDao, facilityDao);
    }

    @Test
    @DisplayName("should sanitize nextPage when rejecting before facility mutation")
    void shouldSanitizeNextPage_whenRejectingBeforeFacilityMutation() throws Exception {
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "provider\r\nforged-next");
        request.getSession(false).setAttribute(SessionConstants.PENDING_FACILITY_SELECTION, Boolean.TRUE);

        try (LogCapture capture = LogCapture.forLogger(SelectFacility2Action.class)) {
            String result = action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            String logged = capture.messages().stream()
                    .filter(message -> message.contains("nextPage before facility mutation"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("provider\\r\\nforged-next");
        }
    }

    private SelectFacility2Action action() {
        return new SelectFacility2Action(providerDao, facilityDao);
    }
}
