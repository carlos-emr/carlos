/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Create Key forwards to {@code createKey.jsp}, which generates and stores a key pair for any request
 * that names a service. A request carrying {@code name} must therefore be the page's own POST; a GET
 * without it still opens the empty form (#6321, app-findings-log finding 163).
 */
@Tag("unit")
@Tag("security")
class KeygenCreateKeyMethodUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void prepare() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        user = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(user, "_admin", "w", null)).thenReturn(true);
        servlet = org.mockito.Mockito.mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void closeServlet() {
        if (servlet != null) {
            servlet.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "OPTIONS", "post"})
    @DisplayName("should refuse a request that names a service unless it is a POST")
    void shouldRefuseKeyCreation_whenNamedRequestIsNotPost(String method) throws Exception {
        request.setMethod(method);
        request.addParameter("name", "probe-get");

        assertThat(new ViewKeygenCreateKey2Action().execute())
                .as("NONE: the key-creating createKey.jsp is never forwarded to")
                .isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(response.getContentAsByteArray()).as("nothing is streamed").isEmpty();
        // The privilege check is the only collaborator touched before the refusal.
        verify(security).hasPrivilege(user, "_admin", "w", null);
        verifyNoMoreInteractions(security);
    }

    @Test
    @DisplayName("should refuse a GET whose service name is empty, since the view still reads it")
    void shouldRefuseKeyCreation_whenNameIsEmptyOnGet() throws Exception {
        request.setMethod("GET");
        request.addParameter("name", "");

        assertThat(new ViewKeygenCreateKey2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should open the empty Create Key form for a request without a service name")
    void shouldOpenForm_whenNoServiceNameIsSent(String method) throws Exception {
        request.setMethod(method);

        assertThat(new ViewKeygenCreateKey2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should forward the form's own POST to the key-creating view")
    void shouldForwardToCreateView_forAuthorizedPost() throws Exception {
        request.setMethod("POST");
        request.addParameter("name", "probe-post");

        assertThat(new ViewKeygenCreateKey2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    @DisplayName("should keep requiring admin write before anything else")
    void shouldRequireAdminWrite_forEveryMethod(String method) {
        request.setMethod(method);
        request.addParameter("name", "probe");
        when(security.hasPrivilege(user, "_admin", "w", null)).thenReturn(false);

        assertThatThrownBy(() -> new ViewKeygenCreateKey2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
    }
}
