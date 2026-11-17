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
package io.github.carlos_emr.carlos.prevention.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

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

import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.nvc.NvcBundleException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("fast")
@Tag("prevention")
@DisplayName("UpdateVaccineCatalogue2Action")
class UpdateVaccineCatalogue2ActionUnitTest extends CarlosUnitTestBase {

    private final CanadianVaccineCatalogueManager catalogue = mock(CanadianVaccineCatalogueManager.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo admin = mock(LoggedInInfo.class);
    private final Runnable preventionTypeRefresh = mock(Runnable.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/prevention/UpdateVaccineCatalogue");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> session;
    private UpdateVaccineCatalogue2Action action;

    @BeforeEach
    void setUp() {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        session = mockStatic(LoggedInInfo.class);
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(admin);
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(true);
        action = new UpdateVaccineCatalogue2Action(security, catalogue, preventionTypeRefresh);
    }

    @AfterEach
    void tearDown() {
        session.close();
        servlet.close();
    }

    @Test
    void shouldInstallAndRefreshPreventionTypes_whenPostSucceeds() throws Exception {
        assertThat(action.execute()).isEqualTo("updated");

        verify(catalogue).update(admin);
        verify(preventionTypeRefresh).run();
    }

    @Test
    void shouldReportUnavailable_whenDownloadFails() throws Exception {
        when(catalogue.update(admin)).thenThrow(new IOException("connect timed out"));

        assertThat(action.execute()).isEqualTo("unavailable");

        verify(preventionTypeRefresh, never()).run();
    }

    @Test
    void shouldReportFailed_whenBundleIsUnusable() throws Exception {
        when(catalogue.update(admin)).thenThrow(new NvcBundleException("missing Generic"));

        assertThat(action.execute()).isEqualTo("failed");

        verify(preventionTypeRefresh, never()).run();
    }

    @Test
    void shouldReportFailed_whenInstallRollsBack() throws Exception {
        when(catalogue.update(admin)).thenThrow(new IllegalStateException("constraint violation"));

        assertThat(action.execute()).isEqualTo("failed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectUnsafeMethod_withoutTouchingCatalogue(String method) throws Exception {
        request.setMethod(method);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(catalogue);
    }

    @Test
    void shouldThrowSecurityException_whenMissingAdminWrite() {
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(catalogue);
    }
}
