/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.clinical.summary.*;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Exercises real review state and exact synthetic bytes behind mocked request/authorization readers. */
@Tag("unit")
class AiReviewedDocumentSummaryActionUnitTest {
    private static final String TEXT = "FAKE document. Repeat blood work was planned.";
    private static final DocumentSummaryReviewBinding.Scope SCOPE = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 7, 11);
    private static final DocumentSummaryReviewBinding.Metadata META = new DocumentSummaryReviewBinding.Metadata("FAKE.txt", "text/plain", "FAKE document", 1234L);
    private static final String GENERATION_KEY = AiReviewedDocumentSummary2Action.class.getName() + ".generation";
    private static final String AUDIT = AiReviewedDocumentSummary2Action.AUDIT_CONTENT;
    private final DocumentSummaryReviewContext context = mock(DocumentSummaryReviewContext.class);
    private final DocumentSummaryService generator = mock(DocumentSummaryService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final LoggedInInfo actor = actor(71);
    private final Map<String, Object> state = new HashMap<>();
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> authentication;
    private MockedStatic<DocumentSummaryReviewContext> enabled;
    private MockedStatic<LogAction> audit;
    private AiReviewedDocumentSummary2Action action;

    private static LoggedInInfo actor(int login) {
        var actor = mock(LoggedInInfo.class); var provider = mock(Provider.class); var security = mock(Security.class);
        when(provider.getProviderNo()).thenReturn("FAKE-provider"); when(provider.getFormattedName()).thenReturn("FAKE Clinician");
        when(security.getSecurityNo()).thenReturn(login);
        when(actor.getLoggedInProvider()).thenReturn(provider); when(actor.getLoggedInSecurity()).thenReturn(security);
        return actor;
    }
    @BeforeEach void setUp() throws Exception {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
        authentication = mockStatic(LoggedInInfo.class);
        authentication.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
        enabled = mockStatic(DocumentSummaryReviewContext.class); enabled.when(DocumentSummaryReviewContext::enabled).thenReturn(true);
        audit = mockStatic(LogAction.class);
        when(request.getMethod()).thenReturn("GET"); when(request.getSession(false)).thenReturn(session);
        when(request.getParameterValues("documentId")).thenReturn(new String[]{"11"});
        when(session.getAttribute(anyString())).thenAnswer(invocation -> state.get(invocation.getArgument(0)));
        doAnswer(invocation -> { state.put(invocation.getArgument(0), invocation.getArgument(1)); return null; }).when(session).setAttribute(anyString(), any());
        doAnswer(invocation -> { state.remove(invocation.getArgument(0)); return null; }).when(session).removeAttribute(anyString());
        when(context.load(any(LoggedInInfo.class), eq(11))).thenReturn(new DocumentSummaryReviewContext.Snapshot(SCOPE, META));
        when(context.read(any())).thenAnswer(ignored -> bytes()); when(context.currentSource(any())).thenAnswer(ignored -> source());
        var generatedSummary = summary();
        when(generator.summarize(TEXT)).thenReturn(generatedSummary);
        action = new AiReviewedDocumentSummary2Action(context, generator, "FAKE-model@r1");
    }
    @AfterEach void close() {
        Object current = state.get(DocumentSummaryReviewSession.SESSION_KEY);
        if (current instanceof DocumentSummaryReviewSession review) review.close();
        if (audit != null) audit.close();
        if (enabled != null) enabled.close();
        if (authentication != null) authentication.close();
        if (servlet != null) servlet.close();
    }
    private static DocumentSummary summary() {
        var result = mock(DocumentSummary.class);
        when(result.overview()).thenReturn("FAKE document.");
        when(result.points()).thenReturn(List.of(Map.of("text", "Repeat blood work was planned.",
                "evidence", List.of("Repeat blood work was planned."))));
        return result;
    }
    private static byte[] bytes() { return TEXT.getBytes(StandardCharsets.UTF_8); }
    private static DocumentSummaryReviewBinding.Source source() { return DocumentSummaryReviewBinding.capture(SCOPE, META, bytes()); }
    private DocumentSummaryReviewSession current() { return (DocumentSummaryReviewSession) state.get(DocumentSummaryReviewSession.SESSION_KEY); }
    private DocumentSummaryReviewSession generate() throws Exception {
        when(request.getMethod()).thenReturn("POST"); assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS); return current();
    }
    private void token(DocumentSummaryReviewSession review) {
        String draftId = review.view(actor, SCOPE, source()).draftId().toString();
        when(request.getParameterValues("draftId")).thenReturn(new String[]{draftId});
    }
    private void approve(DocumentSummaryReviewSession review) throws Exception {
        when(request.getMethod()).thenReturn("POST"); token(review);
        when(request.getParameterValues("affirmReview")).thenReturn(new String[]{"true"});
        assertThat(action.approve()).isEqualTo(ActionSupport.SUCCESS);
    }
    @Test void disabledAnswers404BeforeMethodAuthOrReaders() throws Exception {
        enabled.when(DocumentSummaryReviewContext::enabled).thenReturn(false);
        assertThat(action.approve()).isEqualTo(ActionSupport.NONE); verify(response).sendError(404);
        verifyNoInteractions(context, generator); authentication.verifyNoInteractions();
    }
    @Test void mutationsRejectGetBeforeAnySourceOrModelRead() throws Exception {
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        assertThat(action.approve()).isEqualTo(ActionSupport.NONE);
        assertThat(action.discard()).isEqualTo(ActionSupport.NONE);
        verify(response, times(3)).sendError(405); verifyNoInteractions(context, generator);
    }
    @Test void initialViewShowsUnreviewedNoticeWithoutModelOrApproval() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS); verifyNoInteractions(generator);
        verify(request).setAttribute("documentSummaryNotice", DocumentSummaryReviewBinding.unreviewedNotice());
        assertThat(state).doesNotContainKey(DocumentSummaryReviewSession.SESSION_KEY);
    }
    @Test void generationThenPreviewNeverApproveUntilAffirmativePost() throws Exception {
        var review = generate(); assertThat(review).isNotNull();
        when(request.getMethod()).thenReturn("GET"); assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(review.view(actor, SCOPE, source()).approved()).isFalse();
        approve(review); assertThat(review.view(actor, SCOPE, source()).approved()).isTrue();
        verify(request, atLeastOnce()).setAttribute("documentSummaryDeliveryReady", false);
    }
    @Test void missingAffirmationNeverProducesAReviewedLabel() throws Exception {
        var review = generate(); token(review); assertThat(action.approve()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        verify(request, never()).setAttribute("documentSummaryReviewed", true);
    }
    @Test void staleDisplayedTokenCannotApproveOrDiscardNewerDraft() throws Exception {
        var review = generate(); when(request.getParameterValues("draftId")).thenReturn(new String[]{UUID.randomUUID().toString()});
        assertThat(action.approve()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        assertThat(current()).isSameAs(review); assertThat(review.view(actor, SCOPE, source()).approved()).isFalse();
    }
    @Test void changedLoginCannotUseExistingReview() throws Exception {
        var review = generate(); token(review);
        var nextLogin = actor(72);
        authentication.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(nextLogin);
        assertThat(action.approve()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(409);
        assertThat(current()).isNull();
        assertThatThrownBy(() -> review.view(actor, SCOPE, source())).isInstanceOf(IllegalStateException.class);
    }
    @Test void changedLoginDisposesPriorStateBeforeDeniedDocumentReaders() throws Exception {
        var review = generate(); approve(review);
        var nextLogin = actor(72);
        authentication.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(nextLogin);
        when(context.load(nextLogin, 11)).thenThrow(new SecurityException("FAKE denied document"));
        clearInvocations(context);
        assertThat(action.approve()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(409); verifyNoInteractions(context);
        assertThat(current()).isNull();
        assertThatThrownBy(() -> review.view(actor, SCOPE, source())).isInstanceOf(IllegalStateException.class);
    }
    @Test void changedSourceInvalidatesAndRemovesApproval() throws Exception {
        var review = generate(); approve(review);
        when(context.currentSource(any())).thenReturn(DocumentSummaryReviewBinding.capture(SCOPE, META, (TEXT + " Changed.").getBytes(StandardCharsets.UTF_8)));
        when(request.getMethod()).thenReturn("GET"); assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        assertThatThrownBy(() -> review.view(actor, SCOPE, source())).isInstanceOf(IllegalStateException.class);
    }
    @Test void failedRegenerationClearsPriorApprovalAndRetainsOriginalWorkflow() throws Exception {
        var review = generate(); approve(review);
        when(generator.summarize(TEXT)).thenThrow(new ClinicalSummaryGenerationException("FAKE outage"));
        assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS); assertThat(current()).isNull();
        verify(request).setAttribute(eq("documentSummaryError"), contains("Original-only delivery remains available"));
    }
    @Test void staleGenerationFailureDoesNotRemoveNewerSessionsDraft() throws Exception {
        final DocumentSummaryReviewSession[] winner = new DocumentSummaryReviewSession[1];
        var winnerGenerator = mock(DocumentSummaryService.class);
        var winnerSummary = summary();
        when(winnerGenerator.summarize(TEXT)).thenReturn(winnerSummary);
        when(generator.summarize(TEXT)).thenAnswer(ignored -> {
            winner[0] = DocumentSummaryReviewSession.generate(SCOPE, META, AiReviewedDocumentSummaryActionUnitTest::bytes, winnerGenerator, "FAKE-winner", actor);
            state.put(GENERATION_KEY, UUID.randomUUID()); state.put(DocumentSummaryReviewSession.SESSION_KEY, winner[0]);
            throw new ClinicalSummaryGenerationException("FAKE superseded result");
        });
        generate(); assertThat(current()).isSameAs(winner[0]);
        assertThat(current().view(actor, SCOPE, source()).approved()).isFalse();
    }
    @Test void staleDocumentARequestCannotDeleteApprovedDocumentBReview() throws Exception {
        var otherScope = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 8, 12);
        var other = DocumentSummaryReviewSession.generate(otherScope, META, AiReviewedDocumentSummaryActionUnitTest::bytes,
                generator, "FAKE-model", actor);
        var otherSource = DocumentSummaryReviewBinding.capture(otherScope, META, bytes());
        other.approve(actor, otherScope, other.view(actor, otherScope, otherSource).draftId(), otherSource, true);
        state.put(DocumentSummaryReviewSession.SESSION_KEY, other);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        assertThat(current()).isSameAs(other);
        assertThat(other.view(actor, otherScope, otherSource).approved()).isTrue();
    }
    @Test void replacementBetweenGenerationInstallAndViewCannotDeleteApprovedNewerDocument() throws Exception {
        var otherScope = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 8, 12);
        var other = DocumentSummaryReviewSession.generate(otherScope, META, AiReviewedDocumentSummaryActionUnitTest::bytes,
                generator, "FAKE-model", actor);
        var otherSource = DocumentSummaryReviewBinding.capture(otherScope, META, bytes());
        other.approve(actor, otherScope, other.view(actor, otherScope, otherSource).draftId(), otherSource, true);
        final boolean[] installed = {false};
        doAnswer(invocation -> {
            state.put(invocation.getArgument(0), invocation.getArgument(1));
            if (DocumentSummaryReviewSession.SESSION_KEY.equals(invocation.getArgument(0))) installed[0] = true;
            return null;
        }).when(session).setAttribute(anyString(), any());
        when(session.getAttribute(DocumentSummaryReviewSession.SESSION_KEY)).thenAnswer(ignored -> {
            if (installed[0]) {
                var displaced = state.put(DocumentSummaryReviewSession.SESSION_KEY, other);
                if (displaced instanceof DocumentSummaryReviewSession old) old.close();
                installed[0] = false;
            }
            return state.get(DocumentSummaryReviewSession.SESSION_KEY);
        });
        when(request.getMethod()).thenReturn("POST");
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        assertThat(current()).isSameAs(other);
        assertThat(other.view(actor, otherScope, otherSource).approved()).isTrue();
    }
    @Test void duplicateDocumentParametersFailBeforeReaders() throws Exception {
        when(request.getParameterValues("documentId")).thenReturn(new String[]{"11", "12"});
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE); verify(response).sendError(400); verifyNoInteractions(context, generator);
    }
    @Test void browserSuppliedReviewerAndTimeCannotOverrideRecordedIdentity() throws Exception {
        var review = generate(); approve(review);
        try (var bundle = review.forDelivery(actor, SCOPE, review.view(actor, SCOPE, source()).draftId(), source(), true)) {
            assertThat(bundle.approval().reviewerId()).isEqualTo("FAKE-provider");
            verify(request, never()).getParameterValues("reviewerId"); verify(request, never()).getParameterValues("reviewedAt");
        }
    }
    @Test void originalHeadPreservesUnreviewedStatusAndWritesNoBody() throws Exception {
        var review = generate(); token(review); when(request.getMethod()).thenReturn("HEAD");
        assertThat(action.original()).isEqualTo(ActionSupport.NONE); verify(response).setContentLength(bytes().length);
        verify(response, never()).getOutputStream(); assertThat(review.view(actor, SCOPE, source()).approved()).isFalse();
    }
    @Test void originalDownloadUsesCapturedExactBytesWithoutImplicitApproval() throws Exception {
        var review = generate(); token(review); when(request.getMethod()).thenReturn("GET");
        var output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            public void write(int value) { output.write(value); }
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener listener) { }
        });
        assertThat(action.original()).isEqualTo(ActionSupport.NONE); assertThat(output.toByteArray()).isEqualTo(bytes());
        assertThat(review.view(actor, SCOPE, source()).approved()).isFalse();
    }
    private void correction(DocumentSummaryReviewSession review, String overview, String... texts) {
        token(review);
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterValues("overview")).thenReturn(new String[]{overview});
        when(request.getParameterValues("pointText")).thenReturn(texts);
    }
    @Test void correctionRendersNewUnreviewedContentIgnoringPostedAffirmationAndEvidence() throws Exception {
        var review = generate(); approve(review);
        var before = review.view(actor, SCOPE, source());
        correction(review, "FAKE blood work document.", "Blood work was planned.");
        when(request.getParameterValues("affirmReview")).thenReturn(new String[]{"true"});
        when(request.getParameterValues("evidence")).thenReturn(new String[]{"Invented evidence."});
        when(request.getParameterValues("modelIdentity")).thenReturn(new String[]{"IMPOSTOR"});
        clearInvocations(request, generator);
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        var revised = review.view(actor, SCOPE, source());
        assertThat(revised.approved()).isFalse();
        assertThat(revised.draftId()).isNotEqualTo(before.draftId());
        assertThat(revised.generatedAt()).isEqualTo(before.generatedAt());
        assertThat(revised.originalSha256()).isEqualTo(before.originalSha256());
        assertThat(revised.summary().points().get(0).get("evidence")).isEqualTo(List.of("Repeat blood work was planned."));
        verify(request).setAttribute("documentSummaryOverview", "FAKE blood work document.");
        verify(request).setAttribute("documentSummaryDraftId", revised.draftId().toString());
        verify(request).setAttribute("documentSummaryReviewed", false);
        verify(request, never()).getParameterValues("affirmReview");
        verify(request, never()).getParameterValues("evidence");
        verify(request, never()).getParameterValues("modelIdentity");
        verifyNoInteractions(generator);
        approve(review);
        assertThat(review.view(actor, SCOPE, source()).approved()).isTrue();
    }
    @Test void correctionRejectsGetAndRemainsDefaultOffBeforeReaders() throws Exception {
        assertThat(action.revise()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(405); verifyNoInteractions(context, generator);
        when(request.getMethod()).thenReturn("POST");
        enabled.when(DocumentSummaryReviewContext::enabled).thenReturn(false);
        assertThat(action.revise()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(404); verifyNoInteractions(context, generator);
    }
    @Test void staleCorrectionPreservesCurrentApprovalAndNeverReadsPostedContent() throws Exception {
        var review = generate(); approve(review);
        var before = review.view(actor, SCOPE, source());
        when(request.getParameterValues("draftId")).thenReturn(new String[]{UUID.randomUUID().toString()});
        clearInvocations(request);
        assertThat(action.revise()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        assertThat(current()).isSameAs(review);
        assertThat(review.view(actor, SCOPE, source()).version()).isEqualTo(before.version());
        assertThat(review.view(actor, SCOPE, source()).approved()).isTrue();
        verify(request, never()).getParameterValues("pointText");
    }
    @Test void crossDocumentCorrectionCannotModifyOrDiscardNewerApprovedReview() throws Exception {
        var otherScope = new DocumentSummaryReviewBinding.Scope("FAKE-clinic", 8, 12);
        var other = DocumentSummaryReviewSession.generate(otherScope, META, AiReviewedDocumentSummaryActionUnitTest::bytes,
                generator, "FAKE-model", actor);
        var otherSource = DocumentSummaryReviewBinding.capture(otherScope, META, bytes());
        var before = other.view(actor, otherScope, otherSource);
        other.approve(actor, otherScope, before.draftId(), otherSource, true);
        state.put(DocumentSummaryReviewSession.SESSION_KEY, other);
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterValues("draftId")).thenReturn(new String[]{before.draftId().toString()});
        when(request.getParameterValues("removePoint")).thenReturn(new String[]{"0"});
        assertThat(action.revise()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        assertThat(current()).isSameAs(other);
        assertThat(other.view(actor, otherScope, otherSource).version()).isEqualTo(before.version());
        assertThat(other.view(actor, otherScope, otherSource).approved()).isTrue();
        verify(request, never()).getParameterValues("pointText");
    }
    @Test void correctionOnChangedLoginClosesPriorReviewBeforeDocumentReaders() throws Exception {
        var review = generate(); approve(review); correction(review, "FAKE document.", "Blood work planned.");
        var nextLogin = actor(72);
        authentication.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(nextLogin);
        clearInvocations(context);
        assertThat(action.revise()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        verifyNoInteractions(context); assertThat(current()).isNull();
        assertThatThrownBy(() -> review.view(actor, SCOPE, source())).isInstanceOf(IllegalStateException.class);
    }
    @Test void correctionCannotBypassRevokedDocumentAuthorization() throws Exception {
        var review = generate(); approve(review); correction(review, "FAKE document.", "Blood work planned.");
        var before = review.view(actor, SCOPE, source());
        when(context.load(actor, 11)).thenThrow(new SecurityException("FAKE access revoked"));
        clearInvocations(request);
        assertThatThrownBy(action::revise).isInstanceOf(SecurityException.class);
        verify(request, never()).getParameterValues("pointText");
        assertThat(review.view(actor, SCOPE, source()).version()).isEqualTo(before.version());
    }
    @Test void changedSourceCorrectionCannotRetainOldReview() throws Exception {
        var review = generate(); approve(review); correction(review, "FAKE document.", "Blood work planned.");
        when(context.currentSource(any())).thenReturn(DocumentSummaryReviewBinding.capture(SCOPE, META,
                (TEXT + " Changed.").getBytes(StandardCharsets.UTF_8)));
        clearInvocations(request);
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        verify(request, never()).getParameterValues("pointText");
    }
    @Test void removingEveryPointDiscardsDraftInsteadOfProducingEmptySummary() throws Exception {
        var review = generate(); approve(review); correction(review, "FAKE document.", "Repeat blood work was planned.");
        when(request.getParameterValues("removePoint")).thenReturn(new String[]{"0"});
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        assertThatThrownBy(() -> review.view(actor, SCOPE, source())).isInstanceOf(IllegalStateException.class);
        verify(generator, times(1)).summarize(TEXT);
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewDiscarded", AUDIT, "11", 7, "documentId=11"));
        audit.verify(() -> LogAction.addLogSynchronous(any(), eq("DocumentSummary.reviewRevised"), anyString(), anyString(), any(), anyString()), never());
    }
    @Test void everyAuditRowNamesTheDocumentAndThePatientWithoutSummaryText() throws Exception {
        var review = generate();
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewGenerate", AUDIT, "11", 7, "documentId=11"));
        approve(review);
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewApproved", AUDIT, "11", 7, "documentId=11"));
        correction(review, "FAKE blood work document.", "Blood work was planned.");
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewRevised", AUDIT, "11", 7, "documentId=11"));
        token(review);
        assertThat(action.discard()).isEqualTo(ActionSupport.SUCCESS);
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewDiscarded", AUDIT, "11", 7, "documentId=11"));
        // No row is left without its document and patient, and the note never carries summary text.
        audit.verify(() -> LogAction.addLogSynchronous(any(), anyString(), anyString()), never());
        audit.verify(() -> LogAction.addLogSynchronous(any(), anyString(), contains("blood"), anyString(), any(), anyString()), never());
        assertThat(AUDIT).doesNotContain("FAKE").doesNotContain("blood");
    }
    @Test void originalFetchForADisposedDraftAnswers409NotAnUnresolvableSuccess() throws Exception {
        var review = generate(); token(review);
        when(request.getMethod()).thenReturn("POST");
        assertThat(action.discard()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        // The page's download link still carries the disposed draft id; GET it.
        when(request.getMethod()).thenReturn("GET");
        clearInvocations(request, response);
        assertThat(action.original()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(409);
        verify(response, never()).getOutputStream();
        verify(request, never()).setAttribute(eq("documentSummaryError"), any());
        // The same after removing every point, and with no review ever made.
        review = generate(); correction(review, "FAKE document.", "Repeat blood work was planned.");
        when(request.getParameterValues("removePoint")).thenReturn(new String[]{"0"});
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        when(request.getMethod()).thenReturn("GET");
        clearInvocations(request, response);
        assertThat(action.original()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(409);
        verify(request, never()).setAttribute(eq("documentSummaryError"), any());
    }
    @Test void originalFetchFailingBeforeAnyByteIsWrittenAnswers409() throws Exception {
        var review = generate(); token(review);
        when(request.getMethod()).thenReturn("GET");
        when(context.currentSource(any())).thenThrow(new IllegalStateException("FAKE source unreadable"));
        clearInvocations(request, response);
        assertThat(action.original()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(409);
        verify(request, never()).setAttribute(eq("documentSummaryError"), any());
    }
    @Test void frenchPatientRecordGivesTheFrenchNoticeOnThePageAndInTheReview() throws Exception {
        when(context.load(any(LoggedInInfo.class), eq(11)))
                .thenReturn(new DocumentSummaryReviewContext.Snapshot(SCOPE, META, "fr"));
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(request, atLeastOnce()).setAttribute("documentSummaryNotice", DocumentSummaryReviewBinding.unreviewedNotice("fr"));
        var review = generate();
        var view = review.view(actor, SCOPE, source());
        assertThat(view.notice()).contains("Statut de la révision").doesNotContain("Review status");
        verify(request, atLeastOnce()).setAttribute("documentSummaryNotice", view.notice());
    }

    @Test void spanishPolishAndPortugueseRecordsGiveTheirOwnNoticeOnThePageAndInTheReview() throws Exception {
        String[][] cases = {{"es", "Estado de la revisión"}, {"pl", "Status weryfikacji"}, {"pt-BR", "Situação da revisão"}};
        for (String[] c : cases) {
            clearInvocations(request);
            when(request.getMethod()).thenReturn("GET");
            when(context.load(any(LoggedInInfo.class), eq(11)))
                    .thenReturn(new DocumentSummaryReviewContext.Snapshot(SCOPE, META, c[0]));
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(request, atLeastOnce()).setAttribute("documentSummaryNotice", DocumentSummaryReviewBinding.unreviewedNotice(c[0]));
            var review = generate();
            try {
                var view = review.view(actor, SCOPE, source());
                assertThat(view.notice()).as(c[0]).contains(c[1]).doesNotContain("Review status", "Statut de la");
                verify(request, atLeastOnce()).setAttribute("documentSummaryNotice", view.notice());
            } finally {
                review.close();
            }
        }
    }

    @Test void aCapRefusalWritesNoGenerateRowOnlyADistinctRefusedRowWithTheIds() throws Exception {
        int capacity = DocumentSummaryReviewSession.capacity();
        var held = new java.util.ArrayList<DocumentSummaryReviewSession>();
        try {
            for (int i = 0; i < capacity; i++) {
                held.add(DocumentSummaryReviewSession.generate(SCOPE, META, () -> bytes(), generator, "FAKE-model", actor));
            }
            when(request.getMethod()).thenReturn("POST");
            clearInvocations(request);
            assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS);
            audit.verify(() -> LogAction.addLogSynchronous(any(), eq("DocumentSummary.reviewGenerate"), anyString(), anyString(), any(), anyString()), never());
            audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewGenerateRefused", AUDIT, "11", 7, "documentId=11"));
            assertThat(current()).isNull();
            verify(request).setAttribute(eq("documentSummaryError"), anyString());
            // The refused request never reached the model: only the held generations called it.
            verify(generator, times(capacity)).summarize(TEXT);
        } finally {
            held.forEach(DocumentSummaryReviewSession::close);
        }
        // With room again the same request is admitted and logged as a real generation, once.
        var review = generate();
        assertThat(current()).isSameAs(review);
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewGenerate", AUDIT, "11", 7, "documentId=11"), times(1));
    }
    @Test void discardWritesAnAuditEntryForTheDocument() throws Exception {
        var review = generate(); approve(review); token(review);
        when(request.getMethod()).thenReturn("POST");
        audit.verify(() -> LogAction.addLogSynchronous(any(), eq("DocumentSummary.reviewDiscarded"), anyString(), anyString(), any(), anyString()), never());
        assertThat(action.discard()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isNull();
        audit.verify(() -> LogAction.addLogSynchronous(actor, "DocumentSummary.reviewDiscarded", AUDIT, "11", 7, "documentId=11"));
    }
    @Test void staleDiscardWritesNoAuditEntry() throws Exception {
        var review = generate();
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterValues("draftId")).thenReturn(new String[]{UUID.randomUUID().toString()});
        assertThat(action.discard()).isEqualTo(ActionSupport.NONE); verify(response).sendError(409);
        assertThat(current()).isSameAs(review);
        audit.verify(() -> LogAction.addLogSynchronous(any(), eq("DocumentSummary.reviewDiscarded"), anyString(), anyString(), any(), anyString()), never());
    }
    @Test void invalidHonestCorrectionKeepsTheSavedDraftAndExplainsTheRule() throws Exception {
        var review = generate(); approve(review);
        var before = review.view(actor, SCOPE, source());
        for (String[] attempt : List.of(
                new String[]{"FAKE document.", "Totally unrelated zebra."},
                new String[]{"Unrelated zebra overview.", "Blood work was planned."},
                new String[]{"FAKE document.", "   "},
                new String[]{"FAKE document.", "Blood\rwork was planned."})) {
            correction(review, attempt[0], attempt[1]);
            clearInvocations(request, generator);
            assertThat(action.revise()).as(attempt[1]).isEqualTo(ActionSupport.SUCCESS);
            assertThat(current()).as(attempt[1]).isSameAs(review);
            var after = review.view(actor, SCOPE, source());
            assertThat(after.version()).isEqualTo(before.version());
            assertThat(after.draftId()).isEqualTo(before.draftId());
            assertThat(after.approved()).isTrue();
            assertThat(after.revisedAt()).isNull();
            verify(request).setAttribute("documentSummaryCorrectionError", AiReviewedDocumentSummary2Action.CORRECTION_NOT_SAVED);
            verify(request).setAttribute("documentSummaryCorrectionPending", true);
            verify(request).setAttribute("documentSummaryCorrectionOverview", attempt[0].strip());
            verify(request).setAttribute("documentSummaryCorrectionPoints", List.of(attempt[1].strip()));
            verify(request).setAttribute("documentSummaryCorrectionRemoved", List.of(false));
            verify(request, never()).setAttribute(eq("documentSummaryError"), any());
            verify(request).setAttribute("documentSummaryGenerated", true);
            verify(request).setAttribute("documentSummaryDraftId", before.draftId().toString());
            verify(request).setAttribute("documentSummaryOverview", before.summary().overview());
            verify(request).setAttribute("documentSummaryReviewed", true);
            verifyNoInteractions(generator);
        }
        audit.verify(() -> LogAction.addLogSynchronous(any(), eq("DocumentSummary.reviewRevised"), anyString(), anyString(), any(), anyString()), never());
        audit.verify(() -> LogAction.addLogSynchronous(any(), eq("DocumentSummary.reviewDiscarded"), anyString(), anyString(), any(), anyString()), never());
        var duplicate = generateTwoPoints();
        correction(duplicate, "FAKE document.", "Blood work was planned.", "Blood work was planned.");
        clearInvocations(request);
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isSameAs(duplicate);
        verify(request).setAttribute("documentSummaryCorrectionError", AiReviewedDocumentSummary2Action.CORRECTION_NOT_SAVED);
        verify(request).setAttribute("documentSummaryCorrectionRemoved", List.of(false, false));
    }
    @Test void rejectedCorrectionReturnsPostedEditsAndRemovalTicksWhileTheSessionDraftStays() throws Exception {
        var review = generateTwoPoints();
        var before = review.view(actor, SCOPE, source());
        String typed = "Totally unrelated zebra paragraph\r\nthat took a long time to type. ";
        correction(review, "  FAKE document.", typed, "Blood work planned.");
        when(request.getParameterValues("removePoint")).thenReturn(new String[]{"1"});
        clearInvocations(request);
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(current()).isSameAs(review);
        var after = review.view(actor, SCOPE, source());
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after.summary().points()).isEqualTo(before.summary().points());
        verify(request).setAttribute("documentSummaryCorrectionPending", true);
        verify(request).setAttribute("documentSummaryCorrectionOverview", "FAKE document.");
        verify(request).setAttribute("documentSummaryCorrectionPoints",
                List.of("Totally unrelated zebra paragraph\nthat took a long time to type.", "Blood work planned."));
        verify(request).setAttribute("documentSummaryCorrectionRemoved", List.of(false, true));
        verify(request).setAttribute("documentSummaryOverview", before.summary().overview());
        verify(request).setAttribute("documentSummaryPoints", before.summary().points());
        verify(request).setAttribute("documentSummaryDraftId", before.draftId().toString());
        // Keeping the removal tick and fixing the text then saves normally.
        correction(review, "FAKE document.", "Blood work was planned.", "Blood work planned.");
        clearInvocations(request);
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(review.view(actor, SCOPE, source()).summary().points()).hasSize(1);
        verify(request, never()).setAttribute(eq("documentSummaryCorrectionPending"), any());
        verify(request, never()).setAttribute(eq("documentSummaryCorrectionError"), any());
    }
    private DocumentSummaryReviewSession generateTwoPoints() throws Exception {
        var two = mock(DocumentSummary.class);
        when(two.overview()).thenReturn("FAKE document.");
        when(two.points()).thenReturn(List.of(
                Map.of("text", "Repeat blood work was planned.", "evidence", List.of("Repeat blood work was planned.")),
                Map.of("text", "Blood work planned.", "evidence", List.of("Repeat blood work was planned."))));
        when(generator.summarize(TEXT)).thenReturn(two);
        return generate();
    }
    @Test void correctionTrimsSurroundingWhitespaceSoStoredTextAndVersionAreCanonical() throws Exception {
        var review = generate();
        correction(review, "  FAKE document.\r\n", "\r\nBlood work was planned. \t");
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        var first = review.view(actor, SCOPE, source());
        assertThat(first.summary().overview()).isEqualTo("FAKE document.");
        assertThat(first.summary().points().get(0).get("text")).isEqualTo("Blood work was planned.");
        correction(review, "FAKE document.", "Blood work was planned.");
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        var second = review.view(actor, SCOPE, source());
        assertThat(second.summary().overview()).isEqualTo(first.summary().overview());
        assertThat(second.summary().points()).isEqualTo(first.summary().points());
    }
    @Test void correctionRejectsExtraPointOrDuplicateRemovalRatherThanAddingContent() throws Exception {
        var review = generate(); correction(review, "FAKE document.", "Blood work planned.", "Injected extra point.");
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS); assertThat(current()).isNull();
        review = generate(); correction(review, "FAKE document.", "Blood work planned.");
        when(request.getParameterValues("removePoint")).thenReturn(new String[]{"0", "0"});
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS); assertThat(current()).isNull();
        verify(request, never()).setAttribute(eq("documentSummaryCorrectionPending"), any());
        verify(request, never()).setAttribute(eq("documentSummaryCorrectionPoints"), any());
        verify(request, never()).setAttribute(eq("documentSummaryCorrectionOverview"), any());
    }
    @Test void correctionNormalizesBrowserLineEndingsAndKeepsMarkupAsPlainText() throws Exception {
        var review = generate();
        String payload = "FAKE document.\r\n</textarea><script>alert(1)</script>";
        correction(review, payload, "Repeat blood work was planned.");
        assertThat(action.revise()).isEqualTo(ActionSupport.SUCCESS);
        String stored = review.view(actor, SCOPE, source()).summary().overview();
        assertThat(stored).isEqualTo(payload.replace("\r\n", "\n"));
        verify(request).setAttribute("documentSummaryOverview", stored);
        assertThat(review.view(actor, SCOPE, source()).approved()).isFalse();
    }

}
