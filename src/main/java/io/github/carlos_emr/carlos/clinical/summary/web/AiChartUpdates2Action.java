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
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.clinical.summary.ChartUpdateContext;
import io.github.carlos_emr.carlos.clinical.summary.ChartUpdateProposals;
import io.github.carlos_emr.carlos.clinical.summary.ChartUpdateReview;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryGenerationException;
import io.github.carlos_emr.carlos.clinical.summary.ReviewedChartUpdateService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Explicit generation and per-item approval. The session owns all proposal identity and evidence. */
public final class AiChartUpdates2Action extends ActionSupport {
    private final ChartUpdateContext context;
    private final ReviewedChartUpdateService writer;
    private final ProviderDao providers;
    private final ChartUpdateProposals generator;

    public AiChartUpdates2Action() {
        this(SpringUtils.getBean(ChartUpdateContext.class), SpringUtils.getBean(ReviewedChartUpdateService.class),
                SpringUtils.getBean(ProviderDao.class), null);
    }
    AiChartUpdates2Action(ChartUpdateContext context, ReviewedChartUpdateService writer, ProviderDao providers,
            ChartUpdateProposals generator) {
        this.context = context;
        this.writer = writer;
        this.providers = providers;
        this.generator = generator;
    }

    @Override public String execute() throws Exception { return handle("view"); }
    public String generate() throws Exception { return handle("generate"); }
    public String apply() throws Exception { return handle("apply"); }
    public String dismiss() throws Exception { return handle("dismiss"); }

