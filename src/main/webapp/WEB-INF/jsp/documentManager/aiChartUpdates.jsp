<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
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
    <p><fmt:message key="chartUpdates.patient"/>: <carlos:encode value="${chartUpdatePatientLabel}"/> (#<carlos:encode value="${chartUpdatePatient}"/>)</p>
    <p><carlos:encode value="${chartUpdateTitle}"/> · <carlos:encode value="${chartUpdateDate}"/></p>
    <p class="review-guidance"><fmt:message key="chartUpdates.warning"/></p>
    </c:if>
</div>
<c:if test="${not empty chartUpdateError}"><p class="alert alert-danger" role="alert"><carlos:encode value="${chartUpdateError}"/></p></c:if>
<c:if test="${chartUpdateReady}">
<fmt:message key="chartUpdates.proposals" var="chartUpdateWorkflowLabel"/>
<div class="chart-update-layout">
<main id="proposals">
    <c:choose>
    <c:when test="${not empty chartUpdateReview}">
        <h2 class="h4"><fmt:message key="chartUpdates.proposals"/> (<carlos:encode value="${chartUpdateRows.size()}"/>)</h2>
        <c:if test="${not empty chartUpdateReview.agentName}"><p class="small"><fmt:message key="chartUpdates.agent"/>: <carlos:encode value="${chartUpdateReview.agentName}"/></p></c:if>
        <p class="review-progress" role="status"><fmt:message key="chartUpdates.remaining"><fmt:param value="${chartUpdateRemaining}"/></fmt:message></p>
        <c:if test="${chartUpdateRemaining == 0 and not empty chartUpdateRows}"><p class="alert alert-success"><fmt:message key="chartUpdates.complete"/></p></c:if>
        <c:if test="${empty chartUpdateRows}"><p><fmt:message key="chartUpdates.none"/></p></c:if>
        <p><a href="#current-chart-title"><fmt:message key="chartUpdates.current"/></a></p>
        <p class="suggestions-help"><fmt:message key="chartUpdates.suggestionsHelp"/></p>
        <nav class="review-steps" aria-label="${carlos:forHtmlAttribute(chartUpdateWorkflowLabel)}" hidden>
            <button type="button" class="btn btn-secondary btn-sm" data-review-previous><fmt:message key="dms.incomingDocs.previous"/></button>
            <span data-review-position role="status"></span>
            <button type="button" class="btn btn-secondary btn-sm" data-review-next><fmt:message key="dms.incomingDocs.next"/></button>
        </nav>
        <c:forEach items="${chartUpdateRows}" var="proposal" varStatus="position">
        <article class="card mb-3 proposal ${empty proposal.outcome ? '' : 'proposal-complete'}" data-proposal-key="${carlos:forHtmlAttribute(proposal.key)}">
            <div class="card-body">
                <div class="proposal-heading"><span class="proposal-number" aria-hidden="true"><carlos:encode value="${position.count}"/>.</span><h3 class="h5"><c:choose><c:when test="${proposal.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler"/></c:when><c:otherwise><fmt:message key="chartUpdates.history"/></c:otherwise></c:choose></h3></div>
                <details class="proposal-evidence" open><summary><fmt:message key="chartUpdates.evidence"/></summary><blockquote class="source-text"><carlos:encode value="${proposal.evidence}"/></blockquote></details>
                <p><a href="#full-source" data-show-source><fmt:message key="chartUpdates.showSource"/></a></p>
                <div class="chart-match-notice" role="status" hidden>
                    <p><fmt:message key="chartUpdates.matchingChartText"/></p>
                    <ul class="chart-match-links"></ul>
                </div>
                <div class="related-proposal-notice" role="status" hidden>
                    <p><fmt:message key="chartUpdates.relatedProposals"/></p>
                    <div class="related-proposal-quotes"></div>
                </div>
                <c:choose>
                <c:when test="${not empty proposal.outcome}"><p class="alert alert-success mt-2" role="status"><carlos:encode value="${proposal.outcome}"/></p></c:when>
                <c:otherwise>
                <form class="proposal-form" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ApplyAiChartUpdate">
                    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
                    <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(chartUpdateDocumentId)}">
                    <input type="hidden" name="reviewToken" value="${carlos:forHtmlAttribute(chartUpdateReview.token)}">
                    <input type="hidden" name="proposalKey" value="${carlos:forHtmlAttribute(proposal.key)}">
                    <input type="hidden" name="chartFingerprint" value="${carlos:forHtmlAttribute(chartUpdateReview.fingerprint)}">
                    <label class="form-label mt-2" for="text-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.entry"/></label>
                    <textarea id="text-${carlos:forHtmlAttribute(proposal.key)}" class="form-control" name="entryText" rows="3" maxlength="2000" required><carlos:encode value="${proposal.text}"/></textarea>
                    <c:choose><c:when test="${proposal.kind == 'tickler'}">
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
                            <option value="MedHistory" <c:if test="${proposal.destination == 'MedHistory'}">selected</c:if>><fmt:message key="chartUpdates.medicalHistory"/></option>
                            <option value="Concerns" <c:if test="${proposal.destination == 'Concerns'}">selected</c:if>><fmt:message key="chartUpdates.concerns"/></option>
                        </select>
                        <p class="field-help"><c:choose><c:when test="${proposal.suggestedDestination == 'MedHistory'}"><fmt:message key="chartUpdates.historySuggestion"/></c:when><c:otherwise><fmt:message key="chartUpdates.concernsSuggestion"/></c:otherwise></c:choose></p>
                        <p class="small mt-2"><fmt:message key="chartUpdates.signing"/></p>
                    </c:otherwise></c:choose>
                    <label class="approval-confirmation"><input type="checkbox" name="confirmed" value="true" required> <fmt:message key="chartUpdates.confirm"/></label>
                    <div class="proposal-actions"><button class="btn btn-primary btn-sm" type="submit"><fmt:message key="chartUpdates.accept"/></button>
                    <button class="btn btn-secondary btn-sm" type="submit" formnovalidate formaction="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/DismissAiChartUpdate"><fmt:message key="chartUpdates.dismiss"/></button></div>
                </form>
                </c:otherwise></c:choose>
            </div>
        </article>
        </c:forEach>
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
    <c:if test="${not empty chartUpdateReview}"></details></c:if>
