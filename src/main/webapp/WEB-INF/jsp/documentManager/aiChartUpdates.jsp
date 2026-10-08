<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
<%--
    Review AI-suggested chart updates for one document.

    With script, suggestions are reviewed one at a time: a progress strip (one step per suggestion, in
    its chart section's colour), the current suggestion with its fields, the source document and a
    chart check beside it, the actions in a bar that stays at the bottom of the window, and a summary
    at the end. Without script, every suggestion is listed in order, followed by the summary.
    Nothing is written to the chart without the clinician's explicit approval of that suggestion.
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<!doctype html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="chartUpdates.title"/> | CARLOS</title>
    <%@ include file="/WEB-INF/jspf/bootstrap-css.jspf" %>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/css/global.css">
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-chart-updates.css">
</head>
<body>
<header class="page-header-bar page-header-bar--flex review-header">
    <h1 class="page-header-title"><fmt:message key="chartUpdates.title"/></h1>
    <c:choose>
        <c:when test="${not empty chartUpdatePatient}">
            <c:url var="chartUpdateBackUrl" value="/documentManager/ViewDocumentReport">
                <c:param name="function" value="demographic"/>
                <c:param name="functionid" value="${chartUpdatePatient}"/>
                <c:param name="chartUpdates" value="1"/>
            </c:url>
            <a class="btn btn-secondary btn-sm" href="${carlos:forHtmlAttribute(chartUpdateBackUrl)}"><fmt:message key="global.btnBack"/></a>
        </c:when>
        <c:otherwise><button class="btn btn-secondary btn-sm" type="button" data-review-back><fmt:message key="global.btnBack"/></button></c:otherwise>
    </c:choose>
</header>
<div class="review-content">
    <div class="review-context">
    <c:if test="${chartUpdateReady}">
    <p class="review-document"><fmt:message key="chartUpdates.patient"/>: <carlos:encode value="${chartUpdatePatientLabel}"/> (#<carlos:encode value="${chartUpdatePatient}"/>) · <carlos:encode value="${chartUpdateTitle}"/> · <carlos:encode value="${chartUpdateDate}"/></p>
    <div class="review-guidance">
        <p><fmt:message key="chartUpdates.warning"/></p>
        <c:if test="${not empty chartUpdateRows}"><p class="suggestions-help"><fmt:message key="chartUpdates.suggestionsHelp"/></p></c:if>
    </div>
    </c:if>
</div>
<c:if test="${not empty chartUpdateError}"><p class="alert alert-danger" role="alert"><carlos:encode value="${chartUpdateError}"/></p></c:if>
<c:if test="${chartUpdateReady}">
<fmt:message key="chartUpdates.proposals" var="chartUpdateWorkflowLabel"/>
<fmt:message key="chartUpdates.notReviewed" var="chartUpdateNotReviewed"/>
<fmt:message key="chartUpdates.summary" var="chartUpdateSummaryLabel"/>
<c:if test="${not empty chartUpdateReview and not empty chartUpdateRows}">
<%-- One step per suggestion. Shown by script, which reviews one suggestion at a time. --%>
<nav class="review-strip" aria-label="${carlos:forHtmlAttribute(chartUpdateWorkflowLabel)}" data-summary-label="${carlos:forHtmlAttribute(chartUpdateSummaryLabel)}" hidden>
    <ol class="review-strip-steps">
    <c:forEach items="${chartUpdateRows}" var="proposal" varStatus="position">
        <c:choose><c:when test="${proposal.kind == 'review'}"><fmt:message key="chartUpdates.section.${proposal.recordDestination}" var="stepKind"/></c:when><c:when test="${proposal.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler" var="stepKind"/></c:when><c:otherwise><fmt:message key="chartUpdates.history" var="stepKind"/></c:otherwise></c:choose>
        <fmt:message key="chartUpdates.step" var="stepLabel"><fmt:param value="${position.count}"/><fmt:param value="${chartUpdateRows.size()}"/><fmt:param value="${stepKind}"/><fmt:param value="${empty proposal.outcome ? chartUpdateNotReviewed : proposal.outcome}"/></fmt:message>
        <li><button type="button" class="review-step ${empty proposal.outcome ? '' : 'review-step-done'}" data-review-step="${carlos:forHtmlAttribute(proposal.key)}" data-section="${carlos:forHtmlAttribute(proposal.kind == 'tickler' ? 'Tickler' : (proposal.kind == 'review' ? proposal.recordDestination : proposal.destination))}" title="${carlos:forHtmlAttribute(stepLabel)}"><span class="visually-hidden"><carlos:encode value="${stepLabel}"/></span></button></li>
    </c:forEach>
    </ol>
    <span class="review-strip-count" data-review-position role="status"></span>
    <button type="button" class="btn btn-outline-secondary btn-sm" data-review-summary><carlos:encode value="${chartUpdateSummaryLabel}"/></button>
</nav>
</c:if>
<div class="chart-update-layout">
<main id="proposals" data-current-proposal="${carlos:forHtmlAttribute(chartUpdateCurrent)}">
    <c:choose>
    <c:when test="${not empty chartUpdateReview}">
        <h2 class="visually-hidden"><fmt:message key="chartUpdates.proposals"/> (<carlos:encode value="${chartUpdateRows.size()}"/>)</h2>
        <c:forEach items="${chartUpdateRows}" var="proposal" varStatus="position">
        <c:set var="proposalSection" value="${proposal.kind == 'tickler' ? 'Tickler' : (proposal.kind == 'review' ? proposal.recordDestination : proposal.destination)}"/>
        <article id="proposal-${carlos:forHtmlAttribute(proposal.key)}" class="card mb-3 proposal ${empty proposal.outcome ? '' : 'proposal-complete'}" data-proposal-key="${carlos:forHtmlAttribute(proposal.key)}" data-kind="${carlos:forHtmlAttribute(proposal.kind)}" data-destination="${carlos:forHtmlAttribute(proposal.recordDestination)}" data-section="${carlos:forHtmlAttribute(proposalSection)}">
            <div class="proposal-heading">
                <span class="proposal-number" aria-hidden="true"><carlos:encode value="${position.count}"/>.</span>
                <h3 class="h5"><c:choose><c:when test="${proposal.kind == 'review'}"><fmt:message key="chartUpdates.section.${proposal.recordDestination}"/></c:when><c:when test="${proposal.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler"/></c:when><c:otherwise><fmt:message key="chartUpdates.history"/></c:otherwise></c:choose></h3>
                <c:if test="${proposal.kind == 'history'}"><span class="section-chip" data-section-chip <c:if test="${empty proposal.destination}">hidden</c:if>><c:if test="${not empty proposal.destination}"><fmt:message key="chartUpdates.section.${proposal.destination}"/></c:if></span></c:if>
                <span class="proposal-position"><fmt:message key="chartUpdates.position"><fmt:param value="${position.count}"/><fmt:param value="${chartUpdateRows.size()}"/></fmt:message></span>
            </div>
            <div class="card-body">
                <div class="proposal-evidence">
                    <p class="evidence-label"><fmt:message key="chartUpdates.evidence"/></p>
                    <blockquote class="source-text"><carlos:encode value="${proposal.evidence}"/></blockquote>
                    <a href="#full-source" data-show-source><fmt:message key="chartUpdates.showSource"/></a>
                </div>
                <%-- Shown by script when the chart check beside the suggestion (below it on a narrow
                     screen) has found something, so it is never missed before adding. --%>
                <p class="chart-check-pointer" hidden><a href="#chart-check"><fmt:message key="chartUpdates.chartCheckPointer"/></a></p>
                <%-- Filled by script, which moves them all into the chart check beside the suggestions and
                     shows the current suggestion's. --%>
                <div class="chart-check-notices" data-check-for="${carlos:forHtmlAttribute(proposal.key)}">
                    <div class="chart-match-notice" role="status" hidden>
                        <p><fmt:message key="chartUpdates.matchingChartText"/></p>
                        <ul class="chart-match-links"></ul>
                    </div>
                    <div class="related-proposal-notice" role="status" hidden>
                        <p><fmt:message key="chartUpdates.relatedProposals"/></p>
                        <div class="related-proposal-quotes"></div>
                    </div>
                </div>
                <c:choose>
                <c:when test="${not empty proposal.outcome}">
                    <p class="alert alert-success mt-2" role="status"><carlos:encode value="${proposal.outcome}"/></p>
                    <div class="review-action-bar" data-step-only hidden>
                        <button type="button" class="btn btn-outline-secondary btn-sm" data-review-previous><span aria-hidden="true">← </span><fmt:message key="dms.incomingDocs.previous"/></button>
                        <span class="review-action-spacer"></span>
                        <button type="button" class="btn btn-primary btn-sm" data-review-next><fmt:message key="dms.incomingDocs.next"/><span aria-hidden="true"> →</span></button>
                    </div>
                </c:when>
                <c:otherwise>
                <form class="proposal-form" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ApplyAiChartUpdate">
                    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
                    <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(chartUpdateDocumentId)}">
                    <input type="hidden" name="reviewToken" value="${carlos:forHtmlAttribute(chartUpdateReview.token)}">
                    <input type="hidden" name="proposalKey" value="${carlos:forHtmlAttribute(proposal.key)}">
                    <input type="hidden" name="chartFingerprint" value="${carlos:forHtmlAttribute(chartUpdateReview.fingerprint)}">
                    <label class="form-label mt-2" for="text-${carlos:forHtmlAttribute(proposal.key)}"><c:choose><c:when test="${proposal.kind == 'review'}"><fmt:message key="chartUpdates.nativeInformation"/></c:when><c:otherwise><fmt:message key="chartUpdates.entry"/></c:otherwise></c:choose></label>
                    <textarea id="text-${carlos:forHtmlAttribute(proposal.key)}" class="form-control" name="entryText" rows="5" maxlength="2000" <c:if test="${proposal.kind == 'review'}">readonly</c:if> required><carlos:encode value="${proposal.text}"/></textarea>
                    <c:choose><c:when test="${proposal.kind == 'review'}">
                        <p><fmt:message key="chartUpdates.section.${proposal.recordDestination}"/></p>
                        <p class="field-help"><fmt:message key="chartUpdates.nativeHelp"/></p>
                        <c:choose><c:when test="${not empty proposal.nativeUrl}">
                            <button type="button" class="btn btn-primary btn-sm native-review-open" data-native-url="${carlos:forHtmlAttribute(pageContext.request.contextPath)}${carlos:forHtmlAttribute(proposal.nativeUrl)}" data-native-title="${carlos:forHtmlAttribute(proposal.recordDestination)}"><fmt:message key="chartUpdates.openForm"/></button>
                        </c:when><c:otherwise><p class="field-help"><c:choose><c:when test="${proposal.recordDestination == 'Medications' or proposal.recordDestination == 'Allergies'}"><fmt:message key="chartUpdates.rxAllergyHandoff"/></c:when><c:otherwise><fmt:message key="chartUpdates.nativeUnavailable"/></c:otherwise></c:choose></p></c:otherwise></c:choose>
                    </c:when><c:when test="${proposal.kind == 'tickler'}">
                        <div class="proposal-fields"><div>
                        <label class="form-label mt-2" for="due-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.due"/></label>
                        <input id="due-${carlos:forHtmlAttribute(proposal.key)}" class="form-control" type="date" name="dueDate" aria-describedby="date-help-${carlos:forHtmlAttribute(proposal.key)}" value="${carlos:forHtmlAttribute(proposal.dueDate)}" required>
                        <div class="field-help" id="date-help-${carlos:forHtmlAttribute(proposal.key)}">
                            <c:choose>
                                <c:when test="${proposal.dateBasis == 'relative'}"><fmt:message key="chartUpdates.relativeDate"><fmt:param><carlos:encode value="${proposal.suggestedDate}"/></fmt:param><fmt:param><carlos:encode value="${proposal.dateAnchor}"/></fmt:param></fmt:message></c:when>
                                <c:when test="${proposal.dateBasis == 'explicit'}"><fmt:message key="chartUpdates.explicitDate"><fmt:param><carlos:encode value="${proposal.suggestedDate}"/></fmt:param></fmt:message></c:when>
                                <c:otherwise><fmt:message key="chartUpdates.missingDate"/></c:otherwise>
                            </c:choose>
                            <c:if test="${proposal.pastDue}"><p class="past-date"><fmt:message key="chartUpdates.pastDate"/></p></c:if>
                        </div>
                        </div><div>
                        <label class="form-label mt-2" for="assignee-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.assignee"/></label>
                        <select id="assignee-${carlos:forHtmlAttribute(proposal.key)}" class="form-select" name="assignee" required>
                            <option value=""><fmt:message key="chartUpdates.choose"/></option>
                            <c:forEach items="${chartUpdateProviders}" var="provider"><option value="${carlos:forHtmlAttribute(provider.providerNo)}" <c:if test="${proposal.assignee == provider.providerNo}">selected</c:if>><carlos:encode value="${provider.formattedName}"/></option></c:forEach>
                        </select>
                        <c:if test="${not empty proposal.suggestedAssignee}"><p class="field-help"><fmt:message key="chartUpdates.assigneeHelp"/></p></c:if>
                        </div></div>
                    </c:when><c:otherwise>
                        <label class="form-label mt-2" for="destination-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.destination"/></label>
                        <select id="destination-${carlos:forHtmlAttribute(proposal.key)}" class="form-select" name="destination" required>
                            <option value=""><fmt:message key="chartUpdates.choose"/></option>
                            <c:forEach items="${chartUpdateSections}" var="section">
                                <option value="${carlos:forHtmlAttribute(section)}" <c:if test="${proposal.destination == section}">selected</c:if>><fmt:message key="chartUpdates.section.${section}"/></option>
                            </c:forEach>
                        </select>
                        <p class="field-help"><fmt:message key="chartUpdates.sectionHelp"/></p>
                        <p class="small mt-2"><fmt:message key="chartUpdates.signing"/></p>
                    </c:otherwise></c:choose>
                    <%-- Kept at the bottom of the window by script, which also adds Previous and Skip (they
                         only move between suggestions). "Add & next" is the form's first submit button, so
                         Enter in a field saves (after the browser's checks) and never dismisses. --%>
                    <div class="review-action-bar">
                        <button type="button" class="btn btn-outline-secondary btn-sm" data-review-previous data-step-only hidden><span aria-hidden="true">← </span><fmt:message key="dms.incomingDocs.previous"/></button>
                        <c:choose><c:when test="${proposal.kind != 'review'}"><label class="approval-confirmation"><input type="checkbox" name="confirmed" value="true" required> <fmt:message key="chartUpdates.confirm"/></label>
                            <button class="btn btn-primary btn-sm" type="submit"><fmt:message key="chartUpdates.addNext"/><span aria-hidden="true"> →</span></button></c:when>
                        <c:otherwise><span class="review-action-spacer"></span></c:otherwise></c:choose>
                        <button type="button" class="btn btn-outline-secondary btn-sm" data-review-skip data-step-only hidden><fmt:message key="chartUpdates.skip"/></button>
                        <button class="btn btn-outline-secondary btn-sm" type="submit" formnovalidate formaction="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/DismissAiChartUpdate"><c:choose><c:when test="${proposal.kind == 'review'}"><fmt:message key="chartUpdates.doneReviewing"/></c:when><c:otherwise><fmt:message key="chartUpdates.dismiss"/></c:otherwise></c:choose></button>
                    </div>
                </form>
                </c:otherwise></c:choose>
            </div>
        </article>
        </c:forEach>
        <section id="review-summary" class="review-summary" aria-labelledby="review-summary-title">
        <h2 id="review-summary-title" class="h4" data-review-focus><carlos:encode value="${chartUpdateSummaryLabel}"/></h2>
        <c:if test="${not empty chartUpdateReview.agentName}"><p class="small"><fmt:message key="chartUpdates.agent"/>: <carlos:encode value="${chartUpdateReview.agentName}"/></p></c:if>
        <p class="review-progress" role="status"><fmt:message key="chartUpdates.remaining"><fmt:param value="${chartUpdateRemaining}"/></fmt:message></p>
        <c:if test="${chartUpdateRemaining == 0 and not empty chartUpdateRows}"><p class="alert alert-success"><fmt:message key="chartUpdates.complete"/></p></c:if>
        <c:if test="${empty chartUpdateRows}"><p><fmt:message key="chartUpdates.none"/></p></c:if>
        <%-- Links that open in a new tab need the GET review: after a refused save this page sits at a
             POST-only action URL, which a bare fragment would resolve against. --%>
        <c:url var="chartUpdateReviewUrl" value="/documentManager/AiChartUpdates">
            <c:param name="documentId" value="${chartUpdateDocumentId}"/>
        </c:url>
        <c:if test="${not empty chartUpdateRows}">
        <div class="table-responsive">
        <table class="table table-sm review-summary-table">
            <thead class="table-light"><tr><th scope="col"><fmt:message key="chartUpdates.summarySection"/></th><th scope="col"><fmt:message key="chartUpdates.summaryEntry"/></th><th scope="col"><fmt:message key="chartUpdates.summaryResult"/></th></tr></thead>
            <tbody>
            <c:forEach items="${chartUpdateRows}" var="proposal">
                <tr data-summary-for="${carlos:forHtmlAttribute(proposal.key)}" data-section="${carlos:forHtmlAttribute(proposal.kind == 'tickler' ? 'Tickler' : (proposal.kind == 'review' ? proposal.recordDestination : proposal.destination))}">
                    <td><span class="section-chip" data-summary-section><c:choose><c:when test="${proposal.kind == 'review'}"><fmt:message key="chartUpdates.section.${proposal.recordDestination}"/></c:when><c:when test="${proposal.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler"/></c:when><c:when test="${not empty proposal.destination}"><fmt:message key="chartUpdates.section.${proposal.destination}"/></c:when><c:otherwise><fmt:message key="chartUpdates.history"/></c:otherwise></c:choose></span></td>
                    <td class="source-text" data-summary-entry><carlos:encode value="${proposal.text}"/></td>
                    <td><c:choose><c:when test="${not empty proposal.outcome}"><carlos:encode value="${proposal.outcome}"/></c:when>
                        <c:otherwise><a href="${carlos:forHtmlAttribute(chartUpdateReviewUrl)}#proposal-${carlos:forHtmlAttribute(proposal.key)}" data-review-proposal="${carlos:forHtmlAttribute(proposal.key)}"><carlos:encode value="${chartUpdateNotReviewed}"/></a></c:otherwise></c:choose></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        </div>
        <p data-step-only hidden><button type="button" class="btn btn-primary btn-sm" data-review-skipped <c:if test="${chartUpdateRemaining == 0}">disabled</c:if>><fmt:message key="chartUpdates.reviewSkipped"><fmt:param value="${chartUpdateRemaining}"/></fmt:message></button></p>
        </c:if>
        <p><a href="#current-chart-title"><fmt:message key="chartUpdates.current"/></a></p>
        <details class="coverage-audit" id="coverage-audit">
            <summary><fmt:message key="chartUpdates.audit.title"/></summary>
            <p><fmt:message key="chartUpdates.audit.help"/></p>
            <p><fmt:message key="chartUpdates.audit.workflows"/></p>
            <c:choose><c:when test="${not empty chartUpdateCoverage}">
                <p><fmt:message key="chartUpdates.audit.processed"><fmt:param value="${chartUpdateCoverage.size()}"/></fmt:message></p>
                <%-- Script handles plain clicks; others open the GET review (chartUpdateReviewUrl, above). --%>
                <c:forEach items="${chartUpdateCoverage}" var="section" varStatus="sectionNumber">
                    <details class="coverage-section">
                        <summary><fmt:message key="chartUpdates.audit.section"><fmt:param value="${sectionNumber.count}"/><fmt:param value="${section.links.size()}"/><fmt:param value="${section.gaps.size()}"/></fmt:message></summary>
                        <h3 class="h5"><fmt:message key="chartUpdates.evidence"/></h3>
                        <blockquote class="source-text"><carlos:encode value="${section.text}"/></blockquote>
                        <c:forEach items="${section.links}" var="link">
                            <a href="${carlos:forHtmlAttribute(chartUpdateReviewUrl)}#proposal-${carlos:forHtmlAttribute(link.key)}" data-review-proposal="${carlos:forHtmlAttribute(link.key)}"><fmt:message key="chartUpdates.audit.suggestion"><fmt:param value="${link.number}"/></fmt:message></a>
                            <c:if test="${link.nativeRecord}"> (<fmt:message key="chartUpdates.section.${link.destination}"/>: <fmt:message key="chartUpdates.audit.normalForm"/>)</c:if>
                        </c:forEach>
                        <c:if test="${not empty section.gaps}">
                            <h3 class="h5"><fmt:message key="chartUpdates.audit.gaps"/></h3>
                            <p><fmt:message key="chartUpdates.audit.gapsHelp"/></p>
                            <c:forEach items="${section.gaps}" var="gap"><blockquote class="source-text coverage-gap"><carlos:encode value="${gap}"/></blockquote></c:forEach>
                        </c:if>
                    </details>
                </c:forEach>
                <c:if test="${not empty chartUpdateRejected}">
                    <details class="coverage-rejected">
                        <summary><fmt:message key="chartUpdates.audit.rejected"><fmt:param value="${chartUpdateRejected.size()}"/></fmt:message></summary>
                        <p><fmt:message key="chartUpdates.audit.rejectedHelp"/></p>
                        <c:forEach items="${chartUpdateRejected}" var="rejected">
                            <blockquote class="source-text"><carlos:encode value="${rejected.evidence}"/></blockquote>
                            <p><carlos:encode value="${rejected.reason}"/></p>
                        </c:forEach>
                    </details>
                </c:if>
            </c:when><c:otherwise><p><fmt:message key="chartUpdates.audit.unavailable"/></p></c:otherwise></c:choose>
        </details>
    </c:when>
    <c:otherwise><p><fmt:message key="chartUpdates.intro"/></p></c:otherwise>
    </c:choose>
    <c:if test="${not empty chartUpdateReview}"><details class="regenerate mt-4"><summary><fmt:message key="chartUpdates.regenerate"/></summary><p class="mt-2"><fmt:message key="chartUpdates.regenerateHelp"/></p></c:if>
    <fmt:message key="chartUpdates.generating" var="generatingLabel"/>
    <fmt:message key="chartUpdates.discardForGeneration" var="discardForGeneration"/>
    <form class="generation-form" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/GenerateAiChartUpdates" data-discard-confirm="${carlos:forHtmlAttribute(discardForGeneration)}" data-busy-label="${carlos:forHtmlAttribute(generatingLabel)}">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
        <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(chartUpdateDocumentId)}">
        <button class="btn btn-primary btn-sm" type="submit"><fmt:message key="chartUpdates.generate"/></button>
    </form>
    <c:if test="${not empty chartUpdateReview}"></details></section></c:if>
</main>
<aside class="chart-reference" id="chart-reference">
    <section aria-labelledby="full-source-title">
        <h2 id="full-source-title"><fmt:message key="chartUpdates.fullSource"/></h2>
        <p class="small source-highlight-help" hidden><fmt:message key="chartUpdates.sourceHighlight"/></p>
        <div id="full-source" class="source-viewer" tabindex="0" aria-labelledby="full-source-title">
            <div id="chart-update-source" class="source-text"><carlos:encode value="${chartUpdateSource}"/></div>
        </div>
    </section>
    <section class="chart-check" id="chart-check" aria-labelledby="chart-check-title" hidden>
        <h2 id="chart-check-title"><fmt:message key="chartUpdates.chartCheck"/></h2>
        <p class="chart-check-clear" hidden><fmt:message key="chartUpdates.chartCheckClear"/></p>
        <div class="chart-check-body"></div>
    </section>
    <section class="current-chart" aria-labelledby="current-chart-title">
        <h2 id="current-chart-title"><fmt:message key="chartUpdates.current"/></h2>
        <p class="small"><a href="#proposals"><fmt:message key="chartUpdates.proposals"/></a></p>
        <p class="small"><fmt:message key="chartUpdates.coverage"/></p>
        <p class="small"><fmt:message key="chartUpdates.duplicateCoverage"/></p>
        <c:if test="${empty chartUpdateEntries}"><p><fmt:message key="chartUpdates.noEntries"/></p></c:if>
        <c:forEach items="${chartUpdateEntries}" var="entry">
            <details class="mb-2 chart-entry" data-kind="${carlos:forHtmlAttribute(entry.kind)}" data-destinations="${carlos:forHtmlAttribute(entry.getDestinationCodes())}" id="chart-entry-${carlos:forHtmlAttribute(entry.id)}">
                <summary><carlos:encode value="${entry.id}"/> · <c:choose>
                    <c:when test="${not empty entry.destinations}"><c:forEach items="${entry.getDestinations()}" var="section" varStatus="sectionStatus"><c:if test="${not sectionStatus.first}">, </c:if><fmt:message key="chartUpdates.section.${section}"/></c:forEach></c:when>
                    <c:when test="${entry.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler"/></c:when>
                    <c:when test="${entry.kind == 'medication'}"><fmt:message key="chartUpdates.section.Medications"/></c:when>
                    <c:when test="${entry.kind == 'allergy'}"><fmt:message key="chartUpdates.section.Allergies"/></c:when>
                    <c:when test="${entry.kind == 'prevention'}"><fmt:message key="chartUpdates.section.Preventions"/></c:when>
                    <c:when test="${entry.kind == 'measurement'}"><fmt:message key="chartUpdates.measurements"/></c:when>
                    <c:otherwise><fmt:message key="chartUpdates.history"/></c:otherwise>
                </c:choose></summary>
                <p class="source-text chart-entry-text"><carlos:encode value="${entry.text}"/></p>
            </details>
        </c:forEach>
    </section>
</aside>
</div>
</c:if>
</div>
<fmt:message key="chartUpdates.nativeCloseWarning" var="nativeCloseWarning"/>
<dialog id="native-chart-review" class="native-chart-review" aria-labelledby="native-chart-title" data-close-warning="${carlos:forHtmlAttribute(nativeCloseWarning)}">
    <header class="page-header-bar page-header-bar--flex"><h2 id="native-chart-title" class="page-header-title"><fmt:message key="chartUpdates.normalForm"/></h2><button type="button" class="btn btn-secondary btn-sm" data-native-close><fmt:message key="global.btnClose"/></button></header>
    <p><fmt:message key="chartUpdates.patient"/>: <carlos:encode value="${chartUpdatePatientLabel}"/> (#<carlos:encode value="${chartUpdatePatient}"/>)</p>
    <details open><summary><fmt:message key="chartUpdates.evidence"/></summary><pre class="source-text native-review-source"></pre></details>
    <fmt:message key="chartUpdates.normalForm" var="nativeFormTitle"/>
    <iframe title="${carlos:forHtmlAttribute(nativeFormTitle)}" referrerpolicy="same-origin"></iframe>
</dialog>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates-matching.js"></script>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates-evidence.js"></script>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates.js"></script>
</body>
</html>
