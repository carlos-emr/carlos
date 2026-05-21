/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanUserPassDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("ManageTeleplan2Action method guard")
@Tag("unit")
@Tag("security")
class ManageTeleplan2ActionUnitTest extends CarlosUnitTestBase {

    private AutoCloseable mockitoCloseable;
    private MockedStatic<ServletActionContext> servletActionContextMock;

    @Mock private SecurityInfoManager securityInfoManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        request = new MockHttpServletRequest("GET", "/billing/CA/BC/ManageTeleplan");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "setUserName",
            "updateBillingCodes",
            "updateteleplanICDCodesList",
            "updateExplanatoryCodesList",
            "commitUpdateBillingCodes",
            "getSequenceNumber",
            "setSequenceNumber",
            "sendFile",
            "remit",
            "setPass",
            "changePass",
            "checkElig"
    })
    @DisplayName("should reject GET when Teleplan method dispatch requested")
    void shouldRejectGet_whenTeleplanMethodDispatchRequested(String method) throws Exception {
        request.addParameter("method", method);

        try (MockedConstruction<TeleplanUserPassDAO> teleplanUserPassDao =
                     mockConstruction(TeleplanUserPassDAO.class)) {
            String result = new ManageTeleplan2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(teleplanUserPassDao.constructed()).isEmpty();
            verifyNoInteractions(securityInfoManager);
        }
    }
}
