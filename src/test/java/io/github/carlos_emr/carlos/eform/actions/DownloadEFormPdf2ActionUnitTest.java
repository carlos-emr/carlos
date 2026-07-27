/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import java.lang.reflect.Field;
import java.nio.file.Path;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the render-only retry used to approve a download the completeness gate refused.
 *
 * @since 2026-07-26
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("eForm approved-download retry")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class DownloadEFormPdf2ActionUnitTest {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private DocumentAttachmentManager documentAttachmentManager;
    @Mock
    private EFormRenderApprovalService renderApprovalService;
    @Mock
    private LoggedInInfo loggedInInfo;
    @Mock
    private io.github.carlos_emr.carlos.managers.DemographicManager demographicManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DownloadEFormPdf2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        lenient().when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        lenient().when(demographicManager.getDemographicFormattedName(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("Doe, Jane");
        action = new DownloadEFormPdf2Action(
                securityInfoManager, documentAttachmentManager, renderApprovalService, demographicManager);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should never depend on the eForm save manager, so an approval cannot duplicate the record")
    void shouldNotDependOnSaveManager_soApprovalCannotDuplicateTheRecord() throws Exception {
        // The reason this route exists. AddEForm2Action calls saveEformData, which persists a NEW
        // eForm on every submit, so approving a render by re-posting the original form would
        // duplicate the saved clinical record. Holding no reference to the save manager makes that
        // impossible here by construction rather than by care.
        for (Field field : DownloadEFormPdf2Action.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(EformDataManager.class);
        }
    }

    @Test
    @DisplayName("should reject a non-POST retry so an approval cannot be replayed from a link")
    void shouldRejectNonPost_soApprovalCannotBeReplayed() {
        request.setMethod("GET");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
    }

    @Test
    @DisplayName("should require demographic-scoped eForm read access")
    void shouldRequireDemographicScopedEformRead_beforeRendering() {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should reject a malformed identifier rather than rendering")
    void shouldRejectMalformedIdentifier_ratherThanRendering() {
        request.setParameter("fdid", "not-a-number");
        request.setParameter("demographicNo", "123");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should consume the download approval and render the already-saved form with it")
    void shouldConsumeDownloadApproval_andRenderSavedFormWithIt() throws Exception {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");
        request.setParameter("renderApproval", "ticket");
        EFormRenderApproval approval = org.mockito.Mockito.mock(EFormRenderApproval.class);
        when(renderApprovalService.consume(request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.DOWNLOAD, "ticket")).thenReturn(approval);
        when(documentAttachmentManager.renderEFormPacketWithCompleteness(eq(request), any(), eq(approval)))
                .thenReturn(new EformDataManager.EformPdfRender(
                        Path.of("eform-browser-render-1.pdf"),
                        io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport.complete()));
        when(documentAttachmentManager.convertPDFToBase64(any())).thenReturn("QUJD");

        String result = action.execute();

        assertThat(result).isEqualTo("download");
        assertThat(request.getAttribute("eFormPDF")).isEqualTo("QUJD");
        // The ticket is scoped to DOWNLOAD: a PREVIEW or FAX ticket must not unlock a download.
        verify(renderApprovalService).consume(request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.DOWNLOAD, "ticket");
        verify(documentAttachmentManager).renderEFormPacketWithCompleteness(eq(request), any(), eq(approval));
    }

    @Test
    @DisplayName("should reject a lowercase method token rather than case-folding it")
    void shouldRejectLowercaseMethodToken_ratherThanCaseFolding() {
        // HTTP method tokens are case-sensitive uppercase ASCII (RFC 9110 section 9.1), and the
        // check is an allow-list, so only an exact "POST" proceeds. Case-folding a value that gates
        // a security decision is the pattern IMPROPER_UNICODE warns about.
        request.setMethod("post");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager);
    }

    @Test
    @DisplayName("should still reject a lowercase read verb after the allow-list conversion")
    void shouldStillRejectLowercaseReadVerb_afterAllowListConversion() {
        // The regression guard for this change. The previous deny-list matched GET/HEAD
        // case-insensitively; had it been converted to exact comparison instead of to an
        // allow-list, "get" would have stopped matching and fallen through to the action body.
        request.setMethod("get");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager);
    }
}
