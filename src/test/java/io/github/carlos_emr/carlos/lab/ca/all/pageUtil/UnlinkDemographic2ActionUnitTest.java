/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import io.github.carlos_emr.carlos.lab.service.Hl7PatientUnlinkService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("web")
class UnlinkDemographic2ActionUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Hl7PatientUnlinkService service;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private final String version = "a".repeat(64);

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/lab/CA/ALL/UnlinkDemographic");
        request.setParameter("labNo", "456"); request.setParameter("reason", "incorrect match");
        request.setParameter("matchVersion", version); request.setRemoteAddr("127.0.0.1");
        response = new MockHttpServletResponse();
        service = createAndRegisterMock(Hl7PatientUnlinkService.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        actor = mock(LoggedInInfo.class);
        when(security.hasPrivilege(actor, "_lab", "u", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
    }
    @AfterEach void closeMocks() { login.close(); servlet.close(); }

    @Test void shouldReturnSuccessOnlyAfterTransactionalUnlinkCompletes() throws Exception {
        when(service.unlink(456, version, "incorrect match", actor, "127.0.0.1")).thenReturn(123);
        new UnlinkDemographic2Action().execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"success\":true", "\"unlinkedDemographicNo\":123");
    }
    @Test void shouldRefuseGetWithoutMutation() throws Exception {
        request.setMethod("GET"); new UnlinkDemographic2Action().execute();
        assertThat(response.getStatus()).isEqualTo(405); verifyNoInteractions(service);
    }
    @Test void shouldRefuseDuplicateSelectorsWithoutMutation() throws Exception {
        request.setParameter("labNo", "456", "789"); new UnlinkDemographic2Action().execute();
        assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(service);
    }
    @Test void shouldReturnConflictWhenPatientMatchChanged() throws Exception {
        when(service.unlink(456, version, "incorrect match", actor, "127.0.0.1"))
                .thenThrow(new Hl7PatientUnlinkService.StaleMatch());
        new UnlinkDemographic2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).doesNotContain("\"success\":true");
    }
    @Test void shouldNotClaimSuccessOrExposeClinicalErrorsWhenTransactionFails() throws Exception {
        when(service.unlink(456, version, "incorrect match", actor, "127.0.0.1"))
                .thenThrow(new IllegalStateException("PRIVATE-SYNTHETIC-RESULT"));
        new UnlinkDemographic2Action().execute();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).doesNotContain("PRIVATE-SYNTHETIC-RESULT");
        assertThat(response.getContentAsString()).doesNotContain("\"success\":true");
    }
    @Test void shouldRequireUpdatePrivilegeBeforeMutation() {
        when(security.hasPrivilege(actor, "_lab", "u", null)).thenReturn(false);
        assertThatThrownBy(() -> new UnlinkDemographic2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(service);
    }
}
