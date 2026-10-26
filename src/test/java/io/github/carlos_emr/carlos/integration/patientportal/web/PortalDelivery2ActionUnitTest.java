/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryApplicationService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.IOException;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class PortalDelivery2ActionUnitTest {
    @Test void shouldRejectMutationGetHeadAndUnknownPost_beforeBeanOrPatientWork() throws Exception {
        for (String verb : new String[] {"GET", "HEAD", "POST"}) {
            var request = new MockHttpServletRequest(verb, "/demographic/PortalDelivery");
            request.setParameter("method", verb.equals("POST") ? "unknown" : "send");
            var response = new MockHttpServletResponse();
            try (var servlet = mockStatic(ServletActionContext.class); var beans = mockStatic(SpringUtils.class)) {
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                assertEquals("none", action().execute());
                assertEquals(verb.equals("POST") ? 400 : 405, response.getStatus());
                beans.verifyNoInteractions();
            }
        }
    }
    @Test void shouldRefuseMissingPatientAuthority_beforeResolvingDeliveryApplication() throws Exception {
        var request = request("GET", "read"); var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var beans = mockStatic(SpringUtils.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            beans.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(mock(SecurityInfoManager.class));
            assertEquals("none", action().execute()); assertEquals(403, response.getStatus());
            beans.verify(() -> SpringUtils.getBean(PortalMyCarlosDeliveryApplicationService.class), never());
        }
    }
    @Test void shouldRefuseMalformedPageOperationId_asBadRequest() throws Exception {
        var request = request("GET", "page"); request.setParameter("deliveryId", "bad");
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var beans = mockStatic(SpringUtils.class);
             var identity = mockStatic(LoggedInInfo.class); var enabled = mockStatic(PortalMyCarlosDeliveryApplicationService.class)) {
            configure(servlet, beans, identity, enabled, request, response);
            assertEquals("none", action().execute()); assertEquals(400, response.getStatus());
            beans.verify(() -> SpringUtils.getBean(PortalMyCarlosDeliveryApplicationService.class), never());
        }
    }
    @Test void shouldReturnSavedStatusJson_whenAuthorizedPostCallsApplication() throws Exception {
        var request = request("POST", "send"); var response = new MockHttpServletResponse();
        var application = mock(PortalMyCarlosDeliveryApplicationService.class);
        when(application.send(any(), eq(6001), eq(44), eq(request.getParameter("deliveryId")), eq("A".repeat(43)), any()))
                .thenReturn(new PortalMyCarlosDeliveryApplicationService.State("FAKE Document", List.of(), List.of(), null, null));
        try (var servlet = mockStatic(ServletActionContext.class); var beans = mockStatic(SpringUtils.class);
             var identity = mockStatic(LoggedInInfo.class); var enabled = mockStatic(PortalMyCarlosDeliveryApplicationService.class);
             var portal = mockStatic(PatientPortalSettings.class)) {
            configure(servlet, beans, identity, enabled, request, response);
            beans.when(() -> SpringUtils.getBean(PortalMyCarlosDeliveryApplicationService.class)).thenReturn(application);
            assertEquals("none", action().execute()); assertEquals(200, response.getStatus());
            assertTrue(response.getContentAsString().contains("\"ok\":true"));
            verify(application).send(any(), eq(6001), eq(44), anyString(), eq("A".repeat(43)), isNull());
        }
    }
    @Test void shouldRedactSigningAndClinicalDiagnostics_whenApplicationCannotPrepareDelivery() throws Exception {
        var request = request("POST", "send"); var response = new MockHttpServletResponse();
        var application = mock(PortalMyCarlosDeliveryApplicationService.class);
        when(application.send(any(), anyInt(), anyInt(), anyString(), anyString(), any()))
                .thenThrow(new IOException("FAKE clinical title and private provider diagnostics"));
        try (var servlet = mockStatic(ServletActionContext.class); var beans = mockStatic(SpringUtils.class);
             var identity = mockStatic(LoggedInInfo.class); var enabled = mockStatic(PortalMyCarlosDeliveryApplicationService.class);
             var portal = mockStatic(PatientPortalSettings.class)) {
            configure(servlet, beans, identity, enabled, request, response);
            beans.when(() -> SpringUtils.getBean(PortalMyCarlosDeliveryApplicationService.class)).thenReturn(application);
            assertEquals("none", action().execute()); assertEquals(503, response.getStatus());
            assertFalse(response.getContentAsString().contains("clinical title"));
        }
    }
    private static PortalDelivery2Action action() {
        var action = spy(new PortalDelivery2Action());
        doAnswer(call -> call.getArgument(0)).when(action).getText(anyString());
        return action;
    }
    private static MockHttpServletRequest request(String verb, String operation) {
        var request = new MockHttpServletRequest(verb, "/demographic/PortalDelivery");
        request.setParameter("method", operation); request.setParameter("demographicNo", "6001"); request.setParameter("documentId", "44");
        request.setParameter("deliveryId", "00000000-0000-4000-8000-000000000001"); request.setParameter("deviceKeyId", "A".repeat(43));
        return request;
    }
    private static void configure(org.mockito.MockedStatic<ServletActionContext> servlet, org.mockito.MockedStatic<SpringUtils> beans,
            org.mockito.MockedStatic<LoggedInInfo> identity, org.mockito.MockedStatic<PortalMyCarlosDeliveryApplicationService> enabled,
            MockHttpServletRequest request, MockHttpServletResponse response) {
        servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        beans.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        identity.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));
        enabled.when(PortalMyCarlosDeliveryApplicationService::isEnabled).thenReturn(true);
    }
}
