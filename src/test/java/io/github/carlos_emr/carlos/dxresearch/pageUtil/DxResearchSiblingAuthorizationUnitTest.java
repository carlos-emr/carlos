/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import java.lang.reflect.Field;
import io.github.carlos_emr.carlos.dxresearch.bean.dxCodeSearchBeanHandler;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListItemsHandler;
import io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Both sibling routes must use the clinical package's existing SecurityException-to-403 mapping. */
@Tag("unit")
@Tag("dxresearch")
@Isolated("Temporarily replaces shared action security managers")
@Execution(ExecutionMode.SAME_THREAD)
class DxResearchSiblingAuthorizationUnitTest extends CarlosUnitTestBase {
    private AutoCloseable replaceSecurity(boolean items, SecurityInfoManager security) throws Exception {
        Class<?> type = items ? dxResearchLoadQuickListItems2Action.class : dxResearchCodeSearch2Action.class;
        Field field = type.getDeclaredField("securityInfoManager");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, security);
        return () -> field.set(null, previous);
    }

    private ActionSupport action(boolean items) {
        if (items) {
            var action = new dxResearchLoadQuickListItems2Action();
            action.setQuickListName("FAKE sibling");
            return action;
        }
        return new dxResearchCodeSearch2Action();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldUseForbiddenMapping_whenReadPrivilegeIsMissing(boolean items) throws Exception {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.setParameter("codeType", "icd9");
        try (var servlet = mockStatic(ServletActionContext.class);
             var restoredSecurity = replaceSecurity(items, security);
             var searches = mockConstruction(dxCodeSearchBeanHandler.class);
             var lists = mockConstruction(dxQuickListItemsHandler.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = action(items);

            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_dxresearch)");
            verify(security).hasPrivilege(login, "_dxresearch", "r", null);
            assertThat(searches.constructed()).isEmpty();
            assertThat(lists.constructed()).isEmpty();
            assertThat(request.getSession().getAttributeNames().asIterator()).toIterable()
                    .doesNotContain("allMatchedCodes", "allQuickListItems", "codeType", "quickListName", "codingSystem");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldKeepSearchAndQuickListPayload_whenReadPrivilegeIsGranted(boolean items) throws Exception {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.setParameter("codeType", "icd9");
        request.setParameter("xml_research1", "250.0");
        when(security.hasPrivilege(login, "_dxresearch", "r", null)).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var restoredSecurity = replaceSecurity(items, security);
             var searches = mockConstruction(dxCodeSearchBeanHandler.class);
             var lists = mockConstruction(dxQuickListItemsHandler.class);
             var systems = mockConstruction(dxResearchCodingSystem.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(action(items).execute()).isEqualTo(ActionSupport.SUCCESS);
            if (items) {
                assertThat(lists.constructed()).hasSize(1);
                assertThat(request.getSession().getAttribute("allQuickListItems")).isSameAs(lists.constructed().getFirst());
                assertThat(request.getSession().getAttribute("quickListName")).isEqualTo("FAKE sibling");
                assertThat(request.getSession().getAttribute("codingSystem")).isSameAs(systems.constructed().getFirst());
            } else {
                assertThat(searches.constructed()).hasSize(1);
                assertThat(request.getSession().getAttribute("allMatchedCodes")).isSameAs(searches.constructed().getFirst());
                assertThat(request.getSession().getAttribute("codeType")).isEqualTo("icd9");
            }
            assertThat(response.getStatus()).isEqualTo(200);
            verify(security).hasPrivilege(login, "_dxresearch", "r", null);
        }
    }
}
