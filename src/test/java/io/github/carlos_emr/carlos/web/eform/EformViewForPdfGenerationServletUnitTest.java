/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.web.eform;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.FacilityManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EformViewForPdfGenerationServlet unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EformViewForPdfGenerationServletUnitTest {

    @Mock
    private ProviderDao providerDao;

    @Mock
    private SecurityDao securityDao;

    @Mock
    private FacilityManager facilityManager;

    private MockedStatic<SpringUtils> springUtilsMock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        springUtilsMock = mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(ProviderDao.class)).thenReturn(providerDao);
        springUtilsMock.when(() -> SpringUtils.getBean(SecurityDao.class)).thenReturn(securityDao);
        springUtilsMock.when(() -> SpringUtils.getBean(FacilityManager.class)).thenReturn(facilityManager);
    }

    @AfterEach
    void tearDown() {
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
    }

    @Test
    @DisplayName("should establish a synthetic logged-in session for local renderer requests")
    void shouldEstablishRendererSession_whenProviderAndSecurityExist() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        Security security = new Security();
        security.setProviderNo("999998");
        Facility facility = new Facility();
        facility.setId(1);
        when(providerDao.getProvider("999998")).thenReturn(provider);
        when(securityDao.getByProviderNo("999998")).thenReturn(security);
        when(facilityManager.getDefaultFacility(any(LoggedInInfo.class))).thenReturn(facility);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/eformViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");

        boolean established = new EformViewForPdfGenerationServlet().establishRendererSession(request, "999998");

        assertThat(established).isTrue();
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession().getAttribute("user")).isEqualTo("999998");
        assertThat(request.getSession().getAttribute(SessionConstants.LOGGED_IN_PROVIDER)).isSameAs(provider);
        assertThat(request.getSession().getAttribute(SessionConstants.LOGGED_IN_SECURITY)).isSameAs(security);
        assertThat(request.getSession().getAttribute(SessionConstants.CURRENT_FACILITY)).isSameAs(facility);
        assertThat(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo()).isEqualTo("999998");
    }

    @Test
    @DisplayName("should forward local renderer requests into efmshowform_data once session is established")
    void shouldForwardToShowData_whenRendererSessionIsEstablished() throws Exception {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        Security security = new Security();
        security.setProviderNo("999998");
        when(providerDao.getProvider("999998")).thenReturn(provider);
        when(securityDao.getByProviderNo("999998")).thenReturn(security);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/eformViewForPdfGenerationServlet") {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                assertThat(path).isEqualTo("/eform/efmshowform_data");
                return dispatcher;
            }
        };
        request.setRemoteAddr("127.0.0.1");
        request.setParameter("providerId", "999998");
        request.setParameter("fdid", "250");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new EformViewForPdfGenerationServlet().doGet(request, response);

        verify(dispatcher).forward(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should not include provider input in renderer-session failure logs")
    void shouldNotIncludeProviderInputInFailureLogs_whenRendererSessionCannotBeEstablished() {
        when(providerDao.getProvider("999998\r\ninjected")).thenReturn(null);
        when(securityDao.getByProviderNo("999998\r\ninjected")).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/eformViewForPdfGenerationServlet");
        request.setRemoteAddr("127.0.0.1");

        try (LogCapture logCapture = LogCapture.forLogger(EformViewForPdfGenerationServlet.class)) {
            boolean established =
                new EformViewForPdfGenerationServlet().establishRendererSession(request, "999998\r\ninjected");

            String lastMessage = logCapture.messages().getLast();

            assertThat(established).isFalse();
            assertThat(lastMessage).contains("Renderer session initialization failed");
            assertThat(lastMessage).doesNotContain("999998");
            assertThat(lastMessage).doesNotContain("injected");
        }
    }
}
