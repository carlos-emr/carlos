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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingInrReportViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.GstReportViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingInrReportViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GstReportViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

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

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ON billing report view actions")
@Tag("unit")
@Tag("billing")
class BillingReportViewActionsAuthorizedUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void gstReportAuthorizedPath_stashesAssemblerModel() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        GstReportViewModelAssembler assembler = mock(GstReportViewModelAssembler.class);
        GstReportViewModel model = new GstReportViewModel(
                "2026-05-01", "", "", "all", Collections.emptyList(), Collections.emptyList(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(assembler.assemble(request, loggedInInfo)).thenReturn(model);

        String result = new GstReport2Action(securityInfoManager, assembler).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("gstReportModel")).isSameAs(model);
        verify(assembler).assemble(request, loggedInInfo);
    }

    @Test
    void viewInrReportAuthorizedPath_stashesAssemblerModel() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
        BillingInrReportViewModelAssembler assembler = mock(BillingInrReportViewModelAssembler.class);
        BillingInrReportViewModel model = BillingInrReportViewModel.builder()
                .userNo("999998")
                .providerView("all")
                .build();
        when(assembler.assemble(request, loggedInInfo)).thenReturn(model);

        String result = new ViewInrReport2Action(securityInfoManager, assembler).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("reportInrModel")).isSameAs(model);
        verify(assembler).assemble(request, loggedInInfo);
    }

    @Test
    void viewBillingOhipReportRejectsMissingSessionBeforePrivilegeCheck() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> new ViewBillingOhipReport2Action(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(securityInfoManager).hasPrivilege(isNull(), eq("_billing"), eq("r"), isNull());
    }

    @Test
    void viewBillingOhipReportRejectsMissingBillingPrivilege() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new ViewBillingOhipReport2Action(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void viewBillingOhipReportAuthorizedPathReturnsSuccessWithoutSideEffects() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);

        String result = new ViewBillingOhipReport2Action(securityInfoManager).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(Collections.list(request.getAttributeNames())).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
