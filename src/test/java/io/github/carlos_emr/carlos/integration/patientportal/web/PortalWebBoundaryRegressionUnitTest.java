/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Regression tests for patient scope and structured failure responses. */
@Tag("unit")
@Tag("patient-portal")
class PortalWebBoundaryRegressionUnitTest {
    @Test
    void shouldReturnJson_whenConfiguredPortalBeanCannotInitialize() throws Exception {
        var security = mock(SecurityInfoManager.class);
        var resolver = mock(PortalStaffContextResolver.class);
        var session = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("method", "unlock");
        request.setParameter("demographicNo", "123");
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var settings = mockStatic(PatientPortalSettings.class);
             var spring = mockStatic(SpringUtils.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            settings.when(PatientPortalSettings::isConfigured).thenReturn(true);
            spring.when(() -> SpringUtils.getBean(PatientPortalService.class)).thenThrow(
                    new org.springframework.beans.factory.BeanCreationException("patientPortalService",
                            "configuration failed", new PatientPortalConfigurationException("bad timeout")));
            assertThatCode(() -> new PortalAccount2Action(security, null, resolver).execute())
                    .doesNotThrowAnyException();
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).contains("portal_configuration_invalid");
            assertThat(response.getContentType()).startsWith("application/json");
        }
    }

    @Test
    void shouldNotUnlockPatient_whenScopedPermissionIsDenied() throws Exception {
        var security = mock(SecurityInfoManager.class);
        var portal = mock(PatientPortalService.class);
        var resolver = mock(PortalStaffContextResolver.class);
        var session = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("method", "unlock");
        request.setParameter("demographicNo", "123");
        // The user may see this patient's record and holds every other permission for them; only the
        // unlock object is denied, so the refusal can come from nowhere but the unlock gate.
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK),
                eq(SecurityInfoManager.WRITE), eq("123"))).thenReturn(false);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            new PortalAccount2Action(security, portal, resolver).execute();
        }
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"not_permitted\"");
        verify(security).hasPrivilege(session, PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK,
                SecurityInfoManager.WRITE, "123");
        verifyNoInteractions(portal, resolver);
    }

    @Test
    void shouldNotDescribeRejectedServiceCredentials_asNoPatientAccount() throws Exception {
        var security = mock(SecurityInfoManager.class);
        var portal = mock(PatientPortalService.class);
        var resolver = mock(PortalStaffContextResolver.class);
        var session = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setParameter("demographicNo", "123");
        when(security.hasPrivilege(any(), anyString(), eq("r"), eq("123")))
                .thenReturn(true);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenReturn(new PatientPortalStaffContext(
                "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE)));
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(portal.findAccount(eq(123), any())).thenThrow(
                PatientPortalException.ofStatus(404, "/internal/carlos/patients/{id}/portal-account", "not found"));
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            new PortalPanel2Action(security, portal, resolver).execute();
            assertThat(response.getContentAsString()).contains("accountError", "\"ok\":false")
                    .doesNotContain("no_portal_account");
        }
    }
}
