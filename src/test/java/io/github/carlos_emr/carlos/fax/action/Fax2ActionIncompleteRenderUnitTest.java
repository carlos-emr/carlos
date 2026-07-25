/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 1, true, true);

        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(faxManager.getFaxGatewayAccounts(loggedInInfo))
                .thenReturn(List.of(mock(FaxConfig.class)));
        when(documentAttachmentManager.renderEFormWithAttachments(
                eq(request), eq(response), isNull(EFormRenderApproval.class)))
                .thenThrow(new EformContentUnavailableException("incomplete", 42, report));
        when(approvalService.issue(
                eq(request), eq(loggedInInfo), eq(42), eq("123"),
                eq(EFormRenderApprovalService.Operation.FAX), eq(report),
                isNull(EFormRenderApproval.class), eq(42)))
                .thenReturn("exact-approval-token");

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EFormRenderApprovalService.class, approvalService);

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
        verify(approvalService).issue(
                request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.FAX, report, null, 42);
    }

    @Test
    @DisplayName("should reject an invalid or expired incomplete-render approval before rendering")
    void shouldRejectInvalidApproval_beforeRenderingEForm() throws Exception {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EFormRenderApprovalService approvalService = mock(EFormRenderApprovalService.class);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setParameter("renderApproval", "forged-or-expired");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(faxManager.getFaxGatewayAccounts(loggedInInfo))
                .thenReturn(List.of(mock(FaxConfig.class)));
        when(approvalService.consume(
                request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.FAX, "forged-or-expired"))
                .thenReturn(null);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EFormRenderApprovalService.class, approvalService);

        try (MockedStatic<ServletActionContext> servletActionContext =
                     mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = eFormAction();

            assertThat(action.prepareFax()).isEqualTo(Fax2Action.NONE);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).contains("invalid or expired");
        verify(documentAttachmentManager, never()).renderEFormWithAttachments(
                any(), any(), any());
    }

    private static Fax2Action eFormAction() {
        Fax2Action action = new Fax2Action();
        action.setTransactionType("eform");
        action.setTransactionId(42);
        action.setDemographicNo(123);
        return action;
    }
}
