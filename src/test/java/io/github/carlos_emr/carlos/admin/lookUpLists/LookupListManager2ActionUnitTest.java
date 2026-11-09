/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.admin.lookUpLists;

import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class LookupListManager2ActionUnitTest extends CarlosUnitTestBase {
    private final LookupListManager lists = mock(LookupListManager.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo actor = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private LookupListManager2Action action;

    @BeforeEach
    void configureRequest() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        registerMock(LookupListManager.class, lists);
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
        when(security.hasPrivilege(actor, "_admin", "w", null)).thenReturn(true);
        action = new LookupListManager2Action();
    }

    @AfterEach
    void restoreStatics() {
        if (login != null) login.close();
        if (servlet != null) servlet.close();
    }

    @ParameterizedTest
    @CsvSource({"order,GET", "order,HEAD", "add,GET", "add,HEAD", "remove,GET", "remove,HEAD"})
    void shouldRefuseReadMethod_beforeAnyLookupAccess(String method, String httpMethod) {
        populate(method, httpMethod);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(lists);
    }

    @ParameterizedTest
    @CsvSource({"order,GET", "order,HEAD", "add,GET", "add,HEAD", "remove,GET", "remove,HEAD"})
    void shouldRefuseDirectMutatorDispatch_beforeAnyLookupAccess(String method, String httpMethod) {
        populate(method, httpMethod);
        String result = switch (method) {
            case "order" -> action.order();
            case "add" -> action.add();
            case "remove" -> action.remove();
            default -> throw new AssertionError(method);
        };
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(lists);
    }

    @ParameterizedTest
    @ValueSource(strings = {"order", "add", "remove"})
    void shouldPreserveAuthorizedPostMutations(String method) {
        populate(method, "POST");
        when(lists.findLookupListItemsByLookupListId(actor, 9)).thenReturn(List.of());
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
        switch (method) {
            case "order" -> verify(lists).updateLookupListItemDisplayOrder(actor, 23, 7);
            case "remove" -> verify(lists).removeLookupListItem(actor, 23);
            case "add" -> {
                var item = ArgumentCaptor.forClass(LookupListItem.class);
                verify(lists).addLookupListItem(eq(actor), item.capture());
                assertThat(item.getValue().getLabel()).isEqualTo("N & <role>");
                assertThat(item.getValue().getLookupListId()).isEqualTo(9);
                assertThat(item.getValue().getCreatedBy()).isEqualTo("fixture-admin");
                assertThat(item.getValue().getDisplayOrder()).isEqualTo(1);
                assertThat(item.getValue().isActive()).isTrue();
                assertThatCode(() -> java.util.UUID.fromString(item.getValue().getValue())).doesNotThrowAnyException();
            }
            default -> throw new AssertionError(method);
        }
    }

    @Test
    void shouldKeepTheManagementReadAvailable_onGet() {
        populate("manage", "GET");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(lists).findAllActiveLookupLists(actor);
        verifyNoMoreInteractions(lists);
    }

    @Test
    void shouldPreserveAdminAuthorization_beforePostMutation() {
        populate("order", "POST");
        when(security.hasPrivilege(actor, "_admin", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(lists);
    }

    private void populate(String method, String httpMethod) {
        request.setMethod(httpMethod);
        request.setParameter("method", method);
        request.setParameter("lookupListItemId", "23");
        request.setParameter("lookupListItemDisplayOrder", "7");
        request.setParameter("lookupListId", "9");
        request.setParameter("lookupListItemLabel", "N & <role>");
        request.getSession().setAttribute("user", "fixture-admin");
    }
}
