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

/** Unit coverage for required Spring dependency injection on Ontario billing report actions. */
@DisplayName("ON report actions use injected services")
@Tag("unit")
@Tag("billing")
class ReportActionDependencyInjectionUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<io.github.carlos_emr.carlos.log.LogAction> logActionMock;
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
        logActionMock = mockStatic(io.github.carlos_emr.carlos.log.LogAction.class);
    }

    @AfterEach
    void tearDown() {
        if (logActionMock != null) logActionMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldGenerateNewDisk_throughInjectedService() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);

        request.setParameter("providers", "999998");
        request.setParameter("billcenter", "4");
        request.setParameter("xml_vdate", "2026-04-01");
        request.setParameter("xml_appointment_date", "2026-04-30");

        assertThat(new ViewOnReportGeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).generateNewDisk(request);
        // OSCAR 19 contract: a generated OHIP file leaves an audit row (generate / ohip file).
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(
                eq(loggedInInfo), eq(io.github.carlos_emr.carlos.log.LogConst.GENERATE),
                eq(io.github.carlos_emr.carlos.log.LogConst.CON_OHIP), isNull(), isNull(),
                eq("provider_no=999998; billCenter=4; dateBegin=2026-04-01; dateEnd=2026-04-30")));
    }

    @Test
    void shouldNotAuditGeneration_whenBillingGroupIsInvalid() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);
        org.mockito.Mockito.doThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.InvalidBillingGroupException(
                java.util.List.of("999998"))).when(service).generateNewDisk(request);

        new ViewOnReportGeneration2Action(securityInfoManager, service).execute();

        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldAuditSimulation_asOhipFileSimulate() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
        var assembler = mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipSimulationViewModelAssembler.class);
        request.setParameter("submit", "Create Report");
        request.setParameter("providers", "999998");
        request.setParameter("xml_vdate", "2026-04-01");
        request.setParameter("xml_appointment_date", "2026-04-30");

        assertThat(new ViewBillingOhipSimulation2Action(securityInfoManager, assembler).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(
                eq(loggedInInfo), eq(io.github.carlos_emr.carlos.log.LogConst.SIMULATE),
                eq(io.github.carlos_emr.carlos.log.LogConst.CON_OHIP), isNull(), isNull(),
                eq("provider_no=999998; dateBegin=2026-04-01; dateEnd=2026-04-30")));
    }

    @Test
    void shouldReturnProviderConfigurationGuidance_whenBillingGroupIsInvalid() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);
        var providers = java.util.List.of("999998", "999997");
        org.mockito.Mockito.doThrow(
                new io.github.carlos_emr.carlos.billings.ca.on.validator.InvalidBillingGroupException(providers))
                .when(service).generateNewDisk(request);

        assertThat(new ViewOnReportGeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("ohipInvalidGroupProviders")).isEqualTo(providers);
    }

    @Test
    void shouldReturnGenerationGuidance_whenSelectedProviderIsNotBillable() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);
        org.mockito.Mockito.doThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                "Selected provider is not available for group billing."))
                .when(service).generateNewDisk(request);

        assertThat(new ViewOnReportGeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("ohipGenerationError"))
                .isEqualTo("Selected provider is not available for group billing.");
        assertThat(request.getAttribute("ohipInvalidGroupProviders")).isNull();
    }

    @Test
    void shouldRegenerateDisk_throughInjectedService() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);

        assertThat(new ViewOnReportRegeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).regenerateDisk(request);
    }

    @Test
    void shouldGenerateSimulation_throughInjectedService() throws Exception {
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
    void shouldGenerateGroupReport_throughInjectedService() throws Exception {
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
    void shouldSettleStandardRA_throughInjectedService() throws Exception {
        request.setParameter("rano", "123");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        OnRaSettlementService service = mock(OnRaSettlementService.class);

        assertThat(new ViewOnGenRaSettle2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).settle("123", OnRaSettlementService.Mode.STANDARD);
    }

    @Test
    void shouldSettleI235RA_throughInjectedService() throws Exception {
        request.setParameter("rano", "123");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        OnRaSettlementService service = mock(OnRaSettlementService.class);

        assertThat(new ViewOnGenRaSettle352Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(service).settle("123", OnRaSettlementService.Mode.I2_35_WITH_QCODES);
    }

    @Test
    void shouldNotAuditSimulation_whenOnlyDisplayingTheForm() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
        var assembler = mock(io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipSimulationViewModelAssembler.class);

        assertThat(new ViewBillingOhipSimulation2Action(securityInfoManager, assembler).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldAuditStoppedGeneration_whenValidationFailsMidRun() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);
        request.setParameter("providers", "all");
        request.setParameter("billcenter", "4");
        request.setParameter("xml_vdate", "2026-04-01");
        request.setParameter("xml_appointment_date", "2026-04-30");
        org.mockito.Mockito.doThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.ClaimFileValidationException(
                "999997", "Header1: Date of birth missing or invalid! - 77")).when(service).generateNewDisk(request);

        assertThat(new ViewOnReportGeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("ohipGenerationError")).asString().contains("Provider 999997");
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(
                eq(loggedInInfo), eq(io.github.carlos_emr.carlos.log.LogConst.GENERATE),
                eq(io.github.carlos_emr.carlos.log.LogConst.CON_OHIP), isNull(), isNull(),
                eq("provider_no=all; billCenter=4; dateBegin=2026-04-01; dateEnd=2026-04-30; outcome=stopped: ClaimFileValidationException")));
    }

    @Test
    void shouldReturnRegenerationGuidance_whenRegenerationIsRejected() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);
        org.mockito.Mockito.doThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                "No billable provider remains on disk 20")).when(service).regenerateDisk(request);

        assertThat(new ViewOnReportRegeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.INPUT);

        assertThat(request.getAttribute("ohipGenerationError")).isEqualTo("No billable provider remains on disk 20");
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldAuditRegeneration_asOhipFileGenerate() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        BillingOnDiskService service = mock(BillingOnDiskService.class);
        request.setParameter("diskId", "20");
        request.setParameter("billcenter", "4");

        assertThat(new ViewOnReportRegeneration2Action(securityInfoManager, service).execute())
                .isEqualTo(ActionSupport.SUCCESS);

        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(
                eq(loggedInInfo), eq(io.github.carlos_emr.carlos.log.LogConst.GENERATE),
                eq(io.github.carlos_emr.carlos.log.LogConst.CON_OHIP), isNull(), isNull(),
                eq("regenerate diskId=20; billCenter=4")));
    }
}
