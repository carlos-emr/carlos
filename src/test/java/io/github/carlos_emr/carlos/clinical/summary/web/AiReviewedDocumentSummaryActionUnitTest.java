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
}
