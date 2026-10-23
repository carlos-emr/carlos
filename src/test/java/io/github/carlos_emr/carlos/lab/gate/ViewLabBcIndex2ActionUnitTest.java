/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.gate;

import io.github.carlos_emr.carlos.lab.service.PathnetPatientLinkService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
@Tag("lab")
class ViewLabBcIndex2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private PathnetPatientLinkService service;

    @BeforeEach
    void prepareRequest() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        LoggedInInfo info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = createAndRegisterMock(SecurityInfoManager.class);
        service = createAndRegisterMock(PathnetPatientLinkService.class);
        when(security.hasPrivilege(eq(info), eq("_lab"), eq("w"), isNull())).thenReturn(true);
    }

    private String execute() throws Exception {
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new ViewLabBcIndex2Action().execute();
        }
    }

    @Test
    void shouldRenderWithoutWrites_whenOpenedWithGet() throws Exception {
        request.setMethod("GET");
        assertThat(execute()).isEqualTo("success");
        verifyNoInteractions(service);
    }

    @Test
    void shouldRefuseMutation_whenGetCarriesPatientSelection() throws Exception {
        request.setMethod("GET");
        request.setParameter("pid", "3");
        request.setParameter("demo_id", "42");
        assertThat(execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(service);
    }

    @Test
    void shouldLinkThroughService_whenPatientSelectedByPost() throws Exception {
        request.setMethod("POST");
        request.setParameter("pid", "3");
        request.setParameter("demo_id", "42");
        assertThat(execute()).isEqualTo("success");
        verify(service).link(3, 42, "999998");
    }

    @Test
    void shouldConfirmThroughService_whenMatchedLabChecked() throws Exception {
        request.setMethod("POST");
        request.setParameter("chk", "3");
        assertThat(execute()).isEqualTo("success");
        verify(service).link(3, null, "999998");
    }

    @Test
    void shouldRejectMalformedIdentifiers_whenPosted() throws Exception {
        request.setMethod("POST");
        request.setParameter("pid", "invalid");
        request.setParameter("demo_id", "42");
        assertThat(execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void shouldRequireLabWritePrivilege_whenLinkingPageOpened() {
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(false);
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(service);
    }
}
