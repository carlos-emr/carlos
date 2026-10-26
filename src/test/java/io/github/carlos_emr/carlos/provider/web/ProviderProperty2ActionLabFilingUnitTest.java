/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class ProviderProperty2ActionLabFilingUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final ProviderManager2 manager = mock(ProviderManager2.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private ProviderProperty2Action action;
    @BeforeEach void fixture() {
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class)); registerMock(ProviderManager2.class, manager);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        when(info.getLoggedInProviderNo()).thenReturn("100001");
        request.setMethod("POST"); request.setParameter("value", "true");
        action = new ProviderProperty2Action();
    }
    @AfterEach void release() { sessions.close(); servlet.close(); }
    @ParameterizedTest @ValueSource(strings={"GET", "HEAD", "PUT"})
    void shouldRejectWithoutMutation_whenMethodIsNotPost(String method) {
        request.setMethod(method); action.setOfferFileForOthersPref(); action.setAllowOthersFileForYouPref();
        assertThat(response.getStatus()).isEqualTo(405); verifyNoInteractions(manager);
    }
    @ParameterizedTest @ValueSource(strings={"TRUE", "yes", "1", "", "false,true"})
    void shouldRejectInvalidBoolean_whenPreferenceValueIsMalformed(String value) {
        request.setParameter("value", value); action.setOfferFileForOthersPref(); action.setAllowOthersFileForYouPref();
        assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(manager);
    }
    @Test void shouldIgnorePostedProvider_whenUpdatingOwnConsent() {
        request.setParameter("providerNo", "100002"); action.setAllowOthersFileForYouPref();
        verify(manager).updateHl7AllowOthersFileForYou(info, "100001", "true");
    }
    @Test void shouldRejectDuplicateValue_whenPreferenceRequestIsAmbiguous() {
        request.setParameter("value", new String[]{"true", "false"}); action.setAllowOthersFileForYouPref();
        assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(manager);
    }
}
