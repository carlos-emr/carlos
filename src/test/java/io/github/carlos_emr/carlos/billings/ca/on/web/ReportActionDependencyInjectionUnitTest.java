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

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnDiskService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaSettlementService;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ON report actions use injected services")
@Tag("unit")
@Tag("billing")
class ReportActionDependencyInjectionUnitTest {

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
        request.setMethod("POST");
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
    void shouldGenerateNewDiskThroughInjectedService() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);

        assertThat(new ViewOnReportGeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).generateNewDisk(request);
    }

    @Test
    void shouldRegenerateDiskThroughInjectedService() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);

        assertThat(new ViewOnReportRegeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).regenerateDisk(request);
    }

    @Test
    void shouldGenerateSimulationThroughInjectedService() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
        OhipReportGenerationService service = mock(OhipReportGenerationService.class);
        when(service.generateSimulation(request))
                .thenReturn(new OhipReportGenerationService.SimulationResult("preview", "", "2026-04-01", "2026-04-28"));

        assertThat(new ViewGenSimulation2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).generateSimulation(request);
        assertThat(request.getAttribute("html")).isEqualTo("preview");
    }

    @Test
    void shouldGenerateGroupReportThroughInjectedService() throws Exception {
        request.setParameter("monthCode", "APR2026");
        request.setParameter("providers", "all");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        OhipReportGenerationService service = mock(OhipReportGenerationService.class);

        assertThat(new ViewGenGroupReport2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).generateReport(request, OhipReportGenerationService.Mode.GROUP_REPORT);
    }

    @Test
    void shouldSettleStandardRAThroughInjectedService() throws Exception {
        request.setParameter("rano", "123");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        OnRaSettlementService service = mock(OnRaSettlementService.class);

        assertThat(new ViewOnGenRaSettle2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).settle("123", OnRaSettlementService.Mode.STANDARD);
    }

    @Test
    void shouldSettleI235RAThroughInjectedService() throws Exception {
        request.setParameter("rano", "123");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        OnRaSettlementService service = mock(OnRaSettlementService.class);

        assertThat(new ViewOnGenRaSettle352Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).settle("123", OnRaSettlementService.Mode.I2_35_WITH_QCODES);
    }
}
