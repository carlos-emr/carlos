/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgents;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryGenerationException;
import io.github.carlos_emr.carlos.clinical.summary.DocumentSummaryReviewContext;
import io.github.carlos_emr.carlos.clinical.summary.DocumentSummaryReviewSession;
import io.github.carlos_emr.carlos.clinical.summary.DocumentSummaryService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Default-off Canadian synthetic review trial. Every mutation is a CSRFGuard-protected POST. */
public final class AiReviewedDocumentSummary2Action extends ActionSupport {
    private static final String GENERATION_KEY = AiReviewedDocumentSummary2Action.class.getName() + ".generation";
    /** oscarLog.content for every DocumentSummary.* row: the record type, never summary text. */
    static final String AUDIT_CONTENT = "AI document summary";
    /** Shown when an honest correction fails provenance validation; the saved draft stays available. */
    static final String CORRECTION_NOT_SAVED_KEY = "documentSummary.review.correctionNotSaved";
    static final String REVIEW_UNAVAILABLE_KEY = "documentSummary.review.unavailable";
    private final DocumentSummaryReviewContext context;
    private final DocumentSummaryService suppliedGenerator;
    private final String suppliedModelIdentity;

    public AiReviewedDocumentSummary2Action() {
        this(SpringUtils.getBean(DocumentSummaryReviewContext.class), null, null);
    }
    AiReviewedDocumentSummary2Action(DocumentSummaryReviewContext context, DocumentSummaryService generator, String modelIdentity) {
        this.context = context;
        this.suppliedGenerator = generator;
        this.suppliedModelIdentity = modelIdentity;
    }
    @Override public String execute() throws Exception { return handle("view"); }
    public String generate() throws Exception { return handle("generate"); }
    public String approve() throws Exception { return handle("approve"); }
    public String revise() throws Exception { return handle("revise"); }
    public String discard() throws Exception { return handle("discard"); }
    public String original() throws Exception { return handle("original"); }

