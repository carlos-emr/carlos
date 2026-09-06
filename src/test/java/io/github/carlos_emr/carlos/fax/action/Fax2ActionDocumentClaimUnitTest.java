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

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the session claim on DOCUMENT fax promotion.
 *
 * <p>Before this claim existed, {@code queue()} consumed a claim only for eForms. A DOCUMENT
 * fax therefore accepted whatever {@code faxFilePath} the cover-page form carried, and the
 * circle-of-care check ran against a {@code demographicNo} the same form supplied, with
 * nothing tying the two together. A user with fax rights could pair their own patient's
 * number with a path to another patient's stored document and transmit it.
 *
 * <p>These cases lock the fix: a path this session did not stage is refused, and the
 * refusal happens before any fax job is persisted.
 */
@DisplayName("Fax2Action DOCUMENT claim enforcement unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionDocumentClaimUnitTest extends CarlosUnitTestBase {

    /** The document under test, and the patient its ctl_document row links it to. */
    private static final int DOCUMENT_NO = 31;
    private static final int DEMOGRAPHIC_NO = 42;

    private static final String APP_TEMP_ROOT =
            Paths.get(System.getProperty("java.io.tmpdir"), "carlos-temp").toString();

    private FaxManager faxManager;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private void setUpCommonMocks() {
        faxManager = mock(FaxManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("w"), isNull()))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), any()))
                .thenReturn(true);

        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, mock(DocumentAttachmentManager.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);
        // queue() re-derives the document's patient from the row before promoting it, so the
        // DAO has to answer even on the paths that only exercise the path claim.
        registerMock(DocumentDao.class, documentDaoLinking(DOCUMENT_NO, DEMOGRAPHIC_NO));
    }

    /** A DocumentDao whose one document is linked to {@code demographicNo} via ctl_document. */
    private static DocumentDao documentDaoLinking(int documentNo, int demographicNo) {
        Document document = new Document();
        document.setDocumentNo(documentNo);
        document.setDocfilename("staged.pdf");
        document.setContenttype("application/pdf");
        // EDocUtil.getDoc unboxes this, so it must be present even though nothing here reads it.
        document.setNumberofpages(1);

        CtlDocument ctl = new CtlDocument();
        CtlDocumentPK pk = new CtlDocumentPK();
        pk.setModule("demographic");
        pk.setModuleId(demographicNo);
        pk.setDocumentNo(documentNo);
        ctl.setId(pk);

        DocumentDao dao = mock(DocumentDao.class);
        when(dao.findCtlDocsAndDocsByDocNo(documentNo))
                .thenReturn(List.<Object[]>of(new Object[]{document, ctl}));
        return dao;
    }

    private Fax2Action documentFaxAction(String faxFilePath) {
        return documentFaxAction(faxFilePath, DEMOGRAPHIC_NO);
    }

    private Fax2Action documentFaxAction(String faxFilePath, int submittedDemographicNo) {
        Fax2Action action = new Fax2Action();
        action.setTransactionType("DOCUMENT");
        action.setTransactionId(DOCUMENT_NO);
        action.setDemographicNo(submittedDemographicNo);
        action.setRecipientFaxNumber("1234567890");
        action.setFaxFilePath(faxFilePath);
        return action;
    }

    @Test
    @DisplayName("should reject queue when the document path was not staged by this session")
    void shouldRejectQueue_whenDocumentPathUnclaimed() throws Exception {
        setUpCommonMocks();
        Files.createDirectories(Paths.get(APP_TEMP_ROOT));
        Path unclaimed = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "not-staged-", ".pdf");

        // The session holds no claim at all: this is the "client named its own path" case.
        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = documentFaxAction(unclaimed.toString());

            assertThatThrownBy(action::queue)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Unclaimed fax file path");
        } finally {
            Files.deleteIfExists(unclaimed);
        }

        verify(faxManager, never()).persistAndLogFaxJobs(any(), anyMap(), any(), any());
    }

    @Test
    @DisplayName("should reject queue when the submitted path is not the one this session staged")
    void shouldRejectQueue_whenDocumentPathIsAnotherFile() throws Exception {
        setUpCommonMocks();
        Files.createDirectories(Paths.get(APP_TEMP_ROOT));
        Path staged = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "staged-", ".pdf");
        Path substituted = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "substituted-", ".pdf");

        // A claim exists, but the form carries a different file: the substitution case.
        request.getSession(true).setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY,
                new HashSet<>(List.of(staged.toString())));

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = documentFaxAction(substituted.toString());

            assertThatThrownBy(action::queue).isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(staged);
            Files.deleteIfExists(substituted);
        }

        verify(faxManager, never()).persistAndLogFaxJobs(any(), anyMap(), any(), any());
    }

    @Test
    @DisplayName("should consume the claim when the staged document path matches")
    void shouldConsumeClaim_whenDocumentPathMatches() throws Exception {
        setUpCommonMocks();
        when(faxManager.persistAndLogFaxJobs(any(), anyMap(), any(), any())).thenReturn(List.of());
        when(faxManager.getFaxGatewayAccounts(any())).thenReturn(List.of());
        Files.createDirectories(Paths.get(APP_TEMP_ROOT));
        Path staged = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "staged-match-", ".pdf");

        request.getSession(true).setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY,
                new HashSet<>(List.of(staged.toString())));

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            documentFaxAction(staged.toString()).queue();
        } finally {
            Files.deleteIfExists(staged);
        }

        verify(faxManager).persistAndLogFaxJobs(any(), anyMap(), any(), any());

        // Single use: the claim is spent, so replaying the same cover-page submission cannot
        // queue the document a second time.
        java.util.Collection<?> remaining = (java.util.Collection<?>) request.getSession(true)
                .getAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY);
        assertThat(remaining == null || !remaining.contains(staged.toString()))
                .as("the claim is single use, so a replayed submission cannot queue it again")
                .isTrue();
    }

    @Test
    @DisplayName("should reject queue when the cover page substitutes a different patient")
    void shouldRejectQueue_whenSubmittedDemographicIsSubstituted() throws Exception {
        setUpCommonMocks();
        Files.createDirectories(Paths.get(APP_TEMP_ROOT));
        Path staged = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "staged-rebind-", ".pdf");

        // The path claim is genuine: this session really did stage this file. What is forged is
        // the patient the cover-page form submits with it. Without re-deriving the binding from
        // the document row, the fax would be filed against a chart it does not belong to.
        request.getSession(true).setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY,
                new HashSet<>(List.of(staged.toString())));

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = documentFaxAction(staged.toString(), DEMOGRAPHIC_NO + 1);

            assertThatThrownBy(action::queue)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("no longer belongs to this patient");
        } finally {
            Files.deleteIfExists(staged);
        }

        verify(faxManager, never()).persistAndLogFaxJobs(any(), anyMap(), any(), any());
    }

    @Test
    @DisplayName("should reject queue when the submitted document number resolves to nothing")
    void shouldRejectQueue_whenDocumentNumberIsUnknown() throws Exception {
        setUpCommonMocks();
        Files.createDirectories(Paths.get(APP_TEMP_ROOT));
        Path staged = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "staged-unknown-", ".pdf");

        // EDocUtil.getDoc never returns null — it hands back a default-initialised EDoc whose
        // moduleId is "" — so naming a document number that matches no row used to reach the
        // "unlinked document, no binding to contradict" branch and pass. That let a genuine path
        // claim be promoted against an arbitrary demographicNo.
        request.getSession(true).setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY,
                new HashSet<>(List.of(staged.toString())));

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("DOCUMENT");
            action.setTransactionId(DOCUMENT_NO + 9999);
            action.setDemographicNo(DEMOGRAPHIC_NO);
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(staged.toString());

            assertThatThrownBy(action::queue).isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(staged);
        }

        verify(faxManager, never()).persistAndLogFaxJobs(any(), anyMap(), any(), any());
    }

    @Test
    @DisplayName("should reject queue when the document number is omitted entirely")
    void shouldRejectQueue_whenDocumentNumberIsAbsent() throws Exception {
        setUpCommonMocks();
        Files.createDirectories(Paths.get(APP_TEMP_ROOT));
        Path staged = Files.createTempFile(Paths.get(APP_TEMP_ROOT), "staged-absent-", ".pdf");

        // prepareFax requires transactionId; queue() is a separate request whose parameters the
        // client re-supplies. Simply dropping the hidden field skipped the re-binding check.
        request.getSession(true).setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY,
                new HashSet<>(List.of(staged.toString())));

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("DOCUMENT");
            action.setDemographicNo(DEMOGRAPHIC_NO);
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(staged.toString());

            assertThatThrownBy(action::queue).isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(staged);
        }

        verify(faxManager, never()).persistAndLogFaxJobs(any(), anyMap(), any(), any());
    }
}
