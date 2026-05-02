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

import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleApplyResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleAppliedChange;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleChange;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleValidationError;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleImportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.StrutsUploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for Schedule of Benefits action-layer request, validation, and result contracts. */
@DisplayName("Schedule of Benefits actions")
@Tag("unit")
@Tag("billing")
class ScheduleOfBenefitsActionsUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private FeeScheduleImportService feeScheduleImportService;
    private LoggedInInfo loggedInInfo;
    private Path uploadFile;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        feeScheduleImportService = mock(FeeScheduleImportService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);

        // Update is POST-only; default mocks need to satisfy the gate so the
        // existing fee-application happy-path test still drives applySelected.
        request.setMethod("POST");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (uploadFile != null) Files.deleteIfExists(uploadFile);
    }

    @Test
    void shouldPreviewFeeSchedule_withInjectedService_onUpload() throws Exception {
        uploadFile = Files.createTempFile("schedule-of-benefits", ".txt");
        Files.writeString(uploadFile,
                "A001" + "20260428" + "99999999"
                        + "00000337000" + "00000000000" + "00000000000"
                        + "00000000000" + "00000000000" + "\n");
        FeeScheduleChange change = new FeeScheduleChange("A001A", null, new BigDecimal("33.70"), null,
                "prices", "20260428", "99999999", "Minor assessment", 0, true);
        when(feeScheduleImportService.preview(any(), any()))
                .thenReturn(new FeeScheduleImportResult(List.of(change), Collections.emptyList(), false));

        ScheduleOfBenefitsUpload2Action action =
                new ScheduleOfBenefitsUpload2Action(securityInfoManager, feeScheduleImportService);
        attachUpload(action);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("outcome")).isEqualTo("success");
        assertThat((List) request.getAttribute("warnings")).singleElement()
                .satisfies(warning -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> w = (java.util.Map<String, Object>) warning;
                    assertThat(w).containsEntry("feeCode", "A001A");
                    // Round-7 C3: pin the corrected map key 'terminationDate'
                    // (was the typo 'terminactionDate'). The JSP renders
                    // ${warning.terminationDate}; a regression of the
                    // record→map symmetry would silently render empty.
                    assertThat(w).containsKey("terminationDate");
                });
        verify(feeScheduleImportService).preview(any(), any());
    }

    @Test
    void shouldApplySelectedChanges_withInjectedService_onUpdate() throws Exception {
        request.setParameter("change", "A001A|33.70|20260428|99999999|Minor assessment");
        when(feeScheduleImportService.applySelected(any()))
                .thenReturn(new FeeScheduleApplyResult(
                        List.of(new FeeScheduleAppliedChange("A001A", new BigDecimal("33.70"))),
                        Collections.emptyList()));

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat((List) request.getAttribute("changes")).singleElement()
                .satisfies(change -> assertThat((java.util.Map) change).containsEntry("code", "A001A"));
        verify(feeScheduleImportService).applySelected(any());
    }

    // -- Security tier --------------------------------------------------

    @Test
    void shouldThrowSecurityException_onUpload_whenAdminBillingPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        ScheduleOfBenefitsUpload2Action action =
                new ScheduleOfBenefitsUpload2Action(securityInfoManager, feeScheduleImportService);

        org.assertj.core.api.Assertions.assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }

    @Test
    void shouldThrowSecurityException_onUpdate_whenAdminBillingPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        org.assertj.core.api.Assertions.assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }

    @Test
    void shouldRecordValidationExceptionOutcomeWhenImportServiceThrows() throws Exception {
        // The action's broad catch-all must surface as outcome=exception so
        // the JSP renders the error banner. Pin the contract because a
        // future refactor that changes the catch label or removes the
        // attribute would silently render a misleading "success" page.
        uploadFile = Files.createTempFile("schedule-of-benefits-bad", ".txt");
        Files.writeString(uploadFile, "garbage");
        when(feeScheduleImportService.preview(any(), any()))
                .thenThrow(new RuntimeException("boom"));

        ScheduleOfBenefitsUpload2Action action =
                new ScheduleOfBenefitsUpload2Action(securityInfoManager, feeScheduleImportService);
        attachUpload(action);

        assertThat(action.execute()).isEqualTo("exception");

        assertThat(request.getAttribute("outcome")).isEqualTo("exception");
    }

    @Test
    void shouldReturnException_whenPreviewFindsValidationErrors() throws Exception {
        uploadFile = Files.createTempFile("schedule-of-benefits-errors", ".txt");
        Files.writeString(uploadFile, "bad");
        when(feeScheduleImportService.preview(any(), any()))
                .thenReturn(new FeeScheduleImportResult(
                        Collections.emptyList(),
                        List.of(new FeeScheduleValidationError(
                                1, "bad", "serviceCode", "Invalid fee schedule line")),
                        false));

        ScheduleOfBenefitsUpload2Action action =
                new ScheduleOfBenefitsUpload2Action(securityInfoManager, feeScheduleImportService);
        attachUpload(action);

        assertThat(action.execute()).isEqualTo("exception");
        assertThat(request.getAttribute("outcome")).isEqualTo("exception");
    }

    @Test
    void shouldReturn405WithAllowHeader_onUpload_whenGet() throws Exception {
        // The fee-schedule upload mutates billingservice rows. A forged GET
        // would have bypassed the multipart-aware preview entirely; pin the
        // 405 + Allow contract so a future regression that drops the gate
        // surfaces here.
        request.setMethod("GET");

        ScheduleOfBenefitsUpload2Action action =
                new ScheduleOfBenefitsUpload2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(feeScheduleImportService, org.mockito.Mockito.never()).preview(any(), any());
    }

    @Test
    void shouldReturn405WithAllowHeader_onUpdate_whenGet() throws Exception {
        request.setMethod("GET");
        request.setParameter("change", "A001A|33.70|20260428|99999999|Minor assessment");

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(feeScheduleImportService, org.mockito.Mockito.never()).applySelected(any());
        verify(feeScheduleImportService, org.mockito.Mockito.never()).applyAll(any());
    }

    @Test
    void shouldApplyAllTypedFeeScheduleChanges_whenForceUpdateRequested() throws Exception {
        FeeScheduleChange change = new FeeScheduleChange("A001A", null, new BigDecimal("33.70"), null,
                "prices", "20260428", "99999999", "Minor assessment", 0, true);
        request.setParameter("forceUpdate", "true");
        request.setAttribute("feeScheduleChanges", List.of(change));
        when(feeScheduleImportService.applyAll(any()))
                .thenReturn(new FeeScheduleApplyResult(
                        List.of(new FeeScheduleAppliedChange("A001A", new BigDecimal("33.70"))),
                        Collections.emptyList()));

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat((List) request.getAttribute("changes")).singleElement()
                .satisfies(applied -> assertThat((java.util.Map) applied).containsEntry("code", "A001A"));
        verify(feeScheduleImportService).applyAll(List.of(change));
        verify(feeScheduleImportService, org.mockito.Mockito.never()).applySelected(any());
    }

    @Test
    void shouldRejectMalformedSelectedChange_withoutApplyingAnySelections() throws Exception {
        request.setParameter("change",
                "not-a-valid-submitted-value",
                "A001A|33.70|20260428|99999999|Minor assessment");

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("changes")).isEqualTo(List.of());
        assertThat(request.getAttribute("validationErrors"))
                .isEqualTo(List.of("Invalid selected fee schedule change at row 1"));
        verify(feeScheduleImportService, org.mockito.Mockito.never()).applySelected(any());
    }

    @Test
    void shouldNotCallApplyService_whenNoChangesSubmitted() throws Exception {
        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(securityInfoManager, feeScheduleImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(feeScheduleImportService, org.mockito.Mockito.never()).applySelected(any());
        verify(feeScheduleImportService, org.mockito.Mockito.never()).applyAll(any());
    }

    private void attachUpload(ScheduleOfBenefitsUpload2Action action) {
        action.withUploadedFiles(List.of(StrutsUploadedFile.Builder.create(uploadFile.toFile())
                .withOriginalName(uploadFile.getFileName().toString())
                .withInputName("importFile")
                .build()));
    }
}
