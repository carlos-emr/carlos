/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.UnavailableHrmAttachmentService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class RemoveUnavailableHrm2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private UnavailableHrmAttachmentService service;
    private LoggedInInfo actor;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach void setup() {
        request = new MockHttpServletRequest(); request.setMethod("POST"); request.setParameter("selection", "opaque");
        response = new MockHttpServletResponse(); service = mock(UnavailableHrmAttachmentService.class);
        registerMock(UnavailableHrmAttachmentService.class, service);
        actor = mock(LoggedInInfo.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
    }
    @AfterEach void close() { login.close(); servlet.close(); }
    @Test void shouldRejectGetAndHead_beforeCallingTheService() throws Exception {
        for (String method : new String[]{"GET", "HEAD"}) {
            request.setMethod(method); response = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            new RemoveUnavailableHrm2Action().execute();
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
        }
        verifyNoInteractions(service);
    }
    @Test void shouldReturnSuccess_onlyAfterTheServiceCompletes() throws Exception {
        new RemoveUnavailableHrm2Action().execute();
        verify(service).remove(actor, "opaque", null);
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }
    @Test void shouldReturnConflict_whenTheSelectionIsExpired() throws Exception {
        doThrow(new IllegalArgumentException()).when(service).remove(actor, "opaque", null);
        new RemoveUnavailableHrm2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
    }
    @Test void shouldReturnForbidden_whenThePatientIsInaccessible() throws Exception {
        doThrow(new SecurityException()).when(service).remove(actor, "opaque", null);
        new RemoveUnavailableHrm2Action().execute();
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void shouldReturnServerError_withoutASuccessBody_whenTheTransactionFails() throws Exception {
        doThrow(new IllegalStateException("synthetic failure")).when(service).remove(actor, "opaque", null);
        new RemoveUnavailableHrm2Action().execute();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).doesNotContain("success");
    }
}
