/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("admin")
class ProviderAddRoleWriteUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo viewer = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(viewer);
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
    }

    @AfterEach
    void tearDown() {
        spring.close();
        loggedIn.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_admin", "_admin.userAdmin"})
    void shouldAllowDisplayAndSearch_withoutOfferingSave_whenViewerHasReadOnly(String object) throws Exception {
        when(security.hasPrivilege(viewer, object, "r", null)).thenReturn(true);
        request.setMethod("GET");
        assertThat(new ProviderAddRole2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("canSaveRole")).isEqualTo(false);
        request.addParameter("submit", "Search");
        for (String method : new String[]{"GET", "POST"}) {
            request.setMethod(method);
            assertThat(new ProviderAddRole2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("canSaveRole")).isEqualTo(false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"_admin", "_admin.userAdmin"})
    void shouldRefuseSaveBeforeForwarding_whenViewerHasReadOnly(String object) {
        when(security.hasPrivilege(viewer, object, "r", null)).thenReturn(true);
        request.setMethod("POST");
        request.addParameter("submit", "Save");
        request.addParameter("action", "addFAKE-ROLE");
        assertThatThrownBy(() -> new ProviderAddRole2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin or _admin.userAdmin)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_admin", "_admin.userAdmin"})
    void shouldAllowSave_whenViewerHasWrite(String object) throws Exception {
        when(security.hasPrivilege(viewer, object, "r", null)).thenReturn(true);
        when(security.hasPrivilege(viewer, object, "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.addParameter("submit", "Save");
        assertThat(new ProviderAddRole2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("canSaveRole")).isEqualTo(true);
    }

    @ParameterizedTest
    @CsvSource({"_admin,GET", "_admin,HEAD", "_admin.userAdmin,GET", "_admin.userAdmin,HEAD"})
    void shouldRequirePost_whenWriterSubmitsSave(String object, String method) throws Exception {
        when(security.hasPrivilege(viewer, object, "r", null)).thenReturn(true);
        when(security.hasPrivilege(viewer, object, "w", null)).thenReturn(true);
        request.setMethod(method);
        request.addParameter("submit", "Save");
        assertThat(new ProviderAddRole2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldRefuseDisplay_whenViewerHasNoReadAccess() {
        request.setMethod("GET");
        assertThatThrownBy(() -> new ProviderAddRole2Action().execute()).isInstanceOf(SecurityException.class);
    }
}
