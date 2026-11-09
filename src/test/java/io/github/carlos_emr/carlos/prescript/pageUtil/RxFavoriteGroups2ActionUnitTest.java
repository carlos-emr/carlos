/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.RxFavoriteGroupService;
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
        var manager = mock(RxFavoriteGroupService.class);
        registerMock(RxFavoriteGroupService.class, manager);
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            new RxFavoriteGroups2Action(mock(SecurityInfoManager.class), manager, transactionManager()).execute();
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
        var manager = mock(RxFavoriteGroupService.class);
        registerMock(RxFavoriteGroupService.class, manager);
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
            new RxFavoriteGroups2Action(security, manager, transactionManager()).execute();
            verify(manager).assign("997001", 7, java.util.List.of(2, 3));
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/rx/favoriteGroups?demographicNo=1001");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"create", "up", "down", "delete"})
    void shouldApplyMutationOnce_whenRenderingIsSubmittedTwice(String command) throws Exception {
        var request = new MockHttpServletRequest("POST", "/rx/favoriteGroups");
        request.setContextPath("/carlos");
        request.addParameter("command", command); request.addParameter("name", "Synthetic group");
        request.addParameter("groupId", "2");
        var response = new MockHttpServletResponse();
        var manager = mock(RxFavoriteGroupService.class);
        var security = mock(SecurityInfoManager.class);
        var info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("997001");
        when(security.hasPrivilege(info, "_rx", "u", null)).thenReturn(true);
        var bean = mock(RxSessionBean.class); when(bean.getDemographicNo()).thenReturn(1001);
        String token = io.github.carlos_emr.carlos.utility.FormSubmissionGuard.issue(request.getSession(),
                io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Scope.RX_FAVORITE_GROUP, "997001:1001");
        request.addParameter(io.github.carlos_emr.carlos.utility.FormSubmissionGuard.PARAMETER, token);
        try (var context = mockStatic(ServletActionContext.class); var session = mockStatic(LoggedInInfo.class);
                var access = mockStatic(RxRequestedPatientAccess.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            access.when(() -> RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r")).thenReturn(bean);
            new RxFavoriteGroups2Action(security, manager, transactionManager()).execute();
            response.reset(); // The replay is a separate HTTP response with the same rendered token.
            new RxFavoriteGroups2Action(security, manager, transactionManager()).execute();
            switch (command) {
                case "create" -> verify(manager).create("997001", "Synthetic group");
                case "up" -> verify(manager).move("997001", 2, -1);
                case "down" -> verify(manager).move("997001", 2, 1);
                case "delete" -> verify(manager).delete("997001", 2);
                default -> throw new AssertionError();
            }
            verifyNoMoreInteractions(manager);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/rx/favoriteGroups?demographicNo=1001");
        }
    }

    private static org.springframework.transaction.PlatformTransactionManager transactionManager() {
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        return manager;
    }
}