    private String handle(String operation) throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        boolean view = "view".equals(operation);
        if (view ? !List.of("GET", "HEAD").contains(request.getMethod()) : !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", view ? "GET, HEAD" : "POST");
            response.sendError(405);
            return NONE;
        }
        try { ChartUpdateContext.requireEnabled(); }
        catch (IllegalStateException disabled) { response.sendError(404); return NONE; }
        LoggedInInfo user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (user == null) throw new SecurityException("missing required sec object (_edoc)");
        int document;
        try {
            String id = single(request, "documentId");
            if (!id.matches("[1-9][0-9]{0,8}")) throw new IllegalArgumentException();
            document = Integer.parseInt(id);
        } catch (IllegalArgumentException invalid) { response.sendError(400); return NONE; }
        var session = request.getSession(false);
        if (session == null) throw new SecurityException("missing required sec object (_edoc)");
        // The document list checks this same authorized read boundary before navigating.
        // It receives no source/chart text and never generates proposals or changes review state.
        if (view && "application/json".equals(request.getHeader("Accept"))) {
            Map<String, Object> availability = new LinkedHashMap<>();
            try {
                context.load(user, document);
                availability.put("available", true);
            } catch (IllegalStateException unavailable) {
                availability.put("available", false);
                availability.put("message", unavailable.getMessage());
                availability.put("originalAvailable", !(unavailable instanceof ChartUpdateContext.OriginalDocumentMissingException));
            }
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getWriter().write(new ObjectMapper().writeValueAsString(availability));
            return NONE;
        }
        try {
            var snapshot = context.load(user, document);
            if ("generate".equals(operation)) {
                LogAction.addLogSynchronous(user, "ChartUpdates.generate", "documentId=" + document);
                var engine = generator == null ? new ChartUpdateProposals() : generator;
                var proposals = engine.generateReport(snapshot.source());
                var after = context.load(user, document);
                if (!snapshot.fingerprint().equals(after.fingerprint())) {
                    throw new IllegalStateException("The source or chart changed during generation. Generate proposals again.");
                }
                synchronized (session) {
                    session.setAttribute(ChartUpdateReview.SESSION_KEY,
                            new ChartUpdateReview(user.getLoggedInProviderNo(), after, proposals, engine.displayName(),
                                    providers.getActiveProviders().stream().anyMatch(provider ->
                                            user.getLoggedInProviderNo().equals(provider.getProviderNo()))
                                            ? user.getLoggedInProviderNo() : ""));
                }
            }
            synchronized (session) {
                ChartUpdateReview review = (ChartUpdateReview) session.getAttribute(ChartUpdateReview.SESSION_KEY);
                if (!view && !"generate".equals(operation)) {
                    if (review == null || review.getDocument() != document) throw new IllegalStateException("Generate proposals before reviewing an update.");
                    review.authorize(user.getLoggedInProviderNo(), single(request, "reviewToken"));
                    String key = single(request, "proposalKey");
                    review.proposal(key);
                    rememberDrafts(request, review, key);
                    if (!review.getOutcomes().containsKey(key)) {
                        if ("dismiss".equals(operation)) {
                            review.record(key, "review".equals(review.proposal(key).kind())
                                    ? "Review item closed. This action did not save a record." : "Dismissed. Nothing saved.");
                        } else {
                            var approval = new ReviewedChartUpdateService.Approval(single(request, "entryText"),
                                    single(request, "dueDate"), single(request, "assignee"), single(request, "destination"),
                                    "true".equals(single(request, "confirmed")), single(request, "chartFingerprint"));
                            var result = writer.apply(user, review, single(request, "reviewToken"), key, approval);
                            review.record(key, (result.replay() ? "Already saved: " : "Saved: ") + result.kind() + " #" + result.target());
                            LogAction.addLogSynchronous(user, "ChartUpdates.accept", "documentId=" + document
                                    + ",kind=" + result.kind() + ",targetId=" + result.target());
                        }
                    }
                }
                if (view) {
                    // Read under the session lock so the rendered fingerprint cannot predate a concurrent approval.
                    render(request, user, context.load(user, document), review);
                } else {
                    // Refreshing the result page must never repeat inference or a chart mutation.
                    response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                    response.setHeader("Location", request.getContextPath() + "/documentManager/AiChartUpdates?documentId=" + document);
                    return NONE;
                }
            }
        // SecurityException deliberately propagates to the document package's securityError
        // mapping: CarlosExceptionMappingInterceptor records the refusal and returns HTTP 403.
        } catch (IllegalArgumentException | IllegalStateException | ClinicalSummaryGenerationException expected) {
            request.setAttribute("chartUpdateError", expected.getMessage());
            try {
                synchronized (session) {
                    render(request, user, context.load(user, document),
                            (ChartUpdateReview) session.getAttribute(ChartUpdateReview.SESSION_KEY));
                }
            } catch (IllegalStateException unavailable) {
                request.setAttribute("chartUpdateError", unavailable.getMessage());
            }
        }
        request.setAttribute("chartUpdateDocumentId", document);
        return SUCCESS;
    }

    private void render(HttpServletRequest request, LoggedInInfo user, ChartUpdateContext.Snapshot snapshot, ChartUpdateReview review) {
        request.setAttribute("chartUpdateTitle", snapshot.title());
        request.setAttribute("chartUpdateDate", snapshot.date());
        request.setAttribute("chartUpdatePatient", snapshot.patientId());
        request.setAttribute("chartUpdatePatientLabel", snapshot.patientLabel());
        request.setAttribute("chartUpdateSource", snapshot.source());
        request.setAttribute("chartUpdateReady", true);
        request.setAttribute("chartUpdateEntries", snapshot.entries());
        request.setAttribute("chartUpdateProviders", providers.getActiveProviders());
        request.setAttribute("chartUpdateSections", context.writableSections(user, snapshot.patientId()));
        if (review == null || review.getDocument() != snapshot.documentId()) return;
        review.authorize(user.getLoggedInProviderNo(), review.getToken());
        if (review.getPatient() != snapshot.patientId() || !review.getSourceHash().equals(snapshot.sourceHash())) {
            throw new IllegalStateException("The source changed. Generate proposals again.");
        }
        // Every response renders the fresh chart alongside the proposals before accepting another approval.
        review.refresh(snapshot.fingerprint());
        List<Map<String, Object>> rows = new ArrayList<>();
        review.getProposals().forEach((key, proposal) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("kind", proposal.kind());
            row.put("recordDestination", proposal.destination());
            row.put("nativeUrl", "review".equals(proposal.kind())
                    ? context.nativeReviewUrl(user, snapshot.patientId(), proposal.destination()) : "");
            row.put("evidence", proposal.evidence());
            row.put("outcome", review.getOutcomes().getOrDefault(key, ""));
            var draft = review.draft(key);
            row.put("text", draft.text());
            row.put("dueDate", draft.dueDate());
            row.put("assignee", draft.assignee());
            row.put("destination", draft.destination());
            var suggestion = review.suggestion(key);
            row.put("suggestedDate", suggestion.draft().dueDate());
            row.put("dateBasis", suggestion.dateBasis());
            row.put("dateAnchor", suggestion.anchor());
            row.put("suggestedDestination", suggestion.draft().destination());
            row.put("suggestedAssignee", suggestion.draft().assignee());
            row.put("pastDue", !suggestion.draft().dueDate().isEmpty()
                    && java.time.LocalDate.parse(suggestion.draft().dueDate()).isBefore(java.time.LocalDate.now()));
            rows.add(row);
        });
        request.setAttribute("chartUpdateReview", review);
        request.setAttribute("chartUpdateRows", rows);
        if (review.getCoverage() != null) {
            request.setAttribute("chartUpdateCoverage", review.getCoverage().sections(snapshot.source(),
                    List.copyOf(review.getProposals().values())));
            request.setAttribute("chartUpdateRejected", review.getCoverage().getRejected());
        }
        request.setAttribute("chartUpdateRemaining", rows.size() - review.getOutcomes().size());
    }

    private static void rememberDrafts(HttpServletRequest request, ChartUpdateReview review, String submittedKey) {
        // Read only fields for this authorized review's proposals. No client-supplied identity or approval is retained.
        Map<String, ChartUpdateReview.Draft> drafts = new LinkedHashMap<>();
        for (String key : review.getProposals().keySet()) {
            String prefix = key.equals(submittedKey) ? "" : "draft." + key + ".";
            if (request.getParameter(prefix + "entryText") == null) continue;
            drafts.put(key, new ChartUpdateReview.Draft(single(request, prefix + "entryText"),
                    single(request, prefix + "dueDate"), single(request, prefix + "assignee"),
                    single(request, prefix + "destination")));
        }
        drafts.forEach(review::remember);
    }

    private static String single(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) return "";
        if (values.length != 1 || values[0] == null) throw new IllegalArgumentException("Invalid review form.");
        return values[0];
    }
}