    private String handle(String operation) throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!DocumentSummaryReviewContext.enabled()) { response.sendError(404); return NONE; }
        boolean read = List.of("view", "original").contains(operation);
        if (read ? !List.of("GET", "HEAD").contains(request.getMethod()) : !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", read ? "GET, HEAD" : "POST");
            response.sendError(405); return NONE;
        }
        int documentId;
        UUID draftId = null;
        try {
            String value = single(request, "documentId");
            if (!value.matches("[1-9][0-9]{0,8}")) throw new IllegalArgumentException();
            documentId = Integer.parseInt(value);
            if (List.of("approve", "original", "discard", "revise").contains(operation)) {
                String token = single(request, "draftId");
                draftId = UUID.fromString(token);
                if (!draftId.toString().equals(token)) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) { response.sendError(400); return NONE; }
        var actor = LoggedInInfo.getLoggedInInfoFromSession(request);
        var session = request.getSession(false);
        if (actor == null || session == null) throw new SecurityException("Authenticated clinician session required.");
        DocumentSummaryMessages.prepare(request);
        request.setAttribute("documentSummaryId", documentId);
        request.setAttribute("documentSummaryReviewWorkflow", true);
        request.setAttribute("documentSummaryAllowed", true);
        request.setAttribute("documentSummaryNotice", io.github.carlos_emr.carlos.clinical.summary.DocumentSummaryReviewBinding.unreviewedNotice());
        DocumentSummaryReviewSession pending = null;
        DocumentSummaryReviewSession observedReview = null;
        DocumentSummaryReviewSession installedReview = null;
        UUID ownedGeneration = null;
        try {
            synchronized (session) {
                // Principal lifecycle is independent of access to the requested document.
                // Refresh the server actor before any reader or generation call.
                actor = LoggedInInfo.getLoggedInInfoFromSession(request);
                var prior = session.getAttribute(DocumentSummaryReviewSession.SESSION_KEY);
                if (prior instanceof DocumentSummaryReviewSession review && !review.samePrincipal(actor)) {
                    removeReview(session);
                    response.sendError(409); return NONE;
                }
                if (actor == null) throw new SecurityException("Authenticated clinician session required.");
            }
            var snapshot = context.load(actor, documentId);
            request.setAttribute("documentSummaryId", documentId);
            request.setAttribute("documentSummaryTitle", snapshot.metadata().title());
            request.setAttribute("documentSummaryReviewWorkflow", true);
            // The sender stack is an explicit integration dependency, never implied by review.
            request.setAttribute("documentSummaryDeliveryReady", false);
            if ("generate".equals(operation)) {
                UUID generation = UUID.randomUUID();
                ownedGeneration = generation;
                synchronized (session) {
                    removeReview(session);
                    session.setAttribute(GENERATION_KEY, generation);
                }
                var generator = suppliedGenerator;
                String model = suppliedModelIdentity;
                if (generator == null) {
                    var agent = ClinicalSummaryAgents.configuredDocument();
                    generator = new DocumentSummaryService(agent);
                    model = agent.displayName();
                }
                final var auditActor = actor;
                try {
                    pending = DocumentSummaryReviewSession.generate(snapshot.scope(), snapshot.metadata(),
                            () -> {
                                // Called only once the cap has admitted this request, before any model call.
                                audit(auditActor, "DocumentSummary.reviewGenerate", snapshot.scope());
                                return context.read(snapshot);
                            }, generator, model, actor);
                } catch (DocumentSummaryReviewSession.CapacityFullException refused) {
                    // A refusal is its own audit row, never a generation that did not happen.
                    audit(auditActor, "DocumentSummary.reviewGenerateRefused", snapshot.scope());
                    throw refused;
                }
                var freshActor = LoggedInInfo.getLoggedInInfoFromSession(request);
                var fresh = context.load(freshActor, documentId);
                var source = context.currentSource(fresh);
                pending.view(freshActor, fresh.scope(), source);
                synchronized (session) {
                    if (!generation.equals(session.getAttribute(GENERATION_KEY))) {
                        throw new IllegalStateException("A later generation replaced this request. Reopen the current draft.");
                    }
                    installedReview = pending;
                    session.setAttribute(DocumentSummaryReviewSession.SESSION_KEY, pending);
                    pending = null;
                    session.removeAttribute(GENERATION_KEY);
                }
            }
            synchronized (session) {
                var current = session.getAttribute(DocumentSummaryReviewSession.SESSION_KEY);
                if (!(current instanceof DocumentSummaryReviewSession review)) {
                    if ("original".equals(operation)) {
                        // A download has no page to explain itself on: a disposed or never-made
                        // review answers like a stale draft token does, never a view result.
                        response.sendError(409); return NONE;
                    }
                    if (!"view".equals(operation) && !"generate".equals(operation)) throw new IllegalStateException("No current document review is available.");
                    return SUCCESS;
                }
                var currentActor = LoggedInInfo.getLoggedInInfoFromSession(request);
                var fresh = context.load(currentActor, documentId);
                if ("generate".equals(operation) && installedReview != review) {
                    response.sendError(409); return NONE;
                }
                if (!review.samePrincipal(currentActor)) {
                    // A genuine login change must dispose the previous login's patient state.
                    removeReview(session);
                    response.sendError(409); return NONE;
                }
                if (!review.belongsTo(currentActor, fresh.scope(), draftId)) {
                    // The same login's stale document/tab/token never owns the newer review.
                    response.sendError(409); return NONE;
                }
                // Only this exact actor/scope/displayed draft can become failure-cleanup ownership.
                observedReview = review;
                var source = context.currentSource(fresh);
                var before = review.view(currentActor, fresh.scope(), source);
                if (draftId != null && !before.draftId().equals(draftId)) {
                    response.sendError(409); return NONE;
                }
                if ("revise".equals(operation)) {
                    int count = before.summary().points().size();
                    var texts = request.getParameterValues("pointText");
                    if (texts == null || texts.length != count) throw new IllegalArgumentException();
                    var corrected = new ArrayList<String>(count);
                    for (String text : texts) corrected.add(correctionText(text, 2000));
                    String overview = correctionText(single(request, "overview"), 4000);
                    Set<Integer> removed = new HashSet<>();
                    var selections = request.getParameterValues("removePoint");
                    if (selections != null) {
                        if (selections.length > count) throw new IllegalArgumentException();
                        for (String selection : selections) {
                            if (selection == null || !selection.matches("0|[1-9][0-9]?")) throw new IllegalArgumentException();
                            int index = Integer.parseInt(selection);
                            if (index >= count || !removed.add(index)) throw new IllegalArgumentException();
                        }
                    }
                    if (removed.size() == count) {
                        // Identical ownership/freshness guard as discard; never accept an empty summary.
                        removeReview(session);
                        session.removeAttribute(GENERATION_KEY);
                        audit(currentActor, "DocumentSummary.reviewDiscarded", fresh.scope());
                        return SUCCESS;
                    }
                    try {
                        review.revise(currentActor, fresh.scope(), draftId, source, overview, corrected, removed);
                        audit(currentActor, "DocumentSummary.reviewRevised", fresh.scope());
                    } catch (IllegalArgumentException unsupported) {
                        // Well-formed input that fails provenance validation keeps the saved draft; the
                        // review session rejects it atomically. Only shape-forged input above discards.
                        // This catch would also absorb the binding's own shape rejections (pointTexts size,
                        // removedPoints range); they are unreachable here only because the checks above
                        // pre-validate against the same draft under the same session lock.
                        // The posted edits go back into the form (encoded by the page) so no typing is lost.
                        request.setAttribute("documentSummaryCorrectionError", DocumentSummaryMessages.text(request, CORRECTION_NOT_SAVED_KEY));
                        request.setAttribute("documentSummaryCorrectionPending", true);
                        request.setAttribute("documentSummaryCorrectionOverview", overview);
                        request.setAttribute("documentSummaryCorrectionPoints", List.copyOf(corrected));
                        var ticks = new ArrayList<Boolean>(count);
                        for (int index = 0; index < count; index++) ticks.add(removed.contains(index));
                        request.setAttribute("documentSummaryCorrectionRemoved", List.copyOf(ticks));
                    }
                    // Ignore any submitted affirmation: only the separately rendered existing form may approve.
                }
                if ("approve".equals(operation)) {
                    boolean affirmative = Arrays.equals(request.getParameterValues("affirmReview"), new String[]{"true"});
                    review.approve(currentActor, fresh.scope(), draftId, source, affirmative);
                    audit(currentActor, "DocumentSummary.reviewApproved", fresh.scope());
                }
                var view = review.view(currentActor, fresh.scope(), source);
                if ("discard".equals(operation)) {
                    if (!view.draftId().equals(draftId)) throw new IllegalStateException("The displayed review was replaced.");
                    removeReview(session);
                    session.removeAttribute(GENERATION_KEY);
                    audit(currentActor, "DocumentSummary.reviewDiscarded", fresh.scope());
                    return SUCCESS;
                }
                if ("original".equals(operation)) {
                    byte[] bytes = review.copyOriginalForReview(currentActor, fresh.scope(), draftId, source);
                    try {
                        String type = fresh.metadata().contentType().split(";", 2)[0];
                        response.setContentType(type);
                        response.setHeader("Content-Disposition", "attachment; filename=\"reviewed-original"
                                + ("application/pdf".equals(type) ? ".pdf" : "text/html".equals(type) ? ".html" : ".txt") + "\"");
                        response.setContentLength(bytes.length);
                        if (!"HEAD".equals(request.getMethod())) response.getOutputStream().write(bytes);
                        return NONE;
                    } finally { Arrays.fill(bytes, (byte) 0); }
                }
                request.setAttribute("documentSummaryGenerated", true);
                request.setAttribute("documentSummaryOverview", view.summary().overview());
                request.setAttribute("documentSummaryPoints", view.summary().points());
                request.setAttribute("documentSummaryDraftId", view.draftId().toString());
                request.setAttribute("documentSummaryNotice", view.notice());
                request.setAttribute("documentSummaryReviewed", view.approved());
                request.setAttribute("documentSummaryGeneratedAt", view.generatedAt().toString());
                request.setAttribute("documentSummaryRevisedAt", view.revisedAt() == null ? null : view.revisedAt().toString());
                request.setAttribute("documentSummaryExtraction", view.extractionDisclosure());
            }
            return SUCCESS;
        } catch (ClinicalSummaryGenerationException | IOException | IllegalArgumentException | IllegalStateException unavailable) {
            clearOwned(session, ownedGeneration, observedReview);
            if ("original".equals(operation)) {
                // The download mapping has no view result; an uncommitted failure is a plain 409.
                if (response.isCommitted()) throw unavailable;
                response.sendError(409); return NONE;
            }
            // Never include model output, patient details, file paths or upstream diagnostics here.
            request.setAttribute("documentSummaryError", DocumentSummaryMessages.text(request, REVIEW_UNAVAILABLE_KEY));
            return SUCCESS;
        } catch (SecurityException denied) {
            clearOwned(session, ownedGeneration, observedReview);
            throw denied;
        } finally { if (pending != null) pending.close(); }
    }

    /** The audit row names the document and the patient; the note carries no document text. */
    private static void audit(LoggedInInfo actor, String action, io.github.carlos_emr.carlos.clinical.summary.DocumentSummaryReviewBinding.Scope scope) {
        LogAction.addLogSynchronous(actor, action, AUDIT_CONTENT, Integer.toString(scope.documentId()),
                scope.patientId(), "documentId=" + scope.documentId());
    }
    private static void clearOwned(HttpSession session, UUID generation, DocumentSummaryReviewSession observed) {
        try {
            synchronized (session) {
                if (observed != null && session.getAttribute(DocumentSummaryReviewSession.SESSION_KEY) == observed) {
                    removeReview(session);
                }
                if (generation != null && generation.equals(session.getAttribute(GENERATION_KEY))) {
                    session.removeAttribute(GENERATION_KEY);
                }
            }
        } catch (IllegalStateException invalidated) { /* Session invalidation already invokes valueUnbound. */ }
    }
    private static void removeReview(HttpSession session) {
        var current = session.getAttribute(DocumentSummaryReviewSession.SESSION_KEY);
        if (current instanceof DocumentSummaryReviewSession review) review.close();
        session.removeAttribute(DocumentSummaryReviewSession.SESSION_KEY);
    }
    private static String correctionText(String value, int maximum) {
        // Browser textarea form encoding uses CRLF. Canonicalize those transport line endings and the
        // surrounding whitespace so stored text and the draft version fingerprint stay canonical.
        if (value == null || value.length() > maximum * 2) throw new IllegalArgumentException();
        String text = value.replace("\r\n", "\n").strip();
        if (text.length() > maximum) throw new IllegalArgumentException();
        return text;
    }
    private static String single(HttpServletRequest request, String name) {
        var values = request.getParameterValues(name);
        if (values == null || values.length != 1 || values[0] == null) throw new IllegalArgumentException();
        return values[0];
    }
}
