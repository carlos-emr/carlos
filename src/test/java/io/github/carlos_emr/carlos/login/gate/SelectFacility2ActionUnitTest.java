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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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
    @DisplayName("should reject unsupported method")
    void shouldRejectUnsupportedMethod_whenNotGetHeadOrPost() throws Exception {
        request.setMethod("DELETE");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }

    @Test
    @DisplayName("should set current facility and return next result")
    void shouldSetCurrentFacilityAndReturnNextResult_whenSelectionIsAuthorized() throws Exception {
        Facility facility = new Facility();
        facility.setId(10);
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "10");
        request.addParameter("nextPage", "provider");
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10, 11));
        when(facilityDao.find(10)).thenReturn(facility);

        String result = action().execute();

        assertThat(result).isEqualTo("provider");
        assertThat(request.getSession(false).getAttribute(SessionConstants.CURRENT_FACILITY))
                .isSameAs(facility);
        assertThat(LoggedInInfo.getLoggedInInfoFromSession(request.getSession(false))).isNotNull();
        logActionMock.verify(() -> LogAction.addLog("999998", "log in", "login",
                "facilityId=10", request.getRemoteAddr()));
    }

    @Test
    @DisplayName("should reject unauthorized facility")
    void shouldRejectUnauthorizedFacility_whenProviderIsNotAllowed() throws Exception {
        request.addParameter(Login2Action.SELECTED_FACILITY_ID, "99");
        request.addParameter("nextPage", "provider");
        when(providerDao.getFacilityIds("999998")).thenReturn(List.of(10, 11));

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/loginfailed");
        verify(facilityDao, org.mockito.Mockito.never()).find(99);
    }

    private SelectFacility2Action action() {
        return new SelectFacility2Action(providerDao, facilityDao);
    }
}
