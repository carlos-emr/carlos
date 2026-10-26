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
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
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
@DisplayName("eForm approved eDoc archive retry")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class SaveEFormAsEDoc2ActionUnitTest {

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
    private EFormDataDao eFormDataDao;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SaveEFormAsEDoc2Action action;

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
        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(123);
        lenient().when(eFormDataDao.find(42)).thenReturn(eFormData);
        action = new SaveEFormAsEDoc2Action(
                securityInfoManager, documentAttachmentManager, renderApprovalService, eFormDataDao);
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
        // impossible here by construction rather than by care. The eDoc itself is bounded by the
        // one-time approval ticket: consume() removes it, so one ticket yields at most one document.
        for (Field field : SaveEFormAsEDoc2Action.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(EformDataManager.class);
        }
    }

    @Test
    @DisplayName("should reject GET before any document is created")
    void shouldRejectGet_beforeAnyDocumentIsCreated() throws Exception {
        // This retry is a mutator: it archives a document. GET must be refused before the side
        // effect, per the project's mutator contract.
        request.setMethod("GET");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager);
    }

    @Test
    @DisplayName("should require demographic-scoped eForm update access")
    void shouldRequireDemographicScopedEformUpdate_beforeArchiving() {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, "123"))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should reject a malformed identifier rather than rendering")
    void shouldRejectMalformedIdentifier_ratherThanArchiving() {
        request.setParameter("fdid", "not-a-number");
        request.setParameter("demographicNo", "123");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should reject a patient number that does not match the saved eForm")
    void shouldRejectMismatchedDemographic_beforePrivilegeOrArchiving() {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "456");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager, renderApprovalService);
    }

    @Test
    @DisplayName("should require a valid approval before archiving")
    void shouldRequireApproval_beforeArchiving() {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");

        assertThat(action.execute()).isEqualTo("error");
        assertThat(request.getAttribute("errorMessage")).isNotNull();
        org.mockito.Mockito.verifyNoInteractions(renderApprovalService, documentAttachmentManager);
    }

    @Test
    @DisplayName("should reject an invalid or replayed approval before archiving")
    void shouldRejectInvalidApproval_beforeArchiving() {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");
        request.setParameter("renderApproval", "spent-or-invalid-ticket");

        assertThat(action.execute()).isEqualTo("error");
        verify(renderApprovalService).consume(request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.EDOC, "spent-or-invalid-ticket");
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager);
    }

    @Test
    @DisplayName("should consume the eDoc approval and archive the already-saved form with it")
    void shouldConsumeEDocApproval_andArchiveSavedFormWithIt() throws Exception {
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");
        request.setParameter("renderApproval", "ticket");
        EFormRenderApproval approval = org.mockito.Mockito.mock(EFormRenderApproval.class);
        when(renderApprovalService.consume(request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.EDOC, "ticket")).thenReturn(approval);
        when(documentAttachmentManager.saveEFormAsEDoc(eq(request), any(), eq(approval))).thenReturn(7);

        String result = action.execute();

        assertThat(result).isEqualTo("close");
        // Scoped to EDOC: a DOWNLOAD, PREVIEW or FAX ticket must not unlock an archive.
        verify(renderApprovalService).consume(request, loggedInInfo, 42, "123",
                EFormRenderApprovalService.Operation.EDOC, "ticket");
        verify(documentAttachmentManager).saveEFormAsEDoc(eq(request), any(), eq(approval));
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
