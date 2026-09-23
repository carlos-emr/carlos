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
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

/** The page gate refuses callers who could use none of the portal routes and says which controls to render. */
@Tag("unit")
@Tag("patient-portal")
class PortalManage2ActionUnitTest {

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach
    void setUp() {
        request.setParameter("demographicNo", "123");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));
        when(security.hasPrivilege(any(), eq("_demographic"), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
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
        assertThatThrownBy(() -> new PortalManage2Action(security).execute())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should refuse a request without a patient")
    void shouldRefuse_withoutAPatient() {
        request.removeParameter("demographicNo");

        assertThatThrownBy(() -> new PortalManage2Action(security).execute())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should offer each control only where the server will allow it")
    void shouldOfferControls_onlyWhereTheServerAllowsThem() {
        grant("_portal.invite", SecurityInfoManager.READ);
        grant("_portal.invite", SecurityInfoManager.WRITE);

        assertThat(new PortalManage2Action(security).execute()).isEqualTo(ActionSupport.SUCCESS);
        // Invitation rights alone: revoke only, the front-desk case.
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanRecover")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanRevoke")).isEqualTo(true);

        // Email write: resolving a delivery works, sending still needs document write for the archive.
        when(security.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        new PortalManage2Action(security).execute();
        assertThat(request.getAttribute("portalCanRecover")).isEqualTo(true);
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);

        when(security.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        new PortalManage2Action(security).execute();
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(true);
        assertThat(request.getAttribute(PortalManage2Action.DEMOGRAPHIC_ATTRIBUTE)).isEqualTo(123);
    }

    @Test
    @DisplayName("should let an account reader in without offering account changes")
    void shouldAdmitAccountReader_withoutAccountControls() {
        grant("_portal.account", SecurityInfoManager.READ);

        assertThat(new PortalManage2Action(security).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("portalCanSetAccess")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanUnlock")).isEqualTo(false);
        assertThat(request.getAttribute("portalCanInvite")).isEqualTo(false);
    }

    private void grant(String object, String right) {
        when(security.hasPrivilege(any(), eq(object), eq(right), eq("123"))).thenReturn(true);
    }
}