</main>
<aside class="chart-reference" id="chart-reference">
    <section aria-labelledby="full-source-title">
        <h2 id="full-source-title"><fmt:message key="chartUpdates.fullSource"/></h2>
        <p class="small source-highlight-help" hidden><fmt:message key="chartUpdates.sourceHighlight"/></p>
        <div id="full-source" class="source-viewer" tabindex="0" aria-labelledby="full-source-title">
            <div id="chart-update-source" class="source-text"><carlos:encode value="${chartUpdateSource}"/></div>
        </div>
    </section>
    <section class="current-chart" aria-labelledby="current-chart-title">
        <h2 id="current-chart-title"><fmt:message key="chartUpdates.current"/></h2>
        <p class="small"><a href="#proposals"><fmt:message key="chartUpdates.proposals"/></a></p>
        <p class="small"><fmt:message key="chartUpdates.coverage"/></p>
        <p class="small"><fmt:message key="chartUpdates.duplicateCoverage"/></p>
        <c:if test="${empty chartUpdateEntries}"><p><fmt:message key="chartUpdates.noEntries"/></p></c:if>
        <c:forEach items="${chartUpdateEntries}" var="entry">
            <details class="mb-2 chart-entry" id="chart-entry-${carlos:forHtmlAttribute(entry.id)}">
                <summary><carlos:encode value="${entry.id}"/> · <c:choose><c:when test="${entry.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler"/></c:when><c:otherwise><fmt:message key="chartUpdates.history"/></c:otherwise></c:choose></summary>
                <p class="source-text chart-entry-text"><carlos:encode value="${entry.text}"/></p>
            </details>
        </c:forEach>
    </section>
</aside>
</div>
</c:if>
</div>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates-matching.js"></script>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates-evidence.js"></script>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates.js"></script>
</body>
</html>
