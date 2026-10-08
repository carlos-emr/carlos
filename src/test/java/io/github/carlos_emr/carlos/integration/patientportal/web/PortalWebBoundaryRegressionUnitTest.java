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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
             var spring = mockStatic(SpringUtils.class);
             var logs = LogCapture.forLogger(PortalJsonAction.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            settings.when(PatientPortalSettings::isConfigured).thenReturn(true);
            spring.when(() -> SpringUtils.getBean(PatientPortalService.class)).thenThrow(
                    new org.springframework.beans.factory.BeanCreationException("patientPortalService",
                            "configuration failed", new PatientPortalConfigurationException(
                                    "patient_portal.timeout.request.ms must be at most 59000 milliseconds")));
            assertThatCode(() -> new PortalAccount2Action(security, null, resolver).execute())
                    .doesNotThrowAnyException();
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).contains("portal_configuration_invalid");
            assertThat(response.getContentType()).startsWith("application/json");
            // Only the setting the portal exception names is logged; the bean's own message, and the
            // rest of the configuration message, may carry configured values.
            assertThat(logs.messages()).anySatisfy(message ->
                    assertThat(message).endsWith(": patient_portal.timeout.request.ms"));
            assertThat(logs.messages()).noneSatisfy(message -> assertThat(message).contains("configuration failed"));
            assertThat(logs.messages()).noneSatisfy(message -> assertThat(message).contains("59000"));
        }
    }

    /** The three struts-demographic.xml routes, each valid up to the point it needs the portal client. */
    @ParameterizedTest
    @ValueSource(strings = {"portalInvite", "portalAccount", "portalPanel"})
    void shouldAnswerPortalNotConfigured_whenPortalIsSwitchedOff(String route) throws Exception {
        var security = mock(SecurityInfoManager.class);
        var resolver = mock(PortalStaffContextResolver.class);
        var session = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("portalPanel".equals(route) ? "GET" : "POST");
        request.setParameter("demographicNo", "123");
        if ("portalInvite".equals(route)) {
            // Create, resend and recovery are covered by the delivery test below.
            request.setParameter("method", PortalInvite2Action.METHOD_REVOKE);
            request.setParameter("inviteId", "7");
        } else if ("portalAccount".equals(route)) {
            request.setParameter("method", PortalAccount2Action.METHOD_UNLOCK);
        }
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var settings = mockStatic(PatientPortalSettings.class);
             var spring = mockStatic(SpringUtils.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            settings.when(PatientPortalSettings::isConfigured).thenReturn(false);
            PortalJsonAction action = switch (route) {
                case "portalInvite" -> new PortalInvite2Action(security, null, resolver);
                case "portalAccount" -> new PortalAccount2Action(security, null, resolver);
                default -> new PortalPanel2Action(security, null, resolver);
            };
            action.execute();
            spring.verify(() -> SpringUtils.getBean(PatientPortalService.class), never());
        }
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"portal_not_configured\"");
        verifyNoInteractions(resolver);
    }

    /**
     * The invitation workflow (#3856) has the portal prepare and commit each invitation code that CARLOS
     * then emails, so with the portal switched off it answers like every other portal action: nothing is
     * prepared on the portal, no email is queued, and the workflow is never even looked up.
     */
    @ParameterizedTest
    @ValueSource(strings = {PortalInvite2Action.METHOD_CREATE, PortalInvite2Action.METHOD_RESEND,
            PortalInvite2Action.METHOD_RECOVER})
    void shouldAnswerPortalNotConfigured_whenInviteDeliveryIsRequestedWithPortalSwitchedOff(String method)
            throws Exception {
        var security = mock(SecurityInfoManager.class);
        var resolver = mock(PortalStaffContextResolver.class);
        var demographics = mock(DemographicManager.class);
        var session = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", "123");
        request.setParameter("method", method);
        request.setParameter("inviteId", "7");
        request.setParameter("deliveryId", "9");
        request.setParameter("decision", PortalInviteDeliveryService.Decision.CONFIRM_SENT.requestValue());
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var settings = mockStatic(PatientPortalSettings.class);
             var spring = mockStatic(SpringUtils.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            settings.when(PatientPortalSettings::isConfigured).thenReturn(false);
            new PortalInvite2Action(security, null, resolver, null, demographics).execute();
            spring.verify(() -> SpringUtils.getBean(PortalInviteDeliveryService.class), never());
            spring.verify(() -> SpringUtils.getBean(PatientPortalService.class), never());
        }
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"portal_not_configured\"");
        verifyNoInteractions(resolver, demographics);
    }

    /**
     * Privileges are checked before the switch, so a user without them gets the same refusal
     * whether the portal is on or off and cannot learn which.
     */
    @ParameterizedTest
    @ValueSource(strings = {"portalInvite", "portalAccount", "portalPanel"})
    void shouldRefuseBeforeConsultingTheSwitch_whenPrivilegeIsDenied(String route) throws Exception {
        var security = mock(SecurityInfoManager.class);
        var resolver = mock(PortalStaffContextResolver.class);
        var session = mock(LoggedInInfo.class);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("portalPanel".equals(route) ? "GET" : "POST");
        request.setParameter("demographicNo", "123");
        if ("portalInvite".equals(route)) {
            request.setParameter("method", PortalInvite2Action.METHOD_REVOKE);
            request.setParameter("inviteId", "7");
        } else if ("portalAccount".equals(route)) {
            request.setParameter("method", PortalAccount2Action.METHOD_UNLOCK);
        }
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(false);
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var settings = mockStatic(PatientPortalSettings.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
            settings.when(PatientPortalSettings::isConfigured).thenReturn(false);
            PortalJsonAction action = switch (route) {
                case "portalInvite" -> new PortalInvite2Action(security, null, resolver);
                case "portalAccount" -> new PortalAccount2Action(security, null, resolver);
                default -> new PortalPanel2Action(security, null, resolver);
            };
            action.execute();
            settings.verify(PatientPortalSettings::isConfigured, never());
        }
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("portal_not_configured");
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
