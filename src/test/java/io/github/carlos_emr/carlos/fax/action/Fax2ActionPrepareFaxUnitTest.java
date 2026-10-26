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
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the POST side of {@link Fax2Action#prepareFax()}: the request the saved-eForm
 * handoff ({@code EFormFaxPreparation.jsp}) submits after a protected eForm save.
 *
 * <p>The handoff carries only saved-form references ({@code transactionId},
 * {@code demographicNo}) and the optional recipient fields, never the eForm body. These tests pin
 * what preparation does with them once the request is a POST: a complete render reaches the cover
 * page with every reference carried through and the staged PDF claimed for this session only;
 * a patient reference that no longer matches the saved eForm is refused before anything is
 * rendered; and a site with no active fax account gets an honest 503 instead of the
 * {@code errorpage.jsp} "CARLOS Error: 0" page. The non-POST rejection itself is pinned by
 * {@link Fax2ActionMethodGateUnitTest}.</p>
 *
 * @since 2026-05-29
 */
@DisplayName("Fax2Action prepareFax POST handoff")
@Tag("unit")
@Tag("fax")
class Fax2ActionPrepareFaxUnitTest extends CarlosUnitTestBase {

    private static final int SAVED_FDID = 42;
    private static final int PATIENT = 123;
    private static final String PROVIDER = "999998";

    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private EFormRenderApprovalService approvalService;
    private EFormDataDao eFormDataDao;
    private LoggedInInfo user;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        faxManager = createAndRegisterMock(FaxManager.class);
        documentAttachmentManager = createAndRegisterMock(DocumentAttachmentManager.class);
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        approvalService = createAndRegisterMock(EFormRenderApprovalService.class);
        eFormDataDao = createAndRegisterMock(EFormDataDao.class);

        // The handoff form posts to the action URL with the saved-form references in the query
        // string and only the CSRF token in the body; Struts binds both into the action fields.
        request = new MockHttpServletRequest("POST", "/fax/faxAction");
        request.setParameter("method", "prepareFax");
        response = new MockHttpServletResponse();

        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn(PROVIDER);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        when(securityInfoManager.hasPrivilege(eq(user), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(user, "_eform", SecurityInfoManager.READ, String.valueOf(PATIENT)))
                .thenReturn(true);
        when(faxManager.getFaxGatewayAccounts(user)).thenReturn(List.of(new FaxConfig()));

        EFormData saved = new EFormData();
        saved.setDemographicId(PATIENT);
        when(eFormDataDao.find(SAVED_FDID)).thenReturn(saved);
    }

    /** The action exactly as the saved-eForm handoff binds it. */
    private Fax2Action handoffAction(int demographicNo) {
        Fax2Action action = new Fax2Action();
        action.setTransactionType(TransactionType.EFORM.name());
        action.setTransactionId(SAVED_FDID);
        action.setDemographicNo(demographicNo);
        action.setRecipient("Dr & Smith");
        action.setRecipientFaxNumber("5551234567");
        action.setLetterheadFax("clinic");
        return action;
    }

    private String execute(Supplier<Fax2Action> actionFactory) {
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return actionFactory.get().execute();
        }
    }

    @Test
    @DisplayName("should open the cover page with every saved-form reference when the handoff POST renders completely")
    void shouldRenderCoverPagePreview_whenHandoffPostRendersCompletely() throws Exception {
        Path staged = Path.of("staged-eform.pdf");
        EFormRenderCompletenessReport complete = EFormRenderCompletenessReport.complete();
        when(documentAttachmentManager.stageEFormPacketForFaxPreview(eq(request), eq(response), any()))
                .thenReturn(new EformDataManager.EformPdfRender(staged, complete, Map.of(SAVED_FDID, complete)));

        String result = execute(() -> handoffAction(PATIENT));

        assertThat(result).isEqualTo("preview");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.isCommitted()).isFalse();
        assertThat(request.getAttribute("faxFilePath")).isEqualTo(staged);
        assertThat(request.getAttribute("documents")).isEqualTo(List.of(staged));
        assertThat(request.getAttribute("transactionType")).isEqualTo(TransactionType.EFORM.name());
        assertThat(request.getAttribute("transactionId")).isEqualTo(SAVED_FDID);
        assertThat(request.getAttribute("demographicNo")).isEqualTo(PATIENT);
        // CoverPage.jsp reads the recipient under these names, not the request parameter names.
        assertThat(request.getAttribute("professionalSpecialistName")).isEqualTo("Dr & Smith");
        assertThat(request.getAttribute("fax")).isEqualTo("5551234567");
        assertThat(request.getAttribute("letterheadFax")).isEqualTo("clinic");
        assertThat(request.getAttribute("advisoryIssues")).isEqualTo(0);

        // The staged PDF is claimable only by this session, for this eForm, patient and provider:
        // queue() later refuses any faxFilePath that this preparation did not hand out.
        @SuppressWarnings("unchecked")
        Map<String, Fax2Action.FaxPreviewClaim> claims = (Map<String, Fax2Action.FaxPreviewClaim>)
                request.getSession().getAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY);
        assertThat(claims).containsOnly(
                Map.entry(staged.toString(), new Fax2Action.FaxPreviewClaim(SAVED_FDID, PATIENT, PROVIDER)));

        verify(approvalService).stagedFaxPreviewApproval(request, user, SAVED_FDID, String.valueOf(PATIENT));
        verify(documentAttachmentManager, times(1)).stageEFormPacketForFaxPreview(eq(request), eq(response), any());
    }

    @Test
    @DisplayName("should refuse the handoff before rendering when it names a patient the saved eForm does not belong to")
    void shouldRefuseBeforeRendering_whenHandoffPatientDiffersFromSavedEForm() throws Exception {
        int otherPatient = 456;

        String result = execute(() -> handoffAction(otherPatient));

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).isEqualTo("The eForm no longer belongs to this patient");
        // Any preview staged for the stale tuple is revoked rather than left claimable.
        verify(approvalService).cancelStagedFaxPreview(request, user, SAVED_FDID, String.valueOf(otherPatient), null);
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_eform"), anyString(), anyString());
        verify(documentAttachmentManager, never()).stageEFormPacketForFaxPreview(any(), any(), any());
        assertThat(request.getSession().getAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("should answer 503 with a configuration message when no fax account is active")
    void shouldReturnServiceUnavailable_whenNoActiveFaxAccount() {
        when(faxManager.getFaxGatewayAccounts(user)).thenReturn(List.of());

        String result = execute(() -> handoffAction(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertThat(response.getErrorMessage()).contains("No active fax accounts are configured");
        verifyNoInteractions(documentAttachmentManager, eFormDataDao, approvalService);
    }

    @Test
    @DisplayName("should reject a handoff without the saved eForm reference before any lookup or render")
    void shouldRejectBadRequest_whenSavedEFormReferenceIsMissing() {
        String result = execute(() -> {
            Fax2Action action = handoffAction(PATIENT);
            action.setTransactionId(null);
            return action;
        });

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(documentAttachmentManager, eFormDataDao, approvalService);
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_eform"), anyString(), anyString());
    }
}
