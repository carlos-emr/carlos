/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.RxFavoriteGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("prescript")
class RxFavoriteGroups2ActionUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectMutationIntent_beforeAnyDependency(String method) throws Exception {
        var request = new MockHttpServletRequest(method, "/rx/favoriteGroups");
        request.addParameter("command", "create");
        var response = new MockHttpServletResponse();
        var manager = mock(RxFavoriteGroupManager.class);
        registerMock(RxFavoriteGroupManager.class, manager);
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            new RxFavoriteGroups2Action().execute();
            assertThat(response.getStatus()).isEqualTo(405);
            verifyNoInteractions(manager);
        }
    }

    @Test void shouldAssignOnlyAuthenticatedProvidersFavorite_andRedirectAfterCommit() throws Exception {
        var request = new MockHttpServletRequest("POST", "/rx/favoriteGroups");
        request.setContextPath("/carlos");
        request.addParameter("command", "assign");
        request.addParameter("providerNo", "VICTIM");
        request.addParameter("favoriteId", "7");
        request.addParameter("groupIds", "2", "3");
        var response = new MockHttpServletResponse();
        var manager = mock(RxFavoriteGroupManager.class);
        registerMock(RxFavoriteGroupManager.class, manager);
        var security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        var info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("997001");
        when(security.hasPrivilege(info, "_rx", "u", null)).thenReturn(true);
        var bean = mock(RxSessionBean.class);
        when(bean.getDemographicNo()).thenReturn(1001);
        try (var context = mockStatic(ServletActionContext.class);
             var session = mockStatic(LoggedInInfo.class);
             var access = mockStatic(RxRequestedPatientAccess.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            access.when(() -> RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r")).thenReturn(bean);
            new RxFavoriteGroups2Action().execute();
            verify(manager).assign("997001", 7, java.util.List.of(2, 3));
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/rx/favoriteGroups?demographicNo=1001");
        }
    }
}
