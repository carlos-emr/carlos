/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class PortalDeviceApproval2ActionUnitTest {
    @Test void shouldRefuseMutationGetAndHead_whenApprovalParametersArePresent() throws Exception {
        for (String method : new String[]{"GET", "HEAD"}) {
            var security = mock(SecurityInfoManager.class);
            var request = new MockHttpServletRequest(method, "/demographic/portalDeviceApproval");
            request.setParameter("method", "approveFirst");
            request.setParameter("demographicNo", "6001");
            var response = new MockHttpServletResponse();
            try (var context = mockStatic(ServletActionContext.class)) {
                context.when(ServletActionContext::getRequest).thenReturn(request);
                context.when(ServletActionContext::getResponse).thenReturn(response);
                assertEquals("none", new PortalDeviceApproval2Action(security).execute());
                assertEquals(405, response.getStatus());
                verifyNoInteractions(security);
            }
        }
    }
    @Test void shouldRefusePatientWithoutAccess_whenReadingApprovalState() throws Exception {
        var security = mock(SecurityInfoManager.class);
        var request = new MockHttpServletRequest("GET", "/demographic/portalDeviceApproval");
        request.setParameter("demographicNo", "6001");
        var response = new MockHttpServletResponse();
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            assertEquals("none", new PortalDeviceApproval2Action(security).execute());
            assertEquals(403, response.getStatus());
        }
    }
}
