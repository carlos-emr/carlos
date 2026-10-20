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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.pageUtil.PatientNavModel;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The page gate refuses callers who could use none of the portal routes and says which controls to render. */
@Tag("unit")
@Tag("patient-portal")
class PortalManage2ActionUnitTest {

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final EmailComposeManager compose = mock(EmailComposeManager.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/portalManage");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<PatientPortalSettings> settings;

    @BeforeEach
    void setUp() {
        request.setParameter("demographicNo", "123");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));
        when(security.hasPrivilege(any(), eq("_demographic"), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        settings = mockStatic(PatientPortalSettings.class);
        settings.when(PatientPortalSettings::isConfigured).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (settings != null) {
            settings.close();
        }
        if (login != null) {
            login.close();
        }
        if (servlet != null) {
            servlet.close();
        }
    }

    @Test
    @DisplayName("should refuse a caller with no portal read rights")
    void shouldRefuse_withoutPortalReadRights() {
        assertThatThrownBy(() -> new PortalManage2Action(security, compose).execute())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should refuse a request without a patient")
    void shouldRefuse_withoutAPatient() {
        request.removeParameter("demographicNo");

        assertThatThrownBy(() -> new PortalManage2Action(security, compose).execute())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should offer each control only where the server will allow it")
    void shouldOfferControls_onlyWhereTheServerAllowsThem() {
        grant("_portal.invite", SecurityInfoManager.READ);
        grant("_portal.invite", SecurityInfoManager.WRITE);

        assertThat(new PortalManage2Action(security, compose).execute()).isEqualTo(ActionSupport.SUCCESS);
        // Invitation rights alone: revoke only, the front-desk case.
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanRecover")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanRevoke")).isEqualTo(true);

        // Email write: resolving a delivery works, sending still needs document write for the archive.
        when(security.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        new PortalManage2Action(security, compose).execute();
        assertThat(request.getAttribute("portalCanRecover")).isEqualTo(true);
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);

        when(security.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(compose.getEmailConsentStatus(any(), eq(123)))
                .thenReturn(new String[]{"Email", "UNKNOWN", "email.consent.status.unknown"});
        new PortalManage2Action(security, compose).execute();
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(true);
        assertThat(request.getAttribute(PortalManage2Action.DEMOGRAPHIC_ATTRIBUTE)).isEqualTo(123);
    }

    @Test
    @DisplayName("should let an account reader in without offering account changes")
    void shouldAdmitAccountReader_withoutAccountControls() {
        grant("_portal.account", SecurityInfoManager.READ);

        assertThat(new PortalManage2Action(security, compose).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("portalCanSetAccess")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanUnlock")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);
    }

    private void grant(String object, String right) {
        when(security.hasPrivilege(any(), eq(object), eq(right), eq("123"))).thenReturn(true);
    }

    @Test
    @DisplayName("should show a user who may invite what the chart says about email consent")
    void shouldPassChartConsent_whenUserMayInvite() {
        grant("_portal.invite", SecurityInfoManager.READ);
        grant("_portal.invite", SecurityInfoManager.WRITE);
        when(security.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(compose.getEmailConsentStatus(any(), eq(123)))
                .thenReturn(new String[]{"Email communication", "OPT_OUT", "email.consent.status.optOut"});

        new PortalManage2Action(security, compose).execute();

        assertThat(request.getAttribute(PortalManage2Action.CONSENT_NAME_ATTRIBUTE)).isEqualTo("Email communication");
        assertThat(request.getAttribute(PortalManage2Action.CONSENT_STATUS_ATTRIBUTE)).isEqualTo("OPT_OUT");
        assertThat(request.getAttribute(PortalManage2Action.CONSENT_LABEL_ATTRIBUTE))
                .isEqualTo("email.consent.status.optOut");
    }

    @Test
    @DisplayName("should not look up consent for a user who cannot invite")
    void shouldSkipConsent_whenUserCannotInvite() {
        grant("_portal.account", SecurityInfoManager.READ);

        new PortalManage2Action(security, compose).execute();

        verifyNoInteractions(compose);
        assertThat(request.getAttribute(PortalManage2Action.CONSENT_STATUS_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should still render the page when the consent lookup fails")
    void shouldRenderPage_whenConsentLookupFails() {
        grant("_portal.invite", SecurityInfoManager.READ);
        grant("_portal.invite", SecurityInfoManager.WRITE);
        grant("_portal.account", SecurityInfoManager.WRITE);
        when(security.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(compose.getEmailConsentStatus(any(), eq(123))).thenThrow(new IllegalStateException("database down"));

        assertThat(new PortalManage2Action(security, compose).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(true);
        assertThat(request.getAttribute("portalCanSetAccess")).isEqualTo(true);
        assertThat(request.getAttribute(PortalManage2Action.CONSENT_STATUS_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should offer no portal action, and skip the consent lookup, while the portal is switched off")
    void shouldOfferNoControls_whenPortalIsSwitchedOff() {
        settings.when(PatientPortalSettings::isConfigured).thenReturn(false);
        grant("_portal.invite", SecurityInfoManager.READ);
        grant("_portal.invite", SecurityInfoManager.WRITE);
        grant("_portal.account", SecurityInfoManager.READ);
        grant("_portal.account", SecurityInfoManager.WRITE);
        grant("_portal.account.unlock", SecurityInfoManager.WRITE);
        when(security.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);

        assertThat(new PortalManage2Action(security, compose).execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(PortalManage2Action.DEMOGRAPHIC_ATTRIBUTE)).isEqualTo(123);
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanRecover")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanRevoke")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanSetAccess")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanUnlock")).isEqualTo(false);
        verifyNoInteractions(compose);
    }

    @Test
    void shouldOfferAccountChanges_withoutUnlockRights() {
        grant("_portal.account", SecurityInfoManager.READ);
        grant("_portal.account", SecurityInfoManager.WRITE);
        new PortalManage2Action(security, compose).execute();
        assertThat(request.getAttribute("portalCanSetAccess")).isEqualTo(true);
        assertThat(request.getAttribute("portalCanUnlock")).isEqualTo(false);
    }

    @Test
    void shouldOfferUnlock_withoutAccountWriteRights() {
        grant("_portal.account", SecurityInfoManager.READ);
        grant("_portal.account.unlock", SecurityInfoManager.WRITE);
        new PortalManage2Action(security, compose).execute();
        assertThat(request.getAttribute("portalCanSetAccess")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanUnlock")).isEqualTo(true);
    }

    @Test
    @DisplayName("should give the page the record's navigation for the patient it shows")
    void shouldGiveRecordNavigation_whenPatientIsFound() {
        grant("_portal.account", SecurityInfoManager.READ);
        Demographic patient = new Demographic();
        PatientNavModel model = mock(PatientNavModel.class);
        List<Integer> looked = new ArrayList<>();

        try (MockedStatic<PatientNavModel> nav = mockStatic(PatientNavModel.class)) {
            nav.when(() -> PatientNavModel.forRequest(request, patient, PatientNavModel.Page.PORTAL)).thenReturn(model);

            assertThat(withPatient(no -> {
                looked.add(no);
                return patient;
            }).execute()).isEqualTo(ActionSupport.SUCCESS);
        }

        assertThat(looked).containsExactly(123);
        assertThat(request.getAttribute(PatientNavModel.REQUEST_ATTRIBUTE)).isSameAs(model);
    }

    @Test
    @DisplayName("should keep the page's short list of links when the patient is not found")
    void shouldLeaveNavigationOut_whenPatientIsNotFound() {
        grant("_portal.account", SecurityInfoManager.READ);

        try (MockedStatic<PatientNavModel> nav = mockStatic(PatientNavModel.class)) {
            assertThat(withPatient(no -> null).execute()).isEqualTo(ActionSupport.SUCCESS);
            nav.verify(() -> PatientNavModel.forRequest(any(), any(), any()), never());
        }

        assertThat(request.getAttribute(PatientNavModel.REQUEST_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should still render the page when the navigation cannot be built")
    void shouldRenderPage_whenNavigationFails() {
        grant("_portal.invite", SecurityInfoManager.READ);

        assertThat(withPatient(no -> { throw new IllegalStateException("database down"); }).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(PatientNavModel.REQUEST_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(PortalManage2Action.DEMOGRAPHIC_ATTRIBUTE)).isEqualTo(123);
    }

    @Test
    @DisplayName("should not look the patient up for a caller the page refuses")
    void shouldNotLookUpPatient_whenCallerIsRefused() {
        List<Integer> looked = new ArrayList<>();

        PortalManage2Action action = withPatient(no -> {
            looked.add(no);
            return new Demographic();
        });

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class);
        assertThat(looked).isEmpty();
    }

    private PortalManage2Action withPatient(IntFunction<Demographic> lookup) {
        return new PortalManage2Action(security, compose, lookup);
    }

    @Test
    @DisplayName("should answer anything but GET with 405 before any check")
    void shouldRejectPost_withMethodNotAllowed() {
        request.setMethod("POST");

        assertThat(new PortalManage2Action(security, compose).execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
        verifyNoInteractions(security, compose);
    }
}
