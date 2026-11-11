/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentReviewServiceUnitTest extends CarlosUnitTestBase {
    private Document stored;
    private DocumentDao documents;
    private DocumentExtraReviewerDao extras;
    private LoggedInInfo actor;
    private RecordingTransactionManager transactions;
    private DocumentReviewService service;
    private SecurityInfoManager security;
    private MockedStatic<IncomingDocumentCapacityResponse> access;
    private MockedStatic<EDocUtil> persistence;

    @BeforeEach
    void setup() {
        stored = new Document(); stored.setDocumentNo(42); stored.setStatus('H'); stored.setDocxml("old content");
        stored.setDoctype("Lab"); stored.setDocdesc("Synthetic"); stored.setResponsible("7");
        documents = mock(DocumentDao.class); extras = mock(DocumentExtraReviewerDao.class);
        when(documents.findForPageMutation(42)).thenReturn(stored);
        actor = mock(LoggedInInfo.class); when(actor.getLoggedInProviderNo()).thenReturn("7");
        transactions = new RecordingTransactionManager();
        security = mock(SecurityInfoManager.class);
        service = new DocumentReviewService(documents, extras, mock(CtlDocumentDao.class), security, transactions);
        access = mockStatic(IncomingDocumentCapacityResponse.class);
        persistence = mockStatic(EDocUtil.class);
    }
    @AfterEach void close() { persistence.close(); access.close(); }

    private EDoc edit(String text) {
        EDoc edit = new EDoc("Synthetic", "Lab", "", text, "7", "7", "", 'H', "", "forged", "2000/01/01 00:00:00", "provider", "7");
        edit.setDocId("42"); return edit;
    }
    private DocumentReviewService.Outcome save(EDoc edit, boolean review) throws Exception {
        return service.edit(actor, edit, review, false, true, DocumentEditRevision.content(stored), DocumentEditRevision.metadata(stored));
    }

    @Test void shouldUseSessionActor_whenPostedReviewerIsForged() throws Exception {
        EDoc edit = edit("old content");
        assertThat(save(edit, true)).isEqualTo(DocumentReviewService.Outcome.FIRST);
        assertThat(edit.getReviewerId()).isEqualTo("7");
        assertThat(edit.getReviewDateTime()).doesNotStartWith("2000");
        assertThat(transactions.commits).isEqualTo(1);
        logActionMock.verify(() -> LogAction.addLogStrict(eq(actor), eq("reviewed"), anyString(), eq("42"), isNull(), eq("")));
    }
    @Test void shouldArchiveOldReviewsAndStartANewAttestation_whenContentChanges() throws Exception {
        stored.setReviewer("8"); stored.setReviewdatetime(new Date(1_700_000_000_000L));
        var prior = new DocumentExtraReviewer(); prior.setId(91); prior.setDocumentNo(42); prior.setReviewerProviderNo("7");
        when(extras.findByDocumentNo(42)).thenReturn(List.of(prior));
        EDoc edit = edit("replacement");
        assertThat(save(edit, true)).isEqualTo(DocumentReviewService.Outcome.FIRST);
        assertThat(edit.getReviewerId()).isEqualTo("7");
        verify(extras).remove(91);
        verify(documents).retireCurrentReviewRows(42);
        verify(extras, never()).persist(any());
        logActionMock.verify(() -> LogAction.addLogStrict(eq(actor), eq("REVIEW_INVALIDATED"), anyString(), eq("42"), isNull(), anyString()), times(2));
    }
    @Test void shouldRejectAStaleContentBaseline_beforeAnyPersistence() throws Exception {
        String observed = DocumentEditRevision.content(stored);
        stored.setDocxml("changed by another editor");
        assertThatThrownBy(() -> service.edit(actor, edit("old content"), true, false, true, observed, ""))
                .isInstanceOf(DocumentReviewService.SaveFailure.class).hasCauseInstanceOf(DocumentReviewService.Conflict.class);
        persistence.verify(() -> EDocUtil.editDocumentSQL(any(), anyBoolean()), never());
        verify(extras, never()).persist(any());
        assertThat(transactions.rollbacks).isEqualTo(1);
    }
    @Test void shouldPreserveFirstReviewAndAvoidDuplicates_whenOnlyReviewStateChanged() throws Exception {
        String observed = DocumentEditRevision.content(stored);
        stored.setReviewer("8"); stored.setReviewdatetime(new Date());
        EDoc edit = edit("old content");
        assertThat(service.edit(actor, edit, true, false, true, observed, DocumentEditRevision.metadata(stored)))
                .isEqualTo(DocumentReviewService.Outcome.ADDITIONAL);
        assertThat(edit.getReviewerId()).isEqualTo("8");
        verify(extras).persist(argThat(r -> r instanceof DocumentExtraReviewer x && x.getReviewerProviderNo().equals("7")));
    }
    @Test void shouldRollback_whenStrictAuditFails() throws Exception {
        logActionMock.when(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(), nullable(String.class), anyString()))
                .thenThrow(new IllegalStateException("synthetic audit failure"));
        EDoc edit = edit("old content"); String content = DocumentEditRevision.content(stored);
        assertThatThrownBy(() -> service.edit(actor, edit, true, false, true, content, ""))
                .isInstanceOf(DocumentReviewService.SaveFailure.class)
                .satisfies(f -> assertThat(((DocumentReviewService.SaveFailure) f).rollbackConfirmed()).isTrue());
        assertThat(transactions.commits).isZero(); assertThat(transactions.rollbacks).isEqualTo(1);
    }
    @Test void shouldReportAnUncertainOutcome_whenCommitCannotBeConfirmed() throws Exception {
        transactions.failCommit = true;
        EDoc edit = edit("old content"); String content = DocumentEditRevision.content(stored);
        assertThatThrownBy(() -> service.edit(actor, edit, true, false, true, content, ""))
                .isInstanceOf(DocumentReviewService.SaveFailure.class)
                .satisfies(f -> assertThat(((DocumentReviewService.SaveFailure) f).rollbackConfirmed()).isFalse());
    }

    @Test void shouldKeepNewerMetadata_whenAReviewSubmitsUnchangedOldFields() throws Exception {
        String observed = DocumentEditRevision.metadata(stored);
        EDoc edit = edit("old content");
        stored.setDocdesc("New title");
        service.edit(actor, edit, true, false, true, DocumentEditRevision.content(stored), observed);
        assertThat(edit.getDescription()).isEqualTo("New title");
    }

    @Test void shouldRefuseConflictingMetadataEdits_withoutAnyPersistence() throws Exception {
        String observed = DocumentEditRevision.metadata(stored);
        EDoc edit = edit("old content"); edit.setDescription("My new title");
        stored.setDocdesc("Another title");
        assertThatThrownBy(() -> service.edit(actor, edit, true, false, true, DocumentEditRevision.content(stored), observed))
                .hasCauseInstanceOf(DocumentReviewService.Conflict.class);
        persistence.verify(() -> EDocUtil.editDocumentSQL(any(), anyBoolean()), never());
    }

    @Test void shouldArchiveTheCurrentRevision_whenAPlainUpdateHasAnOldBaseline() throws Exception {
        String observed = DocumentEditRevision.content(stored);
        stored.setDocxml("newer content"); stored.setReviewer("8"); stored.setReviewdatetime(new Date());
        service.edit(actor, edit("newer content"), false, false, true, observed, "");
        logActionMock.verify(() -> LogAction.addLogStrict(eq(actor), eq("REVIEW_INVALIDATED"), anyString(), eq("42"), isNull(),
                contains("contentRevision=" + DocumentEditRevision.digest("newer content"))));
    }

    @Test void shouldUseTheGeneratedIdAndSessionActor_whenCreatingAnInitiallyReviewedHtmlDocument() {
        when(security.hasPrivilege(actor, "_edoc", "w", (String) null)).thenReturn(true);
        persistence.when(() -> EDocUtil.addDocumentSQL(any())).thenReturn("84");
        var created = new Document(84); when(documents.findForPageMutation(84)).thenReturn(created);
        assertThat(service.create(actor, edit("created content"), true)).isEqualTo("84");
        assertThat(created.getReviewer()).isEqualTo("7");
        assertThat(created.getReviewdatetime()).isCloseTo(new Date(), 2000);
        logActionMock.verify(() -> LogAction.addLogStrict(eq(actor), eq("reviewed"), anyString(), eq("84"), isNull(), eq("")));
    }

    @Test void shouldRefuseTheHtmlEditor_whenTheStoredDocumentIsAFile() {
        stored.setStatus('A');
        assertThatThrownBy(() -> service.edit(actor, edit("unseen inline content"), true, false, true, "", ""))
                .hasCauseInstanceOf(DocumentReviewService.Conflict.class);
        persistence.verify(() -> EDocUtil.editDocumentSQL(any(), anyBoolean()), never());
    }
}
