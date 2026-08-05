/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Fax2Action incomplete eForm render approval")
@Tag("unit")
@Tag("fast")
class Fax2ActionIncompleteRenderUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should inform the clinician and issue an exact approval before faxing incomplete content")
    void shouldRequireInformedApproval_whenEFormRenderIsIncomplete() throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EFormRenderApprovalService approvalService = mock(EFormRenderApprovalService.class);
        EFormDataDao eFormDataDao = mock(EFormDataDao.class);
        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(123);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 1, 0, 0, true, true, false, false);

        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        when(eFormDataDao.find(42)).thenReturn(eFormData);
        when(faxManager.getFaxGatewayAccounts(loggedInInfo))
                .thenReturn(List.of(mock(FaxConfig.class)));
        when(documentAttachmentManager.stageEFormPacketForFaxPreview(eq(request), eq(response), any()))
                .thenReturn(new io.github.carlos_emr.carlos.managers.EformDataManager.EformPdfRender(
                        Path.of("staged-eform.pdf"), report, Map.of(42, report)));
        when(approvalService.issueStagedFaxPreview(
                request, loggedInInfo, 42, "123", Map.of(42, report), 1,
                Path.of("staged-eform.pdf")))
                .thenReturn("exact-approval-token");

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EFormRenderApprovalService.class, approvalService);

        registerMock(EFormDataDao.class, eFormDataDao);
        try (MockedStatic<ServletActionContext> servletActionContext =
                     mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = eFormAction();

            assertThat(action.prepareFax()).isEqualTo("eFormMissingContent");
        }

        assertThat(request.getAttribute("renderApproval")).isEqualTo("exact-approval-token");
        assertThat(request.getAttribute("failedContentResources")).isEqualTo(2);
        assertThat(request.getAttribute("excludedContentElements")).isEqualTo(1);
        assertThat(request.getAttribute("signatureMissing")).isEqualTo(true);
        assertThat(request.getAttribute("timerCompatibilityFailure")).isEqualTo(true);
        assertThat(response.isCommitted()).isFalse();
        verify(approvalService).issueStagedFaxPreview(
                request, loggedInInfo, 42, "123", Map.of(42, report), 1,
                Path.of("staged-eform.pdf"));
    }

    @Test
    @DisplayName("should reject an unavailable staged incomplete-render approval before rendering")
    void shouldRejectUnavailableStagedApproval_beforeRenderingEForm() throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EFormRenderApprovalService approvalService = mock(EFormRenderApprovalService.class);
        EFormDataDao eFormDataDao = mock(EFormDataDao.class);
        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(123);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setParameter("renderApproval", "forged-or-expired");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        when(eFormDataDao.find(42)).thenReturn(eFormData);
        when(faxManager.getFaxGatewayAccounts(loggedInInfo))
                .thenReturn(List.of(mock(FaxConfig.class)));
        when(approvalService.consumeStagedFaxPreview(
                request, loggedInInfo, 42, "123", "forged-or-expired"))
                .thenReturn(null);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EFormRenderApprovalService.class, approvalService);

        registerMock(EFormDataDao.class, eFormDataDao);
        try (MockedStatic<ServletActionContext> servletActionContext =
                     mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = eFormAction();

            assertThat(action.prepareFax()).isEqualTo(Fax2Action.NONE);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).contains("no longer available");
        verify(documentAttachmentManager, never()).stageEFormPacketForFaxPreview(
                any(), any(), any());
    }

    @Test
    @DisplayName("should revoke a staged approval when the saved eForm moves to another patient")
    void shouldRevalidateCurrentPatientBinding_beforeClaimingStagedPreview() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EFormRenderApprovalService approvalService = mock(EFormRenderApprovalService.class);
        EFormDataDao eFormDataDao = mock(EFormDataDao.class);
        EFormData movedEForm = new EFormData();
        movedEForm.setDemographicId(456);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setParameter("renderApproval", "stale-patient-token");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(faxManager.getFaxGatewayAccounts(loggedInInfo))
                .thenReturn(List.of(mock(FaxConfig.class)));
        when(eFormDataDao.find(42)).thenReturn(movedEForm);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EFormRenderApprovalService.class, approvalService);
        registerMock(EFormDataDao.class, eFormDataDao);

        try (MockedStatic<ServletActionContext> servletActionContext =
                     mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(eFormAction().prepareFax()).isEqualTo(Fax2Action.NONE);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(approvalService).cancelStagedFaxPreview(
                request, loggedInInfo, 42, "123", "stale-patient-token");
        verify(approvalService, never()).consumeStagedFaxPreview(any(), any(), any(Integer.class), any(), any());
    }

    @Test
    @DisplayName("should revoke the staged approval on the server when the clinician cancels")
    void shouldRevokeStagedPreview_onCancel() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EFormRenderApprovalService approvalService = mock(EFormRenderApprovalService.class);
        EFormDataDao eFormDataDao = mock(EFormDataDao.class);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setMethod("POST");
        request.setParameter("method", "cancelStagedEFormFax");
        request.setParameter("renderApproval", "cancel-token");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EFormRenderApprovalService.class, approvalService);
        registerMock(EFormDataDao.class, eFormDataDao);

        try (MockedStatic<ServletActionContext> servletActionContext =
                     mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(eFormAction().execute()).isEqualTo(Fax2Action.NONE);
        }

        verify(approvalService).cancelStagedFaxPreview(
                request, loggedInInfo, 42, "123", "cancel-token");
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/eform/efmshowform_data?fdid=42&parentAjaxId=eforms");
    }

    @Test
    @DisplayName("should delete the staged PDF when approval issuance fails")
    void shouldDeleteUnownedStagedPreview_whenApprovalIssuanceFails() throws Exception {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"), "carlos-temp");
        Files.createDirectories(tempRoot);
        Path testRoot = Files.createTempDirectory(tempRoot, "fax-issue-failure-");
        Path stagedPath = Files.createTempFile(testRoot, "staged-", ".pdf");
        try {
            FaxManager faxManager = mock(FaxManager.class);
            DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
            SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
            EFormRenderApprovalService approvalService = mock(EFormRenderApprovalService.class);
            EFormDataDao eFormDataDao = mock(EFormDataDao.class);
            EFormData eFormData = new EFormData();
            eFormData.setDemographicId(123);
            LoggedInInfo loggedInInfo = new LoggedInInfo();
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            EFormRenderCompletenessReport report =
                    new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false);

            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
            when(securityInfoManager.hasPrivilege(
                    eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                    .thenReturn(true);
            when(securityInfoManager.hasPrivilege(
                    loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                    .thenReturn(true);
            when(eFormDataDao.find(42)).thenReturn(eFormData);
            when(faxManager.getFaxGatewayAccounts(loggedInInfo))
                    .thenReturn(List.of(mock(FaxConfig.class)));
            when(documentAttachmentManager.stageEFormPacketForFaxPreview(eq(request), eq(response), any()))
                    .thenReturn(new io.github.carlos_emr.carlos.managers.EformDataManager.EformPdfRender(
                            stagedPath, report, Map.of(42, report)));
            when(approvalService.issueStagedFaxPreview(
                    request, loggedInInfo, 42, "123", Map.of(42, report), 0, stagedPath))
                    .thenThrow(new IllegalStateException("cache unavailable"));

            registerMock(FaxManager.class, faxManager);
            registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
            registerMock(SecurityInfoManager.class, securityInfoManager);
            registerMock(EFormRenderApprovalService.class, approvalService);
            registerMock(EFormDataDao.class, eFormDataDao);

            try (MockedStatic<ServletActionContext> servletActionContext =
                         mockStatic(ServletActionContext.class)) {
                servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
                servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

                Fax2Action action = eFormAction();
                assertThatThrownBy(action::prepareFax)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("cache unavailable");
            }

            assertThat(Files.exists(stagedPath)).isFalse();
        } finally {
            Files.deleteIfExists(stagedPath);
            Files.deleteIfExists(testRoot);
        }
    }

    private static Fax2Action eFormAction() {
        Fax2Action action = new Fax2Action();
        action.setTransactionType("eform");
        action.setTransactionId(42);
        action.setDemographicNo(123);
        return action;
    }
}
